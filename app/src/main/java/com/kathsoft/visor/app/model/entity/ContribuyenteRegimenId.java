package com.kathsoft.visor.app.model.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * PK compuesta de `contribuyente_regimen`. Conserva periodos históricos
 * sin convertir sus atributos en una lista multivaluada.
 */
@Embeddable
public class ContribuyenteRegimenId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "contribuyente_id", nullable = false)
    private Long contribuyenteId;

    @Column(name = "clave_regimen", nullable = false, length = 5)
    private String claveRegimen;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** Constructor JPA. */
    protected ContribuyenteRegimenId() {
    }

    /**
     * @param contribuyenteId id del titular del régimen
     * @param claveRegimen clave del catálogo SAT
     * @param vigenteDesde fecha inicial
     */
    public ContribuyenteRegimenId(Long contribuyenteId, String claveRegimen,
            LocalDate vigenteDesde) {
        this.contribuyenteId = contribuyenteId;
        this.claveRegimen = claveRegimen;
        this.vigenteDesde = vigenteDesde;
    }

    public Long getContribuyenteId() { return contribuyenteId; }
    public String getClaveRegimen() { return claveRegimen; }
    public LocalDate getVigenteDesde() { return vigenteDesde; }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) return true;
        if (!(objeto instanceof ContribuyenteRegimenId otro)) return false;
        return Objects.equals(contribuyenteId, otro.contribuyenteId)
                && Objects.equals(claveRegimen, otro.claveRegimen)
                && Objects.equals(vigenteDesde, otro.vigenteDesde);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contribuyenteId, claveRegimen, vigenteDesde);
    }
}
