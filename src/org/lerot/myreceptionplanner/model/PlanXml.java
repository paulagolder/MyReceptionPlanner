package org.lerot.myreceptionplanner.model;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 * Reads and writes tables.xml:
 *
 *   <tables>
 *     <table id="1" number="1" shape="round" places="10" x="300" y="200" rotation="0"/>
 *     <table id="2" shape="rectangle" sideplaces="6" endplaces="0"
 *            x="600" y="100" rotation="0" head="true" firstseat="3" disabled="7 12"/>
 *     <table id="3" name="Smith family" shape="round" places="8" x="300" y="500" rotation="0"/>
 *   </tables>
 *
 * The id never changes. name is only there for a table with a name of its
 * own; the rest are called "Head table" or by their number. Files from
 * before then named every table "Table <id>"; such a name is read as none,
 * and as they have no numbers, the tables are numbered by where they stand.
 *
 * Sizes and positions are whole centimetres, rotation whole degrees.
 * firstseat and disabled name chairs by counting clockwise from 1 at the
 * shape's starting chair (the top of a round table, the left end of the far
 * side of a rectangular one); they are left out when there is nothing to say.
 */
public class PlanXml
{
	public static final String FILE = "tables.xml";

	public static void save(Plan plan, Path file) throws IOException
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("tables");
		for (Table t : plan.tables())
		{
			Element e = root.addElement("table")
					.addAttribute("id", Integer.toString(t.getId()));
			if (t.getNumber() > 0) e.addAttribute("number", Integer.toString(t.getNumber()));
			if (t.hasName()) e.addAttribute("name", t.getName());
			e.addAttribute("shape", t.shape());
			if (t instanceof RoundTable round)
			{
				e.addAttribute("places", Integer.toString(round.places()));
			} else if (t instanceof RectTable rect)
			{
				e.addAttribute("sideplaces", Integer.toString(rect.getSidePlaces()));
				e.addAttribute("endplaces", Integer.toString(rect.getEndPlaces()));
			}
			e.addAttribute("x", whole(t.getX()));
			e.addAttribute("y", whole(t.getY()));
			e.addAttribute("rotation", whole(t.getRotation()));
			if (t.isHead()) e.addAttribute("head", "true");
			if (t.getFirstSeat() != 0)
				e.addAttribute("firstseat", Integer.toString(t.getFirstSeat() + 1));
			if (!t.getDisabledSeats().isEmpty())
				e.addAttribute("disabled", String.join(" ",
						t.getDisabledSeats().stream().map(c -> Integer.toString(c + 1)).toList()));
		}
		Files.createDirectories(file.toAbsolutePath().getParent());
		try (OutputStream out = Files.newOutputStream(file))
		{
			XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
			writer.write(doc);
			writer.flush();
		}
	}

	public static Plan load(Path file) throws IOException
	{
		Document doc;
		try
		{
			doc = new SAXReader().read(file.toFile());
		} catch (DocumentException e)
		{
			throw new IOException(file + " is not a readable tables file: " + e.getMessage(), e);
		}
		Plan plan = new Plan();
		for (Element e : doc.getRootElement().elements("table"))
		{
			int id = number(e, "id", 0);
			String name = e.attributeValue("name", "");
			if (name.trim().matches("Table \\d+")) name = "";  // the old default
			String shape = e.attributeValue("shape", RoundTable.SHAPE);
			Table t = switch (shape)
			{
				case RoundTable.SHAPE -> new RoundTable(id, name, number(e, "places", 8));
				case RectTable.SHAPE -> new RectTable(id, name,
						number(e, "sideplaces", 4), number(e, "endplaces", 0));
				default -> throw new IOException("Table " + id + " has an unknown shape: " + shape);
			};
			t.setNumber(number(e, "number", 0));
			t.moveTo(number(e, "x", 0), number(e, "y", 0));
			t.setRotation(number(e, "rotation", 0));
			t.markHead("true".equals(e.attributeValue("head")));
			int first = number(e, "firstseat", 1);
			if (first >= 1 && first <= t.places()) t.setFirstSeat(first - 1);
			for (String chair : e.attributeValue("disabled", "").trim().split("\\s+"))
			{
				if (chair.isEmpty()) continue;
				int c = chairNumber(chair, t);
				t.setDisabled(c - 1, true);
			}
			plan.add(t);
		}
		if (plan.tables().stream().allMatch(t -> t.getNumber() == 0)) plan.renumber();
		else plan.numberUnnumbered();
		return plan;
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
