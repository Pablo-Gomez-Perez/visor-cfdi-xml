package com.kathsoft.visor.app.view.usuario;

import java.awt.Dimension;
import java.awt.Frame;
import java.util.Arrays;
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
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Alta de una cuenta en RF-002. Usa componentes Swing y GroupLayout explícito.
 */
public class Fr_NuevoUsuario extends JDialog {

    private static final long serialVersionUID = 1L;
    private UsuariosService usuarios;
    private UsuarioSesion sesion;
    private Runnable alGuardar;

    private JPanel contentPane;
    private JLabel lblNombre;
    private JLabel lblUsuario;
    private JLabel lblCorreo;
    private JLabel lblRol;
    private JLabel lblClave;
    private JLabel lblRepetir;
    private JTextField txfNombre;
    private JTextField txfUsuario;
    private JTextField txfCorreo;
    private JComboBox<String> cmbRol;
    private JPasswordField pswfClave;
    private JPasswordField pswfRepetir;
    private JButton btnGuardar;
    private JButton btnCancelar;

    /** Constructor vacío para Eclipse WindowBuilder. */
    public Fr_NuevoUsuario() {
        this(null, null, null, null);
    }

    /**
     * @param dueno ventana que abre el formulario
     * @param usuarios servicio de administración
     * @param sesion administrador autenticado
     * @param alGuardar actualización de la lista al terminar
     */
    public Fr_NuevoUsuario(Frame dueno, UsuariosService usuarios, UsuarioSesion sesion, Runnable alGuardar) {
        super(dueno, "Registrar usuario", ModalityType.APPLICATION_MODAL);
        this.usuarios = usuarios;
        this.sesion = sesion;
        this.alGuardar = alGuardar;
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 580, 425);
        setMinimumSize(new Dimension(530, 400));
        setLocationRelativeTo(getOwner());
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(14, 18, 14, 18));
        setContentPane(contentPane);

        lblNombre = new JLabel("Nombre completo");
        lblUsuario = new JLabel("Usuario");
        lblCorreo = new JLabel("Correo (opcional)");
        lblRol = new JLabel("Rol");
        lblClave = new JLabel("Contraseña");
        lblRepetir = new JLabel("Confirmar contraseña");
        txfNombre = new JTextField(20);
        txfUsuario = new JTextField(20);
        txfCorreo = new JTextField(20);
        cmbRol = new JComboBox<>(new String[] {UsuariosService.CONSULTA, UsuariosService.ADMIN});
        pswfClave = new JPasswordField(20);
        pswfRepetir = new JPasswordField(20);
        btnGuardar = new JButton("Guardar");
        btnCancelar = new JButton("Cancelar");
        btnGuardar.addActionListener(e -> guardar());
        btnCancelar.addActionListener(e -> dispose());

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(lblNombre, 155, 155, 155)
                    .addComponent(lblUsuario, 155, 155, 155)
                    .addComponent(lblCorreo, 155, 155, 155)
                    .addComponent(lblRol, 155, 155, 155)
                    .addComponent(lblClave, 155, 155, 155)
                    .addComponent(lblRepetir, 155, 155, 155))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(txfNombre, GroupLayout.DEFAULT_SIZE, 260, Short.MAX_VALUE)
                    .addComponent(txfUsuario, GroupLayout.DEFAULT_SIZE, 260, Short.MAX_VALUE)
                    .addComponent(txfCorreo, GroupLayout.DEFAULT_SIZE, 260, Short.MAX_VALUE)
                    .addComponent(cmbRol, GroupLayout.DEFAULT_SIZE, 260, Short.MAX_VALUE)
                    .addComponent(pswfClave, GroupLayout.DEFAULT_SIZE, 260, Short.MAX_VALUE)
                    .addComponent(pswfRepetir, GroupLayout.DEFAULT_SIZE, 260, Short.MAX_VALUE)))
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(btnCancelar)
                .addComponent(btnGuardar)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblNombre)
                .addComponent(txfNombre, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblUsuario)
                .addComponent(txfUsuario, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblCorreo)
                .addComponent(txfCorreo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblRol)
                .addComponent(cmbRol, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblClave)
                .addComponent(pswfClave, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblRepetir)
                .addComponent(pswfRepetir, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(btnCancelar)
                .addComponent(btnGuardar)));
        getRootPane().setDefaultButton(btnGuardar);
    }

    private void guardar() {
        if (usuarios == null || sesion == null) return;
        char[] clave = pswfClave.getPassword();
        char[] confirmacion = pswfRepetir.getPassword();
        if (!Arrays.equals(clave, confirmacion)) {
            JOptionPane.showMessageDialog(this, "Las contraseñas no coinciden.");
            Arrays.fill(clave, '\0');
            Arrays.fill(confirmacion, '\0');
            return;
        }
        Arrays.fill(confirmacion, '\0');
        SolicitudUsuario solicitud = new SolicitudUsuario(txfNombre.getText(), txfUsuario.getText(),
                txfCorreo.getText(), (String) cmbRol.getSelectedItem());
        btnGuardar.setEnabled(false);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                usuarios.registrar(sesion.id(), solicitud, clave);
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
                    JOptionPane.showMessageDialog(Fr_NuevoUsuario.this,
                            causa == null ? "No se pudo crear el usuario." : causa.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    Arrays.fill(clave, '\0');
                    pswfClave.setText("");
                    pswfRepetir.setText("");
                    btnGuardar.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
