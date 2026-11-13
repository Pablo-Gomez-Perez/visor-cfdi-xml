package com.kathsoft.visor.app.service.clasificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal;
import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal.Sentido;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.clasificacion.ClasificadorDocumentoFiscal.Categoria;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Verifica filtros y RBAC RF-009 contra MySQL 8 desechable, sin tocar la base
 * de desarrollo ni instalar esquemas ajenos al SQL canónico.
 */
@Testcontainers(disabledWithoutDocker = true)
class ClasificacionCfdiServiceIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("visor_cfdi")
            .withUsername("visor_rf009_it")
            .withPassword("clave_rf009_integracion")
            .withCopyFileToContainer(MountableFile.forHostPath(buscarEsquema()),
                    "/docker-entrypoint-initdb.d/01-visor-schema.sql");

    private static Path buscarEsquema() {
        Path base = Path.of(System.getProperty("user.dir"));
        Path directo = base.resolve("database/schema/visor_cfdi_mysql8_schema_v1.sql");
        Path anidado = base.resolve("app/database/schema/visor_cfdi_mysql8_schema_v1.sql");
        Path encontrado = Files.isRegularFile(directo) ? directo : anidado;
        if (!Files.isRegularFile(encontrado)) {
            throw new IllegalStateException("Falta el esquema SQL canónico");
        }
        return encontrado.toAbsolutePath();
    }

    @Test
    void clasificaPersistenciaRealRestringeAccesoYConservaClases() {
        ConfiguracionSistema config = new ConfiguracionSistema(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getDatabaseName(),
                MYSQL.getUsername(), Path.of("xml"));
        try (JpaContext jpa = new JpaContext(config, MYSQL.getPassword().toCharArray())) {
            UsuariosService usuarios = new UsuariosService(jpa);
            usuarios.inicializarRoles();
            UsuarioSesion admin = usuarios.registrarAdministradorInicial(
                    new SolicitudUsuario("Administración RF-009", "adminrf009",
                            null, "ADMIN"),
                    "ClaveSegura#Rf009-2026".toCharArray());
            assertTrue(usuarios.permisosDe(admin.id())
                    .contains(Permisos.CFDI_CONSULTAR_CLASIFICACION));
            Long principal = usuarios.contribuyentes().registrar(admin.id(),
                    new SolicitudContribuyente("ABC010101AB1",
                            "Titular principal", null, null));
            Long segundo = usuarios.contribuyentes().registrar(admin.id(),
                    new SolicitudContribuyente("BBB010101BB1",
                            "Segundo titular", null, null));
            usuarios.registrar(admin.id(),
                    new SolicitudUsuario("Usuario sin privilegios", "consultarf009",
                            null, "CONSULTA"),
                    "ClaveSegura#Consulta-2026".toCharArray());
            Long consultaId = jpa.consultar(em -> ((Number) em.createNativeQuery(
                    "SELECT id FROM usuario WHERE login='consultarf009'")
                    .getSingleResult()).longValue());

            jpa.enTransaccion(em -> {
                String[] tipos = {"I", "E", "T", "N", "P"};
                for (int i = 0; i < 8; i++) {
                    long id = 910_001 + i;
                    String uuid = "123e4567-e89b-12d3-a456-" + String.format("%012d", i + 1);
                    boolean retencion = i == 5;
                    em.createNativeQuery(
                            "INSERT INTO documento_fiscal "
                            + "(id,clase,uuid,version_estandar,fecha_emision) "
                            + "VALUES (:id,:clase,:uuid,:version,:fecha)")
                            .setParameter("id", id)
                            .setParameter("clase", retencion ? "RETENCION" : "CFDI")
                            .setParameter("uuid", uuid)
                            .setParameter("version", retencion ? "2.0" : "4.0")
                            .setParameter("fecha", Timestamp.valueOf("2026-11-12 10:15:00"))
                            .executeUpdate();
                    if (i < 5 || i == 7) {
                        em.createNativeQuery(
                                "INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total) "
                                + "VALUES (:id,:tipo,'MXN',100.00,116.00)")
                                .setParameter("id", id)
                                .setParameter("tipo", i == 7 ? "I" : tipos[i])
                                .executeUpdate();
                    }
                    if (retencion) {
                        em.createNativeQuery(
                                "INSERT INTO retencion(documento_id,clave_retencion) "
                                + "VALUES (:id,'01')")
                                .setParameter("id", id).executeUpdate();
                    }
                    // El esquema exige participantes previos y RFC coincidente
                    // con el sentido EMITIDO o RECIBIDO del titular.
                    String titularRfc = i == 7 ? "BBB010101BB1" : "ABC010101AB1";
                    boolean recibido = i == 4 || i == 5;
                    em.createNativeQuery(
                            "INSERT INTO documento_participante(documento_id,rol,rfc) "
                            + "VALUES (:id,'E',:emisor),(:id,'R',:receptor)")
                            .setParameter("id", id)
                            .setParameter("emisor", recibido ? "ZZZ010101ZZ1" : titularRfc)
                            .setParameter("receptor", recibido ? titularRfc : "ZZZ010101ZZ1")
                            .executeUpdate();
                    em.createNativeQuery(
                            "INSERT INTO contribuyente_documento"
                            + "(contribuyente_id,documento_id,sentido) "
                            + "VALUES (:titular,:id,:sentido)")
                            .setParameter("titular", i == 7 ? segundo : principal)
                            .setParameter("id", id)
                            .setParameter("sentido",
                                    i == 4 || i == 5 ? "RECIBIDO" : "EMITIDO")
                            .executeUpdate();
                }
                return null;
            });

            ClasificacionCfdiService clasificaciones = usuarios.clasificaciones();
            assertEquals(2, clasificaciones.contribuyentes(admin.id()).size());
            FiltroClasificacionFiscal todos = new FiltroClasificacionFiscal(
                    Categoria.TODAS, Sentido.TODOS, "");
            var pagina = clasificaciones.consultar(admin.id(), principal, todos, 0);
            assertEquals(7, pagina.documentos().size());
            assertFalse(pagina.tieneSiguiente());
            assertTrue(pagina.documentos().stream()
                    .anyMatch(d -> d.categoria() == Categoria.INGRESO));
            assertTrue(pagina.documentos().stream()
                    .anyMatch(d -> d.categoria() == Categoria.RETENCION));
            assertTrue(pagina.documentos().stream()
                    .anyMatch(d -> d.categoria() == Categoria.INCONSISTENTE));

            for (Categoria tipo : List.of(Categoria.INGRESO, Categoria.EGRESO,
                    Categoria.TRASLADO, Categoria.NOMINA, Categoria.PAGO,
                    Categoria.RETENCION, Categoria.INCONSISTENTE)) {
                var resultado = clasificaciones.consultar(admin.id(), principal,
                        new FiltroClasificacionFiscal(tipo, Sentido.TODOS, ""), 0);
                assertEquals(1, resultado.documentos().size(), "Categoría " + tipo);
                assertEquals(tipo, resultado.documentos().get(0).categoria());
            }
            assertEquals(2, clasificaciones.consultar(admin.id(), principal,
                    new FiltroClasificacionFiscal(Categoria.TODAS,
                            Sentido.RECIBIDO, ""), 0).documentos().size());
            assertEquals(1, clasificaciones.consultar(admin.id(), principal,
                    new FiltroClasificacionFiscal(Categoria.TODAS,
                            Sentido.TODOS, "123e4567-e89b-12d3-a456-000000000006"), 0)
                    .documentos().size());
            assertEquals(1, clasificaciones.consultar(admin.id(), segundo, todos, 0)
                    .documentos().size());
            assertThrows(SecurityException.class, () -> clasificaciones.consultar(
                    admin.id(), 999_999L, todos, 0));
            assertThrows(SecurityException.class, () -> clasificaciones.consultar(
                    consultaId, principal, todos, 0));
            assertThrows(SecurityException.class,
                    () -> clasificaciones.contribuyentes(consultaId));
        }
    }
}
