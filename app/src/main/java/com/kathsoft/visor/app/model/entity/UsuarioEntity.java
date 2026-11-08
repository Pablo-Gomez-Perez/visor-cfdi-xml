package com.kathsoft.visor.app.model.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Mapeo JPA de usuario y su asociación usuario_rol del esquema v1.
 *
 * <p>Nunca exportar hashContrasena a modelos de tabla Swing ni a DTO.</p>
 */
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    @Column(name = "login", nullable = false, length = 80, unique = true)
    private String login;

    @Column(name = "correo", length = 254, unique = true)
    private String correo;

    @Column(name = "hash_contrasena", nullable = false, length = 255)
    private String hashContrasena;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private UsuarioEstado estado = UsuarioEstado.ACTIVO;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<RolEntity> roles = new HashSet<>();

    /** Constructor requerido por JPA. */
    protected UsuarioEntity() {
    }

    /**
     * Construye una cuenta nueva, conservando el hash como secreto.
     *
     * @param nombreCompleto nombre visible
     * @param login identificador único de acceso
     * @param correo correo o null
     * @param hashContrasena hash de contraseña con sal
     */
    public UsuarioEntity(String nombreCompleto, String login, String correo, String hashContrasena) {
        this.nombreCompleto = nombreCompleto;
        this.login = login;
        this.correo = correo;
        this.hashContrasena = hashContrasena;
    }

    public Long getId() { return id; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getLogin() { return login; }
    public String getCorreo() { return correo; }
    public String getHashContrasena() { return hashContrasena; }
    public UsuarioEstado getEstado() { return estado; }
    public LocalDateTime getUltimoAcceso() { return ultimoAcceso; }
    public Set<RolEntity> getRoles() { return roles; }

    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public void setCorreo(String correo) { this.correo = correo; }
    public void setHashContrasena(String hashContrasena) { this.hashContrasena = hashContrasena; }
    public void setEstado(UsuarioEstado estado) { this.estado = estado; }
    public void setUltimoAcceso(LocalDateTime ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; }
}
