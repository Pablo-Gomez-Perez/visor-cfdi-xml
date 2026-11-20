package com.kathsoft.visor.app.model.entity;

import java.io.Serializable;
import java.util.Objects;

/** Identidad compuesta del vínculo entre movimiento y obligación existentes. */
public class AplicacionPagoId implements Serializable {
    private static final long serialVersionUID=1L;
    private Long movimientoPagoId;
    private Long obligacionId;
    /** Constructor requerido por JPA. */
    public AplicacionPagoId(){ }
    @Override public boolean equals(Object otro){return otro instanceof AplicacionPagoId id&&Objects.equals(movimientoPagoId,id.movimientoPagoId)&&Objects.equals(obligacionId,id.obligacionId);}
    @Override public int hashCode(){return Objects.hash(movimientoPagoId,obligacionId);}
}
