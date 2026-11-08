package com.kathsoft.visor.app.service.configuracion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;

/** Comprueba la política de cifrado según la ubicación de MySQL. */
class ConexionMySqlServiceTest {

    @Test
    void conexiónLocalPuedeUsarLoopback() {
        ConfiguracionSistema datos = new ConfiguracionSistema(
                "localhost", 3306, "visor_cfdi", "visor_app", Path.of("xml"));
        assertTrue(new ConexionMySqlService().construirUrl(datos).contains("sslMode=DISABLED"));
    }

    @Test
    void conexiónRemotaExigeIdentidadVerificada() {
        ConfiguracionSistema datos = new ConfiguracionSistema(
                "mysql.empresa.mx", 3306, "visor_cfdi", "visor_app", Path.of("xml"));
        assertTrue(new ConexionMySqlService().construirUrl(datos).contains("sslMode=VERIFY_IDENTITY"));
    }
}
