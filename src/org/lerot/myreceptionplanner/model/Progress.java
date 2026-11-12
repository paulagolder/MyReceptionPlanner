package org.lerot.myreceptionplanner.model;

import java.util.ArrayList;
import java.util.List;

/*
 * How far the planning has got, stage by stage, following the workflow: the
 * occasion's own stages (its details, the events, the guests, invitations,
 * menus sent, replies), then for each event its tables, menu, seating and
 * menu choices. Each stage counts what is done of what there is to do, with
 * a note of what is left and a warning when something is wrong.
 */
public final class Progress
{
	private Progress() {}

	public enum Step
	{
		OCCASION("Define occasion"), EVENTS("Define events"), GUESTS("Manage guests"),
		INVITATIONS("Send invitations"), MENUS_SENT("Send out menus"), REPLIES("Process replies"),
		TABLES("Tables and seats"), MENU("Menu"), SEATS("Allocate seats"), CHOICES("Menu choices");

		public final String label;

		Step(String label) { this.label = label; }

		public static final List<Step> OCCASION_STEPS = List.of(OCCASION, EVENTS, GUESTS, INVITATIONS, MENUS_SENT, REPLIES);
		public static final List<Step> EVENT_STEPS = List.of(TABLES, MENU, SEATS, CHOICES);
	}

	public enum State { NOT_NEEDED, NOT_STARTED, PART_DONE, DONE }

	// done of total; warn when something needs putting right whatever the count
	public record Stage(Step step, int done, int total, String note, State state, boolean warn)
	{
		public String count() { return state == State.NOT_NEEDED || total == 0 ? "" : done + " of " + total; }
	}

	private static Stage counted(Step step, int done, int total, String note, boolean warn)
	{
		State state = total > 0 && done >= total ? State.DONE : done > 0 ? State.PART_DONE : State.NOT_STARTED;
		return new Stage(step, Math.min(done, total), total, note, state, warn);
	}

	private static Stage notNeeded(Step step, String note)
	{
		return new Stage(step, 0, 0, note, State.NOT_NEEDED, false);
	}

	private static Stage notStarted(Step step, String note)
	{
		return new Stage(step, 0, 0, note, State.NOT_STARTED, false);
	}

	public static List<Stage> ofOccasion(Occasion o)
	{
		return List.of(details(o), events(o), guests(o), invitations(o), menusSent(o), replies(o));
	}

	public static List<Stage> ofEvent(Occasion o, Event e)
	{
		return List.of(tables(e), menu(o, e), seats(o, e), choices(e));
	}

	static Stage details(Occasion o)
	{
		List<String> missing = new ArrayList<>();
		if (o.getDate() == null) missing.add("date");
		if (o.getSender().isEmpty()) missing.add("who it is from");
		if (o.getReplyTo().isEmpty()) missing.add("how to reply");
		return counted(Step.OCCASION, 4 - missing.size(), 4,
				missing.isEmpty() ? "name, date, sender and reply" : "still to give: " + String.join(", ", missing), false);
	}

	static Stage events(Occasion o)
	{
		List<String> without = o.events().stream().filter(e -> e.getPlace().isEmpty() || e.getTime().isEmpty())
				.map(Event::getName).toList();
		int n = o.events().size();
		String note = n + (n == 1 ? " event" : " events")
				+ (without.isEmpty() ? ", all with place and time" : "; no place or time for " + String.join(", ", without));
		return counted(Step.EVENTS, n - without.size(), n, note, false);
	}

	static Stage guests(Occasion o)
	{
		GuestList guests = o.getGuests();
		int total = guests.guests().size();
		if (total == 0) return notStarted(Step.GUESTS, "no guests yet");
		int named = (int) guests.guests().stream().filter(Guest::isNamed).count();
		int alike = guests.alike().size();
		long unreachable = guests.parties().stream().filter(p -> p.getLead() != null && !reachable(p.getLead())).count();
		List<String> notes = new ArrayList<>();
		notes.add(guests.parties().size() + (guests.parties().size() == 1 ? " party" : " parties"));
		if (named < total) notes.add((total - named) + (total - named == 1 ? " place" : " places") + " to name");
		if (unreachable > 0) notes.add(unreachable + (unreachable == 1 ? " lead" : " leads") + " with no email, phone or address");
		if (alike > 0) notes.add(alike + (alike == 1 ? " pair" : " pairs") + " of names alike");
		return counted(Step.GUESTS, named, total, String.join("; ", notes), alike > 0);
	}

	private static boolean reachable(Guest lead)
	{
		Contact c = lead.getContact();
		return c != null && !(c.getEmail().isEmpty() && c.getMobile().isEmpty() && c.getPhone().isEmpty() && c.getWhatsApp().isEmpty()
				&& c.getAddress().isEmpty());
	}

	// each guest's invitation to each event they are expected at: sent, or still to send
	static Stage invitations(Occasion o)
	{
		int toSend = 0, sent = 0;
		for (Event e : o.events())
			for (Invitation i : e.invitations().values())
			{
				if (i == Invitation.TO_INVITE) toSend++;
				else if (i.sent()) sent++;
			}
		if (toSend + sent == 0) return notStarted(Step.INVITATIONS, "no one is invited yet");
		String note = toSend == 0 ? "every invitation sent"
				: toSend + (toSend == 1 ? " invitation" : " invitations") + " to send (○); send the \""
				+ Messages.INVITATION + "\" message";
		return counted(Step.INVITATIONS, sent, toSend + sent, note, false);
	}

	// of the invitations sent, those answered: accepted or declined
	static Stage replies(Occasion o)
	{
		int waiting = 0, accepted = 0, declined = 0;
		for (Event e : o.events())
			for (Invitation i : e.invitations().values())
				switch (i)
				{
					case INVITED -> waiting++;
					case ACCEPTED -> accepted++;
					case DECLINED -> declined++;
					default -> { }
				}
		int total = waiting + accepted + declined;
		if (total == 0) return notStarted(Step.REPLIES, "no invitation sent yet");
		String note = accepted + " accepted, " + declined + " declined"
				+ (waiting == 0 ? "" : "; " + waiting + " awaiting a reply");
		return counted(Step.REPLIES, accepted + declined, total, note, false);
	}

	// done for a party once sent the menu message, or with nothing left to choose
	static Stage menusSent(Occasion o)
	{
		if (o.events().stream().noneMatch(Event::servesMenu)) return notNeeded(Step.MENUS_SENT, "no catered event has a menu yet");
		GuestList guests = o.getGuests();
		List<Party> catered = guests.parties().stream().filter(p -> o.events().stream().filter(Event::servesMenu)
				.anyMatch(e -> guests.membersOf(p).stream().anyMatch(g -> g.isNamed() && e.isInvited(g)))).toList();
		if (catered.isEmpty()) return notStarted(Step.MENUS_SENT, "no named guest is invited to an event with a menu");
		int outstanding = 0, done = 0;
		for (Party p : catered)
		{
			boolean toChoose = !Messages.menusToChoose(o, p).isEmpty();
			if (toChoose) outstanding++;
			if (!toChoose || o.lastSent(p, Messages.MENU_CHOICES) != null) done++;
		}
		String note = outstanding == 0 ? "every party has chosen"
				: outstanding + (outstanding == 1 ? " party has" : " parties have") + " choices to make; "
				+ (catered.size() - done) + " not yet sent the \"" + Messages.MENU_CHOICES + "\" message";
		return counted(Step.MENUS_SENT, done, catered.size(), note, false);
	}

	// enough places for those invited
	static Stage tables(Event e)
	{
		int invited = e.invited().size();
		String note;
		int room;
		if (!e.isSeated())
		{
			if (e.getCapacity() == 0) return notNeeded(Step.TABLES, "unreserved, " + invited + " invited, no capacity set");
			room = e.getCapacity();
			note = "capacity " + room + " for " + invited + " invited";
		} else
		{
			int places = e.getPlan().places();
			if (places == 0) return notStarted(Step.TABLES, "no tables yet");
			int tables = e.getPlan().tables().size();
			note = tables + (tables == 1 ? " table, " : " tables, ") + places + " places";
			room = e.getKind() == Event.Kind.HALL && e.getCapacity() > 0 ? e.getCapacity() : places;
			if (room != places) note += ", capacity " + room;
			note += " for " + invited + " invited";
		}
		if (e.overCapacity() > 0) note += ": " + e.overCapacity() + " too many";
		if (invited == 0) return new Stage(Step.TABLES, 0, 0, note, State.DONE, false);
		return counted(Step.TABLES, room, invited, note, e.overCapacity() > 0);
	}

	// a menu with a dish in every course for every invited guest's needs
	static Stage menu(Occasion o, Event e)
	{
		if (!e.isCatered()) return notNeeded(Step.MENU, "not catered");
		if (!e.hasMenu()) return notStarted(Step.MENU, "no menu yet");
		List<Menu.Course> courses = e.getMenu().courses();
		int suited = 0;
		List<String> lacking = new ArrayList<>();
		for (Menu.Course c : courses)
		{
			boolean all = !c.dishes().isEmpty() && e.invited().stream().filter(Guest::isNamed).allMatch(g ->
					c.dishes().stream().anyMatch(d -> Diet.unmet(d.suits(), g.diets()).isEmpty()));
			if (all) suited++;
			else lacking.add(c.getName());
		}
		int dishes = e.getMenu().dishes().size();
		String note = courses.size() + (courses.size() == 1 ? " course, " : " courses, ") + dishes
				+ (dishes == 1 ? " dish" : " dishes")
				+ (lacking.isEmpty() ? "" : "; nothing for some guests' needs in " + String.join(", ", lacking));
		return counted(Step.MENU, suited, courses.size(), note, !lacking.isEmpty());
	}

	static Stage seats(Occasion o, Event e)
	{
		if (!e.isSeated()) return notNeeded(Step.SEATS, "unreserved: no one is given a seat");
		if (e.getKind() == Event.Kind.HALL)
		{
			int reserved = e.getPlan().reservedPlaces(), given = e.getSeating().seatedCount();
			if (reserved == 0) return notNeeded(Step.SEATS, "no reserved seats");
			return counted(Step.SEATS, given, reserved, given + " of the reserved seats given", false);
		}
		int invited = e.invited().size();
		if (invited == 0) return notStarted(Step.SEATS, "no one is invited");
		int unseated = Checks.unseated(o.getGuests(), e).stream().mapToInt(Checks.Unseated::unseated).sum();
		return counted(Step.SEATS, invited - unseated, invited,
				unseated == 0 ? "everyone has a seat" : unseated + " still to seat", false);
	}

	// of the named guests invited; places not yet named choose once named
	static Stage choices(Event e)
	{
		if (!e.isCatered()) return notNeeded(Step.CHOICES, "not catered");
		if (!e.hasMenu()) return notStarted(Step.CHOICES, "no menu yet");
		int named = (int) e.invited().stream().filter(Guest::isNamed).count();
		if (named == 0) return notStarted(Step.CHOICES, "no named guest is invited");
		int notChosen = Catering.notChosen(e).size(), bad = Catering.unsuitable(e).size(), unnamed = Catering.notNamed(e);
		List<String> notes = new ArrayList<>();
		notes.add(notChosen == 0 ? "all chosen" : notChosen + " still to choose");
		if (bad > 0) notes.add(bad + " with a dish unsuited to their needs");
		if (unnamed > 0) notes.add(unnamed + (unnamed == 1 ? " place" : " places") + " not yet named");
		return counted(Step.CHOICES, named - notChosen, named, String.join("; ", notes), bad > 0);
	}
}
