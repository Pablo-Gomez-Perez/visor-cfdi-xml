package com.kathsoft.visor.app.service.contribuyente;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.kathsoft.visor.app.model.dto.ContribuyenteListado;
import com.kathsoft.visor.app.model.dto.RegimenContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.UsuarioAccesoContribuyente;
import com.kathsoft.visor.app.model.entity.BitacoraEventoEntity;
import com.kathsoft.visor.app.model.entity.ContribuyenteEntity;
import com.kathsoft.visor.app.model.entity.ContribuyenteRegimenEntity;
import com.kathsoft.visor.app.model.entity.ContribuyenteRegimenId;
import com.kathsoft.visor.app.model.entity.UsuarioEntity;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;

/**
 * RF-004: contribuyentes propios, vigencia de sus regímenes fiscales y
 * asignaciones explícitas de usuarios.
 *
 * <p>CRUD habitual con Hibernate/JPA y transacciones desde Java SE.
 * `usuario_contribuyente` limita la visibilidad y las operaciones por
 * contribuyente. El privilegio sensible `CONTRIBUYENTES.ASIGNAR_USUARIOS`
 * permite operar sobre todos los contribuyentes y administrar sus accesos.
 * No se asume que un tercero de CFDI sea un contribuyente propio.</p>
 */
public final class ContribuyentesService {

    private final JpaContext jpa;

    /**
     * @param jpa infraestructura de transacciones y EntityManager
     */
    public ContribuyentesService(JpaContext jpa) {
        this.jpa = Objects.requireNonNull(jpa);
    }

    /**
     * Lista exclusivamente contribuyentes autorizados para el operador, o
     * todos si dispone del permiso global de asignar usuarios.
     *
     * @param operadorId usuario activo
     * @return filas DTO ordenadas por razón social, sin entidades JPA
     */
    public List<ContribuyenteListado> listar(Long operadorId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CONTRIBUYENTES_CONSULTAR);
            List<ContribuyenteEntity> datos;
            if (puedeGestionarAccesos(em, operadorId)) {
                datos = em.createQuery(
                        "select c from ContribuyenteEntity c order by c.nombreRazonSocial, c.id",
                        ContribuyenteEntity.class).getResultList();
            } else {
                datos = em.createQuery(
                        "select distinct c from ContribuyenteEntity c join c.usuariosAutorizados u "
                        + "where u.id = :id order by c.nombreRazonSocial, c.id",
                        ContribuyenteEntity.class)
                        .setParameter("id", operadorId).getResultList();
            }
            return datos.stream().map(ContribuyentesService::dto).toList();
        });
    }

    /**
     * Consulta un contribuyente verificando tanto permiso como alcance.
     *
     * @param operadorId usuario activo
     * @param contribuyenteId identificador del contribuyente
     * @return datos sin secretos ni relaciones JPA
     */
    public ContribuyenteListado obtener(Long operadorId, Long contribuyenteId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CONTRIBUYENTES_CONSULTAR);
            ContribuyenteEntity c = buscar(em, contribuyenteId, false);
            exigirAlcance(em, operadorId, c);
            return dto(c);
        });
    }

    /**
     * Crea un contribuyente y vincula inmediatamente al usuario creador,
     * asegurando que podrá acceder a sus datos cuando carezca de permisos
     * globales de asignación.
     *
     * @param operadorId usuario autorizado
     * @param solicitud datos normalizables
     * @return identificador interno recién creado
     */
    public Long registrar(Long operadorId, SolicitudContribuyente solicitud) {
        SolicitudContribuyente datos = ValidadorContribuyente.normalizar(solicitud);
        return jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CONTRIBUYENTES_CREAR);
            long repetidos = em.createQuery(
                    "select count(c) from ContribuyenteEntity c where c.rfc = :rfc",
                    Long.class).setParameter("rfc", datos.rfc()).getSingleResult();
            if (repetidos != 0) {
                throw new IllegalArgumentException("El RFC ya está registrado como contribuyente");
            }
            ContribuyenteEntity c = new ContribuyenteEntity(
                    datos.rfc(), datos.nombreRazonSocial(),
                    datos.codigoPostalFiscal(), datos.correoContacto());
            UsuarioEntity creador = em.find(UsuarioEntity.class, operadorId);
            c.getUsuariosAutorizados().add(creador);
            em.persist(c);
            em.flush();
            auditar(em, operadorId, c.getId(), "CREAR",
                    "Contribuyente registrado: " + datos.rfc());
            return c.getId();
        });
    }

    /**
     * Modifica los atributos editables. El RFC no se modifica porque es una
     * identidad fiscal que podría estar vinculada con CFDI históricos.
     *
     * @param operadorId cuenta autorizada
     * @param contribuyenteId contribuyente a actualizar
     * @param solicitud datos nuevos; el RFC debe coincidir con el original
     */
    public void modificar(Long operadorId, Long contribuyenteId,
            SolicitudContribuyente solicitud) {
        SolicitudContribuyente datos = ValidadorContribuyente.normalizar(solicitud);
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CONTRIBUYENTES_MODIFICAR);
            ContribuyenteEntity c = buscar(em, contribuyenteId, true);
            exigirAlcance(em, operadorId, c);
            if (!c.getRfc().equals(datos.rfc())) {
                throw new IllegalArgumentException(
                        "El RFC es la identidad fiscal del contribuyente y no puede modificarse");
            }
            c.setNombreRazonSocial(datos.nombreRazonSocial());
            c.setCodigoPostalFiscal(datos.codigoPostalFiscal());
            c.setCorreoContacto(datos.correoContacto());
            auditar(em, operadorId, c.getId(), "MODIFICAR", "Datos de contribuyente modificados");
            return null;
        });
    }

    /**
     * Activa o desactiva sin eliminar CFDI ni relaciones documentales.
     *
     * @param operadorId cuenta autorizada
     * @param contribuyenteId id de contribuyente
     * @param activo estado requerido
     */
    public void cambiarEstado(Long operadorId, Long contribuyenteId, boolean activo) {
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CONTRIBUYENTES_CAMBIAR_ESTADO);
            ContribuyenteEntity c = buscar(em, contribuyenteId, true);
            exigirAlcance(em, operadorId, c);
            c.setActivo(activo);
            auditar(em, operadorId, c.getId(), "CAMBIAR_ESTADO",
                    activo ? "Contribuyente activado" : "Contribuyente desactivado");
            return null;
        });
    }

    /**
     * Recupera el historial registrado de regímenes fiscales de un titular.
     *
     * @param operadorId usuario autorizado
     * @param contribuyenteId contribuyente
     * @return periodos ordenados por inicio y código
     */
    public List<RegimenContribuyente> listarRegimenes(Long operadorId, Long contribuyenteId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CONTRIBUYENTES_CONSULTAR);
            ContribuyenteEntity c = buscar(em, contribuyenteId, false);
            exigirAlcance(em, operadorId, c);
            return em.createQuery(
                    "select r from ContribuyenteRegimenEntity r "
                    + "where r.id.contribuyenteId = :id "
                    + "order by r.id.vigenteDesde desc, r.id.claveRegimen",
                    ContribuyenteRegimenEntity.class)
                    .setParameter("id", contribuyenteId)
                    .getResultStream()
                    .map(r -> new RegimenContribuyente(r.getId().getClaveRegimen(),
                            r.getId().getVigenteDesde(), r.getVigenteHasta()))
                    .toList();
        });
    }

    /**
     * Incorpora una vigencia fiscal sin duplicar ni solapar periodos del
     * mismo código. Se permiten distintos regímenes simultáneos.
     *
     * <p>La clave de tres dígitos se valida estructuralmente; comprobar
     * existencia o vigencia en c_RegimenFiscal requiere importar el catálogo
     * SAT oficial, funcionalidad todavía no implementada.</p>
     *
     * @param operadorId cuenta autorizada
     * @param contribuyenteId contribuyente
     * @param datos periodo que se incorpora
     */
    public void registrarRegimen(Long operadorId, Long contribuyenteId,
            RegimenContribuyente datos) {
        if (datos == null) {
            throw new IllegalArgumentException("Debe proporcionar el régimen fiscal");
        }
        String codigo = ValidadorContribuyente.normalizarRegimen(datos.claveRegimen());
        ValidadorContribuyente.validarVigencia(datos.desde(), datos.hasta());
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId,
                    Permisos.CONTRIBUYENTES_GESTIONAR_REGIMENES);
            ContribuyenteEntity c = buscar(em, contribuyenteId, true);
            exigirAlcance(em, operadorId, c);
            comprobarSolapamiento(em, contribuyenteId, codigo, datos.desde(),
                    datos.hasta(), null);
            ContribuyenteRegimenId clave = new ContribuyenteRegimenId(
                    contribuyenteId, codigo, datos.desde());
            em.persist(new ContribuyenteRegimenEntity(clave, datos.hasta()));
            auditar(em, operadorId, contribuyenteId, "REGISTRAR_REGIMEN",
                    "Régimen " + codigo + " desde " + datos.desde());
            return null;
        });
    }

    /**
     * Ajusta el fin de una vigencia existente. La clave y el inicio
     * pertenecen a la PK y no son editables.
     *
     * @param operadorId cuenta autorizada
     * @param contribuyenteId contribuyente
     * @param claveRegimen clave del periodo
     * @param desde fecha de inicio de la PK
     * @param hasta nueva fecha de término o null
     */
    public void actualizarFinRegimen(Long operadorId, Long contribuyenteId,
            String claveRegimen, LocalDate desde, LocalDate hasta) {
        String codigo = ValidadorContribuyente.normalizarRegimen(claveRegimen);
        ValidadorContribuyente.validarVigencia(desde, hasta);
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId,
                    Permisos.CONTRIBUYENTES_GESTIONAR_REGIMENES);
            ContribuyenteEntity c = buscar(em, contribuyenteId, true);
            exigirAlcance(em, operadorId, c);
            ContribuyenteRegimenId clave = new ContribuyenteRegimenId(
                    contribuyenteId, codigo, desde);
            ContribuyenteRegimenEntity periodo = em.find(ContribuyenteRegimenEntity.class, clave);
            if (periodo == null) {
                throw new IllegalArgumentException("El periodo de régimen no existe");
            }
            comprobarSolapamiento(em, contribuyenteId, codigo, desde, hasta, clave);
            periodo.setVigenteHasta(hasta);
            auditar(em, operadorId, contribuyenteId, "MODIFICAR_REGIMEN",
                    "Régimen " + codigo + " desde " + desde + " actualizado");
            return null;
        });
    }

    /**
     * Elimina un periodo registrado por error. No altera los CFDI originales
     * ni tablas de documentos fiscales.
     *
     * @param operadorId cuenta autorizada
     * @param contribuyenteId contribuyente
     * @param claveRegimen código
     * @param desde inicio de PK
     */
    public void eliminarRegimen(Long operadorId, Long contribuyenteId,
            String claveRegimen, LocalDate desde) {
        String codigo = ValidadorContribuyente.normalizarRegimen(claveRegimen);
        ValidadorContribuyente.validarVigencia(desde, null);
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId,
                    Permisos.CONTRIBUYENTES_GESTIONAR_REGIMENES);
            ContribuyenteEntity c = buscar(em, contribuyenteId, true);
            exigirAlcance(em, operadorId, c);
            ContribuyenteRegimenId clave = new ContribuyenteRegimenId(
                    contribuyenteId, codigo, desde);
            ContribuyenteRegimenEntity encontrado = em.find(ContribuyenteRegimenEntity.class, clave);
            if (encontrado == null) {
                throw new IllegalArgumentException("El régimen fiscal no existe");
            }
            em.remove(encontrado);
            auditar(em, operadorId, contribuyenteId, "ELIMINAR_REGIMEN",
                    "Periodo de régimen " + codigo + " desde " + desde + " eliminado");
            return null;
        });
    }

    /**
     * Lista todas las cuentas y sus asignaciones al contribuyente
     * seleccionado, solo para quien puede gestionar los accesos.
     *
     * @param operadorId operador con permiso específico
     * @param contribuyenteId contribuyente
     * @return usuarios y estado de selección de la matriz
     */
    public List<UsuarioAccesoContribuyente> listarUsuarios(Long operadorId,
            Long contribuyenteId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId,
                    Permisos.CONTRIBUYENTES_ASIGNAR_USUARIOS);
            ContribuyenteEntity c = buscar(em, contribuyenteId, false);
            Set<Long> asignados = c.getUsuariosAutorizados().stream()
                    .map(UsuarioEntity::getId).collect(Collectors.toSet());
            return em.createQuery("select u from UsuarioEntity u order by u.login",
                    UsuarioEntity.class).getResultStream()
                    .map(u -> new UsuarioAccesoContribuyente(u.getId(),
                            u.getLogin(), u.getNombreCompleto(),
                            "ACTIVO".equals(u.getEstado().name()), asignados.contains(u.getId())))
                    .toList();
        });
    }

    /**
     * Reemplaza atómicamente los usuarios explícitamente autorizados.
     * El acceso global de un gestor de permisos permanece independiente de
     * estas asociaciones.
     *
     * @param operadorId usuario con facultad global
     * @param contribuyenteId contribuyente
     * @param usuariosIds conjunto de usuarios (puede quedar vacío)
     */
    public void guardarUsuarios(Long operadorId, Long contribuyenteId,
            Set<Long> usuariosIds) {
        if (usuariosIds == null || usuariosIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("La selección de usuarios es inválida");
        }
        Set<Long> ids = Set.copyOf(usuariosIds);
        jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em, operadorId,
                    Permisos.CONTRIBUYENTES_ASIGNAR_USUARIOS);
            ContribuyenteEntity c = buscar(em, contribuyenteId, true);
            List<UsuarioEntity> nuevos = ids.isEmpty() ? List.of()
                    : em.createQuery("select u from UsuarioEntity u where u.id in :ids",
                            UsuarioEntity.class).setParameter("ids", ids).getResultList();
            if (nuevos.size() != ids.size()) {
                throw new IllegalArgumentException("La lista incluye usuarios inexistentes");
            }
            c.getUsuariosAutorizados().clear();
            c.getUsuariosAutorizados().addAll(nuevos);
            auditar(em, operadorId, contribuyenteId, "ASIGNAR_USUARIOS",
                    "Asignación de usuarios del contribuyente: " + ids.size());
            return null;
        });
    }

    private static void comprobarSolapamiento(EntityManager em, Long contribuyenteId,
            String codigo, LocalDate desde, LocalDate hasta,
            ContribuyenteRegimenId ignorar) {
        List<ContribuyenteRegimenEntity> periodos = em.createQuery(
                "select r from ContribuyenteRegimenEntity r "
                + "where r.id.contribuyenteId = :id and r.id.claveRegimen = :codigo",
                ContribuyenteRegimenEntity.class)
                .setParameter("id", contribuyenteId)
                .setParameter("codigo", codigo).getResultList();
        for (ContribuyenteRegimenEntity existente : periodos) {
            ContribuyenteRegimenId clave = existente.getId();
            if (clave.equals(ignorar)) continue;
            LocalDate inicioExistente = clave.getVigenteDesde();
            LocalDate finExistente = existente.getVigenteHasta();
            boolean esteIniciaAntesDeFin = finExistente == null
                    || !desde.isAfter(finExistente);
            boolean otroIniciaAntesDeFin = hasta == null
                    || !inicioExistente.isAfter(hasta);
            if (esteIniciaAntesDeFin && otroIniciaAntesDeFin) {
                throw new IllegalArgumentException(
                        "El régimen ya tiene un periodo de vigencia superpuesto");
            }
        }
    }

    private static boolean puedeGestionarAccesos(EntityManager em, Long usuarioId) {
        return AutorizacionService.tiene(em, usuarioId,
                Permisos.CONTRIBUYENTES_ASIGNAR_USUARIOS);
    }

    private static void exigirAlcance(EntityManager em, Long operadorId,
            ContribuyenteEntity c) {
        if (puedeGestionarAccesos(em, operadorId)) {
            return;
        }
        if (c.getUsuariosAutorizados().stream()
                .noneMatch(u -> u.getId().equals(operadorId))) {
            throw new SecurityException("No tiene acceso al contribuyente seleccionado");
        }
    }

    private static ContribuyenteEntity buscar(EntityManager em, Long id, boolean bloqueo) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Seleccione un contribuyente válido");
        }
        ContribuyenteEntity c = bloqueo
                ? em.find(ContribuyenteEntity.class, id, LockModeType.PESSIMISTIC_WRITE)
                : em.find(ContribuyenteEntity.class, id);
        if (c == null) {
            throw new IllegalArgumentException("El contribuyente no existe");
        }
        return c;
    }

    private static ContribuyenteListado dto(ContribuyenteEntity c) {
        return new ContribuyenteListado(c.getId(), c.getRfc(), c.getNombreRazonSocial(),
                c.getCodigoPostalFiscal(), c.getCorreoContacto(), Boolean.TRUE.equals(c.getActivo()));
    }

    private static void auditar(EntityManager em, Long operador, Long contribuyenteId,
            String accion, String motivo) {
        em.persist(new BitacoraEventoEntity(operador, "contribuyente",
                contribuyenteId, accion, motivo));
    }
}
