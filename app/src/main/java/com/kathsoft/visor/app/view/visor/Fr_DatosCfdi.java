package com.kathsoft.visor.app.view.visor;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService.Detalle;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService.Seccion;
import java.awt.Frame;
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
        GroupLayout g=new GroupLayout(contenido);contenido.setLayout(g);g.setAutoCreateGaps(true);g.setAutoCreateContainerGaps(true);
        g.setHorizontalGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(lblUuid,0,600,Short.MAX_VALUE)
            .addComponent(pestanas,0,600,Short.MAX_VALUE)
            .addGroup(g.createSequentialGroup().addComponent(lblEstado,0,300,Short.MAX_VALUE).addComponent(btnCerrar)));
        g.setVerticalGroup(g.createSequentialGroup().addComponent(lblUuid).addComponent(pestanas,100,390,Short.MAX_VALUE)
            .addGroup(g.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblEstado).addComponent(btnCerrar)));
        cmbSeccion.addActionListener(e->mostrarSeccion());btnCerrar.addActionListener(e->dispose());
        tabla.getSelectionModel().addListSelectionListener(e->mostrarValor());
        tabla.getColumnModel().getSelectionModel().addListSelectionListener(e->mostrarValor());
    }

    private void cargar(DetalleCfdiService servicio,UsuarioSesion sesion,long titular,long documento){
        lblEstado.setText("Consultando...");
        new SwingWorker<Detalle,Void>(){
            @Override protected Detalle doInBackground(){return servicio.consultar(sesion.id(),titular,documento);}
            @Override protected void done(){
                if(!isDisplayable())return;
                try{
                    Detalle d=get();lblUuid.setText("UUID: "+d.uuid());cmbSeccion.removeAllItems();
                    for(Seccion s:d.secciones())cmbSeccion.addItem(s);
                    txaXml.setText(d.xml());txaXml.setCaretPosition(0);
                    lblXml.setText(d.avisoXml());lblXml.setToolTipText(d.avisoXml());
                    mostrarSeccion();
                }catch(InterruptedException e){Thread.currentThread().interrupt();}
                catch(ExecutionException e){lblEstado.setText("Detalle no disponible");JOptionPane.showMessageDialog(Fr_DatosCfdi.this,e.getCause().getMessage(),"Detalle CFDI",JOptionPane.ERROR_MESSAGE);}
            }
        }.execute();
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
