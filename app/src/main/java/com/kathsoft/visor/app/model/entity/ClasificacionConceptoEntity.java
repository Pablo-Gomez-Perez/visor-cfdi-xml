package com.kathsoft.visor.app.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Distribución administrativa de un concepto entre categorías del titular.
 *
 * <p>Mapea exclusivamente la tabla existente {@code clasificacion_concepto}.
 * El servicio valida pertenencia, límites agregados, revisión y auditoría
 * dentro de la transacción. El importe asignado no modifica el XML ni
 * representa por sí mismo una base tributaria. MySQL genera {@code creado_en}.</p>
 */
@Entity
@Table(name = "clasificacion_concepto")
public class ClasificacionConceptoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "contribuyente_id", nullable = false)
    private Long contribuyenteId;

    @Column(name = "concepto_id", nullable = false)
    private Long origenId;

    @Column(name = "categoria_id", nullable = false)
    private Long destinoId;

    @Column(name = "importe_asignado", nullable = false, precision = 20, scale = 6)
    private BigDecimal importe;

    @Column(name = "nota", length = 600)
    private String nota;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Constructor requerido por JPA; no realiza operaciones externas. */
    protected ClasificacionConceptoEntity() {
    }

    /**
     * Construye una asignación previamente validada por el servicio.
     *
     * @param titular contribuyente propietario de la clasificación
     * @param origen concepto original distribuido
     * @param destino categoría administrativa del mismo titular
     * @param importe importe neto asignado, sin redondeo implícito
     * @param nota observación opcional de hasta 600 caracteres
     * @param usuario usuario responsable
     */
    public ClasificacionConceptoEntity(long titular, long origen, long destino,
            BigDecimal importe, String nota, long usuario) {
        this.contribuyenteId = titular;
        this.origenId = origen;
        this.destinoId = destino;
        this.importe = importe;
        this.nota = nota;
        this.usuarioId = usuario;
    }

    /** @return identificador generado al persistir */
    public Long getId() { return id; }

    /** @return contribuyente propietario */
    public Long getContribuyenteId() { return contribuyenteId; }

    /** @return identificador del concepto original */
    public Long getOrigenId() { return origenId; }

    /** @return categoría administrativa asignada */
    public Long getDestinoId() { return destinoId; }

    /** @return importe neto asignado en moneda original */
    public BigDecimal getImporte() { return importe; }

    /** @return observación administrativa opcional */
    public String getNota() { return nota; }

    /** @return usuario responsable de la asignación */
    public Long getUsuarioId() { return usuarioId; }
}
