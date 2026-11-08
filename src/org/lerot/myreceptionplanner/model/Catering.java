package org.lerot.myreceptionplanner.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/*
 * What the caterers need to know about an event, from those invited: how
 * many have each dietary need (known before any menu is written), and once
 * there is a menu, how many of each dish, overall and at each table, who
 * has not chosen, and choices that do not suit a guest's needs. Only named
 * guests have needs and choices; places not yet named are counted apart.
 */
public class Catering
{
	public record DishCount(Menu.Course course, Menu.Dish dish, int count) {}

	// each need, with how many invited guests have it (needs no one has are left out)
	public static Map<Diet, Integer> dietCounts(Event event)
	{
		Map<Diet, Integer> out = new EnumMap<>(Diet.class);
		for (Guest g : event.invited())
			for (Diet d : g.diets())
				out.merge(d, 1, Integer::sum);
		return out;
	}

	// the invited guests with a dietary need or note, by where they sit (unseated last), then name
	public static List<Guest> withNeeds(Event event)
	{
		return event.invited().stream()
				.filter(g -> g.hasDietaryNeeds() || !event.noteFor(g).isEmpty())
				.sorted(bySeat(event))
				.toList();
	}

	// each dish, with how many of these guests chose it, course by course
	public static List<DishCount> dishCounts(Event event, List<Guest> guests)
	{
		List<DishCount> out = new ArrayList<>();
		for (Menu.Course c : event.getMenu().courses())
			for (Menu.Dish d : c.dishes())
			{
				int n = (int) guests.stream().filter(g -> event.choice(g, c) == d).count();
				out.add(new DishCount(c, d, n));
			}
		return out;
	}

	public static List<DishCount> dishCounts(Event event)
	{
		return dishCounts(event, List.copyOf(event.invited()));
	}

	// invited named guests who have not chosen a dish for every course
	public static List<Guest> notChosen(Event event)
	{
		if (!event.hasMenu()) return List.of();
		return event.invited().stream().filter(g -> g.isNamed() && !event.hasChosenAll(g)).sorted(bySeat(event)).toList();
	}

	// invited places not yet named, who choose and give their needs once named
	public static int notNamed(Event event)
	{
		return (int) event.invited().stream().filter(Guest::isPlaceholder).count();
	}

	// invited guests who have chosen something that does not suit their needs
	public static List<Guest> unsuitable(Event event)
	{
		return event.invited().stream().filter(g -> !event.unsuitable(g).isEmpty()).sorted(bySeat(event)).toList();
	}

	// all those invited, by where they sit (unseated last), then name
	public static List<Guest> inSeatOrder(Event event)
	{
		return event.invited().stream().sorted(bySeat(event)).toList();
	}

	// "Table 3, seat 5", or "" if not seated
	public static String seatOf(Event event, Guest g)
	{
		Seat s = event.getSeating().seatOf(g);
		if (s == null) return "";
		return event.getPlan().find(s.tableId())
				.map(t -> event.getPlan().label(t) + ", seat " + t.seatNumber(s.chair())).orElse("");
	}

	private static Comparator<Guest> bySeat(Event event)
	{
		List<Table> order = Checks.inGuideOrder(event.getPlan());
		return Comparator.comparingInt((Guest g) ->
				{
					Seat s = event.getSeating().seatOf(g);
					if (s == null) return Integer.MAX_VALUE;
					int t = order.indexOf(event.getPlan().find(s.tableId()).orElse(null));
					return t * 1000 + event.getPlan().find(s.tableId()).map(x -> x.seatNumber(s.chair())).orElse(0);
				})
				.thenComparing(Guest::displayName, String.CASE_INSENSITIVE_ORDER);
	}
}
