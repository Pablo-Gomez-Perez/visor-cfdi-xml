package com.kathsoft.visor.app.service.importacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Extractor de comprobantes Retenciones e Información de Pagos 2.0.
 *
 * <p>Esta familia no es un CFDI tipo de comprobante y se registra como
 * documento_fiscal.clase=RETENCION. Los complementos especializados distintos
 * al timbre se rechazan mientras no exista almacenamiento estructurado.</p>
 */
final class LectorRetenciones20 {
    static final String NAMESPACE = "http://www.sat.gob.mx/esquemas/retencionpago/2";
    private static final String TFD = "http://www.sat.gob.mx/TimbreFiscalDigital";
    private static final int LIMITE_IMPUESTOS = 10000;

    private LectorRetenciones20() { }

    /** Lee desde START_ELEMENT Retenciones hasta su END_ELEMENT inclusive. */
    static Map<String, Object> leer(XMLStreamReader xml) throws XMLStreamException {
        if (!NAMESPACE.equals(xml.getNamespaceURI())
                || !"Retenciones".equals(xml.getLocalName())) {
            throw new IllegalArgumentException("Raíz Retenciones 2.0 inexistente");
        }
        String version = LectorComplementosCfdi.obligatorio(xml, "Version");
        if (!"2.0".equals(version)) {
            throw new IllegalArgumentException("Solo se admiten retenciones 2.0");
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        Map<String, Object> ret = new LinkedHashMap<>();
        Map<String, Object> emisor = new LinkedHashMap<>();
        Map<String, Object> receptor = new LinkedHashMap<>();
        Map<String, Object> timbre = new LinkedHashMap<>();
        List<Map<String, Object>> impuestos = new ArrayList<>();
        List<String> relacionados = new ArrayList<>();
        datos.put("clase", "RETENCION");
        datos.put("version", version);
        datos.put("retencion", ret);
        datos.put("emisor", emisor);
        datos.put("receptor", receptor);
        datos.put("timbre", timbre);
        datos.put("impuestosRetencion", impuestos);
        datos.put("relacionadosRetencion", relacionados);
        dato(datos, "fecha", LectorComplementosCfdi.fecha(
                LectorComplementosCfdi.obligatorio(xml, "FechaExp")));
        dato(ret, "folio", LectorComplementosCfdi.attr(xml, "FolioInt"));
        dato(ret, "clave", LectorComplementosCfdi.obligatorio(xml, "CveRetenc"));
        dato(ret, "lugar", LectorComplementosCfdi.attr(xml, "LugarExpRetenc"));
        dato(ret, "certificado", LectorComplementosCfdi.attr(xml, "NoCertificado"));
        dato(ret, "sello", LectorComplementosCfdi.attr(xml, "Sello"));
        String nacionalidad = null;
        boolean dentroComplemento = false;
        int nivel = 1;
        while (nivel > 0 && xml.hasNext()) {
            int evento = xml.next();
            if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new IllegalArgumentException("Entidades XML no permitidas en retención");
            }
            if (evento == XMLStreamConstants.END_ELEMENT) {
                if (NAMESPACE.equals(xml.getNamespaceURI())
                        && "Complemento".equals(xml.getLocalName())) {
                    dentroComplemento = false;
                }
                nivel--;
                continue;
            }
            if (evento != XMLStreamConstants.START_ELEMENT) continue;
            nivel++;
            String n = xml.getLocalName();
            String ns = xml.getNamespaceURI();
            if (dentroComplemento) {
                if (!TFD.equals(ns) || !"TimbreFiscalDigital".equals(n)) {
                    throw new IllegalArgumentException("Complemento de retenciones no soportado: " + n);
                }
                timbre.put("version", LectorComplementosCfdi.obligatorio(xml, "Version"));
                dato(datos, "uuid", LectorComplementosCfdi.obligatorio(xml, "UUID")
                        .toUpperCase(Locale.ROOT));
                dato(timbre, "fecha", LectorComplementosCfdi.fecha(
                        LectorComplementosCfdi.obligatorio(xml, "FechaTimbrado")));
                dato(timbre, "rfcPac", LectorComplementosCfdi.attr(xml, "RfcProvCertif"));
                dato(timbre, "noCertificadoSat",
                        LectorComplementosCfdi.obligatorio(xml, "NoCertificadoSAT"));
                dato(timbre, "selloCfd", LectorComplementosCfdi.attr(xml, "SelloCFD"));
                dato(timbre, "selloSat", LectorComplementosCfdi.attr(xml, "SelloSAT"));
                continue;
            }
            if (!NAMESPACE.equals(ns)) {
                throw new IllegalArgumentException("Namespace inesperado en retenciones: " + ns);
            }
            switch (n) {
                case "CfdiRetenRelacionados" -> {
                    LectorComplementosCfdi.obligatorio(xml, "TipoRelacion");
                    relacionados.add(LectorComplementosCfdi.obligatorio(xml, "UUID")
                            .toUpperCase(Locale.ROOT));
                }
                case "Emisor" -> {
                    dato(emisor, "rfc", LectorComplementosCfdi.obligatorio(xml, "RfcE")
                            .toUpperCase(Locale.ROOT));
                    dato(emisor, "nombre", LectorComplementosCfdi.obligatorio(xml, "NomDenRazSocE"));
                    dato(emisor, "regimen", LectorComplementosCfdi.attr(xml, "RegimenFiscalE"));
                }
                case "Receptor" -> nacionalidad =
                        LectorComplementosCfdi.obligatorio(xml, "NacionalidadR");
                case "Nacional" -> {
                    if (!"Nacional".equals(nacionalidad)) {
                        throw new IllegalArgumentException("Receptor Nacional con nacionalidad incompatible");
                    }
                    dato(receptor, "rfc", LectorComplementosCfdi.obligatorio(xml, "RfcR")
                            .toUpperCase(Locale.ROOT));
                    dato(receptor, "nombre", LectorComplementosCfdi.obligatorio(xml, "NomDenRazSocR"));
                    dato(receptor, "domicilio", LectorComplementosCfdi.attr(xml, "DomicilioFiscalR"));
                }
                case "Extranjero" -> {
                    if (!"Extranjero".equals(nacionalidad)) {
                        throw new IllegalArgumentException("Receptor Extranjero con nacionalidad incompatible");
                    }
                    dato(receptor, "nombre", LectorComplementosCfdi.obligatorio(xml, "NomDenRazSocR"));
                    dato(receptor, "registroExtranjero",
                            LectorComplementosCfdi.attr(xml, "NumRegIdTribR"));
                    Map<String, Object> extranjero = new LinkedHashMap<>();
                    dato(extranjero, "nacionalidad", nacionalidad);
                    dato(extranjero, "registro", LectorComplementosCfdi.attr(xml, "NumRegIdTribR"));
                    datos.put("receptorExtranjero", extranjero);
                }
                case "Periodo" -> {
                    Map<String, Object> periodo = new LinkedHashMap<>();
                    dato(periodo, "mesInicial", LectorComplementosCfdi.obligatorio(xml, "MesIni"));
                    dato(periodo, "mesFinal", LectorComplementosCfdi.obligatorio(xml, "MesFin"));
                    dato(periodo, "ejercicio", LectorComplementosCfdi.obligatorio(xml, "Ejercicio"));
                    datos.put("periodo", periodo);
                }
                case "Totales" -> {
                    Map<String, Object> totales = new LinkedHashMap<>();
                    dato(totales, "operacion", LectorComplementosCfdi.decimal(
                            LectorComplementosCfdi.obligatorio(xml, "MontoTotOperacion"), 20, 6));
                    dato(totales, "gravado", LectorComplementosCfdi.decimal(
                            LectorComplementosCfdi.obligatorio(xml, "MontoTotGrav"), 20, 6));
                    dato(totales, "exento", LectorComplementosCfdi.decimal(
                            LectorComplementosCfdi.obligatorio(xml, "MontoTotExent"), 20, 6));
                    dato(totales, "retenido", LectorComplementosCfdi.decimal(
                            LectorComplementosCfdi.obligatorio(xml, "MontoTotRet"), 20, 6));
                    datos.put("totalesRetencion", totales);
                }
                case "ImpRetenidos" -> {
                    if (impuestos.size() >= LIMITE_IMPUESTOS) {
                        throw new IllegalArgumentException("Demasiados impuestos en retención");
                    }
                    Map<String, Object> impuesto = new LinkedHashMap<>();
                    dato(impuesto, "base", LectorComplementosCfdi.decimal(
                            LectorComplementosCfdi.attr(xml, "BaseRet"), 20, 6));
                    dato(impuesto, "impuesto",
                            LectorComplementosCfdi.obligatorio(xml, "ImpuestoRet"));
                    dato(impuesto, "importe", LectorComplementosCfdi.decimal(
                            LectorComplementosCfdi.obligatorio(xml, "MontoRet"), 20, 6));
                    dato(impuesto, "tipoPago", LectorComplementosCfdi.attr(xml, "TipoPagoRet"));
                    impuestos.add(impuesto);
                }
                case "Complemento" -> dentroComplemento = true;
                case "Addenda" -> throw new IllegalArgumentException(
                        "Addenda de retenciones todavía no soportada");
                default -> throw new IllegalArgumentException(
                        "Nodo de retenciones no soportado: " + n);
            }
        }
        if (!datos.containsKey("uuid") || !emisor.containsKey("rfc")
                || (!receptor.containsKey("rfc")
                    && !datos.containsKey("receptorExtranjero"))
                || !datos.containsKey("periodo") || !datos.containsKey("totalesRetencion")) {
            throw new IllegalArgumentException("Retenciones 2.0 sin timbre, partes, periodo o totales");
        }
        return datos;
    }

    private static void dato(Map<String, Object> destino, String campo, String valor) {
        if (valor != null) destino.put(campo, valor);
    }
}
