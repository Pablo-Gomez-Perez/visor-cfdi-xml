package com.kathsoft.visor.app.model.dto.grafo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/** Identidad documental; un nodo sin ID solo contiene la referencia declarada. */
public record NodoCfdi(Long documentoId,String uuid,String tipo,String serie,String folio,
        String rfcEmisor,String rfcReceptor,LocalDateTime emision,BigDecimal total,
        String moneda,String metodoPago,String estadoSat) {
    public NodoCfdi {
        uuid=Objects.requireNonNull(uuid).toUpperCase(Locale.ROOT);
    }
    /** No distingue ausencia, archivado o falta de autorización. */
    public static NodoCfdi referencia(String uuid) {
        return new NodoCfdi(null,uuid,null,null,null,null,null,null,null,null,null,null);
    }
    /** Indica si el documento puede ser consultado por el titular. */
    public boolean disponible(){return documentoId!=null;}
}
