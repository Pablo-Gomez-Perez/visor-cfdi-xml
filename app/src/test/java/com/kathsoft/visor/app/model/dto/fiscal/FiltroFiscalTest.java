package com.kathsoft.visor.app.model.dto.fiscal;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Validación de intervalos y campos cerrados, sin aceptar fragmentos SQL como filtros. */
class FiltroFiscalTest {
    private static final LocalDate DESDE = LocalDate.of(2026, 6, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 6, 30);

    private static FiltroFiscal filtro(String sentido, String rfc, String naturaleza,
            String moneda, String tipo, String estado) {
        return new FiltroFiscal(1, DESDE, HASTA, sentido, rfc, naturaleza, 0, moneda, tipo, estado);
    }

    @Test void aceptaMesEjercicioYRangoInclusivoConNormalizacion() {
        var mensual = new FiltroFiscal(1, DESDE, HASTA);
        assertEquals(DESDE, mensual.desde());
        assertEquals(HASTA, mensual.hasta());
        assertEquals("", mensual.moneda());
        var ejercicio = new FiltroFiscal(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(12, ejercicio.hasta().getMonthValue());
        var unDia = new FiltroFiscal(1, DESDE, DESDE);
        assertEquals(unDia.desde(), unDia.hasta());
        var normalizado = filtro(" recibido ", " bbb010101bbb ", " gasto ", " mxn ", " i ", " sin_consulta ");
        assertEquals("RECIBIDO", normalizado.sentido());
        assertEquals("BBB010101BBB", normalizado.rfc());
        assertEquals("GASTO", normalizado.naturaleza());
        assertEquals("MXN", normalizado.moneda());
        assertEquals("I", normalizado.tipo());
        assertEquals("SIN_CONSULTA", normalizado.estadoSat());
        assertEquals("", filtro(null, null, null, null, null, null).rfc());
    }

    @Test void rechazaRangosInversosIdentidadesYCategoriasInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> new FiltroFiscal(0, DESDE, HASTA));
        assertThrows(IllegalArgumentException.class, () -> new FiltroFiscal(1, HASTA, DESDE));
        assertThrows(IllegalArgumentException.class, () -> new FiltroFiscal(1, LocalDate.of(999, 1, 1), HASTA));
        assertThrows(IllegalArgumentException.class, () -> new FiltroFiscal(1, DESDE, LocalDate.of(9999, 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new FiltroFiscal(1, DESDE, HASTA, "", "", "", -1, "", "", ""));
        assertThrows(NullPointerException.class, () -> new FiltroFiscal(1, null, HASTA));
        assertThrows(NullPointerException.class, () -> new FiltroFiscal(1, DESDE, null));
    }

    @Test void monedaDebeSerVaciaOTresLetrasSinConversionImplicita() {
        for (String moneda : new String[] { "MXN", "USD", "EUR" }) {
            assertEquals(moneda, filtro("", "", "", moneda, "", "").moneda());
        }
        for (String moneda : new String[] { "M", "US", "MXNN", "123", "M X", "MXN'" }) {
            assertThrows(IllegalArgumentException.class, () -> filtro("", "", "", moneda, "", ""), moneda);
        }
    }

    @Test void rechazaInyeccionYValoresFueraDeLosDominiosCerrados() {
        assertThrows(IllegalArgumentException.class, () -> filtro("RECIBIDO' OR 1=1", "", "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> filtro("", "%' OR 1=1", "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> filtro("", "", "GASTO;DROP TABLE cfdi", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> filtro("", "", "", "MXN;", "", ""));
        assertThrows(IllegalArgumentException.class, () -> filtro("", "", "", "", "I'", ""));
        assertThrows(IllegalArgumentException.class, () -> filtro("", "", "", "", "", "VIGENTE OR 1=1"));
        assertThrows(IllegalArgumentException.class, () -> filtro("", "", "GASTO_16", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> filtro("", "", "", "", "RETENCION", ""));
        assertThrows(IllegalArgumentException.class, () -> filtro("", "", "", "", "", "PAGADO"));
    }
}
