package com.kathsoft.visor.app.service.grafo;

import com.kathsoft.visor.app.model.dto.grafo.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Grafo sin BD: ciclos, relaciones paralelas, fronteras, límites y layout. */
class ConstructorGrafoFiscalTest {
    private static String uuid(int n){return "123e4567-e89b-12d3-a456-"+String.format("%012d",n);}
    private static NodoCfdi nodo(int n){
        return new NodoCfdi((long)n,uuid(n),"I","F",""+n,"AAA010101AAA","BBB010101BBB",null,BigDecimal.TEN,"MXN","PPD","SIN_CONSULTA");
    }
    private static AristaFiscal arista(String id,int a,int b){
        return new AristaFiscal(id,uuid(a),uuid(b),TipoRelacionDocumental.CFDI_RELACIONADO,"04",null,null,null);
    }

    @Test void dosNivelesSinArbolNiDuplicarAristas(){
        var relaciones=List.of(arista("a",2,1),arista("b",2,1),arista("c",1,2),arista("d",2,2),
            arista("e",2,3),arista("f",3,4),arista("g",1,5));
        var disponibles=List.of(nodo(1),nodo(2),nodo(3),nodo(4));
        var g=new ConstructorGrafoFiscal().construir(nodo(1),frontera->{
            var ids=frontera.stream().map(NodoCfdi::uuid).toList();
            return new ConstructorGrafoFiscal.Relaciones(relaciones.stream()
                .filter(a->ids.contains(a.origen())||ids.contains(a.destino())).toList(),false);
        },ids->disponibles.stream().filter(n->ids.contains(n.uuid())).toList());
        assertEquals(4,g.nodos().size());assertEquals(6,g.aristas().size());
        assertFalse(g.nodos().stream().anyMatch(n->n.uuid().equals(nodo(4).uuid())));
        assertTrue(g.nodos().stream().anyMatch(n->!n.disponible()&&n.uuid().equalsIgnoreCase(uuid(5))));
        assertFalse(g.limitado());
        var posiciones=new DistribucionGrafoService().distribuir(g);
        assertEquals(4,posiciones.size());
        assertEquals(840.0,posiciones.get(nodo(3).uuid()).x);
        assertEquals(0.0,posiciones.get(nodo(1).uuid()).x);
    }

    @Test void limitaNodosYAristasSinInventarDatos(){
        var muchas=IntStream.rangeClosed(2,150).mapToObj(i->arista("a"+i,1,i)).toList();
        var g=new ConstructorGrafoFiscal().construir(nodo(1),f->new ConstructorGrafoFiscal.Relaciones(muchas,false),u->List.of());
        assertEquals(ConstructorGrafoFiscal.MAX_NODOS,g.nodos().size());assertTrue(g.limitado());
        assertEquals(ConstructorGrafoFiscal.MAX_NODOS-1,g.aristas().size());
        var paralelas=IntStream.rangeClosed(1,250).mapToObj(i->arista("b"+i,1,1)).toList();
        var h=new ConstructorGrafoFiscal().construir(nodo(1),f->new ConstructorGrafoFiscal.Relaciones(paralelas,false),u->List.of());
        assertEquals(ConstructorGrafoFiscal.MAX_ARISTAS,h.aristas().size());assertTrue(h.limitado());
    }

    @Test void etiquetaRepConMonedaDelDocumento(){
        var a=new AristaFiscal("P:1",uuid(2),uuid(1),TipoRelacionDocumental.COMPLEMENTO_PAGO,null,2L,new BigDecimal("50.00"),"USD");
        assertEquals("REP #2 · 50 USD",a.etiqueta());
    }
}
