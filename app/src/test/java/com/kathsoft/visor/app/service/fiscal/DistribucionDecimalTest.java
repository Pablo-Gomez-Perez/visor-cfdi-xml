package com.kathsoft.visor.app.service.fiscal;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Prueba límites y residuos administrativos con resultados calculados independientemente. */
class DistribucionDecimalTest {

    private static BigDecimal decimal(String valor) { return new BigDecimal(valor); }

    private static void igual(String esperado, BigDecimal actual) {
        assertEquals(0, decimal(esperado).compareTo(actual));
    }

    @Test void conservaRemanentesParcialesYNoModificaElOriginal() {
        BigDecimal original = decimal("100.123456");
        igual("25.123456", DistribucionDecimal.validar(original,
                List.of(decimal("50"), decimal("25"))));
        igual("100.123456", original);
        igual("0", DistribucionDecimal.validar(original, List.of(original)));
        igual("0", DistribucionDecimal.validar(BigDecimal.ZERO, List.of(BigDecimal.ZERO)));
    }

    @Test void rechazaNegativosExcesoYPrecisionNoRepresentable() {
        assertThrows(IllegalArgumentException.class,
                () -> DistribucionDecimal.validar(decimal("100"), List.of(decimal("-0.000001"))));
        assertThrows(IllegalArgumentException.class,
                () -> DistribucionDecimal.validar(decimal("-1"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DistribucionDecimal.validar(decimal("100"), List.of(decimal("70"), decimal("30.000001"))));
        assertThrows(IllegalArgumentException.class,
                () -> DistribucionDecimal.validar(decimal("100"), List.of(decimal("0.1234567"))));
        assertThrows(IllegalArgumentException.class,
                () -> DistribucionDecimal.validar(decimal("100000000000000"), List.of(decimal("100000000000000"))));
        igual("0", DistribucionDecimal.validar(decimal("99999999999999.999999"),
                List.of(decimal("99999999999999.999999"))));
        igual("0", DistribucionDecimal.validar(decimal("1"), List.of(decimal("1.0000000"))));
    }

    @Test void asignaResiduoAlUltimoDestinoSinPerderUnidades() {
        var importes = DistribucionDecimal.proporciones(decimal("1"), List.of(
                decimal("0.333333333333"), decimal("0.333333333333"), decimal("0.333333333334")));
        igual("0.333333", importes.get(0));
        igual("0.333333", importes.get(1));
        igual("0.333334", importes.get(2));
        igual("1", importes.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        igual("0", DistribucionDecimal.validar(decimal("1"), importes));
        assertThrows(UnsupportedOperationException.class, () -> importes.add(BigDecimal.ZERO));
    }

    @Test void distribucionProporcionalParcialConservaPendiente() {
        var importes = DistribucionDecimal.proporciones(decimal("100"),
                List.of(decimal("0.25"), decimal("0.125")));
        igual("25", importes.get(0));
        igual("12.5", importes.get(1));
        igual("62.5", DistribucionDecimal.validar(decimal("100"), importes));
        assertTrue(DistribucionDecimal.proporciones(decimal("100"), List.of()).isEmpty());
    }

    @Test void rechazaProporcionesInvalidasSinNormalizarlasSilenciosamente() {
        assertThrows(IllegalArgumentException.class,
                () -> DistribucionDecimal.proporciones(decimal("100"), List.of(decimal("-0.1"))));
        assertThrows(IllegalArgumentException.class,
                () -> DistribucionDecimal.proporciones(decimal("100"), List.of(decimal("1.000001"))));
        assertThrows(IllegalArgumentException.class,
                () -> DistribucionDecimal.proporciones(decimal("100"), List.of(decimal("0.6"), decimal("0.5"))));
    }
}
