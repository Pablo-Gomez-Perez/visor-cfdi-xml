package com.kathsoft.visor.app.view.clasificacion;

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
 * Construcción Swing sin MySQL ni sesión, compatible estructuralmente con
 * WindowBuilder; no sustituye abrir el formulario en Eclipse Design.
 */
class PanelClasificacionCfdiTest {

    @Test
    void panelEsIndependienteDePersistenciaYDisponeDeTabla() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelClasificacionCfdi panel = new PanelClasificacionCfdi();
            assertInstanceOf(GroupLayout.class, panel.getLayout());
            JTable tabla = encontrar(panel, JTable.class);
            assertNotNull(tabla);
            assertEquals(9, tabla.getColumnCount());
            panel.configurar(null, null, Set.of());
            panel.recargar();
        });
    }

    private static <T extends Component> T encontrar(Container contenedor, Class<T> tipo) {
        for (Component componente : contenedor.getComponents()) {
            if (tipo.isInstance(componente)) return tipo.cast(componente);
            if (componente instanceof Container interior) {
                T encontrado = encontrar(interior, tipo);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }
}
