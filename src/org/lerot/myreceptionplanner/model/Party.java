package org.lerot.myreceptionplanner.model;

import java.awt.Color;

/*
 * A group of guests invited together (a family, a set of friends), led by
 * one named guest who is the one to contact. It has a number of places: its
 * named guests, and places kept for guests not named yet, shown as the lead's
 * name +1, +2 ... Its colour marks its guests on the plan.
 */
public class Party
{
	private final int id;
	private String name;
	private Color colour;
	private Guest lead;  // one of its guests, named; null only in files from before leads

	public Party(int id, String name, Color colour)
	{
		this.id = id;
		setName(name);
		this.colour = colour;
	}

	public int getId() { return id; }

	// its own name, else its lead's
	public String getName()
	{
		if (!name.isEmpty() || lead == null) return name.isEmpty() ? "Party " + id : name;
		return lead.getName();
	}

	public boolean hasOwnName() { return !name.isEmpty(); }

	// "" to go by the lead's name
	public void setName(String name) { this.name = name == null ? "" : name.trim(); }

	public Color getColour() { return colour; }

	public void setColour(Color colour) { this.colour = colour; }

	public Guest getLead() { return lead; }

	// use GuestList.createParty, or setLead for one of its own named guests
	void setLead(Guest lead) { this.lead = lead; }

	@Override
	public String toString() { return getName(); }
}
