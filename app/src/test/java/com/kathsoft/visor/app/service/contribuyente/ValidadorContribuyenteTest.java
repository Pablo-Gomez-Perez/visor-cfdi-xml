package com.kathsoft.visor.app.service.contribuyente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;

/**
 * Validaciones de campos de contribuyente independientes de Hibernate/MySQL.
 */
class ValidadorContribuyenteTest {

    @Test
    void normalizaRfcNombreYCamposOpcionales() {
        SolicitudContribuyente c = ValidadorContribuyente.normalizar(
                new SolicitudContribuyente(" abc010101ab1 ", "  Compañía Uno  ",
                        "", "  ADMIN@EJEMPLO.MX  "));
        assertEquals("ABC010101AB1", c.rfc());
        assertEquals("Compañía Uno", c.nombreRazonSocial());
        assertNull(c.codigoPostalFiscal());
        assertEquals("admin@ejemplo.mx", c.correoContacto());
    }

    @Test
    void aceptaEstructuraDePersonaFisicaYRechazaFormatosInvalidos() {
        assertEquals("ABCD010101AAA", ValidadorContribuyente.normalizarRfc("abcd010101aaa"));
        assertThrows(IllegalArgumentException.class,
                () -> ValidadorContribuyente.normalizarRfc("ABC-010101-AAA"));
        assertThrows(IllegalArgumentException.class,
                () -> ValidadorContribuyente.normalizarRfc("ABC"));
        // Las claves genéricas de CFDI no identifican contribuyentes propios.
        assertThrows(IllegalArgumentException.class,
                () -> ValidadorContribuyente.normalizarRfc("XAXX010101000"));
        assertThrows(IllegalArgumentException.class,
                () -> ValidadorContribuyente.normalizarRfc("XEXX010101000"));
    }

    @Test
    void validaCpCorreoNombreYFechas() {
        assertThrows(IllegalArgumentException.class, () ->
                ValidadorContribuyente.normalizar(
                        new SolicitudContribuyente("ABC010101AB1", " ", null, null)));
        assertThrows(IllegalArgumentException.class, () ->
                ValidadorContribuyente.normalizar(
                        new SolicitudContribuyente("ABC010101AB1", "Empresa", "2900", null)));
        assertThrows(IllegalArgumentException.class, () ->
                ValidadorContribuyente.normalizar(
                        new SolicitudContribuyente("ABC010101AB1", "Empresa", null, "correo-mal")));
        assertThrows(IllegalArgumentException.class, () ->
                ValidadorContribuyente.validarVigencia(
                        LocalDate.parse("2025-07-02"), LocalDate.parse("2025-07-01")));
        assertThrows(IllegalArgumentException.class,
                () -> ValidadorContribuyente.normalizarRegimen("ABC"));
        assertEquals("601", ValidadorContribuyente.normalizarRegimen(" 601 "));
    }
}
