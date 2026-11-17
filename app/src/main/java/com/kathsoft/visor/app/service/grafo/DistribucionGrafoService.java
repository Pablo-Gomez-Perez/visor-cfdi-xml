package com.kathsoft.visor.app.service.grafo;

import com.kathsoft.visor.app.model.dto.grafo.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

/** Layout determinista por distancia al focal; sin animación ni simulación física. */
public final class DistribucionGrafoService {
    public static final double ANCHO=246, ALTO=148;

    /** Posiciones iniciales independientes del estado fiscal. */
    public Map<String,Rectangle2D.Double> distribuir(GrafoFiscal datos){
        Graph<String,AristaFiscal> g=new DirectedPseudograph<>(null,null,false);
        datos.nodos().forEach(n->g.addVertex(n.uuid()));
        datos.aristas().forEach(a->g.addEdge(a.origen(),a.destino(),a));
        var bfs=new BreadthFirstIterator<>(new AsUndirectedGraph<>(g),datos.uuidFocal());
        Map<Integer,List<String>> niveles=new TreeMap<>();
        while(bfs.hasNext()){
            String u=bfs.next();niveles.computeIfAbsent(bfs.getDepth(u),k->new ArrayList<>()).add(u);
        }
        Map<String,Rectangle2D.Double> pos=new LinkedHashMap<>();
        niveles.forEach((nivel,uuids)->{
            uuids.sort(String::compareTo);
            for(int i=0;i<uuids.size();i++){
                double y=(i-(uuids.size()-1)/2.0)*208;
                pos.put(uuids.get(i),new Rectangle2D.Double(nivel*420,y,ANCHO,ALTO));
            }
        });
        return pos;
    }
}
