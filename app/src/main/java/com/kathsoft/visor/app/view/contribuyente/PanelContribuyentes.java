package com.kathsoft.visor.app.view.contribuyente;

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
import javax.swing.JComboBox;
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

import com.kathsoft.visor.app.model.dto.ContribuyenteListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.contribuyente.ContribuyentesService;

/**
 * RF-004: catálogo de contribuyentes en una tarjeta del CardLayout principal.
 *
 * <p>Patrón Kath ERP: encabezado, acciones superiores, JTable central y
 * filtros inferiores. No genera JFrames adicionales. Las operaciones de
 * persistencia utilizan SwingWorker fuera del EDT. Constructor público sin
 * dependencias para Eclipse WindowBuilder.</p>
 */
public class PanelContribuyentes extends JPanel {

    private static final long serialVersionUID = 1L;

    private ContribuyentesService servicio;
    private UsuarioSesion sesion;
    private Set<String> privilegios = Set.of();
    private boolean consultaActiva;
    private boolean recargaPendiente;

    private JPanel panelEtiquetaContribuyentes;
    private JPanel panelCentralContenedor;
    private JPanel panelSuperiorBotones;
    private JPanel panelInferiorBusqueda;
    private JLabel lblTitulo;
    private JLabel lblBuscar;
    private JLabel lblEstado;
    private JLabel lblResultado;
    private JButton btnAgregar;
    private JButton btnDetalle;
    private JButton btnEstado;
    private JButton btnRegimenes;
    private JButton btnAccesos;
    private JButton btnBuscar;
    private JButton btnLimpiar;
    private JButton btnActualizar;
    private JTextField txfBuscar;
    private JComboBox<String> cmbEstado;
    private JScrollPane scrollPaneContribuyentes;
    private JTable tablaContribuyentes;
    private DefaultTableModel modeloContribuyentes;
    private TableRowSorter<DefaultTableModel> ordenacion;

    /** Diseña el panel sin abrir conexión MySQL o usar archivos. */
    public PanelContribuyentes() {
        inicializarComponentes();
    }

    /**
     * Inyecta los servicios y privilegios actuales, consultados por
     * Fr_Principal, y actualiza los botones permitidos.
     *
     * @param servicio operaciones RF-004
     * @param sesion cuenta autenticada
     * @param privilegios permisos efectivos
     */
    public void configurar(ContribuyentesService servicio, UsuarioSesion sesion,
            Set<String> privilegios) {
        this.servicio = servicio;
        this.sesion = sesion;
        this.privilegios = privilegios == null ? Set.of() : Set.copyOf(privilegios);
        if (!this.privilegios.contains(Permisos.CONTRIBUYENTES_CONSULTAR)) {
            // Al revocar consulta se retiran los datos que permanecían en
            // memoria del JTable, sin esperar a otra navegación.
            modeloContribuyentes.setRowCount(0);
            lblResultado.setText("Sin permiso de consulta; puede crear si tiene autorización.");
        }
        establecerOcupado(false);
    }

    private void inicializarComponentes() {
        panelEtiquetaContribuyentes = new JPanel();
        panelEtiquetaContribuyentes.setBackground(new Color(25, 25, 112));
        lblTitulo = new JLabel("Administración de contribuyentes");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 16f));
        lblTitulo.setForeground(Color.WHITE);
        panelEtiquetaContribuyentes.add(lblTitulo);

        panelCentralContenedor = new JPanel();
        panelCentralContenedor.setBorder(new EmptyBorder(4, 6, 4, 6));
        panelSuperiorBotones = new JPanel();
        panelSuperiorBotones.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));

        btnAgregar = new JButton("Agregar");
        btnDetalle = new JButton("Detalle / Editar");
        btnEstado = new JButton("Activar / inactivar");
        btnRegimenes = new JButton("Regímenes");
        btnAccesos = new JButton("Usuarios autorizados");
        panelSuperiorBotones.add(btnAgregar);
        panelSuperiorBotones.add(btnDetalle);
        panelSuperiorBotones.add(btnEstado);
        panelSuperiorBotones.add(btnRegimenes);
        panelSuperiorBotones.add(btnAccesos);

        scrollPaneContribuyentes = new JScrollPane();
        tablaContribuyentes = new JTable();
        modeloContribuyentes = new DefaultTableModel(
                new Object[] {"ID", "RFC", "Nombre / razón social", "CP fiscal",
                        "Correo", "Estado"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int fila, int columna) { return false; }

            @Override
            public Class<?> getColumnClass(int columna) {
                return columna == 0 ? Long.class : String.class;
            }
        };
        tablaContribuyentes.setModel(modeloContribuyentes);
        tablaContribuyentes.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tablaContribuyentes.setRowHeight(25);
        tablaContribuyentes.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tablaContribuyentes.getColumnModel().getColumn(0).setPreferredWidth(60);
        tablaContribuyentes.getColumnModel().getColumn(1).setPreferredWidth(125);
        tablaContribuyentes.getColumnModel().getColumn(2).setPreferredWidth(260);
        tablaContribuyentes.getColumnModel().getColumn(3).setPreferredWidth(85);
        tablaContribuyentes.getColumnModel().getColumn(4).setPreferredWidth(215);
        tablaContribuyentes.getColumnModel().getColumn(5).setPreferredWidth(95);
        ordenacion = new TableRowSorter<>(modeloContribuyentes);
        tablaContribuyentes.setRowSorter(ordenacion);
        scrollPaneContribuyentes.setViewportView(tablaContribuyentes);

        panelInferiorBusqueda = new JPanel();
        lblBuscar = new JLabel("Buscar");
        txfBuscar = new JTextField(12);
        lblEstado = new JLabel("Estado");
        cmbEstado = new JComboBox<>(new String[] {"Todos", "ACTIVO", "INACTIVO"});
        btnBuscar = new JButton("Buscar");
        btnLimpiar = new JButton("Limpiar");
        btnActualizar = new JButton("Actualizar");
        lblResultado = new JLabel("Sin registros cargados");

        GroupLayout gl_panelInferiorBusqueda = new GroupLayout(panelInferiorBusqueda);
        panelInferiorBusqueda.setLayout(gl_panelInferiorBusqueda);
        gl_panelInferiorBusqueda.setAutoCreateGaps(true);
        gl_panelInferiorBusqueda.setAutoCreateContainerGaps(true);
        gl_panelInferiorBusqueda.setHorizontalGroup(
            gl_panelInferiorBusqueda.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(gl_panelInferiorBusqueda.createSequentialGroup()
                    .addComponent(lblBuscar)
                    .addComponent(txfBuscar, GroupLayout.DEFAULT_SIZE, 120, Short.MAX_VALUE)
                    .addComponent(lblEstado)
                    .addComponent(cmbEstado, 105, 105, 105)
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
                    .addComponent(lblEstado)
                    .addComponent(cmbEstado, GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBuscar)
                    .addComponent(btnLimpiar)
                    .addComponent(btnActualizar))
                .addComponent(lblResultado));

        GroupLayout gl_central = new GroupLayout(panelCentralContenedor);
        panelCentralContenedor.setLayout(gl_central);
        gl_central.setAutoCreateGaps(true);
        gl_central.setHorizontalGroup(gl_central.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(panelSuperiorBotones, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(scrollPaneContribuyentes, GroupLayout.DEFAULT_SIZE, 360, Short.MAX_VALUE)
            .addComponent(panelInferiorBusqueda, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        gl_central.setVerticalGroup(gl_central.createSequentialGroup()
            .addComponent(panelSuperiorBotones, GroupLayout.PREFERRED_SIZE,
                    GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(scrollPaneContribuyentes, GroupLayout.DEFAULT_SIZE, 235, Short.MAX_VALUE)
            .addComponent(panelInferiorBusqueda, GroupLayout.PREFERRED_SIZE,
                    GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(panelEtiquetaContribuyentes, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(panelCentralContenedor, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(panelEtiquetaContribuyentes, GroupLayout.PREFERRED_SIZE,
                    GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(panelCentralContenedor, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));

        btnAgregar.addActionListener(e -> abrirDetalle(null, 0));
        btnDetalle.addActionListener(e -> {
            ContribuyenteListado c = seleccionado();
            if (c != null) abrirDetalle(c, 0);
        });
        btnRegimenes.addActionListener(e -> {
            ContribuyenteListado c = seleccionado();
            if (c != null) abrirDetalle(c, 1);
        });
        btnAccesos.addActionListener(e -> {
            ContribuyenteListado c = seleccionado();
            if (c != null) abrirDetalle(c, 2);
        });
        btnEstado.addActionListener(e -> cambiarEstado());
        btnActualizar.addActionListener(e -> recargar());
        btnBuscar.addActionListener(e -> filtrar());
        txfBuscar.addActionListener(e -> filtrar());
        cmbEstado.addActionListener(e -> filtrar());
        btnLimpiar.addActionListener(e -> {
            txfBuscar.setText("");
            cmbEstado.setSelectedIndex(0);
            filtrar();
        });
        configurar(null, null, Set.of());
    }

    /**
     * Consulta la lista de contribuyentes permitidos sin bloquear Swing.
     */
    public void recargar() {
        if (servicio == null || sesion == null
                || !privilegios.contains(Permisos.CONTRIBUYENTES_CONSULTAR)) {
            return;
        }
        if (consultaActiva) {
            recargaPendiente = true;
            return;
        }
        consultaActiva = true;
        establecerOcupado(true);
        lblResultado.setText("Consultando contribuyentes...");
        SwingWorker<List<ContribuyenteListado>, Void> tarea = new SwingWorker<>() {
            @Override
            protected List<ContribuyenteListado> doInBackground() {
                return servicio.listar(sesion.id());
            }
            @Override
            protected void done() {
                try {
                    List<ContribuyenteListado> datos = get();
                    modeloContribuyentes.setRowCount(0);
                    for (ContribuyenteListado c : datos) {
                        modeloContribuyentes.addRow(new Object[] {
                                c.id(), c.rfc(), c.nombreRazonSocial(),
                                c.codigoPostalFiscal(), c.correoContacto(),
                                c.activo() ? "ACTIVO" : "INACTIVO"
                        });
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
        String estado = (String) cmbEstado.getSelectedItem();
        List<RowFilter<DefaultTableModel, Integer>> filtros = new java.util.ArrayList<>();
        if (!texto.isEmpty()) {
            filtros.add(RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 1, 2, 3, 4));
        }
        if (estado != null && !"Todos".equals(estado)) {
            filtros.add(RowFilter.regexFilter("^" + Pattern.quote(estado) + "$", 5));
        }
        ordenacion.setRowFilter(filtros.isEmpty() ? null : RowFilter.andFilter(filtros));
        lblResultado.setText(tablaContribuyentes.getRowCount() + " de "
                + modeloContribuyentes.getRowCount() + " contribuyente(s)");
    }

    private ContribuyenteListado seleccionado() {
        int vista = tablaContribuyentes.getSelectedRow();
        if (vista < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un contribuyente.");
            return null;
        }
        int fila = tablaContribuyentes.convertRowIndexToModel(vista);
        return new ContribuyenteListado(
                (Long) modeloContribuyentes.getValueAt(fila, 0),
                (String) modeloContribuyentes.getValueAt(fila, 1),
                (String) modeloContribuyentes.getValueAt(fila, 2),
                (String) modeloContribuyentes.getValueAt(fila, 3),
                (String) modeloContribuyentes.getValueAt(fila, 4),
                "ACTIVO".equals(modeloContribuyentes.getValueAt(fila, 5)));
    }

    private Frame ventanaPrincipal() {
        Window w = SwingUtilities.getWindowAncestor(this);
        return w instanceof Frame ? (Frame) w : null;
    }

    private void abrirDetalle(ContribuyenteListado c, int pestana) {
        if (servicio == null || sesion == null) return;
        new Fr_DatosContribuyente(ventanaPrincipal(), servicio, sesion, c,
                privilegios, this::recargar, pestana).setVisible(true);
    }

    private void cambiarEstado() {
        ContribuyenteListado c = seleccionado();
        if (c == null || servicio == null || sesion == null) return;
        boolean nuevo = !c.activo();
        String mensaje = nuevo ? "activar" : "desactivar";
        if (JOptionPane.showConfirmDialog(this,
                "¿Desea " + mensaje + " al contribuyente " + c.rfc() + "?",
                "Confirmar estado", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        establecerOcupado(true);
        SwingWorker<Void, Void> tarea = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                servicio.cambiarEstado(sesion.id(), c.id(), nuevo);
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
        btnAgregar.setEnabled(disponible && privilegios.contains(Permisos.CONTRIBUYENTES_CREAR));
        btnDetalle.setEnabled(disponible && privilegios.contains(Permisos.CONTRIBUYENTES_CONSULTAR));
        btnEstado.setEnabled(disponible && privilegios.contains(Permisos.CONTRIBUYENTES_CAMBIAR_ESTADO));
        btnRegimenes.setEnabled(disponible && privilegios.contains(Permisos.CONTRIBUYENTES_CONSULTAR));
        btnAccesos.setEnabled(disponible && privilegios.contains(Permisos.CONTRIBUYENTES_ASIGNAR_USUARIOS));
        btnActualizar.setEnabled(disponible && privilegios.contains(Permisos.CONTRIBUYENTES_CONSULTAR));
    }

    private void mostrarError(ExecutionException ex) {
        Throwable error = ex.getCause();
        lblResultado.setText("No fue posible completar la operación");
        JOptionPane.showMessageDialog(this,
                error == null ? "Error inesperado" : error.getMessage(),
                "Error de contribuyentes", JOptionPane.ERROR_MESSAGE);
    }
}
