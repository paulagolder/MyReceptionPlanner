package org.lerot.myreceptionplanner.gui.dialogs;

import org.lerot.myreceptionplanner.gui.Theme;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/*
 * Every pair of guests whose names are alike (see NameMatch), with their
 * parties and why, for finding anyone on the list twice. A pair can be
 * marked as two different people, and is then left out from now on. To put
 * right a real duplicate, rename or remove one of them in the Guests tab.
 */
public class NamesAlikeDialog
{
	// true if any pair was marked as different people
	public static boolean show(Component parent, GuestList guests)
	{
		List<GuestList.Likeness> pairs = new ArrayList<>(guests.alike());
		if (pairs.isEmpty())
		{
			JOptionPane.showMessageDialog(parent, "No two guests have names alike.", "Names alike",
					JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		AbstractTableModel model = new AbstractTableModel()
		{
			private final String[] columns = { "Guest", "Party", "Guest", "Party", "Why" };

			public int getRowCount() { return pairs.size(); }

			public int getColumnCount() { return columns.length; }

			public String getColumnName(int col) { return columns[col]; }

			public Object getValueAt(int row, int col)
			{
				GuestList.Likeness l = pairs.get(row);
				return switch (col)
				{
					case 0 -> l.a().getName();
					case 1 -> party(l.a());
					case 2 -> l.b().getName();
					case 3 -> party(l.b());
					default -> l.why();
				};
			}
		};
		JTable table = new JTable(model);
		table.setRowHeight(Math.max(table.getRowHeight(), 22));
		table.setFillsViewportHeight(true);
		table.getColumnModel().getColumn(4).setPreferredWidth(260);
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(820, Math.min(420, 60 + pairs.size() * 24)));

		boolean[] marked = { false };
		JButton different = new JButton("Not the same person");
		different.setEnabled(false);
		table.getSelectionModel().addListSelectionListener(e -> different.setEnabled(table.getSelectedRowCount() > 0));
		different.addActionListener(e ->
		{
			int[] rows = table.getSelectedRows();
			for (int i = rows.length - 1; i >= 0; i--)
			{
				GuestList.Likeness l = pairs.remove(rows[i]);
				guests.markDistinct(l.a(), l.b());
				marked[0] = true;
			}
			model.fireTableDataChanged();
		});
		JLabel hint = new JLabel("Each person should be on the list only once. Rename or remove a duplicate "
				+ "in the Guests tab; mark a pair that are two people so it is left out from now on.");
		hint.setForeground(Theme.EDGE);
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.add(hint, BorderLayout.NORTH);
		p.add(scroll, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.add(different);
		p.add(buttons, BorderLayout.SOUTH);
		JOptionPane.showMessageDialog(parent, p, "Names alike", JOptionPane.PLAIN_MESSAGE);
		return marked[0];
	}

	private static String party(Guest g)
	{
		return g.getParty() == null ? "" : g.getParty().getName();
	}
}
