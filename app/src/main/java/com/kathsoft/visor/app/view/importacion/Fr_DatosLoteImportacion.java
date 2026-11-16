package com.kathsoft.visor.app.view.importacion;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Archivo;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Lote;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Pagina;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Eliminacion;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** RF-013: detalle modal de los archivos de un lote, incluyendo diagnósticos de rechazo. */
public class Fr_DatosLoteImportacion extends JDialog {
    private static final long serialVersionUID=1L;
    private HistorialImportacionesService servicio;
    private UsuarioSesion sesion;
    private Set<String> permisos=Set.of();
    private Lote lote;
    private JLabel lblLote,lblEstado;
    private JTable tabla;
    private DefaultTableModel modelo;
    private JTextArea txaDetalle;
    private JButton btnAnterior,btnSiguiente,btnEliminar,btnActualizar,btnCerrar;
    private List<Archivo> filas=List.of();
    private int pagina;
    private boolean ocupado,siguiente;

    /** Constructor de diseño sin sesión ni conexión. */
    public Fr_DatosLoteImportacion() {
        inicializarComponentes();
    }

    /** Inyecta dependencias al abrir el diálogo; el servicio verifica cada solicitud. */
    public void configurar(HistorialImportacionesService servicio,UsuarioSesion sesion,
            Set<String> permisos,Lote lote) {
        this.servicio=servicio; this.sesion=sesion;
        this.permisos=permisos==null?Set.of():Set.copyOf(permisos);
        this.lote=lote;
        lblLote.setText(lote==null?"Sin lote":"Lote "+lote.id()+" — "+lote.rfc()+" — "+lote.estado());
        habilitar();
    }

    /** Carga archivos de la primera página fuera del EDT. */
    public void recargar() { cargar(0); }

    private void inicializarComponentes() {
        setTitle("Detalle de lote de importación");
        setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel contenido=new JPanel();
        setContentPane(contenido);
        lblLote=new JLabel("Detalle de importación");
        lblEstado=new JLabel("Sin consulta");
        btnAnterior=new JButton("Anterior");
        btnSiguiente=new JButton("Siguiente");
        btnEliminar=new JButton("Eliminar seleccionado");
        btnActualizar=new JButton("Actualizar");
        btnCerrar=new JButton("Cerrar");
        modelo=new DefaultTableModel(new String[]{"Ordinal","Archivo","UUID","Resultado","Detalle"},0) {
            private static final long serialVersionUID=1L;
            @Override public boolean isCellEditable(int fila,int columna) { return false; }
        };
        tabla=new JTable(modelo);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.setAutoCreateRowSorter(true);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(240);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(290);
        tabla.getColumnModel().getColumn(4).setPreferredWidth(330);
        JScrollPane scroll=new JScrollPane(tabla);
        txaDetalle=new JTextArea(5,50);
        txaDetalle.setEditable(false);
        txaDetalle.setLineWrap(true);
        txaDetalle.setWrapStyleWord(true);
        JScrollPane diagnostico=new JScrollPane(txaDetalle);
        JLabel lblDetalle=new JLabel("Identificación y diagnóstico del archivo seleccionado");
        GroupLayout layout=new GroupLayout(contenido);
        contenido.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblLote)
                .addGroup(layout.createSequentialGroup().addComponent(btnEliminar).addComponent(btnActualizar))
                .addComponent(scroll,0,690,Short.MAX_VALUE)
                .addComponent(lblDetalle)
                .addComponent(diagnostico,0,690,Short.MAX_VALUE)
                .addGroup(layout.createSequentialGroup().addComponent(btnAnterior).addComponent(btnSiguiente)
                    .addComponent(lblEstado,0,200,Short.MAX_VALUE).addComponent(btnCerrar)));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(lblLote)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(btnEliminar).addComponent(btnActualizar))
                .addComponent(scroll,140,250,Short.MAX_VALUE)
                .addComponent(lblDetalle)
                .addComponent(diagnostico,80,120,180)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(btnAnterior).addComponent(btnSiguiente)
                    .addComponent(lblEstado).addComponent(btnCerrar)));
        btnAnterior.addActionListener(e->cargar(pagina-1));
        btnSiguiente.addActionListener(e->cargar(pagina+1));
        btnActualizar.addActionListener(e->recargar());
        btnEliminar.addActionListener(e->eliminar());
        btnCerrar.addActionListener(e->dispose());
        tabla.getSelectionModel().addListSelectionListener(e->{mostrarDetalle();habilitar();});
        pack();
        setSize(740,530);
        habilitar();
    }

    private boolean autorizado() {
        return lote!=null && sesion!=null && servicio!=null
                && permisos.contains(Permisos.IMPORTACIONES_CONSULTAR)
                && permisos.contains(Permisos.IMPORTACIONES_CONSULTAR_DETALLE);
    }

    private void cargar(int destino) {
        if(!autorizado() || ocupado || destino<0) return;
        ocupado=true; habilitar(); lblEstado.setText("Consultando...");
        final Long operador=sesion.id();
        final long loteId=lote.id();
        new SwingWorker<Pagina<Archivo>,Void>() {
            @Override protected Pagina<Archivo> doInBackground() { return servicio.archivos(operador,loteId,destino); }
            @Override protected void done() {
                try {
                    Pagina<Archivo> r=get();
                    pagina=destino; siguiente=r.siguiente(); filas=r.filas();
                    modelo.setRowCount(0);
                    for(Archivo a:filas) modelo.addRow(new Object[]{a.ordinal(),a.nombre(),a.uuid(),a.resultado(),a.detalle()});
                    txaDetalle.setText("");
                    lblEstado.setText("Página "+(pagina+1)+" — "+filas.size()+" archivos");
                } catch(InterruptedException e) { Thread.currentThread().interrupt(); limpiar(); }
                catch(ExecutionException e) { limpiar(); error(e.getCause()); }
                finally { ocupado=false; habilitar(); }
            }
        }.execute();
    }

    private void limpiar() {
        filas=List.of(); modelo.setRowCount(0); txaDetalle.setText("");
        siguiente=false; pagina=0; lblEstado.setText("Consulta no disponible");
    }

    private Archivo seleccionado() {
        int v=tabla.getSelectedRow();
        if(v<0) return null;
        int i=tabla.convertRowIndexToModel(v);
        return i<filas.size()?filas.get(i):null;
    }

    private void mostrarDetalle() {
        Archivo a=seleccionado();
        txaDetalle.setText(a==null?"":"Archivo: "+a.nombre()+"\nRuta: "+valor(a.ruta())
                +"\nUUID: "+valor(a.uuid())+"\nSHA-256: "+valor(a.sha256())
                +"\nResultado: "+a.resultado()+"\nDetalle: "+valor(a.detalle()));
        txaDetalle.setCaretPosition(0);
    }

    private static String valor(String s) { return s==null?"—":s; }

    private void eliminar() {
        Archivo a=seleccionado();
        if(!autorizado() || ocupado || a==null || lote.fin()==null || "EN_PROCESO".equals(lote.estado())
                || !permisos.contains(Permisos.IMPORTACIONES_ELIMINAR_ARCHIVO)) return;
        String motivo=JOptionPane.showInputDialog(this,"Eliminar registro "+a.nombre()+"\n"
                +"Se eliminan los datos del CFDI solo si es exclusivo y no tiene dependencias protegidas.\n"
                +"Se conservan los XML originales. No es cancelación ante el SAT.\n"
                +"Indique el motivo (máx. 200 caracteres):");
        if(motivo==null) return;
        if(motivo.isBlank() || motivo.strip().length()>200) {
            JOptionPane.showMessageDialog(this,"Motivo obligatorio de 1 a 200 caracteres.");return;
        }
        ocupado=true; habilitar(); lblEstado.setText("Eliminando...");
        final Long operador=sesion.id();
        final long loteId=lote.id();
        new SwingWorker<Eliminacion,Void>() {
            @Override protected Eliminacion doInBackground() { return servicio.eliminarArchivo(operador,loteId,a.id(),motivo); }
            @Override protected void done() {
                try { JOptionPane.showMessageDialog(Fr_DatosLoteImportacion.this,PanelHistorialImportaciones.resumen(get())); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                catch(ExecutionException e) { error(e.getCause()); }
                finally { ocupado=false; habilitar(); cargar(0); }
            }
        }.execute();
    }

    private void habilitar() {
        boolean libre=autorizado() && !ocupado;
        btnAnterior.setEnabled(libre && pagina>0);
        btnSiguiente.setEnabled(libre && siguiente);
        btnActualizar.setEnabled(libre);
        btnEliminar.setEnabled(libre && seleccionado()!=null && lote.fin()!=null
                && !"EN_PROCESO".equals(lote.estado()) && permisos.contains(Permisos.IMPORTACIONES_ELIMINAR_ARCHIVO));
        btnCerrar.setEnabled(!ocupado);
        setDefaultCloseOperation(ocupado?DO_NOTHING_ON_CLOSE:DISPOSE_ON_CLOSE);
    }

    private void error(Throwable e) {
        JOptionPane.showMessageDialog(this,e==null?"Error desconocido":e.getMessage(),
                "Detalle de importación",JOptionPane.ERROR_MESSAGE);
    }
}
