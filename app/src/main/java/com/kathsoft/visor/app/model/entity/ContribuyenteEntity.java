package com.kathsoft.visor.app.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Contribuyente propio administrado desde RF-004. No representa un tercero
 * identificado en un CFDI, que utiliza la tabla `tercero` separada.
 *
 * <p>La identidad estable es `id`. El RFC es una clave natural única y
 * no se puede sustituir mediante la operación de modificación ordinaria:
 * corregir un RFC una vez vinculados CFDI requiere un flujo de identidad
 * específico y auditable.</p>
 */
@Entity
@Table(name = "contribuyente")
public class ContribuyenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rfc", nullable = false, length = 13, unique = true)
    private String rfc;

    @Column(name = "nombre_razon_social", nullable = false, length = 300)
    private String nombreRazonSocial;

    @Column(name = "codigo_postal_fiscal", length = 5, columnDefinition = "char(5)")
    private String codigoPostalFiscal;

    @Column(name = "correo_contacto", length = 254)
    private String correoContacto;

    @Column(name = "activo", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean activo = true;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Constructor para Hibernate. */
    protected ContribuyenteEntity() {
    }

    /**
     * Crea un contribuyente; los datos ya deben estar normalizados.
     *
     * @param rfc RFC de la empresa o persona
     * @param nombreRazonSocial denominación o nombre
     * @param codigoPostalFiscal código postal SAT opcional
     * @param correoContacto correo opcional
     */
    public ContribuyenteEntity(String rfc, String nombreRazonSocial,
            String codigoPostalFiscal, String correoContacto) {
        this.rfc = rfc;
        this.nombreRazonSocial = nombreRazonSocial;
        this.codigoPostalFiscal = codigoPostalFiscal;
        this.correoContacto = correoContacto;
    }

    public Long getId() { return id; }
    public String getRfc() { return rfc; }
    public String getNombreRazonSocial() { return nombreRazonSocial; }
    public String getCodigoPostalFiscal() { return codigoPostalFiscal; }
    public String getCorreoContacto() { return correoContacto; }
    public Boolean getActivo() { return activo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    /** @param nombre nombre legal o razón social */
    public void setNombreRazonSocial(String nombre) { this.nombreRazonSocial = nombre; }
    /** @param codigoPostalFiscal código postal fiscal de cinco dígitos o null */
    public void setCodigoPostalFiscal(String codigoPostalFiscal) {
        this.codigoPostalFiscal = codigoPostalFiscal;
    }
    /** @param correoContacto correo de contacto o null */
    public void setCorreoContacto(String correoContacto) { this.correoContacto = correoContacto; }
    /** @param activo estado para baja lógica, nunca borrado físico */
    public void setActivo(Boolean activo) { this.activo = activo; }
}
