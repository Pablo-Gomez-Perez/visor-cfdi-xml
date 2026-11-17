package com.kathsoft.visor.app.model.dto.grafo;

import java.util.List;
import java.util.Objects;

/** Instantánea inmutable, acotada, sin coordenadas de presentación. */
public record GrafoFiscal(String uuidFocal,List<NodoCfdi> nodos,List<AristaFiscal> aristas,boolean limitado) {
    public GrafoFiscal {
        Objects.requireNonNull(uuidFocal);
        nodos=List.copyOf(nodos);aristas=List.copyOf(aristas);
    }
}
