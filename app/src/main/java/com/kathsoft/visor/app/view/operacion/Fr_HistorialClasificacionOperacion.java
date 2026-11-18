package com.kathsoft.visor.app.view.operacion;

import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.*;
import java.awt.Frame;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Detalle histórico paginado de un comprobante. */
public class Fr_HistorialClasificacionOperacion extends JDialog{
    private static final long serialVersionUID=1L;
    private JPanel contenido,panelBotones;
    private JLabel lblUuid,lblEstado;
    private JTable tabla;
    private JScrollPane scroll;
    private JButton btnAnterior,btnSiguiente,btnCerrar;
    private DefaultTableModel modelo;
    private ClasificacionOperacionesService servicio;
    private Long actor;
    private long titular,documento;
    private int pagina;
    private boolean ocupado,siguiente;
    private SwingWorker<Pagina<Evento>,Void> tarea;

    /** Constructor sin persistencia para WindowBuilder. */
    public Fr_HistorialClasificacionOperacion(){super((Frame)null,true);inicializarComponentes();}
    /** Abre el historial autorizado del documento. */
    public Fr_HistorialClasificacionOperacion(Frame owner,ClasificacionOperacionesService servicio,Long actor,long titular,Operacion op){
        super(owner,true);inicializarComponentes();this.servicio=servicio;this.actor=actor;this.titular=titular;documento=op.id();
        lblUuid.setText("UUID: "+op.uuid());SwingUtilities.invokeLater(()->cargar(0));
    }
    private void inicializarComponentes(){
        setTitle("Historial de clasificación");setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(700,400);
        contenido=new JPanel();setContentPane(contenido);lblUuid=new JLabel("UUID");lblEstado=new JLabel("Sin consulta");
        modelo=new DefaultTableModel(new String[]{"Fecha","Usuario","Categoría resultante","Naturaleza","Motivo"},0){
            private static final long serialVersionUID=1L;
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        tabla=new JTable(modelo);tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);scroll=new JScrollPane(tabla);
        for(int i=0;i<5;i++)tabla.getColumnModel().getColumn(i).setPreferredWidth(i==4?360:150);
        panelBotones=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        btnAnterior=new JButton("Anterior");btnSiguiente=new JButton("Siguiente");btnCerrar=new JButton("Cerrar");
        panelBotones.add(btnAnterior);panelBotones.add(btnSiguiente);panelBotones.add(lblEstado);panelBotones.add(btnCerrar);
        GroupLayout g=new GroupLayout(contenido);contenido.setLayout(g);g.setAutoCreateContainerGaps(true);g.setAutoCreateGaps(true);
        g.setHorizontalGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(lblUuid)
            .addComponent(scroll,0,650,Short.MAX_VALUE).addComponent(panelBotones,0,650,Short.MAX_VALUE));
        g.setVerticalGroup(g.createSequentialGroup().addComponent(lblUuid).addComponent(scroll,90,280,Short.MAX_VALUE)
            .addComponent(panelBotones,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE));
        btnAnterior.addActionListener(e->cargar(pagina-1));btnSiguiente.addActionListener(e->cargar(pagina+1));btnCerrar.addActionListener(e->dispose());
        habilitar();
    }
    private void habilitar(){btnAnterior.setEnabled(!ocupado&&pagina>0);btnSiguiente.setEnabled(!ocupado&&siguiente);}
    private void cargar(int p){
        if(servicio==null||ocupado||p<0)return;
        ocupado=true;habilitar();lblEstado.setText("Consultando...");
        tarea=new SwingWorker<>(){
            @Override protected Pagina<Evento> doInBackground(){return servicio.historial(actor,titular,documento,p);}
            @Override protected void done(){
                if(isCancelled()||!isDisplayable())return;
                try{
                    Pagina<Evento> datos=get();modelo.setRowCount(0);
                    for(Evento x:datos.filas())modelo.addRow(new Object[]{x.fecha(),x.usuario(),x.categoria()==null?"Sin clasificación (retiro)":x.categoria(),x.naturaleza(),x.motivo()});
                    pagina=p;siguiente=datos.siguiente();lblEstado.setText("Página "+(p+1)+" · "+datos.filas().size()+" cambios");
                }catch(InterruptedException ex){Thread.currentThread().interrupt();}
                catch(ExecutionException ex){modelo.setRowCount(0);siguiente=false;lblEstado.setText("Historial no disponible");JOptionPane.showMessageDialog(Fr_HistorialClasificacionOperacion.this,ex.getCause().getMessage());}
                finally{ocupado=false;habilitar();}
            }
        };
        tarea.execute();
    }
    @Override public void dispose(){if(tarea!=null)tarea.cancel(true);super.dispose();}
}
