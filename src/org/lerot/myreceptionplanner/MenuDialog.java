package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.model.Diet;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Menu;
import org.lerot.myreceptionplanner.model.Occasion;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * An event's menu: a line for each dish, with its course and the diets it
 * suits. The courses come in the order they first appear. Changes apply on
 * OK; a dish taken away takes with it any guest's choice of it.
 */
public class MenuDialog
{
	private static class Row
	{
		String course;
		String dish;
		final Set<Diet> suits = EnumSet.noneOf(Diet.class);
		Menu.Dish was;  // null for a dish added here

		Row(String course, Menu.Dish d)
		{
			this.course = course;
			this.was = d;
			dish = d == null ? "" : d.getName();
			if (d != null) suits.addAll(d.suits());
		}
	}

	// true if changed
	public static boolean edit(Component parent, Occasion occasion, Event event)
	{
		List<Row> rows = new ArrayList<>();
		for (Menu.Course c : event.getMenu().courses())
			for (Menu.Dish d : c.dishes())
				rows.add(new Row(c.getName(), d));
		Diet[] diets = Diet.values();
		AbstractTableModel model = new AbstractTableModel()
		{
			public int getRowCount() { return rows.size(); }

			public int getColumnCount() { return 2 + diets.length; }

			public String getColumnName(int col)
			{
				return col == 0 ? "Course" : col == 1 ? "Dish" : diets[col - 2].code();
			}

			public Class<?> getColumnClass(int col) { return col < 2 ? String.class : Boolean.class; }

			public boolean isCellEditable(int row, int col) { return true; }

			public Object getValueAt(int row, int col)
			{
				Row r = rows.get(row);
				return col == 0 ? r.course : col == 1 ? r.dish : r.suits.contains(diets[col - 2]);
			}

			public void setValueAt(Object value, int row, int col)
			{
				Row r = rows.get(row);
				if (col == 0) r.course = value.toString().trim();
				else if (col == 1) r.dish = value.toString().trim();
				else if ((Boolean) value) r.suits.add(diets[col - 2]);
				else r.suits.remove(diets[col - 2]);
			}
		};
		JTable table = new JTable(model);
		table.setRowHeight(Math.max(table.getRowHeight(), 22));
		table.setFillsViewportHeight(true);
		table.putClientProperty("terminateEditOnFocusLost", true);
		table.getColumnModel().getColumn(0).setPreferredWidth(120);
		table.getColumnModel().getColumn(1).setPreferredWidth(220);
		for (int c = 2; c < model.getColumnCount(); c++)
		{
			table.getColumnModel().getColumn(c).setPreferredWidth(36);
			table.getColumnModel().getColumn(c).setHeaderValue(diets[c - 2].code());
		}
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(640, 320));

		JButton addCourse = new JButton("Add course");
		addCourse.addActionListener(e ->
		{
			stop(table);
			rows.add(new Row(rows.isEmpty() ? "Starter" : "Course " + (courseNames(rows).size() + 1), null));
			model.fireTableDataChanged();
			edit(table, rows.size() - 1, 0);
		});
		JButton addDish = new JButton("Add dish");
		addDish.addActionListener(e ->
		{
			stop(table);
			int sel = table.getSelectedRow();
			String course = sel >= 0 ? rows.get(sel).course : rows.isEmpty() ? "Main" : rows.get(rows.size() - 1).course;
			int at = sel >= 0 ? sel + 1 : rows.size();
			while (at < rows.size() && rows.get(at).course.equals(course)) at++;
			rows.add(at, new Row(course, null));
			model.fireTableDataChanged();
			edit(table, at, 1);
		});
		JButton remove = new JButton("Remove");
		remove.addActionListener(e ->
		{
			stop(table);
			int sel = table.getSelectedRow();
			if (sel < 0) return;
			rows.remove(sel);
			model.fireTableDataChanged();
		});
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttons.add(addCourse);
		buttons.add(addDish);
		buttons.add(remove);
		StringBuilder key = new StringBuilder("Tick what each dish suits:  ");
		for (Diet d : diets) key.append(d.code()).append(" ").append(d).append("   ");
		JLabel hint = new JLabel(key.toString().strip());
		hint.setForeground(Theme.EDGE);
		JTextArea note = new JTextArea(event.getMenu().getNote(), 4, 50);
		note.setLineWrap(true);
		note.setWrapStyleWord(true);
		note.setToolTipText("About the menu as a whole, e.g. \"All our food is sourced locally\"; "
				+ "it goes with the menu to the guests and on the catering list");
		JPanel notePanel = new JPanel(new BorderLayout(0, 2));
		notePanel.add(new JLabel("Note (a few lines about the menu, for the guests and the kitchen)"), BorderLayout.NORTH);
		notePanel.add(new JScrollPane(note), BorderLayout.CENTER);
		JPanel south = new JPanel(new BorderLayout(0, 8));
		south.add(buttons, BorderLayout.NORTH);
		south.add(notePanel, BorderLayout.CENTER);
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.add(hint, BorderLayout.NORTH);
		p.add(scroll, BorderLayout.CENTER);
		p.add(south, BorderLayout.SOUTH);

		while (true)
		{
			if (JOptionPane.showConfirmDialog(parent, p, "Menu: " + event.getName(), JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return false;
			stop(table);
			long lost = event.choices().values().stream().flatMap(m -> m.values().stream())
					.filter(d -> rows.stream().noneMatch(r -> r.was == d && !r.dish.isBlank())).count();
			if (lost == 0 || JOptionPane.showConfirmDialog(parent, lost + (lost == 1 ? " choice is" : " choices are")
					+ " of dishes no longer on the menu and will be cleared.", "Menu",
					JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) break;
		}
		apply(event, rows);
		event.getMenu().setNote(note.getText());
		event.tidy(occasion.getGuests());
		return true;
	}

	// the menu as the lines say, keeping the courses and dishes that were there
	private static void apply(Event event, List<Row> rows)
	{
		Menu menu = event.getMenu();
		Map<String, Menu.Course> old = new LinkedHashMap<>();
		for (Menu.Course c : menu.courses()) old.putIfAbsent(c.getName(), c);
		int next = menu.nextDishId();
		Map<String, Menu.Course> now = new LinkedHashMap<>();
		for (Row r : rows)
		{
			if (r.dish.isBlank()) continue;
			String name = r.course.isBlank() ? "Course" : r.course;
			Menu.Course c = now.computeIfAbsent(name, n ->
			{
				Menu.Course keep = old.get(n);
				if (keep == null) return new Menu.Course(n);
				keep.dishes().clear();
				return keep;
			});
			Menu.Dish d = r.was != null ? r.was : new Menu.Dish(next++, r.dish);
			d.setName(r.dish);
			d.suits().clear();
			d.suits().addAll(r.suits);
			c.dishes().add(d);
		}
		menu.courses().clear();
		menu.courses().addAll(now.values());
	}

	private static List<String> courseNames(List<Row> rows)
	{
		return rows.stream().map(r -> r.course).distinct().toList();
	}

	private static void stop(JTable t)
	{
		if (t.isEditing()) t.getCellEditor().stopCellEditing();
	}

	private static void edit(JTable t, int row, int col)
	{
		t.setRowSelectionInterval(row, row);
		t.editCellAt(row, col);
		if (t.getEditorComponent() != null) t.getEditorComponent().requestFocusInWindow();
	}
}
