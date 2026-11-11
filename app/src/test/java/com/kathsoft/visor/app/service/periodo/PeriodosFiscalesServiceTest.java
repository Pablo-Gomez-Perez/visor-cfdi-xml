package com.kathsoft.visor.app.service.periodo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kathsoft.visor.app.model.dto.MovimientoPeriodoFiscal;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de regresión para la conversión de filas SQL nativas de RF-006.
 * El driver y Hibernate pueden representar CHAR(1) como Character.
 */
class PeriodosFiscalesServiceTest {
    private static final String UUID = "123e4567-e89b-12d3-a456-426614174000";
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 11, 10, 15);

    @Test
    void convierteTipoCfdiCharacterSinClassCastException() {
        Object[] fila = {17L, UUID, "RECIBIDO", Character.valueOf('I'),
                Timestamp.valueOf(FECHA), null, null, null};

        MovimientoPeriodoFiscal movimiento = PeriodosFiscalesService.mapearFila(fila);

        assertEquals(17L, movimiento.documentoId());
        assertEquals("I", movimiento.tipoCfdi());
        assertEquals("RECIBIDO", movimiento.sentido());
        assertEquals(FECHA, movimiento.fechaEvento());
        assertNull(movimiento.pagoId());
    }

    @Test
    void admiteTipoStringYValoresDePagoSinPerderPrecision() {
        BigDecimal monto = new BigDecimal("1234.560000");
        Object[] fila = {18L, UUID, "EMITIDO", "P", FECHA, 23L, monto, "MXN"};

        MovimientoPeriodoFiscal movimiento = PeriodosFiscalesService.mapearFila(fila);

        assertEquals("P", movimiento.tipoCfdi());
        assertEquals(23L, movimiento.pagoId());
        assertEquals(monto, movimiento.montoPago());
        assertEquals("MXN", movimiento.monedaPago());
    }

    @Test
    void permiteTipoNuloEnDocumentosSinFilaCfdi() {
        Object[] fila = {19L, UUID, "RECIBIDO", null, FECHA, null, null, null};
        assertNull(PeriodosFiscalesService.mapearFila(fila).tipoCfdi());
    }

    @Test
    void diagnosticaTipoJdbcInesperadoConDocumentoYColumna() {
        Object[] fila = {20L, UUID, "EMITIDO", Integer.valueOf(1), FECHA,
                null, null, null};
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PeriodosFiscalesService.mapearFila(fila));
        assertTrue(error.getMessage().contains("documento 20"));
        assertTrue(error.getCause().getMessage().contains("cfdi.tipo"));
        assertTrue(error.getCause().getMessage().contains("java.lang.Integer"));
    }
}
