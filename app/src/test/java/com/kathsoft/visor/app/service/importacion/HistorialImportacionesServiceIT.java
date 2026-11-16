package com.kathsoft.visor.app.service.importacion;

import static org.junit.jupiter.api.Assertions.*;
import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Rango;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Filtro;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.EstadoLote;
import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal;
import com.kathsoft.visor.app.model.dto.FiltroPeriodoFiscal;
import com.kathsoft.visor.app.service.clasificacion.ClasificadorDocumentoFiscal.Categoria;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
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

/** Pruebas MySQL 8 reales: no omitir Docker ni tocar una BD operativa. */
@Testcontainers
class HistorialImportacionesServiceIT {
    @TempDir Path temporal;

    private static Path db(String ruta) {
        Path base=Path.of(System.getProperty("user.dir"));
        Path directa=base.resolve("database").resolve(ruta);
        return (Files.isRegularFile(directa)?directa:base.resolve("app/database").resolve(ruta)).toAbsolutePath();
    }

    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.46")
        .withDatabaseName("visor_cfdi").withUsername("visor_it").withPassword("Prueba#Rf013")
        .withLogConsumer(frame -> System.err.print(frame.getUtf8String()))
        .withCopyFileToContainer(MountableFile.forHostPath(db("schema/visor_cfdi_mysql8_schema_v1.sql")),
                "/docker-entrypoint-initdb.d/01-schema.sql")
        .withCopyFileToContainer(MountableFile.forHostPath(db("procedures/procedures.sql")),
                "/docker-entrypoint-initdb.d/02-procedures.sql");

    @Test void consultaAislamientoArchivoReversibleYConservacionHistorica() throws Exception {
        Path repo=temporal.resolve("repo");
        var config=new ConfiguracionSistema(MYSQL.getHost(),MYSQL.getMappedPort(3306),
                MYSQL.getDatabaseName(),MYSQL.getUsername(),repo);
        try(JpaContext jpa=new JpaContext(config,MYSQL.getPassword().toCharArray())) {
            var usuarios=new UsuariosService(jpa);
            usuarios.inicializarRoles();
            var admin=usuarios.registrarAdministradorInicial(
                    new SolicitudUsuario("Admin RF013","adminrf013",null,"ADMIN"),
                    "ClaveSegura#Rf013-2026".toCharArray());
            long actor=admin.id();
            assertTrue(usuarios.permisosDe(actor).containsAll(Set.of(
                    Permisos.IMPORTACIONES_CONSULTAR,Permisos.IMPORTACIONES_CONSULTAR_DETALLE,
                    Permisos.IMPORTACIONES_ARCHIVAR_LOTE,Permisos.IMPORTACIONES_ARCHIVAR_CFDI,
                     Permisos.IMPORTACIONES_RESTAURAR_CFDI)));
            long a=usuarios.contribuyentes().registrar(actor,
                    new SolicitudContribuyente("BBB010101BBB","Titular A",null,null));
            long b=usuarios.contribuyentes().registrar(actor,
                    new SolicitudContribuyente("AAA010101AAA","Titular B",null,null));
            usuarios.registrar(actor,new SolicitudUsuario("Consulta RF013","lector13",null,"CONSULTA"),
                    "ClaveSegura#Lector-2026".toCharArray());
            long lector=jpa.<Long>consultar(em->numero(em,"SELECT id FROM usuario WHERE login='lector13'"));

            var importador=new ImportacionCfdiService(jpa,repo);
            var historial=usuarios.historialImportaciones();
            Path exclusivo=xml("exclusivo.xml",1,EjemplosPagosHistoricos.PAGO10_IMPUESTOS,"3.3");
            Path compartido=xml("compartido.xml",2,EjemplosPagosHistoricos.PAGO20,"4.0");
            Path rechazo=temporal.resolve("rechazado.xml");
            Files.writeString(rechazo,"<incompleto>");
            Path conDependencia=xml("dependencia.xml",3,EjemplosPagosHistoricos.PAGO10,"3.3");

            long lote=importar(importador,actor,a,List.of(exclusivo,compartido,rechazo,conDependencia));
            long otro=importar(importador,actor,b,List.of(compartido));
            long repetido=importar(importador,actor,a,List.of(compartido));
            long abierto=jpa.enTransaccion(em->{
                em.createNativeQuery("INSERT INTO lote_importacion(contribuyente_id,usuario_id,inicio,estado) "
                        +"VALUES (?1,?2,'2026-11-15 10:00:00','EN_PROCESO')")
                        .setParameter(1,a).setParameter(2,actor).executeUpdate();
                return numero(em,"SELECT LAST_INSERT_ID()");
            });
            jpa.enTransaccion(em->{
                em.createNativeQuery("UPDATE lote_importacion SET inicio='2026-11-15 23:59:59.999999',"
                        +"fin='2026-11-16 00:00:01' WHERE id=?1").setParameter(1,lote).executeUpdate();
                em.createNativeQuery("UPDATE lote_importacion SET inicio='2026-11-16 00:00:00',"
                        +"fin='2026-11-16 00:00:01' WHERE id IN (?1,?2)")
                        .setParameter(1,otro).setParameter(2,repetido).executeUpdate();
                em.createNativeQuery("INSERT INTO usuario_contribuyente(usuario_id,contribuyente_id) "
                        +"VALUES (?1,?2)").setParameter(1,lector).setParameter(2,b).executeUpdate();
                return null;
            });
            var rango=new Rango(LocalDate.of(2026,11,15),LocalDate.of(2026,11,15));
            var ambosDias=new Rango(rango.desde(),rango.hasta().plusDays(1));
            assertEquals(2,historial.listar(actor,rango,0).filas().size());
            var resumen=historial.listar(actor,rango,0).filas().get(0);
            assertEquals(lote,resumen.id());
            assertEquals(3,resumen.aceptados());
            assertEquals(1,resumen.rechazados());
            assertEquals(2,historial.listar(actor,new Rango(rango.hasta().plusDays(1),rango.hasta().plusDays(1)),0).filas().size());
            var detalles=historial.archivos(actor,lote,0).filas();
            assertEquals(4,detalles.size());
            assertEquals("ACEPTADO",detalles.get(0).resultado());
            assertNotNull(detalles.get(0).uuid());
            assertEquals(64,detalles.get(0).sha256().length());
            assertEquals("RECHAZADO",detalles.get(2).resultado());
            assertNotNull(detalles.get(2).detalle());
            assertNull(detalles.get(2).documentoId());
            assertThrows(SecurityException.class,()->historial.listar(lector,rango,0));
            conceder(jpa,"CONSULTAR");
            assertEquals(1,historial.listar(lector,ambosDias,0).filas().size());
            assertThrows(SecurityException.class,()->historial.archivos(lector,otro,0));
            conceder(jpa,"CONSULTAR_DETALLE");
            assertEquals(1,historial.archivos(lector,otro,0).filas().size());
            assertThrows(SecurityException.class,()->historial.archivos(lector,lote,0));
            assertThrows(SecurityException.class,()->historial.archivarLote(lector,otro,"Sin privilegio"));
            assertThrows(SecurityException.class,()->historial.archivarArchivo(lector,otro,
                    historial.archivos(lector,otro,0).filas().get(0).id(),"Sin privilegio"));
            conceder(jpa,"ARCHIVAR_LOTE");
            assertThrows(SecurityException.class,()->historial.archivarLote(lector,lote,"Fuera de titular"));
            assertThrows(IllegalStateException.class,()->historial.archivarLote(actor,abierto,"En proceso"));
            assertThrows(IllegalArgumentException.class,()->historial.archivarArchivo(actor,lote,
                    historial.archivos(actor,otro,0).filas().get(0).id(),"Archivo ajeno"));
            assertThrows(IllegalArgumentException.class,()->historial.archivarLote(actor,lote," "));
            assertThrows(IllegalArgumentException.class,()->historial.archivarArchivo(actor,lote,detalles.get(2).id(),"Sin documento"));
            assertEquals(1,historial.opciones(lector).contribuyentes().size());
            assertEquals(b,historial.opciones(lector).contribuyentes().get(0).id());
            assertEquals(1,historial.listar(actor,new Filtro(ambosDias,b,actor,EstadoLote.FINALIZADO),0).filas().size());
            assertEquals(1,historial.listar(actor,new Filtro(ambosDias,a,actor,EstadoLote.PARCIAL),0).filas().size());
            assertTrue(historial.listar(lector,new Filtro(ambosDias,a,actor,EstadoLote.TODOS),0).filas().isEmpty());
            assertTrue(historial.listar(actor,new Filtro(ambosDias,b,lector,EstadoLote.TODOS),0).filas().isEmpty());

            var seleccionado=detalles.get(1);
            long archivoB=historial.archivos(actor,otro,0).filas().get(0).id();
            assertTrue(historial.archivarArchivo(actor,lote,seleccionado.id(),"Archivar compartido"));
            assertFalse(historial.archivarArchivo(actor,lote,seleccionado.id(),"Ya archivado"));
            assertEquals("ARCHIVADO",historial.archivos(actor,lote,0).filas().get(1).estadoOperativo());
            assertEquals("ARCHIVADO",historial.archivos(actor,repetido,0).filas().get(0).estadoOperativo());
            assertEquals("ACTIVO",historial.archivos(actor,otro,0).filas().get(0).estadoOperativo());
            var filtro=new FiltroClasificacionFiscal(Categoria.TODAS,FiltroClasificacionFiscal.Sentido.TODOS,seleccionado.uuid());
            var periodo=FiltroPeriodoFiscal.ejercicio(2020,FiltroPeriodoFiscal.TipoFechaFiscal.EMISION);
            assertEquals(0,usuarios.clasificaciones().consultar(actor,a,filtro,0).documentos().size());
            assertEquals(1,usuarios.clasificaciones().consultar(actor,b,filtro,0).documentos().size());
            assertEquals(2,usuarios.periodosFiscales().consultar(actor,a,periodo,0).size());
            assertEquals(1,usuarios.periodosFiscales().consultar(actor,b,periodo,0).size());
            assertEquals(3,historial.listar(actor,rango,0).filas().get(0).aceptados());
            assertEquals(1,historial.listar(actor,rango,0).filas().get(0).archivados());
            assertThrows(SecurityException.class,()->historial.restaurarArchivo(lector,otro,archivoB,"Sin permiso"));
            assertThrows(SecurityException.class,()->historial.restaurarArchivo(lector,lote,seleccionado.id(),"Fuera de titular"));

            // El mismo XML sigue siendo duplicado y no reactiva un vínculo archivado.
            long nuevo=importar(importador,actor,a,List.of(compartido));
            assertEquals("DUPLICADO",historial.archivos(actor,nuevo,0).filas().get(0).resultado());
            assertEquals("ARCHIVADO",historial.archivos(actor,nuevo,0).filas().get(0).estadoOperativo());
            Path conflicto=temporal.resolve("conflicto.xml");
            Files.writeString(conflicto,Files.readString(compartido)+"\n");
            assertThrows(RuntimeException.class,()->importar(importador,actor,a,List.of(conflicto)));
            assertEquals(3L,jpa.<Long>consultar(em->numero(em,"SELECT COUNT(*) FROM documento_fiscal")));

            // Restauración usa el repositorio, aunque ya no exista el origen externo.
            String sha=seleccionado.sha256().toLowerCase(java.util.Locale.ROOT);
            Path original=repo.resolve("cfdi").resolve(sha.substring(0,2)).resolve(sha+".xml");
            byte[] originalBytes=Files.readAllBytes(original);
            Files.delete(compartido);
            byte[] alterado=originalBytes.clone(); alterado[0]^=1;
            Files.write(original,alterado);
            assertThrows(IllegalStateException.class,()->historial.restaurarArchivo(actor,lote,seleccionado.id(),"Original alterado"));
            assertEquals("ARCHIVADO",historial.archivos(actor,lote,0).filas().get(1).estadoOperativo());
            Files.write(original,originalBytes);
            assertTrue(historial.restaurarArchivo(actor,lote,seleccionado.id(),"Original verificado"));
            assertEquals(1,usuarios.clasificaciones().consultar(actor,a,filtro,0).documentos().size());
            assertFalse(historial.restaurarArchivo(actor,lote,seleccionado.id(),"Ya activo"));

            assertEquals(3,historial.archivarLote(actor,lote,"Archivar todos los CFDI"));
            assertEquals(0,historial.archivarLote(actor,lote,"Operación idempotente"));
            assertEquals(0,usuarios.periodosFiscales().consultar(actor,a,periodo,0).size());
            assertEquals(1,usuarios.periodosFiscales().consultar(actor,b,periodo,0).size());
            assertEquals(4,historial.archivos(actor,lote,0).filas().size());
            assertEquals(3,historial.listar(actor,rango,0).filas().get(0).aceptados());
            assertEquals(3,historial.listar(actor,rango,0).filas().get(0).archivados());
            assertEquals(1,historial.listar(actor,rango,0).filas().get(0).rechazados());
            jpa.consultar(em->{
                assertEquals(3L,numero(em,"SELECT COUNT(*) FROM documento_fiscal"));
                assertEquals(4L,numero(em,"SELECT COUNT(*) FROM contribuyente_documento"));
                assertEquals(3L,numero(em,"SELECT COUNT(*) FROM archivo_documental"));
                assertEquals(4L,numero(em,"SELECT COUNT(*) FROM bitacora_evento WHERE accion='ARCHIVAR'"));
                assertEquals(1L,numero(em,"SELECT COUNT(*) FROM bitacora_evento WHERE accion='RESTAURAR'"));
                return null;
            });
            assertTrue(historial.restaurarArchivo(actor,lote,seleccionado.id(),"Restaurar desde historial"));
            assertEquals(1,usuarios.periodosFiscales().consultar(actor,a,periodo,0).size());
        }
    }

    private Path xml(String nombre,int n,String complemento,String version) throws Exception {
        Path p=temporal.resolve(nombre);
        Files.writeString(p,EjemplosPagosHistoricos.xml(version,
                "123e4567-e89b-12d3-a456-"+String.format("%012d",n),complemento));
        return p;
    }
    private static long importar(ImportacionCfdiService servicio,long actor,long c,List<Path> paths) {
        return servicio.importar(actor,new ImportacionCfdiService.Solicitud(c,paths,false),p->{},()->false).loteId();
    }
    private static long numero(EntityManager em,String sql) {
        return ((Number)em.createNativeQuery(sql).getSingleResult()).longValue();
    }
    private static void conceder(JpaContext jpa,String accion) {
        jpa.enTransaccion(em->{
            em.createNativeQuery("INSERT INTO rol_permiso(rol_id,permiso_id) SELECT r.id,p.id "
                   +"FROM rol r CROSS JOIN permiso p WHERE r.clave='CONSULTA' AND p.recurso='IMPORTACIONES' AND p.accion=?1")
                .setParameter(1,accion).executeUpdate();return null;
        });
    }
}
