package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Gender;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Seat;
import org.lerot.myreceptionplanner.model.Seating;
import org.lerot.myreceptionplanner.model.Table;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/*
 * Seating mode's side panel, with two tabs.
 *
 * Table lists the seats of the table chosen on the plan, seat 1 first, and
 * who sits in each. On an empty seat, choosing a party seats one of its
 * places not yet seated (a new place if none is left); typing a name puts a
 * new guest there. Right-click a seat to seat someone from the guest list, or
 * to clear it.
 *
 * Guests lists the parties (each made and edited in PartyDialog) and every
 * guest. A place not named yet shows as the lead's name +1, +2 ...; type a
 * name over it to name that guest.
 */
public class SeatingPanel extends JPanel
{
	public interface Listener
	{
		void guestsChanged();

		// a seat chosen here, to show on the plan
		void seatSelected(Table table, int chair);
	}

	private static final String NO_PARTY = "No party";
	private static final int ICON = 14;
	private static final Icon BLANK = new ImageIcon(new java.awt.image.BufferedImage(ICON, ICON,
			java.awt.image.BufferedImage.TYPE_INT_ARGB));  // keeps empty seats' numbers in line

	private final Occasion occasion;
	private final Plan plan;
	private final GuestList guests;
	private final Event event;
	private final Seating seating;
	private final Listener listener;
	private Table table;      // the table whose seats are listed
	private Party lastParty;  // given to a new guest put in an empty seat

	private final JTabbedPane tabs = new JTabbedPane();
	private final JLabel tableTitle = new JLabel();
	private final SeatModel seatModel = new SeatModel();
	private final JTable seatTable = new JTable(seatModel);
	private final PartyModel partyModel = new PartyModel();
	private final JTable partyTable = new JTable(partyModel);
	private final GuestModel guestModel = new GuestModel();
	private final JTable guestTable = new JTable(guestModel);
	private final JCheckBox unseatedOnly = new JCheckBox("Not seated");
	private final JComboBox<Object> seatPartyCombo = new JComboBox<>();
	private final JComboBox<Object> guestPartyCombo = new JComboBox<>();

	public SeatingPanel(Occasion occasion, Event event, Listener listener)
	{
		super(new BorderLayout());
		this.occasion = occasion;
		this.plan = event.getPlan();
		this.guests = occasion.getGuests();
		this.event = event;
		this.seating = event.getSeating();
		this.listener = listener;
		setPreferredSize(new Dimension(380, 0));
		setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.GRID));
		tabs.addTab("Table", tableTab());
		tabs.addTab("Guests", guestsTab());
		add(tabs, BorderLayout.CENTER);
		add(legend(), BorderLayout.SOUTH);
		refresh();
	}

	// lists this table's seats (null for none) and selects the chair's row
	public void showTable(Table t, int chair)
	{
		stopEditing();
		table = t;
		seatModel.fireTableDataChanged();
		updateTitle();
		tabs.setSelectedIndex(0);
		if (t != null && chair >= 0)
		{
			int row = t.seatNumber(chair) - 1;
			seatTable.getSelectionModel().setSelectionInterval(row, row);
			seatTable.scrollRectToVisible(seatTable.getCellRect(row, 0, true));
		}
	}

	// call when the tables or guests have changed elsewhere
	public void refresh()
	{
		stopEditing();
		if (table != null && !plan.tables().contains(table)) table = null;
		fillPartyCombo(seatPartyCombo);
		fillPartyCombo(guestPartyCombo);
		int seatRow = seatTable.getSelectedRow();
		seatModel.fireTableDataChanged();
		if (seatRow >= 0 && seatRow < seatModel.getRowCount())
			seatTable.getSelectionModel().setSelectionInterval(seatRow, seatRow);
		partyModel.fireTableDataChanged();
		guestModel.reload();
		updateTitle();
	}

	private void stopEditing()
	{
		for (JTable t : new JTable[] { seatTable, partyTable, guestTable })
		{
			if (t.isEditing()) t.getCellEditor().cancelCellEditing();
		}
	}

	// after an edit: once the edit is over, show it everywhere
	private void changed()
	{
		SwingUtilities.invokeLater(() ->
		{
			occasion.tidy();  // new guests invited, and seats only for those invited
			refresh();
			listener.guestsChanged();
		});
	}

	private void fillPartyCombo(JComboBox<Object> combo)
	{
		combo.removeAllItems();
		combo.addItem(NO_PARTY);
		for (Party p : guests.parties())
			combo.addItem(p);
	}

	private void updateTitle()
	{
		if (table == null)
		{
			tableTitle.setText("Click a table on the plan");
			return;
		}
		tableTitle.setText("<html><b>" + escape(plan.label(table)) + "</b>&nbsp;&nbsp;"
				+ seating.seatedAt(table.getId()).size() + " of " + table.usablePlaces() + " seats taken</html>");
	}

	// ---- the Table tab ----

	private JComponent tableTab()
	{
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		p.add(tableTitle, BorderLayout.NORTH);
		setUp(seatTable, seatModel::guestAt, this::notForGuests, 1);
		seatTable.getColumnModel().getColumn(0).setMaxWidth(60);
		seatTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(Gender.values())));
		seatTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(seatPartyCombo));
		seatTable.getSelectionModel().addListSelectionListener(e ->
		{
			int row = seatTable.getSelectedRow();
			if (!e.getValueIsAdjusting() && table != null && row >= 0)
				listener.seatSelected(table, seatModel.chairAt(row));
		});
		seatTable.addMouseListener(new MouseAdapter()
		{
			public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) seatMenu(e); }

			public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) seatMenu(e); }
		});
		p.add(new JScrollPane(seatTable), BorderLayout.CENTER);
		JLabel hint = new JLabel("<html><body style='width:250px'>Type a name, or choose a gender or party, to fill a seat. "
				+ "A party alone keeps the seat for one of its guests. "
				+ "Right-click a seat for the guest list.</html>");
		hint.setForeground(Theme.EDGE);
		p.add(hint, BorderLayout.SOUTH);
		return p;
	}

	private void seatMenu(MouseEvent e)
	{
		int row = seatTable.rowAtPoint(e.getPoint());
		if (table == null || row < 0) return;
		seatTable.setRowSelectionInterval(row, row);
		int chair = seatModel.chairAt(row);
		if (!table.isReserved(chair)) return;
		Seat seat = new Seat(table.getId(), chair);
		Guest there = seatModel.guestAt(row);
		JPopupMenu menu = new JPopupMenu();

		JMenu seatSomeone = new JMenu(there == null ? "Seat a guest" : "Seat another guest here");
		List<Guest> waiting = invitedNotSeated().stream()
				.sorted(Comparator.comparing((Guest g) -> g.getParty() == null ? "￿" : g.getParty().getName())
						.thenComparing(Guest::displayName))
				.toList();
		for (Guest g : waiting)
		{
			JMenuItem item = new JMenuItem(g.displayName(), Marks.icon(g.getGender(), GuestList.colourOf(g), ICON));
			item.addActionListener(a ->
			{
				seating.seat(g, seat);
				changed();
			});
			seatSomeone.add(item);
		}
		if (waiting.isEmpty())
		{
			JMenuItem none = new JMenuItem("Everyone is seated");
			none.setEnabled(false);
			seatSomeone.add(none);
		}
		menu.add(seatSomeone);

		if (there != null)
		{
			menu.add("Clear seat (back to the guest list)").addActionListener(a ->
			{
				seating.unseat(there);
				changed();
			});
			menu.add("Remove guest").addActionListener(a -> removeGuest(there));
		}
		menu.show(seatTable, e.getX(), e.getY());
	}

	private void removeGuest(Guest g)
	{
		if (g.isLead())
		{
			JOptionPane.showMessageDialog(this, g.getName() + " leads the party " + g.getParty().getName()
					+ ". Remove the party, or make someone else its lead, first.", "Remove guest",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (g.isNamed())
		{
			int answer = JOptionPane.showConfirmDialog(this, "Remove " + g.getName() + " from the guest list?",
					"Remove guest", JOptionPane.OK_CANCEL_OPTION);
			if (answer != JOptionPane.OK_OPTION) return;
		}
		guests.removeGuest(g);
		occasion.tidy();
		changed();
	}

	// the seats of the shown table, seat 1 first
	private class SeatModel extends AbstractTableModel
	{
		private final String[] columns = { "Seat", "Guest", "Gender", "Party" };

		public int getRowCount() { return table == null ? 0 : table.places(); }

		public int getColumnCount() { return columns.length; }

		public String getColumnName(int col) { return columns[col]; }

		int chairAt(int row) { return table.chairOfSeat(row + 1); }

		Guest guestAt(int row)
		{
			return table == null ? null : seating.guestAt(table.getId(), chairAt(row)).orElse(null);
		}

		public Object getValueAt(int row, int col)
		{
			Guest g = guestAt(row);
			return switch (col)
			{
				case 0 -> row + 1;
				case 1 -> g == null ? "" : g.getName();
				case 2 -> g == null ? null : g.getGender();
				default -> g == null ? null : partyOf(g);
			};
		}

		public boolean isCellEditable(int row, int col)
		{
			return col > 0 && table.isReserved(chairAt(row));
		}

		public void setValueAt(Object value, int row, int col)
		{
			if (value == null) return;
			Guest g = guestAt(row);
			Seat seat = new Seat(table.getId(), chairAt(row));
			switch (col)
			{
				case 1 ->
				{
					String name = value.toString().trim();
					if (g == null && name.isEmpty()) return;
					if (g == null)
					{
						g = guests.addGuest(name, Gender.UNKNOWN, lastParty);
						event.invite(g);
						seating.seat(g, seat);
					} else guests.rename(g, name);
				}
				case 2 ->
				{
					if (g == null) g = newGuestAt(seat);
					g.setGender((Gender) value);
				}
				case 3 ->
				{
					Party p = value instanceof Party party ? party : null;
					lastParty = p;
					if (g == null) seatOneOf(p, seat);
					else if (!guests.setParty(g, p)) leadStays(g);
				}
				default -> { return; }
			}
			changed();
		}

		private Guest newGuestAt(Seat seat)
		{
			return seatOneOf(lastParty, seat);
		}

		// one of the party invited here and not seated yet, a place not named
		// first, the lowest numbered; if there is none, a new place in the party
		private Guest seatOneOf(Party p, Seat seat)
		{
			Guest g = invitedNotSeated().stream()
					.filter(u -> u.getParty() == p && p != null)
					.min(Comparator.comparing(Guest::isNamed).thenComparingInt(Guest::getPlus))
					.orElseGet(() -> guests.addGuest("", Gender.UNKNOWN, p));
			event.invite(g);
			seating.seat(g, seat);
			return g;
		}
	}

	private List<Guest> invitedNotSeated()
	{
		return seating.unseated(guests.guests()).stream().filter(event::isInvited).toList();
	}

	// ---- the Guests tab ----

	private JComponent guestsTab()
	{
		JPanel parties = new JPanel(new MigLayout("insets 8, fill, wrap 1", "[grow]", "[][grow][]"));
		parties.add(new JLabel("<html><b>Parties</b></html>"));
		partyTable.getColumnModel().getColumn(0).setMaxWidth(30);
		partyTable.getColumnModel().getColumn(2).setMaxWidth(60);
		partyTable.getColumnModel().getColumn(3).setMaxWidth(60);
		partyTable.getColumnModel().getColumn(4).setMaxWidth(60);
		partyTable.setDefaultRenderer(Object.class, new CellRenderer(row -> null, row -> null, -1));
		partyTable.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() != 2) return;
				if (partyTable.columnAtPoint(e.getPoint()) == 0) chooseColour();
				else editParty();
			}
		});
		parties.add(new JScrollPane(partyTable), "grow");
		JPanel partyButtons = new JPanel(new MigLayout("insets 0"));
		partyButtons.add(button("New…", this::newParty));
		partyButtons.add(button("Edit…", this::editParty));
		partyButtons.add(button("Colour…", this::chooseColour));
		partyButtons.add(button("Remove", this::removeParty));
		parties.add(partyButtons);

		JPanel people = new JPanel(new MigLayout("insets 8, fill, wrap 1", "[grow]", "[][grow][]"));
		people.add(new JLabel("<html><b>Guests</b></html>"), "split 2");
		unseatedOnly.addActionListener(e -> guestModel.reload());
		people.add(unseatedOnly, "gapleft push");
		setUp(guestTable, row -> guestModel.rows.get(guestTable.convertRowIndexToModel(row)), row -> null, 0);
		guestTable.setAutoCreateRowSorter(true);
		guestTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(Gender.values())));
		guestTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(guestPartyCombo));
		guestTable.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				int row = guestTable.rowAtPoint(e.getPoint());
				if (e.getClickCount() == 2 && row >= 0 && guestTable.columnAtPoint(e.getPoint()) == 4)
					findSeat(guestModel.rows.get(guestTable.convertRowIndexToModel(row)));
			}
		});
		people.add(new JScrollPane(guestTable), "grow");
		JPanel guestButtons = new JPanel(new MigLayout("insets 0"));
		guestButtons.add(button("Add guest", this::addGuest));
		guestButtons.add(button("Remove", () ->
		{
			int row = guestTable.getSelectedRow();
			if (row >= 0) removeGuest(guestModel.rows.get(guestTable.convertRowIndexToModel(row)));
		}));
		JLabel hint = new JLabel("Double-click a seat to find it");
		hint.setForeground(Theme.EDGE);
		guestButtons.add(hint, "gapleft 8");
		people.add(guestButtons);

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, parties, people);
		split.setResizeWeight(0.3);
		split.setBorder(null);
		return split;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.addActionListener(e -> action.run());
		return b;
	}

	private Party selectedParty()
	{
		int row = partyTable.getSelectedRow();
		return row < 0 ? null : guests.parties().get(row);
	}

	private void leadStays(Guest g)
	{
		JOptionPane.showMessageDialog(this, g.getName() + " leads " + g.getParty().getName()
				+ " and stays in it.", "Party", JOptionPane.INFORMATION_MESSAGE);
	}

	private void newParty()
	{
		stopEditing();
		Party p = PartyDialog.show(this, occasion, null);
		if (p == null) return;
		refresh();
		listener.guestsChanged();
		int row = guests.parties().indexOf(p);
		partyTable.setRowSelectionInterval(row, row);
	}

	private void editParty()
	{
		Party p = selectedParty();
		if (p == null) return;
		stopEditing();
		if (PartyDialog.show(this, occasion, p) == null) return;
		refresh();
		listener.guestsChanged();
		int row = guests.parties().indexOf(p);
		partyTable.setRowSelectionInterval(row, row);
	}

	private void chooseColour()
	{
		Party p = selectedParty();
		if (p == null) return;
		Color c = JColorChooser.showDialog(this, "Colour for " + p.getName(), p.getColour());
		if (c == null) return;
		p.setColour(c);
		changed();
	}

	private void removeParty()
	{
		Party p = selectedParty();
		if (p == null) return;
		int named = guests.namedIn(p);
		if (guests.placesOf(p) > 0)
		{
			int answer = JOptionPane.showConfirmDialog(this, "Remove the party " + p.getName() + "?\n"
							+ "Its places not named yet go. Its " + named
							+ (named == 1 ? " named guest stays" : " named guests stay") + " on the guest list, in no party.",
					"Remove party", JOptionPane.OK_CANCEL_OPTION);
			if (answer != JOptionPane.OK_OPTION) return;
		}
		guests.removeParty(p);
		occasion.tidy();
		changed();
	}

	// a new guest in the selected party, with their name ready to type
	private void addGuest()
	{
		stopEditing();
		if (guestTable.getRowSorter() != null)
			guestTable.getRowSorter().setSortKeys(null);  // keep the new guest at the bottom
		Guest g = guests.addGuest("", Gender.UNKNOWN, selectedParty());
		refresh();
		listener.guestsChanged();
		int row = guestTable.convertRowIndexToView(guestModel.rows.indexOf(g));
		if (row < 0) return;
		guestTable.setRowSelectionInterval(row, row);
		guestTable.scrollRectToVisible(guestTable.getCellRect(row, 0, true));
		guestTable.editCellAt(row, 0);
		guestTable.getEditorComponent().requestFocusInWindow();
	}

	private void findSeat(Guest g)
	{
		Seat s = seating.seatOf(g);
		if (s == null) return;
		plan.find(s.tableId()).ifPresent(t ->
		{
			showTable(t, s.chair());
			listener.seatSelected(t, s.chair());
		});
	}

	private Object partyOf(Guest g)
	{
		return g.getParty() == null ? NO_PARTY : g.getParty();
	}

	private String seatOf(Guest g)
	{
		Seat s = seating.seatOf(g);
		if (s == null) return "";
		return plan.find(s.tableId()).map(t -> plan.label(t) + ", seat " + t.seatNumber(s.chair())).orElse("");
	}

	private class PartyModel extends AbstractTableModel
	{
		private final String[] columns = { "", "Party", "Places", "Named", "Invited" };

		public int getRowCount() { return guests.parties().size(); }

		public int getColumnCount() { return columns.length; }

		public String getColumnName(int col) { return columns[col]; }

		public Object getValueAt(int row, int col)
		{
			Party p = guests.parties().get(row);
			return switch (col)
			{
				case 0 -> p.getColour();
				case 1 -> p.getName();
				case 2 -> guests.placesOf(p);
				case 3 -> guests.namedIn(p);
				default -> event.invitedIn(p, guests);
			};
		}
	}

	private class GuestModel extends AbstractTableModel
	{
		private final String[] columns = { "Name", "Gender", "Party", "Invited", "Seat" };
		private List<Guest> rows = List.of();

		void reload()
		{
			rows = unseatedOnly.isSelected() ? invitedNotSeated() : guests.guests();
			fireTableDataChanged();
		}

		public int getRowCount() { return rows.size(); }

		public int getColumnCount() { return columns.length; }

		public String getColumnName(int col) { return columns[col]; }

		public Object getValueAt(int row, int col)
		{
			Guest g = rows.get(row);
			return switch (col)
			{
				case 0 -> g.getName();
				case 1 -> g.getGender();
				case 2 -> partyOf(g);
				case 3 -> event.isInvited(g);
				default -> seatOf(g);
			};
		}

		public Class<?> getColumnClass(int col) { return col == 3 ? Boolean.class : Object.class; }

		public boolean isCellEditable(int row, int col) { return col < 4; }

		public void setValueAt(Object value, int row, int col)
		{
			if (value == null) return;
			Guest g = rows.get(row);
			switch (col)
			{
				case 0 -> guests.rename(g, value.toString());
				case 1 -> g.setGender((Gender) value);
				case 2 ->
				{
					if (!guests.setParty(g, value instanceof Party p ? p : null)) leadStays(g);
				}
				case 3 ->
				{
					if ((Boolean) value) event.invite(g);
					else if (!uninvite(g)) return;
				}
				default -> { return; }
			}
			changed();
		}
	}

	// asks first if this takes away others too (the lead's party) or a seat
	private boolean uninvite(Guest g)
	{
		int others = g.isLead() ? event.invitedIn(g.getParty(), guests) - 1 : 0;
		boolean seated = seating.isSeated(g) || (g.isLead()
				&& guests.membersOf(g.getParty()).stream().anyMatch(seating::isSeated));
		if (others > 0 || seated)
		{
			String what = others > 0
					? g.getName() + " leads " + g.getParty().getName() + ", so the whole party (" + (others + 1)
					+ " guests) will no longer be invited to " + event.getName() + "."
					: g.displayName() + " will no longer be invited to " + event.getName() + ".";
			int answer = JOptionPane.showConfirmDialog(this, what + (seated ? "\nTheir seats here are cleared." : ""),
					"Invitation", JOptionPane.OK_CANCEL_OPTION);
			if (answer != JOptionPane.OK_OPTION) return false;
		}
		event.uninvite(g, guests);
		return true;
	}

	// ---- drawing the cells ----

	// "disabled" or "open access" for a seat no guest can be given; null for one that can
	private String notForGuests(int row)
	{
		if (table == null) return null;
		int chair = seatModel.chairAt(row);
		return table.isDisabled(chair) ? "disabled" : table.isOpen(chair) ? "open access" : null;
	}

	private void setUp(JTable t, IntFunction<Guest> guestOfRow, IntFunction<String> offRow, int nameColumn)
	{
		t.setDefaultRenderer(Object.class, new CellRenderer(guestOfRow, offRow, nameColumn));
		t.setDefaultRenderer(Integer.class, t.getDefaultRenderer(Object.class));
		t.setRowHeight(Math.max(t.getRowHeight(), 22));
		t.setFillsViewportHeight(true);
		t.putClientProperty("terminateEditOnFocusLost", true);
		t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	}

	/*
	 * Genders with their shape, parties with their colour, a seat number with
	 * the mark of whoever sits there; a guest with no name yet shows who they
	 * are with, in grey; a disabled or open-access seat is greyed out.
	 */
	private static class CellRenderer extends DefaultTableCellRenderer
	{
		private final IntFunction<Guest> guestOfRow;
		private final IntFunction<String> offRow;
		private final int nameColumn;

		CellRenderer(IntFunction<Guest> guestOfRow, IntFunction<String> offRow, int nameColumn)
		{
			this.guestOfRow = guestOfRow;
			this.offRow = offRow;
			this.nameColumn = nameColumn;
		}

		public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
				boolean focus, int row, int col)
		{
			super.getTableCellRendererComponent(t, value, selected, focus, row, col);
			setIcon(null);
			setHorizontalAlignment(LEADING);
			setFont(t.getFont());
			Color fg = Theme.TEXT;
			Guest g = guestOfRow.apply(row);
			String offWhat = offRow.apply(row);
			boolean off = offWhat != null;
			if (value instanceof Gender gender)
				setIcon(Marks.icon(gender, Theme.EDGE, ICON));
			else if (value instanceof Party p)
				setIcon(Marks.swatch(p.getColour(), ICON));
			else if (value instanceof Color c)
			{
				setIcon(Marks.swatch(c, ICON));
				setText("");
				setHorizontalAlignment(CENTER);
			} else if (value instanceof Integer && col == 0 && nameColumn == 1)
			{
				// a seat number, with its guest's mark
				setIcon(g != null ? Marks.icon(g.getGender(), GuestList.colourOf(g), ICON) : BLANK);
			} else if (NO_PARTY.equals(value))
				fg = Theme.EDGE;
			if (col == nameColumn)
			{
				if (off) setText(offWhat);
				else if (g != null && !g.isNamed())
				{
					setText(g.displayName());
					setFont(t.getFont().deriveFont(Font.ITALIC));
					fg = Theme.EDGE;
				}
			}
			if (off) fg = Theme.OFF;
			if (!selected) setForeground(fg);
			return this;
		}
	}

	// ---- the key ----

	private JComponent legend()
	{
		JPanel p = new JPanel(new MigLayout("insets 6 8 6 8", "[]12[]12[]push"));
		p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.GRID));
		p.add(new JLabel("Female", Marks.icon(Gender.FEMALE, Theme.EDGE, ICON), SwingConstants.LEADING));
		p.add(new JLabel("Male", Marks.icon(Gender.MALE, Theme.EDGE, ICON), SwingConstants.LEADING));
		p.add(new JLabel("Not known", Marks.icon(Gender.UNKNOWN, Theme.EDGE, ICON), SwingConstants.LEADING));
		JLabel colour = new JLabel("colour: party");
		colour.setForeground(Theme.EDGE);
		p.add(colour);
		return p;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
