package com.kathsoft.visor.app.model.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Clave compuesta de la relación entre contribuyente, tercero y función.
 */
@Embeddable
public class ContribuyenteTerceroRolId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "contribuyente_id", nullable = false)
    private Long contribuyenteId;

    @Column(name = "tercero_id", nullable = false)
    private Long terceroId;

    @Column(name = "rol", nullable = false, length = 30)
    private String rol;

    /** Constructor requerido por JPA. */
    protected ContribuyenteTerceroRolId() { }

    /** @param contribuyenteId titular fiscal
     * @param terceroId contraparte
     * @param rol función de la contraparte para el titular
     */
    public ContribuyenteTerceroRolId(Long contribuyenteId, Long terceroId, String rol) {
        this.contribuyenteId = contribuyenteId;
        this.terceroId = terceroId;
        this.rol = rol;
    }

    public Long getContribuyenteId() { return contribuyenteId; }
    public Long getTerceroId() { return terceroId; }
    public String getRol() { return rol; }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) return true;
        if (!(otro instanceof ContribuyenteTerceroRolId id)) return false;
        return Objects.equals(contribuyenteId, id.contribuyenteId)
                && Objects.equals(terceroId, id.terceroId)
                && Objects.equals(rol, id.rol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contribuyenteId, terceroId, rol);
    }
}
