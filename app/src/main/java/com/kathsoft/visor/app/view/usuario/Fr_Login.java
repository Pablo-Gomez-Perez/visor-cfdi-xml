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
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import com.kathsoft.visor.app.view.Fr_Principal;

/**
 * Autenticación de usuarios de la aplicación (distinta de la clave MySQL).
 * El constructor vacío permite editar el formulario sin conexión externa.
 */
public class Fr_Login extends JFrame {

    private static final long serialVersionUID = 1L;

    private UsuariosService usuarios;
    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblUsuario;
    private JLabel lblClave;
    private JLabel lblEstado;
    private JTextField txfUsuario;
    private JPasswordField pswfClave;
    private JButton btnIngresar;

    /** Constructor para WindowBuilder. */
    public Fr_Login() {
        inicializarComponentes();
    }

    /**
     * @param usuarios servicio de autenticación
     */
    public Fr_Login(UsuariosService usuarios) {
        this();
        this.usuarios = usuarios;
    }

    private void inicializarComponentes() {
        setTitle("Visor CFDI XML - Inicio de sesión");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 510, 280);
        setMinimumSize(new Dimension(470, 260));
        setLocationRelativeTo(null);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(18, 22, 18, 22));
        setContentPane(contentPane);
        lblTitulo = new JLabel("Acceso al sistema");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        lblUsuario = new JLabel("Usuario");
        lblClave = new JLabel("Contraseña");
        lblEstado = new JLabel("Ingrese sus credenciales de aplicación.");
        txfUsuario = new JTextField(22);
        pswfClave = new JPasswordField(22);
        btnIngresar = new JButton("Ingresar");
        btnIngresar.addActionListener(e -> ingresar());

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblTitulo)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(lblUsuario, 130, 130, 130)
                    .addComponent(lblClave, 130, 130, 130))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(txfUsuario, GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)
                    .addComponent(pswfClave, GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)))
            .addComponent(lblEstado)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(btnIngresar)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblTitulo)
            .addGap(15)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblUsuario)
                .addComponent(txfUsuario, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblClave)
                .addComponent(pswfClave, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addComponent(lblEstado)
            .addGap(0, 0, Short.MAX_VALUE)
            .addComponent(btnIngresar));
        getRootPane().setDefaultButton(btnIngresar);
    }

    private void ingresar() {
        if (usuarios == null) {
            return;
        }
        String login = txfUsuario.getText();
        char[] clave = pswfClave.getPassword();
        btnIngresar.setEnabled(false);
        SwingWorker<UsuarioSesion, Void> worker = new SwingWorker<>() {
            @Override
            protected UsuarioSesion doInBackground() {
                return usuarios.autenticar(login, clave);
            }

            @Override
            protected void done() {
                try {
                    UsuarioSesion sesion = get();
                    new Fr_Principal(usuarios, sesion).setVisible(true);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    lblEstado.setText("Credenciales no válidas o acceso no disponible.");
                    JOptionPane.showMessageDialog(Fr_Login.this,
                            "No se pudo iniciar sesión. Revise las credenciales y el estado de la cuenta.",
                            "Acceso denegado", JOptionPane.WARNING_MESSAGE);
                } finally {
                    Arrays.fill(clave, '\0');
                    pswfClave.setText("");
                    btnIngresar.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
