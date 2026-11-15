package com.kathsoft.visor.app.service.importacion;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LectorPagos10Test {
    @TempDir Path temporal;
    private static final String UUID = "123e4567-e89b-12d3-a456-426614174020";

    private JsonNode leer(String version,String complemento) throws Exception {
        Path archivo = temporal.resolve("pago.xml");
        Files.writeString(archivo,EjemplosPagosHistoricos.xml(version,UUID,complemento));
        return new ObjectMapper().readTree(new LectorCfdiXml().leer(archivo).json());
    }

    @Test void historicoSinTotales() throws Exception {
        JsonNode j = leer("3.3",EjemplosPagosHistoricos.PAGO10).path("pagos");
        assertEquals("1.0",j.path("version").asText());
        assertFalse(j.has("totales"));
        JsonNode d = j.path("pagos").get(0).path("documentos").get(0);
        assertEquals("PPD",d.path("metodoPagoDR").asText());
        assertEquals("50.00",d.path("pagado").asText());
        assertFalse(d.has("equivalencia"));
        assertFalse(d.has("objeto"));
    }

    @Test void omitidosYGruposNoSeInventan() throws Exception {
        JsonNode p = leer("3.3",EjemplosPagosHistoricos.PAGO10_IMPUESTOS)
                .path("pagos").path("pagos").get(0);
        JsonNode d = p.path("documentos").get(0);
        assertEquals("123-45-123456789",d.path("idHistorico").asText());
        assertFalse(d.has("uuid"));
        assertFalse(d.has("pagado"));
        assertEquals(2,p.path("gruposImpuestos10").size());
        assertEquals("5.00",p.path("gruposImpuestos10").get(0).path("retenidos").asText());
        assertEquals(2,p.path("impuestos").get(1).path("grupo10").asInt());
        assertFalse(p.path("impuestos").get(1).has("base"));
    }

    @Test void tipoCambioHistoricoSeConserva() throws Exception {
        String c = EjemplosPagosHistoricos.PAGO10.replace("MonedaDR=\"MXN\"","MonedaDR=\"USD\" TipoCambioDR=\"0.050000\"");
        JsonNode d = leer("3.3",c).path("pagos").path("pagos").get(0).path("documentos").get(0);
        assertEquals("0.050000",d.path("tipoCambioDR").asText());
        assertFalse(d.has("equivalencia"));
    }

    @Test void namespaceVersionIncompatiblesYDiagnosticoDelTimbrePosterior() throws Exception {
        String c = EjemplosPagosHistoricos.PAGO10.replace("Version=\"1.0\"","Version=\"9.0\"");
        assertThrows(IllegalArgumentException.class,()->leer("3.3",c));
        String d = DiagnosticoXml.describir(temporal.resolve("pago.xml"));
        assertTrue(d.contains(UUID));
        assertTrue(d.contains("versión=9.0"));
        assertTrue(d.contains("http://www.sat.gob.mx/Pagos"));
        assertThrows(IllegalArgumentException.class,()->leer("4.0",EjemplosPagosHistoricos.PAGO10));
    }

    @Test void version20Permanece() throws Exception {
        JsonNode j = leer("4.0",EjemplosPagosHistoricos.PAGO20).path("pagos");
        assertEquals("2.0",j.path("version").asText());
        assertEquals("50.00",j.path("totales").path("MontoTotalPagos").asText());
    }
}
