package com.kathsoft.visor.app.model.dto;

/**
 * Fila sin entidades JPA para la vista del catálogo de roles.
 *
 * @param id clave primaria del rol
 * @param clave clave de negocio estable
 * @param descripcion descripción
 * @param permisos cantidad de privilegios asignados
 * @param usuarios cantidad de usuarios relacionados
 */
public record RolListado(Short id, String clave, String descripcion,
        int permisos, long usuarios) {
    @Override
    public String toString() {
        return clave + " — " + descripcion;
    }
}
