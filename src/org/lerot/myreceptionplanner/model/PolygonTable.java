package org.lerot.myreceptionplanner.model;

/*
 * A table with equal sides (a hexagon, a heptagon ...), given by its sides
 * and the seats on each; each side is that many seat units long. Side 1 is
 * flat along the far edge, seat 1 at its left end, and the sides go
 * clockwise from it.
 */
public class PolygonTable extends WalkTable
{
	public static final String SHAPE = "polygon";

	private int sides;
	private int perSide;

	public PolygonTable(int id, String name, int sides, int perSide)
	{
		super(id, name, walk(sides, perSide));
		this.sides = sides;
		this.perSide = perSide;
	}

	public void setSides(int sides, int perSide)
	{
		setWalk(walk(sides, perSide));
		this.sides = sides;
		this.perSide = perSide;
	}

	@Override
	public String shape() { return SHAPE; }

	@Override
	protected Table sameShape(int id) { return new PolygonTable(id, "", sides, perSide); }

	@Override
	public String arrangement() { return SHAPE + " " + sides + "x" + perSide; }

	public int getSides() { return sides; }

	public int getPerSide() { return perSide; }

	static Walk walk(int sides, int perSide)
	{
		if (sides < 3) throw new IllegalArgumentException("a polygon needs at least 3 sides");
		if (perSide < 1) throw new IllegalArgumentException("each side needs a seat");
		Walk walk = new Walk();
		for (int i = 0; i < sides; i++)
			walk.step(perSide, perSide).turn(360.0 / sides);
		return walk;
	}
}
