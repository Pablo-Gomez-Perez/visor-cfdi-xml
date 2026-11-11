package com.kathsoft.visor.app.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Contraparte fiscal identificada en CFDI o registrada manualmente.
 * No equivale a un contribuyente propio. Un RFC genérico no identifica
 * de forma única a una persona y por ello no existe restricción UNIQUE.
 */
@Entity
@Table(name = "tercero")
public class TerceroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rfc", length = 13)
    private String rfc;

    @Column(name = "nombre_razon_social", nullable = false, length = 300)
    private String nombreRazonSocial;

    @Column(name = "pais_fiscal", length = 3, columnDefinition = "char(3)")
    private String paisFiscal;

    @Column(name = "num_registro_fiscal_extranjero", length = 100)
    private String numeroRegistroFiscalExtranjero;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Constructor JPA. */
    protected TerceroEntity() {
    }

    /**
     * Crea una contraparte con valores previamente validados.
     *
     * @param rfc RFC nacional o genérico, si aplica
     * @param nombreRazonSocial nombre legal
     * @param paisFiscal clave de país de tres caracteres, si aplica
     * @param numeroRegistroFiscalExtranjero identificación extranjera, si aplica
     */
    public TerceroEntity(String rfc, String nombreRazonSocial,
            String paisFiscal, String numeroRegistroFiscalExtranjero) {
        this.rfc = rfc;
        this.nombreRazonSocial = nombreRazonSocial;
        this.paisFiscal = paisFiscal;
        this.numeroRegistroFiscalExtranjero = numeroRegistroFiscalExtranjero;
    }

    public Long getId() { return id; }
    public String getRfc() { return rfc; }
    public String getNombreRazonSocial() { return nombreRazonSocial; }
    public String getPaisFiscal() { return paisFiscal; }
    public String getNumeroRegistroFiscalExtranjero() {
        return numeroRegistroFiscalExtranjero;
    }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    /** @param nombreRazonSocial nombre legal actualizado */
    public void setNombreRazonSocial(String nombreRazonSocial) {
        this.nombreRazonSocial = nombreRazonSocial;
    }
    /** @param paisFiscal país fiscal actualizado */
    public void setPaisFiscal(String paisFiscal) { this.paisFiscal = paisFiscal; }
    /** @param numeroRegistroFiscalExtranjero identificador fiscal extranjero */
    public void setNumeroRegistroFiscalExtranjero(String numeroRegistroFiscalExtranjero) {
        this.numeroRegistroFiscalExtranjero = numeroRegistroFiscalExtranjero;
    }
}
