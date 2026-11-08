package com.kathsoft.visor.app.service.configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;

/** Valida la lectura y escritura de parámetros sin secretos. */
class ConfiguracionLocalServiceTest {

    @TempDir
    Path temporal;

    @Test
    void primeraEjecucionNoTieneConfiguracion() throws Exception {
        ConfiguracionLocalService servicio = new ConfiguracionLocalService(
                temporal.resolve("config").resolve("config.properties"));
        assertTrue(servicio.cargar().isEmpty());
    }

    @Test
    void guardaYCargaSinIncluirContrasenas() throws Exception {
        Path archivo = temporal.resolve("config").resolve("config.properties");
        ConfiguracionLocalService servicio = new ConfiguracionLocalService(archivo);
        ConfiguracionSistema esperado = new ConfiguracionSistema(
                "localhost", 3306, "visor_cfdi", "visor_app", temporal.resolve("documentos"));

        servicio.prepararRepositorio(esperado.directorioXml());
        servicio.guardar(esperado);

        assertTrue(Files.isDirectory(esperado.directorioXml()));
        assertEquals(esperado, servicio.cargar().orElseThrow());
        assertFalse(Files.readString(archivo).toLowerCase().contains("password"));
        assertFalse(Files.readString(archivo).toLowerCase().contains("contrasena"));
    }

    @Test
    void creaRepositorioSinCrearArchivosXmlFicticios() throws Exception {
        ConfiguracionLocalService servicio = new ConfiguracionLocalService(
                temporal.resolve("config.properties"));
        Path carpeta = temporal.resolve("xml");
        servicio.prepararRepositorio(carpeta);
        try (var contenido = Files.list(carpeta)) {
            assertEquals(0L, contenido.count());
        }
    }
}
