package com.kathsoft.visor.app.view.visor;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService.Detalle;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService.Seccion;
import java.awt.Frame;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import com.kathsoft.visor.app.model.dto.grafo.GrafoFiscal;
import com.kathsoft.visor.app.model.dto.grafo.NodoCfdi;
import com.kathsoft.visor.app.service.grafo.DistribucionGrafoService;
import com.kathsoft.visor.app.view.grafo.PanelGrafoCfdi;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** RF-017: diálogo modal de inspección, sin edición del documento original. */
public class Fr_DatosCfdi extends JDialog {
    private static final long serialVersionUID=1L;
    private JPanel contenido,panelDatos,panelXml;
    private JLabel lblUuid,lblSeccion,lblEstado,lblXml;
    private JComboBox<Seccion> cmbSeccion;
    private JTabbedPane pestanas;
    private JTable tabla;
    private JTextArea txaXml,txaValor;
    private JScrollPane scrollDatos,scrollXml,scrollValor;
    private JButton btnCerrar;
    private DefaultTableModel modelo;
    private JPanel panelRelaciones,panelSuperiorGrafo;
    private PanelGrafoCfdi panelGrafo;
    private JButton btnCentrar,btnAjustar,btnActualizarGrafo,btnVolver;
    private JLabel lblGrafo,lblSeleccion;
    private DetalleCfdiService servicioActual;
    private UsuarioSesion sesionActual;
    private long titularActual,documentoActual;
    private int generacion;
    private boolean detalleListo,grafoListo,grafoOcupado;
    private SwingWorker<DetalleCargado,Void> tareaDetalle;
    private SwingWorker<GrafoCargado,Void> tareaGrafo;
    private final java.util.Deque<Long> historial=new java.util.ArrayDeque<>();

    private record DetalleCargado(Detalle detalle,boolean grafoPermitido){}
    private record GrafoCargado(GrafoFiscal grafo,Map<String,Rectangle2D.Double> posiciones){}


    /** Constructor público sin persistencia para Eclipse WindowBuilder. */
    public Fr_DatosCfdi(){super((Frame)null,true);inicializarComponentes();}

    /** Abre un detalle autorizado; la lectura se realiza en segundo plano. */
    public Fr_DatosCfdi(Frame owner,DetalleCfdiService servicio,UsuarioSesion sesion,long titular,long documento){
        super(owner,true);inicializarComponentes();
        SwingUtilities.invokeLater(()->cargar(servicio,sesion,titular,documento));
    }

    private void inicializarComponentes(){
        setTitle("Detalle de comprobante fiscal");setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(730,530);setMinimumSize(new java.awt.Dimension(600,420));
        contenido=new JPanel();setContentPane(contenido);
        lblUuid=new JLabel("Comprobante");lblSeccion=new JLabel("Sección");lblEstado=new JLabel("Sin consulta");
        lblXml=new JLabel("XML original");btnCerrar=new JButton("Cerrar");
        pestanas=new JTabbedPane();
        panelDatos=new JPanel();panelXml=new JPanel();
        cmbSeccion=new JComboBox<>();
        modelo=new DefaultTableModel(){private static final long serialVersionUID=1L;
            @Override public boolean isCellEditable(int fila,int columna){return false;}};
        tabla=new JTable(modelo);tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);tabla.setCellSelectionEnabled(true);
        scrollDatos=new JScrollPane(tabla);
        txaValor=new JTextArea(3,40);txaValor.setEditable(false);txaValor.setLineWrap(true);txaValor.setWrapStyleWord(true);
        scrollValor=new JScrollPane(txaValor);
        txaXml=new JTextArea();txaXml.setEditable(false);txaXml.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.PLAIN,12));
        scrollXml=new JScrollPane(txaXml);
        GroupLayout d=new GroupLayout(panelDatos);panelDatos.setLayout(d);d.setAutoCreateGaps(true);d.setAutoCreateContainerGaps(true);
        d.setHorizontalGroup(d.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(d.createSequentialGroup().addComponent(lblSeccion).addComponent(cmbSeccion,140,300,Short.MAX_VALUE))
            .addComponent(scrollDatos,0,600,Short.MAX_VALUE).addComponent(scrollValor,0,600,Short.MAX_VALUE));
        d.setVerticalGroup(d.createSequentialGroup()
            .addGroup(d.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblSeccion).addComponent(cmbSeccion))
            .addComponent(scrollDatos,100,240,Short.MAX_VALUE).addComponent(scrollValor,60,80,100));
        GroupLayout x=new GroupLayout(panelXml);panelXml.setLayout(x);x.setAutoCreateContainerGaps(true);x.setAutoCreateGaps(true);
        x.setHorizontalGroup(x.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(lblXml,0,600,Short.MAX_VALUE).addComponent(scrollXml,0,600,Short.MAX_VALUE));
        x.setVerticalGroup(x.createSequentialGroup().addComponent(lblXml).addComponent(scrollXml,100,320,Short.MAX_VALUE));
        pestanas.addTab("Datos fiscales",panelDatos);pestanas.addTab("XML original",panelXml);
        panelRelaciones=new JPanel();
        panelSuperiorGrafo=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,4,2));
        btnCentrar=new JButton("Centrar");
        btnAjustar=new JButton("Ajustar");
        btnActualizarGrafo=new JButton("Actualizar");
        btnVolver=new JButton("Volver");
        btnVolver.setEnabled(false);
        btnCentrar.setEnabled(false);btnAjustar.setEnabled(false);btnActualizarGrafo.setEnabled(false);
        panelSuperiorGrafo.add(btnCentrar);panelSuperiorGrafo.add(btnAjustar);panelSuperiorGrafo.add(btnActualizarGrafo);
        panelGrafo=new PanelGrafoCfdi();
        lblGrafo=new JLabel("Rueda: zoom · Arrastrar fondo: mover · Doble clic: detalle");
        lblSeleccion=new JLabel("Sin selección");
        GroupLayout r=new GroupLayout(panelRelaciones);panelRelaciones.setLayout(r);
        r.setAutoCreateGaps(true);r.setAutoCreateContainerGaps(true);
        r.setHorizontalGroup(r.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(panelSuperiorGrafo,0,480,Short.MAX_VALUE).addComponent(panelGrafo,0,480,Short.MAX_VALUE)
            .addComponent(lblGrafo,0,480,Short.MAX_VALUE).addComponent(lblSeleccion,0,480,Short.MAX_VALUE));
        r.setVerticalGroup(r.createSequentialGroup()
            .addComponent(panelSuperiorGrafo,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE)
            .addComponent(panelGrafo,100,280,Short.MAX_VALUE).addComponent(lblGrafo).addComponent(lblSeleccion));
        pestanas.addTab("Documentos relacionados",panelRelaciones);
        pestanas.setEnabledAt(2,false);
        panelGrafo.configurar(this::seleccionarNodo,this::abrirNodo);
        btnCentrar.addActionListener(e->panelGrafo.centrar());
        btnAjustar.addActionListener(e->panelGrafo.ajustar());
        btnActualizarGrafo.addActionListener(e->cargarGrafo());
        btnVolver.addActionListener(e->{if(!historial.isEmpty())cargar(servicioActual,sesionActual,titularActual,historial.pop());});
        pestanas.addChangeListener(e->{if(pestanas.getSelectedComponent()==panelRelaciones&&!grafoListo&&!grafoOcupado)cargarGrafo();});
        GroupLayout g=new GroupLayout(contenido);contenido.setLayout(g);g.setAutoCreateGaps(true);g.setAutoCreateContainerGaps(true);
        g.setHorizontalGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(lblUuid,0,600,Short.MAX_VALUE)
            .addComponent(pestanas,0,600,Short.MAX_VALUE)
            .addGroup(g.createSequentialGroup().addComponent(lblEstado,0,300,Short.MAX_VALUE).addComponent(btnVolver).addComponent(btnCerrar)));
        g.setVerticalGroup(g.createSequentialGroup().addComponent(lblUuid).addComponent(pestanas,100,390,Short.MAX_VALUE)
            .addGroup(g.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblEstado).addComponent(btnVolver).addComponent(btnCerrar)));
        cmbSeccion.addActionListener(e->mostrarSeccion());btnCerrar.addActionListener(e->dispose());
        tabla.getSelectionModel().addListSelectionListener(e->mostrarValor());
        tabla.getColumnModel().getSelectionModel().addListSelectionListener(e->mostrarValor());
    }

    private void cargar(DetalleCfdiService servicio,UsuarioSesion sesion,long titular,long documento){
        final int ciclo=++generacion;
        if(tareaDetalle!=null)tareaDetalle.cancel(true);
        if(tareaGrafo!=null)tareaGrafo.cancel(true);
        servicioActual=servicio;sesionActual=sesion;titularActual=titular;documentoActual=documento;
        detalleListo=false;grafoListo=false;grafoOcupado=false;
        pestanas.setSelectedIndex(0);pestanas.setEnabledAt(2,false);
        panelGrafo.mostrar(null,Map.of());btnCentrar.setEnabled(false);btnAjustar.setEnabled(false);btnActualizarGrafo.setEnabled(false);
        lblUuid.setText("Comprobante");cmbSeccion.removeAllItems();modelo.setRowCount(0);txaXml.setText("");txaValor.setText("");
        lblXml.setText("XML original");lblXml.setToolTipText(null);lblEstado.setText("Consultando...");
        btnVolver.setEnabled(!historial.isEmpty());
        tareaDetalle=new SwingWorker<DetalleCargado,Void>(){
            @Override protected DetalleCargado doInBackground(){
                Detalle d=servicio.consultar(sesion.id(),titular,documento);
                return new DetalleCargado(d,servicio.puedeConsultarGrafo(sesion.id(),titular));
            }
            @Override protected void done(){
                if(ciclo!=generacion||isCancelled()||!isDisplayable())return;
                try{
                    DetalleCargado resultado=get();Detalle d=resultado.detalle();
                    lblUuid.setText("UUID: "+d.uuid());cmbSeccion.removeAllItems();
                    for(Seccion sec:d.secciones())cmbSeccion.addItem(sec);
                    txaXml.setText(d.xml());txaXml.setCaretPosition(0);
                    lblXml.setText(d.avisoXml());lblXml.setToolTipText(d.avisoXml());
                    detalleListo=true;
                    pestanas.setEnabledAt(2,resultado.grafoPermitido());
                    pestanas.setToolTipTextAt(2,resultado.grafoPermitido()?"Grafo dirigido de dos niveles":"Requiere CFDI.CONSULTAR_GRAFO");
                    mostrarSeccion();
                }catch(InterruptedException e){Thread.currentThread().interrupt();}
                catch(ExecutionException e){lblEstado.setText("Detalle no disponible");JOptionPane.showMessageDialog(Fr_DatosCfdi.this,e.getCause().getMessage(),"Detalle CFDI",JOptionPane.ERROR_MESSAGE);}
            }
        };
        tareaDetalle.execute();
    }

    private void cargarGrafo(){
        if(!detalleListo||grafoOcupado||!pestanas.isEnabledAt(2))return;
        final int ciclo=generacion;
        final DetalleCfdiService servicio=servicioActual;final Long actor=sesionActual.id();
        final long titular=titularActual,documento=documentoActual;
        grafoOcupado=true;grafoListo=false;panelGrafo.mostrar(null,Map.of());
        btnCentrar.setEnabled(false);btnAjustar.setEnabled(false);btnActualizarGrafo.setEnabled(false);
        lblGrafo.setText("Consultando relaciones documentales...");
        tareaGrafo=new SwingWorker<GrafoCargado,Void>(){
            @Override protected GrafoCargado doInBackground(){
                GrafoFiscal g=servicio.consultarGrafo(actor,titular,documento);
                return new GrafoCargado(g,new DistribucionGrafoService().distribuir(g));
            }
            @Override protected void done(){
                if(ciclo!=generacion||isCancelled()||!isDisplayable())return;
                grafoOcupado=false;btnActualizarGrafo.setEnabled(true);
                try{
                    GrafoCargado r=get();GrafoFiscal g=r.grafo();
                    panelGrafo.mostrar(g,r.posiciones());
                    if(panelGrafo.getWidth()>32)panelGrafo.ajustar();
                    grafoListo=true;btnCentrar.setEnabled(true);btnAjustar.setEnabled(true);
                    lblGrafo.setText(g.nodos().size()+" nodos · "+g.aristas().size()+" relaciones · 2 niveles"
                        +(g.limitado()?" · Grafo parcial (límite alcanzado)":""));
                    lblGrafo.setToolTipText("Rueda: zoom · Arrastre: mover nodo/lienzo · Doble clic: detalle");
                }catch(InterruptedException e){Thread.currentThread().interrupt();}
                catch(ExecutionException e){lblGrafo.setText("Grafo no disponible: "+e.getCause().getMessage());}
            }
        };
        tareaGrafo.execute();
    }

    private void seleccionarNodo(NodoCfdi n){
        lblSeleccion.setText(n==null?"Sin selección":n.uuid()+(n.disponible()?"":" · No disponible"));
    }

    private void abrirNodo(NodoCfdi n){
        if(!grafoListo||n==null||!n.disponible())return;
        if(n.documentoId()==documentoActual){pestanas.setSelectedIndex(0);return;}
        if(historial.size()>=30)historial.removeLast();
        historial.push(documentoActual);
        cargar(servicioActual,sesionActual,titularActual,n.documentoId());
    }

    /** Cancela resultados pendientes para no reactivar un diálogo cerrado. */
    @Override public void dispose(){
        generacion++;
        if(tareaDetalle!=null)tareaDetalle.cancel(true);
        if(tareaGrafo!=null)tareaGrafo.cancel(true);
        super.dispose();
    }

    private void mostrarSeccion(){
        Seccion s=(Seccion)cmbSeccion.getSelectedItem();
        modelo.setRowCount(0);txaValor.setText("");
        if(s==null)return;
        modelo.setColumnIdentifiers(s.columnas().toArray());
        for(var f:s.filas())modelo.addRow(f.toArray());
        for(int i=0;i<tabla.getColumnCount();i++)tabla.getColumnModel().getColumn(i).setPreferredWidth(150);
        lblEstado.setText(s.filas().size()+" filas"+(s.limitada()?" (límite 1000; consulte XML original)":""));
    }

    private void mostrarValor(){
        int r=tabla.getSelectedRow(),c=tabla.getSelectedColumn();
        if(r<0||c<0){txaValor.setText("");return;}
        Object v=tabla.getValueAt(r,c);txaValor.setText(v==null?"Sin dato":v.toString());txaValor.setCaretPosition(0);
    }
}
