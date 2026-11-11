package com.kathsoft.visor.app.view.contribuyente;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.dto.ContribuyenteListado;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.contribuyente.ContribuyentesService;

/**
 * Diálogo ÚNICO de detalle de contribuyente: datos, historial de regímenes
 * y usuarios autorizados en tres pestañas sin ventanas adicionales.
 *
 * <p>Patrón Kath ERP. Cada panel es una clase Java propia compatible con
 * Eclipse WindowBuilder; ningún constructor sin parámetros accede a MySQL,
 * red, configuración local ni EntityManager.</p>
 */
public class Fr_DatosContribuyente extends JDialog {

    private static final long serialVersionUID = 1L;

    private ContribuyentesService servicio;
    private UsuarioSesion sesion;
    private ContribuyenteListado registro;
    private Set<String> privilegios = Set.of();
    private Runnable alGuardar;

    private JPanel contentPane;
    private JPanel panelDatos;
    private JTabbedPane tabbedPane;
    private PanelRegimenesContribuyente panelRegimenes;
    private PanelAccesosContribuyente panelAccesos;

    private JLabel lblTitulo;
    private JLabel lblRfc;
    private JLabel lblNombre;
    private JLabel lblCodigoPostal;
    private JLabel lblCorreo;
    private JLabel lblNota;
    private JTextField txfRfc;
    private JTextField txfNombre;
    private JTextField txfCodigoPostal;
    private JTextField txfCorreo;
    private JButton btnGuardar;
    private JButton btnCancelar;

    /** Constructor público de diseño; no inicializa servicios. */
    public Fr_DatosContribuyente() {
        this(null, null, null, null, Set.of(), null, 0);
    }

    /**
     * Abre datos de un contribuyente y sus aspectos asociados.
     *
     * @param dueno ventana principal
     * @param servicio servicio de gestión
     * @param sesion usuario autenticado
     * @param registro cuenta fiscal existente, o null para alta
     * @param privilegios autorizaciones actuales
     * @param alGuardar acción para refrescar el catálogo
     * @param pestana 0 Datos, 1 Regímenes, 2 Usuarios
     */
    public Fr_DatosContribuyente(Frame dueno, ContribuyentesService servicio,
            UsuarioSesion sesion, ContribuyenteListado registro,
            Set<String> privilegios, Runnable alGuardar, int pestana) {
        super(dueno, "Datos del contribuyente", ModalityType.APPLICATION_MODAL);
        this.servicio = servicio;
        this.sesion = sesion;
        this.registro = registro;
        this.privilegios = privilegios == null ? Set.of() : Set.copyOf(privilegios);
        this.alGuardar = alGuardar;
        inicializarComponentes();

        if (registro != null) {
            lblTitulo.setText("Contribuyente: " + registro.rfc());
            setTitle("Detalle del contribuyente - " + registro.rfc());
            txfRfc.setText(registro.rfc());
            txfNombre.setText(registro.nombreRazonSocial());
            txfCodigoPostal.setText(registro.codigoPostalFiscal() == null
                    ? "" : registro.codigoPostalFiscal());
            txfCorreo.setText(registro.correoContacto() == null
                    ? "" : registro.correoContacto());

            txfRfc.setEditable(false);
            boolean editarDatos = this.privilegios.contains(Permisos.CONTRIBUYENTES_MODIFICAR);
            txfNombre.setEditable(editarDatos);
            txfCodigoPostal.setEditable(editarDatos);
            txfCorreo.setEditable(editarDatos);
            btnGuardar.setEnabled(editarDatos);
            lblNota.setText("El RFC no se cambia tras el alta. "
                    + "Los regímenes y accesos se editan en sus pestañas.");

            boolean consultar = this.privilegios.contains(Permisos.CONTRIBUYENTES_CONSULTAR);
            boolean gestionar = this.privilegios.contains(
                    Permisos.CONTRIBUYENTES_GESTIONAR_REGIMENES);
            boolean asignar = this.privilegios.contains(
                    Permisos.CONTRIBUYENTES_ASIGNAR_USUARIOS);
            tabbedPane.setEnabledAt(1, consultar);
            tabbedPane.setEnabledAt(2, asignar);
            if (consultar) {
                panelRegimenes.configurar(servicio, sesion, registro.id(), gestionar);
            }
            if (asignar) {
                panelAccesos.configurar(servicio, sesion, registro.id());
            }
            if (pestana >= 0 && pestana < tabbedPane.getTabCount()
                    && tabbedPane.isEnabledAt(pestana)) {
                tabbedPane.setSelectedIndex(pestana);
            }
        } else {
            lblTitulo.setText("Registrar contribuyente");
            lblNota.setText("Registre y guarde primero los datos para habilitar las demás pestañas.");
            tabbedPane.setEnabledAt(1, false);
            tabbedPane.setEnabledAt(2, false);
            btnGuardar.setEnabled(this.privilegios.contains(Permisos.CONTRIBUYENTES_CREAR));
        }
    }

    private void inicializarComponentes() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 755, 555);
        setMinimumSize(new Dimension(665, 480));
        setLocationRelativeTo(getOwner());

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(12, 14, 12, 14));
        setContentPane(contentPane);
        lblTitulo = new JLabel("Datos del contribuyente");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 20f));

        tabbedPane = new JTabbedPane();
        panelDatos = new JPanel();
        panelRegimenes = new PanelRegimenesContribuyente();
        panelAccesos = new PanelAccesosContribuyente();
        tabbedPane.addTab("Datos fiscales", panelDatos);
        tabbedPane.addTab("Regímenes fiscales", panelRegimenes);
        tabbedPane.addTab("Usuarios autorizados", panelAccesos);

        lblRfc = new JLabel("RFC");
        lblNombre = new JLabel("Nombre / razón social");
        lblCodigoPostal = new JLabel("Código postal fiscal");
        lblCorreo = new JLabel("Correo de contacto");
        lblNota = new JLabel("El RFC y el nombre son obligatorios; los demás campos son opcionales.");

        txfRfc = new JTextField(18);
        txfNombre = new JTextField(35);
        txfCodigoPostal = new JTextField(8);
        txfCorreo = new JTextField(30);
        txfRfc.setToolTipText("RFC de persona física (13) o moral (12). No implica validación SAT.");
        txfCodigoPostal.setToolTipText("Código postal de cinco dígitos");

        GroupLayout gl_panelDatos = new GroupLayout(panelDatos);
        panelDatos.setLayout(gl_panelDatos);
        gl_panelDatos.setAutoCreateGaps(true);
        gl_panelDatos.setAutoCreateContainerGaps(true);
        gl_panelDatos.setHorizontalGroup(
            gl_panelDatos.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(gl_panelDatos.createSequentialGroup()
                    .addGroup(gl_panelDatos.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(lblRfc, 175, 175, 175)
                        .addComponent(lblNombre, 175, 175, 175)
                        .addComponent(lblCodigoPostal, 175, 175, 175)
                        .addComponent(lblCorreo, 175, 175, 175))
                    .addGroup(gl_panelDatos.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(txfRfc, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                        .addComponent(txfNombre, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                        .addComponent(txfCodigoPostal, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                        .addComponent(txfCorreo, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)))
                .addComponent(lblNota));
        gl_panelDatos.setVerticalGroup(gl_panelDatos.createSequentialGroup()
            .addGap(12)
            .addGroup(gl_panelDatos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblRfc)
                .addComponent(txfRfc, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(gl_panelDatos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblNombre)
                .addComponent(txfNombre, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(gl_panelDatos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblCodigoPostal)
                .addComponent(txfCodigoPostal, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(gl_panelDatos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblCorreo)
                .addComponent(txfCorreo, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGap(8)
            .addComponent(lblNota)
            .addGap(0, 0, Short.MAX_VALUE));

        btnGuardar = new JButton("Guardar datos");
        btnCancelar = new JButton("Cerrar");
        btnGuardar.addActionListener(e -> guardar());
        btnCancelar.addActionListener(e -> dispose());

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblTitulo)
            .addComponent(tabbedPane, GroupLayout.DEFAULT_SIZE, 590, Short.MAX_VALUE)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(btnCancelar)
                .addComponent(btnGuardar)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblTitulo)
            .addComponent(tabbedPane, GroupLayout.DEFAULT_SIZE, 330, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(btnCancelar)
                .addComponent(btnGuardar)));
        getRootPane().setDefaultButton(btnGuardar);
        tabbedPane.setEnabledAt(1, false);
        tabbedPane.setEnabledAt(2, false);
    }

    private void guardar() {
        if (servicio == null || sesion == null) return;
        SolicitudContribuyente datos = new SolicitudContribuyente(
                txfRfc.getText(), txfNombre.getText(), txfCodigoPostal.getText(),
                txfCorreo.getText());
        btnGuardar.setEnabled(false);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                if (registro == null) {
                    servicio.registrar(sesion.id(), datos);
                } else {
                    servicio.modificar(sesion.id(), registro.id(), datos);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (alGuardar != null) alGuardar.run();
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable error = ex.getCause();
                    JOptionPane.showMessageDialog(Fr_DatosContribuyente.this,
                            error == null ? "Error inesperado" : error.getMessage(),
                            "No se pudo guardar el contribuyente", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnGuardar.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
