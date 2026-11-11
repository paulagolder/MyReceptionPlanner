package org.lerot.myreceptionplanner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Messages to the parties' leads: the templates that come with a new
 * occasion, the fill-ins a template may use, and a template completed for a
 * party (and an event, for the fill-ins about one event). Unknown fill-ins
 * are left as written.
 */
public final class Messages
{
	private Messages() {}

	// a message completed for one party
	public record Message(String subject, String body) {}

	// a message prepared for a party, and how
	public record Sent(Party party, LocalDateTime when, String what, String how, String event) {}

	// the standard templates whose sending the progress screen counts
	public static final String INVITATION = "Invitation", MENU_CHOICES = "Menu choices";

	// each fill-in and what it gives
	public static final Map<String, String> FILL_INS = new LinkedHashMap<>();

	static
	{
		FILL_INS.put("lead", "the lead's full name");
		FILL_INS.put("leadfirst", "the lead's first name, or title and name: Count Paris, Dr Smith (honorifics.yml)");
		FILL_INS.put("party", "the party's name");
		FILL_INS.put("guests", "the party's guests, e.g. \"Ann Smith, Bob Smith and a guest\"");
		FILL_INS.put("places", "how many places the party has");
		FILL_INS.put("occasion", "the occasion, e.g. Janet and John's Wedding");
		FILL_INS.put("date", "the occasion's date, e.g. 12 May 2026");
		FILL_INS.put("events", "a line for each event the party is invited to: what, where, when");
		FILL_INS.put("event", "the event chosen, e.g. Wedding Breakfast");
		FILL_INS.put("eventplace", "where the event chosen is");
		FILL_INS.put("eventwhen", "the chosen event's date and time");
		FILL_INS.put("seats", "a line for each of the party at the event chosen: where they sit");
		FILL_INS.put("menu", "the chosen event's menu, course by course");
		FILL_INS.put("menuschosen", "thanks for the menu choices made, naming the events, or nothing");
		FILL_INS.put("menustochoose", "each catered event the party is invited to with choices still to make or "
				+ "confirm: who, what they have chosen so far, and the menu; or nothing");
		FILL_INS.put("missing", "what is still needed from the party: names, dietary needs, menu choices");
		FILL_INS.put("from", "who the message is from (Occasion details)");
		FILL_INS.put("reply", "how to reply (Occasion details)");
	}

	public static List<MessageTemplate> standard()
	{
		return new ArrayList<>(List.of(
				new MessageTemplate(INVITATION, "{occasion}: an invitation",
						"""
						Dear {leadfirst},

						We would love you to join us for {occasion} on {date}.

						You are invited to:
						{events}

						This invitation is for {guests}.

						Please let us know whether you can come{reply}.

						With love,
						{from}"""),
				new MessageTemplate(MENU_CHOICES, "{occasion}: menu choices",
						"""
						Dear {leadfirst},

						{menuschosen}

						{menustochoose}

						Please let us know{reply}.

						With love,
						{from}"""),
				new MessageTemplate("Request for details", "{occasion}: a few details, please",
						"""
						Dear {leadfirst},

						To help us plan {occasion}, please could you let us know:
						{missing}

						Thank you,
						{from}"""),
				new MessageTemplate("Reminder", "{occasion}: a reminder",
						"""
						Dear {leadfirst},

						Just a reminder about {occasion} on {date}:
						{events}

						{missing}

						With love,
						{from}"""),
				new MessageTemplate("Table and arrangements", "{occasion}: {event}",
						"""
						Dear {leadfirst},

						We are looking forward to seeing you at {event}, {eventplace}, {eventwhen}.

						{seats}

						With love,
						{from}""")));
	}

	// the template completed for the party; event may be null
	public static Message fill(MessageTemplate t, Occasion o, Party party, Event event)
	{
		return new Message(fill(t.getSubject(), o, party, event), fill(t.getBody(), o, party, event));
	}

	private static final Pattern FILL_IN = Pattern.compile("\\{(\\w+)}");

	static String fill(String text, Occasion o, Party party, Event event)
	{
		Matcher m = FILL_IN.matcher(text);
		StringBuilder out = new StringBuilder();
		while (m.find())
		{
			String value = value(m.group(1).toLowerCase(), o, party, event);
			m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
		}
		m.appendTail(out);
		return out.toString().replaceAll("\n{3,}", "\n\n").strip();
	}

	private static String value(String key, Occasion o, Party party, Event event)
	{
		GuestList guests = o.getGuests();
		Guest lead = party.getLead();
		return switch (key)
		{
			case "lead" -> lead == null ? party.getName() : lead.getName();
			case "leadfirst" -> lead == null ? party.getName() : o.getHonorifics().greeting(lead.getName());
			case "party" -> party.getName();
			case "guests" -> guestsText(guests.membersOf(party));
			case "places" -> Integer.toString(guests.placesOf(party));
			case "occasion" -> o.getName();
			case "date" -> o.getDate() == null ? "" : Occasion.format(o.getDate());
			case "events" -> eventsText(o, party);
			case "event" -> event == null ? "" : event.getName();
			case "eventplace" -> event == null ? "" : event.getPlace();
			case "eventwhen" -> event == null ? "" : when(o, event);
			case "seats" -> event == null ? "" : seatsText(o, party, event);
			case "menu" -> event == null ? "" : menuText(event);
			case "menuschosen" -> menusChosenText(o, party);
			case "menustochoose" -> menusToChooseText(o, party);
			case "missing" -> missingText(o, party);
			case "from" -> o.getSender();
			case "reply" -> o.getReplyTo().isEmpty() ? "" : " (" + o.getReplyTo() + ")";
			default -> null;
		};
	}

	// "Ann Smith, Bob Smith and 2 guests"
	static String guestsText(List<Guest> members)
	{
		List<String> parts = new ArrayList<>(members.stream().filter(Guest::isNamed).map(Guest::getName).toList());
		long unnamed = members.stream().filter(g -> !g.isNamed()).count();
		if (unnamed == 1) parts.add("a guest");
		else if (unnamed > 1) parts.add(unnamed + " guests");
		if (parts.size() <= 1) return parts.isEmpty() ? "" : parts.get(0);
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	// "Tuesday 12 May 2026 at 2pm", or just the time on the occasion's day
	static String when(Occasion o, Event e)
	{
		LocalDate d = o.dateOf(e);
		String date = d == null ? "" : Occasion.format(d);
		if (e.getTime().isEmpty()) return date;
		return date.isEmpty() ? "at " + e.getTime() : date + " at " + e.getTime();
	}

	static String eventsText(Occasion o, Party party)
	{
		List<String> lines = new ArrayList<>();
		for (Event e : o.events())
		{
			if (e.invitedIn(party, o.getGuests()) == 0) continue;
			List<String> parts = new ArrayList<>(List.of(e.getName()));
			if (!e.getPlace().isEmpty()) parts.add(e.getPlace());
			String w = when(o, e);
			if (!w.isEmpty()) parts.add(w);
			lines.add("  • " + String.join(", ", parts));
		}
		return String.join("\n", lines);
	}

	static String seatsText(Occasion o, Party party, Event e)
	{
		List<String> lines = new ArrayList<>();
		for (Guest g : o.getGuests().membersOf(party))
		{
			if (!e.isInvited(g)) continue;
			String seat = Catering.seatOf(e, g);
			String where = !seat.isEmpty() ? seat : e.isSeated() && e.getKind() == Event.Kind.SEATED ? "seat to be arranged" : "open seating";
			lines.add("  • " + g.displayName() + ": " + where);
		}
		return String.join("\n", lines);
	}

	static String menuText(Event e)
	{
		List<String> lines = new ArrayList<>();
		for (Menu.Course c : e.getMenu().courses())
			lines.add("  " + c.getName() + ": " + String.join(" / ", c.dishes().stream().map(Menu.Dish::withDiets).toList()));
		if (!e.getMenu().getNote().isEmpty())
			lines.add(e.getMenu().getNote().replaceAll("(?m)^", "  "));
		return String.join("\n", lines);
	}

	// the party's named guests invited to the event
	private static List<Guest> namedInvited(Occasion o, Party party, Event e)
	{
		return o.getGuests().membersOf(party).stream().filter(g -> g.isNamed() && e.isInvited(g)).toList();
	}

	// the events with a menu where the party's named guests still have choices to make
	public static List<Event> menusToChoose(Occasion o, Party party)
	{
		return o.events().stream().filter(Event::servesMenu)
				.filter(e -> namedInvited(o, party, e).stream().anyMatch(g -> !e.hasChosenAll(g))).toList();
	}

	// "Thank you for your menu choices for the Wedding Breakfast and the Dinner." or nothing
	static String menusChosenText(Occasion o, Party party)
	{
		List<String> done = o.events().stream().filter(Event::servesMenu)
				.filter(e -> !namedInvited(o, party, e).isEmpty()
						&& namedInvited(o, party, e).stream().allMatch(e::hasChosenAll))
				.map(Event::getName).toList();
		if (done.isEmpty()) return "";
		return "Thank you for your menu choices for " + and(done) + ".";
	}

	/*
	 * Each event still to choose for: what, where, when; each guest with what
	 * they have chosen so far (to confirm) and what is left; then the menu.
	 */
	static String menusToChooseText(Occasion o, Party party)
	{
		List<Event> events = menusToChoose(o, party);
		if (events.isEmpty()) return "";
		StringBuilder out = new StringBuilder(events.size() == 1
				? "We would love your menu choices for this event; where some are chosen already, please confirm them:"
				: "We would love your menu choices for these events; where some are chosen already, please confirm them:");
		for (Event e : events)
		{
			out.append("\n\n").append(e.getName());
			String w = when(o, e);
			if (!e.getPlace().isEmpty()) out.append(", ").append(e.getPlace());
			if (!w.isEmpty()) out.append(", ").append(w);
			for (Guest g : namedInvited(o, party, e))
			{
				List<String> chosen = new ArrayList<>(), left = new ArrayList<>();
				for (Menu.Course c : e.getMenu().courses())
				{
					Menu.Dish d = e.choice(g, c);
					if (d == null) left.add(c.getName());
					else chosen.add(c.getName() + ": " + d.getName());
				}
				String line = "  • " + g.getName() + ": ";
				if (left.isEmpty()) line += "all chosen (" + String.join(", ", chosen) + ")";
				else if (chosen.isEmpty()) line += "please choose";
				else line += String.join(", ", chosen) + " (please confirm); " + and(left) + " still to choose";
				out.append('\n').append(line);
			}
			out.append("\n  The menu:\n").append(menuText(e).replaceAll("(?m)^", "  "));
		}
		return out.toString();
	}

	// "a", "a and b", "a, b and c"
	private static String and(List<String> parts)
	{
		if (parts.size() <= 1) return parts.isEmpty() ? "" : parts.get(0);
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	// what is still needed; a line of thanks if nothing
	static String missingText(Occasion o, Party party)
	{
		GuestList guests = o.getGuests();
		List<Guest> members = guests.membersOf(party);
		List<String> lines = new ArrayList<>();
		long unnamed = members.stream().filter(g -> !g.isNamed()).count();
		if (unnamed == 1) lines.add("  • the name of the guest coming with you");
		else if (unnamed > 1) lines.add("  • the names of the " + unnamed + " guests coming with you");
		List<String> noNeeds = members.stream().filter(g -> g.isNamed() && !g.hasDietaryNeeds()).map(Guest::getName).toList();
		if (!noNeeds.isEmpty())
			lines.add("  • any dietary needs or allergies (" + String.join(", ", noNeeds) + ")");
		for (Event e : o.events())
		{
			if (!e.servesMenu()) continue;
			List<String> toChoose = members.stream().filter(g -> g.isNamed() && e.isInvited(g) && !e.hasChosenAll(g))
					.map(Guest::getName).toList();
			if (!toChoose.isEmpty())
				lines.add("  • menu choices for " + e.getName() + " (" + String.join(", ", toChoose) + "):\n"
						+ menuText(e).replaceAll("(?m)^", "    "));
		}
		return lines.isEmpty() ? "We have everything we need from you — thank you!" : String.join("\n", lines);
	}

	// the phone number as WhatsApp wants it: digits with the country code, "447700900123"
	public static String whatsAppNumber(String phone, String countryCode)
	{
		if (phone == null) return "";
		String digits = phone.replaceAll("[^0-9+]", "");
		if (digits.startsWith("+")) return digits.substring(1);
		if (digits.startsWith("00")) return digits.substring(2);
		if (digits.startsWith("0")) return countryCode.replaceAll("\\D", "") + digits.substring(1);
		return digits;
	}
}
