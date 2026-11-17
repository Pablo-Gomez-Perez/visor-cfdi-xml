package com.kathsoft.visor.app.service.operacion;

import static org.junit.jupiter.api.Assertions.*;
import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.*;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** Prueba RF-020 sobre MySQL real, sin usar datos operativos. */
@Testcontainers
class ClasificacionOperacionesServiceIT {
    @TempDir Path temporal;
    private static Path db(String ruta){
        Path p=Path.of("database").resolve(ruta);
        return (Files.isRegularFile(p)?p:Path.of("app").resolve(p)).toAbsolutePath();
    }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.46")
        .withDatabaseName("visor_cfdi").withUsername("visor_it").withPassword("Prueba#Rf020")
        .withLogConsumer(f->System.err.print(f.getUtf8String()))
        .withCopyFileToContainer(MountableFile.forHostPath(db("schema/visor_cfdi_mysql8_schema_v1.sql")),"/docker-entrypoint-initdb.d/01-schema.sql")
        .withCopyFileToContainer(MountableFile.forHostPath(db("migrations/20261117_rf020_clasificacion_operaciones.sql")),"/tmp/rf020.sql");

    private static void ejecutar(EntityManager em,String sql,Object... args){
        var q=em.createNativeQuery(sql);for(int i=0;i<args.length;i++)q.setParameter(i+1,args[i]);q.executeUpdate();
    }
    private static long numero(EntityManager em,String sql){
        return ((Number)em.createNativeQuery(sql).getSingleResult()).longValue();
    }
    private static Seleccion sel(Operacion o){return new Seleccion(o.id(),o.revision());}
    private static Filtro filtro(long t,long cat){
        return new Filtro(t,LocalDate.of(2026,6,1),LocalDate.of(2026,6,30),"RECIBIDO","",cat);
    }
    @Test void catalogoHistorialReclasificacionAislamientoYAtomicidad()throws Exception{
        var migracion=MYSQL.execInContainer("sh","-ec","""
            export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
            mysql -uroot visor_cfdi -e 'DROP TABLE clasificacion_operacion_evento'
            mysql -uroot visor_cfdi < /tmp/rf020.sql
            mysql -uroot visor_cfdi < /tmp/rf020.sql
            """);
        assertEquals(0,migracion.getExitCode(),migracion.getStderr());
        var config=new ConfiguracionSistema(MYSQL.getHost(),MYSQL.getMappedPort(3306),MYSQL.getDatabaseName(),MYSQL.getUsername(),temporal.resolve("repo"));
        try(var jpa=new JpaContext(config,MYSQL.getPassword().toCharArray())){
            var usuarios=new UsuariosService(jpa);usuarios.inicializarRoles();
            long actor=usuarios.registrarAdministradorInicial(new SolicitudUsuario("Admin RF020","adminrf020",null,"ADMIN"),"ClaveSegura#Rf020-2026".toCharArray()).id();
            assertTrue(usuarios.permisosDe(actor).containsAll(Set.of(Permisos.OPERACIONES_CONSULTAR,Permisos.OPERACIONES_CLASIFICAR,Permisos.OPERACIONES_GESTIONAR_CATEGORIAS)));
            long a=usuarios.contribuyentes().registrar(actor,new SolicitudContribuyente("BBB010101BBB","Titular A",null,null));
            long b=usuarios.contribuyentes().registrar(actor,new SolicitudContribuyente("AAA010101AAA","Titular B",null,null));
            usuarios.registrar(actor,new SolicitudUsuario("Lector RF020","lector20",null,"CONSULTA"),"ClaveSegura#Lector-2026".toCharArray());
            long lector=jpa.<Long>consultar(em->numero(em,"SELECT id FROM usuario WHERE login='lector20'"));
            jpa.enTransaccion(em->{
                for(int i=1;i<=5;i++){
                    long id=960000+i;
                    String uuid="123e4567-e89b-12d3-a456-"+String.format("%012d",i);
                    ejecutar(em,"INSERT INTO documento_fiscal(id,clase,uuid,version_estandar,fecha_emision) VALUES (?1,'CFDI',?2,'4.0','2026-06-30 23:59:59')",id,uuid);
                    ejecutar(em,"INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total) VALUES (?1,?2,?3,?4,?4)",id,i==4?"P":"I",i==4?"XXX":"MXN",i==4?0:100);
                    ejecutar(em,"INSERT INTO documento_participante(documento_id,rol,rfc) VALUES (?1,'E','AAA010101AAA'),(?1,'R','BBB010101BBB')",id);
                    ejecutar(em,"INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido) VALUES (?1,?2,'RECIBIDO')",i==5?b:a,id);
                }
                ejecutar(em,"INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido) VALUES (?1,960001,'EMITIDO')",b);
                ejecutar(em,"UPDATE contribuyente_documento SET archivado_en=CURRENT_TIMESTAMP(6) WHERE contribuyente_id=?1 AND documento_id=960003",a);
                ejecutar(em,"INSERT INTO usuario_contribuyente(usuario_id,contribuyente_id) VALUES (?1,?2)",lector,a);
                return null;
            });
            var s=usuarios.operaciones();
            assertEquals(6,s.crearPredeterminadas(actor,a));assertEquals(0,s.crearPredeterminadas(actor,a));
            s.crearCategoria(actor,a,"papeleria","Papelería",Naturaleza.GASTO);
            assertThrows(IllegalArgumentException.class,()->s.crearCategoria(actor,a,"PAPELERIA","Duplicada",Naturaleza.COSTO));
            s.crearPredeterminadas(actor,b);
            long gasto=s.categorias(actor,a).stream().filter(c->c.clave().equals("GASTO")).findFirst().orElseThrow().id();
            long costo=s.categorias(actor,a).stream().filter(c->c.clave().equals("COSTO")).findFirst().orElseThrow().id();
            long ajena=s.categorias(actor,b).get(0).id();
            var inicial=s.consultar(actor,filtro(a,0),0).filas();assertEquals(2,inicial.size());
            var uno=inicial.stream().filter(o->o.id()==960001).findFirst().orElseThrow();
            var dos=inicial.stream().filter(o->o.id()==960002).findFirst().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->s.clasificar(actor,a,List.of(sel(uno)),ajena,"Categoría ajena"));
            assertThrows(SecurityException.class,()->s.clasificar(actor,a,List.of(sel(uno),new Seleccion(960005,0)),gasto,"Rollback"));
            assertTrue(s.historial(actor,a,uno.id(),0).filas().isEmpty());
            assertThrows(SecurityException.class,()->s.clasificar(actor,a,List.of(new Seleccion(960003,0)),gasto,"Archivado"));
            assertThrows(SecurityException.class,()->s.clasificar(actor,a,List.of(new Seleccion(960004,0)),gasto,"REP"));
            assertEquals(2,s.clasificar(actor,a,List.of(sel(uno),sel(dos)),gasto,"Clasificación inicial"));
            assertEquals(0,s.consultar(actor,filtro(a,0),0).filas().size());
            var asignadas=s.consultar(actor,filtro(a,gasto),0).filas();assertEquals(2,asignadas.size());
            assertThrows(IllegalStateException.class,()->s.clasificar(actor,a,List.of(sel(uno)),costo,"Revisión obsoleta"));
            var actual=asignadas.stream().filter(o->o.id()==uno.id()).findFirst().orElseThrow();
            assertEquals(0,s.clasificar(actor,a,List.of(sel(actual)),gasto,"Sin cambios"));
            assertEquals(1,s.clasificar(actor,a,List.of(sel(actual)),costo,"Reclasificación"));
            assertTrue(s.cambiarEstadoCategoria(actor,a,costo,false));
            var reclasificada=s.consultar(actor,filtro(a,costo),0).filas().get(0);
            assertEquals("Costo",reclasificada.categoria());
            assertThrows(IllegalArgumentException.class,()->s.clasificar(actor,a,List.of(new Seleccion(dos.id(),asignadas.stream().filter(o->o.id()==dos.id()).findFirst().orElseThrow().revision())),costo,"Inactiva"));
            assertEquals(1,s.clasificar(actor,a,List.of(sel(reclasificada)),null,"Retiro justificado"));
            var eventos=s.historial(actor,a,uno.id(),0).filas();
            assertEquals(3,eventos.size());assertNull(eventos.get(0).categoria());assertEquals("Costo",eventos.get(1).categoria());
            assertEquals("Gasto",eventos.get(2).categoria());assertEquals("adminrf020",eventos.get(0).usuario());
            assertEquals("Retiro justificado",eventos.get(0).motivo());
            assertEquals(1,s.consultar(actor,filtro(a,0),0).filas().size());
            assertTrue(s.historial(actor,b,uno.id(),0).filas().isEmpty());
            assertThrows(SecurityException.class,()->s.historial(actor,a,960005,0));
            assertThrows(SecurityException.class,()->s.consultar(lector,filtro(a,-1),0));
            jpa.enTransaccion(em->{
                ejecutar(em,"INSERT INTO rol_permiso(rol_id,permiso_id) SELECT r.id,p.id FROM rol r CROSS JOIN permiso p WHERE r.clave='CONSULTA' AND p.recurso='OPERACIONES' AND p.accion='CONSULTAR'");
                return null;
            });
            assertEquals(2,s.consultar(lector,filtro(a,-1),0).filas().size());
            assertThrows(SecurityException.class,()->s.consultar(lector,filtro(b,-1),0));
            assertThrows(SecurityException.class,()->s.clasificar(lector,a,List.of(sel(reclasificada)),gasto,"Sin permiso"));
            assertThrows(SecurityException.class,()->s.crearPredeterminadas(lector,a));
            assertEquals(4L,jpa.<Long>consultar(em->numero(em,"SELECT COUNT(*) FROM clasificacion_operacion_evento")).longValue());
            assertEquals(0L,jpa.<Long>consultar(em->numero(em,"SELECT COUNT(*) FROM clasificacion_concepto")).longValue());
        }
    }
}
