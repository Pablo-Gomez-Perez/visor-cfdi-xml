package com.kathsoft.visor.app.view.periodo;

import java.awt.FlowLayout;
import java.time.LocalDate;
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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.kathsoft.visor.app.model.dto.FiltroPeriodoFiscal;
import com.kathsoft.visor.app.model.dto.FiltroPeriodoFiscal.TipoFechaFiscal;
import com.kathsoft.visor.app.model.dto.MovimientoPeriodoFiscal;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.periodo.PeriodosFiscalesService;
import com.kathsoft.visor.app.service.periodo.PeriodosFiscalesService.ContribuyentePeriodo;

/**
 * RF-006: vista de periodos integrada al CardLayout principal.
 * Constructor vacío y GroupLayout explícito para Eclipse WindowBuilder.
 * La consulta a MySQL siempre se ejecuta fuera del EDT.
 */
public class PanelPeriodosFiscales extends JPanel {
    private static final long serialVersionUID = 1L;
    private PeriodosFiscalesService servicio;
    private UsuarioSesion sesion;
    private JComboBox<ContribuyentePeriodo> cmbContribuyente;
    private JComboBox<TipoFechaFiscal> cmbTipoFecha;
    private JComboBox<String> cmbPeriodo;
    private JSpinner spEjercicio;
    private JSpinner spMes;
    private JSpinner spDesde;
    private JSpinner spHasta;
    private JButton btnConsultar;
    private JButton btnAnterior;
    private JButton btnSiguiente;
    private JLabel lblEstado;
    private JTable tabla;
    private DefaultTableModel modelo;
    private int pagina;

    /** Crea solamente componentes Swing, sin acceder a BD ni a disco. */
    public PanelPeriodosFiscales() { inicializarComponentes(); }

    /** Asigna dependencias sin ejecutar operaciones de persistencia. */
    public void configurar(PeriodosFiscalesService servicio, UsuarioSesion sesion,
            Set<String> permisos) {
        this.servicio = servicio;
        this.sesion = sesion;
        boolean autorizado = permisos != null
                && permisos.contains(Permisos.PERIODOS_FISCALES_CONSULTAR);
        btnConsultar.setEnabled(autorizado);
        if (!autorizado) {
            modelo.setRowCount(0);
            cmbContribuyente.removeAllItems();
            lblEstado.setText("Sin permiso de consulta");
        }
    }

    /** Recarga contribuyentes autorizados mediante SwingWorker. */
    public void recargar() {
        if (servicio == null || sesion == null || !btnConsultar.isEnabled()) return;
        btnConsultar.setEnabled(false);
        new SwingWorker<List<ContribuyentePeriodo>, Void>() {
            @Override protected List<ContribuyentePeriodo> doInBackground() {
                return servicio.contribuyentes(sesion.id());
            }
            @Override protected void done() {
                try {
                    cmbContribuyente.removeAllItems();
                    for (ContribuyentePeriodo c : get()) cmbContribuyente.addItem(c);
                    lblEstado.setText("Seleccione contribuyente y periodo");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    error(ex.getCause());
                } finally {
                    btnConsultar.setEnabled(true);
                }
            }
        }.execute();
    }

    private void inicializarComponentes() {
        JLabel titulo = new JLabel("Consulta de periodos fiscales");
        JLabel lblContribuyente = new JLabel("Contribuyente");
        JLabel lblFecha = new JLabel("Fecha");
        JLabel lblPeriodo = new JLabel("Periodo");
        JLabel lblEjercicio = new JLabel("Ejercicio");
        JLabel lblMes = new JLabel("Mes");
        JLabel lblDesde = new JLabel("Desde (AAAAMMDD)");
        JLabel lblHasta = new JLabel("Hasta (AAAAMMDD)");
        cmbContribuyente = new JComboBox<>();
        cmbTipoFecha = new JComboBox<>(TipoFechaFiscal.values());
        cmbPeriodo = new JComboBox<>(new String[] {"Ejercicio", "Mes", "Rango"});
        spEjercicio = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 1, 9998, 1));
        spMes = new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1));
        spDesde = new JSpinner(new SpinnerNumberModel(20260101, 10101, 99981231, 1));
        spHasta = new JSpinner(new SpinnerNumberModel(20261231, 10101, 99981231, 1));
        btnConsultar = new JButton("Consultar");
        btnConsultar.setEnabled(false);
        btnAnterior = new JButton("Anterior");
        btnSiguiente = new JButton("Siguiente");
        btnAnterior.setEnabled(false);
        btnSiguiente.setEnabled(false);
        lblEstado = new JLabel("Sin consulta");
        JPanel filtros = new JPanel();
        GroupLayout gl = new GroupLayout(filtros);
        filtros.setLayout(gl);
        gl.setAutoCreateGaps(true);
        gl.setAutoCreateContainerGaps(true);
        gl.setHorizontalGroup(gl.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(gl.createSequentialGroup().addComponent(lblContribuyente)
                .addComponent(cmbContribuyente, 160, 220, Short.MAX_VALUE)
                .addComponent(lblFecha).addComponent(cmbTipoFecha, 110, 130, 160))
            .addGroup(gl.createSequentialGroup().addComponent(lblPeriodo)
                .addComponent(cmbPeriodo, 90, 100, 130)
                .addComponent(lblEjercicio).addComponent(spEjercicio, 70, 75, 90)
                .addComponent(lblMes).addComponent(spMes, 45, 50, 65))
            .addGroup(gl.createSequentialGroup().addComponent(lblDesde)
                .addComponent(spDesde, 90, 100, 115)
                .addComponent(lblHasta).addComponent(spHasta, 90, 100, 115)
                .addComponent(btnConsultar)));
        gl.setVerticalGroup(gl.createSequentialGroup()
            .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblContribuyente).addComponent(cmbContribuyente)
                .addComponent(lblFecha).addComponent(cmbTipoFecha))
            .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblPeriodo).addComponent(cmbPeriodo)
                .addComponent(lblEjercicio).addComponent(spEjercicio)
                .addComponent(lblMes).addComponent(spMes))
            .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblDesde).addComponent(spDesde)
                .addComponent(lblHasta).addComponent(spHasta).addComponent(btnConsultar)));
        modelo = new DefaultTableModel(
                new String[] {"Documento", "UUID", "Sentido", "Tipo", "Fecha evento",
                        "Pago ID", "Monto pago", "Moneda"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int fila, int columna) { return false; }
        };
        tabla = new JTable(modelo);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane scroll = new JScrollPane(tabla);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.add(btnAnterior);
        acciones.add(btnSiguiente);
        acciones.add(lblEstado);
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(titulo)
            .addComponent(filtros, GroupLayout.DEFAULT_SIZE, 600, Short.MAX_VALUE)
            .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 600, Short.MAX_VALUE)
            .addComponent(acciones, GroupLayout.DEFAULT_SIZE, 600, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(titulo)
            .addComponent(filtros, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.PREFERRED_SIZE)
            .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
            .addComponent(acciones, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.PREFERRED_SIZE));
        btnConsultar.addActionListener(e -> { pagina = 0; consultar(); });
        btnAnterior.addActionListener(e -> { if (pagina > 0) { pagina--; consultar(); } });
        btnSiguiente.addActionListener(e -> { pagina++; consultar(); });
    }

    private FiltroPeriodoFiscal filtro() {
        TipoFechaFiscal tipo = (TipoFechaFiscal) cmbTipoFecha.getSelectedItem();
        int ejercicio = (Integer) spEjercicio.getValue();
        return switch (cmbPeriodo.getSelectedIndex()) {
            case 0 -> FiltroPeriodoFiscal.ejercicio(ejercicio, tipo);
            case 1 -> FiltroPeriodoFiscal.mes(ejercicio, (Integer) spMes.getValue(), tipo);
            default -> new FiltroPeriodoFiscal(
                LocalDate.parse(String.format("%08d", (Integer) spDesde.getValue()),
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                LocalDate.parse(String.format("%08d", (Integer) spHasta.getValue()),
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE), tipo);
        };
    }

    private void consultar() {
        ContribuyentePeriodo contribuyente = (ContribuyentePeriodo) cmbContribuyente.getSelectedItem();
        if (contribuyente == null || servicio == null || sesion == null) return;
        final FiltroPeriodoFiscal filtro;
        try { filtro = filtro(); }
        catch (RuntimeException ex) { error(ex); return; }
        int paginaSolicitada = pagina;
        btnConsultar.setEnabled(false);
        btnAnterior.setEnabled(false);
        btnSiguiente.setEnabled(false);
        new SwingWorker<List<MovimientoPeriodoFiscal>, Void>() {
            @Override protected List<MovimientoPeriodoFiscal> doInBackground() {
                return servicio.consultar(sesion.id(), contribuyente.id(), filtro, paginaSolicitada);
            }
            @Override protected void done() {
                try {
                    List<MovimientoPeriodoFiscal> filas = get();
                    modelo.setRowCount(0);
                    for (MovimientoPeriodoFiscal m : filas) {
                        modelo.addRow(new Object[] {m.documentoId(), m.uuid(), m.sentido(),
                            m.tipoCfdi(), m.fechaEvento(), m.pagoId(), m.montoPago(),
                            m.monedaPago()});
                    }
                    btnAnterior.setEnabled(paginaSolicitada > 0);
                    btnSiguiente.setEnabled(filas.size() == 200);
                    lblEstado.setText("Página " + (paginaSolicitada + 1)
                            + " — " + filas.size() + " movimientos (sin determinación fiscal)");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    modelo.setRowCount(0);
                    error(ex.getCause());
                } finally {
                    btnConsultar.setEnabled(true);
                }
            }
        }.execute();
    }

    private void error(Throwable ex) {
        lblEstado.setText("No se pudo completar la consulta");
        JOptionPane.showMessageDialog(this, ex.getMessage(),
                "Consulta de periodos", JOptionPane.ERROR_MESSAGE);
    }
}
