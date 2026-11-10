package com.kathsoft.visor.app.view.usuario;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Font;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.UsuarioListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Diálogo único de detalle para crear o modificar un usuario.
 *
 * <p>Equivale a los formularios Fr_Datos* de Kath ERP: el listado permanece
 * dentro de CardLayout y solo los campos de un registro se muestran en un
 * diálogo modal. Usa GroupLayout y constructor sin dependencias para
 * Eclipse WindowBuilder.</p>
 */
public class Fr_DatosUsuario extends JDialog {

    private static final long serialVersionUID = 1L;

    private UsuariosService usuarios;
    private UsuarioSesion sesion;
    private UsuarioListado registro;
    private Runnable alGuardar;

    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblNombre;
    private JLabel lblLogin;
    private JLabel lblCorreo;
    private JLabel lblRol;
    private JLabel lblClave;
    private JLabel lblConfirmar;
    private JLabel lblNota;
    private JTextField txfNombre;
    private JTextField txfLogin;
    private JTextField txfCorreo;
    private JComboBox<String> cmbRol;
    private JPasswordField pswfClave;
    private JPasswordField pswfConfirmar;
    private JButton btnGuardar;
    private JButton btnCancelar;

    /**
     * Constructor sin servicios para previsualización en WindowBuilder.
     */
    public Fr_DatosUsuario() {
        this(null, null, null, null, null, List.of());
    }

    /**
     * Crea un formulario de alta o edición, según se proporcione registro.
     *
     * @param dueno ventana principal
     * @param usuarios servicio de administración
     * @param sesion administrador autenticado
     * @param registro cuenta a editar, o null para crear una nueva
     * @param alGuardar acción de refresco del catálogo
     * @param rolesDisponibles claves existentes para poblar el combo en alta
     */
    public Fr_DatosUsuario(Frame dueno, UsuariosService usuarios,
            UsuarioSesion sesion, UsuarioListado registro, Runnable alGuardar,
            List<String> rolesDisponibles) {
        super(dueno, "Datos del usuario", ModalityType.APPLICATION_MODAL);
        this.usuarios = usuarios;
        this.sesion = sesion;
        this.registro = registro;
        this.alGuardar = alGuardar;
        inicializarComponentes();
        if (rolesDisponibles != null && !rolesDisponibles.isEmpty()) {
            cmbRol.removeAllItems();
            for (String rol : rolesDisponibles) {
                cmbRol.addItem(rol);
            }
            if (rolesDisponibles.contains(UsuariosService.CONSULTA)) {
                cmbRol.setSelectedItem(UsuariosService.CONSULTA);
            }
        }
        if (registro != null) {
            prepararEdicion(registro);
        }
    }

    private void inicializarComponentes() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 570, 420);
        setMinimumSize(new Dimension(520, 310));
        setLocationRelativeTo(getOwner());
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(14, 18, 14, 18));
        setContentPane(contentPane);

        lblTitulo = new JLabel("Nuevo usuario");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 19f));
        lblNombre = new JLabel("Nombre completo");
        lblLogin = new JLabel("Usuario (login)");
        lblCorreo = new JLabel("Correo (opcional)");
        lblRol = new JLabel("Rol inicial");
        lblClave = new JLabel("Contraseña");
        lblConfirmar = new JLabel("Confirmar contraseña");
        lblNota = new JLabel("Login: 3-80 caracteres. Contraseña: 12-128 caracteres.");

        txfNombre = new JTextField(25);
        txfLogin = new JTextField(25);
        txfCorreo = new JTextField(25);
        cmbRol = new JComboBox<>(new String[] {
                UsuariosService.CONSULTA, UsuariosService.ADMIN
        });
        pswfClave = new JPasswordField(25);
        pswfConfirmar = new JPasswordField(25);
        btnGuardar = new JButton("Guardar");
        btnCancelar = new JButton("Cancelar");
        btnGuardar.addActionListener(e -> guardar());
        btnCancelar.addActionListener(e -> dispose());

        GroupLayout groupLayout = new GroupLayout(contentPane);
        contentPane.setLayout(groupLayout);
        groupLayout.setAutoCreateGaps(true);
        groupLayout.setAutoCreateContainerGaps(true);
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblTitulo)
                .addGroup(groupLayout.createSequentialGroup()
                    .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(lblNombre, 170, 170, 170)
                        .addComponent(lblLogin, 170, 170, 170)
                        .addComponent(lblCorreo, 170, 170, 170)
                        .addComponent(lblRol, 170, 170, 170)
                        .addComponent(lblClave, 170, 170, 170)
                        .addComponent(lblConfirmar, 170, 170, 170))
                    .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(txfNombre, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                        .addComponent(txfLogin, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                        .addComponent(txfCorreo, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                        .addComponent(cmbRol, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                        .addComponent(pswfClave, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                        .addComponent(pswfConfirmar, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)))
                .addComponent(lblNota)
                .addGroup(GroupLayout.Alignment.TRAILING, groupLayout.createSequentialGroup()
                    .addComponent(btnCancelar)
                    .addComponent(btnGuardar)));
        groupLayout.setVerticalGroup(
            groupLayout.createSequentialGroup()
                .addComponent(lblTitulo)
                .addGap(10)
                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblNombre)
                    .addComponent(txfNombre, GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblLogin)
                    .addComponent(txfLogin, GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblCorreo)
                    .addComponent(txfCorreo, GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblRol)
                    .addComponent(cmbRol, GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblClave)
                    .addComponent(pswfClave, GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblConfirmar)
                    .addComponent(pswfConfirmar, GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addComponent(lblNota)
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(btnCancelar)
                    .addComponent(btnGuardar)));
        getRootPane().setDefaultButton(btnGuardar);
    }

    private void prepararEdicion(UsuarioListado usuario) {
        setTitle("Modificar usuario");
        lblTitulo.setText("Modificar datos del usuario");
        txfNombre.setText(usuario.nombre());
        txfLogin.setText(usuario.login());
        txfCorreo.setText(usuario.correo() == null ? "" : usuario.correo());

        lblRol.setVisible(false);
        cmbRol.setVisible(false);
        lblClave.setVisible(false);
        pswfClave.setVisible(false);
        lblConfirmar.setVisible(false);
        pswfConfirmar.setVisible(false);
        lblNota.setText("La contraseña y el rol se administran desde el catálogo.");
        setSize(570, 310);
        setLocationRelativeTo(getOwner());
    }

    private void guardar() {
        if (usuarios == null || sesion == null) {
            return;
        }
        String nombre = txfNombre.getText();
        String login = txfLogin.getText();
        String correo = txfCorreo.getText();
        boolean editando = registro != null;

        char[] clave = editando ? new char[0] : pswfClave.getPassword();
        if (!editando) {
            char[] confirmacion = pswfConfirmar.getPassword();
            boolean iguales = Arrays.equals(clave, confirmacion);
            Arrays.fill(confirmacion, '\0');
            if (!iguales) {
                Arrays.fill(clave, '\0');
                JOptionPane.showMessageDialog(this, "Las contraseñas no coinciden.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                pswfClave.setText("");
                pswfConfirmar.setText("");
                return;
            }
        }

        String rol = editando ? null : (String) cmbRol.getSelectedItem();
        btnGuardar.setEnabled(false);
        SwingWorker<Void, Void> tarea = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                if (editando) {
                    usuarios.modificarDatos(sesion.id(), registro.id(), nombre, login, correo);
                } else {
                    usuarios.registrar(sesion.id(),
                            new SolicitudUsuario(nombre, login, correo, rol), clave);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (alGuardar != null) {
                        alGuardar.run();
                    }
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause();
                    JOptionPane.showMessageDialog(Fr_DatosUsuario.this,
                            causa == null ? "No fue posible guardar los datos." : causa.getMessage(),
                            "Error al guardar", JOptionPane.ERROR_MESSAGE);
                } finally {
                    Arrays.fill(clave, '\0');
                    pswfClave.setText("");
                    pswfConfirmar.setText("");
                    btnGuardar.setEnabled(true);
                }
            }
        };
        tarea.execute();
    }
}
