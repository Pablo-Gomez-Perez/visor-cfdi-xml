package com.kathsoft.visor.app.service.fiscal;

import static org.junit.jupiter.api.Assertions.*;

import com.kathsoft.visor.app.service.fiscal.MotorIva.Evidencia;
import com.kathsoft.visor.app.service.fiscal.MotorIva.Resultado;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Resultados independientes del auxiliar puro; no sustituye pruebas de temporalidad del servicio. */
class MotorIvaTest {

    private static BigDecimal decimal(String valor) { return valor == null ? null : new BigDecimal(valor); }

    private static void igual(String esperado, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, decimal(esperado).compareTo(actual));
    }

    private static Evidencia evidencia(String dr, String satFactura, String satRep, boolean disponible,
            boolean consistente, boolean sustitucion, boolean historico, String original,
            String acreditable, String noAcreditable, boolean retenido) {
        return new Evidencia(decimal(dr), "MXN", "MXN", BigDecimal.ONE, BigDecimal.ONE,
                satFactura, satRep, disponible, consistente, sustitucion, historico,
                decimal(original), decimal(acreditable), decimal(noAcreditable), retenido);
    }

    private static Evidencia valida(String dr) {
        return evidencia(dr, "VIGENTE", "VIGENTE", true, true, false, false, "160", "160", "0", false);
    }

    private static void sinConfirmacion(Resultado resultado) {
        assertNull(resultado.realizadoDR());
        assertNull(resultado.convertidoMXN());
        assertNull(resultado.potencialDR());
        assertNull(resultado.acreditableDR());
        assertNull(resultado.noAcreditableDR());
        assertNull(resultado.pendienteTratamientoDR());
    }

    @Test void dosPagosDeOchentaReconocenCientoSesentaEnTotalSinAlterarOriginal() {
        var junio = valida("80.00");
        var julio = valida("80.00");
        var primero = MotorIva.evaluar(junio);
        var segundo = MotorIva.evaluar(julio);
        igual("80", primero.realizadoDR());
        igual("80", segundo.realizadoDR());
        igual("160", primero.realizadoDR().add(segundo.realizadoDR()));
        igual("80", primero.acreditableDR());
        igual("0", primero.pendienteTratamientoDR());
        igual("160", junio.impuestoOriginal());
        igual("160", julio.impuestoOriginal());
        assertTrue(primero.incidencias().isEmpty());
        assertEquals("EVIDENCIA_Y_TRATAMIENTO_REGISTRADOS", primero.estado());
    }

    @Test void estadosSatNoConfirmadosNuncaSePresentanComoImporteCeroConfirmado() {
        for (String estado : List.of("CANCELADO", "DESCONOCIDO", "SIN_CONSULTA", "NO_ENCONTRADO", "ERROR")) {
            var factura = MotorIva.evaluar(evidencia("80", estado, "VIGENTE", true, true,
                    false, false, "160", "160", "0", false));
            sinConfirmacion(factura);
            assertEquals("PENDIENTE_EVIDENCIA", factura.estado());
            assertTrue(factura.incidencias().contains("SAT_FACTURA_" + estado));
            var rep = MotorIva.evaluar(evidencia("80", "VIGENTE", estado, true, true,
                    false, false, "160", "160", "0", false));
            sinConfirmacion(rep);
            assertTrue(rep.incidencias().contains("SAT_REP_" + estado));
        }
    }

    @Test void sustitucionInconsistenciaYFacturaNoDisponibleRequierenRevision() {
        var sustitucion = MotorIva.evaluar(evidencia("80", "VIGENTE", "VIGENTE", true, true,
                true, false, "160", "160", "0", false));
        sinConfirmacion(sustitucion);
        assertTrue(sustitucion.incidencias().contains("SUSTITUCION_REQUIERE_REVISION"));
        var inconsistente = MotorIva.evaluar(evidencia("80", "VIGENTE", "VIGENTE", true, false,
                false, false, "160", "160", "0", false));
        sinConfirmacion(inconsistente);
        assertTrue(inconsistente.incidencias().contains("PAGO_O_IMPUESTO_INCONSISTENTE"));
        var noDisponible = MotorIva.evaluar(evidencia("80", "VIGENTE", "VIGENTE", false, true,
                false, false, "160", "160", "0", false));
        sinConfirmacion(noDisponible);
        assertTrue(noDisponible.incidencias().contains("FACTURA_NO_DISPONIBLE"));
    }

    @Test void historicoSinImpuestosDrNoInventaProporcionOriginal() {
        var resultado = MotorIva.evaluar(evidencia(null, "VIGENTE", "VIGENTE", true, true,
                false, true, "160", "160", "0", false));
        sinConfirmacion(resultado);
        assertTrue(resultado.incidencias().contains("HISTORICO_SIN_DESGLOSE_DR"));
        assertTrue(resultado.incidencias().contains("IMPORTE_DR_NO_DECLARADO"));
    }

    @Test void conversionDistingueMonedaDrPagoYMxnSinFactoresInventados() {
        // 8 USD DR / 0.05 USD por MXN P = 160 MXN.
        igual("160", MotorIva.convertir(decimal("8"), "USD", "MXN", decimal("0.05"), null));
        // 10 EUR DR / 0.8 EUR por USD P * 20 MXN por USD P = 250 MXN.
        igual("250", MotorIva.convertir(decimal("10"), "EUR", "USD", decimal("0.8"), decimal("20")));
        igual("160", MotorIva.convertir(decimal("8"), "USD", "USD", null, decimal("20")));
        igual("8", MotorIva.convertir(decimal("8"), "MXN", "USD", null, null));
        assertNull(MotorIva.convertir(decimal("8"), "USD", "MXN", null, null));
        assertNull(MotorIva.convertir(decimal("8"), "USD", "USD", null, null));
        assertNull(MotorIva.convertir(decimal("8"), "EUR", "USD", BigDecimal.ZERO, decimal("20")));
        assertNull(MotorIva.convertir(decimal("8"), "EUR", "USD", BigDecimal.ONE, decimal("-1")));
        assertNull(MotorIva.convertir(decimal("8"), "XXX", "MXN", BigDecimal.ONE, BigDecimal.ONE));
        assertNull(MotorIva.convertir(null, "MXN", "MXN", BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test void faltaTipoCambioConservaImporteOriginalYAdvierteConversionPendiente() {
        var evidencia = new Evidencia(decimal("8"), "USD", "USD", null, null,
                "VIGENTE", "VIGENTE", true, true, false, false,
                decimal("16"), decimal("16"), BigDecimal.ZERO, false);
        var resultado = MotorIva.evaluar(evidencia);
        igual("8", resultado.realizadoDR());
        assertNull(resultado.convertidoMXN());
        assertEquals("REALIZADO_CON_PENDIENTES", resultado.estado());
        assertTrue(resultado.incidencias().contains("CONVERSION_MXN_SIN_FACTORES_VERIFICABLES"));
    }

    @Test void tratamientoParcialSeProrrateaSinDuplicarImporteRealizado() {
        var resultado = MotorIva.evaluar(evidencia("80", "VIGENTE", "VIGENTE", true, true,
                false, false, "160", "80", "40", false));
        igual("80", resultado.realizadoDR());
        igual("40", resultado.potencialDR());
        igual("40", resultado.acreditableDR());
        igual("20", resultado.noAcreditableDR());
        igual("20", resultado.pendienteTratamientoDR());
        igual("80", resultado.acreditableDR().add(resultado.noAcreditableDR()).add(resultado.pendienteTratamientoDR()));
        assertTrue(resultado.incidencias().contains("TRATAMIENTO_O_REQUISITOS_PENDIENTES"));
        var pendiente = MotorIva.evaluar(evidencia("80", "VIGENTE", "VIGENTE", true, true,
                false, false, "160", null, null, false));
        igual("80", pendiente.pendienteTratamientoDR());
        assertEquals("REALIZADO_CON_PENDIENTES", pendiente.estado());
    }

    @Test void ivaRetenidoNoSeAcreditaSinEvidenciaDeEnteroYMesAplicable() {
        var resultado = MotorIva.evaluar(evidencia("80", "VIGENTE", "VIGENTE", true, true,
                false, false, "160", "160", "0", true));
        igual("80", resultado.realizadoDR());
        igual("80", resultado.potencialDR());
        assertNull(resultado.acreditableDR());
        igual("80", resultado.pendienteTratamientoDR());
        assertTrue(resultado.incidencias().contains("LIVA_5_IV_ENTERO_Y_MES_POSTERIOR_PENDIENTES"));
    }

    @Test void excesosYNulosOriginalesNoProducenConfirmacionFalsa() {
        for (String original : new String[] { null, "-1", "79.99" }) {
            var resultado = MotorIva.evaluar(evidencia("80", "VIGENTE", "VIGENTE", true, true,
                    false, false, original, "0", "0", false));
            sinConfirmacion(resultado);
            assertEquals("REVISAR_CONCILIACION", resultado.estado());
        }
        var negativo = MotorIva.evaluar(valida("-0.01"));
        sinConfirmacion(negativo);
        assertTrue(negativo.incidencias().contains("IMPORTE_DR_NEGATIVO"));
        var ausente = MotorIva.evaluar(valida(null));
        sinConfirmacion(ausente);
        var tratamiento = MotorIva.evaluar(evidencia("80", "VIGENTE", "VIGENTE", true, true,
                false, false, "160", "120", "41", false));
        igual("80", tratamiento.realizadoDR());
        assertNull(tratamiento.acreditableDR());
        assertNull(tratamiento.noAcreditableDR());
        assertEquals("REVISAR_TRATAMIENTO", tratamiento.estado());
    }
}
