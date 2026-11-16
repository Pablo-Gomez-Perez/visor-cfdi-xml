package com.kathsoft.visor.app.view.visor;

import static org.junit.jupiter.api.Assertions.*;
import com.kathsoft.visor.app.service.visor.VisorCfdiService.Columna;
import java.awt.Component;
import java.awt.Container;
import java.util.Set;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Construcción sin sesión, BD ni archivos. No sustituye WindowBuilder Design. */
class PanelVisorCfdiTest {
    @Test void construyeSinPersistencia() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new PanelVisorCfdi();
            assertInstanceOf(GroupLayout.class,panel.getLayout());
            JTable t=tabla(panel);assertNotNull(t);assertEquals(Columna.values().length,t.getColumnCount());
            panel.configurar(null,null,null,Set.of());panel.recargar();
        });
    }
    private static JTable tabla(Container c){
        for(Component x:c.getComponents()){
            if(x instanceof JTable t)return t;
            if(x instanceof Container hijo){JTable t=tabla(hijo);if(t!=null)return t;}
        }
        return null;
    }
}
