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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/*
 * An occasion is a folder. occasion.xml holds its name and date, the guest
 * list (see GuestXml) and the files of its events in order:
 *
 *   <occasion name="Lydia and Isaac's Wedding" date="2026-05-12"
 *             from="Lydia and Isaac" reply="lydia@example.com" countrycode="44">
 *     <event file="wedding-breakfast.xml"/>
 *     <event file="evening-reception.xml"/>
 *     <guests> ... </guests>
 *     <templates>
 *       <template name="Invitation" subject="{occasion}: an invitation">
 *         <line t="Dear {leadfirst},"/><line t=""/> ...
 *       </template>
 *     </templates>
 *     <sent party="3" when="2026-11-08T16:05" what="Invitation" how="email" event=""/>
 *   </occasion>
 *
 * from, reply and countrycode are for messages to the guests; a template's
 * text is kept a line at a time; sent records each message prepared. The
 * occasion's emblem, if it has one, is the SVG file named by emblem="emblem.svg"
 * beside occasion.xml.
 *
 * Each event's file holds where and when it is, the ids of the guests it
 * invites, its tables (see PlanXml) and who sits where, by guest id, table id and chair (counted from 1 as in the
 * tables):
 *
 *   <event name="Wedding Breakfast" type="seated" place="The Old Barn, Lichfield" time="2pm">
 *     <invited guests="1 2 3 12"/>
 *     <tables> ... </tables>
 *     <seating>
 *       <seat guest="12" table="3" chair="5"/>
 *     </seating>
 *     <menu>
 *       <course name="Main">
 *         <dish id="3" name="Risotto" suits="v vg"/>
 *       </course>
 *     </menu>
 *     <choices>
 *       <choice guest="12" dishes="1 3 6" note="leaving before dessert"/>
 *     </choices>
 *   </event>
 *
 * An event's date is only there if it differs from the occasion's. A
 * guest's choice names one dish of each course they have chosen for. type is
 * seated, or unreserved for a party where no one has a seat, which may have
 * a capacity="150".
 */
public class OccasionXml
{
	public static final String FILE = "occasion.xml";
	public static final String EMBLEM = "emblem.svg";

	public static boolean isOccasion(Path folder)
	{
		return Files.isRegularFile(folder.resolve(FILE));
	}

	public static void save(Occasion occasion, Path folder) throws IOException
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("occasion").addAttribute("name", occasion.getName());
		if (occasion.getDate() != null) root.addAttribute("date", occasion.getDate().toString());
		if (!occasion.getSender().isEmpty()) root.addAttribute("from", occasion.getSender());
		if (!occasion.getReplyTo().isEmpty()) root.addAttribute("reply", occasion.getReplyTo());
		root.addAttribute("countrycode", occasion.getCountryCode());
		if (occasion.getEmblem() != null) root.addAttribute("emblem", EMBLEM);
		for (Event e : occasion.events())
			root.addElement("event").addAttribute("file", e.getFile());
		GuestXml.write(occasion.getGuests(), root);
		Element templates = root.addElement("templates");
		for (MessageTemplate t : occasion.templates())
		{
			Element te = templates.addElement("template").addAttribute("name", t.getName())
					.addAttribute("subject", t.getSubject());
			for (String line : t.getBody().split("\n", -1))
				te.addElement("line").addAttribute("t", line);  // an attribute keeps leading spaces
		}
		for (Messages.Sent s : occasion.sent())
		{
			root.addElement("sent").addAttribute("party", Integer.toString(s.party().getId()))
					.addAttribute("when", s.when().toString()).addAttribute("what", s.what())
					.addAttribute("how", s.how()).addAttribute("event", s.event());
		}
		Files.createDirectories(folder);
		write(doc, folder.resolve(FILE));
		if (occasion.getEmblem() != null)
			Files.writeString(folder.resolve(EMBLEM), occasion.getEmblem(), java.nio.charset.StandardCharsets.UTF_8);
		else Files.deleteIfExists(folder.resolve(EMBLEM));
		for (Event e : occasion.events())
			write(event(e), folder.resolve(e.getFile()));
	}

	private static Document event(Event event)
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("event").addAttribute("name", event.getName())
				.addAttribute("type", event.getKind().code());
		if (event.getCapacity() > 0) root.addAttribute("capacity", Integer.toString(event.getCapacity()));
		if (!event.getPlace().isEmpty()) root.addAttribute("place", event.getPlace());
		if (event.getDate() != null) root.addAttribute("date", event.getDate().toString());
		if (!event.getTime().isEmpty()) root.addAttribute("time", event.getTime());
		root.addElement("invited").addAttribute("guests",
				String.join(" ", event.invited().stream().map(g -> Integer.toString(g.getId())).toList()));
		PlanXml.write(event.getPlan(), root);
		Element seating = root.addElement("seating");
		for (var s : event.getSeating().all().entrySet())
		{
			seating.addElement("seat")
					.addAttribute("guest", Integer.toString(s.getKey().getId()))
					.addAttribute("table", Integer.toString(s.getValue().tableId()))
					.addAttribute("chair", Integer.toString(s.getValue().chair() + 1));
		}
		if (event.hasMenu())
		{
			Element menu = root.addElement("menu");
			for (Menu.Course c : event.getMenu().courses())
			{
				Element ce = menu.addElement("course").addAttribute("name", c.getName());
				for (Menu.Dish d : c.dishes())
				{
					Element de = ce.addElement("dish").addAttribute("id", Integer.toString(d.getId()))
							.addAttribute("name", d.getName());
					if (!d.suits().isEmpty()) de.addAttribute("suits", Diet.codes(d.suits()));
				}
			}
		}
		Element choices = root.addElement("choices");
		java.util.Set<Guest> who = new java.util.LinkedHashSet<>(event.choices().keySet());
		who.addAll(event.notes().keySet());
		for (Guest g : who)
		{
			Element ce = choices.addElement("choice").addAttribute("guest", Integer.toString(g.getId()));
			String dishes = String.join(" ", event.getMenu().courses().stream().map(c -> event.choice(g, c))
					.filter(d -> d != null).map(d -> Integer.toString(d.getId())).toList());
			if (!dishes.isEmpty()) ce.addAttribute("dishes", dishes);
			if (!event.noteFor(g).isEmpty()) ce.addAttribute("note", event.noteFor(g));
		}
		return doc;
	}

	public static Occasion load(Path folder) throws IOException
	{
		Element root = read(folder.resolve(FILE)).getRootElement();
		GuestList guests = GuestXml.read(root.element("guests"));
		Occasion occasion = new Occasion(root.attributeValue("name", folder.getFileName().toString()), guests);
		occasion.setDate(date(root, FILE));
		occasion.setSender(root.attributeValue("from"));
		occasion.setReplyTo(root.attributeValue("reply"));
		occasion.setCountryCode(root.attributeValue("countrycode"));
		String emblem = root.attributeValue("emblem");
		if (emblem != null && Files.isRegularFile(folder.resolve(emblem)))
			occasion.setEmblem(Files.readString(folder.resolve(emblem), java.nio.charset.StandardCharsets.UTF_8));
		Element te = root.element("templates");
		if (te != null)
		{
			occasion.templates().clear();
			for (Element t : te.elements("template"))
			{
				String body = String.join("\n", t.elements("line").stream().map(l -> l.attributeValue("t", "")).toList());
				occasion.templates().add(new MessageTemplate(t.attributeValue("name"), t.attributeValue("subject"), body));
			}
		}
		for (Element s : root.elements("sent"))
		{
			int id = PlanXml.number(s, "party", 0);
			guests.parties().stream().filter(p -> p.getId() == id).findFirst().ifPresent(p ->
			{
				try
				{
					occasion.sent().add(new Messages.Sent(p, java.time.LocalDateTime.parse(s.attributeValue("when")),
							s.attributeValue("what", ""), s.attributeValue("how", ""), s.attributeValue("event", "")));
				} catch (java.time.format.DateTimeParseException ignored)
				{
				}
			});
		}
		for (Element ee : root.elements("event"))
		{
			String file = ee.attributeValue("file", "");
			if (file.isBlank()) continue;
			occasion.addEvent(loadEvent(folder.resolve(file), file, guests));
		}
		occasion.settleAll();
		return occasion;
	}

	private static Event loadEvent(Path path, String file, GuestList guests) throws IOException
	{
		Element root = read(path).getRootElement();
		Plan plan = PlanXml.read(root.element("tables"));
		Seating seating = new Seating();
		Element se = root.element("seating");
		if (se != null)
		{
			for (Element s : se.elements("seat"))
			{
				int id = PlanXml.number(s, "guest", 0);
				guests.guests().stream().filter(g -> g.getId() == id).findFirst().ifPresent(g ->
						seating.seat(g, new Seat(numberOr(s, "table"), numberOr(s, "chair") - 1)));
			}
		}
		Event event = new Event(root.attributeValue("name", "Event"), file, plan, seating);
		event.setKind(Event.Kind.fromCode(root.attributeValue("type", "seated")));
		event.setCapacity(PlanXml.number(root, "capacity", 0));
		Element inv = root.element("invited");
		if (inv != null)
		{
			for (String id : inv.attributeValue("guests", "").trim().split("\\s+"))
			{
				if (!id.matches("\\d+")) continue;
				int n = Integer.parseInt(id);
				guests.guests().stream().filter(g -> g.getId() == n).findFirst().ifPresent(event::invite);
			}
		}
		Element me = root.element("menu");
		if (me != null)
		{
			for (Element ce : me.elements("course"))
			{
				Menu.Course c = new Menu.Course(ce.attributeValue("name"));
				for (Element de : ce.elements("dish"))
				{
					Menu.Dish d = new Menu.Dish(PlanXml.number(de, "id", 0), de.attributeValue("name"));
					d.suits().addAll(Diet.fromCodes(de.attributeValue("suits")));
					c.dishes().add(d);
				}
				event.getMenu().courses().add(c);
			}
		}
		Element ch = root.element("choices");
		if (ch != null)
		{
			for (Element ce : ch.elements("choice"))
			{
				int id = PlanXml.number(ce, "guest", 0);
				Guest g = guests.guests().stream().filter(x -> x.getId() == id).findFirst().orElse(null);
				if (g == null) continue;
				for (String d : ce.attributeValue("dishes", "").trim().split("\\s+"))
				{
					if (!d.matches("\\d+")) continue;
					event.getMenu().dish(Integer.parseInt(d)).ifPresent(dish ->
							event.getMenu().courseOf(dish).ifPresent(c -> event.choose(g, c, dish)));
				}
				event.setNote(g, ce.attributeValue("note"));
			}
		}
		event.tidy(guests);
		event.setPlace(root.attributeValue("place"));
		event.setDate(date(root, file));
		event.setTime(root.attributeValue("time"));
		return event;
	}

	private static int numberOr(Element e, String attribute)
	{
		try
		{
			return PlanXml.number(e, attribute, 0);
		} catch (IOException ex)
		{
			return 0;  // the seat is then dropped by tidy
		}
	}

	private static LocalDate date(Element e, String file) throws IOException
	{
		String value = e.attributeValue("date");
		if (value == null || value.isBlank()) return null;
		try
		{
			return LocalDate.parse(value.trim());
		} catch (DateTimeParseException ex)
		{
			throw new IOException(file + ": not a date: " + value);
		}
	}

	private static Document read(Path file) throws IOException
	{
		if (!Files.isRegularFile(file)) throw new IOException(file + " is missing");
		try
		{
			return new SAXReader().read(file.toFile());
		} catch (DocumentException e)
		{
			throw new IOException(file + " cannot be read: " + e.getMessage(), e);
		}
	}

	private static void write(Document doc, Path file) throws IOException
	{
		try (OutputStream out = Files.newOutputStream(file))
		{
			XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
			writer.write(doc);
			writer.flush();
		}
	}
}
