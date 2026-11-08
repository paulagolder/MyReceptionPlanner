package org.lerot.myreceptionplanner.reports;

import org.lerot.myreceptionplanner.model.Catering;
import org.lerot.myreceptionplanner.model.Checks;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Menu;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
 * Which dish goes to which seat: for each table (or pew) in the order of the
 * guide, a line for each seat with someone in it, in seat order, giving the
 * guest, their dish for each course and their needs and note; under it the
 * table's dishes counted, to plate up a table at a time. Then any invited
 * guest without a seat. "?" is a dish not yet chosen; "–" a place not yet
 * named, who chooses once named.
 */
public class ServiceByTable extends Report
{
	public ServiceByTable(Occasion occasion, Event event)
	{
		super(occasion.heading(), "Service by table: " + occasion.headingOf(event));
		if (!event.isSeated())
		{
			sections.add(new Section("No seats", List.of(), List.of(),
					event.getName() + " is a party, unreserved: no one has a seat to be served at."));
			return;
		}
		List<Menu.Course> courses = event.getMenu().courses();
		List<String> columns = new ArrayList<>(List.of("Seat", "Guest"));
		courses.forEach(c -> columns.add(c.getName()));
		columns.add("Needs and notes");
		for (Table t : Checks.inGuideOrder(event.getPlan()))
		{
			List<List<String>> rows = new ArrayList<>();
			List<Guest> there = new ArrayList<>();
			for (int n = 1; n <= t.places(); n++)
			{
				int chair = t.chairOfSeat(n);
				Optional<Guest> g = event.getSeating().guestAt(t.getId(), chair);
				if (g.isEmpty()) continue;
				there.add(g.get());
				rows.add(line(Integer.toString(n), g.get(), event, courses));
			}
			if (rows.isEmpty()) continue;
			sections.add(new Section(event.getPlan().label(t), columns, rows, counts(there, event, courses)));
		}
		List<Guest> unseated = event.getSeating().unseated(List.copyOf(event.invited())).stream()
				.sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName())).toList();
		if (!unseated.isEmpty())
		{
			List<List<String>> rows = new ArrayList<>();
			for (Guest g : unseated) rows.add(line("", g, event, courses));
			sections.add(new Section("Invited but not seated", columns, rows, ""));
		}
		if (sections.isEmpty())
			sections.add(new Section("No one seated yet", List.of(), List.of(), ""));
	}

	private static List<String> line(String seat, Guest g, Event event, List<Menu.Course> courses)
	{
		List<String> row = new ArrayList<>(List.of(seat, g.isNamed() ? g.displayName() : g.displayName() + " (not named)"));
		for (Menu.Course c : courses)
		{
			Menu.Dish d = event.choice(g, c);
			row.add(d != null ? d.getName() : g.isNamed() ? "?" : "–");
		}
		String needs = g.dietText();
		String note = event.noteFor(g);
		row.add(needs.isEmpty() ? note : note.isEmpty() ? needs : needs + "; " + note);
		return row;
	}

	// "Starter: 3 Leek soup, 2 Pâté  ·  Main: …"
	private static String counts(List<Guest> there, Event event, List<Menu.Course> courses)
	{
		if (courses.isEmpty()) return there.size() + (there.size() == 1 ? " guest" : " guests");
		List<String> parts = new ArrayList<>();
		for (Menu.Course c : courses)
		{
			List<String> dishes = new ArrayList<>();
			for (Catering.DishCount d : Catering.dishCounts(event, there))
				if (d.course() == c && d.count() > 0) dishes.add(d.count() + " " + d.dish().getName());
			long open = there.stream().filter(g -> event.choice(g, c) == null).count();
			if (open > 0) dishes.add(open + " ?");
			parts.add(c.getName() + ": " + String.join(", ", dishes));
		}
		return String.join("   ·   ", parts);
	}
}
