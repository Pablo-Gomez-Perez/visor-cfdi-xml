package com.kathsoft.visor.app.service.fiscal;

import static org.junit.jupiter.api.Assertions.*;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.fiscal.ClasificacionDetalleService.Asignacion;
import com.kathsoft.visor.app.service.fiscal.ClasificacionDetalleService.Tipo;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Categoria;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Seleccion;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** Distribuciones mixtas, aislamiento e historial sobre el esquema original MySQL 8. */
@Testcontainers
class ClasificacionDetalleServiceIT {
    private static final long DOCUMENTO = 981001;
    private static final long MATERIA = 9810011;
    private static final long PAPELERIA = 9810012;
    private static final long EQUIPO = 9810013;
    private static final long IVA = 98100110;
    @TempDir Path temporal;

    private static Path esquema() {
        Path p = Path.of("database/schema/visor_cfdi_mysql8_schema_v1.sql");
        return (Files.isRegularFile(p) ? p : Path.of("app").resolve(p)).toAbsolutePath();
    }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("visor_cfdi").withUsername("visor_it").withPassword("Prueba#Rf021")
            .withCopyFileToContainer(MountableFile.forHostPath(esquema()), "/docker-entrypoint-initdb.d/01-schema.sql");

    private static void ejecutar(EntityManager em, String sql, Object... args) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < args.length; i++) q.setParameter(i + 1, args[i]);
        q.executeUpdate();
    }

    private static long numero(EntityManager em, String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private static void igual(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual));
    }

    private static Asignacion asignar(long destino, String importe) {
        return new Asignacion(destino, new BigDecimal(importe), "Clasificación sintética documentada");
    }

    private static long categoria(List<Categoria> categorias, String clave) {
        return categorias.stream().filter(c -> c.clave().equals(clave)).findFirst().orElseThrow().id();
    }

    private static void concepto(EntityManager em, long id, int ordinal, String descripcion, int importe) {
        ejecutar(em, """
            INSERT INTO cfdi_concepto(id,documento_id,ordinal,clave_prod_serv,cantidad,clave_unidad,
                descripcion,valor_unitario,importe,objeto_impuesto)
            VALUES (?1,?2,?3,'01010101',1,'H87',?4,?5,?5,'02')
            """, id, DOCUMENTO, ordinal, descripcion, importe);
    }

    @Test void clasificaOperacionMixtaConHistoriaLimitesPermisosYConcurrencia() throws Exception {
        var config = new ConfiguracionSistema(MYSQL.getHost(), MYSQL.getMappedPort(3306),
                MYSQL.getDatabaseName(), MYSQL.getUsername(), temporal.resolve("repo"));
        try (var jpa = new JpaContext(config, MYSQL.getPassword().toCharArray())) {
            var usuarios = new UsuariosService(jpa);
            usuarios.inicializarRoles();
            long actor = usuarios.registrarAdministradorInicial(
                    new SolicitudUsuario("Admin RF021", "admin21", null, "ADMIN"),
                    "ClaveSegura#Rf021-2026".toCharArray()).id();
            assertTrue(usuarios.permisosDe(actor).contains(Permisos.OPERACIONES_CLASIFICAR_DETALLE));
            long a = usuarios.contribuyentes().registrar(actor,
                    new SolicitudContribuyente("BBB010101BBB", "Titular receptor sintético", null, null));
            long b = usuarios.contribuyentes().registrar(actor,
                    new SolicitudContribuyente("AAA010101AAA", "Titular emisor sintético", null, null));
            usuarios.registrar(actor, new SolicitudUsuario("Lector RF021", "lector21", null, "CONSULTA"),
                    "ClaveSegura#Lector21-2026".toCharArray());
            long lector = jpa.consultar(em -> numero(em, "SELECT id FROM usuario WHERE login='lector21'"));
            jpa.enTransaccion(em -> {
                ejecutar(em, """
                    INSERT INTO documento_fiscal(id,clase,uuid,version_estandar,fecha_emision)
                    VALUES (?1,'CFDI','123e4567-e89b-12d3-a456-000000981001','4.0','2026-06-10 12:00:00')
                    """, DOCUMENTO);
                ejecutar(em, "INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total) VALUES (?1,'I','MXN',27000,31320)", DOCUMENTO);
                ejecutar(em, "INSERT INTO documento_participante(documento_id,rol,rfc) VALUES (?1,'E','AAA010101AAA'),(?1,'R','BBB010101BBB')", DOCUMENTO);
                ejecutar(em, "INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido) VALUES (?1,?3,'RECIBIDO'),(?2,?3,'EMITIDO')", a, b, DOCUMENTO);
                concepto(em, MATERIA, 1, "Materia prima sintética", 10000);
                concepto(em, PAPELERIA, 2, "Papelería sintética", 2000);
                concepto(em, EQUIPO, 3, "Equipo de cómputo sintético", 15000);
                ejecutar(em, """
                    INSERT INTO cfdi_concepto_impuesto(id,concepto_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
                    VALUES (?1,?2,'TRASLADO',1,'002','Tasa',10000,0.16,1600),
                           (?1+1,?3,'TRASLADO',1,'002','Tasa',2000,0.16,320),
                           (?1+2,?4,'TRASLADO',1,'002','Tasa',15000,0.16,2400)
                    """, IVA, MATERIA, PAPELERIA, EQUIPO);
                ejecutar(em, "INSERT INTO usuario_contribuyente(usuario_id,contribuyente_id) VALUES (?1,?2)", lector, a);
                return null;
            });
            var general = usuarios.operaciones();
            general.crearPredeterminadas(actor, a);
            general.crearPredeterminadas(actor, b);
            var categorias = general.categorias(actor, a);
            long costo = categoria(categorias, "COSTO"), gasto = categoria(categorias, "GASTO"), inversion = categoria(categorias, "INVERSION");
            long ajena = general.categorias(actor, b).get(0).id();
            general.clasificar(actor, a, List.of(new Seleccion(DOCUMENTO, 0)), gasto, "Clasificación general de referencia");
            var servicio = new ClasificacionDetalleService(jpa);
            assertEquals(3, servicio.conceptos(actor, a, DOCUMENTO, 0).size());
            assertTrue(servicio.conceptos(actor, a, DOCUMENTO, 1).isEmpty());
            assertEquals("Gasto", servicio.conceptos(actor, a, DOCUMENTO, 0).get(0).categoriaGeneral());
            assertEquals(1, servicio.impuestos(actor, a, DOCUMENTO, MATERIA).size());
            long revision = servicio.guardar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA, 0,
                    List.of(asignar(costo, "10000")), "");
            revision = servicio.guardar(actor, a, DOCUMENTO, Tipo.CONCEPTO, PAPELERIA, revision,
                    List.of(asignar(gasto, "2000")), "Papelería como gasto");
            revision = servicio.guardar(actor, a, DOCUMENTO, Tipo.CONCEPTO, EQUIPO, revision,
                    List.of(asignar(inversion, "15000")), "Equipo como inversión");
            igual("0", servicio.consultar(actor, a, DOCUMENTO, Tipo.CONCEPTO, EQUIPO).pendiente());
            var tratamientos = servicio.tratamientos(actor, a);
            long acreditable = tratamientos.stream().filter(t -> t.clave().equals("IVA_ACREDITABLE")).findFirst().orElseThrow().id();
            long noAcreditable = tratamientos.stream().filter(t -> t.clave().equals("IVA_NO_ACREDITABLE")).findFirst().orElseThrow().id();
            var distribucionImpuesto = List.of(asignar(acreditable, "800"), asignar(noAcreditable, "400"))
                    .stream().sorted(Comparator.comparingLong(Asignacion::destino)).toList();
            revision = servicio.guardar(actor, a, DOCUMENTO, Tipo.IMPUESTO, IVA, revision,
                    distribucionImpuesto, "Tratamiento parcial respaldado para prueba");
            igual("400", servicio.consultar(actor, a, DOCUMENTO, Tipo.IMPUESTO, IVA).pendiente());
            assertEquals(4, servicio.historial(actor, a, DOCUMENTO, 0).size());
            assertEquals(revision, servicio.guardar(actor, a, DOCUMENTO, Tipo.IMPUESTO, IVA, revision,
                    distribucionImpuesto, "Sin cambios"));
            assertEquals(4, servicio.historial(actor, a, DOCUMENTO, 0).size());
            long actual = revision;
            assertThrows(IllegalStateException.class, () -> servicio.guardar(actor, a, DOCUMENTO,
                    Tipo.CONCEPTO, MATERIA, 0, List.of(asignar(gasto, "10000")), "Revisión obsoleta"));
            assertThrows(IllegalArgumentException.class, () -> servicio.guardar(actor, a, DOCUMENTO,
                    Tipo.CONCEPTO, MATERIA, actual, List.of(asignar(costo, "10000.000001")), "Exceso"));
            assertThrows(IllegalArgumentException.class, () -> servicio.guardar(actor, a, DOCUMENTO,
                    Tipo.CONCEPTO, MATERIA, actual, List.of(asignar(ajena, "10000")), "Categoría ajena"));
            general.cambiarEstadoCategoria(actor, a, inversion, false);
            assertThrows(IllegalArgumentException.class, () -> servicio.guardar(actor, a, DOCUMENTO,
                    Tipo.CONCEPTO, MATERIA, actual, List.of(asignar(inversion, "10000")), "Categoría inactiva"));
            assertEquals(actual, servicio.consultar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA).revision());
            assertEquals(costo, servicio.consultar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA).asignaciones().get(0).destino());
            assertTrue(servicio.consultar(actor, b, DOCUMENTO, Tipo.CONCEPTO, MATERIA).asignaciones().isEmpty());
            assertThrows(SecurityException.class, () -> servicio.consultar(lector, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA));
            jpa.enTransaccion(em -> {
                ejecutar(em, """
                    INSERT INTO rol_permiso(rol_id,permiso_id)
                    SELECT r.id,p.id FROM rol r CROSS JOIN permiso p
                    WHERE r.clave='CONSULTA' AND p.recurso='OPERACIONES' AND p.accion='CONSULTAR'
                    """);
                return null;
            });
            assertEquals(actual, servicio.consultar(lector, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA).revision());
            assertThrows(SecurityException.class, () -> servicio.consultar(lector, b, DOCUMENTO, Tipo.CONCEPTO, MATERIA));
            assertThrows(SecurityException.class, () -> servicio.guardar(lector, a, DOCUMENTO,
                    Tipo.CONCEPTO, MATERIA, actual, List.of(asignar(gasto, "10000")), "Sin privilegio de escritura"));
            revision = servicio.guardar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA, actual,
                    List.of(asignar(costo, "6000")), "Distribución parcial");
            igual("4000", servicio.consultar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA).pendiente());

            // Dos operadores parten de la misma revisión: exactamente uno puede confirmarla.
            long concurrente = revision;
            var inicio = new CountDownLatch(1);
            var preparados = new CountDownLatch(2);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var tareas = List.of("5000", "4000").stream().map(importe -> executor.submit(() -> {
                    preparados.countDown();
                    assertTrue(inicio.await(10, TimeUnit.SECONDS));
                    try {
                        servicio.guardar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA, concurrente,
                                List.of(asignar(costo, importe)), "Edición concurrente controlada");
                        return true;
                    } catch (IllegalStateException esperada) {
                        assertTrue(esperada.getMessage().contains("clasificación cambió"));
                        return false;
                    }
                })).toList();
                assertTrue(preparados.await(10, TimeUnit.SECONDS));
                inicio.countDown();
                int confirmadas = 0;
                for (var tarea : tareas) if (tarea.get(20, TimeUnit.SECONDS)) confirmadas++;
                assertEquals(1, confirmadas, "No deben sobrescribirse dos ediciones de la misma revisión");
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
            revision = servicio.consultar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA).revision();
            long antesRetiro = revision;
            assertThrows(IllegalArgumentException.class, () -> servicio.guardar(actor, a, DOCUMENTO,
                    Tipo.CONCEPTO, MATERIA, antesRetiro, List.of(), ""));
            revision = servicio.guardar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA, revision,
                    List.of(), "Retiro documentado de la distribución");
            assertTrue(servicio.consultar(actor, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA).asignaciones().isEmpty());
            assertEquals(7, servicio.historial(actor, a, DOCUMENTO, 0).size());
            var retiro = servicio.historial(actor, a, DOCUMENTO, 0).get(0);
            assertEquals("Retiro documentado de la distribución", retiro[4]);
            assertTrue(retiro[6].toString().contains("importe"));
            assertEquals("[]", retiro[7].toString());
            jpa.consultar(em -> {
                igual("27000", (BigDecimal) em.createNativeQuery("SELECT SUM(importe) FROM cfdi_concepto WHERE documento_id=981001").getSingleResult());
                igual("4320", (BigDecimal) em.createNativeQuery("SELECT SUM(i.importe) FROM cfdi_concepto_impuesto i JOIN cfdi_concepto c ON c.id=i.concepto_id WHERE c.documento_id=981001").getSingleResult());
                igual("10000", (BigDecimal) em.createNativeQuery("SELECT base FROM cfdi_concepto_impuesto WHERE id=98100110").getSingleResult());
                return null;
            });
            jpa.enTransaccion(em -> {
                ejecutar(em, "DELETE rp FROM rol_permiso rp JOIN rol r ON r.id=rp.rol_id JOIN permiso p ON p.id=rp.permiso_id WHERE r.clave='CONSULTA' AND p.recurso='OPERACIONES' AND p.accion='CONSULTAR'");
                return null;
            });
            usuarios.inicializarRoles();
            assertThrows(SecurityException.class, () -> servicio.consultar(lector, a, DOCUMENTO, Tipo.CONCEPTO, MATERIA));
            jpa.enTransaccion(em -> {
                ejecutar(em, "UPDATE contribuyente_documento SET archivado_en=CURRENT_TIMESTAMP(6) WHERE contribuyente_id=?1 AND documento_id=?2", a, DOCUMENTO);
                return null;
            });
            long archivada = revision;
            assertThrows(SecurityException.class, () -> servicio.guardar(actor, a, DOCUMENTO,
                    Tipo.CONCEPTO, MATERIA, archivada, List.of(asignar(costo, "10000")), "Documento archivado"));
            assertTrue(servicio.consultar(actor, b, DOCUMENTO, Tipo.CONCEPTO, MATERIA).asignaciones().isEmpty());
        }
    }
}
