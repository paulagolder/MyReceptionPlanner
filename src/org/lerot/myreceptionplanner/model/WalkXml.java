package org.lerot.myreceptionplanner.model;

import org.dom4j.Element;

import java.io.IOException;

/*
 * A walk in XML, as the steps, turns and arcs within an element (a table's,
 * in an event's file), with a <hole> for each hole:
 *
 *   <table id="5" shape="walk" x="600" y="200" rotation="0">
 *     <step length="20" seats="20"/>
 *     <turn by="90"/>
 *     <arc by="180" radius="1" seats="3"/>
 *     <hole at="0 1.68">
 *       <arc by="360" radius="1.5"/>
 *     </hole>
 *   </table>
 *
 * Lengths are in seat units, turns in degrees. A step's seats are 0 if left
 * out, and its length the same as its seats; an arc's radius, if left out,
 * makes it as long as its seats.
 */
public class WalkXml
{
	public static void write(Walk walk, Element parent)
	{
		for (int i = 0; i < walk.loops().size(); i++)
		{
			Walk.Loop loop = walk.loops().get(i);
			Element into = i == 0 ? parent
					: parent.addElement("hole").addAttribute("at", Walk.number(loop.x()) + " " + Walk.number(loop.y()));
			for (Walk.Move m : loop.moves())
			{
				switch (m.kind())
				{
					case STEP -> seats(into.addElement("step").addAttribute("length", Walk.number(m.length())), m);
					case TURN -> into.addElement("turn").addAttribute("by", Walk.number(m.by()));
					case ARC -> seats(into.addElement("arc").addAttribute("by", Walk.number(m.by()))
							.addAttribute("radius", Walk.number(m.radius())), m);
				}
			}
		}
	}

	private static void seats(Element e, Walk.Move m)
	{
		if (m.seats() > 0) e.addAttribute("seats", Integer.toString(m.seats()));
	}

	// what describes it goes in the message if it is wrong ("Table 5")
	public static Walk read(Element parent, String what) throws IOException
	{
		Walk walk = new Walk();
		try
		{
			moves(parent, walk, what, true);
			walk.trace();  // that it comes back to its start
		} catch (IllegalArgumentException e)
		{
			throw new IOException(what + ": " + e.getMessage());
		}
		return walk;
	}

	private static void moves(Element parent, Walk walk, String what, boolean outside) throws IOException
	{
		for (Element e : parent.elements())
		{
			switch (e.getName())
			{
				case "step" ->
				{
					int seats = (int) decimal(e, "seats", 0, what);
					walk.step(decimal(e, "length", seats, what), seats);
				}
				case "turn" -> walk.turn(required(e, "by", what));
				case "arc" ->
				{
					double by = required(e, "by", what);
					int seats = (int) decimal(e, "seats", 0, what);
					if (e.attributeValue("radius") == null) walk.arc(by, seats);
					else walk.arc(by, decimal(e, "radius", 0, what), seats);
				}
				case "hole" ->
				{
					if (!outside) throw new IOException(what + ": a hole cannot be inside a hole");
					String[] at = e.attributeValue("at", "").trim().split("\\s+");
					if (at.length != 2) throw new IOException(what + ": a hole needs at=\"x y\"");
					walk.hole(parse(at[0], "at", what), parse(at[1], "at", what));
					moves(e, walk, what, false);
				}
				default -> throw new IOException(what + ": a walk has no <" + e.getName() + ">");
			}
		}
	}

	private static double required(Element e, String attribute, String what) throws IOException
	{
		if (e.attributeValue(attribute) == null)
			throw new IOException(what + ": <" + e.getName() + "> needs " + attribute + "=\"…\"");
		return decimal(e, attribute, 0, what);
	}

	private static double decimal(Element e, String attribute, double otherwise, String what) throws IOException
	{
		String value = e.attributeValue(attribute);
		return value == null || value.isBlank() ? otherwise : parse(value, attribute, what);
	}

	private static double parse(String value, String attribute, String what) throws IOException
	{
		try
		{
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException ex)
		{
			throw new IOException(what + ": not a number: " + attribute + "=\"" + value + "\"");
		}
	}
}
