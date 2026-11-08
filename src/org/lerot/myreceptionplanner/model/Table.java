package org.lerot.myreceptionplanner.model;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/*
 * A table on the floor plan. Sizes and positions are in centimetres; x, y is
 * the table's centre and rotation turns it clockwise about that point.
 * Each kind of table gives its top and its seats as if it sat at 0,0 unturned.
 *
 * Chairs are indexed 0, 1, 2 ... clockwise from the shape's own starting chair.
 * Any chair can be made seat 1 and the seats are numbered clockwise from it;
 * a chair can be disabled (no one is to sit there) without changing the numbers.
 */
public abstract class Table
{
	public static final double SEAT_SPACING = 60;  // cm of table edge for each place
	public static final double SEAT_RADIUS = 20;   // a chair, drawn as a circle
	public static final double SEAT_GAP = 5;       // between the table edge and a chair

	private final int id;
	private String name;
	private double x;
	private double y;
	private double rotation;
	private boolean head;
	private int firstSeat;                                     // the chair that is seat 1
	private final Set<Integer> disabledSeats = new TreeSet<>(); // chair indexes

	protected Table(int id, String name)
	{
		this.id = id;
		this.name = name;
	}

	// the shape's name in tables.xml
	public abstract String shape();

	public abstract int places();

	// the table top
	public abstract Shape top();

	// the centre of each chair, clockwise from the shape's starting chair
	public abstract List<Point2D> seats();

	// how far the table reaches from its centre, chairs included
	public abstract double reach();

	public int getId() { return id; }

	public String getName() { return name; }

	public void setName(String name) { this.name = name; }

	public double getX() { return x; }

	public double getY() { return y; }

	public void moveTo(double x, double y)
	{
		this.x = x;
		this.y = y;
	}

	public double getRotation() { return rotation; }

	// in degrees, kept between 0 and 360
	public void setRotation(double degrees)
	{
		rotation = ((degrees % 360) + 360) % 360;
	}

	// the chairs disabled and the places that remain
	public int usablePlaces() { return places() - disabledSeats.size(); }

	// ---- seat 1 and disabled seats ----

	public int getFirstSeat() { return firstSeat; }

	// makes this chair seat 1; it cannot then be disabled
	public void setFirstSeat(int chair)
	{
		checkChair(chair);
		firstSeat = chair;
		disabledSeats.remove(chair);
	}

	// 1 for the first seat, then clockwise
	public int seatNumber(int chair)
	{
		checkChair(chair);
		return Math.floorMod(chair - firstSeat, places()) + 1;
	}

	public boolean isDisabled(int chair) { return disabledSeats.contains(chair); }

	public Set<Integer> getDisabledSeats() { return Collections.unmodifiableSet(disabledSeats); }

	// seat 1 cannot be disabled
	public void setDisabled(int chair, boolean disabled)
	{
		checkChair(chair);
		if (!disabled) disabledSeats.remove(chair);
		else if (chair != firstSeat) disabledSeats.add(chair);
	}

	public void enableAllSeats() { disabledSeats.clear(); }

	// call when the number or arrangement of chairs changes: the old chair
	// indexes no longer mean the same chairs, so seat 1 goes back to the
	// starting chair and every chair is enabled again (or, at the head
	// table, it starts again with the head table's seating)
	protected void chairsChanged()
	{
		firstSeat = 0;
		disabledSeats.clear();
		if (head) seatAsHeadTable();
	}

	private void checkChair(int chair)
	{
		if (chair < 0 || chair >= places())
			throw new IndexOutOfBoundsException("no chair " + chair + " at a table of " + places());
	}

	public boolean isHead() { return head; }

	// use Plan.setHead, so there is only one head table; a table that
	// becomes the head table gets the head table's seating
	void setHead(boolean head)
	{
		boolean becomesHead = head && !this.head;
		this.head = head;
		if (becomesHead) seatAsHeadTable();
	}

	// for loading: the seats are as saved, so no head table seating
	void markHead(boolean head) { this.head = head; }

	// the seats a head table starts with; by default no change
	protected void seatAsHeadTable() {}

	// from the table's own coordinates to the floor's
	public AffineTransform toFloor()
	{
		AffineTransform t = AffineTransform.getTranslateInstance(x, y);
		t.rotate(Math.toRadians(rotation));
		return t;
	}

	public Point2D toLocal(Point2D floor)
	{
		try
		{
			return toFloor().inverseTransform(floor, null);
		} catch (NoninvertibleTransformException e)
		{
			throw new IllegalStateException(e);  // a turn and a move can always be undone
		}
	}

	// true if the point is on the table top or one of its chairs
	public boolean contains(Point2D floor)
	{
		Point2D p = toLocal(floor);
		if (top().contains(p)) return true;
		for (Point2D seat : seats())
		{
			if (seat.distance(p) <= SEAT_RADIUS) return true;
		}
		return false;
	}

	// the chair at this floor point, or -1
	public int chairAt(Point2D floor)
	{
		Point2D p = toLocal(floor);
		List<Point2D> seats = seats();
		for (int i = 0; i < seats.size(); i++)
		{
			if (seats.get(i).distance(p) <= SEAT_RADIUS) return i;
		}
		return -1;
	}

	public Point2D seatOnFloor(int index)
	{
		return toFloor().transform(seats().get(index), null);
	}

	// the floor area the table and its chairs cover
	public Rectangle2D bounds()
	{
		double r = reach();
		return new Rectangle2D.Double(x - r, y - r, 2 * r, 2 * r);
	}
}
