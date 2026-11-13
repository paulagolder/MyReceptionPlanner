package org.lerot.myreceptionplanner.model;

import org.dom4j.Element;

import java.io.IOException;

/*
 * Reads and writes an event's tables, within its file (see OccasionXml):
 *
 *   <tables>
 *     <table id="1" number="1" shape="round" places="10" x="300" y="200" rotation="0"/>
 *     <table id="2" shape="rectangle" sideplaces="6" endplaces="0"
 *            x="600" y="100" rotation="0" head="true" firstseat="3" disabled="7 12"/>
 *     <table id="3" name="Smith family" shape="round" places="8" x="300" y="500" rotation="0"/>
 *     <table id="4" number="1" shape="pew" places="10" x="300" y="900" rotation="0" open="3 4 5 6 7 8 9 10"/>
 *   </tables>
 *
 * The id never changes. name is only there for a table with a name of its
 * own; the rest are called "Head table" or by their number.
 *
 * Sizes and positions are whole centimetres, rotation whole degrees.
 * firstseat, disabled and open (open-access seats; the rest are reserved)
 * name chairs by counting clockwise from 1 at the shape's starting chair (the
 * top of a round table, the left end of the far side of a rectangular one,
 * the left end of a pew); they are left out when there is nothing to say.
 * Pews and tables are numbered each on their own. An altar (shape="altar",
 * length="4" seats along its front) has no chairs and no number.
 *
 * An oval table has box="8 4" (length and width in seats), a polygon
 * sides="6" perside="2". A table of any other shape is a walk round its
 * edge (shape="walk"), its steps, turns and arcs within it (see WalkXml).
 * A composed table (shape="composed") is the venue's tables pushed
 * together, a <tables size="2 1" count="5" at="0 0" going="right"
 * bare="near"/> for each line of them (see ComposedTable), and
 * seated="holes" if the edges round its holes have seats. Ovals, polygons,
 * walks and composed tables number their chairs in the order walked.
 * Tables pushed together and combined, each still a table of its own,
 * share a combined="n" (see Plan.combine); the chairs they block are
 * among the disabled. The whole has one number, kept by one of them; the
 * others go by their names, name-given="true" on a name given on
 * combining rather than typed.
 */
public class PlanXml
{
	public static void write(Plan plan, Element parent)
	{
		Element root = parent.addElement("tables");
		for (Table t : plan.tables())
		{
			Element e = root.addElement("table")
					.addAttribute("id", Integer.toString(t.getId()));
			if (t.getNumber() > 0) e.addAttribute("number", Integer.toString(t.getNumber()));
			if (t.hasName()) e.addAttribute("name", t.getName());
			if (t.isNameGiven()) e.addAttribute("name-given", "true");
			e.addAttribute("shape", t.shape());
			if (t instanceof RoundTable || t instanceof Pew)
			{
				e.addAttribute("places", Integer.toString(t.places()));
			} else if (t instanceof Altar altar)
			{
				e.addAttribute("length", Integer.toString(altar.getLength()));
			} else if (t instanceof OvalTable oval)
			{
				e.addAttribute("box", oval.getLength() + " " + oval.getWidth());
			} else if (t instanceof PolygonTable polygon)
			{
				e.addAttribute("sides", Integer.toString(polygon.getSides()));
				e.addAttribute("perside", Integer.toString(polygon.getPerSide()));
			} else if (t instanceof RectTable rect)
			{
				e.addAttribute("sideplaces", Integer.toString(rect.getSidePlaces()));
				e.addAttribute("endplaces", Integer.toString(rect.getEndPlaces()));
			}
			e.addAttribute("x", whole(t.getX()));
			e.addAttribute("y", whole(t.getY()));
			e.addAttribute("rotation", whole(t.getRotation()));
			if (t.isHead()) e.addAttribute("head", "true");
			if (t.getGroup() != 0) e.addAttribute("combined", Integer.toString(t.getGroup()));
			if (t.getFirstSeat() != 0)
				e.addAttribute("firstseat", Integer.toString(t.getFirstSeat() + 1));
			if (!t.getDisabledSeats().isEmpty())
				e.addAttribute("disabled", String.join(" ",
						t.getDisabledSeats().stream().map(c -> Integer.toString(c + 1)).toList()));
			if (!t.getOpenSeats().isEmpty())
				e.addAttribute("open", String.join(" ",
						t.getOpenSeats().stream().map(c -> Integer.toString(c + 1)).toList()));
			// the other shapes make their walk from their size, or their tables
			if (t instanceof WalkTable walked && WalkTable.SHAPE.equals(t.shape())) WalkXml.write(walked.getWalk(), e);
			if (t instanceof ComposedTable composed) writePieces(composed, e);
		}
	}

	private static void writePieces(ComposedTable t, Element e)
	{
		if (t.seatsHoles()) e.addAttribute("seated", "holes");
		for (ComposedTable.Piece p : t.pieces())
		{
			Element line = e.addElement("tables").addAttribute("size", p.width() + " " + p.depth());
			if (p.count() > 1) line.addAttribute("count", Integer.toString(p.count()));
			line.addAttribute("at", p.x() + " " + p.y());
			if (p.down()) line.addAttribute("going", "down");
			if (!p.bare().isEmpty())
				line.addAttribute("bare", String.join(" ", java.util.List.of("far", "near", "left", "right").stream()
						.filter(p.bare()::contains).toList()));
		}
	}

	public static Plan read(Element root) throws IOException
	{
		Plan plan = new Plan();
		for (Element e : root == null ? java.util.List.<Element>of() : root.elements("table"))
			plan.add(table(e, number(e, "id", 0)));
		plan.numberUnnumbered();
		return plan;
	}

	// one <table>, given this id (a table design's has none of its own)
	public static Table table(Element e, int id) throws IOException
	{
		String name = e.attributeValue("name", "");
		String shape = e.attributeValue("shape", RoundTable.SHAPE);
		Table t = switch (shape)
		{
			case RoundTable.SHAPE -> new RoundTable(id, name, number(e, "places", 8));
			case RectTable.SHAPE -> new RectTable(id, name,
					number(e, "sideplaces", 4), number(e, "endplaces", 0));
			case Pew.SHAPE -> new Pew(id, name, number(e, "places", 8));
			case Altar.SHAPE -> new Altar(id, name, number(e, "length", 4));
			case WalkTable.SHAPE -> new WalkTable(id, name, WalkXml.read(e, "Table " + id));
			case OvalTable.SHAPE -> oval(id, name, e);
			case PolygonTable.SHAPE -> polygon(id, name, number(e, "sides", 6), number(e, "perside", 1));
			case ComposedTable.SHAPE -> composed(id, name, e);
			default -> throw new IOException("Table " + id + " has an unknown shape: " + shape);
		};
		t.setNumber(number(e, "number", 0));
		t.moveTo(number(e, "x", 0), number(e, "y", 0));
		t.setRotation(number(e, "rotation", 0));
		t.markHead("true".equals(e.attributeValue("head")));
		if (t.combines()) t.setGroup(number(e, "combined", 0));
		if ("true".equals(e.attributeValue("name-given")) && t.hasName()) t.giveName(t.getName());
		int first = number(e, "firstseat", 1);
		if (first >= 1 && first <= t.places()) t.setFirstSeat(first - 1);
		for (String chair : e.attributeValue("disabled", "").trim().split("\\s+"))
		{
			if (chair.isEmpty()) continue;
			int c = chairNumber(chair, t);
			t.setDisabled(c - 1, true);
		}
		t.setAllOpen(false);  // as the file says, not as the shape starts
		for (String chair : e.attributeValue("open", "").trim().split("\\s+"))
		{
			if (chair.isEmpty()) continue;
			t.setOpen(chairNumber(chair, t) - 1, true);
		}
		return t;
	}

	// <tables size="2 1" count="5" at="0 0" going="right" bare="near"/> for each line of tables
	private static Table composed(int id, String name, Element e) throws IOException
	{
		String what = "Table " + id;
		java.util.List<ComposedTable.Piece> pieces = new java.util.ArrayList<>();
		for (Element p : e.elements())
		{
			if (!p.getName().equals("tables"))
				throw new IOException(what + ": a composed table is made of <tables>, not <" + p.getName() + ">");
			int[] size = pair(p, "size", null, what);
			int[] at = pair(p, "at", "0 0", what);
			String going = p.attributeValue("going", "right").trim();
			if (!going.equals("right") && !going.equals("down"))
				throw new IOException(what + ": tables go right or down, not \"" + going + "\"");
			java.util.Set<String> bare = new java.util.HashSet<>();
			for (String side : p.attributeValue("bare", "").trim().split("\\s+"))
				if (!side.isEmpty()) bare.add(side);
			int count = number(p, "count", 1);
			pieces.add(shaped(id, () -> new ComposedTable.Piece(size[0], size[1], count, at[0], at[1],
					going.equals("down"), bare)));
		}
		String seated = e.attributeValue("seated", "").trim();
		if (!seated.isEmpty() && !seated.equals("holes"))
			throw new IOException(what + ": seated=\"holes\" is the only choice for a composed table");
		return shaped(id, () -> new ComposedTable(id, name, pieces, seated.equals("holes")));
	}

	// "2 1": two whole numbers
	private static int[] pair(Element e, String attribute, String otherwise, String what) throws IOException
	{
		String value = e.attributeValue(attribute, otherwise);
		if (value == null) throw new IOException(what + ": <" + e.getName() + "> needs " + attribute + "=\"…\"");
		String[] parts = value.trim().split("\\s+");
		try
		{
			if (parts.length == 2) return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
		} catch (NumberFormatException ignored)
		{
		}
		throw new IOException(what + ": " + attribute + "=\"" + value + "\" should be two whole numbers");
	}

	private static Table oval(int id, String name, Element e) throws IOException
	{
		String[] box = e.attributeValue("box", "4 2").trim().split("\\s+");
		if (box.length != 2) throw new IOException("Table " + id + " needs box=\"length width\"");
		return shaped(id, () -> new OvalTable(id, name,
				(int) Math.round(Double.parseDouble(box[0])), (int) Math.round(Double.parseDouble(box[1]))));
	}

	private static Table polygon(int id, String name, int sides, int perSide) throws IOException
	{
		return shaped(id, () -> new PolygonTable(id, name, sides, perSide));
	}

	// a shape that cannot be made as the file says (or a box that is not
	// numbers) is the file's fault
	private static <T> T shaped(int id, java.util.function.Supplier<T> make) throws IOException
	{
		try
		{
			return make.get();
		} catch (IllegalArgumentException ex)
		{
			throw new IOException("Table " + id + ": " + ex.getMessage());
		}
	}

	private static String whole(double value)
	{
		return Long.toString(Math.round(value));
	}

	private static int chairNumber(String value, Table t) throws IOException
	{
		try
		{
			int c = Integer.parseInt(value);
			if (c >= 1 && c <= t.places()) return c;
		} catch (NumberFormatException ignored)
		{
		}
		throw new IOException("Table " + t.getId() + " has no chair " + value + " to disable");
	}

	static int number(Element e, String attribute, int otherwise) throws IOException
	{
		String value = e.attributeValue(attribute);
		if (value == null || value.isBlank()) return otherwise;
		try
		{
			return (int) Math.round(Double.parseDouble(value.trim()));
		} catch (NumberFormatException ex)
		{
			throw new IOException("Not a number: " + attribute + "=\"" + value + "\"");
		}
	}
}
