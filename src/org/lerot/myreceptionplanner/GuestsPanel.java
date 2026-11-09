package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Contact;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Gender;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Invitation;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/*
 * The Guests view: the guest list of the whole occasion, full width. The
 * parties on the left, each made and edited in PartyDialog; every guest on the
 * right with their gender, party and dietary needs, how their party's lead can
 * be reached (email, phone, address), and where they stand with each event
 * (see InvitationMarks; click a mark to change it). Inviting anyone of a
 * party invites its lead; uninviting the lead uninvites the party, asking
 * first if anyone loses a seat. Seats are given in the Event view.
 */
public class GuestsPanel extends JPanel
{
	public interface Listener
	{
		void guestsChanged();
	}

	private static final String NO_PARTY = "No party";
	private static final int ICON = 14;
	private static final int EVENTS_FROM = 5;  // the first event's column in the guest table

	private final Occasion occasion;
	private final GuestList guests;
	private final List<Event> events;
	private final Listener listener;

	private final PartyModel partyModel = new PartyModel();
	private final JTable partyTable = new JTable(partyModel);
	private final GuestModel guestModel = new GuestModel();
	private final JTable guestTable;  // made once the events are known, a column for each
	private final JComboBox<Object> partyCombo = new JComboBox<>();
	private final JCheckBox chosenPartyOnly = new JCheckBox("Only the party chosen");
	private final JLabel totals = new JLabel();

	public GuestsPanel(Occasion occasion, Listener listener)
	{
		super(new BorderLayout());
		this.occasion = occasion;
		this.guests = occasion.getGuests();
		this.events = occasion.events();
		this.listener = listener;
		guestTable = new JTable(guestModel);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, partiesSide(), guestsSide());
		split.setResizeWeight(0.3);
		split.setDividerLocation(360);
		split.setBorder(null);
		add(split, BorderLayout.CENTER);
		add(legend(), BorderLayout.SOUTH);
		refresh();
	}

	public void refresh()
	{
		stopEditing();
		Party chosen = selectedParty();
		partyCombo.removeAllItems();
		partyCombo.addItem(NO_PARTY);
		guests.parties().forEach(partyCombo::addItem);
		partyModel.fireTableDataChanged();
		select(chosen);
		guestModel.reload();
		showTotals();
	}

	private void stopEditing()
	{
		for (JTable t : new JTable[] { partyTable, guestTable })
			if (t.isEditing()) t.getCellEditor().cancelCellEditing();
	}

	// after an edit: once the edit is over, show it everywhere
	private void changed()
	{
		SwingUtilities.invokeLater(() ->
		{
			occasion.tidy();
			refresh();
			listener.guestsChanged();
		});
	}

	// ---- the parties ----

	private JComponent partiesSide()
	{
		JPanel p = new JPanel(new MigLayout("insets 8, fill, wrap 1", "[grow]", "[][grow][]"));
		p.add(new JLabel("<html><b>Parties</b></html>"));
		setUp(partyTable);
		partyTable.getColumnModel().getColumn(0).setMaxWidth(30);
		for (int c = 2; c <= 3; c++) partyTable.getColumnModel().getColumn(c).setMaxWidth(60);
		partyTable.getSelectionModel().addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && chosenPartyOnly.isSelected()) guestModel.reload();
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
		p.add(new JScrollPane(partyTable), "grow");
		JPanel buttons = new JPanel(new MigLayout("insets 0"));
		buttons.add(button("New…", this::newParty));
		buttons.add(button("Edit…", this::editParty));
		buttons.add(button("Colour…", this::chooseColour));
		buttons.add(button("Remove", this::removeParty));
		p.add(buttons);
		return p;
	}

	private Party selectedParty()
	{
		int row = partyTable.getSelectedRow();
		return row < 0 || row >= guests.parties().size() ? null : guests.parties().get(row);
	}

	private void select(Party p)
	{
		int row = p == null ? -1 : guests.parties().indexOf(p);
		if (row >= 0) partyTable.setRowSelectionInterval(row, row);
	}

	private void newParty()
	{
		stopEditing();
		Party p = PartyDialog.show(this, occasion, null);
		if (p == null) return;
		occasion.tidy();
		refresh();
		select(p);
		listener.guestsChanged();
	}

	private void editParty()
	{
		Party p = selectedParty();
		if (p == null) return;
		stopEditing();
		if (PartyDialog.show(this, occasion, p) == null) return;
		occasion.tidy();
		refresh();
		select(p);
		listener.guestsChanged();
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
		changed();
	}

	private class PartyModel extends AbstractTableModel
	{
		private final String[] columns = { "", "Party", "Places", "Named" };

		public int getRowCount() { return guests.parties().size(); }

		public int getColumnCount() { return columns.length; }

		public String getColumnName(int col) { return columns[col]; }

		public Class<?> getColumnClass(int col) { return col >= 2 ? Integer.class : Object.class; }

		public Object getValueAt(int row, int col)
		{
			Party p = guests.parties().get(row);
			return switch (col)
			{
				case 0 -> p.getColour();
				case 1 -> p.getName();
				case 2 -> guests.placesOf(p);
				default -> guests.namedIn(p);
			};
		}
	}

	// ---- the guests ----

	private JComponent guestsSide()
	{
		JPanel p = new JPanel(new MigLayout("insets 8, fill, wrap 1", "[grow]", "[][grow][]"));
		p.add(new JLabel("<html><b>Guests</b></html>"), "split 2");
		chosenPartyOnly.addActionListener(e -> guestModel.reload());
		p.add(chosenPartyOnly, "gapleft push");
		setUp(guestTable);
		guestTable.setAutoCreateRowSorter(true);
		guestTable.setDefaultRenderer(Invitation.class, InvitationMarks.renderer());
		TableColumn gender = guestTable.getColumnModel().getColumn(1);
		gender.setCellEditor(new DefaultCellEditor(new JComboBox<>(Gender.values())));
		gender.setPreferredWidth(90);
		guestTable.getColumnModel().getColumn(0).setPreferredWidth(200);
		guestTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(partyCombo));
		guestTable.getColumnModel().getColumn(2).setPreferredWidth(160);
		guestTable.getColumnModel().getColumn(4).setPreferredWidth(90);
		guestTable.getColumnModel().getColumn(4).setMaxWidth(110);
		for (int i = 0; i < events.size(); i++)
		{
			TableColumn c = guestTable.getColumnModel().getColumn(EVENTS_FROM + i);
			c.setPreferredWidth(90);
			c.setMaxWidth(160);
			c.setHeaderValue(events.get(i).getName());
		}
		guestTable.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				int row = guestTable.rowAtPoint(e.getPoint());
				if (row < 0) return;
				Guest g = guestAt(row);
				int col = guestTable.convertColumnIndexToModel(guestTable.columnAtPoint(e.getPoint()));
				if (col >= EVENTS_FROM)
				{
					InvitationMarks.menu(guestTable, e.getX(), e.getY(), occasion, events.get(col - EVENTS_FROM), g,
							GuestsPanel.this::changed);
					return;
				}
				if (e.getClickCount() != 2) return;
				if (col == 3 && DietDialog.editNamed(GuestsPanel.this, g)) changed();
				else if (col == 4 && g.getParty() != null)
				{
					select(g.getParty());
					editParty();
				}
			}
		});
		p.add(new JScrollPane(guestTable), "grow");
		JPanel buttons = new JPanel(new MigLayout("insets 0"));
		buttons.add(button("Add guest", this::addGuest));
		buttons.add(button("Remove", () ->
		{
			int row = guestTable.getSelectedRow();
			if (row >= 0) removeGuest(guestAt(row));
		}));
		JLabel hint = new JLabel("Click an invitation mark to change it; double-click a diet to change it, "
				+ "the lead's contact to edit the party");
		hint.setForeground(Theme.EDGE);
		buttons.add(hint, "gapleft 8");
		p.add(buttons);
		return p;
	}

	private Guest guestAt(int viewRow)
	{
		return guestModel.rows.get(guestTable.convertRowIndexToModel(viewRow));
	}

	// a new guest in the chosen party, with their name ready to type
	private void addGuest()
	{
		stopEditing();
		if (guestTable.getRowSorter() != null) guestTable.getRowSorter().setSortKeys(null);
		Guest g = guests.addGuest("", Gender.UNKNOWN, selectedParty());
		occasion.tidy();
		refresh();
		listener.guestsChanged();
		int row = guestTable.convertRowIndexToView(guestModel.rows.indexOf(g));
		if (row < 0) return;
		guestTable.setRowSelectionInterval(row, row);
		guestTable.scrollRectToVisible(guestTable.getCellRect(row, 0, true));
		guestTable.editCellAt(row, 0);
		if (guestTable.getEditorComponent() != null) guestTable.getEditorComponent().requestFocusInWindow();
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
		changed();
	}

	// "✉ ☎ ⌂" for the ways a party's lead can be reached, a dot for each missing
	private static String reach(Guest g)
	{
		if (!g.isLead() && g.getParty() != null) return "";
		Contact c = g.getContact();
		if (c == null) return "· · ·";
		boolean phone = !c.getPhone().isEmpty() || !c.getMobile().isEmpty() || !c.getWhatsApp().isEmpty();
		return (c.getEmail().isEmpty() ? "·" : "✉") + " " + (phone ? "☎" : "·") + " "
				+ (c.getAddress().isEmpty() ? "·" : "⌂");
	}

	private void showTotals()
	{
		long named = guests.guests().stream().filter(Guest::isNamed).count();
		boolean over = false;
		StringBuilder s = new StringBuilder(guests.parties().size() + (guests.parties().size() == 1 ? " party, " : " parties, ")
				+ guests.guests().size() + " guests (" + named + " named)");
		for (Event e : events)
		{
			int[] n = new int[Invitation.values().length];
			e.invitations().values().forEach(i -> n[i.ordinal()]++);
			s.append("   ·   ").append(e.getName()).append(": ").append(e.invited().size()).append(" expected (")
					.append(n[Invitation.ACCEPTED.ordinal()]).append(" accepted, ")
					.append(n[Invitation.INVITED.ordinal()]).append(" awaiting reply, ")
					.append(n[Invitation.TO_INVITE.ordinal()]).append(" to invite), ")
					.append(n[Invitation.DECLINED.ordinal()]).append(" declined");
			if (e.overCapacity() > 0)
			{
				s.append(" ⚠ ").append(e.overCapacity()).append(" more than its ")
						.append(e.getKind() == Event.Kind.HALL ? "seats" : "capacity");
				over = true;
			}
		}
		totals.setForeground(over ? Theme.HEAD.darker() : Theme.EDGE);
		totals.setText(s.toString());
	}

	private class GuestModel extends AbstractTableModel
	{
		private final String[] columns = { "Name", "Gender", "Party", "Diet", "Reach" };
		private List<Guest> rows = List.of();

		void reload()
		{
			Party only = chosenPartyOnly.isSelected() ? selectedParty() : null;
			rows = only == null ? guests.guests() : guests.membersOf(only);
			fireTableDataChanged();
		}

		public int getRowCount() { return rows.size(); }

		public int getColumnCount() { return columns.length + events.size(); }

		public String getColumnName(int col) { return col < columns.length ? columns[col] : events.get(col - EVENTS_FROM).getName(); }

		public Class<?> getColumnClass(int col) { return col >= EVENTS_FROM ? Invitation.class : Object.class; }

		public Object getValueAt(int row, int col)
		{
			Guest g = rows.get(row);
			return switch (col)
			{
				case 0 -> g.getName();
				case 1 -> g.getGender();
				case 2 -> g.getParty() == null ? NO_PARTY : g.getParty();
				case 3 -> g.dietText();
				case 4 -> reach(g);
				default -> events.get(col - EVENTS_FROM).invitation(g);
			};
		}

		public boolean isCellEditable(int row, int col) { return col <= 2; }

		public void setValueAt(Object value, int row, int col)
		{
			if (value == null) return;
			Guest g = rows.get(row);
			switch (col)
			{
				case 0 ->
				{
					if (value.toString().equals(g.getName())) return;
					if (NameCheck.ask(GuestsPanel.this, guests, value.toString(), g, false) != NameCheck.Answer.DIFFERENT) return;
					guests.rename(g, value.toString());
				}
				case 1 -> g.setGender((Gender) value);
				case 2 ->
				{
					if (!guests.setParty(g, value instanceof Party p ? p : null))
					{
						JOptionPane.showMessageDialog(GuestsPanel.this, g.getName() + " leads " + g.getParty().getName()
								+ " and stays in it.", "Party", JOptionPane.INFORMATION_MESSAGE);
						return;
					}
				}
				default -> { return; }
			}
			changed();
		}
	}

	// ---- drawing ----

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.addActionListener(e -> action.run());
		return b;
	}

	private void setUp(JTable t)
	{
		t.setDefaultRenderer(Object.class, new CellRenderer());
		t.setDefaultRenderer(Integer.class, new CellRenderer());
		t.setRowHeight(Math.max(t.getRowHeight(), 22));
		t.setFillsViewportHeight(true);
		t.putClientProperty("terminateEditOnFocusLost", true);
		t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		t.getTableHeader().setReorderingAllowed(false);
	}

	// genders with their shape, parties with their colour; a place not named
	// yet shows who it is with, in grey italics
	private class CellRenderer extends DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
				int row, int col)
		{
			super.getTableCellRendererComponent(t, value, selected, focus, row, col);
			setIcon(null);
			setFont(t.getFont());
			setHorizontalAlignment(value instanceof Integer ? TRAILING : LEADING);
			Color fg = Theme.TEXT;
			if (value instanceof Gender gender) setIcon(Marks.icon(gender, Theme.EDGE, ICON));
			else if (value instanceof Party p) setIcon(Marks.swatch(p.getColour(), ICON));
			else if (value instanceof Color c)
			{
				setIcon(Marks.swatch(c, ICON));
				setText("");
				setHorizontalAlignment(CENTER);
			} else if (NO_PARTY.equals(value)) fg = Theme.EDGE;
			if (t == guestTable && t.convertColumnIndexToModel(col) == 0)
			{
				Guest g = guestAt(row);
				if (!g.isNamed())
				{
					setText(g.displayName());
					setFont(t.getFont().deriveFont(Font.ITALIC));
					fg = Theme.EDGE;
				}
			}
			if (t == guestTable && t.convertColumnIndexToModel(col) == 4) setHorizontalAlignment(CENTER);
			if (!selected) setForeground(fg);
			return this;
		}
	}

	// the five marks, in a row
	static JComponent marksKey()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		p.setOpaque(false);
		for (Invitation state : Invitation.values())
		{
			if (state == Invitation.NONE) continue;
			JLabel l = new JLabel(state.toString().replace(" (sent)", "").replace(" or cancelled", ""),
					InvitationMarks.icon(state, 14), SwingConstants.LEADING);
			l.setForeground(Theme.EDGE);
			p.add(l);
		}
		return p;
	}

	private JComponent legend()
	{
		JPanel p = new JPanel(new MigLayout("insets 6 8 6 8", "[]12[]12[]12[]12[]push"));
		p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.GRID));
		p.add(new JLabel("Female", Marks.icon(Gender.FEMALE, Theme.EDGE, ICON), SwingConstants.LEADING));
		p.add(new JLabel("Male", Marks.icon(Gender.MALE, Theme.EDGE, ICON), SwingConstants.LEADING));
		p.add(new JLabel("Not known", Marks.icon(Gender.UNKNOWN, Theme.EDGE, ICON), SwingConstants.LEADING));
		JLabel key = new JLabel("Reach: ✉ email  ☎ phone  ⌂ address");
		key.setForeground(Theme.EDGE);
		p.add(key);
		p.add(marksKey());
		totals.setForeground(Theme.EDGE);
		p.add(totals, "newline, span");
		return p;
	}
}
