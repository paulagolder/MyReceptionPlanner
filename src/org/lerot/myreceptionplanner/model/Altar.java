package org.lerot.myreceptionplanner.model;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/*
 * An altar or speakers' table, there to show which way the room faces: as
 * long as so many seats along its front, but with no chairs. It is not
 * numbered and plays no part in seating; it is called "Altar" unless given
 * a name of its own.
 */
public class Altar extends Table
{
	public static final String SHAPE = "altar";
	public static final double DEPTH = 80;

	private int length;  // in seats along the front

	public Altar(int id, String name, int length)
	{
		super(id, name);
		setLength(length);
	}

	public int getLength() { return length; }

	public void setLength(int length)
	{
		if (length < 1) throw new IllegalArgumentException("an altar is at least one seat long");
		this.length = length;
	}

	public double width() { return length * SEAT_SPACING; }

	@Override
	public String shape() { return SHAPE; }

	@Override
	public String series() { return "Altar"; }

	@Override
	public boolean isNumbered() { return false; }

	@Override
	protected Table sameShape(int id) { return new Altar(id, "", length); }

	@Override
	public String arrangement() { return SHAPE + " " + length; }

	@Override
	public int places() { return 0; }

	@Override
	public Shape top() { return new Rectangle2D.Double(-width() / 2, -DEPTH / 2, width(), DEPTH); }

	@Override
	public List<Point2D> seats() { return List.of(); }

	@Override
	public double reach() { return Math.hypot(width() / 2, DEPTH / 2); }
}
