package org.lerot.myreceptionplanner.model;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/*
 * A guest. Their name is kept in its parts, title, forenames and family name
 * (see Name). A party keeps places for guests not named yet: such a guest has no
 * name and goes by the lead's name and a number, "Ann Smith+2" (the plus one,
 * built in). They can be seated like anyone else until a name is given;
 * where each guest sits is kept by each event (Seating).
 * Gender may not be known. A role says who they are at the occasion,
 * "Mother of the bride", "Guest speaker", in at most ROLE_LENGTH characters.
 */
public class Guest
{
	public static final int ROLE_LENGTH = 20;

	private final int id;
	private Name name = Name.NONE;
	private Gender gender;
	private Party party;  // null if in no party
	private int plus;     // n for the lead's +n while unnamed; 0 otherwise
	private Contact contact = new Contact();
	private final Set<Diet> diets = EnumSet.noneOf(Diet.class);
	private String dietNote = "";  // allergies and the like, in words
	private String role = "";      // "Mother of the bride", or ""

	public Guest(int id, Name name, Gender gender, Party party)
	{
		this.id = id;
		setName(name);
		setGender(gender);
		this.party = party;
	}

	public int getId() { return id; }

	// the whole name, "Dame Margot Fairweather"; "" for a place not named
	public String getName() { return name.full(); }

	public Name getNameParts() { return name; }

	// use GuestList.rename for a guest in a party, so a guest whose name is
	// cleared gets a number again
	public void setName(Name name) { this.name = name == null ? Name.NONE : name; }

	public boolean isNamed() { return !name.isEmpty(); }

	public Gender getGender() { return gender; }

	public void setGender(Gender gender) { this.gender = gender == null ? Gender.UNKNOWN : gender; }

	public Party getParty() { return party; }

	// use GuestList.setParty, which keeps the lead in their party and numbers placeholders
	void setParty(Party party) { this.party = party; }

	public int getPlus() { return plus; }

	void setPlus(int plus) { this.plus = plus; }

	// a place kept for one of the party not named yet
	public boolean isPlaceholder() { return !isNamed() && party != null; }

	public boolean isLead() { return party != null && party.getLead() == this; }

	public Contact getContact() { return contact; }

	public String getRole() { return role; }

	// trimmed, and cut to ROLE_LENGTH characters
	public void setRole(String role)
	{
		String r = role == null ? "" : role.strip();
		this.role = r.length() > ROLE_LENGTH ? r.substring(0, ROLE_LENGTH).strip() : r;
	}

	// the guest's dietary needs, the same at every event; change it directly
	public Set<Diet> diets() { return diets; }

	public String getDietNote() { return dietNote; }

	public void setDietNote(String note) { this.dietNote = note == null ? "" : note.strip(); }

	public boolean hasDietaryNeeds() { return !diets.isEmpty() || !dietNote.isEmpty(); }

	// "vg nf, no sesame", or ""
	public String dietText()
	{
		String codes = Diet.codes(diets);
		if (dietNote.isEmpty()) return codes;
		return codes.isEmpty() ? dietNote : codes + ", " + dietNote;
	}

	// the name, or for a place not named yet the lead's name and its number
	public String displayName()
	{
		if (isNamed()) return name.full();
		if (party == null) return "Unnamed guest";
		Guest lead = party.getLead();
		String who = lead != null && lead.isNamed() ? lead.getName() : party.getName();
		return who + "+" + plus;
	}

	// for finding guests in a long list: every word typed is somewhere in the
	// name, the party's name or the role, whatever the case or accents
	// ("obri" finds Ó Briain); true if nothing is typed
	public boolean matches(String typed)
	{
		// spaces left out, so "obri" finds Ó Briain and "vander" Van der Berg
		String within = plain(displayName() + " " + (party == null ? "" : party.getName()) + " " + role).replace(" ", "");
		for (String word : plain(typed).split("\\s+"))
			if (!within.contains(word)) return false;
		return true;
	}

	private static String plain(String s)
	{
		return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.replace("'", "").toLowerCase(Locale.ROOT).strip();
	}

	/*
	 * As a list in order of family name prints it, "Capulet, Juliet", with or
	 * without the title; a place not named yet goes by its lead's name and
	 * number, "Capulet, Lord+1".
	 */
	public String listName(boolean titles)
	{
		if (isNamed()) return name.listed(titles);
		Guest lead = party == null ? null : party.getLead();
		return lead != null && lead.isNamed() ? lead.listName(titles) + "+" + plus : displayName();
	}

	// the name it sorts by: its own, or for a place not named yet its lead's
	public Name sortName()
	{
		if (isNamed()) return name;
		Guest lead = party == null ? null : party.getLead();
		return lead != null && lead.isNamed() ? lead.name : new Name("", displayName(), "");
	}

	/*
	 * In order of family name as typed ("Van der Berg" under V), then
	 * forenames, then title, with a lead's places after them; case and
	 * accents left aside.
	 */
	public static java.util.Comparator<Guest> byFamilyName()
	{
		java.text.Collator order = java.text.Collator.getInstance(java.util.Locale.UK);
		order.setStrength(java.text.Collator.PRIMARY);
		return java.util.Comparator.comparing((Guest g) -> g.sortName().sortWord(), order)
				.thenComparing(g -> g.sortName().forenames(), order)
				.thenComparing(g -> g.sortName().title(), order)
				.thenComparingInt(g -> g.isNamed() ? 0 : g.plus);
	}

	@Override
	public String toString() { return displayName(); }
}
