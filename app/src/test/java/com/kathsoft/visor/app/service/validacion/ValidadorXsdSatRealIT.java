package com.kathsoft.visor.app.service.validacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integración XSD real: solo se ejecuta si VISOR_SAT_XSD_DIR apunta al
 * paquete completo SAT (no utiliza redes ni procesa archivos reales privados).
 * El documento deliberadamente omite campos: compilar esquemas debe ser
 * posible y la validación XSD debe informar INVALIDO, nunca ERROR.
 */
@EnabledIfEnvironmentVariable(named = "VISOR_SAT_XSD_DIR", matches = ".+")
class ValidadorXsdSatRealIT {
    @TempDir Path temporal;

    @Test
    void compilaXsdSatOficialesDeCfdiYComplementos() throws Exception {
        ValidadorXsdSat validador = new ValidadorXsdSat(
                Path.of(System.getenv("VISOR_SAT_XSD_DIR")));
        verificar(validador, "cfdi4.xml", xml("4", "4.0", "I", ""), "CFDI");
        verificar(validador, "cfdi33.xml", xml("3", "3.3", "I", ""), "CFDI");
        verificar(validador, "pago.xml", xml("4", "4.0", "P",
                "<pagos:Pagos xmlns:pagos=\"http://www.sat.gob.mx/Pagos20\" Version=\"2.0\"/>"),
                "CFDI");
        verificar(validador, "nomina.xml", xml("4", "4.0", "N",
                "<nomina:Nomina xmlns:nomina=\"http://www.sat.gob.mx/nomina12\" Version=\"1.2\"/>"),
                "CFDI");
        String ret = """
                <ret:Retenciones xmlns:ret="http://www.sat.gob.mx/esquemas/retencionpago/2"
                      xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                      Version="2.0">
                  <ret:Complemento><tfd:TimbreFiscalDigital Version="1.1"/></ret:Complemento>
                </ret:Retenciones>
                """;
        verificar(validador, "retenciones.xml", ret, "RETENCION");
    }

    private void verificar(ValidadorXsdSat validador, String archivo,
            String contenido, String familia) throws Exception {
        Path ruta = temporal.resolve(archivo);
        Files.writeString(ruta, contenido);
        ValidadorXsdSat.Resultado resultado = validador.validar(ruta);
        assertEquals(familia, resultado.familia(), resultado.detalle());
        assertEquals(ValidadorXsdSat.Estado.INVALIDO, resultado.estado(),
                archivo + ": " + resultado.detalle());
        assertTrue(resultado.detalle().startsWith("XSD:"), resultado.detalle());
    }

    private static String xml(String ns, String version, String tipo, String complemento) {
        return """
                <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/%s"
                  xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                  Version="%s" TipoDeComprobante="%s">
                    <cfdi:Complemento>
                      %s
                      <tfd:TimbreFiscalDigital Version="1.1"/>
                    </cfdi:Complemento>
                </cfdi:Comprobante>
                """.formatted(ns, version, tipo, complemento);
    }
}
