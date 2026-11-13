package org.lerot.myreceptionplanner.gui.dialogs;

import org.lerot.myreceptionplanner.gui.Theme;
import org.lerot.myreceptionplanner.model.Diet;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Menu;
import org.lerot.myreceptionplanner.model.MenuText;
import org.lerot.myreceptionplanner.model.Occasion;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * An event's menu: a line for each dish, with its course and the diets it
 * suits. The courses come in the order they first appear. Changes apply on
 * OK; a dish taken away takes with it any guest's choice of it. A menu can
 * be pasted in (a caterer's, as text) or imported from a menu file (XML or
 * YAML) or an event's file, to replace the menu or add to it; either way it
 * shows here to check before OK.
 */
public class MenuDialog
{
	public enum Start { MENU, IMPORT }  // IMPORT: choose a menu file first

	private static class Row
	{
		String course;
		String dish;
		final Set<Diet> suits = EnumSet.noneOf(Diet.class);
		Menu.Dish was;  // null for a dish added here (or pasted, imported)

		Row(String course, Menu.Dish d)
		{
			this.course = course;
			this.was = d;
			dish = d == null ? "" : d.getName();
			if (d != null) suits.addAll(d.suits());
		}
	}

	// true if changed; menu files are looked for in lookIn
	public static boolean edit(Component parent, Occasion occasion, Event event, Path lookIn, Start start)
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
		scroll.setPreferredSize(new Dimension(760, 420));

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
		JTextArea note = new JTextArea(event.getMenu().getNote(), 4, 50);
		JButton paste = new JButton("Paste a menu…");
		paste.setToolTipText("Paste a caterer's menu as text: its headings become courses, the lines under them dishes");
		paste.addActionListener(e ->
		{
			stop(table);
			Menu m = pasted(table);
			if (m != null && take(table, m, rows, note)) model.fireTableDataChanged();
		});
		JButton load = new JButton("Import…");
		load.setToolTipText("Take a menu file (XML or YAML), or the menu of an event's file");
		load.addActionListener(e ->
		{
			stop(table);
			Menu m = imported(table, lookIn);
			if (m != null && take(table, m, rows, note)) model.fireTableDataChanged();
		});
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttons.add(addCourse);
		buttons.add(addDish);
		buttons.add(remove);
		buttons.add(Box.createHorizontalStrut(24));
		buttons.add(paste);
		buttons.add(load);
		StringBuilder key = new StringBuilder("Tick what each dish suits:  ");
		for (Diet d : diets) key.append(d.code()).append(" ").append(d).append("   ");
		JLabel hint = new JLabel(key.toString().strip());
		hint.setForeground(Theme.EDGE);
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

		if (start == Start.IMPORT)
		{
			Menu m = imported(parent, lookIn);
			if (m == null || !take(parent, m, rows, note)) return false;
		}
		while (true)
		{
			if (!ResizableDialog.ok(parent, p, "Menu: " + event.getName(), "menuDialog")) return false;
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

	// a caterer's menu pasted in; null if none
	private static Menu pasted(Component parent)
	{
		JTextArea text = new JTextArea(18, 60);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		JLabel how = new JLabel("<html>Paste the menu as text (copied from the caterer's PDF, Word document or email).<br>"
				+ "Headings such as <i>Starters</i>, <i>MAIN COURSE</i> or <i>Desserts:</i> start courses; the lines under "
				+ "them are dishes. Marks such as (V), (VG), (GF), (DF) give the diets; prices are left out.<br>"
				+ "You can check and change it all before OK.</html>");
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.add(how, BorderLayout.NORTH);
		p.add(new JScrollPane(text), BorderLayout.CENTER);
		SwingUtilities.invokeLater(text::requestFocusInWindow);
		if (!ResizableDialog.ok(parent, p, "Paste a menu", "pasteMenuDialog") || text.getText().isBlank()) return null;
		Menu m = MenuText.fromText(text.getText());
		if (m.isEmpty())
		{
			JOptionPane.showMessageDialog(parent, "No courses or dishes could be found in the text.", "Paste a menu",
					JOptionPane.WARNING_MESSAGE);
			return null;
		}
		return m;
	}

	// a menu from a file; null if none
	private static Menu imported(Component parent, Path lookIn)
	{
		JFileChooser fc = new JFileChooser(lookIn.toFile());
		fc.setDialogTitle("Import a menu");
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				"Menu or event files (XML, YAML)", "xml", "yml", "yaml"));
		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
		File f = fc.getSelectedFile();
		try
		{
			return MenuText.load(f.toPath());
		} catch (IOException | RuntimeException e)
		{
			JOptionPane.showMessageDialog(parent, f.getName() + ": " + e.getMessage(), "Cannot import",
					JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}

	/*
	 * The menu taken into the lines, in place of them or after them (asking,
	 * if there are any). In place of them, a dish of the same course and name
	 * stays the same dish, so guests who chose it keep their choice. False if
	 * cancelled.
	 */
	private static boolean take(Component parent, Menu m, List<Row> rows, JTextArea note)
	{
		boolean replace = true;
		if (rows.stream().anyMatch(r -> !r.dish.isBlank()))
		{
			Object[] options = {"Replace the menu", "Add to it", "Cancel"};
			int a = JOptionPane.showOptionDialog(parent, m.dishes().size() + " dishes in " + m.courses().size()
							+ (m.courses().size() == 1 ? " course" : " courses") + ". Replace the menu with them, or add them to it?",
					"Menu", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
			if (a != 0 && a != 1) return false;
			replace = a == 0;
		}
		List<Row> was = new ArrayList<>(rows);
		if (replace) rows.clear();
		for (Menu.Course c : m.courses())
		{
			for (Menu.Dish d : c.dishes())
			{
				if (!replace && was.stream().anyMatch(r -> same(r, c.getName(), d.getName()))) continue;
				Row r = new Row(c.getName(), null);
				r.dish = d.getName();
				r.suits.addAll(d.suits());
				if (replace)
					was.stream().filter(o -> o.was != null && same(o, c.getName(), d.getName())).findFirst()
							.ifPresent(o -> r.was = o.was);
				rows.add(r);
			}
		}
		if (!m.getNote().isEmpty())
			note.setText(replace || note.getText().isBlank() ? m.getNote() : note.getText().strip() + "\n" + m.getNote());
		return true;
	}

	private static boolean same(Row r, String course, String dish)
	{
		return r.course.equalsIgnoreCase(course) && r.dish.equalsIgnoreCase(dish);
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
