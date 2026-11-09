package org.lerot.myreceptionplanner.reports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lerot.myreceptionplanner.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// The kitchen's quantities and service by table
class KitchenReportsTest
{
	@TempDir
	Path folder;

	private Occasion o;
	private Event e;
	private Menu.Course main;
	private Menu.Dish beef, risotto;
	private Guest ann, bob, cath, plus1;

	private void breakfast()
	{
		o = new Occasion("Wedding", new GuestList());
		e = new Event("Wedding Breakfast", "wb.xml", new Plan(), new Seating());
		e.setCatered(true);
		o.addEvent(e);
		RoundTable t1 = new RoundTable(1, "", 6), t2 = new RoundTable(2, "", 6);
		t1.moveTo(200, 300);
		t2.moveTo(600, 300);
		e.getPlan().add(t1);
		e.getPlan().add(t2);
		e.getPlan().renumber();
		GuestList g = o.getGuests();
		Party smiths = g.createParty("Ann Smith", 2, "");
		ann = smiths.getLead();
		plus1 = g.membersOf(smiths).get(1);
		bob = g.createParty("Bob Jones", 1, "").getLead();
		cath = g.createParty("Cath Brown", 1, "").getLead();
		o.tidy();
		ann.diets().addAll(Set.of(Diet.VEGAN, Diet.NUT_FREE));
		main = new Menu.Course("Main");
		beef = new Menu.Dish(1, "Beef");
		risotto = new Menu.Dish(2, "Risotto");
		risotto.suits().addAll(Set.of(Diet.VEGETARIAN, Diet.VEGAN));
		main.dishes().addAll(List.of(beef, risotto));
		e.getMenu().courses().add(main);
		e.getSeating().seat(ann, new Seat(1, 2));
		e.getSeating().seat(bob, new Seat(1, 0));
		e.getSeating().seat(plus1, new Seat(2, 0));
		e.choose(ann, main, risotto);
		e.choose(bob, main, beef);
		e.setNote(bob, "leaving early");
	}

	@Test
	void quantitiesCountEachDishAndTheNeedsAmongThem()
	{
		breakfast();
		KitchenQuantities q = new KitchenQuantities(o, e);
		Report.Section mainSection = q.sections.stream().filter(s -> s.heading().equals("Main")).findFirst().orElseThrow();
		assertEquals(List.of("Beef", "1", "1 with a note"), mainSection.rows().get(0));
		assertEquals(List.of("Risotto (v vg)", "1", "1 vg, 1 nf"), mainSection.rows().get(1));
		assertTrue(mainSection.footnote().contains("1 named guest still to choose"), "Cath");
		assertTrue(mainSection.footnote().contains("1 place not yet named"), "Ann Smith+1");
	}

	@Test
	void serviceByTableGivesEachSeatItsDishesInSeatOrder()
	{
		breakfast();
		ServiceByTable s = new ServiceByTable(o, e);
		Report.Section t1 = s.sections.get(0);
		assertEquals("Table 1", t1.heading());
		assertEquals(List.of("Seat", "Guest", "Main", "Needs and notes"), t1.columns());
		assertEquals(List.of("1", "Bob Jones", "Beef", "leaving early"), t1.rows().get(0));
		assertEquals(List.of("3", "Ann Smith", "Risotto", "vg nf"), t1.rows().get(1));
		assertEquals("Main: 1 Beef, 1 Risotto", t1.footnote());
		Report.Section t2 = s.sections.get(1);
		assertEquals(List.of("1", "Ann Smith+1 (not named)", "–", ""), t2.rows().get(0));
		Report.Section unseated = s.sections.get(2);
		assertEquals("Invited but not seated", unseated.heading());
		assertEquals(List.of("", "Cath Brown", "?", ""), unseated.rows().get(0));
	}

	@Test
	void anUnreservedPartyHasNoService()
	{
		breakfast();
		e.setKind(Event.Kind.UNRESERVED);
		assertEquals("No seats", new ServiceByTable(o, e).sections.get(0).heading());
	}

	@Test
	void eachReportBecomesAPdf() throws Exception
	{
		breakfast();
		for (Report r : List.of(new KitchenQuantities(o, e), new ServiceByTable(o, e), new CateringReport(o, e)))
		{
			Path f = folder.resolve(r.getClass().getSimpleName() + ".pdf");
			ReportPdf.write(r, f, true);
			assertTrue(Files.size(f) > 500);
		}
	}
}
