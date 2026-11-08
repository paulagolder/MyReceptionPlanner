package org.lerot.myreceptionplanner.model;

/*
 * A guest. A seat can be given to a party before anyone knows who will sit
 * there, so a guest may have no name yet, and their gender may not be known.
 */
public class Guest
{
	private final int id;
	private String name;
	private Gender gender;
	private Party party;  // null if in no party
	private Seat seat;    // null if not seated

	public Guest(int id, String name, Gender gender, Party party)
	{
		this.id = id;
		setName(name);
		setGender(gender);
		this.party = party;
	}

	public int getId() { return id; }

	public String getName() { return name; }

	public void setName(String name) { this.name = name == null ? "" : name.trim(); }

	public boolean isNamed() { return !name.isEmpty(); }

	public Gender getGender() { return gender; }

	public void setGender(Gender gender) { this.gender = gender == null ? Gender.UNKNOWN : gender; }

	public Party getParty() { return party; }

	public void setParty(Party party) { this.party = party; }

	public Seat getSeat() { return seat; }

	public boolean isSeated() { return seat != null; }

	// use GuestList.seat and unseat, so no two guests share a seat
	void setSeat(Seat seat) { this.seat = seat; }

	// the name, or who they are with if no one knows it yet
	public String displayName()
	{
		if (isNamed()) return name;
		return party == null ? "Unnamed guest" : "Guest of " + party.getName();
	}

	@Override
	public String toString() { return displayName(); }
}
