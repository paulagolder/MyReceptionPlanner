package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.model.Catering;
import org.lerot.myreceptionplanner.model.Diet;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Menu;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/*
 * The guests invited to an event, where they sit, their dietary needs, a
 * choice for each course of its menu and a note for this event only. A
 * dish that does not suit a guest's needs is shown in red with ⚠.
 * Double-click a guest's needs to change them (they hold at every event).
 * Only named guests choose, so a choice follows the person wherever they are
 * seated; places not yet named (Ann Smith+1) wait until named. Changes take
 * effect at once.
 */
public class MenuChoicesDialog
{
	// true if anything changed
	public static boolean show(Component parent, Event event)
	{
		List<Guest> rows = Catering.inSeatOrder(event);
		if (rows.isEmpty())
		{
			JOptionPane.showMessageDialog(parent, "No one is invited to " + event.getName() + " yet.",
					"Menu choices", JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		List<Menu.Course> courses = event.getMenu().courses();
		boolean[] changed = { false };
		AbstractTableModel model = new AbstractTableModel()
		{
			public int getRowCount() { return rows.size(); }

			public int getColumnCount() { return 4 + courses.size(); }

			public String getColumnName(int col)
			{
				if (col == 0) return "Guest";
				if (col == 1) return "Seat";
				if (col == 2) return "Dietary needs";
				if (col == 3 + courses.size()) return "Note for this event";
				return courses.get(col - 3).getName();
			}

			// a place not yet named has no choices or note: they follow a named person
			public boolean isCellEditable(int row, int col) { return col >= 3 && rows.get(row).isNamed(); }

			public Object getValueAt(int row, int col)
			{
				Guest g = rows.get(row);
				if (col == 0) return g.displayName();
				if (col == 1) return Catering.seatOf(event, g);
				if (col == 2) return g.dietText();
				if (col == 3 + courses.size()) return event.noteFor(g);
				return event.choice(g, courses.get(col - 3));
			}

			public void setValueAt(Object value, int row, int col)
			{
				Guest g = rows.get(row);
				if (col == 3 + courses.size()) event.setNote(g, value == null ? "" : value.toString());
				else event.choose(g, courses.get(col - 3), value instanceof Menu.Dish d ? d : null);
				changed[0] = true;
				fireTableRowsUpdated(row, row);
			}
		};
		JTable table = new JTable(model);
		table.setRowHeight(Math.max(table.getRowHeight(), 22));
		table.setFillsViewportHeight(true);
		table.putClientProperty("terminateEditOnFocusLost", true);
		table.getColumnModel().getColumn(0).setPreferredWidth(170);
		table.getColumnModel().getColumn(1).setPreferredWidth(110);
		table.getColumnModel().getColumn(2).setPreferredWidth(130);
		for (int c = 0; c < courses.size(); c++)
		{
			Menu.Course course = courses.get(c);
			JComboBox<Object> combo = new JComboBox<>();
			combo.addItem("");
			course.dishes().forEach(combo::addItem);
			table.getColumnModel().getColumn(3 + c).setCellEditor(new DefaultCellEditor(combo));
			table.getColumnModel().getColumn(3 + c).setPreferredWidth(130);
		}
		table.getColumnModel().getColumn(3 + courses.size()).setPreferredWidth(170);
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
		{
			public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col)
			{
				super.getTableCellRendererComponent(t, value, sel, focus, row, col);
				Guest g = rows.get(row);
				Color fg = Theme.TEXT;
				if (value instanceof Menu.Dish d)
				{
					boolean bad = !Diet.unmet(d.suits(), g.diets()).isEmpty();
					setText((bad ? "⚠ " : "") + d.getName());
					if (bad) fg = new Color(0xC0392B);
					setToolTipText(bad ? "Not suitable: " + Diet.codes(Diet.unmet(d.suits(), g.diets())) : null);
				} else if (!g.isNamed())
				{
					setFont(getFont().deriveFont(Font.ITALIC));
					fg = Theme.EDGE;
					if (col >= 3) setText(col == 3 ? "name first" : "");
				} else if (col == 2 && g.hasDietaryNeeds()) fg = Theme.HEAD.darker();
				if (!sel) setForeground(fg);
				return this;
			}
		});
		table.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				int row = table.rowAtPoint(e.getPoint());
				if (e.getClickCount() == 2 && row >= 0 && table.columnAtPoint(e.getPoint()) == 2
						&& DietDialog.editNamed(table, rows.get(row)))
				{
					changed[0] = true;
					model.fireTableRowsUpdated(row, row);
				}
			}
		});
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(Math.min(1150, 600 + 130 * courses.size()), 480));
		JLabel hint = new JLabel(event.hasMenu()
				? "Choose a dish for each course. ⚠ marks a dish that does not suit the guest's needs. "
				+ "Double-click needs to change them. Places not yet named choose once named."
				: "There is no menu yet (Catering ▾ → Menu…); dietary needs can be gathered now. "
				+ "Double-click needs to change them.");
		hint.setForeground(Theme.EDGE);
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.add(hint, BorderLayout.NORTH);
		p.add(scroll, BorderLayout.CENTER);
		Object[] close = { "Close" };
		ResizableDialog.show(parent, new JOptionPane(p, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
				null, close, close[0]), "Menu choices and dietary needs: " + event.getName(), "menuChoicesDialog");
		if (table.isEditing()) table.getCellEditor().stopCellEditing();
		return changed[0];
	}
}
