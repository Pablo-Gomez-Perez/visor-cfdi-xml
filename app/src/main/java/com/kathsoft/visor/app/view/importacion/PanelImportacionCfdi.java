package com.kathsoft.visor.app.view.importacion;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.importacion.ImportacionCfdiService;
import com.kathsoft.visor.app.service.importacion.ImportacionCfdiService.Avance;
import com.kathsoft.visor.app.service.importacion.ImportacionCfdiService.ContribuyenteImportacion;
import com.kathsoft.visor.app.service.importacion.ImportacionCfdiService.Resultado;
import com.kathsoft.visor.app.service.importacion.ImportacionCfdiService.Solicitud;
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
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/**
 * RF-007: selección de archivos y directorios y ejecución de importación.
 * Swing declarativo con GroupLayout, constructor sin argumentos apto para
 * Eclipse WindowBuilder. Nunca accede a MySQL ni al disco en el constructor.
 */
public class PanelImportacionCfdi extends JPanel {
    private static final long serialVersionUID = 1L;
    private ImportacionCfdiService servicio;
    private UsuarioSesion sesion;
    private boolean autorizado;
    private boolean ocupado;
    private final List<Path> fuentes = new ArrayList<>();
    private AtomicBoolean cancelacion = new AtomicBoolean();
    private JComboBox<ContribuyenteImportacion> cmbContribuyente;
    private JCheckBox chkSubcarpetas;
    private JButton btnArchivos;
    private JButton btnCarpeta;
    private JButton btnQuitar;
    private JButton btnImportar;
    private JButton btnCancelar;
    private JTextArea txtFuentes;
    private JProgressBar progreso;
    private JLabel lblEstado;

    /** Únicamente instancia componentes visuales. */
    public PanelImportacionCfdi() {
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        JLabel titulo = new JLabel("Importación de CFDI XML");
        JLabel lblContribuyente = new JLabel("Contribuyente");
        JLabel lblFuentes = new JLabel("Archivos y carpetas seleccionados");
        cmbContribuyente = new JComboBox<>();
        chkSubcarpetas = new JCheckBox("Incluir subcarpetas");
        btnArchivos = new JButton("Seleccionar archivos");
        btnCarpeta = new JButton("Seleccionar carpeta");
        btnQuitar = new JButton("Limpiar selección");
        btnImportar = new JButton("Importar");
        btnCancelar = new JButton("Cancelar");
        txtFuentes = new JTextArea(8, 42);
        txtFuentes.setEditable(false);
        txtFuentes.setLineWrap(true);
        txtFuentes.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(txtFuentes);
        progreso = new JProgressBar();
        progreso.setStringPainted(true);
        progreso.setString("Sin iniciar");
        lblEstado = new JLabel("Seleccione contribuyente y origen de los archivos.");
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        acciones.add(btnArchivos);
        acciones.add(btnCarpeta);
        acciones.add(btnQuitar);
        acciones.add(btnImportar);
        acciones.add(btnCancelar);

        GroupLayout gl = new GroupLayout(this);
        setLayout(gl);
        gl.setAutoCreateGaps(true);
        gl.setAutoCreateContainerGaps(true);
        gl.setHorizontalGroup(gl.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(titulo)
            .addGroup(gl.createSequentialGroup().addComponent(lblContribuyente)
                .addComponent(cmbContribuyente, 180, 270, Short.MAX_VALUE)
                .addComponent(chkSubcarpetas))
            .addComponent(acciones, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE)
            .addComponent(lblFuentes)
            .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 520, Short.MAX_VALUE)
            .addComponent(progreso, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE)
            .addComponent(lblEstado));
        gl.setVerticalGroup(gl.createSequentialGroup()
            .addComponent(titulo)
            .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblContribuyente).addComponent(cmbContribuyente)
                .addComponent(chkSubcarpetas))
            .addComponent(acciones, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE)
            .addComponent(lblFuentes)
            .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 170, Short.MAX_VALUE)
            .addComponent(progreso, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE)
            .addComponent(lblEstado));

        btnArchivos.addActionListener(e -> seleccionar(JFileChooser.FILES_ONLY, true));
        btnCarpeta.addActionListener(e -> seleccionar(JFileChooser.DIRECTORIES_ONLY, false));
        btnQuitar.addActionListener(e -> { fuentes.clear(); actualizarFuentes(); });
        btnImportar.addActionListener(e -> importar());
        btnCancelar.addActionListener(e -> {
            cancelacion.set(true);
            btnCancelar.setEnabled(false);
            lblEstado.setText("Cancelación solicitada; se revertirá el lote activo...");
        });
        habilitar();
    }

    /** Inyecta la sesión y sus privilegios sin trabajo de persistencia. */
    public void configurar(ImportacionCfdiService servicio, UsuarioSesion sesion,
            Set<String> permisos) {
        this.servicio = servicio;
        this.sesion = sesion;
        autorizado = permisos != null && permisos.contains(Permisos.CFDI_IMPORTAR);
        if (!autorizado && !ocupado) {
            cmbContribuyente.removeAllItems();
            fuentes.clear();
            actualizarFuentes();
        }
        habilitar();
    }

    /** Carga contribuyentes autorizados sin bloquear la interfaz. */
    public void recargar() {
        if (ocupado || servicio == null || sesion == null || !autorizado) return;
        ocupado = true;
        habilitar();
        new SwingWorker<List<ContribuyenteImportacion>, Void>() {
            @Override protected List<ContribuyenteImportacion> doInBackground() {
                return servicio.contribuyentes(sesion.id());
            }
            @Override protected void done() {
                try {
                    List<ContribuyenteImportacion> datos = get();
                    cmbContribuyente.removeAllItems();
                    if (autorizado) for (ContribuyenteImportacion c : datos) {
                        cmbContribuyente.addItem(c);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    error(ex.getCause());
                } finally {
                    ocupado = false;
                    habilitar();
                }
            }
        }.execute();
    }

    private void seleccionar(int modo, boolean multiple) {
        JFileChooser selector = new JFileChooser();
        selector.setFileSelectionMode(modo);
        selector.setMultiSelectionEnabled(multiple);
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        if (multiple) {
            for (java.io.File archivo : selector.getSelectedFiles()) {
                agregar(archivo.toPath());
            }
        } else {
            agregar(selector.getSelectedFile().toPath());
        }
        actualizarFuentes();
    }

    private void agregar(Path path) {
        Path normal = path.toAbsolutePath().normalize();
        if (!fuentes.contains(normal)) fuentes.add(normal);
    }

    private void actualizarFuentes() {
        StringBuilder texto = new StringBuilder();
        for (Path fuente : fuentes) texto.append(fuente).append('\n');
        txtFuentes.setText(texto.toString());
        txtFuentes.setCaretPosition(0);
        habilitar();
    }

    private void importar() {
        ContribuyenteImportacion c = (ContribuyenteImportacion) cmbContribuyente.getSelectedItem();
        if (ocupado || !autorizado || servicio == null || sesion == null
                || c == null || fuentes.isEmpty()) return;
        Solicitud solicitud = new Solicitud(c.id(), List.copyOf(fuentes),
                chkSubcarpetas.isSelected());
        cancelacion = new AtomicBoolean();
        AtomicBoolean bandera = cancelacion;
        long operadorId = sesion.id();
        ImportacionCfdiService importador = servicio;
        ocupado = true;
        progreso.setIndeterminate(true);
        progreso.setString("Procesando...");
        lblEstado.setText("Lectura paralela y persistencia transaccional en curso");
        habilitar();
        new SwingWorker<Resultado, Avance>() {
            @Override protected Resultado doInBackground() {
                return importador.importar(operadorId, solicitud,
                        avance -> publish(avance), bandera::get);
            }
            @Override protected void process(List<Avance> avances) {
                if (avances.isEmpty()) return;
                Avance p = avances.get(avances.size() - 1);
                progreso.setString(p.procesados() + " procesados; "
                        + p.nuevos() + " nuevos, " + p.duplicados() + " duplicados");
                lblEstado.setText("Último lote confirmado: " + p.ultimo());
            }
            @Override protected void done() {
                try {
                    Resultado r = get();
                    lblEstado.setText("Lote " + r.loteId() + " finalizado: "
                            + r.nuevos() + " nuevos y " + r.duplicados() + " duplicados");
                    progreso.setString("Importación finalizada");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    lblEstado.setText("Importación interrumpida");
                } catch (CancellationException ex) {
                    lblEstado.setText("Importación cancelada");
                } catch (ExecutionException ex) {
                    lblEstado.setText("Importación detenida; consulte el error");
                    error(ex.getCause());
                } finally {
                    progreso.setIndeterminate(false);
                    ocupado = false;
                    habilitar();
                }
            }
        }.execute();
    }

    private void habilitar() {
        boolean listo = autorizado && servicio != null && sesion != null && !ocupado;
        cmbContribuyente.setEnabled(listo);
        chkSubcarpetas.setEnabled(listo);
        btnArchivos.setEnabled(listo);
        btnCarpeta.setEnabled(listo);
        btnQuitar.setEnabled(listo && !fuentes.isEmpty());
        btnImportar.setEnabled(listo && !fuentes.isEmpty()
                && cmbContribuyente.getItemCount() > 0);
        btnCancelar.setEnabled(ocupado && !cancelacion.get());
    }

    private void error(Throwable ex) {
        Throwable causa = ex == null ? new IllegalStateException("Error desconocido") : ex;
        JOptionPane.showMessageDialog(this,
                "No se completó la importación. Los lotes confirmados no se revierten.\n"
                + causa.getClass().getSimpleName() + ": " + causa.getMessage(),
                "Importación CFDI", JOptionPane.ERROR_MESSAGE);
    }
}
