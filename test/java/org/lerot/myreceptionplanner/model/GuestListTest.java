package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Parties and their guests
class GuestListTest
{
	@Test
	void aNewPartyIsItsLeadAndNumberedPlaces()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann Smith", 3, "");
		assertEquals("Ann Smith", p.getName(), "a party goes by its lead's name");
		assertEquals("Ann Smith", p.getLead().getName());
		assertTrue(p.getLead().isLead());
		assertEquals(List.of("Ann Smith", "Ann Smith+1", "Ann Smith+2"),
				list.membersOf(p).stream().map(Guest::displayName).toList());
		assertEquals(3, list.placesOf(p));
		assertEquals(1, list.namedIn(p));
	}

	@Test
	void guestsAreFoundByNamePartyOrRole()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Seán Ó Briain", 2, "The Harvest Committee");
		Guest sean = p.getLead(), plusOne = list.membersOf(p).get(1);
		sean.setRole("Chairman");
		Guest bob = list.addGuest("Bob O'Neill", Gender.MALE, null);
		assertTrue(sean.matches(""), "nothing typed: everyone");
		assertTrue(sean.matches("SEAN"), "case and accents left aside");
		assertTrue(sean.matches("obri"), "Ó Briain as typed on a plain keyboard");
		assertTrue(sean.matches("sean briain"), "every word, in any order");
		assertFalse(sean.matches("sean neill"));
		assertTrue(plusOne.matches("sean"), "a place goes by its lead's name");
		assertTrue(plusOne.matches("committee"), "and is found by its party");
		assertTrue(sean.matches("chair"), "by role");
		assertTrue(bob.matches("oneill"), "the apostrophe left aside");
		assertTrue(bob.matches("O'Neill"));
		assertFalse(bob.matches("committee"));
	}

	@Test
	void placesFollowTheLeadsName()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann Smith", 2, "The Smiths");
		Guest plusOne = list.membersOf(p).get(1);
		assertEquals("The Smiths", p.getName());
		list.rename(p.getLead(), "Ann Jones");
		assertEquals("Ann Jones+1", plusOne.displayName());
		p.setName("");
		assertEquals("Ann Jones", p.getName());
		assertEquals("Ann Jones", p.toString(), "as lists show it");
	}

	@Test
	void namingAPlaceMakesItANamedGuest()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann Smith", 3, "");
		Guest plusOne = list.membersOf(p).get(1);
		list.rename(plusOne, "Bob Smith");
		assertFalse(plusOne.isPlaceholder());
		assertEquals("Bob Smith", plusOne.displayName());
		assertEquals(2, list.namedIn(p));
		list.rename(plusOne, "");
		assertTrue(plusOne.isPlaceholder(), "clearing the name makes it a place again");
		assertTrue(plusOne.getPlus() > 0);
	}

	@Test
	void placesCannotGoBelowTheNamedGuests()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann Smith", 4, "");
		list.rename(list.membersOf(p).get(1), "Bob");
		assertThrows(IllegalArgumentException.class, () -> list.setPlaces(p, 1, g -> false));
		list.setPlaces(p, 2, g -> false);
		assertEquals(List.of("Ann Smith", "Bob"), list.membersOf(p).stream().map(Guest::displayName).toList());
	}

	@Test
	void fewerPlacesTakesThoseNotSeatedAndHighestNumberedFirst()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann", 5, "");  // Ann, +1 .. +4
		List<Guest> m = list.membersOf(p);
		Guest seated = m.get(4);                     // +4 is seated somewhere
		List<Guest> gone = list.setPlaces(p, 3, g -> g == seated);
		assertEquals(List.of("Ann+3", "Ann+2"), gone.stream().map(Guest::displayName).toList());
		assertTrue(list.guests().contains(m.get(4)));
		list.setPlaces(p, 5, g -> false);
		assertEquals(List.of("Ann", "Ann+1", "Ann+4", "Ann+5", "Ann+6"),
				list.membersOf(p).stream().map(Guest::displayName).toList(), "new places take new numbers");
	}

	@Test
	void theLeadStaysNamedAndInTheirParty()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann", 2, "");
		Party q = list.createParty("Cath", 1, "");
		Guest ann = p.getLead();
		assertFalse(list.removeGuest(ann));
		assertFalse(list.setParty(ann, q));
		list.rename(ann, "  ");
		assertEquals("Ann", ann.getName());
	}

	@Test
	void movingAPlaceToAnotherPartyNumbersItThere()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann", 2, "");
		Party q = list.createParty("Cath", 3, "");
		Guest place = list.membersOf(p).get(1);
		assertTrue(list.setParty(place, q));
		assertEquals("Cath+3", place.displayName());
	}

	@Test
	void eachNewPartyHasItsOwnColour()
	{
		GuestList list = new GuestList();
		assertNotEquals(list.createParty("A", 1, "").getColour(), list.createParty("B", 1, "").getColour());
	}

	@Test
	void removingAPartyKeepsItsNamedGuestsOnly()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann", 3, "");
		Guest ann = p.getLead();
		list.removeParty(p);
		assertEquals(List.of(ann), list.guests());
		assertNull(ann.getParty());
		assertEquals(GuestList.NO_PARTY, GuestList.colourOf(ann));
	}

}
