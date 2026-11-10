package com.kathsoft.visor.app.view.rol;

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
 * Construcción headless de PanelRoles, separada del test manual WindowBuilder.
 */
class PanelRolesTest {

    @Test
    void panelDeRolesSeConstruyeSinBaseDeDatosNiSesiones() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelRoles panel = new PanelRoles();
            assertInstanceOf(GroupLayout.class, panel.getLayout());
            JTable tabla = encontrar(panel, JTable.class);
            assertNotNull(tabla);
            assertEquals(5, tabla.getColumnCount());
            panel.configurar(null, null, Set.of());
            panel.recargar();
        });
    }

    private static <T extends Component> T encontrar(Container c, Class<T> tipo) {
        for (Component item : c.getComponents()) {
            if (tipo.isInstance(item)) return tipo.cast(item);
            if (item instanceof Container interior) {
                T encontrado = encontrar(interior, tipo);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }
}
