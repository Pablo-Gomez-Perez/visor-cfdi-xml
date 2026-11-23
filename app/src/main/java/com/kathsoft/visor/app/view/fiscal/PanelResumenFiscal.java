package com.kathsoft.visor.app.view.fiscal;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.fiscal.*;
import com.kathsoft.visor.app.service.fiscal.ResumenFiscalService.Seccion;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Titular;
import com.kathsoft.visor.app.service.visor.DetalleCfdiService;
import com.kathsoft.visor.app.view.visor.Fr_DatosCfdi;
import com.kathsoft.visor.app.view.util.SelectorColumnasTabla;
import java.awt.Frame;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.MaskFormatter;

/**
 * Papeles de trabajo mensuales en una tarjeta Swing. Consultas explícitas y
 * paginadas en SwingWorker. El constructor público no inicializa persistencia
 * ni servicios; GroupLayout permanece explícito.
 */
public class PanelResumenFiscal extends JPanel {
	private static final long serialVersionUID = 1L;
	private JPanel acciones, filtros;
	private JTabbedPane pestanas;
	private JTable tablaConciliacionDrP, tablaConciliacionTotales, tablaTributosAgrupados;
	private JScrollPane scrollConciliacionDrP, scrollConciliacionTotales, scrollTributosAgrupados;
	private JTable tablaIvaResumen, tablaIvaManual, tablaImpuestosDr;
	private JScrollPane scrollIvaResumen, scrollIvaManual, scrollImpuestosDr;
	private JTable tablaGenerales, tablaIngresos, tablaEgresos, tablaIva, tablaIeps, tablaRetenciones, tablaTerceros,
			tablaFinanciero, tablaIncidencias, tablaOrigenes, tablaImpuestos, tablaGlobales, tablaImpuestosPago,
			tablaTotales, tablaConciliacion;
	private JScrollPane scrollGenerales, scrollIngresos, scrollEgresos, scrollIva, scrollIeps, scrollRetenciones,
			scrollTerceros, scrollFinanciero, scrollIncidencias, scrollOrigenes, scrollImpuestos, scrollGlobales,
			scrollImpuestosPago, scrollTotales, scrollConciliacion;
	private JButton buscar, anterior, siguiente, verDetalle, origenes, evidencia, aplicarRep, exportar, columnas;
	private JLabel lblTitular, lblPeriodo, lblAnio, lblMes, lblDesde, lblHasta, lblRfc, lblMoneda, lblNaturaleza,
			lblSentido, lblSat, estado, advertencia;
	private JComboBox<Titular> titular;
	private JComboBox<String> periodo, naturaleza, sentido, sat;
	private JSpinner anio, mes;
	private JFormattedTextField desde, hasta;
	private JTextField rfc, moneda;
	private ResumenFiscalService servicio;
	private EvidenciaFinancieraService financiero;
	private SelectorColumnasTabla selectorColumnas;
	private DetalleCfdiService detalle;
	private UsuarioSesion sesion;
	private Set<String> permisos = Set.of();
	private FiltroFiscal aplicado;
	private TablaFiscal datos;
	private int pagina, generacion;
	private boolean ocupado, cargando;

	/** Construye únicamente componentes explícitos para Eclipse WindowBuilder. */
	public PanelResumenFiscal() {
		acciones = new JPanel();
		filtros = new JPanel();
		pestanas = new JTabbedPane();
		buscar = new JButton("Buscar");
		anterior = new JButton("Anterior");
		siguiente = new JButton("Siguiente");
		verDetalle = new JButton("Ver CFDI");
		origenes = new JButton("Orígenes");
		evidencia = new JButton("Evidencia / ajuste");
		aplicarRep = new JButton("Aplicar REP");
		aplicarRep.setToolTipText("Financiero: identificar un REP disponible y confirmar su aplicación sin escribir el UUID.");
		exportar = new JButton("CSV completo");
		columnas = new JButton("Columnas ▾");
		columnas.setToolTipText("Mostrar u ocultar columnas de esta pestaña. No cambia datos ni exportaciones CSV.");
		GroupLayout a = new GroupLayout(acciones);
		acciones.setLayout(a);
		a.setAutoCreateGaps(true);
		a.setHorizontalGroup(a.createParallelGroup()
				.addGroup(a.createSequentialGroup().addComponent(anterior).addComponent(siguiente).addComponent(origenes).addComponent(verDetalle))
				.addGroup(a.createSequentialGroup().addComponent(evidencia).addComponent(aplicarRep).addComponent(columnas).addComponent(exportar)));
		a.setVerticalGroup(a.createSequentialGroup()
				.addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(anterior).addComponent(siguiente).addComponent(origenes).addComponent(verDetalle))
				.addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(evidencia).addComponent(aplicarRep).addComponent(columnas).addComponent(exportar)));
		tablaGenerales = new JTable();
		tablaIngresos = new JTable();
		tablaEgresos = new JTable();
		tablaIva = new JTable();
		tablaIeps = new JTable();
		tablaRetenciones = new JTable();
		tablaTerceros = new JTable();
		tablaFinanciero = new JTable();
		tablaIncidencias = new JTable();
		tablaOrigenes = new JTable();
		tablaImpuestos = new JTable();
		tablaGlobales = new JTable();
		tablaImpuestosPago = new JTable();
		tablaTotales = new JTable();
		tablaConciliacion = new JTable();
		scrollGenerales = new JScrollPane(tablaGenerales);
		scrollIngresos = new JScrollPane(tablaIngresos);
		scrollEgresos = new JScrollPane(tablaEgresos);
		scrollIva = new JScrollPane(tablaIva);
		scrollIeps = new JScrollPane(tablaIeps);
		scrollRetenciones = new JScrollPane(tablaRetenciones);
		scrollTerceros = new JScrollPane(tablaTerceros);
		scrollFinanciero = new JScrollPane(tablaFinanciero);
		scrollIncidencias = new JScrollPane(tablaIncidencias);
		scrollOrigenes = new JScrollPane(tablaOrigenes);
		scrollImpuestos = new JScrollPane(tablaImpuestos);
		scrollGlobales = new JScrollPane(tablaGlobales);
		scrollImpuestosPago = new JScrollPane(tablaImpuestosPago);
		scrollTotales = new JScrollPane(tablaTotales);
		scrollConciliacion = new JScrollPane(tablaConciliacion);
		pestanas.addTab("Periodo", scrollGenerales);
		pestanas.addTab("Ingresos", scrollIngresos);
		pestanas.addTab("Egresos", scrollEgresos);
		pestanas.addTab("IVA por pago", scrollIva);
		pestanas.addTab("IEPS", scrollIeps);
		pestanas.addTab("ISR y retenciones", scrollRetenciones);
		pestanas.addTab("Terceros", scrollTerceros);
		pestanas.addTab("Financiero", scrollFinanciero);
		pestanas.addTab("Incidencias", scrollIncidencias);
		pestanas.addTab("Orígenes", scrollOrigenes);
		pestanas.addTab("Impuestos concepto", scrollImpuestos);
		pestanas.addTab("Impuestos globales", scrollGlobales);
		pestanas.addTab("Impuestos pago", scrollImpuestosPago);
		pestanas.addTab("Totales originales", scrollTotales);
		pestanas.addTab("Conciliación", scrollConciliacion);
		tablaIvaResumen = new JTable();
		tablaIvaManual = new JTable();
		tablaImpuestosDr = new JTable();
		scrollIvaResumen = new JScrollPane(tablaIvaResumen);
		scrollIvaManual = new JScrollPane(tablaIvaManual);
		scrollImpuestosDr = new JScrollPane(tablaImpuestosDr);
		pestanas.addTab("Carátula IVA", scrollIvaResumen);
		pestanas.addTab("Evidencia manual IVA", scrollIvaManual);
		pestanas.addTab("Impuestos DR originales", scrollImpuestosDr);
		tablaConciliacionDrP = new JTable();
		tablaConciliacionTotales = new JTable();
		scrollConciliacionDrP = new JScrollPane(tablaConciliacionDrP);
		scrollConciliacionTotales = new JScrollPane(tablaConciliacionTotales);
		pestanas.addTab("Conciliación DR / P", scrollConciliacionDrP);
		pestanas.addTab("Conciliación Totales MXN", scrollConciliacionTotales);
		tablaTributosAgrupados = new JTable();
		scrollTributosAgrupados = new JScrollPane(tablaTributosAgrupados);
		pestanas.addTab("Tributos por categoría", scrollTributosAgrupados);
		pestanas.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		lblTitular = new JLabel("Contribuyente");
		titular = new JComboBox<>();
		lblPeriodo = new JLabel("Periodo");
		periodo = new JComboBox<>(new String[] { "Mes", "Ejercicio", "Rango" });
		lblAnio = new JLabel("Año");
		anio = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 1000, 9998, 1));
		lblMes = new JLabel("Mes");
		mes = new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1));
		lblDesde = new JLabel("Desde");
		desde = new JFormattedTextField();
		lblHasta = new JLabel("Hasta");
		hasta = new JFormattedTextField();
		try {
			MaskFormatter d = new MaskFormatter("####-##-##");
			d.install(desde);
			MaskFormatter h = new MaskFormatter("####-##-##");
			h.install(hasta);
		} catch (java.text.ParseException e) {
			throw new IllegalStateException(e);
		}
		desde.setText(LocalDate.now().withDayOfMonth(1).toString());
		hasta.setText(LocalDate.now().toString());
		lblRfc = new JLabel("RFC");
		rfc = new JTextField();
		lblMoneda = new JLabel("Moneda");
		moneda = new JTextField();
		lblNaturaleza = new JLabel("Naturaleza");
		naturaleza = new JComboBox<>(new String[] { "", "COSTO", "GASTO", "GASTO_FINANCIERO", "INVERSION", "INGRESO",
				"OTRA", "SIN_CLASIFICAR" });
		lblSentido = new JLabel("Sentido");
		sentido = new JComboBox<>(new String[] { "", "EMITIDO", "RECIBIDO", "AMBOS" });
		lblSat = new JLabel("SAT");
		sat = new JComboBox<>(
				new String[] { "", "VIGENTE", "CANCELADO", "NO_ENCONTRADO", "DESCONOCIDO", "ERROR", "SIN_CONSULTA" });
		GroupLayout f = new GroupLayout(filtros);
		filtros.setLayout(f);
		f.setAutoCreateGaps(true);
		f.setHorizontalGroup(f.createParallelGroup()
				.addGroup(f.createSequentialGroup().addComponent(lblTitular)
						.addComponent(titular, 100, 250, Short.MAX_VALUE).addComponent(lblPeriodo)
						.addComponent(periodo, 70, 85, 85).addComponent(lblAnio).addComponent(anio, 60, 70, 70)
						.addComponent(lblMes).addComponent(mes, 40, 50, 50))
				.addGroup(f.createSequentialGroup().addComponent(lblDesde).addComponent(desde, 85, 100, 100)
						.addComponent(lblHasta).addComponent(hasta, 85, 100, 100).addComponent(lblRfc)
						.addComponent(rfc, 80, 120, Short.MAX_VALUE).addComponent(lblMoneda)
						.addComponent(moneda, 35, 50, 50))
				.addGroup(f.createSequentialGroup().addComponent(lblNaturaleza)
						.addComponent(naturaleza, 85, 150, Short.MAX_VALUE).addComponent(lblSentido)
						.addComponent(sentido, 80, 100, 120).addComponent(lblSat).addComponent(sat, 90, 120, 140)
						.addComponent(buscar)));
		f.setVerticalGroup(f.createSequentialGroup()
				.addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblTitular)
						.addComponent(titular).addComponent(lblPeriodo).addComponent(periodo).addComponent(lblAnio)
						.addComponent(anio).addComponent(lblMes).addComponent(mes))
				.addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblDesde)
						.addComponent(desde).addComponent(lblHasta).addComponent(hasta).addComponent(lblRfc)
						.addComponent(rfc).addComponent(lblMoneda).addComponent(moneda))
				.addGroup(f.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblNaturaleza)
						.addComponent(naturaleza).addComponent(lblSentido).addComponent(sentido).addComponent(lblSat)
						.addComponent(sat).addComponent(buscar)));
		advertencia = new JLabel(
				"Papel de trabajo preliminar. Ausencia de evidencia no significa cero confirmado. Seleccione moneda para comparar importes.");
		estado = new JLabel("Sin consulta");
		GroupLayout g = new GroupLayout(this);
		setLayout(g);
		g.setAutoCreateGaps(true);
		g.setAutoCreateContainerGaps(true);
		g.setHorizontalGroup(g.createParallelGroup().addComponent(acciones).addComponent(pestanas)
				.addComponent(advertencia, 0, 600, Short.MAX_VALUE).addComponent(filtros)
				.addComponent(estado, 0, 600, Short.MAX_VALUE));
		g.setVerticalGroup(g.createSequentialGroup()
				.addComponent(acciones, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
						GroupLayout.PREFERRED_SIZE)
				.addComponent(pestanas, 100, 280, Short.MAX_VALUE).addComponent(advertencia)
				.addComponent(filtros, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
				.addComponent(estado));
		for (JTable t : tablas()) {
			t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			t.setAutoCreateRowSorter(true);
			t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			t.getSelectionModel().addListSelectionListener(e -> controles());
			t.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e) {
					if (e.getClickCount() == 2) {
						if (documentoSeleccionado(false) > 0)
							abrirDetalle();
						else
							abrirOrigenes();
					}
				}
			});
		}
		buscar.addActionListener(e -> buscar());
		anterior.addActionListener(e -> {
			if (pagina > 0)
				consultar(aplicado, pagina - 1);
		});
		siguiente.addActionListener(e -> consultar(aplicado, pagina + 1));
		origenes.addActionListener(e -> abrirOrigenes());
		verDetalle.addActionListener(e -> abrirDetalle());
		evidencia.addActionListener(e -> abrirEvidencia());
		aplicarRep.addActionListener(e -> aplicarRepDetectado());
		exportar.addActionListener(e -> exportar());
		columnas.addActionListener(e -> mostrarColumnas());
		pestanas.addChangeListener(e -> {
			if (!cargando && aplicado != null && !ocupado)
				consultar(aplicado, 0);
			controles();
		});
		titular.addActionListener(e -> {
			if (!cargando) {
				generacion++;
				ocupado = false;
				aplicado = null;
				limpiar();
				controles();
			}
		});
		periodo.addActionListener(e -> controles());
		rfc.addActionListener(e -> buscar());
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F5"), "actualizar");
		getActionMap().put("actualizar", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				if (aplicado != null && !ocupado)
					consultar(aplicado, pagina);
			}
		});
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control F"), "buscarRFC");
		getActionMap().put("buscarRFC", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				rfc.requestFocusInWindow();
			}
		});
		controles();
	}

	/**
	 * Revalida permisos al entrar; no conserva resultados de otra sesión o titular.
	 */
	public void configurar(ResumenFiscalService servicio, EvidenciaFinancieraService financiero,
			DetalleCfdiService detalle, UsuarioSesion sesion, Set<String> permisos) {
		generacion++;
		ocupado = false;
		this.servicio = servicio;
		this.financiero = financiero;
		this.detalle = detalle;
		this.sesion = sesion;
		this.selectorColumnas = sesion == null ? null : new SelectorColumnasTabla("fiscal-" + sesion.id());
		this.permisos = Set.copyOf(permisos);
		aplicado = null;
		limpiar();
		cargando = true;
		titular.removeAllItems();
		cargando = false;
		if (autorizado())
			ejecutar(() -> servicio.titulares(sesion.id()), lista -> {
				cargando = true;
				for (Titular t : lista)
					titular.addItem(t);
				cargando = false;
				estado.setText("Seleccione periodo y pulse Buscar");
			});
		controles();
	}

	private boolean autorizado() {
		return servicio != null && sesion != null && permisos.contains(Permisos.FISCAL_CONSULTAR);
	}

	private List<JTable> tablas() {
		return List.of(tablaGenerales, tablaIngresos, tablaEgresos, tablaIva, tablaIeps, tablaRetenciones,
				tablaTerceros, tablaFinanciero, tablaIncidencias, tablaOrigenes, tablaImpuestos, tablaGlobales,
				tablaImpuestosPago, tablaTotales, tablaConciliacion, tablaIvaResumen, tablaIvaManual, tablaImpuestosDr,
				tablaConciliacionDrP, tablaConciliacionTotales, tablaTributosAgrupados);
	}

	private JTable tabla() {
		return tablas().get(pestanas.getSelectedIndex());
	}

	private Seccion seccion() {
		return Seccion.values()[pestanas.getSelectedIndex()];
	}

	private void limpiar() {
		datos = null;
		for (JTable t : tablas())
			t.setModel(new DefaultTableModel());
	}

	private void buscar() {
		if (!buscar.isEnabled())
			return;
		try {
			Titular t = (Titular) titular.getSelectedItem();
			int a = ((Number) anio.getValue()).intValue(), m = ((Number) mes.getValue()).intValue();
			LocalDate d, h;
			if (periodo.getSelectedIndex() == 0) {
				YearMonth ym = YearMonth.of(a, m);
				d = ym.atDay(1);
				h = ym.atEndOfMonth();
			} else if (periodo.getSelectedIndex() == 1) {
				d = LocalDate.of(a, 1, 1);
				h = LocalDate.of(a, 12, 31);
			} else {
				d = LocalDate.parse(desde.getText());
				h = LocalDate.parse(hasta.getText());
			}
			FiltroFiscal f = new FiltroFiscal(t.id(), d, h, (String) sentido.getSelectedItem(), rfc.getText(),
					(String) naturaleza.getSelectedItem(), 0, moneda.getText(), "", (String) sat.getSelectedItem());
			limpiar();
			consultar(f, 0);
		} catch (RuntimeException e) {
			estado.setText(e.getMessage());
		}
	}

	private void consultar(FiltroFiscal f, int p) {
		if (f == null || !autorizado())
			return;
		aplicado = f;
		Seccion seleccion = seccion();
		JTable destino = tabla();
		estado.setText("Consultando...");
		ejecutar(() -> servicio.consultar(sesion.id(), f, seleccion, p), r -> {
			datos = r;
			pagina = p;
			DefaultTableModel modelo = new DefaultTableModel(r.columnas().toArray(), 0) {
				@Override
				public boolean isCellEditable(int row, int col) {
					return false;
				}
			};
			for (List<Object> fila : r.filas())
				modelo.addRow(fila.toArray());
			destino.setModel(modelo);
			for (int c = 0; c < destino.getColumnCount(); c++)
				destino.getColumnModel().getColumn(c).setPreferredWidth(135);
			if(selectorColumnas!=null) selectorColumnas.actualizar(destino,seleccion.name());
			advertencia.setText(r.aviso());
			advertencia.setToolTipText(r.aviso());
			estado.setText("Página " + (p + 1) + " · " + r.filas().size() + " filas · " + f.desde() + " / " + f.hasta()
					+ (f.documento() > 0 ? " · comprobante de origen seleccionado" : "")
					+ (f.categoria() > 0 ? " · categoría seleccionada" : ""));
		});
	}

	private void controles() {
		boolean libre = autorizado() && !ocupado;
		buscar.setEnabled(libre && titular.getSelectedItem() != null);
		titular.setEnabled(libre);
		pestanas.setEnabled(!ocupado);
		periodo.setEnabled(libre);
		anio.setEnabled(libre && periodo.getSelectedIndex() != 2);
		mes.setEnabled(libre && periodo.getSelectedIndex() == 0);
		desde.setEnabled(libre && periodo.getSelectedIndex() == 2);
		hasta.setEnabled(libre && periodo.getSelectedIndex() == 2);
		anterior.setEnabled(libre && aplicado != null && pagina > 0);
		siguiente.setEnabled(libre && datos != null && datos.siguiente());
		origenes.setEnabled(libre && aplicado != null);
		exportar.setEnabled(libre && aplicado != null && datos != null);
		columnas.setEnabled(libre && selectorColumnas != null && aplicado != null && tabla().getModel().getColumnCount()>0);
		verDetalle.setEnabled(libre && documentoSeleccionado(false) > 0 && detalle != null
				&& permisos.containsAll(Set.of(Permisos.CFDI_CONSULTAR, Permisos.CFDI_CONSULTAR_DETALLE)));
		evidencia.setEnabled(libre && documentoSeleccionado(true) > 0 && financiero != null
				&& permisos.contains(Permisos.FISCAL_REGISTRAR_EVIDENCIA));
		aplicarRep.setEnabled(libre && seccion()==Seccion.FINANCIERO && documentoSeleccionado(true)>0
				&& financiero!=null && permisos.contains(Permisos.FISCAL_REGISTRAR_EVIDENCIA)
				&& valorSeleccionado("REP elegibles por SAT")>0);
	}

	/** Lee un contador de la fila actualmente seleccionada sin depender de su orden visual. */
	private long valorSeleccionado(String columna) {
		if (pestanas==null || pestanas.getSelectedIndex()<0) return 0;
		JTable t=tabla();int fila=t.getSelectedRow();
		if(fila<0) return 0;
		int modelo=t.convertRowIndexToModel(fila);
		for(int i=0;i<t.getModel().getColumnCount();i++)
			if(columna.equals(t.getModel().getColumnName(i))){
				Object valor=t.getModel().getValueAt(modelo,i);
				return valor instanceof Number n?n.longValue():0;
			}
		return 0;
	}

	private long documentoSeleccionado(boolean factura) {
		if (pestanas == null || pestanas.getTabCount() == 0)
			return 0;
		JTable t = tabla();
		int v = t.getSelectedRow();
		if (v < 0)
			return 0;
		int r = t.convertRowIndexToModel(v);
		for (String nombre : factura ? List.of("Factura documento", "Documento")
				: List.of("Documento", "REP documento", "Documento REP"))
			for (int c = 0; c < t.getModel().getColumnCount(); c++)
				if (t.getModel().getColumnName(c).equals(nombre)) {
					Object id = t.getModel().getValueAt(r, c);
					return id instanceof Number n ? n.longValue() : 0;
				}
		return 0;
	}

	private void abrirDetalle() {
		if (!verDetalle.isEnabled())
			return;
		var d = new Fr_DatosCfdi(owner(), detalle, sesion, aplicado.titular(), documentoSeleccionado(false));
		d.setLocationRelativeTo(this);
		d.setVisible(true);
	}

	private void abrirEvidencia() {
		if (!evidencia.isEnabled())
			return;
		var d = new Fr_DatosEvidencia();
		d.configurar(financiero, sesion.id(), aplicado.titular(), documentoSeleccionado(true));
		d.setLocationRelativeTo(this);
		d.setVisible(true);
		consultar(aplicado, pagina);
	}

	/**
	 * Detección automática y aplicación voluntaria: el usuario sólo elige un
	 * documento relacionado. No necesita capturar UUID, moneda ni importe.
	 * El servicio vuelve a validar el REP dentro de la transacción de escritura.
	 */
	private void aplicarRepDetectado(){
		if(!aplicarRep.isEnabled())return;
		long documento=documentoSeleccionado(true);
		long titularId=aplicado.titular();
		Long actor=sesion.id();
		EvidenciaFinancieraService backend=financiero;
		ejecutar(()->backend.referencias(actor,titularId,documento),referencias->{
			var disponibles=referencias.stream().filter(EvidenciaFinancieraService.ReferenciaRep::candidataAplicable).toList();
			if(disponibles.isEmpty()){
				estado.setText("Los REP detectados no están disponibles para aplicación. Revise SAT, moneda, importes o vuelva a buscar.");
				return;
			}
			EvidenciaFinancieraService.ReferenciaRep elegido=(EvidenciaFinancieraService.ReferenciaRep)
				JOptionPane.showInputDialog(this,"Seleccione el pago a aplicar a la factura. Se evitará duplicar un pago manual coincidente.",
					"Confirmar REP relacionado",JOptionPane.QUESTION_MESSAGE,null,disponibles.toArray(),disponibles.get(0));
			if(elegido==null)return;
			int confirmar=JOptionPane.showConfirmDialog(this,
				"Factura documento: "+documento+"\nREP: "+elegido.uuid()
				+"\nFecha: "+elegido.fecha()+"\nImporte: "+elegido.importeFactura()
				+" "+elegido.monedaFactura()+"\n\n¿Confirmar aplicación financiera?",
				"Aplicar complemento de pago",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
			if(confirmar!=JOptionPane.YES_OPTION)return;
			ejecutar(()->backend.confirmarRep(actor,titularId,documento,elegido.id()),
				id->{
					estado.setText("REP conciliado; movimiento "+id+". Actualizando Financiero...");
					consultar(aplicado,pagina);
				});
		});
	}

	private Frame owner() {
		var w = SwingUtilities.getWindowAncestor(this);
		return w instanceof Frame f ? f : null;
	}

	private String seleccionado(String nombre, String defecto) {
		JTable t = tabla();
		int v = t.getSelectedRow();
		if (v < 0)
			return defecto;
		int r = t.convertRowIndexToModel(v);
		for (int c = 0; c < t.getModel().getColumnCount(); c++)
			if (t.getModel().getColumnName(c).equals(nombre)) {
				Object valor = t.getModel().getValueAt(r, c);
				return valor == null ? defecto : valor.toString();
			}
		return defecto;
	}

	private void abrirOrigenes() {
		if (!origenes.isEnabled() || aplicado == null)
			return;
		Seccion actual = seccion();
		boolean pagos = actual == Seccion.IVA_RESUMEN && seleccionado("Fuente temporal", "").equals("REP_FECHA_PAGO");
		boolean iepsDr = actual == Seccion.IEPS && seleccionado("Fuente temporal", "").equals("REP_FECHA_PAGO");
		boolean finanzas = seleccionado("Origen del periodo", "").startsWith("FINANCIERO")
				|| actual == Seccion.IEPS && seleccionado("Fuente temporal", "").equals("AJUSTE_ADMINISTRATIVO");
		long doc = actual == Seccion.IVA ? documentoSeleccionado(true) : documentoSeleccionado(false);
		String tasa = seleccionado("Tasa/cuota", "");
		String impuesto = seleccionado("Código", seleccionado("Impuesto", ""));
		if (actual == Seccion.IVA_RESUMEN || actual == Seccion.IVA)
			impuesto = "002";
		if (!Set.of("001", "002", "003").contains(impuesto))
			impuesto = "";
		String categoria = seleccionado("Categoría ID", "");
		FiltroFiscal f = new FiltroFiscal(aplicado.titular(), aplicado.desde(), aplicado.hasta(),
				seleccionado("Sentido", seleccionado("Sentido factura", aplicado.sentido())),
				seleccionado("RFC", aplicado.rfc()), seleccionado("Naturaleza", aplicado.naturaleza()),
				categoria.isEmpty() ? aplicado.categoria() : Long.parseLong(categoria),
				seleccionado("Moneda", seleccionado("MonedaDR", aplicado.moneda())),
				seleccionado("Tipo", aplicado.tipo()),
				seleccionado("SAT", seleccionado("SAT factura", aplicado.estadoSat())),
				doc > 0 ? doc : aplicado.documento(), impuesto, seleccionado("Movimiento", aplicado.movimiento()),
				seleccionado("Factor", aplicado.factor()),
				tasa.isEmpty() ? aplicado.tasa() : new java.math.BigDecimal(tasa));
		cargando = true;
		pestanas.setSelectedIndex(
				(pagos ? Seccion.IVA : iepsDr ? Seccion.IMPUESTOS_DR : finanzas ? Seccion.FINANCIERO : Seccion.ORIGENES)
						.ordinal());
		cargando = false;
		consultar(f, 0);
	}

	/** Selector transversal para cualquiera de las pestañas con el mismo motor Swing. */
	private void mostrarColumnas(){
		if(!columnas.isEnabled() || selectorColumnas==null)return;
		selectorColumnas.menu(tabla(),seccion().name()).show(columnas,0,columnas.getHeight());
	}

	private void exportar() {
		if (!exportar.isEnabled())
			return;
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(
				new java.io.File("auxiliar-" + seccion().name().toLowerCase(java.util.Locale.ROOT) + ".csv"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		Path path = chooser.getSelectedFile().toPath();
		if (java.nio.file.Files.exists(path)
				&& JOptionPane.showConfirmDialog(this, "El archivo existe. ¿Desea reemplazarlo?", "Exportar CSV",
						JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			return;
		FiltroFiscal f = aplicado;
		Seccion s = seccion();
		ejecutar(() -> ExportacionFiscalService.exportar(servicio, sesion.id(), f, s, path),
				n -> estado.setText(n + " filas exportadas en CSV UTF-8. Las lecturas revalidan permisos por página."));
	}

	private <T> void ejecutar(Callable<T> tarea, Consumer<T> resultado) {
		int token = ++generacion;
		ocupado = true;
		controles();
		new SwingWorker<T, Void>() {
			@Override
			protected T doInBackground() throws Exception {
				return tarea.call();
			}

			@Override
			protected void done() {
				if (token != generacion)
					return;
				ocupado = false;
				try {
					resultado.accept(get());
				} catch (Exception e) {
					datos = null;
					estado.setText((e.getCause() == null ? e : e.getCause()).getMessage());
				}
				controles();
			}
		}.execute();
	}
}
