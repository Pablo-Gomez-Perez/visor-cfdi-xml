package com.kathsoft.visor.app.service.importacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Extractor StAX de cabecera, participantes, timbre y conceptos CFDI 3.3/4.0.
 * No autentica sellos, no consulta SAT y rechaza tipos/complementos que aun
 * carecen de persistencia especializada: evita aceptar datos incompletos.
 * Cada instancia es de uso exclusivo de un hilo.
 */
public final class LectorCfdiXml {
    private static final String CFDI = "http://www.sat.gob.mx/cfd/4";
    private static final String CFDI33 = "http://www.sat.gob.mx/cfd/3";
    private static final String TFD = "http://www.sat.gob.mx/TimbreFiscalDigital";
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Resultado compacto y serializable para transferir al escritor JDBC. */
    public record Documento(String uuid, String emisorRfc, String receptorRfc, String json) { }

    /**
     * Lee un XML local sin resolver entidades externas ni cargar el árbol DOM.
     * @throws Exception si falta información mínima, hay complementos no soportados
     *         o el documento no tiene estructura fiscal reconocida
     */
    public Documento leer(Path archivo) throws Exception {
        XMLInputFactory fabrica = XMLInputFactory.newDefaultFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        fabrica.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        fabrica.setXMLResolver((publicId, systemId, baseURI, namespace) -> {
            throw new XMLStreamException("No se permiten entidades XML externas");
        });
        Map<String, Object> datos = new LinkedHashMap<>();
        Map<String, Object> cfdi = new LinkedHashMap<>();
        Map<String, Object> emisor = new LinkedHashMap<>();
        Map<String, Object> receptor = new LinkedHashMap<>();
        Map<String, Object> timbre = new LinkedHashMap<>();
        List<Map<String, Object>> conceptos = new ArrayList<>();
        List<Map<String, Object>> impuestos = new ArrayList<>();
        List<Map<String, Object>> relaciones = new ArrayList<>();
        Map<String, Object> relacionActual = null;
        Map<String, Object> conceptoActual = null;
        int profundidad = 0;
        int profundidadConcepto = -1;
        datos.put("cfdi", cfdi);
        datos.put("emisor", emisor);
        datos.put("receptor", receptor);
        datos.put("timbre", timbre);
        datos.put("conceptos", conceptos);
        datos.put("impuestos", impuestos);
        datos.put("relaciones", relaciones);
        boolean root = false;
        boolean complemento = false;
        try (InputStream entrada = Files.newInputStream(archivo)) {
            XMLStreamReader xml = fabrica.createXMLStreamReader(entrada);
            try {
                while (xml.hasNext()) {
                    int event = xml.next();
                    if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                        throw new IllegalArgumentException("XML con DTD o entidades no permitidas");
                    }
                    if (event == XMLStreamConstants.END_ELEMENT) {
                        if (esCfdi(xml) && "Complemento".equals(xml.getLocalName())) {
                            complemento = false;
                        }
                        if (profundidad == profundidadConcepto) {
                            conceptoActual = null;
                            profundidadConcepto = -1;
                        }
                        if (esCfdi(xml) && "CfdiRelacionados".equals(xml.getLocalName())) {
                            relacionActual = null;
                        }
                        profundidad--;
                        continue;
                    }
                    if (event != XMLStreamConstants.START_ELEMENT) continue;
                    profundidad++;
                    String nombre = xml.getLocalName();
                    if (!root) {
                        if (!esCfdi(xml) || !"Comprobante".equals(nombre)) {
                            throw new IllegalArgumentException("No es un comprobante CFDI");
                        }
                        root = true;
                        String version = requerido(xml, "Version");
                        if (!"4.0".equals(version) && !"3.3".equals(version)) {
                            throw new IllegalArgumentException("Versión CFDI no soportada: " + version);
                        }
                        datos.put("version", version);
                        datos.put("fecha", LocalDateTime.parse(requerido(xml, "Fecha"))
                                .format(FECHA));
                        put(cfdi, "tipo", requerido(xml, "TipoDeComprobante"));
                        put(cfdi, "serie", atributo(xml, "Serie"));
                        put(cfdi, "folio", atributo(xml, "Folio"));
                        put(cfdi, "lugar", atributo(xml, "LugarExpedicion"));
                        put(cfdi, "moneda", requerido(xml, "Moneda"));
                        put(cfdi, "subtotal", decimal(requerido(xml, "SubTotal")));
                        put(cfdi, "total", decimal(requerido(xml, "Total")));
                        put(cfdi, "descuento", decimal(atributo(xml, "Descuento")));
                        put(cfdi, "tipoCambio", decimal(atributo(xml, "TipoCambio")));
                        put(cfdi, "forma", atributo(xml, "FormaPago"));
                        put(cfdi, "metodo", atributo(xml, "MetodoPago"));
                        put(cfdi, "condiciones", atributo(xml, "CondicionesDePago"));
                        put(cfdi, "exportacion", atributo(xml, "Exportacion"));
                        put(cfdi, "confirmacion", atributo(xml, "Confirmacion"));
                        put(cfdi, "noCertificado", atributo(xml, "NoCertificado"));
                        put(cfdi, "sello", atributo(xml, "Sello"));
                    } else if (esCfdi(xml) && "Complemento".equals(nombre)) {
                        complemento = true;
                    } else if (complemento) {
                        if (!TFD.equals(xml.getNamespaceURI())
                                || !"TimbreFiscalDigital".equals(nombre)) {
                            throw new IllegalArgumentException(
                                    "Complemento no soportado por RF-007: " + nombre);
                        }
                        put(datos, "uuid", requerido(xml, "UUID").toUpperCase(Locale.ROOT));
                        put(timbre, "version", requerido(xml, "Version"));
                        put(timbre, "fecha", LocalDateTime.parse(
                                requerido(xml, "FechaTimbrado")).format(FECHA));
                        put(timbre, "rfcPac", atributo(xml, "RfcProvCertif"));
                        put(timbre, "noCertificadoSat", requerido(xml, "NoCertificadoSAT"));
                        put(timbre, "selloCfd", atributo(xml, "SelloCFD"));
                        put(timbre, "selloSat", atributo(xml, "SelloSAT"));
                    } else if (esCfdi(xml) && "Emisor".equals(nombre)) {
                        put(emisor, "rfc", requerido(xml, "Rfc").toUpperCase(Locale.ROOT));
                        put(emisor, "nombre", atributo(xml, "Nombre"));
                        put(emisor, "regimen", atributo(xml, "RegimenFiscal"));
                    } else if (esCfdi(xml) && "Receptor".equals(nombre)) {
                        put(receptor, "rfc", requerido(xml, "Rfc").toUpperCase(Locale.ROOT));
                        put(receptor, "nombre", atributo(xml, "Nombre"));
                        put(receptor, "regimen", atributo(xml, "RegimenFiscalReceptor"));
                        put(receptor, "domicilio", atributo(xml, "DomicilioFiscalReceptor"));
                        put(receptor, "uso", atributo(xml, "UsoCFDI"));
                        put(receptor, "residencia", atributo(xml, "ResidenciaFiscal"));
                        put(receptor, "registroExtranjero", atributo(xml, "NumRegIdTrib"));
                    } else if (esCfdi(xml) && "InformacionGlobal".equals(nombre)) {
                        Map<String, Object> global = new LinkedHashMap<>();
                        put(global, "periodicidad", requerido(xml, "Periodicidad"));
                        put(global, "meses", requerido(xml, "Meses"));
                        put(global, "ejercicio", requerido(xml, "Año"));
                        datos.put("informacionGlobal", global);
                    } else if (esCfdi(xml) && "CfdiRelacionados".equals(nombre)) {
                        relacionActual = new LinkedHashMap<>();
                        put(relacionActual, "tipo", requerido(xml, "TipoRelacion"));
                        relacionActual.put("uuids", new ArrayList<String>());
                        relaciones.add(relacionActual);
                    } else if (esCfdi(xml) && "CfdiRelacionado".equals(nombre)) {
                        if (relacionActual == null) {
                            throw new IllegalArgumentException("CFDI relacionado sin grupo");
                        }
                        @SuppressWarnings("unchecked")
                        List<String> uuids = (List<String>) relacionActual.get("uuids");
                        uuids.add(requerido(xml, "UUID").toUpperCase(Locale.ROOT));
                    } else if (esCfdi(xml) && "Concepto".equals(nombre)) {
                        if (conceptos.size() >= 10000) {
                            throw new IllegalArgumentException("Demasiados conceptos en un XML");
                        }
                        Map<String, Object> c = new LinkedHashMap<>();
                        put(c, "clave", requerido(xml, "ClaveProdServ"));
                        put(c, "identificacion", atributo(xml, "NoIdentificacion"));
                        put(c, "cantidad", decimal(requerido(xml, "Cantidad")));
                        put(c, "unidadClave", requerido(xml, "ClaveUnidad"));
                        put(c, "unidad", atributo(xml, "Unidad"));
                        put(c, "descripcion", requerido(xml, "Descripcion"));
                        put(c, "valorUnitario", decimal(requerido(xml, "ValorUnitario")));
                        put(c, "importe", decimal(requerido(xml, "Importe")));
                        put(c, "descuento", decimal(atributo(xml, "Descuento")));
                        put(c, "objeto", atributo(xml, "ObjetoImp"));
                        c.put("impuestos", new ArrayList<Map<String, Object>>());
                        conceptos.add(c);
                        conceptoActual = c;
                        profundidadConcepto = profundidad;
                    } else if (esCfdi(xml) && "Impuestos".equals(nombre)
                            && conceptoActual == null) {
                        Map<String, Object> totales = new LinkedHashMap<>();
                        put(totales, "trasladados",
                                decimal(atributo(xml, "TotalImpuestosTrasladados")));
                        put(totales, "retenidos",
                                decimal(atributo(xml, "TotalImpuestosRetenidos")));
                        datos.put("totalesImpuestos", totales);
                    } else if (esCfdi(xml) && ("Traslado".equals(nombre)
                            || "Retencion".equals(nombre))) {
                        Map<String, Object> impuesto = new LinkedHashMap<>();
                        put(impuesto, "movimiento",
                                "Traslado".equals(nombre) ? "TRASLADO" : "RETENCION");
                        put(impuesto, "impuesto", requerido(xml, "Impuesto"));
                        put(impuesto, "factor", atributo(xml, "TipoFactor"));
                        put(impuesto, "base", decimal(atributo(xml, "Base")));
                        put(impuesto, "tasa", decimal(atributo(xml, "TasaOCuota")));
                        put(impuesto, "importe", decimal(atributo(xml, "Importe")));
                        if (conceptoActual != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> tax = (List<Map<String, Object>>)
                                    conceptoActual.get("impuestos");
                            tax.add(impuesto);
                        } else {
                            impuestos.add(impuesto);
                        }
                    }
                }
            } finally {
                xml.close();
            }
        }
        String tipo = (String) cfdi.get("tipo");
        if (!root || !List.of("I", "E", "T").contains(tipo)) {
            throw new IllegalArgumentException(
                    "RF-007 admite inicialmente CFDI I/E/T; otros tipos requieren complementos especializados");
        }
        if (conceptos.isEmpty()) {
            throw new IllegalArgumentException("Comprobante sin conceptos");
        }
        String uuid = (String) datos.get("uuid");
        if (uuid == null || !UUID.matcher(uuid).matches()) {
            throw new IllegalArgumentException("Falta UUID válido del timbre fiscal digital");
        }
        if (!emisor.containsKey("rfc") || !receptor.containsKey("rfc")) {
            throw new IllegalArgumentException("Faltan emisor o receptor CFDI");
        }
        try {
            return new Documento(uuid, (String) emisor.get("rfc"),
                    (String) receptor.get("rfc"), JSON.writeValueAsString(datos));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el documento fiscal", ex);
        }
    }

    private static boolean esCfdi(XMLStreamReader xml) {
        return CFDI.equals(xml.getNamespaceURI()) || CFDI33.equals(xml.getNamespaceURI());
    }

    private static String atributo(XMLStreamReader xml, String nombre) {
        String valor = xml.getAttributeValue(null, nombre);
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String requerido(XMLStreamReader xml, String nombre) {
        String valor = atributo(xml, nombre);
        if (valor == null) throw new IllegalArgumentException("Falta atributo CFDI: " + nombre);
        return valor;
    }

    private static String decimal(String texto) {
        if (texto == null) return null;
        BigDecimal numero = new BigDecimal(texto);
        if (numero.precision() > 20 || numero.scale() > 6) {
            throw new IllegalArgumentException("Importe fuera de DECIMAL(20,6): " + texto);
        }
        return numero.toPlainString();
    }

    private static void put(Map<String, Object> datos, String nombre, String valor) {
        if (valor != null) datos.put(nombre, valor);
    }
}
