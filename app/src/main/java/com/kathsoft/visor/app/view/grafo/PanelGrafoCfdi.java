package com.kathsoft.visor.app.view.grafo;

import com.kathsoft.visor.app.model.dto.grafo.*;
import com.kathsoft.visor.app.service.grafo.DistribucionGrafoService;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/** Canvas Java2D independiente; JavaBean Swing sin I/O, apto para diseño. */
public class PanelGrafoCfdi extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final double MIN_ZOOM = .025, MAX_ZOOM = 3;
	private final RenderizadorGrafo renderizador = new RenderizadorGrafo();
	private GrafoFiscal grafo;
	private Map<String, Rectangle2D.Double> posiciones = new LinkedHashMap<>();
	private String seleccionado, arrastrado;
	private Point anterior;
	private double escala = 1, tx, ty;
	private Consumer<NodoCfdi> alSeleccionar = n -> {
	}, alAbrir = n -> {
	};

	/** Constructor de diseño: no inicializa Hibernate ni consulta archivos. */
	public PanelGrafoCfdi() {
		setBackground(new Color(237, 242, 247));
		setPreferredSize(new Dimension(500, 320));
		ToolTipManager.sharedInstance().registerComponent(this);
		MouseAdapter raton = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e) && !SwingUtilities.isMiddleMouseButton(e))
					return;
				anterior = e.getPoint();
				arrastrado = SwingUtilities.isLeftMouseButton(e) ? nodoEn(anterior) : null;
				seleccionado = arrastrado;
				alSeleccionar.accept(nodo(seleccionado));
				repaint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (anterior == null)
					return;
				double dx = e.getX() - anterior.x, dy = e.getY() - anterior.y;
				if (arrastrado != null) {
					var r = posiciones.get(arrastrado);
					r.x += dx / escala;
					r.y += dy / escala;
				} else {
					tx += dx;
					ty += dy;
				}
				anterior = e.getPoint();
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				anterior = null;
				arrastrado = null;
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					NodoCfdi n = nodo(nodoEn(e.getPoint()));
					if (n != null && n.disponible())
						alAbrir.accept(n);
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (grafo == null)
					return;
				double nueva = Math.max(MIN_ZOOM,
						Math.min(MAX_ZOOM, escala * Math.pow(1.15, -e.getPreciseWheelRotation())));
				double factor = nueva / escala;
				tx = e.getX() - (e.getX() - tx) * factor;
				ty = e.getY() - (e.getY() - ty) * factor;
				escala = nueva;
				repaint();
			}
		};
		addMouseListener(raton);
		addMouseMotionListener(raton);
		addMouseWheelListener(raton);
	}

	/** Callbacks exclusivamente de presentación. */
	public void configurar(Consumer<NodoCfdi> seleccionar, Consumer<NodoCfdi> abrir) {
		alSeleccionar = Objects.requireNonNull(seleccionar);
		alAbrir = Objects.requireNonNull(abrir);
	}

	/** Instala instantánea con layout calculado fuera del EDT. */
	public void mostrar(GrafoFiscal datos, Map<String, Rectangle2D.Double> layout) {
		grafo = datos;
		posiciones = new LinkedHashMap<>();
		layout.forEach((u, r) -> posiciones.put(u, (Rectangle2D.Double) r.clone()));
		seleccionado = datos == null ? null : datos.uuidFocal();
		arrastrado = null;
		anterior = null;
		escala = .85;
		centrar();
		alSeleccionar.accept(nodo(seleccionado));
		repaint();
	}

	/** Centra el documento focal conservando el zoom. */
	public void centrar() {
		if (grafo == null)
			return;
		var r = posiciones.get(grafo.uuidFocal());
		tx = getWidth() / 2.0 - r.getCenterX() * escala;
		ty = getHeight() / 2.0 - r.getCenterY() * escala;
		repaint();
	}

	/** Ajusta el conjunto de tarjetas al área visible. */
	public void ajustar() {
		if (posiciones.isEmpty() || getWidth() <= 32 || getHeight() <= 32)
			return;
		Rectangle2D bounds = null;
		for (var r : posiciones.values())
			bounds = bounds == null ? (Rectangle2D) r.clone() : bounds.createUnion(r);
		escala = Math.max(MIN_ZOOM, Math.min(1, Math.min((getWidth() - 48) / Math.max(1, bounds.getWidth()),
				(getHeight() - 48) / Math.max(1, bounds.getHeight()))));
		tx = getWidth() / 2.0 - bounds.getCenterX() * escala;
		ty = getHeight() / 2.0 - bounds.getCenterY() * escala;
		repaint();
	}

	private String nodoEn(Point p) {
		double x = (p.x - tx) / escala, y = (p.y - ty) / escala;
		String ultimo = null;
		for (var e : posiciones.entrySet())
			if (e.getValue().contains(x, y))
				ultimo = e.getKey();
		return ultimo;
	}

	private NodoCfdi nodo(String uuid) {
		if (grafo == null || uuid == null)
			return null;
		return grafo.nodos().stream().filter(n -> n.uuid().equals(uuid)).findFirst().orElse(null);
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		NodoCfdi n = nodo(nodoEn(e.getPoint()));
		return n == null ? null
				: n.uuid() + " — " + (n.disponible() ? "Doble clic: detalle" : "Documento no disponible");
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (grafo == null) {
				g2.setColor(new Color(74, 88, 106));
				g2.drawString("Seleccione Documentos relacionados para cargar el grafo.", 18, 28);
				return;
			}
			g2.translate(tx, ty);
			g2.scale(escala, escala);
			renderizador.dibujar(g2, grafo, posiciones, seleccionado);
		} finally {
			g2.dispose();
		}
	}
}
