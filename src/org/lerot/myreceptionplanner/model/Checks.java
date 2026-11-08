package org.lerot.myreceptionplanner.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * What is not yet settled at an event: tables with seats free, and parties
 * with guests invited but not yet seated. The tables come in the order a guide lists
 * them: the head table, then tables by number, then pews by number, then
 * those with names of their own.
 */
public class Checks
{
	// a table with reserved seats still free (disabled and open seats don't count)
	public record SpareSeats(Table table, String label, int spare, int usable) {}

	// a party (null for guests in none) with guests not seated
	public record Unseated(Party party, String name, int unseated, int places) {}

	public static List<SpareSeats> spareSeats(Plan plan, Seating seating)
	{
		List<SpareSeats> out = new ArrayList<>();
		for (Table t : inGuideOrder(plan))
		{
			int spare = t.reservedPlaces() - seating.seatedAt(t.getId()).size();
			if (spare > 0) out.add(new SpareSeats(t, plan.label(t), spare, t.reservedPlaces()));
		}
		return out;
	}

	// of those invited to the event; places counts the party's invited
	public static List<Unseated> unseated(GuestList guests, Event event)
	{
		List<Unseated> out = new ArrayList<>();
		Seating seating = event.getSeating();
		for (Party p : guests.parties())
		{
			List<Guest> invited = guests.membersOf(p).stream().filter(event::isInvited).toList();
			int n = seating.unseated(invited).size();
			if (n > 0) out.add(new Unseated(p, p.getName(), n, invited.size()));
		}
		List<Guest> loners = guests.guests().stream().filter(g -> g.getParty() == null && event.isInvited(g)).toList();
		int n = seating.unseated(loners).size();
		if (n > 0) out.add(new Unseated(null, "No party", n, loners.size()));
		return out;
	}

	public static List<Table> inGuideOrder(Plan plan)
	{
		return plan.tables().stream()
				.sorted(Comparator.comparingInt((Table t) -> t.isHead() ? 0 : t.hasName() ? 3 : t instanceof Pew ? 2 : 1)
						.thenComparingInt(Table::getNumber)
						.thenComparing(plan::label, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}
}
