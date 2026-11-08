package org.lerot.myreceptionplanner.model;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 * Reads and writes guests.xml:
 *
 *   <guests>
 *     <party id="1" name="Smith family" colour="#1F77B4">
 *       <guest id="1" name="Ann Smith" gender="f" table="2" chair="4"/>
 *       <guest id="2" gender="u"/>
 *     </party>
 *     <guest id="3" name="Father Brown" gender="m"/>
 *   </guests>
 *
 * gender is f, m or u (not known). A guest with no name is one of the party
 * not yet named. table and chair give their seat, the chair counted as in
 * tables.xml (clockwise from 1 at the table's starting chair); a guest
 * outside any party sits directly under guests.
 */
public class GuestXml
{
	public static final String FILE = "guests.xml";

	public static void save(GuestList list, Path file) throws IOException
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("guests");
		for (Party p : list.parties())
		{
			Element pe = root.addElement("party")
					.addAttribute("id", Integer.toString(p.getId()))
					.addAttribute("name", p.getName())
					.addAttribute("colour", String.format("#%06X", p.getColour().getRGB() & 0xFFFFFF));
			for (Guest g : list.guests())
			{
				if (g.getParty() == p) addGuest(pe, g);
			}
		}
		for (Guest g : list.guests())
		{
			if (g.getParty() == null) addGuest(root, g);
		}
		Files.createDirectories(file.toAbsolutePath().getParent());
		try (OutputStream out = Files.newOutputStream(file))
		{
			XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
			writer.write(doc);
			writer.flush();
		}
	}

	private static void addGuest(Element parent, Guest g)
	{
		Element e = parent.addElement("guest").addAttribute("id", Integer.toString(g.getId()));
		if (g.isNamed()) e.addAttribute("name", g.getName());
		e.addAttribute("gender", g.getGender().code());
		if (g.isSeated())
		{
			e.addAttribute("table", Integer.toString(g.getSeat().tableId()));
			e.addAttribute("chair", Integer.toString(g.getSeat().chair() + 1));
		}
	}

	public static GuestList load(Path file) throws IOException
	{
		Document doc;
		try
		{
			doc = new SAXReader().read(file.toFile());
		} catch (DocumentException e)
		{
			throw new IOException(file + " is not a readable guest list: " + e.getMessage(), e);
		}
		GuestList list = new GuestList();
		Element root = doc.getRootElement();
		for (Element pe : root.elements("party"))
		{
			int id = PlanXml.number(pe, "id", 0);
			Color colour;
			try
			{
				colour = Color.decode(pe.attributeValue("colour", "#8A8A8E"));
			} catch (NumberFormatException ex)
			{
				throw new IOException("Not a colour: " + pe.attributeValue("colour"));
			}
			Party p = new Party(id, pe.attributeValue("name", "Party " + id), colour);
			list.addParty(p);
			for (Element ge : pe.elements("guest"))
				list.addGuest(guest(ge, p));
		}
		for (Element ge : root.elements("guest"))
			list.addGuest(guest(ge, null));
		return list;
	}

	private static Guest guest(Element e, Party party) throws IOException
	{
		Guest g = new Guest(PlanXml.number(e, "id", 0), e.attributeValue("name", ""),
				Gender.fromCode(e.attributeValue("gender", "u")), party);
		int table = PlanXml.number(e, "table", 0);
		int chair = PlanXml.number(e, "chair", 0);
		if (table > 0 && chair > 0) g.setSeat(new Seat(table, chair - 1));
		return g;
	}
}
