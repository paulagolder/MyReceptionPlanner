package org.lerot.myreceptionplanner.model;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * A dietary need of a guest, and what a dish is suitable for. A guest's
 * needs are the same at every event; a dish suits a guest if it suits all
 * their needs (a vegan dish suits a vegetarian too).
 */
public enum Diet
{
	VEGETARIAN("v", "Vegetarian"),
	VEGAN("vg", "Vegan"),
	GLUTEN_FREE("gf", "Gluten-free"),
	DAIRY_FREE("df", "Dairy-free"),
	NUT_FREE("nf", "Nut allergy"),
	HALAL("h", "Halal"),
	KOSHER("k", "Kosher");

	private final String code;   // in the files, and shown short beside names
	private final String label;

	Diet(String code, String label)
	{
		this.code = code;
		this.label = label;
	}

	public String code() { return code; }

	@Override
	public String toString() { return label; }

	public static Diet fromCode(String code)
	{
		for (Diet d : values())
		{
			if (d.code.equalsIgnoreCase(code)) return d;
		}
		return null;
	}

	// "vg nf"
	public static String codes(Collection<Diet> diets)
	{
		return diets.stream().map(Diet::code).collect(Collectors.joining(" "));
	}

	// from "vg nf", ignoring what is not known
	public static Set<Diet> fromCodes(String codes)
	{
		Set<Diet> out = EnumSet.noneOf(Diet.class);
		if (codes == null) return out;
		for (String c : codes.trim().split("[\\s,]+"))
		{
			Diet d = fromCode(c);
			if (d != null) out.add(d);
		}
		return out;
	}

	// whether a dish suitable for these suits someone with this need
	public static boolean suits(Set<Diet> dish, Diet need)
	{
		return dish.contains(need) || (need == VEGETARIAN && dish.contains(VEGAN));
	}

	// the needs a dish does not meet; empty if it suits
	public static Set<Diet> unmet(Set<Diet> dish, Set<Diet> needs)
	{
		Set<Diet> out = EnumSet.noneOf(Diet.class);
		for (Diet n : needs)
		{
			if (!suits(dish, n)) out.add(n);
		}
		return out;
	}
}
