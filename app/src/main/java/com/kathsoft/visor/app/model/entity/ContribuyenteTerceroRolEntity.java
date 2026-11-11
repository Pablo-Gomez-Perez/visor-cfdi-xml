package com.kathsoft.visor.app.model.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Relación de un tercero con un contribuyente en una función comercial.
 * La misma contraparte puede ser simultáneamente cliente y proveedor.
 */
@Entity
@Table(name = "contribuyente_tercero_rol")
public class ContribuyenteTerceroRolEntity {
    @EmbeddedId
    private ContribuyenteTerceroRolId id;

    /** Constructor JPA. */
    protected ContribuyenteTerceroRolEntity() { }

    /** @param id identidad de la asignación */
    public ContribuyenteTerceroRolEntity(ContribuyenteTerceroRolId id) {
        this.id = id;
    }

    public ContribuyenteTerceroRolId getId() { return id; }
}
