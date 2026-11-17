package com.kathsoft.visor.app.view.grafo;

import com.kathsoft.visor.app.model.dto.grafo.*;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

/** Composición de aristas antes de tarjetas; separa referencias paralelas. */
final class RenderizadorGrafo {
    private final RenderizadorNodoCfdi nodos=new RenderizadorNodoCfdi();
    private final RenderizadorAristaFiscal aristas=new RenderizadorAristaFiscal();

    void dibujar(Graphics2D g,GrafoFiscal grafo,Map<String,Rectangle2D.Double> posiciones,String seleccionado){
        Map<String,List<AristaFiscal>> grupos=new LinkedHashMap<>();
        for(AristaFiscal a:grafo.aristas()){
            String clave=a.origen().compareTo(a.destino())<=0?a.origen()+":"+a.destino():a.destino()+":"+a.origen();
            grupos.computeIfAbsent(clave,k->new ArrayList<>()).add(a);
        }
        for(List<AristaFiscal> grupo:grupos.values()){
            for(int i=0;i<grupo.size();i++){
                AristaFiscal a=grupo.get(i);
                double offset=(i-(grupo.size()-1)/2.0)*42;
                if(a.origen().compareTo(a.destino())>0)offset=-offset;
                aristas.dibujar(g,a,posiciones.get(a.origen()),posiciones.get(a.destino()),offset,
                    a.origen().equals(seleccionado)||a.destino().equals(seleccionado));
            }
        }
        for(NodoCfdi n:grafo.nodos())nodos.dibujar(g,n,posiciones.get(n.uuid()),grafo.uuidFocal().equals(n.uuid()),n.uuid().equals(seleccionado));
    }
}
