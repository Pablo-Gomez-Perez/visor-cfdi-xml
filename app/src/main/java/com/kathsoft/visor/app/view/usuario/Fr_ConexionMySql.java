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
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Abre la persistencia Hibernate usando la credencial MySQL solicitada
 * para esta ejecución. Constructor vacío seguro para WindowBuilder.
 */
public class Fr_ConexionMySql extends JFrame {

    private static final long serialVersionUID = 1L;

    private ConfiguracionSistema configuracion;
    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblServidor;
    private JLabel lblClave;
    private JLabel lblEstado;
    private JPasswordField pswfClave;
    private JButton btnConectar;

    /** Constructor sin dependencias externas para Eclipse WindowBuilder. */
    public Fr_ConexionMySql() {
        inicializarComponentes();
    }

    /**
     * @param configuracion parámetros guardados por RF-001
     */
    public Fr_ConexionMySql(ConfiguracionSistema configuracion) {
        this();
        this.configuracion = configuracion;
        if (configuracion != null) {
            lblServidor.setText("Servidor: " + configuracion.host() + " / " + configuracion.baseDatos());
        }
    }

    private void inicializarComponentes() {
        setTitle("Visor CFDI XML - Conexión");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 510, 270);
        setMinimumSize(new Dimension(480, 250));
        setLocationRelativeTo(null);

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(18, 22, 18, 22));
        setContentPane(contentPane);
        lblTitulo = new JLabel("Conexión con MySQL 8");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        lblServidor = new JLabel("Servidor sin configurar");
        lblClave = new JLabel("Contraseña del usuario MySQL");
        lblEstado = new JLabel("Esta contraseña no se guardará en disco.");
        pswfClave = new JPasswordField(22);
        btnConectar = new JButton("Conectar");
        btnConectar.addActionListener(e -> conectar());

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblTitulo)
            .addComponent(lblServidor)
            .addComponent(lblClave)
            .addComponent(pswfClave, GroupLayout.DEFAULT_SIZE, 370, Short.MAX_VALUE)
            .addComponent(lblEstado)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(btnConectar)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblTitulo)
            .addComponent(lblServidor)
            .addGap(8)
            .addComponent(lblClave)
            .addComponent(pswfClave, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(lblEstado)
            .addGap(0, 0, Short.MAX_VALUE)
            .addComponent(btnConectar));
        getRootPane().setDefaultButton(btnConectar);
    }

    private void conectar() {
        if (configuracion == null) {
            JOptionPane.showMessageDialog(this, "Configure MySQL antes de continuar.");
            return;
        }
        char[] clave = pswfClave.getPassword();
        btnConectar.setEnabled(false);
        lblEstado.setText("Inicializando la conexión y validando el esquema...");
        SwingWorker<ResultadoConexion, Void> worker = new SwingWorker<>() {
            @Override
            protected ResultadoConexion doInBackground() {
                JpaContext jpa = new JpaContext(configuracion, clave);
                try {
                    UsuariosService usuarios = new UsuariosService(jpa);
                    usuarios.inicializarRoles();
                    boolean registrarAdmin = usuarios.requiereAdministrador();
                    return new ResultadoConexion(jpa, usuarios, registrarAdmin);
                } catch (RuntimeException ex) {
                    jpa.close();
                    throw ex;
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
                    mostrarError("No se pudo establecer la persistencia Hibernate.\n"
                            + (causa == null ? "Error desconocido" : causa.getMessage()));
                } finally {
                    Arrays.fill(clave, '\0');
                    pswfClave.setText("");
                    btnConectar.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void mostrarError(String mensaje) {
        lblEstado.setText("Conexión no establecida");
        JOptionPane.showMessageDialog(this, mensaje,
                "Error de conexión", JOptionPane.ERROR_MESSAGE);
    }

    private record ResultadoConexion(JpaContext jpa, UsuariosService usuarios, boolean registrarAdmin) {
    }
}
