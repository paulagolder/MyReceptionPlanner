package org.lerot.myreceptionplanner.model;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.List;

/*
 * An oval or polygon table: its top and chairs come from a walk round its
 * edge (see Walk), the chairs in the order walked; the table is moved and
 * turned like any other.
 */
public abstract class WalkTable extends Table
{
	private Walk walk;
	private Walk.Traced traced;

	// IllegalArgumentException if the walk does not come back to its start
	protected WalkTable(int id, String name, Walk walk)
	{
		super(id, name);
		setWalk(walk);
	}

	@Override
	public int places() { return traced.seats().size(); }

	public Walk getWalk() { return walk; }

	protected void setWalk(Walk walk)
	{
		Walk.Traced traced = walk.trace();
		boolean changed = this.walk == null || !walk.toString().equals(this.walk.toString());
		this.walk = walk;
		this.traced = traced;
		if (changed) chairsChanged();
	}

	// seats so close their chairs would collide, and the like
	public List<String> warnings() { return traced.warnings(); }

	@Override
	public Shape top() { return traced.top(); }

	@Override
	public List<Point2D> seats() { return traced.seats(); }

	@Override
	public double reach()
	{
		double far = 0;
		double[] p = new double[6];
		for (PathIterator it = traced.top().getPathIterator(null); !it.isDone(); it.next())
		{
			if (it.currentSegment(p) != PathIterator.SEG_CLOSE) far = Math.max(far, Math.hypot(p[0], p[1]));
		}
		for (Point2D s : traced.seats())
			far = Math.max(far, s.distance(0, 0) + SEAT_RADIUS);
		return far;
	}
}
