package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

// Pews, open and reserved seats, and halls
class PewTest
{
	@TempDir
	Path folder;

	@Test
	void aPewIsARowOfForwardFacingSeatsAllOpen()
	{
		Pew p = new Pew(1, "", 6);
		assertEquals(6, p.seats().size());
		assertEquals(300, p.length(), 0.001);
		List<Double> ys = p.seats().stream().map(s -> s.getY()).distinct().toList();
		assertEquals(1, ys.size(), "all in one row");
		assertTrue(p.top().getBounds2D().getMinY() > ys.get(0), "the backrest is behind the seats");
		assertEquals(6, p.getOpenSeats().size());
		assertEquals(0, p.reservedPlaces());
		assertEquals(6, p.usablePlaces());
	}

	@Test
	void seatsAreNumberedFromSeatOneTowardsTheFarEnd()
	{
		Pew p = new Pew(1, "", 5);
		assertEquals(List.of(1, 2, 3, 4, 5), IntStream.range(0, 5).map(p::seatNumber).boxed().toList());
		p.setFirstSeat(4);  // the right-hand end, by the aisle
		assertEquals(List.of(5, 4, 3, 2, 1), IntStream.range(0, 5).map(p::seatNumber).boxed().toList());
		assertEquals(4, p.chairOfSeat(1));
		assertEquals(0, p.chairOfSeat(5));
	}

	@Test
	void seatsCanBeReservedOneByOneOrAll()
	{
		Pew p = new Pew(1, "", 4);
		p.setOpen(0, false);
		assertTrue(p.isReserved(0));
		assertEquals(1, p.reservedPlaces());
		p.setDisabled(3, true);
		assertFalse(p.isOpen(3), "a disabled seat is neither open nor reserved");
		assertFalse(p.isReserved(3));
		p.setAllOpen(false);
		assertEquals(3, p.reservedPlaces());
		RoundTable t = new RoundTable(2, "", 8);
		assertEquals(8, t.reservedPlaces(), "table chairs start reserved");
	}

	@Test
	void noOneIsSeatedOnAnOpenSeat()
	{
		Plan plan = new Plan();
		Pew p = new Pew(1, "", 4);
		plan.add(p);
		p.setOpen(0, false);
		GuestList g = new GuestList();
		Guest ann = g.addGuest("Ann", null, null), bob = g.addGuest("Bob", null, null);
		Seating s = new Seating();
		s.seat(ann, new Seat(1, 0));
		s.seat(bob, new Seat(1, 1));
		assertEquals(1, s.tidy(plan, g));
		assertTrue(s.isSeated(ann));
		assertFalse(s.isSeated(bob));
	}

	@Test
	void pewsAreNumberedOnTheirOwn()
	{
		Plan plan = new Plan();
		RoundTable t = new RoundTable(1, "", 8);
		t.moveTo(300, 300);
		Pew front = new Pew(2, "", 8), back = new Pew(3, "", 8);
		front.moveTo(300, 600);
		back.moveTo(300, 800);
		plan.add(t);
		plan.add(back);
		plan.add(front);
		plan.renumber();
		assertEquals("Table 1", plan.label(t));
		assertEquals("Pew 1", plan.label(front));
		assertEquals("Pew 2", plan.label(back));
		Pew another = new Pew(4, "", 6);
		plan.add(another);
		plan.numberUnnumbered();
		assertEquals("Pew 3", plan.label(another));
	}

	@Test
	void pewsCloseBehindOneAnotherAreSeparateRows()
	{
		Plan plan = new Plan();
		int id = 1;
		for (int row = 0; row < 3; row++)
			for (int side = 0; side < 2; side++)
			{
				Pew p = new Pew(id++, "", 6);
				p.moveTo(side == 0 ? 300 : 800, 300 + row * 100);
				plan.add(p);
			}
		plan.renumber();
		// across each row, then the next row back
		assertEquals(List.of(1, 2, 3, 4, 5, 6), IntStream.rangeClosed(1, 6)
				.map(i -> plan.find(i).orElseThrow().getNumber()).boxed().toList());
	}

	@Test
	void aHallsCapacityIsAllItsSeats()
	{
		Plan plan = new Plan();
		Pew p = new Pew(1, "", 3);
		plan.add(p);
		p.setDisabled(2, true);
		Event hall = new Event("Ceremony", "c.xml", plan, new Seating());
		hall.setKind(Event.Kind.HALL);
		assertTrue(hall.isSeated(), "reserved seats are given in a hall");
		GuestList g = new GuestList();
		for (String n : new String[] { "A", "B", "C" })
			hall.invite(g.addGuest(n, null, null));
		assertEquals(2, hall.capacityLimit());
		assertEquals(1, hall.overCapacity());
	}

	@Test
	void pewsAndOpenSeatsAreSavedAndDuplicated() throws IOException
	{
		Occasion o = new Occasion("Wedding", new GuestList());
		Event church = new Event("Ceremony", "ceremony.xml", new Plan(), new Seating());
		church.setKind(Event.Kind.HALL);
		o.addEvent(church);
		Pew p = new Pew(1, "", 5);
		p.moveTo(400, 600);
		p.setRotation(90);
		p.setOpen(0, false);
		p.setOpen(1, false);
		p.setFirstSeat(4);
		church.getPlan().add(p);
		RoundTable t = new RoundTable(2, "", 6);
		t.setOpen(5, true);
		church.getPlan().add(t);
		church.getPlan().renumber();

		Pew copy = (Pew) p.duplicate(3, 0, 80);
		assertEquals(p.getOpenSeats(), copy.getOpenSeats());

		OccasionXml.save(o, folder);
		Event loaded = OccasionXml.load(folder).events().get(0);
		assertEquals(Event.Kind.HALL, loaded.getKind());
		Pew lp = (Pew) loaded.getPlan().find(1).orElseThrow();
		assertEquals(5, lp.places());
		assertEquals(90, lp.getRotation());
		assertEquals(4, lp.getFirstSeat());
		assertEquals(Set.of(2, 3, 4), lp.getOpenSeats());
		assertEquals("Pew 1", loaded.getPlan().label(lp));
		assertEquals(Set.of(5), loaded.getPlan().find(2).orElseThrow().getOpenSeats());
	}

	@Test
	void changingAPewsSeatsOpensThemAllAgain()
	{
		Pew p = new Pew(1, "", 4);
		p.setAllOpen(false);
		p.setPlaces(6);
		assertEquals(6, p.getOpenSeats().size());
	}

	@Test
	void anAltarShowsTheWayButHasNoSeats() throws IOException
	{
		Occasion o = new Occasion("Wedding", new GuestList());
		Event church = new Event("Ceremony", "ceremony.xml", new Plan(), new Seating());
		o.addEvent(church);
		Plan plan = church.getPlan();
		Altar altar = new Altar(1, "", 5);
		altar.moveTo(500, 100);
		plan.add(altar);
		Pew pew = new Pew(2, "", 6);
		pew.moveTo(500, 400);
		plan.add(pew);
		plan.renumber();
		assertEquals("Altar", plan.label(altar));
		assertEquals(0, altar.getNumber());
		assertEquals("Pew 1", plan.label(pew), "the altar takes no number");
		assertEquals(0, altar.places());
		assertEquals(300, altar.width(), 0.001);
		assertEquals(6, plan.places(), "only the pew's seats count");
		assertTrue(Checks.spareSeats(plan, church.getSeating()).stream().noneMatch(s -> s.table() == altar));
		altar.setName("Speakers' table");
		assertEquals("Speakers' table", plan.label(altar));

		OccasionXml.save(o, folder);
		Altar loaded = (Altar) OccasionXml.load(folder).events().get(0).getPlan().find(1).orElseThrow();
		assertEquals(5, loaded.getLength());
		assertEquals("Speakers' table", loaded.getName());
		assertEquals(5, ((Altar) loaded.duplicate(9, 0, 0)).getLength());
	}
}
