package com.kathsoft.visor.app.service.clasificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal;
import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal.Sentido;
import com.kathsoft.visor.app.service.clasificacion.ClasificadorDocumentoFiscal.Categoria;
import org.junit.jupiter.api.Test;

/** Pruebas del catálogo SAT de tipos y la separación de retenciones. */
class ClasificadorDocumentoFiscalTest {

    @Test
    void identificaCincoTiposCfdi() {
        assertEquals(Categoria.INGRESO,
                ClasificadorDocumentoFiscal.identificar("CFDI", "I", true, false));
        assertEquals(Categoria.EGRESO,
                ClasificadorDocumentoFiscal.identificar("CFDI", "E", true, false));
        assertEquals(Categoria.TRASLADO,
                ClasificadorDocumentoFiscal.identificar("CFDI", "T", true, false));
        assertEquals(Categoria.NOMINA,
                ClasificadorDocumentoFiscal.identificar("CFDI", "N", true, false));
        assertEquals(Categoria.PAGO,
                ClasificadorDocumentoFiscal.identificar("CFDI", "P", true, false));
    }

    @Test
    void retencionesSonFamiliaIndependienteDeCfdiPago() {
        assertEquals(Categoria.RETENCION,
                ClasificadorDocumentoFiscal.identificar("RETENCION", null, false, true));
        assertEquals(Categoria.INCONSISTENTE,
                ClasificadorDocumentoFiscal.identificar("RETENCION", "P", true, true));
        assertEquals(Categoria.INCONSISTENTE,
                ClasificadorDocumentoFiscal.identificar("CFDI", "P", false, true));
    }

    @Test
    void detectaEspecializacionesAusentesOContradictorias() {
        assertEquals(Categoria.INCONSISTENTE,
                ClasificadorDocumentoFiscal.identificar("CFDI", null, false, false));
        assertEquals(Categoria.INCONSISTENTE,
                ClasificadorDocumentoFiscal.identificar("CFDI", "X", true, false));
        assertEquals(Categoria.INCONSISTENTE,
                ClasificadorDocumentoFiscal.identificar("CFDI", "I", true, true));
        assertEquals(Categoria.INCONSISTENTE,
                ClasificadorDocumentoFiscal.identificar("DESCONOCIDA", "I", true, false));
    }

    @Test
    void prefijoUuidNormalizadoYRestringido() {
        var filtro = new FiltroClasificacionFiscal(Categoria.PAGO, Sentido.EMITIDO,
                " ab12-cD ");
        assertEquals("AB12-CD", filtro.prefijoUuid());
        assertThrows(IllegalArgumentException.class,
                () -> new FiltroClasificacionFiscal(Categoria.TODAS, Sentido.TODOS, "%"));
        assertThrows(IllegalArgumentException.class,
                () -> new FiltroClasificacionFiscal(Categoria.TODAS, Sentido.TODOS, "_"));
        assertThrows(IllegalArgumentException.class,
                () -> new FiltroClasificacionFiscal(Categoria.TODAS, Sentido.TODOS, "G012"));
    }

    @Test
    void mapeoJdbcAdmiteCharYTimestamp() {
        Object[] fila = { 7L, "123e4567-e89b-12d3-a456-426614174000",
                "CFDI", 'N', null, "RECIBIDO",
                java.sql.Timestamp.valueOf("2026-11-12 14:00:00"),
                "ABC010101AB1", "BBB010101BB1", 7L, null };
        var resultado = ClasificacionCfdiService.mapear(fila);
        assertEquals(Categoria.NOMINA, resultado.categoria());
        assertEquals("N", resultado.tipoCfdi());
        assertEquals("RECIBIDO", resultado.sentido());
        assertThrows(IllegalArgumentException.class,
                () -> ClasificacionCfdiService.mapear(new Object[10]));
    }
}
