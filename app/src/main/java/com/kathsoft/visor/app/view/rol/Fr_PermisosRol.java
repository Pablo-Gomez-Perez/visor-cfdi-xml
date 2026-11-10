package com.kathsoft.visor.app.view.rol;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Font;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import com.kathsoft.visor.app.model.dto.MatrizPermisosRol;
import com.kathsoft.visor.app.model.dto.PermisoSeleccionable;
import com.kathsoft.visor.app.model.dto.RolListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.rol.RolesService;

/**
 * Matriz de privilegios por rol en un único JDialog de detalle.
 *
 * <p>La columna `Permitir` del JTable es de tipo Boolean y Swing la
 * renderiza como JCheckBox. La columna se edita marcando/desmarcando
 * cada operación; los privilegios indispensables de ADMIN se bloquean.
 * GroupLayout y constructor vacío son compatibles con WindowBuilder.</p>
 */
public class Fr_PermisosRol extends JDialog {

    private static final long serialVersionUID = 1L;

    private RolesService roles;
    private UsuarioSesion sesion;
    private RolListado registro;
    private Runnable alGuardar;

    private JPanel contentPane;
    private JPanel panelAcciones;
    private JLabel lblTitulo;
    private JLabel lblRol;
    private JLabel lblInfo;
    private JLabel lblEstado;
    private JScrollPane scrollPanePermisos;
    private JTable tablaPermisos;
    private DefaultTableModel modeloPermisos;
    private JButton btnMarcar;
    private JButton btnDesmarcar;
    private JButton btnGuardar;
    private JButton btnCancelar;

    private List<PermisoSeleccionable> disponibles = List.of();

    /** Constructor independiente de servicios y MySQL para WindowBuilder. */
    public Fr_PermisosRol() {
        this(null, null, null, null, null);
    }

    /**
     * Prepara la edición de privilegios de un rol concreto.
     *
     * @param dueno ventana principal
     * @param roles operaciones del módulo
     * @param sesion administrador de la operación
     * @param registro rol seleccionado
     * @param alGuardar callback para refrescar el catálogo
     */
    public Fr_PermisosRol(Frame dueno, RolesService roles, UsuarioSesion sesion,
            RolListado registro, Runnable alGuardar) {
        super(dueno, "Privilegios del rol", ModalityType.APPLICATION_MODAL);
        this.roles = roles;
        this.sesion = sesion;
        this.registro = registro;
        this.alGuardar = alGuardar;
        inicializarComponentes();
        if (registro != null) {
            lblRol.setText("Rol: " + registro.clave() + " — " + registro.descripcion());
            SwingUtilities.invokeLater(this::cargarPermisos);
        }
    }

    private void inicializarComponentes() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 755, 520);
        setMinimumSize(new Dimension(605, 410));
        setLocationRelativeTo(getOwner());

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(12, 14, 12, 14));
        setContentPane(contentPane);
        lblTitulo = new JLabel("Configurar privilegios");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 20f));
        lblRol = new JLabel("Rol sin seleccionar (vista previa)");
        lblInfo = new JLabel("Marque las casillas de las acciones permitidas. Se guardarán como una transacción.");
        lblEstado = new JLabel("Sin privilegios cargados");

        scrollPanePermisos = new JScrollPane();
        tablaPermisos = new JTable();
        modeloPermisos = new DefaultTableModel(
                new Object[] {"Permitir", "Recurso", "Acción", "Descripción"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public Class<?> getColumnClass(int columna) {
                return columna == 0 ? Boolean.class : String.class;
            }
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return columna == 0 && fila < disponibles.size()
                        && !disponibles.get(fila).obligatorio();
            }
        };
        tablaPermisos.setModel(modeloPermisos);
        tablaPermisos.setRowHeight(27);
        tablaPermisos.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tablaPermisos.getColumnModel().getColumn(0).setPreferredWidth(75);
        tablaPermisos.getColumnModel().getColumn(1).setPreferredWidth(105);
        tablaPermisos.getColumnModel().getColumn(2).setPreferredWidth(170);
        tablaPermisos.getColumnModel().getColumn(3).setPreferredWidth(330);
        scrollPanePermisos.setViewportView(tablaPermisos);

        panelAcciones = new JPanel();
        btnMarcar = new JButton("Marcar todos");
        btnDesmarcar = new JButton("Desmarcar todos");
        btnGuardar = new JButton("Guardar privilegios");
        btnCancelar = new JButton("Cancelar");
        btnMarcar.addActionListener(e -> cambiarTodos(true));
        btnDesmarcar.addActionListener(e -> cambiarTodos(false));
        btnGuardar.addActionListener(e -> guardar());
        btnCancelar.addActionListener(e -> dispose());

        GroupLayout gl_panelAcciones = new GroupLayout(panelAcciones);
        panelAcciones.setLayout(gl_panelAcciones);
        gl_panelAcciones.setAutoCreateGaps(true);
        gl_panelAcciones.setHorizontalGroup(gl_panelAcciones.createSequentialGroup()
            .addComponent(btnMarcar)
            .addComponent(btnDesmarcar)
            .addGap(0, 0, Short.MAX_VALUE)
            .addComponent(btnCancelar)
            .addComponent(btnGuardar));
        gl_panelAcciones.setVerticalGroup(gl_panelAcciones.createParallelGroup(
                GroupLayout.Alignment.BASELINE)
            .addComponent(btnMarcar)
            .addComponent(btnDesmarcar)
            .addComponent(btnCancelar)
            .addComponent(btnGuardar));

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblTitulo)
            .addComponent(lblRol)
            .addComponent(lblInfo)
            .addComponent(scrollPanePermisos, GroupLayout.DEFAULT_SIZE, 555, Short.MAX_VALUE)
            .addComponent(lblEstado)
            .addComponent(panelAcciones, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblTitulo)
            .addComponent(lblRol)
            .addComponent(lblInfo)
            .addComponent(scrollPanePermisos, GroupLayout.DEFAULT_SIZE, 240, Short.MAX_VALUE)
            .addComponent(lblEstado)
            .addComponent(panelAcciones, GroupLayout.PREFERRED_SIZE,
                    GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));

        habilitarAcciones(false);
        getRootPane().setDefaultButton(btnGuardar);
    }

    private void cargarPermisos() {
        if (roles == null || sesion == null || registro == null) {
            return;
        }
        lblEstado.setText("Consultando privilegios...");
        habilitarAcciones(false);
        SwingWorker<MatrizPermisosRol, Void> tarea = new SwingWorker<>() {
            @Override
            protected MatrizPermisosRol doInBackground() {
                return roles.cargarMatriz(sesion.id(), registro.id());
            }
            @Override
            protected void done() {
                try {
                    MatrizPermisosRol matriz = get();
                    disponibles = matriz.permisos();
                    modeloPermisos.setRowCount(0);
                    for (PermisoSeleccionable p : disponibles) {
                        modeloPermisos.addRow(new Object[] {
                                p.seleccionado(), p.recurso(), p.accion(), p.descripcion()
                        });
                    }
                    lblEstado.setText(disponibles.size() + " privilegios disponibles. "
                            + "El permiso de recuperación de ADMIN es obligatorio.");
                    habilitarAcciones(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                }
            }
        };
        tarea.execute();
    }

    private void cambiarTodos(boolean estado) {
        for (int i = 0; i < disponibles.size(); i++) {
            if (!disponibles.get(i).obligatorio()) {
                modeloPermisos.setValueAt(estado, i, 0);
            }
        }
    }

    private void guardar() {
        if (roles == null || sesion == null || registro == null) {
            return;
        }
        // Confirma la edición de la celda activa antes de recoger los checks.
        if (tablaPermisos.isEditing() && !tablaPermisos.getCellEditor().stopCellEditing()) {
            return;
        }
        Set<Short> ids = new HashSet<>();
        for (int i = 0; i < disponibles.size(); i++) {
            if (Boolean.TRUE.equals(modeloPermisos.getValueAt(i, 0))) {
                ids.add(disponibles.get(i).id());
            }
        }
        habilitarAcciones(false);
        lblEstado.setText("Guardando permisos y auditoría...");
        SwingWorker<Void, Void> tarea = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                roles.guardarPermisos(sesion.id(), registro.id(), ids);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    if (alGuardar != null) {
                        alGuardar.run();
                    }
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                    habilitarAcciones(true);
                }
            }
        };
        tarea.execute();
    }

    private void habilitarAcciones(boolean habilitar) {
        tablaPermisos.setEnabled(habilitar);
        btnMarcar.setEnabled(habilitar);
        btnDesmarcar.setEnabled(habilitar);
        btnGuardar.setEnabled(habilitar);
    }

    private void mostrarError(ExecutionException ex) {
        Throwable causa = ex.getCause();
        lblEstado.setText("No se completó la operación");
        JOptionPane.showMessageDialog(this,
                causa == null ? "Error inesperado" : causa.getMessage(),
                "Error de privilegios", JOptionPane.ERROR_MESSAGE);
    }
}
