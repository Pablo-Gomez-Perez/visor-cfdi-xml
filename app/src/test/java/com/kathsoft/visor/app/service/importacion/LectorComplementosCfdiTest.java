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

/** Pruebas de regresión de complementos y familia Retenciones 2.0. */
class LectorComplementosCfdiTest {
    @TempDir Path temporal;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INICIO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                xmlns:pago20="http://www.sat.gob.mx/Pagos20"
                xmlns:nomina12="http://www.sat.gob.mx/nomina12"
                Version="4.0" Fecha="2026-11-11T10:15:00"
                TipoDeComprobante="%s" SubTotal="0" Total="0" Moneda="XXX">
              <cfdi:Emisor Rfc="AAA010101AAA"/>
              <cfdi:Receptor Rfc="BBB010101BBB"/>
              <cfdi:Conceptos>
                <cfdi:Concepto ClaveProdServ="84111506" ClaveUnidad="ACT"
                    Cantidad="1" Descripcion="Concepto" ValorUnitario="0" Importe="0"/>
              </cfdi:Conceptos>
              <cfdi:Complemento>
            """;
    private static final String FIN = """
                <tfd:TimbreFiscalDigital Version="1.1"
                    UUID="123e4567-e89b-12d3-a456-426614174000"
                    FechaTimbrado="2026-11-11T10:16:00"
                    NoCertificadoSAT="00001000000512345678"/>
              </cfdi:Complemento>
            </cfdi:Comprobante>
            """;

    private LectorCfdiXml.Documento leer(String contenido) throws Exception {
        Path archivo = temporal.resolve("ejemplo.xml");
        Files.writeString(archivo, contenido);
        return new LectorCfdiXml().leer(archivo);
    }

    @Test
    void pago20ConDocumentosRelacionadosEImpuestosEnAmbosNiveles() throws Exception {
        String complemento = """
                <pago20:Pagos Version="2.0">
                  <pago20:Totales MontoTotalPagos="116.00"
                      TotalTrasladosBaseIVA16="100.00" TotalTrasladosImpuestoIVA16="16.00"/>
                  <pago20:Pago FechaPago="2026-11-10T09:10:00" FormaDePagoP="03"
                      MonedaP="MXN" Monto="116.00">
                    <pago20:DoctoRelacionado IdDocumento="123e4567-e89b-12d3-a456-426614174001"
                        MonedaDR="MXN" NumParcialidad="1" ImpSaldoAnt="116.00"
                        ImpPagado="116.00" ImpSaldoInsoluto="0.00" ObjetoImpDR="02">
                      <pago20:ImpuestosDR><pago20:TrasladosDR>
                        <pago20:TrasladoDR BaseDR="100.00" ImpuestoDR="002"
                            TipoFactorDR="Tasa" TasaOCuotaDR="0.160000" ImporteDR="16.00"/>
                      </pago20:TrasladosDR></pago20:ImpuestosDR>
                    </pago20:DoctoRelacionado>
                    <pago20:ImpuestosP><pago20:TrasladosP>
                      <pago20:TrasladoP BaseP="100.00" ImpuestoP="002" TipoFactorP="Tasa"
                          TasaOCuotaP="0.160000" ImporteP="16.00"/>
                    </pago20:TrasladosP></pago20:ImpuestosP>
                  </pago20:Pago>
                </pago20:Pagos>
                """;
        LectorCfdiXml.Documento dato = leer(INICIO.formatted("P") + complemento + FIN);
        JsonNode j = JSON.readTree(dato.json());
        assertEquals("CFDI", dato.clase());
        assertEquals("P", dato.tipo());
        assertEquals(1, j.path("pagos").path("pagos").size());
        assertEquals("116.00", j.path("pagos").path("totales")
                .path("MontoTotalPagos").asText());
        assertEquals("002", j.path("pagos").path("pagos").get(0)
                .path("documentos").get(0).path("impuestos").get(0)
                .path("impuesto").asText());
        assertEquals("002", j.path("pagos").path("pagos").get(0)
                .path("impuestos").get(0).path("impuesto").asText());
    }

    @Test
    void nomina12ConPercepcionDeduccionOtroPagoYSubsidio() throws Exception {
        String complemento = """
                <nomina12:Nomina Version="1.2" TipoNomina="O" FechaPago="2026-11-11"
                    FechaInicialPago="2026-11-01" FechaFinalPago="2026-11-11"
                    NumDiasPagados="11.000" TotalPercepciones="1500.00"
                    TotalDeducciones="100.00" TotalOtrosPagos="50.00">
                  <nomina12:Emisor RegistroPatronal="Y1234567890"/>
                  <nomina12:Receptor Curp="AAAA000101HDFBBB01" NumEmpleado="10"
                      TipoRegimen="02" ClaveEntFed="CHP"/>
                  <nomina12:Percepciones>
                    <nomina12:Percepcion TipoPercepcion="001" Clave="P001"
                        Concepto="Sueldo" ImporteGravado="1500.00" ImporteExento="0.00"/>
                  </nomina12:Percepciones>
                  <nomina12:Deducciones>
                    <nomina12:Deduccion TipoDeduccion="002" Clave="D001"
                        Concepto="ISR" Importe="100.00"/>
                  </nomina12:Deducciones>
                  <nomina12:OtrosPagos>
                    <nomina12:OtroPago TipoOtroPago="002" Clave="OP001"
                        Concepto="Subsidio" Importe="50.00">
                      <nomina12:SubsidioAlEmpleo SubsidioCausado="50.00"/>
                    </nomina12:OtroPago>
                  </nomina12:OtrosPagos>
                  <nomina12:Incapacidades><nomina12:Incapacidad
                      DiasIncapacidad="1" TipoIncapacidad="01" ImporteMonetario="100.00"/>
                  </nomina12:Incapacidades>
                </nomina12:Nomina>
                """;
        LectorCfdiXml.Documento dato = leer(INICIO.formatted("N") + complemento + FIN);
        JsonNode j = JSON.readTree(dato.json());
        assertEquals("N", dato.tipo());
        assertEquals("CHP", j.path("nomina").path("receptor").path("entidad").asText());
        assertEquals(1, j.path("nomina").path("percepciones").size());
        assertEquals(1, j.path("nomina").path("incapacidades").size());
        assertEquals("50.00", j.path("nomina").path("otros").get(0)
                .path("subsidio").asText());
    }

    @Test
    void retenciones20ConReceptorNacionalImpuestoYRelacion() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ret:Retenciones xmlns:ret="http://www.sat.gob.mx/esquemas/retencionpago/2"
                    xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                    Version="2.0" FechaExp="2026-11-11T10:15:00"
                    LugarExpRetenc="29000" CveRetenc="01" FolioInt="R1">
                  <ret:CfdiRetenRelacionados TipoRelacion="01"
                      UUID="123e4567-e89b-12d3-a456-426614174003"/>
                  <ret:Emisor RfcE="AAA010101AAA" NomDenRazSocE="Emisor" RegimenFiscalE="601"/>
                  <ret:Receptor NacionalidadR="Nacional">
                    <ret:Nacional RfcR="BBB010101BBB" NomDenRazSocR="Receptor"
                        DomicilioFiscalR="29000"/>
                  </ret:Receptor>
                  <ret:Periodo MesIni="01" MesFin="11" Ejercicio="2026"/>
                  <ret:Totales MontoTotOperacion="100.00" MontoTotGrav="100.00"
                      MontoTotExent="0.00" MontoTotRet="10.00">
                    <ret:ImpRetenidos BaseRet="100.00" ImpuestoRet="002"
                        MontoRet="10.00" TipoPagoRet="01"/>
                  </ret:Totales>
                  <ret:Complemento>
                    <tfd:TimbreFiscalDigital Version="1.1"
                        UUID="123e4567-e89b-12d3-a456-426614174002"
                        FechaTimbrado="2026-11-11T10:16:00"
                        NoCertificadoSAT="00001000000512345678"/>
                  </ret:Complemento>
                </ret:Retenciones>
                """;
        LectorCfdiXml.Documento dato = leer(xml);
        JsonNode j = JSON.readTree(dato.json());
        assertEquals("RETENCION", dato.clase());
        assertEquals("AAA010101AAA", dato.emisorRfc());
        assertEquals("BBB010101BBB", dato.receptorRfc());
        assertEquals("01", j.path("retencion").path("clave").asText());
        assertEquals(1, j.path("impuestosRetencion").size());
        assertEquals("2026", j.path("periodo").path("ejercicio").asText());
        assertEquals("123E4567-E89B-12D3-A456-426614174003",
                j.path("relacionadosRetencion").get(0).asText());
    }

    @Test
    void retenciones20ExtranjerasGuardanIdentidadFiscalSinInventarRfc() throws Exception {
        String xml = """
                <ret:Retenciones xmlns:ret="http://www.sat.gob.mx/esquemas/retencionpago/2"
                    xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                    Version="2.0" FechaExp="2026-11-11T10:15:00" CveRetenc="01">
                  <ret:Emisor RfcE="AAA010101AAA" NomDenRazSocE="Emisor"/>
                  <ret:Receptor NacionalidadR="Extranjero">
                    <ret:Extranjero NumRegIdTribR="US-4321" NomDenRazSocR="Receptor Foreign"/>
                  </ret:Receptor>
                  <ret:Periodo MesIni="01" MesFin="11" Ejercicio="2026"/>
                  <ret:Totales MontoTotOperacion="100" MontoTotGrav="100"
                      MontoTotExent="0" MontoTotRet="10"/>
                  <ret:Complemento><tfd:TimbreFiscalDigital Version="1.1"
                      UUID="123e4567-e89b-12d3-a456-426614174004"
                      FechaTimbrado="2026-11-11T10:16:00"
                      NoCertificadoSAT="00001000000512345678"/></ret:Complemento>
                </ret:Retenciones>
                """;
        LectorCfdiXml.Documento dato = leer(xml);
        JsonNode j = JSON.readTree(dato.json());
        assertEquals("RETENCION", dato.clase());
        assertEquals(null, dato.receptorRfc());
        assertEquals("US-4321", j.path("receptorExtranjero").path("registro").asText());
    }

    @Test
    void noAceptaPagoSinComplementoNiComplementoNoImplementado() {
        Exception e = assertThrows(Exception.class, () -> leer(INICIO.formatted("P") + FIN));
        assertTrue(e.getMessage().contains("incompatible"));
        Exception e2 = assertThrows(Exception.class, () -> leer(INICIO.formatted("N")
                + "<nomina12:Nomina Version=\"1.3\" />" + FIN));
        assertTrue(e2.getMessage().contains("Nómina 1.2"));
    }
}
