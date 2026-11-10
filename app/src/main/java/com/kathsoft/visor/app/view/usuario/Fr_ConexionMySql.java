package com.kathsoft.visor.app.view.usuario;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.service.configuracion.CredencialesMySqlService;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Conecta automáticamente MySQL usando la credencial cifrada del usuario.
 *
 * <p>Durante una migración de RF-001 sin contraseña almacenada, la solicita
 * una sola vez y la cifra después de verificar Hibernate. Si la clave dejó
 * de ser válida, se permite introducir una nueva de forma explícita.
 * El constructor vacío no toca archivos ni inicializa servicios MySQL,
 * permitiendo la vista previa en Eclipse WindowBuilder.</p>
 */
public class Fr_ConexionMySql extends JFrame {

    private static final long serialVersionUID = 1L;

    private ConfiguracionSistema configuracion;
    private final CredencialesMySqlService almacen = new CredencialesMySqlService();
    private boolean modoCaptura;

    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblServidor;
    private JLabel lblClave;
    private JLabel lblEstado;
    private JLabel lblAyuda;
    private JPasswordField pswfClave;
    private JButton btnConectar;
    private JButton btnCambiarClave;

    /** Constructor para Eclipse WindowBuilder, sin acceso a datos. */
    public Fr_ConexionMySql() {
        inicializarComponentes();
    }

    /**
     * Inicia la conexión automática, sin solicitar contraseña si hay una
     * credencial cifrada vinculada a la configuración actual.
     *
     * @param configuracion parámetros MySQL guardados por RF-001
     */
    public Fr_ConexionMySql(ConfiguracionSistema configuracion) {
        this();
        this.configuracion = configuracion;
        if (configuracion != null) {
            lblServidor.setText("Servidor: " + configuracion.host()
                    + " / " + configuracion.baseDatos());
            SwingUtilities.invokeLater(this::iniciarAutomaticamente);
        }
    }

    private void inicializarComponentes() {
        setTitle("Visor CFDI XML - Conexión MySQL");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 550, 285);
        setMinimumSize(new Dimension(520, 265));
        setLocationRelativeTo(null);

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(16, 20, 16, 20));
        setContentPane(contentPane);
        lblTitulo = new JLabel("Conexión a la base de datos");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        lblServidor = new JLabel("Servidor sin configurar");
        lblClave = new JLabel("Contraseña MySQL");
        lblEstado = new JLabel("Preparando conexión...");
        lblAyuda = new JLabel("La contraseña MySQL se almacena cifrada y es distinta del login del sistema.");
        lblAyuda.setFont(lblAyuda.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        pswfClave = new JPasswordField(22);
        btnConectar = new JButton("Reintentar");
        btnCambiarClave = new JButton("Actualizar contraseña");
        btnConectar.addActionListener(e -> conectar());
        btnCambiarClave.addActionListener(e -> activarCaptura(
                "Introduzca la contraseña de MySQL para actualizar el archivo cifrado."));

        GroupLayout groupLayout = new GroupLayout(contentPane);
        contentPane.setLayout(groupLayout);
        groupLayout.setAutoCreateGaps(true);
        groupLayout.setAutoCreateContainerGaps(true);
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblTitulo)
                .addComponent(lblServidor)
                .addComponent(lblClave)
                .addComponent(pswfClave, GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE)
                .addComponent(lblEstado)
                .addComponent(lblAyuda)
                .addGroup(GroupLayout.Alignment.TRAILING, groupLayout.createSequentialGroup()
                    .addComponent(btnCambiarClave)
                    .addComponent(btnConectar)));
        groupLayout.setVerticalGroup(
            groupLayout.createSequentialGroup()
                .addComponent(lblTitulo)
                .addComponent(lblServidor)
                .addGap(8)
                .addComponent(lblClave)
                .addComponent(pswfClave, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblEstado)
                .addComponent(lblAyuda)
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(btnCambiarClave)
                    .addComponent(btnConectar)));
        getRootPane().setDefaultButton(btnConectar);
        mostrarModoAutomatico();
    }

    /**
     * Oculta la captura del secreto si el almacen cifrado ya está disponible.
     */
    private void mostrarModoAutomatico() {
        modoCaptura = false;
        lblClave.setVisible(false);
        pswfClave.setVisible(false);
        btnCambiarClave.setVisible(true);
        btnConectar.setText("Reintentar");
    }

    /**
     * Permite migrar la configuración anterior o actualizar una contraseña.
     *
     * @param mensaje indicación de recuperación
     */
    private void activarCaptura(String mensaje) {
        modoCaptura = true;
        lblClave.setVisible(true);
        pswfClave.setVisible(true);
        btnCambiarClave.setVisible(false);
        btnConectar.setText("Guardar y conectar");
        btnConectar.setEnabled(true);
        lblEstado.setText("Contraseña MySQL requerida solo para guardar o actualizar.");
        pswfClave.requestFocusInWindow();
        if (mensaje != null && !mensaje.isBlank()) {
            pswfClave.setToolTipText(mensaje);
        }
    }

    /**
     * Intenta conectar de forma automática en un worker, después de abrir
     * el JFrame; no ejecuta MySQL en el Event Dispatch Thread.
     */
    private void iniciarAutomaticamente() {
        if (configuracion == null) {
            lblEstado.setText("Falta configurar el servidor MySQL");
            return;
        }
        if (!almacen.existen()) {
            activarCaptura("Primera conexión: introduzca su contraseña MySQL una sola vez.");
            return;
        }
        mostrarModoAutomatico();
        conectar();
    }

    private void conectar() {
        if (configuracion == null) {
            JOptionPane.showMessageDialog(this, "Configure MySQL antes de continuar.",
                    "Configuración", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean guardarNueva = modoCaptura;
        char[] introducida = guardarNueva ? pswfClave.getPassword() : null;

        btnConectar.setEnabled(false);
        btnCambiarClave.setEnabled(false);
        lblEstado.setText(guardarNueva
                ? "Comprobando contraseña y preparando el almacen cifrado..."
                : "Conectando automáticamente con MySQL...");

        SwingWorker<ResultadoConexion, Void> tarea = new SwingWorker<>() {
            @Override
            protected ResultadoConexion doInBackground() throws Exception {
                char[] clave = guardarNueva ? introducida : almacen.cargar(configuracion);
                try {
                    JpaContext jpa = new JpaContext(configuracion, clave);
                    try {
                        UsuariosService usuarios = new UsuariosService(jpa);
                        usuarios.inicializarRoles();
                        boolean registrarAdmin = usuarios.requiereAdministrador();
                        if (guardarNueva) {
                            // Solo guarda un secreto si las conexiones Hibernate
                            // y la verificación de esquema funcionaron.
                            almacen.guardar(configuracion, clave);
                        }
                        return new ResultadoConexion(jpa, usuarios, registrarAdmin);
                    } catch (Exception | Error ex) {
                        jpa.close();
                        throw ex;
                    }
                } finally {
                    Arrays.fill(clave, '\0');
                }
            }

            @Override
            protected void done() {
                try {
                    ResultadoConexion resultado = get();
                    Runtime.getRuntime().addShutdownHook(new Thread(resultado.jpa()::close));
                    JFrame siguiente = resultado.registrarAdmin()
                            ? new Fr_AdministradorInicial(resultado.usuarios())
                            : new Fr_Login(resultado.usuarios());
                    siguiente.setVisible(true);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    mostrarError("La conexión fue interrumpida");
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause();
                    mostrarError("No fue posible establecer la conexión MySQL.\n"
                            + (causa == null ? "Error desconocido" : causa.getMessage())
                            + "\nCompruebe que MySQL está iniciado y que las credenciales sean válidas.");
                    if (!guardarNueva) {
                        lblEstado.setText("Conexión automática fallida: reintente o actualice la contraseña.");
                    }
                } finally {
                    if (introducida != null) {
                        Arrays.fill(introducida, '\0');
                    }
                    pswfClave.setText("");
                    btnConectar.setEnabled(true);
                    btnCambiarClave.setEnabled(true);
                }
            }
        };
        tarea.execute();
    }

    private void mostrarError(String mensaje) {
        lblEstado.setText("Conexión no establecida");
        JOptionPane.showMessageDialog(this, mensaje,
                "Error de conexión", JOptionPane.ERROR_MESSAGE);
    }

    private record ResultadoConexion(JpaContext jpa, UsuariosService usuarios, boolean registrarAdmin) {
    }
}
