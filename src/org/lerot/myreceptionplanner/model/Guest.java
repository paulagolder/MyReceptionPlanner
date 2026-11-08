package org.lerot.myreceptionplanner.model;

/*
 * A guest. A party keeps places for guests not named yet: such a guest has no
 * name and goes by the lead's name and a number, "Ann Smith+2" (the plus one,
 * built in). They can be seated like anyone else until a name is given.
 * Gender may not be known.
 */
public class Guest
{
	private final int id;
	private String name;
	private Gender gender;
	private Party party;  // null if in no party
	private Seat seat;    // null if not seated
	private int plus;     // n for the lead's +n while unnamed; 0 otherwise
	private Contact contact = new Contact();

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

	public Seat getSeat() { return seat; }

	public boolean isSeated() { return seat != null; }

	// use GuestList.seat and unseat, so no two guests share a seat
	void setSeat(Seat seat) { this.seat = seat; }

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
