package com.kathsoft.visor.app.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Aplicación financiera; importes en cada moneda permanecen independientes. */
@Entity @Table(name="aplicacion_pago") @IdClass(AplicacionPagoId.class)
public class AplicacionPagoEntity {
    @Id @Column(name="movimiento_pago_id") private Long movimientoPagoId;
    @Id @Column(name="obligacion_id") private Long obligacionId;
    @Column(name="importe_moneda_pago",precision=20,scale=6,nullable=false) private BigDecimal importeMonedaPago;
    @Column(name="importe_moneda_obligacion",precision=20,scale=6,nullable=false) private BigDecimal importeMonedaObligacion;
    /** Constructor requerido por JPA. */
    protected AplicacionPagoEntity(){ }
    /** Registra importes validados bajo bloqueo de la obligación. */
    public AplicacionPagoEntity(long movimiento,long obligacion,BigDecimal importePago,BigDecimal importeObligacion){
        movimientoPagoId=movimiento;obligacionId=obligacion;importeMonedaPago=importePago;importeMonedaObligacion=importeObligacion;
    }
}
