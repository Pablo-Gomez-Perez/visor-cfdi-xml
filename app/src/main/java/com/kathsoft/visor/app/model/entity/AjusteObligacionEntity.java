package com.kathsoft.visor.app.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Ajuste documentado de saldo; no representa por sí mismo restitución de IVA ni devolución de dinero. */
@Entity @Table(name="ajuste_obligacion")
public class AjusteObligacionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="obligacion_id",nullable=false) private Long obligacionId;
    @Column(name="documento_soporte_id") private Long documentoSoporteId;
    @Column(name="tipo",length=25,nullable=false) private String tipo;
    @Column(name="fecha_ajuste",nullable=false) private LocalDate fechaAjuste;
    @Column(name="importe_moneda_obligacion",precision=20,scale=6,nullable=false) private BigDecimal importe;
    @Column(name="observacion",length=600) private String observacion;
    @Column(name="usuario_id",nullable=false) private Long usuarioId;
    /** Constructor requerido por JPA. */
    protected AjusteObligacionEntity(){ }
    /** Construye un ajuste con soporte disponible para el mismo titular. */
    public AjusteObligacionEntity(long obligacion,long soporte,String tipo,LocalDate fecha,BigDecimal importe,String motivo,long usuario){
        obligacionId=obligacion;documentoSoporteId=soporte;this.tipo=tipo;fechaAjuste=fecha;this.importe=importe;observacion=motivo;usuarioId=usuario;
    }
    /** @return identidad del ajuste, generada al persistir */
    public Long getId(){return id;}
}
