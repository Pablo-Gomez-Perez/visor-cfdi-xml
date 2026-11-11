package com.kathsoft.visor.app.view.contribuyente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Component;
import java.awt.Container;
import java.util.Set;

import javax.swing.GroupLayout;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * Comprueba construcción visual headless sin MySQL; no sustituye abrir el
 * diseñador real de Eclipse WindowBuilder.
 */
class PanelContribuyentesTest {

    @Test
    void catalogoSwingNoRequiereMySqlYContieneTabla() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelContribuyentes panel = new PanelContribuyentes();
            assertInstanceOf(GroupLayout.class, panel.getLayout());
            JTable tabla = buscar(panel, JTable.class);
            assertNotNull(tabla);
            assertEquals(6, tabla.getColumnCount());
            panel.configurar(null, null, Set.of());
            panel.recargar();
        });
    }

    @Test
    void pestañasDeDetalleSePuedenConstruirSinServicios() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelRegimenesContribuyente regimenes = new PanelRegimenesContribuyente();
            PanelAccesosContribuyente accesos = new PanelAccesosContribuyente();
            assertInstanceOf(GroupLayout.class, regimenes.getLayout());
            assertInstanceOf(GroupLayout.class, accesos.getLayout());
            assertEquals(3, buscar(regimenes, JTable.class).getColumnCount());
            assertEquals(4, buscar(accesos, JTable.class).getColumnCount());
        });
    }

    private static <T extends Component> T buscar(Container contenedor, Class<T> clase) {
        for (Component c : contenedor.getComponents()) {
            if (clase.isInstance(c)) return clase.cast(c);
            if (c instanceof Container interior) {
                T encontrado = buscar(interior, clase);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }
}
