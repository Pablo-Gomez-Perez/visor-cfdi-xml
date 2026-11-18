package com.kathsoft.visor.app.view.operacion;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.*;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService;
import com.kathsoft.visor.app.view.visor.Fr_DatosCfdi;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.MaskFormatter;

/** RF-020: clasificación administrativa, separada de RF-009 y del visor fiscal. */
public class PanelClasificacionOperaciones extends JPanel {
    private static final long serialVersionUID=1L;
    private static final DateTimeFormatter DIA=DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
    private JPanel panelEtiquetaOperaciones,panelCentralContenedor,panelSuperiorBotones,panelInferiorBusqueda;
    private JLabel lblTitulo,lblTitular,lblPeriodo,lblAnio,lblMes,lblDesde,lblHasta,lblSentido,lblRfc,lblFiltro,lblCategoria,lblEstado;
    private JComboBox<Titular> cmbTitular;
    private JComboBox<String> cmbPeriodo,cmbSentido;
    private JComboBox<FiltroCategoria> cmbFiltro;
    private JComboBox<Categoria> cmbCategoria;
    private JSpinner spAnio,spMes;
    private JFormattedTextField txfDesde,txfHasta;
    private JTextField txfRfc;
    private JButton btnBuscar,btnClasificar,btnRetirar,btnHistorial,btnDetalle,btnAnterior,btnSiguiente,
        btnNueva,btnPredeterminadas,btnEstadoCategoria;
    private JTable tabla;
    private JScrollPane scroll;
    private DefaultTableModel modelo;
    private ClasificacionOperacionesService servicio;
    private DetalleCfdiService detalle;
    private UsuarioSesion sesion;
    private Set<String> permisos=Set.of();
    private List<Operacion> filas=List.of();
    private Filtro ultimo;
    private int pagina,generacion;
    private boolean ocupado,siguiente,cargandoTitulares,cargandoCategorias;
    private record FiltroCategoria(long id,String etiqueta){@Override public String toString(){return etiqueta;}}
    private record Consulta(Pagina<Operacion> pagina,List<Categoria> categorias){}

    /** Constructor público sin I/O para WindowBuilder. */
    public PanelClasificacionOperaciones(){inicializarComponentes();}

    /** Inyección y limpieza de datos de la sesión previa. */
    public void configurar(ClasificacionOperacionesService servicio,DetalleCfdiService detalle,UsuarioSesion sesion,Set<String> permisos){
        generacion++;ocupado=false;this.servicio=servicio;this.detalle=detalle;this.sesion=sesion;
        this.permisos=permisos==null?Set.of():Set.copyOf(permisos);
        cargandoTitulares=true;cmbTitular.removeAllItems();cargandoTitulares=false;
        limpiar();categorias(List.of());lblEstado.setText("Seleccione contribuyente");habilitar();
    }
    /** Carga titulares al entrar desde el menú. */
    public void recargar(){
        if(!autorizado()||ocupado)return;
        var s=servicio;Long actor=sesion.id();
        ejecutar(()->s.titulares(actor),datos->{
            cargandoTitulares=true;cmbTitular.removeAllItems();for(Titular t:datos)cmbTitular.addItem(t);cargandoTitulares=false;
            lblEstado.setText(datos.isEmpty()?"Sin contribuyentes asignados":"Seleccione contribuyente y Buscar");
        },()->cargarCategorias());
    }

    private void inicializarComponentes(){
        panelEtiquetaOperaciones=new JPanel(new FlowLayout(FlowLayout.LEFT));panelEtiquetaOperaciones.setBackground(new Color(25,25,112));
        lblTitulo=new JLabel("Clasificación de operaciones — RF-020");lblTitulo.setForeground(Color.WHITE);panelEtiquetaOperaciones.add(lblTitulo);
        panelCentralContenedor=new JPanel();panelSuperiorBotones=new JPanel(new FlowLayout(FlowLayout.LEFT,3,2));panelInferiorBusqueda=new JPanel();
        btnClasificar=new JButton("Clasificar");btnRetirar=new JButton("Retirar");btnHistorial=new JButton("Historial");btnDetalle=new JButton("Ver CFDI");
        btnAnterior=new JButton("Anterior");btnSiguiente=new JButton("Siguiente");btnBuscar=new JButton("Buscar");
        panelSuperiorBotones.add(btnClasificar);panelSuperiorBotones.add(btnRetirar);panelSuperiorBotones.add(btnHistorial);
        panelSuperiorBotones.add(btnDetalle);panelSuperiorBotones.add(btnAnterior);panelSuperiorBotones.add(btnSiguiente);
        modelo=new DefaultTableModel(new String[]{"Categoría","Naturaleza","Emisión","Tipo","Sentido","RFC emisor","Emisor","RFC receptor","Receptor",
            "Subtotal","Descuento","Total CFDI","Moneda","Último SAT","UUID"},0){
            private static final long serialVersionUID=1L;
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        tabla=new JTable(modelo);tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);tabla.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tabla.setAutoCreateRowSorter(true);scroll=new JScrollPane(tabla);
        for(int i=0;i<tabla.getColumnCount();i++)tabla.getColumnModel().getColumn(i).setPreferredWidth(i==14?260:i==6||i==8?210:120);
        lblTitular=new JLabel("Titular");cmbTitular=new JComboBox<>();
        lblPeriodo=new JLabel("Periodo");cmbPeriodo=new JComboBox<>(new String[]{"Mes","Ejercicio","Rango"});
        lblAnio=new JLabel("Año");spAnio=new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(),1000,9998,1));
        spAnio.setEditor(new JSpinner.NumberEditor(spAnio,"0"));lblMes=new JLabel("Mes");spMes=new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(),1,12,1));
        lblDesde=new JLabel("Desde");lblHasta=new JLabel("Hasta");txfDesde=new JFormattedTextField();txfHasta=new JFormattedTextField();
        try{
            MaskFormatter a=new MaskFormatter("##-##-####");a.setPlaceholderCharacter('_');a.install(txfDesde);
            MaskFormatter b=new MaskFormatter("##-##-####");b.setPlaceholderCharacter('_');b.install(txfHasta);
        }catch(ParseException ex){throw new IllegalStateException(ex);}
        txfDesde.setText(LocalDate.now().withDayOfMonth(1).format(DIA));txfHasta.setText(LocalDate.now().format(DIA));
        lblSentido=new JLabel("Sentido");cmbSentido=new JComboBox<>(new String[]{"RECIBIDO","EMITIDO","AMBOS",""});
        lblRfc=new JLabel("RFC");txfRfc=new JTextField();lblFiltro=new JLabel("Filtro categoría");cmbFiltro=new JComboBox<>();
        lblCategoria=new JLabel("Asignar categoría");cmbCategoria=new JComboBox<>();
        btnNueva=new JButton("Nueva");btnPredeterminadas=new JButton("Iniciales");btnEstadoCategoria=new JButton("Activar/desactivar");
        lblEstado=new JLabel("Sin consulta");
        GroupLayout f=new GroupLayout(panelInferiorBusqueda);panelInferiorBusqueda.setLayout(f);f.setAutoCreateGaps(true);
        f.setHorizontalGroup(f.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(f.createSequentialGroup().addComponent(lblTitular).addComponent(cmbTitular,85,190,Short.MAX_VALUE)
                .addComponent(lblPeriodo).addComponent(cmbPeriodo,60,85,100).addComponent(lblAnio).addComponent(spAnio,48,65,70)
                .addComponent(lblMes).addComponent(spMes,35,45,50))
            .addGroup(f.createSequentialGroup().addComponent(lblDesde).addComponent(txfDesde,82,90,100)
                .addComponent(lblHasta).addComponent(txfHasta,82,90,100).addComponent(lblSentido).addComponent(cmbSentido,90,110,130))
            .addGroup(f.createSequentialGroup().addComponent(lblRfc).addComponent(txfRfc,70,125,Short.MAX_VALUE)
                .addComponent(lblFiltro).addComponent(cmbFiltro,90,185,Short.MAX_VALUE).addComponent(btnBuscar))
            .addGroup(f.createSequentialGroup().addComponent(lblCategoria).addComponent(cmbCategoria,120,240,Short.MAX_VALUE))
            .addGroup(f.createSequentialGroup().addComponent(btnNueva).addComponent(btnPredeterminadas).addComponent(btnEstadoCategoria))
            .addComponent(lblEstado,0,520,Short.MAX_VALUE));
        f.setVerticalGroup(f.createSequentialGroup()
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblTitular).addComponent(cmbTitular).addComponent(lblPeriodo).addComponent(cmbPeriodo)
                .addComponent(lblAnio).addComponent(spAnio).addComponent(lblMes).addComponent(spMes))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblDesde).addComponent(txfDesde).addComponent(lblHasta).addComponent(txfHasta)
                .addComponent(lblSentido).addComponent(cmbSentido))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblRfc).addComponent(txfRfc).addComponent(lblFiltro).addComponent(cmbFiltro).addComponent(btnBuscar))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblCategoria).addComponent(cmbCategoria))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(btnNueva).addComponent(btnPredeterminadas).addComponent(btnEstadoCategoria))
            .addComponent(lblEstado));
        GroupLayout c=new GroupLayout(panelCentralContenedor);panelCentralContenedor.setLayout(c);c.setAutoCreateGaps(true);c.setAutoCreateContainerGaps(true);
        c.setHorizontalGroup(c.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(panelSuperiorBotones,0,620,Short.MAX_VALUE)
            .addComponent(scroll,0,620,Short.MAX_VALUE).addComponent(panelInferiorBusqueda,0,620,Short.MAX_VALUE));
        c.setVerticalGroup(c.createSequentialGroup().addComponent(panelSuperiorBotones,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE)
            .addComponent(scroll,60,200,Short.MAX_VALUE).addComponent(panelInferiorBusqueda,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE));
        GroupLayout g=new GroupLayout(this);setLayout(g);g.setAutoCreateGaps(true);
        g.setHorizontalGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(panelEtiquetaOperaciones,0,620,Short.MAX_VALUE).addComponent(panelCentralContenedor,0,620,Short.MAX_VALUE));
        g.setVerticalGroup(g.createSequentialGroup().addComponent(panelEtiquetaOperaciones,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE)
            .addComponent(panelCentralContenedor,0,350,Short.MAX_VALUE));
        cmbTitular.addActionListener(e->{if(!cargandoTitulares&&!ocupado){limpiar();categorias(List.of());cargarCategorias();}});
        cmbPeriodo.addActionListener(e->habilitar());cmbCategoria.addActionListener(e->{if(!cargandoCategorias)habilitar();});
        tabla.getSelectionModel().addListSelectionListener(e->habilitar());
        btnBuscar.addActionListener(e->buscar());txfRfc.addActionListener(e->buscar());txfDesde.addActionListener(e->buscar());txfHasta.addActionListener(e->buscar());
        btnAnterior.addActionListener(e->consultar(ultimo,pagina-1));btnSiguiente.addActionListener(e->consultar(ultimo,pagina+1));
        btnClasificar.addActionListener(e->clasificar(false));btnRetirar.addActionListener(e->clasificar(true));
        btnHistorial.addActionListener(e->historial());btnDetalle.addActionListener(e->detalle());
        btnNueva.addActionListener(e->nueva());btnPredeterminadas.addActionListener(e->predeterminadas());btnEstadoCategoria.addActionListener(e->estadoCategoria());
        categorias(List.of());habilitar();
    }

    private boolean autorizado(){return servicio!=null&&sesion!=null&&permisos.contains(Permisos.OPERACIONES_CONSULTAR);}
    private Titular titular(){return (Titular)cmbTitular.getSelectedItem();}
    private Categoria categoria(){return (Categoria)cmbCategoria.getSelectedItem();}
    private void habilitar(){
        boolean libre=autorizado()&&!ocupado,t=libre&&titular()!=null,seleccion=t&&ultimo!=null&&tabla.getSelectedRowCount()>0;
        boolean gestion=t&&permisos.contains(Permisos.OPERACIONES_GESTIONAR_CATEGORIAS);
        boolean cambio=seleccion&&permisos.contains(Permisos.OPERACIONES_CLASIFICAR);
        btnBuscar.setEnabled(t);btnClasificar.setEnabled(cambio&&categoria()!=null&&categoria().activa());btnRetirar.setEnabled(cambio);
        btnHistorial.setEnabled(seleccion&&tabla.getSelectedRowCount()==1);
        btnDetalle.setEnabled(btnHistorial.isEnabled()&&detalle!=null&&permisos.containsAll(Set.of(Permisos.CFDI_CONSULTAR,Permisos.CFDI_CONSULTAR_DETALLE)));
        btnAnterior.setEnabled(t&&ultimo!=null&&pagina>0);btnSiguiente.setEnabled(t&&ultimo!=null&&siguiente);
        btnNueva.setEnabled(gestion);btnPredeterminadas.setEnabled(gestion);btnEstadoCategoria.setEnabled(gestion&&categoria()!=null);
        cmbTitular.setEnabled(libre);cmbPeriodo.setEnabled(t);cmbSentido.setEnabled(t);cmbFiltro.setEnabled(t);cmbCategoria.setEnabled(t);txfRfc.setEnabled(t);
        spAnio.setEnabled(t&&cmbPeriodo.getSelectedIndex()!=2);spMes.setEnabled(t&&cmbPeriodo.getSelectedIndex()==0);
        txfDesde.setEnabled(t&&cmbPeriodo.getSelectedIndex()==2);txfHasta.setEnabled(t&&cmbPeriodo.getSelectedIndex()==2);
        tabla.setEnabled(libre);
    }
    private void limpiar(){filas=List.of();ultimo=null;pagina=0;siguiente=false;modelo.setRowCount(0);}
    private void categorias(List<Categoria> datos){
        long filtro=cmbFiltro.getSelectedItem() instanceof FiltroCategoria f?f.id():-1;
        long cat=categoria()==null?0:categoria().id();
        cargandoCategorias=true;cmbFiltro.removeAllItems();cmbCategoria.removeAllItems();
        cmbFiltro.addItem(new FiltroCategoria(-1,"Todas"));cmbFiltro.addItem(new FiltroCategoria(0,"Sin clasificación"));
        for(Categoria c:datos){
            FiltroCategoria f=new FiltroCategoria(c.id(),c.toString());cmbFiltro.addItem(f);cmbCategoria.addItem(c);
            if(c.id()==filtro)cmbFiltro.setSelectedItem(f);if(c.id()==cat)cmbCategoria.setSelectedItem(c);
        }
        if(filtro==0)cmbFiltro.setSelectedIndex(1);
        cargandoCategorias=false;
    }
    private void cargarCategorias(){
        Titular t=titular();if(!autorizado()||ocupado||t==null)return;
        var s=servicio;Long actor=sesion.id();
        ejecutar(()->s.categorias(actor,t.id()),this::categorias,()->{});
    }
    private void buscar(){
        if(!btnBuscar.isEnabled())return;
        try{
            spAnio.commitEdit();spMes.commitEdit();int a=(Integer)spAnio.getValue(),m=(Integer)spMes.getValue();
            LocalDate desde,hasta;
            if(cmbPeriodo.getSelectedIndex()==0){YearMonth ym=YearMonth.of(a,m);desde=ym.atDay(1);hasta=ym.atEndOfMonth();}
            else if(cmbPeriodo.getSelectedIndex()==1){desde=LocalDate.of(a,1,1);hasta=LocalDate.of(a,12,31);}
            else{desde=LocalDate.parse(txfDesde.getText(),DIA);hasta=LocalDate.parse(txfHasta.getText(),DIA);}
            FiltroCategoria f=(FiltroCategoria)cmbFiltro.getSelectedItem();
            consultar(new Filtro(titular().id(),desde,hasta,(String)cmbSentido.getSelectedItem(),txfRfc.getText(),f==null?-1:f.id()),0);
        }catch(RuntimeException|ParseException ex){error(ex);}
    }
    private void consultar(Filtro f,int p){
        if(f==null||p<0||ocupado||!autorizado())return;
        var s=servicio;Long actor=sesion.id();limpiar();lblEstado.setText("Consultando...");
        ejecutar(()->new Consulta(s.consultar(actor,f,p),s.categorias(actor,f.titular())),datos->{
            categorias(datos.categorias());filas=datos.pagina().filas();modelo.setRowCount(0);
            for(Operacion x:filas)modelo.addRow(new Object[]{x.categoria()==null?"Sin clasificación":x.categoria(),x.naturaleza(),x.emision(),x.tipo(),x.sentido(),
                x.rfcEmisor(),x.emisor(),x.rfcReceptor(),x.receptor(),x.subtotal(),x.descuento(),x.total(),x.moneda(),x.estadoSat(),x.uuid()});
            ultimo=f;pagina=p;siguiente=datos.pagina().siguiente();lblEstado.setText("Página "+(p+1)+" · "+filas.size()+" operaciones (orden local por columna)");
        },()->{});
    }
    private List<Operacion> seleccionadas(){
        List<Operacion> salida=new ArrayList<>();for(int i:tabla.getSelectedRows()){
            int m=tabla.convertRowIndexToModel(i);if(m>=0&&m<filas.size())salida.add(filas.get(m));
        }
        return salida;
    }
    private void clasificar(boolean retirar){
        if(retirar?!btnRetirar.isEnabled():!btnClasificar.isEnabled())return;
        List<Seleccion> sel=seleccionadas().stream().map(x->new Seleccion(x.id(),x.revision())).toList();
        if(sel.isEmpty())return;
        Categoria c=categoria();
        String motivo=JOptionPane.showInputDialog(this,(retirar?"Retirar categoría":"Asignar "+c)+" a "+sel.size()+" operaciones. Motivo obligatorio:");
        if(motivo==null)return;
        if(motivo.isBlank()){error(new IllegalArgumentException("El motivo es obligatorio"));return;}
        var s=servicio;Long actor=sesion.id();Filtro f=ultimo;int p=pagina;Long id=retirar?null:c.id();
        ejecutar(()->s.clasificar(actor,f.titular(),sel,id,motivo),n->JOptionPane.showMessageDialog(this,n+" operaciones modificadas."),()->consultar(f,p));
    }
    private void historial(){
        if(!btnHistorial.isEnabled())return;
        var d=new Fr_HistorialClasificacionOperacion(owner(),servicio,sesion.id(),ultimo.titular(),seleccionadas().get(0));
        d.setLocationRelativeTo(this);d.setVisible(true);
    }
    private void detalle(){
        if(!btnDetalle.isEnabled())return;
        var d=new Fr_DatosCfdi(owner(),detalle,sesion,ultimo.titular(),seleccionadas().get(0).id());
        d.setLocationRelativeTo(this);d.setVisible(true);
    }
    private Frame owner(){var w=SwingUtilities.getWindowAncestor(this);return w instanceof Frame f?f:null;}
    private void nueva(){
        if(!btnNueva.isEnabled())return;
        var d=new Fr_DatosCategoriaOperacion(owner());d.setLocationRelativeTo(this);d.setVisible(true);var x=d.solicitud();if(x==null)return;
        var s=servicio;Long actor=sesion.id();long t=titular().id();
        ejecutar(()->{s.crearCategoria(actor,t,x.clave(),x.nombre(),x.naturaleza());return null;},v->{},this::cargarCategorias);
    }
    private void predeterminadas(){
        if(!btnPredeterminadas.isEnabled())return;
        var s=servicio;Long actor=sesion.id();long t=titular().id();
        ejecutar(()->s.crearPredeterminadas(actor,t),n->JOptionPane.showMessageDialog(this,n+" categorías nuevas."),this::cargarCategorias);
    }
    private void estadoCategoria(){
        if(!btnEstadoCategoria.isEnabled())return;
        var c=categoria();
        if(JOptionPane.showConfirmDialog(this,(c.activa()?"Desactivar ":"Reactivar ")+c+"? No cambia el historial.","Categoría",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        var s=servicio;Long actor=sesion.id();long t=titular().id();
        ejecutar(()->s.cambiarEstadoCategoria(actor,t,c.id(),!c.activa()),v->{},this::cargarCategorias);
    }
    private <T> void ejecutar(Callable<T> trabajo,Consumer<T> resultado,Runnable despues){
        if(ocupado)return;
        int ciclo=++generacion;ocupado=true;habilitar();
        new SwingWorker<T,Void>(){
            @Override protected T doInBackground()throws Exception{return trabajo.call();}
            @Override protected void done(){
                if(ciclo!=generacion)return;boolean ok=false;
                try{resultado.accept(get());ok=true;}
                catch(InterruptedException ex){Thread.currentThread().interrupt();}
                catch(ExecutionException ex){error(ex.getCause());}
                finally{ocupado=false;habilitar();}
                if(ok)despues.run();
            }
        }.execute();
    }
    private void error(Throwable ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Clasificación de operaciones",JOptionPane.ERROR_MESSAGE);}
}
