package org.lerot.myreceptionplanner.gui.dialogs;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.gui.Theme;
import org.lerot.myreceptionplanner.model.Contact;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.MessageTemplate;
import org.lerot.myreceptionplanner.model.Messages;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;
import org.lerot.myreceptionplanner.reports.ContactList;
import org.lerot.myreceptionplanner.reports.LettersPdf;
import org.lerot.myreceptionplanner.reports.ReportPdf;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/*
 * Writing to the parties' leads. Choose a message (a template) and the event
 * its event fill-ins speak of, and which parties to show; tick those to
 * write to. Each party's message is shown completed. Then open it as an
 * email in the mail program or in WhatsApp, ready to check and send, or make
 * a PDF of letters or of address labels. Each is noted against the party
 * ("last sent"), as prepared: whether it was sent is up to you.
 */
public class MessagesDialog
{
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm");

	private final Occasion occasion;
	private final GuestList guests;
	private final Function<String, Path> choosePdf;
	private final java.util.function.Consumer<Path> openFile;
	private final JComboBox<MessageTemplate> template;
	private final JComboBox<Object> eventChoice = new JComboBox<>();
	private final JComboBox<String> show = new JComboBox<>();
	private int showing;
	private final JTextArea preview = new JTextArea(18, 46);
	private final JLabel previewSubject = new JLabel(" ");
	private final List<Party> rows = new ArrayList<>();
	private final List<Party> ticked = new ArrayList<>();
	private final Model model = new Model();
	private final JTable table = new JTable(model);
	private boolean changed;

	private MessagesDialog(Occasion occasion, Event current, Function<String, Path> choosePdf,
			java.util.function.Consumer<Path> openFile)
	{
		this.occasion = occasion;
		this.guests = occasion.getGuests();
		this.choosePdf = choosePdf;
		this.openFile = openFile;
		template = new JComboBox<>(occasion.templates().toArray(new MessageTemplate[0]));
		eventChoice.addItem("No particular event");
		occasion.events().forEach(eventChoice::addItem);
		eventChoice.setSelectedItem(current);
		eventChoice.setRenderer(new DefaultListCellRenderer()
		{
			public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f)
			{
				super.getListCellRendererComponent(l, v, i, s, f);
				if (v instanceof Event e) setText(e.getName());
				return this;
			}
		});
	}

	// true if any message was noted as prepared
	public static boolean show(Component parent, Occasion occasion, Event current, Function<String, Path> choosePdf,
			java.util.function.Consumer<Path> openFile)
	{
		return show(parent, occasion, current, choosePdf, openFile, null, 0);
	}

	/*
	 * Starting with the template of that name (if there is one) and the Show
	 * choice given: 0 all parties, 1 invited to the event, 2 with details
	 * missing, 3 not yet sent this message, 4 with menu choices to make, 5
	 * with invitations to send.
	 */
	public static boolean show(Component parent, Occasion occasion, Event current, Function<String, Path> choosePdf,
			java.util.function.Consumer<Path> openFile, String templateName, int showing)
	{
		if (occasion.templates().isEmpty())
		{
			JOptionPane.showMessageDialog(parent, "There are no message templates; add some with Messages ▾ → "
					+ "Message templates….", "Write to guests", JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		if (occasion.getGuests().parties().isEmpty())
		{
			JOptionPane.showMessageDialog(parent, "There are no parties to write to yet.", "Write to guests",
					JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		MessagesDialog d = new MessagesDialog(occasion, current, choosePdf, openFile);
		occasion.templates().stream().filter(t -> t.getName().equals(templateName)).findFirst()
				.ifPresent(d.template::setSelectedItem);
		d.showing = showing;
		return d.run(parent);
	}

	private Event event()
	{
		return eventChoice.getSelectedItem() instanceof Event e ? e : null;
	}

	private boolean run(Component parent)
	{
		for (String s : new String[] { "All parties", "Invited to the event", "With details missing",
				"Not yet sent this message", "With menu choices to make", "With invitations to send (○)" })
			show.addItem(s);
		show.setSelectedIndex(showing);
		show.addActionListener(e -> reload());
		eventChoice.addActionListener(e -> reload());
		template.addActionListener(e -> reload());

		table.setRowHeight(Math.max(table.getRowHeight(), 22));
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getColumnModel().getColumn(0).setMaxWidth(30);
		table.getColumnModel().getColumn(1).setPreferredWidth(150);
		for (int c = 3; c <= 5; c++) table.getColumnModel().getColumn(c).setMaxWidth(70);
		table.getColumnModel().getColumn(6).setPreferredWidth(110);
		DefaultTableCellRenderer centred = new DefaultTableCellRenderer();
		centred.setHorizontalAlignment(SwingConstants.CENTER);
		for (int c = 3; c <= 5; c++) table.getColumnModel().getColumn(c).setCellRenderer(centred);
		table.getSelectionModel().addListSelectionListener(e -> showPreview());

		preview.setEditable(false);
		preview.setLineWrap(true);
		preview.setWrapStyleWord(true);
		preview.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, preview.getFont().getSize()));

		JPanel top = new JPanel(new MigLayout("insets 0", "[][grow,fill]20[][grow,fill]20[][grow,fill]"));
		top.add(new JLabel("Event"));
		top.add(eventChoice);
		top.add(new JLabel("Message"));
		top.add(template);
		top.add(new JLabel("Show"));
		top.add(show);

		JPanel left = new JPanel(new BorderLayout(0, 4));
		left.add(new JScrollPane(table), BorderLayout.CENTER);
		JPanel tickButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		JButton all = new JButton("Tick all");
		all.addActionListener(e -> { ticked.clear(); ticked.addAll(rows); model.fireTableDataChanged(); });
		JButton none = new JButton("Tick none");
		none.addActionListener(e -> { ticked.clear(); model.fireTableDataChanged(); });
		tickButtons.add(all);
		tickButtons.add(none);
		left.add(tickButtons, BorderLayout.SOUTH);

		JPanel right = new JPanel(new BorderLayout(0, 4));
		previewSubject.setFont(previewSubject.getFont().deriveFont(Font.BOLD));
		right.add(previewSubject, BorderLayout.NORTH);
		right.add(new JScrollPane(preview), BorderLayout.CENTER);
		JButton copy = new JButton("Copy this message");
		copy.addActionListener(e -> copyPreview());
		JPanel copyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		copyRow.add(copy);
		right.add(copyRow, BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		split.setResizeWeight(0.55);
		split.setBorder(null);

		JPanel send = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		send.add(new JLabel("For the ticked parties:"));
		send.add(button("List…", this::list));
		send.add(button("Email…", this::email));
		send.add(button("WhatsApp…", this::whatsApp));
		send.add(button("Letters (PDF)…", this::letters));
		send.add(button("Address labels (PDF)…", this::labels));
		JLabel hint = new JLabel("List makes a list to contact by hand (or to try things out without sending). "
				+ "Emails open in your mail program and WhatsApp in its app or browser, ready to check and send. "
				+ "Each is noted as last sent.");
		hint.setForeground(Theme.EDGE);

		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.add(top, BorderLayout.NORTH);
		p.add(split, BorderLayout.CENTER);
		JPanel bottom = new JPanel(new BorderLayout(0, 4));
		bottom.add(send, BorderLayout.NORTH);
		bottom.add(hint, BorderLayout.SOUTH);
		p.add(bottom, BorderLayout.SOUTH);
		p.setPreferredSize(new Dimension(1100, 600));
		reload();

		Object[] close = { "Close" };
		JOptionPane.showOptionDialog(parent, p, "Write to guests: " + occasion.getName(), JOptionPane.DEFAULT_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, close, close[0]);
		return changed;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.addActionListener(e -> action.run());
		return b;
	}

	// the parties the Show choice lets through
	private void reload()
	{
		Party selected = selected();
		rows.clear();
		Event e = event();
		MessageTemplate t = (MessageTemplate) template.getSelectedItem();
		for (Party p : guests.parties())
		{
			boolean keep = switch (show.getSelectedIndex())
			{
				case 1 -> e == null ? anyEvent(p) : e.invitedIn(p, guests) > 0;
				case 2 -> !Messages.fill(new MessageTemplate("", "", "{missing}"), occasion, p, e).body()
						.startsWith("We have everything");
				case 3 -> t != null && occasion.lastSent(p, t.getName()) == null;
				case 4 -> !Messages.menusToChoose(occasion, p).isEmpty();
				case 5 -> toInvite(p);
				default -> true;
			};
			if (keep) rows.add(p);
		}
		ticked.retainAll(rows);
		model.fireTableDataChanged();
		int at = selected == null ? -1 : rows.indexOf(selected);
		if (at < 0 && !rows.isEmpty()) at = 0;
		if (at >= 0) table.setRowSelectionInterval(at, at);
		showPreview();
	}

	// some of the party are to be invited somewhere: the invitation is still to send
	private boolean toInvite(Party p)
	{
		return occasion.events().stream().anyMatch(e -> guests.membersOf(p).stream()
				.anyMatch(g -> e.invitation(g) == org.lerot.myreceptionplanner.model.Invitation.TO_INVITE));
	}

	private boolean anyEvent(Party p)
	{
		return occasion.events().stream().anyMatch(e -> e.invitedIn(p, guests) > 0);
	}

	private Party selected()
	{
		int row = table.getSelectedRow();
		return row < 0 || row >= rows.size() ? null : rows.get(row);
	}

	private Messages.Message message(Party p)
	{
		MessageTemplate t = (MessageTemplate) template.getSelectedItem();
		return Messages.fill(t, occasion, p, event());
	}

	private void showPreview()
	{
		Party p = selected();
		if (p == null || template.getSelectedItem() == null)
		{
			previewSubject.setText(" ");
			preview.setText("");
			return;
		}
		Messages.Message m = message(p);
		String subject = m.subject().isEmpty() ? " " : escape(m.subject());
		boolean invitation = ((MessageTemplate) template.getSelectedItem()).getName().equals(Messages.INVITATION);
		if (invitation && !toInvite(p) && anyEvent(p))
			subject += "<br><span style='color:#C0392B;font-weight:normal'>Already invited: a reminder may suit better</span>";
		previewSubject.setText("<html>" + subject + "</html>");
		preview.setText(m.body());
		preview.setCaretPosition(0);
	}

	private void copyPreview()
	{
		Party p = selected();
		if (p == null) return;
		Messages.Message m = message(p);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new StringSelection(m.subject() + "\n\n" + m.body()), null);
	}

	private List<Party> tickedOrSelected()
	{
		if (!ticked.isEmpty()) return List.copyOf(ticked);
		Party p = selected();
		return p == null ? List.of() : List.of(p);
	}

	private static Contact contact(Party p)
	{
		return p.getLead() == null ? new Contact() : p.getLead().getContact();
	}

	private String whatsAppNumber(Party p)
	{
		Contact c = contact(p);
		String n = !c.getWhatsApp().isEmpty() ? c.getWhatsApp() : c.getMobile();
		return Messages.whatsAppNumber(n, occasion.getCountryCode());
	}

	private void note(Party p, String how)
	{
		occasion.logSent(p, ((MessageTemplate) template.getSelectedItem()).getName(), how, event());
		changed = true;
	}

	// ---- sending ----

	private void email()
	{
		List<Party> to = tickedOrSelected();
		List<Party> with = to.stream().filter(p -> !contact(p).getEmail().isEmpty()).toList();
		if (!confirm("email", to, with)) return;
		for (Party p : with)
		{
			Messages.Message m = message(p);
			try
			{
				Desktop.getDesktop().mail(new URI("mailto:" + contact(p).getEmail() + "?subject=" + enc(m.subject())
						+ "&body=" + enc(m.body())));
				note(p, "email");
			} catch (Exception ex)
			{
				problem("Cannot open an email for " + p.getName() + ": " + ex.getMessage());
				break;
			}
		}
		model.fireTableDataChanged();
	}

	private void whatsApp()
	{
		List<Party> to = tickedOrSelected();
		List<Party> with = to.stream().filter(p -> !whatsAppNumber(p).isEmpty()).toList();
		if (!confirm("WhatsApp message", to, with)) return;
		for (Party p : with)
		{
			try
			{
				Desktop.getDesktop().browse(new URI("https://wa.me/" + whatsAppNumber(p) + "?text=" + enc(message(p).body())));
				note(p, "whatsapp");
			} catch (Exception ex)
			{
				problem("Cannot open WhatsApp for " + p.getName() + ": " + ex.getMessage());
				break;
			}
		}
		model.fireTableDataChanged();
	}

	/*
	 * A list of the ticked parties with every way to reach them and their
	 * message in full, to contact by hand; noting them as sent "by list"
	 * moves on their invitations as if sent. A PDF of it if wanted.
	 */
	private void list()
	{
		List<Party> to = tickedOrSelected();
		if (to.isEmpty()) return;
		MessageTemplate t = (MessageTemplate) template.getSelectedItem();
		Map<Party, Messages.Message> messages = new LinkedHashMap<>();
		for (Party p : to) messages.put(p, message(p));
		ContactList report = new ContactList(occasion, t.getName(), messages);
		JEditorPane page = new JEditorPane("text/html", report.html());
		page.setEditable(false);
		page.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(page);
		scroll.setPreferredSize(new Dimension(820, 520));
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.add(scroll, BorderLayout.CENTER);
		JLabel note = new JLabel("Noting them as sent records \"" + t.getName() + "\" against each party, by list"
				+ (t.getName().equals(Messages.INVITATION) ? ", and turns their rings (to be invited) into dots (invited)." : "."));
		note.setForeground(Theme.EDGE);
		p.add(note, BorderLayout.SOUTH);
		Object[] options = { "Note as sent and save as PDF…", "Note as sent", "Cancel" };
		int choice = JOptionPane.showOptionDialog(table, p, "List: " + t.getName(), JOptionPane.DEFAULT_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, options, options[2]);
		if (choice != 0 && choice != 1) return;
		if (choice == 0)
		{
			Path file = choosePdf.apply("list-" + slug(t.getName()) + ".pdf");
			if (file == null) return;
			try
			{
				ReportPdf.write(report, file, false);
				openFile.accept(file);
			} catch (IOException ex)
			{
				problem(ex.getMessage());
				return;
			}
		}
		to.forEach(party -> note(party, "list"));
		reload();
	}

	private void letters()
	{
		List<Party> to = tickedOrSelected();
		if (to.isEmpty()) return;
		long noAddress = to.stream().filter(p -> contact(p).getAddress().isEmpty()).count();
		if (noAddress > 0 && JOptionPane.showConfirmDialog(table, noAddress + " of the " + to.size()
				+ " have no address; their letters will have only the name.", "Letters",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		Path file = choosePdf.apply("letters-" + slug(((MessageTemplate) template.getSelectedItem()).getName()) + ".pdf");
		if (file == null) return;
		List<LettersPdf.Letter> letters = new ArrayList<>();
		for (Party p : to) letters.add(new LettersPdf.Letter(p, message(p)));
		try
		{
			LettersPdf.letters(occasion, letters, file);
			to.forEach(p -> note(p, "letter"));
			model.fireTableDataChanged();
			openFile.accept(file);
		} catch (IOException ex)
		{
			problem(ex.getMessage());
		}
	}

	private void labels()
	{
		List<Party> to = tickedOrSelected().stream().filter(p -> !contact(p).getAddress().isEmpty()).toList();
		if (to.isEmpty())
		{
			problem("None of these parties' leads has an address.");
			return;
		}
		Path file = choosePdf.apply("address-labels.pdf");
		if (file == null) return;
		try
		{
			LettersPdf.labels(to, file);
			openFile.accept(file);
		} catch (IOException ex)
		{
			problem(ex.getMessage());
		}
	}

	// asks before opening several, and says who has no way to be reached
	private boolean confirm(String what, List<Party> to, List<Party> with)
	{
		if (to.isEmpty()) return false;
		if (with.isEmpty())
		{
			problem("None of these parties' leads has " + (what.equals("email") ? "an email address." : "a WhatsApp or mobile number."));
			return false;
		}
		String missing = to.size() > with.size() ? "\n" + (to.size() - with.size()) + " have no "
				+ (what.equals("email") ? "email address" : "WhatsApp or mobile number") + " and are left out." : "";
		if (with.size() == 1 && missing.isEmpty()) return true;
		return JOptionPane.showConfirmDialog(table, "Open " + with.size() + " " + what + (with.size() == 1 ? "" : "s")
				+ ", one for each lead?" + missing, "Write to guests", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
	}

	private void problem(String message)
	{
		JOptionPane.showMessageDialog(table, message, "Write to guests", JOptionPane.WARNING_MESSAGE);
	}

	// for a mailto or WhatsApp link: spaces as %20, not +
	private static String enc(String s)
	{
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String slug(String s)
	{
		return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private class Model extends AbstractTableModel
	{
		private final String[] columns = { "", "Party", "Lead", "Email", "WhatsApp", "Address", "Last sent" };

		public int getRowCount() { return rows.size(); }

		public int getColumnCount() { return columns.length; }

		public String getColumnName(int col) { return columns[col]; }

		public Class<?> getColumnClass(int col) { return col == 0 ? Boolean.class : String.class; }

		public boolean isCellEditable(int row, int col) { return col == 0; }

		public Object getValueAt(int row, int col)
		{
			Party p = rows.get(row);
			Contact c = contact(p);
			return switch (col)
			{
				case 0 -> ticked.contains(p);
				case 1 -> p.getName();
				case 2 -> p.getLead() == null ? "" : p.getLead().getName();
				case 3 -> c.getEmail().isEmpty() ? "" : "✓";
				case 4 -> whatsAppNumber(p).isEmpty() ? "" : "✓";
				case 5 -> c.getAddress().isEmpty() ? "" : "✓";
				default ->
				{
					MessageTemplate t = (MessageTemplate) template.getSelectedItem();
					Messages.Sent s = t == null ? null : occasion.lastSent(p, t.getName());
					yield s == null ? "" : WHEN.format(s.when()) + " (" + s.how() + ")";
				}
			};
		}

		public void setValueAt(Object value, int row, int col)
		{
			Party p = rows.get(row);
			if ((Boolean) value) { if (!ticked.contains(p)) ticked.add(p); }
			else ticked.remove(p);
		}
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
