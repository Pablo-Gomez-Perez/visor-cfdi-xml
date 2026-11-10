package com.kathsoft.visor.app.model.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Periodo histórico de régimen fiscal de un contribuyente. El registro se
 * identifica por contribuyente, clave de régimen y fecha de inicio.
 */
@Entity
@Table(name = "contribuyente_regimen")
public class ContribuyenteRegimenEntity {

    @EmbeddedId
    private ContribuyenteRegimenId id;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** Constructor JPA. */
    protected ContribuyenteRegimenEntity() {
    }

    /**
     * @param id identidad compuesta del periodo
     * @param vigenteHasta fecha final inclusiva o null si sigue vigente
     */
    public ContribuyenteRegimenEntity(ContribuyenteRegimenId id, LocalDate vigenteHasta) {
        this.id = id;
        this.vigenteHasta = vigenteHasta;
    }

    public ContribuyenteRegimenId getId() { return id; }
    public LocalDate getVigenteHasta() { return vigenteHasta; }
    /** @param vigenteHasta nueva fecha final inclusiva o null */
    public void setVigenteHasta(LocalDate vigenteHasta) { this.vigenteHasta = vigenteHasta; }
}
