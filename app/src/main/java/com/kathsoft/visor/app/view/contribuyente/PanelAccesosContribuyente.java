package com.kathsoft.visor.app.view.contribuyente;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import com.kathsoft.visor.app.model.dto.UsuarioAccesoContribuyente;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.contribuyente.ContribuyentesService;

/**
 * Selector de usuarios por contribuyente dentro del formulario de detalle.
 *
 * <p>La columna Boolean se muestra mediante JCheckBox en JTable. El
 * catálogo no crea otra ventana y no consulta MySQL en el constructor.
 * Los cambios son transaccionales en ContribuyentesService.</p>
 */
public class PanelAccesosContribuyente extends JPanel {

    private static final long serialVersionUID = 1L;

    private ContribuyentesService servicio;
    private UsuarioSesion sesion;
    private Long contribuyenteId;

    private JLabel lblInstrucciones;
    private JLabel lblEstado;
    private JScrollPane scrollPaneUsuarios;
    private JTable tablaUsuarios;
    private DefaultTableModel modeloUsuarios;
    private JPanel panelAcciones;
    private JButton btnTodos;
    private JButton btnNinguno;
    private JButton btnGuardar;
    private JButton btnActualizar;
    private List<UsuarioAccesoContribuyente> registros = List.of();

    /** Constructor sin acceso a servicios para WindowBuilder. */
    public PanelAccesosContribuyente() {
        inicializarComponentes();
    }

    /**
     * Define el contribuyente y carga las asignaciones de usuarios.
     *
     * @param servicio servicio de contribuyentes
     * @param sesion cuenta autorizada
     * @param contribuyenteId titular seleccionado
     */
    public void configurar(ContribuyentesService servicio, UsuarioSesion sesion,
            Long contribuyenteId) {
        this.servicio = servicio;
        this.sesion = sesion;
        this.contribuyenteId = contribuyenteId;
        recargar();
    }

    private void inicializarComponentes() {
        setBorder(new EmptyBorder(8, 8, 8, 8));
        lblInstrucciones = new JLabel(
                "Marque las cuentas que podrán operar con este contribuyente.");
        lblEstado = new JLabel("No hay contribuyente seleccionado");

        scrollPaneUsuarios = new JScrollPane();
        tablaUsuarios = new JTable();
        modeloUsuarios = new DefaultTableModel(
                new Object[] {"Permitir", "Usuario", "Nombre", "Estado"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public Class<?> getColumnClass(int columna) {
                return columna == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int fila, int columna) {
                return columna == 0 && servicio != null;
            }
        };
        tablaUsuarios.setModel(modeloUsuarios);
        tablaUsuarios.setRowHeight(26);
        tablaUsuarios.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tablaUsuarios.getColumnModel().getColumn(0).setPreferredWidth(80);
        tablaUsuarios.getColumnModel().getColumn(1).setPreferredWidth(155);
        tablaUsuarios.getColumnModel().getColumn(2).setPreferredWidth(260);
        tablaUsuarios.getColumnModel().getColumn(3).setPreferredWidth(90);
        scrollPaneUsuarios.setViewportView(tablaUsuarios);

        panelAcciones = new JPanel();
        btnTodos = new JButton("Marcar todos");
        btnNinguno = new JButton("Desmarcar todos");
        btnGuardar = new JButton("Guardar usuarios");
        btnActualizar = new JButton("Actualizar");
        btnTodos.addActionListener(e -> seleccionarTodos(true));
        btnNinguno.addActionListener(e -> seleccionarTodos(false));
        btnGuardar.addActionListener(e -> guardar());
        btnActualizar.addActionListener(e -> recargar());

        GroupLayout gl_acciones = new GroupLayout(panelAcciones);
        panelAcciones.setLayout(gl_acciones);
        gl_acciones.setAutoCreateGaps(true);
        gl_acciones.setHorizontalGroup(gl_acciones.createSequentialGroup()
                .addComponent(btnTodos)
                .addComponent(btnNinguno)
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(btnActualizar)
                .addComponent(btnGuardar));
        gl_acciones.setVerticalGroup(gl_acciones.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(btnTodos)
                .addComponent(btnNinguno)
                .addComponent(btnActualizar)
                .addComponent(btnGuardar));

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblInstrucciones)
                .addComponent(scrollPaneUsuarios, GroupLayout.DEFAULT_SIZE, 440, Short.MAX_VALUE)
                .addComponent(panelAcciones, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(lblEstado));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(lblInstrucciones)
                .addComponent(scrollPaneUsuarios, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addComponent(panelAcciones, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblEstado));
        habilitarAcciones(false);
    }

    private void recargar() {
        if (servicio == null || sesion == null || contribuyenteId == null) return;
        habilitarAcciones(false);
        lblEstado.setText("Cargando usuarios autorizados...");
        SwingWorker<List<UsuarioAccesoContribuyente>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<UsuarioAccesoContribuyente> doInBackground() {
                return servicio.listarUsuarios(sesion.id(), contribuyenteId);
            }

            @Override
            protected void done() {
                try {
                    registros = get();
                    modeloUsuarios.setRowCount(0);
                    for (UsuarioAccesoContribuyente u : registros) {
                        modeloUsuarios.addRow(new Object[] {
                                u.asignado(), u.login(), u.nombre(),
                                u.activo() ? "ACTIVO" : "INACTIVO"
                        });
                    }
                    lblEstado.setText(registros.size() + " usuario(s) disponible(s)");
                    habilitarAcciones(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    error(ex.getCause());
                }
            }
        };
        worker.execute();
    }

    private void seleccionarTodos(boolean seleccionado) {
        if (servicio == null) return;
        for (int i = 0; i < registros.size(); i++) {
            modeloUsuarios.setValueAt(seleccionado, i, 0);
        }
    }

    private void guardar() {
        if (servicio == null || sesion == null || contribuyenteId == null) return;
        if (tablaUsuarios.isEditing() && !tablaUsuarios.getCellEditor().stopCellEditing()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < registros.size(); i++) {
            if (Boolean.TRUE.equals(modeloUsuarios.getValueAt(i, 0))) {
                ids.add(registros.get(i).id());
            }
        }
        habilitarAcciones(false);
        lblEstado.setText("Guardando usuarios autorizados...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                servicio.guardarUsuarios(sesion.id(), contribuyenteId, ids);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    recargar();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    habilitarAcciones(true);
                } catch (ExecutionException ex) {
                    error(ex.getCause());
                    habilitarAcciones(true);
                }
            }
        };
        worker.execute();
    }

    private void habilitarAcciones(boolean habilitar) {
        boolean disponible = habilitar && servicio != null;
        tablaUsuarios.setEnabled(disponible);
        btnTodos.setEnabled(disponible);
        btnNinguno.setEnabled(disponible);
        btnGuardar.setEnabled(disponible);
        btnActualizar.setEnabled(disponible);
    }

    private void error(Throwable error) {
        lblEstado.setText("No fue posible completar la operación");
        JOptionPane.showMessageDialog(this,
                error == null ? "Error inesperado" : error.getMessage(),
                "Acceso por contribuyente", JOptionPane.ERROR_MESSAGE);
    }
}
