package org.lerot.myreceptionplanner.model;

import java.util.EnumSet;
import java.util.Set;

/*
 * A guest. A party keeps places for guests not named yet: such a guest has no
 * name and goes by the lead's name and a number, "Ann Smith+2" (the plus one,
 * built in). They can be seated like anyone else until a name is given;
 * where each guest sits is kept by each event (Seating).
 * Gender may not be known.
 */
public class Guest
{
	private final int id;
	private String name;
	private Gender gender;
	private Party party;  // null if in no party
	private int plus;     // n for the lead's +n while unnamed; 0 otherwise
	private Contact contact = new Contact();
	private final Set<Diet> diets = EnumSet.noneOf(Diet.class);
	private String dietNote = "";  // allergies and the like, in words

	public Guest(int id, String name, Gender gender, Party party)
	{
		this.id = id;
		setName(name);
		setGender(gender);
		this.party = party;
	}

	public int getId() { return id; }

	public String getName() { return name; }

	// use GuestList.rename for a guest in a party, so a guest whose name is
	// cleared gets a number again
	public void setName(String name) { this.name = name == null ? "" : name.trim(); }

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
		if (isNamed()) return name;
		if (party == null) return "Unnamed guest";
		Guest lead = party.getLead();
		String who = lead != null && lead.isNamed() ? lead.getName() : party.getName();
		return who + "+" + plus;
	}

	@Override
	public String toString() { return displayName(); }
}
