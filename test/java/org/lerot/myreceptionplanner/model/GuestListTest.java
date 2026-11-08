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
	void aGuestNotYetNamedIsKnownByTheirParty()
	{
		GuestList list = new GuestList();
		Party smiths = list.addParty("Smiths");
		assertEquals("Guest of Smiths", list.addGuest("", Gender.UNKNOWN, smiths).displayName());
		assertEquals("Unnamed guest", list.addGuest(null, null, null).displayName());
	}

	@Test
	void eachNewPartyHasItsOwnColour()
	{
		GuestList list = new GuestList();
		Party a = list.addParty("A");
		Party b = list.addParty("B");
		assertNotEquals(a.getColour(), b.getColour());
	}

	@Test
	void removingAPartyKeepsItsGuests()
	{
		GuestList list = new GuestList();
		Party smiths = list.addParty("Smiths");
		Guest ann = list.addGuest("Ann", Gender.FEMALE, smiths);
		list.removeParty(smiths);
		assertTrue(list.guests().contains(ann));
		assertNull(ann.getParty());
		assertEquals(GuestList.NO_PARTY, GuestList.colourOf(ann));
	}

	@Test
	void tidyUnseatsGuestsWhoseChairsHaveGone()
	{
		Plan plan = new Plan();
		RoundTable t = new RoundTable(1, "T", 8);
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
		Party smiths = list.addParty("Smith & family");
		Guest ann = list.addGuest("Ann Smith", Gender.FEMALE, smiths);
		Guest someone = list.addGuest("", Gender.UNKNOWN, smiths);
		Guest priest = list.addGuest("Father Brown", Gender.MALE, null);
		list.seat(ann, new Seat(2, 0));
		list.seat(someone, new Seat(2, 5));

		Path file = folder.resolve(GuestXml.FILE);
		GuestXml.save(list, file);
		GuestList loaded = GuestXml.load(file);

		assertEquals(1, loaded.parties().size());
		Party p = loaded.parties().get(0);
		assertEquals("Smith & family", p.getName());
		assertEquals(smiths.getColour(), p.getColour());
		assertEquals(3, loaded.guests().size());
		Guest a = loaded.guestAt(2, 0).orElseThrow();
		assertEquals("Ann Smith", a.getName());
		assertEquals(Gender.FEMALE, a.getGender());
		assertSame(p, a.getParty());
		Guest s = loaded.guestAt(2, 5).orElseThrow();
		assertFalse(s.isNamed());
		assertEquals(Gender.UNKNOWN, s.getGender());
		Guest b = loaded.unseated().get(0);
		assertEquals("Father Brown", b.getName());
		assertNull(b.getParty());
		assertEquals(priest.getId(), b.getId());
	}

	@Test
	void newIdsFollowTheHighestLoaded() throws IOException
	{
		Path file = folder.resolve(GuestXml.FILE);
		Files.writeString(file, "<guests><party id=\"4\" name=\"P\" colour=\"#123456\">"
				+ "<guest id=\"9\" gender=\"f\"/></party></guests>");
		GuestList list = GuestXml.load(file);
		assertEquals(10, list.addGuest("", null, null).getId());
		assertEquals(5, list.addParty("Q").getId());
	}
}
