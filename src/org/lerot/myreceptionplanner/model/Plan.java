package org.lerot.myreceptionplanner.model;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// The tables of one reception, in drawing order (the last is on top)
public class Plan
{
	// tables whose centres are this close top to bottom are in the same row
	static final double ROW_DEPTH = 120;

	private final List<Table> tables = new ArrayList<>();

	public List<Table> tables()
	{
		return Collections.unmodifiableList(tables);
	}

	public void add(Table table)
	{
		tables.add(table);
		if (table.isHead()) setHead(table);
	}

	public void remove(Table table)
	{
		separate(table);
		tables.remove(table);
	}

	public void bringToFront(Table table)
	{
		if (tables.remove(table)) tables.add(table);
	}

	public Optional<Table> find(int id)
	{
		return tables.stream().filter(t -> t.getId() == id).findFirst();
	}

	public int nextId()
	{
		return tables.stream().mapToInt(Table::getId).max().orElse(0) + 1;
	}

	// makes this the head table (null for none); there is only ever one
	public void setHead(Table head)
	{
		for (Table t : tables)
			t.setHead(t == head);
	}

	public Optional<Table> head()
	{
		return tables.stream().filter(Table::isHead).findFirst();
	}

	/*
	 * Numbers the tables afresh by where they stand: in rows from the top of
	 * the plan down, left to right along each row. The head table and named
	 * tables are numbered too, though their names show instead, so the
	 * numbers shown may skip theirs. Otherwise numbers stay put when tables
	 * move.
	 */
	public void renumber()
	{
		for (Table t : tables)
		{
			if (!t.isNumbered()) t.setNumber(0);
		}
		// tables and pews are numbered each on their own
		for (String series : series())
		{
			int next = 1;
			for (List<Table> row : rows(tables.stream()
					.filter(t -> t.isNumbered() && t.series().equals(series)).toList()))
			{
				for (Table t : row)
					t.setNumber(next++);
			}
		}
	}

	private List<String> series()
	{
		return tables.stream().map(Table::series).distinct().toList();
	}

	// all the tables in rows, as renumber sees them
	public List<List<Table>> rows() { return rows(tables); }

	// in rows from the top down, each left to right: a row is the tables not
	// much lower than its topmost (see Table.rowDepth)
	private static List<List<Table>> rows(List<Table> these)
	{
		List<Table> byDepth = new ArrayList<>(these);
		byDepth.sort(Comparator.comparingDouble(Table::getY));
		List<List<Table>> rows = new ArrayList<>();
		int start = 0;
		while (start < byDepth.size())
		{
			double top = byDepth.get(start).getY();
			double depth = byDepth.get(start).rowDepth();
			int end = start;
			while (end < byDepth.size() && byDepth.get(end).getY() - top <= depth) end++;
			List<Table> row = new ArrayList<>(byDepth.subList(start, end));
			row.sort(Comparator.comparingDouble(Table::getX));
			rows.add(row);
			start = end;
		}
		return rows;
	}

	// gives the next number up to any table that has none, or shares one
	// with a table before it (a new table, or one from an older file whose
	// named tables had none)
	public void numberUnnumbered()
	{
		for (String series : series())
		{
			List<Table> these = tables.stream().filter(t -> t.series().equals(series)).toList();
			Set<Integer> used = new HashSet<>();
			List<Table> needing = new ArrayList<>();
			for (Table t : these)
			{
				if (!t.isNumbered()) continue;
				if (t.getNumber() > 0 && used.add(t.getNumber())) continue;
				needing.add(t);
			}
			int next = these.stream().mapToInt(Table::getNumber).max().orElse(0) + 1;
			for (Table t : needing)
				t.setNumber(next++);
		}
	}

	// what the table is called: its own name, else "Head table", "Table 3" or "Pew 2"
	public String label(Table table)
	{
		if (table.hasName()) return table.getName();
		if (table.isHead()) return "Head table";
		if (!table.isNumbered() || table.getNumber() == 0) return table.series();
		return table.series() + " " + table.getNumber();
	}

	// ---- tables combined: pushed together, each keeping its own identity ----

	private static final double TOUCHING = 5;  // cm between tops still counted as pushed together
	private static final double TOPS_OVER = 3; // cm one combined table's top may lie over another's

	// the table and those combined with it, or the table alone
	public List<Table> groupOf(Table t)
	{
		if (t.getGroup() == 0) return List.of(t);
		return tables.stream().filter(o -> o.getGroup() == t.getGroup()).toList();
	}

	// the tables combined with it whose tops lie over its own (see overlaps), to push apart
	public List<Table> lyingOver(Table t)
	{
		List<Table> out = new ArrayList<>();
		for (Overlap o : overlaps())
		{
			if (o.a().getGroup() == 0 || o.a().getGroup() != o.b().getGroup()) continue;
			if (o.a() == t) out.add(o.b());
			else if (o.b() == t) out.add(o.a());
		}
		return out;
	}

	// the tables not combined with it whose tops touch its own, that it could be combined with
	public List<Table> touching(Table t)
	{
		if (!t.combines()) return List.of();
		java.awt.geom.Area near = new java.awt.geom.Area(
				new java.awt.BasicStroke((float) (2 * TOUCHING)).createStrokedShape(t.topOnFloor()));
		near.add(new java.awt.geom.Area(t.topOnFloor()));
		List<Table> out = new ArrayList<>();
		for (Table o : tables)
		{
			if (o == t || !o.combines() || (t.getGroup() != 0 && o.getGroup() == t.getGroup())) continue;
			java.awt.geom.Area both = new java.awt.geom.Area(o.topOnFloor());
			both.intersect(near);
			if (!both.isEmpty()) out.add(o);
		}
		return out;
	}

	/*
	 * The chairs of each table in the group that its partners block: a chair
	 * on or against another's top, and at an inside corner, where two
	 * tables' chairs would clash, the chair of the later table. Seat 1 is
	 * among them if blocked (see block).
	 */
	public java.util.Map<Table, Set<Integer>> blocked(List<Table> group)
	{
		// by id, so the same chair is chosen at a corner however the tables were last touched
		List<Table> byId = group.stream().sorted(Comparator.comparingInt(Table::getId)).toList();
		java.util.Map<Table, Set<Integer>> out = new java.util.LinkedHashMap<>();
		double r = Table.SEAT_RADIUS;
		for (Table t : byId)
		{
			Set<Integer> chairs = new java.util.TreeSet<>();
			for (int c = 0; c < t.places(); c++)
				for (Table o : byId)
					if (o != t && overlapsCircle(o.topOnFloor(), t.seatOnFloor(c), r)) chairs.add(c);
			out.put(t, chairs);
		}
		// inside corners: two tables' chairs on each other, neither on a top (whether turned off yet or not)
		for (int i = 0; i < byId.size(); i++)
			for (int j = i + 1; j < byId.size(); j++)
			{
				Table a = byId.get(i), b = byId.get(j);
				for (int ca = 0; ca < a.places(); ca++)
				{
					if (out.get(a).contains(ca)) continue;
					for (int cb = 0; cb < b.places(); cb++)
						if (!out.get(b).contains(cb) && a.seatOnFloor(ca).distance(b.seatOnFloor(cb)) < 2 * r)
							out.get(b).add(cb);
				}
			}
		return out;
	}

	// whether the shape covers any of the circle (more than a sliver)
	private static boolean overlapsCircle(java.awt.Shape shape, Point2D centre, double radius)
	{
		java.awt.geom.Area a = new java.awt.geom.Area(shape);
		a.intersect(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(centre.getX() - radius,
				centre.getY() - radius, 2 * radius, 2 * radius)));
		java.awt.geom.Rectangle2D b = a.getBounds2D();
		return !a.isEmpty() && b.getWidth() > 1 && b.getHeight() > 1;
	}

	// turns off the blocked chairs; seat 1, if blocked, moves on to the next chair that is not
	public static void block(java.util.Map<Table, Set<Integer>> blocked)
	{
		blocked.forEach((t, chairs) ->
		{
			if (chairs.contains(t.getFirstSeat()))
				for (int k = 1; k < t.places(); k++)
				{
					int c = (t.getFirstSeat() + k) % t.places();
					if (!chairs.contains(c) && !t.isDisabled(c))
					{
						t.setFirstSeat(c);
						break;
					}
				}
			for (int c : chairs)
				t.setDisabled(c, true);
		});
	}

	// the chairs combining these tables would block, as block would turn them off
	public java.util.Map<Table, Set<Integer>> blockedIfCombined(Table a, Table b)
	{
		List<Table> group = new ArrayList<>(groupOf(a));
		for (Table o : groupOf(b))
			if (!group.contains(o)) group.add(o);
		return blocked(group);
	}

	// combines the two (and any already combined with either), turning off the chairs they block
	public void combine(Table a, Table b)
	{
		if (!a.combines() || !b.combines()) throw new IllegalArgumentException("Pews and altars are not combined");
		java.util.Map<Table, Set<Integer>> blocked = blockedIfCombined(a, b);
		int g = a.getGroup() != 0 ? a.getGroup() : b.getGroup() != 0 ? b.getGroup()
				: tables.stream().mapToInt(Table::getGroup).max().orElse(0) + 1;
		for (Table t : blocked.keySet())
			t.setGroup(g);
		block(blocked);
	}

	// after a table of the group changed size or shape: turns off the chairs now blocked
	public void reblock(Table t)
	{
		if (t.getGroup() != 0) block(blocked(groupOf(t)));
	}

	/*
	 * Takes the table out of its group, turning back on the chairs that
	 * combining had blocked: its own, and those of the rest that it alone
	 * blocked. A group left with one table is no longer a group.
	 */
	public void separate(Table t)
	{
		if (t.getGroup() == 0) return;
		List<Table> group = groupOf(t);
		java.util.Map<Table, Set<Integer>> before = blocked(group);
		List<Table> rest = group.stream().filter(o -> o != t).toList();
		java.util.Map<Table, Set<Integer>> after = rest.size() > 1 ? blocked(rest) : java.util.Map.of();
		t.setGroup(0);
		if (rest.size() == 1) rest.get(0).setGroup(0);
		before.forEach((table, chairs) ->
		{
			for (int c : chairs)
				if (!after.getOrDefault(table, Set.of()).contains(c)) table.setDisabled(c, false);
		});
	}

	/*
	 * Slides the table (and those combined with it) until its top just
	 * meets the other's: a table lying over the other is drawn back, one a
	 * little apart is pushed up to it. A long table slides along its own
	 * length, so one laid at an angle ends with its corner on the other's
	 * edge (a chevron); a round one along the line between their middles.
	 * False if they cannot be brought together that way.
	 */
	public boolean pushTogether(Table t, Table other)
	{
		Point2D axis = t instanceof RoundTable || t instanceof PolygonTable ? null
				: t.toFloor().deltaTransform(new Point2D.Double(1, 0), null);
		Point2D towards = new Point2D.Double(other.getX() - t.getX(), other.getY() - t.getY());
		if (axis == null || Math.abs(axis.getX() * towards.getX() + axis.getY() * towards.getY()) < 1e-6) axis = towards;
		double length = Math.hypot(axis.getX(), axis.getY());
		if (length == 0) return false;
		double ux = axis.getX() / length, uy = axis.getY() / length;
		if (ux * towards.getX() + uy * towards.getY() < 0)
		{
			ux = -ux;
			uy = -uy;
		}
		// now (ux, uy) points towards the other: out of it if over it, into it if apart
		boolean over = topsMeet(t, other, 0, 0, 0);
		double sign = over ? -1 : 1, clear = 0, meet = -1;
		for (double s = 1; s <= 600; s += 1)
		{
			if (topsMeet(t, other, sign * ux, sign * uy, s) != over) { meet = s; break; }
			clear = s;
		}
		if (meet < 0) return false;
		// between the last step one way and the first the other, to half a centimetre
		double lo = clear, hi = meet;
		while (hi - lo > 0.5)
		{
			double mid = (lo + hi) / 2;
			if (topsMeet(t, other, sign * ux, sign * uy, mid) == over) lo = mid; else hi = mid;
		}
		double s = over ? hi : lo;  // just clear of it, or just short of it
		if (t.getGroup() != 0 && t.getGroup() == other.getGroup())
		{
			// combined already, one lying over the other: this one alone, then the chairs it now blocks
			t.moveTo(t.getX() + sign * ux * s, t.getY() + sign * uy * s);
			reblock(t);
		} else moveGroup(t, sign * ux * s, sign * uy * s);
		return true;
	}

	// whether the table's top, moved by s along (ux, uy), covers part of the other's
	private static boolean topsMeet(Table t, Table other, double ux, double uy, double s)
	{
		java.awt.geom.Area a = new java.awt.geom.Area(t.topOnFloor());
		a.transform(java.awt.geom.AffineTransform.getTranslateInstance(ux * s, uy * s));
		a.intersect(new java.awt.geom.Area(other.topOnFloor()));
		return !a.isEmpty();
	}

	// moves the table and those combined with it by this much
	public void moveGroup(Table t, double dx, double dy)
	{
		List<Table> group = groupOf(t);
		dx = Math.max(dx, -group.stream().mapToDouble(Table::getX).min().orElse(0));
		dy = Math.max(dy, -group.stream().mapToDouble(Table::getY).min().orElse(0));
		for (Table o : group)
			o.moveTo(o.getX() + dx, o.getY() + dy);
	}

	// turns the table and those combined with it about their middle, clockwise by this many degrees
	public void turnGroup(Table t, double degrees)
	{
		List<Table> group = groupOf(t);
		double cx = group.stream().mapToDouble(Table::getX).average().orElse(t.getX());
		double cy = group.stream().mapToDouble(Table::getY).average().orElse(t.getY());
		java.awt.geom.AffineTransform turn = java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(degrees), cx, cy);
		for (Table o : group)
		{
			Point2D p = turn.transform(new Point2D.Double(o.getX(), o.getY()), null);
			o.moveTo(p.getX(), p.getY());
			o.setRotation(o.getRotation() + degrees);
		}
	}

	// two tables whose tops or chairs cover the same floor, and where
	public record Overlap(Table a, Table b, java.awt.geom.Area where) {}

	/*
	 * Every pair of tables standing on each other: a top or a chair in use
	 * of one covering part of the other's, by more than a centimetre each
	 * way (tables pushed edge to edge only touch). Tables combined count
	 * only if one's top lies over the other's by more than a few cm.
	 */
	public List<Overlap> overlaps()
	{
		List<Overlap> out = new ArrayList<>();
		java.util.Map<Table, java.awt.geom.Area> covers = new java.util.HashMap<>();
		for (int i = 0; i < tables.size(); i++)
			for (int j = i + 1; j < tables.size(); j++)
			{
				Table a = tables.get(i), b = tables.get(j);
				if (!a.bounds().intersects(b.bounds())) continue;
				if (a.getGroup() != 0 && a.getGroup() == b.getGroup())
				{
					// pushed together on purpose: only their tops lying over each other
					java.awt.geom.Area tops = new java.awt.geom.Area(a.topOnFloor());
					tops.intersect(new java.awt.geom.Area(b.topOnFloor()));
					java.awt.geom.Rectangle2D r = tops.getBounds2D();
					if (!tops.isEmpty() && r.getWidth() > TOPS_OVER && r.getHeight() > TOPS_OVER)
						out.add(new Overlap(a, b, tops));
					continue;
				}
				java.awt.geom.Area both = new java.awt.geom.Area(covers.computeIfAbsent(a, Table::footprint));
				both.intersect(covers.computeIfAbsent(b, Table::footprint));
				java.awt.geom.Rectangle2D r = both.getBounds2D();
				if (!both.isEmpty() && r.getWidth() > 1 && r.getHeight() > 1) out.add(new Overlap(a, b, both));
			}
		return out;
	}

	// "Table 3 and Table 4 overlap", or how many pairs do; "" if none
	public String overlapText()
	{
		List<Overlap> o = overlaps();
		if (o.isEmpty()) return "";
		if (o.size() == 1) return label(o.get(0).a()) + " and " + label(o.get(0).b()) + " overlap";
		return o.size() + " pairs of tables overlap";
	}

	// the places that can be given to guests
	public int reservedPlaces()
	{
		return tables.stream().mapToInt(Table::reservedPlaces).sum();
	}

	// the places that can be used: every chair less the disabled ones
	public int places()
	{
		return tables.stream().mapToInt(Table::usablePlaces).sum();
	}
}
