package com.kathsoft.visor.app.model.dto;

/**
 * Fila inmutable del administrador de usuarios; no expone hashes.
 *
 * @param id identificador
 * @param nombre nombre completo
 * @param login nombre de acceso
 * @param correo dirección opcional
 * @param estado estado de cuenta
 * @param rol rol asignado
 */
public record UsuarioListado(Long id, String nombre, String login,
        String correo, String estado, String rol) {
}
