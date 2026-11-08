package com.kathsoft.visor.app.model.dto;

import java.util.Set;

/**
 * Identidad autenticada durante la sesión Swing, sin secretos de acceso.
 *
 * @param id identificador de usuario
 * @param login usuario normalizado
 * @param nombre nombre visible
 * @param roles claves de los roles efectivos al iniciar sesión
 */
public record UsuarioSesion(Long id, String login, String nombre, Set<String> roles) {
    public UsuarioSesion {
        roles = Set.copyOf(roles);
    }

    /** @return si dispone del rol administrativo */
    public boolean esAdministrador() {
        return roles.contains("ADMIN");
    }
}
