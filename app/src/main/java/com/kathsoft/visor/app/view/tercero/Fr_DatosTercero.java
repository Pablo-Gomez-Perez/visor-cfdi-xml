package com.kathsoft.visor.app.view.tercero;

import java.awt.Dimension;
import java.awt.Frame;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.dto.SolicitudTercero;
import com.kathsoft.visor.app.model.dto.TerceroListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.tercero.TercerosService;

/**
 * Diálogo único de tercero: identidad y funciones comerciales.
 * Inicialización Swing pura compatible con Eclipse WindowBuilder.
 */
public class Fr_DatosTercero extends JDialog {
    private static final long serialVersionUID = 1L;
    private TercerosService servicio;
    private UsuarioSesion sesion;
    private Long contribuyenteId;
    private TerceroListado registro;
    private Set<String> permisos = Set.of();
    private Runnable alGuardar;
    private JTextField txfRfc;
    private JTextField txfNombre;
    private JTextField txfPais;
    private JTextField txfExtranjero;
    private JComboBox<String> cmbRol;
    private JButton btnGuardar;
    private JButton btnAsignar;
    private JButton btnRetirar;
    private JLabel lblFunciones;
    private JTabbedPane pestanas;

    /** Constructor vacío para diseñador visual. */
    public Fr_DatosTercero() {
        this(null, null, null, null, null, Set.of(), null, 0);
    }

    /** Construye el detalle del tercero en el contribuyente seleccionado. */
    public Fr_DatosTercero(Frame dueno, TercerosService servicio, UsuarioSesion sesion,
            Long contribuyenteId, TerceroListado registro, Set<String> permisos,
            Runnable alGuardar, int pestana) {
        super(dueno, "Datos del tercero", ModalityType.APPLICATION_MODAL);
        this.servicio = servicio;
        this.sesion = sesion;
        this.contribuyenteId = contribuyenteId;
        this.registro = registro;
        this.permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
        this.alGuardar = alGuardar;
        inicializarComponentes();
        if (registro != null) {
            txfRfc.setText(registro.rfc() == null ? "" : registro.rfc());
            txfNombre.setText(registro.nombreRazonSocial());
            txfPais.setText(registro.paisFiscal() == null ? "" : registro.paisFiscal());
            txfExtranjero.setText(registro.numeroRegistroFiscalExtranjero() == null
                    ? "" : registro.numeroRegistroFiscalExtranjero());
            txfRfc.setEditable(false);
            txfExtranjero.setEditable(false);
            boolean editable = this.permisos.contains(Permisos.TERCEROS_MODIFICAR);
            txfNombre.setEditable(editable);
            txfPais.setEditable(editable);
            btnGuardar.setEnabled(editable);
            btnAsignar.setEnabled(this.permisos.contains(Permisos.TERCEROS_ASIGNAR_ROLES));
            btnRetirar.setEnabled(this.permisos.contains(Permisos.TERCEROS_ASIGNAR_ROLES));
            cargarFunciones();
        } else {
            btnGuardar.setEnabled(this.permisos.contains(Permisos.TERCEROS_CREAR));
            pestanas.setEnabledAt(1, false);
        }
        if (pestana == 1 && registro != null) pestanas.setSelectedIndex(1);
    }

    private void inicializarComponentes() {
        setBounds(100, 100, 700, 430);
        setMinimumSize(new Dimension(610, 390));
        setLocationRelativeTo(getOwner());
        JPanel contenido = new JPanel(new EmptyBorder(10, 12, 10, 12));
        setContentPane(contenido);
        JLabel lblRfc = new JLabel("RFC");
        JLabel lblNombre = new JLabel("Nombre / razón social");
        JLabel lblPais = new JLabel("País fiscal");
        JLabel lblExtranjero = new JLabel("Registro fiscal extranjero");
        txfRfc = new JTextField(18);
        txfNombre = new JTextField(30);
        txfPais = new JTextField(5);
        txfExtranjero = new JTextField(20);
        JPanel datos = new JPanel();
        GroupLayout glDatos = new GroupLayout(datos);
        datos.setLayout(glDatos);
        glDatos.setAutoCreateGaps(true);
        glDatos.setAutoCreateContainerGaps(true);
        glDatos.setHorizontalGroup(glDatos.createSequentialGroup()
            .addGroup(glDatos.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblRfc).addComponent(lblNombre)
                .addComponent(lblPais).addComponent(lblExtranjero))
            .addGroup(glDatos.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(txfRfc).addComponent(txfNombre)
                .addComponent(txfPais).addComponent(txfExtranjero)));
        glDatos.setVerticalGroup(glDatos.createSequentialGroup()
            .addGroup(glDatos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblRfc).addComponent(txfRfc))
            .addGroup(glDatos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblNombre).addComponent(txfNombre))
            .addGroup(glDatos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblPais).addComponent(txfPais))
            .addGroup(glDatos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblExtranjero).addComponent(txfExtranjero)));
        JPanel funciones = new JPanel();
        JLabel lblFuncion = new JLabel("Función comercial");
        cmbRol = new JComboBox<>(new String[] {"CLIENTE", "PROVEEDOR", "PRESTADOR", "OTRO"});
        btnAsignar = new JButton("Asignar");
        btnRetirar = new JButton("Retirar");
        lblFunciones = new JLabel("Sin funciones cargadas");
        GroupLayout glFunciones = new GroupLayout(funciones);
        funciones.setLayout(glFunciones);
        glFunciones.setAutoCreateGaps(true);
        glFunciones.setAutoCreateContainerGaps(true);
        glFunciones.setHorizontalGroup(glFunciones.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(glFunciones.createSequentialGroup()
                .addComponent(lblFuncion).addComponent(cmbRol)
                .addComponent(btnAsignar).addComponent(btnRetirar))
            .addComponent(lblFunciones));
        glFunciones.setVerticalGroup(glFunciones.createSequentialGroup()
            .addGroup(glFunciones.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblFuncion).addComponent(cmbRol)
                .addComponent(btnAsignar).addComponent(btnRetirar))
            .addComponent(lblFunciones));
        pestanas = new JTabbedPane();
        pestanas.addTab("Datos fiscales", datos);
        pestanas.addTab("Funciones comerciales", funciones);
        btnGuardar = new JButton("Guardar");
        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.addActionListener(e -> dispose());
        btnGuardar.addActionListener(e -> guardar());
        btnAsignar.addActionListener(e -> cambiarFuncion(true));
        btnRetirar.addActionListener(e -> cambiarFuncion(false));
        GroupLayout layout = new GroupLayout(contenido);
        contenido.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(pestanas, GroupLayout.DEFAULT_SIZE, 550, Short.MAX_VALUE)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(btnCerrar).addComponent(btnGuardar)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(pestanas, GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(btnCerrar).addComponent(btnGuardar)));
        btnAsignar.setEnabled(false);
        btnRetirar.setEnabled(false);
    }

    private void guardar() {
        if (servicio == null || sesion == null) return;
        SolicitudTercero datos = new SolicitudTercero(txfRfc.getText(),
                txfNombre.getText(), txfPais.getText(), txfExtranjero.getText());
        btnGuardar.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                if (registro == null) servicio.registrar(sesion.id(), contribuyenteId,
                        datos, (String) cmbRol.getSelectedItem());
                else servicio.modificar(sesion.id(), contribuyenteId, registro.id(), datos);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    if (alGuardar != null) alGuardar.run();
                    dispose();
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException e) { error(e.getCause()); btnGuardar.setEnabled(true); }
            }
        }.execute();
    }

    private void cargarFunciones() {
        if (registro == null || servicio == null || sesion == null) return;
        new SwingWorker<Set<String>, Void>() {
            @Override protected Set<String> doInBackground() {
                return servicio.listarRoles(sesion.id(), contribuyenteId, registro.id());
            }
            @Override protected void done() {
                try { lblFunciones.setText("Funciones: " + String.join(", ", get())); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException e) { error(e.getCause()); }
            }
        }.execute();
    }

    private void cambiarFuncion(boolean asignar) {
        if (registro == null || servicio == null || sesion == null) return;
        String rol = (String) cmbRol.getSelectedItem();
        btnAsignar.setEnabled(false);
        btnRetirar.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                if (asignar) servicio.asignarRol(sesion.id(), contribuyenteId, registro.id(), rol);
                else servicio.retirarRol(sesion.id(), contribuyenteId, registro.id(), rol);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    cargarFunciones();
                    if (alGuardar != null) alGuardar.run();
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException e) { error(e.getCause()); }
                finally {
                    boolean permitido = permisos.contains(Permisos.TERCEROS_ASIGNAR_ROLES);
                    btnAsignar.setEnabled(permitido);
                    btnRetirar.setEnabled(permitido);
                }
            }
        }.execute();
    }

    private void error(Throwable e) {
        JOptionPane.showMessageDialog(this, e == null ? "Error inesperado" : e.getMessage(),
                "Administración de terceros", JOptionPane.ERROR_MESSAGE);
    }
}
