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
 *   <occasion name="Lydia and Isaac's Wedding" date="2026-05-12">
 *     <event file="wedding-breakfast.xml"/>
 *     <event file="evening-reception.xml"/>
 *     <guests> ... </guests>
 *   </occasion>
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
 *   </event>
 *
 * An event's date is only there if it differs from the occasion's. type is
 * seated, or unreserved for a party where no one has a seat, which may have
 * a capacity="150".
 */
public class OccasionXml
{
	public static final String FILE = "occasion.xml";

	public static boolean isOccasion(Path folder)
	{
		return Files.isRegularFile(folder.resolve(FILE));
	}

	public static void save(Occasion occasion, Path folder) throws IOException
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("occasion").addAttribute("name", occasion.getName());
		if (occasion.getDate() != null) root.addAttribute("date", occasion.getDate().toString());
		for (Event e : occasion.events())
			root.addElement("event").addAttribute("file", e.getFile());
		GuestXml.write(occasion.getGuests(), root);
		Files.createDirectories(folder);
		write(doc, folder.resolve(FILE));
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
		return doc;
	}

	public static Occasion load(Path folder) throws IOException
	{
		Element root = read(folder.resolve(FILE)).getRootElement();
		GuestList guests = GuestXml.read(root.element("guests"));
		Occasion occasion = new Occasion(root.attributeValue("name", folder.getFileName().toString()), guests);
		occasion.setDate(date(root, FILE));
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
