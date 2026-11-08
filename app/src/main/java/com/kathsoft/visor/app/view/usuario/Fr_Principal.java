package com.kathsoft.visor.app.view.usuario;

import java.awt.Dimension;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.usuario.UsuariosService;

/**
 * Contenedor inicial tras autenticación. El tablero CFDI no pertenece a RF-002.
 */
public class Fr_Principal extends JFrame {

    private static final long serialVersionUID = 1L;

    private UsuariosService usuarios;
    private UsuarioSesion sesion;
    private JPanel contentPane;
    private JLabel lblTitulo;
    private JLabel lblSesion;
    private JLabel lblProximamente;
    private JButton btnUsuarios;
    private JButton btnSalir;

    /** Constructor de vista previa independiente de la persistencia. */
    public Fr_Principal() {
        inicializarComponentes();
    }

    /**
     * @param usuarios servicio con sesión activa
     * @param sesion usuario autenticado
     */
    public Fr_Principal(UsuariosService usuarios, UsuarioSesion sesion) {
        this();
        this.usuarios = usuarios;
        this.sesion = sesion;
        lblSesion.setText("Sesión: " + sesion.nombre() + " (" + sesion.login() + ")");
        btnUsuarios.setVisible(sesion.esAdministrador());
    }

    private void inicializarComponentes() {
        setTitle("Visor CFDI XML");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 690, 335);
        setMinimumSize(new Dimension(580, 300));
        setLocationRelativeTo(null);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(18, 22, 18, 22));
        setContentPane(contentPane);
        lblTitulo = new JLabel("Visor CFDI XML");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(java.awt.Font.BOLD, 22f));
        lblSesion = new JLabel("Sin sesión (vista previa WindowBuilder)");
        lblProximamente = new JLabel("La importación y consulta de CFDI se agregarán en próximos módulos.");
        btnUsuarios = new JButton("Administración de usuarios");
        btnSalir = new JButton("Cerrar sesión");
        btnUsuarios.addActionListener(e -> {
            if (usuarios != null && sesion != null && sesion.esAdministrador()) {
                new Fr_Usuarios(usuarios, sesion).setVisible(true);
            }
        });
        btnSalir.addActionListener(e -> {
            if (usuarios != null) {
                new Fr_Login(usuarios).setVisible(true);
                dispose();
            }
        });

        GroupLayout layout = new GroupLayout(contentPane);
        contentPane.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(lblTitulo)
            .addComponent(lblSesion)
            .addComponent(lblProximamente)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(btnUsuarios)
                .addComponent(btnSalir)));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(lblTitulo)
            .addComponent(lblSesion)
            .addGap(25)
            .addComponent(lblProximamente)
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(btnUsuarios)
                .addComponent(btnSalir)));
    }
}
