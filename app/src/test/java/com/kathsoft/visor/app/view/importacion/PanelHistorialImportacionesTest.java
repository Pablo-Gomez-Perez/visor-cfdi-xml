package com.kathsoft.visor.app.view.importacion;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.awt.Container;
import java.util.Set;
import javax.swing.GroupLayout;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Construcción sin sesión o base; no sustituye la verificación en WindowBuilder. */
class PanelHistorialImportacionesTest {
    @Test void panelSeConstruyeEnEdtSinPersistencia() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new PanelHistorialImportaciones();
            assertInstanceOf(GroupLayout.class,panel.getLayout());
            JTable tabla=buscar(panel);
            assertNotNull(tabla);
            assertEquals(12,tabla.getColumnCount());
            assertEquals("CFDI archivados",tabla.getColumnName(11));
            panel.configurar(null,null,Set.of());
            panel.recargar();
        });
    }
    private static JTable buscar(Container c) {
        for(Component x:c.getComponents()) {
            if(x instanceof JTable t) return t;
            if(x instanceof Container hijo) {
                JTable t=buscar(hijo);
                if(t!=null)return t;
            }
        }
        return null;
    }
}
