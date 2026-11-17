package com.kathsoft.visor.app.service.grafo;

import com.kathsoft.visor.app.model.dto.grafo.*;
import java.util.*;
import java.util.function.Function;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedPseudograph;

/** Explora dos fronteras por amplitud; conserva ciclos y aristas paralelas. */
public final class ConstructorGrafoFiscal {
    public static final int MAX_NODOS=80, MAX_ARISTAS=200, PROFUNDIDAD=2;

    /** Resultado acotado de una consulta de relaciones. */
    public record Relaciones(List<AristaFiscal> aristas,boolean limitada) {
        public Relaciones {aristas=List.copyOf(aristas);}
    }

    /** Construye mediante cargas por frontera; nunca expande referencias no disponibles. */
    public GrafoFiscal construir(NodoCfdi focal,Function<List<NodoCfdi>,Relaciones> relaciones,
            Function<List<String>,List<NodoCfdi>> resolver) {
        if(!focal.disponible())throw new IllegalArgumentException("Focal no disponible");
        Graph<String,AristaFiscal> grafo=new DirectedPseudograph<>(null,null,false);
        Map<String,NodoCfdi> nodos=new LinkedHashMap<>();
        Set<String> ids=new HashSet<>();
        nodos.put(focal.uuid(),focal);grafo.addVertex(focal.uuid());
        List<NodoCfdi> frontera=List.of(focal);
        boolean limitado=false;
        for(int nivel=0;nivel<PROFUNDIDAD&&!frontera.isEmpty();nivel++){
            Relaciones lote=relaciones.apply(frontera);
            limitado|=lote.limitada();
            Set<String> actuales=new HashSet<>();
            frontera.forEach(n->actuales.add(n.uuid()));
            Set<String> nuevos=new LinkedHashSet<>();
            for(AristaFiscal a:lote.aristas()){
                if(ids.contains(a.id()))continue;
                if(!actuales.contains(a.origen())&&!actuales.contains(a.destino()))continue;
                if(grafo.edgeSet().size()>=MAX_ARISTAS){limitado=true;break;}
                int faltan=(grafo.containsVertex(a.origen())?0:1)
                    +(grafo.containsVertex(a.destino())||a.origen().equals(a.destino())?0:1);
                if(nodos.size()+faltan>MAX_NODOS){limitado=true;continue;}
                for(String u:List.of(a.origen(),a.destino())){
                    if(!grafo.containsVertex(u)){
                        grafo.addVertex(u);nodos.put(u,NodoCfdi.referencia(u));nuevos.add(u);
                    }
                }
                grafo.addEdge(a.origen(),a.destino(),a);ids.add(a.id());
            }
            if(!nuevos.isEmpty()){
                for(NodoCfdi n:resolver.apply(List.copyOf(nuevos))){
                    if(nuevos.contains(n.uuid())&&n.disponible())nodos.put(n.uuid(),n);
                }
            }
            frontera=nuevos.stream().map(nodos::get).filter(NodoCfdi::disponible).toList();
        }
        return new GrafoFiscal(focal.uuid(),List.copyOf(nodos.values()),List.copyOf(grafo.edgeSet()),limitado);
    }
}
