package org.lerot.myreceptionplanner.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * What is not yet settled in a plan: tables with seats free, and parties
 * with guests not yet seated. The tables come in the order a guide lists
 * them: the head table, then by number, then those with names of their own.
 */
public class Checks
{
	// a table with seats still free (disabled seats don't count)
	public record SpareSeats(Table table, String label, int spare, int usable) {}

	// a party (null for guests in none) with guests not seated
	public record Unseated(Party party, String name, int unseated, int places) {}

	public static List<SpareSeats> spareSeats(Plan plan, GuestList guests)
	{
		List<SpareSeats> out = new ArrayList<>();
		for (Table t : inGuideOrder(plan))
		{
			int spare = t.usablePlaces() - guests.seatedAt(t.getId()).size();
			if (spare > 0) out.add(new SpareSeats(t, plan.label(t), spare, t.usablePlaces()));
		}
		return out;
	}

	public static List<Unseated> unseated(GuestList guests)
	{
		List<Unseated> out = new ArrayList<>();
		for (Party p : guests.parties())
		{
			int n = (int) guests.membersOf(p).stream().filter(g -> !g.isSeated()).count();
			if (n > 0) out.add(new Unseated(p, p.getName(), n, guests.placesOf(p)));
		}
		List<Guest> loners = guests.guests().stream().filter(g -> g.getParty() == null).toList();
		int n = (int) loners.stream().filter(g -> !g.isSeated()).count();
		if (n > 0) out.add(new Unseated(null, "No party", n, loners.size()));
		return out;
	}

	public static List<Table> inGuideOrder(Plan plan)
	{
		return plan.tables().stream()
				.sorted(Comparator.comparingInt((Table t) -> t.isHead() ? 0 : t.hasName() ? 2 : 1)
						.thenComparingInt(Table::getNumber)
						.thenComparing(plan::label, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}
}
