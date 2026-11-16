package com.kathsoft.visor.app.view.visor;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.visor.VisorCfdiService;
import com.kathsoft.visor.app.service.visor.VisorCfdiService.*;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.MaskFormatter;

/**
 * RF-015/016: visor independiente con filtros explícitos y orden global SQL.
 * No accede a datos en el constructor de diseño.
 */
public class PanelVisorCfdi extends JPanel {
    private static final long serialVersionUID=1L;
    private static final DateTimeFormatter DIA=DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
    private JPanel panelEtiquetaVisorCfdi,panelCentralContenedor,panelSuperiorBotones,panelInferiorBusqueda;
    private JLabel lblTitulo,lblEstado,lblTitular,lblPeriodo,lblEjercicio,lblMes,lblDesde,lblHasta,lblFecha,
        lblUuid,lblRfc,lblTipo,lblSentido,lblEmisor,lblReceptor,lblMin,lblMax,lblMoneda,lblSat,lblPago,lblMetodo;
    private JComboBox<Titular> cmbTitular;
    private JComboBox<String> cmbPeriodo,cmbTipo,cmbSentido,cmbSat,cmbPago,cmbMetodo;
    private JComboBox<Fecha> cmbFecha;
    private JSpinner spEjercicio,spMes;
    private JFormattedTextField txfDesde,txfHasta;
    private JTextField txfUuid,txfRfc,txfEmisor,txfReceptor,txfMin,txfMax,txfMoneda;
    private JButton btnBuscar,btnDetalle,btnAgrupar,btnAnterior,btnSiguiente;
    private JTable tabla;
    private JScrollPane scroll;
    private DefaultTableModel modelo;
    private VisorCfdiService servicio;
    private DetalleCfdiService detalles;
    private UsuarioSesion sesion;
    private Set<String> permisos=Set.of();
    private List<Fila> filas=List.of();
    private Filtro ultimoFiltro;
    private Orden orden=new Orden(Columna.EMISION,false);
    private boolean agrupado,ocupado,siguiente;
    private int pagina,generacion;

    /** Constructor para WindowBuilder. */
    public PanelVisorCfdi(){inicializarComponentes();}

    /** Inyecta dependencias y actualiza acciones sin consultas en el EDT. */
    public void configurar(VisorCfdiService servicio,DetalleCfdiService detalles,UsuarioSesion sesion,Set<String> permisos){
        this.servicio=servicio;this.detalles=detalles;this.sesion=sesion;
        this.permisos=permisos==null?Set.of():Set.copyOf(permisos);
        generacion++;ocupado=false;filas=List.of();ultimoFiltro=null;siguiente=false;agrupado=false;pagina=0;
        btnAgrupar.setText("Agrupar por RFC");modelo.setColumnIdentifiers(Columna.values());
        modelo.setRowCount(0);cmbTitular.removeAllItems();lblEstado.setText("Seleccione contribuyente");
        habilitar();
    }

    /** Carga titulares autorizados al activar la tarjeta. */
    public void recargar(){
        if(!autorizado()||ocupado)return;
        final int g=++generacion;final var s=servicio;final Long actor=sesion.id();
        ocupado=true;habilitar();
        new SwingWorker<List<Titular>,Void>(){
            @Override protected List<Titular> doInBackground(){return s.titulares(actor);}
            @Override protected void done(){
                if(g!=generacion)return;
                try{cmbTitular.removeAllItems();for(Titular t:get())cmbTitular.addItem(t);}
                catch(InterruptedException e){Thread.currentThread().interrupt();}
                catch(ExecutionException e){error(e.getCause());}
                finally{ocupado=false;habilitar();}
            }
        }.execute();
    }

    private void inicializarComponentes(){
        panelEtiquetaVisorCfdi=new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelEtiquetaVisorCfdi.setBackground(new Color(25,25,112));
        lblTitulo=new JLabel("Administrar XML — Visor general de CFDI");
        lblTitulo.setForeground(Color.WHITE);panelEtiquetaVisorCfdi.add(lblTitulo);
        panelCentralContenedor=new JPanel();panelSuperiorBotones=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
        panelInferiorBusqueda=new JPanel();
        btnDetalle=new JButton("Ver detalle");btnAgrupar=new JButton("Agrupar por RFC");
        btnAnterior=new JButton("Anterior");btnSiguiente=new JButton("Siguiente");btnBuscar=new JButton("Buscar");
        lblEstado=new JLabel("Sin consulta");
        panelSuperiorBotones.add(btnDetalle);panelSuperiorBotones.add(btnAgrupar);
        panelSuperiorBotones.add(btnAnterior);panelSuperiorBotones.add(btnSiguiente);panelSuperiorBotones.add(lblEstado);
        modelo=new DefaultTableModel(){private static final long serialVersionUID=1L;
            @Override public boolean isCellEditable(int r,int c){return false;}};
        modelo.setColumnIdentifiers(Columna.values());
        tabla=new JTable(modelo);tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scroll=new JScrollPane(tabla);
        lblTitular=new JLabel("Titular");cmbTitular=new JComboBox<>();
        lblPeriodo=new JLabel("Periodo");cmbPeriodo=new JComboBox<>(new String[]{"Mes","Ejercicio","Rango"});
        lblEjercicio=new JLabel("Año");spEjercicio=new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(),1000,9998,1));
        spEjercicio.setEditor(new JSpinner.NumberEditor(spEjercicio,"0"));
        lblMes=new JLabel("Mes");spMes=new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(),1,12,1));
        lblDesde=new JLabel("Desde");lblHasta=new JLabel("Hasta");lblFecha=new JLabel("Fecha");
        txfDesde=new JFormattedTextField();txfHasta=new JFormattedTextField();
        try{
            MaskFormatter m1=new MaskFormatter("##-##-####");m1.setPlaceholderCharacter('_');m1.install(txfDesde);
            MaskFormatter m2=new MaskFormatter("##-##-####");m2.setPlaceholderCharacter('_');m2.install(txfHasta);
        }catch(ParseException e){throw new IllegalStateException(e);}
        txfDesde.setText(LocalDate.now().withDayOfMonth(1).format(DIA));txfHasta.setText(LocalDate.now().format(DIA));
        cmbFecha=new JComboBox<>(Fecha.values());
        lblUuid=new JLabel("UUID");txfUuid=new JTextField();
        lblRfc=new JLabel("RFC");txfRfc=new JTextField();
        lblTipo=new JLabel("Tipo");cmbTipo=new JComboBox<>(new String[]{"","I","E","T","N","P","RETENCION"});
        lblSentido=new JLabel("Sentido");cmbSentido=new JComboBox<>(new String[]{"","RECIBIDO","EMITIDO","AMBOS"});
        lblEmisor=new JLabel("Emisor");txfEmisor=new JTextField();lblReceptor=new JLabel("Receptor");txfReceptor=new JTextField();
        lblMin=new JLabel("Mín.");txfMin=new JTextField();lblMax=new JLabel("Máx.");txfMax=new JTextField();
        lblMoneda=new JLabel("Moneda");txfMoneda=new JTextField();
        lblSat=new JLabel("SAT");cmbSat=new JComboBox<>(new String[]{"","VIGENTE","CANCELADO","NO_ENCONTRADO","DESCONOCIDO","ERROR","SIN_CONSULTA"});
        lblPago=new JLabel("Obligación");cmbPago=new JComboBox<>(new String[]{"","SIN_REGISTRO","LIQUIDADO","PENDIENTE","PARCIAL","SOBREAPLICADO","MIXTO"});
        lblMetodo=new JLabel("Método");cmbMetodo=new JComboBox<>(new String[]{"","PUE","PPD","PIP"});
        GroupLayout f=new GroupLayout(panelInferiorBusqueda);panelInferiorBusqueda.setLayout(f);
        f.setAutoCreateGaps(true);
        f.setHorizontalGroup(f.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(f.createSequentialGroup().addComponent(lblTitular).addComponent(cmbTitular,100,210,Short.MAX_VALUE)
                .addComponent(lblPeriodo).addComponent(cmbPeriodo,65,83,100).addComponent(lblEjercicio).addComponent(spEjercicio,45,60,65)
                .addComponent(lblMes).addComponent(spMes,35,45,50))
            .addGroup(f.createSequentialGroup().addComponent(lblDesde).addComponent(txfDesde,86,93,100)
                .addComponent(lblHasta).addComponent(txfHasta,86,93,100).addComponent(lblFecha).addComponent(cmbFecha,85,100,130)
                .addComponent(lblTipo).addComponent(cmbTipo,65,82,100))
            .addGroup(f.createSequentialGroup().addComponent(lblUuid).addComponent(txfUuid,80,160,Short.MAX_VALUE)
                .addComponent(lblRfc).addComponent(txfRfc,70,110,160).addComponent(lblSentido).addComponent(cmbSentido,75,100,115))
            .addGroup(f.createSequentialGroup().addComponent(lblEmisor).addComponent(txfEmisor,70,180,Short.MAX_VALUE)
                .addComponent(lblReceptor).addComponent(txfReceptor,70,180,Short.MAX_VALUE))
            .addGroup(f.createSequentialGroup().addComponent(lblMin).addComponent(txfMin,55,85,120)
                .addComponent(lblMax).addComponent(txfMax,55,85,120).addComponent(lblMoneda).addComponent(txfMoneda,40,50,60)
                .addComponent(lblMetodo).addComponent(cmbMetodo,55,65,80))
            .addGroup(f.createSequentialGroup().addComponent(lblSat).addComponent(cmbSat,95,125,160)
                .addComponent(lblPago).addComponent(cmbPago,100,130,160).addComponent(btnBuscar)));
        f.setVerticalGroup(f.createSequentialGroup()
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblTitular).addComponent(cmbTitular)
                .addComponent(lblPeriodo).addComponent(cmbPeriodo).addComponent(lblEjercicio).addComponent(spEjercicio).addComponent(lblMes).addComponent(spMes))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblDesde).addComponent(txfDesde).addComponent(lblHasta).addComponent(txfHasta)
                .addComponent(lblFecha).addComponent(cmbFecha).addComponent(lblTipo).addComponent(cmbTipo))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblUuid).addComponent(txfUuid).addComponent(lblRfc).addComponent(txfRfc)
                .addComponent(lblSentido).addComponent(cmbSentido))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblEmisor).addComponent(txfEmisor).addComponent(lblReceptor).addComponent(txfReceptor))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblMin).addComponent(txfMin).addComponent(lblMax).addComponent(txfMax)
                .addComponent(lblMoneda).addComponent(txfMoneda).addComponent(lblMetodo).addComponent(cmbMetodo))
            .addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblSat).addComponent(cmbSat).addComponent(lblPago).addComponent(cmbPago).addComponent(btnBuscar)));
        GroupLayout c=new GroupLayout(panelCentralContenedor);panelCentralContenedor.setLayout(c);c.setAutoCreateGaps(true);c.setAutoCreateContainerGaps(true);
        c.setHorizontalGroup(c.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(panelSuperiorBotones,0,630,Short.MAX_VALUE)
            .addComponent(scroll,0,630,Short.MAX_VALUE).addComponent(panelInferiorBusqueda,0,630,Short.MAX_VALUE));
        c.setVerticalGroup(c.createSequentialGroup().addComponent(panelSuperiorBotones,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE)
            .addComponent(scroll,100,220,Short.MAX_VALUE).addComponent(panelInferiorBusqueda,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE));
        GroupLayout g=new GroupLayout(this);setLayout(g);g.setAutoCreateGaps(true);
        g.setHorizontalGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(panelEtiquetaVisorCfdi,0,630,Short.MAX_VALUE)
            .addComponent(panelCentralContenedor,0,630,Short.MAX_VALUE));
        g.setVerticalGroup(g.createSequentialGroup().addComponent(panelEtiquetaVisorCfdi,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE)
            .addComponent(panelCentralContenedor,0,420,Short.MAX_VALUE));
        btnBuscar.addActionListener(e->buscar());btnDetalle.addActionListener(e->detalle());
        btnAgrupar.addActionListener(e->{agrupado=!agrupado;btnAgrupar.setText(agrupado?"Ver documentos":"Agrupar por RFC");cargar(ultimoFiltro,0);});
        btnAnterior.addActionListener(e->{if(pagina>0)cargar(ultimoFiltro,pagina-1);});
        btnSiguiente.addActionListener(e->{if(siguiente)cargar(ultimoFiltro,pagina+1);});
        txfUuid.addActionListener(e->buscar());txfRfc.addActionListener(e->buscar());txfEmisor.addActionListener(e->buscar());txfReceptor.addActionListener(e->buscar());
        txfDesde.addActionListener(e->buscar());txfHasta.addActionListener(e->buscar());txfMin.addActionListener(e->buscar());txfMax.addActionListener(e->buscar());txfMoneda.addActionListener(e->buscar());
        cmbPeriodo.addActionListener(e->habilitar());
        tabla.getSelectionModel().addListSelectionListener(e->habilitar());
        tabla.getTableHeader().addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                if(agrupado||ocupado||ultimoFiltro==null||!autorizado())return;
                int vista=tabla.getTableHeader().columnAtPoint(e.getPoint());if(vista<0)return;
                Columna col=Columna.values()[tabla.convertColumnIndexToModel(vista)];
                orden=new Orden(col,orden.columna()!=col||!orden.ascendente());cargar(ultimoFiltro,0);
            }
        });
        habilitar();
    }

    private boolean autorizado(){return servicio!=null&&sesion!=null&&permisos.contains(Permisos.CFDI_CONSULTAR);}
    private void habilitar(){
        boolean libre=autorizado()&&!ocupado;
        btnBuscar.setEnabled(libre&&cmbTitular.getSelectedItem()!=null);btnAgrupar.setEnabled(libre&&ultimoFiltro!=null);
        btnAnterior.setEnabled(libre&&ultimoFiltro!=null&&pagina>0);btnSiguiente.setEnabled(libre&&siguiente);
        btnDetalle.setEnabled(libre&&!agrupado&&ultimoFiltro!=null&&tabla.getSelectedRow()>=0&&detalles!=null&&permisos.contains(Permisos.CFDI_CONSULTAR_DETALLE));
        cmbTitular.setEnabled(libre);cmbPeriodo.setEnabled(libre);
        spEjercicio.setEnabled(libre&&cmbPeriodo.getSelectedIndex()!=2);spMes.setEnabled(libre&&cmbPeriodo.getSelectedIndex()==0);
        txfDesde.setEnabled(libre&&cmbPeriodo.getSelectedIndex()==2);txfHasta.setEnabled(libre&&cmbPeriodo.getSelectedIndex()==2);
    }
    private static String valor(JComboBox<String> c){return (String)c.getSelectedItem();}
    private static BigDecimal monto(JTextField t){return t.getText().isBlank()?null:new BigDecimal(t.getText().strip());}
    private void buscar(){
        if(!autorizado()||ocupado)return;
        try{
            Titular t=(Titular)cmbTitular.getSelectedItem();if(t==null)return;
            int anio=(Integer)spEjercicio.getValue(),mes=(Integer)spMes.getValue();
            LocalDate desde,hasta;
            if(cmbPeriodo.getSelectedIndex()==0){YearMonth ym=YearMonth.of(anio,mes);desde=ym.atDay(1);hasta=ym.atEndOfMonth();}
            else if(cmbPeriodo.getSelectedIndex()==1){desde=LocalDate.of(anio,1,1);hasta=LocalDate.of(anio,12,31);}
            else{desde=LocalDate.parse(txfDesde.getText(),DIA);hasta=LocalDate.parse(txfHasta.getText(),DIA);}
            cargar(new Filtro(t.id(),desde,hasta,(Fecha)cmbFecha.getSelectedItem(),txfUuid.getText(),valor(cmbTipo),
                valor(cmbSentido),txfRfc.getText(),txfEmisor.getText(),txfReceptor.getText(),monto(txfMin),monto(txfMax),
                txfMoneda.getText(),valor(cmbSat),valor(cmbPago),valor(cmbMetodo)),0);
        }catch(RuntimeException e){error(e);}
    }

    private void cargar(Filtro filtro,int destino){
        if(filtro==null||ocupado||!autorizado())return;
        final int g=++generacion;final boolean grupos=agrupado;final var s=servicio;
        final Orden ordenActual=orden;final Long actor=sesion.id();
        ocupado=true;filas=List.of();modelo.setRowCount(0);siguiente=false;habilitar();lblEstado.setText("Consultando...");
        new SwingWorker<Pagina<?>,Void>(){
            @Override protected Pagina<?> doInBackground(){return grupos?s.agrupar(actor,filtro,destino):s.consultar(actor,filtro,ordenActual,destino);}
            @Override protected void done(){
                if(g!=generacion)return;
                try{
                    Pagina<?> r=get();modelo.setRowCount(0);
                    if(grupos){
                        modelo.setColumnIdentifiers(new String[]{"RFC emisor","Moneda","Tipo","Documentos","Subtotal","Descuento","Traslados","Retenciones","Total CFDI","Operación ret.","Retenido ret.","Pagos 2.0 MXN"});
                        for(Object o:r.filas()){Grupo x=(Grupo)o;modelo.addRow(new Object[]{x.rfc(),x.moneda(),x.tipo(),x.documentos(),x.subtotal(),x.descuento(),x.traslados(),x.retenciones(),x.total(),x.operacionRetencion(),x.retenidoRetencion(),x.pagosMxn()});}
                    }else{
                        modelo.setColumnIdentifiers(Columna.values());filas=r.filas().stream().map(Fila.class::cast).toList();
                        for(Fila x:filas)modelo.addRow(x.valores().toArray());
                    }
                    for(int i=0;i<tabla.getColumnCount();i++)tabla.getColumnModel().getColumn(i).setPreferredWidth(i==0?255:120);
                    ultimoFiltro=filtro;pagina=destino;siguiente=r.siguiente();
                    lblEstado.setText("Pág. "+(pagina+1)+" · "+r.filas().size()+(grupos?" grupos":" filas · "+ordenActual.columna()+(ordenActual.ascendente()?" ↑":" ↓")));
                }catch(InterruptedException e){Thread.currentThread().interrupt();}
                catch(ExecutionException e){ultimoFiltro=null;error(e.getCause());}
                finally{ocupado=false;habilitar();}
            }
        }.execute();
    }

    private void detalle(){
        if(!btnDetalle.isEnabled())return;
        int i=tabla.convertRowIndexToModel(tabla.getSelectedRow());
        if(i<0||i>=filas.size())return;
        java.awt.Window w=SwingUtilities.getWindowAncestor(this);
        Frame owner=w instanceof Frame f?f:null;
        var d=new Fr_DatosCfdi(owner,detalles,sesion,ultimoFiltro.titular(),filas.get(i).id());
        d.setLocationRelativeTo(this);d.setVisible(true);
    }
    private void error(Throwable e){lblEstado.setText("Consulta no disponible");JOptionPane.showMessageDialog(this,e.getMessage(),"Visor CFDI",JOptionPane.ERROR_MESSAGE);}
}
