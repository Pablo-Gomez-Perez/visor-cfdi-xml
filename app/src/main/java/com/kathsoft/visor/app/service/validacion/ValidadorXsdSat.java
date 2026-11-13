package com.kathsoft.visor.app.service.validacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * RF-008: validación XSD real, exclusivamente con los esquemas SAT instalados
 * localmente. Nunca interpreta el schemaLocation del archivo a validar ni
 * descarga esquemas por Internet. Se distingue XML inválido, versión no
 * soportada y ausencia de archivos XSD: no hay falsos positivos.
 *
 * <p>Una instancia Schema es compartible entre hilos, pero cada Validator,
 * StAX reader y SAX XMLReader se crea por invocación. La caché LRU
 * retiene como máximo tres gramáticas para limitar la memoria de equipos modestos.</p>
 */
public final class ValidadorXsdSat {
    public static final long LIMITE_BYTES = 8L * 1024 * 1024;
    private static final String CFDI40 = "http://www.sat.gob.mx/cfd/4";
    private static final String CFDI33 = "http://www.sat.gob.mx/cfd/3";
    private static final String RET20 = "http://www.sat.gob.mx/esquemas/retencionpago/2";
    private static final String PAGO20 = "http://www.sat.gob.mx/Pagos20";
    private static final String NOMINA12 = "http://www.sat.gob.mx/nomina12";
    private static final String TFD = "http://www.sat.gob.mx/TimbreFiscalDigital";
    private static final String PREFIX = "www.sat.gob.mx/";
    private static final Map<String, String> ESQUEMAS = Map.of(
            "4.0", "sitio_internet/cfd/4/cfdv40.xsd",
            "3.3", "sitio_internet/cfd/3/cfdv33.xsd",
            "RET2.0", "esquemas/retencionpago/2/retencionpagov2.xsd",
            "TFD1.1", "sitio_internet/cfd/TimbreFiscalDigital/TimbreFiscalDigitalv11.xsd",
            "PAGOS2.0", "sitio_internet/cfd/Pagos/Pagos20.xsd",
            "NOMINA1.2", "sitio_internet/cfd/nomina/nomina12.xsd");
    private final Path raizXsd;
    private final Map<String, Schema> cache = new LinkedHashMap<>(4, 0.75f, true) {
        private static final long serialVersionUID = 1L;
        @Override protected boolean removeEldestEntry(Map.Entry<String, Schema> eldest) {
            return size() > 3;
        }
    };

    /** Estado terminal de una verificación; solo VALIDO significa XSD verificado. */
    public enum Estado { VALIDO, INVALIDO, INCOMPATIBLE, SIN_ESQUEMA, ERROR }

    /** Resultado sin referencias al DOM ni contenido fiscal del XML. */
    public record Resultado(Path archivo, Estado estado, String familia, String version,
            String tipo, String detalle, int linea, int columna) {
        /** @return true únicamente para una validación XSD satisfactoria */
        public boolean valido() { return estado == Estado.VALIDO; }
    }

    private record Inspeccion(String familia, String version, String tipo, Set<String> complementos) { }

    /**
     * @param raizXsd carpeta que contiene www.sat.gob.mx/ con las rutas
     *                oficiales y todas las dependencias relativas de cada XSD
     */
    public ValidadorXsdSat(Path raizXsd) {
        this.raizXsd = Objects.requireNonNull(raizXsd).toAbsolutePath().normalize();
    }

    /**
     * Identifica la familia por namespace y versión y aplica el XSD concreto.
     * No contacta servicios del SAT; no verifica sellos ni cancelación.
     * @param archivo XML no confiable
     * @return diagnóstico acotado, con línea/columna cuando es posible
     */
    public Resultado validar(Path archivo) {
        Path ruta = Objects.requireNonNull(archivo, "XML requerido")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(ruta, LinkOption.NOFOLLOW_LINKS)) {
            return resultado(ruta, Estado.ERROR, null, null, null,
                    "No existe un archivo XML regular o es un enlace simbólico", 0, 0);
        }
        try {
            long bytes = Files.size(ruta);
            if (bytes < 1 || bytes > LIMITE_BYTES) {
                return resultado(ruta, Estado.ERROR, null, null, null,
                        "XML fuera del límite de 1 a 8 MiB", 0, 0);
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            return resultado(ruta, Estado.ERROR, null, null, null,
                    "No se pudo leer el archivo: " + ex.getMessage(), 0, 0);
        }
        Inspeccion inspeccion;
        try {
            inspeccion = inspeccionar(ruta);
        } catch (javax.xml.stream.XMLStreamException ex) {
            javax.xml.stream.Location loc = ex.getLocation();
            return resultado(ruta, Estado.INVALIDO, null, null, null,
                    "XML mal formado: " + ex.getMessage(),
                    loc == null ? 0 : loc.getLineNumber(),
                    loc == null ? 0 : loc.getColumnNumber());
        } catch (IllegalArgumentException ex) {
            return resultado(ruta, Estado.INVALIDO, null, null, null,
                    ex.getMessage(), 0, 0);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            return resultado(ruta, Estado.ERROR, null, null, null,
                    "Error de lectura: " + ex.getMessage(), 0, 0);
        }
        String identidad = inspeccion.familia();
        String version = inspeccion.version();
        String tipo = inspeccion.tipo();
        String principal = "RETENCION".equals(identidad) ? "RET2.0" : version;
        if (principal == null || !ESQUEMAS.containsKey(principal)
                || ("RETENCION".equals(identidad) && !"2.0".equals(version))
                || ("CFDI".equals(identidad) && tipo != null
                    && !List.of("I", "E", "T", "P", "N").contains(tipo))) {
            return resultado(ruta, Estado.INCOMPATIBLE, identidad, version, tipo,
                    "Familia, versión o tipo de comprobante todavía no soportado", 0, 0);
        }
        Set<String> complementos = inspeccion.complementos();
        if (!complementos.contains("TFD1.1")
                || ("P".equals(tipo) != complementos.contains("PAGOS2.0"))
                || ("N".equals(tipo) != complementos.contains("NOMINA1.2"))) {
            return resultado(ruta, Estado.INCOMPATIBLE, identidad, version, tipo,
                    "Falta timbre 1.1 o complemento compatible con el tipo P/N", 0, 0);
        }
        for (String comp : complementos) {
            if (!ESQUEMAS.containsKey(comp)) {
                return resultado(ruta, Estado.INCOMPATIBLE, identidad, version, tipo,
                        "Complemento o versión de esquema no soportada: " + comp, 0, 0);
            }
        }
        String clave = principal + ":" + String.join(",", complementos.stream().sorted().toList());
        Schema schema;
        try {
            synchronized (cache) {
                schema = cache.get(clave);
                if (schema == null) {
                    schema = construirEsquema(principal, complementos);
                    cache.put(clave, schema);
                }
            }
        } catch (EsquemaNoDisponible ex) {
            return resultado(ruta, Estado.SIN_ESQUEMA, identidad, version, tipo,
                    ex.getMessage(), 0, 0);
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            Throwable causa = ex;
            while (causa != null) {
                if (causa instanceof EsquemaNoDisponible) {
                    return resultado(ruta, Estado.SIN_ESQUEMA, identidad, version,
                            tipo, causa.getMessage(), 0, 0);
                }
                causa = causa.getCause();
            }
            return resultado(ruta, Estado.ERROR, identidad, version, tipo,
                    "No se pudo compilar el esquema SAT: " + ex.getMessage(), 0, 0);
        }
        try (InputStream entrada = Files.newInputStream(ruta)) {
            Validator validador = schema.newValidator();
            validador.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validador.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            SAXParserFactory sax = SAXParserFactory.newInstance();
            sax.setNamespaceAware(true);
            sax.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            sax.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            sax.setFeature("http://xml.org/sax/features/external-general-entities", false);
            sax.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var lector = sax.newSAXParser().getXMLReader();
            lector.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("No se admiten entidades externas");
            });
            validador.validate(new SAXSource(lector, new InputSource(entrada)));
            return resultado(ruta, Estado.VALIDO, identidad, version, tipo,
                    "Estructura XSD verificada con esquemas locales", 0, 0);
        } catch (SAXParseException ex) {
            return resultado(ruta, Estado.INVALIDO, identidad, version, tipo,
                    "XSD: " + ex.getMessage(), ex.getLineNumber(), ex.getColumnNumber());
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            return resultado(ruta, Estado.ERROR, identidad, version, tipo,
                    "Error durante validación XSD: " + ex.getMessage(), 0, 0);
        }
    }

    private static Resultado resultado(Path archivo, Estado estado,
            String familia, String version, String tipo, String detalle,
            int linea, int columna) {
        return new Resultado(archivo, estado, familia, version, tipo, detalle, linea, columna);
    }

    private static Inspeccion inspeccionar(Path ruta) throws IOException,
            javax.xml.stream.XMLStreamException {
        XMLInputFactory fabrica = XMLInputFactory.newFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        fabrica.setXMLResolver((p, s, b, n) -> {
            throw new javax.xml.stream.XMLStreamException("Entidad XML externa prohibida");
        });
        try (InputStream in = Files.newInputStream(ruta)) {
            XMLStreamReader xml = fabrica.createXMLStreamReader(in);
            try {
                boolean raizEncontrada = false;
                String familia = null, version = null, tipo = null;
                int nivel = 0, nivelComplemento = -1;
                Set<String> complementos = new LinkedHashSet<>();
                int nodos = 0;
                while (xml.hasNext()) {
                    int evento = xml.next();
                    if (evento == XMLStreamConstants.DTD
                            || evento == XMLStreamConstants.ENTITY_REFERENCE) {
                        throw new javax.xml.stream.XMLStreamException("DTD/entidad XML prohibida");
                    }
                    if (evento == XMLStreamConstants.END_ELEMENT) {
                        if (nivel == nivelComplemento) nivelComplemento = -1;
                        nivel--;
                        continue;
                    }
                    if (evento != XMLStreamConstants.START_ELEMENT) continue;
                    nivel++;
                    if (++nodos > 100_000 || nivel > 64) {
                        throw new javax.xml.stream.XMLStreamException(
                                "Límite de profundidad o nodos XML excedido");
                    }
                    String ns = xml.getNamespaceURI();
                    String nombre = xml.getLocalName();
                    if (!raizEncontrada) {
                        raizEncontrada = true;
                        if (("Comprobante".equals(nombre)
                                && (CFDI40.equals(ns) || CFDI33.equals(ns)))) {
                            familia = "CFDI";
                            version = xml.getAttributeValue(null, "Version");
                            tipo = xml.getAttributeValue(null, "TipoDeComprobante");
                            if (("4.0".equals(version) && !CFDI40.equals(ns))
                                    || ("3.3".equals(version) && !CFDI33.equals(ns))) {
                                throw new IllegalArgumentException(
                                        "Namespace y versión CFDI incompatibles");
                            }
                        } else if ("Retenciones".equals(nombre) && RET20.equals(ns)) {
                            familia = "RETENCION";
                            version = xml.getAttributeValue(null, "Version");

                        } else {
                            familia = "DESCONOCIDO";
                        }
                    } else if (("Complemento".equals(nombre) && CFDI40.equals(ns))
                            || ("Complemento".equals(nombre) && CFDI33.equals(ns))
                            || ("Complemento".equals(nombre) && RET20.equals(ns))) {
                        nivelComplemento = nivel;
                    } else if (nivel == nivelComplemento + 1 && nivelComplemento > 0) {
                        String c = switch (ns == null ? "" : ns) {
                            case TFD -> "TimbreFiscalDigital".equals(nombre)
                                    && "1.1".equals(xml.getAttributeValue(null, "Version"))
                                        ? "TFD1.1" : "TFD_NO_SOPORTADO";
                            case PAGO20 -> "Pagos".equals(nombre)
                                    && "2.0".equals(xml.getAttributeValue(null, "Version"))
                                        ? "PAGOS2.0" : "PAGOS_NO_SOPORTADO";
                            case NOMINA12 -> "Nomina".equals(nombre)
                                    && "1.2".equals(xml.getAttributeValue(null, "Version"))
                                        ? "NOMINA1.2" : "NOMINA_NO_SOPORTADA";
                            default -> "NAMESPACE_NO_SOPORTADO:" + ns;
                        };
                        if (!complementos.add(c)) {
                            throw new IllegalArgumentException("Complemento duplicado: " + c);
                        }
                    }
                }
                if (!raizEncontrada) {
                    throw new IllegalArgumentException("Documento XML vacío");
                }
                return new Inspeccion(familia, version, tipo, Set.copyOf(complementos));
            } finally {
                xml.close();
            }
        }
    }

    private Schema construirEsquema(String principal, Set<String> complementos) {
        List<String> rutas = new ArrayList<>();
        rutas.add(ESQUEMAS.get(principal));
        complementos.stream().sorted().forEach(c -> rutas.add(ESQUEMAS.get(c)));
        try {
            if (!Files.isDirectory(raizXsd)) {
                throw new EsquemaNoDisponible("Instale XSD del SAT en: " + raizXsd);
            }
            Path real = raizXsd.toRealPath();
            List<Source> fuentes = new ArrayList<>();
            for (String relativa : rutas) {
                Path ruta = asegurarLocal(real, real.resolve(PREFIX + relativa));
                fuentes.add(new StreamSource(ruta.toFile()));
            }
            SchemaFactory fabrica = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            fabrica.setResourceResolver(new ResolutorLocal(real));
            return fabrica.newSchema(fuentes.toArray(Source[]::new));
        } catch (EsquemaNoDisponible ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static Path asegurarLocal(Path base, Path candidata) {
        try {
            Path ruta = candidata.toRealPath();
            if (!ruta.startsWith(base) || !Files.isRegularFile(ruta)
                    || ruta.getFileName() == null
                    || !ruta.getFileName().toString().endsWith(".xsd")) {
                throw new EsquemaNoDisponible("Dependencia XSD no autorizada: " + candidata);
            }
            return ruta;
        } catch (IOException ex) {
            throw new EsquemaNoDisponible("Falta esquema/dependencia SAT: " + candidata);
        }
    }

    private static final class EsquemaNoDisponible extends RuntimeException {
        private static final long serialVersionUID = 1L;
        EsquemaNoDisponible(String mensaje) { super(mensaje); }
    }

    /** Resolver cerrado al paquete XSD local y al host www.sat.gob.mx. */
    private static final class ResolutorLocal implements LSResourceResolver {
        private final Path base;
        ResolutorLocal(Path base) { this.base = base; }

        @Override public LSInput resolveResource(String type, String namespaceURI,
                String publicId, String systemId, String baseURI) {
            if (systemId == null) {
                throw new EsquemaNoDisponible("Import XSD sin schemaLocation");
            }
            java.net.URI destino;
            try {
                java.net.URI recurso = java.net.URI.create(systemId);
                if (recurso.isAbsolute()
                        && ("http".equalsIgnoreCase(recurso.getScheme())
                            || "https".equalsIgnoreCase(recurso.getScheme()))) {
                    if (!"www.sat.gob.mx".equalsIgnoreCase(recurso.getHost())
                            || recurso.getQuery() != null || recurso.getFragment() != null) {
                        throw new EsquemaNoDisponible("XSD externo no autorizado: " + systemId);
                    }
                    destino = base.resolve(PREFIX + recurso.getPath().replaceFirst("^/", ""))
                            .toUri();
                } else {
                    if (baseURI == null) {
                        throw new EsquemaNoDisponible("XSD sin base local: " + systemId);
                    }
                    destino = java.net.URI.create(baseURI).resolve(recurso);
                }
                if (!"file".equalsIgnoreCase(destino.getScheme())) {
                    throw new EsquemaNoDisponible("XSD remoto bloqueado: " + destino);
                }
                Path ruta = asegurarLocal(base, Path.of(destino));
                Input in = new Input();
                in.publicId = publicId;
                in.systemId = ruta.toUri().toString();
                in.baseURI = in.systemId;
                in.byteStream = Files.newInputStream(ruta);
                return in;
            } catch (IOException ex) {
                throw new EsquemaNoDisponible("No se puede leer XSD: " + systemId);
            }
        }
    }

    /** Implementación mínima LSInput para resolver exclusivamente ficheros. */
    private static final class Input implements LSInput {
        String publicId, systemId, baseURI;
        InputStream byteStream;
        @Override public java.io.Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(java.io.Reader reader) { }
        @Override public InputStream getByteStream() { return byteStream; }
        @Override public void setByteStream(InputStream stream) { byteStream = stream; }
        @Override public String getStringData() { return null; }
        @Override public void setStringData(String value) { }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String value) { systemId = value; }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String value) { publicId = value; }
        @Override public String getBaseURI() { return baseURI; }
        @Override public void setBaseURI(String value) { baseURI = value; }
        @Override public String getEncoding() { return null; }
        @Override public void setEncoding(String value) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean value) { }
    }
}
