package com.kathsoft.visor.app.view.operacion;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.*;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService;
import com.kathsoft.visor.app.view.visor.Fr_DatosCfdi;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.MaskFormatter;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import java.awt.Component;

/**
 * RF-020: clasificación administrativa, separada de RF-009 y del visor fiscal.
 */
public class PanelClasificacionOperaciones extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd-MM-uuuu")
			.withResolverStyle(ResolverStyle.STRICT);
	private JPanel panelEtiquetaOperaciones, panelCentralContenedor, panelInferiorBusqueda;
	private JLabel lblTitulo, lblTitular, lblPeriodo, lblAnio, lblMes, lblDesde, lblHasta, lblSentido, lblRfc,
			lblFiltro, lblEstado;
	private JComboBox<Titular> cmbTitular;
	private JComboBox<String> cmbPeriodo, cmbSentido;
	private JComboBox<FiltroCategoria> cmbFiltro;
	private JComboBox<Categoria> cmbCategoria;
	private JSpinner spAnio, spMes;
	private JFormattedTextField txfDesde, txfHasta;
	private JTextField txfRfc;
	private JButton btnBuscar, btnClasificar, btnRetirar, btnHistorial, btnDetalle, btnAnterior, btnSiguiente, btnNueva,
			btnPredeterminadas, btnEstadoCategoria;
	private DefaultTableModel modelo;
	private ClasificacionOperacionesService servicio;
	private DetalleCfdiService detalle;
	private UsuarioSesion sesion;
	private Set<String> permisos = Set.of();
	private List<Operacion> filas = List.of();
	private Filtro ultimo;
	private int pagina, generacion;
	private boolean ocupado, siguiente, cargandoTitulares, cargandoCategorias;
	private JTable tabla;
	private JSplitPane splitPane;
	private JScrollPane scroll;
	private JPanel panelHerramientasClasificacion;
	private JLabel lblCategoria;
	private JSeparator separator;
	private List<Categoria> catalogo = List.of();
	private static final String MOTIVO_INICIAL = "Clasificación inicial manual mediante RF-020.";
	private record OpcionCategoria(Long id, String nombre) {
		@Override public String toString() { return nombre; }
	}

	private record FiltroCategoria(long id, String etiqueta) {
		@Override
		public String toString() {
			return etiqueta;
		}
	}

	private record Consulta(Pagina<Operacion> pagina, List<Categoria> categorias) {
	}

	/** Constructor público sin I/O para WindowBuilder. */
	public PanelClasificacionOperaciones() {
		inicializarComponentes();
	}

	/** Inyección y limpieza de datos de la sesión previa. */
	public void configurar(ClasificacionOperacionesService servicio, DetalleCfdiService detalle, UsuarioSesion sesion,
			Set<String> permisos) {
		generacion++;
		ocupado = false;
		this.servicio = servicio;
		this.detalle = detalle;
		this.sesion = sesion;
		this.permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
		cargandoTitulares = true;
		cmbTitular.removeAllItems();
		cargandoTitulares = false;
		limpiar();
		categorias(List.of());
		lblEstado.setText("Seleccione contribuyente");
		habilitar();
	}

	/** Carga titulares al entrar desde el menú. */
	public void recargar() {
		if (!autorizado() || ocupado)
			return;
		var s = servicio;
		Long actor = sesion.id();
		ejecutar(() -> s.titulares(actor), datos -> {
			cargandoTitulares = true;
			cmbTitular.removeAllItems();
			for (Titular t : datos)
				cmbTitular.addItem(t);
			cargandoTitulares = false;
			lblEstado.setText(datos.isEmpty() ? "Sin contribuyentes asignados" : "Seleccione contribuyente y Buscar");
		}, () -> cargarCategorias());
	}

	private void inicializarComponentes() {
		panelEtiquetaOperaciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panelEtiquetaOperaciones.setBackground(new Color(25, 25, 112));
		lblTitulo = new JLabel("Clasificación de operaciones — RF-020");
		lblTitulo.setForeground(Color.WHITE);
		panelEtiquetaOperaciones.add(lblTitulo);
		panelCentralContenedor = new JPanel();
		panelInferiorBusqueda = new JPanel();
		btnBuscar = new JButton("Buscar");
		modelo = new DefaultTableModel(new String[] { "Categoría", "Naturaleza", "Emisión", "Tipo", "Sentido",
				"RFC emisor", "Emisor", "RFC receptor", "Receptor", "Subtotal", "Descuento", "Total CFDI", "Moneda",
				"Último SAT", "UUID" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int r, int c) {
				return c == 0 && puedeEditar(r);
			}
			@Override public void setValueAt(Object valor, int r, int c) {
				if(c != 0 || !(valor instanceof OpcionCategoria opcion) || !puedeEditar(r)) return;
				Operacion op = filas.get(r);
				Filtro f = ultimo;
				int ciclo = generacion;
				// JTable termina de cerrar el editor antes de abrir un diálogo o iniciar I/O.
				SwingUtilities.invokeLater(() -> {
					if(ciclo == generacion && Objects.equals(f, ultimo) && !ocupado)
						aplicar(List.of(op), opcion.id());
				});
				// No se modifica la celda antes de que MySQL confirme la transacción.
			}
		};
		tabla = new JTable();
		tabla.setModel(modelo);
		tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		tabla.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		tabla.setAutoCreateRowSorter(true);
		tabla.getColumnModel().getColumn(0).setCellEditor(new EditorCategoria());
		tabla.getColumnModel().getColumn(0).setCellRenderer(new RenderCategoria());
		tabla.setRowHeight(Math.max(tabla.getRowHeight(), 25));
		
		for (int i = 0; i < tabla.getColumnCount(); i++)
			tabla.getColumnModel().getColumn(i).setPreferredWidth(i == 14 ? 260 : i == 0 || i == 6 || i == 8 ? 210 : 120);
		lblTitular = new JLabel("Titular");
		cmbTitular = new JComboBox<>();
		lblPeriodo = new JLabel("Periodo");
		cmbPeriodo = new JComboBox<>(new String[] { "Mes", "Ejercicio", "Rango" });
		lblAnio = new JLabel("Año");
		spAnio = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 1000, 9998, 1));
		spAnio.setEditor(new JSpinner.NumberEditor(spAnio, "0"));
		lblMes = new JLabel("Mes");
		spMes = new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1));
		lblDesde = new JLabel("Desde");
		lblHasta = new JLabel("Hasta");
		txfDesde = new JFormattedTextField();
		txfHasta = new JFormattedTextField();
		
		try {
			MaskFormatter a = new MaskFormatter("##-##-####");
			a.setPlaceholderCharacter('_');
			a.install(txfDesde);
			MaskFormatter b = new MaskFormatter("##-##-####");
			b.setPlaceholderCharacter('_');
			b.install(txfHasta);
		} catch (ParseException ex) {
			throw new IllegalStateException(ex);
		}
		txfDesde.setText(LocalDate.now().withDayOfMonth(1).format(DIA));
		txfHasta.setText(LocalDate.now().format(DIA));
		lblSentido = new JLabel("Sentido");
		cmbSentido = new JComboBox<>(new String[] { "RECIBIDO", "EMITIDO", "AMBOS", "" });
		lblRfc = new JLabel("RFC");
		txfRfc = new JTextField();
		lblFiltro = new JLabel("Filtro categoría");
		cmbFiltro = new JComboBox<>();
		lblEstado = new JLabel("Sin consulta");
		GroupLayout f = new GroupLayout(panelInferiorBusqueda);
		f.setHorizontalGroup(
			f.createParallelGroup(Alignment.LEADING)
				.addGroup(f.createSequentialGroup()
					.addComponent(lblTitular)
					.addComponent(cmbTitular, 85, 502, Short.MAX_VALUE)
					.addComponent(lblPeriodo)
					.addComponent(cmbPeriodo, 60, 100, 100)
					.addComponent(lblAnio)
					.addComponent(spAnio, 48, 70, 70)
					.addComponent(lblMes)
					.addComponent(spMes, 35, 50, 50))
				.addGroup(f.createSequentialGroup()
					.addComponent(lblDesde)
					.addComponent(txfDesde, 82, 100, 100)
					.addComponent(lblHasta)
					.addComponent(txfHasta, 82, 100, 100)
					.addComponent(lblSentido)
					.addComponent(cmbSentido, 90, 130, 130))
				.addGroup(f.createSequentialGroup()
					.addComponent(lblRfc)
					.addComponent(txfRfc, 70, 325, Short.MAX_VALUE)
					.addComponent(lblFiltro)
					.addComponent(cmbFiltro, 90, 384, Short.MAX_VALUE)
					.addComponent(btnBuscar))
				.addComponent(lblEstado, 0, 939, Short.MAX_VALUE)
		);
		f.setVerticalGroup(
			f.createParallelGroup(Alignment.LEADING)
				.addGroup(f.createSequentialGroup()
					.addGroup(f.createParallelGroup(Alignment.BASELINE)
						.addComponent(lblTitular)
						.addComponent(cmbTitular, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(lblPeriodo)
						.addComponent(cmbPeriodo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(lblAnio)
						.addComponent(spAnio, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(lblMes)
						.addComponent(spMes, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
					.addGroup(f.createParallelGroup(Alignment.BASELINE)
						.addComponent(lblDesde)
						.addComponent(txfDesde, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(lblHasta)
						.addComponent(txfHasta, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(lblSentido)
						.addComponent(cmbSentido, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
					.addGroup(f.createParallelGroup(Alignment.BASELINE)
						.addComponent(lblRfc)
						.addComponent(txfRfc, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(lblFiltro)
						.addComponent(cmbFiltro, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(btnBuscar))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(lblEstado))
		);
		panelInferiorBusqueda.setLayout(f);
		f.setAutoCreateGaps(true);
		
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setDividerSize(5);
		GroupLayout c = new GroupLayout(panelCentralContenedor);
		c.setHorizontalGroup(
			c.createParallelGroup(Alignment.LEADING)
				.addComponent(panelInferiorBusqueda, 0, 939, Short.MAX_VALUE)
				.addGroup(c.createSequentialGroup()
					.addComponent(splitPane, GroupLayout.DEFAULT_SIZE, 927, Short.MAX_VALUE)
					.addGap(12))
		);
		c.setVerticalGroup(
			c.createParallelGroup(Alignment.LEADING)
				.addGroup(Alignment.TRAILING, c.createSequentialGroup()
					.addGap(10)
					.addComponent(splitPane, GroupLayout.DEFAULT_SIZE, 447, Short.MAX_VALUE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(panelInferiorBusqueda, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
		);
		
		scroll = new JScrollPane((Component) null);
		splitPane.setRightComponent(scroll);
				
		scroll.setViewportView(tabla);
		
		panelHerramientasClasificacion = new JPanel();
		splitPane.setLeftComponent(panelHerramientasClasificacion);
		btnClasificar = new JButton("Clasificar");
		btnClasificar.setToolTipText("Ctrl+Enter: aplicar la categoría lateral a las filas seleccionadas");
		btnClasificar.addActionListener(e -> clasificar(false));
		btnRetirar = new JButton("Retirar");
		btnRetirar.addActionListener(e -> clasificar(true));
		btnDetalle = new JButton("Ver CFDI");
		btnDetalle.addActionListener(e -> detalle());
		btnHistorial = new JButton("Historial");
		btnHistorial.setToolTipText("Ctrl+H: historial de la fila seleccionada");
		btnHistorial.addActionListener(e -> historial());
		btnAnterior = new JButton("Anterior");
		btnAnterior.addActionListener(e -> consultar(ultimo, pagina - 1));
		btnSiguiente = new JButton("Siguiente");
		btnSiguiente.addActionListener(e -> consultar(ultimo, pagina + 1));
		
		separator = new JSeparator();
		cmbCategoria = new JComboBox<>();
		cmbCategoria.addActionListener(e -> {
			if (!cargandoCategorias)
				habilitar();
		});
		
		lblCategoria = new JLabel("Categoría");
		btnNueva = new JButton("Nueva");
		btnNueva.addActionListener(e -> nueva());
		btnPredeterminadas = new JButton("Iniciales");
		btnPredeterminadas.addActionListener(e -> predeterminadas());
		btnEstadoCategoria = new JButton("Activar/desactivar");
		btnEstadoCategoria.addActionListener(e -> estadoCategoria());
		GroupLayout gl_panelHerramientasClasificacion = new GroupLayout(panelHerramientasClasificacion);
		gl_panelHerramientasClasificacion.setHorizontalGroup(
			gl_panelHerramientasClasificacion.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_panelHerramientasClasificacion.createSequentialGroup()
					.addContainerGap()
					.addGroup(gl_panelHerramientasClasificacion.createParallelGroup(Alignment.LEADING)
						.addComponent(btnEstadoCategoria, GroupLayout.PREFERRED_SIZE, 131, Short.MAX_VALUE)
						.addComponent(cmbCategoria, 0, 125, Short.MAX_VALUE)
						.addGroup(gl_panelHerramientasClasificacion.createSequentialGroup()
							.addComponent(lblCategoria)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(separator, GroupLayout.PREFERRED_SIZE, 73, Short.MAX_VALUE))
						.addComponent(btnRetirar, GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE)
						.addComponent(btnClasificar, GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE)
						.addComponent(btnDetalle, GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE)
						.addComponent(btnHistorial, GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE)
						.addComponent(btnAnterior, GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE)
						.addComponent(btnSiguiente, GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE)
						.addComponent(btnNueva, GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE)
						.addComponent(btnPredeterminadas, GroupLayout.DEFAULT_SIZE, 125, Short.MAX_VALUE))
					.addContainerGap())
		);
		gl_panelHerramientasClasificacion.setVerticalGroup(
			gl_panelHerramientasClasificacion.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_panelHerramientasClasificacion.createSequentialGroup()
					.addContainerGap()
					.addComponent(btnClasificar)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(btnRetirar)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(btnDetalle)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(btnHistorial)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(btnAnterior)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(btnSiguiente)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(gl_panelHerramientasClasificacion.createParallelGroup(Alignment.LEADING)
						.addComponent(separator, GroupLayout.PREFERRED_SIZE, 2, GroupLayout.PREFERRED_SIZE)
						.addComponent(lblCategoria))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(cmbCategoria, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(btnNueva)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(btnPredeterminadas)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(btnEstadoCategoria)
					.addContainerGap(32, Short.MAX_VALUE))
		);
		panelHerramientasClasificacion.setLayout(gl_panelHerramientasClasificacion);
		splitPane.setDividerLocation(165);
		splitPane.setResizeWeight(0.0);
		panelHerramientasClasificacion.setMinimumSize(new java.awt.Dimension(145, 0));
		scroll.setMinimumSize(new java.awt.Dimension(120, 60));
		panelCentralContenedor.setLayout(c);
		c.setAutoCreateGaps(true);
		c.setAutoCreateContainerGaps(true);
		GroupLayout g = new GroupLayout(this);
		setLayout(g);
		g.setAutoCreateGaps(true);
		g.setHorizontalGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING)
				.addComponent(panelEtiquetaOperaciones, 0, 620, Short.MAX_VALUE)
				.addComponent(panelCentralContenedor, 0, 620, Short.MAX_VALUE));
		g.setVerticalGroup(g
				.createSequentialGroup().addComponent(panelEtiquetaOperaciones, GroupLayout.PREFERRED_SIZE,
						GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
				.addComponent(panelCentralContenedor, 0, 350, Short.MAX_VALUE));
		cmbTitular.addActionListener(e -> {
			if (!cargandoTitulares && !ocupado) {
				limpiar();
				categorias(List.of());
				cargarCategorias();
			}
		});
		tabla.getSelectionModel().addListSelectionListener(e -> habilitar());
		cmbPeriodo.addActionListener(e -> habilitar());
		btnBuscar.addActionListener(e -> buscar());
		txfRfc.addActionListener(e -> buscar());
		txfDesde.addActionListener(e -> buscar());
		txfHasta.addActionListener(e -> buscar());
		categorias(List.of());
		instalarAtajos();
		habilitar();
	}

	private boolean autorizado() {
		return servicio != null && sesion != null && permisos.contains(Permisos.OPERACIONES_CONSULTAR);
	}

	private Titular titular() {
		return (Titular) cmbTitular.getSelectedItem();
	}

	private Categoria categoria() {
		return (Categoria) cmbCategoria.getSelectedItem();
	}

	private void habilitar() {
		boolean libre = autorizado() && !ocupado;
		boolean t = libre && titular() != null;
		boolean seleccion = t && ultimo != null && tabla.getSelectedRowCount() > 0;
		boolean gestion = t && permisos.contains(Permisos.OPERACIONES_GESTIONAR_CATEGORIAS);
		boolean cambio = seleccion && permisos.contains(Permisos.OPERACIONES_CLASIFICAR);
		btnBuscar.setEnabled(t);
		btnClasificar.setEnabled(cambio && categoria() != null && categoria().activa());
		btnRetirar.setEnabled(cambio);
		btnHistorial.setEnabled(seleccion && tabla.getSelectedRowCount() == 1);
		tabla.setEnabled(libre);
		btnDetalle.setEnabled(btnHistorial.isEnabled() && detalle != null
				&& permisos.containsAll(Set.of(Permisos.CFDI_CONSULTAR, Permisos.CFDI_CONSULTAR_DETALLE)));
		btnAnterior.setEnabled(t && ultimo != null && pagina > 0);
		btnSiguiente.setEnabled(t && ultimo != null && siguiente);
		btnNueva.setEnabled(gestion);
		btnPredeterminadas.setEnabled(gestion);
		btnEstadoCategoria.setEnabled(gestion && categoria() != null);
		cmbTitular.setEnabled(libre);
		cmbPeriodo.setEnabled(t);
		cmbSentido.setEnabled(t);
		cmbFiltro.setEnabled(t);
		cmbCategoria.setEnabled(t);
		txfRfc.setEnabled(t);
		spAnio.setEnabled(t && cmbPeriodo.getSelectedIndex() != 2);
		spMes.setEnabled(t && cmbPeriodo.getSelectedIndex() == 0);
		txfDesde.setEnabled(t && cmbPeriodo.getSelectedIndex() == 2);
		txfHasta.setEnabled(t && cmbPeriodo.getSelectedIndex() == 2);
	}

	private void limpiar() {
		cancelarEdicion();
		filas = List.of();
		ultimo = null;
		pagina = 0;
		siguiente = false;
		modelo.setRowCount(0);
	}

	private void categorias(List<Categoria> datos) {
		cancelarEdicion();
		catalogo = List.copyOf(datos);
		long filtro = cmbFiltro.getSelectedItem() instanceof FiltroCategoria f ? f.id() : -1;
		long cat = categoria() == null ? 0 : categoria().id();
		cargandoCategorias = true;
		cmbFiltro.removeAllItems();
		cmbCategoria.removeAllItems();
		cmbFiltro.addItem(new FiltroCategoria(-1, "Todas"));
		cmbFiltro.addItem(new FiltroCategoria(0, "Sin clasificación"));
		for (Categoria c : datos) {
			FiltroCategoria f = new FiltroCategoria(c.id(), c.toString());
			cmbFiltro.addItem(f);
			cmbCategoria.addItem(c);
			if (c.id() == filtro)
				cmbFiltro.setSelectedItem(f);
			if (c.id() == cat)
				cmbCategoria.setSelectedItem(c);
		}
		if (filtro == 0)
			cmbFiltro.setSelectedIndex(1);
		cargandoCategorias = false;
	}

	private void cargarCategorias() {
		Titular t = titular();
		if (!autorizado() || ocupado || t == null)
			return;
		var s = servicio;
		Long actor = sesion.id();
		ejecutar(() -> s.categorias(actor, t.id()), this::categorias, () -> {
		});
	}

	private void buscar() {
		if (!btnBuscar.isEnabled())
			return;
		try {
			spAnio.commitEdit();
			spMes.commitEdit();
			int a = (Integer) spAnio.getValue(), m = (Integer) spMes.getValue();
			LocalDate desde, hasta;
			if (cmbPeriodo.getSelectedIndex() == 0) {
				YearMonth ym = YearMonth.of(a, m);
				desde = ym.atDay(1);
				hasta = ym.atEndOfMonth();
			} else if (cmbPeriodo.getSelectedIndex() == 1) {
				desde = LocalDate.of(a, 1, 1);
				hasta = LocalDate.of(a, 12, 31);
			} else {
				desde = LocalDate.parse(txfDesde.getText(), DIA);
				hasta = LocalDate.parse(txfHasta.getText(), DIA);
			}
			FiltroCategoria f = (FiltroCategoria) cmbFiltro.getSelectedItem();
			consultar(new Filtro(titular().id(), desde, hasta, (String) cmbSentido.getSelectedItem(), txfRfc.getText(),
					f == null ? -1 : f.id()), 0);
		} catch (RuntimeException | ParseException ex) {
			error(ex);
		}
	}

	private void consultar(Filtro f, int p) {
		if (f == null || p < 0 || ocupado || !autorizado())
			return;
		var s = servicio;
		Long actor = sesion.id();
		limpiar();
		lblEstado.setText("Consultando...");
		ejecutar(() -> new Consulta(s.consultar(actor, f, p), s.categorias(actor, f.titular())), datos -> {
			categorias(datos.categorias());
			filas = datos.pagina().filas();
			modelo.setRowCount(0);
			for (Operacion x : filas)
				modelo.addRow(new Object[] { opcionActual(x),
						x.naturaleza(), x.emision(), x.tipo(), x.sentido(), x.rfcEmisor(), x.emisor(), x.rfcReceptor(),
						x.receptor(), x.subtotal(), x.descuento(), x.total(), x.moneda(), x.estadoSat(), x.uuid() });
			ultimo = f;
			pagina = p;
			siguiente = datos.pagina().siguiente();
			lblEstado.setText("Página " + (p + 1) + " · " + filas.size() + " operaciones (orden local por columna)");
		}, () -> {
		});
	}

	private List<Operacion> seleccionadas() {
		List<Operacion> salida = new ArrayList<>();
		for (int i : tabla.getSelectedRows()) {
			int m = tabla.convertRowIndexToModel(i);
			if (m >= 0 && m < filas.size())
				salida.add(filas.get(m));
		}
		return salida;
	}

	/** Solo la columna de categoría admite edición, con autorización y datos vigentes. */
	private boolean puedeEditar(int fila) {
		return autorizado() && !ocupado && ultimo != null && fila >= 0 && fila < filas.size()
			&& permisos.contains(Permisos.OPERACIONES_CLASIFICAR);
	}

	private OpcionCategoria opcionActual(Operacion op) {
		Categoria cat = catalogo.stream().filter(c -> Objects.equals(c.id(), op.categoriaId())).findFirst().orElse(null);
		return new OpcionCategoria(op.categoriaId(), op.categoriaId() == null ? "Sin clasificación"
			: op.categoria() + (cat != null && !cat.activa() ? " (inactiva)" : ""));
	}

	/** Editor reutilizable; las categorías inactivas solo aparecen como valor actual. */
	private final class EditorCategoria extends AbstractCellEditor implements TableCellEditor {
		private static final long serialVersionUID = 1L;
		private final JComboBox<OpcionCategoria> combo = new JComboBox<>();
		private boolean preparando;
		EditorCategoria() {
			combo.addActionListener(e -> { if(!preparando) stopCellEditing(); });
		}
		@Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int fila, int columna) {
			preparando = true;
			try {
				Operacion op = filas.get(t.convertRowIndexToModel(fila));
				OpcionCategoria actual = opcionActual(op);
				combo.removeAllItems();
				combo.addItem(new OpcionCategoria(null, "Sin clasificación"));
				for(Categoria c : catalogo) {
					if(c.activa()) combo.addItem(new OpcionCategoria(c.id(), c.nombre()));
				}
				if(op.categoriaId() != null && catalogo.stream().noneMatch(c -> c.activa() && Objects.equals(c.id(),op.categoriaId())))
					combo.addItem(actual);
				combo.setSelectedItem(actual);
			} finally { preparando = false; }
			return combo;
		}
		@Override public Object getCellEditorValue() { return combo.getSelectedItem(); }
	}

	/** Un único JComboBox decorativo para todas las filas; nunca consulta BD. */
	private final class RenderCategoria extends JComboBox<OpcionCategoria> implements TableCellRenderer {
		private static final long serialVersionUID = 1L;
		@Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean foco,int fila,int columna) {
			removeAllItems();
			if(v instanceof OpcionCategoria o) addItem(o);
			setEnabled(puedeEditar(t.convertRowIndexToModel(fila)));
			setBackground(sel?t.getSelectionBackground():t.getBackground());
			setForeground(sel?t.getSelectionForeground():t.getForeground());
			return this;
		}
	}

	private void cancelarEdicion() {
		if(tabla.isEditing()) tabla.getCellEditor().cancelCellEditing();
	}

	private void instalarAtajos() {
		atajo(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
			"aplicarCategoria", () -> { if(!tabla.isEditing()) clasificar(false); });
		atajo(tabla, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
			"aplicarCategoria", () -> { if(!tabla.isEditing()) clasificar(false); });
		atajo(tabla, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
			"seleccionarPagina", () -> { if(tabla.isEnabled() && !tabla.isEditing()) tabla.selectAll(); });
		atajo(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
			"buscarRfc", () -> { if(txfRfc.isEnabled()) { cancelarEdicion(); txfRfc.requestFocusInWindow(); txfRfc.selectAll(); } });
		atajo(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK),
			"historialOperacion", () -> { if(!tabla.isEditing()) historial(); });
		atajo(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_F5,0),
			"actualizarOperaciones", () -> { if(ultimo != null && !ocupado) consultar(ultimo,pagina); });
		atajo(tabla, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),
			"cancelarCategoria", this::cancelarEdicion);
	}

	private static void atajo(JComponent componente,int condicion,KeyStroke tecla,String nombre,Runnable accion) {
		componente.getInputMap(condicion).put(tecla,nombre);
		componente.getActionMap().put(nombre,new AbstractAction(){
			private static final long serialVersionUID=1L;
			@Override public void actionPerformed(ActionEvent e){accion.run();}
		});
	}

	private void clasificar(boolean retirar) {
		if (retirar ? !btnRetirar.isEnabled() : !btnClasificar.isEnabled()) return;
		cancelarEdicion();
		aplicar(seleccionadas(), retirar ? null : categoria().id());
	}

	/** Solo la primera asignación (sin eventos anteriores) admite motivo automático. */
	static boolean motivoAutomatico(List<Operacion> cambios, Long categoria) {
		return categoria != null && !cambios.isEmpty() && cambios.stream().allMatch(o -> o.revision() == 0 && o.categoriaId() == null);
	}

	private void aplicar(List<Operacion> operaciones, Long id) {
		if(ocupado || ultimo == null || !autorizado() || !permisos.contains(Permisos.OPERACIONES_CLASIFICAR)) return;
		List<Operacion> cambios = operaciones.stream().filter(o -> !Objects.equals(o.categoriaId(),id)).toList();
		if(cambios.isEmpty()) return;
		if(id != null && catalogo.stream().noneMatch(c -> c.id() == id && c.activa())) {
			error(new IllegalArgumentException("Seleccione una categoría activa")); return;
		}
		String motivo = motivoAutomatico(cambios,id) ? MOTIVO_INICIAL
			: JOptionPane.showInputDialog(this,(id == null ? "Retirar categoría" : "Reclasificar")+" "+cambios.size()+" operaciones. Motivo obligatorio:");
		if(motivo == null) return;
		if(motivo.isBlank() || motivo.strip().length() > 600) {
			error(new IllegalArgumentException("Capture un motivo de 1 a 600 caracteres")); return;
		}
		List<Seleccion> sel = cambios.stream().map(o -> new Seleccion(o.id(),o.revision())).toList();
		var s = servicio; Long actor = sesion.id(); Filtro f = ultimo; int p = pagina;
		lblEstado.setText("Guardando clasificación...");
		ejecutar(() -> s.clasificar(actor,f.titular(),sel,id,motivo),
			n -> lblEstado.setText(n+" operaciones modificadas."), () -> consultar(f,p));
	}

	private void historial() {
		if (!btnHistorial.isEnabled())
			return;
		var d = new Fr_HistorialClasificacionOperacion(owner(), servicio, sesion.id(), ultimo.titular(),
				seleccionadas().get(0));
		d.setLocationRelativeTo(this);
		d.setVisible(true);
	}

	private void detalle() {
		if (!btnDetalle.isEnabled())
			return;
		var d = new Fr_DatosCfdi(owner(), detalle, sesion, ultimo.titular(), seleccionadas().get(0).id());
		d.setLocationRelativeTo(this);
		d.setVisible(true);
	}

	private Frame owner() {
		var w = SwingUtilities.getWindowAncestor(this);
		return w instanceof Frame f ? f : null;
	}

	private void nueva() {
		if (!btnNueva.isEnabled())
			return;
		var d = new Fr_DatosCategoriaOperacion(owner());
		d.setLocationRelativeTo(this);
		d.setVisible(true);
		var x = d.solicitud();
		if (x == null)
			return;
		var s = servicio;
		Long actor = sesion.id();
		long t = titular().id();
		ejecutar(() -> {
			s.crearCategoria(actor, t, x.clave(), x.nombre(), x.naturaleza());
			return null;
		}, v -> {
		}, this::cargarCategorias);
	}

	private void predeterminadas() {
		if (!btnPredeterminadas.isEnabled())
			return;
		var s = servicio;
		Long actor = sesion.id();
		long t = titular().id();
		ejecutar(() -> s.crearPredeterminadas(actor, t),
				n -> JOptionPane.showMessageDialog(this, n + " categorías nuevas."), this::cargarCategorias);
	}

	private void estadoCategoria() {
		if (!btnEstadoCategoria.isEnabled())
			return;
		var c = categoria();
		if (JOptionPane.showConfirmDialog(this,
				(c.activa() ? "Desactivar " : "Reactivar ") + c + "? No cambia el historial.", "Categoría",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			return;
		var s = servicio;
		Long actor = sesion.id();
		long t = titular().id();
		ejecutar(() -> s.cambiarEstadoCategoria(actor, t, c.id(), !c.activa()), v -> {
		}, this::cargarCategorias);
	}

	private <T> void ejecutar(Callable<T> trabajo, Consumer<T> resultado, Runnable despues) {
		if (ocupado)
			return;
		int ciclo = ++generacion;
		ocupado = true;
		habilitar();
		new SwingWorker<T, Void>() {
			@Override
			protected T doInBackground() throws Exception {
				return trabajo.call();
			}

			@Override
			protected void done() {
				if (ciclo != generacion)
					return;
				boolean ok = false;
				try {
					resultado.accept(get());
					ok = true;
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException ex) {
					error(ex.getCause());
				} finally {
					ocupado = false;
					habilitar();
				}
				if (ok)
					despues.run();
			}
		}.execute();
	}

	private void error(Throwable ex) {
		JOptionPane.showMessageDialog(this, ex.getMessage(), "Clasificación de operaciones", JOptionPane.ERROR_MESSAGE);
	}
}
