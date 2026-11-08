package org.lerot.myreceptionplanner.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
 * One event of an occasion, such as the Wedding Breakfast: where and when it
 * is, who is invited, its tables and who sits where. Only those invited can
 * be seated. Whenever anyone of a party is invited, so is its lead.
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
	private final Set<Guest> invited = new LinkedHashSet<>();

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
		return limit == 0 ? 0 : Math.max(0, invited.size() - limit);
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

	// ---- who is invited ----

	public boolean isInvited(Guest guest) { return invited.contains(guest); }

	public Set<Guest> invited() { return Collections.unmodifiableSet(invited); }

	// with their lead
	public void invite(Guest guest)
	{
		invited.add(guest);
		if (guest.getParty() != null && guest.getParty().getLead() != null) invited.add(guest.getParty().getLead());
	}

	// they lose their seat here; uninviting a lead uninvites their whole party
	public void uninvite(Guest guest, GuestList guests)
	{
		List<Guest> going = guest.isLead() ? guests.membersOf(guest.getParty()) : List.of(guest);
		for (Guest g : going)
		{
			invited.remove(g);
			seating.unseat(g);
		}
	}

	public int invitedIn(Party party, GuestList guests)
	{
		return (int) guests.membersOf(party).stream().filter(invited::contains).count();
	}

	// exactly these (and their leads); returns how many lose their seats here
	public int setInvited(Collection<Guest> these, GuestList guests)
	{
		invited.clear();
		these.forEach(this::invite);
		return tidy(guests);
	}

	// keeps the invitations to those on the list, each with their lead, and
	// the seats to those invited; returns how many lose their seats
	public int tidy(GuestList guests)
	{
		invited.retainAll(guests.guests());
		for (Guest g : List.copyOf(invited))
			invite(g);
		int lost = 0;
		for (Guest g : List.copyOf(seating.all().keySet()))
		{
			if (!invited.contains(g) || !isSeated())
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
