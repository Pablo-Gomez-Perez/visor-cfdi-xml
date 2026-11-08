package com.kathsoft.visor.app.view.usuario;

import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import com.kathsoft.visor.app.model.dto.UsuarioListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.model.entity.UsuarioEstado;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Administración de usuarios. Las restricciones efectivas se aplican en el
 * servicio; el constructor sin parámetros permite editar en WindowBuilder.
 */
public class Fr_Usuarios extends JFrame {

    private static final long serialVersionUID = 1L;

    private UsuariosService usuarios;
    private UsuarioSesion sesion;
    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblEstado;
    private JScrollPane scrollPaneUsuarios;
    private JTable tableUsuarios;
    private DefaultTableModel modelUsuarios;
    private JButton btnNuevo;
    private JButton btnEditar;
    private JButton btnEstado;
    private JButton btnRol;
    private JButton btnContrasena;
    private JButton btnActualizar;
    private JButton btnCerrar;

    /** Constructor seguro para vista previa de Eclipse WindowBuilder. */
    public Fr_Usuarios() {
        inicializarComponentes();
    }

    /**
     * @param usuarios servicio de administración
     * @param sesion sesión del operador
     */
    public Fr_Usuarios(UsuariosService usuarios, UsuarioSesion sesion) {
        this();
        this.usuarios = usuarios;
        this.sesion = sesion;
        SwingUtilities.invokeLater(this::recargar);
    }

    private void inicializarComponentes() {
        setTitle("Visor CFDI XML - Administración de usuarios");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 1030, 560);
        setMinimumSize(new Dimension(850, 490));
        setLocationRelativeTo(null);

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(14, 16, 14, 16));
        setContentPane(contentPane);
        lblTitulo = new JLabel("Administración de usuarios");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        lblEstado = new JLabel("Seleccione un usuario para modificarlo.");
        scrollPaneUsuarios = new JScrollPane();
        tableUsuarios = new JTable();
        modelUsuarios = new DefaultTableModel(
                new Object[] {"ID", "Nombre", "Usuario", "Correo", "Estado", "Rol"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int fila, int columna) { return false; }
            @Override
            public Class<?> getColumnClass(int columna) {
                return columna == 0 ? Long.class : String.class;
            }
        };
        tableUsuarios.setModel(modelUsuarios);
        tableUsuarios.setAutoCreateRowSorter(true);
        tableUsuarios.setRowHeight(27);
        tableUsuarios.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        scrollPaneUsuarios.setViewportView(tableUsuarios);
        btnNuevo = new JButton("Nuevo");
        btnEditar = new JButton("Editar");
        btnEstado = new JButton("Activar / Inactivar");
        btnRol = new JButton("Cambiar rol");
        btnContrasena = new JButton("Restablecer contraseña");
        btnActualizar = new JButton("Actualizar lista");
        btnCerrar = new JButton("Cerrar");

        btnNuevo.addActionListener(e -> {
            if (usuarios != null && sesion != null) {
                Fr_NuevoUsuario ventana = new Fr_NuevoUsuario(this, usuarios, sesion, this::recargar);
                ventana.setVisible(true);
            }
        });
        btnEditar.addActionListener(e -> editar());
        btnEstado.addActionListener(e -> cambiarEstado());
        btnRol.addActionListener(e -> cambiarRol());
        btnContrasena.addActionListener(e -> restablecerContrasena());
        btnActualizar.addActionListener(e -> recargar());
        btnCerrar.addActionListener(e -> dispose());

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblTitulo)
            .addGroup(layout.createSequentialGroup()
                .addComponent(btnNuevo)
                .addComponent(btnEditar)
                .addComponent(btnEstado)
                .addComponent(btnRol)
                .addComponent(btnContrasena))
            .addComponent(scrollPaneUsuarios, GroupLayout.DEFAULT_SIZE, 790, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(lblEstado)
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(btnActualizar)
                .addComponent(btnCerrar)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblTitulo)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(btnNuevo)
                .addComponent(btnEditar)
                .addComponent(btnEstado)
                .addComponent(btnRol)
                .addComponent(btnContrasena))
            .addComponent(scrollPaneUsuarios, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblEstado)
                .addComponent(btnActualizar)
                .addComponent(btnCerrar)));
    }

    private Long idSeleccionado() {
        int fila = tableUsuarios.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione una cuenta de la tabla.");
            return null;
        }
        int indiceModelo = tableUsuarios.convertRowIndexToModel(fila);
        return (Long) modelUsuarios.getValueAt(indiceModelo, 0);
    }

    private void recargar() {
        if (usuarios == null || sesion == null) {
            return;
        }
        lblEstado.setText("Consultando usuarios...");
        btnActualizar.setEnabled(false);
        SwingWorker<List<UsuarioListado>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<UsuarioListado> doInBackground() {
                return usuarios.listar(sesion.id());
            }

            @Override
            protected void done() {
                try {
                    modelUsuarios.setRowCount(0);
                    for (UsuarioListado u : get()) {
                        modelUsuarios.addRow(new Object[] {
                                u.id(), u.nombre(), u.login(), u.correo(), u.estado(), u.rol()
                        });
                    }
                    lblEstado.setText(modelUsuarios.getRowCount() + " usuario(s) registrado(s).");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                } finally {
                    btnActualizar.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * Abre un formulario editable en Eclipse WindowBuilder para los tres
     * atributos de cuenta autorizados: nombre, login y correo.
     */
    private void editar() {
        Long id = idSeleccionado();
        if (id == null || usuarios == null || sesion == null) {
            return;
        }
        int fila = tableUsuarios.convertRowIndexToModel(tableUsuarios.getSelectedRow());
        UsuarioListado seleccionado = new UsuarioListado(
                id,
                (String) modelUsuarios.getValueAt(fila, 1),
                (String) modelUsuarios.getValueAt(fila, 2),
                (String) modelUsuarios.getValueAt(fila, 3),
                (String) modelUsuarios.getValueAt(fila, 4),
                (String) modelUsuarios.getValueAt(fila, 5));
        new Fr_EditarUsuario(this, usuarios, sesion, seleccionado, this::recargar)
                .setVisible(true);
    }

    private void cambiarEstado() {
        Long id = idSeleccionado();
        if (id == null) return;
        int fila = tableUsuarios.convertRowIndexToModel(tableUsuarios.getSelectedRow());
        String estado = (String) modelUsuarios.getValueAt(fila, 4);
        UsuarioEstado nuevo = "ACTIVO".equals(estado) ? UsuarioEstado.INACTIVO : UsuarioEstado.ACTIVO;
        if (JOptionPane.showConfirmDialog(this,
                "¿Cambiar el estado del usuario " + id + " a " + nuevo + "?",
                "Confirmar cambio", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            ejecutarOperacion(() -> usuarios.cambiarEstado(sesion.id(), id, nuevo));
        }
    }

    private void cambiarRol() {
        Long id = idSeleccionado();
        if (id == null) return;
        String rol = (String) JOptionPane.showInputDialog(this, "Seleccione el rol básico:",
                "Cambiar rol", JOptionPane.QUESTION_MESSAGE, null,
                new String[] {UsuariosService.ADMIN, UsuariosService.CONSULTA},
                UsuariosService.CONSULTA);
        if (rol != null) {
            ejecutarOperacion(() -> usuarios.cambiarRol(sesion.id(), id, rol));
        }
    }

    private void restablecerContrasena() {
        Long id = idSeleccionado();
        if (id == null) return;
        javax.swing.JPasswordField campo = new javax.swing.JPasswordField(22);
        if (JOptionPane.showConfirmDialog(this, campo, "Nueva contraseña (mínimo 12 caracteres)",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            campo.setText("");
            return;
        }
        char[] clave = campo.getPassword();
        campo.setText("");
        btnContrasena.setEnabled(false);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                usuarios.cambiarContrasena(sesion.id(), id, clave);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(Fr_Usuarios.this, "Contraseña actualizada.");
                    recargar();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                } finally {
                    java.util.Arrays.fill(clave, '\0');
                    btnContrasena.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void ejecutarOperacion(Runnable operacion) {
        btnActualizar.setEnabled(false);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                operacion.run();
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    recargar();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    mostrarError(ex);
                } finally {
                    btnActualizar.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void mostrarError(ExecutionException ex) {
        Throwable causa = ex.getCause();
        lblEstado.setText("La operación no pudo completarse.");
        JOptionPane.showMessageDialog(this, causa == null ? "Error inesperado" : causa.getMessage(),
                "Error de administración", JOptionPane.ERROR_MESSAGE);
    }
}
