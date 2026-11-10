package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.contacts.MyContactsBook;
import org.lerot.myreceptionplanner.model.Contact;
import org.lerot.myreceptionplanner.model.Gender;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
 * Makes a new party or edits one: the lead guest and how to reach them (typed
 * in, or copied once from MyContacts), the party's own name if it has one,
 * its number of places, and its guests. A new party is the lead and places
 * not named yet, "Ann Smith+1", "Ann Smith+2" ...; type over those to name
 * them. Each guest, named or not, may have a role ("Guest speaker"). Places
 * cannot go below the named guests. Nothing changes until OK.
 */
public class PartyDialog
{
	// what the dialog shows for each guest of the party, changed only on OK
	private static class Row
	{
		Guest guest;  // null for a place added here
		String name;
		Gender gender;
		String role = "";
		int plus;
		boolean lead;

		Row(Guest g, boolean seated)
		{
			guest = g;
			this.seated = seated;
			name = g.getName();
			gender = g.getGender();
			role = g.getRole();
			plus = g.getPlus();
			lead = g.isLead();
		}

		Row(int plus)
		{
			name = "";
			gender = Gender.UNKNOWN;
			this.plus = plus;
		}

		boolean named() { return !name.isBlank(); }

		boolean seated;  // at any event
		boolean locked;  // an invitation sent to them stands, so they stay
	}

	private final Occasion occasion;
	private final GuestList guests;
	private final Party party;  // null for a new one
	private final List<Row> rows = new ArrayList<>();

	private final JTextField leadName = new JTextField();
	private final JTextField partyName = new JTextField();
	private final JSpinner places = new JSpinner(new SpinnerNumberModel(2, 1, 60, 1));
	private final JLabel placesHint = new JLabel();
	private final JTextArea address = new JTextArea(3, 24);
	private final JTextField phone = new JTextField();
	private final JTextField mobile = new JTextField();
	private final JTextField whatsApp = new JTextField();
	private final JTextField email = new JTextField();
	private final JLabel source = new JLabel();
	private String contactSource;
	private final MemberModel memberModel = new MemberModel();
	private final JTable memberTable = new JTable(memberModel);

	// the party made or edited, or null if cancelled
	public static Party show(Component parent, Occasion occasion, Party party)
	{
		return new PartyDialog(occasion, party).run(parent);
	}

	private PartyDialog(Occasion occasion, Party party)
	{
		this.occasion = occasion;
		this.guests = occasion.getGuests();
		this.party = party;
		if (party == null)
		{
			Row lead = new Row(0);
			lead.lead = true;
			rows.add(lead);
			rows.add(new Row(1));  // the plus one
			contactSource = "";
		} else
		{
			// the lead first, then the named, then the places by number
			guests.membersOf(party).stream()
					.sorted(Comparator.comparing((Guest g) -> !g.isLead())
							.thenComparing(g -> !g.isNamed())
							.thenComparingInt(Guest::getPlus))
					.forEach(g ->
					{
						Row r = new Row(g, occasion.seatedAnywhere(g));
						r.locked = !occasion.invitationsStanding(g).isEmpty();
						rows.add(r);
					});
			Guest lead = party.getLead();
			if (lead == null)
			{
				// from a file made before parties had leads
				Row r = new Row(0);
				r.lead = true;
				rows.add(0, r);
			}
			leadName.setText(lead == null ? "" : lead.getName());
			if (party.hasOwnName()) partyName.setText(party.getName());
			Contact c = lead == null ? new Contact() : lead.getContact();
			address.setText(c.getAddress());
			phone.setText(c.getPhone());
			mobile.setText(c.getMobile());
			whatsApp.setText(c.getWhatsApp());
			email.setText(c.getEmail());
			contactSource = c.getSource();
		}
		places.setValue(rows.size());
		showSource();
		updatePlacesLimit();
	}

	private Party run(Component parent)
	{
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 3", "[right][grow,fill][]"));
		form.add(new JLabel("Lead guest"));
		form.add(leadName, "w 220!");
		JButton find = new JButton("Find in MyContacts…");
		find.addActionListener(e -> findInMyContacts(form));
		form.add(find);
		partyName.putClientProperty("JTextField.placeholderText", "the lead's name");
		form.add(new JLabel("Party name"));
		form.add(partyName, "wrap");
		form.add(new JLabel("Places"));
		form.add(places, "w 70!, growx 0, split 2");
		placesHint.setForeground(Theme.EDGE);
		form.add(placesHint, "wrap");

		form.add(heading("Contact for the lead guest"), "span 3, gaptop 10, growx");
		address.setLineWrap(true);
		address.setWrapStyleWord(true);
		form.add(new JLabel("Address"), "top");
		form.add(new JScrollPane(address), "span 2");
		form.add(new JLabel("Phone"));
		form.add(phone, "wrap");
		form.add(new JLabel("Mobile"));
		form.add(mobile, "wrap");
		form.add(new JLabel("WhatsApp"));
		form.add(whatsApp);
		JButton sameAsMobile = new JButton("Same as mobile");
		sameAsMobile.addActionListener(e -> whatsApp.setText(mobile.getText()));
		form.add(sameAsMobile);
		form.add(new JLabel("Email"));
		form.add(email, "wrap");
		source.setForeground(Theme.EDGE);
		form.add(source, "skip, span 2");

		form.add(heading("Guests"), "span 3, gaptop 10, growx");
		memberTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(Gender.values())));
		memberTable.getColumnModel().getColumn(2).setCellEditor(RoleField.editor());
		memberTable.getColumnModel().getColumn(2).setPreferredWidth(150);
		memberTable.getColumnModel().getColumn(0).setPreferredWidth(200);
		memberTable.setDefaultRenderer(Object.class, new MemberRenderer());
		memberTable.putClientProperty("terminateEditOnFocusLost", true);
		memberTable.setRowHeight(Math.max(memberTable.getRowHeight(), 22));
		memberTable.setFillsViewportHeight(true);
		memberTable.setPreferredScrollableViewportSize(new Dimension(520, 150));
		form.add(new JScrollPane(memberTable), "span 3, grow");
		JLabel hint = new JLabel("Type over a +1, +2 … to name that guest.");
		hint.setForeground(Theme.EDGE);
		form.add(hint, "span 3");

		places.addChangeListener(e -> resize((Integer) places.getValue()));
		leadName.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { leadChanged(); }

			public void removeUpdate(DocumentEvent e) { leadChanged(); }

			public void changedUpdate(DocumentEvent e) { leadChanged(); }
		});

		String title = party == null ? "New party" : "Party: " + party.getName();
		while (true)
		{
			int answer = JOptionPane.showConfirmDialog(parent, form, title,
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (answer != JOptionPane.OK_OPTION) return null;
			if (memberTable.isEditing()) memberTable.getCellEditor().stopCellEditing();
			long goingNow = party == null ? 0 : guests.membersOf(party).stream()
					.filter(g -> rows.stream().noneMatch(r -> r.guest == g)).count();
			if (goingNow > 0 && JOptionPane.showConfirmDialog(parent, goingNow == 1
							? "One place not named yet will be taken off the guest list. Are you sure?"
							: goingNow + " places not named yet will be taken off the guest list. Are you sure?",
					title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
				continue;
			if (!leadName.getText().isBlank())
			{
				List<Pending> alike = namesAlike();
				if (alike.isEmpty() || sameAnyway(parent, alike))
				{
					Party p = apply();
					for (Pending a : alike)
					{
						if (a.other() != null && a.row().guest != null) guests.markDistinct(a.row().guest, a.other());
					}
					return p;
				}
				continue;
			}
			JOptionPane.showMessageDialog(parent, "A party needs a lead guest's name.", title,
					JOptionPane.WARNING_MESSAGE);
		}
	}

	private static JComponent heading(String text)
	{
		JLabel l = new JLabel("<html><b>" + text + "</b></html>");
		l.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.GRID));
		return l;
	}

	private void leadChanged()
	{
		rows.get(0).name = leadName.getText().trim();
		updatePlacesLimit();
		memberModel.fireTableDataChanged();
	}

	// places cannot go below the named guests
	private void updatePlacesLimit()
	{
		int named = Math.max(1, (int) rows.stream().filter(Row::named).count());
		int locked = (int) rows.stream().filter(r -> !r.named() && r.locked).count();
		((SpinnerNumberModel) places.getModel()).setMinimum(named + locked);
		if ((Integer) places.getValue() < named + locked) places.setValue(named + locked);
		long open = rows.stream().filter(r -> !r.named()).count();
		placesHint.setText(named + " named" + (open > 0 ? ", " + open + " not named yet" : "")
				+ (locked > 0 ? " (" + locked + " invited, so staying)" : ""));
	}

	// adds places not named yet, or takes them away: those not seated first,
	// the highest numbered first (as GuestList.setPlaces does)
	private void resize(int n)
	{
		if (memberTable.isEditing()) memberTable.getCellEditor().stopCellEditing();
		while (rows.size() < n)
			rows.add(new Row(rows.stream().mapToInt(r -> r.plus).max().orElse(0) + 1));
		if (rows.size() > n)
		{
			List<Row> going = rows.stream()
					.filter(r -> !r.named() && !r.lead && !r.locked)
					.sorted(Comparator.comparing((Row r) -> r.seated).thenComparing(Comparator.comparingInt((Row r) -> r.plus).reversed()))
					.limit(rows.size() - n)
					.toList();
			rows.removeAll(going);
		}
		updatePlacesLimit();
		memberModel.fireTableDataChanged();
	}

	// a name in the dialog like another: a guest elsewhere on the list, or another name here
	private record Pending(Row row, Guest other, String otherName, String why) {}

	// the names typed or changed here that look like someone already on the list, or like each other
	private List<Pending> namesAlike()
	{
		rows.get(0).name = leadName.getText().trim();
		List<Guest> here = rows.stream().map(r -> r.guest).filter(g -> g != null).toList();
		List<Pending> out = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++)
		{
			Row r = rows.get(i);
			if (!r.named() || (r.guest != null && r.guest.getName().equals(r.name))) continue;
			for (var l : guests.like(r.name, r.guest))
			{
				if (!here.contains(l.b())) out.add(new Pending(r, l.b(), l.b().getName()
						+ (l.b().getParty() == null ? "" : " (" + l.b().getParty().getName() + ")"), l.why()));
			}
			for (int j = 0; j < rows.size(); j++)
			{
				Row o = rows.get(j);
				if (j == i || !o.named() || (j < i && changed(o))) continue;
				org.lerot.myreceptionplanner.model.NameMatch.why(r.name, o.name)
						.ifPresent(w -> out.add(new Pending(r, o.guest, o.name + " (in this party)", w)));
			}
		}
		return out;
	}

	private static boolean changed(Row r)
	{
		return r.named() && (r.guest == null || !r.guest.getName().equals(r.name));
	}

	// true to save anyway: they are different people
	private boolean sameAnyway(Component parent, List<Pending> alike)
	{
		StringBuilder html = new StringBuilder("<html><body style='width:380px'>"
				+ "These names look like others on the guest list:<ul>");
		for (Pending a : alike)
			html.append("<li><b>").append(escape(a.row().name)).append("</b> and <b>").append(escape(a.otherName()))
					.append("</b> — ").append(escape(a.why())).append("</li>");
		html.append("</ul>Each person should be on the list only once, in one party.</body></html>");
		Object[] options = { "Save: they are different people", "Go back" };
		return JOptionPane.showOptionDialog(parent, new JLabel(html.toString()), "Already on the guest list?",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]) == 0;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private Party apply()
	{
		String lead = leadName.getText().trim();
		Party p = party;
		if (p == null)
		{
			p = guests.createParty(lead, 1, partyName.getText());
			rows.get(0).guest = p.getLead();
		} else
		{
			p.setName(partyName.getText());
			if (p.getLead() != null) guests.rename(p.getLead(), lead);
			else
			{
				Guest g = guests.addGuest(lead, Gender.UNKNOWN, p);
				guests.setLead(p, g);
				rows.get(0).guest = g;
			}
		}
		// guests taken away in the dialog
		List<Guest> kept = rows.stream().map(r -> r.guest).filter(g -> g != null).toList();
		for (Guest g : guests.membersOf(p))
		{
			if (!kept.contains(g)) guests.removeGuest(g);
		}
		for (Row r : rows)
		{
			if (r.guest == null) r.guest = guests.addGuest(r.name, r.gender, p);
			else if (!r.lead) guests.rename(r.guest, r.name);
			r.guest.setGender(r.gender);
			r.guest.setRole(r.role);
		}
		Contact c = p.getLead().getContact();
		c.setAddress(address.getText());
		c.setPhone(phone.getText());
		c.setMobile(mobile.getText());
		c.setWhatsApp(whatsApp.getText());
		c.setEmail(email.getText());
		c.setSource(c.isEmpty() ? "" : contactSource);
		occasion.tidy();  // no seats for places taken away
		return p;
	}

	private void showSource()
	{
		source.setText(contactSource.isEmpty() ? " " : "Copied from " + contactSource + "; not kept in step with it.");
	}

	// ---- MyContacts ----

	private void findInMyContacts(Component parent)
	{
		List<MyContactsBook.Entry> entries;
		try
		{
			parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			entries = MyContactsBook.standard().all();
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(parent, e.getMessage(), "MyContacts", JOptionPane.ERROR_MESSAGE);
			return;
		} finally
		{
			parent.setCursor(Cursor.getDefaultCursor());
		}
		MyContactsBook.Entry chosen = choose(parent, entries, leadName.getText().trim());
		if (chosen == null) return;
		leadName.setText(chosen.name());
		address.setText(chosen.address());
		phone.setText(chosen.phone());
		mobile.setText(chosen.mobile());
		email.setText(chosen.email());
		contactSource = chosen.addressFrom().isEmpty() ? "MyContacts"
				: "MyContacts (address from " + chosen.addressFrom() + ")";
		showSource();
	}

	// a list of the contacts, narrowed as you type
	private static MyContactsBook.Entry choose(Component parent, List<MyContactsBook.Entry> entries, String start)
	{
		DefaultListModel<MyContactsBook.Entry> shown = new DefaultListModel<>();
		JList<MyContactsBook.Entry> list = new JList<>(shown);
		list.setCellRenderer(new DefaultListCellRenderer()
		{
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus)
			{
				super.getListCellRendererComponent(l, value, index, sel, focus);
				MyContactsBook.Entry e = (MyContactsBook.Entry) value;
				setText(e.place().isEmpty() ? e.name() : e.name() + "   —   " + e.place());
				return this;
			}
		});
		list.setVisibleRowCount(14);
		JTextField search = new JTextField(start, 24);
		search.putClientProperty("JTextField.placeholderText", "type part of a name");
		Runnable filter = () ->
		{
			String words = search.getText().trim().toLowerCase();
			shown.clear();
			for (MyContactsBook.Entry e : entries)
			{
				if (words.isEmpty() || e.name().toLowerCase().contains(words)) shown.addElement(e);
			}
			if (!shown.isEmpty()) list.setSelectedIndex(0);
		};
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { filter.run(); }

			public void removeUpdate(DocumentEvent e) { filter.run(); }

			public void changedUpdate(DocumentEvent e) { filter.run(); }
		});
		filter.run();
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.add(search, BorderLayout.NORTH);
		p.add(new JScrollPane(list), BorderLayout.CENTER);
		JLabel note = new JLabel("The details are copied once; MyContacts is not changed.");
		note.setForeground(Theme.EDGE);
		p.add(note, BorderLayout.SOUTH);
		JOptionPane pane = new JOptionPane(p, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		JDialog dialog = pane.createDialog(parent, "Find in MyContacts");
		list.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2 && list.getSelectedValue() != null)
				{
					pane.setValue(JOptionPane.OK_OPTION);
					dialog.dispose();
				}
			}
		});
		search.addActionListener(e ->
		{
			pane.setValue(JOptionPane.OK_OPTION);
			dialog.dispose();
		});
		dialog.addWindowListener(new java.awt.event.WindowAdapter()
		{
			public void windowOpened(java.awt.event.WindowEvent e) { search.requestFocusInWindow(); }
		});
		dialog.setVisible(true);
		Object value = pane.getValue();
		if (!(value instanceof Integer i) || i != JOptionPane.OK_OPTION) return null;
		return list.getSelectedValue();
	}

	// ---- the guests ----

	private class MemberModel extends AbstractTableModel
	{
		private final String[] columns = { "Name", "Gender", "Role", "Seat" };

		public int getRowCount() { return rows.size(); }

		public int getColumnCount() { return columns.length; }

		public String getColumnName(int col) { return columns[col]; }

		public Object getValueAt(int row, int col)
		{
			Row r = rows.get(row);
			return switch (col)
			{
				case 0 -> r.name;
				case 1 -> r.gender;
				case 2 -> r.role;
				default -> r.seated ? "seated" : "";
			};
		}

		// the lead's name is the one above
		public boolean isCellEditable(int row, int col)
		{
			return col == 1 || col == 2 || (col == 0 && !rows.get(row).lead);
		}

		public void setValueAt(Object value, int row, int col)
		{
			if (value == null) return;
			Row r = rows.get(row);
			if (col == 0) r.name = value.toString().trim();
			else if (col == 1) r.gender = (Gender) value;
			else if (col == 2) r.role = value.toString().strip();
			updatePlacesLimit();
		}
	}

	// a place not named yet shows as the lead's name and its number, in grey
	private class MemberRenderer extends DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
				boolean focus, int row, int col)
		{
			super.getTableCellRendererComponent(t, value, selected, focus, row, col);
			Row r = rows.get(row);
			setIcon(null);
			setFont(t.getFont());
			Color fg = Theme.TEXT;
			if (col == 0)
			{
				if (r.lead) setText(r.name + "  (lead)");
				else if (!r.named())
				{
					String lead = leadName.getText().isBlank() ? "Lead" : leadName.getText().trim();
					setText(lead + "+" + r.plus);
					setFont(t.getFont().deriveFont(Font.ITALIC));
					fg = Theme.EDGE;
				}
			} else if (value instanceof Gender g)
				setIcon(Marks.icon(g, Theme.EDGE, 14));
			if (!selected) setForeground(fg);
			return this;
		}
	}
}
