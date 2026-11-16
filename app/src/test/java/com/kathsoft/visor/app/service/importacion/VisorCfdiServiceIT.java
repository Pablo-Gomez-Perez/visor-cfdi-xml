package com.kathsoft.visor.app.service.importacion;

import static org.junit.jupiter.api.Assertions.*;
import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import com.kathsoft.visor.app.service.visor.VisorCfdiService.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** Integración real RF-015/016/017 con documentos sintéticos. */
@Testcontainers
class VisorCfdiServiceIT {
    @TempDir Path temporal;
    private static Path db(String ruta){
        Path base=Path.of(System.getProperty("user.dir"));
        Path p=base.resolve("database").resolve(ruta);
        return (Files.isRegularFile(p)?p:base.resolve("app/database").resolve(ruta)).toAbsolutePath();
    }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.46")
        .withDatabaseName("visor_cfdi").withUsername("visor_it").withPassword("Prueba#Rf015")
        .withLogConsumer(frame->System.err.print(frame.getUtf8String()))
        .withCopyFileToContainer(MountableFile.forHostPath(db("schema/visor_cfdi_mysql8_schema_v1.sql")),"/docker-entrypoint-initdb.d/01-schema.sql")
        .withCopyFileToContainer(MountableFile.forHostPath(db("procedures/procedures.sql")),"/docker-entrypoint-initdb.d/02-procedures.sql");

    @Test void paginaOrdenAgrupaDetalleRepYRestringeAcceso() throws Exception {
        Path repo=temporal.resolve("repo");
        var config=new ConfiguracionSistema(MYSQL.getHost(),MYSQL.getMappedPort(3306),MYSQL.getDatabaseName(),MYSQL.getUsername(),repo);
        try(JpaContext jpa=new JpaContext(config,MYSQL.getPassword().toCharArray())){
            var usuarios=new UsuariosService(jpa);usuarios.inicializarRoles();
            var admin=usuarios.registrarAdministradorInicial(new SolicitudUsuario("Admin visor","adminvisor",null,"ADMIN"),"ClaveSegura#Rf015-2026".toCharArray());
            long actor=admin.id();
            assertTrue(usuarios.permisosDe(actor).containsAll(List.of(Permisos.CFDI_CONSULTAR,Permisos.CFDI_CONSULTAR_DETALLE)));
            long a=usuarios.contribuyentes().registrar(actor,new SolicitudContribuyente("BBB010101BBB","Titular A",null,null));
            long b=usuarios.contribuyentes().registrar(actor,new SolicitudContribuyente("AAA010101AAA","Titular B",null,null));
            usuarios.registrar(actor,new SolicitudUsuario("Lector visor","lectorvisor",null,"CONSULTA"),"ClaveSegura#Lector-2026".toCharArray());
            long lector=jpa.<Long>consultar(em->((Number)em.createNativeQuery("SELECT id FROM usuario WHERE login='lectorvisor'").getSingleResult()).longValue());
            jpa.enTransaccion(em->{
                for(int i=1;i<=105;i++){
                    long id=920000+i;
                    String uuid=i==1?"123e4567-e89b-12d3-a456-426614174099":"123e4567-e89b-12d3-a456-"+String.format("%012d",i);
                    em.createNativeQuery("INSERT INTO documento_fiscal(id,clase,uuid,version_estandar,fecha_emision) VALUES (?1,'CFDI',?2,'4.0','2026-06-30 23:59:59')")
                        .setParameter(1,id).setParameter(2,uuid).executeUpdate();
                    em.createNativeQuery("INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total,metodo_pago) VALUES (?1,'I','MXN',?2,?2,'PPD')")
                        .setParameter(1,id).setParameter(2,BigDecimal.valueOf(i)).executeUpdate();
                    em.createNativeQuery("INSERT INTO documento_participante(documento_id,rol,rfc,nombre_razon_social) VALUES (?1,'E','AAA010101AAA','Proveedor sintético'),(?1,'R','BBB010101BBB','Titular A')")
                        .setParameter(1,id).executeUpdate();
                    em.createNativeQuery("INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido) VALUES (?1,?2,'RECIBIDO'),(?3,?2,'EMITIDO')")
                        .setParameter(1,a).setParameter(2,id).setParameter(3,b).executeUpdate();
                }
                em.createNativeQuery("UPDATE contribuyente_documento SET archivado_en=CURRENT_TIMESTAMP(6) WHERE contribuyente_id=?1 AND documento_id=920105")
                    .setParameter(1,a).executeUpdate();
                em.createNativeQuery("INSERT INTO consulta_estado_sat(documento_id,estado,fecha_consulta) VALUES (920001,'VIGENTE','2026-07-01'),(920001,'CANCELADO','2026-07-02')").executeUpdate();
                em.createNativeQuery("INSERT INTO usuario_contribuyente(usuario_id,contribuyente_id) VALUES (?1,?2)").setParameter(1,lector).setParameter(2,b).executeUpdate();
                return null;
            });
            Path xml=temporal.resolve("rep.xml");
            String uuidRep="123e4567-e89b-12d3-a456-999999999999";
            Files.writeString(xml,EjemplosPagosHistoricos.xml("4.0",uuidRep,EjemplosPagosHistoricos.PAGO20));
            new ImportacionCfdiService(jpa,repo).importar(actor,new ImportacionCfdiService.Solicitud(a,List.of(xml),false),p->{},()->false);
            var visor=usuarios.visorCfdi();var detalle=usuarios.detalleCfdi();
            Filtro junio=new Filtro(a,LocalDate.of(2026,6,1),LocalDate.of(2026,6,30),Fecha.EMISION,"","I","RECIBIDO","","Proveedor","",null,null,"","","","");
            // Orden global descendente: 104 activos, salto de página sin duplicar.
            Orden orden=new Orden(Columna.TOTAL,false);
            var primera=visor.consultar(actor,junio,orden,0);
            assertEquals(100,primera.filas().size());assertTrue(primera.siguiente());
            assertEquals(920104L,primera.filas().get(0).id());
            var segunda=visor.consultar(actor,junio,orden,1);
            assertEquals(4,segunda.filas().size());assertFalse(segunda.siguiente());
            assertEquals(920001L,segunda.filas().get(3).id());
            var grupos=visor.agrupar(actor,junio,0);
            assertEquals(1,grupos.filas().size());
            assertEquals(104L,grupos.filas().get(0).documentos());
            assertEquals(0,BigDecimal.valueOf(5460).compareTo(grupos.filas().get(0).total()));
            var factura=segunda.filas().get(3);
            assertEquals("CANCELADO",factura.valores().get(Columna.SAT.ordinal()));
            assertEquals("REP_SIN_CONCILIAR",factura.valores().get(Columna.PAGO.ordinal()));
            var d=detalle.consultar(actor,a,factura.id());
            assertTrue(d.secciones().stream().anyMatch(s->s.nombre().equals("REP que referencian este CFDI")));
            assertTrue(d.xml().isEmpty());
            assertThrows(SecurityException.class,()->detalle.consultar(actor,a,920105));
            assertThrows(SecurityException.class,()->visor.consultar(lector,junio,orden,0));
            jpa.enTransaccion(em->{
                em.createNativeQuery("INSERT INTO rol_permiso(rol_id,permiso_id) SELECT r.id,p.id FROM rol r CROSS JOIN permiso p WHERE r.clave='CONSULTA' AND p.recurso='CFDI' AND p.accion='CONSULTAR'").executeUpdate();
                return null;
            });
            assertEquals(1,visor.titulares(lector).size());
            assertThrows(SecurityException.class,()->visor.consultar(lector,junio,orden,0));
            assertThrows(SecurityException.class,()->detalle.consultar(lector,b,920105));
            Filtro rep=new Filtro(a,LocalDate.of(2020,1,1),LocalDate.of(2020,12,31),Fecha.EMISION,uuidRep,"P","","","","",null,null,"","","","");
            var filaRep=visor.consultar(actor,rep,orden,0).filas().get(0);
            assertEquals(0,BigDecimal.valueOf(50).compareTo((BigDecimal)filaRep.valores().get(Columna.TOTAL_REP.ordinal())));
            var dr=detalle.consultar(actor,a,filaRep.id());
            assertTrue(dr.xml().contains(uuidRep));
            assertTrue(dr.secciones().stream().anyMatch(s->s.nombre().equals("Nodos Pago")));
            assertTrue(dr.secciones().stream().anyMatch(s->s.nombre().equals("Documentos pagados por este CFDI")));
        }
    }
}
