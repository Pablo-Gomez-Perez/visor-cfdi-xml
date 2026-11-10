package com.kathsoft.visor.app.view.rol;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.kathsoft.visor.app.model.dto.RolListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.rol.RolesService;

/**
 * Panel de listado de roles dentro del CardLayout de Fr_Principal.
 *
 * <p>Replica la estructura de catálogos de Kath ERP: cabecera, barra de
 * acciones, JTable central y filtros inferiores. El constructor sin
 * parámetros crea exclusivamente Swing para Eclipse WindowBuilder.</p>
 */
public class PanelRoles extends JPanel {

    private static final long serialVersionUID = 1L;

    private RolesService roles;
    private UsuarioSesion sesion;
    private Set<String> privilegios = Set.of();
    private boolean consultaActiva;
    private boolean recargaPendiente;

    private JPanel panelEtiquetaRoles;
    private JPanel panelCentralContenedor;
    private JPanel panelSuperiorBotones;
    private JPanel panelInferiorBusqueda;
    private JLabel lblTitulo;
    private JLabel lblBuscar;
    private JLabel lblResultado;
    private JButton btnAgregar;
    private JButton btnModificar;
    private JButton btnPermisos;
    private JButton btnEliminar;
    private JButton btnBuscar;
    private JButton btnLimpiar;
    private JButton btnActualizar;
    private JTextField txfBuscar;
    private JScrollPane scrollPaneRoles;
    private JTable tablaRoles;
    private DefaultTableModel modeloRoles;
    private TableRowSorter<DefaultTableModel> ordenacion;

    /** Constructor vacío: ninguna consulta MySQL, Hibernate o filesystem. */
    public PanelRoles() {
        inicializarComponentes();
    }

    /**
     * Configura el panel con la sesión y el conjunto de permisos fresco.
     *
     * @param roles servicio de catálogo y matriz
     * @param sesion usuario de la aplicación
     * @param privilegios permisos efectivos consultados de MySQL
     */
    public void configurar(RolesService roles, UsuarioSesion sesion, Set<String> privilegios) {
        this.roles = roles;
        this.sesion = sesion;
        this.privilegios = privilegios == null ? Set.of() : Set.copyOf(privilegios);
        establecerOcupado(false);
    }

    private void inicializarComponentes() {
        panelEtiquetaRoles = new JPanel();
        panelEtiquetaRoles.setBackground(new Color(25, 25, 112));
        lblTitulo = new JLabel("Roles y permisos");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 16f));
        lblTitulo.setForeground(Color.WHITE);
        panelEtiquetaRoles.add(lblTitulo);

        panelCentralContenedor = new JPanel();
        panelCentralContenedor.setBorder(new EmptyBorder(4, 6, 4, 6));
        panelSuperiorBotones = new JPanel();
        panelSuperiorBotones.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        btnAgregar = new JButton("Crear rol");
        btnModificar = new JButton("Modificar");
        btnPermisos = new JButton("Configurar privilegios");
        btnEliminar = new JButton("Eliminar");
        panelSuperiorBotones.add(btnAgregar);
        panelSuperiorBotones.add(btnModificar);
        panelSuperiorBotones.add(btnPermisos);
        panelSuperiorBotones.add(btnEliminar);

        scrollPaneRoles = new JScrollPane();
        tablaRoles = new JTable();
        modeloRoles = new DefaultTableModel(
                new Object[] {"ID", "Clave", "Descripción", "Privilegios", "Usuarios"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
            @Override
            public Class<?> getColumnClass(int columna) {
                return switch (columna) {
                    case 0 -> Short.class;
                    case 3 -> Integer.class;
                    case 4 -> Long.class;
                    default -> String.class;
                };
            }
        };
        tablaRoles.setModel(modeloRoles);
        tablaRoles.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tablaRoles.setRowHeight(27);
        tablaRoles.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tablaRoles.getColumnModel().getColumn(0).setPreferredWidth(55);
        tablaRoles.getColumnModel().getColumn(1).setPreferredWidth(155);
        tablaRoles.getColumnModel().getColumn(2).setPreferredWidth(360);
        tablaRoles.getColumnModel().getColumn(3).setPreferredWidth(100);
        tablaRoles.getColumnModel().getColumn(4).setPreferredWidth(85);
        ordenacion = new TableRowSorter<>(modeloRoles);
        tablaRoles.setRowSorter(ordenacion);
        scrollPaneRoles.setViewportView(tablaRoles);

        panelInferiorBusqueda = new JPanel();
        lblBuscar = new JLabel("Buscar");
        txfBuscar = new JTextField(14);
        btnBuscar = new JButton("Buscar");
        btnLimpiar = new JButton("Limpiar");
        btnActualizar = new JButton("Actualizar");
        lblResultado = new JLabel("Sin datos cargados");

        GroupLayout gl_panelInferiorBusqueda = new GroupLayout(panelInferiorBusqueda);
        panelInferiorBusqueda.setLayout(gl_panelInferiorBusqueda);
        gl_panelInferiorBusqueda.setAutoCreateGaps(true);
        gl_panelInferiorBusqueda.setAutoCreateContainerGaps(true);
        gl_panelInferiorBusqueda.setHorizontalGroup(
            gl_panelInferiorBusqueda.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(gl_panelInferiorBusqueda.createSequentialGroup()
                    .addComponent(lblBuscar)
                    .addComponent(txfBuscar, GroupLayout.DEFAULT_SIZE, 190, Short.MAX_VALUE)
                    .addComponent(btnBuscar)
                    .addComponent(btnLimpiar)
                    .addComponent(btnActualizar))
                .addComponent(lblResultado));
        gl_panelInferiorBusqueda.setVerticalGroup(
            gl_panelInferiorBusqueda.createSequentialGroup()
                .addGroup(gl_panelInferiorBusqueda.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblBuscar)
                    .addComponent(txfBuscar, GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBuscar)
                    .addComponent(btnLimpiar)
                    .addComponent(btnActualizar))
                .addComponent(lblResultado));

        GroupLayout gl_panelCentralContenedor = new GroupLayout(panelCentralContenedor);
        panelCentralContenedor.setLayout(gl_panelCentralContenedor);
        gl_panelCentralContenedor.setAutoCreateGaps(true);
        gl_panelCentralContenedor.setHorizontalGroup(
            gl_panelCentralContenedor.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(panelSuperiorBotones, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(scrollPaneRoles, GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE)
                .addComponent(panelInferiorBusqueda, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        gl_panelCentralContenedor.setVerticalGroup(
            gl_panelCentralContenedor.createSequentialGroup()
                .addComponent(panelSuperiorBotones, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(scrollPaneRoles, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addComponent(panelInferiorBusqueda, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));

        GroupLayout groupLayout = new GroupLayout(this);
        setLayout(groupLayout);
        groupLayout.setAutoCreateGaps(true);
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(panelEtiquetaRoles, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(panelCentralContenedor, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        groupLayout.setVerticalGroup(
            groupLayout.createSequentialGroup()
                .addComponent(panelEtiquetaRoles, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(panelCentralContenedor, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));

        btnAgregar.addActionListener(e -> abrirDatos(null));
        btnModificar.addActionListener(e -> {
            RolListado rol = seleccionado();
            if (rol != null) {
                abrirDatos(rol);
            }
        });
        btnPermisos.addActionListener(e -> {
            RolListado rol = seleccionado();
            if (rol != null && roles != null && sesion != null) {
                new Fr_PermisosRol(ventanaPrincipal(), roles, sesion,
                        rol, this::recargar).setVisible(true);
            }
        });
        btnEliminar.addActionListener(e -> eliminar());
        btnActualizar.addActionListener(e -> recargar());
        btnBuscar.addActionListener(e -> filtrar());
        btnLimpiar.addActionListener(e -> {
            txfBuscar.setText("");
            filtrar();
        });
        txfBuscar.addActionListener(e -> filtrar());
        configurar(null, null, Set.of());
    }

    /**
     * Lee roles mediante SwingWorker al activar la tarjeta central.
     */
    public void recargar() {
        if (roles == null || sesion == null || privilegios.stream()
                .noneMatch(p -> p.startsWith("ROLES."))) {
            return;
        }
        if (consultaActiva) {
            recargaPendiente = true;
            return;
        }
        consultaActiva = true;
        establecerOcupado(true);
        lblResultado.setText("Consultando roles...");
        SwingWorker<List<RolListado>, Void> tarea = new SwingWorker<>() {
            @Override
            protected List<RolListado> doInBackground() {
                return roles.listar(sesion.id());
            }
            @Override
            protected void done() {
                try {
                    List<RolListado> registros = get();
                    modeloRoles.setRowCount(0);
                    for (RolListado rol : registros) {
                        modeloRoles.addRow(new Object[] {rol.id(), rol.clave(),
                                rol.descripcion(), rol.permisos(), rol.usuarios()});
                    }
                    filtrar();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                } finally {
                    consultaActiva = false;
                    if (recargaPendiente) {
                        recargaPendiente = false;
                        recargar();
                    } else {
                        establecerOcupado(false);
                    }
                }
            }
        };
        tarea.execute();
    }

    private void filtrar() {
        String texto = txfBuscar.getText().strip();
        ordenacion.setRowFilter(texto.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 1, 2));
        lblResultado.setText(tablaRoles.getRowCount() + " de "
                + modeloRoles.getRowCount() + " rol(es)");
    }

    private RolListado seleccionado() {
        int vista = tablaRoles.getSelectedRow();
        if (vista < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un rol de la tabla.");
            return null;
        }
        int fila = tablaRoles.convertRowIndexToModel(vista);
        return new RolListado(
                (Short) modeloRoles.getValueAt(fila, 0),
                (String) modeloRoles.getValueAt(fila, 1),
                (String) modeloRoles.getValueAt(fila, 2),
                (Integer) modeloRoles.getValueAt(fila, 3),
                (Long) modeloRoles.getValueAt(fila, 4));
    }

    private Frame ventanaPrincipal() {
        Window ventana = SwingUtilities.getWindowAncestor(this);
        return ventana instanceof Frame ? (Frame) ventana : null;
    }

    private void abrirDatos(RolListado registro) {
        if (roles != null && sesion != null) {
            new Fr_DatosRol(ventanaPrincipal(), roles, sesion,
                    registro, this::recargar).setVisible(true);
        }
    }

    private void eliminar() {
        RolListado rol = seleccionado();
        if (rol == null || roles == null || sesion == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "¿Eliminar el rol " + rol.clave() + "? Solo es posible si no tiene usuarios.",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        establecerOcupado(true);
        SwingWorker<Void, Void> tarea = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                roles.eliminar(sesion.id(), rol.id());
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    recargar();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    establecerOcupado(false);
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                    establecerOcupado(false);
                }
            }
        };
        tarea.execute();
    }

    private void establecerOcupado(boolean ocupado) {
        boolean disponible = !ocupado && sesion != null;
        btnAgregar.setEnabled(disponible && privilegios.contains(Permisos.ROLES_CREAR));
        btnModificar.setEnabled(disponible && privilegios.contains(Permisos.ROLES_MODIFICAR));
        btnPermisos.setEnabled(disponible && privilegios.contains(Permisos.ROLES_CONFIGURAR_PERMISOS));
        btnEliminar.setEnabled(disponible && privilegios.contains(Permisos.ROLES_ELIMINAR));
        btnActualizar.setEnabled(disponible && privilegios.stream().anyMatch(p -> p.startsWith("ROLES.")));
    }

    private void mostrarError(ExecutionException ex) {
        Throwable causa = ex.getCause();
        lblResultado.setText("La operación no se completó");
        JOptionPane.showMessageDialog(this,
                causa == null ? "Error inesperado" : causa.getMessage(),
                "Error al administrar roles", JOptionPane.ERROR_MESSAGE);
    }
}
