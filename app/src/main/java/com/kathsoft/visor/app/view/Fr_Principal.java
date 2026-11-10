package com.kathsoft.visor.app.view;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import com.kathsoft.visor.app.view.usuario.Fr_Login;
import com.kathsoft.visor.app.view.usuario.PanelUsuarios;

/**
 * Ventana principal del programa: JMenuBar, accesos rápidos y un solo
 * contenedor CardLayout para los módulos de listado y consulta.
 *
 * <p>Reproduce el patrón de Fr_principal de Kath ERP sin abrir JFrames para
 * listados. El constructor sin argumentos no consulta servicios ni MySQL
 * y permite editar la ventana mediante Eclipse WindowBuilder.</p>
 */
public class Fr_Principal extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String CARD_INICIO = "panelInicio";
    private static final String CARD_USUARIOS = "panelUsuarios";

    private UsuariosService usuarios;
    private UsuarioSesion sesion;

    private JMenuBar barraMenu;
    private JMenu menuInicio;
    private JMenu menuAdministracion;
    private JMenu menuCfdi;
    private JMenu menuSesion;
    private JMenuItem opcionInicio;
    private JMenuItem opcionUsuarios;
    private JMenuItem opcionAdministrarXml;
    private JMenuItem opcionCargarXml;
    private JMenuItem opcionCerrarSesion;
    private JMenuItem opcionSalir;

    private JPanel contentPane;
    private JPanel panelSuperiorBotones;
    private JPanel panelPrincipalContenedor;
    private JPanel panelEstado;
    private PanelInicio panelInicio;
    private PanelUsuarios panelUsuarios;
    private JButton btnInicio;
    private JButton btnAdministrarXml;
    private JButton btnCargarXml;
    private JLabel lblEstadoSesion;
    private CardLayout cardLayout;

    /**
     * Construye exclusivamente la interfaz visual para WindowBuilder.
     */
    public Fr_Principal() {
        inicializarComponentes();
    }

    /**
     * Inicia la navegación de la aplicación con un usuario autenticado.
     *
     * @param usuarios servicio de usuarios
     * @param sesion identidad autenticada de la aplicación
     */
    public Fr_Principal(UsuariosService usuarios, UsuarioSesion sesion) {
        this();
        this.usuarios = usuarios;
        this.sesion = sesion;
        if (sesion != null) {
            lblEstadoSesion.setText("Sesión: " + sesion.nombre() + " (" + sesion.login() + ")");
            boolean admin = sesion.esAdministrador();
            opcionUsuarios.setEnabled(admin);
            panelUsuarios.configurar(usuarios, sesion);
        }
    }

    private void inicializarComponentes() {
        setTitle("Visor CFDI XML");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 860, 600);
        setMinimumSize(new Dimension(740, 490));
        setLocationRelativeTo(null);

        barraMenu = new JMenuBar();
        setJMenuBar(barraMenu);

        menuInicio = new JMenu("Inicio");
        barraMenu.add(menuInicio);
        opcionInicio = new JMenuItem("Ir al inicio");
        menuInicio.add(opcionInicio);
        opcionInicio.addActionListener(e -> mostrarInicio());

        menuAdministracion = new JMenu("Administración");
        barraMenu.add(menuAdministracion);
        opcionUsuarios = new JMenuItem("Administrar usuarios");
        opcionUsuarios.setEnabled(false);
        menuAdministracion.add(opcionUsuarios);
        opcionUsuarios.addActionListener(e -> mostrarUsuarios());

        menuCfdi = new JMenu("CFDI");
        barraMenu.add(menuCfdi);
        opcionAdministrarXml = new JMenuItem("Administrar XML");
        opcionAdministrarXml.setEnabled(false);
        opcionAdministrarXml.setToolTipText("Disponible cuando se implemente el módulo de CFDI");
        menuCfdi.add(opcionAdministrarXml);
        opcionCargarXml = new JMenuItem("Cargar XML");
        opcionCargarXml.setEnabled(false);
        opcionCargarXml.setToolTipText("Disponible cuando se implemente la importación XML");
        menuCfdi.add(opcionCargarXml);

        menuSesion = new JMenu("Sesión");
        barraMenu.add(menuSesion);
        opcionCerrarSesion = new JMenuItem("Cerrar sesión");
        opcionCerrarSesion.addActionListener(e -> cerrarSesion());
        menuSesion.add(opcionCerrarSesion);
        opcionSalir = new JMenuItem("Salir");
        opcionSalir.addActionListener(e -> dispose());
        menuSesion.add(opcionSalir);

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 6, 5, 6));
        setContentPane(contentPane);

        panelSuperiorBotones = new JPanel();
        panelSuperiorBotones.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnInicio = new JButton("Inicio");
        btnInicio.addActionListener(e -> mostrarInicio());
        panelSuperiorBotones.add(btnInicio);
        btnAdministrarXml = new JButton("Administrar XML");
        btnAdministrarXml.setEnabled(false);
        btnAdministrarXml.setToolTipText("Módulo de consulta de CFDI pendiente");
        panelSuperiorBotones.add(btnAdministrarXml);
        btnCargarXml = new JButton("Cargar XML");
        btnCargarXml.setEnabled(false);
        btnCargarXml.setToolTipText("Módulo de importación de CFDI pendiente");
        panelSuperiorBotones.add(btnCargarXml);

        panelPrincipalContenedor = new JPanel();
        cardLayout = new CardLayout(0, 0);
        panelPrincipalContenedor.setLayout(cardLayout);
        panelInicio = new PanelInicio();
        panelPrincipalContenedor.add(panelInicio, CARD_INICIO);
        panelUsuarios = new PanelUsuarios();
        panelPrincipalContenedor.add(panelUsuarios, CARD_USUARIOS);
        cardLayout.show(panelPrincipalContenedor, CARD_INICIO);

        panelEstado = new JPanel();
        panelEstado.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 2));
        lblEstadoSesion = new JLabel("Vista previa de diseño - sin sesión activa");
        lblEstadoSesion.setFont(lblEstadoSesion.getFont().deriveFont(Font.PLAIN, 11f));
        panelEstado.add(lblEstadoSesion);

        GroupLayout gl_contentPane = new GroupLayout(contentPane);
        contentPane.setLayout(gl_contentPane);
        gl_contentPane.setAutoCreateGaps(true);
        gl_contentPane.setHorizontalGroup(
            gl_contentPane.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(panelSuperiorBotones, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(panelPrincipalContenedor, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(panelEstado, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        gl_contentPane.setVerticalGroup(
            gl_contentPane.createSequentialGroup()
                .addComponent(panelSuperiorBotones, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(panelPrincipalContenedor, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(panelEstado, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
    }

    /**
     * Muestra la vista principal sin generar nuevas ventanas.
     */
    private void mostrarInicio() {
        cardLayout.show(panelPrincipalContenedor, CARD_INICIO);
    }

    /**
     * Muestra el catálogo de usuarios dentro de CardLayout y actualiza datos.
     */
    private void mostrarUsuarios() {
        if (sesion != null && sesion.esAdministrador() && usuarios != null) {
            cardLayout.show(panelPrincipalContenedor, CARD_USUARIOS);
            panelUsuarios.recargar();
        }
    }

    /**
     * Cierra solo la sesión de la aplicación conservando la conexión MySQL.
     */
    private void cerrarSesion() {
        if (usuarios == null) {
            dispose();
            return;
        }
        Fr_Login login = new Fr_Login(usuarios);
        login.setVisible(true);
        dispose();
    }
}
