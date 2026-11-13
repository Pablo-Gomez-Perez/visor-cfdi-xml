package com.kathsoft.visor.app.view.validacion;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.validacion.ValidacionTecnicaService;
import com.kathsoft.visor.app.service.validacion.ValidacionTecnicaService.Resumen;
import com.kathsoft.visor.app.service.validacion.ValidacionTecnicaService.Solicitud;
import com.kathsoft.visor.app.service.validacion.ValidadorXsdSat;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/**
 * RF-008: consulta de validación técnica para archivos/carpeta sin importar.
 *
 * <p>Constructor vacío y componentes explícitos: compatible con Eclipse
 * WindowBuilder. El EDT solo dibuja resultados, nunca parsea XML o XSD.</p>
 */
public class PanelValidacionTecnica extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MAX_FILAS_UI = 500;
    private final List<Path> fuentes = new ArrayList<>();
    private ValidacionTecnicaService servicio;
    private UsuarioSesion sesion;
    private boolean autorizado;
    private boolean ocupado;
    private AtomicBoolean cancelacion = new AtomicBoolean();

    private JButton btnArchivos;
    private JButton btnCarpeta;
    private JButton btnLimpiar;
    private JButton btnValidar;
    private JButton btnCancelar;
    private JCheckBox chkSubcarpetas;
    private JTextArea txaFuentes;
    private JTable tabla;
    private DefaultTableModel modelo;
    private JProgressBar progreso;
    private JLabel lblEstado;
    private JLabel lblXsd;

    /** Construye la vista de diseño sin conexiones ni E/S. */
    public PanelValidacionTecnica() {
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        JLabel titulo = new JLabel("RF-008 — Validación técnica XML / XSD SAT");
        JLabel fuentesLabel = new JLabel("Archivos o carpetas a verificar");
        lblXsd = new JLabel("Esquemas SAT: no configurados para la vista de diseño");
        btnArchivos = new JButton("Seleccionar XML");
        btnCarpeta = new JButton("Seleccionar carpeta");
        btnLimpiar = new JButton("Limpiar");
        btnValidar = new JButton("Validar XSD");
        btnCancelar = new JButton("Cancelar");
        chkSubcarpetas = new JCheckBox("Incluir subcarpetas");
        txaFuentes = new JTextArea(3, 30);
        txaFuentes.setEditable(false);
        JScrollPane scrollFuentes = new JScrollPane(txaFuentes);
        modelo = new DefaultTableModel(
                new String[] {"Archivo", "Estado", "Familia", "Versión", "Tipo",
                        "Línea", "Diagnóstico"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int fila, int columna) { return false; }
        };
        tabla = new JTable(modelo);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane scrollResultados = new JScrollPane(tabla);
        progreso = new JProgressBar();
        progreso.setStringPainted(true);
        progreso.setString("Sin iniciar");
        lblEstado = new JLabel("Seleccione archivos XML para comprobar su estructura.");
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        acciones.add(btnArchivos);
        acciones.add(btnCarpeta);
        acciones.add(btnLimpiar);
        acciones.add(chkSubcarpetas);
        acciones.add(btnValidar);
        acciones.add(btnCancelar);

        GroupLayout gl = new GroupLayout(this);
        setLayout(gl);
        gl.setAutoCreateGaps(true);
        gl.setAutoCreateContainerGaps(true);
        gl.setHorizontalGroup(gl.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(titulo)
            .addComponent(lblXsd)
            .addComponent(acciones, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                    Short.MAX_VALUE)
            .addComponent(fuentesLabel)
            .addComponent(scrollFuentes, GroupLayout.DEFAULT_SIZE, 580, Short.MAX_VALUE)
            .addComponent(scrollResultados, GroupLayout.DEFAULT_SIZE, 580, Short.MAX_VALUE)
            .addComponent(progreso, GroupLayout.DEFAULT_SIZE, 580, Short.MAX_VALUE)
            .addComponent(lblEstado));
        gl.setVerticalGroup(gl.createSequentialGroup()
            .addComponent(titulo)
            .addComponent(lblXsd)
            .addComponent(acciones, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.PREFERRED_SIZE)
            .addComponent(fuentesLabel)
            .addComponent(scrollFuentes, GroupLayout.PREFERRED_SIZE, 55,
                    GroupLayout.PREFERRED_SIZE)
            .addComponent(scrollResultados, GroupLayout.DEFAULT_SIZE, 180, Short.MAX_VALUE)
            .addComponent(progreso, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.PREFERRED_SIZE)
            .addComponent(lblEstado));

        btnArchivos.addActionListener(e -> seleccionar(JFileChooser.FILES_ONLY, true));
        btnCarpeta.addActionListener(e -> seleccionar(JFileChooser.DIRECTORIES_ONLY, false));
        btnLimpiar.addActionListener(e -> {
            fuentes.clear();
            actualizarFuentes();
            modelo.setRowCount(0);
        });
        btnValidar.addActionListener(e -> validar());
        btnCancelar.addActionListener(e -> {
            cancelacion.set(true);
            lblEstado.setText("Cancelación solicitada...");
            btnCancelar.setEnabled(false);
        });
        habilitar();
    }

    /** Asigna servicios y permisos refrescados desde MySQL. */
    public void configurar(ValidacionTecnicaService servicio, UsuarioSesion sesion,
            Set<String> permisos) {
        this.servicio = servicio;
        this.sesion = sesion;
        autorizado = servicio != null && sesion != null && permisos != null
                && permisos.contains(Permisos.CFDI_VALIDAR_TECNICAMENTE);
        if (!ocupado && !autorizado) {
            fuentes.clear();
            actualizarFuentes();
            modelo.setRowCount(0);
        }
        lblXsd.setText(autorizado ? "XSD local: "
                + ValidacionTecnicaService.carpetaEsquemas()
                : "Esquemas SAT: acceso no autorizado");
        habilitar();
    }

    private void seleccionar(int modo, boolean multiple) {
        JFileChooser selector = new JFileChooser();
        selector.setFileSelectionMode(modo);
        selector.setMultiSelectionEnabled(multiple);
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        if (multiple) {
            for (java.io.File archivo : selector.getSelectedFiles()) agregar(archivo.toPath());
        } else {
            agregar(selector.getSelectedFile().toPath());
        }
        actualizarFuentes();
    }

    private void agregar(Path ruta) {
        Path normal = ruta.toAbsolutePath().normalize();
        if (!fuentes.contains(normal)) fuentes.add(normal);
    }

    private void actualizarFuentes() {
        StringBuilder sb = new StringBuilder();
        for (Path ruta : fuentes) sb.append(ruta).append('\n');
        txaFuentes.setText(sb.toString());
        txaFuentes.setCaretPosition(0);
        habilitar();
    }

    private void validar() {
        if (!autorizado || ocupado || fuentes.isEmpty()) return;
        Solicitud solicitud = new Solicitud(List.copyOf(fuentes), chkSubcarpetas.isSelected());
        ValidacionTecnicaService validador = servicio;
        long usuarioId = sesion.id();
        cancelacion = new AtomicBoolean();
        AtomicBoolean bandera = cancelacion;
        modelo.setRowCount(0);
        progreso.setIndeterminate(true);
        progreso.setString("Validando XML...");
        lblEstado.setText("Comprobación estructural XSD en curso");
        ocupado = true;
        habilitar();
        new SwingWorker<Resumen, ValidadorXsdSat.Resultado>() {
            @Override protected Resumen doInBackground() {
                return validador.validar(usuarioId, solicitud, r -> publish(r), bandera::get);
            }
            @Override protected void process(List<ValidadorXsdSat.Resultado> resultados) {
                for (ValidadorXsdSat.Resultado r : resultados) {
                    modelo.addRow(new Object[] {
                        r.archivo().getFileName().toString(), r.estado(),
                        r.familia(), r.version(), r.tipo(),
                        r.linea() > 0 ? r.linea() + ":" + r.columna() : "",
                        r.detalle()
                    });
                    if (modelo.getRowCount() > MAX_FILAS_UI) modelo.removeRow(0);
                }
                progreso.setString("Resultados recientes: " + modelo.getRowCount()
                        + " (máx. 500 filas visibles)");
            }
            @Override protected void done() {
                try {
                    Resumen r = get();
                    lblEstado.setText("XML: " + r.total() + " | válidos: "
                            + r.validos() + " | inválidos: " + r.invalidos()
                            + " | incompatibles: " + r.incompatibles()
                            + " | sin XSD: " + r.sinEsquema()
                            + " | errores: " + r.errores());
                    progreso.setString("Verificación finalizada");
                } catch (CancellationException ex) {
                    lblEstado.setText("Validación cancelada.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblEstado.setText("Validación interrumpida.");
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause() == null ? ex : ex.getCause();
                    causa.printStackTrace(System.err);
                    lblEstado.setText("Error: " + causa.getMessage());
                    JOptionPane.showMessageDialog(PanelValidacionTecnica.this,
                            causa.getMessage(), "Validación técnica",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    progreso.setIndeterminate(false);
                    ocupado = false;
                    habilitar();
                }
            }
        }.execute();
    }

    private void habilitar() {
        boolean disponible = autorizado && !ocupado;
        btnArchivos.setEnabled(disponible);
        btnCarpeta.setEnabled(disponible);
        btnLimpiar.setEnabled(disponible && !fuentes.isEmpty());
        chkSubcarpetas.setEnabled(disponible);
        btnValidar.setEnabled(disponible && !fuentes.isEmpty());
        btnCancelar.setEnabled(ocupado && !cancelacion.get());
    }
}
