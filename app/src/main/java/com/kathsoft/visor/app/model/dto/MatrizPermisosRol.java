package com.kathsoft.visor.app.model.dto;

import java.util.List;

/**
 * Matriz inmutable de privilegios para editar un rol fuera de JPA.
 *
 * @param rol rol seleccionado
 * @param permisos todos los privilegios disponibles y su asignación actual
 */
public record MatrizPermisosRol(RolListado rol, List<PermisoSeleccionable> permisos) {
    public MatrizPermisosRol {
        permisos = List.copyOf(permisos);
    }
}
