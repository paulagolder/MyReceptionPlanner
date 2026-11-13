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
	 * the plan down, left to right along each row. The head table and named
	 * tables are numbered too, though their names show instead, so the
	 * numbers shown may skip theirs. Otherwise numbers stay put when tables
	 * move.
	 */
	public void renumber()
	{
		for (Table t : tables)
		{
			if (!t.isNumbered()) t.setNumber(0);
		}
		// tables and pews are numbered each on their own
		for (String series : series())
		{
			int next = 1;
			for (List<Table> row : rows(tables.stream()
					.filter(t -> t.isNumbered() && t.series().equals(series)).toList()))
			{
				for (Table t : row)
					t.setNumber(next++);
			}
		}
	}

	private List<String> series()
	{
		return tables.stream().map(Table::series).distinct().toList();
	}

	// all the tables in rows, as renumber sees them
	public List<List<Table>> rows() { return rows(tables); }

	// in rows from the top down, each left to right: a row is the tables not
	// much lower than its topmost (see Table.rowDepth)
	private static List<List<Table>> rows(List<Table> these)
	{
		List<Table> byDepth = new ArrayList<>(these);
		byDepth.sort(Comparator.comparingDouble(Table::getY));
		List<List<Table>> rows = new ArrayList<>();
		int start = 0;
		while (start < byDepth.size())
		{
			double top = byDepth.get(start).getY();
			double depth = byDepth.get(start).rowDepth();
			int end = start;
			while (end < byDepth.size() && byDepth.get(end).getY() - top <= depth) end++;
			List<Table> row = new ArrayList<>(byDepth.subList(start, end));
			row.sort(Comparator.comparingDouble(Table::getX));
			rows.add(row);
			start = end;
		}
		return rows;
	}

	// gives the next number up to any table that has none, or shares one
	// with a table before it (a new table, or one from an older file whose
	// named tables had none)
	public void numberUnnumbered()
	{
		for (String series : series())
		{
			List<Table> these = tables.stream().filter(t -> t.series().equals(series)).toList();
			Set<Integer> used = new HashSet<>();
			List<Table> needing = new ArrayList<>();
			for (Table t : these)
			{
				if (!t.isNumbered()) continue;
				if (t.getNumber() > 0 && used.add(t.getNumber())) continue;
				needing.add(t);
			}
			int next = these.stream().mapToInt(Table::getNumber).max().orElse(0) + 1;
			for (Table t : needing)
				t.setNumber(next++);
		}
	}

	// what the table is called: its own name, else "Head table", "Table 3" or "Pew 2"
	public String label(Table table)
	{
		if (table.hasName()) return table.getName();
		if (table.isHead()) return "Head table";
		if (!table.isNumbered() || table.getNumber() == 0) return table.series();
		return table.series() + " " + table.getNumber();
	}

	// the places that can be given to guests
	public int reservedPlaces()
	{
		return tables.stream().mapToInt(Table::reservedPlaces).sum();
	}

	// the places that can be used: every chair less the disabled ones
	public int places()
	{
		return tables.stream().mapToInt(Table::usablePlaces).sum();
	}
}
