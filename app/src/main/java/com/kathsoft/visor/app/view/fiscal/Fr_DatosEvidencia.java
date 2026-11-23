package com.kathsoft.visor.app.view.fiscal;

import com.kathsoft.visor.app.service.fiscal.EvidenciaFinancieraService;
import com.kathsoft.visor.app.service.fiscal.EvidenciaFinancieraService.AplicacionRegistrada;
import com.kathsoft.visor.app.service.fiscal.EvidenciaFinancieraService.ReferenciaRep;
import com.kathsoft.visor.app.service.fiscal.EvidenciaFinancieraService.NotaRelacionada;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.*;
import javax.swing.text.MaskFormatter;

/**
 * Formulario de una factura: registra evidencia o ajuste sobre el modelo
 * financiero existente.
 */
public class Fr_DatosEvidencia extends JDialog {
	private static final long serialVersionUID = 1L;
	private JPanel contenido, panelPago, panelAjuste;
	private JTabbedPane pestanas;
	private JComboBox<ReferenciaRep> rep;
	private JCheckBox vincularRegistrado;
	private JComboBox<AplicacionRegistrada> registrados;
	private JComboBox<String> tipoAjuste;
	private JComboBox<NotaRelacionada> notasRelacionadas;
	private JLabel lblNotasRelacionadas;
	private JFormattedTextField fechaPago, fechaAjuste;
	private JTextField moneda, importePago, importeFactura, referencia, motivoPago, soporte, importeAjuste,
			motivoAjuste;
	private JLabel lblFechaPago, lblMoneda, lblImportePago, lblImporteFactura, lblReferencia, lblMotivoPago, lblRep;
	private JLabel lblRegistrado;
	private JLabel lblFechaAjuste, lblTipoAjuste, lblSoporte, lblImporteAjuste, lblMotivoAjuste, estado;
	private JButton guardar, cerrar;
	private EvidenciaFinancieraService servicio;
	private Long actor;
	private long titular, documento;
	private boolean ocupado;
	private int generacion;

	private record Opciones(List<ReferenciaRep> referencias, List<AplicacionRegistrada> aplicaciones, List<NotaRelacionada> notas) {
	}

	/** Constructor visual público sin persistencia, archivos ni red. */
	public Fr_DatosEvidencia() {
		setTitle("Evidencia de cobro, pago o ajuste");
		setModal(true);
		setSize(650, 470);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		contenido = new JPanel();
		setContentPane(contenido);
		panelPago = new JPanel();
		panelAjuste = new JPanel();
		pestanas = new JTabbedPane();
		lblRep = new JLabel("REP disponible");
		rep = new JComboBox<>();
		rep.setToolTipText("Sin REP: evidencia administrativa. Con REP: se vincula sin duplicarlo.");
		vincularRegistrado = new JCheckBox("Vincular pago ya registrado");
		vincularRegistrado.setToolTipText(
				"Concilia un pago manual existente con un REP posterior; conserva movimiento y aplicación.");
		lblRegistrado = new JLabel("Pago / cobro registrado");
		registrados = new JComboBox<>();
		registrados
				.setToolTipText("Aplicaciones manuales de esta factura aún sin REP. Se muestran hasta 200 opciones.");
		lblFechaPago = new JLabel("Fecha y hora del pago");
		fechaPago = new JFormattedTextField();
		lblMoneda = new JLabel("Moneda del pago");
		moneda = new JTextField("MXN");
		lblImportePago = new JLabel("Importe en moneda del pago");
		importePago = new JTextField();
		lblImporteFactura = new JLabel("Importe aplicado en moneda de factura");
		importeFactura = new JTextField();
		lblReferencia = new JLabel("Referencia de evidencia");
		referencia = new JTextField();
		lblMotivoPago = new JLabel("Justificación");
		motivoPago = new JTextField();
		lblNotasRelacionadas = new JLabel("Nota de crédito detectada");
		notasRelacionadas = new JComboBox<>();
		notasRelacionadas.setToolTipText("CFDI E relacionados (TipoRelacion 01): seleccione para completar UUID e importe disponible.");
		lblFechaAjuste = new JLabel("Fecha del ajuste");
		fechaAjuste = new JFormattedTextField();
		lblTipoAjuste = new JLabel("Tipo");
		tipoAjuste = new JComboBox<>(new String[] { "NOTA_CREDITO", "DESCUENTO", "DEVOLUCION", "ANTICIPO", "OTRO" });
		lblSoporte = new JLabel("UUID del comprobante soporte");
		soporte = new JTextField();
		lblImporteAjuste = new JLabel("Importe en moneda de factura");
		importeAjuste = new JTextField();
		lblMotivoAjuste = new JLabel("Justificación");
		motivoAjuste = new JTextField();
		try {
			MaskFormatter pago = new MaskFormatter("####-##-## ##:##:##");
			pago.setPlaceholderCharacter('_');
			pago.install(fechaPago);
			MaskFormatter ajuste = new MaskFormatter("####-##-##");
			ajuste.setPlaceholderCharacter('_');
			ajuste.install(fechaAjuste);
		} catch (java.text.ParseException e) {
			throw new IllegalStateException(e);
		}
		fechaPago.setText(LocalDate.now() + " 12:00:00");
		fechaAjuste.setText(LocalDate.now().toString());
		GroupLayout p = new GroupLayout(panelPago);
		panelPago.setLayout(p);
		p.setAutoCreateGaps(true);
		p.setAutoCreateContainerGaps(true);
		p.setHorizontalGroup(p.createParallelGroup().addComponent(vincularRegistrado).addGroup(p.createSequentialGroup()
				.addGroup(p.createParallelGroup().addComponent(lblRegistrado).addComponent(lblRep)
						.addComponent(lblFechaPago).addComponent(lblMoneda).addComponent(lblImportePago)
						.addComponent(lblImporteFactura).addComponent(lblReferencia).addComponent(lblMotivoPago))
				.addGroup(p.createParallelGroup().addComponent(registrados, 0, 260, Short.MAX_VALUE)
						.addComponent(rep, 0, 260, Short.MAX_VALUE).addComponent(fechaPago).addComponent(moneda)
						.addComponent(importePago).addComponent(importeFactura).addComponent(referencia)
						.addComponent(motivoPago))));
		p.setVerticalGroup(p.createSequentialGroup().addComponent(vincularRegistrado)
				.addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblRegistrado)
						.addComponent(registrados))
				.addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblRep).addComponent(rep))
				.addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblFechaPago)
						.addComponent(fechaPago))
				.addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblMoneda)
						.addComponent(moneda))
				.addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblImportePago)
						.addComponent(importePago))
				.addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblImporteFactura)
						.addComponent(importeFactura))
				.addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblReferencia)
						.addComponent(referencia))
				.addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblMotivoPago)
						.addComponent(motivoPago)));
		GroupLayout a = new GroupLayout(panelAjuste);
		panelAjuste.setLayout(a);
		a.setAutoCreateGaps(true);
		a.setAutoCreateContainerGaps(true);
		a.setHorizontalGroup(a.createSequentialGroup()
				.addGroup(a.createParallelGroup().addComponent(lblNotasRelacionadas).addComponent(lblFechaAjuste).addComponent(lblTipoAjuste)
						.addComponent(lblSoporte).addComponent(lblImporteAjuste).addComponent(lblMotivoAjuste))
				.addGroup(a.createParallelGroup().addComponent(notasRelacionadas).addComponent(fechaAjuste).addComponent(tipoAjuste)
						.addComponent(soporte).addComponent(importeAjuste).addComponent(motivoAjuste)));
		a.setVerticalGroup(a.createSequentialGroup()
				.addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblNotasRelacionadas)
						.addComponent(notasRelacionadas))
				.addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblFechaAjuste)
						.addComponent(fechaAjuste))
				.addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblTipoAjuste)
						.addComponent(tipoAjuste))
				.addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblSoporte)
						.addComponent(soporte))
				.addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblImporteAjuste)
						.addComponent(importeAjuste))
				.addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblMotivoAjuste)
						.addComponent(motivoAjuste)));
		pestanas.addTab("Pago / cobro", panelPago);
		pestanas.addTab("Ajuste de saldo", panelAjuste);
		estado = new JLabel(
				"Evidencia administrativa no garantiza acreditamiento. Una nota de crédito no prueba restitución de dinero.");
		guardar = new JButton("Guardar");
		guardar.setEnabled(false);
		cerrar = new JButton("Cerrar");
		GroupLayout g = new GroupLayout(contenido);
		contenido.setLayout(g);
		g.setAutoCreateGaps(true);
		g.setAutoCreateContainerGaps(true);
		g.setHorizontalGroup(g.createParallelGroup().addComponent(pestanas).addComponent(estado)
				.addGroup(g.createSequentialGroup().addComponent(guardar).addComponent(cerrar)));
		g.setVerticalGroup(g.createSequentialGroup().addComponent(pestanas).addComponent(estado).addGroup(
				g.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(guardar).addComponent(cerrar)));
		rep.addActionListener(e -> {
			if (!vincularRegistrado.isSelected())
				presentarRep();
			controles();
		});
		registrados.addActionListener(e -> {
			if (vincularRegistrado.isSelected())
				presentarRegistrado();
			controles();
		});
		vincularRegistrado.addActionListener(e -> {
			if (vincularRegistrado.isSelected())
				presentarRegistrado();
			else
				presentarRep();
			controles();
		});
		notasRelacionadas.addActionListener(e -> {
			NotaRelacionada nota = (NotaRelacionada) notasRelacionadas.getSelectedItem();
			if(nota!=null){
				tipoAjuste.setSelectedItem("NOTA_CREDITO");
				soporte.setText(nota.uuid());
				importeAjuste.setText(nota.disponible().signum()>0?nota.disponible().toPlainString():"");
				fechaAjuste.setText(nota.emision().toString());
				estado.setText("Nota relacionada detectada. Confirme el importe aplicable y la fecha real del ajuste.");
			}
			controles();
		});
		pestanas.addChangeListener(e -> controles());
		guardar.addActionListener(e -> guardar());
		cerrar.addActionListener(e -> dispose());
		controles();
	}

	/** Inyecta factura y obtiene opciones REP fuera del EDT. */
	public void configurar(EvidenciaFinancieraService servicio, Long actor, long titular, long documento) {
		this.servicio = servicio;
		this.actor = actor;
		this.titular = titular;
		this.documento = documento;
		ocupado = true;
		int token = ++generacion;
		rep.removeAllItems();
		registrados.removeAllItems();
		notasRelacionadas.removeAllItems();
		vincularRegistrado.setSelected(false);
		controles();
		new SwingWorker<Opciones, Void>() {
			@Override
			protected Opciones doInBackground() {
				return new Opciones(servicio.referencias(actor, titular, documento),
						servicio.aplicacionesNoVinculadas(actor, titular, documento),
						servicio.notasRelacionadas(actor, titular, documento));
			}

			@Override
			protected void done() {
				if (token != generacion)
					return;
				try {
					Opciones opciones = get();
					rep.addItem(null);
					for (ReferenciaRep r : opciones.referencias())
						rep.addItem(r);
					for (AplicacionRegistrada a : opciones.aplicaciones())
						registrados.addItem(a);
					notasRelacionadas.addItem(null);
					for (NotaRelacionada n : opciones.notas()) notasRelacionadas.addItem(n);
					ocupado = false;
					controles();
				} catch (Exception e) {
					estado.setText(causa(e));
					ocupado = false;
					guardar.setEnabled(false);
				}
			}
		}.execute();
	}

	private void presentarRep() {
		ReferenciaRep r = (ReferenciaRep) rep.getSelectedItem();
		if (r != null) {
			fechaPago.setText(r.fecha().toString().replace('T', ' '));
			moneda.setText(r.monedaPago());
			importePago.setText(r.importePago() == null ? "" : r.importePago().toPlainString());
			importeFactura.setText(r.importeFactura() == null ? "" : r.importeFactura().toPlainString());
			referencia.setText(r.uuid());
		}
	}

	private void presentarRegistrado() {
		AplicacionRegistrada a = (AplicacionRegistrada) registrados.getSelectedItem();
		if (a != null) {
			fechaPago.setText(a.fecha().toString().replace('T', ' '));
			moneda.setText(a.moneda());
			importePago.setText(a.importePago().toPlainString());
			importeFactura.setText(a.importeAplicado().toPlainString());
			referencia.setText(a.referencia() == null ? "" : a.referencia());
		}
	}

	private void controles() {
		boolean libre = servicio != null && !ocupado;
		boolean vincular = vincularRegistrado.isSelected();
		vincularRegistrado.setEnabled(libre && registrados.getItemCount() > 0);
		registrados.setEnabled(libre && vincular);
		rep.setEnabled(libre);
		notasRelacionadas.setEnabled(libre && pestanas.getSelectedIndex()==1);
		fechaPago.setEnabled(libre && !vincular);
		moneda.setEnabled(libre && !vincular);
		importePago.setEnabled(libre && !vincular);
		importeFactura.setEnabled(libre && !vincular);
		referencia.setEnabled(libre && !vincular);
		motivoPago.setEnabled(libre);
		pestanas.setEnabled(!ocupado);
		guardar.setEnabled(libre && (pestanas.getSelectedIndex() != 0 || !vincular
				|| (registrados.getSelectedItem() != null && rep.getSelectedItem() != null)));
	}

	private void guardar() {
		if (ocupado || servicio == null)
			return;
		try {
			boolean pago = pestanas.getSelectedIndex() == 0;
			boolean vincular = pago && vincularRegistrado.isSelected();
			ReferenciaRep r = (ReferenciaRep) rep.getSelectedItem();
			AplicacionRegistrada registrada = (AplicacionRegistrada) registrados.getSelectedItem();
			if (vincular && (r == null || registrada == null))
				throw new IllegalArgumentException("Seleccione un pago registrado y un DR disponible");
			EvidenciaFinancieraService.Pago solicitud = pago && !vincular
					? new EvidenciaFinancieraService.Pago(LocalDateTime.parse(fechaPago.getText().replace(' ', 'T')),
							moneda.getText().strip().toUpperCase(java.util.Locale.ROOT),
							new BigDecimal(importePago.getText().strip()),
							new BigDecimal(importeFactura.getText().strip()), referencia.getText(),
							motivoPago.getText(), r == null ? null : r.id())
					: null;
			LocalDate fecha = pago ? null : LocalDate.parse(fechaAjuste.getText());
			BigDecimal importe = pago ? null : new BigDecimal(importeAjuste.getText().strip());
			String uuid = soporte.getText().strip(), tipo = (String) tipoAjuste.getSelectedItem(),
					motivo = motivoAjuste.getText();
			String motivoVinculo = motivoPago.getText();
			EvidenciaFinancieraService backend = servicio;
			Long usuario = actor;
			long contribuyente = titular, factura = documento;
			int token = ++generacion;
			ocupado = true;
			controles();
			new SwingWorker<Long, Void>() {
				@Override
				protected Long doInBackground() {
					if (vincular)
						return backend.vincular(usuario, contribuyente, factura, registrada.id(), r.id(),
								motivoVinculo);
					return pago ? backend.registrar(usuario, contribuyente, factura, solicitud)
							: backend.ajustarPorUuid(usuario, contribuyente, factura, uuid, fecha, tipo, importe,
									motivo);
				}

				@Override
				protected void done() {
					if (token != generacion)
						return;
					ocupado = false;
					try {
						get();
						dispose();
					} catch (Exception e) {
						estado.setText(causa(e));
					}
					controles();
				}
			}.execute();
		} catch (RuntimeException e) {
			estado.setText(e.getMessage());
		}
	}

	private static String causa(Exception e) {
		return (e.getCause() == null ? e : e.getCause()).getMessage();
	}
}
