package com.kathsoft.visor.app.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Obligación documental de cobro o pago del modelo financiero existente.
 *
 * <p>El importe original conserva la moneda de la obligación. Las aplicaciones
 * de pagos y los ajustes se registran por separado; esta entidad no calcula
 * saldos ni presupone que una factura PUE haya sido liquidada. El servicio
 * valida autorización, disponibilidad documental y consistencia monetaria
 * dentro de la transacción que persiste la obligación.</p>
 */
@Entity
@Table(name = "obligacion")
public class ObligacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contribuyente_id", nullable = false)
    private Long contribuyenteId;

    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(name = "naturaleza", nullable = false, length = 10)
    private String naturaleza;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    @Column(name = "monto_original", nullable = false, precision = 20, scale = 6)
    private BigDecimal montoOriginal;

    @Column(name = "fecha_origen", nullable = false)
    private LocalDate fechaOrigen;

    /** Constructor reservado a Hibernate. */
    protected ObligacionEntity() {
    }

    /**
     * Crea una obligación asociada a un documento del titular.
     *
     * <p>Los argumentos deben haber sido validados por el servicio. La clave
     * compuesta documental y la unicidad por titular, documento y naturaleza
     * permanecen bajo las restricciones del esquema canónico.</p>
     *
     * @param titular identificador del contribuyente autorizado
     * @param documento identificador del documento fiscal disponible
     * @param tercero contraparte del documento, o {@code null} si no se identificó
     * @param naturaleza {@code PAGAR} o {@code COBRAR}
     * @param moneda moneda original de tres caracteres
     * @param original importe original no negativo, sin conversión monetaria
     * @param fecha fecha documental de origen
     */
    public ObligacionEntity(long titular, long documento, Long tercero,
            String naturaleza, String moneda, BigDecimal original, LocalDate fecha) {
        this.contribuyenteId = titular;
        this.documentoId = documento;
        this.terceroId = tercero;
        this.naturaleza = naturaleza;
        this.moneda = moneda;
        this.montoOriginal = original;
        this.fechaOrigen = fecha;
    }

    /** @return identificador persistido, o {@code null} antes de la inserción */
    public Long getId() {
        return id;
    }
}
