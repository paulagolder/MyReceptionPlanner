package org.lerot.myreceptionplanner.model;

import org.dom4j.Element;
import org.lerot.myreceptionplanner.model.tables.PlanXml;

import java.awt.Color;
import java.io.IOException;

/*
 * Reads and writes the occasion's guest list, guests.xml beside occasion.xml
 * (see OccasionXml):
 *
 *   <guests>
 *     <party id="1" name="Smith family" colour="#1F77B4" lead="1">
 *       <guest id="1" title="Mrs" forenames="Ann" family="Smith" gender="f" role="Mother of the bride">
 *         <contact source="MyContacts" phone="01234 567890" mobile="07700 900123"
 *                  whatsapp="07700 900123" email="ann@example.com">
 *           <address><line>1 High Street</line><line>Mayfield</line></address>
 *         </contact>
 *       </guest>
 *       <guest id="2" gender="u" plus="1" diet="vg nf" dietnote="no sesame"/>
 *     </party>
 *     <guest id="3" title="Father" family="Brown" gender="m"/>
 *     <distinct a="3" b="9"/>
 *   </guests>
 *
 * A guest's name is in its parts (see Name), each left out when empty; a
 * file from before then has name="Ann Smith", split as Name.guess does.
 * gender is f, m or u (not known). lead is the id of the party's lead guest;
 * a party without a name of its own goes by the lead's. A guest with no name
 * is a place kept for one of the party, shown as the lead's name +plus. A
 * guest outside any party sits directly under guests. diet gives a guest's
 * dietary needs (see Diet: v vg gf df nf h k) and dietnote any more in
 * words; they hold at every event. role is who the guest is at the occasion
 * (at most 20 characters). distinct records two
 * guests whose names are alike but who are said to be different people.
 */
public class GuestXml
{
	private static final Honorifics HONORIFICS = Honorifics.standard();

	public static void write(GuestList list, org.dom4j.Branch parent)
	{
		Element root = parent.addElement("guests");
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
		for (String pair : list.distinctPairs())
		{
			String[] ab = pair.split(":");
			root.addElement("distinct").addAttribute("a", ab[0]).addAttribute("b", ab[1]);
		}
	}

	private static void addGuest(Element parent, Guest g)
	{
		Element e = parent.addElement("guest").addAttribute("id", Integer.toString(g.getId()));
		Name n = g.getNameParts();
		if (!n.title().isEmpty()) e.addAttribute("title", n.title());
		if (!n.forenames().isEmpty()) e.addAttribute("forenames", n.forenames());
		if (!n.family().isEmpty()) e.addAttribute("family", n.family());
		e.addAttribute("gender", g.getGender().code());
		if (!g.getRole().isEmpty()) e.addAttribute("role", g.getRole());
		if (g.isPlaceholder()) e.addAttribute("plus", Integer.toString(g.getPlus()));
		if (!g.diets().isEmpty()) e.addAttribute("diet", Diet.codes(g.diets()));
		if (!g.getDietNote().isEmpty()) e.addAttribute("dietnote", g.getDietNote());
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

	public static GuestList read(Element root) throws IOException
	{
		GuestList list = new GuestList();
		if (root == null) return list;
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
		for (Element de : root.elements("distinct"))
		{
			int a = PlanXml.number(de, "a", 0), b = PlanXml.number(de, "b", 0);
			boolean known = list.guests().stream().anyMatch(g -> g.getId() == a)
					&& list.guests().stream().anyMatch(g -> g.getId() == b);
			if (known && a != b) list.markDistinct(a, b);
		}
		return list;
	}

	private static Guest guest(Element e, Party party) throws IOException
	{
		Name name = new Name(e.attributeValue("title"), e.attributeValue("forenames"), e.attributeValue("family"));
		if (name.isEmpty() && e.attributeValue("name") != null) name = Name.guess(e.attributeValue("name"), HONORIFICS);
		Guest g = new Guest(PlanXml.number(e, "id", 0), name, Gender.fromCode(e.attributeValue("gender", "u")), party);
		if (g.isPlaceholder()) g.setPlus(PlanXml.number(e, "plus", 0));
		g.setRole(e.attributeValue("role"));
		g.diets().addAll(Diet.fromCodes(e.attributeValue("diet")));
		g.setDietNote(e.attributeValue("dietnote"));
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
