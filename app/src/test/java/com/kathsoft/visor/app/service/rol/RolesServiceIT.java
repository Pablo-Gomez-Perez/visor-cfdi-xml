package com.kathsoft.visor.app.service.rol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.MatrizPermisosRol;
import com.kathsoft.visor.app.model.dto.RolListado;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Pruebas de RF-003 contra el esquema real MySQL 8 aislado por Testcontainers.
 */
@Testcontainers(disabledWithoutDocker = true)
class RolesServiceIT {

    private static final Path ESQUEMA = buscarEsquema();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("visor_cfdi")
            .withUsername("visor_rbac_it")
            .withPassword("clave_integracion_rbac")
            .withCopyFileToContainer(MountableFile.forHostPath(ESQUEMA),
                    "/docker-entrypoint-initdb.d/01-visor-schema.sql");

    private static Path buscarEsquema() {
        Path base = Path.of(System.getProperty("user.dir"));
        Path dentroApp = base.resolve("database/schema/visor_cfdi_mysql8_schema_v1.sql");
        Path desdeRaiz = base.resolve("app/database/schema/visor_cfdi_mysql8_schema_v1.sql");
        Path resultado = Files.isRegularFile(dentroApp) ? dentroApp : desdeRaiz;
        if (!Files.isRegularFile(resultado)) {
            throw new IllegalStateException("No se encontró el esquema SQL del repositorio");
        }
        return resultado.toAbsolutePath();
    }

    @Test
    void protegePrivilegiosYPermiteRolesConfigurablesConAplicacionInmediata() {
        ConfiguracionSistema cfg = new ConfiguracionSistema(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getDatabaseName(),
                MYSQL.getUsername(), Path.of("xml"));
        try (JpaContext jpa = new JpaContext(cfg, MYSQL.getPassword().toCharArray())) {
            UsuariosService usuarios = new UsuariosService(jpa);
            RolesService roles = usuarios.roles();

            usuarios.inicializarRoles();
            UsuarioSesion admin = usuarios.registrarAdministradorInicial(
                    new SolicitudUsuario("Administrador", "adminrbac", null, "ADMIN"),
                    "claveAdminSegura#2026".toCharArray());

            assertTrue(roles.tienePermiso(admin.id(), Permisos.ROLES_CONFIGURAR_PERMISOS));
            assertTrue(roles.tienePermiso(admin.id(), Permisos.USUARIOS_CONSULTAR));
            assertTrue(roles.listar(admin.id()).stream()
                    .anyMatch(r -> r.clave().equals("CONSULTA")));
            assertTrue(roles.cargarMatriz(admin.id(),
                    roles.listar(admin.id()).stream().filter(r -> r.clave().equals("ADMIN"))
                            .findFirst().orElseThrow().id())
                    .permisos().size() >= 12);

            Short custom = roles.crear(admin.id(), "auditor_cfdi", "Auditor de comprobantes");
            assertNotNull(custom);
            RolListado rolNuevo = roles.listar(admin.id()).stream()
                    .filter(r -> r.id().equals(custom)).findFirst().orElseThrow();
            assertEquals("AUDITOR_CFDI", rolNuevo.clave());
            assertEquals(0, rolNuevo.permisos());
            assertThrows(IllegalArgumentException.class, () ->
                    roles.crear(admin.id(), "AUDITOR_CFDI", "Duplicado"));

            roles.modificar(admin.id(), custom, "Solo lectura de usuarios");
            MatrizPermisosRol matriz = roles.cargarMatriz(admin.id(), custom);
            assertTrue(matriz.permisos().stream().noneMatch(p -> p.seleccionado()));
            Short consultar = matriz.permisos().stream()
                    .filter(p -> Permisos.USUARIOS_CONSULTAR.equals(p.recurso() + "." + p.accion()))
                    .findFirst().orElseThrow().id();
            Short rolCambiar = matriz.permisos().stream()
                    .filter(p -> Permisos.USUARIOS_ASIGNAR_ROL.equals(p.recurso() + "." + p.accion()))
                    .findFirst().orElseThrow().id();
            roles.guardarPermisos(admin.id(), custom, Set.of(consultar, rolCambiar));
            assertEquals(2, roles.cargarMatriz(admin.id(), custom).rol().permisos());

            usuarios.registrar(admin.id(),
                    new SolicitudUsuario("Auditor", "auditor1", null, "AUDITOR_CFDI"),
                    "claveAuditorSegura#2026".toCharArray());
            Long auditorId = usuarios.listar(admin.id()).stream()
                    .filter(u -> "auditor1".equals(u.login())).findFirst().orElseThrow().id();

            assertTrue(roles.tienePermiso(auditorId, Permisos.USUARIOS_CONSULTAR));
            assertTrue(roles.tienePermiso(auditorId, Permisos.USUARIOS_ASIGNAR_ROL));
            assertFalse(roles.tienePermiso(auditorId, Permisos.USUARIOS_MODIFICAR));
            assertEquals(2, usuarios.listar(auditorId).size());
            assertThrows(SecurityException.class, () ->
                    usuarios.modificarDatos(auditorId, admin.id(),
                            "Intento no permitido", "admincambiado", null));
            assertThrows(SecurityException.class, () ->
                    usuarios.cambiarRol(auditorId, admin.id(), "ADMIN"));
            assertThrows(SecurityException.class, () ->
                    roles.guardarPermisos(auditorId, custom, Set.of(consultar)));
            assertThrows(IllegalStateException.class, () ->
                    roles.eliminar(admin.id(), custom));

            roles.guardarPermisos(admin.id(), custom, Set.of());
            assertFalse(roles.tienePermiso(auditorId, Permisos.USUARIOS_CONSULTAR));
            assertThrows(SecurityException.class, () -> usuarios.listar(auditorId));

            Short adminRol = roles.listar(admin.id()).stream()
                    .filter(r -> r.clave().equals("ADMIN")).findFirst().orElseThrow().id();
            MatrizPermisosRol adminMatriz = roles.cargarMatriz(admin.id(), adminRol);
            Short recuperacion = adminMatriz.permisos().stream()
                    .filter(p -> Permisos.PERMISO_RECUPERACION.equals(
                            p.recurso() + "." + p.accion()))
                    .findFirst().orElseThrow().id();
            assertTrue(adminMatriz.permisos().stream()
                    .filter(p -> recuperacion.equals(p.id())).findFirst()
                    .orElseThrow().obligatorio());
            assertThrows(IllegalStateException.class, () ->
                    roles.guardarPermisos(admin.id(), adminRol, Set.of()));
            assertTrue(roles.tienePermiso(admin.id(), Permisos.ROLES_CONFIGURAR_PERMISOS));

            // Un administrador puede revocar una acción concreta; el reinicio
            // no debe restaurarla. El permiso de recuperación es permanente.
            Set<Short> originales = adminMatriz.permisos().stream()
                    .filter(p -> p.seleccionado()).map(p -> p.id())
                    .collect(Collectors.toSet());
            Short crearUsuario = adminMatriz.permisos().stream()
                    .filter(p -> Permisos.USUARIOS_CREAR.equals(p.recurso() + "." + p.accion()))
                    .findFirst().orElseThrow().id();
            Set<Short> editados = originales.stream()
                    .filter(id -> !id.equals(crearUsuario)).collect(Collectors.toSet());
            roles.guardarPermisos(admin.id(), adminRol, editados);
            assertFalse(usuarios.tienePermiso(admin.id(), Permisos.USUARIOS_CREAR));
            usuarios.inicializarRoles();
            assertFalse(usuarios.tienePermiso(admin.id(), Permisos.USUARIOS_CREAR));
            assertTrue(usuarios.tienePermiso(admin.id(), Permisos.ROLES_CONFIGURAR_PERMISOS));
            roles.guardarPermisos(admin.id(), adminRol, originales);
            assertTrue(usuarios.tienePermiso(admin.id(), Permisos.USUARIOS_CREAR));

            usuarios.cambiarRol(admin.id(), auditorId, "CONSULTA");
            roles.eliminar(admin.id(), custom);
            assertFalse(roles.listar(admin.id()).stream()
                    .anyMatch(r -> r.clave().equals("AUDITOR_CFDI")));
            assertThrows(IllegalStateException.class, () ->
                    roles.eliminar(admin.id(), adminRol));

            Long auditorias = jpa.consultar(em -> ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM bitacora_evento WHERE entidad = 'rol'")
                    .getSingleResult()).longValue());
            assertTrue(auditorias >= 5);
        }
    }
}
