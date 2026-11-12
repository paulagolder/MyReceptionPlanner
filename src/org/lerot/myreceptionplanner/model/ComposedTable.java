package org.lerot.myreceptionplanner.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
 * A table made of the venue's own tables pushed together: a horseshoe, a T,
 * an E (see docs/table-notation.md). Each piece is a line of tables of one
 * size, in seat units on a grid, from the top left. The squares they cover
 * give the outline, and any holes; the outline is walked from its top left,
 * heading right, clockwise, then each hole: that is the seat order.
 *
 * Every unit of the outline is seated unless its table says that side is
 * bare. The edges round a hole are bare unless seatHoles. Where both edges
 * of an inside corner are seated the two chairs would want the same place,
 * so the later one round the walk is left out.
 */
public class ComposedTable extends WalkTable
{
	public static final String SHAPE = "composed";
	public static final Set<String> SIDES = Set.of("far", "near", "left", "right");

	/*
	 * count tables of width x depth (as placed: along, across), the first at
	 * x, y, each going right or down from the one before; bare names the
	 * sides with no seats, as the tables are placed.
	 */
	public record Piece(int width, int depth, int count, int x, int y, boolean down, Set<String> bare)
	{
		public Piece
		{
			if (width < 1 || depth < 1) throw new IllegalArgumentException("a table is at least 1 by 1");
			if (count < 1) throw new IllegalArgumentException("a line of tables has at least one");
			if (!SIDES.containsAll(bare))
				throw new IllegalArgumentException("bare sides are far, near, left and right, not " + bare);
			bare = Set.copyOf(bare);
		}
	}

	private final List<Piece> pieces;
	private final boolean seatHoles;

	public ComposedTable(int id, String name, List<Piece> pieces, boolean seatHoles)
	{
		super(id, name, walk(pieces, seatHoles));
		this.pieces = List.copyOf(pieces);
		this.seatHoles = seatHoles;
	}

	@Override
	public String shape() { return SHAPE; }

	@Override
	protected Table sameShape(int id) { return new ComposedTable(id, "", pieces, seatHoles); }

	@Override
	public String arrangement() { return SHAPE + " " + getWalk(); }

	public List<Piece> pieces() { return pieces; }

	public boolean seatsHoles() { return seatHoles; }

	// how many of each size of table it takes, the larger first: "2×1" → 5
	public Map<String, Integer> pickList()
	{
		Map<List<Integer>, Integer> bySize = new TreeMap<>((a, b) -> a.get(0) * a.get(1) != b.get(0) * b.get(1)
				? b.get(0) * b.get(1) - a.get(0) * a.get(1) : b.get(0) - a.get(0));
		for (Piece p : pieces)
			bySize.merge(List.of(Math.max(p.width(), p.depth()), Math.min(p.width(), p.depth())), p.count(), Integer::sum);
		Map<String, Integer> list = new LinkedHashMap<>();
		bySize.forEach((size, n) -> list.put(size.get(0) + "×" + size.get(1), n));
		return list;
	}

	// "9 × 2×1, 1 × 1×1"
	public String pickListText()
	{
		List<String> parts = new ArrayList<>();
		pickList().forEach((size, n) -> parts.add(n + " × " + size));
		return String.join(", ", parts);
	}

	// ---- from the tables to a walk ----

	private record Cell(int x, int y) {}

	private record Dir(int dx, int dy) {}

	private record Edge(Cell from, Cell to, boolean seated) {}  // corners, as cells' top lefts

	private static final Dir FAR = new Dir(0, -1), RIGHT = new Dir(1, 0), NEAR = new Dir(0, 1), LEFT = new Dir(-1, 0);
	private static final Map<Dir, String> SIDE = Map.of(FAR, "far", RIGHT, "right", NEAR, "near", LEFT, "left");

	static Walk walk(List<Piece> pieces, boolean seatHoles)
	{
		if (pieces.isEmpty()) throw new IllegalArgumentException("a composed table needs some tables");
		Set<Cell> cells = new HashSet<>();
		Set<List<Object>> bare = new HashSet<>();  // (cell, side)
		for (Piece p : pieces)
		{
			for (int i = 0; i < p.count(); i++)
			{
				int ox = p.down() ? p.x() : p.x() + i * p.width();
				int oy = p.down() ? p.y() + i * p.depth() : p.y();
				for (int cx = ox; cx < ox + p.width(); cx++)
				{
					for (int cy = oy; cy < oy + p.depth(); cy++)
					{
						Cell c = new Cell(cx, cy);
						if (!cells.add(c))
							throw new IllegalArgumentException("two tables are on the same square, " + cx + " " + cy);
						for (Map.Entry<Dir, String> side : SIDE.entrySet())
						{
							Dir d = side.getKey();
							boolean withinTable = cx + d.dx() >= ox && cx + d.dx() < ox + p.width()
									&& cy + d.dy() >= oy && cy + d.dy() < oy + p.depth();
							if (!withinTable && p.bare().contains(side.getValue())) bare.add(List.of(c, d));
						}
					}
				}
			}
		}

		// each unit edge of the outline, the table on its right
		Map<Cell, List<Edge>> edges = new HashMap<>();
		for (Cell c : cells)
		{
			int x = c.x(), y = c.y();
			edge(edges, cells, bare, c, FAR, new Cell(x, y), new Cell(x + 1, y));
			edge(edges, cells, bare, c, RIGHT, new Cell(x + 1, y), new Cell(x + 1, y + 1));
			edge(edges, cells, bare, c, NEAR, new Cell(x + 1, y + 1), new Cell(x, y + 1));
			edge(edges, cells, bare, c, LEFT, new Cell(x, y + 1), new Cell(x, y));
		}
		List<List<Edge>> loops = new ArrayList<>();
		while (!edges.isEmpty())
		{
			Cell at = edges.keySet().stream().min((a, b) -> a.y() != b.y() ? a.y() - b.y() : a.x() - b.x()).orElseThrow();
			List<Edge> loop = new ArrayList<>();
			while (edges.containsKey(at))
			{
				List<Edge> out = edges.get(at);
				Edge e = out.remove(out.size() - 1);
				if (out.isEmpty()) edges.remove(at);
				loop.add(e);
				at = e.to();
			}
			loops.add(loop);
		}
		List<List<Edge>> outsides = loops.stream().filter(l -> area(l) > 0).toList();
		List<List<Edge>> holes = loops.stream().filter(l -> area(l) < 0).toList();
		if (outsides.size() != 1) throw new IllegalArgumentException("the tables are not all joined together");

		Walk walk = new Walk();
		Cell start = steps(outsides.get(0), false, walk);
		for (List<Edge> hole : holes)
		{
			List<Edge> clockwise = new ArrayList<>();
			for (int i = hole.size() - 1; i >= 0; i--)
				clockwise.add(new Edge(hole.get(i).to(), hole.get(i).from(), hole.get(i).seated() && seatHoles));
			Cell from = first(clockwise);
			walk.hole(from.x() - start.x(), from.y() - start.y());
			steps(clockwise, true, walk);
		}
		return walk;
	}

	private static void edge(Map<Cell, List<Edge>> edges, Set<Cell> cells, Set<List<Object>> bare,
			Cell c, Dir d, Cell from, Cell to)
	{
		if (cells.contains(new Cell(c.x() + d.dx(), c.y() + d.dy()))) return;
		edges.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(from, to, !bare.contains(List.of(c, d))));
	}

	// positive going clockwise as drawn, y down the page
	private static long area(List<Edge> loop)
	{
		long a = 0;
		for (Edge e : loop)
			a += (long) e.from().x() * e.to().y() - (long) e.to().x() * e.from().y();
		return a;
	}

	// the top left corner, leaving it heading right
	private static Cell first(List<Edge> loop)
	{
		return loop.get(firstIndex(loop)).from();
	}

	private static int firstIndex(List<Edge> loop)
	{
		int best = 0;
		for (int i = 1; i < loop.size(); i++)
		{
			Edge a = loop.get(i), b = loop.get(best);
			int byY = a.from().y() - b.from().y(), byX = a.from().x() - b.from().x();
			boolean rightA = a.to().x() > a.from().x(), rightB = b.to().x() > b.from().x();
			if (byY < 0 || byY == 0 && (byX < 0 || byX == 0 && rightA && !rightB)) best = i;
		}
		return best;
	}

	// the loop as steps and turns added to the walk; where it starts
	private static Cell steps(List<Edge> loop, boolean hole, Walk walk)
	{
		int i0 = firstIndex(loop);
		List<Edge> l = new ArrayList<>(loop.subList(i0, loop.size()));
		l.addAll(loop.subList(0, i0));
		int n = l.size();
		Dir[] heading = new Dir[n];
		boolean[] seated = new boolean[n];
		for (int i = 0; i < n; i++)
		{
			heading[i] = new Dir(l.get(i).to().x() - l.get(i).from().x(), l.get(i).to().y() - l.get(i).from().y());
			seated[i] = l.get(i).seated();
		}
		// an inside corner costs the later seat, round to the start again
		for (int k = 1; k <= n; k++)
		{
			int i = k % n;
			Dir a = heading[(i - 1 + n) % n], b = heading[i];
			int cross = a.dx() * b.dy() - a.dy() * b.dx();
			boolean inside = hole ? cross > 0 : cross < 0;
			if (inside && seated[(i - 1 + n) % n] && seated[i]) seated[i] = false;
		}
		for (int i = 0; i < n; )
		{
			int j = i;
			while (j + 1 < n && heading[j + 1].equals(heading[i]) && seated[j + 1] == seated[i]) j++;
			int length = j - i + 1;
			walk.step(length, seated[i] ? length : 0);
			Dir next = heading[(j + 1) % n];
			if (!next.equals(heading[i]))
				walk.turn(heading[i].dx() * next.dy() - heading[i].dy() * next.dx() > 0 ? 90 : -90);
			i = j + 1;
		}
		return l.get(0).from();
	}
}
