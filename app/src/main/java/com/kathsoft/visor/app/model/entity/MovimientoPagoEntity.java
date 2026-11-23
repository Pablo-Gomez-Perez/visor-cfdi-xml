package com.kathsoft.visor.app.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Evidencia administrativa de un cobro o pago en el modelo financiero existente.
 *
 * <p>El importe se expresa exclusivamente en la moneda del movimiento. Su
 * aplicación a obligaciones, el vínculo con complementos y cualquier efecto
 * tributario corresponden a registros y validaciones independientes. Un
 * movimiento manual no modifica los atributos originales del CFDI.</p>
 */
@Entity
@Table(name = "movimiento_pago")
public class MovimientoPagoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contribuyente_id", nullable = false)
    private Long contribuyenteId;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(name = "naturaleza", nullable = false, length = 10)
    private String naturaleza;

    @Column(name = "fecha_movimiento", nullable = false)
    private LocalDateTime fechaMovimiento;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    @Column(name = "importe", nullable = false, precision = 20, scale = 6)
    private BigDecimal importe;

    @Column(name = "origen", nullable = false, length = 15)
    private String origen;

    @Column(name = "referencia", length = 160)
    private String referencia;

    @Column(name = "observaciones", length = 1000)
    private String observaciones;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Constructor reservado a Hibernate. */
    protected MovimientoPagoEntity() {
    }

    /**
     * Crea un movimiento de origen {@code MANUAL} previamente validado.
     *
     * <p>El servicio debe comprobar autorización, evidencia, límites de
     * aplicación y ausencia de duplicación dentro de la misma transacción.</p>
     *
     * @param titular identificador del contribuyente autorizado
     * @param tercero contraparte, o {@code null} si no se identificó
     * @param naturaleza {@code PAGO} o {@code COBRO}
     * @param fecha fecha y hora de realización sustentada por la evidencia
     * @param moneda moneda del movimiento de tres caracteres
     * @param importe importe estrictamente positivo, sin conversión monetaria
     * @param referencia identificación de la evidencia, hasta 160 caracteres
     * @param observaciones explicación administrativa, hasta 1000 caracteres
     * @param usuario identificador del usuario responsable
     */
    public MovimientoPagoEntity(long titular, Long tercero, String naturaleza,
            LocalDateTime fecha, String moneda, BigDecimal importe,
            String referencia, String observaciones, long usuario) {
        this(titular,tercero,naturaleza,fecha,moneda,importe,referencia,observaciones,usuario,"MANUAL");
    }

    /** Identifica una confirmación REP como COMPLEMENTO sin crear evidencia manual ficticia. */
    public MovimientoPagoEntity(long titular, Long tercero, String naturaleza,
            LocalDateTime fecha, String moneda, BigDecimal importe,
            String referencia, String observaciones, long usuario,String origen) {
        if(!java.util.Set.of("MANUAL","COMPLEMENTO").contains(origen))
            throw new IllegalArgumentException("Origen financiero inválido");
        this.contribuyenteId = titular;
        this.terceroId = tercero;
        this.naturaleza = naturaleza;
        this.fechaMovimiento = fecha;
        this.moneda = moneda;
        this.importe = importe;
        this.origen = origen;
        this.referencia = referencia;
        this.observaciones = observaciones;
        this.usuarioId = usuario;
    }

    /** @return identificador persistido, o {@code null} antes de la inserción */
    public Long getId() {
        return id;
    }
}
