package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.model.Gender;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/*
 * The mark on a chair someone is seated in: a circle for a woman, a square
 * for a man and a diamond when not known, filled with their party's colour.
 */
public final class Marks
{
	private Marks() {}

	// centred on cx, cy, about r from the centre to the edge
	public static void paint(Graphics2D g, Gender gender, Color colour, double cx, double cy, double r)
	{
		Shape mark = switch (gender)
		{
			case FEMALE -> new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
			case MALE ->
			{
				double h = r * 0.88;  // a square looks bigger than a circle of the same width
				yield new Rectangle2D.Double(cx - h, cy - h, 2 * h, 2 * h);
			}
			case UNKNOWN ->
			{
				double d = r * 1.2;
				Path2D p = new Path2D.Double();
				p.moveTo(cx, cy - d);
				p.lineTo(cx + d, cy);
				p.lineTo(cx, cy + d);
				p.lineTo(cx - d, cy);
				p.closePath();
				yield p;
			}
		};
		g.setColor(colour);
		g.fill(mark);
		g.setColor(colour.darker());
		g.setStroke(new BasicStroke(1f));
		g.draw(mark);
	}

	public static Icon icon(Gender gender, Color colour, int size)
	{
		return new Icon()
		{
			public void paintIcon(Component c, Graphics g0, int x, int y)
			{
				Graphics2D g = (Graphics2D) g0.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				paint(g, gender, colour, x + size / 2.0, y + size / 2.0, size / 2.0 - 2);
				g.dispose();
			}

			public int getIconWidth() { return size; }

			public int getIconHeight() { return size; }
		};
	}

	// a party's colour: a short bar, so it is not taken for a man's square
	public static Icon swatch(Color colour, int size)
	{
		return new Icon()
		{
			public void paintIcon(Component c, Graphics g0, int x, int y)
			{
				Graphics2D g = (Graphics2D) g0.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				double h = size * 0.5;
				Shape s = new RoundRectangle2D.Double(x + 1, y + (size - h) / 2, size - 2, h, h, h);
				g.setColor(colour);
				g.fill(s);
				g.setColor(colour.darker());
				g.draw(s);
				g.dispose();
			}

			public int getIconWidth() { return size; }

			public int getIconHeight() { return size; }
		};
	}
}
