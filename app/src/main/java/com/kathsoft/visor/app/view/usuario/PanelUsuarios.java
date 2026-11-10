package com.kathsoft.visor.app.view.usuario;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.kathsoft.visor.app.model.dto.UsuarioListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.model.entity.UsuarioEstado;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import com.kathsoft.visor.app.security.Permisos;

/**
 * Vista principal del catálogo de usuarios, incrustada en el CardLayout de
 * Fr_Principal, siguiendo PanelClientes/PanelTipoCliente de Kath ERP.
 *
 * <p>Contiene botones de acción, JTable y controles de filtro, sin abrir
 * ventanas adicionales para listados. Solo el detalle de alta y modificación
 * utiliza un JDialog. Constructor sin dependencias para Eclipse WindowBuilder.</p>
 */
public class PanelUsuarios extends JPanel {

    private static final long serialVersionUID = 1L;

    private UsuariosService usuarios;
    private UsuarioSesion sesion;
    private Set<String> privilegios = Set.of();

    private boolean consultaActiva;
    private boolean recargaPendiente;

    private JPanel panelEtiquetaUsuarios;
    private JPanel panelCentralContenedor;
    private JPanel panelSuperiorBotones;
    private JPanel panelInferiorBusqueda;
    private JLabel lblTitulo;
    private JLabel lblBuscar;
    private JLabel lblFiltrarEstado;
    private JLabel lblResultado;
    private JButton btnAgregar;
    private JButton btnModificar;
    private JButton btnEstado;
    private JButton btnRol;
    private JButton btnContrasena;
    private JButton btnBuscar;
    private JButton btnLimpiar;
    private JButton btnActualizar;
    private JTextField txfBuscar;
    private JComboBox<String> cmbEstado;
    private JScrollPane scrollPaneUsuarios;
    private JTable tablaUsuarios;
    private DefaultTableModel modeloUsuarios;
    private TableRowSorter<DefaultTableModel> ordenacion;

    /**
     * Construye el panel para edición visual; no consulta MySQL ni Hibernate.
     */
    public PanelUsuarios() {
        inicializarComponentes();
    }

    /**
     * Inyecta los servicios después de construir el panel en el CardLayout.
     *
     * @param usuarios servicio transaccional del módulo
     * @param sesion cuenta autenticada
     */
    public void configurar(UsuariosService usuarios, UsuarioSesion sesion,
            Set<String> privilegios) {
        this.usuarios = usuarios;
        this.sesion = sesion;
        this.privilegios = privilegios == null ? Set.of() : Set.copyOf(privilegios);
        establecerOcupado(false);
    }

    private void inicializarComponentes() {
        panelEtiquetaUsuarios = new JPanel();
        panelEtiquetaUsuarios.setBackground(new Color(25, 25, 112));
        lblTitulo = new JLabel("Administración de usuarios");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 16f));
        lblTitulo.setForeground(Color.WHITE);
        panelEtiquetaUsuarios.add(lblTitulo);

        panelCentralContenedor = new JPanel();
        panelCentralContenedor.setBorder(new EmptyBorder(4, 6, 4, 6));

        panelSuperiorBotones = new JPanel();
        panelSuperiorBotones.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        btnAgregar = new JButton("Agregar");
        btnModificar = new JButton("Modificar");
        btnEstado = new JButton("Activar / inactivar");
        btnRol = new JButton("Cambiar rol");
        btnContrasena = new JButton("Restablecer clave");
        panelSuperiorBotones.add(btnAgregar);
        panelSuperiorBotones.add(btnModificar);
        panelSuperiorBotones.add(btnEstado);
        panelSuperiorBotones.add(btnRol);
        panelSuperiorBotones.add(btnContrasena);

        scrollPaneUsuarios = new JScrollPane();
        tablaUsuarios = new JTable();
        modeloUsuarios = new DefaultTableModel(
                new Object[] {"ID", "Nombre", "Usuario", "Correo", "Estado", "Rol"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columna) {
                return columna == 0 ? Long.class : String.class;
            }
        };
        tablaUsuarios.setModel(modeloUsuarios);
        tablaUsuarios.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tablaUsuarios.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tablaUsuarios.setRowHeight(25);
        tablaUsuarios.getColumnModel().getColumn(0).setPreferredWidth(65);
        tablaUsuarios.getColumnModel().getColumn(1).setPreferredWidth(195);
        tablaUsuarios.getColumnModel().getColumn(2).setPreferredWidth(145);
        tablaUsuarios.getColumnModel().getColumn(3).setPreferredWidth(215);
        tablaUsuarios.getColumnModel().getColumn(4).setPreferredWidth(115);
        tablaUsuarios.getColumnModel().getColumn(5).setPreferredWidth(110);
        ordenacion = new TableRowSorter<>(modeloUsuarios);
        tablaUsuarios.setRowSorter(ordenacion);
        scrollPaneUsuarios.setViewportView(tablaUsuarios);

        panelInferiorBusqueda = new JPanel();
        lblBuscar = new JLabel("Buscar");
        txfBuscar = new JTextField(12);
        lblFiltrarEstado = new JLabel("Estado");
        cmbEstado = new JComboBox<>(new String[] {"Todos", "ACTIVO", "INACTIVO", "BLOQUEADO"});
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
                    .addComponent(txfBuscar, GroupLayout.DEFAULT_SIZE, 135, Short.MAX_VALUE)
                    .addComponent(lblFiltrarEstado)
                    .addComponent(cmbEstado, 110, 110, 110)
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
                    .addComponent(lblFiltrarEstado)
                    .addComponent(cmbEstado, GroupLayout.PREFERRED_SIZE,
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
                .addComponent(scrollPaneUsuarios, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(panelInferiorBusqueda, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        gl_panelCentralContenedor.setVerticalGroup(
            gl_panelCentralContenedor.createSequentialGroup()
                .addComponent(panelSuperiorBotones, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(scrollPaneUsuarios, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                .addComponent(panelInferiorBusqueda, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));

        GroupLayout groupLayout = new GroupLayout(this);
        setLayout(groupLayout);
        groupLayout.setAutoCreateGaps(true);
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(panelEtiquetaUsuarios, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(panelCentralContenedor, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        groupLayout.setVerticalGroup(
            groupLayout.createSequentialGroup()
                .addComponent(panelEtiquetaUsuarios, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(panelCentralContenedor, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));

        btnAgregar.addActionListener(e -> abrirDetalle(null));
        btnModificar.addActionListener(e -> {
            UsuarioListado seleccionado = registroSeleccionado();
            if (seleccionado != null) {
                abrirDetalle(seleccionado);
            }
        });
        btnEstado.addActionListener(e -> cambiarEstado());
        btnRol.addActionListener(e -> cambiarRol());
        btnContrasena.addActionListener(e -> restablecerClave());
        btnBuscar.addActionListener(e -> filtrar());
        txfBuscar.addActionListener(e -> filtrar());
        cmbEstado.addActionListener(e -> filtrar());
        btnLimpiar.addActionListener(e -> {
            txfBuscar.setText("");
            cmbEstado.setSelectedIndex(0);
            filtrar();
        });
        btnActualizar.addActionListener(e -> recargar());
        configurar(null, null, Set.of());
    }

    /**
     * Actualiza las filas desde Hibernate fuera del hilo de interfaz.
     * Debe llamarse al entrar en la tarjeta desde el JMenuItem.
     */
    public void recargar() {
        if (usuarios == null || sesion == null
                || !privilegios.contains(Permisos.USUARIOS_CONSULTAR)) {
            return;
        }
        if (consultaActiva) {
            recargaPendiente = true;
            return;
        }
        consultaActiva = true;
        establecerOcupado(true);
        lblResultado.setText("Consultando usuarios...");
        SwingWorker<List<UsuarioListado>, Void> tarea = new SwingWorker<>() {
            @Override
            protected List<UsuarioListado> doInBackground() {
                return usuarios.listar(sesion.id());
            }

            @Override
            protected void done() {
                try {
                    List<UsuarioListado> registros = get();
                    modeloUsuarios.setRowCount(0);
                    for (UsuarioListado usuario : registros) {
                        modeloUsuarios.addRow(new Object[] {
                                usuario.id(), usuario.nombre(), usuario.login(), usuario.correo(),
                                usuario.estado(), usuario.rol()
                        });
                    }
                    filtrar();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblResultado.setText("Consulta interrumpida");
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
        String estado = (String) cmbEstado.getSelectedItem();
        List<RowFilter<DefaultTableModel, Integer>> filtros = new java.util.ArrayList<>();
        if (!texto.isEmpty()) {
            filtros.add(RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 1, 2, 3));
        }
        if (estado != null && !"Todos".equals(estado)) {
            filtros.add(RowFilter.regexFilter("^" + Pattern.quote(estado) + "$", 4));
        }
        ordenacion.setRowFilter(filtros.isEmpty() ? null : RowFilter.andFilter(filtros));
        lblResultado.setText(tablaUsuarios.getRowCount() + " de "
                + modeloUsuarios.getRowCount() + " usuario(s)");
    }

    private UsuarioListado registroSeleccionado() {
        int filaVista = tablaUsuarios.getSelectedRow();
        if (filaVista < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione una cuenta de la tabla.");
            return null;
        }
        int fila = tablaUsuarios.convertRowIndexToModel(filaVista);
        return new UsuarioListado(
                (Long) modeloUsuarios.getValueAt(fila, 0),
                (String) modeloUsuarios.getValueAt(fila, 1),
                (String) modeloUsuarios.getValueAt(fila, 2),
                (String) modeloUsuarios.getValueAt(fila, 3),
                (String) modeloUsuarios.getValueAt(fila, 4),
                (String) modeloUsuarios.getValueAt(fila, 5));
    }

    private Frame obtenerVentana() {
        Window ventana = SwingUtilities.getWindowAncestor(this);
        return ventana instanceof Frame ? (Frame) ventana : null;
    }

    private void abrirDetalle(UsuarioListado seleccionado) {
        if (usuarios == null || sesion == null) {
            return;
        }
        if (seleccionado != null) {
            new Fr_DatosUsuario(obtenerVentana(), usuarios, sesion, seleccionado,
                    this::recargar, List.of()).setVisible(true);
            return;
        }
        // Los roles pueden crecer desde RF-003: cargar opciones reales.
        establecerOcupado(true);
        SwingWorker<List<String>, Void> tarea = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                return usuarios.listarClavesRoles(sesion.id());
            }
            @Override
            protected void done() {
                try {
                    List<String> claves = get();
                    if (claves.isEmpty()) {
                        throw new IllegalStateException("No existen roles disponibles");
                    }
                    new Fr_DatosUsuario(obtenerVentana(), usuarios, sesion, null,
                            PanelUsuarios.this::recargar, claves).setVisible(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(PanelUsuarios.this, ex.getMessage(),
                            "Error de roles", JOptionPane.ERROR_MESSAGE);
                } finally {
                    establecerOcupado(false);
                }
            }
        };
        tarea.execute();
    }

    private void cambiarEstado() {
        UsuarioListado usuario = registroSeleccionado();
        if (usuario == null) {
            return;
        }
        UsuarioEstado nuevo = "ACTIVO".equals(usuario.estado())
                ? UsuarioEstado.INACTIVO : UsuarioEstado.ACTIVO;
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Cambiar el estado de " + usuario.login() + " a " + nuevo + "?",
                "Confirmar cambio", JOptionPane.YES_NO_OPTION);
        if (respuesta == JOptionPane.YES_OPTION) {
            ejecutarOperacion(() -> usuarios.cambiarEstado(sesion.id(), usuario.id(), nuevo));
        }
    }

    private void cambiarRol() {
        UsuarioListado usuario = registroSeleccionado();
        if (usuario == null || usuarios == null || sesion == null) {
            return;
        }
        establecerOcupado(true);
        SwingWorker<List<String>, Void> tarea = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                return usuarios.listarClavesRoles(sesion.id());
            }
            @Override
            protected void done() {
                boolean operacionIniciada = false;
                try {
                    List<String> disponibles = get();
                    String rol = (String) JOptionPane.showInputDialog(
                            PanelUsuarios.this,
                            "Seleccione el rol de " + usuario.login(),
                            "Cambiar rol", JOptionPane.QUESTION_MESSAGE, null,
                            disponibles.toArray(new String[0]),
                            disponibles.isEmpty() ? null : disponibles.get(0));
                    if (rol != null) {
                        ejecutarOperacion(() ->
                                usuarios.cambiarRol(sesion.id(), usuario.id(), rol));
                        operacionIniciada = true;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                } finally {
                    if (!operacionIniciada) {
                        establecerOcupado(false);
                    }
                }
            }
        };
        tarea.execute();
    }

    private void restablecerClave() {
        UsuarioListado usuario = registroSeleccionado();
        if (usuario == null) {
            return;
        }
        JPasswordField campoClave = new JPasswordField(22);
        int respuesta = JOptionPane.showConfirmDialog(this, campoClave,
                "Nueva contraseña (mínimo 12 caracteres)", JOptionPane.OK_CANCEL_OPTION);
        if (respuesta != JOptionPane.OK_OPTION) {
            campoClave.setText("");
            return;
        }
        char[] clave = campoClave.getPassword();
        campoClave.setText("");
        ejecutarOperacion(() -> usuarios.cambiarContrasena(sesion.id(), usuario.id(), clave),
                () -> Arrays.fill(clave, '\0'));
    }

    private void ejecutarOperacion(Runnable operacion) {
        ejecutarOperacion(operacion, () -> { });
    }

    private void ejecutarOperacion(Runnable operacion, Runnable despues) {
        establecerOcupado(true);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                operacion.run();
                return null;
            }
            @Override
            protected void done() {
                boolean actualizarEnCurso = false;
                try {
                    get();
                    recargar();
                    actualizarEnCurso = true;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                } finally {
                    despues.run();
                    if (!actualizarEnCurso) {
                        establecerOcupado(false);
                    }
                }
            }
        };
        worker.execute();
    }

    private void establecerOcupado(boolean ocupado) {
        boolean disponible = !ocupado && sesion != null;
        btnAgregar.setEnabled(disponible && privilegios.contains(Permisos.USUARIOS_CREAR));
        btnModificar.setEnabled(disponible && privilegios.contains(Permisos.USUARIOS_MODIFICAR));
        btnEstado.setEnabled(disponible && privilegios.contains(Permisos.USUARIOS_CAMBIAR_ESTADO));
        btnRol.setEnabled(disponible && privilegios.contains(Permisos.USUARIOS_ASIGNAR_ROL));
        btnContrasena.setEnabled(disponible && privilegios.contains(Permisos.USUARIOS_RESTABLECER_CLAVE));
        btnActualizar.setEnabled(disponible && privilegios.contains(Permisos.USUARIOS_CONSULTAR));
    }

    private void mostrarError(ExecutionException ex) {
        Throwable causa = ex.getCause();
        lblResultado.setText("No fue posible completar la operación");
        JOptionPane.showMessageDialog(this, causa == null ? "Error inesperado" : causa.getMessage(),
                "Error en administración de usuarios", JOptionPane.ERROR_MESSAGE);
    }
}
