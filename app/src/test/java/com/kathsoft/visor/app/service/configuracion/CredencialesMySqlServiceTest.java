package com.kathsoft.visor.app.service.configuracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;

/**
 * Verifica AES-GCM, vinculación de configuración y manejo de claves sin MySQL.
 */
class CredencialesMySqlServiceTest {

    @TempDir
    Path temporal;

    private ConfiguracionSistema configuracion() {
        return new ConfiguracionSistema("localhost", 3306, "visor_cfdi",
                "visor_app", temporal.resolve("xml"));
    }

    @Test
    void guardaCifradoConClaveSeparadaYRecuperaContrasenaSinCambios() throws Exception {
        CredencialesMySqlService servicio = new CredencialesMySqlService(temporal.resolve("config"));
        char[] clave = "Secreto-MySQL-2026!Ñ".toCharArray();
        assertFalse(servicio.existen());
        servicio.guardar(configuracion(), clave);
        assertTrue(servicio.existen());
        assertTrue(Files.isRegularFile(servicio.rutaArchivo()));
        assertTrue(Files.isRegularFile(servicio.rutaClave()));
        assertEquals(32, Files.readAllBytes(servicio.rutaClave()).length);
        String contenido = new String(Files.readAllBytes(servicio.rutaArchivo()),
                StandardCharsets.ISO_8859_1);
        assertFalse(contenido.contains("Secreto-MySQL-2026"));
        char[] recuperada = servicio.cargar(configuracion());
        try {
            assertArrayEquals(clave, recuperada);
        } finally {
            Arrays.fill(recuperada, '\0');
            Arrays.fill(clave, '\0');
        }
    }

    @Test
    void cadaActualizacionUsaNuevoNonceYReemplazaPassword() throws Exception {
        CredencialesMySqlService servicio = new CredencialesMySqlService(temporal.resolve("config"));
        servicio.guardar(configuracion(), "primeraClave!2026".toCharArray());
        byte[] primero = Files.readAllBytes(servicio.rutaArchivo());
        servicio.guardar(configuracion(), "nuevaClaveSegura!2026".toCharArray());
        byte[] segundo = Files.readAllBytes(servicio.rutaArchivo());
        assertFalse(Arrays.equals(primero, segundo));
        char[] recuperada = servicio.cargar(configuracion());
        try {
            assertArrayEquals("nuevaClaveSegura!2026".toCharArray(), recuperada);
        } finally {
            Arrays.fill(recuperada, '\0');
        }
    }

    @Test
    void detectaAlteracionDelArchivoSinDevolverTexto() throws Exception {
        CredencialesMySqlService servicio = new CredencialesMySqlService(temporal.resolve("config"));
        servicio.guardar(configuracion(), "claveOriginal!2026".toCharArray());
        byte[] alterado = Files.readAllBytes(servicio.rutaArchivo());
        alterado[alterado.length - 1] ^= 1;
        Files.write(servicio.rutaArchivo(), alterado);
        assertThrows(IOException.class, () -> servicio.cargar(configuracion()));
    }

    @Test
    void noDescifraConOtraConfiguracionMySql() throws Exception {
        CredencialesMySqlService servicio = new CredencialesMySqlService(temporal.resolve("config"));
        servicio.guardar(configuracion(), "claveOriginal!2026".toCharArray());

        ConfiguracionSistema otraBase = new ConfiguracionSistema("localhost", 3306,
                "otro_esquema", "visor_app", temporal.resolve("xml"));
        ConfiguracionSistema otroServidor = new ConfiguracionSistema("127.0.0.1", 3306,
                "visor_cfdi", "visor_app", temporal.resolve("xml"));
        ConfiguracionSistema otroUsuario = new ConfiguracionSistema("localhost", 3306,
                "visor_cfdi", "visor_otro", temporal.resolve("xml"));
        assertThrows(IOException.class, () -> servicio.cargar(otraBase));
        assertThrows(IOException.class, () -> servicio.cargar(otroServidor));
        assertThrows(IOException.class, () -> servicio.cargar(otroUsuario));
    }

    @Test
    void clavePerdidaNoSeRegeneraAutomaticamenteEncimaDeCredencial() throws Exception {
        CredencialesMySqlService servicio = new CredencialesMySqlService(temporal.resolve("config"));
        servicio.guardar(configuracion(), "claveOriginal!2026".toCharArray());
        Files.delete(servicio.rutaClave());
        assertThrows(IOException.class, () -> servicio.cargar(configuracion()));
        assertThrows(IOException.class,
                () -> servicio.guardar(configuracion(), "nuevaClave!2026".toCharArray()));
        assertTrue(Files.exists(servicio.rutaArchivo()));
    }

    @Test
    void generaPermisosDePropietarioCuandoExistePosix() throws Exception {
        CredencialesMySqlService servicio = new CredencialesMySqlService(temporal.resolve("config"));
        servicio.guardar(configuracion(), "claveOriginal!2026".toCharArray());
        if (Files.getFileStore(servicio.rutaArchivo()).supportsFileAttributeView("posix")) {
            assertEquals(EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(servicio.rutaArchivo()));
            assertEquals(EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(servicio.rutaClave()));
            assertEquals(EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE),
                    Files.getPosixFilePermissions(servicio.rutaArchivo().getParent()));
        }
    }

    @Test
    void cuandoNoExisteCredencialNoDaContrasenaVaciaSilenciosamente() {
        CredencialesMySqlService servicio = new CredencialesMySqlService(temporal.resolve("sin-config"));
        assertFalse(servicio.existen());
        assertThrows(IOException.class, () -> servicio.cargar(configuracion()));
    }
}
