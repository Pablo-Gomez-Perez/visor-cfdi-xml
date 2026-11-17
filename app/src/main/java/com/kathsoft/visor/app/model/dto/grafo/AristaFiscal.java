package com.kathsoft.visor.app.model.dto.grafo;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** Referencia dirigida desde el documento declarante hacia el UUID referenciado. */
public record AristaFiscal(String id,String origen,String destino,TipoRelacionDocumental tipo,
        String claveSat,Long parcialidad,BigDecimal importePagado,String moneda) {
    public AristaFiscal {
        Objects.requireNonNull(id);Objects.requireNonNull(tipo);
        origen=Objects.requireNonNull(origen).toUpperCase(Locale.ROOT);
        destino=Objects.requireNonNull(destino).toUpperCase(Locale.ROOT);
    }
    /** Importe del documento relacionado (MonedaDR), no el monto total del REP. */
    public String etiqueta(){
        return switch(tipo){
            case CFDI_RELACIONADO -> "Rel. "+claveSat;
            case RETENCION_RELACIONADA -> "Retención";
            case COMPLEMENTO_PAGO -> "REP"+(parcialidad==null?"":" #"+parcialidad)
                +(importePagado==null?" · sin importe":" · "+importePagado.stripTrailingZeros().toPlainString()+" "+moneda);
        };
    }
}
