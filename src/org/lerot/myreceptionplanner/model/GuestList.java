package org.lerot.myreceptionplanner.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

// The parties and guests of an occasion; where they sit is kept by each event
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
	// pairs of guests said not to be the same person, as "smaller id:larger id"
	private final Set<String> distinct = new TreeSet<>();

	// a guest whose name is like another's, and why
	public record Likeness(Guest a, Guest b, String why) {}

	public List<Party> parties() { return Collections.unmodifiableList(parties); }

	public List<Guest> guests() { return Collections.unmodifiableList(guests); }

	/*
	 * A new party: its lead, named, and places for the rest not named yet,
	 * "Ann Smith+1", "Ann Smith+2" ... The party is called after the lead
	 * unless given a name of its own.
	 */
	public Party createParty(Name leadName, int places, String partyName)
	{
		if (leadName == null || leadName.isEmpty()) throw new IllegalArgumentException("a party needs a lead guest");
		if (places < 1) throw new IllegalArgumentException("a party has at least its lead's place");
		int id = parties.stream().mapToInt(Party::getId).max().orElse(0) + 1;
		Party p = new Party(id, partyName, nextColour());
		parties.add(p);
		Guest lead = addGuest(leadName, Gender.UNKNOWN, p);
		p.setLead(lead);
		setPlaces(p, places, g -> false);
		return p;
	}

	void addParty(Party party) { parties.add(party); }

	// its named guests stay, in no party; its places not named go
	public void removeParty(Party party)
	{
		parties.remove(party);
		guests.stream().filter(g -> g.getParty() == party && !g.isNamed()).toList().forEach(this::forget);
		guests.removeIf(g -> g.getParty() == party && !g.isNamed());
		for (Guest g : guests)
		{
			if (g.getParty() == party) g.setParty(null);
		}
	}

	public List<Guest> membersOf(Party party)
	{
		return guests.stream().filter(g -> g.getParty() == party).toList();
	}

	// the party's places: its named guests and the places kept for others
	public int placesOf(Party party) { return membersOf(party).size(); }

	public int namedIn(Party party)
	{
		return (int) guests.stream().filter(g -> g.getParty() == party && g.isNamed()).count();
	}

	/*
	 * Makes the party this many places, adding places not named yet or taking
	 * them away: those not seated (at any event) first, the highest numbered
	 * first. It cannot go below the party's named guests. Returns the guests
	 * taken away; Occasion.tidy then takes away their seats.
	 */
	public List<Guest> setPlaces(Party party, int places, Predicate<Guest> seated)
	{
		int named = namedIn(party);
		if (places < named)
			throw new IllegalArgumentException(party.getName() + " has " + named + " named guests");
		List<Guest> members = membersOf(party);
		for (int i = members.size(); i < places; i++)
			addGuest("", Gender.UNKNOWN, party);
		if (members.size() <= places) return List.of();
		List<Guest> going = members.stream()
				.filter(g -> !g.isNamed())
				.sorted(Comparator.comparing((Guest g) -> seated.test(g)).thenComparing(Comparator.comparingInt(Guest::getPlus).reversed()))
				.limit(members.size() - places)
				.toList();
		guests.removeAll(going);
		going.forEach(this::forget);
		return going;
	}

	// a whole name typed in one piece is split as Name.guess does
	public Party createParty(String leadName, int places, String partyName)
	{
		return createParty(Name.guess(leadName, HONORIFICS), places, partyName);
	}

	public Guest addGuest(String name, Gender gender, Party party)
	{
		return addGuest(Name.guess(name, HONORIFICS), gender, party);
	}

	public void rename(Guest guest, String name)
	{
		rename(guest, Name.guess(name, HONORIFICS));
	}

	private static final Honorifics HONORIFICS = Honorifics.standard();

	public Guest addGuest(Name name, Gender gender, Party party)
	{
		int id = guests.stream().mapToInt(Guest::getId).max().orElse(0) + 1;
		Guest g = new Guest(id, name, gender, party);
		if (g.isPlaceholder()) g.setPlus(nextPlus(party));
		guests.add(g);
		return g;
	}

	void addGuest(Guest guest) { guests.add(guest); }

	// ---- names alike ----

	/*
	 * The named guests whose names are like this one, and why (see
	 * NameMatch), leaving out the guest being named and anyone already said
	 * not to be them.
	 */
	public List<Likeness> like(String name, Guest self)
	{
		List<Likeness> out = new ArrayList<>();
		if (name == null || name.isBlank()) return out;
		for (Guest g : guests)
		{
			if (g == self || !g.isNamed() || (self != null && isDistinct(self, g))) continue;
			NameMatch.why(name, g.getName()).ifPresent(w -> out.add(new Likeness(self, g, w)));
		}
		return out;
	}

	// every pair of named guests whose names are alike, but not those said to be different people
	public List<Likeness> alike()
	{
		List<Likeness> out = new ArrayList<>();
		List<Guest> named = guests.stream().filter(Guest::isNamed).toList();
		for (int i = 0; i < named.size(); i++)
			for (int j = i + 1; j < named.size(); j++)
			{
				Guest a = named.get(i), b = named.get(j);
				if (isDistinct(a, b)) continue;
				NameMatch.why(a.getName(), b.getName()).ifPresent(w -> out.add(new Likeness(a, b, w)));
			}
		return out;
	}

	// they are two people, whatever their names
	public void markDistinct(Guest a, Guest b)
	{
		distinct.add(pair(a.getId(), b.getId()));
	}

	public boolean isDistinct(Guest a, Guest b)
	{
		return distinct.contains(pair(a.getId(), b.getId()));
	}

	// for saving: "3:9" ...
	public Set<String> distinctPairs() { return Collections.unmodifiableSet(distinct); }

	void markDistinct(int a, int b) { distinct.add(pair(a, b)); }

	private static String pair(int a, int b)
	{
		return Math.min(a, b) + ":" + Math.max(a, b);
	}

	// the lead cannot be removed; returns false if not
	public boolean removeGuest(Guest guest)
	{
		if (guest.isLead()) return false;
		guests.remove(guest);
		forget(guest);
		return true;
	}

	// a guest whose name is cleared keeps a place in their party, with a number;
	// the lead must keep a name, so an empty one is ignored for them
	public void rename(Guest guest, Name name)
	{
		boolean empty = name == null || name.isEmpty();
		if (empty && guest.isLead()) return;
		guest.setName(name);
		if (guest.isPlaceholder() && guest.getPlus() == 0) guest.setPlus(nextPlus(guest.getParty()));
		if (guest.isPlaceholder())
		{
			// a place not named yet has no needs of its own
			guest.diets().clear();
			guest.setDietNote("");
		}
	}

	// moves a guest to another party (null for none); the lead stays with
	// theirs, so returns false for them
	public boolean setParty(Guest guest, Party party)
	{
		if (guest.getParty() == party) return true;
		if (guest.isLead()) return false;
		guest.setParty(party);
		guest.setPlus(guest.isPlaceholder() ? nextPlus(party) : 0);
		return true;
	}

	// makes one of the party's named guests its lead
	public void setLead(Party party, Guest lead)
	{
		if (lead.getParty() != party || !lead.isNamed())
			throw new IllegalArgumentException("the lead must be one of the party's named guests");
		party.setLead(lead);
	}

	// no pairs kept for a guest who has left
	private void forget(Guest g)
	{
		distinct.removeIf(p -> p.startsWith(g.getId() + ":") || p.endsWith(":" + g.getId()));
	}

	private int nextPlus(Party party)
	{
		return guests.stream().filter(g -> g.getParty() == party).mapToInt(Guest::getPlus).max().orElse(0) + 1;
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
