package com.kathsoft.visor.app.view.importacion;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Lote;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Pagina;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Rango;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Eliminacion;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.MaskFormatter;

/** RF-013: catálogo de lotes; constructor sin acceso a persistencia para WindowBuilder. */
public class PanelHistorialImportaciones extends JPanel {
    private static final long serialVersionUID=1L;
    private static final DateTimeFormatter DIA=DateTimeFormatter.ofPattern("dd-MM-uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm:ss");
    private HistorialImportacionesService servicio;
    private UsuarioSesion sesion;
    private Set<String> permisos=Set.of();
    private JFormattedTextField txfDesde,txfHasta;
    private JButton btnConsultar,btnDetalle,btnEliminar,btnAnterior,btnSiguiente;
    private JLabel lblEstado;
    private JTable tabla;
    private DefaultTableModel modelo;
    private List<Lote> filas=List.of();
    private Rango ultimoRango;
    private int pagina,generacion;
    private boolean ocupado,siguiente;

    /** Constructor editable; no usa MySQL ni el sistema de archivos. */
    public PanelHistorialImportaciones() {
        inicializarComponentes();
    }

    /** Inyecta sesión y catálogo de permisos; las operaciones los revalidan en el servicio. */
    public void configurar(HistorialImportacionesService servicio,UsuarioSesion sesion,Set<String> permisos) {
        boolean cambio=this.servicio!=servicio || !java.util.Objects.equals(this.sesion,sesion);
        this.servicio=servicio;
        this.sesion=sesion;
        this.permisos=permisos==null?Set.of():Set.copyOf(permisos);
        if (cambio || !autorizado()) {
            generacion++;
            ocupado=false;
            siguiente=false;
            pagina=0;
            ultimoRango=null;
            filas=List.of();
            modelo.setRowCount(0);
            lblEstado.setText(autorizado()?"Pulse Consultar":"Sin permiso de consulta");
        }
        habilitar();
    }

    /** Carga la primera página para las fechas actuales. */
    public void recargar() { consultar(0,true); }

    private void inicializarComponentes() {
        JLabel lblTitulo=new JLabel("Historial de importaciones CFDI");
        JLabel lblDesde=new JLabel("Desde");
        JLabel lblHasta=new JLabel("Hasta");
        txfDesde=new JFormattedTextField(mascara());
        txfHasta=new JFormattedTextField(mascara());
        txfDesde.setColumns(10);
        txfHasta.setColumns(10);
        txfDesde.setFocusLostBehavior(JFormattedTextField.PERSIST);
        txfHasta.setFocusLostBehavior(JFormattedTextField.PERSIST);
        LocalDate hoy=LocalDate.now();
        txfDesde.setText(hoy.withDayOfMonth(1).format(DIA));
        txfHasta.setText(hoy.format(DIA));
        btnConsultar=new JButton("Consultar");
        btnDetalle=new JButton("Ver detalle");
        btnEliminar=new JButton("Eliminar lote");
        btnAnterior=new JButton("Anterior");
        btnSiguiente=new JButton("Siguiente");
        lblEstado=new JLabel("Sin consulta");
        modelo=new DefaultTableModel(new String[]{"Lote","RFC","Contribuyente","Usuario",
                "Inicio","Fin","Estado","Aceptados","Duplicados","Rechazados","Errores"},0) {
            private static final long serialVersionUID=1L;
            @Override public boolean isCellEditable(int fila,int columna) { return false; }
        };
        tabla=new JTable(modelo);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.setAutoCreateRowSorter(true);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(200);
        tabla.getColumnModel().getColumn(4).setPreferredWidth(145);
        tabla.getColumnModel().getColumn(5).setPreferredWidth(145);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(125);
        JScrollPane scroll=new JScrollPane(tabla);

        GroupLayout layout=new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblTitulo)
                .addGroup(layout.createSequentialGroup().addComponent(btnDetalle).addComponent(btnEliminar))
                .addComponent(scroll,0,650,Short.MAX_VALUE)
                .addGroup(layout.createSequentialGroup().addComponent(lblDesde)
                    .addComponent(txfDesde,100,112,135).addComponent(lblHasta)
                    .addComponent(txfHasta,100,112,135).addComponent(btnConsultar))
                .addGroup(layout.createSequentialGroup().addComponent(btnAnterior)
                    .addComponent(btnSiguiente).addComponent(lblEstado,0,200,Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(lblTitulo)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(btnDetalle).addComponent(btnEliminar))
                .addComponent(scroll,150,300,Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblDesde).addComponent(txfDesde).addComponent(lblHasta)
                    .addComponent(txfHasta).addComponent(btnConsultar))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(btnAnterior).addComponent(btnSiguiente).addComponent(lblEstado)));
        btnConsultar.addActionListener(e->consultar(0,true));
        txfDesde.addActionListener(e->consultar(0,true));
        txfHasta.addActionListener(e->consultar(0,true));
        btnAnterior.addActionListener(e->consultar(pagina-1,false));
        btnSiguiente.addActionListener(e->consultar(pagina+1,false));
        btnDetalle.addActionListener(e->detalle());
        btnEliminar.addActionListener(e->eliminar());
        tabla.getSelectionModel().addListSelectionListener(e->habilitar());
        habilitar();
    }

    private static MaskFormatter mascara() {
        try {
            MaskFormatter m=new MaskFormatter("##-##-####");
            m.setPlaceholderCharacter('_');
            return m;
        } catch(ParseException e) { throw new IllegalStateException(e); }
    }

    private boolean autorizado() {
        return servicio!=null && sesion!=null && permisos.contains(Permisos.IMPORTACIONES_CONSULTAR);
    }

    private void consultar(int destino,boolean nuevo) {
        if(!autorizado() || ocupado || destino<0) return;
        final Rango rango;
        try {
            rango=nuevo?new Rango(LocalDate.parse(txfDesde.getText(),DIA),
                    LocalDate.parse(txfHasta.getText(),DIA)):ultimoRango;
            if(rango==null) return;
        } catch(RuntimeException e) {
            JOptionPane.showMessageDialog(this,"Indique un intervalo válido (dd-mm-aaaa).",
                    "Historial de importaciones",JOptionPane.WARNING_MESSAGE);
            return;
        }
        final int token=++generacion;
        final Long operador=sesion.id();
        final HistorialImportacionesService actual=servicio;
        ocupado=true;
        lblEstado.setText("Consultando...");
        habilitar();
        new SwingWorker<Pagina<Lote>,Void>() {
            @Override protected Pagina<Lote> doInBackground() { return actual.listar(operador,rango,destino); }
            @Override protected void done() {
                if(token!=generacion || !autorizado()) return;
                try {
                    Pagina<Lote> r=get();
                    filas=r.filas();
                    siguiente=r.siguiente();
                    pagina=destino;
                    ultimoRango=rango;
                    modelo.setRowCount(0);
                    for(Lote l:filas) modelo.addRow(new Object[]{l.id(),l.rfc(),l.contribuyente(),l.usuario(),
                        l.inicio()==null?"":l.inicio().format(FECHA),l.fin()==null?"":l.fin().format(FECHA),
                        l.estado(),l.aceptados(),l.duplicados(),l.rechazados(),l.errores()});
                    lblEstado.setText("Página "+(pagina+1)+" — "+filas.size()+" lotes");
                } catch(InterruptedException e) { Thread.currentThread().interrupt(); limpiarConsulta(); }
                catch(ExecutionException e) { limpiarConsulta(); error(e.getCause()); }
                finally { ocupado=false; habilitar(); }
            }
        }.execute();
    }

    private void limpiarConsulta() {
        filas=List.of(); modelo.setRowCount(0); siguiente=false; pagina=0; ultimoRango=null;
        lblEstado.setText("Consulta no disponible");
    }

    private Lote seleccionado() {
        int v=tabla.getSelectedRow();
        if(v<0) return null;
        int i=tabla.convertRowIndexToModel(v);
        return i<filas.size()?filas.get(i):null;
    }

    private void detalle() {
        Lote lote=seleccionado();
        if(!autorizado() || ocupado || lote==null) return;
        Fr_DatosLoteImportacion dialogo=new Fr_DatosLoteImportacion();
        dialogo.configurar(servicio,sesion,permisos,lote);
        dialogo.setLocationRelativeTo(this);
        dialogo.recargar();
        dialogo.setVisible(true);
        consultar(0,false);
    }

    private void eliminar() {
        Lote lote=seleccionado();
        if(!autorizado() || ocupado || lote==null || lote.fin()==null
                || "EN_PROCESO".equals(lote.estado()) || !permisos.contains(Permisos.IMPORTACIONES_ELIMINAR_LOTE)) return;
        String motivo=JOptionPane.showInputDialog(this,
                "Eliminar lote "+lote.id()+" y sus registros de importación.\n"
                +"Solo se eliminan datos de CFDI exclusivos sin dependencias protegidas.\n"
                +"Los XML originales y comprobantes compartidos se conservan.\n"
                +"No es cancelación ante el SAT. Indique el motivo (máx. 200 caracteres):");
        if(motivo==null) return;
        if(motivo.isBlank() || motivo.strip().length()>200) {
            JOptionPane.showMessageDialog(this,"Motivo obligatorio de 1 a 200 caracteres."); return;
        }
        final int token=++generacion;
        final Long operador=sesion.id();
        final HistorialImportacionesService actual=servicio;
        ocupado=true; habilitar();
        lblEstado.setText("Eliminando lote...");
        new SwingWorker<Eliminacion,Void>() {
            @Override protected Eliminacion doInBackground() { return actual.eliminarLote(operador,lote.id(),motivo); }
            @Override protected void done() {
                if(token!=generacion || !autorizado()) return;
                try {
                    Eliminacion r=get();
                    JOptionPane.showMessageDialog(PanelHistorialImportaciones.this,resumen(r));
                } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                catch(ExecutionException e) { error(e.getCause()); }
                finally { ocupado=false; habilitar(); consultar(0,false); }
            }
        }.execute();
    }

    /** Explica por separado registros eliminados y comprobantes conservados. */
    static String resumen(Eliminacion r) {
        return "Registros eliminados: "+r.registros()+"\nDatos fiscales eliminados: "+r.documentosEliminados()
                +"\nComprobantes preservados: "+r.documentosPreservados()+"\nXML originales conservados.";
    }

    private void habilitar() {
        boolean libre=autorizado() && !ocupado;
        Lote l=seleccionado();
        txfDesde.setEnabled(libre); txfHasta.setEnabled(libre);
        btnConsultar.setEnabled(libre);
        btnDetalle.setEnabled(libre && l!=null);
        btnEliminar.setEnabled(libre && l!=null && l.fin()!=null && !"EN_PROCESO".equals(l.estado())
                && permisos.contains(Permisos.IMPORTACIONES_ELIMINAR_LOTE));
        btnAnterior.setEnabled(libre && pagina>0);
        btnSiguiente.setEnabled(libre && siguiente);
    }

    private void error(Throwable e) {
        JOptionPane.showMessageDialog(this,e==null?"Error desconocido":e.getMessage(),
                "Historial de importaciones",JOptionPane.ERROR_MESSAGE);
    }
}
