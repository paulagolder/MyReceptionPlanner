package org.lerot.myreceptionplanner.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * The occasion, such as "Lydia and Isaac's Wedding" on 12 May 2026: one guest
 * list (parties, their leads and contacts) shared by its events, each with
 * its own place, time, tables and seating. Its wording goes on the reports
 * and on anything sent to the guests.
 */
public class Occasion
{
	public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
	// Britain's first New Style day; dates before it are Old Style (Julian)
	private static final LocalDate NEW_STYLE = LocalDate.of(1752, 9, 14);

	private String name;
	private LocalDate date;  // null if not set
	private final GuestList guests;
	private final List<Event> events = new ArrayList<>();
	private String sender = "";        // who messages are from, "Lydia and Isaac"
	private String replyTo = "";       // how to reply, "lydia@example.com or 07700 900123"
	private String countryCode = "44"; // for making phone numbers international (WhatsApp)
	private String emblem;             // the occasion's SVG emblem, as text; null for none
	private final List<MessageTemplate> templates = Messages.standard();
	private Honorifics honorifics = Honorifics.standard();
	private final List<Messages.Sent> sent = new ArrayList<>();
	// guests whose invitations have been settled; anyone else is new
	private final Set<Guest> settled = Collections.newSetFromMap(new IdentityHashMap<>());

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

	// the emblem's SVG text, or null; it heads the printed documents
	public String getEmblem() { return emblem; }

	public void setEmblem(String svg) { emblem = svg == null || svg.isBlank() ? null : svg; }

	public String getSender() { return sender; }

	public void setSender(String sender) { this.sender = sender == null ? "" : sender.strip(); }

	public String getReplyTo() { return replyTo; }

	public void setReplyTo(String replyTo) { this.replyTo = replyTo == null ? "" : replyTo.strip(); }

	public String getCountryCode() { return countryCode; }

	public void setCountryCode(String code)
	{
		String digits = code == null ? "" : code.replaceAll("\\D", "");
		countryCode = digits.isEmpty() ? "44" : digits;
	}

	// the message templates; change the list directly
	public List<MessageTemplate> templates() { return templates; }

	// the titles greeted with the name after them (Count Paris); from honorifics.yml, not saved
	public Honorifics getHonorifics() { return honorifics; }

	public void setHonorifics(Honorifics honorifics) { this.honorifics = honorifics == null ? Honorifics.standard() : honorifics; }

	// the messages prepared so far, oldest first
	public List<Messages.Sent> sent() { return sent; }

	// noted as sent; the Invitation makes those of the party to be invited invited
	public void logSent(Party party, String what, String how, Event event)
	{
		sent.add(new Messages.Sent(party, java.time.LocalDateTime.now().withNano(0), what, how,
				event == null ? "" : event.getName()));
		if (Messages.INVITATION.equals(what))
			for (Event e : events)
				e.sent(guests.membersOf(party));
	}

	// the events whose invitation has gone to the guest and stands; until
	// declined or cancelled there, they stay on the guest list
	public List<Event> invitationsStanding(Guest guest)
	{
		return events.stream().filter(e -> e.invitation(guest).sent()).toList();
	}

	// the last time this message went to the party, or null
	public Messages.Sent lastSent(Party party, String what)
	{
		Messages.Sent last = null;
		for (Messages.Sent s : sent)
			if (s.party() == party && (what == null || s.what().equals(what))) last = s;
		return last;
	}

	public List<Event> events() { return Collections.unmodifiableList(events); }

	public void addEvent(Event event) { events.add(event); }

	public void removeEvent(Event event) { events.remove(event); }

	// a file name for a new event, from its name, not used by another
	// the occasion's own folder, from its name: "Lydia and Isaac's Wedding" → "lydia-and-isaacs-wedding"
	public static String folderName(String occasionName)
	{
		String base = java.text.Normalizer.normalize(occasionName, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").replaceAll("['’]", "").toLowerCase(Locale.UK)
				.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return base.isEmpty() ? "occasion" : base;
	}

	public String folderName() { return folderName(name); }

	// where the occasion's printed and written output goes, inside its folder
	public static final String OUTPUT = "Output";

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
		return date == null ? name : name + "  ·  " + format(date);
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
			when = (format(event.getDate()) + " " + when).trim();
		if (!when.isEmpty()) parts.add(when);
		return String.join("  ·  ", parts);
	}

	/*
	 * After the guest list changes. New guests are invited: one of a party
	 * already on the list goes wherever their party is invited; one of a new
	 * party, or of none, goes to every event (and to any event that has
	 * invited them already). Then no one keeps an invitation or a seat they
	 * should not have.
	 */
	public void tidy()
	{
		List<Guest> fresh = guests.guests().stream().filter(g -> !settled.contains(g)).toList();
		for (Guest g : fresh)
		{
			List<Guest> others = g.getParty() == null ? List.of()
					: guests.membersOf(g.getParty()).stream().filter(settled::contains).toList();
			for (Event e : events)
			{
				if (others.isEmpty() || others.stream().anyMatch(e::isInvited)) e.invite(g);
			}
		}
		settled.addAll(fresh);
		settled.retainAll(guests.guests());
		sent.removeIf(s -> !guests.parties().contains(s.party()));
		for (Event e : events)
			e.tidy(guests);
	}

	// everyone on the list now has their invitations settled (after loading)
	public void settleAll()
	{
		settled.addAll(guests.guests());
	}

	public boolean seatedAnywhere(Guest guest)
	{
		return events.stream().anyMatch(e -> e.getSeating().isSeated(guest));
	}

	// "Tuesday 12 May 2026", with the day of the week the date fell on
	public static String format(LocalDate date)
	{
		return weekday(date).getDisplayName(TextStyle.FULL, Locale.UK) + " " + DATE.format(date);
	}

	// the day of the week, as it was in Britain: before 14 September 1752 a
	// date is Old Style (Julian), so 16 July 1595 was a Wednesday
	public static DayOfWeek weekday(LocalDate date)
	{
		if (!date.isBefore(NEW_STYLE)) return date.getDayOfWeek();
		int a = (14 - date.getMonthValue()) / 12;
		long y = date.getYear() + 4800 - a;
		long m = date.getMonthValue() + 12 * a - 3;
		long julianDay = date.getDayOfMonth() + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
		return DayOfWeek.of((int) (julianDay % 7) + 1);  // Julian day 0 was a Monday
	}

	// "12 May 2026", "12/5/2026", "2026-05-12" ..., perhaps after a day of the
	// week ("Monday, 16 July 1595"), which is dropped: the date says which day
	// it was. null for blank, and an exception if it cannot be read
	public static LocalDate parseDate(String text)
	{
		if (text == null || text.isBlank()) return null;
		String t = text.trim().replaceFirst("(?i)^(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun|"
				+ "monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\.?,?\\s+", "");
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
