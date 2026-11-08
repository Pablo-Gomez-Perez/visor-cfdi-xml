package com.kathsoft.visor.app.service.usuario;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.UsuarioListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.model.entity.BitacoraEventoEntity;
import com.kathsoft.visor.app.model.entity.RolEntity;
import com.kathsoft.visor.app.model.entity.UsuarioEntity;
import com.kathsoft.visor.app.model.entity.UsuarioEstado;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.PasswordHasher;

/**
 * Casos de uso de RF-002. Toda operación de escritura es transaccional desde
 * Java, sin SQL ni sesiones Hibernate dentro de formularios Swing.
 *
 * <p>Los permisos se verifican nuevamente contra la BD por operación,
 * incluso cuando la interfaz presenta botones habilitados.</p>
 */
public final class UsuariosService {

    public static final String ADMIN = "ADMIN";
    public static final String CONSULTA = "CONSULTA";

    private final JpaContext jpa;

    /**
     * @param jpa infraestructura de transacciones del proceso
     */
    public UsuariosService(JpaContext jpa) {
        this.jpa = jpa;
    }

    /**
     * Registra idempotentemente los dos roles básicos definidos para RF-002.
     * La administración granular de permisos corresponderá a RF-003.
     */
    public void inicializarRoles() {
        jpa.enTransaccion(em -> {
            em.createNativeQuery("INSERT IGNORE INTO rol (clave, descripcion) "
                    + "VALUES ('ADMIN','Administrador del sistema'),"
                    + " ('CONSULTA','Usuario con acceso de consulta')").executeUpdate();
            return null;
        });
    }

    /**
     * Determina si debe mostrarse el asistente de primer administrador.
     *
     * @return true cuando la tabla usuario aún no contiene cuentas
     */
    public boolean requiereAdministrador() {
        return jpa.consultar(em -> em.createQuery(
                "select count(u) from UsuarioEntity u", Long.class)
                .getSingleResult() == 0);
    }

    /**
     * Crea exactamente una cuenta administradora en una BD sin usuarios.
     *
     * <p>La fila del rol ADMIN se bloquea transaccionalmente para impedir
     * registros iniciales concurrentes; no hay usuario predeterminado.</p>
     *
     * @param solicitud datos de la primera cuenta
     * @param clave contraseña temporal
     * @return cuenta creada
     */
    public UsuarioSesion registrarAdministradorInicial(SolicitudUsuario solicitud, char[] clave) {
        validarSolicitud(solicitud);
        PasswordHasher.validarNueva(clave);
        if (!ADMIN.equals(solicitud.rol())) {
            throw new IllegalArgumentException("El primer usuario debe ser ADMIN");
        }
        String hash = PasswordHasher.crearHash(clave);
        return jpa.enTransaccion(em -> {
            RolEntity rol = obtenerRolBloqueado(em, ADMIN);
            long cantidad = em.createQuery("select count(u) from UsuarioEntity u", Long.class)
                    .getSingleResult();
            if (cantidad != 0) {
                throw new IllegalStateException("El administrador inicial ya fue registrado");
            }
            UsuarioEntity usuario = nuevoUsuario(solicitud, hash);
            usuario.getRoles().add(rol);
            em.persist(usuario);
            em.flush();
            registrarEvento(em, usuario.getId(), usuario.getId(), "CREACION_ADMIN_INICIAL");
            return sesion(usuario);
        });
    }

    /**
     * Autentica una cuenta activa y registra la fecha de último acceso.
     *
     * @param login identificador de acceso
     * @param clave contraseña proporcionada
     * @return identidad y roles de la sesión
     * @throws SecurityException si las credenciales o el estado no permiten acceder
     */
    public UsuarioSesion autenticar(String login, char[] clave) {
        if (login == null || login.isBlank() || clave == null) {
            throw new SecurityException("Usuario o contraseña incorrectos");
        }
        return jpa.enTransaccion(em -> {
            List<UsuarioEntity> usuarios = em.createQuery(
                    "select distinct u from UsuarioEntity u left join fetch u.roles "
                    + "where u.login = :login", UsuarioEntity.class)
                    .setParameter("login", login.strip().toLowerCase(Locale.ROOT))
                    .getResultList();
            if (usuarios.isEmpty()) {
                throw new SecurityException("Usuario o contraseña incorrectos");
            }
            UsuarioEntity usuario = usuarios.get(0);
            if (usuario.getEstado() != UsuarioEstado.ACTIVO
                    || !PasswordHasher.verificar(clave, usuario.getHashContrasena())) {
                throw new SecurityException("Usuario o contraseña incorrectos");
            }
            usuario.setUltimoAcceso(LocalDateTime.now());
            return sesion(usuario);
        });
    }

    /**
     * Lista usuarios sin exponer las contraseñas.
     *
     * @param administradorId cuenta responsable, validada contra MySQL
     * @return lista ordenada para el JTable
     */
    public List<UsuarioListado> listar(Long administradorId) {
        return jpa.consultar(em -> {
            asegurarAdministrador(em, administradorId);
            return em.createQuery("select distinct u from UsuarioEntity u left join fetch u.roles",
                    UsuarioEntity.class).getResultList().stream()
                    .map(u -> new UsuarioListado(u.getId(), u.getNombreCompleto(), u.getLogin(),
                            u.getCorreo(), u.getEstado().name(),
                            u.getRoles().stream().map(RolEntity::getClave).sorted()
                                    .collect(Collectors.joining(", "))))
                    .sorted(Comparator.comparing(UsuarioListado::login))
                    .toList();
        });
    }

    /**
     * Crea una cuenta ADMIN o CONSULTA tras verificar al operador.
     *
     * @param administradorId identificador del administrador autenticado
     * @param solicitud datos nuevos
     * @param clave contraseña inicial
     */
    public void registrar(Long administradorId, SolicitudUsuario solicitud, char[] clave) {
        validarSolicitud(solicitud);
        PasswordHasher.validarNueva(clave);
        String hash = PasswordHasher.crearHash(clave);
        jpa.enTransaccion(em -> {
            asegurarAdministrador(em, administradorId);
            RolEntity rol = obtenerRolBloqueado(em, solicitud.rol());
            UsuarioEntity usuario = nuevoUsuario(solicitud, hash);
            usuario.getRoles().add(rol);
            em.persist(usuario);
            em.flush();
            registrarEvento(em, administradorId, usuario.getId(), "CREAR");
            return null;
        });
    }

    /**
     * Edita el nombre y correo, preservando login, roles y clave.
     *
     * @param administradorId administrador autenticado
     * @param usuarioId usuario objetivo
     * @param nombre nuevo nombre
     * @param correo nuevo correo, opcional
     */
    public void modificarDatos(Long administradorId, Long usuarioId, String nombre, String correo) {
        String nuevoNombre = nombreNormalizado(nombre);
        String nuevoCorreo = correoNormalizado(correo);
        jpa.enTransaccion(em -> {
            asegurarAdministrador(em, administradorId);
            UsuarioEntity usuario = buscarUsuario(em, usuarioId);
            usuario.setNombreCompleto(nuevoNombre);
            usuario.setCorreo(nuevoCorreo);
            registrarEvento(em, administradorId, usuarioId, "MODIFICAR");
            return null;
        });
    }

    /**
     * Activa o desactiva una cuenta. Nunca permite desactivar el último ADMIN
     * activo, aunque la operación se inicie desde otra ventana o proceso.
     *
     * @param administradorId identificador del administrador
     * @param usuarioId cuenta objetivo
     * @param nuevoEstado ACTIVO o INACTIVO
     */
    public void cambiarEstado(Long administradorId, Long usuarioId, UsuarioEstado nuevoEstado) {
        if (nuevoEstado != UsuarioEstado.ACTIVO && nuevoEstado != UsuarioEstado.INACTIVO) {
            throw new IllegalArgumentException("Estado no permitido para esta operación");
        }
        jpa.enTransaccion(em -> {
            asegurarAdministrador(em, administradorId);
            bloquearRolAdmin(em);
            UsuarioEntity usuario = buscarUsuarioConRoles(em, usuarioId);
            if (nuevoEstado == UsuarioEstado.INACTIVO) {
                verificarNoEsUltimoAdmin(em, usuario);
            }
            usuario.setEstado(nuevoEstado);
            registrarEvento(em, administradorId, usuarioId, "CAMBIAR_ESTADO");
            return null;
        });
    }

    /**
     * Sustituye el rol básico de una cuenta. La administración granular de
     * permisos se implementará en RF-003.
     *
     * @param administradorId identificador del administrador
     * @param usuarioId cuenta objetivo
     * @param nuevaClave ADMIN o CONSULTA
     */
    public void cambiarRol(Long administradorId, Long usuarioId, String nuevaClave) {
        validarRol(nuevaClave);
        jpa.enTransaccion(em -> {
            asegurarAdministrador(em, administradorId);
            bloquearRolAdmin(em);
            UsuarioEntity usuario = buscarUsuarioConRoles(em, usuarioId);
            if (!ADMIN.equals(nuevaClave)) {
                verificarNoEsUltimoAdmin(em, usuario);
            }
            RolEntity rol = obtenerRol(em, nuevaClave);
            usuario.getRoles().clear();
            usuario.getRoles().add(rol);
            registrarEvento(em, administradorId, usuarioId, "CAMBIAR_ROL");
            return null;
        });
    }

    /**
     * Reemplaza el hash de la contraseña, sin guardar el secreto en bitácora.
     *
     * @param administradorId identificador del administrador
     * @param usuarioId cuenta objetivo
     * @param claveNueva nueva contraseña temporal
     */
    public void cambiarContrasena(Long administradorId, Long usuarioId, char[] claveNueva) {
        PasswordHasher.validarNueva(claveNueva);
        String hash = PasswordHasher.crearHash(claveNueva);
        jpa.enTransaccion(em -> {
            asegurarAdministrador(em, administradorId);
            UsuarioEntity usuario = buscarUsuario(em, usuarioId);
            usuario.setHashContrasena(hash);
            registrarEvento(em, administradorId, usuarioId, "RESTABLECER_CONTRASENA");
            return null;
        });
    }

    private static UsuarioEntity nuevoUsuario(SolicitudUsuario s, String hash) {
        return new UsuarioEntity(nombreNormalizado(s.nombre()),
                s.login().strip().toLowerCase(Locale.ROOT),
                correoNormalizado(s.correo()), hash);
    }

    private static String nombreNormalizado(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.strip().length() > 200) {
            throw new IllegalArgumentException("El nombre debe contener entre 1 y 200 caracteres");
        }
        return nombre.strip();
    }

    private static String correoNormalizado(String correo) {
        if (correo == null || correo.isBlank()) {
            return null;
        }
        String resultado = correo.strip().toLowerCase(Locale.ROOT);
        if (resultado.length() > 254 || !resultado.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("El correo electrónico no es válido");
        }
        return resultado;
    }

    private static void validarSolicitud(SolicitudUsuario s) {
        if (s == null) {
            throw new IllegalArgumentException("La solicitud es obligatoria");
        }
        nombreNormalizado(s.nombre());
        if (s.login() == null || !s.login().strip().matches("[A-Za-z0-9._-]{3,80}")) {
            throw new IllegalArgumentException("El usuario debe contener 3-80 caracteres alfanuméricos, . _ o -");
        }
        correoNormalizado(s.correo());
        validarRol(s.rol());
    }

    private static void validarRol(String clave) {
        if (!ADMIN.equals(clave) && !CONSULTA.equals(clave)) {
            throw new IllegalArgumentException("El rol solicitado no está permitido");
        }
    }

    private static RolEntity obtenerRol(EntityManager em, String clave) {
        return em.createQuery("select r from RolEntity r where r.clave = :clave", RolEntity.class)
                .setParameter("clave", clave).getSingleResult();
    }

    private static RolEntity obtenerRolBloqueado(EntityManager em, String clave) {
        return em.createQuery("select r from RolEntity r where r.clave = :clave", RolEntity.class)
                .setParameter("clave", clave)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getSingleResult();
    }

    private static void bloquearRolAdmin(EntityManager em) {
        obtenerRolBloqueado(em, ADMIN);
    }

    private static void asegurarAdministrador(EntityManager em, Long administradorId) {
        if (administradorId == null) {
            throw new SecurityException("Se requiere iniciar sesión como administrador");
        }
        Long permitidos = em.createQuery(
                "select count(distinct u) from UsuarioEntity u join u.roles r "
                + "where u.id = :id and u.estado = :estado and r.clave = :rol", Long.class)
                .setParameter("id", administradorId)
                .setParameter("estado", UsuarioEstado.ACTIVO)
                .setParameter("rol", ADMIN)
                .getSingleResult();
        if (permitidos != 1L) {
            throw new SecurityException("La cuenta no tiene permisos administrativos");
        }
    }

    private static UsuarioEntity buscarUsuario(EntityManager em, Long usuarioId) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("Seleccione un usuario válido");
        }
        UsuarioEntity usuario = em.find(UsuarioEntity.class, usuarioId);
        if (usuario == null) {
            throw new IllegalArgumentException("No existe el usuario seleccionado");
        }
        return usuario;
    }

    private static UsuarioEntity buscarUsuarioConRoles(EntityManager em, Long id) {
        UsuarioEntity usuario = buscarUsuario(em, id);
        usuario.getRoles().size();
        return usuario;
    }

    private static void verificarNoEsUltimoAdmin(EntityManager em, UsuarioEntity usuario) {
        if (usuario.getEstado() != UsuarioEstado.ACTIVO
                || usuario.getRoles().stream().noneMatch(r -> ADMIN.equals(r.getClave()))) {
            return;
        }
        Long administradores = em.createQuery(
                "select count(distinct u) from UsuarioEntity u join u.roles r "
                + "where u.estado = :estado and r.clave = :clave", Long.class)
                .setParameter("estado", UsuarioEstado.ACTIVO)
                .setParameter("clave", ADMIN)
                .getSingleResult();
        if (administradores <= 1L) {
            throw new IllegalStateException("No se puede desactivar o quitar el rol del último administrador");
        }
    }

    private static void registrarEvento(EntityManager em, Long operador, Long objetivo, String accion) {
        em.persist(new BitacoraEventoEntity(operador, "usuario", objetivo, accion,
                "Operación administrativa sobre cuenta de usuario"));
    }

    private static UsuarioSesion sesion(UsuarioEntity u) {
        Set<String> roles = u.getRoles().stream().map(RolEntity::getClave)
                .collect(Collectors.toUnmodifiableSet());
        return new UsuarioSesion(u.getId(), u.getLogin(), u.getNombreCompleto(), roles);
    }
}
