package com.kathsoft.visor.app.view;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.rol.RolesService;
import com.kathsoft.visor.app.service.contribuyente.ContribuyentesService;
import com.kathsoft.visor.app.view.contribuyente.PanelContribuyentes;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import com.kathsoft.visor.app.view.rol.PanelRoles;
import com.kathsoft.visor.app.view.usuario.Fr_Login;
import com.kathsoft.visor.app.view.usuario.PanelUsuarios;

/**
 * Ventana principal: un JFrame con JMenuBar, accesos rápidos y CardLayout.
 *
 * <p>Los módulos RF-002 y RF-003 son paneles independientes editables desde
 * WindowBuilder, con GroupLayout. La autorización del menú es indicativa:
 * los servicios siempre vuelven a comprobar permisos en MySQL.</p>
 */
public class Fr_Principal extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String CARD_INICIO = "panelInicio";
    private static final String CARD_USUARIOS = "panelUsuarios";
    private static final String CARD_ROLES = "panelRoles";
    private static final String CARD_CONTRIBUYENTES = "panelContribuyentes";

    private UsuariosService usuarios;
    private RolesService roles;
    private ContribuyentesService contribuyentes;
    private UsuarioSesion sesion;

    private JMenuBar barraMenu;
    private JMenu menuInicio;
    private JMenu menuAdministracion;
    private JMenu menuCfdi;
    private JMenu menuSesion;
    private JMenuItem opcionInicio;
    private JMenuItem opcionUsuarios;
    private JMenuItem opcionRoles;
    private JMenuItem opcionContribuyentes;
    private JMenuItem opcionAdministrarXml;
    private JMenuItem opcionCargarXml;
    private JMenuItem opcionActualizarPermisos;
    private JMenuItem opcionCerrarSesion;
    private JMenuItem opcionSalir;

    private JPanel contentPane;
    private JPanel panelSuperiorBotones;
    private JPanel panelPrincipalContenedor;
    private JPanel panelEstado;
    private PanelInicio panelInicio;
    private PanelUsuarios panelUsuarios;
    private PanelRoles panelRoles;
    private PanelContribuyentes panelContribuyentes;
    private JButton btnInicio;
    private JButton btnContribuyentes;
    private JButton btnAdministrarXml;
    private JButton btnCargarXml;
    private JLabel lblEstadoSesion;
    private CardLayout cardLayout;

    /**
     * Constructor vacío; únicamente crea Swing para Eclipse WindowBuilder.
     */
    public Fr_Principal() {
        inicializarComponentes();
    }

    /**
     * Inicializa una sesión; permisos efectivos se consultan en SwingWorker.
     *
     * @param usuarios servicio de usuarios
     * @param sesion identidad autenticada
     */
    public Fr_Principal(UsuariosService usuarios, UsuarioSesion sesion) {
        this();
        this.usuarios = usuarios;
        this.roles = usuarios == null ? null : usuarios.roles();
        this.contribuyentes = usuarios == null ? null : usuarios.contribuyentes();
        this.sesion = sesion;
        if (sesion != null) {
            lblEstadoSesion.setText("Sesión: " + sesion.nombre() + " (" + sesion.login() + ")");
        }
        SwingUtilities.invokeLater(() -> actualizarPermisos(null));
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
        opcionUsuarios.addActionListener(e -> actualizarPermisos(CARD_USUARIOS));
        opcionRoles = new JMenuItem("Roles y permisos");
        opcionRoles.setEnabled(false);
        menuAdministracion.add(opcionRoles);
        opcionRoles.addActionListener(e -> actualizarPermisos(CARD_ROLES));
        opcionContribuyentes = new JMenuItem("Administrar contribuyentes");
        opcionContribuyentes.setEnabled(false);
        menuAdministracion.add(opcionContribuyentes);
        opcionContribuyentes.addActionListener(e -> actualizarPermisos(CARD_CONTRIBUYENTES));

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
        opcionActualizarPermisos = new JMenuItem("Actualizar accesos");
        opcionActualizarPermisos.addActionListener(e -> actualizarPermisos(null));
        menuSesion.add(opcionActualizarPermisos);
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
        btnContribuyentes = new JButton("Contribuyentes");
        btnContribuyentes.setEnabled(false);
        btnContribuyentes.addActionListener(e -> actualizarPermisos(CARD_CONTRIBUYENTES));
        panelSuperiorBotones.add(btnContribuyentes);
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
        panelRoles = new PanelRoles();
        panelPrincipalContenedor.add(panelRoles, CARD_ROLES);
        panelContribuyentes = new PanelContribuyentes();
        panelPrincipalContenedor.add(panelContribuyentes, CARD_CONTRIBUYENTES);
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

    /** Muestra Inicio sin crear otra ventana. */
    private void mostrarInicio() {
        cardLayout.show(panelPrincipalContenedor, CARD_INICIO);
        if (usuarios != null) {
            actualizarPermisos(null);
        }
    }

    /**
     * Reconsulta los privilegios efectivos sin bloquear Swing y reconfigura
     * ambos paneles. El backend los valida nuevamente en cada operación.
     *
     * @param destino tarjeta requerida, o null para solo refrescar menús
     */
    private void actualizarPermisos(String destino) {
        if (sesion == null || usuarios == null || roles == null || contribuyentes == null) {
            return;
        }
        opcionActualizarPermisos.setEnabled(false);
        SwingWorker<Set<String>, Void> tarea = new SwingWorker<>() {
            @Override
            protected Set<String> doInBackground() {
                return usuarios.permisosDe(sesion.id());
            }
            @Override
            protected void done() {
                try {
                    Set<String> permisos = get();
                    boolean verUsuarios = permisos.contains(Permisos.USUARIOS_CONSULTAR);
                    boolean verRoles = permisos.stream().anyMatch(p -> p.startsWith("ROLES."));
                    boolean verContribuyentes = permisos.contains(Permisos.CONTRIBUYENTES_CONSULTAR);
                    opcionUsuarios.setEnabled(verUsuarios);
                    opcionRoles.setEnabled(verRoles);
                    opcionContribuyentes.setEnabled(verContribuyentes);
                    btnContribuyentes.setEnabled(verContribuyentes);
                    panelUsuarios.configurar(usuarios, sesion, permisos);
                    panelRoles.configurar(roles, sesion, permisos);
                    panelContribuyentes.configurar(contribuyentes, sesion, permisos);
                    if (CARD_USUARIOS.equals(destino)) {
                        if (verUsuarios) {
                            cardLayout.show(panelPrincipalContenedor, CARD_USUARIOS);
                            panelUsuarios.recargar();
                        } else {
                            JOptionPane.showMessageDialog(Fr_Principal.this,
                                    "No tiene permiso para consultar usuarios.");
                        }
                    } else if (CARD_ROLES.equals(destino)) {
                        if (verRoles) {
                            cardLayout.show(panelPrincipalContenedor, CARD_ROLES);
                            panelRoles.recargar();
                        } else {
                            JOptionPane.showMessageDialog(Fr_Principal.this,
                                    "No tiene permiso para acceder al catálogo de roles.");
                        }
                    } else if (CARD_CONTRIBUYENTES.equals(destino)) {
                        if (verContribuyentes) {
                            cardLayout.show(panelPrincipalContenedor, CARD_CONTRIBUYENTES);
                            panelContribuyentes.recargar();
                        } else {
                            JOptionPane.showMessageDialog(Fr_Principal.this,
                                    "No tiene permiso para consultar contribuyentes.");
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    opcionUsuarios.setEnabled(false);
                    opcionRoles.setEnabled(false);
                    opcionContribuyentes.setEnabled(false);
                    btnContribuyentes.setEnabled(false);
                    JOptionPane.showMessageDialog(Fr_Principal.this,
                            "No se pudieron actualizar los permisos. Revise la conexión MySQL.",
                            "Error de permisos", JOptionPane.ERROR_MESSAGE);
                } finally {
                    opcionActualizarPermisos.setEnabled(true);
                }
            }
        };
        tarea.execute();
    }

    /**
     * Cierra la sesión del usuario sin finalizar Hibernate ni crear otro
     * JFrame de catálogo.
     */
    private void cerrarSesion() {
        if (usuarios == null) {
            dispose();
            return;
        }
        new Fr_Login(usuarios).setVisible(true);
        dispose();
    }
}
