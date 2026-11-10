package com.kathsoft.visor.app.service.configuracion;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;

/** Comprueba la política de cifrado según la ubicación de MySQL. */
class ConexionMySqlServiceTest {

    @Test
    void conexiónLocalPuedeUsarLoopback() {
        ConfiguracionSistema datos = new ConfiguracionSistema(
                "localhost", 3306, "visor_cfdi", "visor_app", Path.of("xml"));
        String url = new ConexionMySqlService().construirUrl(datos);
        assertTrue(url.contains("sslMode=DISABLED"));
        assertTrue(url.contains("allowPublicKeyRetrieval=true"));
    }

    @Test
    void conexiónRemotaExigeIdentidadVerificada() {
        ConfiguracionSistema datos = new ConfiguracionSistema(
                "mysql.empresa.mx", 3306, "visor_cfdi", "visor_app", Path.of("xml"));
        String url = new ConexionMySqlService().construirUrl(datos);
        assertTrue(url.contains("sslMode=VERIFY_IDENTITY"));
        assertTrue(url.contains("allowPublicKeyRetrieval=false"));
        assertFalse(url.contains("sslMode=DISABLED"));
    }
    @Test
    void loopbackIpv4EIpv6ConservanLaPoliticaLocal() {
        for (String host : new String[] {"127.0.0.1", "::1", "[::1]"}) {
            ConfiguracionSistema datos = new ConfiguracionSistema(
                    host, 3306, "visor_cfdi", "visor_app", Path.of("xml"));
            String url = new ConexionMySqlService().construirUrl(datos);
            assertTrue(url.contains("allowPublicKeyRetrieval=true"), host);
            assertTrue(url.contains("sslMode=DISABLED"), host);
        }
    }

    @Test
    void ipDeRedLocalNoDebeHabilitarRecuperacionPublicaRsa() {
        ConfiguracionSistema datos = new ConfiguracionSistema(
                "192.168.1.15", 3306, "visor_cfdi", "visor_app", Path.of("xml"));
        String url = new ConexionMySqlService().construirUrl(datos);
        assertTrue(url.contains("allowPublicKeyRetrieval=false"));
        assertTrue(url.contains("sslMode=VERIFY_IDENTITY"));
    }
}
