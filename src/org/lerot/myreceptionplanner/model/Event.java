package org.lerot.myreceptionplanner.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * One event of an occasion, such as the Wedding Breakfast: where and when it
 * is, where each guest stands with it (see Invitation), its tables and who
 * sits where. Only those expected (to be invited, invited or accepted) can be
 * seated. Whenever anyone of a party is expected, so is its lead.
 *
 * An unreserved party (the evening do) has invitations and may have tables
 * set out for the staff, but no one is given a seat; it may have a capacity,
 * the most guests the place can take. A hall or ceremony (the church) is set
 * out mostly with pews of open seats, and only its reserved seats are given
 * to guests; its capacity is all its seats. It is kept in its own file in the
 * occasion's folder; the file's name is given when the event is made and
 * stays the same if the event is renamed.
 */
public class Event
{
	public enum Kind
	{
		SEATED("seated", "Seated"),
		HALL("hall", "Hall / ceremony"),
		UNRESERVED("unreserved", "Party, unreserved");

		private final String code;   // in the event's file
		private final String label;

		Kind(String code, String label)
		{
			this.code = code;
			this.label = label;
		}

		public String code() { return code; }

		@Override
		public String toString() { return label; }

		public static Kind fromCode(String code)
		{
			for (Kind k : values())
			{
				if (k.code.equalsIgnoreCase(code)) return k;
			}
			return SEATED;
		}
	}

	private String name;
	private Kind kind = Kind.SEATED;
	private int capacity;      // 0 for no limit; for an unreserved party
	private String place = "";
	private LocalDate date;    // null: the occasion's date
	private String time = "";  // as typed, e.g. "2pm" or "14:00"
	private final String file;
	private final Plan plan;
	private final Seating seating;
	private final Map<Guest, Invitation> invitations = new LinkedHashMap<>();  // NONE is left out
	private final Menu menu = new Menu();
	// each guest's dish for each course, and any note for this event
	private final Map<Guest, Map<Menu.Course, Menu.Dish>> choices = new LinkedHashMap<>();
	private final Map<Guest, String> notes = new LinkedHashMap<>();

	public Event(String name, String file, Plan plan, Seating seating)
	{
		setName(name);
		this.file = file;
		this.plan = plan;
		this.seating = seating;
	}

	public String getName() { return name; }

	public void setName(String name) { this.name = name == null || name.isBlank() ? "Event" : name.trim(); }

	public Kind getKind() { return kind; }

	// whether guests are given seats (all at a seated event, the reserved ones in a hall)
	public boolean isSeated() { return kind != Kind.UNRESERVED; }

	// an unreserved party has no seats, so any there are cleared
	public void setKind(Kind kind)
	{
		this.kind = kind == null ? Kind.SEATED : kind;
		if (!isSeated()) for (Guest g : List.copyOf(seating.all().keySet())) seating.unseat(g);
	}

	public int getCapacity() { return capacity; }

	public void setCapacity(int capacity) { this.capacity = Math.max(0, capacity); }

	// the most guests: an unreserved party's capacity, all a hall's usable
	// seats; 0 for no limit (a seated event is limited by its seats instead)
	public int capacityLimit()
	{
		return switch (kind)
		{
			case UNRESERVED -> capacity;
			case HALL -> plan.places();
			case SEATED -> 0;
		};
	}

	// how many more are invited than the capacity allows; 0 if none or no limit
	public int overCapacity()
	{
		int limit = capacityLimit();
		return limit == 0 ? 0 : Math.max(0, invited().size() - limit);
	}

	public String getPlace() { return place; }

	public void setPlace(String place) { this.place = place == null ? "" : place.trim(); }

	public LocalDate getDate() { return date; }

	public void setDate(LocalDate date) { this.date = date; }

	public String getTime() { return time; }

	public void setTime(String time) { this.time = time == null ? "" : time.trim(); }

	public String getFile() { return file; }

	public Plan getPlan() { return plan; }

	public Seating getSeating() { return seating; }

	// ---- the menu and what guests choose ----

	public Menu getMenu() { return menu; }

	public boolean hasMenu() { return !menu.isEmpty(); }

	public Menu.Dish choice(Guest guest, Menu.Course course)
	{
		Map<Menu.Course, Menu.Dish> mine = choices.get(guest);
		return mine == null ? null : mine.get(course);
	}

	// null to clear; only a named guest chooses, so the choice follows them wherever they sit
	public void choose(Guest guest, Menu.Course course, Menu.Dish dish)
	{
		if (guest.isPlaceholder() && dish != null) return;
		if (dish == null)
		{
			Map<Menu.Course, Menu.Dish> mine = choices.get(guest);
			if (mine != null) mine.remove(course);
			return;
		}
		choices.computeIfAbsent(guest, g -> new LinkedHashMap<>()).put(course, dish);
	}

	public Map<Guest, Map<Menu.Course, Menu.Dish>> choices() { return Collections.unmodifiableMap(choices); }

	// a note for this event only, such as "leaving before dessert"
	public String noteFor(Guest guest) { return notes.getOrDefault(guest, ""); }

	// for a named guest only
	public void setNote(Guest guest, String note)
	{
		if (note == null || note.isBlank() || guest.isPlaceholder()) notes.remove(guest);
		else notes.put(guest, note.strip());
	}

	public Map<Guest, String> notes() { return Collections.unmodifiableMap(notes); }

	// the guest has chosen a dish for every course
	public boolean hasChosenAll(Guest guest)
	{
		return menu.courses().stream().allMatch(c -> choice(guest, c) != null);
	}

	// the dishes chosen that do not suit the guest's diet
	public List<Menu.Dish> unsuitable(Guest guest)
	{
		return menu.courses().stream().map(c -> choice(guest, c))
				.filter(d -> d != null && !Diet.unmet(d.suits(), guest.diets()).isEmpty()).toList();
	}

	// no choices or notes for those not invited or not named, nor choices of
	// dishes no longer on the menu
	private void tidyChoices()
	{
		choices.keySet().removeIf(g -> !isInvited(g));
		notes.keySet().removeIf(g -> !isInvited(g));
		choices.keySet().removeIf(Guest::isPlaceholder);
		notes.keySet().removeIf(Guest::isPlaceholder);
		for (Map<Menu.Course, Menu.Dish> mine : choices.values())
			mine.entrySet().removeIf(e -> !menu.courses().contains(e.getKey()) || !e.getKey().dishes().contains(e.getValue()));
		choices.values().removeIf(Map::isEmpty);
	}

	// ---- who is invited ----

	public Invitation invitation(Guest guest) { return invitations.getOrDefault(guest, Invitation.NONE); }

	// expected: to be invited, invited or accepted
	public boolean isInvited(Guest guest) { return invitation(guest).expected(); }

	// those expected, in the order first invited
	public Set<Guest> invited()
	{
		Set<Guest> out = new LinkedHashSet<>();
		invitations.forEach((g, i) -> { if (i.expected()) out.add(g); });
		return Collections.unmodifiableSet(out);
	}

	// everyone with an invitation of any kind, declined too
	public Map<Guest, Invitation> invitations() { return Collections.unmodifiableMap(invitations); }

	// to be invited, if not expected already; with their lead
	public void invite(Guest guest)
	{
		if (!isInvited(guest)) invitations.put(guest, Invitation.TO_INVITE);
		Guest lead = guest.getParty() == null ? null : guest.getParty().getLead();
		if (lead != null && lead != guest && !isInvited(lead)) invitations.put(lead, Invitation.TO_INVITE);
	}

	/*
	 * Sets where the guest stands, keeping a party's lead expected whenever
	 * any of the party is. A lead not expected (not invited, or declined)
	 * takes the party with them; anyone no longer expected loses their seat,
	 * choices and notes here.
	 */
	public void setInvitation(Guest guest, Invitation state, GuestList guests)
	{
		if (state.expected())
		{
			invitations.put(guest, state);
			invite(guest);
			return;
		}
		List<Guest> going = guest.isLead() ? guests.membersOf(guest.getParty()) : List.of(guest);
		for (Guest g : going)
		{
			if (g != guest && !isInvited(g)) continue;  // others keep a refusal already given
			if (state == Invitation.NONE) invitations.remove(g);
			else invitations.put(g, state);
			seating.unseat(g);
			choices.remove(g);
			notes.remove(g);
		}
	}

	// not invited at all; uninviting a lead uninvites their whole party
	public void uninvite(Guest guest, GuestList guests)
	{
		setInvitation(guest, Invitation.NONE, guests);
	}

	// those to be invited now invited: their invitation has been sent
	public void sent(Collection<Guest> these)
	{
		for (Guest g : these)
			if (invitation(g) == Invitation.TO_INVITE) invitations.put(g, Invitation.INVITED);
	}

	public int invitedIn(Party party, GuestList guests)
	{
		return (int) guests.membersOf(party).stream().filter(this::isInvited).count();
	}

	/*
	 * Exactly these (and their leads) expected; returns how many lose their
	 * seats here. Of the others, one whose invitation was sent is cancelled
	 * (declined); one only to be invited is not invited.
	 */
	public int setInvited(Collection<Guest> these, GuestList guests)
	{
		Set<Guest> keep = new LinkedHashSet<>(these);
		for (Guest g : List.copyOf(invitations.keySet()))
		{
			if (keep.contains(g) || !isInvited(g)) continue;
			if (invitation(g).sent()) invitations.put(g, Invitation.DECLINED);
			else invitations.remove(g);
		}
		keep.forEach(this::invite);
		return tidy(guests);
	}

	// keeps the invitations to those on the list, each with their lead, and
	// the seats to those invited; returns how many lose their seats
	public int tidy(GuestList guests)
	{
		invitations.keySet().retainAll(guests.guests());
		for (Guest g : invited())
			invite(g);
		tidyChoices();
		int lost = 0;
		for (Guest g : List.copyOf(seating.all().keySet()))
		{
			if (!isInvited(g) || !isSeated())
			{
				seating.unseat(g);
				lost++;
			}
		}
		return lost + seating.tidy(plan, guests);
	}

	@Override
	public String toString() { return name; }
}
