package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MenuTextTest
{
	@TempDir
	Path folder;

	private static List<String> courses(Menu m)
	{
		return m.courses().stream().map(Menu.Course::getName).toList();
	}

	private static List<String> dishes(Menu m)
	{
		return m.dishes().stream().map(Menu.Dish::withDiets).toList();
	}

	@Test
	void aMenuFileRoundTrip() throws Exception
	{
		Menu m = new Menu();
		m.setNote("All our food is sourced locally.\n\nAsk the steward: \"if in doubt\".");
		Menu.Course first = new Menu.Course("First credenza: cold dishes");
		Menu.Dish tongue = new Menu.Dish(7, "Salted ox tongue, with mostarda");
		tongue.suits().addAll(EnumSet.of(Diet.GLUTEN_FREE, Diet.NUT_FREE));
		first.dishes().add(tongue);
		first.dishes().add(new Menu.Dish(8, "\"Peacock\" # in its feathers"));
		Menu.Course sweets = new Menu.Course("Sweets");
		Menu.Dish nadalin = new Menu.Dish(9, "[Nadalin] of Verona");
		nadalin.suits().add(Diet.VEGETARIAN);
		sweets.dishes().add(nadalin);
		m.courses().addAll(List.of(first, sweets));

		Path f = folder.resolve("banquet.yml");
		MenuText.save(m, "Banquet: the Capulets", f);
		Menu back = MenuText.load(f);
		assertEquals(courses(m), courses(back));
		assertEquals(dishes(m), dishes(back));
		assertEquals(m.getNote(), back.getNote());
		assertEquals(List.of(1, 2, 3), back.dishes().stream().map(Menu.Dish::getId).toList());
		assertTrue(Files.readString(f).contains("      - Salted ox tongue, with mostarda [gf nf]"));
	}

	@Test
	void otherWaysOfWritingIt()
	{
		Menu m = MenuText.fromYaml("""
				menu:
				  title: Supper   # just for us
				  notes: >
				    Served from
				    seven.
				  courses:
				    - course: Starter
				      dishes:
				        - name: Soup
				          suits: [v, gf]
				        - name: Pâté
				          diets:
				            - gf
				    - course: 'Main'
				      dishes:
				      - Beef (gf, df)
				      - "Risotto: wild mushroom [vegan]"
				""");
		assertEquals(List.of("Starter", "Main"), courses(m));
		assertEquals(List.of("Soup (v gf)", "Pâté (gf)", "Beef (gf df)", "Risotto: wild mushroom (vg)"), dishes(m));
		assertEquals("Served from seven.", m.getNote());

		Menu keyed = MenuText.fromYaml("""
				courses:
				  Starters:
				    - Soup [v]
				  Desserts:
				    - Trifle
				""");
		assertEquals(List.of("Starters", "Desserts"), courses(keyed));
		assertEquals(List.of("Soup (v)", "Trifle"), dishes(keyed));
	}

	@Test
	void anXmlMenuStillLoads() throws Exception
	{
		Menu m = new Menu();
		Menu.Course c = new Menu.Course("Main");
		c.dishes().add(new Menu.Dish(1, "Beef"));
		m.courses().add(c);
		Path f = folder.resolve("main_menu.xml");
		OccasionXml.saveMenu(m, f);
		assertEquals(List.of("Beef"), dishes(MenuText.load(f)));
	}

	@Test
	void aCaterersMenuPasted()
	{
		Menu m = MenuText.fromText("""
				WEDDING BREAKFAST MENU
				Mayfield Hall, Saturday 12 May

				STARTERS
				• Leek and potato soup (V) (GF)
				• Chicken liver pâté
				  with toasted brioche and onion chutney
				• Smoked salmon roulade GF

				Main Course:
				Roast sirloin of beef, Yorkshire pudding – £24.50
				or
				Wild mushroom risotto (VG, GF)
				Pan-fried sea bass (gluten free, dairy free)

				Desserts
				1. Sticky toffee pudding
				2. Lemon tart (v)

				(V) Vegetarian  (VG) Vegan  (GF) Gluten free
				Please tell us of any allergies when you reply to the invitation.
				""");
		assertEquals(List.of("Starters", "Main Course", "Desserts"), courses(m));
		assertEquals(List.of("Leek and potato soup (v gf)", "Chicken liver pâté with toasted brioche and onion chutney",
				"Smoked salmon roulade (gf)", "Roast sirloin of beef, Yorkshire pudding", "Wild mushroom risotto (vg gf)",
				"Pan-fried sea bass (gf df)", "Sticky toffee pudding", "Lemon tart (v)"), dishes(m));
		assertEquals("Mayfield Hall, Saturday 12 May\nPlease tell us of any allergies when you reply to the invitation.",
				m.getNote());
	}

	@Test
	void withNoHeadingsEachBlockIsACourse()
	{
		Menu m = MenuText.fromText("""
				Soup of the day (v)
				Prawn cocktail

				Roast chicken
				Nut roast (vg)
				""");
		assertEquals(List.of("Course 1", "Course 2"), courses(m));
		assertEquals(4, m.dishes().size());
	}

	@Test
	void dietMarks()
	{
		Set<Diet> d = EnumSet.noneOf(Diet.class);
		assertEquals("Henry's pie", MenuText.withoutDiets("Henry's pie (Vegetarian / Nut-free)", d));
		assertEquals(EnumSet.of(Diet.VEGETARIAN, Diet.NUT_FREE), d);
		d.clear();
		assertEquals("Eggs (Florentine)", MenuText.withoutDiets("Eggs (Florentine)", d));
		assertTrue(d.isEmpty());
	}
}
