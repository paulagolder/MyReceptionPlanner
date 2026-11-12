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
import java.util.List;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/*
 * An occasion is a folder. occasion.xml holds its name and date, the files of
 * its events in order, and the messages sent:
 *
 *   <occasion name="Janet and John's Wedding" date="2026-05-12"
 *             from="Janet and John" reply="janet@example.com" countrycode="44">
 *     <event file="wedding-breakfast.xml"/>
 *     <event file="evening-reception.xml"/>
 *     <communications>
 *       <sent party="3" when="2026-11-08T16:05" what="Invitation" how="email" event=""/>
 *     </communications>
 *   </occasion>
 *
 * from, reply and countrycode are for messages to the guests; sent records
 * each message prepared. Beside it: guests.xml, the guest list (see
 * GuestXml); email_templates.xml, the message templates; and the emblem, if
 * there is one, the SVG file named by emblem="emblem.svg".
 *
 *   <templates>
 *     <template name="Invitation" subject="{occasion}: an invitation">
 *       <line t="Dear {leadfirst},"/><line t=""/> ...
 *     </template>
 *   </templates>
 *
 * A template's text is kept a line at a time. The templates can be shared:
 * an occasion without its own email_templates.xml takes the one in the folder
 * above it (MyReceptions), if there is one, or else the standard ones.
 *
 * Each event's file holds where and when it is, the ids of the guests it
 * invites, its tables (see PlanXml) and who sits where, by guest id, table id and chair (counted from 1 as in the
 * tables):
 *
 *   <event name="Wedding Breakfast" type="seated" place="The Old Barn, Mayfield" time="2pm">
 *     <invitations decided="1 2" invited="3 12" accepted="4" declined="7"/>
 *     <tables> ... </tables>
 *     <seating>
 *       <seat guest="12" table="3" chair="5"/>
 *     </seating>
 *     <menu>
 *       <note><line t="All our food is sourced locally."/></note>
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
 * a capacity="150"; so may a hall, whose capacity is otherwise its seats. catered="yes" if food is served there (its menu, choices
 * and kitchen lists apply).
 */
public class OccasionXml
{
	public static final String FILE = "occasion.xml";
	public static final String EMBLEM = "emblem.svg";
	public static final String GUESTS = "guests.xml";
	public static final String TEMPLATES = "email_templates.xml";

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
		Element communications = root.addElement("communications");
		for (Messages.Sent s : occasion.sent())
		{
			communications.addElement("sent").addAttribute("party", Integer.toString(s.party().getId()))
					.addAttribute("when", s.when().toString()).addAttribute("what", s.what())
					.addAttribute("how", s.how()).addAttribute("event", s.event());
		}
		Files.createDirectories(folder);
		write(doc, folder.resolve(FILE));
		Document guests = DocumentHelper.createDocument();
		GuestXml.write(occasion.getGuests(), guests);
		write(guests, folder.resolve(GUESTS));
		saveTemplates(occasion.templates(), folder.resolve(TEMPLATES));
		if (occasion.getEmblem() != null)
			Files.writeString(folder.resolve(EMBLEM), occasion.getEmblem(), java.nio.charset.StandardCharsets.UTF_8);
		else Files.deleteIfExists(folder.resolve(EMBLEM));
		for (Event e : occasion.events())
			write(event(e), folder.resolve(e.getFile()));
	}

	// just this event's file, in the occasion's folder
	public static void saveEvent(Event event, Path folder) throws IOException
	{
		write(event(event), folder.resolve(event.getFile()));
	}

	private static Document event(Event event)
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("event").addAttribute("name", event.getName())
				.addAttribute("type", event.getKind().code());
		if (event.getCapacity() > 0) root.addAttribute("capacity", Integer.toString(event.getCapacity()));
		root.addAttribute("catered", event.isCatered() ? "yes" : "no");
		if (!event.getPlace().isEmpty()) root.addAttribute("place", event.getPlace());
		if (event.getDate() != null) root.addAttribute("date", event.getDate().toString());
		if (!event.getTime().isEmpty()) root.addAttribute("time", event.getTime());
		Element inv = root.addElement("invitations");
		for (Invitation state : Invitation.values())
		{
			if (state == Invitation.NONE) continue;
			List<String> ids = event.invitations().entrySet().stream().filter(x -> x.getValue() == state)
					.map(x -> Integer.toString(x.getKey().getId())).toList();
			if (!ids.isEmpty()) inv.addAttribute(state.code(), String.join(" ", ids));
		}
		PlanXml.write(event.getPlan(), root);
		Element seating = root.addElement("seating");
		for (var s : event.getSeating().all().entrySet())
		{
			seating.addElement("seat")
					.addAttribute("guest", Integer.toString(s.getKey().getId()))
					.addAttribute("table", Integer.toString(s.getValue().tableId()))
					.addAttribute("chair", Integer.toString(s.getValue().chair() + 1));
		}
		if (event.hasMenu() || !event.getMenu().getNote().isEmpty()) writeMenu(event.getMenu(), root);
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
		Element guestsRoot = Files.isRegularFile(folder.resolve(GUESTS))
				? read(folder.resolve(GUESTS)).getRootElement() : root.element("guests");  // or as once kept, inside
		GuestList guests = GuestXml.read(guestsRoot);
		Occasion occasion = new Occasion(root.attributeValue("name", folder.getFileName().toString()), guests);
		occasion.setDate(date(root, FILE));
		occasion.setSender(root.attributeValue("from"));
		occasion.setReplyTo(root.attributeValue("reply"));
		occasion.setCountryCode(root.attributeValue("countrycode"));
		String emblem = root.attributeValue("emblem");
		if (emblem != null && Files.isRegularFile(folder.resolve(emblem)))
			occasion.setEmblem(Files.readString(folder.resolve(emblem), java.nio.charset.StandardCharsets.UTF_8));
		List<MessageTemplate> templates = Files.isRegularFile(folder.resolve(TEMPLATES))
				? loadTemplates(folder.resolve(TEMPLATES))
				: root.element("templates") != null ? templates(root.element("templates"))
				: sharedTemplates(folder);
		if (templates != null)
		{
			occasion.templates().clear();
			occasion.templates().addAll(templates);
		}
		occasion.setHonorifics(Honorifics.find(folder));
		Element communications = root.element("communications");
		for (Element s : (communications != null ? communications : root).elements("sent"))
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

	// ---- menus ----

	private static void writeMenu(Menu m, org.dom4j.Branch parent)
	{
		Element menu = parent.addElement("menu");
		if (!m.getNote().isEmpty())
		{
			Element note = menu.addElement("note");
			for (String line : m.getNote().split("\n", -1))
				note.addElement("line").addAttribute("t", line);
		}
		for (Menu.Course c : m.courses())
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

	// into the menu given (empty); nothing if me is null
	private static void readMenu(Element me, Menu into) throws IOException
	{
		if (me == null) return;
		Element note = me.element("note");
		if (note != null)
			into.setNote(String.join("\n", note.elements("line").stream().map(l -> l.attributeValue("t", "")).toList()));
		for (Element ce : me.elements("course"))
		{
			Menu.Course c = new Menu.Course(ce.attributeValue("name"));
			for (Element de : ce.elements("dish"))
			{
				Menu.Dish d = new Menu.Dish(PlanXml.number(de, "id", 0), de.attributeValue("name"));
				d.suits().addAll(Diet.fromCodes(de.attributeValue("suits")));
				c.dishes().add(d);
			}
			into.courses().add(c);
		}
	}

	/*
	 * A menu on its own, to be taken into another event: <menu> with its note
	 * and courses as in an event's file.
	 */
	public static void saveMenu(Menu menu, Path file) throws IOException
	{
		Document doc = DocumentHelper.createDocument();
		writeMenu(menu, doc);
		Files.createDirectories(file.toAbsolutePath().getParent());
		write(doc, file);
	}

	// a menu file, or the menu of an event's file
	public static Menu loadMenu(Path file) throws IOException
	{
		Element root = read(file).getRootElement();
		Element me = root.getName().equals("menu") ? root : root.element("menu");
		if (me == null) throw new IOException(file.getFileName() + " has no menu in it.");
		Menu menu = new Menu();
		readMenu(me, menu);
		return menu;
	}

	// ---- layouts ----

	/*
	 * The tables of an event on their own, to be taken into another:
	 * <layout><tables> ... </tables></layout>, as in an event's file.
	 */
	public static void saveLayout(Plan plan, Path file) throws IOException
	{
		Document doc = DocumentHelper.createDocument();
		PlanXml.write(plan, doc.addElement("layout"));
		Files.createDirectories(file.toAbsolutePath().getParent());
		write(doc, file);
	}

	// a layout file, or the tables of an event's file
	public static Plan loadLayout(Path file) throws IOException
	{
		Element tables = read(file).getRootElement().element("tables");
		if (tables == null) throw new IOException(file.getFileName() + " has no tables in it.");
		return PlanXml.read(tables);
	}

	// ---- the message templates ----

	public static void saveTemplates(List<MessageTemplate> list, Path file) throws IOException
	{
		Document doc = DocumentHelper.createDocument();
		Element templates = doc.addElement("templates");
		for (MessageTemplate t : list)
		{
			Element te = templates.addElement("template").addAttribute("name", t.getName())
					.addAttribute("subject", t.getSubject());
			for (String line : t.getBody().split("\n", -1))
				te.addElement("line").addAttribute("t", line);  // an attribute keeps leading spaces
		}
		Files.createDirectories(file.getParent());
		write(doc, file);
	}

	public static List<MessageTemplate> loadTemplates(Path file) throws IOException
	{
		return templates(read(file).getRootElement());
	}

	// the shared templates for occasions kept in this folder, or null if there are none
	public static List<MessageTemplate> sharedTemplates(Path occasionFolder) throws IOException
	{
		Path parent = occasionFolder.toAbsolutePath().getParent();
		if (parent == null || !Files.isRegularFile(parent.resolve(TEMPLATES))) return null;
		return loadTemplates(parent.resolve(TEMPLATES));
	}

	private static List<MessageTemplate> templates(Element te)
	{
		List<MessageTemplate> out = new java.util.ArrayList<>();
		for (Element t : te.elements("template"))
		{
			String body = String.join("\n", t.elements("line").stream().map(l -> l.attributeValue("t", "")).toList());
			out.add(new MessageTemplate(t.attributeValue("name"), t.attributeValue("subject"), body));
		}
		return out;
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
		String catered = root.attributeValue("catered");
		Element inv = root.element("invitations");
		Element old = root.element("invited");  // before replies were kept: all to be invited
		if (inv == null && old != null)
			inv = DocumentHelper.createElement("invitations").addAttribute(Invitation.TO_INVITE.code(),
					old.attributeValue("guests", ""));
		if (inv != null)
		{
			// the declined first, so that a lead brought in by their party is not taken for one
			for (Invitation state : List.of(Invitation.DECLINED, Invitation.TO_INVITE, Invitation.INVITED, Invitation.ACCEPTED))
			{
				for (String id : inv.attributeValue(state.code(), "").trim().split("\\s+"))
				{
					if (!id.matches("\\d+")) continue;
					int n = Integer.parseInt(id);
					guests.guests().stream().filter(g -> g.getId() == n).findFirst()
							.ifPresent(g -> event.setInvitation(g, state, guests));
				}
			}
		}
		readMenu(root.element("menu"), event.getMenu());
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
		// an older file says nothing: catered if it has a menu
		event.setCatered(catered != null ? catered.equals("yes") : event.hasMenu());
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
