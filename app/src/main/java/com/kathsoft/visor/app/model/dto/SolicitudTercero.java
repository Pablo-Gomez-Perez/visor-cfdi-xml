package com.kathsoft.visor.app.model.dto;

/**
 * Datos administrativos de una contraparte. No certifican su situación SAT.
 *
 * @param rfc RFC nacional, genérico o null para identificación extranjera
 * @param nombreRazonSocial denominación o nombre
 * @param paisFiscal clave de país de tres caracteres
 * @param numeroRegistroFiscalExtranjero identificador fiscal extranjero
 */
public record SolicitudTercero(String rfc, String nombreRazonSocial,
        String paisFiscal, String numeroRegistroFiscalExtranjero) {
}
