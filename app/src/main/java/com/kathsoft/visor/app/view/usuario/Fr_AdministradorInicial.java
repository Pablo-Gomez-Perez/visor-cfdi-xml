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

import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Registro del primer administrador, permitido solo cuando no existen cuentas.
 * El diseñador WindowBuilder puede construirlo sin un servicio inyectado.
 */
public class Fr_AdministradorInicial extends JFrame {

    private static final long serialVersionUID = 1L;

    private UsuariosService usuarios;
    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblNombre;
    private JLabel lblLogin;
    private JLabel lblCorreo;
    private JLabel lblContrasena;
    private JLabel lblRepetir;
    private JLabel lblNota;
    private JTextField txfNombre;
    private JTextField txfLogin;
    private JTextField txfCorreo;
    private JPasswordField pswfContrasena;
    private JPasswordField pswfRepetir;
    private JButton btnCrear;

    /** Constructor independiente de infraestructura para WindowBuilder. */
    public Fr_AdministradorInicial() {
        inicializarComponentes();
    }

    /**
     * @param usuarios servicio Hibernate transaccional
     */
    public Fr_AdministradorInicial(UsuariosService usuarios) {
        this();
        this.usuarios = usuarios;
    }

    private void inicializarComponentes() {
        setTitle("Visor CFDI XML - Primer administrador");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 620, 430);
        setMinimumSize(new Dimension(560, 410));
        setLocationRelativeTo(null);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(18, 22, 18, 22));
        setContentPane(contentPane);

        lblTitulo = new JLabel("Registro del primer administrador");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        lblNombre = new JLabel("Nombre completo");
        lblLogin = new JLabel("Usuario");
        lblCorreo = new JLabel("Correo (opcional)");
        lblContrasena = new JLabel("Contraseña");
        lblRepetir = new JLabel("Confirmar contraseña");
        lblNota = new JLabel("Mínimo 12 caracteres. No se genera una clave por defecto.");
        txfNombre = new JTextField(24);
        txfLogin = new JTextField(24);
        txfCorreo = new JTextField(24);
        pswfContrasena = new JPasswordField(24);
        pswfRepetir = new JPasswordField(24);
        btnCrear = new JButton("Crear administrador");
        btnCrear.addActionListener(e -> crear());

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblTitulo)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(lblNombre, 165, 165, 165)
                    .addComponent(lblLogin, 165, 165, 165)
                    .addComponent(lblCorreo, 165, 165, 165)
                    .addComponent(lblContrasena, 165, 165, 165)
                    .addComponent(lblRepetir, 165, 165, 165))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(txfNombre, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                    .addComponent(txfLogin, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                    .addComponent(txfCorreo, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                    .addComponent(pswfContrasena, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                    .addComponent(pswfRepetir, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)))
            .addComponent(lblNota)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(btnCrear)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblTitulo)
            .addGap(10)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblNombre)
                .addComponent(txfNombre, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblLogin)
                .addComponent(txfLogin, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblCorreo)
                .addComponent(txfCorreo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblContrasena)
                .addComponent(pswfContrasena, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblRepetir)
                .addComponent(pswfRepetir, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addComponent(lblNota)
            .addGap(0, 0, Short.MAX_VALUE)
            .addComponent(btnCrear));
        getRootPane().setDefaultButton(btnCrear);
    }

    private void crear() {
        if (usuarios == null) {
            return;
        }
        char[] clave = pswfContrasena.getPassword();
        char[] confirmar = pswfRepetir.getPassword();
        if (!Arrays.equals(clave, confirmar)) {
            JOptionPane.showMessageDialog(this, "Las contraseñas no coinciden.");
            Arrays.fill(clave, '\0');
            Arrays.fill(confirmar, '\0');
            return;
        }
        Arrays.fill(confirmar, '\0');
        SolicitudUsuario solicitud = new SolicitudUsuario(txfNombre.getText(), txfLogin.getText(),
                txfCorreo.getText(), UsuariosService.ADMIN);
        btnCrear.setEnabled(false);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                usuarios.registrarAdministradorInicial(solicitud, clave);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(Fr_AdministradorInicial.this,
                            "Administrador registrado correctamente. Inicie sesión.");
                    new Fr_Login(usuarios).setVisible(true);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause();
                    JOptionPane.showMessageDialog(Fr_AdministradorInicial.this,
                            causa == null ? "No fue posible registrar la cuenta." : causa.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    Arrays.fill(clave, '\0');
                    pswfContrasena.setText("");
                    pswfRepetir.setText("");
                    btnCrear.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
