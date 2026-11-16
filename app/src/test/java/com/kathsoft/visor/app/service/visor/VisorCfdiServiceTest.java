package com.kathsoft.visor.app.service.visor;

import static org.junit.jupiter.api.Assertions.*;
import com.kathsoft.visor.app.service.visor.VisorCfdiService.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contratos de filtros, orden y tipos de columnas. */
class VisorCfdiServiceTest {
    @Test void rangoInvalidoYComodinesLiterales(){
        LocalDate d=LocalDate.of(2026,6,1);
        assertThrows(IllegalArgumentException.class,()->new Filtro(1,d,d.minusDays(1),Fecha.EMISION,"","","","","","",null,null,"","","",""));
        assertEquals("a!%!_!!",VisorCfdiService.escapar("a%_!"));
        assertThrows(IllegalArgumentException.class,()->VisorCfdiService.offset(-1));
    }
    @Test void conservaNulosYTipos(){
        Object[] fila=new Object[Columna.values().length+1];
        fila[0]=1L;fila[1]="UUID";fila[2]='I';
        var f=VisorCfdiService.mapear(fila);
        assertEquals("I",f.valores().get(Columna.TIPO.ordinal()));
        assertNull(f.valores().get(Columna.TOTAL.ordinal()));
        assertThrows(UnsupportedOperationException.class,()->f.valores().set(0,"otro"));
    }
}
