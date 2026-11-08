package com.kathsoft.visor.app.view.usuario;

import java.awt.Dimension;
import java.awt.Frame;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.dto.UsuarioListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Modifica los datos públicos de una cuenta, incluido su login de acceso.
 *
 * <p>Constructor vacío y GroupLayout explícito para Eclipse WindowBuilder.
 * Las operaciones de persistencia se delegan al servicio mediante SwingWorker.
 * El identificador interno de la cuenta (PK) no se edita.</p>
 */
public class Fr_EditarUsuario extends JDialog {

    private static final long serialVersionUID = 1L;

    private UsuariosService usuarios;
    private UsuarioSesion sesion;
    private UsuarioListado seleccionado;
    private Runnable alGuardar;

    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblNombre;
    private JLabel lblLogin;
    private JLabel lblCorreo;
    private JLabel lblNota;
    private JTextField txfNombre;
    private JTextField txfLogin;
    private JTextField txfCorreo;
    private JButton btnGuardar;
    private JButton btnCancelar;

    /** Constructor sin servicios para el diseñador WindowBuilder. */
    public Fr_EditarUsuario() {
        this(null, null, null, null, null);
    }

    /**
     * Abre el diálogo con los datos de la cuenta seleccionada.
     *
     * @param dueno ventana administradora de usuarios
     * @param usuarios servicio de administración
     * @param sesion operador autenticado
     * @param seleccionado cuenta seleccionada para edición
     * @param alGuardar callback para actualizar la lista tras guardar
     */
    public Fr_EditarUsuario(Frame dueno, UsuariosService usuarios, UsuarioSesion sesion,
            UsuarioListado seleccionado, Runnable alGuardar) {
        super(dueno, "Modificar usuario", ModalityType.APPLICATION_MODAL);
        this.usuarios = usuarios;
        this.sesion = sesion;
        this.seleccionado = seleccionado;
        this.alGuardar = alGuardar;
        inicializarComponentes();
        if (seleccionado != null) {
            txfNombre.setText(seleccionado.nombre());
            txfLogin.setText(seleccionado.login());
            txfCorreo.setText(seleccionado.correo() == null ? "" : seleccionado.correo());
        }
    }

    /**
     * Construye únicamente componentes Swing con GroupLayout.
     */
    private void inicializarComponentes() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 610, 345);
        setMinimumSize(new Dimension(550, 320));
        setLocationRelativeTo(getOwner());

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(14, 18, 14, 18));
        setContentPane(contentPane);

        lblTitulo = new JLabel("Modificar datos del usuario");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(java.awt.Font.BOLD, 19f));
        lblNombre = new JLabel("Nombre completo");
        lblLogin = new JLabel("Usuario (login)");
        lblCorreo = new JLabel("Correo (opcional)");
        lblNota = new JLabel("El nuevo login se utilizará en el siguiente inicio de sesión.");

        txfNombre = new JTextField(25);
        txfLogin = new JTextField(25);
        txfCorreo = new JTextField(25);

        btnGuardar = new JButton("Guardar");
        btnCancelar = new JButton("Cancelar");
        btnGuardar.addActionListener(e -> guardar());
        btnCancelar.addActionListener(e -> dispose());

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblTitulo)
                .addGroup(layout.createSequentialGroup()
                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(lblNombre, 155, 155, 155)
                        .addComponent(lblLogin, 155, 155, 155)
                        .addComponent(lblCorreo, 155, 155, 155))
                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(txfNombre, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                        .addComponent(txfLogin, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                        .addComponent(txfCorreo, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)))
                .addComponent(lblNota)
                .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                    .addComponent(btnCancelar)
                    .addComponent(btnGuardar)));
        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addComponent(lblTitulo)
                .addGap(10)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblNombre)
                    .addComponent(txfNombre, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblLogin)
                    .addComponent(txfLogin, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(lblCorreo)
                    .addComponent(txfCorreo, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addComponent(lblNota)
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(btnCancelar)
                    .addComponent(btnGuardar)));
        getRootPane().setDefaultButton(btnGuardar);
    }

    /**
     * Solicita al servicio una modificación atómica sin bloquear Swing.
     */
    private void guardar() {
        if (usuarios == null || sesion == null || seleccionado == null) {
            return;
        }
        String nombre = txfNombre.getText();
        String login = txfLogin.getText();
        String correo = txfCorreo.getText();
        btnGuardar.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                usuarios.modificarDatos(sesion.id(), seleccionado.id(), nombre, login, correo);
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
                    JOptionPane.showMessageDialog(Fr_EditarUsuario.this,
                            causa == null ? "No fue posible modificar el usuario." : causa.getMessage(),
                            "Error al modificar usuario", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnGuardar.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
