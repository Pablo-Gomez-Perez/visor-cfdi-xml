package com.kathsoft.visor.app.security;

import java.util.List;

/**
 * Claves estables de las operaciones realmente implementadas.
 *
 * <p>Añadir un privilegio nuevo requiere incorporarlo al catálogo y validar
 * su cumplimiento desde los servicios; nunca basta con crear una casilla
 * en la interfaz.</p>
 */
public final class Permisos {

    public static final String USUARIOS_CONSULTAR = "USUARIOS.CONSULTAR";
    public static final String USUARIOS_CREAR = "USUARIOS.CREAR";
    public static final String USUARIOS_MODIFICAR = "USUARIOS.MODIFICAR";
    public static final String USUARIOS_CAMBIAR_ESTADO = "USUARIOS.CAMBIAR_ESTADO";
    public static final String USUARIOS_ASIGNAR_ROL = "USUARIOS.ASIGNAR_ROL";
    public static final String USUARIOS_RESTABLECER_CLAVE = "USUARIOS.RESTABLECER_CLAVE";
    public static final String USUARIOS_ASIGNAR_ADMIN = "USUARIOS.ASIGNAR_ADMIN";

    public static final String CONTRIBUYENTES_CONSULTAR = "CONTRIBUYENTES.CONSULTAR";
    public static final String CONTRIBUYENTES_CREAR = "CONTRIBUYENTES.CREAR";
    public static final String CONTRIBUYENTES_MODIFICAR = "CONTRIBUYENTES.MODIFICAR";
    public static final String CONTRIBUYENTES_CAMBIAR_ESTADO = "CONTRIBUYENTES.CAMBIAR_ESTADO";
    public static final String CONTRIBUYENTES_GESTIONAR_REGIMENES = "CONTRIBUYENTES.GESTIONAR_REGIMENES";
    public static final String CONTRIBUYENTES_ASIGNAR_USUARIOS = "CONTRIBUYENTES.ASIGNAR_USUARIOS";

    public static final String ROLES_CONSULTAR = "ROLES.CONSULTAR";
    public static final String ROLES_CREAR = "ROLES.CREAR";
    public static final String ROLES_MODIFICAR = "ROLES.MODIFICAR";
    public static final String ROLES_CONFIGURAR_PERMISOS = "ROLES.CONFIGURAR_PERMISOS";
    public static final String ROLES_ELIMINAR = "ROLES.ELIMINAR";

    /**
     * El administrador no puede quitarse este privilegio de recuperación.
     * Los demás permisos, incluidos los de usuarios, pueden editarse.
     */
    public static final String PERMISO_RECUPERACION = ROLES_CONFIGURAR_PERMISOS;

    private static final List<DefinicionPermiso> CATALOGO = List.of(
        new DefinicionPermiso("USUARIOS", "CONSULTAR", "Consultar el listado de usuarios"),
        new DefinicionPermiso("USUARIOS", "CREAR", "Registrar usuarios (por defecto con rol CONSULTA)"),
        new DefinicionPermiso("USUARIOS", "MODIFICAR", "Editar nombre, login y correo de usuarios"),
        new DefinicionPermiso("USUARIOS", "CAMBIAR_ESTADO", "Activar e inactivar usuarios"),
        new DefinicionPermiso("USUARIOS", "ASIGNAR_ROL", "Asignar roles sin conceder privilegios superiores a los propios"),
        new DefinicionPermiso("USUARIOS", "RESTABLECER_CLAVE", "Restablecer contraseñas de usuarios"),
        new DefinicionPermiso("USUARIOS", "ASIGNAR_ADMIN", "Asignar el rol privilegiado ADMIN (operación crítica)"),
        new DefinicionPermiso("CONTRIBUYENTES", "CONSULTAR", "Consultar contribuyentes autorizados"),
        new DefinicionPermiso("CONTRIBUYENTES", "CREAR", "Registrar nuevos contribuyentes"),
        new DefinicionPermiso("CONTRIBUYENTES", "MODIFICAR", "Editar datos fiscales y contacto"),
        new DefinicionPermiso("CONTRIBUYENTES", "CAMBIAR_ESTADO", "Activar o desactivar contribuyentes"),
        new DefinicionPermiso("CONTRIBUYENTES", "GESTIONAR_REGIMENES", "Registrar y modificar vigencias de régimen fiscal"),
        new DefinicionPermiso("CONTRIBUYENTES", "ASIGNAR_USUARIOS", "Administrar usuarios autorizados por contribuyente"),
        new DefinicionPermiso("ROLES", "CONSULTAR", "Consultar el catálogo de roles"),
        new DefinicionPermiso("ROLES", "CREAR", "Crear roles personalizados"),
        new DefinicionPermiso("ROLES", "MODIFICAR", "Editar la descripción de roles"),
        new DefinicionPermiso("ROLES", "CONFIGURAR_PERMISOS", "Asignar o revocar privilegios de roles"),
        new DefinicionPermiso("ROLES", "ELIMINAR", "Eliminar roles personalizados sin usuarios")
    );

    private Permisos() {
    }

    /** @return definiciones inmutables de los permisos existentes */
    public static List<DefinicionPermiso> catalogo() {
        return CATALOGO;
    }

    /**
     * Define recurso, acción y descripción de un permiso atómico.
     *
     * @param recurso nombre del módulo
     * @param accion operación
     * @param descripcion etiqueta visible para el usuario
     */
    public record DefinicionPermiso(String recurso, String accion, String descripcion) {
        /** @return clave de autorización recurso.accion */
        public String clave() { return recurso + "." + accion; }
    }
}
