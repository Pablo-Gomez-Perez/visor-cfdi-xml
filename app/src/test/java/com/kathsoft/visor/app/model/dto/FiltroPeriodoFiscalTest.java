package com.kathsoft.visor.app.model.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.kathsoft.visor.app.model.dto.FiltroPeriodoFiscal.TipoFechaFiscal;

/** Límites de periodos y exclusión del día siguiente. */
class FiltroPeriodoFiscalTest {
    @Test void ejercicioBisiesto() {
        var filtro = FiltroPeriodoFiscal.ejercicio(2024, TipoFechaFiscal.EMISION);
        assertEquals(LocalDate.of(2024, 1, 1), filtro.desde());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), filtro.finExclusivo());
    }

    @Test void mesBisiesto() {
        var filtro = FiltroPeriodoFiscal.mes(2024, 2, TipoFechaFiscal.PAGO);
        assertEquals(LocalDate.of(2024, 2, 29), filtro.hasta());
        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0), filtro.finExclusivo());
    }

    @Test void rechazaIntervaloInvertido() {
        assertThrows(IllegalArgumentException.class, () -> new FiltroPeriodoFiscal(
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), TipoFechaFiscal.CERTIFICACION));
    }

    @Test void distingueCriterios() {
        assertNotEquals(FiltroPeriodoFiscal.mes(2026, 1, TipoFechaFiscal.EMISION),
                FiltroPeriodoFiscal.mes(2026, 1, TipoFechaFiscal.PAGO));
    }

    @Test void rechazaTipoNulo() {
        assertThrows(NullPointerException.class, () -> FiltroPeriodoFiscal.ejercicio(2026, null));
    }
}
