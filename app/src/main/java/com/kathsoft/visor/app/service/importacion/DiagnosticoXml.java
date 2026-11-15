package com.kathsoft.visor.app.service.importacion;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Inspección de mejor esfuerzo para un XML rechazado. No valida el documento
 * ni confía en datos de red; recupera el UUID incluso si el timbre aparece
 * después del complemento incompatible.
 */
final class DiagnosticoXml {
    private DiagnosticoXml() { }

    /** Recupera únicamente metadatos breves, nunca sellos o XML completos. */
    static String describir(Path archivo) {
        String version = "desconocida", tipo = "desconocido", uuid = "no extraído";
        StringBuilder complementos = new StringBuilder();
        XMLInputFactory fabrica = XMLInputFactory.newDefaultFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        fabrica.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        fabrica.setXMLResolver((p,s,b,n) -> {
            throw new javax.xml.stream.XMLStreamException("Entidad externa prohibida");
        });
        try (InputStream entrada = Files.newInputStream(archivo)) {
            XMLStreamReader xml = fabrica.createXMLStreamReader(entrada);
            try {
                int nivel = 0, complemento = -1, elementos = 0;
                while (xml.hasNext()) {
                    int evento = xml.next();
                    if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE) break;
                    if (evento == XMLStreamConstants.END_ELEMENT) {
                        if (nivel == complemento) complemento = -1;
                        nivel--;
                    } else if (evento == XMLStreamConstants.START_ELEMENT) {
                        if (++elementos > 100000 || ++nivel > 64) break;
                        String nombre = xml.getLocalName(), ns = xml.getNamespaceURI();
                        if (nivel == 1) {
                            version = valor(xml, "Version");
                            tipo = valor(xml, "TipoDeComprobante");
                        } else if ("Complemento".equals(nombre) && nivel == 2) {
                            complemento = nivel;
                        } else if (complemento > 0 && nivel == complemento + 1) {
                            if ("TimbreFiscalDigital".equals(nombre)
                                    && "http://www.sat.gob.mx/TimbreFiscalDigital".equals(ns)) {
                                uuid = valor(xml, "UUID");
                            } else if (complementos.length() < 500) {
                                complementos.append(nombre).append(" versión=")
                                        .append(valor(xml,"Version")).append(" ns=")
                                        .append(acotar(ns,180)).append("; ");
                            }
                        }
                    }
                }
            } finally {
                xml.close();
            }
        } catch (java.io.IOException | javax.xml.stream.XMLStreamException ignored) {
            // Los metadatos recuperados antes del error siguen siendo útiles.
        }
        return "UUID=" + uuid + "; versión=" + version + "; tipo=" + tipo
                + "; complementos=" + (complementos.isEmpty() ? "no extraídos" : complementos);
    }

    private static String valor(XMLStreamReader xml, String nombre) {
        return acotar(xml.getAttributeValue(null,nombre),70);
    }

    /** Limita caracteres para almacenar diagnósticos y evita cortar un surrogate. */
    static String acotar(String texto, int maximo) {
        if (texto == null) return "no disponible";
        texto = texto.replaceAll("[\\p{Cntrl}]", " ");
        if (texto.length() <= maximo) return texto;
        int fin = maximo-1;
        if (Character.isHighSurrogate(texto.charAt(fin-1))) fin--;
        return texto.substring(0,fin) + "…";
    }
}
