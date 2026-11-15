package com.kathsoft.visor.app.service.importacion;

import static com.kathsoft.visor.app.service.importacion.LectorComplementosCfdi.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Lector histórico Pagos 1.0. Conserva atributos ausentes como ausentes,
 * sin fabricar Totales, EquivalenciaDR ni ObjetoImpDR de Pagos 2.0. */
final class LectorPagos10 {
    static final String NAMESPACE = "http://www.sat.gob.mx/Pagos";

    private LectorPagos10() { }

    /** Consume el complemento completo y conserva sus grupos de impuestos. */
    static Map<String, Object> leer(XMLStreamReader xml) throws XMLStreamException {
        if (!NAMESPACE.equals(xml.getNamespaceURI()) || !"1.0".equals(obligatorio(xml,"Version"))) {
            throw new IllegalArgumentException("Namespace/versión incompatible con Pagos 1.0");
        }
        Map<String,Object> resultado = new LinkedHashMap<>();
        resultado.put("version","1.0");
        List<Map<String,Object>> pagos = new ArrayList<>();
        resultado.put("pagos",pagos);
        Map<String,Object> pago = null;
        int grupo = 0;
        java.util.Deque<String> nodos = new java.util.ArrayDeque<>();
        nodos.push("Pagos");
        int elementos = 0;
        while (!nodos.isEmpty() && xml.hasNext()) {
            int evento = xml.next();
            if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE)
                throw new IllegalArgumentException("Entidades prohibidas en Pagos 1.0");
            if (evento == XMLStreamConstants.END_ELEMENT) {
                if ("Pago".equals(nodos.peek())) pago = null;
                nodos.pop();
                continue;
            }
            if (evento != XMLStreamConstants.START_ELEMENT) continue;
            if (!NAMESPACE.equals(xml.getNamespaceURI()) || ++elementos > 10000)
                throw new IllegalArgumentException("Namespace inesperado o límite de Pagos 1.0 excedido");
            String nombre = xml.getLocalName();
            String padre = nodos.peek();
            switch (nombre) {
                case "Pago" -> {
                    exigir("Pagos".equals(padre),nombre);
                    pago = new LinkedHashMap<>();
                    dato(pago, "fecha", fecha(obligatorio(xml, "FechaPago")));
                    dato(pago, "forma", obligatorio(xml, "FormaDePagoP"));
                    dato(pago, "moneda", obligatorio(xml, "MonedaP"));
                    dato(pago, "monto", decimal(obligatorio(xml, "Monto"), 20, 6));
                    dato(pago, "tipoCambio", decimal(attr(xml, "TipoCambioP"), 20, 6));
                    dato(pago, "numeroOperacion", attr(xml, "NumOperacion"));
                    dato(pago, "rfcCuentaOrdenante", attr(xml, "RfcEmisorCtaOrd"));
                    dato(pago, "bancoOrdenante", attr(xml, "NomBancoOrdExt"));
                    dato(pago, "cuentaOrdenante", attr(xml, "CtaOrdenante"));
                    dato(pago, "rfcCuentaBeneficiaria", attr(xml, "RfcEmisorCtaBen"));
                    dato(pago, "cuentaBeneficiaria", attr(xml, "CtaBeneficiario"));
                    dato(pago, "tipoCadena", attr(xml, "TipoCadPago"));
                    dato(pago, "certificado", attr(xml, "CertPago"));
                    dato(pago, "cadena", attr(xml, "CadPago"));
                    dato(pago, "sello", attr(xml, "SelloPago"));
                    pago.put("documentos", new ArrayList<Map<String, Object>>());
                    pago.put("impuestos", new ArrayList<Map<String, Object>>());
                    pago.put("gruposImpuestos10", new ArrayList<Map<String, Object>>());
                    pagos.add(pago);
                    grupo = 0;
                }
                case "DoctoRelacionado" -> {
                    exigir(pago != null && "Pago".equals(padre),nombre);
                    Map<String,Object> d = new LinkedHashMap<>();
                    String id = obligatorio(xml,"IdDocumento");
                    if (!id.matches("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}|[0-9]{3}-[0-9]{2}-[0-9]{9}"))
                        throw new IllegalArgumentException("IdDocumento no reconocido en Pagos 1.0");
                    dato(d,id.length()==36 ? "uuid" : "idHistorico",id);
                    dato(d,"serie",attr(xml,"Serie"));
                    dato(d,"folio",attr(xml,"Folio"));
                    dato(d,"moneda",obligatorio(xml,"MonedaDR"));
                    dato(d,"tipoCambioDR",decimal(attr(xml,"TipoCambioDR"),20,6));
                    dato(d,"metodoPagoDR",obligatorio(xml,"MetodoDePagoDR"));
                    dato(d,"parcialidad",attr(xml,"NumParcialidad"));
                    dato(d,"saldoAnterior",decimal(attr(xml,"ImpSaldoAnt"),20,6));
                    dato(d,"pagado",decimal(attr(xml,"ImpPagado"),20,6));
                    dato(d,"saldoInsoluto",decimal(attr(xml,"ImpSaldoInsoluto"),20,6));
                    lista(pago,"documentos").add(d);
                }
                case "Impuestos" -> {
                    exigir(pago != null && "Pago".equals(padre),nombre);
                    Map<String,Object> tot = new LinkedHashMap<>();
                    dato(tot,"retenidos",decimal(attr(xml,"TotalImpuestosRetenidos"),20,6));
                    dato(tot,"trasladados",decimal(attr(xml,"TotalImpuestosTrasladados"),20,6));
                    lista(pago,"gruposImpuestos10").add(tot);
                    grupo++;
                }
                case "Retenciones", "Traslados" -> exigir(pago != null && "Impuestos".equals(padre),nombre);
                case "Retencion", "Traslado" -> {
                    boolean traslado = "Traslado".equals(nombre);
                    exigir(pago != null && (traslado ? "Traslados" : "Retenciones").equals(padre),nombre);
                    Map<String,Object> impuesto = new LinkedHashMap<>();
                    impuesto.put("grupo10",grupo);
                    dato(impuesto,"movimiento",traslado ? "TRASLADO" : "RETENCION");
                    dato(impuesto,"impuesto",obligatorio(xml,"Impuesto"));
                    if (traslado) {
                        dato(impuesto,"factor",obligatorio(xml,"TipoFactor"));
                        dato(impuesto,"tasa",decimal(obligatorio(xml,"TasaOCuota"),18,6));
                    }
                    dato(impuesto,"importe",decimal(obligatorio(xml,"Importe"),20,6));
                    lista(pago,"impuestos").add(impuesto);
                }
                default -> throw new IllegalArgumentException("Nodo de Pagos 1.0 no soportado: "+nombre);
            }
            nodos.push(nombre);
        }
        if (!nodos.isEmpty() || pagos.isEmpty()) throw new IllegalArgumentException("Pagos 1.0 incompleto");
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> lista(Map<String,Object> padre,String clave) {
        return (List<Map<String,Object>>) padre.get(clave);
    }

    private static void exigir(boolean valido,String nodo) {
        if (!valido) throw new IllegalArgumentException("Nodo fuera de posición en Pagos 1.0: "+nodo);
    }

    private static void dato(Map<String,Object> map,String clave,String valor) {
        if (valor != null) map.put(clave,valor);
    }
}
