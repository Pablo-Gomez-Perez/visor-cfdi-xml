package com.kathsoft.visor.app.security;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import com.kathsoft.visor.app.model.entity.PermisoEntity;
import com.kathsoft.visor.app.model.entity.UsuarioEstado;

/**
 * Autorización RBAC evaluada en la base de datos para cada operación.
 *
 * <p>No depende de la identidad almacenada al iniciar sesión ni se limita
 * a habilitar botones Swing. Una revocación en `rol_permiso` tiene efecto
 * en la siguiente consulta. Un cliente con credenciales MySQL directas puede
 * eludir esta protección; requiere servicio intermedio en despliegues no fiables.</p>
 */
public final class AutorizacionService {

    private AutorizacionService() {
    }

    /**
     * Consulta permisos efectivos de una cuenta ACTIVA, mediante el conjunto
     * unión de sus roles. No confía en un conjunto enviado por el cliente.
     *
     * @param em EntityManager abierto y acotado al caso de uso
     * @param usuarioId identificador de la cuenta
     * @return claves recurso.accion actualmente concedidas
     */
    public static Set<String> permisosDe(EntityManager em, Long usuarioId) {
        if (usuarioId == null) {
            return Set.of();
        }
        return em.createQuery(
                "select distinct p from UsuarioEntity u join u.roles r join r.permisos p "
                + "where u.id = :id and u.estado = :estado", PermisoEntity.class)
                .setParameter("id", usuarioId)
                .setParameter("estado", UsuarioEstado.ACTIVO)
                .getResultStream()
                .map(PermisoEntity::getClave)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Comprueba un permiso sin almacenar temporalmente el resultado en sesión.
     *
     * @param em administrador de entidades
     * @param usuarioId usuario de aplicación
     * @param clave permiso solicitado
     * @return true si la cuenta activa dispone del privilegio
     */
    public static boolean tiene(EntityManager em, Long usuarioId, String clave) {
        if (usuarioId == null || clave == null) {
            return false;
        }
        return permisosDe(em, usuarioId).contains(clave);
    }

    /**
     * Rechaza la operación si el usuario no posee el permiso requerido.
     *
     * @param em administrador de entidades
     * @param usuarioId identificador de usuario activo
     * @param clave clave estable del privilegio
     */
    public static void exigir(EntityManager em, Long usuarioId, String clave) {
        if (!tiene(em, usuarioId, clave)) {
            throw new SecurityException("No tiene permiso para " + clave);
        }
    }

    /**
     * Requiere al menos uno de los permisos indicados.
     *
     * @param em administrador de entidades
     * @param usuarioId cuenta activa
     * @param claves permisos alternativos
     */
    public static void exigirAlguno(EntityManager em, Long usuarioId, String... claves) {
        Set<String> efectivos = permisosDe(em, usuarioId);
        for (String clave : claves) {
            if (efectivos.contains(clave)) {
                return;
            }
        }
        throw new SecurityException("La cuenta no dispone de permisos para esta operación");
    }
}
