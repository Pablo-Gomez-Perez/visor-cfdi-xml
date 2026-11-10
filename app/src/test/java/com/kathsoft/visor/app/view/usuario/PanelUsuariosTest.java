package com.kathsoft.visor.app.view.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;

import javax.swing.GroupLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.kathsoft.visor.app.view.PanelInicio;

/**
 * Verificación headless de los paneles de catálogo, independiente de MySQL.
 * La validación visual en Eclipse WindowBuilder permanece manual.
 */
class PanelUsuariosTest {

    @Test
    void constructorSinServiciosPermiteVistaPreviaYContieneTabla() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelUsuarios catalogo = new PanelUsuarios();
            assertInstanceOf(GroupLayout.class, catalogo.getLayout());
            assertNotNull(encontrarComponente(catalogo, JTable.class));
            assertNotNull(encontrarComponente(catalogo, JScrollPane.class));
            catalogo.recargar(); // no requiere JPA, JDBC ni conexión durante la vista previa
            assertTrue(catalogo.getComponentCount() >= 2);
        });
    }

    @Test
    void inicioEsPanelIndependienteConGroupLayout() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelInicio inicio = new PanelInicio();
            assertInstanceOf(JPanel.class, inicio);
            assertInstanceOf(GroupLayout.class, inicio.getLayout());
            assertEquals(2, inicio.getComponentCount());
        });
    }

    private static <T extends Component> T encontrarComponente(Container contenedor, Class<T> tipo) {
        for (Component componente : contenedor.getComponents()) {
            if (tipo.isInstance(componente)) {
                return tipo.cast(componente);
            }
            if (componente instanceof Container interior) {
                T encontrado = encontrarComponente(interior, tipo);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }
}
