package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Messages to the parties' leads
class MessagesTest
{
	@TempDir
	Path folder;

	private Occasion o;
	private Event breakfast, evening;
	private Party smiths;

	private void wedding()
	{
		o = new Occasion("Lydia and Isaac's Wedding", new GuestList());
		o.setDate(Occasion.parseDate("12 May 2026"));
		o.setSender("Lydia and Isaac");
		o.setReplyTo("lydia@example.com");
		breakfast = new Event("Wedding Breakfast", "wb.xml", new Plan(), new Seating());
		breakfast.setPlace("The Old Barn");
		breakfast.setTime("2pm");
		evening = new Event("Evening Party", "ev.xml", new Plan(), new Seating());
		evening.setTime("7.30pm");
		o.addEvent(breakfast);
		o.addEvent(evening);
		smiths = o.getGuests().createParty("Ann Smith", 3, "");
		o.getGuests().rename(o.getGuests().membersOf(smiths).get(1), "Bob Smith");
		o.tidy();
		RoundTable t = new RoundTable(1, "", 8);
		breakfast.getPlan().add(t);
		breakfast.getPlan().renumber();
		breakfast.getSeating().seat(smiths.getLead(), new Seat(1, 0));
	}

	private String fill(String text, Event e)
	{
		return Messages.fill(new MessageTemplate("t", "", text), o, smiths, e).body();
	}

	@Test
	void fillInsAreCompletedForTheParty()
	{
		wedding();
		assertEquals("Dear Ann,", fill("Dear {leadfirst},", null));
		assertEquals("Ann Smith, Bob Smith and a guest", fill("{guests}", null));
		assertEquals("Lydia and Isaac's Wedding on 12 May 2026", fill("{occasion} on {date}", null));
		assertEquals("• Wedding Breakfast, The Old Barn, 12 May 2026 at 2pm\n  • Evening Party, 12 May 2026 at 7.30pm",
				fill("{events}", null));
		assertEquals("{nonsense} stays", fill("{nonsense} stays", null));
		assertEquals("Lydia and Isaac", fill("{from}", null));
	}

	@Test
	void seatsSayWhereEachSits()
	{
		wedding();
		String seats = fill("{seats}", breakfast);
		assertTrue(seats.contains("Ann Smith: Table 1, seat 1"));
		assertTrue(seats.contains("Bob Smith: seat to be arranged"));
		evening.setKind(Event.Kind.UNRESERVED);
		assertTrue(fill("{seats}", evening).contains("Bob Smith: open seating"));
	}

	@Test
	void missingListsWhatIsStillNeeded()
	{
		wedding();
		Menu.Course main = new Menu.Course("Main");
		Menu.Dish beef = new Menu.Dish(1, "Beef");
		main.dishes().add(beef);
		breakfast.getMenu().courses().add(main);
		String missing = fill("{missing}", null);
		assertTrue(missing.contains("the name of the guest coming with you"));
		assertTrue(missing.contains("any dietary needs or allergies (Ann Smith, Bob Smith)"));
		assertTrue(missing.contains("menu choices for Wedding Breakfast (Ann Smith, Bob Smith)"));
		assertTrue(missing.contains("Main: Beef"));

		o.getGuests().setPlaces(smiths, 2, g -> false);
		o.tidy();
		for (Guest g : o.getGuests().membersOf(smiths))
		{
			g.diets().add(Diet.VEGETARIAN);
			breakfast.choose(g, main, beef);
		}
		assertEquals("We have everything we need from you — thank you!", fill("{missing}", null));
	}

	@Test
	void theMenuMessageThanksForChoicesMadeAndAsksForTheRest()
	{
		wedding();
		Menu.Course starter = new Menu.Course("Starter"), main = new Menu.Course("Main");
		Menu.Dish soup = new Menu.Dish(1, "Soup"), beef = new Menu.Dish(2, "Beef");
		starter.dishes().add(soup);
		main.dishes().add(beef);
		breakfast.getMenu().courses().addAll(List.of(starter, main));
		Menu.Course pud = new Menu.Course("Pudding");
		Menu.Dish trifle = new Menu.Dish(1, "Trifle");
		pud.dishes().add(trifle);
		evening.getMenu().courses().add(pud);
		Guest ann = smiths.getLead(), bob = o.getGuests().membersOf(smiths).get(1);

		assertEquals("", fill("{menuschosen}", null));
		String ask = fill("{menustochoose}", null);
		assertTrue(ask.startsWith("We would love your menu choices for these events"));
		assertTrue(ask.contains("Wedding Breakfast, The Old Barn, 12 May 2026 at 2pm"));
		assertTrue(ask.contains("• Ann Smith: please choose"));

		evening.choose(ann, pud, trifle);
		evening.choose(bob, pud, trifle);
		breakfast.choose(ann, main, beef);
		assertEquals("Thank you for your menu choices for Evening Party.", fill("{menuschosen}", null));
		ask = fill("{menustochoose}", null);
		assertTrue(ask.startsWith("We would love your menu choices for this event"));
		assertFalse(ask.contains("Evening Party"));
		assertTrue(ask.contains("• Ann Smith: Main: Beef (please confirm); Starter still to choose"), ask);
		assertEquals(List.of(breakfast), Messages.menusToChoose(o, smiths));

		breakfast.choose(ann, starter, soup);
		breakfast.choose(bob, starter, soup);
		breakfast.choose(bob, main, beef);
		assertEquals("Thank you for your menu choices for Wedding Breakfast and Evening Party.", fill("{menuschosen}", null));
		assertEquals("", fill("{menustochoose}", null));
	}

	@Test
	void phoneNumbersBecomeInternationalForWhatsApp()
	{
		assertEquals("447700900123", Messages.whatsAppNumber("07700 900123", "44"));
		assertEquals("447700900123", Messages.whatsAppNumber("+44 7700 900123", "44"));
		assertEquals("33612345678", Messages.whatsAppNumber("0033 6 12 34 56 78", "44"));
		assertEquals("", Messages.whatsAppNumber("", "44"));
	}

	@Test
	void templatesSettingsAndTheLogAreSaved() throws IOException
	{
		wedding();
		o.setCountryCode("+33");
		o.templates().add(new MessageTemplate("Directions", "How to find us", "Dear {leadfirst},\n\n   indented line\n\n{from}"));
		o.logSent(smiths, "Invitation", "email", breakfast);
		OccasionXml.save(o, folder);
		Occasion loaded = OccasionXml.load(folder);
		assertEquals("Lydia and Isaac", loaded.getSender());
		assertEquals("lydia@example.com", loaded.getReplyTo());
		assertEquals("33", loaded.getCountryCode());
		assertEquals(6, loaded.templates().size());
		MessageTemplate d = loaded.templates().get(5);
		assertEquals("How to find us", d.getSubject());
		assertEquals("Dear {leadfirst},\n\n   indented line\n\n{from}", d.getBody());
		Party p = loaded.getGuests().parties().get(0);
		Messages.Sent s = loaded.lastSent(p, "Invitation");
		assertEquals("email", s.how());
		assertEquals("Wedding Breakfast", s.event());
		assertNull(loaded.lastSent(p, "Reminder"));
	}

	@Test
	void theLogForgetsAPartyRemoved()
	{
		wedding();
		o.logSent(smiths, "Invitation", "letter", null);
		o.getGuests().removeParty(smiths);
		o.tidy();
		assertTrue(o.sent().isEmpty());
	}

	@Test
	void theStandardMessagesReadWell()
	{
		wedding();
		for (MessageTemplate t : Messages.standard())
		{
			Messages.Message m = Messages.fill(t, o, smiths, breakfast);
			assertFalse(m.body().contains("{"), t.getName() + " left a fill-in: " + m.body());
			assertFalse(m.subject().contains("{"));
		}
		Messages.Message inv = Messages.fill(Messages.standard().get(0), o, smiths, breakfast);
		assertTrue(inv.body().contains("Please let us know whether you can come (lydia@example.com)."));
	}

	@Test
	void lettersAndLabelsBecomePdfs() throws Exception
	{
		wedding();
		smiths.getLead().getContact().setAddress("1 High Street\nLichfield\nWS13 6AA");
		Path letters = folder.resolve("letters.pdf"), labels = folder.resolve("labels.pdf");
		org.lerot.myreceptionplanner.reports.LettersPdf.letters(o, List.of(new org.lerot.myreceptionplanner.reports.LettersPdf.Letter(
				smiths, Messages.fill(Messages.standard().get(0), o, smiths, null))), letters);
		org.lerot.myreceptionplanner.reports.LettersPdf.labels(List.of(smiths), labels);
		assertTrue(Files.size(letters) > 500);
		assertTrue(Files.size(labels) > 300);
		assertEquals(List.of("Ann Smith", "1 High Street", "Lichfield", "WS13 6AA"),
				org.lerot.myreceptionplanner.reports.LettersPdf.addressLines(smiths));
	}
}
