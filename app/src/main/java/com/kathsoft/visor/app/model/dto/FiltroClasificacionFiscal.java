package com.kathsoft.visor.app.model.dto;

import com.kathsoft.visor.app.service.clasificacion.ClasificadorDocumentoFiscal.Categoria;
import java.util.Locale;
import java.util.Objects;

/**
 * RF-009: filtros no fiscales para consultar documentos ya importados.
 * La búsqueda por UUID es un prefijo, no una búsqueda arbitraria sobre XML.
 *
 * @param categoria familia documental solicitada
 * @param sentido relación con el contribuyente titular
 * @param prefijoUuid prefijo hexadecimal de UUID; cadena vacía para todos
 */
public record FiltroClasificacionFiscal(Categoria categoria, Sentido sentido,
        String prefijoUuid) {

    /** Dirección de la relación contribuyente-documento. */
    public enum Sentido {
        TODOS("Todos"), EMITIDO("Emitido"), RECIBIDO("Recibido"), AMBOS("Ambos");

        private final String etiqueta;

        Sentido(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /** Normaliza el prefijo y rechaza caracteres comodín de SQL. */
    public FiltroClasificacionFiscal {
        Objects.requireNonNull(categoria, "Categoría requerida");
        Objects.requireNonNull(sentido, "Sentido requerido");
        prefijoUuid = prefijoUuid == null ? "" : prefijoUuid.strip().toUpperCase(Locale.ROOT);
        if (prefijoUuid.length() > 36 || !prefijoUuid.matches("[0-9A-F-]*")) {
            throw new IllegalArgumentException("El prefijo del UUID solo admite caracteres hexadecimales y guiones (máx. 36)");
        }
    }
}
