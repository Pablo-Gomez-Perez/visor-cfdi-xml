package com.kathsoft.visor.app.view.fiscal;

import static org.junit.jupiter.api.Assertions.*;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.service.fiscal.ResumenFiscalService.Seccion;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

/** Construcción sin persistencia y selección por identidad de modelo, sin diseñador visual externo. */
class PanelResumenFiscalTest {

    @Test void constructorSinServiciosConLayoutExplicitoYPestanasDeTrabajo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new PanelResumenFiscal();
            panel.setSize(740, 490);
            panel.doLayout();
            assertInstanceOf(GroupLayout.class, panel.getLayout());
            var pestanas = campo(panel, "pestanas", JTabbedPane.class);
            var nombres = new ArrayList<String>();
            for (int i = 0; i < pestanas.getTabCount(); i++) nombres.add(pestanas.getTitleAt(i));
            assertTrue(nombres.containsAll(List.of("Periodo", "Ingresos", "Egresos", "IVA por pago", "IEPS",
                    "ISR y retenciones", "Terceros", "Financiero", "Incidencias", "Orígenes",
                    "Impuestos concepto", "Impuestos globales", "Impuestos pago", "Totales originales", "Conciliación")));
            assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, pestanas.getTabLayoutPolicy());
            for (JPanel auxiliar : componentes(panel, JPanel.class)) {
                if (auxiliar == campo(panel, "acciones", JPanel.class) || auxiliar == campo(panel, "filtros", JPanel.class))
                    assertInstanceOf(GroupLayout.class, auxiliar.getLayout());
            }
            assertFalse(componentes(panel, JTable.class).isEmpty());
            for (JTable tabla : componentes(panel, JTable.class)) {
                assertEquals(JTable.AUTO_RESIZE_OFF, tabla.getAutoResizeMode());
                assertEquals(ListSelectionModel.SINGLE_SELECTION, tabla.getSelectionModel().getSelectionMode());
                assertEquals(0, tabla.getRowCount());
                // Los modelos vacíos del constructor no contienen ninguna celda editable.
                for (int r = 0; r < tabla.getRowCount(); r++)
                    for (int c = 0; c < tabla.getColumnCount(); c++) assertFalse(tabla.isCellEditable(r, c));
            }
            for (String nombre : List.of("buscar", "anterior", "siguiente", "verDetalle", "origenes", "evidencia", "exportar"))
                assertFalse(campo(panel, nombre, JButton.class).isEnabled(), nombre);
            assertFalse(campo(panel, "titular", JComboBox.class).isEnabled());
            panel.configurar(null, null, null, null, Set.of());
            assertFalse(campo(panel, "buscar", JButton.class).isEnabled());
            assertEquals(0, seleccionado(panel, false));
        });
    }

    @Test void atajosRegistradosFuncionanSinSesionNiConsultas() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new PanelResumenFiscal();
            var mapa = panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            Object actualizar = mapa.get(KeyStroke.getKeyStroke("F5"));
            Object buscarRfc = mapa.get(KeyStroke.getKeyStroke("control F"));
            assertEquals("actualizar", actualizar);
            assertEquals("buscarRFC", buscarRfc);
            assertNotNull(panel.getActionMap().get(actualizar));
            assertNotNull(panel.getActionMap().get(buscarRfc));
            assertDoesNotThrow(() -> panel.getActionMap().get(actualizar).actionPerformed(new ActionEvent(panel, 0, "F5")));
            assertDoesNotThrow(() -> panel.getActionMap().get(buscarRfc).actionPerformed(new ActionEvent(panel, 0, "Ctrl+F")));
            assertFalse(campo(panel, "buscar", JButton.class).isEnabled());
        });
    }

    @Test void seleccionOrdenadaUsaIndiceDeModeloYPriorizaFacturaParaEvidencia() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new PanelResumenFiscal();
            JTable tabla = campo(panel, "tablaGenerales", JTable.class);
            tabla.setModel(new DefaultTableModel(new Object[][] {
                { 100L, 900L, "B" }, { 10L, 700L, "A" }
            }, new Object[] { "Documento", "Factura documento", "Descripción" }) {
                @Override public boolean isCellEditable(int fila, int columna) { return false; }
            });
            tabla.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.ASCENDING)));
            tabla.setRowSelectionInterval(0, 0);
            assertEquals(1, tabla.convertRowIndexToModel(0));
            assertEquals(10L, seleccionado(panel, false));
            assertEquals(700L, seleccionado(panel, true));
            tabla.moveColumn(2, 0);
            assertEquals(10L, seleccionado(panel, false));
            tabla.setRowSelectionInterval(1, 1);
            assertEquals(100L, seleccionado(panel, false));
            assertEquals(900L, seleccionado(panel, true));
            tabla.clearSelection();
            assertEquals(0, seleccionado(panel, false));
            tabla.setModel(new DefaultTableModel(new Object[][] {{ 55L }}, new Object[] { "REP documento" }));
            tabla.setRowSelectionInterval(0, 0);
            assertEquals(55L, seleccionado(panel, false));
            assertEquals(0, seleccionado(panel, true));
            tabla.setModel(new DefaultTableModel(new Object[][] {{ "55" }}, new Object[] { "Documento" }));
            tabla.setRowSelectionInterval(0, 0);
            assertEquals(0, seleccionado(panel, false));
        });
    }

    @Test void origenDesdeIvaSeleccionaFacturaAnteriorYConservaPeriodoSinSesion() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new PanelResumenFiscal();
            var pestanas = campo(panel, "pestanas", JTabbedPane.class);
            pestanas.setSelectedIndex(Seccion.IVA.ordinal());
            var julio = new FiltroFiscal(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
            establecer(panel, "aplicado", julio);
            JTable tabla = campo(panel, "tablaIva", JTable.class);
            tabla.setModel(new DefaultTableModel(new Object[][] {
                { 2008L, 1008L, "USD", "VIGENTE", "RECIBIDO" },
                { 2002L, 1001L, "MXN", "VIGENTE", "RECIBIDO" }
            }, new Object[] { "REP documento", "Factura documento", "MonedaDR", "SAT factura", "Sentido factura" }));
            tabla.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.ASCENDING)));
            tabla.setRowSelectionInterval(0, 0);
            assertEquals(1, tabla.convertRowIndexToModel(0));
            assertEquals(1001, seleccionado(panel, true));
            assertEquals(2002, seleccionado(panel, false));
            campo(panel, "origenes", JButton.class).setEnabled(true);
            invocar(panel, "abrirOrigenes");
            assertEquals(Seccion.ORIGENES.ordinal(), pestanas.getSelectedIndex());
            FiltroFiscal contexto = campo(panel, "aplicado", FiltroFiscal.class);
            assertEquals(julio.desde(), contexto.desde());
            assertEquals(julio.hasta(), contexto.hasta());
            // Sin servicio ni sesión, consultar no inicia persistencia. El filtro con documento
            // se ejercita contra MySQL en MvpFiscalServiceIT para recuperar la factura de junio.
            assertNull(campo(panel, "servicio", Object.class));
            assertNull(campo(panel, "sesion", Object.class));
        });
    }

    private static void invocar(Object objeto, String nombre) {
        try {
            Method metodo = objeto.getClass().getDeclaredMethod(nombre);
            metodo.setAccessible(true);
            metodo.invoke(objeto);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void establecer(Object objeto, String nombre, Object valor) {
        try {
            Field campo = objeto.getClass().getDeclaredField(nombre);
            campo.setAccessible(true);
            campo.set(objeto, valor);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static long seleccionado(PanelResumenFiscal panel, boolean factura) {
        try {
            Method metodo = PanelResumenFiscal.class.getDeclaredMethod("documentoSeleccionado", boolean.class);
            metodo.setAccessible(true);
            return ((Number) metodo.invoke(panel, factura)).longValue();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static <T> T campo(Object objeto, String nombre, Class<T> tipo) {
        try {
            Field campo = objeto.getClass().getDeclaredField(nombre);
            campo.setAccessible(true);
            return tipo.cast(campo.get(objeto));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static <T extends Component> List<T> componentes(Container raiz, Class<T> tipo) {
        List<T> encontrados = new ArrayList<>();
        for (Component componente : raiz.getComponents()) {
            if (tipo.isInstance(componente)) encontrados.add(tipo.cast(componente));
            if (componente instanceof Container contenedor) encontrados.addAll(componentes(contenedor, tipo));
        }
        return encontrados;
    }
}
