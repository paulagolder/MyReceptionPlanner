package org.lerot.myreceptionplanner.model;

// A guest's gender, shown by the shape of their mark on the plan
public enum Gender
{
	FEMALE("f", "Female"),
	MALE("m", "Male"),
	UNKNOWN("u", "Not known");

	private final String code;   // in guests.xml
	private final String label;

	Gender(String code, String label)
	{
		this.code = code;
		this.label = label;
	}

	public String code() { return code; }

	@Override
	public String toString() { return label; }

	public static Gender fromCode(String code)
	{
		for (Gender g : values())
		{
			if (g.code.equalsIgnoreCase(code)) return g;
		}
		return UNKNOWN;
	}
}
