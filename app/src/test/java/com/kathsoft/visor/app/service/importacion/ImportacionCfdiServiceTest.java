package com.kathsoft.visor.app.service.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Regresión del diagnóstico de despliegues MySQL RF-007 de primera etapa.
 * El error SQL original debe conservarse para no ocultar otros fallos.
 */
class ImportacionCfdiServiceTest {

    @Test
    void adviertePosibleProcedimientoAntiguoAlRechazarTipoPago() {
        String mensaje = ImportacionCfdiService.diagnosticarErrorProcedimiento(
                "P", "CFDI incompleto o version/tipo no admitido");
        assertTrue(mensaje.contains("CFDI incompleto o version/tipo no admitido"));
        assertTrue(mensaje.contains("SHOW CREATE PROCEDURE"));
        assertTrue(mensaje.contains("I/E/T/P/N"));
        assertTrue(mensaje.contains("etapa 2"));
    }

    @Test
    void advierteTambienParaNominaYNoParaIngreso() {
        String origen = "CFDI incompleto o version/tipo no admitido";
        assertTrue(ImportacionCfdiService.diagnosticarErrorProcedimiento(
                "N", origen).contains("procedimiento MySQL RF-007 desactualizado"));
        assertEquals(origen, ImportacionCfdiService.diagnosticarErrorProcedimiento(
                "I", origen));
    }

    @Test
    void conservaSinCambiosOtrosErroresDeMySql() {
        String sql = "Duplicate entry 'uuid' for key 'uk_doc_uuid'";
        String resultado = ImportacionCfdiService.diagnosticarErrorProcedimiento("P", sql);
        assertEquals(sql, resultado);
        assertFalse(resultado.contains("SHOW CREATE PROCEDURE"));
    }
}
