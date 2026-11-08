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
 *     <party id="1" name="Smith family" colour="#1F77B4" lead="1">
 *       <guest id="1" name="Ann Smith" gender="f" table="2" chair="4">
 *         <contact source="MyContacts" phone="01234 567890" mobile="07700 900123"
 *                  whatsapp="07700 900123" email="ann@example.com">
 *           <address><line>1 High Street</line><line>Lichfield</line></address>
 *         </contact>
 *       </guest>
 *       <guest id="2" gender="u" plus="1"/>
 *     </party>
 *     <guest id="3" name="Father Brown" gender="m"/>
 *   </guests>
 *
 * gender is f, m or u (not known). lead is the id of the party's lead guest;
 * a party without a name of its own goes by the lead's.
 * A guest with no name is a place kept for one of the party, shown as the
 * lead's name +plus. table and chair give their seat, the chair counted as in
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
			Element pe = root.addElement("party").addAttribute("id", Integer.toString(p.getId()));
			if (p.hasOwnName()) pe.addAttribute("name", p.getName());
			pe.addAttribute("colour", String.format("#%06X", p.getColour().getRGB() & 0xFFFFFF));
			if (p.getLead() != null) pe.addAttribute("lead", Integer.toString(p.getLead().getId()));
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
		if (g.isPlaceholder()) e.addAttribute("plus", Integer.toString(g.getPlus()));
		if (g.isSeated())
		{
			e.addAttribute("table", Integer.toString(g.getSeat().tableId()));
			e.addAttribute("chair", Integer.toString(g.getSeat().chair() + 1));
		}
		Contact c = g.getContact();
		if (!c.isEmpty())
		{
			Element ce = e.addElement("contact");
			if (!c.getSource().isEmpty()) ce.addAttribute("source", c.getSource());
			if (!c.getPhone().isEmpty()) ce.addAttribute("phone", c.getPhone());
			if (!c.getMobile().isEmpty()) ce.addAttribute("mobile", c.getMobile());
			if (!c.getWhatsApp().isEmpty()) ce.addAttribute("whatsapp", c.getWhatsApp());
			if (!c.getEmail().isEmpty()) ce.addAttribute("email", c.getEmail());
			if (!c.getAddress().isEmpty())
			{
				Element address = ce.addElement("address");
				for (String line : c.getAddress().split("\n"))
					address.addElement("line").setText(line.strip());
			}
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
			Party p = new Party(id, pe.attributeValue("name", ""), colour);
			list.addParty(p);
			int leadId = PlanXml.number(pe, "lead", 0);
			for (Element ge : pe.elements("guest"))
			{
				Guest g = guest(ge, p);
				list.addGuest(g);
				if (g.getId() == leadId && g.isNamed()) p.setLead(g);
			}
		}
		for (Element ge : root.elements("guest"))
			list.addGuest(guest(ge, null));
		list.fillInParties();
		return list;
	}

	private static Guest guest(Element e, Party party) throws IOException
	{
		Guest g = new Guest(PlanXml.number(e, "id", 0), e.attributeValue("name", ""),
				Gender.fromCode(e.attributeValue("gender", "u")), party);
		int table = PlanXml.number(e, "table", 0);
		int chair = PlanXml.number(e, "chair", 0);
		if (table > 0 && chair > 0) g.setSeat(new Seat(table, chair - 1));
		if (g.isPlaceholder()) g.setPlus(PlanXml.number(e, "plus", 0));
		Element ce = e.element("contact");
		if (ce != null)
		{
			Contact c = g.getContact();
			c.setSource(ce.attributeValue("source"));
			c.setPhone(ce.attributeValue("phone"));
			c.setMobile(ce.attributeValue("mobile"));
			c.setWhatsApp(ce.attributeValue("whatsapp"));
			c.setEmail(ce.attributeValue("email"));
			Element address = ce.element("address");
			if (address != null)
				c.setAddress(String.join("\n", address.elements("line").stream().map(Element::getTextTrim).toList()));
		}
		return g;
	}
}
