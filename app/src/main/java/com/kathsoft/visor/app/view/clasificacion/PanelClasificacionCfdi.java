package com.kathsoft.visor.app.view.clasificacion;

import com.kathsoft.visor.app.model.dto.DocumentoClasificado;
import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal;
import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal.Sentido;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.clasificacion.ClasificacionCfdiService;
import com.kathsoft.visor.app.service.clasificacion.ClasificacionCfdiService.ContribuyenteDisponible;
import com.kathsoft.visor.app.service.clasificacion.ClasificacionCfdiService.Pagina;
import com.kathsoft.visor.app.service.clasificacion.ClasificadorDocumentoFiscal.Categoria;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/**
 * RF-009: catálogo de clasificación documental dentro del CardLayout.
 * Constructor vacío, componentes explícitos y GroupLayout para WindowBuilder.
 * No ejecuta SQL, Hibernate ni operaciones de E/S en el EDT.
 */
public class PanelClasificacionCfdi extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");

    private ClasificacionCfdiService servicio;
    private UsuarioSesion sesion;
    private boolean autorizado;
    private boolean ocupado;
    private boolean tieneSiguiente;
    private int pagina;
    private int generacion;

    private JComboBox<ContribuyenteDisponible> cmbContribuyente;
    private JComboBox<Categoria> cmbCategoria;
    private JComboBox<Sentido> cmbSentido;
    private JTextField txfUuid;
    private JButton btnConsultar;
    private JButton btnActualizar;
    private JButton btnAnterior;
    private JButton btnSiguiente;
    private JLabel lblEstado;
    private JTable tabla;
    private DefaultTableModel modelo;

    /** Diseña el panel sin dependencias ni acceso a datos. */
    public PanelClasificacionCfdi() {
        inicializarComponentes();
    }

    /** Asigna dependencias y privilegios visuales; el backend los revalida. */
    public void configurar(ClasificacionCfdiService servicio, UsuarioSesion sesion,
            Set<String> permisos) {
        this.servicio = servicio;
        this.sesion = sesion;
        autorizado = servicio != null && sesion != null && permisos != null
                && permisos.contains(Permisos.CFDI_CONSULTAR_CLASIFICACION);
        if (!autorizado) {
            generacion++;
            ocupado = false;
            tieneSiguiente = false;
            pagina = 0;
            modelo.setRowCount(0);
            cmbContribuyente.removeAllItems();
            lblEstado.setText("No tiene permiso para consultar la clasificación");
        }
        actualizarControles();
    }

    /** Recarga únicamente los contribuyentes asignados usando SwingWorker. */
    public void recargar() {
        if (!autorizado || ocupado) return;
        final int solicitud = ++generacion;
        final Long operador = sesion.id();
        ocupado = true;
        tieneSiguiente = false;
        pagina = 0;
        modelo.setRowCount(0);
        lblEstado.setText("Cargando contribuyentes autorizados...");
        actualizarControles();
        new SwingWorker<List<ContribuyenteDisponible>, Void>() {
            @Override
            protected List<ContribuyenteDisponible> doInBackground() {
                return servicio.contribuyentes(operador);
            }

            @Override
            protected void done() {
                if (solicitud != generacion || !autorizado) return;
                try {
                    List<ContribuyenteDisponible> titulares = get();
                    cmbContribuyente.removeAllItems();
                    for (ContribuyenteDisponible c : titulares) {
                        cmbContribuyente.addItem(c);
                    }
                    lblEstado.setText(titulares.isEmpty()
                            ? "No tiene contribuyentes asignados"
                            : "Seleccione filtros y pulse Consultar");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblEstado.setText("Carga interrumpida");
                } catch (ExecutionException ex) {
                    error(ex.getCause());
                } finally {
                    ocupado = false;
                    actualizarControles();
                }
            }
        }.execute();
    }

    private void inicializarComponentes() {
        JLabel lblTitulo = new JLabel("Identificación y clasificación de comprobantes");
        JLabel lblContribuyente = new JLabel("Contribuyente");
        JLabel lblCategoria = new JLabel("Tipo fiscal");
        JLabel lblSentido = new JLabel("Sentido");
        JLabel lblUuid = new JLabel("UUID (prefijo)");
        cmbContribuyente = new JComboBox<>();
        cmbCategoria = new JComboBox<>(Categoria.values());
        cmbSentido = new JComboBox<>(Sentido.values());
        txfUuid = new JTextField();
        txfUuid.setColumns(17);
        txfUuid.setToolTipText("Prefijo del UUID; letras A-F, números y guiones");
        btnConsultar = new JButton("Consultar");
        btnActualizar = new JButton("Actualizar titulares");
        btnAnterior = new JButton("Anterior");
        btnSiguiente = new JButton("Siguiente");
        lblEstado = new JLabel("Sin consulta");
        modelo = new DefaultTableModel(
                new String[] {"ID", "UUID", "Clasificación", "Sentido", "Emisión",
                        "Emisor", "Receptor", "Tipo", "Clave retención"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.setAutoCreateRowSorter(true);
        JScrollPane scroll = new JScrollPane(tabla);

        JPanel filtros = new JPanel();
        GroupLayout gl = new GroupLayout(filtros);
        filtros.setLayout(gl);
        gl.setAutoCreateGaps(true);
        gl.setAutoCreateContainerGaps(true);
        gl.setHorizontalGroup(gl.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(gl.createSequentialGroup()
                        .addComponent(lblContribuyente)
                        .addComponent(cmbContribuyente, 105, 160, Short.MAX_VALUE)
                        .addComponent(lblCategoria)
                        .addComponent(cmbCategoria, 110, 155, 210)
                        .addComponent(lblSentido)
                        .addComponent(cmbSentido, 70, 85, 115))
                .addGroup(gl.createSequentialGroup()
                        .addComponent(lblUuid)
                        .addComponent(txfUuid, 145, 220, 310)
                        .addComponent(btnConsultar)
                        .addComponent(btnActualizar)));
        gl.setVerticalGroup(gl.createSequentialGroup()
                .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(lblContribuyente).addComponent(cmbContribuyente)
                        .addComponent(lblCategoria).addComponent(cmbCategoria)
                        .addComponent(lblSentido).addComponent(cmbSentido))
                .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(lblUuid).addComponent(txfUuid)
                        .addComponent(btnConsultar).addComponent(btnActualizar)));
        JPanel navegacion = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 3));
        navegacion.add(btnAnterior);
        navegacion.add(btnSiguiente);
        navegacion.add(lblEstado);
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblTitulo)
                .addComponent(filtros, GroupLayout.DEFAULT_SIZE, 600, Short.MAX_VALUE)
                .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 600, Short.MAX_VALUE)
                .addComponent(navegacion, GroupLayout.DEFAULT_SIZE, 600, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(lblTitulo)
                .addComponent(filtros, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)
                .addComponent(navegacion, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
        btnConsultar.addActionListener(e -> consultar(0));
        txfUuid.addActionListener(e -> consultar(0));
        btnActualizar.addActionListener(e -> recargar());
        btnAnterior.addActionListener(e -> consultar(pagina - 1));
        btnSiguiente.addActionListener(e -> consultar(pagina + 1));
        actualizarControles();
    }

    private void consultar(int nuevaPagina) {
        if (!autorizado || ocupado || nuevaPagina < 0) return;
        ContribuyenteDisponible c =
                (ContribuyenteDisponible) cmbContribuyente.getSelectedItem();
        if (c == null) return;
        final FiltroClasificacionFiscal filtro;
        try {
            filtro = new FiltroClasificacionFiscal(
                    (Categoria) cmbCategoria.getSelectedItem(),
                    (Sentido) cmbSentido.getSelectedItem(), txfUuid.getText());
        } catch (RuntimeException ex) {
            error(ex);
            return;
        }
        final int solicitud = ++generacion;
        final Long operador = sesion.id();
        final long contribuyenteId = c.id();
        ocupado = true;
        actualizarControles();
        lblEstado.setText("Consultando clasificación...");
        new SwingWorker<Pagina, Void>() {
            @Override protected Pagina doInBackground() {
                return servicio.consultar(operador, contribuyenteId, filtro, nuevaPagina);
            }
            @Override protected void done() {
                if (solicitud != generacion || !autorizado) return;
                try {
                    Pagina resultado = get();
                    pagina = resultado.indice();
                    tieneSiguiente = resultado.tieneSiguiente();
                    modelo.setRowCount(0);
                    for (DocumentoClasificado d : resultado.documentos()) {
                        modelo.addRow(new Object[] {d.documentoId(), d.uuid(),
                                d.categoria(), d.sentido(),
                                d.fechaEmision() == null ? "" :
                                        d.fechaEmision().format(FECHA),
                                d.rfcEmisor(), d.rfcReceptor(),
                                d.tipoCfdi(), d.claveRetencion()});
                    }
                    lblEstado.setText("Página " + (pagina + 1) + " — "
                            + resultado.documentos().size()
                            + " comprobantes; clasificación, no validación SAT");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblEstado.setText("Consulta interrumpida");
                } catch (ExecutionException ex) {
                    modelo.setRowCount(0);
                    tieneSiguiente = false;
                    error(ex.getCause());
                } finally {
                    ocupado = false;
                    actualizarControles();
                }
            }
        }.execute();
    }

    private void actualizarControles() {
        boolean disponible = autorizado && !ocupado;
        cmbContribuyente.setEnabled(disponible);
        cmbCategoria.setEnabled(disponible);
        cmbSentido.setEnabled(disponible);
        txfUuid.setEnabled(disponible);
        btnConsultar.setEnabled(disponible && cmbContribuyente.getItemCount() > 0);
        btnActualizar.setEnabled(disponible);
        btnAnterior.setEnabled(disponible && pagina > 0);
        btnSiguiente.setEnabled(disponible && tieneSiguiente);
    }

    private void error(Throwable fallo) {
        Throwable causa = fallo == null
                ? new IllegalStateException("Sin diagnóstico de la consulta") : fallo;
        causa.printStackTrace(System.err);
        lblEstado.setText("Error en consulta de clasificación");
        JOptionPane.showMessageDialog(this, causa.getMessage(),
                "Clasificación de comprobantes", JOptionPane.ERROR_MESSAGE);
    }
}
