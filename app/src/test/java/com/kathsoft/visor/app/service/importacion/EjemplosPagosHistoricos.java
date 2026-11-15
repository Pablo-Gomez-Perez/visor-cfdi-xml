package com.kathsoft.visor.app.service.importacion;

/** XML sintéticos: no contienen comprobantes, sellos ni RFC de clientes reales. */
final class EjemplosPagosHistoricos {
    private EjemplosPagosHistoricos() { }

    static String xml(String version, String uuid, String complemento) {
        String namespace = "3.3".equals(version) ? "3" : "4";
        return """
            <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/%s"
                xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                Version="%s" Fecha="2020-03-05T01:40:28"
                Moneda="XXX" SubTotal="0" Total="0" TipoDeComprobante="P">
              <cfdi:Emisor Rfc="AAA010101AAA" RegimenFiscal="601"/>
              <cfdi:Receptor Rfc="BBB010101BBB" UsoCFDI="P01"/>
              <cfdi:Conceptos>
                <cfdi:Concepto Cantidad="1" ClaveProdServ="84111506" ClaveUnidad="ACT"
                    Descripcion="Pago" Importe="0" ValorUnitario="0"/>
              </cfdi:Conceptos>
              <cfdi:Complemento>
                %s
                <tfd:TimbreFiscalDigital Version="1.1" UUID="%s"
                    FechaTimbrado="2020-03-05T01:40:31" NoCertificadoSAT="00001000000512345678"/>
              </cfdi:Complemento>
            </cfdi:Comprobante>
            """.formatted(namespace,version,complemento,uuid);
    }

    static final String PAGO10 = """
        <pago:Pagos xmlns:pago="http://www.sat.gob.mx/Pagos" Version="1.0">
          <pago:Pago FechaPago="2020-03-04T00:12:32" FormaDePagoP="01"
              MonedaP="MXN" Monto="50.00">
            <pago:DoctoRelacionado IdDocumento="123e4567-e89b-12d3-a456-426614174099"
                MetodoDePagoDR="PPD" MonedaDR="MXN" NumParcialidad="1"
                ImpSaldoAnt="50.00" ImpPagado="50.00" ImpSaldoInsoluto="0.00"/>
          </pago:Pago>
        </pago:Pagos>
        """;

    static final String PAGO20 = """
        <pago:Pagos xmlns:pago="http://www.sat.gob.mx/Pagos20" Version="2.0">
          <pago:Totales MontoTotalPagos="50.00"/>
          <pago:Pago FechaPago="2023-04-04T00:12:32" FormaDePagoP="01"
              MonedaP="MXN" Monto="50.00">
            <pago:DoctoRelacionado IdDocumento="123e4567-e89b-12d3-a456-426614174099"
                MonedaDR="MXN" NumParcialidad="1" ImpSaldoAnt="50.00" ImpPagado="50.00"
                ImpSaldoInsoluto="0.00" ObjetoImpDR="01"/>
          </pago:Pago>
        </pago:Pagos>
        """;

    static final String PAGO10_IMPUESTOS = """
        <pago:Pagos xmlns:pago="http://www.sat.gob.mx/Pagos" Version="1.0">
          <pago:Pago FechaPago="2020-03-04T00:12:32" FormaDePagoP="01"
              MonedaP="MXN" Monto="50.00">
            <pago:DoctoRelacionado IdDocumento="123-45-123456789"
                MetodoDePagoDR="PPD" MonedaDR="MXN" NumParcialidad="1"
                ImpSaldoAnt="50.00" ImpSaldoInsoluto="0.00"/>
            <pago:Impuestos TotalImpuestosRetenidos="5.00">
              <pago:Retenciones><pago:Retencion Impuesto="001" Importe="5.00"/></pago:Retenciones>
            </pago:Impuestos>
            <pago:Impuestos TotalImpuestosTrasladados="8.00">
              <pago:Traslados><pago:Traslado Impuesto="002" TipoFactor="Tasa"
                  TasaOCuota="0.160000" Importe="8.00"/></pago:Traslados>
            </pago:Impuestos>
          </pago:Pago>
        </pago:Pagos>
        """;
}
