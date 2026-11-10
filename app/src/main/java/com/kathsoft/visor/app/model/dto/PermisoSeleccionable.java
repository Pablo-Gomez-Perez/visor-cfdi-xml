package com.kathsoft.visor.app.model.dto;

/**
 * Opción de privilegio para la tabla de casillas del diálogo de roles.
 *
 * @param id identificador MySQL
 * @param recurso nombre del módulo
 * @param accion operación protegida
 * @param descripcion detalle legible
 * @param seleccionado está asignado al rol
 * @param obligatorio no puede desmarcarse para este rol
 */
public record PermisoSeleccionable(Short id, String recurso, String accion,
        String descripcion, boolean seleccionado, boolean obligatorio) {
}
