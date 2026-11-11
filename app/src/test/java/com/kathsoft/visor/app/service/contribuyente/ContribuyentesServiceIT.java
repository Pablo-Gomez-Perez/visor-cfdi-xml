package com.kathsoft.visor.app.service.contribuyente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.RegimenContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.rol.RolesService;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * RF-004 sobre esquema real MySQL 8 en Testcontainers, sin acceso a la BD
 * del usuario. También verifica incorporación incremental de privilegios.
 */
@Testcontainers(disabledWithoutDocker = true)
class ContribuyentesServiceIT {

    private static final Path ESQUEMA = buscarEsquema();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("visor_cfdi")
            .withUsername("visor_contrib_it")
            .withPassword("clave_integracion_contrib")
            .withCopyFileToContainer(MountableFile.forHostPath(ESQUEMA),
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
    void administraContribuyentesYRegimenesConAislamientoYPermisosIncrementales() {
        ConfiguracionSistema cfg = new ConfiguracionSistema(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getDatabaseName(),
                MYSQL.getUsername(), Path.of("xml"));
        try (JpaContext jpa = new JpaContext(cfg, MYSQL.getPassword().toCharArray())) {
            UsuariosService usuarios = new UsuariosService(jpa);
            RolesService roles = usuarios.roles();
            ContribuyentesService contribuyentes = usuarios.contribuyentes();

            usuarios.inicializarRoles();
            UsuarioSesion admin = usuarios.registrarAdministradorInicial(
                    new SolicitudUsuario("Administrador", "admincontrib", null, "ADMIN"),
                    "claveAdminFiscal#2026".toCharArray());
            assertTrue(roles.tienePermiso(admin.id(), Permisos.CONTRIBUYENTES_CONSULTAR));
            assertTrue(roles.tienePermiso(admin.id(), Permisos.CONTRIBUYENTES_CREAR));
            assertTrue(roles.tienePermiso(admin.id(), Permisos.CONTRIBUYENTES_GESTIONAR_REGIMENES));
            assertTrue(roles.tienePermiso(admin.id(), Permisos.CONTRIBUYENTES_ASIGNAR_USUARIOS));

            Long c1 = contribuyentes.registrar(admin.id(),
                    new SolicitudContribuyente("abc010101ab1", "  Empresa Uno  ",
                            "29000", "FISCAL@EJEMPLO.MX"));
            Long c2 = contribuyentes.registrar(admin.id(),
                    new SolicitudContribuyente("def010101ab2", "Empresa Dos", null, null));
            assertEquals(2, contribuyentes.listar(admin.id()).size());
            assertEquals("ABC010101AB1", contribuyentes.obtener(admin.id(), c1).rfc());
            assertEquals("Empresa Uno", contribuyentes.obtener(admin.id(), c1).nombreRazonSocial());
            assertEquals("fiscal@ejemplo.mx",
                    contribuyentes.obtener(admin.id(), c1).correoContacto());

            assertThrows(IllegalArgumentException.class, () ->
                    contribuyentes.registrar(admin.id(),
                            new SolicitudContribuyente("ABC010101AB1", "Repetido", null, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    contribuyentes.registrar(admin.id(),
                            new SolicitudContribuyente("RFC_INVÁLIDO", "Inválido", null, null)));
            contribuyentes.modificar(admin.id(), c1,
                    new SolicitudContribuyente("ABC010101AB1", "Empresa Editada",
                            "29010", "nueva@ejemplo.mx"));
            assertEquals("Empresa Editada",
                    contribuyentes.obtener(admin.id(), c1).nombreRazonSocial());
            assertThrows(IllegalArgumentException.class, () ->
                    contribuyentes.modificar(admin.id(), c1,
                            new SolicitudContribuyente("XYZ010101AB3", "No válido", null, null)));
            assertEquals("Empresa Editada",
                    contribuyentes.obtener(admin.id(), c1).nombreRazonSocial());

            contribuyentes.cambiarEstado(admin.id(), c1, false);
            assertFalse(contribuyentes.obtener(admin.id(), c1).activo());
            contribuyentes.cambiarEstado(admin.id(), c1, true);
            assertTrue(contribuyentes.obtener(admin.id(), c1).activo());

            LocalDate inicio2024 = LocalDate.parse("2024-01-01");
            contribuyentes.registrarRegimen(admin.id(), c1,
                    new RegimenContribuyente("601", inicio2024,
                            LocalDate.parse("2024-12-31")));
            contribuyentes.registrarRegimen(admin.id(), c1,
                    new RegimenContribuyente("601", LocalDate.parse("2025-01-01"), null));
            contribuyentes.registrarRegimen(admin.id(), c1,
                    new RegimenContribuyente("603", inicio2024, null));
            assertEquals(3, contribuyentes.listarRegimenes(admin.id(), c1).size());
            assertThrows(IllegalArgumentException.class, () ->
                    contribuyentes.registrarRegimen(admin.id(), c1,
                            new RegimenContribuyente("601", LocalDate.parse("2024-06-01"),
                                    LocalDate.parse("2024-08-31"))));
            assertThrows(IllegalArgumentException.class, () ->
                    contribuyentes.actualizarFinRegimen(admin.id(), c1, "601",
                            inicio2024, LocalDate.parse("2025-06-01")));
            assertEquals(3, contribuyentes.listarRegimenes(admin.id(), c1).size());
            contribuyentes.actualizarFinRegimen(admin.id(), c1, "601",
                    LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));
            contribuyentes.eliminarRegimen(admin.id(), c1, "603", inicio2024);
            assertEquals(2, contribuyentes.listarRegimenes(admin.id(), c1).size());

            Short rolLimitado = roles.crear(admin.id(), "CONSULTA_FISCAL",
                    "Contribuyentes autorizados únicamente");
            Set<Short> grants = roles.cargarMatriz(admin.id(), rolLimitado).permisos().stream()
                    .filter(p -> Permisos.CONTRIBUYENTES_CONSULTAR.equals(
                            p.recurso() + "." + p.accion())
                            || Permisos.CONTRIBUYENTES_MODIFICAR.equals(
                            p.recurso() + "." + p.accion()))
                    .map(p -> p.id()).collect(Collectors.toSet());
            roles.guardarPermisos(admin.id(), rolLimitado, grants);
            usuarios.registrar(admin.id(),
                    new SolicitudUsuario("Auxiliar fiscal", "auxfiscal", null, "CONSULTA_FISCAL"),
                    "claveAuxFiscal#2026".toCharArray());
            Long auxiliar = usuarios.listar(admin.id()).stream()
                    .filter(u -> "auxfiscal".equals(u.login()))
                    .findFirst().orElseThrow().id();

            assertTrue(roles.tienePermiso(auxiliar, Permisos.CONTRIBUYENTES_CONSULTAR));
            assertFalse(roles.tienePermiso(auxiliar, Permisos.CONTRIBUYENTES_GESTIONAR_REGIMENES));
            assertEquals(0, contribuyentes.listar(auxiliar).size());
            assertThrows(SecurityException.class, () -> contribuyentes.obtener(auxiliar, c1));
            assertThrows(SecurityException.class, () ->
                    contribuyentes.modificar(auxiliar, c2,
                            new SolicitudContribuyente("DEF010101AB2",
                                    "Acceso indebido", null, null)));

            Set<Long> vinculados = Set.of(admin.id(), auxiliar);
            contribuyentes.guardarUsuarios(admin.id(), c1, vinculados);
            assertEquals(2, contribuyentes.listarUsuarios(admin.id(), c1).stream()
                    .filter(u -> u.asignado()).count());
            assertEquals(1, contribuyentes.listar(auxiliar).size());
            contribuyentes.modificar(auxiliar, c1,
                    new SolicitudContribuyente("ABC010101AB1",
                            "Empresa con auxiliar", "29010", null));
            assertEquals("Empresa con auxiliar",
                    contribuyentes.obtener(admin.id(), c1).nombreRazonSocial());
            assertThrows(SecurityException.class, () ->
                    contribuyentes.registrarRegimen(auxiliar, c1,
                            new RegimenContribuyente("605", LocalDate.now(), null)));
            assertThrows(SecurityException.class, () ->
                    contribuyentes.listarUsuarios(auxiliar, c1));

            assertThrows(IllegalArgumentException.class, () ->
                    contribuyentes.guardarUsuarios(admin.id(), c1,
                            Set.of(admin.id(), 9876543210L)));
            assertEquals(2, contribuyentes.listarUsuarios(admin.id(), c1).stream()
                    .filter(u -> u.asignado()).count());

            roles.guardarPermisos(admin.id(), rolLimitado, grants.stream()
                    .filter(id -> roles.cargarMatriz(admin.id(), rolLimitado).permisos().stream()
                            .anyMatch(p -> p.id().equals(id) &&
                                    Permisos.CONTRIBUYENTES_CONSULTAR.equals(
                                    p.recurso() + "." + p.accion())))
                    .collect(Collectors.toSet()));
            assertThrows(SecurityException.class, () ->
                    contribuyentes.modificar(auxiliar, c1,
                            new SolicitudContribuyente("ABC010101AB1", "Revocado", null, null)));
            assertEquals("Empresa con auxiliar",
                    contribuyentes.obtener(admin.id(), c1).nombreRazonSocial());

            // RF-003 + RF-004: no restaurar derechos revocados al reiniciar.
            Short rolAdmin = roles.listar(admin.id()).stream()
                    .filter(r -> "ADMIN".equals(r.clave())).findFirst().orElseThrow().id();
            Set<Short> actuales = roles.cargarMatriz(admin.id(), rolAdmin).permisos().stream()
                    .filter(p -> p.seleccionado()).map(p -> p.id()).collect(Collectors.toSet());
            Short crear = roles.cargarMatriz(admin.id(), rolAdmin).permisos().stream()
                    .filter(p -> Permisos.CONTRIBUYENTES_CREAR.equals(
                            p.recurso() + "." + p.accion()))
                    .findFirst().orElseThrow().id();
            Set<Short> sinCrear = actuales.stream().filter(id -> !id.equals(crear))
                    .collect(Collectors.toSet());
            roles.guardarPermisos(admin.id(), rolAdmin, sinCrear);
            usuarios.inicializarRoles();
            assertFalse(usuarios.tienePermiso(admin.id(), Permisos.CONTRIBUYENTES_CREAR));
            roles.guardarPermisos(admin.id(), rolAdmin, actuales);

            // Un rol con solo CREATE puede dar altas sin consultar a otros
            // contribuyentes: los permisos no se suponen implícitamente.
            Short capturistaRol = roles.crear(admin.id(), "CAPTURISTA",
                    "Alta sin acceso al catálogo");
            Short crearContrib = roles.cargarMatriz(admin.id(), capturistaRol).permisos().stream()
                    .filter(p -> Permisos.CONTRIBUYENTES_CREAR.equals(
                            p.recurso() + "." + p.accion()))
                    .findFirst().orElseThrow().id();
            roles.guardarPermisos(admin.id(), capturistaRol, Set.of(crearContrib));
            usuarios.registrar(admin.id(),
                    new SolicitudUsuario("Capturista", "capturista", null, "CAPTURISTA"),
                    "claveCapturista#2026".toCharArray());
            Long capturista = usuarios.listar(admin.id()).stream()
                    .filter(u -> "capturista".equals(u.login())).findFirst().orElseThrow().id();
            Long c3 = contribuyentes.registrar(capturista,
                    new SolicitudContribuyente("ghi010101ab3", "Empresa Tres", null, null));
            assertTrue(contribuyentes.obtener(admin.id(), c3).activo());
            assertThrows(SecurityException.class, () -> contribuyentes.listar(capturista));
            assertThrows(SecurityException.class,
                    () -> contribuyentes.obtener(capturista, c3));

            // Simula despliegue de una feat nueva: el permiso no existía aún.
            // Solo ese privilegio nuevo se concede a ADMIN en el bootstrap.
            jpa.enTransaccion(em -> {
                em.createNativeQuery(
                    "DELETE FROM rol_permiso WHERE permiso_id IN "
                    + "(SELECT id FROM permiso WHERE recurso = 'CONTRIBUYENTES' "
                    + "AND accion = 'CAMBIAR_ESTADO')").executeUpdate();
                em.createNativeQuery(
                    "DELETE FROM permiso WHERE recurso = 'CONTRIBUYENTES' "
                    + "AND accion = 'CAMBIAR_ESTADO'").executeUpdate();
                return null;
            });
            assertFalse(usuarios.tienePermiso(admin.id(),
                    Permisos.CONTRIBUYENTES_CAMBIAR_ESTADO));
            usuarios.inicializarRoles();
            assertTrue(usuarios.tienePermiso(admin.id(),
                    Permisos.CONTRIBUYENTES_CAMBIAR_ESTADO));
            assertFalse(usuarios.tienePermiso(capturista,
                    Permisos.CONTRIBUYENTES_CAMBIAR_ESTADO));

            long bitacoras = jpa.consultar(em -> ((Number) em.createNativeQuery(
                    "select count(*) from bitacora_evento "
                    + "where entidad = 'contribuyente'").getSingleResult()).longValue());
            assertTrue(bitacoras >= 9);
        }
    }
}
