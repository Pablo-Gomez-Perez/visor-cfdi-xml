package com.kathsoft.visor.app.view.grafo;

import com.kathsoft.visor.app.model.dto.grafo.*;
import com.kathsoft.visor.app.service.grafo.DistribucionGrafoService;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Construcción, renderizado e interacción sin MySQL ni pantalla física. */
class PanelGrafoCfdiTest {
    @Test void seleccionZoomArrastreYDobleClic()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            String uuid="123e4567-e89b-12d3-a456-000000000001";
            var n=new NodoCfdi(1L,uuid,"I","F","1",null,null,null,null,null,null,"SIN_CONSULTA");
            var g=new GrafoFiscal(n.uuid(),List.of(n),List.of(),false);
            var panel=new PanelGrafoCfdi();panel.setSize(650,450);
            AtomicReference<NodoCfdi> abierto=new AtomicReference<>();
            AtomicReference<NodoCfdi> seleccionado=new AtomicReference<>();
            panel.configurar(seleccionado::set,abierto::set);
            panel.mostrar(g,new DistribucionGrafoService().distribuir(g));panel.ajustar();panel.centrar();
            var imagen=new BufferedImage(650,450,BufferedImage.TYPE_INT_ARGB);
            var graphics=imagen.createGraphics();try{panel.paint(graphics);}finally{graphics.dispose();}
            panel.dispatchEvent(new MouseWheelEvent(panel,MouseEvent.MOUSE_WHEEL,1,0,325,225,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,-1));
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,2,InputEvent.BUTTON1_DOWN_MASK,325,225,1,false,MouseEvent.BUTTON1));
            assertEquals(n,seleccionado.get());
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_DRAGGED,3,InputEvent.BUTTON1_DOWN_MASK,365,245,0,false,MouseEvent.NOBUTTON));
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,4,0,365,245,1,false,MouseEvent.BUTTON1));
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_CLICKED,5,0,365,245,2,false,MouseEvent.BUTTON1));
            assertEquals(n,abierto.get());
        });
    }
}
