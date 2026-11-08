package org.lerot.myreceptionplanner.model;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/*
 * A rectangular table with sidePlaces along each long side and endPlaces at
 * each end, so 2 x sidePlaces + 2 x endPlaces in all. Unturned, the long
 * sides run left to right.
 */
public class RectTable extends Table
{
	public static final String SHAPE = "rectangle";
	private static final double MIN_WIDTH = 80;

	private int sidePlaces;
	private int endPlaces;

	public RectTable(int id, String name, int sidePlaces, int endPlaces)
	{
		super(id, name);
		setPlaces(sidePlaces, endPlaces);
	}

	@Override
	public String shape() { return SHAPE; }

	@Override
	public int places() { return 2 * sidePlaces + 2 * endPlaces; }

	@Override
	protected Table sameShape(int id) { return new RectTable(id, "", sidePlaces, endPlaces); }

	@Override
	public String arrangement() { return SHAPE + " " + sidePlaces + "x" + endPlaces; }

	public int getSidePlaces() { return sidePlaces; }

	public int getEndPlaces() { return endPlaces; }

	public void setPlaces(int sidePlaces, int endPlaces)
	{
		if (sidePlaces < 1 || endPlaces < 0)
			throw new IllegalArgumentException("a rectangular table needs a place on each side");
		if (sidePlaces == this.sidePlaces && endPlaces == this.endPlaces) return;
		this.sidePlaces = sidePlaces;
		this.endPlaces = endPlaces;
		chairsChanged();
	}

	public double length() { return sidePlaces * SEAT_SPACING; }

	public double width() { return Math.max(MIN_WIDTH, endPlaces * SEAT_SPACING); }

	@Override
	public Shape top()
	{
		return new Rectangle2D.Double(-length() / 2, -width() / 2, length(), width());
	}

	// clockwise from the top left: along the top, down the right end,
	// back along the bottom and up the left end
	@Override
	public List<Point2D> seats()
	{
		double halfL = length() / 2;
		double halfW = width() / 2;
		double off = SEAT_GAP + SEAT_RADIUS;
		List<Point2D> seats = new ArrayList<>();
		for (int i = 0; i < sidePlaces; i++)
			seats.add(new Point2D.Double(-halfL + SEAT_SPACING * (i + 0.5), -halfW - off));
		for (int i = 0; i < endPlaces; i++)
			seats.add(new Point2D.Double(halfL + off, along(i, endPlaces, halfW)));
		for (int i = sidePlaces - 1; i >= 0; i--)
			seats.add(new Point2D.Double(-halfL + SEAT_SPACING * (i + 0.5), halfW + off));
		for (int i = endPlaces - 1; i >= 0; i--)
			seats.add(new Point2D.Double(-halfL - off, along(i, endPlaces, halfW)));
		return seats;
	}

	// the head table faces the room: the long side across from seat 1 is
	// disabled, so everyone sits on seat 1's side; chairs at the ends stay.
	// Nothing changes if seat 1 is at an end.
	@Override
	protected void seatAsHeadTable()
	{
		int first = getFirstSeat();
		int bottom = sidePlaces + endPlaces;    // the near side's first chair
		int across;
		if (first < sidePlaces) across = bottom;
		else if (first >= bottom && first < bottom + sidePlaces) across = 0;
		else return;
		for (int i = 0; i < sidePlaces; i++)
			setDisabled(across + i, true);
	}

	// the i'th of n chairs spread evenly along an end
	private static double along(int i, int n, double halfW)
	{
		return -halfW + 2 * halfW * (i + 0.5) / n;
	}

	@Override
	public double reach()
	{
		double off = SEAT_GAP + 2 * SEAT_RADIUS;
		return Math.hypot(length() / 2 + off, width() / 2 + off);
	}
}
