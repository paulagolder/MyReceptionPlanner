package org.lerot.myreceptionplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// The tables of one reception, in drawing order (the last is on top)
public class Plan
{
	private final List<Table> tables = new ArrayList<>();

	public List<Table> tables()
	{
		return Collections.unmodifiableList(tables);
	}

	public void add(Table table)
	{
		tables.add(table);
		if (table.isHead()) setHead(table);
	}

	public void remove(Table table)
	{
		tables.remove(table);
	}

	public void bringToFront(Table table)
	{
		if (tables.remove(table)) tables.add(table);
	}

	public Optional<Table> find(int id)
	{
		return tables.stream().filter(t -> t.getId() == id).findFirst();
	}

	public int nextId()
	{
		return tables.stream().mapToInt(Table::getId).max().orElse(0) + 1;
	}

	// makes this the head table (null for none); there is only ever one
	public void setHead(Table head)
	{
		for (Table t : tables)
			t.setHead(t == head);
	}

	public Optional<Table> head()
	{
		return tables.stream().filter(Table::isHead).findFirst();
	}

	// the places that can be used: every chair less the disabled ones
	public int places()
	{
		return tables.stream().mapToInt(Table::usablePlaces).sum();
	}
}
