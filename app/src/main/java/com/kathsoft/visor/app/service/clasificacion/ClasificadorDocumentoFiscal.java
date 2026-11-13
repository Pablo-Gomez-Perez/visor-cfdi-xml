package com.kathsoft.visor.app.service.clasificacion;

/**
 * RF-009: identifica la familia fiscal de un documento a partir de la clase y
 * del tipo originales almacenados por RF-007. No modifica el XML ni determina
 * deducibilidad, acreditamiento, efectos contables o situación ante el SAT.
 */
public final class ClasificadorDocumentoFiscal {

    /** Categorías documentales de lectura; TODAS es exclusivamente un filtro. */
    public enum Categoria {
        TODAS("Todas"),
        INGRESO("Ingreso"),
        EGRESO("Egreso"),
        TRASLADO("Traslado"),
        NOMINA("Nómina"),
        PAGO("Pago"),
        RETENCION("Retenciones e información de pagos"),
        INCONSISTENTE("Registro inconsistente");

        private final String descripcion;

        Categoria(String descripcion) {
            this.descripcion = descripcion;
        }

        @Override
        public String toString() {
            return descripcion;
        }
    }

    private ClasificadorDocumentoFiscal() {
    }

    /**
     * Identifica una familia registrada sin confundir el CFDI tipo P con el
     * comprobante de Retenciones e Información de Pagos 2.0.
     *
     * @param clase "CFDI" o "RETENCION" de documento_fiscal
     * @param tipo tipo original c_TipoDeComprobante, o null
     * @param tieneCfdi existencia de la fila especializada cfdi
     * @param tieneRetencion existencia de la fila especializada retencion
     * @return categoría o INCONSISTENTE si clase, tipo y especialización difieren
     */
    public static Categoria identificar(String clase, String tipo,
            boolean tieneCfdi, boolean tieneRetencion) {
        if ("RETENCION".equals(clase) && tieneRetencion && !tieneCfdi) {
            return Categoria.RETENCION;
        }
        if ("CFDI".equals(clase) && tieneCfdi && !tieneRetencion && tipo != null) {
            return switch (tipo) {
                case "I" -> Categoria.INGRESO;
                case "E" -> Categoria.EGRESO;
                case "T" -> Categoria.TRASLADO;
                case "N" -> Categoria.NOMINA;
                case "P" -> Categoria.PAGO;
                default -> Categoria.INCONSISTENTE;
            };
        }
        return Categoria.INCONSISTENTE;
    }
}
