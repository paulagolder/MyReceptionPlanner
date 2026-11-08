package org.lerot.myreceptionplanner.model;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/*
 * A pew, or any straight row of seats all facing the same way: forward, up
 * the plan when unturned, with the backrest behind them. Its seats start
 * open access; reserve those to be given to guests (the front rows at a
 * ceremony, say). Pews are numbered on their own, "Pew 1", "Pew 2" ... The
 * chairs run left to right; the seats are numbered from seat 1 towards the
 * far end of the pew.
 */
public class Pew extends Table
{
	public static final String SHAPE = "pew";
	public static final double SPACING = 50;   // cm of pew for each seat
	public static final double BACK = 8;       // the backrest's thickness
	private static final double BACK_AT = SEAT_RADIUS + 4;  // behind the seats' centres

	private int places;

	public Pew(int id, String name, int places)
	{
		super(id, name);
		setPlaces(places);
	}

	@Override
	public String shape() { return SHAPE; }

	@Override
	public String series() { return "Pew"; }

	@Override
	protected boolean openByDefault() { return true; }

	// pews stand close one behind another, so a row is those side by side
	@Override
	public double rowDepth() { return 40; }

	@Override
	protected Table sameShape(int id) { return new Pew(id, "", places); }

	@Override
	public String arrangement() { return SHAPE + " " + places; }

	@Override
	public int places() { return places; }

	public void setPlaces(int places)
	{
		if (places < 1) throw new IllegalArgumentException("a pew needs at least one seat");
		if (places == this.places) return;
		this.places = places;
		chairsChanged();
	}

	public double length() { return places * SPACING; }

	// the backrest
	@Override
	public Shape top()
	{
		return new Rectangle2D.Double(-length() / 2, BACK_AT, length(), BACK);
	}

	// left to right, facing up
	@Override
	public List<Point2D> seats()
	{
		List<Point2D> seats = new ArrayList<>();
		for (int i = 0; i < places; i++)
			seats.add(new Point2D.Double(-length() / 2 + SPACING * (i + 0.5), 0));
		return seats;
	}

	@Override
	public double reach()
	{
		return Math.hypot(length() / 2, BACK_AT + BACK);
	}

	// from seat 1 towards the far end: rightwards from a seat in the left
	// half, leftwards from one in the right half
	private boolean leftwards()
	{
		return getFirstSeat() * 2 > places - 1;
	}

	@Override
	public int seatNumber(int chair)
	{
		int step = leftwards() ? getFirstSeat() - chair : chair - getFirstSeat();
		return Math.floorMod(step, places) + 1;
	}

	@Override
	public int chairOfSeat(int n)
	{
		return Math.floorMod(leftwards() ? getFirstSeat() - (n - 1) : getFirstSeat() + n - 1, places);
	}
}
