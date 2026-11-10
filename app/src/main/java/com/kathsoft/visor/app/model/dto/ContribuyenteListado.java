package com.kathsoft.visor.app.model.dto;

/**
 * Información resumida de un contribuyente, para catálogo y formulario.
 *
 * @param id identificador interno estable
 * @param rfc RFC
 * @param nombreRazonSocial denominación
 * @param codigoPostalFiscal CP opcional
 * @param correoContacto correo opcional
 * @param activo estado de baja lógica
 */
public record ContribuyenteListado(Long id, String rfc, String nombreRazonSocial,
        String codigoPostalFiscal, String correoContacto, boolean activo) {
}
