package com.kathsoft.visor.app.view.rol;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Font;
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

import com.kathsoft.visor.app.model.dto.RolListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.rol.RolesService;

/**
 * Alta y modificación de roles en un solo formulario de detalle.
 *
 * <p>GroupLayout explícito, campos Swing declarados y constructor público
 * vacío: totalmente desacoplado de MySQL para la vista de WindowBuilder.</p>
 */
public class Fr_DatosRol extends JDialog {

    private static final long serialVersionUID = 1L;

    private RolesService roles;
    private UsuarioSesion sesion;
    private RolListado registro;
    private Runnable alGuardar;

    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblClave;
    private JLabel lblDescripcion;
    private JLabel lblNota;
    private JTextField txfClave;
    private JTextField txfDescripcion;
    private JButton btnGuardar;
    private JButton btnCancelar;

    /** Constructor sin servicios para el diseñador Eclipse WindowBuilder. */
    public Fr_DatosRol() {
        this(null, null, null, null, null);
    }

    /**
     * Prepara el formulario de alta o edición de un registro de rol.
     *
     * @param dueno JFrame principal
     * @param roles servicio transaccional
     * @param sesion operador autenticado
     * @param registro rol seleccionado; null para creación
     * @param alGuardar callback para actualizar el listado
     */
    public Fr_DatosRol(Frame dueno, RolesService roles, UsuarioSesion sesion,
            RolListado registro, Runnable alGuardar) {
        super(dueno, "Datos del rol", ModalityType.APPLICATION_MODAL);
        this.roles = roles;
        this.sesion = sesion;
        this.registro = registro;
        this.alGuardar = alGuardar;
        inicializarComponentes();
        if (registro != null) {
            lblTitulo.setText("Modificar rol");
            txfClave.setText(registro.clave());
            txfClave.setEditable(false);
            txfDescripcion.setText(registro.descripcion());
            lblNota.setText("La clave del rol no puede cambiarse tras su creación.");
            setTitle("Modificar rol");
        }
    }

    private void inicializarComponentes() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 585, 315);
        setMinimumSize(new Dimension(500, 300));
        setLocationRelativeTo(getOwner());

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(14, 18, 14, 18));
        setContentPane(contentPane);
        lblTitulo = new JLabel("Nuevo rol");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 20f));
        lblClave = new JLabel("Clave del rol");
        lblDescripcion = new JLabel("Descripción");
        lblNota = new JLabel("Clave: 3-40 letras, números y guion bajo. No se edita después.");
        txfClave = new JTextField(22);
        txfDescripcion = new JTextField(22);
        btnGuardar = new JButton("Guardar");
        btnCancelar = new JButton("Cancelar");
        btnGuardar.addActionListener(e -> guardar());
        btnCancelar.addActionListener(e -> dispose());

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblTitulo)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(lblClave, 135, 135, 135)
                    .addComponent(lblDescripcion, 135, 135, 135))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(txfClave, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                    .addComponent(txfDescripcion, GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)))
            .addComponent(lblNota)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(btnCancelar)
                .addComponent(btnGuardar)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblTitulo)
            .addGap(8)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblClave)
                .addComponent(txfClave, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblDescripcion)
                .addComponent(txfDescripcion, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addComponent(lblNota)
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(btnCancelar)
                .addComponent(btnGuardar)));
        getRootPane().setDefaultButton(btnGuardar);
    }

    private void guardar() {
        if (roles == null || sesion == null) {
            return;
        }
        String clave = txfClave.getText();
        String descripcion = txfDescripcion.getText();
        btnGuardar.setEnabled(false);
        SwingWorker<Void, Void> tarea = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                if (registro == null) {
                    roles.crear(sesion.id(), clave, descripcion);
                } else {
                    roles.modificar(sesion.id(), registro.id(), descripcion);
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
                    JOptionPane.showMessageDialog(Fr_DatosRol.this,
                            causa == null ? "No se pudo guardar el rol" : causa.getMessage(),
                            "Error al guardar rol", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnGuardar.setEnabled(true);
                }
            }
        };
        tarea.execute();
    }
}
