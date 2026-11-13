package org.lerot.myreceptionplanner.model.tables;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

// A round table; its size follows from the number of places
public class RoundTable extends Table
{
	public static final String SHAPE = "round";
	private static final double MIN_DIAMETER = 80;

	private int places;

	public RoundTable(int id, String name, int places)
	{
		super(id, name);
		setPlaces(places);
	}

	@Override
	public String shape() { return SHAPE; }

	@Override
	public int places() { return places; }

	@Override
	protected Table sameShape(int id) { return new RoundTable(id, "", places); }

	@Override
	public String arrangement() { return SHAPE + " " + places; }

	public void setPlaces(int places)
	{
		if (places < 1) throw new IllegalArgumentException("a table needs at least one place");
		if (places == this.places) return;
		this.places = places;
		chairsChanged();
	}

	public double diameter()
	{
		return Math.max(MIN_DIAMETER, places * SEAT_SPACING / Math.PI);
	}

	@Override
	public Shape top()
	{
		double d = diameter();
		return new Ellipse2D.Double(-d / 2, -d / 2, d, d);
	}

	// spread evenly round the table, starting at the top
	@Override
	public List<Point2D> seats()
	{
		double r = diameter() / 2 + SEAT_GAP + SEAT_RADIUS;
		List<Point2D> seats = new ArrayList<>();
		for (int i = 0; i < places; i++)
		{
			double angle = 2 * Math.PI * i / places - Math.PI / 2;
			seats.add(new Point2D.Double(r * Math.cos(angle), r * Math.sin(angle)));
		}
		return seats;
	}

	@Override
	public double reach()
	{
		return diameter() / 2 + SEAT_GAP + 2 * SEAT_RADIUS;
	}
}
