package com.kathsoft.visor.app.service.tercero;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.kathsoft.visor.app.model.dto.SolicitudTercero;
import com.kathsoft.visor.app.model.dto.TerceroListado;
import com.kathsoft.visor.app.model.entity.BitacoraEventoEntity;
import com.kathsoft.visor.app.model.entity.ContribuyenteEntity;
import com.kathsoft.visor.app.model.entity.ContribuyenteTerceroRolEntity;
import com.kathsoft.visor.app.model.entity.ContribuyenteTerceroRolId;
import com.kathsoft.visor.app.model.entity.TerceroEntity;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;

/**
 * RF-005: contrapartes fiscales y sus funciones por contribuyente.
 *
 * <p>El catálogo global de terceros no implica visibilidad global: cada
 * consulta y operación sobre una contraparte exige un vínculo autorizado
 * con el contribuyente y permisos de acción efectivos.</p>
 */
public final class TercerosService {
    private static final Set<String> ROLES = Set.of("CLIENTE", "PROVEEDOR", "PRESTADOR", "OTRO");
    private final JpaContext jpa;

    /** @param jpa gestor de transacciones compartido */
    public TercerosService(JpaContext jpa) {
        this.jpa = Objects.requireNonNull(jpa);
    }

    /** @return terceros relacionados con el contribuyente indicado */
    public List<TerceroListado> listar(Long operadorId, Long contribuyenteId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.TERCEROS_CONSULTAR);
            exigirAlcance(em, operadorId, contribuyenteId);
            return em.createQuery(
                    "select distinct t from TerceroEntity t, ContribuyenteTerceroRolEntity r "
                    + "where r.id.terceroId = t.id and r.id.contribuyenteId = :id "
                    + "order by t.nombreRazonSocial, t.id", TerceroEntity.class)
                    .setParameter("id", contribuyenteId).getResultStream()
                    .map(TercerosService::dto).toList();
        });
    }

    /** @return tercero relacionado con el titular o error de autorización */
    public TerceroListado obtener(Long operadorId, Long contribuyenteId, Long terceroId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.TERCEROS_CONSULTAR);
            exigirAlcance(em, operadorId, contribuyenteId);
            exigirRelacion(em, contribuyenteId, terceroId);
            return dto(buscar(em, terceroId, false));
        });
    }

    /**
     * Crea una contraparte vinculada a un contribuyente y función.
     * No deduplica automáticamente RFC genéricos ni personas homónimas.
     */
    public Long registrar(Long operadorId, Long contribuyenteId,
            SolicitudTercero solicitud, String rol) {
        SolicitudTercero datos = ValidadorTercero.normalizar(solicitud);
        String funcion = normalizarRol(rol);
        return jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.TERCEROS_CREAR);
            exigirAlcance(em, operadorId, contribuyenteId);
            TerceroEntity t = new TerceroEntity(datos.rfc(), datos.nombreRazonSocial(),
                    datos.paisFiscal(), datos.numeroRegistroFiscalExtranjero());
            em.persist(t);
            em.flush();
            em.persist(new ContribuyenteTerceroRolEntity(
                    new ContribuyenteTerceroRolId(contribuyenteId, t.getId(), funcion)));
            auditar(em, operadorId, t.getId(), "CREAR", "Tercero registrado");
            return t.getId();
        });
    }

    /**
     * Actualiza únicamente atributos no identificadores. La modificación de
     * un tercero compartido requiere permisos de modificación sobre TODOS
     * los contribuyentes vinculados para evitar cambios entre titulares.
     */
    public void modificar(Long operadorId, Long contribuyenteId,
            Long terceroId, SolicitudTercero solicitud) {
        SolicitudTercero datos = ValidadorTercero.normalizar(solicitud);
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.TERCEROS_MODIFICAR);
            exigirAlcance(em, operadorId, contribuyenteId);
            TerceroEntity t = buscar(em, terceroId, true);
            exigirRelacion(em, contribuyenteId, terceroId);
            List<Long> titulares = em.createQuery(
                    "select distinct r.id.contribuyenteId from ContribuyenteTerceroRolEntity r "
                    + "where r.id.terceroId = :id", Long.class)
                    .setParameter("id", terceroId).getResultList();
            for (Long titular : titulares) exigirAlcance(em, operadorId, titular);
            if (!Objects.equals(t.getRfc(), datos.rfc())
                    || !Objects.equals(t.getNumeroRegistroFiscalExtranjero(),
                            datos.numeroRegistroFiscalExtranjero())) {
                throw new IllegalArgumentException(
                        "La identidad fiscal no se modifica desde edición ordinaria");
            }
            t.setNombreRazonSocial(datos.nombreRazonSocial());
            t.setPaisFiscal(datos.paisFiscal());
            auditar(em, operadorId, terceroId, "MODIFICAR", "Datos de tercero modificados");
            return null;
        });
    }

    /** Vincula un tercero existente con un titular sin duplicar la identidad. */
    public void asignarRol(Long operadorId, Long contribuyenteId,
            Long terceroId, String rol) {
        String funcion = normalizarRol(rol);
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.TERCEROS_ASIGNAR_ROLES);
            exigirAlcance(em, operadorId, contribuyenteId);
            buscar(em, terceroId, false);
            ContribuyenteTerceroRolId id =
                    new ContribuyenteTerceroRolId(contribuyenteId, terceroId, funcion);
            if (em.find(ContribuyenteTerceroRolEntity.class, id) == null) {
                em.persist(new ContribuyenteTerceroRolEntity(id));
                auditar(em, operadorId, terceroId, "ASIGNAR_ROL",
                        "Función " + funcion + " en contribuyente " + contribuyenteId);
            }
            return null;
        });
    }

    /** Elimina solo la relación comercial, sin borrar el tercero ni sus CFDI. */
    public void retirarRol(Long operadorId, Long contribuyenteId,
            Long terceroId, String rol) {
        String funcion = normalizarRol(rol);
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.TERCEROS_ASIGNAR_ROLES);
            exigirAlcance(em, operadorId, contribuyenteId);
            ContribuyenteTerceroRolEntity entidad = em.find(
                    ContribuyenteTerceroRolEntity.class,
                    new ContribuyenteTerceroRolId(contribuyenteId, terceroId, funcion));
            if (entidad == null) {
                throw new IllegalArgumentException("La función no está asignada");
            }
            em.remove(entidad);
            auditar(em, operadorId, terceroId, "RETIRAR_ROL",
                    "Función " + funcion + " retirada del contribuyente " + contribuyenteId);
            return null;
        });
    }

    /** @return funciones asignadas a una contraparte dentro de un titular */
    public Set<String> listarRoles(Long operadorId, Long contribuyenteId, Long terceroId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.TERCEROS_CONSULTAR);
            exigirAlcance(em, operadorId, contribuyenteId);
            exigirRelacion(em, contribuyenteId, terceroId);
            return Set.copyOf(em.createQuery(
                    "select r.id.rol from ContribuyenteTerceroRolEntity r "
                    + "where r.id.contribuyenteId = :c and r.id.terceroId = :t",
                    String.class).setParameter("c", contribuyenteId)
                    .setParameter("t", terceroId).getResultList());
        });
    }

    private static String normalizarRol(String rol) {
        if (rol == null) throw new IllegalArgumentException("La función es obligatoria");
        String valor = rol.strip().toUpperCase(java.util.Locale.ROOT);
        if (!ROLES.contains(valor)) throw new IllegalArgumentException("Función no permitida");
        return valor;
    }

    private static TerceroEntity buscar(EntityManager em, Long id, boolean bloquear) {
        if (id == null) throw new IllegalArgumentException("Tercero no especificado");
        TerceroEntity tercero = bloquear
                ? em.find(TerceroEntity.class, id, LockModeType.PESSIMISTIC_WRITE)
                : em.find(TerceroEntity.class, id);
        if (tercero == null) throw new IllegalArgumentException("Tercero inexistente");
        return tercero;
    }

    private static void exigirAlcance(EntityManager em, Long usuarioId, Long contribuyenteId) {
        if (contribuyenteId == null) throw new IllegalArgumentException("Falta contribuyente");
        ContribuyenteEntity titular = em.find(ContribuyenteEntity.class, contribuyenteId);
        if (titular == null) throw new IllegalArgumentException("Contribuyente inexistente");
        if (AutorizacionService.tiene(em, usuarioId, Permisos.CONTRIBUYENTES_ASIGNAR_USUARIOS)) {
            return;
        }
        boolean vinculado = em.createQuery(
                "select count(c) from ContribuyenteEntity c join c.usuariosAutorizados u "
                + "where c.id = :c and u.id = :u", Long.class)
                .setParameter("c", contribuyenteId).setParameter("u", usuarioId)
                .getSingleResult() > 0;
        if (!vinculado) throw new SecurityException("Sin acceso al contribuyente");
    }

    private static void exigirRelacion(EntityManager em, Long contribuyenteId, Long terceroId) {
        long cantidad = em.createQuery(
                "select count(r) from ContribuyenteTerceroRolEntity r "
                + "where r.id.contribuyenteId = :c and r.id.terceroId = :t", Long.class)
                .setParameter("c", contribuyenteId).setParameter("t", terceroId)
                .getSingleResult();
        if (cantidad == 0) throw new SecurityException("Tercero ajeno al contribuyente");
    }

    private static TerceroListado dto(TerceroEntity t) {
        return new TerceroListado(t.getId(), t.getRfc(), t.getNombreRazonSocial(),
                t.getPaisFiscal(), t.getNumeroRegistroFiscalExtranjero());
    }

    private static void auditar(EntityManager em, Long operadorId,
            Long terceroId, String accion, String motivo) {
        em.persist(new BitacoraEventoEntity(operadorId, "tercero", terceroId, accion, motivo));
    }
}
