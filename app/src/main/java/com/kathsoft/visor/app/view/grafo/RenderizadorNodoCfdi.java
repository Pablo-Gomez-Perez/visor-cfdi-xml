package com.kathsoft.visor.app.view.grafo;

import com.kathsoft.visor.app.model.dto.grafo.NodoCfdi;
import java.awt.*;
import java.awt.geom.*;
import java.time.format.DateTimeFormatter;

/**
 * Tarjetas fiscales en coordenadas del mundo; no consulta ni modifica datos.
 */
final class RenderizadorNodoCfdi {
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

	private static Color color(String tipo) {
		return switch (tipo == null ? "" : tipo) {
		case "I" -> new Color(30, 64, 105);
		case "P" -> new Color(34, 105, 68);
		case "E" -> new Color(153, 78, 24);
		case "N" -> new Color(102, 65, 145);
		case "RETENCION" -> new Color(20, 105, 111);
		default -> new Color(80, 91, 106);
		};
	}

	private static String tipo(String t) {
		return switch (t == null ? "" : t) {
		case "I" -> "INGRESO";
		case "E" -> "EGRESO";
		case "P" -> "PAGO / REP";
		case "N" -> "NÓMINA";
		case "T" -> "TRASLADO";
		case "RETENCION" -> "RETENCIONES";
		default -> "NO DISPONIBLE";
		};
	}

	void dibujar(Graphics2D g, NodoCfdi n, Rectangle2D r, boolean focal, boolean seleccionado) {
		double x = r.getX(), y = r.getY(), w = r.getWidth(), h = r.getHeight();
		var tarjeta = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
		g.setColor(new Color(250, 252, 255));
		g.fill(tarjeta);
		g.setColor(color(n.tipo()));
		g.fill(new RoundRectangle2D.Double(x, y, w, 30, 14, 14));
		g.fill(new Rectangle2D.Double(x, y + 15, w, 15));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		g.setColor(Color.WHITE);
		texto(g, (focal ? "★ " : "") + "CFDI " + tipo(n.tipo()), x + 10, y + 20, w - 20);
		g.setColor(new Color(35, 45, 57));
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		if (n.disponible()) {
			String folio = (n.serie() == null ? "" : n.serie()) + (n.folio() == null ? "" : n.folio());
			texto(g, "Serie/folio: " + (folio.isBlank() ? "—" : folio), x + 10, y + 47, w - 20);
			texto(g, "Emisión: " + (n.emision() == null ? "—" : FECHA.format(n.emision())), x + 10, y + 65, w - 20);
			texto(g, "RFC emisor: " + valor(n.rfcEmisor()), x + 10, y + 83, w - 20);
			texto(g, "Total CFDI: " + (n.total() == null ? "—"
					: n.total().stripTrailingZeros().toPlainString() + " " + valor(n.moneda())), x + 10, y + 101,
					w - 20);
			texto(g, "Método: " + valor(n.metodoPago()), x + 10, y + 119, w - 20);
			g.setColor("CANCELADO".equals(n.estadoSat()) ? new Color(163, 38, 38) : new Color(47, 70, 79));
			texto(g, "● SAT: " + valor(n.estadoSat()), x + 10, y + 137, w - 20);
		} else {
			texto(g, n.uuid().substring(0, 18), x + 10, y + 55, w - 20);
			texto(g, n.uuid().substring(18), x + 10, y + 73, w - 20);
			texto(g, "Referencia sin documento activo", x + 10, y + 101, w - 20);
			texto(g, "disponible para este titular", x + 10, y + 119, w - 20);
		}
		g.setStroke(seleccionado || focal ? new BasicStroke(3)
				: n.disponible() ? new BasicStroke(1.3f)
						: new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[] { 6, 4 },
								0));
		g.setColor(seleccionado ? new Color(14, 111, 198) : focal ? color(n.tipo()) : new Color(124, 139, 153));
		g.draw(tarjeta);
	}

	private static String valor(String s) {
		return s == null ? "—" : s;
	}

	private static void texto(Graphics2D g, String s, double x, double y, double ancho) {
		FontMetrics fm = g.getFontMetrics();
		if (fm.stringWidth(s) > ancho) {
			while (s.length() > 1 && fm.stringWidth(s + "…") > ancho)
				s = s.substring(0, s.length() - 1);
			s += "…";
		}
		g.drawString(s, (float) x, (float) y);
	}
}
