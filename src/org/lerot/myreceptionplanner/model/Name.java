package org.lerot.myreceptionplanner.model;

import java.util.Arrays;
import java.util.List;

/*
 * A guest's name in its parts, as typed: the title ("Dame", "Dr", "The
 * Reverend"; may be empty), the forenames ("Margot", "Mary Ann") and the
 * family name ("Fairweather", "Van der Berg", "Ó Briain"), any of which may
 * be empty: "Tybalt" has a forename only, "The Mayor of Thornbury" may be all
 * title. The family name is what lists sort by, exactly as typed, so a
 * particle goes where the person typing puts it. Every way a name is printed
 * is made from the parts.
 */
public record Name(String title, String forenames, String family)
{
	public static final Name NONE = new Name("", "", "");

	public Name
	{
		title = tidy(title);
		forenames = tidy(forenames);
		family = tidy(family);
	}

	private static String tidy(String s) { return s == null ? "" : s.strip().replaceAll("\\s+", " "); }

	public boolean isEmpty() { return title.isEmpty() && forenames.isEmpty() && family.isEmpty(); }

	// "Dame Margot Fairweather"
	public String full() { return join(title, forenames, family); }

	// "Margot Fairweather", without the title
	public String plain() { return forenames.isEmpty() && family.isEmpty() ? title : join(forenames, family); }

	// the first forename, "Mary" of "Mary Ann"; else the family name
	public String firstName()
	{
		return forenames.isEmpty() ? family : forenames.split(" ")[0];
	}

	/*
	 * "Fairweather, Dame Margot", for a list in order of family name; without
	 * the title if titles is false ("Fairweather, Margot"). A name with no
	 * family name is as written.
	 */
	public String listed(boolean titles)
	{
		if (family.isEmpty()) return titles ? full() : plain();
		String rest = titles ? join(title, forenames) : forenames;
		return rest.isEmpty() ? family : family + ", " + rest;
	}

	// what a list sorts it by first: the family name, else the forenames, else the title
	public String sortWord() { return !family.isEmpty() ? family : !forenames.isEmpty() ? forenames : title; }

	public Name withTitle(String t) { return new Name(t, forenames, family); }

	public Name withForenames(String f) { return new Name(title, f, family); }

	public Name withFamily(String f) { return new Name(title, forenames, f); }

	private static String join(String... parts)
	{
		return String.join(" ", Arrays.stream(parts).filter(p -> !p.isEmpty()).toList());
	}

	/*
	 * A whole name split as best it can be, for a name typed in one piece or
	 * read from an older file: a title the honorifics know at the front, the
	 * last word as the family name, the rest as forenames. A title and one
	 * more word ("Count Paris", "Dr Smith") is taken the way the honorifics
	 * greet it; one word alone is a forename. Only a guess: the parts are
	 * there to be put right.
	 */
	public static Name guess(String whole, Honorifics h)
	{
		String s = tidy(whole);
		if (s.isEmpty()) return NONE;
		List<String> w = List.of(s.split(" "));
		int t = h == null ? 0 : h.titleLength(s);
		String title = String.join(" ", w.subList(0, t));
		List<String> rest = w.subList(t, w.size());
		if (rest.isEmpty()) return new Name(title, "", "");
		if (rest.size() == 1)
			return t > 0 && h.greetsBySurname(title) ? new Name(title, "", rest.get(0)) : new Name(title, rest.get(0), "");
		if (t > 0 && Honorifics.JOINS.contains(rest.get(0).toLowerCase(java.util.Locale.ROOT)))
			return new Name(title + " " + String.join(" ", rest), "", "");  // Widow of Vitruvio
		return new Name(title, String.join(" ", rest.subList(0, rest.size() - 1)), rest.get(rest.size() - 1));
	}
}
