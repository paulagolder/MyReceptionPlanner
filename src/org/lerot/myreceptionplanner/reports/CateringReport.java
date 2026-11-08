package org.lerot.myreceptionplanner.reports;

import org.lerot.myreceptionplanner.model.Catering;
import org.lerot.myreceptionplanner.model.Diet;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Menu;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * The catering list for an event, as headed sections of rows, for showing on
 * screen or writing as a PDF: dietary needs and how many have each, then if
 * there is a menu the dishes and how many of each (and at each table), then
 * everyone with a need or a note, then choices that do not suit a guest and
 * who has yet to choose.
 */
public class CateringReport
{
	// a heading, column names (may be empty), rows of cells, and a line under it
	public record Section(String heading, List<String> columns, List<List<String>> rows, String footnote) {}

	public final String title;
	public final String subtitle;
	public final List<Section> sections = new ArrayList<>();

	public CateringReport(Occasion occasion, Event event)
	{
		title = occasion.heading();
		subtitle = "Catering: " + occasion.headingOf(event);
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

			if (event.isSeated() && event.getSeating().seatedCount() > 0)
			{
				List<List<String>> tables = new ArrayList<>();
				for (Table t : org.lerot.myreceptionplanner.model.Checks.inGuideOrder(event.getPlan()))
				{
					List<Guest> there = event.getSeating().seatedAt(t.getId());
					if (there.isEmpty()) continue;
					List<String> counts = new ArrayList<>();
					for (Catering.DishCount d : Catering.dishCounts(event, there))
						if (d.count() > 0) counts.add(d.count() + " " + d.dish().getName());
					tables.add(List.of(event.getPlan().label(t), String.join(", ", counts)));
				}
				sections.add(new Section("At each table", List.of("Table", "Dishes"), tables, ""));
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

	// for showing in a dialog
	public String html()
	{
		StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif; margin:8px'>");
		h.append("<h2 style='margin:0'>").append(esc(title)).append("</h2>");
		h.append("<p style='margin-top:2px; color:#6E6E72'>").append(esc(subtitle)).append("</p>");
		for (Section s : sections)
		{
			h.append("<h3 style='margin-bottom:4px'>").append(esc(s.heading())).append("</h3>");
			if (!s.rows().isEmpty())
			{
				h.append("<table border='0' cellspacing='0' cellpadding='3'>");
				if (!s.columns().isEmpty())
				{
					h.append("<tr>");
					for (String c : s.columns()) h.append("<th align='left' bgcolor='#F4F1EA'>").append(esc(c)).append("</th>");
					h.append("</tr>");
				}
				for (List<String> r : s.rows())
				{
					h.append("<tr>");
					for (String c : r) h.append("<td>").append(esc(c)).append("</td>");
					h.append("</tr>");
				}
				h.append("</table>");
			}
			if (!s.footnote().isEmpty())
				h.append("<p style='margin-top:4px; color:#6E6E72'>").append(esc(s.footnote())).append("</p>");
		}
		return h.append("</body></html>").toString();
	}

	private static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
