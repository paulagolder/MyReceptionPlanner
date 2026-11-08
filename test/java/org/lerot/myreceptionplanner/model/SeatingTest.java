package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Who sits where at an event
class SeatingTest
{
	@Test
	void seatingSomeoneWhereAnotherSitsUnseatsTheOther()
	{
		GuestList list = new GuestList();
		Guest ann = list.addGuest("Ann", Gender.FEMALE, null);
		Guest bob = list.addGuest("Bob", Gender.MALE, null);
		Seating seating = new Seating();
		seating.seat(ann, new Seat(1, 3));
		seating.seat(bob, new Seat(1, 3));
		assertFalse(seating.isSeated(ann));
		assertEquals(bob, seating.guestAt(1, 3).orElseThrow());
		assertEquals(List.of(ann), seating.unseated(list.guests()));
	}

	@Test
	void eachEventSeatsTheSameGuestsItsOwnWay()
	{
		GuestList list = new GuestList();
		Guest ann = list.addGuest("Ann", Gender.FEMALE, null);
		Seating breakfast = new Seating(), evening = new Seating();
		breakfast.seat(ann, new Seat(1, 0));
		evening.seat(ann, new Seat(4, 2));
		assertEquals(new Seat(1, 0), breakfast.seatOf(ann));
		assertEquals(new Seat(4, 2), evening.seatOf(ann));
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
		Guest left = list.addGuest("Left", Gender.MALE, null);
		Seating seating = new Seating();
		seating.seat(fine, new Seat(1, 0));
		seating.seat(offChair, new Seat(1, 2));
		seating.seat(noChair, new Seat(1, 9));
		seating.seat(noTable, new Seat(5, 0));
		seating.seat(left, new Seat(1, 3));
		list.removeGuest(left);
		t.setDisabled(2, true);
		assertEquals(3, seating.tidy(plan, list));
		assertTrue(seating.isSeated(fine));
		assertFalse(seating.isSeated(left), "someone no longer on the list loses their seat");
		assertEquals(1, seating.seatedCount());
	}

	@Test
	void takingAwayPlacesTakesAwayTheirSeatsAtEveryEvent()
	{
		Occasion o = new Occasion("Wedding", new GuestList());
		Event breakfast = new Event("Breakfast", "breakfast.xml", new Plan(), new Seating());
		o.addEvent(breakfast);
		Party p = o.getGuests().createParty("Ann", 3, "");
		Guest plus2 = o.getGuests().membersOf(p).get(2);
		breakfast.getSeating().seat(plus2, new Seat(1, 0));
		o.getGuests().setPlaces(p, 1, o::seatedAnywhere);
		o.tidy();
		assertEquals(0, breakfast.getSeating().seatedCount());
	}
}
