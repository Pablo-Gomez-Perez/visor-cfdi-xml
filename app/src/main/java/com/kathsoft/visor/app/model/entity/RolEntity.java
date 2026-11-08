package com.kathsoft.visor.app.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entidad del catálogo existente rol; no crea ni modifica tablas. */
@Entity
@Table(name = "rol")
public class RolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Short id;

    @Column(name = "clave", nullable = false, length = 40, unique = true)
    private String clave;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String descripcion;

    /** Constructor requerido por JPA. */
    protected RolEntity() {
    }

    /**
     * Crea una descripción de rol.
     * @param clave identificador funcional
     * @param descripcion descripción del rol
     */
    public RolEntity(String clave, String descripcion) {
        this.clave = clave;
        this.descripcion = descripcion;
    }

    public Short getId() { return id; }

    public String getClave() { return clave; }

    public String getDescripcion() { return descripcion; }
}
