package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Dietary needs, menus, choices and the caterers' numbers
class MenuTest
{
	@TempDir
	Path folder;

	private Occasion o;
	private Event breakfast;
	private Menu.Course starter, main;
	private Menu.Dish soup, pate, beef, risotto;
	private Guest ann, bob, cath;

	private void wedding()
	{
		o = new Occasion("Wedding", new GuestList());
		breakfast = new Event("Wedding Breakfast", "wb.xml", new Plan(), new Seating());
		o.addEvent(breakfast);
		GuestList g = o.getGuests();
		ann = g.createParty("Ann Smith", 1, "").getLead();
		bob = g.createParty("Bob Jones", 1, "").getLead();
		cath = g.createParty("Cath Brown", 1, "").getLead();
		o.tidy();
		ann.diets().addAll(Set.of(Diet.VEGAN, Diet.NUT_FREE));
		ann.setDietNote("no sesame");
		bob.diets().add(Diet.VEGETARIAN);
		Menu m = breakfast.getMenu();
		starter = new Menu.Course("Starter");
		soup = new Menu.Dish(1, "Soup");
		soup.suits().addAll(Set.of(Diet.VEGAN, Diet.GLUTEN_FREE, Diet.NUT_FREE));
		pate = new Menu.Dish(2, "Pâté");
		starter.dishes().addAll(List.of(soup, pate));
		main = new Menu.Course("Main");
		beef = new Menu.Dish(3, "Beef");
		risotto = new Menu.Dish(4, "Risotto");
		risotto.suits().addAll(Set.of(Diet.VEGETARIAN, Diet.VEGAN));
		main.dishes().addAll(List.of(beef, risotto));
		m.courses().addAll(List.of(starter, main));
	}

	@Test
	void aVeganDishSuitsAVegetarianButNotTheOtherWay()
	{
		assertTrue(Diet.suits(EnumSet.of(Diet.VEGAN), Diet.VEGETARIAN));
		assertFalse(Diet.suits(EnumSet.of(Diet.VEGETARIAN), Diet.VEGAN));
		assertEquals(Set.of(Diet.NUT_FREE), Diet.unmet(EnumSet.of(Diet.VEGAN), EnumSet.of(Diet.VEGAN, Diet.NUT_FREE)));
		assertEquals(EnumSet.of(Diet.VEGAN, Diet.GLUTEN_FREE), Diet.fromCodes("vg, gf x"));
	}

	@Test
	void dietaryNeedsAreCountedBeforeThereIsAnyMenu()
	{
		wedding();
		breakfast.getMenu().courses().clear();
		Map<Diet, Integer> counts = Catering.dietCounts(breakfast);
		assertEquals(Map.of(Diet.VEGAN, 1, Diet.NUT_FREE, 1, Diet.VEGETARIAN, 1), counts);
		assertEquals(List.of(ann, bob), Catering.withNeeds(breakfast));
		assertTrue(Catering.notChosen(breakfast).isEmpty(), "nothing to choose yet");
	}

	@Test
	void choicesAreCountedAndUnsuitableOnesFound()
	{
		wedding();
		breakfast.choose(ann, starter, soup);
		breakfast.choose(ann, main, beef);      // not vegan
		breakfast.choose(bob, starter, soup);
		breakfast.choose(bob, main, risotto);
		assertEquals(List.of(beef), breakfast.unsuitable(ann));
		assertEquals(List.of(ann), Catering.unsuitable(breakfast));
		assertEquals(List.of(cath), Catering.notChosen(breakfast));
		List<Catering.DishCount> counts = Catering.dishCounts(breakfast);
		assertEquals(List.of(2, 0, 1, 1), counts.stream().map(Catering.DishCount::count).toList());
	}

	@Test
	void choicesGoWithTheInvitationAndTheDish()
	{
		wedding();
		breakfast.choose(ann, main, beef);
		breakfast.choose(bob, main, risotto);
		breakfast.setNote(bob, "leaving early");
		breakfast.uninvite(bob, o.getGuests());
		assertNull(breakfast.choice(bob, main));
		assertEquals("", breakfast.noteFor(bob));
		main.dishes().remove(beef);
		breakfast.tidy(o.getGuests());
		assertNull(breakfast.choice(ann, main), "the dish is off the menu");
	}

	@Test
	void dietsMenusAndChoicesAreSaved() throws IOException
	{
		wedding();
		breakfast.choose(ann, starter, soup);
		breakfast.choose(ann, main, risotto);
		breakfast.setNote(ann, "high chair");
		OccasionXml.save(o, folder);

		Occasion loaded = OccasionXml.load(folder);
		Event b = loaded.events().get(0);
		Guest a = loaded.getGuests().guests().stream().filter(x -> x.getName().equals("Ann Smith")).findFirst().orElseThrow();
		assertEquals(Set.of(Diet.VEGAN, Diet.NUT_FREE), a.diets());
		assertEquals("no sesame", a.getDietNote());
		assertEquals(List.of("Starter", "Main"), b.getMenu().courses().stream().map(Menu.Course::getName).toList());
		Menu.Course m = b.getMenu().courses().get(1);
		assertEquals("Risotto", b.choice(a, m).getName());
		assertEquals(Set.of(Diet.VEGETARIAN, Diet.VEGAN), b.choice(a, m).suits());
		assertEquals("Pâté", b.getMenu().dish(2).orElseThrow().getName());
		assertEquals("high chair", b.noteFor(a));
	}

	@Test
	void theCateringReportHasItsSections()
	{
		wedding();
		breakfast.choose(ann, main, beef);
		var report = new org.lerot.myreceptionplanner.reports.CateringReport(o, breakfast);
		List<String> headings = report.sections.stream().map(org.lerot.myreceptionplanner.reports.CateringReport.Section::heading).toList();
		assertEquals("Dietary needs", headings.get(0));
		assertTrue(headings.contains("Dishes"));
		assertTrue(headings.contains("Guests with dietary needs or notes"));
		assertTrue(headings.stream().anyMatch(h -> h.contains("do not suit")));
		assertTrue(headings.contains("Not yet chosen"));
		assertTrue(report.html().contains("no sesame"));
	}

	@Test
	void onlyNamedGuestsChooseSoChoicesFollowThePerson()
	{
		wedding();
		GuestList g = o.getGuests();
		Party smiths = ann.getParty();
		g.setPlaces(smiths, 2, x -> false);
		o.tidy();
		Guest plus1 = g.membersOf(smiths).get(1);
		breakfast.choose(plus1, main, risotto);
		breakfast.setNote(plus1, "high chair");
		assertNull(breakfast.choice(plus1, main), "a place not yet named does not choose");
		assertEquals("", breakfast.noteFor(plus1));
		assertEquals(1, Catering.notNamed(breakfast));
		assertFalse(Catering.notChosen(breakfast).contains(plus1));

		g.rename(plus1, "Dora Smith");
		breakfast.choose(plus1, main, risotto);
		plus1.diets().add(Diet.VEGETARIAN);
		breakfast.getSeating().seat(plus1, new Seat(1, 0));
		breakfast.getSeating().seat(plus1, new Seat(1, 3));      // reseated: the choice goes too
		assertEquals(risotto, breakfast.choice(plus1, main));

		g.rename(plus1, "");                                     // back to a place: no needs or choices
		o.tidy();
		assertTrue(plus1.diets().isEmpty());
		assertNull(breakfast.choice(plus1, main));
	}
}
