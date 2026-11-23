package com.kathsoft.visor.app.service.fiscal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CSV conserva originales decimales y protege datos textuales interpretables como fórmulas. */
class ExportacionFiscalServiceTest {

    private static String fila(Object... valores) throws Exception {
        var writer = new StringWriter();
        ExportacionFiscalService.fila(writer, Arrays.asList(valores));
        return writer.toString();
    }

    @Test void conservaDecimalesExactosNulosYNegativosNumericos() throws Exception {
        assertEquals("\"\",\"123.450000\",\"-123.450000\",\"100000000000000000000\",\"-7\"\r\n",
                fila(null, new BigDecimal("123.450000"), new BigDecimal("-123.450000"), new BigDecimal("1E+20"), -7));
        assertEquals("\"0.000000\",\"0.000001\"\r\n", fila(new BigDecimal("0.000000"), new BigDecimal("0.000001")));
    }

    @Test void escapaComillasComasYSaltosSinPerderTexto() throws Exception {
        assertEquals("\"Dijo \"\"sí\"\", lista\",\"línea uno\nlínea dos\",\"CR\rLF\r\nfinal\"\r\n",
                fila("Dijo \"sí\", lista", "línea uno\nlínea dos", "CR\rLF\r\nfinal"));
        assertEquals("\"\",\"\",\"árbol y ñ\"\r\n", fila(null, "", "árbol y ñ"));
    }

    @Test void neutralizaFormulasTextualesIncluyendoEspaciosIniciales() throws Exception {
        for (String texto : List.of("=1+2", "+SUM(A1,A2)", "-1", "@SUM(A1)", "  =1+2", "\t+1", "  -3", "\t@A1")) {
            assertEquals("\"'" + texto + "\"\r\n", fila(texto), texto);
        }
        assertEquals("\"-3\",\"'-3\"\r\n", fila(new BigDecimal("-3"), "-3"));
        assertEquals("\"texto = 1\",\"  texto\"\r\n", fila("texto = 1", "  texto"));
    }

    @Test void neutralizaFormulaAunqueContengaSaltosDeLinea() throws Exception {
        for (String texto : List.of("=1+2\nsegunda línea", "  +SUM(A1)\r\n", "@A1\r", "\t-3\n")) {
            assertEquals("\"'" + texto + "\"\r\n", fila(texto), texto);
        }
    }

    @Test void filaVaciaConservaTerminadorCsv() throws Exception {
        var writer = new StringWriter();
        ExportacionFiscalService.fila(writer, List.of());
        assertEquals("\r\n", writer.toString());
    }
}
