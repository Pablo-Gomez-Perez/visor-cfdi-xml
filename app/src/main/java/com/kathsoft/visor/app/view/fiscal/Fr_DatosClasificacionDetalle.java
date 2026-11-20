package com.kathsoft.visor.app.view.fiscal;

import com.kathsoft.visor.app.service.fiscal.ClasificacionDetalleService;
import com.kathsoft.visor.app.service.fiscal.ClasificacionDetalleService.*;
import com.kathsoft.visor.app.service.fiscal.DistribucionDecimal;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Categoria;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Editor de un comprobante: distribuciones por concepto y tratamientos, sin E/S en construcción. */
public class Fr_DatosClasificacionDetalle extends JDialog {
    private static final long serialVersionUID=1L;
    private JPanel contenido, acciones;
    private JTabbedPane pestanas;
    private JTable tablaConceptos, tablaImpuestos, tablaHistorial, tablaAsignaciones;
    private JScrollPane scrollConceptos, scrollImpuestos, scrollHistorial, scrollAsignaciones;
    private JSplitPane division;
    private JLabel estado;
    private JButton anterior, siguiente, agregar, quitar, guardar, actualizar;
    private JTextField motivo;
    private JComboBox<String> modo;
    private JComboBox<Opcion> destinos;
    private DefaultTableModel conceptos, impuestos, historial, asignaciones;
    private ClasificacionDetalleService servicio;
    private Long actor;
    private long titular, documento, origen;
    private Tipo tipo;
    private Detalle detalle;
    private List<Concepto> filas=List.of();
    private List<Impuesto> filasImpuesto=List.of();
    private List<Categoria> categorias=List.of();
    private List<Tratamiento> tratamientos=List.of();
    private int pagina, generacion;
    private boolean ocupado, editable;
    private record Opcion(long id,String nombre) { @Override public String toString(){return nombre;} }

    /** Construcción exclusivamente visual compatible con inspección por WindowBuilder. */
    public Fr_DatosClasificacionDetalle() {
        setTitle("Clasificación detallada — conceptos e impuestos originales");
        setModal(true); setSize(730,480); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        contenido=new JPanel(); setContentPane(contenido);
        acciones=new JPanel();
        anterior=new JButton("Anterior"); siguiente=new JButton("Siguiente"); actualizar=new JButton("Actualizar");
        agregar=new JButton("Añadir"); quitar=new JButton("Quitar"); guardar=new JButton("Guardar");
        motivo=new JTextField(); motivo.setToolTipText("Motivo obligatorio al reclasificar o retirar");
        modo=new JComboBox<>(new String[]{"Importe","Proporción [0,1]"}); destinos=new JComboBox<>();
        estado=new JLabel("Seleccione un concepto. El detalle sustituye la clasificación general; el remanente queda pendiente.");
        conceptos=lectura(new String[]{"ID","Nº","Producto","Descripción","Importe","Descuento","Neto","Objeto","General"});
        impuestos=lectura(new String[]{"ID","Impuesto","Movimiento","Factor","Base","Tasa/cuota","Importe"});
        historial=lectura(new String[]{"Revisión","Fecha","Usuario","Acción","Motivo","Origen","Anterior","Resultante"});
        asignaciones=new DefaultTableModel(new String[]{"Categoría / tratamiento","Importe / proporción","Observación / justificación"},0){
            @Override public boolean isCellEditable(int r,int c){return editable&&!ocupado;}
        };
        tablaConceptos=new JTable(conceptos); tablaImpuestos=new JTable(impuestos); tablaHistorial=new JTable(historial); tablaAsignaciones=new JTable(asignaciones);
        tablaConceptos.setAutoCreateRowSorter(true); tablaImpuestos.setAutoCreateRowSorter(true);
        tablaConceptos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); tablaImpuestos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaAsignaciones.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(destinos));
        tablaConceptos.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); tablaImpuestos.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); tablaHistorial.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        scrollConceptos=new JScrollPane(tablaConceptos); scrollImpuestos=new JScrollPane(tablaImpuestos); scrollHistorial=new JScrollPane(tablaHistorial); scrollAsignaciones=new JScrollPane(tablaAsignaciones);
        pestanas=new JTabbedPane(); pestanas.addTab("Conceptos",scrollConceptos); pestanas.addTab("Impuestos del concepto",scrollImpuestos); pestanas.addTab("Historial",scrollHistorial);
        division=new JSplitPane(JSplitPane.VERTICAL_SPLIT,pestanas,scrollAsignaciones); division.setResizeWeight(0.6);
        GroupLayout a=new GroupLayout(acciones); acciones.setLayout(a); a.setAutoCreateGaps(true);
        a.setHorizontalGroup(a.createSequentialGroup().addComponent(anterior).addComponent(siguiente).addComponent(actualizar).addComponent(modo).addComponent(agregar).addComponent(quitar).addComponent(guardar));
        a.setVerticalGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(anterior).addComponent(siguiente).addComponent(actualizar).addComponent(modo).addComponent(agregar).addComponent(quitar).addComponent(guardar));
        GroupLayout g=new GroupLayout(contenido); contenido.setLayout(g);g.setAutoCreateGaps(true);g.setAutoCreateContainerGaps(true);
        g.setHorizontalGroup(g.createParallelGroup().addComponent(acciones).addComponent(division).addComponent(motivo).addComponent(estado));
        g.setVerticalGroup(g.createSequentialGroup().addComponent(acciones,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE).addComponent(division,150,300,Short.MAX_VALUE).addComponent(motivo,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE).addComponent(estado));
        anterior.addActionListener(e->{if(pagina>0){pagina--;cargar();}}); siguiente.addActionListener(e->{pagina++;cargar();});actualizar.addActionListener(e->cargar());
        agregar.addActionListener(e->{if(destinos.getItemCount()>0)asignaciones.addRow(new Object[]{destinos.getItemAt(0),BigDecimal.ZERO,""});});
        quitar.addActionListener(e->{int r=tablaAsignaciones.getSelectedRow();if(r>=0)asignaciones.removeRow(tablaAsignaciones.convertRowIndexToModel(r));});
        guardar.addActionListener(e->guardar());
        tablaConceptos.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!ocupado)seleccionarConcepto();});
        tablaImpuestos.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!ocupado)seleccionarImpuesto();});
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"),"cancelar");
        getRootPane().getActionMap().put("cancelar",new AbstractAction(){@Override public void actionPerformed(java.awt.event.ActionEvent e){if(tablaAsignaciones.isEditing())tablaAsignaciones.getCellEditor().cancelCellEditing();}});
        controles();
    }
    /** Inyecta servicios y contexto después de construir componentes; abre consultas asíncronas. */
    public void configurar(ClasificacionDetalleService servicio,Long actor,long titular,long documento,List<Categoria> categorias,boolean editable) {
        this.servicio=servicio;this.actor=actor;this.titular=titular;this.documento=documento;this.categorias=List.copyOf(categorias);this.editable=editable;pagina=0;cargar();
    }
    private static DefaultTableModel lectura(String[] columnas){return new DefaultTableModel(columnas,0){@Override public boolean isCellEditable(int r,int c){return false;}};}
    private void cargar(){
        if(servicio==null)return;
        origen=0;detalle=null;asignaciones.setRowCount(0);
        ejecutar(()->new Object[]{servicio.conceptos(actor,titular,documento,pagina),servicio.tratamientos(actor,titular),servicio.historial(actor,titular,documento,0)},r->{
            @SuppressWarnings("unchecked") List<Concepto> cs=(List<Concepto>)r[0];filas=cs;
            @SuppressWarnings("unchecked") List<Tratamiento> ts=(List<Tratamiento>)r[1];tratamientos=ts;
            conceptos.setRowCount(0);impuestos.setRowCount(0);filasImpuesto=List.of();
            for(Concepto c:filas)conceptos.addRow(new Object[]{c.id(),c.ordinal(),c.producto(),c.descripcion(),c.importe(),c.descuento(),c.neto(),c.objeto(),c.categoriaGeneral()});
            historial.setRowCount(0);for(Object[] h:(List<Object[]>)r[2])historial.addRow(h);
            estado.setText("Página "+(pagina+1)+" · Historial: últimas 100 revisiones. Seleccione concepto o impuesto.");
        });
    }
    private void seleccionarConcepto(){
        int v=tablaConceptos.getSelectedRow();if(v<0)return;
        detalle=null;asignaciones.setRowCount(0);
        Concepto c=filas.get(tablaConceptos.convertRowIndexToModel(v));tipo=Tipo.CONCEPTO;origen=c.id();
        ejecutar(()->new Object[]{servicio.consultar(actor,titular,documento,Tipo.CONCEPTO,c.id()),servicio.impuestos(actor,titular,documento,c.id())},r->{
            @SuppressWarnings("unchecked") List<Impuesto> is=(List<Impuesto>)r[1];filasImpuesto=is;impuestos.setRowCount(0);
            for(Impuesto i:is)impuestos.addRow(new Object[]{i.id(),i.codigo(),i.movimiento(),i.factor(),i.base(),i.tasa(),i.importe()});
            presentar((Detalle)r[0]);
        });
    }
    private void seleccionarImpuesto(){
        int v=tablaImpuestos.getSelectedRow();if(v<0)return;
        detalle=null;asignaciones.setRowCount(0);
        Impuesto i=filasImpuesto.get(tablaImpuestos.convertRowIndexToModel(v));tipo=Tipo.IMPUESTO;origen=i.id();
        ejecutar(()->servicio.consultar(actor,titular,documento,tipo,origen),this::presentar);
    }
    private void presentar(Detalle d){
        detalle=d;modo.setSelectedIndex(0);destinos.removeAllItems();
        if(tipo==Tipo.CONCEPTO)categorias.forEach(c->destinos.addItem(new Opcion(c.id(),c.toString())));
        else tratamientos.forEach(t->destinos.addItem(new Opcion(t.id(),t.toString())));
        asignaciones.setRowCount(0);
        for(Asignacion a:d.asignaciones()){
            Opcion opcion=null;for(int j=0;j<destinos.getItemCount();j++)if(destinos.getItemAt(j).id()==a.destino())opcion=destinos.getItemAt(j);
            asignaciones.addRow(new Object[]{opcion==null?new Opcion(a.destino(),"ID "+a.destino()):opcion,a.importe(),a.nota()});
        }
        estado.setText(tipo+" "+origen+" · Original: "+d.original()+" · Pendiente: "+d.pendiente()+" · Revisión: "+d.revision());
    }
    private void guardar(){
        if(detalle==null||!editable||ocupado)return;
        if(tablaAsignaciones.isEditing()&&!tablaAsignaciones.getCellEditor().stopCellEditing())return;
        try{
            var valores=new java.util.ArrayList<BigDecimal>();
            for(int r=0;r<asignaciones.getRowCount();r++)valores.add(new BigDecimal(asignaciones.getValueAt(r,1).toString().strip()));
            List<BigDecimal> importes=modo.getSelectedIndex()==1?DistribucionDecimal.proporciones(detalle.original(),valores):valores;
            var solicitudes=new java.util.ArrayList<Asignacion>();
            for(int r=0;r<asignaciones.getRowCount();r++)solicitudes.add(new Asignacion(((Opcion)asignaciones.getValueAt(r,0)).id(),importes.get(r),String.valueOf(asignaciones.getValueAt(r,2))));
            long id=origen,revision=detalle.revision();Tipo actual=tipo;String razon=motivo.getText();
            ejecutar(()->servicio.guardar(actor,titular,documento,actual,id,revision,solicitudes,razon),r->{motivo.setText("");cargar();});
        }catch(RuntimeException e){estado.setText(e.getMessage());}
    }
    private void controles(){
        anterior.setEnabled(!ocupado&&servicio!=null&&pagina>0);siguiente.setEnabled(!ocupado&&filas.size()==100);actualizar.setEnabled(!ocupado&&servicio!=null);
        agregar.setEnabled(!ocupado&&editable&&detalle!=null&&detalle.original()!=null);quitar.setEnabled(agregar.isEnabled());guardar.setEnabled(agregar.isEnabled());
        tablaConceptos.setEnabled(!ocupado);tablaImpuestos.setEnabled(!ocupado);modo.setEnabled(agregar.isEnabled());
    }
    private <T> void ejecutar(Callable<T> tarea,Consumer<T> resultado){
        int token=++generacion;ocupado=true;controles();
        new SwingWorker<T,Void>(){
            @Override protected T doInBackground()throws Exception{return tarea.call();}
            @Override protected void done(){if(token!=generacion)return;ocupado=false;try{resultado.accept(get());}catch(Exception e){Throwable c=e.getCause()==null?e:e.getCause();estado.setText(c.getMessage());}controles();}
        }.execute();
    }
}
