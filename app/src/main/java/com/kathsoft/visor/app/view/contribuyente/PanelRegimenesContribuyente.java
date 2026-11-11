package com.kathsoft.visor.app.view.contribuyente;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import com.kathsoft.visor.app.model.dto.RegimenContribuyente;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.contribuyente.ContribuyentesService;

/**
 * Detalle fiscal embebido en la única ventana de datos de contribuyente.
 * Mantiene historial de periodos del régimen, sin otra ventana adicional.
 *
 * <p>Compatibilidad Eclipse WindowBuilder: GroupLayout, componentes
 * declarados como campos y constructor público sin servicios.</p>
 */
public class PanelRegimenesContribuyente extends JPanel {

    private static final long serialVersionUID = 1L;

    private ContribuyentesService servicio;
    private UsuarioSesion sesion;
    private Long contribuyenteId;
    private boolean puedeEditar;
    private boolean cargando;

    private JLabel lblInstrucciones;
    private JLabel lblCodigo;
    private JLabel lblDesde;
    private JLabel lblHasta;
    private JLabel lblEstado;
    private JTextField txfCodigo;
    private JTextField txfDesde;
    private JTextField txfHasta;
    private JButton btnAgregar;
    private JButton btnCerrarVigencia;
    private JButton btnEliminar;
    private JButton btnActualizar;
    private JScrollPane scrollPane;
    private JTable tablaRegimenes;
    private DefaultTableModel modeloRegimenes;
    private JPanel panelAcciones;

    /** Constructor de diseño independiente de MySQL y del sistema de archivos. */
    public PanelRegimenesContribuyente() {
        inicializarComponentes();
    }

    /**
     * Inyecta dependencias del registro abierto y consulta el historial.
     *
     * @param servicio servicio RF-004
     * @param sesion operador
     * @param contribuyenteId registro padre
     * @param puedeEditar true para usuarios con permiso de régimen
     */
    public void configurar(ContribuyentesService servicio, UsuarioSesion sesion,
            Long contribuyenteId, boolean puedeEditar) {
        this.servicio = servicio;
        this.sesion = sesion;
        this.contribuyenteId = contribuyenteId;
        this.puedeEditar = puedeEditar;
        habilitarControles(true);
        recargar();
    }

    private void inicializarComponentes() {
        setBorder(new EmptyBorder(8, 8, 8, 8));
        lblInstrucciones = new JLabel("Periodos históricos de régimen. Fecha ISO: AAAA-MM-DD; fin vacío = vigente.");
        lblCodigo = new JLabel("Clave SAT");
        lblDesde = new JLabel("Vigente desde");
        lblHasta = new JLabel("Vigente hasta");
        lblEstado = new JLabel("No hay contribuyente seleccionado");
        txfCodigo = new JTextField(8);
        txfDesde = new JTextField(10);
        txfHasta = new JTextField(10);
        txfCodigo.setToolTipText("Código de 3 cifras de c_RegimenFiscal");
        txfDesde.setToolTipText("AAAA-MM-DD");
        txfHasta.setToolTipText("AAAA-MM-DD o vacío");

        tablaRegimenes = new JTable();
        modeloRegimenes = new DefaultTableModel(
                new Object[] {"Clave SAT", "Desde", "Hasta"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int fila, int col) { return false; }
        };
        tablaRegimenes.setModel(modeloRegimenes);
        tablaRegimenes.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tablaRegimenes.setRowHeight(25);
        scrollPane = new JScrollPane();
        scrollPane.setViewportView(tablaRegimenes);
        tablaRegimenes.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int fila = tablaRegimenes.getSelectedRow();
            if (fila >= 0) {
                int modelo = tablaRegimenes.convertRowIndexToModel(fila);
                txfCodigo.setText((String) modeloRegimenes.getValueAt(modelo, 0));
                txfDesde.setText((String) modeloRegimenes.getValueAt(modelo, 1));
                String hasta = (String) modeloRegimenes.getValueAt(modelo, 2);
                txfHasta.setText(hasta == null ? "" : hasta);
            }
        });

        panelAcciones = new JPanel();
        btnAgregar = new JButton("Registrar periodo");
        btnCerrarVigencia = new JButton("Actualizar fin");
        btnEliminar = new JButton("Eliminar periodo");
        btnActualizar = new JButton("Actualizar lista");
        btnAgregar.addActionListener(e -> registrar());
        btnCerrarVigencia.addActionListener(e -> cerrarVigencia());
        btnEliminar.addActionListener(e -> eliminar());
        btnActualizar.addActionListener(e -> recargar());

        GroupLayout gl_acciones = new GroupLayout(panelAcciones);
        panelAcciones.setLayout(gl_acciones);
        gl_acciones.setAutoCreateGaps(true);
        gl_acciones.setHorizontalGroup(gl_acciones.createSequentialGroup()
                .addComponent(btnAgregar)
                .addComponent(btnCerrarVigencia)
                .addComponent(btnEliminar)
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(btnActualizar));
        gl_acciones.setVerticalGroup(gl_acciones.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(btnAgregar)
                .addComponent(btnCerrarVigencia)
                .addComponent(btnEliminar)
                .addComponent(btnActualizar));

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblInstrucciones)
            .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(lblCodigo)
                .addComponent(txfCodigo, 60, 90, 130)
                .addComponent(lblDesde)
                .addComponent(txfDesde, 80, 115, 145)
                .addComponent(lblHasta)
                .addComponent(txfHasta, 80, 115, 145))
            .addComponent(panelAcciones, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(lblEstado));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblInstrucciones)
            .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblCodigo)
                .addComponent(txfCodigo, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblDesde)
                .addComponent(txfDesde, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblHasta)
                .addComponent(txfHasta, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addComponent(panelAcciones, GroupLayout.PREFERRED_SIZE,
                    GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(lblEstado));
        habilitarControles(true);
    }

    private void recargar() {
        if (servicio == null || sesion == null || contribuyenteId == null || cargando) return;
        cargando = true;
        habilitarControles(false);
        lblEstado.setText("Consultando regímenes...");
        SwingWorker<List<RegimenContribuyente>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<RegimenContribuyente> doInBackground() {
                return servicio.listarRegimenes(sesion.id(), contribuyenteId);
            }
            @Override
            protected void done() {
                try {
                    List<RegimenContribuyente> lista = get();
                    modeloRegimenes.setRowCount(0);
                    for (RegimenContribuyente r : lista) {
                        modeloRegimenes.addRow(new Object[] {r.claveRegimen(),
                                r.desde().toString(),
                                r.hasta() == null ? "" : r.hasta().toString()});
                    }
                    lblEstado.setText(lista.size() + " periodo(s) registrado(s)");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    error(ex.getCause());
                } finally {
                    cargando = false;
                    habilitarControles(true);
                }
            }
        };
        worker.execute();
    }

    private void registrar() {
        try {
            String codigo = txfCodigo.getText();
            LocalDate inicio = LocalDate.parse(txfDesde.getText().strip());
            LocalDate fin = fechaOpcional();
            ejecutar(() -> servicio.registrarRegimen(sesion.id(), contribuyenteId,
                    new RegimenContribuyente(codigo, inicio, fin)));
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Fechas inválidas. Utilice AAAA-MM-DD.", "Validación",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void cerrarVigencia() {
        int fila = filaSeleccionada();
        if (fila < 0) return;
        try {
            String codigo = (String) modeloRegimenes.getValueAt(fila, 0);
            LocalDate inicio = LocalDate.parse((String) modeloRegimenes.getValueAt(fila, 1));
            LocalDate fin = fechaOpcional();
            ejecutar(() -> servicio.actualizarFinRegimen(
                    sesion.id(), contribuyenteId, codigo, inicio, fin));
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "La fecha debe tener formato AAAA-MM-DD.");
        }
    }

    private void eliminar() {
        int fila = filaSeleccionada();
        if (fila < 0) return;
        if (JOptionPane.showConfirmDialog(this,
                "¿Eliminar este periodo de régimen registrado?",
                "Confirmar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        String codigo = (String) modeloRegimenes.getValueAt(fila, 0);
        LocalDate inicio = LocalDate.parse((String) modeloRegimenes.getValueAt(fila, 1));
        ejecutar(() -> servicio.eliminarRegimen(sesion.id(), contribuyenteId, codigo, inicio));
    }

    private LocalDate fechaOpcional() {
        String entrada = txfHasta.getText().strip();
        return entrada.isEmpty() ? null : LocalDate.parse(entrada);
    }

    private int filaSeleccionada() {
        int vista = tablaRegimenes.getSelectedRow();
        if (vista < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un periodo en la tabla.");
            return -1;
        }
        return tablaRegimenes.convertRowIndexToModel(vista);
    }

    private void ejecutar(Runnable operacion) {
        habilitarControles(false);
        lblEstado.setText("Guardando cambios...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                operacion.run();
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    habilitarControles(true);
                    recargar();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    habilitarControles(true);
                } catch (ExecutionException ex) {
                    error(ex.getCause());
                    habilitarControles(true);
                }
            }
        };
        worker.execute();
    }

    private void habilitarControles(boolean disponible) {
        boolean edicion = disponible && puedeEditar && servicio != null;
        txfCodigo.setEditable(edicion);
        txfDesde.setEditable(edicion);
        txfHasta.setEditable(edicion);
        btnAgregar.setEnabled(edicion);
        btnCerrarVigencia.setEnabled(edicion);
        btnEliminar.setEnabled(edicion);
        btnActualizar.setEnabled(disponible && servicio != null);
    }

    private void error(Throwable ex) {
        lblEstado.setText("No fue posible completar la operación");
        JOptionPane.showMessageDialog(this,
                ex == null ? "Error inesperado" : ex.getMessage(),
                "Regímenes fiscales", JOptionPane.ERROR_MESSAGE);
    }
}
