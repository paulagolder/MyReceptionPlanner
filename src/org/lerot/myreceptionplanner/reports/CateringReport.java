package org.lerot.myreceptionplanner.reports;

import org.lerot.myreceptionplanner.model.Catering;
import org.lerot.myreceptionplanner.model.Diet;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Menu;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.tables.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * The catering list for an event, as headed sections of rows, for showing on
 * screen or writing as a PDF: dietary needs and how many have each, then if
 * there is a menu the dishes and how many of each (and a grid of how many at
 * each table, a dish to a row and a table to a column), then
 * everyone with a need or a note, then choices that do not suit a guest and
 * who has yet to choose.
 */
public class CateringReport extends Report
{
	public CateringReport(Occasion occasion, Event event)
	{
		super(occasion.heading(), "Catering: " + occasion.headingOf(event));
		emblem = occasion.getEmblem();
		int invited = event.invited().size();

		List<List<String>> needs = new ArrayList<>();
		for (Map.Entry<Diet, Integer> e : Catering.dietCounts(event).entrySet())
			needs.add(List.of(e.getKey() + " (" + e.getKey().code() + ")", Integer.toString(e.getValue())));
		long notes = event.invited().stream().filter(g -> !g.getDietNote().isEmpty()).count();
		sections.add(new Section("Dietary needs", List.of("Need", "Guests"), needs,
				invited + (invited == 1 ? " guest" : " guests") + " invited" + (needs.isEmpty() ? "; no dietary needs given" : "")
						+ (notes > 0 ? "; " + notes + " with a note (below)" : "")));

		if (event.hasMenu())
		{
			List<List<String>> dishes = new ArrayList<>();
			for (Catering.DishCount d : Catering.dishCounts(event))
				dishes.add(List.of(d.course().getName(), d.dish().withDiets(), Integer.toString(d.count())));
			int notChosen = Catering.notChosen(event).size();
			int notNamed = Catering.notNamed(event);
			String foot = notChosen == 0 ? "Everyone named has chosen."
					: notChosen + (notChosen == 1 ? " guest has" : " guests have") + " not chosen every course.";
			if (notNamed > 0)
				foot += " " + notNamed + (notNamed == 1 ? " place is" : " places are") + " not yet named; they choose once named.";
			sections.add(new Section("Dishes", List.of("Course", "Dish", "Number"), dishes, foot));
			if (!event.getMenu().getNote().isEmpty())
				sections.add(new Section("Note on the menu", List.of(), List.of(List.of(event.getMenu().getNote())), ""));

			if (event.isSeated() && event.getSeating().seatedCount() > 0)
			{
				// the tables with anyone at them, across; the dishes, down; blank for none
				List<String> columns = new ArrayList<>(List.of("Dish"));
				List<List<Catering.DishCount>> perTable = new ArrayList<>();
				for (Table t : org.lerot.myreceptionplanner.model.Checks.inGuideOrder(event.getPlan()))
				{
					List<Guest> there = event.getSeating().seatedAt(t.getId());
					if (there.isEmpty()) continue;
					columns.add(t.isHead() ? "Head" : t.hasName() ? t.getName() : Integer.toString(t.getNumber()));
					perTable.add(Catering.dishCounts(event, there));
				}
				columns.add("All");
				List<Catering.DishCount> all = Catering.dishCounts(event);
				List<List<String>> grid = new ArrayList<>();
				for (int i = 0; i < all.size(); i++)
				{
					List<String> row = new ArrayList<>(List.of(all.get(i).dish().getName()));
					int seated = 0;
					for (List<Catering.DishCount> at : perTable)
					{
						row.add(countOrBlank(at.get(i).count()));
						seated += at.get(i).count();
					}
					row.add(countOrBlank(seated));
					grid.add(row);
				}
				sections.add(new Section("At each table", columns, grid, "Seated guests only (All is across the tables); the dishes in the order of the menu."));
			}
		}

		List<List<String>> who = new ArrayList<>();
		for (Guest g : Catering.withNeeds(event))
		{
			List<String> chosen = event.getMenu().courses().stream().map(c -> event.choice(g, c))
					.filter(d -> d != null).map(Menu.Dish::getName).toList();
			who.add(List.of(g.displayName(), Catering.seatOf(event, g), g.dietText(),
					String.join(", ", chosen), event.noteFor(g)));
		}
		sections.add(new Section("Guests with dietary needs or notes",
				List.of("Guest", "Seat", "Needs", "Chosen", "Note for this event"), who,
				who.isEmpty() ? "None." : ""));

		List<List<String>> bad = new ArrayList<>();
		for (Guest g : Catering.unsuitable(event))
			for (Menu.Dish d : event.unsuitable(g))
				bad.add(List.of(g.displayName(), Catering.seatOf(event, g), d.getName(),
						"not " + Diet.codes(Diet.unmet(d.suits(), g.diets()))));
		if (!bad.isEmpty())
			sections.add(new Section("⚠ Choices that do not suit the guest", List.of("Guest", "Seat", "Dish", "Problem"),
					bad, ""));

		List<Guest> notChosen = Catering.notChosen(event);
		if (!notChosen.isEmpty())
		{
			List<List<String>> rows = new ArrayList<>();
			for (Guest g : notChosen) rows.add(List.of(g.displayName(), Catering.seatOf(event, g)));
			sections.add(new Section("Not yet chosen", List.of("Guest", "Seat"), rows, ""));
		}
	}

	private static String countOrBlank(int n) { return n == 0 ? "" : Integer.toString(n); }
}
