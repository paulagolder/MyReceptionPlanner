package org.lerot.myreceptionplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// The tables of one reception, in drawing order (the last is on top)
public class Plan
{
	// tables whose centres are this close top to bottom are in the same row
	static final double ROW_DEPTH = 120;

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

	/*
	 * Numbers the tables afresh by where they stand: in rows from the top of
	 * the plan down, left to right along each row. The head table, and any
	 * table with a name of its own, has no number, so the numbers run on
	 * unbroken. Otherwise numbers stay put when tables move.
	 */
	public void renumber()
	{
		int next = 1;
		for (Table t : tables)
		{
			if (!t.isNumbered()) t.setNumber(0);
		}
		for (List<Table> row : rows(tables.stream().filter(Table::isNumbered).toList()))
		{
			for (Table t : row)
				t.setNumber(next++);
		}
	}

	// all the tables in rows, as renumber sees them
	public List<List<Table>> rows() { return rows(tables); }

	// in rows from the top down, each left to right: a row is the tables not
	// much lower than its topmost
	private static List<List<Table>> rows(List<Table> these)
	{
		List<Table> byDepth = new ArrayList<>(these);
		byDepth.sort(Comparator.comparingDouble(Table::getY));
		List<List<Table>> rows = new ArrayList<>();
		int start = 0;
		while (start < byDepth.size())
		{
			double top = byDepth.get(start).getY();
			int end = start;
			while (end < byDepth.size() && byDepth.get(end).getY() - top <= ROW_DEPTH) end++;
			List<Table> row = new ArrayList<>(byDepth.subList(start, end));
			row.sort(Comparator.comparingDouble(Table::getX));
			rows.add(row);
			start = end;
		}
		return rows;
	}

	// gives the next number up to any table going by its number that has
	// none, or shares one with a table before it (a new table, or one that
	// has lost its name or stopped being the head table)
	public void numberUnnumbered()
	{
		Set<Integer> used = new HashSet<>();
		List<Table> needing = new ArrayList<>();
		for (Table t : tables)
		{
			if (!t.isNumbered()) continue;
			if (t.getNumber() > 0 && used.add(t.getNumber())) continue;
			needing.add(t);
		}
		int next = tables.stream().mapToInt(Table::getNumber).max().orElse(0) + 1;
		for (Table t : needing)
			t.setNumber(next++);
	}

	// what the table is called: its own name, else "Head table" or "Table 3"
	public String label(Table table)
	{
		if (table.hasName()) return table.getName();
		if (table.isHead()) return "Head table";
		return "Table " + table.getNumber();
	}

	// the places that can be used: every chair less the disabled ones
	public int places()
	{
		return tables.stream().mapToInt(Table::usablePlaces).sum();
	}
}
