package org.lerot.myreceptionplanner.model;

import org.lerot.myreceptionplanner.model.tables.Plan;
import org.lerot.myreceptionplanner.model.tables.Table;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Who sits where at one event; no two guests share a seat
public class Seating
{
	private final Map<Guest, Seat> seats = new LinkedHashMap<>();

	public Seat seatOf(Guest guest) { return seats.get(guest); }

	public boolean isSeated(Guest guest) { return seats.containsKey(guest); }

	public Optional<Guest> guestAt(int tableId, int chair)
	{
		Seat seat = new Seat(tableId, chair);
		return seats.entrySet().stream().filter(e -> e.getValue().equals(seat)).map(Map.Entry::getKey).findFirst();
	}

	public List<Guest> seatedAt(int tableId)
	{
		return seats.entrySet().stream().filter(e -> e.getValue().tableId() == tableId).map(Map.Entry::getKey).toList();
	}

	// of these guests, those without a seat, in the same order
	public List<Guest> unseated(Collection<Guest> guests)
	{
		return guests.stream().filter(g -> !isSeated(g)).toList();
	}

	public int seatedCount() { return seats.size(); }

	public Map<Guest, Seat> all() { return java.util.Collections.unmodifiableMap(seats); }

	// whoever sat there goes back among the unseated
	public void seat(Guest guest, Seat seat)
	{
		guestAt(seat.tableId(), seat.chair()).ifPresent(seats::remove);
		seats.put(guest, seat);
	}

	public void unseat(Guest guest) { seats.remove(guest); }

	// returns how many were unseated
	public int unseatTable(int tableId)
	{
		List<Guest> there = seatedAt(tableId);
		there.forEach(seats::remove);
		return there.size();
	}

	// unseats anyone no longer on the guest list, whose table or chair is gone
	// or whose chair is disabled or open access, and all but the first of any sharing a
	// chair; returns how many (not counting those who have left the list)
	public int tidy(Plan plan, GuestList guests)
	{
		seats.keySet().removeIf(g -> !guests.guests().contains(g));
		int count = 0;
		Set<Seat> taken = new HashSet<>();
		for (var it = seats.entrySet().iterator(); it.hasNext(); )
		{
			Seat s = it.next().getValue();
			Optional<Table> t = plan.find(s.tableId());
			boolean ok = t.isPresent() && s.chair() >= 0 && s.chair() < t.get().places()
					&& t.get().isReserved(s.chair()) && taken.add(s);
			if (!ok)
			{
				it.remove();
				count++;
			}
		}
		return count;
	}
}
