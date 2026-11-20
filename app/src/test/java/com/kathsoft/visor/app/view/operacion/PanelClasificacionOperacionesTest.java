package com.kathsoft.visor.app.view.operacion;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.awt.Container;
import java.util.Set;
import java.util.List;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Operacion;
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
            assertNotNull(t.getColumnModel().getColumn(0).getCellEditor());
            assertInstanceOf(JComboBox.class,t.getColumnModel().getColumn(0).getCellRenderer());
            assertEquals(JTable.AUTO_RESIZE_OFF,t.getAutoResizeMode());
            assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,t.getSelectionModel().getSelectionMode());
            assertEquals("aplicarCategoria",t.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,InputEvent.CTRL_DOWN_MASK)));
            assertNotNull(t.getActionMap().get("aplicarCategoria"));
            assertNotNull(p.getActionMap().get("actualizarOperaciones"));
            JSplitPane split=buscar(p,JSplitPane.class);assertNotNull(split);
            assertInstanceOf(JTabbedPane.class,split.getRightComponent());
            JTabbedPane pestanas=(JTabbedPane)split.getRightComponent();
            assertEquals("Operaciones",pestanas.getTitleAt(0));
            assertEquals("Resumen de clasificación",pestanas.getTitleAt(1));
        });
    }
    @Test void motivoAutomaticoSoloPrimeraAsignacion(){
        Operacion inicial=operacion(0,null),retirada=operacion(3,null),clasificada=operacion(4,2L);
        assertTrue(PanelClasificacionOperaciones.motivoAutomatico(List.of(inicial),1L));
        assertFalse(PanelClasificacionOperaciones.motivoAutomatico(List.of(inicial),null));
        assertFalse(PanelClasificacionOperaciones.motivoAutomatico(List.of(retirada),1L));
        assertFalse(PanelClasificacionOperaciones.motivoAutomatico(List.of(inicial,clasificada),1L));
        assertFalse(PanelClasificacionOperaciones.motivoAutomatico(List.of(),1L));
    }
    private static Operacion operacion(long revision,Long categoria){
        return new Operacion(1,"uuid","I","RECIBIDO",null,null,null,null,null,null,null,null,"MXN","SIN_CONSULTA",revision,categoria,null,null);
    }
    private static <T extends Component> T buscar(Container p,Class<T> tipo){
        for(Component c:p.getComponents()){
            if(tipo.isInstance(c))return tipo.cast(c);
            if(c instanceof Container x){T v=buscar(x,tipo);if(v!=null)return v;}
        }
        return null;
    }
    private static JTable tabla(Container p){
        for(Component c:p.getComponents()){
            if(c instanceof JTable t)return t;
            if(c instanceof Container x){JTable t=tabla(x);if(t!=null)return t;}
        }
        return null;
    }
}
