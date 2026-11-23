package com.kathsoft.visor.app.view.grafo;

import com.kathsoft.visor.app.model.dto.grafo.AristaFiscal;
import java.awt.*;
import java.awt.geom.*;

/**
 * Dibuja flechas hacia el documento referenciado y etiquetas de la fuente real.
 */
final class RenderizadorAristaFiscal {
	void dibujar(Graphics2D g, AristaFiscal a, Rectangle2D origen, Rectangle2D destino, double separacion,
			boolean resaltada) {
		Color color = resaltada ? new Color(20, 98, 170) : new Color(92, 109, 128);
		g.setColor(color);
		g.setStroke(new BasicStroke(resaltada ? 2.7f : 1.5f));
		double x = origen.getCenterX(), y = origen.getCenterY(), dx = destino.getCenterX(), dy = destino.getCenterY();
		Point2D.Double inicio, fin, control;
		if (a.origen().equals(a.destino())) {
			inicio = new Point2D.Double(origen.getMaxX(), y - 25);
			fin = new Point2D.Double(origen.getMaxX(), y + 25);
			control = new Point2D.Double(origen.getMaxX() + 100 + Math.abs(separacion), y);
		} else {
			double distancia = Math.hypot(dx - x, dy - y);
			if (distancia < 1)
				return;
			control = new Point2D.Double((x + dx) / 2 - (dy - y) / distancia * separacion,
					(y + dy) / 2 + (dx - x) / distancia * separacion);
			inicio = borde(origen, control.x, control.y);
			fin = borde(destino, control.x, control.y);
		}
		Path2D.Double curva = new Path2D.Double();
		curva.moveTo(inicio.x, inicio.y);
		curva.quadTo(control.x, control.y, fin.x, fin.y);
		g.draw(curva);
		double ang = Math.atan2(fin.y - control.y, fin.x - control.x);
		Path2D.Double flecha = new Path2D.Double();
		flecha.moveTo(fin.x, fin.y);
		flecha.lineTo(fin.x - 12 * Math.cos(ang - .45), fin.y - 12 * Math.sin(ang - .45));
		flecha.lineTo(fin.x - 12 * Math.cos(ang + .45), fin.y - 12 * Math.sin(ang + .45));
		flecha.closePath();
		g.fill(flecha);
		double lx = (inicio.x + 2 * control.x + fin.x) / 4, ly = (inicio.y + 2 * control.y + fin.y) / 4;
		String etiqueta = a.etiqueta();
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		int w = g.getFontMetrics().stringWidth(etiqueta);
		g.setColor(new Color(250, 252, 255));
		g.fill(new RoundRectangle2D.Double(lx - w / 2.0 - 4, ly - 13, w + 8, 17, 6, 6));
		g.setColor(color);
		g.drawString(etiqueta, (float) (lx - w / 2.0), (float) ly);
	}

	private static Point2D.Double borde(Rectangle2D r, double x, double y) {
		double dx = x - r.getCenterX(), dy = y - r.getCenterY();
		double k = 1 / Math.max(Math.abs(dx) / (r.getWidth() / 2), Math.abs(dy) / (r.getHeight() / 2));
		if (!Double.isFinite(k))
			return new Point2D.Double(r.getMaxX(), r.getCenterY());
		return new Point2D.Double(r.getCenterX() + dx * k, r.getCenterY() + dy * k);
	}
}
