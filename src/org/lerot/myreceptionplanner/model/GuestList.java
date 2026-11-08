package org.lerot.myreceptionplanner.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// The parties and guests of one reception, and where each guest sits
public class GuestList
{
	public static final Color NO_PARTY = new Color(0x8A8A8E);

	// party colours, told apart easily; a new party takes the first one unused
	private static final Color[] PALETTE = {
			new Color(0x1F77B4), new Color(0xD62728), new Color(0x2CA02C), new Color(0x9467BD),
			new Color(0xFF7F0E), new Color(0x17BECF), new Color(0xE377C2), new Color(0x8C564B),
			new Color(0xBCBD22), new Color(0x393B79) };

	private final List<Party> parties = new ArrayList<>();
	private final List<Guest> guests = new ArrayList<>();

	public List<Party> parties() { return Collections.unmodifiableList(parties); }

	public List<Guest> guests() { return Collections.unmodifiableList(guests); }

	public Party addParty(String name)
	{
		int id = parties.stream().mapToInt(Party::getId).max().orElse(0) + 1;
		Party p = new Party(id, name, nextColour());
		parties.add(p);
		return p;
	}

	void addParty(Party party) { parties.add(party); }

	// its guests stay, in no party
	public void removeParty(Party party)
	{
		parties.remove(party);
		for (Guest g : guests)
		{
			if (g.getParty() == party) g.setParty(null);
		}
	}

	public int membersOf(Party party)
	{
		return (int) guests.stream().filter(g -> g.getParty() == party).count();
	}

	public Guest addGuest(String name, Gender gender, Party party)
	{
		int id = guests.stream().mapToInt(Guest::getId).max().orElse(0) + 1;
		Guest g = new Guest(id, name, gender, party);
		guests.add(g);
		return g;
	}

	void addGuest(Guest guest) { guests.add(guest); }

	public void removeGuest(Guest guest) { guests.remove(guest); }

	public Optional<Guest> guestAt(int tableId, int chair)
	{
		Seat seat = new Seat(tableId, chair);
		return guests.stream().filter(g -> seat.equals(g.getSeat())).findFirst();
	}

	public List<Guest> seatedAt(int tableId)
	{
		return guests.stream().filter(g -> g.isSeated() && g.getSeat().tableId() == tableId).toList();
	}

	public List<Guest> unseated()
	{
		return guests.stream().filter(g -> !g.isSeated()).toList();
	}

	public int seatedCount()
	{
		return (int) guests.stream().filter(Guest::isSeated).count();
	}

	// whoever sat there goes back among the unseated
	public void seat(Guest guest, Seat seat)
	{
		guestAt(seat.tableId(), seat.chair()).ifPresent(g -> g.setSeat(null));
		guest.setSeat(seat);
	}

	public void unseat(Guest guest) { guest.setSeat(null); }

	// returns how many were unseated
	public int unseatTable(int tableId)
	{
		List<Guest> there = seatedAt(tableId);
		there.forEach(g -> g.setSeat(null));
		return there.size();
	}

	// unseats anyone whose table or chair is gone or whose chair is disabled,
	// and all but the first of any sharing a chair; returns how many
	public int tidy(Plan plan)
	{
		int count = 0;
		Set<Seat> taken = new HashSet<>();
		for (Guest g : guests)
		{
			Seat s = g.getSeat();
			if (s == null) continue;
			Optional<Table> t = plan.find(s.tableId());
			boolean ok = t.isPresent() && s.chair() >= 0 && s.chair() < t.get().places()
					&& !t.get().isDisabled(s.chair()) && taken.add(s);
			if (!ok)
			{
				g.setSeat(null);
				count++;
			}
		}
		return count;
	}

	public static Color colourOf(Guest guest)
	{
		return guest.getParty() == null ? NO_PARTY : guest.getParty().getColour();
	}

	private Color nextColour()
	{
		for (Color c : PALETTE)
		{
			if (parties.stream().noneMatch(p -> p.getColour().equals(c))) return c;
		}
		return PALETTE[parties.size() % PALETTE.length];
	}
}
