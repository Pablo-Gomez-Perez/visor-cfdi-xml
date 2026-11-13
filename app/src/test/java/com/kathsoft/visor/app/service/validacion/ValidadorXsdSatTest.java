package com.kathsoft.visor.app.service.validacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RF-008: regresión de la inspección segura y del motor XSD de JAXP.
 *
 * <p>Los XSD de prueba son mínimos y sintéticos: comprueban el mecanismo,
 * nunca sustituyen los XSD oficiales del SAT para producción.</p>
 */
class ValidadorXsdSatTest {
    @TempDir Path carpeta;
    private Path xsd;
    private ValidadorXsdSat validador;

    @BeforeEach
    void preparar() throws Exception {
        xsd = carpeta.resolve("xsd");
        Path cfdi = xsd.resolve("www.sat.gob.mx/sitio_internet/cfd/4/cfdv40.xsd");
        Path tfd = xsd.resolve(
                "www.sat.gob.mx/sitio_internet/cfd/TimbreFiscalDigital/TimbreFiscalDigitalv11.xsd");
        Files.createDirectories(cfdi.getParent());
        Files.createDirectories(tfd.getParent());
        Files.writeString(cfdi, """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                    targetNamespace="http://www.sat.gob.mx/cfd/4"
                    xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                    elementFormDefault="qualified">
                  <xs:element name="Comprobante">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Emisor">
                        <xs:complexType><xs:attribute name="Rfc" type="xs:string"
                            use="required"/></xs:complexType>
                      </xs:element>
                      <xs:element name="Receptor">
                        <xs:complexType><xs:attribute name="Rfc" type="xs:string"
                            use="required"/></xs:complexType>
                      </xs:element>
                      <xs:element name="Complemento">
                        <xs:complexType><xs:sequence>
                          <xs:any namespace="##other" minOccurs="1"
                              maxOccurs="unbounded" processContents="strict"/>
                        </xs:sequence></xs:complexType>
                      </xs:element>
                    </xs:sequence>
                      <xs:attribute name="Version" use="required" fixed="4.0">
                        <xs:simpleType><xs:restriction base="xs:string"/>
                        </xs:simpleType></xs:attribute>
                      <xs:attribute name="TipoDeComprobante" type="xs:string"
                          use="required"/>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        Files.writeString(tfd, """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                    targetNamespace="http://www.sat.gob.mx/TimbreFiscalDigital"
                    elementFormDefault="qualified">
                  <xs:element name="TimbreFiscalDigital">
                    <xs:complexType>
                      <xs:attribute name="Version" use="required" fixed="1.1">
                        <xs:simpleType><xs:restriction base="xs:string"/>
                        </xs:simpleType></xs:attribute>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        validador = new ValidadorXsdSat(xsd);
    }

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                    xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                    Version="4.0" TipoDeComprobante="I">
              <cfdi:Emisor Rfc="AAA010101AAA"/>
              <cfdi:Receptor Rfc="BBB010101BBB"/>
              <cfdi:Complemento>
                <tfd:TimbreFiscalDigital Version="1.1"/>
              </cfdi:Complemento>
            </cfdi:Comprobante>
            """;

    private Path xml(String nombre, String contenido) throws Exception {
        Path ruta = carpeta.resolve(nombre);
        Files.writeString(ruta, contenido);
        return ruta;
    }

    @Test
    void verificaXmlContraXsdLocalesSinInternet() throws Exception {
        ValidadorXsdSat.Resultado r = validador.validar(xml("correcto.xml", XML));
        assertEquals(ValidadorXsdSat.Estado.VALIDO, r.estado(), r.detalle());
        assertTrue(r.valido());
        assertEquals("4.0", r.version());
        assertEquals("CFDI", r.familia());
        assertEquals("I", r.tipo());
    }

    @Test
    void identificaErrorXsdAunCuandoXmlSeaBienFormado() throws Exception {
        String alterado = XML.replace(" Rfc=\"BBB010101BBB\"", "");
        ValidadorXsdSat.Resultado r = validador.validar(xml("sin-rfc.xml", alterado));
        assertEquals(ValidadorXsdSat.Estado.INVALIDO, r.estado(), r.detalle());
        assertTrue(r.linea() > 0);
        assertFalse(r.valido());
    }

    @Test
    void identificaXmlMalFormadoSinCompilarXsd() throws Exception {
        ValidadorXsdSat.Resultado r = validador.validar(xml("roto.xml",
                "<cfdi:Comprobante><cfdi:Emisor></cfdi:Comprobante>"));
        assertEquals(ValidadorXsdSat.Estado.INVALIDO, r.estado());
    }

    @Test
    void rechazaDtdYEntidadesExternas() throws Exception {
        String ataque = XML.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!DOCTYPE cfdi:Comprobante [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>");
        ValidadorXsdSat.Resultado r = validador.validar(xml("xxe.xml", ataque));
        assertEquals(ValidadorXsdSat.Estado.INVALIDO, r.estado());
    }

    @Test
    void noConsideraValidoUnXmlCuandoNoExistenLosXsd() throws Exception {
        ValidadorXsdSat vacio = new ValidadorXsdSat(carpeta.resolve("sin-paquete-sat"));
        ValidadorXsdSat.Resultado r = vacio.validar(xml("sin-xsd.xml", XML));
        assertEquals(ValidadorXsdSat.Estado.SIN_ESQUEMA, r.estado(), r.detalle());
        assertFalse(r.valido());
    }

    @Test
    void rechazaVersionNoSoportadaYComplementoIncompatible() throws Exception {
        assertEquals(ValidadorXsdSat.Estado.INCOMPATIBLE,
                validador.validar(xml("version5.xml",
                        XML.replace("Version=\"4.0\"", "Version=\"5.0\""))).estado());
        String otro = XML.replace("<tfd:TimbreFiscalDigital Version=\"1.1\"/>",
                "<otro:Adicional xmlns:otro=\"urn:otro\"/>");
        assertEquals(ValidadorXsdSat.Estado.INCOMPATIBLE,
                validador.validar(xml("otro.xml", otro)).estado());
    }

    @Test
    void noConfundeDocumentoBienFormadoConCfdiTimbrado() throws Exception {
        String sinTimbre = XML.replace("<tfd:TimbreFiscalDigital Version=\"1.1\"/>", "");
        assertEquals(ValidadorXsdSat.Estado.INCOMPATIBLE,
                validador.validar(xml("sin-timbre.xml", sinTimbre)).estado());
    }
}
