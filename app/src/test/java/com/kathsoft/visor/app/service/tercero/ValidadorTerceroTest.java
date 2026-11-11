package com.kathsoft.visor.app.service.tercero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.kathsoft.visor.app.model.dto.SolicitudTercero;

/** Contratos de normalización independientes de MySQL. */
class ValidadorTerceroTest {
    @Test
    void permiteRfcGenericoSinConfundirloConContribuyentePropio() {
        SolicitudTercero datos = ValidadorTercero.normalizar(
                new SolicitudTercero(" xaxx010101000 ", " Público en general ", null, null));
        assertEquals("XAXX010101000", datos.rfc());
        assertEquals("Público en general", datos.nombreRazonSocial());
        assertNull(datos.paisFiscal());
    }

    @Test
    void permiteRegistroFiscalExtranjeroSinRfcMexicano() {
        SolicitudTercero datos = ValidadorTercero.normalizar(
                new SolicitudTercero(null, "Foreign customer", "usa", " US-12345 "));
        assertEquals("USA", datos.paisFiscal());
        assertEquals("US-12345", datos.numeroRegistroFiscalExtranjero());
    }

    @Test
    void rechazaIdentidadAusenteYCamposInvalidos() {
        assertThrows(IllegalArgumentException.class, () ->
                ValidadorTercero.normalizar(new SolicitudTercero(null, "Empresa", null, null)));
        assertThrows(IllegalArgumentException.class, () ->
                ValidadorTercero.normalizar(new SolicitudTercero("ABC", "Empresa", null, null)));
        assertThrows(IllegalArgumentException.class, () ->
                ValidadorTercero.normalizar(new SolicitudTercero(null, "Empresa", null, "123")));
        assertThrows(IllegalArgumentException.class, () ->
                ValidadorTercero.normalizar(new SolicitudTercero("ABC010101AAA", " ", null, null)));
    }
}
