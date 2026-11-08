package com.kathsoft.visor.app.service.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.model.entity.UsuarioEstado;
import com.kathsoft.visor.app.persistence.JpaContext;

/**
 * Prueba de integración sobre MySQL 8 real. Ejecuta el esquema original v1
 * del repositorio dentro de un contenedor desechable (sin tocar la BD local).
 */
@Testcontainers(disabledWithoutDocker = true)
class UsuariosServiceIT {

    private static final Path ESQUEMA = buscarEsquema();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("visor_cfdi")
            .withUsername("visor_it")
            .withPassword("clave_integracion")
            .withCopyFileToContainer(MountableFile.forHostPath(ESQUEMA),
                    "/docker-entrypoint-initdb.d/01-visor-schema.sql");

    private static Path buscarEsquema() {
        Path base = Path.of(System.getProperty("user.dir"));
        Path directo = base.resolve("database/schema/visor_cfdi_mysql8_schema_v1.sql");
        Path anidado = base.resolve("app/database/schema/visor_cfdi_mysql8_schema_v1.sql");
        Path encontrado = Files.exists(directo) ? directo : anidado;
        if (!Files.isRegularFile(encontrado)) {
            throw new IllegalStateException("No se encontró el script de esquema del proyecto: " + encontrado);
        }
        return encontrado.toAbsolutePath();
    }

    @Test
    void administraUsuariosEnElEsquemaRealConTransacciones() {
        ConfiguracionSistema config = new ConfiguracionSistema(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getDatabaseName(),
                MYSQL.getUsername(), Path.of("xml"));
        char[] dbClave = MYSQL.getPassword().toCharArray();
        try (JpaContext jpa = new JpaContext(config, dbClave)) {
            Arrays.fill(dbClave, '\0');
            UsuariosService usuarios = new UsuariosService(jpa);
            usuarios.inicializarRoles();
            usuarios.inicializarRoles(); // idempotencia
            assertTrue(usuarios.requiereAdministrador());
            char[] claveAdmin = "administradorSeguro#2026".toCharArray();
            UsuarioSesion admin = usuarios.registrarAdministradorInicial(
                    new SolicitudUsuario("Administrador de prueba", "Admin01", null, "ADMIN"), claveAdmin);
            assertFalse(usuarios.requiereAdministrador());
            assertEquals("admin01", admin.login());
            assertTrue(admin.esAdministrador());

            assertThrows(IllegalStateException.class, () ->
                    usuarios.registrarAdministradorInicial(
                            new SolicitudUsuario("Duplicado", "admin02", null, "ADMIN"),
                            "administradorSeguro#2026".toCharArray()));

            UsuarioSesion acceso = usuarios.autenticar("ADMIN01", claveAdmin);
            assertEquals(admin.id(), acceso.id());
            assertThrows(SecurityException.class, () ->
                    usuarios.autenticar("admin01", "clave_incorrecta".toCharArray()));

            assertThrows(IllegalStateException.class, () ->
                    usuarios.cambiarEstado(admin.id(), admin.id(), UsuarioEstado.INACTIVO));
            assertThrows(IllegalStateException.class, () ->
                    usuarios.cambiarRol(admin.id(), admin.id(), "CONSULTA"));

            usuarios.registrar(admin.id(),
                    new SolicitudUsuario("Auxiliar", "auxiliar1", "auxiliar@ejemplo.test", "CONSULTA"),
                    "contraDeAuxiliar#2026".toCharArray());
            assertEquals(2, usuarios.listar(admin.id()).size());
            long auxiliarId = usuarios.listar(admin.id()).stream()
                    .filter(u -> u.login().equals("auxiliar1")).findFirst().orElseThrow().id();

            assertThrows(SecurityException.class, () -> usuarios.listar(auxiliarId));
            assertThrows(SecurityException.class, () -> usuarios.registrar(auxiliarId,
                    new SolicitudUsuario("Invalido", "invalido", null, "CONSULTA"),
                    "contraDeAuxiliar#2026".toCharArray()));

            // RF-002: el login es modificable, se normaliza y mantiene su PK.
            usuarios.modificarDatos(admin.id(), auxiliarId, "Auxiliar editado",
                    "  AUXILIAR.RENOMBRADO  ", "nuevo@ejemplo.test");
            var modificado = usuarios.listar(admin.id()).stream()
                    .filter(u -> u.id().equals(auxiliarId)).findFirst().orElseThrow();
            assertEquals("auxiliar.renombrado", modificado.login());
            assertEquals("Auxiliar editado", modificado.nombre());
            assertEquals("nuevo@ejemplo.test", modificado.correo());
            assertThrows(SecurityException.class, () ->
                    usuarios.autenticar("auxiliar1", "contraDeAuxiliar#2026".toCharArray()));
            assertEquals(auxiliarId, usuarios.autenticar(
                    "AUXILIAR.RENOMBRADO", "contraDeAuxiliar#2026".toCharArray()).id());

            // Un login duplicado se rechaza sin modificar nombre ni correo.
            assertThrows(IllegalArgumentException.class, () ->
                    usuarios.modificarDatos(admin.id(), auxiliarId, "Cambio indebido",
                            "ADMIN01", "duplicado@ejemplo.test"));
            assertThrows(IllegalArgumentException.class, () ->
                    usuarios.modificarDatos(admin.id(), auxiliarId, "Cambio indebido",
                            "usuario con espacios", "incorrecto@ejemplo.test"));
            assertThrows(IllegalArgumentException.class, () ->
                    usuarios.modificarDatos(admin.id(), auxiliarId, "Cambio indebido",
                            "otro.login", "correo-invalido"));
            var conservado = usuarios.listar(admin.id()).stream()
                    .filter(u -> u.id().equals(auxiliarId)).findFirst().orElseThrow();
            assertEquals("auxiliar.renombrado", conservado.login());
            assertEquals("Auxiliar editado", conservado.nombre());
            assertEquals("nuevo@ejemplo.test", conservado.correo());

            // Un usuario CONSULTA tampoco puede modificar el login de un ADMIN.
            assertThrows(SecurityException.class, () ->
                    usuarios.modificarDatos(auxiliarId, admin.id(),
                            "Admin", "admin.cambiado", null));

            // Editar con el mismo login no debe interpretarse como duplicado.
            usuarios.modificarDatos(admin.id(), auxiliarId, "Auxiliar editado",
                    "AUXILIAR.RENOMBRADO", "nuevo@ejemplo.test");

            String auditoria = jpa.consultar(em -> (String) em.createNativeQuery(
                    "SELECT motivo FROM bitacora_evento "
                    + "WHERE entidad = 'usuario' AND entidad_id = :id "
                    + "AND accion = 'MODIFICAR' AND motivo LIKE 'Login modificado%' "
                    + "ORDER BY id DESC LIMIT 1")
                    .setParameter("id", auxiliarId).getSingleResult());
            assertTrue(auditoria.contains("auxiliar1"));
            assertTrue(auditoria.contains("auxiliar.renombrado"));

            usuarios.cambiarContrasena(admin.id(), auxiliarId, "otraClaveSegura#2026".toCharArray());
            assertThrows(SecurityException.class, () ->
                    usuarios.autenticar("auxiliar.renombrado", "contraDeAuxiliar#2026".toCharArray()));
            assertEquals(auxiliarId,
                    usuarios.autenticar("auxiliar.renombrado", "otraClaveSegura#2026".toCharArray()).id());

            usuarios.cambiarRol(admin.id(), auxiliarId, "ADMIN");
            usuarios.cambiarEstado(admin.id(), admin.id(), UsuarioEstado.INACTIVO);
            assertThrows(SecurityException.class, () -> usuarios.listar(admin.id()));
            assertEquals(2, usuarios.listar(auxiliarId).size());
            assertThrows(IllegalStateException.class, () ->
                    usuarios.cambiarEstado(auxiliarId, auxiliarId, UsuarioEstado.INACTIVO));

            long eventos = jpa.consultar(em -> ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM bitacora_evento WHERE entidad = 'usuario'")
                    .getSingleResult()).longValue());
            assertTrue(eventos >= 6);
        } finally {
            Arrays.fill(dbClave, '\0');
        }
    }
}
