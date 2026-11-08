package com.kathsoft.visor.app.view.configuracion;

import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.App;
import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.service.configuracion.ConexionMySqlService;
import com.kathsoft.visor.app.service.configuracion.ConfiguracionLocalService;

/**
 * RF-001: formulario de primera configuración de Visor CFDI XML.
 *
 * <p>El constructor sin argumentos es independiente de JDBC y del disco
 * para permitir la edición visual en Eclipse WindowBuilder. Todos los
 * componentes y los GroupLayout están declarados explícitamente.</p>
 */
public class Fr_ConfiguracionInicial extends JFrame {

    private static final long serialVersionUID = 1L;

    private final ConexionMySqlService servicioConexion = new ConexionMySqlService();
    private final ConfiguracionLocalService servicioConfiguracion = new ConfiguracionLocalService();

    private JPanel contentPane;
    private JPanel panelConexion;
    private JPanel panelDocumentos;
    private JLabel lblTitulo;
    private JLabel lblDescripcion;
    private JLabel lblHost;
    private JLabel lblPuerto;
    private JLabel lblBaseDatos;
    private JLabel lblUsuario;
    private JLabel lblContrasenia;
    private JLabel lblRuta;
    private JLabel lblNotaRuta;
    private JLabel lblEstado;
    private JTextField txfHost;
    private JTextField txfPuerto;
    private JTextField txfBaseDatos;
    private JTextField txfUsuario;
    private JTextField txfRuta;
    private JPasswordField pswfContrasenia;
    private JButton btnExaminar;
    private JButton btnProbarConexion;
    private JButton btnGuardar;

    /**
     * Constructor para el diseñador visual; no ejecuta consultas ni lee ficheros.
     */
    public Fr_ConfiguracionInicial() {
        this(null);
    }

    /**
     * Constructor de la aplicación para recuperar los valores ya configurados.
     *
     * @param configuracion configuración local; puede ser nula en el primer inicio
     */
    public Fr_ConfiguracionInicial(ConfiguracionSistema configuracion) {
        inicializarComponentes();
        if (configuracion != null) {
            mostrarConfiguracion(configuracion);
            lblEstado.setText("Configuración local cargada. Introduzca la contraseña para verificar.");
        }
    }

    /**
     * Construye exclusivamente componentes Swing y grupos editables.
     */
    private void inicializarComponentes() {
        setTitle("Visor CFDI XML - Configuración inicial");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 725, 575);
        setMinimumSize(new Dimension(625, 540));
        setLocationRelativeTo(null);

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(18, 22, 18, 22));
        setContentPane(contentPane);

        lblTitulo = new JLabel("Configuración inicial");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 22f));

        lblDescripcion = new JLabel("Configure MySQL 8 y el repositorio local de CFDI originales.");

        panelConexion = new JPanel();
        panelConexion.setBorder(BorderFactory.createTitledBorder("Conexión con MySQL 8"));

        lblHost = new JLabel("Servidor / IP");
        lblPuerto = new JLabel("Puerto");
        lblBaseDatos = new JLabel("Base de datos");
        lblUsuario = new JLabel("Usuario MySQL");
        lblContrasenia = new JLabel("Contraseña");

        txfHost = new JTextField("localhost");
        txfHost.setColumns(22);
        txfPuerto = new JTextField("3306");
        txfPuerto.setColumns(10);
        txfBaseDatos = new JTextField("visor_cfdi");
        txfBaseDatos.setColumns(22);
        txfUsuario = new JTextField("visor_app");
        txfUsuario.setColumns(22);
        pswfContrasenia = new JPasswordField();
        pswfContrasenia.setColumns(22);

        GroupLayout gl_panelConexion = new GroupLayout(panelConexion);
        panelConexion.setLayout(gl_panelConexion);
        gl_panelConexion.setAutoCreateGaps(true);
        gl_panelConexion.setAutoCreateContainerGaps(true);
        gl_panelConexion.setHorizontalGroup(
            gl_panelConexion.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(gl_panelConexion.createSequentialGroup()
                    .addGroup(gl_panelConexion.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(lblHost, 145, 145, 145)
                        .addComponent(lblPuerto, 145, 145, 145)
                        .addComponent(lblBaseDatos, 145, 145, 145)
                        .addComponent(lblUsuario, 145, 145, 145)
                        .addComponent(lblContrasenia, 145, 145, 145))
                    .addGroup(gl_panelConexion.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(txfHost, GroupLayout.DEFAULT_SIZE, 360, Short.MAX_VALUE)
                        .addComponent(txfPuerto, GroupLayout.DEFAULT_SIZE, 360, Short.MAX_VALUE)
                        .addComponent(txfBaseDatos, GroupLayout.DEFAULT_SIZE, 360, Short.MAX_VALUE)
                        .addComponent(txfUsuario, GroupLayout.DEFAULT_SIZE, 360, Short.MAX_VALUE)
                        .addComponent(pswfContrasenia, GroupLayout.DEFAULT_SIZE, 360, Short.MAX_VALUE))));
        gl_panelConexion.setVerticalGroup(
            gl_panelConexion.createSequentialGroup()
                .addGroup(gl_panelConexion.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblHost)
                    .addComponent(txfHost, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(gl_panelConexion.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblPuerto)
                    .addComponent(txfPuerto, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(gl_panelConexion.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblBaseDatos)
                    .addComponent(txfBaseDatos, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(gl_panelConexion.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblUsuario)
                    .addComponent(txfUsuario, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(gl_panelConexion.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblContrasenia)
                    .addComponent(pswfContrasenia, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)));

        panelDocumentos = new JPanel();
        panelDocumentos.setBorder(BorderFactory.createTitledBorder("Repositorio documental local"));
        lblRuta = new JLabel("Carpeta para los XML originales:");
        lblNotaRuta = new JLabel("La carpeta se creará al guardar. Los XML no se almacenan en MySQL.");
        txfRuta = new JTextField();
        txfRuta.setText(Path.of(System.getProperty("user.home"), "VisorCFDI", "xml").toString());
        btnExaminar = new JButton("Examinar...");
        btnExaminar.addActionListener(e -> seleccionarDirectorio());

        GroupLayout gl_panelDocumentos = new GroupLayout(panelDocumentos);
        panelDocumentos.setLayout(gl_panelDocumentos);
        gl_panelDocumentos.setAutoCreateGaps(true);
        gl_panelDocumentos.setAutoCreateContainerGaps(true);
        gl_panelDocumentos.setHorizontalGroup(
            gl_panelDocumentos.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblRuta)
                .addGroup(gl_panelDocumentos.createSequentialGroup()
                    .addComponent(txfRuta, GroupLayout.DEFAULT_SIZE, 360, Short.MAX_VALUE)
                    .addComponent(btnExaminar))
                .addComponent(lblNotaRuta));
        gl_panelDocumentos.setVerticalGroup(
            gl_panelDocumentos.createSequentialGroup()
                .addComponent(lblRuta)
                .addGroup(gl_panelDocumentos.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(txfRuta, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExaminar))
                .addComponent(lblNotaRuta));

        lblEstado = new JLabel("La conexión aún no ha sido comprobada.");
        btnProbarConexion = new JButton("Probar conexión");
        btnGuardar = new JButton("Guardar configuración");
        btnProbarConexion.addActionListener(e -> ejecutarOperacion(false));
        btnGuardar.addActionListener(e -> ejecutarOperacion(true));

        GroupLayout gl_contentPane = new GroupLayout(contentPane);
        contentPane.setLayout(gl_contentPane);
        gl_contentPane.setAutoCreateGaps(true);
        gl_contentPane.setAutoCreateContainerGaps(true);
        gl_contentPane.setHorizontalGroup(
            gl_contentPane.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblTitulo)
                .addComponent(lblDescripcion)
                .addComponent(panelConexion, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(panelDocumentos, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(lblEstado)
                .addGroup(GroupLayout.Alignment.TRAILING, gl_contentPane.createSequentialGroup()
                    .addComponent(btnProbarConexion)
                    .addComponent(btnGuardar)));
        gl_contentPane.setVerticalGroup(
            gl_contentPane.createSequentialGroup()
                .addComponent(lblTitulo)
                .addComponent(lblDescripcion)
                .addComponent(panelConexion, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(panelDocumentos, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblEstado)
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(gl_contentPane.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(btnProbarConexion)
                    .addComponent(btnGuardar)));
    }

    private void mostrarConfiguracion(ConfiguracionSistema configuracion) {
        txfHost.setText(configuracion.host());
        txfPuerto.setText(Integer.toString(configuracion.puerto()));
        txfBaseDatos.setText(configuracion.baseDatos());
        txfUsuario.setText(configuracion.usuario());
        txfRuta.setText(configuracion.directorioXml().toString());
    }

    private void seleccionarDirectorio() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Seleccione la carpeta de CFDI originales");
        selector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File actual = new File(txfRuta.getText().trim());
        if (actual.isDirectory()) {
            selector.setCurrentDirectory(actual);
        }
        if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txfRuta.setText(selector.getSelectedFile().getAbsolutePath());
        }
    }

    private ConfiguracionSistema obtenerDatosFormulario() {
        String ruta = txfRuta.getText().trim();
        if (ruta.isBlank()) {
            throw new IllegalArgumentException("Seleccione una carpeta de documentos");
        }
        int puerto;
        try {
            puerto = Integer.parseInt(txfPuerto.getText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El puerto de MySQL debe ser numérico", ex);
        }
        return new ConfiguracionSistema(txfHost.getText(), puerto, txfBaseDatos.getText(),
                txfUsuario.getText(), Path.of(ruta));
    }

    private void ejecutarOperacion(boolean guardar) {
        ConfiguracionSistema datos;
        try {
            datos = obtenerDatosFormulario();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        char[] clave = pswfContrasenia.getPassword();
        habilitarControles(false);
        lblEstado.setText(guardar ? "Verificando MySQL y preparando la configuración..."
                : "Probando conexión con MySQL...");

        SwingWorker<Void, Void> tarea = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                servicioConexion.probarConexion(datos, clave);
                if (guardar) {
                    servicioConfiguracion.prepararRepositorio(datos.directorioXml());
                    servicioConfiguracion.guardar(datos);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    lblEstado.setText(guardar
                            ? "Configuración guardada. Continúe con el acceso al sistema."
                            : "Conexión MySQL verificada correctamente.");
                    if (guardar) {
                        JOptionPane.showMessageDialog(Fr_ConfiguracionInicial.this,
                                "Configuración guardada correctamente.\n"
                                        + "Los XML permanecerán en el repositorio local seleccionado.",
                                "Configuración inicial", JOptionPane.INFORMATION_MESSAGE);
                        App.abrirConexion(datos);
                        dispose();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblEstado.setText("La operación fue interrumpida.");
                } catch (ExecutionException ex) {
                    lblEstado.setText("No se completó la operación.");
                    Throwable causa = ex.getCause();
                    String detalle = causa != null ? causa.getMessage() : "Error desconocido";
                    JOptionPane.showMessageDialog(Fr_ConfiguracionInicial.this,
                            "No fue posible completar la operación:\n" + detalle,
                            "Error de configuración", JOptionPane.ERROR_MESSAGE);
                } finally {
                    Arrays.fill(clave, '\0');
                    pswfContrasenia.setText("");
                    habilitarControles(true);
                }
            }
        };
        tarea.execute();
    }

    private void habilitarControles(boolean habilitar) {
        btnExaminar.setEnabled(habilitar);
        btnGuardar.setEnabled(habilitar);
        btnProbarConexion.setEnabled(habilitar);
        txfHost.setEnabled(habilitar);
        txfPuerto.setEnabled(habilitar);
        txfBaseDatos.setEnabled(habilitar);
        txfUsuario.setEnabled(habilitar);
        txfRuta.setEnabled(habilitar);
        pswfContrasenia.setEnabled(habilitar);
    }
}
