package org.lerot.myreceptionplanner.model;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/*
 * A table described as a walk round its edge, like a waiter going round
 * serving each place (see docs/table-notation.md). Lengths are in seat units
 * (Table.SEAT_SPACING), turns in degrees.
 *
 * The walk starts at 0, 0 heading right and goes clockwise: the table on the
 * right, the seats on the left. A hole is a walk of its own, starting at its
 * point heading right, also clockwise; round a hole the table is on the left,
 * so its seats are on the right, facing into the hole. The seats come in the
 * order walked: round the outside, then round each hole.
 *
 *   new Walk().step(20, 20).turn(90).step(2, 0).turn(90).step(20, 0).turn(90).step(2, 0)
 *   new Walk().arc(360, 3.18, 20).hole(0, 1.68).arc(360, 1.5, 0)
 *
 * Each step and arc adds to the latest loop: the outside, or the hole last
 * begun. Once a walk is given to a table it is not changed.
 */
public final class Walk
{
	public enum Kind { STEP, TURN, ARC }

	// a step has a length, a turn and an arc a turn (by), an arc a radius
	public record Move(Kind kind, double length, double by, double radius, int seats) {}

	// the outside starts at 0, 0; a hole at its point
	public record Loop(double x, double y, List<Move> moves) {}

	// the table top and its seats, in centimetres about the centre of the
	// box round the outline, and anything odd about it
	public record Traced(Shape top, List<Point2D> seats, List<String> warnings) {}

	private static final double CLOSE = 0.01;         // seat units a walk may end from its start
	private static final double SLACK = 1e-6;         // for lengths that are seats exactly
	private static final double FIT = 0.05;           // seat units short a stretch may be for its seats (a rounded radius)
	private static final double ARC_PIECE = 2;        // degrees of an arc drawn as one straight line
	private static final double OFF = (Table.SEAT_GAP + Table.SEAT_RADIUS) / Table.SEAT_SPACING;

	private final List<Loop> loops = new ArrayList<>();

	public Walk()
	{
		loops.add(new Loop(0, 0, new ArrayList<>()));
	}

	// straight on, with the seats spread evenly along it (no more than its length)
	public Walk step(double length, int seats)
	{
		if (length <= 0) throw new IllegalArgumentException("a step must go forward, not " + number(length));
		checkSeats(seats, length, "step " + number(length));
		return add(new Move(Kind.STEP, length, 0, 0, seats));
	}

	// on the spot, clockwise; negative is anticlockwise
	public Walk turn(double by)
	{
		return add(new Move(Kind.TURN, 0, by, 0, 0));
	}

	// bending clockwise as it goes, about a centre radius to the right
	public Walk arc(double by, double radius, int seats)
	{
		if (by == 0) throw new IllegalArgumentException("an arc must turn");
		if (radius <= 0) throw new IllegalArgumentException("an arc needs a radius, not " + number(radius));
		checkSeats(seats, radius * Math.toRadians(Math.abs(by)), "arc " + number(by));
		return add(new Move(Kind.ARC, 0, by, radius, seats));
	}

	// an arc as long as its seats
	public Walk arc(double by, int seats)
	{
		if (seats < 1) throw new IllegalArgumentException("an arc without a radius needs seats");
		return arc(by, seats / Math.toRadians(Math.abs(by)), seats);
	}

	// begins the walk round a hole, from this point (seat units from the
	// outside's start); the steps and arcs after go round it
	public Walk hole(double x, double y)
	{
		loops.add(new Loop(x, y, new ArrayList<>()));
		return this;
	}

	public List<Loop> loops() { return Collections.unmodifiableList(loops); }

	private Walk add(Move move)
	{
		loops.get(loops.size() - 1).moves().add(move);
		return this;
	}

	private static void checkSeats(int seats, double length, String what)
	{
		if (seats < 0) throw new IllegalArgumentException(what + " cannot have " + seats + " seats");
		if (seats > Math.floor(length + FIT))
			throw new IllegalArgumentException(what + " is " + number(length) + " long, too short for "
					+ seats + " seats");
	}

	// ---- tracing ----

	/*
	 * Walks it: the outline and the seats. Every loop must come back to where
	 * it began (the last turn, back to heading right, can be left out), else
	 * IllegalArgumentException says how far out it ends. Seats so close that
	 * their chairs would collide are warned of, not moved.
	 */
	public Traced trace()
	{
		List<List<Point2D>> outlines = new ArrayList<>();
		List<Point2D> seats = new ArrayList<>();
		for (int i = 0; i < loops.size(); i++)
			outlines.add(trace(loops.get(i), i > 0, i == 0 ? "The walk" : "The walk round hole " + i, seats));

		// about the centre of the box round the outline, in centimetres
		Rectangle2D box = null;
		for (List<Point2D> outline : outlines)
		{
			for (Point2D p : outline)
			{
				if (box == null) box = new Rectangle2D.Double(p.getX(), p.getY(), 0, 0);
				else box.add(p);
			}
		}
		double cx = box == null ? 0 : box.getCenterX();
		double cy = box == null ? 0 : box.getCenterY();
		double unit = Table.SEAT_SPACING;
		Path2D.Double top = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		for (List<Point2D> outline : outlines)
		{
			for (int k = 0; k < outline.size(); k++)
			{
				double x = (outline.get(k).getX() - cx) * unit;
				double y = (outline.get(k).getY() - cy) * unit;
				if (k == 0) top.moveTo(x, y);
				else top.lineTo(x, y);
			}
			top.closePath();
		}
		List<Point2D> placed = new ArrayList<>();
		for (Point2D s : seats)
			placed.add(new Point2D.Double((s.getX() - cx) * unit, (s.getY() - cy) * unit));

		List<String> warnings = new ArrayList<>();
		for (int i = 0; i < placed.size(); i++)
		{
			for (int j = i + 1; j < placed.size(); j++)
			{
				if (placed.get(i).distance(placed.get(j)) < 2 * Table.SEAT_RADIUS - SLACK)
					warnings.add("Seats " + (i + 1) + " and " + (j + 1) + " are too close together");
			}
		}
		return new Traced(top, Collections.unmodifiableList(placed), Collections.unmodifiableList(warnings));
	}

	// one loop, in seat units: its outline, and its seats added to seats
	private static List<Point2D> trace(Loop loop, boolean hole, String what, List<Point2D> seats)
	{
		double side = hole ? -1 : 1;   // 1: the seats on the left of the way
		double px = loop.x(), py = loop.y();
		double hx = 1, hy = 0;         // heading right
		List<Point2D> outline = new ArrayList<>();
		outline.add(new Point2D.Double(px, py));
		for (Move m : loop.moves())
		{
			switch (m.kind())
			{
				case TURN ->
				{
					double[] h = turned(hx, hy, m.by());
					hx = h[0];
					hy = h[1];
				}
				case STEP ->
				{
					double lx = hy, ly = -hx;  // to the left
					for (int k = 0; k < m.seats(); k++)
					{
						double f = (k + 0.5) * m.length() / m.seats();
						seats.add(new Point2D.Double(px + hx * f + side * lx * OFF, py + hy * f + side * ly * OFF));
					}
					px += hx * m.length();
					py += hy * m.length();
					outline.add(new Point2D.Double(px, py));
				}
				case ARC ->
				{
					double r = m.radius();
					double ccx = px - hy * r, ccy = py + hx * r;  // the centre, to the right
					double a0 = Math.atan2(py - ccy, px - ccx);
					double sweep = Math.toRadians(m.by());
					double rr = r + side * OFF;
					for (int k = 0; k < m.seats(); k++)
					{
						double a = a0 + sweep * (k + 0.5) / m.seats();
						seats.add(new Point2D.Double(ccx + rr * Math.cos(a), ccy + rr * Math.sin(a)));
					}
					int pieces = Math.max(4, (int) Math.ceil(Math.abs(m.by()) / ARC_PIECE));
					for (int k = 1; k <= pieces; k++)
					{
						double a = a0 + sweep * k / pieces;
						outline.add(new Point2D.Double(ccx + r * Math.cos(a), ccy + r * Math.sin(a)));
					}
					px = ccx + r * Math.cos(a0 + sweep);
					py = ccy + r * Math.sin(a0 + sweep);
					double[] h = turned(hx, hy, m.by());
					hx = h[0];
					hy = h[1];
				}
			}
		}
		double out = Math.hypot(px - loop.x(), py - loop.y());
		if (out > CLOSE)
			throw new IllegalArgumentException(what + " does not come back to where it began: it ends "
					+ number(px - loop.x()) + " across and " + number(py - loop.y()) + " down from it");
		outline.remove(outline.size() - 1);
		return outline;
	}

	// clockwise as drawn, y going down the page; square turns stay square
	// (cos 90 is not quite 0), so straight edges stay straight
	private static double[] turned(double hx, double hy, double degrees)
	{
		double t = Math.toRadians(degrees);
		return new double[] { snapped(hx * Math.cos(t) - hy * Math.sin(t)), snapped(hx * Math.sin(t) + hy * Math.cos(t)) };
	}

	private static double snapped(double value)
	{
		double whole = Math.rint(value);
		return Math.abs(value - whole) < 1e-12 ? whole : value;
	}

	// ---- in words ----

	// "step 20 (20 seats) · turn 90 · step 2 · …; hole at 0 1.68: arc 360, radius 1.5"
	@Override
	public String toString()
	{
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < loops.size(); i++)
		{
			Loop loop = loops.get(i);
			if (i > 0) text.append("; hole at ").append(number(loop.x())).append(' ').append(number(loop.y())).append(": ");
			List<String> words = new ArrayList<>();
			for (Move m : loop.moves())
			{
				String seats = m.seats() == 0 ? "" : " (" + m.seats() + (m.seats() == 1 ? " seat)" : " seats)");
				words.add(switch (m.kind())
				{
					case STEP -> "step " + number(m.length()) + seats;
					case TURN -> "turn " + number(m.by());
					case ARC -> "arc " + number(m.by()) + ", radius " + number(m.radius()) + seats;
				});
			}
			text.append(String.join(" · ", words));
		}
		return text.toString();
	}

	// up to four decimals, no trailing zeros
	static String number(double value)
	{
		String s = String.format(Locale.ROOT, "%.4f", value);
		s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s.equals("-0") ? "0" : s;
	}
}
