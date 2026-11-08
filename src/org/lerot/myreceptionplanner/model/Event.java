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
 * be seated. Whenever anyone of a party is invited, so is its lead. It is kept in its own file in the
 * occasion's folder; the file's name is given when the event is made and
 * stays the same if the event is renamed.
 */
public class Event
{
	private String name;
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
			if (!invited.contains(g))
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
