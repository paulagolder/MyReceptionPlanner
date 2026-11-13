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
 *
 * The id is given when the table is made and never changes; guests' seats
 * refer to it. What the table is called comes from the Plan (see
 * Plan.label): its own name if it has one, else "Head table" or its number.
 * The number is kept until the tables are renumbered (Plan.renumber).
 * Each kind of table gives its top and its seats as if it sat at 0,0 unturned.
 *
 * Chairs are indexed 0, 1, 2 ... clockwise from the shape's own starting chair.
 * Any chair can be made seat 1 and the seats are numbered clockwise from it;
 * a chair can be disabled (no one is to sit there) without changing the numbers.
 *
 * A chair that is not disabled is reserved (it can be given to a guest) or
 * open access (anyone may sit there; no one is given it). Chairs at tables
 * start reserved, seats in pews open (see Pew).
 */
public abstract class Table
{
	public static final double SEAT_SPACING = 60;  // cm of table edge for each place
	public static final double SEAT_RADIUS = 20;   // a chair, drawn as a circle
	public static final double SEAT_GAP = 5;       // between the table edge and a chair

	private final int id;
	private String name;
	private int number;  // 0 for none
	private double x;
	private double y;
	private double rotation;
	private boolean head;
	private int firstSeat;                                     // the chair that is seat 1
	private final Set<Integer> disabledSeats = new TreeSet<>(); // chair indexes
	private final Set<Integer> openSeats = new TreeSet<>();     // chair indexes, none disabled

	protected Table(int id, String name)
	{
		this.id = id;
		setName(name);
	}

	// the shape's name in the event's file
	public abstract String shape();

	// a new table of the same shape and places, with this id
	protected abstract Table sameShape(int id);

	/*
	 * A copy with its own id, standing offset by dx, dy: the same shape,
	 * places, turn, seat 1 and disabled chairs, but not its own name nor
	 * being the head table. It goes its own way after.
	 */
	public Table duplicate(int id, double dx, double dy)
	{
		Table t = sameShape(id);
		t.moveTo(Math.max(0, x + dx), Math.max(0, y + dy));
		t.setRotation(rotation);
		if (places() > 0) t.setFirstSeat(firstSeat);
		t.setAllOpen(false);
		for (int chair : openSeats)
			t.setOpen(chair, true);
		for (int chair : disabledSeats)
			t.setDisabled(chair, true);
		return t;
	}

	public abstract int places();

	// the shape and how its chairs are set out; when this changes, chair
	// indexes no longer mean the same chairs
	public abstract String arrangement();

	// the table top
	public abstract Shape top();

	// the centre of each chair, clockwise from the shape's starting chair
	public abstract List<Point2D> seats();

	// how far the table reaches from its centre, chairs included
	public abstract double reach();

	public int getId() { return id; }

	// the table's own name, "" if it goes by its number
	public String getName() { return name; }

	public boolean hasName() { return !name.isEmpty(); }

	/*
	 * Has a number, by where it stands, whether or not it goes by it: a name,
	 * or being the head table, shows instead, and the number is kept behind
	 * it (so lists may skip it). Not an altar.
	 */
	public boolean isNumbered() { return true; }

	// shown by its number: numbered, with no name of its own, not the head table
	public boolean goesByNumber() { return isNumbered() && !hasName() && !head; }

	public int getNumber() { return number; }

	public void setNumber(int number) { this.number = Math.max(0, number); }

	public void setName(String name) { this.name = name == null ? "" : name.trim(); }

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

	// the places that can be given to guests: neither disabled nor open
	public int reservedPlaces() { return usablePlaces() - openSeats.size(); }

	// "Table" or "Pew": what the table is called with its number, and the
	// tables numbered together
	public String series() { return "Table"; }

	// how far apart top to bottom tables can stand and still be one row
	public double rowDepth() { return Plan.ROW_DEPTH; }

	// whether a chair starts open access when the chairs are set out
	protected boolean openByDefault() { return false; }

	// ---- reserved and open seats ----

	public boolean isOpen(int chair) { return openSeats.contains(chair); }

	public boolean isReserved(int chair) { return !isDisabled(chair) && !isOpen(chair); }

	public Set<Integer> getOpenSeats() { return Collections.unmodifiableSet(openSeats); }

	// a disabled chair stays disabled
	public void setOpen(int chair, boolean open)
	{
		checkChair(chair);
		if (!open) openSeats.remove(chair);
		else if (!disabledSeats.contains(chair)) openSeats.add(chair);
	}

	public void setAllOpen(boolean open)
	{
		openSeats.clear();
		if (!open) return;
		for (int c = 0; c < places(); c++)
		{
			if (!disabledSeats.contains(c)) openSeats.add(c);
		}
	}

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

	// the chair of seat number n (1 for the first seat)
	public int chairOfSeat(int n)
	{
		return Math.floorMod(firstSeat + n - 1, places());
	}

	public boolean isDisabled(int chair) { return disabledSeats.contains(chair); }

	public Set<Integer> getDisabledSeats() { return Collections.unmodifiableSet(disabledSeats); }

	// seat 1 cannot be disabled
	public void setDisabled(int chair, boolean disabled)
	{
		checkChair(chair);
		if (!disabled) disabledSeats.remove(chair);
		else if (chair != firstSeat)
		{
			disabledSeats.add(chair);
			openSeats.remove(chair);
		}
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
		setAllOpen(openByDefault());
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
