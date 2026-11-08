package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Parties, guests, seating them, and guests.xml
class GuestListTest
{
	@TempDir
	Path folder;

	@Test
	void seatingSomeoneWhereAnotherSitsUnseatsTheOther()
	{
		GuestList list = new GuestList();
		Guest ann = list.addGuest("Ann", Gender.FEMALE, null);
		Guest bob = list.addGuest("Bob", Gender.MALE, null);
		list.seat(ann, new Seat(1, 3));
		list.seat(bob, new Seat(1, 3));
		assertFalse(ann.isSeated());
		assertEquals(bob, list.guestAt(1, 3).orElseThrow());
		assertEquals(List.of(ann), list.unseated());
	}

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
		assertThrows(IllegalArgumentException.class, () -> list.setPlaces(p, 1));
		list.setPlaces(p, 2);
		assertEquals(List.of("Ann Smith", "Bob"), list.membersOf(p).stream().map(Guest::displayName).toList());
	}

	@Test
	void fewerPlacesTakesThoseNotSeatedAndHighestNumberedFirst()
	{
		GuestList list = new GuestList();
		Party p = list.createParty("Ann", 5, "");  // Ann, +1 .. +4
		List<Guest> m = list.membersOf(p);
		list.seat(m.get(4), new Seat(1, 0));        // +4 is seated
		List<Guest> gone = list.setPlaces(p, 3);
		assertEquals(List.of("Ann+3", "Ann+2"), gone.stream().map(Guest::displayName).toList());
		assertTrue(list.guests().contains(m.get(4)));
		list.setPlaces(p, 5);
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

	@Test
	void tidyUnseatsGuestsWhoseChairsHaveGone()
	{
		Plan plan = new Plan();
		RoundTable t = new RoundTable(1, "", 8);
		plan.add(t);
		GuestList list = new GuestList();
		Guest fine = list.addGuest("Fine", Gender.FEMALE, null);
		Guest offChair = list.addGuest("Off", Gender.MALE, null);
		Guest noChair = list.addGuest("Gone", Gender.MALE, null);
		Guest noTable = list.addGuest("Lost", Gender.MALE, null);
		list.seat(fine, new Seat(1, 0));
		list.seat(offChair, new Seat(1, 2));
		list.seat(noChair, new Seat(1, 9));
		list.seat(noTable, new Seat(5, 0));
		t.setDisabled(2, true);
		assertEquals(3, list.tidy(plan));
		assertTrue(fine.isSeated());
		assertEquals(1, list.seatedCount());
	}

	@Test
	void aSavedGuestListLoadsTheSame() throws IOException
	{
		GuestList list = new GuestList();
		Party smiths = list.createParty("Ann Smith", 3, "Smith & family");
		Guest ann = smiths.getLead();
		ann.setGender(Gender.FEMALE);
		Contact c = ann.getContact();
		c.setAddress("1 High Street\nLichfield\nWS13 6AA");
		c.setPhone("01543 000000");
		c.setMobile("07700 900123");
		c.setWhatsApp("07700 900123");
		c.setEmail("ann@example.com");
		c.setSource("MyContacts");
		Guest plus2 = list.membersOf(smiths).get(2);
		list.rename(list.membersOf(smiths).get(1), "Bob Smith");
		Guest priest = list.addGuest("Father Brown", Gender.MALE, null);
		list.seat(ann, new Seat(2, 0));
		list.seat(plus2, new Seat(2, 5));

		Path file = folder.resolve(GuestXml.FILE);
		GuestXml.save(list, file);
		GuestList loaded = GuestXml.load(file);

		assertEquals(1, loaded.parties().size());
		Party p = loaded.parties().get(0);
		assertEquals("Smith & family", p.getName());
		assertEquals(smiths.getColour(), p.getColour());
		assertEquals(4, loaded.guests().size());
		Guest a = loaded.guestAt(2, 0).orElseThrow();
		assertSame(p.getLead(), a);
		assertEquals(Gender.FEMALE, a.getGender());
		Contact lc = a.getContact();
		assertEquals("1 High Street\nLichfield\nWS13 6AA", lc.getAddress());
		assertEquals("01543 000000", lc.getPhone());
		assertEquals("07700 900123", lc.getMobile());
		assertEquals("07700 900123", lc.getWhatsApp());
		assertEquals("ann@example.com", lc.getEmail());
		assertEquals("MyContacts", lc.getSource());
		Guest s = loaded.guestAt(2, 5).orElseThrow();
		assertEquals("Ann Smith+2", s.displayName());
		Guest b = loaded.unseated().stream().filter(g -> g.getParty() == null).findFirst().orElseThrow();
		assertEquals("Father Brown", b.getName());
		assertEquals(priest.getId(), b.getId());
	}

	@Test
	void aPartyNamedAfterItsLeadSavesNoName() throws IOException
	{
		GuestList list = new GuestList();
		list.createParty("Ann Smith", 1, "");
		Path file = folder.resolve(GuestXml.FILE);
		GuestXml.save(list, file);
		assertFalse(Files.readString(file).contains("<party id=\"1\" name="));
		assertFalse(GuestXml.load(file).parties().get(0).hasOwnName());
	}

	@Test
	void anOldFileGetsLeadsAndNumberedPlaces() throws IOException
	{
		Path file = folder.resolve(GuestXml.FILE);
		Files.writeString(file, "<guests><party id=\"4\" name=\"Smiths\" colour=\"#123456\">"
				+ "<guest id=\"8\" gender=\"u\"/><guest id=\"9\" name=\"Ann\" gender=\"f\"/>"
				+ "<guest id=\"10\" gender=\"u\"/></party></guests>");
		GuestList list = GuestXml.load(file);
		Party p = list.parties().get(0);
		assertEquals("Ann", p.getLead().getName());
		assertEquals(List.of("Ann+1", "Ann", "Ann+2"), list.membersOf(p).stream().map(Guest::displayName).toList());
		assertEquals(11, list.addGuest("Zed", null, null).getId());
		assertEquals(5, list.createParty("Q", 1, "").getId());
	}
}
