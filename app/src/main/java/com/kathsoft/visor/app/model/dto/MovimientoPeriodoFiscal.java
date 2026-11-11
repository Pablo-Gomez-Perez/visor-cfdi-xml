package com.kathsoft.visor.app.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fila de consulta temporal. Para PAGO, el importe corresponde al nodo Pago
 * y NO al total del CFDI que lo contiene; no es ingreso acumulable calculado.
 */
public record MovimientoPeriodoFiscal(
        long documentoId, String uuid, String sentido, String tipoCfdi,
        LocalDateTime fechaEvento, Long pagoId, BigDecimal montoPago, String monedaPago) {
}
