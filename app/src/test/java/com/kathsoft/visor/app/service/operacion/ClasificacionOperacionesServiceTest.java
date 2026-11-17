package com.kathsoft.visor.app.service.operacion;

import static org.junit.jupiter.api.Assertions.*;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Contratos de entrada, fechas y selección optimista. */
class ClasificacionOperacionesServiceTest {
    @Test void entradasValidasYRechazoDeInvalidas(){
        assertEquals("GASTO_01",ClasificacionOperacionesService.validarClave(" gasto_01 "));
        assertThrows(IllegalArgumentException.class,()->ClasificacionOperacionesService.validarClave("a/b"));
        assertThrows(IllegalArgumentException.class,()->ClasificacionOperacionesService.obligatorio("  ",600));
        assertThrows(IllegalArgumentException.class,()->new Seleccion(1,-1));
        LocalDate fecha=LocalDate.of(2026,6,1);
        assertThrows(IllegalArgumentException.class,()->new Filtro(1,fecha,fecha.minusDays(1),"","",-1));
        assertThrows(IllegalArgumentException.class,()->new Filtro(1,fecha,fecha,"","%_",0));
        assertEquals("AAA010101AAA",new Filtro(1,fecha,fecha,"RECIBIDO","aaa010101aaa",0).rfc());
    }
}
