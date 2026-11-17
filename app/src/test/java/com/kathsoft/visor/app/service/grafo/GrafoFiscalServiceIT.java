package com.kathsoft.visor.app.service.grafo;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.grafo.*;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import static org.junit.jupiter.api.Assertions.*;

/** MySQL real con esquema canónico; no requiere instalar rutinas de importación. */
@Testcontainers
class GrafoFiscalServiceIT {
    private static Path esquema(){
        Path p=Path.of("database/schema/visor_cfdi_mysql8_schema_v1.sql");
        return (Files.isRegularFile(p)?p:Path.of("app").resolve(p)).toAbsolutePath();
    }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.46")
        .withDatabaseName("visor_cfdi").withUsername("visor_it").withPassword("Prueba#Grafo")
        .withLogConsumer(f->System.err.print(f.getUtf8String()))
        .withCopyFileToContainer(MountableFile.forHostPath(esquema()),"/docker-entrypoint-initdb.d/01-schema.sql");

    private static String uuid(long n){return "123e4567-e89b-12d3-a456-"+String.format("%012d",n);}
    private static void ejecutar(EntityManager em,String sql,Object... args){
        var q=em.createNativeQuery(sql);
        for(int i=0;i<args.length;i++)q.setParameter(i+1,args[i]);
        q.executeUpdate();
    }
    private static void relacion(EntityManager em,long gid,long origen,long destino,int ordinal){
        ejecutar(em,"INSERT INTO cfdi_relacion_grupo(id,documento_id,ordinal,tipo_relacion) VALUES (?1,?2,?3,'01')",gid,origen,ordinal);
        ejecutar(em,"INSERT INTO cfdi_relacion_uuid(grupo_id,uuid_relacionado) VALUES (?1,?2)",gid,uuid(destino));
    }

    @Test void consultaBidireccionalAisladaYAcotada(){
        var config=new ConfiguracionSistema(MYSQL.getHost(),MYSQL.getMappedPort(3306),MYSQL.getDatabaseName(),MYSQL.getUsername(),Path.of("xml"));
        try(var jpa=new JpaContext(config,MYSQL.getPassword().toCharArray())){
            var usuarios=new UsuariosService(jpa);usuarios.inicializarRoles();
            long actor=usuarios.registrarAdministradorInicial(new SolicitudUsuario("Admin grafo","admingrafo",null,"ADMIN"),"ClaveSegura#Grafo-2026".toCharArray()).id();
            long a=usuarios.contribuyentes().registrar(actor,new SolicitudContribuyente("BBB010101BBB","Titular A",null,null));
            long b=usuarios.contribuyentes().registrar(actor,new SolicitudContribuyente("AAA010101AAA","Titular B",null,null));
            usuarios.registrar(actor,new SolicitudUsuario("Lector grafo","lectorgrafo",null,"CONSULTA"),"ClaveSegura#Lector-2026".toCharArray());
            long lector=jpa.<Long>consultar(em->((Number)em.createNativeQuery("SELECT id FROM usuario WHERE login='lectorgrafo'").getSingleResult()).longValue());
            assertTrue(usuarios.permisosDe(actor).contains(Permisos.CFDI_CONSULTAR_GRAFO));
            jpa.enTransaccion(em->{
                for(long i=1;i<=7;i++){
                    long id=930000+i;
                    ejecutar(em,"INSERT INTO documento_fiscal(id,clase,uuid,version_estandar,fecha_emision) VALUES (?1,'CFDI',?2,'4.0','2026-06-15 12:00:00')",id,uuid(i));
                    ejecutar(em,"INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total) VALUES (?1,?2,?3,?4,?4)",id,i==2?"P":i>=4?"E":"I",i==2?"XXX":"MXN",i==2?0:100);
                    ejecutar(em,"INSERT INTO documento_participante(documento_id,rol,rfc) VALUES (?1,'E','AAA010101AAA'),(?1,'R','BBB010101BBB')",id);
                    ejecutar(em,"INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido) VALUES (?1,?2,?3)",i==6?b:a,id,i==6?"EMITIDO":"RECIBIDO");
                }
                ejecutar(em,"UPDATE contribuyente_documento SET archivado_en=CURRENT_TIMESTAMP(6) WHERE contribuyente_id=?1 AND documento_id=930007",a);
                ejecutar(em,"INSERT INTO pago_fiscal(id,documento_id,ordinal,fecha_pago,forma_pago,moneda,monto) VALUES (940001,930002,1,'2026-06-20 12:00:00','03','MXN',150)");
                ejecutar(em,"INSERT INTO pago_fiscal_documento(pago_fiscal_id,ordinal,uuid_documento,moneda,num_parcialidad,importe_pagado) VALUES (940001,1,?1,'MXN',1,50),(940001,2,?2,'MXN',1,100)",uuid(1),uuid(3));
                relacion(em,950001,930004,1,1); // NC directamente vinculada
                relacion(em,950002,930005,3,1); // tercera distancia
                relacion(em,950003,930006,1,1); // otro titular, no debe aparecer
                relacion(em,950004,930001,7,1); // archivado: solamente UUID conocido
                relacion(em,950005,930001,6,2); // otro titular: solamente UUID conocido
                relacion(em,950006,930007,5,1); // no expandir archivado
                relacion(em,950007,930001,99,3); // no importado
                ejecutar(em,"INSERT INTO usuario_contribuyente(usuario_id,contribuyente_id) VALUES (?1,?2)",lector,a);
                return null;
            });
            var s=new GrafoFiscalService(jpa);
            var g=s.consultar(actor,a,930001);
            assertEquals(7,g.nodos().size());
            assertEquals(6,g.aristas().size());
            assertEquals(4L,g.nodos().stream().filter(NodoCfdi::disponible).count());
            assertTrue(g.nodos().stream().anyMatch(n->Long.valueOf(930003).equals(n.documentoId())));
            assertFalse(g.nodos().stream().anyMatch(n->n.uuid().equalsIgnoreCase(uuid(5))));
            assertTrue(g.nodos().stream().filter(n->n.uuid().equalsIgnoreCase(uuid(6))||n.uuid().equalsIgnoreCase(uuid(7))).allMatch(n->n.documentoId()==null&&n.rfcEmisor()==null));
            assertTrue(g.aristas().stream().anyMatch(x->x.tipo()==TipoRelacionDocumental.COMPLEMENTO_PAGO&&x.etiqueta().equals("REP #1 · 50 MXN")));
            assertFalse(g.aristas().stream().anyMatch(x->x.origen().equalsIgnoreCase(uuid(6))));
            assertThrows(SecurityException.class,()->s.consultar(actor,a,930007));
            assertThrows(SecurityException.class,()->s.consultar(actor,b,930001));
            assertFalse(s.permitido(lector,a));assertThrows(SecurityException.class,()->s.consultar(lector,a,930001));
            jpa.enTransaccion(em->{
                ejecutar(em,"INSERT INTO rol_permiso(rol_id,permiso_id) SELECT r.id,p.id FROM rol r CROSS JOIN permiso p WHERE r.clave='CONSULTA' AND p.recurso='CFDI' AND p.accion IN ('CONSULTAR','CONSULTAR_DETALLE','CONSULTAR_GRAFO')");
                return null;
            });
            assertTrue(s.permitido(lector,a));assertFalse(s.permitido(lector,b));
            assertEquals(g.nodos().size(),s.consultar(lector,a,930001).nodos().size());
            assertThrows(SecurityException.class,()->s.consultar(lector,b,930006));
        }
    }
}
