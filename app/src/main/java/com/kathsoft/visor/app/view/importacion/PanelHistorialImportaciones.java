package com.kathsoft.visor.app.view.importacion;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Lote;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Pagina;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Rango;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Filtro;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.EstadoLote;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Opcion;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Opciones;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.MaskFormatter;

/**
 * RF-013: catálogo de lotes; constructor sin acceso a persistencia para
 * WindowBuilder.
 */
public class PanelHistorialImportaciones extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd-MM-uuuu")
			.withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm:ss");
	private HistorialImportacionesService servicio;
	private UsuarioSesion sesion;
	private Set<String> permisos = Set.of();
	private JFormattedTextField txfDesde, txfHasta;
	private JButton btnConsultar, btnDetalle, btnArchivar, btnAnterior, btnSiguiente;
	private JLabel lblEstado, lblTitulo, lblDesde, lblHasta, lblContribuyente, lblImportador, lblEstadoLote;
	private JPanel panelEtiquetaHistorial, panelCentralContenedor, panelSuperiorBotones, panelInferiorBusqueda;
	private JScrollPane scroll;
	private JComboBox<Opcion> cmbContribuyente, cmbImportador;
	private JComboBox<EstadoLote> cmbEstadoLote;
	private JTable tabla;
	private DefaultTableModel modelo;
	private List<Lote> filas = List.of();
	private Filtro ultimoFiltro;

	private record ResultadoConsulta(Pagina<Lote> pagina, Opciones opciones) {
	}

	private int pagina, generacion;
	private boolean ocupado, siguiente;

	/** Constructor editable; no usa MySQL ni el sistema de archivos. */
	public PanelHistorialImportaciones() {
		inicializarComponentes();
	}

	/**
	 * Inyecta sesión y catálogo de permisos; las operaciones los revalidan en el
	 * servicio.
	 */
	public void configurar(HistorialImportacionesService servicio, UsuarioSesion sesion, Set<String> permisos) {
		boolean cambio = this.servicio != servicio || !java.util.Objects.equals(this.sesion, sesion);
		this.servicio = servicio;
		this.sesion = sesion;
		this.permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
		if (cambio || !autorizado()) {
			generacion++;
			ocupado = false;
			siguiente = false;
			pagina = 0;
			ultimoFiltro = null;
			filas = List.of();
			cmbContribuyente.removeAllItems();
			cmbContribuyente.addItem(new Opcion(0, "Todos"));
			cmbImportador.removeAllItems();
			cmbImportador.addItem(new Opcion(0, "Todos"));
			modelo.setRowCount(0);
			lblEstado.setText(autorizado() ? "Pulse Consultar" : "Sin permiso de consulta");
		}
		habilitar();
	}

	/** Carga la primera página para las fechas actuales. */
	public void recargar() {
		consultar(0, true);
	}

	private void inicializarComponentes() {
		panelEtiquetaHistorial = new JPanel();
		panelEtiquetaHistorial.setBackground(new java.awt.Color(25, 25, 112));
		lblTitulo = new JLabel("Historial de importaciones CFDI");
		lblTitulo.setForeground(java.awt.Color.WHITE);
		lblTitulo.setFont(lblTitulo.getFont().deriveFont(java.awt.Font.BOLD, 16f));
		panelEtiquetaHistorial.add(lblTitulo);
		panelCentralContenedor = new JPanel();
		panelSuperiorBotones = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
		panelInferiorBusqueda = new JPanel();
		lblDesde = new JLabel("Desde");
		lblHasta = new JLabel("Hasta");
		lblContribuyente = new JLabel("Contribuyente");
		lblImportador = new JLabel("Importador");
		lblEstadoLote = new JLabel("Estado");
		cmbContribuyente = new JComboBox<>();
		cmbContribuyente.addItem(new Opcion(0, "Todos"));
		cmbImportador = new JComboBox<>();
		cmbImportador.addItem(new Opcion(0, "Todos"));
		cmbEstadoLote = new JComboBox<>(EstadoLote.values());
		txfDesde = new JFormattedTextField(mascara());
		txfHasta = new JFormattedTextField(mascara());
		txfDesde.setColumns(10);
		txfHasta.setColumns(10);
		txfDesde.setFocusLostBehavior(JFormattedTextField.PERSIST);
		txfHasta.setFocusLostBehavior(JFormattedTextField.PERSIST);
		LocalDate hoy = LocalDate.now();
		txfDesde.setText(hoy.withDayOfMonth(1).format(DIA));
		txfHasta.setText(hoy.format(DIA));
		btnConsultar = new JButton("Consultar");
		btnDetalle = new JButton("Ver detalle");
		btnArchivar = new JButton("Archivar CFDI del lote");
		btnAnterior = new JButton("Anterior");
		btnSiguiente = new JButton("Siguiente");
		lblEstado = new JLabel("Sin consulta");
		modelo = new DefaultTableModel(new String[] { "Lote", "RFC", "Contribuyente", "Usuario", "Inicio", "Fin",
				"Estado", "Aceptados", "Duplicados", "Rechazados", "Errores", "CFDI archivados" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int fila, int columna) {
				return false;
			}
		};
		tabla = new JTable(modelo);
		tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		tabla.setAutoCreateRowSorter(true);
		tabla.getColumnModel().getColumn(2).setPreferredWidth(200);
		tabla.getColumnModel().getColumn(4).setPreferredWidth(145);
		tabla.getColumnModel().getColumn(5).setPreferredWidth(145);
		tabla.getColumnModel().getColumn(1).setPreferredWidth(125);
		scroll = new JScrollPane(tabla);

		panelSuperiorBotones.add(btnDetalle);
		panelSuperiorBotones.add(btnArchivar);
		GroupLayout filtros = new GroupLayout(panelInferiorBusqueda);
		panelInferiorBusqueda.setLayout(filtros);
		filtros.setAutoCreateGaps(true);
		filtros.setHorizontalGroup(filtros.createParallelGroup(GroupLayout.Alignment.LEADING)
				.addGroup(filtros.createSequentialGroup().addComponent(lblContribuyente)
						.addComponent(cmbContribuyente, 120, 210, Short.MAX_VALUE).addComponent(lblImportador)
						.addComponent(cmbImportador, 90, 130, 180))
				.addGroup(filtros.createSequentialGroup().addComponent(lblDesde).addComponent(txfDesde, 96, 105, 115)
						.addComponent(lblHasta).addComponent(txfHasta, 96, 105, 115).addComponent(lblEstadoLote)
						.addComponent(cmbEstadoLote, 95, 110, 130).addComponent(btnConsultar))
				.addGroup(filtros.createSequentialGroup().addComponent(btnAnterior).addComponent(btnSiguiente)
						.addComponent(lblEstado, 0, 200, Short.MAX_VALUE)));
		filtros.setVerticalGroup(filtros.createSequentialGroup()
				.addGroup(filtros.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblContribuyente)
						.addComponent(cmbContribuyente).addComponent(lblImportador).addComponent(cmbImportador))
				.addGroup(filtros.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblDesde)
						.addComponent(txfDesde).addComponent(lblHasta).addComponent(txfHasta)
						.addComponent(lblEstadoLote).addComponent(cmbEstadoLote).addComponent(btnConsultar))
				.addGroup(filtros.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(btnAnterior)
						.addComponent(btnSiguiente).addComponent(lblEstado)));
		GroupLayout central = new GroupLayout(panelCentralContenedor);
		panelCentralContenedor.setLayout(central);
		central.setAutoCreateGaps(true);
		central.setAutoCreateContainerGaps(true);
		central.setHorizontalGroup(central.createParallelGroup(GroupLayout.Alignment.LEADING)
				.addComponent(panelSuperiorBotones, 0, 650, Short.MAX_VALUE)
				.addComponent(scroll, 0, 650, Short.MAX_VALUE)
				.addComponent(panelInferiorBusqueda, 0, 650, Short.MAX_VALUE));
		central.setVerticalGroup(central.createSequentialGroup()
				.addComponent(panelSuperiorBotones, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
						GroupLayout.PREFERRED_SIZE)
				.addComponent(scroll, 120, 260, Short.MAX_VALUE).addComponent(panelInferiorBusqueda,
						GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
		GroupLayout layout = new GroupLayout(this);
		setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
				.addComponent(panelEtiquetaHistorial, 0, 650, Short.MAX_VALUE)
				.addComponent(panelCentralContenedor, 0, 650, Short.MAX_VALUE));
		layout.setVerticalGroup(layout
				.createSequentialGroup().addComponent(panelEtiquetaHistorial, GroupLayout.PREFERRED_SIZE,
						GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
				.addComponent(panelCentralContenedor, 0, 350, Short.MAX_VALUE));
		btnConsultar.addActionListener(e -> consultar(0, true));
		txfDesde.addActionListener(e -> consultar(0, true));
		txfHasta.addActionListener(e -> consultar(0, true));
		btnAnterior.addActionListener(e -> consultar(pagina - 1, false));
		btnSiguiente.addActionListener(e -> consultar(pagina + 1, false));
		btnDetalle.addActionListener(e -> detalle());
		btnArchivar.addActionListener(e -> archivar());
		tabla.getSelectionModel().addListSelectionListener(e -> habilitar());
		habilitar();
	}

	private static MaskFormatter mascara() {
		try {
			MaskFormatter m = new MaskFormatter("##-##-####");
			m.setPlaceholderCharacter('_');
			return m;
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
	}

	private boolean autorizado() {
		return servicio != null && sesion != null && permisos.contains(Permisos.IMPORTACIONES_CONSULTAR);
	}

	private void consultar(int destino, boolean nuevo) {
		if (!autorizado() || ocupado || destino < 0)
			return;
		final Filtro filtro;
		try {
			filtro = nuevo
					? new Filtro(
							new Rango(LocalDate.parse(txfDesde.getText(), DIA),
									LocalDate.parse(txfHasta.getText(), DIA)),
							id(cmbContribuyente), id(cmbImportador), (EstadoLote) cmbEstadoLote.getSelectedItem())
					: ultimoFiltro;
			if (filtro == null)
				return;
		} catch (RuntimeException e) {
			JOptionPane.showMessageDialog(this, "Indique un intervalo válido (dd-mm-aaaa).",
					"Historial de importaciones", JOptionPane.WARNING_MESSAGE);
			return;
		}
		final int token = ++generacion;
		final Long operador = sesion.id();
		final HistorialImportacionesService actual = servicio;
		ocupado = true;
		lblEstado.setText("Consultando...");
		habilitar();
		new SwingWorker<ResultadoConsulta, Void>() {
			@Override
			protected ResultadoConsulta doInBackground() {
				return new ResultadoConsulta(actual.listar(operador, filtro, destino), actual.opciones(operador));
			}

			@Override
			protected void done() {
				if (token != generacion || !autorizado())
					return;
				try {
					ResultadoConsulta consulta = get();
					Pagina<Lote> r = consulta.pagina();
					cargarOpciones(cmbContribuyente, consulta.opciones().contribuyentes(), filtro.contribuyente());
					cargarOpciones(cmbImportador, consulta.opciones().importadores(), filtro.importador());
					filas = r.filas();
					siguiente = r.siguiente();
					pagina = destino;
					ultimoFiltro = filtro;
					modelo.setRowCount(0);
					for (Lote l : filas)
						modelo.addRow(new Object[] { l.id(), l.rfc(), l.contribuyente(), l.usuario(),
								l.inicio() == null ? "" : l.inicio().format(FECHA),
								l.fin() == null ? "" : l.fin().format(FECHA), l.estado(), l.aceptados(), l.duplicados(),
								l.rechazados(), l.errores(), l.archivados() });
					lblEstado.setText("Página " + (pagina + 1) + " — " + filas.size() + " lotes");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					limpiarConsulta();
				} catch (ExecutionException e) {
					limpiarConsulta();
					error(e.getCause());
				} finally {
					ocupado = false;
					habilitar();
				}
			}
		}.execute();
	}

	private void limpiarConsulta() {
		filas = List.of();
		modelo.setRowCount(0);
		siguiente = false;
		pagina = 0;
		ultimoFiltro = null;
		lblEstado.setText("Consulta no disponible");
	}

	private Lote seleccionado() {
		int v = tabla.getSelectedRow();
		if (v < 0)
			return null;
		int i = tabla.convertRowIndexToModel(v);
		return i < filas.size() ? filas.get(i) : null;
	}

	private void detalle() {
		Lote lote = seleccionado();
		if (!autorizado() || ocupado || lote == null || !permisos.contains(Permisos.IMPORTACIONES_CONSULTAR_DETALLE))
			return;
		Fr_DatosLoteImportacion dialogo = new Fr_DatosLoteImportacion();
		dialogo.configurar(servicio, sesion, permisos, lote);
		dialogo.setLocationRelativeTo(this);
		dialogo.recargar();
		dialogo.setVisible(true);
		consultar(0, false);
	}

	private void archivar() {
		Lote lote = seleccionado();
		if (!autorizado() || ocupado || lote == null || lote.fin() == null || "EN_PROCESO".equals(lote.estado())
				|| !permisos.contains(Permisos.IMPORTACIONES_ARCHIVAR_LOTE))
			return;
		String motivo = JOptionPane.showInputDialog(this,
				"Archivar todos los CFDI del lote " + lote.id() + " para su contribuyente.\n"
						+ "Se excluyen de consultas operativas de ese titular, incluso si aparecen en otros lotes.\n"
						+ "Se conservan XML, datos, vínculos e historial. No es cancelación SAT.\n"
						+ "Indique el motivo (máx. 200 caracteres):");
		if (motivo == null)
			return;
		if (motivo.isBlank() || motivo.strip().length() > 200) {
			JOptionPane.showMessageDialog(this, "Motivo obligatorio de 1 a 200 caracteres.");
			return;
		}
		final int token = ++generacion;
		final Long operador = sesion.id();
		final HistorialImportacionesService actual = servicio;
		ocupado = true;
		habilitar();
		lblEstado.setText("Archivando CFDI...");
		new SwingWorker<Integer, Void>() {
			@Override
			protected Integer doInBackground() {
				return actual.archivarLote(operador, lote.id(), motivo);
			}

			@Override
			protected void done() {
				if (token != generacion || !autorizado())
					return;
				try {
					int r = get();
					JOptionPane.showMessageDialog(PanelHistorialImportaciones.this,
							"CFDI archivados para este titular: " + r);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					error(e.getCause());
				} finally {
					ocupado = false;
					habilitar();
					consultar(0, false);
				}
			}
		}.execute();
	}

	private static long id(JComboBox<Opcion> combo) {
		Opcion seleccion = (Opcion) combo.getSelectedItem();
		return seleccion == null ? 0 : seleccion.id();
	}

	private static void cargarOpciones(JComboBox<Opcion> combo, List<Opcion> opciones, long seleccionado) {
		combo.removeAllItems();
		combo.addItem(new Opcion(0, "Todos"));
		for (Opcion o : opciones) {
			combo.addItem(o);
			if (o.id() == seleccionado)
				combo.setSelectedItem(o);
		}
	}

	private void habilitar() {
		boolean libre = autorizado() && !ocupado;
		Lote l = seleccionado();
		txfDesde.setEnabled(libre);
		txfHasta.setEnabled(libre);
		btnConsultar.setEnabled(libre);
		cmbContribuyente.setEnabled(libre);
		cmbImportador.setEnabled(libre);
		cmbEstadoLote.setEnabled(libre);
		btnDetalle.setEnabled(libre && l != null && permisos.contains(Permisos.IMPORTACIONES_CONSULTAR_DETALLE));
		btnArchivar.setEnabled(libre && l != null && l.fin() != null && !"EN_PROCESO".equals(l.estado())
				&& permisos.contains(Permisos.IMPORTACIONES_ARCHIVAR_LOTE));
		btnAnterior.setEnabled(libre && pagina > 0);
		btnSiguiente.setEnabled(libre && siguiente);
	}

	private void error(Throwable e) {
		JOptionPane.showMessageDialog(this, e == null ? "Error desconocido" : e.getMessage(),
				"Historial de importaciones", JOptionPane.ERROR_MESSAGE);
	}
}
