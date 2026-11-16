package com.kathsoft.visor.app.service.importacion;

import static org.junit.jupiter.api.Assertions.*;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Pagina;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Rango;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contratos de fechas y páginas sin persistencia. */
class HistorialImportacionesServiceTest {
    @Test void rangoInclusivoRechazaFechasInvertidas() {
        LocalDate dia=LocalDate.of(2026,11,15);
        assertEquals(dia,new Rango(dia,dia).hasta());
        assertThrows(IllegalArgumentException.class,()->new Rango(dia.plusDays(1),dia));
        assertThrows(NullPointerException.class,()->new Rango(null,dia));
        assertThrows(IllegalArgumentException.class,()->new Rango(LocalDate.of(999,1,1),dia));
    }
    @Test void filtrosValidanIdentificadoresYEstado() {
        var rango=new Rango(LocalDate.of(2026,11,15),LocalDate.of(2026,11,16));
        assertThrows(IllegalArgumentException.class,()->new HistorialImportacionesService.Filtro(
            rango,-1,0,HistorialImportacionesService.EstadoLote.TODOS));
        assertThrows(NullPointerException.class,()->new HistorialImportacionesService.Filtro(rango,0,0,null));
    }
    @Test void paginaCopiaListaYNoExponeMutacion() {
        var origen=new ArrayList<>(List.of("a"));
        var pagina=new Pagina<>(origen,true);
        origen.clear();
        assertEquals(List.of("a"),pagina.filas());
        assertThrows(UnsupportedOperationException.class,()->pagina.filas().clear());
    }
}
