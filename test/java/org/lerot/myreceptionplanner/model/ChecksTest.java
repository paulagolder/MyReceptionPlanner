package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Spare places and guests not seated
class ChecksTest
{
	@Test
	void spareSeatsLeaveOutFullTablesAndDisabledSeats()
	{
		Plan plan = new Plan();
		RectTable head = new RectTable(1, "", 2, 0);  // 4 chairs, far side disabled when head
		head.moveTo(500, 100);
		plan.add(head);
		plan.setHead(head);
		RoundTable two = table(plan, 2, 600, 400, 4);
		RoundTable one = table(plan, 3, 200, 400, 2);
		RoundTable named = table(plan, 4, 400, 800, 3);
		named.setName("Smith family");
		plan.renumber();
		GuestList guests = new GuestList();
		Seating seating = new Seating();
		seating.seat(guests.addGuest("A", null, null), new Seat(1, 0));
		seating.seat(guests.addGuest("B", null, null), new Seat(3, 0));
		seating.seat(guests.addGuest("C", null, null), new Seat(3, 1));  // table 3 full
		seating.seat(guests.addGuest("D", null, null), new Seat(2, 0));

		List<Checks.SpareSeats> spare = Checks.spareSeats(plan, seating);
		assertEquals(List.of("Head table", "Table 2", "Smith family"), spare.stream().map(Checks.SpareSeats::label).toList());
		assertEquals(List.of(1, 3, 3), spare.stream().map(Checks.SpareSeats::spare).toList());
		assertEquals(1, one.getNumber());
		assertEquals(2, two.getNumber());
	}

	@Test
	void unseatedCountsEachPartyAndThoseInNone()
	{
		GuestList guests = new GuestList();
		Party smiths = guests.createParty("Ann Smith", 3, "");
		Party joneses = guests.createParty("Cath Jones", 2, "");
		Guest brown = guests.addGuest("Father Brown", Gender.MALE, null);
		guests.addGuest("Not Asked", Gender.MALE, null);
		Event event = new Event("Breakfast", "b.xml", new Plan(), new Seating());
		guests.membersOf(smiths).forEach(event::invite);
		guests.membersOf(joneses).forEach(event::invite);
		event.invite(brown);
		Seating seating = event.getSeating();
		seating.seat(smiths.getLead(), new Seat(1, 0));
		guests.membersOf(joneses).forEach(g -> seating.seat(g, new Seat(2, g.getPlus())));

		List<Checks.Unseated> u = Checks.unseated(guests, event);
		assertEquals(2, u.size());
		assertEquals("Ann Smith", u.get(0).name());
		assertEquals(2, u.get(0).unseated());
		assertEquals(3, u.get(0).places());
		assertEquals("No party", u.get(1).name());
		assertNull(u.get(1).party());
		assertEquals(1, u.get(1).unseated(), "only those invited count");
	}

	private static RoundTable table(Plan plan, int id, double x, double y, int places)
	{
		RoundTable t = new RoundTable(id, "", places);
		t.moveTo(x, y);
		plan.add(t);
		return t;
	}
}
