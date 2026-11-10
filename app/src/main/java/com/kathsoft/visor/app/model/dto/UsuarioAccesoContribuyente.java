package com.kathsoft.visor.app.model.dto;

/**
 * Fila de la matriz de cuentas que pueden acceder a un contribuyente.
 *
 * @param id identificador del usuario
 * @param login nombre de acceso
 * @param nombre nombre completo
 * @param activo estado ACTIVO
 * @param asignado vínculo actual con contribuyente
 */
public record UsuarioAccesoContribuyente(Long id, String login, String nombre,
        boolean activo, boolean asignado) {
}
