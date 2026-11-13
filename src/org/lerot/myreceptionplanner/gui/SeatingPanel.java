package org.lerot.myreceptionplanner.gui;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.gui.dialogs.DietDialog;
import org.lerot.myreceptionplanner.gui.dialogs.NameCheck;
import org.lerot.myreceptionplanner.gui.dialogs.PartyDialog;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Gender;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Name;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;
import org.lerot.myreceptionplanner.model.Seat;
import org.lerot.myreceptionplanner.model.Seating;
import org.lerot.myreceptionplanner.model.tables.Plan;
import org.lerot.myreceptionplanner.model.tables.Table;

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
 * Seating mode's side panel, with two tabs. It sits beside the plan, and
 * its width is dragged (MainWindow keeps it). It opens on Guests, and turns
 * to Table when a table is chosen on the plan.
 *
 * Table lists the seats of the table chosen on the plan, seat 1 first, and
 * who sits in each. On an empty seat, choosing a party seats one of its
 * places not yet seated (a new place if none is left); typing a name puts a
 * new guest there. Right-click a seat to seat someone from the guest list, or
 * to clear it.
 *
 * Guests lists the parties (each made and edited in PartyDialog) and the
 * guests with an invitation to this event, with its mark (see
 * InvitationMarks; click for what it means; it is changed in the Guests
 * view). Choosing a party lists only its guests, until Show all (or Esc,
 * or Ctrl-click on the party). A place not named yet shows as the
 * lead's name +1, +2 ...; type a name over it to name that guest.
 */
public class SeatingPanel extends JPanel
{
	public interface Listener
	{
		void guestsChanged();

		// a seat chosen here, to show on the plan
		void seatSelected(Table table, int chair);
	}

	public static final int START_WIDTH = 600;  // until dragged
	public static final int MIN_WIDTH = 300;

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
	private static final int TABLE_TAB = 1;  // Guests first, as no table is chosen at the start
	private final JLabel tableTitle = new JLabel();
	private final SeatModel seatModel = new SeatModel();
	private final JTable seatTable = new JTable(seatModel);
	private final PartyModel partyModel = new PartyModel();
	private final JTable partyTable = new JTable(partyModel);
	private final GuestModel guestModel = new GuestModel();
	private final JTable guestTable = new JTable(guestModel);
	private final JCheckBox unseatedOnly = new JCheckBox("Not seated");
	private final FindField find = new FindField(() -> guestModel.reload());
	private final JLabel guestsTitle = new JLabel();
	private final JButton showAll = new JButton("Show all");
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
		setPreferredSize(new Dimension(START_WIDTH, 0));
		setMinimumSize(new Dimension(MIN_WIDTH, 0));
		setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.GRID));
		tabs.addTab("Guests", guestsTab());
		tabs.addTab("Table", tableTab());
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
		if (t != null) tabs.setSelectedIndex(TABLE_TAB);
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
		Party chosen = selectedParty();
		partyModel.fireTableDataChanged();
		int partyRow = guests.parties().indexOf(chosen);  // still chosen, so the list stays narrowed
		if (partyRow >= 0) partyTable.setRowSelectionInterval(partyRow, partyRow);
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
				+ (table.places() == 0 ? "no seats"
				: seating.seatedAt(table.getId()).size() + " of " + table.reservedPlaces() + " seats taken") + "</html>");
	}

	// ---- the Table tab ----

	private JComponent tableTab()
	{
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		p.add(tableTitle, BorderLayout.NORTH);
		setUp(seatTable, seatModel::guestAt, this::notForGuests, 1);
		seatTable.getColumnModel().getColumn(0).setMaxWidth(60);
		seatTable.getColumnModel().getColumn(1).setCellEditor(NameFields.dialogEditor(occasion.getHonorifics(), "Guest in this seat",
				row -> { Guest g = seatModel.guestAt(row); return g == null ? Name.NONE : g.getNameParts(); }));
		seatTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(genderChooser()));
		narrow(seatTable.getColumnModel().getColumn(2));
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
		if (!InvitationMarks.mayRemove(this, occasion, List.of(g), "Remove " + g.displayName() + " from the guest list?"))
			return;
		guests.removeGuest(g);
		occasion.tidy();
		changed();
	}

	// the seats of the shown table, seat 1 first
	private class SeatModel extends AbstractTableModel
	{
		private final String[] columns = { "Seat", "Guest", "M/F", "Party" };

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
					Name name = (Name) value;
					if (g == null && name.isEmpty()) return;
					if (g != null && name.equals(g.getNameParts())) return;
					if (g == null)
					{
						// someone already on the list may be meant: seat them instead
						List<GuestList.Likeness> like = guests.like(name, null);
						NameCheck.Answer a = like.isEmpty() ? NameCheck.Answer.DIFFERENT
								: NameCheck.ask(SeatingPanel.this, name.full(), like, like.size() == 1);
						if (a == NameCheck.Answer.BACK) return;
						if (a == NameCheck.Answer.USE_EXISTING) g = like.get(0).b();
						else
						{
							g = guests.addGuest(name, Gender.UNKNOWN, lastParty);
							NameCheck.remember(guests, g, like);
						}
						event.invite(g);
						seating.seat(g, seat);
					} else
					{
						if (!name.isEmpty() && !name.plain().equals(g.getNameParts().plain())
								&& NameCheck.ask(SeatingPanel.this, guests, name, g, false) != NameCheck.Answer.DIFFERENT) return;
						guests.rename(g, name);
					}
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

	private Component this_() { return this; }

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
		partyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		partyTable.getSelectionModel().addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting()) guestModel.reload();
		});
		partyTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "showAll");
		partyTable.getActionMap().put("showAll", new AbstractAction()
		{
			public void actionPerformed(java.awt.event.ActionEvent e) { partyTable.clearSelection(); }
		});
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

		JPanel people = new JPanel(new MigLayout("insets 8, fill, wrap 1", "[grow]", "[][grow][][]"));
		people.add(guestsTitle, "split 4");
		showAll.setToolTipText("List the guests of every party again (or Esc in the parties)");
		showAll.addActionListener(e -> partyTable.clearSelection());
		people.add(showAll, "gapleft 8");
		people.add(find, "gapleft 8, growx 0");
		unseatedOnly.addActionListener(e -> guestModel.reload());
		people.add(unseatedOnly, "gapleft push");
		setUp(guestTable, row -> guestModel.rows.get(guestTable.convertRowIndexToModel(row)), row -> null, 0);
		guestTable.setDefaultRenderer(org.lerot.myreceptionplanner.model.Invitation.class, InvitationMarks.renderer(false));
		guestTable.setAutoCreateRowSorter(true);
		guestTable.getColumnModel().getColumn(0).setCellEditor(NameFields.dialogEditor(occasion.getHonorifics(), "Name",
				row -> guestModel.rows.get(row).getNameParts()));
		guestTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(genderChooser()));
		narrow(guestTable.getColumnModel().getColumn(1));
		guestTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(guestPartyCombo));
		guestTable.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				int row = guestTable.rowAtPoint(e.getPoint());
				if (row < 0) return;
				Guest g = guestModel.rows.get(guestTable.convertRowIndexToModel(row));
				int col = guestTable.convertColumnIndexToModel(guestTable.columnAtPoint(e.getPoint()));
				if (col == 3)
				{
					InvitationMarks.explain(guestTable, e.getX(), e.getY(), event, g);
					return;
				}
				if (e.getClickCount() != 2) return;
				if (col == 5) findSeat(g);
				else if (col == 4 && DietDialog.editNamed(SeatingPanel.this, g)) changed();
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
		JLabel hint = new JLabel("<html>Click a mark for what it means;<br>double-click a diet or a seat</html>");
		hint.setForeground(Theme.EDGE);
		guestButtons.add(hint, "gapleft 8");
		people.add(guestButtons);
		people.add(GuestsPanel.marksKey());

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
		List<Guest> going = guests.membersOf(p).stream().filter(g -> !g.isNamed()).toList();
		if (!InvitationMarks.mayRemove(this, occasion, going, "Remove the party " + p.getName() + "?\n"
				+ "Its places not named yet go. Its " + named
				+ (named == 1 ? " named guest stays" : " named guests stay") + " on the guest list, in no party."))
			return;
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
		private final String[] columns = { "Name", "M/F", "Party", "Invited", "Diet", "Seat" };
		private List<Guest> rows = List.of();

		void reload()
		{
			Party only = selectedParty();
			rows = (unseatedOnly.isSelected() ? invitedNotSeated()
					: guests.guests().stream().filter(g -> event.invitation(g) != org.lerot.myreceptionplanner.model.Invitation.NONE).toList())
					.stream().filter(g -> (only == null || g.getParty() == only) && g.matches(find.getText())).toList();
			fireTableDataChanged();
			guestsTitle.setText("<html><b>Guests</b>" + (only == null ? "" : ": " + escape(only.getName())) + "</html>");
			showAll.setVisible(only != null);
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
				case 3 -> event.invitation(g);
				case 4 -> g.dietText();
				default -> seatOf(g);
			};
		}

		public Class<?> getColumnClass(int col) { return col == 3 ? org.lerot.myreceptionplanner.model.Invitation.class : Object.class; }

		public boolean isCellEditable(int row, int col) { return col < 3; }

		public void setValueAt(Object value, int row, int col)
		{
			if (value == null) return;
			Guest g = rows.get(row);
			switch (col)
			{
				case 0 ->
				{
					Name name = (Name) value;
					if (name.equals(g.getNameParts())) return;
					if (!name.isEmpty() && !name.plain().equals(g.getNameParts().plain())
							&& NameCheck.ask(this_(), guests, name, g, false) != NameCheck.Answer.DIFFERENT) return;
					guests.rename(g, name);
				}
				case 1 -> g.setGender((Gender) value);
				case 2 ->
				{
					if (!guests.setParty(g, value instanceof Party p ? p : null)) leadStays(g);
				}
				default -> { return; }
			}
			changed();
		}
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

	// for a gender column: the marks alone, each named in its tooltip (and
	// in the legend below)
	private static JComboBox<Gender> genderChooser()
	{
		JComboBox<Gender> c = new JComboBox<>(Gender.values());
		c.setRenderer(new DefaultListCellRenderer()
		{
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus)
			{
				super.getListCellRendererComponent(l, value, index, sel, focus);
				Gender g = (Gender) value;
				setIcon(g == null ? null : Marks.icon(g, Theme.EDGE, ICON));
				setText("");
				setToolTipText(g == null ? null : g.toString());
				setHorizontalAlignment(CENTER);
				return this;
			}
		});
		return c;
	}

	// a gender column: just wide enough for its mark and "M/F"
	private static void narrow(javax.swing.table.TableColumn c)
	{
		c.setMinWidth(36);
		c.setPreferredWidth(40);
		c.setMaxWidth(48);
	}

	/*
	 * Genders by their shape alone (the word in the tooltip), parties with their colour, a seat number with
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
			setToolTipText(null);
			if (value instanceof Gender gender)
			{
				setIcon(Marks.icon(gender, Theme.EDGE, ICON));
				setText("");
				setToolTipText(gender.toString());
				setHorizontalAlignment(CENTER);
			}
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
