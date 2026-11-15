package com.kathsoft.visor.app.model.dto;

import com.kathsoft.visor.app.service.clasificacion.ClasificadorDocumentoFiscal.Categoria;
import java.time.LocalDateTime;

/**
 * Vista inmutable del documento original y su categoría derivada.
 * No constituye validación XSD, del sello ni consulta de vigencia al SAT.
 *
 * @param documentoId identificador interno, no UUID
 * @param uuid folio fiscal original
 * @param categoria identificación derivada
 * @param clase clase documental registrada
 * @param tipoCfdi clave SAT cuando la clase es CFDI
 * @param claveRetencion clave del tipo de retención, si procede
 * @param sentido relación del comprobante con el contribuyente
 * @param fechaEmision fecha de emisión documental, sin reinterpretar zona
 * @param rfcEmisor RFC del emisor si está disponible
 * @param rfcReceptor RFC del receptor si está disponible
 */
public record DocumentoClasificado(long documentoId, String uuid, Categoria categoria,
        String clase, String tipoCfdi, String claveRetencion, String sentido,
        LocalDateTime fechaEmision, String rfcEmisor, String rfcReceptor) {
}
