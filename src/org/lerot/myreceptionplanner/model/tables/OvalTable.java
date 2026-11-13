package org.lerot.myreceptionplanner.model.tables;

/*
 * An oval table, given by the box round it in seat units: length along,
 * width across, the width no more than the length. It is four arcs, as
 * joiners make an oval: two flat ones along the sides and two tight ones at
 * the ends, each with as many seats as its length allows (see Walk).
 * Seat 1 is where the left end meets the far side.
 * As wide as it is long, it is round.
 */
public class OvalTable extends WalkTable
{
	public static final String SHAPE = "oval";

	private int length;
	private int width;

	public OvalTable(int id, String name, int length, int width)
	{
		super(id, name, walk(length, width));
		this.length = length;
		this.width = width;
	}

	public void setBox(int length, int width)
	{
		setWalk(walk(length, width));
		this.length = length;
		this.width = width;
	}

	@Override
	public String shape() { return SHAPE; }

	@Override
	protected Table sameShape(int id) { return new OvalTable(id, "", length, width); }

	@Override
	public String arrangement() { return SHAPE + " " + length + "x" + width; }

	public int getLength() { return length; }

	public int getWidth() { return width; }

	static Walk walk(int length, int width)
	{
		if (width < 1 || length < width)
			throw new IllegalArgumentException("an oval is at least 1 wide and no wider than it is long");
		if (length == width) return new Walk().arc(360, length / 2.0, seats(Math.PI * length));
		double a = length / 2.0, b = width / 2.0;
		double ends = (b * b / a + b) / 2;                                   // the ends' radius
		double sides = ((a - ends) * (a - ends) + b * b - ends * ends) / (2 * (b - ends));
		double half = Math.toDegrees(Math.atan2(sides - b, a - ends));       // half an end's turn
		double side = 180 - 2 * half;
		int sideSeats = seats(sides * Math.toRadians(side));
		int endSeats = seats(ends * Math.toRadians(2 * half));
		return new Walk().turn(-side / 2)
				.arc(side, sides, sideSeats).arc(2 * half, ends, endSeats)
				.arc(side, sides, sideSeats).arc(2 * half, ends, endSeats);
	}

	// as many as fit, there being no half guests
	private static int seats(double length)
	{
		return (int) Math.floor(length + 1e-9);
	}
}
