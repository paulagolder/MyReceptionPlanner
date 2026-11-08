package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Who is invited to which event
class InvitationsTest
{
	@TempDir
	Path folder;

	private static Occasion wedding()
	{
		Occasion o = new Occasion("Wedding", new GuestList());
		o.addEvent(new Event("Breakfast", "breakfast.xml", new Plan(), new Seating()));
		o.addEvent(new Event("Evening", "evening.xml", new Plan(), new Seating()));
		return o;
	}

	@Test
	void aNewPartyIsInvitedToEveryEvent()
	{
		Occasion o = wedding();
		Party p = o.getGuests().createParty("Ann", 3, "");
		o.tidy();
		for (Event e : o.events())
			assertEquals(3, e.invitedIn(p, o.getGuests()));
	}

	@Test
	void aNewPlaceGoesWhereItsPartyIsInvited()
	{
		Occasion o = wedding();
		GuestList g = o.getGuests();
		Party p = g.createParty("Ann", 2, "");
		o.tidy();
		Event breakfast = o.events().get(0), evening = o.events().get(1);
		breakfast.uninvite(p.getLead(), g);     // the party is not at the breakfast
		g.setPlaces(p, 3, o::seatedAnywhere);
		o.tidy();
		Guest plus2 = g.membersOf(p).get(2);
		assertFalse(breakfast.isInvited(plus2));
		assertTrue(evening.isInvited(plus2));
	}

	@Test
	void invitingAnyoneOfAPartyInvitesItsLead()
	{
		Occasion o = wedding();
		GuestList g = o.getGuests();
		Party p = g.createParty("Ann", 3, "");
		Event e = o.events().get(0);
		e.invite(g.membersOf(p).get(2));
		assertTrue(e.isInvited(p.getLead()));
		assertEquals(2, e.invitedIn(p, g));
	}

	@Test
	void uninvitingTheLeadUninvitesThePartyAndClearsTheirSeats()
	{
		Occasion o = wedding();
		GuestList g = o.getGuests();
		Party p = g.createParty("Ann", 3, "");
		Guest other = g.addGuest("Father Brown", Gender.MALE, null);
		o.tidy();
		Event e = o.events().get(0);
		e.getSeating().seat(g.membersOf(p).get(1), new Seat(1, 0));
		e.uninvite(p.getLead(), g);
		assertEquals(0, e.invitedIn(p, g));
		assertTrue(e.isInvited(other));
		assertEquals(0, e.getSeating().seatedCount());
		assertEquals(3, o.events().get(1).invitedIn(p, g), "the other event is not touched");
	}

	@Test
	void settingWhoIsInvitedKeepsLeadsAndSaysWhoLosesSeats()
	{
		Plan plan = new Plan();
		plan.add(new RoundTable(1, "", 8));
		Occasion o = new Occasion("Wedding", new GuestList());
		Event e = new Event("Breakfast", "b.xml", plan, new Seating());
		o.addEvent(e);
		GuestList g = o.getGuests();
		Party p = g.createParty("Ann", 2, "");
		Guest brown = g.addGuest("Father Brown", Gender.MALE, null);
		o.tidy();
		e.getSeating().seat(brown, new Seat(1, 0));
		Guest plus1 = g.membersOf(p).get(1);
		int lost = e.setInvited(List.of(plus1), g);
		assertEquals(1, lost);
		assertEquals(Set.of(plus1, p.getLead()), e.invited());
	}

	@Test
	void invitationsAreSaved() throws IOException
	{
		Occasion o = wedding();
		GuestList g = o.getGuests();
		Party p = g.createParty("Ann", 2, "");
		Guest brown = g.addGuest("Father Brown", Gender.MALE, null);
		o.tidy();
		o.events().get(1).uninvite(brown, g);
		OccasionXml.save(o, folder);
		Occasion loaded = OccasionXml.load(folder);
		Event b = loaded.events().get(0), ev = loaded.events().get(1);
		assertEquals(3, b.invited().size());
		assertEquals(2, ev.invited().size());
		assertTrue(ev.invited().stream().noneMatch(x -> x.getName().equals("Father Brown")));
		// loaded guests are settled, so tidying does not invite them again
		loaded.tidy();
		assertEquals(2, ev.invited().size());
	}

	@Test
	void aSeatWithoutAnInvitationIsNotKeptOnLoading() throws IOException
	{
		java.nio.file.Files.writeString(folder.resolve("occasion.xml"), "<occasion name=\"W\"><event file=\"e.xml\"/>"
				+ "<guests><guest id=\"1\" name=\"Ann\" gender=\"f\"/><guest id=\"2\" name=\"Bob\" gender=\"m\"/></guests></occasion>");
		java.nio.file.Files.writeString(folder.resolve("e.xml"), "<event name=\"E\"><invited guests=\"1\"/>"
				+ "<tables><table id=\"1\" shape=\"round\" places=\"8\"/></tables>"
				+ "<seating><seat guest=\"1\" table=\"1\" chair=\"1\"/><seat guest=\"2\" table=\"1\" chair=\"2\"/></seating></event>");
		Event e = OccasionXml.load(folder).events().get(0);
		assertEquals(1, e.getSeating().seatedCount());
	}

	@Test
	void anUnreservedPartyHasNoSeatsAndWarnsWhenOverCapacity() throws IOException
	{
		Plan plan = new Plan();
		plan.add(new RoundTable(1, "", 8));
		Occasion o = new Occasion("Wedding", new GuestList());
		Event party = new Event("Evening Party", "evening.xml", plan, new Seating());
		o.addEvent(party);
		GuestList g = o.getGuests();
		g.createParty("Ann", 3, "");
		g.createParty("Cath", 2, "");
		o.tidy();
		party.getSeating().seat(g.guests().get(0), new Seat(1, 0));

		party.setKind(Event.Kind.UNRESERVED);
		assertEquals(0, party.getSeating().seatedCount(), "no seats at an unreserved party");
		assertEquals(0, party.overCapacity(), "no limit set");
		party.setCapacity(4);
		assertEquals(1, party.overCapacity());
		party.getSeating().seat(g.guests().get(0), new Seat(1, 0));
		party.tidy(g);
		assertEquals(0, party.getSeating().seatedCount());

		OccasionXml.save(o, folder);
		Event loaded = OccasionXml.load(folder).events().get(0);
		assertEquals(Event.Kind.UNRESERVED, loaded.getKind());
		assertEquals(4, loaded.getCapacity());
		assertEquals(5, loaded.invited().size());
		assertEquals(1, loaded.overCapacity());
	}

	@Test
	void aSeatedEventHasNoCapacityWarning()
	{
		Event e = new Event("Breakfast", "b.xml", new Plan(), new Seating());
		GuestList g = new GuestList();
		e.invite(g.addGuest("Ann", null, null));
		e.invite(g.addGuest("Bob", null, null));
		e.setCapacity(1);
		assertEquals(0, e.overCapacity(), "a seated event is limited by its tables");
	}
}
