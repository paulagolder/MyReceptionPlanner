package org.lerot.myreceptionplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/*
 * An event's menu: courses in order (Starter, Main, Dessert ...), each with
 * the dishes to choose from, each dish marked with the diets it suits. An
 * event with no courses has no menu. Dishes have ids within the event, so
 * guests' choices can refer to them in its file.
 */
public class Menu
{
	public static class Course
	{
		private String name;
		private final List<Dish> dishes = new ArrayList<>();

		public Course(String name) { setName(name); }

		public String getName() { return name; }

		public void setName(String name) { this.name = name == null || name.isBlank() ? "Course" : name.trim(); }

		public List<Dish> dishes() { return dishes; }

		@Override
		public String toString() { return name; }
	}

	public static class Dish
	{
		private final int id;
		private String name;
		private final Set<Diet> suits = EnumSet.noneOf(Diet.class);

		public Dish(int id, String name)
		{
			this.id = id;
			setName(name);
		}

		public int getId() { return id; }

		public String getName() { return name; }

		public void setName(String name) { this.name = name == null || name.isBlank() ? "Dish" : name.trim(); }

		// the diets it suits; change it directly
		public Set<Diet> suits() { return suits; }

		// "Risotto (v vg)"
		public String withDiets()
		{
			return suits.isEmpty() ? name : name + " (" + Diet.codes(suits) + ")";
		}

		@Override
		public String toString() { return name; }
	}

	private final List<Course> courses = new ArrayList<>();
	private String note = "";  // a few lines about the menu as a whole, for the guests and the kitchen

	public List<Course> courses() { return courses; }

	public String getNote() { return note; }

	public void setNote(String note) { this.note = note == null ? "" : note.strip(); }

	public boolean isEmpty() { return courses.isEmpty(); }

	public int nextDishId()
	{
		return dishes().stream().mapToInt(Dish::getId).max().orElse(0) + 1;
	}

	public List<Dish> dishes()
	{
		List<Dish> out = new ArrayList<>();
		courses.forEach(c -> out.addAll(c.dishes()));
		return Collections.unmodifiableList(out);
	}

	public Optional<Dish> dish(int id)
	{
		return dishes().stream().filter(d -> d.getId() == id).findFirst();
	}

	public Optional<Course> courseOf(Dish dish)
	{
		return courses.stream().filter(c -> c.dishes().contains(dish)).findFirst();
	}
}
