package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// How far the planning has got, stage by stage
class ProgressTest
{
	private Occasion o;
	private Event breakfast, evening;
	private Party smiths;

	private void wedding()
	{
		o = new Occasion("Lydia and Isaac's Wedding", new GuestList());
		breakfast = new Event("Wedding Breakfast", "wb.xml", new Plan(), new Seating());
		evening = new Event("Evening Party", "ev.xml", new Plan(), new Seating());
		evening.setKind(Event.Kind.UNRESERVED);
		o.addEvent(breakfast);
		o.addEvent(evening);
		smiths = o.getGuests().createParty("Ann Smith", 3, "");
		o.getGuests().rename(o.getGuests().membersOf(smiths).get(1), "Bob Smith");
		o.tidy();
	}

	private Progress.Stage stage(List<Progress.Stage> stages, Progress.Step step)
	{
		return stages.stream().filter(s -> s.step() == step).findFirst().orElseThrow();
	}

	@Test
	void theOccasionsStagesCountWhatIsDone()
	{
		wedding();
		List<Progress.Stage> s = Progress.ofOccasion(o);
		assertEquals(Progress.State.PART_DONE, stage(s, Progress.Step.OCCASION).state());
		assertEquals(Progress.State.NOT_STARTED, stage(s, Progress.Step.EVENTS).state());
		Progress.Stage guests = stage(s, Progress.Step.GUESTS);
		assertEquals("2 of 3", guests.count());
		assertTrue(guests.note().contains("1 place to name"));
		assertEquals(Progress.State.NOT_STARTED, stage(s, Progress.Step.INVITATIONS).state());
		assertEquals(Progress.State.NOT_NEEDED, stage(s, Progress.Step.MENUS_SENT).state());
		assertEquals(Progress.State.NOT_NEEDED, stage(s, Progress.Step.REPLIES).state());

		o.setDate(Occasion.parseDate("12 May 2026"));
		o.setSender("Lydia and Isaac");
		o.setReplyTo("lydia@example.com");
		o.logSent(smiths, Messages.INVITATION, "email", null);
		s = Progress.ofOccasion(o);
		assertEquals(Progress.State.DONE, stage(s, Progress.Step.OCCASION).state());
		assertEquals(Progress.State.DONE, stage(s, Progress.Step.INVITATIONS).state());
	}

	@Test
	void anEventsStagesFollowItsTablesSeatsAndMenu()
	{
		wedding();
		List<Progress.Stage> s = Progress.ofEvent(o, breakfast);
		assertEquals(Progress.State.NOT_STARTED, stage(s, Progress.Step.TABLES).state());
		assertEquals(Progress.State.NOT_NEEDED, stage(s, Progress.Step.MENU).state());
		assertEquals(Progress.State.NOT_NEEDED, stage(s, Progress.Step.CHOICES).state());

		RoundTable t = new RoundTable(1, "", 8);
		breakfast.getPlan().add(t);
		breakfast.getPlan().renumber();
		breakfast.getSeating().seat(smiths.getLead(), new Seat(1, 0));
		Menu.Course main = new Menu.Course("Main");
		Menu.Dish beef = new Menu.Dish(1, "Beef");
		main.dishes().add(beef);
		breakfast.getMenu().courses().add(main);
		o.getGuests().membersOf(smiths).get(1).diets().add(Diet.VEGETARIAN);
		breakfast.choose(smiths.getLead(), main, beef);

		s = Progress.ofEvent(o, breakfast);
		assertEquals(Progress.State.DONE, stage(s, Progress.Step.TABLES).state());
		Progress.Stage seats = stage(s, Progress.Step.SEATS);
		assertEquals("1 of 3", seats.count());
		Progress.Stage menu = stage(s, Progress.Step.MENU);
		assertTrue(menu.warn(), "nothing vegetarian");
		assertTrue(menu.note().contains("nothing for some guests' needs in Main"));
		Progress.Stage choices = stage(s, Progress.Step.CHOICES);
		assertEquals("1 of 2", choices.count());
		assertTrue(choices.note().contains("1 place not yet named"));

		List<Progress.Stage> party = Progress.ofEvent(o, evening);
		assertEquals(Progress.State.NOT_NEEDED, stage(party, Progress.Step.SEATS).state());
		evening.setCapacity(2);
		Progress.Stage room = stage(Progress.ofEvent(o, evening), Progress.Step.TABLES);
		assertTrue(room.warn(), "3 invited for 2");

		Progress.Stage sent = stage(Progress.ofOccasion(o), Progress.Step.MENUS_SENT);
		assertEquals("0 of 1", sent.count());
		o.logSent(smiths, Messages.MENU_CHOICES, "email", null);
		assertEquals(Progress.State.DONE, stage(Progress.ofOccasion(o), Progress.Step.MENUS_SENT).state());
	}
}
