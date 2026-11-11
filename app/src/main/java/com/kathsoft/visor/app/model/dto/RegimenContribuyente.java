package com.kathsoft.visor.app.model.dto;

import java.time.LocalDate;

/**
 * Registro histórico de régimen fiscal con fechas inclusivas.
 *
 * @param claveRegimen clave de c_RegimenFiscal (no se infiere de un RFC)
 * @param desde inicio del periodo
 * @param hasta fin del periodo o null si permanece vigente
 */
public record RegimenContribuyente(String claveRegimen, LocalDate desde, LocalDate hasta) {
}
