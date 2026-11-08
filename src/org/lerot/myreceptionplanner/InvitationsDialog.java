package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Who is invited to which event: each party with its guests down the side, a
 * column of ticks for each event. Click a guest's tick to invite or uninvite
 * them; click a party's to do all of it (half ticked when only some are
 * invited). Inviting anyone of a party invites its lead; uninviting the lead
 * uninvites the party. Nothing changes until OK, which says first if anyone
 * would lose a seat.
 */
public class InvitationsDialog
{
	// a party's line (guest null), or one of its guests; party null for those in none
	private record Row(Party party, Guest guest, String name) {}

	private final Occasion occasion;
	private final GuestList guests;
	private final List<Event> events;
	private final Map<Event, Set<Guest>> invited = new LinkedHashMap<>();
	private final List<Row> rows = new ArrayList<>();
	private final JLabel totals = new JLabel();

	// true if anything changed
	public static boolean show(Component parent, Occasion occasion)
	{
		return new InvitationsDialog(occasion).run(parent);
	}

	private InvitationsDialog(Occasion occasion)
	{
		this.occasion = occasion;
		this.guests = occasion.getGuests();
		this.events = occasion.events();
		for (Event e : events)
		{
			Set<Guest> set = Collections.newSetFromMap(new IdentityHashMap<>());
			set.addAll(e.invited());
			invited.put(e, set);
		}
		for (Party p : guests.parties())
		{
			rows.add(new Row(p, null, p.getName()));
			guests.membersOf(p).stream()
					.sorted(Comparator.comparing((Guest g) -> !g.isLead())
							.thenComparing(g -> !g.isNamed())
							.thenComparingInt(Guest::getPlus))
					.forEach(g -> rows.add(new Row(p, g, g.displayName() + (g.isLead() ? "  (lead)" : ""))));
		}
		List<Guest> loners = guests.guests().stream().filter(g -> g.getParty() == null).toList();
		if (!loners.isEmpty())
		{
			rows.add(new Row(null, null, "In no party"));
			loners.forEach(g -> rows.add(new Row(null, g, g.displayName())));
		}
	}

	private boolean run(Component parent)
	{
		Model model = new Model();
		JTable table = new JTable(model);
		table.setRowHeight(Math.max(table.getRowHeight(), 22));
		table.setFillsViewportHeight(true);
		table.setRowSelectionAllowed(false);
		table.getTableHeader().setReorderingAllowed(false);
		table.getColumnModel().getColumn(0).setPreferredWidth(240);
		for (int c = 1; c < model.getColumnCount(); c++)
			table.getColumnModel().getColumn(c).setPreferredWidth(120);
		table.setDefaultRenderer(Object.class, new NameRenderer());
		table.setDefaultRenderer(Boolean.class, new TickRenderer());
		table.addMouseListener(new MouseAdapter()
		{
			public void mousePressed(MouseEvent e)
			{
				int row = table.rowAtPoint(e.getPoint()), col = table.columnAtPoint(e.getPoint());
				if (row < 0 || col < 1) return;
				toggle(rows.get(row), events.get(col - 1));
				model.fireTableDataChanged();
				showTotals();
			}
		});
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(Math.min(1000, 260 + 120 * events.size()), 460));
		JPanel p = new JPanel(new BorderLayout(0, 6));
		JLabel hint = new JLabel("Click a tick to invite or uninvite. A party's lead is invited whenever "
				+ "anyone of the party is; uninviting the lead uninvites the party.");
		hint.setForeground(Theme.EDGE);
		p.add(hint, BorderLayout.NORTH);
		p.add(scroll, BorderLayout.CENTER);
		totals.setForeground(Theme.EDGE);
		p.add(totals, BorderLayout.SOUTH);
		showTotals();

		while (true)
		{
			int answer = JOptionPane.showConfirmDialog(parent, p, "Invitations: " + occasion.getName(),
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (answer != JOptionPane.OK_OPTION) return false;
			String losses = losses();
			if (losses.isEmpty()) break;
			int sure = JOptionPane.showConfirmDialog(parent, losses + "\nGo ahead?", "Invitations",
					JOptionPane.OK_CANCEL_OPTION);
			if (sure == JOptionPane.OK_OPTION) break;
		}
		boolean changed = false;
		for (Event e : events)
		{
			Set<Guest> now = invited.get(e);
			if (now.size() == e.invited().size() && now.containsAll(e.invited())) continue;
			e.setInvited(now, guests);
			changed = true;
		}
		return changed;
	}

	private void toggle(Row row, Event e)
	{
		Set<Guest> set = invited.get(e);
		List<Guest> members = members(row);
		if (row.guest() == null)
		{
			boolean all = set.containsAll(members);
			if (all) members.forEach(set::remove);
			else set.addAll(members);
			return;
		}
		Guest g = row.guest();
		if (set.contains(g))
		{
			if (g.isLead()) members.forEach(set::remove);
			else set.remove(g);
		} else
		{
			set.add(g);
			if (g.getParty() != null && g.getParty().getLead() != null) set.add(g.getParty().getLead());
		}
	}

	// the party's guests (or those in none) for a party's line; for a guest, their party's
	private List<Guest> members(Row row)
	{
		if (row.party() != null) return guests.membersOf(row.party());
		return guests.guests().stream().filter(g -> g.getParty() == null).toList();
	}

	// who would lose a seat, and which parties would be over capacity, event by event
	private String losses()
	{
		StringBuilder out = new StringBuilder();
		for (Event e : events)
		{
			int limit = e.capacityLimit();
			int over = limit == 0 ? 0 : invited.get(e).size() - limit;
			if (over > 0)
				out.append(e.getName()).append(" would have ").append(over)
						.append(over == 1 ? " guest" : " guests").append(" more than its capacity of ")
						.append(limit).append(".\n");
			long n = e.getSeating().all().keySet().stream().filter(g -> !invited.get(e).contains(g)).count();
			if (n > 0)
				out.append(n).append(n == 1 ? " guest loses a seat at " : " guests lose their seats at ")
						.append(e.getName()).append(".\n");
		}
		return out.toString().strip();
	}

	private void showTotals()
	{
		List<String> parts = new ArrayList<>();
		boolean over = false;
		for (Event e : events)
		{
			int n = invited.get(e).size();
			String part = e.getName() + ": " + n + " invited";
			int limit = e.capacityLimit();
			if (limit > 0)
			{
				part += " of " + limit;
				if (n > limit)
				{
					part += " (" + (n - limit) + " over!)";
					over = true;
				}
			}
			parts.add(part);
		}
		totals.setForeground(over ? Theme.HEAD.darker() : Theme.EDGE);
		totals.setText(String.join("     ", parts) + "     (of " + guests.guests().size() + " guests)");
	}

	private class Model extends AbstractTableModel
	{
		public int getRowCount() { return rows.size(); }

		public int getColumnCount() { return 1 + events.size(); }

		public String getColumnName(int col) { return col == 0 ? "Guest" : events.get(col - 1).getName(); }

		public Class<?> getColumnClass(int col) { return col == 0 ? Object.class : Boolean.class; }

		// a guest: ticked or not; a party: all, none, or null for some
		public Object getValueAt(int row, int col)
		{
			Row r = rows.get(row);
			if (col == 0) return r.name();
			Set<Guest> set = invited.get(events.get(col - 1));
			if (r.guest() != null) return set.contains(r.guest());
			List<Guest> members = members(r);
			long n = members.stream().filter(set::contains).count();
			return n == 0 ? Boolean.FALSE : n == members.size() ? Boolean.TRUE : null;
		}
	}

	// a party's line in bold with its colour; its guests set in
	private class NameRenderer extends DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col)
		{
			super.getTableCellRendererComponent(t, value, false, false, row, col);
			Row r = rows.get(row);
			setForeground(Theme.TEXT);
			if (r.guest() == null)
			{
				setFont(t.getFont().deriveFont(Font.BOLD));
				setIcon(Marks.swatch(r.party() == null ? GuestList.NO_PARTY : r.party().getColour(), 14));
				setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
				setBackground(Theme.PAGE);
			} else
			{
				setFont(t.getFont().deriveFont(r.guest().isNamed() ? Font.PLAIN : Font.ITALIC));
				if (!r.guest().isNamed()) setForeground(Theme.EDGE);
				setIcon(null);
				setBorder(BorderFactory.createEmptyBorder(0, 26, 0, 0));
				setBackground(t.getBackground());
			}
			return this;
		}
	}

	// a tick; half ticked for a party with only some invited
	private class TickRenderer implements TableCellRenderer
	{
		private final JCheckBox box = new JCheckBox();

		TickRenderer()
		{
			box.setHorizontalAlignment(SwingConstants.CENTER);
			box.setOpaque(true);
		}

		public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col)
		{
			boolean partyLine = rows.get(row).guest() == null;
			box.setBackground(partyLine ? Theme.PAGE : t.getBackground());
			box.putClientProperty("JButton.selectedState", value == null ? "indeterminate" : null);
			box.setSelected(Boolean.TRUE.equals(value));
			return box;
		}
	}
}
