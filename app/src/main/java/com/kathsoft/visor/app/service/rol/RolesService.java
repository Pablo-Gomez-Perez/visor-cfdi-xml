package com.kathsoft.visor.app.service.rol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.kathsoft.visor.app.model.dto.MatrizPermisosRol;
import com.kathsoft.visor.app.model.dto.PermisoSeleccionable;
import com.kathsoft.visor.app.model.dto.RolListado;
import com.kathsoft.visor.app.model.entity.BitacoraEventoEntity;
import com.kathsoft.visor.app.model.entity.PermisoEntity;
import com.kathsoft.visor.app.model.entity.RolEntity;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;

/**
 * RF-003: CRUD del catálogo de roles y matriz de privilegios configurables.
 *
 * <p>Se respetan las tablas `rol`, `permiso` y `rol_permiso` del esquema
 * vigente. Las transacciones se confirman o revierten desde Java. Cada
 * operación comprueba los permisos efectivos de un usuario activo.</p>
 */
public final class RolesService {

    private final JpaContext jpa;

    /**
     * @param jpa contexto compartido de operaciones transaccionales Java SE
     */
    public RolesService(JpaContext jpa) {
        this.jpa = jpa;
    }

    /**
     * Inserta idempotentemente el catálogo de privilegios realmente
     * implementados, sin alterar las tablas SQL.
     *
     * <p>La primera vez que se habilita RF-003, el ADMIN existente recibe los
     * permisos iniciales para conservar las funciones RF-002. Después de
     * asignar privilegios, los cambios del operador nunca se restauran
     * automáticamente al reiniciar. El permiso de gestionar privilegios se
     * conserva obligatoriamente para ADMIN como mecanismo de recuperación.</p>
     */
    public void inicializarCatalogo() {
        jpa.enTransaccion(em -> {
            RolEntity administrador = em.createQuery(
                    "select r from RolEntity r where r.clave = :clave", RolEntity.class)
                    .setParameter("clave", "ADMIN")
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();

            for (Permisos.DefinicionPermiso item : Permisos.catalogo()) {
                em.createNativeQuery(
                    "INSERT IGNORE INTO permiso (recurso, accion, descripcion) "
                    + "VALUES (:recurso, :accion, :descripcion)")
                    .setParameter("recurso", item.recurso())
                    .setParameter("accion", item.accion())
                    .setParameter("descripcion", item.descripcion())
                    .executeUpdate();
            }

            List<PermisoEntity> disponibles = em.createQuery(
                    "select p from PermisoEntity p order by p.recurso, p.accion",
                    PermisoEntity.class).getResultList();
            boolean primeraConfiguracion = administrador.getPermisos().isEmpty();
            if (primeraConfiguracion) {
                administrador.getPermisos().addAll(disponibles);
            } else {
                disponibles.stream()
                    .filter(p -> Permisos.PERMISO_RECUPERACION.equals(p.getClave()))
                    .findFirst()
                    .ifPresent(p -> administrador.getPermisos().add(p));
            }
            return null;
        });
    }

    /**
     * Comprueba un permiso de sesión contra MySQL, no contra permisos cacheados.
     *
     * @param usuarioId identificador activo
     * @param clave clave del privilegio
     * @return permiso efectivo actual
     */
    public boolean tienePermiso(Long usuarioId, String clave) {
        return jpa.consultar(em -> AutorizacionService.tiene(em, usuarioId, clave));
    }

    /**
     * Consulta el conjunto completo de privilegios actuales del usuario.
     *
     * @param usuarioId cuenta activa
     * @return claves autorizadas
     */
    public Set<String> permisosDe(Long usuarioId) {
        return jpa.consultar(em -> AutorizacionService.permisosDe(em, usuarioId));
    }

    /**
     * Lista los roles con su cantidad de privilegios y cuentas.
     *
     * @param operadorId usuario que consulta
     * @return catálogo de roles desacoplado de JPA
     */
    public List<RolListado> listar(Long operadorId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigirAlguno(em, operadorId,
                    Permisos.ROLES_CONSULTAR, Permisos.ROLES_CONFIGURAR_PERMISOS,
                    Permisos.ROLES_CREAR, Permisos.ROLES_MODIFICAR, Permisos.ROLES_ELIMINAR);
            List<RolEntity> roles = em.createQuery(
                    "select distinct r from RolEntity r left join fetch r.permisos order by r.clave",
                    RolEntity.class).getResultList();
            return roles.stream().map(r -> convertir(em, r)).toList();
        });
    }

    /**
     * Lista claves que pueden asignarse a un usuario.
     *
     * @param operadorId cuenta autorizada para crear usuarios o cambiar roles
     * @return lista de claves para el JComboBox
     */
    public List<String> listarClavesAsignables(Long operadorId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigirAlguno(em, operadorId,
                    Permisos.USUARIOS_CREAR, Permisos.USUARIOS_ASIGNAR_ROL);
            return em.createQuery(
                    "select r.clave from RolEntity r order by r.clave", String.class)
                    .getResultList();
        });
    }

    /**
     * Crea un rol personalizado sin permisos iniciales (mínimo privilegio).
     *
     * @param operadorId cuenta autorizada
     * @param clave clave nueva, mayúscula
     * @param descripcion descripción para la interfaz
     * @return identificador creado
     */
    public Short crear(Long operadorId, String clave, String descripcion) {
        String normalizada = normalizarClave(clave);
        String texto = normalizarDescripcion(descripcion);
        return jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.ROLES_CREAR);
            Long repetidos = em.createQuery(
                    "select count(r) from RolEntity r where r.clave = :clave", Long.class)
                    .setParameter("clave", normalizada).getSingleResult();
            if (repetidos > 0) {
                throw new IllegalArgumentException("Ya existe un rol con la clave " + normalizada);
            }
            RolEntity rol = new RolEntity(normalizada, texto);
            em.persist(rol);
            em.flush();
            registrarEvento(em, operadorId, rol.getId(), "CREAR",
                    "Rol creado: " + normalizada);
            return rol.getId();
        });
    }

    /**
     * Modifica la descripción sin cambiar la clave de negocio, usuarios
     * asociados ni permisos existentes.
     *
     * @param operadorId usuario autorizado
     * @param rolId identificador del rol
     * @param descripcion nueva descripción
     */
    public void modificar(Long operadorId, Short rolId, String descripcion) {
        String texto = normalizarDescripcion(descripcion);
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.ROLES_MODIFICAR);
            RolEntity rol = obtenerRol(em, rolId, true);
            rol.setDescripcion(texto);
            registrarEvento(em, operadorId, rol.getId(), "MODIFICAR",
                    "Descripción modificada del rol " + rol.getClave());
            return null;
        });
    }

    /**
     * Recupera todas las casillas de privilegios de un rol.
     *
     * @param operadorId usuario autorizado a configurar permisos
     * @param rolId rol de la matriz
     * @return matriz y selección actual, sin entidades persistentes
     */
    public MatrizPermisosRol cargarMatriz(Long operadorId, Short rolId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.ROLES_CONFIGURAR_PERMISOS);
            RolEntity rol = obtenerRol(em, rolId, false);
            Set<Short> asignados = rol.getPermisos().stream()
                    .map(PermisoEntity::getId).collect(Collectors.toUnmodifiableSet());
            List<PermisoSeleccionable> opciones = em.createQuery(
                    "select p from PermisoEntity p order by p.recurso, p.accion",
                    PermisoEntity.class).getResultStream()
                    .map(p -> new PermisoSeleccionable(p.getId(), p.getRecurso(),
                            p.getAccion(), p.getDescripcion(), asignados.contains(p.getId()),
                            "ADMIN".equals(rol.getClave())
                                && Permisos.PERMISO_RECUPERACION.equals(p.getClave())))
                    .toList();
            return new MatrizPermisosRol(convertir(em, rol), opciones);
        });
    }

    /**
     * Sustituye atómicamente las relaciones rol_permiso seleccionadas.
     *
     * <p>Se bloquea la fila del rol para serializar los cambios de su matriz.
     * La comprobación final conserva el permiso de recuperación de ADMIN.
     * Los cambios se auditan en la misma transacción.</p>
     *
     * @param operadorId usuario que configura
     * @param rolId identificador de rol
     * @param seleccionados ids del catálogo permiso
     */
    public void guardarPermisos(Long operadorId, Short rolId, Set<Short> seleccionados) {
        if (seleccionados == null || seleccionados.contains(null)) {
            throw new IllegalArgumentException("La selección de permisos no es válida");
        }
        Set<Short> ids = Set.copyOf(seleccionados);
        jpa.enTransaccion(em -> {
            RolEntity rol = obtenerRol(em, rolId, true);
            AutorizacionService.exigir(em, operadorId, Permisos.ROLES_CONFIGURAR_PERMISOS);

            List<PermisoEntity> nuevos = ids.isEmpty()
                    ? List.of()
                    : em.createQuery("select p from PermisoEntity p where p.id in :ids",
                            PermisoEntity.class)
                        .setParameter("ids", ids).getResultList();
            if (nuevos.size() != ids.size()) {
                throw new IllegalArgumentException("La selección incluye permisos inexistentes");
            }
            if ("ADMIN".equals(rol.getClave()) && nuevos.stream()
                    .noneMatch(p -> Permisos.PERMISO_RECUPERACION.equals(p.getClave()))) {
                throw new IllegalStateException(
                        "ADMIN debe conservar el permiso de administrar privilegios");
            }
            Set<String> anteriores = rol.getPermisos().stream()
                    .map(PermisoEntity::getClave).collect(Collectors.toCollection(HashSet::new));
            Set<String> posteriores = nuevos.stream()
                    .map(PermisoEntity::getClave).collect(Collectors.toCollection(HashSet::new));

            rol.getPermisos().clear();
            rol.getPermisos().addAll(nuevos);

            Set<String> agregados = new HashSet<>(posteriores);
            agregados.removeAll(anteriores);
            Set<String> quitados = new HashSet<>(anteriores);
            quitados.removeAll(posteriores);
            String motivo = "Rol " + rol.getClave() + " | +"
                    + agregados.stream().sorted().collect(Collectors.joining(","))
                    + " | -" + quitados.stream().sorted().collect(Collectors.joining(","));
            if (motivo.length() > 600) {
                motivo = motivo.substring(0, 597) + "...";
            }
            registrarEvento(em, operadorId, rol.getId(), "ASIGNAR_PERMISOS", motivo);
            return null;
        });
    }

    /**
     * Elimina únicamente un rol personalizado sin usuarios asociados.
     * Las claves ADMIN y CONSULTA son parte del bootstrap y no se borran.
     *
     * @param operadorId usuario autorizado
     * @param rolId rol eliminable
     */
    public void eliminar(Long operadorId, Short rolId) {
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.ROLES_ELIMINAR);
            RolEntity rol = obtenerRol(em, rolId, true);
            if ("ADMIN".equals(rol.getClave()) || "CONSULTA".equals(rol.getClave())) {
                throw new IllegalStateException("No se puede eliminar un rol básico del sistema");
            }
            Long relacionados = em.createQuery(
                    "select count(distinct u) from UsuarioEntity u join u.roles r "
                    + "where r.id = :id", Long.class)
                    .setParameter("id", rolId).getSingleResult();
            if (relacionados > 0) {
                throw new IllegalStateException(
                        "El rol tiene usuarios asociados; reasígnelos antes de eliminar");
            }
            String clave = rol.getClave();
            rol.getPermisos().clear();
            em.flush();
            em.remove(rol);
            registrarEvento(em, operadorId, rolId, "ELIMINAR",
                    "Rol personalizado eliminado: " + clave);
            return null;
        });
    }

    private static RolListado convertir(EntityManager em, RolEntity rol) {
        long usuarios = em.createQuery(
                "select count(distinct u) from UsuarioEntity u join u.roles r "
                + "where r.id = :id", Long.class)
                .setParameter("id", rol.getId()).getSingleResult();
        return new RolListado(rol.getId(), rol.getClave(), rol.getDescripcion(),
                rol.getPermisos().size(), usuarios);
    }

    private static RolEntity obtenerRol(EntityManager em, Short id, boolean bloquear) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Seleccione un rol válido");
        }
        RolEntity rol = em.find(RolEntity.class, id,
                bloquear ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (rol == null) {
            throw new IllegalArgumentException("No existe el rol seleccionado");
        }
        return rol;
    }

    private static String normalizarClave(String clave) {
        if (clave == null) {
            throw new IllegalArgumentException("La clave del rol es obligatoria");
        }
        String normalizada = clave.strip().toUpperCase(Locale.ROOT);
        if (!normalizada.matches("[A-Z][A-Z0-9_]{2,39}")) {
            throw new IllegalArgumentException("La clave requiere 3-40 caracteres A-Z, 0-9 o _");
        }
        return normalizada;
    }

    private static String normalizarDescripcion(String descripcion) {
        if (descripcion == null || descripcion.isBlank()
                || descripcion.strip().length() > 200) {
            throw new IllegalArgumentException("La descripción debe contener entre 1 y 200 caracteres");
        }
        return descripcion.strip();
    }

    private static void registrarEvento(EntityManager em, Long operadorId,
            Short rolId, String accion, String motivo) {
        em.persist(new BitacoraEventoEntity(operadorId, "rol",
                Long.valueOf(rolId), accion, motivo));
    }
}
