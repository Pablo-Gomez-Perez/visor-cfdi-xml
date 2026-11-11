package com.kathsoft.visor.app.model.dto;

/**
 * Proyección de contraparte desacoplada del contexto JPA.
 *
 * @param id identificador interno
 * @param rfc RFC o null
 * @param nombreRazonSocial nombre legal
 * @param paisFiscal clave de país
 * @param numeroRegistroFiscalExtranjero identificación extranjera
 */
public record TerceroListado(Long id, String rfc, String nombreRazonSocial,
        String paisFiscal, String numeroRegistroFiscalExtranjero) {
}
