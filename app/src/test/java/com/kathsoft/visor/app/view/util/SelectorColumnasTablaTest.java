package com.kathsoft.visor.app.view.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

/** Ocultar columnas es sólo presentación y persiste por usuario/sección. */
class SelectorColumnasTablaTest {
    @Test void visibilidadPersistenciaYNavegacionPorModelo()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            String namespace="test-"+Long.toString(System.nanoTime(),36);
            var selector=new SelectorColumnasTabla(namespace);
            var tabla=new JTable(new DefaultTableModel(new Object[][]{{42L,"FAC-001","100.00"}},new Object[]{"Documento","UUID","Total"}));
            selector.actualizar(tabla,"FINANCIERO");
            assertEquals(List.of("Documento","UUID","Total"),selector.visibles(tabla));
            JPopupMenu menu=selector.menu(tabla,"FINANCIERO");
            ((JCheckBoxMenuItem)menu.getComponent(0)).doClick();
            assertEquals(List.of("UUID","Total"),selector.visibles(tabla));
            assertEquals(42L,tabla.getModel().getValueAt(0,0));
            assertEquals(3,tabla.getModel().getColumnCount());

            // Un nuevo TableModel, como el paginado y el F5, recupera preferencia.
            tabla.setModel(new DefaultTableModel(new Object[][]{{43L,"FAC-002","200.00"}},new Object[]{"Documento","UUID","Total"}));
            selector.actualizar(tabla,"FINANCIERO");
            assertEquals(List.of("UUID","Total"),selector.visibles(tabla));
            assertEquals(43L,tabla.getModel().getValueAt(0,0));

            var otroUsuario=new SelectorColumnasTabla(namespace+"-otro");
            var otraTabla=new JTable(new DefaultTableModel(new Object[][]{{1L,"a","2"}},new Object[]{"Documento","UUID","Total"}));
            otroUsuario.actualizar(otraTabla,"FINANCIERO");
            assertEquals(3,otroUsuario.visibles(otraTabla).size());

            // Las otras pestañas son independientes.
            var otraSeccion=new JTable(new DefaultTableModel(new Object[][]{{1L,"z","3"}},new Object[]{"Documento","UUID","Total"}));
            selector.actualizar(otraSeccion,"IVA_RESUMEN");
            assertEquals(3,selector.visibles(otraSeccion).size());

            JPopupMenu ultimo=selector.menu(tabla,"FINANCIERO");
            ((JCheckBoxMenuItem)ultimo.getComponent(1)).doClick();
            assertEquals(List.of("Total"),selector.visibles(tabla));
            ((JCheckBoxMenuItem)selector.menu(tabla,"FINANCIERO").getComponent(2)).doClick();
            assertEquals(List.of("Total"),selector.visibles(tabla)); // no permite ocultar todas

            JPopupMenu restaurar=selector.menu(tabla,"FINANCIERO");
            ((javax.swing.JMenuItem)restaurar.getComponent(restaurar.getComponentCount()-1)).doClick();
            assertEquals(List.of("Documento","UUID","Total"),selector.visibles(tabla));

            var recargado=new SelectorColumnasTabla(namespace);
            tabla.setModel(new DefaultTableModel(new Object[][]{{44L,"FAC-003","300.00"}},new Object[]{"Documento","UUID","Total"}));
            recargado.actualizar(tabla,"FINANCIERO");
            assertEquals(3,recargado.visibles(tabla).size());
        });
    }

    @Test void sinColumnasMuestraOpcionDeshabilitada()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var s=new SelectorColumnasTabla("test-vacio-"+Long.toString(System.nanoTime(),36));
            var tabla=new JTable();
            tabla.setModel(new DefaultTableModel());
            var menu=s.menu(tabla,"INGRESOS");
            assertEquals(1,menu.getComponentCount());
            assertFalse(menu.getComponent(0).isEnabled());
        });
    }
}
