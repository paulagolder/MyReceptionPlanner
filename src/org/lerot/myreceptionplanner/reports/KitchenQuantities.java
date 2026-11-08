package org.lerot.myreceptionplanner.reports;

import org.lerot.myreceptionplanner.model.Catering;
import org.lerot.myreceptionplanner.model.Diet;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Menu;
import org.lerot.myreceptionplanner.model.Occasion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/*
 * How much of each dish the kitchen is to make for an event: course by
 * course, each dish with how many chose it and how many of those must meet
 * a dietary need (or have a note), then how many have still to choose or are
 * places not yet named, and any choices that do not suit.
 */
public class KitchenQuantities extends Report
{
	public KitchenQuantities(Occasion occasion, Event event)
	{
		super(occasion.heading(), "Kitchen quantities: " + occasion.headingOf(event));
		int named = (int) event.invited().stream().filter(Guest::isNamed).count();
		int notNamed = Catering.notNamed(event);
		if (!event.hasMenu())
		{
			sections.add(new Section("No menu yet", List.of(), List.of(),
					"Set the menu with Occasion ▾ → Menu…. The dietary needs are counted in the catering list."));
			return;
		}
		sections.add(new Section("Guests", List.of(), List.of(),
				plural(named, "named guest") + " invited" + (notNamed > 0 ? ", and " + plural(notNamed, "place") + " not yet named" : "")
						+ "; " + Catering.notChosen(event).size() + " still to choose."));
		for (Menu.Course c : event.getMenu().courses())
		{
			List<List<String>> rows = new ArrayList<>();
			int total = 0;
			for (Menu.Dish d : c.dishes())
			{
				List<Guest> having = event.invited().stream().filter(g -> event.choice(g, c) == d).toList();
				total += having.size();
				rows.add(List.of(d.withDiets(), Integer.toString(having.size()), needs(having, event)));
			}
			int missing = (int) event.invited().stream().filter(g -> g.isNamed() && event.choice(g, c) == null).count();
			String foot = total + " in all";
			if (missing > 0) foot += "; " + plural(missing, "named guest") + " still to choose";
			if (notNamed > 0) foot += "; " + plural(notNamed, "place") + " not yet named";
			sections.add(new Section(c.getName(), List.of("Dish", "Number", "Of which"), rows, foot + "."));
		}
		List<List<String>> bad = new ArrayList<>();
		for (Guest g : Catering.unsuitable(event))
			for (Menu.Dish d : event.unsuitable(g))
				bad.add(List.of(g.displayName(), Catering.seatOf(event, g), d.getName(),
						"not " + Diet.codes(Diet.unmet(d.suits(), g.diets()))));
		if (!bad.isEmpty())
			sections.add(new Section("⚠ Choices that do not suit the guest", List.of("Guest", "Seat", "Dish", "Problem"),
					bad, ""));
	}

	private static String plural(int n, String what)
	{
		return n + " " + what + (n == 1 ? "" : "s");
	}

	// "3 nf, 1 gf, 2 with notes" for the guests having a dish
	static String needs(List<Guest> having, Event event)
	{
		Map<Diet, Integer> counts = new EnumMap<>(Diet.class);
		int notes = 0;
		for (Guest g : having)
		{
			g.diets().forEach(d -> counts.merge(d, 1, Integer::sum));
			if (!g.getDietNote().isEmpty() || !event.noteFor(g).isEmpty()) notes++;
		}
		List<String> parts = new ArrayList<>();
		counts.forEach((d, n) -> parts.add(n + " " + d.code()));
		if (notes > 0) parts.add(notes + (notes == 1 ? " with a note" : " with notes"));
		return String.join(", ", parts);
	}
}
