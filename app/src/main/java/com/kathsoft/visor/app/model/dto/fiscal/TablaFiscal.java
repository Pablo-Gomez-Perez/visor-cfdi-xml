package com.kathsoft.visor.app.model.dto.fiscal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resultado tabular desacoplado de JDBC; conserva null como ausencia de información. */
public record TablaFiscal(List<String> columnas,List<List<Object>> filas,boolean siguiente,String aviso) {
    /** Copia defensiva sin convertir valores ausentes en cero. */
    public TablaFiscal {
        columnas=List.copyOf(columnas);
        filas=filas.stream().map(r->Collections.unmodifiableList(new ArrayList<>(r))).toList();
    }
}
