package com.kathsoft.visor.app.model.configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Comprueba las reglas básicas de configuración del sistema. */
class ConfiguracionSistemaTest {

    @TempDir
    Path temporal;

    @Test
    void normalizaDatosYDirectorio() {
        ConfiguracionSistema datos = new ConfiguracionSistema(
                " localhost ", 3306, "visor_cfdi", " visor_app ", temporal.resolve("xml"));
        assertEquals("localhost", datos.host());
        assertEquals("visor_app", datos.usuario());
        assertTrue(datos.directorioXml().isAbsolute());
    }

    @Test
    void rechazaPuertosFueraDeRango() {
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionSistema(
                "localhost", 0, "visor_cfdi", "visor_app", temporal));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionSistema(
                "localhost", 65536, "visor_cfdi", "visor_app", temporal));
    }

    @Test
    void rechazaParametrosQuePodrianInyectarOpcionesEnLaUrl() {
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionSistema(
                "localhost?sslMode=DISABLED", 3306, "visor_cfdi", "visor_app", temporal));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionSistema(
                "localhost", 3306, "visor_cfdi?parametro=1", "visor_app", temporal));
    }
}
