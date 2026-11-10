package com.kathsoft.visor.app.model.dto;

/**
 * Campos editables de un contribuyente sin exponer entidades JPA.
 *
 * @param rfc identificador fiscal obligatorio (inmutable tras el alta)
 * @param nombreRazonSocial nombre o razón social obligatorios
 * @param codigoPostalFiscal código postal de cinco dígitos, opcional
 * @param correoContacto correo de contacto opcional
 */
public record SolicitudContribuyente(String rfc, String nombreRazonSocial,
        String codigoPostalFiscal, String correoContacto) {
}
