package com.kathsoft.visor.app.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Privilegio atómico del catálogo `permiso` existente en MySQL.
 * Recurso y acción forman una clave de negocio única.
 */
@Entity
@Table(name = "permiso")
public class PermisoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Short id;

    @Column(name = "recurso", length = 70, nullable = false)
    private String recurso;

    @Column(name = "accion", length = 40, nullable = false)
    private String accion;

    @Column(name = "descripcion", length = 200, nullable = false)
    private String descripcion;

    /** Constructor requerido por JPA. */
    protected PermisoEntity() {
    }

    /**
     * Construye un permiso definido por la aplicación.
     *
     * @param recurso módulo o recurso protegido
     * @param accion operación autorizable
     * @param descripcion etiqueta comprensible en la matriz de privilegios
     */
    public PermisoEntity(String recurso, String accion, String descripcion) {
        this.recurso = recurso;
        this.accion = accion;
        this.descripcion = descripcion;
    }

    public Short getId() { return id; }

    public String getRecurso() { return recurso; }

    public String getAccion() { return accion; }

    public String getDescripcion() { return descripcion; }

    /** @return clave estable recurso.accion utilizada en autorizaciones */
    public String getClave() { return recurso + "." + accion; }
}
