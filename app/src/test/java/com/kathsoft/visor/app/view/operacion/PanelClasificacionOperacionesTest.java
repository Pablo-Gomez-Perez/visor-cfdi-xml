package com.kathsoft.visor.app.view.operacion;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.awt.Container;
import java.util.Set;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Construcción sin BD, usuario o ventana real. */
class PanelClasificacionOperacionesTest {
    @Test void constructorDisenoSinPersistencia()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var p=new PanelClasificacionOperaciones();p.setSize(740,490);
            assertInstanceOf(GroupLayout.class,p.getLayout());
            p.configurar(null,null,null,Set.of());p.recargar();
            JTable t=tabla(p);assertNotNull(t);assertEquals(15,t.getColumnCount());
            assertFalse(t.getModel().isCellEditable(0,0));
            assertFalse(t.isEnabled());
        });
    }
    private static JTable tabla(Container p){
        for(Component c:p.getComponents()){
            if(c instanceof JTable t)return t;
            if(c instanceof Container x){JTable t=tabla(x);if(t!=null)return t;}
        }
        return null;
    }
}
