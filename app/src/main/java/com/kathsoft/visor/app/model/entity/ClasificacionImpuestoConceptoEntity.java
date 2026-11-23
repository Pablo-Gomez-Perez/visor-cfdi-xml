package com.kathsoft.visor.app.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Distribución del importe de un impuesto original entre tratamientos fiscales.
 *
 * <p>Mapea la tabla existente {@code clasificacion_impuesto_concepto}.
 * El servicio valida pertenencia, importe disponible y fundamento del
 * tratamiento. Esta asignación conserva el impuesto declarado del XML;
 * no confirma pago ni acreditamiento. MySQL genera {@code creado_en}.</p>
 */
@Entity
@Table(name = "clasificacion_impuesto_concepto")
public class ClasificacionImpuestoConceptoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "contribuyente_id", nullable = false)
    private Long contribuyenteId;

    @Column(name = "impuesto_concepto_id", nullable = false)
    private Long origenId;

    @Column(name = "tratamiento_id", nullable = false, columnDefinition = "smallint unsigned")
    private Integer destinoId;

    @Column(name = "importe_asignado", nullable = false, precision = 20, scale = 6)
    private BigDecimal importe;

    @Column(name = "justificacion", length = 600)
    private String nota;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Constructor requerido por JPA; no realiza operaciones externas. */
    protected ClasificacionImpuestoConceptoEntity() {
    }

    /**
     * Construye una asignación previamente validada por el servicio.
     *
     * @param titular contribuyente propietario de la clasificación
     * @param origen identificador del impuesto original del concepto
     * @param destino identificador del tratamiento, entre 1 y 65535
     * @param importe importe original asignado, sin redondeo implícito
     * @param nota justificación opcional de hasta 600 caracteres
     * @param usuario usuario responsable
     * @throws IllegalArgumentException si el identificador no cabe en el mapeo
     */
    public ClasificacionImpuestoConceptoEntity(long titular, long origen, long destino,
            BigDecimal importe, String nota, long usuario) {
        if (destino < 1 || destino > 65535) {
            throw new IllegalArgumentException("Identificador de tratamiento fuera de rango JPA");
        }
        this.contribuyenteId = titular;
        this.origenId = origen;
        this.destinoId = (int) destino;
        this.importe = importe;
        this.nota = nota;
        this.usuarioId = usuario;
    }

    /** @return identificador generado al persistir */
    public Long getId() { return id; }

    /** @return contribuyente propietario */
    public Long getContribuyenteId() { return contribuyenteId; }

    /** @return identificador del impuesto original del concepto */
    public Long getOrigenId() { return origenId; }

    /** @return identificador del tratamiento fiscal asignado */
    public Long getDestinoId() { return destinoId == null ? null : destinoId.longValue(); }

    /** @return importe original asignado al tratamiento */
    public BigDecimal getImporte() { return importe; }

    /** @return justificación opcional del tratamiento */
    public String getNota() { return nota; }

    /** @return usuario responsable de la asignación */
    public Long getUsuarioId() { return usuarioId; }
}
