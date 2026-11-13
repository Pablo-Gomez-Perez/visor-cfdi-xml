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
import com.kathsoft.visor.app.service.tercero.TercerosService;
import com.kathsoft.visor.app.view.tercero.PanelTerceros;
import com.kathsoft.visor.app.service.periodo.PeriodosFiscalesService;
import com.kathsoft.visor.app.view.periodo.PanelPeriodosFiscales;
import com.kathsoft.visor.app.service.importacion.ImportacionCfdiService;
import com.kathsoft.visor.app.view.importacion.PanelImportacionCfdi;
import com.kathsoft.visor.app.view.validacion.PanelValidacionTecnica;
import com.kathsoft.visor.app.service.validacion.ValidacionTecnicaService;
import com.kathsoft.visor.app.service.clasificacion.ClasificacionCfdiService;
import com.kathsoft.visor.app.view.clasificacion.PanelClasificacionCfdi;
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
    private static final String CARD_TERCEROS = "panelTerceros";
    private static final String CARD_PERIODOS = "panelPeriodos";
    private static final String CARD_IMPORTACION = "panelImportacion";
    private static final String CARD_VALIDACION = "panelValidacion";
    private static final String CARD_CLASIFICACION = "panelClasificacion";

    private UsuariosService usuarios;
    private RolesService roles;
    private ContribuyentesService contribuyentes;
    private TercerosService terceros;
    private PeriodosFiscalesService periodos;
    private ImportacionCfdiService importaciones;
    private ValidacionTecnicaService validaciones;
    private ClasificacionCfdiService clasificaciones;
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
    private JMenuItem opcionTerceros;
    private JMenuItem opcionPeriodos;
    private JMenuItem opcionAdministrarXml;
    private JMenuItem opcionCargarXml;
    private JMenuItem opcionValidarXml;
    private JMenuItem opcionClasificacion;
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
    private PanelTerceros panelTerceros;
    private PanelPeriodosFiscales panelPeriodos;
    private PanelImportacionCfdi panelImportacion;
    private PanelValidacionTecnica panelValidacion;
    private PanelClasificacionCfdi panelClasificacion;
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
        this.terceros = usuarios == null ? null : usuarios.terceros();
        this.periodos = usuarios == null ? null : usuarios.periodosFiscales();
        this.importaciones = usuarios == null ? null : usuarios.importaciones();
        this.validaciones = usuarios == null ? null : usuarios.validacionesTecnicas();
        this.clasificaciones = usuarios == null ? null : usuarios.clasificaciones();
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

        opcionTerceros = new JMenuItem("Administrar terceros");
        opcionTerceros.setEnabled(false);
        menuAdministracion.add(opcionTerceros);
        opcionTerceros.addActionListener(e -> actualizarPermisos(CARD_TERCEROS));

        menuCfdi = new JMenu("CFDI");
        barraMenu.add(menuCfdi);
        opcionPeriodos = new JMenuItem("Consultar periodos");
        opcionPeriodos.setEnabled(false);
        opcionPeriodos.addActionListener(e -> actualizarPermisos(CARD_PERIODOS));
        menuCfdi.add(opcionPeriodos);
        opcionAdministrarXml = new JMenuItem("Administrar XML");
        opcionAdministrarXml.setEnabled(false);
        opcionAdministrarXml.setToolTipText("Disponible cuando se implemente el módulo de CFDI");
        menuCfdi.add(opcionAdministrarXml);
        opcionCargarXml = new JMenuItem("Cargar XML");
        opcionCargarXml.setEnabled(false);
        opcionCargarXml.addActionListener(e -> actualizarPermisos(CARD_IMPORTACION));
        menuCfdi.add(opcionCargarXml);
        opcionValidarXml = new JMenuItem("Validación técnica XML / XSD");
        opcionValidarXml.setEnabled(false);
        opcionValidarXml.addActionListener(e -> actualizarPermisos(CARD_VALIDACION));
        menuCfdi.add(opcionValidarXml);
        opcionClasificacion = new JMenuItem("Clasificación de comprobantes");
        opcionClasificacion.setEnabled(false);
        opcionClasificacion.addActionListener(e -> actualizarPermisos(CARD_CLASIFICACION));
        menuCfdi.add(opcionClasificacion);

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
        btnCargarXml.addActionListener(e -> actualizarPermisos(CARD_IMPORTACION));
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
        panelTerceros = new PanelTerceros();
        panelPrincipalContenedor.add(panelTerceros, CARD_TERCEROS);
        panelPeriodos = new PanelPeriodosFiscales();
        panelPrincipalContenedor.add(panelPeriodos, CARD_PERIODOS);
        panelImportacion = new PanelImportacionCfdi();
        panelPrincipalContenedor.add(panelImportacion, CARD_IMPORTACION);
        panelValidacion = new PanelValidacionTecnica();
        panelPrincipalContenedor.add(panelValidacion, CARD_VALIDACION);
        panelClasificacion = new PanelClasificacionCfdi();
        panelPrincipalContenedor.add(panelClasificacion, CARD_CLASIFICACION);
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
        if (sesion == null || usuarios == null || roles == null || contribuyentes == null || terceros == null || periodos == null || importaciones == null || validaciones == null || clasificaciones == null) {
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
                    boolean verContribuyentes = permisos.stream()
                            .anyMatch(p -> p.startsWith("CONTRIBUYENTES."));
                    opcionUsuarios.setEnabled(verUsuarios);
                    opcionRoles.setEnabled(verRoles);
                    opcionContribuyentes.setEnabled(verContribuyentes);
                    boolean verTerceros = permisos.contains(Permisos.TERCEROS_CONSULTAR)
                            && permisos.contains(Permisos.CONTRIBUYENTES_CONSULTAR);
                    opcionTerceros.setEnabled(verTerceros);
                    boolean verPeriodos = permisos.contains(Permisos.PERIODOS_FISCALES_CONSULTAR);
                    opcionPeriodos.setEnabled(verPeriodos);
                    panelPeriodos.configurar(periodos, sesion, permisos);
                    boolean importar = permisos.contains(Permisos.CFDI_IMPORTAR);
                    opcionCargarXml.setEnabled(importar);
                    btnCargarXml.setEnabled(importar);
                    panelImportacion.configurar(importaciones, sesion, permisos);
                    boolean validarXsd = permisos.contains(Permisos.CFDI_VALIDAR_TECNICAMENTE);
                    opcionValidarXml.setEnabled(validarXsd);
                    panelValidacion.configurar(validaciones, sesion, permisos);
                    boolean clasificar = permisos.contains(Permisos.CFDI_CONSULTAR_CLASIFICACION);
                    opcionClasificacion.setEnabled(clasificar);
                    panelClasificacion.configurar(clasificaciones, sesion, permisos);
                    btnContribuyentes.setEnabled(verContribuyentes);
                    panelUsuarios.configurar(usuarios, sesion, permisos);
                    panelRoles.configurar(roles, sesion, permisos);
                    panelContribuyentes.configurar(contribuyentes, sesion, permisos);
                    panelTerceros.configurar(terceros, contribuyentes, sesion, permisos);
                    if (CARD_CLASIFICACION.equals(destino)) {
                        if (clasificar) {
                            cardLayout.show(panelPrincipalContenedor, CARD_CLASIFICACION);
                            panelClasificacion.recargar();
                        } else {
                            JOptionPane.showMessageDialog(Fr_Principal.this,
                                    "No tiene permiso para consultar la clasificación.");
                        }
                    } else if (CARD_VALIDACION.equals(destino)) {
                        if (validarXsd) {
                            cardLayout.show(panelPrincipalContenedor, CARD_VALIDACION);
                        } else {
                            JOptionPane.showMessageDialog(Fr_Principal.this,
                                    "No tiene permiso para validar XML/XSD.");
                        }
                    } else if (CARD_IMPORTACION.equals(destino)) {
                        if (importar) {
                            cardLayout.show(panelPrincipalContenedor, CARD_IMPORTACION);
                            panelImportacion.recargar();
                        } else {
                            JOptionPane.showMessageDialog(Fr_Principal.this,
                                    "No tiene permiso para importar CFDI.");
                        }
                    } else if (CARD_PERIODOS.equals(destino)) {
                        if (verPeriodos) {
                            cardLayout.show(panelPrincipalContenedor, CARD_PERIODOS);
                            panelPeriodos.recargar();
                        } else {
                            JOptionPane.showMessageDialog(Fr_Principal.this,
                                    "No tiene permiso para consultar periodos.");
                        }
                    } else if (CARD_USUARIOS.equals(destino)) {
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
                    } else if (CARD_TERCEROS.equals(destino)) {
                        if (verTerceros) {
                            cardLayout.show(panelPrincipalContenedor, CARD_TERCEROS);
                            panelTerceros.recargar();
                        } else {
                            JOptionPane.showMessageDialog(Fr_Principal.this,
                                    "No tiene permisos para consultar terceros.");
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
                    opcionTerceros.setEnabled(false);
                    opcionPeriodos.setEnabled(false);
                    opcionCargarXml.setEnabled(false);
                    opcionValidarXml.setEnabled(false);
                    opcionClasificacion.setEnabled(false);
                    btnCargarXml.setEnabled(false);
                    panelImportacion.configurar(null, null, Set.of());
                    panelValidacion.configurar(null, null, Set.of());
                    panelClasificacion.configurar(null, null, Set.of());
                    panelPeriodos.configurar(null, null, Set.of());
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
