package com.kathsoft.visor.app.service.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Extractor StAX de complementos Pagos 2.0 y Nómina 1.2.
 * Consume completamente el subárbol del complemento, sin construir un DOM
 * ni permitir elementos desconocidos que causarían persistencia incompleta.
 * No almacena estado compartido entre lecturas concurrentes.
 */
final class LectorComplementosCfdi {
    static final String PAGOS20 = "http://www.sat.gob.mx/Pagos20";
    static final String NOMINA12 = "http://www.sat.gob.mx/nomina12";
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final int LIMITE_ELEMENTOS = 10000;

    private LectorComplementosCfdi() { }

    /**
     * Lee un complemento Pagos 2.0 desde su elemento raíz hasta su cierre.
     * Incluye impuestos de pagos y documentos relacionados.
     */
    static Map<String, Object> pagos(XMLStreamReader xml) throws XMLStreamException {
        Map<String, Object> resultado = new LinkedHashMap<>();
        String version = obligatorio(xml, "Version");
        if (!"2.0".equals(version)) {
            throw new IllegalArgumentException("Solo se admite Pagos versión 2.0");
        }
        resultado.put("version", version);
        List<Map<String, Object>> pagos = new ArrayList<>();
        resultado.put("pagos", pagos);
        Map<String, Object> pagoActual = null;
        Map<String, Object> documentoActual = null;
        int elementos = 0, nivel = 1;
        while (nivel > 0 && xml.hasNext()) {
            int evento = xml.next();
            if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new IllegalArgumentException("No se admiten entidades en complemento de pagos");
            }
            if (evento == XMLStreamConstants.END_ELEMENT) {
                String nombre = xml.getLocalName();
                if ("Pago".equals(nombre)) pagoActual = null;
                if ("DoctoRelacionado".equals(nombre)) documentoActual = null;
                nivel--;
                continue;
            }
            if (evento != XMLStreamConstants.START_ELEMENT) continue;
            nivel++;
            if (!PAGOS20.equals(xml.getNamespaceURI())) {
                throw new IllegalArgumentException("Namespace inesperado dentro de Pagos 2.0");
            }
            if (++elementos > LIMITE_ELEMENTOS) {
                throw new IllegalArgumentException("Complemento de pagos demasiado grande");
            }
            String nombre = xml.getLocalName();
            switch (nombre) {
                case "Totales" -> {
                    Map<String, Object> totales = new LinkedHashMap<>();
                    for (int i = 0; i < xml.getAttributeCount(); i++) {
                        String atributo = xml.getAttributeLocalName(i);
                        if (!atributo.equals("MontoTotalPagos") && !atributo.startsWith("Total")) {
                            throw new IllegalArgumentException(
                                    "Atributo de totales de pagos no reconocido: " + atributo);
                        }
                        dato(totales, atributo, decimal(xml.getAttributeValue(i), 20, 6));
                    }
                    if (!totales.containsKey("MontoTotalPagos") || resultado.containsKey("totales")) {
                        throw new IllegalArgumentException("Totales de pagos ausentes o duplicados");
                    }
                    resultado.put("totales", totales);
                }
                case "Pago" -> {
                    if (pagoActual != null) throw new IllegalArgumentException("Pago anidado");
                    pagoActual = new LinkedHashMap<>();
                    dato(pagoActual, "fecha", fecha(obligatorio(xml, "FechaPago")));
                    dato(pagoActual, "forma", obligatorio(xml, "FormaDePagoP"));
                    dato(pagoActual, "moneda", obligatorio(xml, "MonedaP"));
                    dato(pagoActual, "monto", decimal(obligatorio(xml, "Monto"), 20, 6));
                    dato(pagoActual, "tipoCambio", decimal(attr(xml, "TipoCambioP"), 20, 6));
                    dato(pagoActual, "numeroOperacion", attr(xml, "NumOperacion"));
                    dato(pagoActual, "rfcCuentaOrdenante", attr(xml, "RfcEmisorCtaOrd"));
                    dato(pagoActual, "bancoOrdenante", attr(xml, "NomBancoOrdExt"));
                    dato(pagoActual, "cuentaOrdenante", attr(xml, "CtaOrdenante"));
                    dato(pagoActual, "rfcCuentaBeneficiaria", attr(xml, "RfcEmisorCtaBen"));
                    dato(pagoActual, "cuentaBeneficiaria", attr(xml, "CtaBeneficiario"));
                    dato(pagoActual, "tipoCadena", attr(xml, "TipoCadPago"));
                    dato(pagoActual, "certificado", attr(xml, "CertPago"));
                    dato(pagoActual, "cadena", attr(xml, "CadPago"));
                    dato(pagoActual, "sello", attr(xml, "SelloPago"));
                    pagoActual.put("documentos", new ArrayList<Map<String, Object>>());
                    pagoActual.put("impuestos", new ArrayList<Map<String, Object>>());
                    pagos.add(pagoActual);
                }
                case "DoctoRelacionado" -> {
                    if (pagoActual == null || documentoActual != null) {
                        throw new IllegalArgumentException("Documento relacionado fuera de Pago");
                    }
                    documentoActual = new LinkedHashMap<>();
                    dato(documentoActual, "uuid", obligatorio(xml, "IdDocumento"));
                    dato(documentoActual, "serie", attr(xml, "Serie"));
                    dato(documentoActual, "folio", attr(xml, "Folio"));
                    dato(documentoActual, "moneda", obligatorio(xml, "MonedaDR"));
                    dato(documentoActual, "equivalencia", decimal(attr(xml, "EquivalenciaDR"), 20, 10));
                    dato(documentoActual, "parcialidad", attr(xml, "NumParcialidad"));
                    dato(documentoActual, "saldoAnterior", decimal(attr(xml, "ImpSaldoAnt"), 20, 6));
                    dato(documentoActual, "pagado", decimal(obligatorio(xml, "ImpPagado"), 20, 6));
                    dato(documentoActual, "saldoInsoluto", decimal(attr(xml, "ImpSaldoInsoluto"), 20, 6));
                    dato(documentoActual, "objeto", attr(xml, "ObjetoImpDR"));
                    documentoActual.put("impuestos", new ArrayList<Map<String, Object>>());
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> documentos = (List<Map<String, Object>>)
                            pagoActual.get("documentos");
                    documentos.add(documentoActual);
                }
                case "TrasladoDR", "RetencionDR", "TrasladoP", "RetencionP" -> {
                    boolean deDocumento = nombre.endsWith("DR");
                    Map<String, Object> contenedor = deDocumento ? documentoActual : pagoActual;
                    if (contenedor == null) {
                        throw new IllegalArgumentException("Impuesto sin documento/pago padre");
                    }
                    Map<String, Object> impuesto = new LinkedHashMap<>();
                    String sufijo = deDocumento ? "DR" : "P";
                    dato(impuesto, "movimiento", nombre.startsWith("Traslado")
                            ? "TRASLADO" : "RETENCION");
                    dato(impuesto, "impuesto", obligatorio(xml, "Impuesto" + sufijo));
                    dato(impuesto, "factor", attr(xml, "TipoFactor" + sufijo));
                    dato(impuesto, "base", decimal(attr(xml, "Base" + sufijo), 20, 6));
                    dato(impuesto, "tasa", decimal(attr(xml, "TasaOCuota" + sufijo), 18, 6));
                    dato(impuesto, "importe", decimal(attr(xml, "Importe" + sufijo), 20, 6));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> impuestos = (List<Map<String, Object>>)
                            contenedor.get("impuestos");
                    impuestos.add(impuesto);
                }
                case "ImpuestosDR", "TrasladosDR", "RetencionesDR",
                        "ImpuestosP", "TrasladosP", "RetencionesP" -> { }
                default -> throw new IllegalArgumentException(
                        "Nodo del complemento de pagos no soportado: " + nombre);
            }
        }
        if (!resultado.containsKey("totales") || pagos.isEmpty()) {
            throw new IllegalArgumentException("Pagos 2.0 sin Totales o nodos Pago");
        }
        return resultado;
    }

    /**
     * Lee complemento Nómina 1.2. Los subnodos no cubiertos por el esquema
     * actual se rechazan, para no perder información fiscal silenciosamente.
     */
    static Map<String, Object> nomina(XMLStreamReader xml) throws XMLStreamException {
        Map<String, Object> nomina = new LinkedHashMap<>();
        if (!"1.2".equals(obligatorio(xml, "Version"))) {
            throw new IllegalArgumentException("Solo se admite Nómina 1.2");
        }
        dato(nomina, "version", "1.2");
        dato(nomina, "tipo", obligatorio(xml, "TipoNomina"));
        dato(nomina, "fechaPago", fechaDia(obligatorio(xml, "FechaPago")));
        dato(nomina, "fechaInicial", fechaDia(obligatorio(xml, "FechaInicialPago")));
        dato(nomina, "fechaFinal", fechaDia(obligatorio(xml, "FechaFinalPago")));
        dato(nomina, "dias", decimal(obligatorio(xml, "NumDiasPagados"), 12, 3));
        dato(nomina, "percepcionesTotal", decimal(attr(xml, "TotalPercepciones"), 20, 6));
        dato(nomina, "deduccionesTotal", decimal(attr(xml, "TotalDeducciones"), 20, 6));
        dato(nomina, "otrosTotal", decimal(attr(xml, "TotalOtrosPagos"), 20, 6));
        List<Map<String, Object>> percepciones = new ArrayList<>();
        List<Map<String, Object>> deducciones = new ArrayList<>();
        List<Map<String, Object>> otros = new ArrayList<>();
        List<Map<String, Object>> incapacidades = new ArrayList<>();
        nomina.put("percepciones", percepciones);
        nomina.put("deducciones", deducciones);
        nomina.put("otros", otros);
        nomina.put("incapacidades", incapacidades);
        Map<String, Object> otroActual = null;
        int nivel = 1, elementos = 0;
        while (nivel > 0 && xml.hasNext()) {
            int evento = xml.next();
            if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new IllegalArgumentException("Entidades XML no admitidas en nómina");
            }
            if (evento == XMLStreamConstants.END_ELEMENT) {
                if ("OtroPago".equals(xml.getLocalName())) otroActual = null;
                nivel--;
                continue;
            }
            if (evento != XMLStreamConstants.START_ELEMENT) continue;
            nivel++;
            if (!NOMINA12.equals(xml.getNamespaceURI())) {
                throw new IllegalArgumentException("Namespace inesperado en complemento Nómina");
            }
            if (++elementos > LIMITE_ELEMENTOS) {
                throw new IllegalArgumentException("Nómina con demasiados elementos");
            }
            String n = xml.getLocalName();
            switch (n) {
                case "Emisor" -> {
                    Map<String, Object> emisor = new LinkedHashMap<>();
                    dato(emisor, "curp", attr(xml, "Curp"));
                    dato(emisor, "registro", attr(xml, "RegistroPatronal"));
                    dato(emisor, "rfcPatron", attr(xml, "RfcPatronOrigen"));
                    dato(emisor, "origen", attr(xml, "OrigenRecurso"));
                    dato(emisor, "montoRecurso", decimal(attr(xml, "MontoRecursoPropio"), 20, 6));
                    nomina.put("emisor", emisor);
                }
                case "Receptor" -> {
                    Map<String, Object> receptor = new LinkedHashMap<>();
                    String[][] atributos = {
                        {"curp", "Curp"}, {"nss", "NumSeguridadSocial"},
                        {"inicioLaboral", "FechaInicioRelLaboral"},
                        {"antiguedad", "Antigüedad"}, {"tipoContrato", "TipoContrato"},
                        {"sindicalizado", "Sindicalizado"}, {"jornada", "TipoJornada"},
                        {"regimen", "TipoRegimen"}, {"empleado", "NumEmpleado"},
                        {"departamento", "Departamento"}, {"puesto", "Puesto"},
                        {"riesgo", "RiesgoPuesto"}, {"periodicidad", "PeriodicidadPago"},
                        {"banco", "Banco"}, {"cuenta", "CuentaBancaria"},
                        {"entidad", "ClaveEntFed"}
                    };
                    for (String[] a : atributos) dato(receptor, a[0], attr(xml, a[1]));
                    dato(receptor, "salarioBase", decimal(attr(xml, "SalarioBaseCotApor"), 20, 6));
                    dato(receptor, "salarioIntegrado", decimal(attr(xml, "SalarioDiarioIntegrado"), 20, 6));
                    nomina.put("receptor", receptor);
                }
                case "Percepcion" -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    dato(p, "tipo", obligatorio(xml, "TipoPercepcion"));
                    dato(p, "clave", obligatorio(xml, "Clave"));
                    dato(p, "concepto", obligatorio(xml, "Concepto"));
                    dato(p, "gravado", decimal(obligatorio(xml, "ImporteGravado"), 20, 6));
                    dato(p, "exento", decimal(obligatorio(xml, "ImporteExento"), 20, 6));
                    percepciones.add(p);
                }
                case "Deduccion" -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    dato(d, "tipo", obligatorio(xml, "TipoDeduccion"));
                    dato(d, "clave", obligatorio(xml, "Clave"));
                    dato(d, "concepto", obligatorio(xml, "Concepto"));
                    dato(d, "importe", decimal(obligatorio(xml, "Importe"), 20, 6));
                    deducciones.add(d);
                }
                case "OtroPago" -> {
                    otroActual = new LinkedHashMap<>();
                    dato(otroActual, "tipo", obligatorio(xml, "TipoOtroPago"));
                    dato(otroActual, "clave", obligatorio(xml, "Clave"));
                    dato(otroActual, "concepto", obligatorio(xml, "Concepto"));
                    dato(otroActual, "importe", decimal(obligatorio(xml, "Importe"), 20, 6));
                    otros.add(otroActual);
                }
                case "SubsidioAlEmpleo" -> {
                    if (otroActual == null || otroActual.containsKey("subsidio")) {
                        throw new IllegalArgumentException("Subsidio de nómina fuera de OtroPago");
                    }
                    dato(otroActual, "subsidio", decimal(
                            obligatorio(xml, "SubsidioCausado"), 20, 6));
                }
                case "Incapacidad" -> {
                    Map<String, Object> incapacidad = new LinkedHashMap<>();
                    dato(incapacidad, "dias", obligatorio(xml, "DiasIncapacidad"));
                    dato(incapacidad, "tipo", obligatorio(xml, "TipoIncapacidad"));
                    dato(incapacidad, "importe", decimal(attr(xml, "ImporteMonetario"), 20, 6));
                    incapacidades.add(incapacidad);
                }
                case "Percepciones", "Deducciones", "OtrosPagos", "Incapacidades" -> { }
                default -> throw new IllegalArgumentException(
                        "Subnodo de Nómina no soportado: " + n);
            }
        }
        if (!nomina.containsKey("receptor")) {
            throw new IllegalArgumentException("Nómina sin receptor");
        }
        return nomina;
    }

    static String attr(XMLStreamReader xml, String nombre) {
        String texto = xml.getAttributeValue(null, nombre);
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    static String obligatorio(XMLStreamReader xml, String nombre) {
        String resultado = attr(xml, nombre);
        if (resultado == null) throw new IllegalArgumentException(
                "Falta atributo obligatorio: " + nombre);
        return resultado;
    }

    static String fecha(String texto) {
        return LocalDateTime.parse(texto).format(FECHA);
    }

    static String fechaDia(String texto) {
        return LocalDate.parse(texto).toString();
    }

    static String decimal(String texto, int precision, int escala) {
        if (texto == null) return null;
        BigDecimal valor = new BigDecimal(texto);
        if (valor.scale() > escala || valor.precision() - valor.scale() > precision - escala) {
            throw new IllegalArgumentException("Decimal fuera de precisión (" + precision
                    + "," + escala + "): " + texto);
        }
        return valor.toPlainString();
    }

    private static void dato(Map<String, Object> map, String clave, String valor) {
        if (valor != null) map.put(clave, valor);
    }
}
