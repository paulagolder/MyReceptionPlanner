package org.lerot.myreceptionplanner.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/*
 * The occasion, such as "Lydia and Isaac's Wedding" on 12 May 2026: one guest
 * list (parties, their leads and contacts) shared by its events, each with
 * its own place, time, tables and seating. Its wording goes on the reports
 * and on anything sent to the guests.
 */
public class Occasion
{
	public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

	private String name;
	private LocalDate date;  // null if not set
	private final GuestList guests;
	private final List<Event> events = new ArrayList<>();

	public Occasion(String name, GuestList guests)
	{
		setName(name);
		this.guests = guests;
	}

	public String getName() { return name; }

	public void setName(String name) { this.name = name == null || name.isBlank() ? "Occasion" : name.trim(); }

	public LocalDate getDate() { return date; }

	public void setDate(LocalDate date) { this.date = date; }

	public GuestList getGuests() { return guests; }

	public List<Event> events() { return Collections.unmodifiableList(events); }

	public void addEvent(Event event) { events.add(event); }

	public void removeEvent(Event event) { events.remove(event); }

	// a file name for a new event, from its name, not used by another
	public String fileFor(String eventName)
	{
		String base = eventName.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		if (base.isEmpty() || base.equals("occasion")) base = "event";
		String file = base + ".xml";
		for (int n = 2; usedFile(file); n++)
			file = base + "-" + n + ".xml";
		return file;
	}

	private boolean usedFile(String file)
	{
		return events.stream().anyMatch(e -> e.getFile().equalsIgnoreCase(file));
	}

	// the event's date, or the occasion's if it has none of its own
	public LocalDate dateOf(Event event)
	{
		return event.getDate() != null ? event.getDate() : date;
	}

	// "Lydia and Isaac's Wedding" and "12 May 2026", as headings put them
	public String heading()
	{
		return date == null ? name : name + "  ·  " + DATE.format(date);
	}

	// "Wedding Breakfast · The Old Barn, Lichfield · 2pm", with the event's
	// date too if it differs from the occasion's
	public String headingOf(Event event)
	{
		List<String> parts = new ArrayList<>();
		parts.add(event.getName());
		if (!event.getPlace().isEmpty()) parts.add(event.getPlace());
		String when = event.getTime();
		if (event.getDate() != null && !event.getDate().equals(date))
			when = (DATE.format(event.getDate()) + " " + when).trim();
		if (!when.isEmpty()) parts.add(when);
		return String.join("  ·  ", parts);
	}

	// the guest list changed: no seat for anyone who has left it, at any event
	public void tidy()
	{
		for (Event e : events)
			e.getSeating().tidy(e.getPlan(), guests);
	}

	public boolean seatedAnywhere(Guest guest)
	{
		return events.stream().anyMatch(e -> e.getSeating().isSeated(guest));
	}

	// "12 May 2026", "12/5/2026", "2026-05-12" ...; null for blank, and an
	// exception if it cannot be read
	public static LocalDate parseDate(String text)
	{
		if (text == null || text.isBlank()) return null;
		String t = text.trim();
		for (String pattern : new String[] { "d MMMM yyyy", "d MMM yyyy", "d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "yyyy-MM-dd" })
		{
			try
			{
				return LocalDate.parse(t, DateTimeFormatter.ofPattern(pattern, Locale.UK));
			} catch (DateTimeParseException ignored)
			{
			}
		}
		throw new IllegalArgumentException("Not a date: " + t + " (try 12 May 2026)");
	}
}
