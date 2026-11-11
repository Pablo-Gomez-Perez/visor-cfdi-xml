package com.kathsoft.visor.app.service.periodo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.FiltroPeriodoFiscal;
import com.kathsoft.visor.app.model.dto.FiltroPeriodoFiscal.TipoFechaFiscal;
import com.kathsoft.visor.app.model.dto.MovimientoPeriodoFiscal;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Reproduce el mapeo de CHAR(1) con Hibernate + MySQL 8 reales.
 * Usa un contenedor desechable y nunca la base del usuario.
 */
@Testcontainers(disabledWithoutDocker = true)
class PeriodosFiscalesServiceIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("visor_cfdi")
            .withUsername("visor_rf006_it")
            .withPassword("clave_rf006_integracion")
            .withCopyFileToContainer(MountableFile.forHostPath(buscarEsquema()),
                    "/docker-entrypoint-initdb.d/01-visor-schema.sql");

    private static Path buscarEsquema() {
        Path base = Path.of(System.getProperty("user.dir"));
        Path directo = base.resolve("database/schema/visor_cfdi_mysql8_schema_v1.sql");
        Path anidado = base.resolve("app/database/schema/visor_cfdi_mysql8_schema_v1.sql");
        Path encontrado = Files.isRegularFile(directo) ? directo : anidado;
        if (!Files.isRegularFile(encontrado)) {
            throw new IllegalStateException("No existe el esquema SQL del repositorio");
        }
        return encontrado.toAbsolutePath();
    }

    @Test
    void consultaCfdiTipoCharSinErrorDeConversion() {
        ConfiguracionSistema config = new ConfiguracionSistema(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getDatabaseName(),
                MYSQL.getUsername(), Path.of("xml"));
        try (JpaContext jpa = new JpaContext(config, MYSQL.getPassword().toCharArray())) {
            UsuariosService usuarios = new UsuariosService(jpa);
            usuarios.inicializarRoles();
            UsuarioSesion admin = usuarios.registrarAdministradorInicial(
                    new SolicitudUsuario("Administración RF-006", "adminrf006",
                            null, "ADMIN"),
                    "ClaveSegura#Rf006-2026".toCharArray());
            Long contribuyenteId = usuarios.contribuyentes().registrar(admin.id(),
                    new SolicitudContribuyente("ABC010101AB1",
                            "Contribuyente de prueba", null, null));

            jpa.enTransaccion(em -> {
                em.createNativeQuery(
                        "INSERT INTO documento_fiscal(id,clase,uuid,version_estandar,"
                        + "fecha_emision) VALUES (900001,'CFDI',"
                        + "'123e4567-e89b-12d3-a456-426614174000',"
                        + "'4.0','2026-11-11 10:15:00')").executeUpdate();
                em.createNativeQuery(
                        "INSERT INTO documento_participante(documento_id,rol,rfc) "
                        + "VALUES (900001,'E','ABC010101AB1'),"
                        + "(900001,'R','BBB010101BB1')").executeUpdate();
                em.createNativeQuery(
                        "INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total) "
                        + "VALUES (900001,'I','MXN',100.000000,116.000000)")
                        .executeUpdate();
                em.createNativeQuery(
                        "INSERT INTO contribuyente_documento("
                        + "contribuyente_id,documento_id,sentido) "
                        + "VALUES (?1,900001,'EMITIDO')")
                        .setParameter(1, contribuyenteId).executeUpdate();
                return null;
            });

            List<MovimientoPeriodoFiscal> encontrados = usuarios.periodosFiscales()
                    .consultar(admin.id(), contribuyenteId,
                            FiltroPeriodoFiscal.mes(2026, 11, TipoFechaFiscal.EMISION), 0);
            assertEquals(1, encontrados.size());
            assertEquals("I", encontrados.get(0).tipoCfdi());
            assertEquals("EMITIDO", encontrados.get(0).sentido());
        }
    }
}
