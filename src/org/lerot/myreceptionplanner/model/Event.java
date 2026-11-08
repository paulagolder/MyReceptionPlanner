package org.lerot.myreceptionplanner.model;

import java.time.LocalDate;

/*
 * One event of an occasion, such as the Wedding Breakfast: where and when it
 * is, its tables and who sits where. It is kept in its own file in the
 * occasion's folder; the file's name is given when the event is made and
 * stays the same if the event is renamed.
 */
public class Event
{
	private String name;
	private String place = "";
	private LocalDate date;    // null: the occasion's date
	private String time = "";  // as typed, e.g. "2pm" or "14:00"
	private final String file;
	private final Plan plan;
	private final Seating seating;

	public Event(String name, String file, Plan plan, Seating seating)
	{
		setName(name);
		this.file = file;
		this.plan = plan;
		this.seating = seating;
	}

	public String getName() { return name; }

	public void setName(String name) { this.name = name == null || name.isBlank() ? "Event" : name.trim(); }

	public String getPlace() { return place; }

	public void setPlace(String place) { this.place = place == null ? "" : place.trim(); }

	public LocalDate getDate() { return date; }

	public void setDate(LocalDate date) { this.date = date; }

	public String getTime() { return time; }

	public void setTime(String time) { this.time = time == null ? "" : time.trim(); }

	public String getFile() { return file; }

	public Plan getPlan() { return plan; }

	public Seating getSeating() { return seating; }

	@Override
	public String toString() { return name; }
}
