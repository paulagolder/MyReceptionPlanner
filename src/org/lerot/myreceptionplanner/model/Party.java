package org.lerot.myreceptionplanner.model;

import java.awt.Color;

// A group of guests invited together (a family, a set of friends); its
// colour marks its guests on the plan
public class Party
{
	private final int id;
	private String name;
	private Color colour;

	public Party(int id, String name, Color colour)
	{
		this.id = id;
		this.name = name;
		this.colour = colour;
	}

	public int getId() { return id; }

	public String getName() { return name; }

	public void setName(String name) { this.name = name; }

	public Color getColour() { return colour; }

	public void setColour(Color colour) { this.colour = colour; }

	@Override
	public String toString() { return name; }
}
