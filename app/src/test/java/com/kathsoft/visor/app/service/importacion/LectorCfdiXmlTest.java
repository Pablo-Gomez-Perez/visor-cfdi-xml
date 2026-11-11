package com.kathsoft.visor.app.service.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pruebas aisladas del extractor StAX, sin acceso a servicios externos. */
class LectorCfdiXmlTest {
    @TempDir Path temporal;

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                Version="4.0" Fecha="2026-11-11T10:15:00"
                TipoDeComprobante="I" SubTotal="100.00" Total="116.00" Moneda="MXN">
                <cfdi:Emisor Rfc="AAA010101AAA" Nombre="Emisor" RegimenFiscal="601"/>
                <cfdi:Receptor Rfc="BBB010101BBB" Nombre="Receptor"
                    DomicilioFiscalReceptor="29000" RegimenFiscalReceptor="601" UsoCFDI="G03"/>
                <cfdi:Conceptos>
                    <cfdi:Concepto ClaveProdServ="01010101" ClaveUnidad="H87"
                        Cantidad="1" Descripcion="Artículo" ValorUnitario="100.00"
                        Importe="100.00" ObjetoImp="02">
                        <cfdi:Impuestos><cfdi:Traslados>
                            <cfdi:Traslado Base="100.00" Impuesto="002" TipoFactor="Tasa"
                                TasaOCuota="0.160000" Importe="16.00"/>
                        </cfdi:Traslados></cfdi:Impuestos>
                    </cfdi:Concepto>
                </cfdi:Conceptos>
                <cfdi:Impuestos TotalImpuestosTrasladados="16.00">
                    <cfdi:Traslados><cfdi:Traslado Base="100.00" Impuesto="002"
                        TipoFactor="Tasa" TasaOCuota="0.160000" Importe="16.00"/>
                    </cfdi:Traslados>
                </cfdi:Impuestos>
                <cfdi:Complemento>
                    <tfd:TimbreFiscalDigital Version="1.1"
                        UUID="123e4567-e89b-12d3-a456-426614174000"
                        FechaTimbrado="2026-11-11T10:16:00"
                        NoCertificadoSAT="00001000000512345678"/>
                </cfdi:Complemento>
            </cfdi:Comprobante>
            """;

    @Test
    void extraeCabeceraTimbreConceptosEImpuestos() throws Exception {
        Path archivo = temporal.resolve("cfdi.xml");
        Files.writeString(archivo, XML);
        LectorCfdiXml.Documento resultado = new LectorCfdiXml().leer(archivo);
        assertEquals("123E4567-E89B-12D3-A456-426614174000", resultado.uuid());
        assertEquals("AAA010101AAA", resultado.emisorRfc());
        JsonNode datos = new ObjectMapper().readTree(resultado.json());
        assertEquals("I", datos.path("cfdi").path("tipo").asText());
        assertEquals(1, datos.path("conceptos").size());
        assertEquals("002", datos.path("impuestos").get(0).path("impuesto").asText());
        assertEquals(1, datos.path("conceptos").get(0).path("impuestos").size());
        assertEquals("16.00", datos.path("totalesImpuestos").path("trasladados").asText());
    }

    @Test
    void rechazaDtdYEntidadesExternas() throws Exception {
        Path archivo = temporal.resolve("ataque.xml");
        Files.writeString(archivo, """
                <!DOCTYPE Comprobante [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Comprobante>&xxe;</Comprobante>
                """);
        assertThrows(Exception.class, () -> new LectorCfdiXml().leer(archivo));
    }

    @Test
    void rechazaComplementoNoImplementadoSinGuardarDocumentoIncompleto() throws Exception {
        Path archivo = temporal.resolve("no-soportado.xml");
        Files.writeString(archivo, XML.replace(
                "<tfd:TimbreFiscalDigital", "<tfd:OtroComplemento"));
        Exception ex = assertThrows(Exception.class, () -> new LectorCfdiXml().leer(archivo));
        assertTrue(ex.getMessage().contains("Complemento no soportado"));
    }

    @Test
    void rechazaCfdiSinConceptos() throws Exception {
        Path archivo = temporal.resolve("sin-conceptos.xml");
        Files.writeString(archivo, XML.replaceAll(
                "(?s)<cfdi:Conceptos>.*?</cfdi:Conceptos>", ""));
        assertThrows(IllegalArgumentException.class, () -> new LectorCfdiXml().leer(archivo));
    }
}
