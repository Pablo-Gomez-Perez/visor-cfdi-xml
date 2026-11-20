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
import java.math.BigDecimal;
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
    private static void decimal(String esperado,BigDecimal actual){assertEquals(0,new BigDecimal(esperado).compareTo(actual));}
    private static void concepto(EntityManager em,long doc,long id,int ordinal,int importe,String objeto,int descuento){
        ejecutar(em,"INSERT INTO cfdi_concepto(id,documento_id,ordinal,clave_prod_serv,cantidad,clave_unidad,descripcion,valor_unitario,importe,descuento,objeto_impuesto) VALUES (?1,?2,?3,'01010101',1,'H87','Concepto sintético',?4,?4,?5,?6)",id,doc,ordinal,importe,descuento,objeto);
    }
    private static void iva(EntityManager em,long id,int ordinal,int base,String factor,BigDecimal tasa,BigDecimal importe){
        ejecutar(em,"INSERT INTO cfdi_concepto_impuesto(concepto_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe) VALUES (?1,'TRASLADO',?2,'002',?3,?4,?5,?6)",id,ordinal,factor,base,tasa,importe);
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

            // Conservar los casos previos y añadir suficientes documentos para superar una página.
            assertTrue(s.cambiarEstadoCategoria(actor,a,costo,true));
            jpa.enTransaccion(em->{
                for(int i=1;i<=104;i++){
                    long id=970000+i;
                    String uuid="123e4567-e89b-12d3-a456-"+String.format("%012d",id);
                    ejecutar(em,"INSERT INTO documento_fiscal(id,clase,uuid,version_estandar,fecha_emision) VALUES (?1,'CFDI',?2,'4.0','2026-06-29 12:00:00')",id,uuid);
                    ejecutar(em,"INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total) VALUES (?1,?2,?3,100,116)",id,i==103?"E":"I",i==104?"USD":"MXN");
                    ejecutar(em,"INSERT INTO documento_participante(documento_id,rol,rfc) VALUES (?1,'E','AAA010101AAA'),(?1,'R','BBB010101BBB')",id);
                    ejecutar(em,"INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido) VALUES (?1,?2,'RECIBIDO')",a,id);
                    if(i!=101){
                        concepto(em,id,id*10,1,100,"02",0);
                        iva(em,id*10,1,100,"Tasa",new BigDecimal("0.16"),new BigDecimal("16"));
                    }else{
                        concepto(em,id,id*10,1,45,"02",5);
                        iva(em,id*10,1,40,"Tasa",new BigDecimal("0.16"),new BigDecimal("6.4"));
                        concepto(em,id,id*10+1,2,20,"02",0);
                        iva(em,id*10+1,1,20,"Tasa",BigDecimal.ZERO,BigDecimal.ZERO);
                        concepto(em,id,id*10+2,3,10,"02",0);
                        ejecutar(em,"INSERT INTO cfdi_concepto_impuesto(concepto_id,movimiento,ordinal,impuesto,tipo_factor,base) VALUES (?1,'TRASLADO',1,'002','Exento',10)",id*10+2);
                        concepto(em,id,id*10+3,4,10,"01",0);
                        concepto(em,id,id*10+4,5,10,"03",0);
                        concepto(em,id,id*10+5,6,10,"02",0);
                        iva(em,id*10+5,1,5,"Tasa",new BigDecimal("0.16"),new BigDecimal("0.8"));
                        iva(em,id*10+5,2,5,"Tasa",new BigDecimal("0.16"),new BigDecimal("0.8"));
                        // Retenciones y otros impuestos no multiplican las bases IVA.
                        ejecutar(em,"INSERT INTO cfdi_concepto_impuesto(concepto_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe) VALUES (?1,'RETENCION',1,'002','Tasa',40,0.04,1.6),(?1,'TRASLADO',2,'003','Tasa',40,0.03,1.2)",id*10);
                    }
                }
                ejecutar(em,"UPDATE cfdi SET descuento=5,subtotal=105,total=108 WHERE documento_id=970101");
                ejecutar(em,"INSERT INTO consulta_estado_sat(documento_id,estado,fecha_consulta) VALUES (970102,'VIGENTE','2026-06-29 12:30:00'),(970102,'CANCELADO','2026-06-30 12:30:00')");
                return null;
            });
            var nuevos=java.util.stream.LongStream.rangeClosed(970001,970104).mapToObj(id->new Seleccion(id,0)).toList();
            assertEquals(100,s.clasificar(actor,a,nuevos.subList(0,100),costo,"Inicial"));
            assertEquals(4,s.clasificar(actor,a,nuevos.subList(100,104),costo,"Inicial"));
            assertEquals(100,s.consultar(actor,filtro(a,costo),0).filas().size());
            assertEquals(4,s.consultar(actor,filtro(a,costo),1).filas().size());
            var resumen=s.resumir(actor,filtro(a,costo));
            assertEquals(104,resumen.operaciones());assertEquals(104,resumen.clasificadas());assertEquals(0,resumen.pendientes());
            assertEquals(4,resumen.grupos().size());
            var g=resumen.grupos().stream().filter(x->x.tipo().equals("I")&&x.moneda().equals("MXN")&&x.estadoSat().equals("SIN_CONSULTA")).findFirst().orElseThrow();
            assertEquals(101,g.documentos());
            decimal("10100",g.subtotalNeto());decimal("10040",g.tasa16());decimal("20",g.tasa0());
            decimal("10",g.exento());decimal("10",g.noObjeto());decimal("10",g.sinIvaDesglosado());
            decimal("10",g.revisar());decimal("1608",g.ivaTrasladado());decimal("0",g.diferencia());
            assertTrue(resumen.grupos().stream().anyMatch(x->x.estadoSat().equals("CANCELADO")&&x.documentos()==1));
            assertTrue(resumen.grupos().stream().anyMatch(x->x.tipo().equals("E")&&x.documentos()==1));
            assertTrue(resumen.grupos().stream().anyMatch(x->x.moneda().equals("USD")&&x.documentos()==1));
            assertEquals(106,s.resumir(actor,filtro(a,-1)).operaciones());
            assertEquals(1,s.resumir(actor,filtro(a,0)).pendientes());
            decimal("100",s.resumir(actor,filtro(a,0)).grupos().get(0).sinConceptos());
            assertEquals(0,s.resumir(actor,new Filtro(a,LocalDate.of(2026,7,1),LocalDate.of(2026,7,31),"","",-1)).operaciones());
            assertThrows(SecurityException.class,()->s.resumir(lector,filtro(b,-1)));
        }
    }
}
