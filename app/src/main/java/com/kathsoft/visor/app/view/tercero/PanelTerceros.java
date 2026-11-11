package com.kathsoft.visor.app.view.tercero;

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.kathsoft.visor.app.model.dto.ContribuyenteListado;
import com.kathsoft.visor.app.model.dto.TerceroListado;
import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.contribuyente.ContribuyentesService;
import com.kathsoft.visor.app.service.tercero.TercerosService;

/**
 * RF-005: catálogo de terceros filtrado por contribuyente autorizado.
 * Constructor vacío y GroupLayout compatibles con Eclipse WindowBuilder.
 */
public class PanelTerceros extends JPanel {
    private static final long serialVersionUID = 1L;
    private TercerosService servicio;
    private ContribuyentesService contribuyentes;
    private UsuarioSesion sesion;
    private Set<String> permisos = Set.of();
    private JComboBox<ContribuyenteListado> cmbContribuyente;
    private JTextField txfBuscar;
    private JButton btnAgregar;
    private JButton btnEditar;
    private JButton btnRoles;
    private JButton btnActualizar;
    private JButton btnBuscar;
    private JButton btnLimpiar;
    private JTable tabla;
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> filtro;
    private JLabel lblEstado;
    private boolean ocupado;

    /** Inicializa únicamente componentes Swing, sin consultar MySQL. */
    public PanelTerceros() {
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel titulo = new JLabel("Administración de terceros");
        titulo.setFont(titulo.getFont().deriveFont(java.awt.Font.BOLD, 17f));
        JLabel lblContribuyente = new JLabel("Contribuyente");
        cmbContribuyente = new JComboBox<>();
        cmbContribuyente.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> lista, Object valor, int indice,
                    boolean seleccionado, boolean foco) {
                String texto = valor instanceof ContribuyenteListado c
                        ? c.rfc() + " — " + c.nombreRazonSocial() : "Seleccione contribuyente";
                return super.getListCellRendererComponent(
                        lista, texto, indice, seleccionado, foco);
            }
        });
        cmbContribuyente.addActionListener(e -> recargarTerceros());
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        btnAgregar = new JButton("Agregar");
        btnEditar = new JButton("Detalle / Editar");
        btnRoles = new JButton("Funciones");
        btnActualizar = new JButton("Actualizar");
        acciones.add(btnAgregar);
        acciones.add(btnEditar);
        acciones.add(btnRoles);
        acciones.add(btnActualizar);
        modelo = new DefaultTableModel(
                new Object[] {"ID", "RFC", "Nombre / razón social", "País", "Registro extranjero"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int f, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 0 ? Long.class : String.class;
            }
        };
        tabla = new JTable(modelo);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(25);
        filtro = new TableRowSorter<>(modelo);
        tabla.setRowSorter(filtro);
        JScrollPane scroll = new JScrollPane(tabla);
        JLabel lblBuscar = new JLabel("Buscar");
        txfBuscar = new JTextField(22);
        btnBuscar = new JButton("Buscar");
        btnLimpiar = new JButton("Limpiar");
        txfBuscar.addActionListener(e -> filtrar());
        btnBuscar.addActionListener(e -> filtrar());
        btnLimpiar.addActionListener(e -> {
            txfBuscar.setText("");
            filtrar();
            txfBuscar.requestFocusInWindow();
        });
        txfBuscar.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
        });
        lblEstado = new JLabel("Seleccione un contribuyente para consultar terceros.");
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(titulo)
            .addGroup(layout.createSequentialGroup().addComponent(lblContribuyente)
                .addComponent(cmbContribuyente, GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE))
            .addComponent(acciones)
            .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup().addComponent(lblBuscar)
                .addComponent(txfBuscar, GroupLayout.PREFERRED_SIZE, 230, GroupLayout.PREFERRED_SIZE)
                .addComponent(btnBuscar)
                .addComponent(btnLimpiar))
            .addComponent(lblEstado));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(titulo)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblContribuyente).addComponent(cmbContribuyente,
                    GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addComponent(acciones, GroupLayout.PREFERRED_SIZE,
                    GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lblBuscar).addComponent(txfBuscar,
                    GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(btnBuscar).addComponent(btnLimpiar))
            .addComponent(lblEstado));
        btnAgregar.addActionListener(e -> abrir(null, 0));
        btnEditar.addActionListener(e -> { TerceroListado t = seleccionado(); if (t != null) abrir(t, 0); });
        btnRoles.addActionListener(e -> { TerceroListado t = seleccionado(); if (t != null) abrir(t, 1); });
        btnActualizar.addActionListener(e -> recargar());
        habilitar();
    }

    /** Configura servicios sin ejecutar consultas durante el diseño visual. */
    public void configurar(TercerosService servicio, ContribuyentesService contribuyentes,
            UsuarioSesion sesion, Set<String> permisos) {
        this.servicio = servicio;
        this.contribuyentes = contribuyentes;
        this.sesion = sesion;
        this.permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
        if (!this.permisos.contains(Permisos.TERCEROS_CONSULTAR)) modelo.setRowCount(0);
        habilitar();
    }

    /** Carga contribuyentes accesibles fuera del hilo de eventos. */
    public void recargar() {
        if (ocupado || servicio == null || sesion == null) return;
        ocupado = true;
        habilitar();
        new SwingWorker<List<ContribuyenteListado>, Void>() {
            @Override protected List<ContribuyenteListado> doInBackground() {
                return contribuyentes.listar(sesion.id());
            }
            @Override protected void done() {
                try {
                    List<ContribuyenteListado> lista = get();
                    Long anterior = contribuyenteId();
                    cmbContribuyente.removeAllItems();
                    for (ContribuyenteListado c : lista) cmbContribuyente.addItem(c);
                    if (anterior != null) {
                        for (int i = 0; i < cmbContribuyente.getItemCount(); i++) {
                            if (anterior.equals(cmbContribuyente.getItemAt(i).id())) {
                                cmbContribuyente.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) { error(e.getCause()); }
                finally { ocupado = false; habilitar(); recargarTerceros(); }
            }
        }.execute();
    }

    private void recargarTerceros() {
        Long titular = contribuyenteId();
        modelo.setRowCount(0);
        if (ocupado || titular == null || servicio == null || sesion == null
                || !permisos.contains(Permisos.TERCEROS_CONSULTAR)) return;
        ocupado = true;
        habilitar();
        new SwingWorker<List<TerceroListado>, Void>() {
            @Override protected List<TerceroListado> doInBackground() {
                return servicio.listar(sesion.id(), titular);
            }
            @Override protected void done() {
                try {
                    List<TerceroListado> datos = get();
                    if (titular.equals(contribuyenteId())
                            && permisos.contains(Permisos.TERCEROS_CONSULTAR)) {
                        for (TerceroListado t : datos) {
                            modelo.addRow(new Object[] {t.id(), t.rfc(), t.nombreRazonSocial(),
                                    t.paisFiscal(), t.numeroRegistroFiscalExtranjero()});
                        }
                    }
                    filtrar();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) { error(e.getCause()); }
                finally { ocupado = false; habilitar(); }
            }
        }.execute();
    }

    private void filtrar() {
        String texto = txfBuscar.getText().strip();
        filtro.setRowFilter(texto.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 1, 2, 3, 4));
        lblEstado.setText(tabla.getRowCount() + " tercero(s) visibles");
    }

    private Long contribuyenteId() {
        Object seleccionado = cmbContribuyente.getSelectedItem();
        return seleccionado instanceof ContribuyenteListado c ? c.id() : null;
    }

    private TerceroListado seleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un tercero.");
            return null;
        }
        int indice = tabla.convertRowIndexToModel(fila);
        return new TerceroListado((Long) modelo.getValueAt(indice, 0),
                (String) modelo.getValueAt(indice, 1), (String) modelo.getValueAt(indice, 2),
                (String) modelo.getValueAt(indice, 3), (String) modelo.getValueAt(indice, 4));
    }

    private void abrir(TerceroListado tercero, int pestana) {
        Long titular = contribuyenteId();
        if (titular == null || servicio == null || sesion == null) return;
        Window ventana = SwingUtilities.getWindowAncestor(this);
        Frame padre = ventana instanceof Frame ? (Frame) ventana : null;
        new Fr_DatosTercero(padre, servicio, sesion, titular, tercero, permisos,
                this::recargarTerceros, pestana).setVisible(true);
    }

    private void habilitar() {
        boolean activo = !ocupado && servicio != null && sesion != null
                && contribuyenteId() != null;
        btnAgregar.setEnabled(activo && permisos.contains(Permisos.TERCEROS_CREAR));
        btnEditar.setEnabled(activo && permisos.contains(Permisos.TERCEROS_CONSULTAR));
        btnRoles.setEnabled(activo && permisos.contains(Permisos.TERCEROS_ASIGNAR_ROLES));
        btnActualizar.setEnabled(activo);
        cmbContribuyente.setEnabled(!ocupado);
    }

    private void error(Throwable e) {
        JOptionPane.showMessageDialog(this, e == null ? "Error inesperado" : e.getMessage(),
                "Administración de terceros", JOptionPane.ERROR_MESSAGE);
    }
}
