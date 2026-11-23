package com.kathsoft.visor.app.view.importacion;

import com.kathsoft.visor.app.model.dto.UsuarioSesion;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Archivo;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Lote;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.Pagina;
import com.kathsoft.visor.app.service.importacion.HistorialImportacionesService.EstadoActual;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * RF-013: detalle modal de los archivos de un lote, incluyendo diagnósticos de
 * rechazo.
 */
public class Fr_DatosLoteImportacion extends JDialog {
	private static final long serialVersionUID = 1L;
	private HistorialImportacionesService servicio;
	private UsuarioSesion sesion;
	private Set<String> permisos = Set.of();
	private Lote lote;
	private JLabel lblLote, lblEstado;
	private JTable tabla;
	private DefaultTableModel modelo;
	private JTextArea txaDetalle;
	private JButton btnAnterior, btnSiguiente, btnArchivar, btnRestaurar, btnActualizar, btnCerrar;
	private EstadoActual estadoActual;
	private JPanel contenido;
	private JScrollPane scroll, diagnostico;
	private JLabel lblDetalle;

	private record DetalleActual(Pagina<Archivo> pagina, EstadoActual estado) {
	}

	private List<Archivo> filas = List.of();
	private int pagina;
	private boolean ocupado, siguiente;

	/** Constructor de diseño sin sesión ni conexión. */
	public Fr_DatosLoteImportacion() {
		inicializarComponentes();
	}

	/**
	 * Inyecta dependencias al abrir el diálogo; el servicio verifica cada
	 * solicitud.
	 */
	public void configurar(HistorialImportacionesService servicio, UsuarioSesion sesion, Set<String> permisos,
			Lote lote) {
		this.servicio = servicio;
		this.sesion = sesion;
		this.permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
		this.lote = lote;
		this.estadoActual = lote == null ? null : new EstadoActual(lote.estado(), lote.fin());
		lblLote.setText(lote == null ? "Sin lote" : "Lote " + lote.id() + " — " + lote.rfc() + " — " + lote.estado());
		habilitar();
	}

	/** Carga archivos de la primera página fuera del EDT. */
	public void recargar() {
		cargar(0);
	}

	private void inicializarComponentes() {
		setTitle("Detalle de lote de importación");
		setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		contenido = new JPanel();
		setContentPane(contenido);
		lblLote = new JLabel("Detalle de importación");
		lblEstado = new JLabel("Sin consulta");
		btnAnterior = new JButton("Anterior");
		btnSiguiente = new JButton("Siguiente");
		btnArchivar = new JButton("Archivar CFDI");
		btnRestaurar = new JButton("Restaurar CFDI");
		btnActualizar = new JButton("Actualizar");
		btnCerrar = new JButton("Cerrar");
		modelo = new DefaultTableModel(
				new String[] { "Ordinal", "Archivo", "UUID", "Resultado histórico", "Detalle", "Estado operativo" },
				0) {
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
		tabla.getColumnModel().getColumn(1).setPreferredWidth(240);
		tabla.getColumnModel().getColumn(2).setPreferredWidth(290);
		tabla.getColumnModel().getColumn(4).setPreferredWidth(330);
		scroll = new JScrollPane(tabla);
		txaDetalle = new JTextArea(5, 50);
		txaDetalle.setEditable(false);
		txaDetalle.setLineWrap(true);
		txaDetalle.setWrapStyleWord(true);
		diagnostico = new JScrollPane(txaDetalle);
		lblDetalle = new JLabel("Identificación y diagnóstico del archivo seleccionado");
		GroupLayout layout = new GroupLayout(contenido);
		contenido.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);
		layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(lblLote)
				.addGroup(layout.createSequentialGroup().addComponent(btnArchivar).addComponent(btnRestaurar)
						.addComponent(btnActualizar))
				.addComponent(scroll, 0, 690, Short.MAX_VALUE).addComponent(lblDetalle)
				.addComponent(diagnostico, 0, 690, Short.MAX_VALUE)
				.addGroup(layout.createSequentialGroup().addComponent(btnAnterior).addComponent(btnSiguiente)
						.addComponent(lblEstado, 0, 200, Short.MAX_VALUE).addComponent(btnCerrar)));
		layout.setVerticalGroup(layout.createSequentialGroup().addComponent(lblLote)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(btnArchivar)
						.addComponent(btnRestaurar).addComponent(btnActualizar))
				.addComponent(scroll, 140, 250, Short.MAX_VALUE).addComponent(lblDetalle)
				.addComponent(diagnostico, 80, 120, 180)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(btnAnterior)
						.addComponent(btnSiguiente).addComponent(lblEstado).addComponent(btnCerrar)));
		btnAnterior.addActionListener(e -> cargar(pagina - 1));
		btnSiguiente.addActionListener(e -> cargar(pagina + 1));
		btnActualizar.addActionListener(e -> recargar());
		btnArchivar.addActionListener(e -> cambiar(false));
		btnRestaurar.addActionListener(e -> cambiar(true));
		btnCerrar.addActionListener(e -> dispose());
		tabla.getSelectionModel().addListSelectionListener(e -> {
			mostrarDetalle();
			habilitar();
		});
		pack();
		setSize(740, 530);
		habilitar();
	}

	private boolean autorizado() {
		return lote != null && sesion != null && servicio != null && permisos.contains(Permisos.IMPORTACIONES_CONSULTAR)
				&& permisos.contains(Permisos.IMPORTACIONES_CONSULTAR_DETALLE);
	}

	private void cargar(int destino) {
		if (!autorizado() || ocupado || destino < 0)
			return;
		ocupado = true;
		habilitar();
		lblEstado.setText("Consultando...");
		final Long operador = sesion.id();
		final long loteId = lote.id();
		new SwingWorker<DetalleActual, Void>() {
			@Override
			protected DetalleActual doInBackground() {
				return new DetalleActual(servicio.archivos(operador, loteId, destino),
						servicio.estadoActual(operador, loteId));
			}

			@Override
			protected void done() {
				try {
					DetalleActual actual = get();
					Pagina<Archivo> r = actual.pagina();
					estadoActual = actual.estado();
					lblLote.setText("Lote " + loteId + " — " + lote.rfc() + " — " + estadoActual.estado());
					pagina = destino;
					siguiente = r.siguiente();
					filas = r.filas();
					modelo.setRowCount(0);
					for (Archivo a : filas)
						modelo.addRow(new Object[] { a.ordinal(), a.nombre(), a.uuid(), a.resultado(), a.detalle(),
								a.estadoOperativo() });
					txaDetalle.setText("");
					lblEstado.setText("Página " + (pagina + 1) + " — " + filas.size() + " archivos");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					limpiar();
				} catch (ExecutionException e) {
					limpiar();
					error(e.getCause());
				} finally {
					ocupado = false;
					habilitar();
				}
			}
		}.execute();
	}

	private void limpiar() {
		filas = List.of();
		estadoActual = null;
		modelo.setRowCount(0);
		txaDetalle.setText("");
		siguiente = false;
		pagina = 0;
		lblEstado.setText("Consulta no disponible");
	}

	private Archivo seleccionado() {
		int v = tabla.getSelectedRow();
		if (v < 0)
			return null;
		int i = tabla.convertRowIndexToModel(v);
		return i < filas.size() ? filas.get(i) : null;
	}

	private void mostrarDetalle() {
		Archivo a = seleccionado();
		txaDetalle.setText(a == null ? ""
				: "Archivo: " + a.nombre() + "\nRuta: " + valor(a.ruta()) + "\nUUID: " + valor(a.uuid()) + "\nSHA-256: "
						+ valor(a.sha256()) + "\nResultado histórico: " + a.resultado() + "\nEstado operativo: "
						+ a.estadoOperativo() + "\nDetalle: " + valor(a.detalle()));
		txaDetalle.setCaretPosition(0);
	}

	private static String valor(String s) {
		return s == null ? "—" : s;
	}

	private void cambiar(boolean restaurar) {
		Archivo a = seleccionado();
		String permiso = restaurar ? Permisos.IMPORTACIONES_RESTAURAR_CFDI : Permisos.IMPORTACIONES_ARCHIVAR_CFDI;
		String esperado = restaurar ? "ARCHIVADO" : "ACTIVO";
		if (!autorizado() || ocupado || a == null || !cerrado() || !esperado.equals(a.estadoOperativo())
				|| !permisos.contains(permiso))
			return;
		String accion = restaurar ? "Restaurar" : "Archivar";
		String motivo = JOptionPane.showInputDialog(this, accion + " CFDI " + a.uuid() + " para este contribuyente.\n"
				+ (restaurar ? "Se comprobará el SHA-256 del XML conservado antes de reactivarlo.\n"
						: "Se excluirá de consultas operativas de este titular, incluso si aparece en otros lotes.\n")
				+ "No se modifica el historial ni el XML. No es cancelación SAT.\nIndique el motivo (máx. 200 caracteres):");
		if (motivo == null)
			return;
		if (motivo.isBlank() || motivo.strip().length() > 200) {
			JOptionPane.showMessageDialog(this, "Motivo obligatorio de 1 a 200 caracteres.");
			return;
		}
		ocupado = true;
		habilitar();
		lblEstado.setText(accion + "...");
		final Long operador = sesion.id();
		final long loteId = lote.id();
		new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() {
				return restaurar ? servicio.restaurarArchivo(operador, loteId, a.id(), motivo)
						: servicio.archivarArchivo(operador, loteId, a.id(), motivo);
			}

			@Override
			protected void done() {
				try {
					JOptionPane.showMessageDialog(Fr_DatosLoteImportacion.this,
							get() ? "Estado operativo actualizado." : "El CFDI ya se encuentra en ese estado.");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					error(e.getCause());
				} finally {
					ocupado = false;
					habilitar();
					cargar(pagina);
				}
			}
		}.execute();
	}

	private boolean cerrado() {
		return estadoActual != null && estadoActual.fin() != null && !"EN_PROCESO".equals(estadoActual.estado());
	}

	private void habilitar() {
		boolean libre = autorizado() && !ocupado;
		btnAnterior.setEnabled(libre && pagina > 0);
		btnSiguiente.setEnabled(libre && siguiente);
		btnActualizar.setEnabled(libre);
		Archivo a = seleccionado();
		btnArchivar.setEnabled(libre && cerrado() && a != null && "ACTIVO".equals(a.estadoOperativo())
				&& permisos.contains(Permisos.IMPORTACIONES_ARCHIVAR_CFDI));
		btnRestaurar.setEnabled(libre && cerrado() && a != null && "ARCHIVADO".equals(a.estadoOperativo())
				&& permisos.contains(Permisos.IMPORTACIONES_RESTAURAR_CFDI));
		btnCerrar.setEnabled(!ocupado);
		setDefaultCloseOperation(ocupado ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
	}

	private void error(Throwable e) {
		JOptionPane.showMessageDialog(this, e == null ? "Error desconocido" : e.getMessage(), "Detalle de importación",
				JOptionPane.ERROR_MESSAGE);
	}
}
