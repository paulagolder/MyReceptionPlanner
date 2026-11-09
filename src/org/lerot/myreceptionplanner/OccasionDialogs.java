package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.PlanXml;
import org.lerot.myreceptionplanner.model.Seating;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;

/*
 * The dialogs for an occasion and its events: making a new occasion with its
 * first event, changing an occasion's name and date, and making or changing
 * an event (its name, place, date, time, type and capacity). Dates are typed, "12 May 2026";
 * an event left without a date is on the occasion's.
 */
public class OccasionDialogs
{
	// a new occasion with its first event, or null if cancelled
	public static Occasion newOccasion(Component parent)
	{
		JTextField name = new JTextField(28);
		name.putClientProperty("JTextField.placeholderText", "Lydia and Isaac's Wedding");
		JTextField date = dateField(null);
		JTextField eventName = new JTextField("Wedding Breakfast");
		JTextField place = new JTextField();
		JTextField time = new JTextField();
		JPanel form = form();
		row(form, "Occasion", name);
		row(form, "Date", date);
		form.add(heading("Its first event"), "span 2, gaptop 10, growx");
		row(form, "Event", eventName);
		row(form, "Place", place);
		row(form, "Time", time);
		while (true)
		{
			if (!ok(parent, form, "New occasion")) return null;
			try
			{
				if (name.getText().isBlank()) throw new IllegalArgumentException("The occasion needs a name.");
				Occasion o = new Occasion(name.getText(), new GuestList());
				o.setDate(Occasion.parseDate(date.getText()));
				Event e = new Event(eventName.getText(), o.fileFor(eventName.getText()), new Plan(), new Seating());
				e.setPlace(place.getText());
				e.setTime(time.getText());
				o.addEvent(e);
				return o;
			} catch (IllegalArgumentException ex)
			{
				problem(parent, ex.getMessage());
			}
		}
	}

	// true if changed
	public static boolean editOccasion(Component parent, Occasion occasion)
	{
		JTextField name = new JTextField(occasion.getName(), 28);
		JTextField date = dateField(occasion.getDate());
		JTextField from = new JTextField(occasion.getSender());
		from.putClientProperty("JTextField.placeholderText", "Lydia and Isaac");
		JTextField reply = new JTextField(occasion.getReplyTo());
		reply.putClientProperty("JTextField.placeholderText", "lydia@example.com or 07700 900123");
		JTextField code = new JTextField(occasion.getCountryCode());
		JPanel form = form();
		row(form, "Occasion", name);
		row(form, "Date", date);
		note(form, "These go at the top of the reports and in anything sent to the guests.");
		form.add(heading("Messages to the guests"), "span 2, gaptop 10, growx");
		row(form, "From", from);
		row(form, "Reply to", reply);
		row(form, "Phone country code", code);
		note(form, "From signs the messages; Reply to says how to answer. The country code (44 for the UK) "
				+ "turns numbers such as 07700 900123 into international ones for WhatsApp.");
		String[] emblem = { occasion.getEmblem() };
		form.add(heading("Emblem"), "span 2, gaptop 10, growx");
		JComponent preview = emblemPreview(emblem);
		form.add(preview, "span 2, center, w 260!, h 150!");
		JPanel emblemButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		JButton importSvg = new JButton("Import SVG…");
		importSvg.addActionListener(e ->
		{
			String svg = importSvg(parent);
			if (svg != null) { emblem[0] = svg; preview.repaint(); }
		});
		emblemButtons.add(importSvg);
		for (String sample : org.lerot.myreceptionplanner.reports.Emblem.SAMPLES)
		{
			JButton b = new JButton(Character.toUpperCase(sample.charAt(0)) + sample.substring(1));
			b.setToolTipText("Use the sample " + sample + " (open emblem.svg in the occasion's folder to change it)");
			b.addActionListener(e ->
			{
				try
				{
					emblem[0] = org.lerot.myreceptionplanner.reports.Emblem.sample(sample);
					preview.repaint();
				} catch (java.io.IOException ex)
				{
					problem(parent, ex.getMessage());
				}
			});
			emblemButtons.add(b);
		}
		JButton removeEmblem = new JButton("Remove");
		removeEmblem.addActionListener(e -> { emblem[0] = null; preview.repaint(); });
		emblemButtons.add(removeEmblem);
		form.add(emblemButtons, "span 2, growx");
		note(form, "An SVG picture for the top of the table plan, the letters and the reports. It is kept as "
				+ "emblem.svg in the occasion's folder, where it can be changed with any SVG editor (Inkscape).");
		while (true)
		{
			if (!ok(parent, form, "Occasion")) return false;
			try
			{
				if (name.getText().isBlank()) throw new IllegalArgumentException("The occasion needs a name.");
				LocalDate d = Occasion.parseDate(date.getText());
				occasion.setName(name.getText());
				occasion.setDate(d);
				occasion.setSender(from.getText());
				occasion.setReplyTo(reply.getText());
				occasion.setCountryCode(code.getText());
				occasion.setEmblem(emblem[0]);
				return true;
			} catch (IllegalArgumentException ex)
			{
				problem(parent, ex.getMessage());
			}
		}
	}

	// a new event, its tables empty or copied from another event; null if cancelled
	public static Event newEvent(Component parent, Occasion occasion, Event current)
	{
		JTextField name = new JTextField(28);
		name.putClientProperty("JTextField.placeholderText", "Evening Reception");
		JTextField place = new JTextField(current == null ? "" : current.getPlace());
		JTextField date = dateField(null);
		JTextField time = new JTextField();
		JComboBox<Event.Kind> kind = new JComboBox<>(Event.Kind.values());
		JSpinner capacity = capacityField(0);
		kind.addActionListener(a -> capacity.setEnabled(kind.getSelectedItem() == Event.Kind.UNRESERVED));
		capacity.setEnabled(false);
		JComboBox<Object> start = new JComboBox<>();
		start.addItem("An empty room");
		for (Event e : occasion.events())
			start.addItem(e);
		if (current != null) start.setSelectedItem(current);
		start.setRenderer(new DefaultListCellRenderer()
		{
			public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f)
			{
				super.getListCellRendererComponent(l, v, i, s, f);
				if (v instanceof Event e) setText("The tables of " + e.getName());
				return this;
			}
		});
		JComboBox<Object> invite = new JComboBox<>();
		invite.addItem("Everyone");
		invite.addItem("No one");
		for (Event e : occasion.events())
			invite.addItem(e);
		invite.setRenderer(new DefaultListCellRenderer()
		{
			public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f)
			{
				super.getListCellRendererComponent(l, v, i, s, f);
				if (v instanceof Event e) setText("The guests invited to " + e.getName());
				return this;
			}
		});
		JPanel form = form();
		row(form, "Event", name);
		row(form, "Place", place);
		row(form, "Date", date);
		row(form, "Time", time);
		row(form, "Type", kind);
		row(form, "Capacity", capacity);
		row(form, "Start with", start);
		row(form, "Invite", invite);
		note(form, "Leave the date empty if it is on the occasion's day. A hall or ceremony is set out with "
				+ "pews whose seats are open to all, and only seats you reserve are given to guests. A party, "
				+ "unreserved, has invitations and may have tables set out, but no seats are given; its capacity "
				+ "(0 for no limit) warns when too many are invited. Copied tables start with no one seated. "
				+ "Change who is invited later in the Guests view.");
		while (true)
		{
			if (!ok(parent, form, "New event")) return null;
			try
			{
				if (name.getText().isBlank()) throw new IllegalArgumentException("The event needs a name.");
				LocalDate d = Occasion.parseDate(date.getText());
				Plan plan = start.getSelectedItem() instanceof Event from ? copy(from.getPlan()) : new Plan();
				Event e = new Event(name.getText(), occasion.fileFor(name.getText()), plan, new Seating());
				e.setPlace(place.getText());
				e.setDate(d);
				e.setTime(time.getText());
				e.setKind((Event.Kind) kind.getSelectedItem());
				e.setCapacity((Integer) capacity.getValue());
				GuestList guests = occasion.getGuests();
				if (invite.getSelectedItem() instanceof Event from) e.setInvited(from.invited(), guests);
				else if ("Everyone".equals(invite.getSelectedItem())) e.setInvited(guests.guests(), guests);
				return e;
			} catch (IllegalArgumentException | IOException ex)
			{
				problem(parent, ex.getMessage());
			}
		}
	}

	// true if changed
	public static boolean editEvent(Component parent, Occasion occasion, Event event)
	{
		JTextField name = new JTextField(event.getName(), 28);
		JTextField place = new JTextField(event.getPlace());
		JTextField date = dateField(event.getDate());
		JTextField time = new JTextField(event.getTime());
		JComboBox<Event.Kind> kind = new JComboBox<>(Event.Kind.values());
		kind.setSelectedItem(event.getKind());
		JSpinner capacity = capacityField(event.getCapacity());
		capacity.setEnabled(!event.isSeated());
		kind.addActionListener(a -> capacity.setEnabled(kind.getSelectedItem() == Event.Kind.UNRESERVED));
		JPanel form = form();
		row(form, "Event", name);
		row(form, "Place", place);
		row(form, "Date", date);
		row(form, "Time", time);
		row(form, "Type", kind);
		row(form, "Capacity", capacity);
		note(form, "Leave the date empty if it is on the occasion's day"
				+ (occasion.getDate() == null ? "." : " (" + Occasion.DATE.format(occasion.getDate()) + ")."));
		while (true)
		{
			if (!ok(parent, form, "Event")) return false;
			try
			{
				if (name.getText().isBlank()) throw new IllegalArgumentException("The event needs a name.");
				LocalDate d = Occasion.parseDate(date.getText());
				Event.Kind k = (Event.Kind) kind.getSelectedItem();
				int seated = event.getSeating().seatedCount();
				if (k == Event.Kind.UNRESERVED && event.isSeated() && seated > 0
						&& JOptionPane.showConfirmDialog(parent, "As an unreserved party, " + event.getName()
								+ " gives no seats, so its " + seated + " seated guests lose their seats.",
						"Event", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) continue;
				event.setName(name.getText());
				event.setPlace(place.getText());
				event.setDate(d);
				event.setTime(time.getText());
				event.setKind(k);
				event.setCapacity((Integer) capacity.getValue());
				return true;
			} catch (IllegalArgumentException ex)
			{
				problem(parent, ex.getMessage());
			}
		}
	}

	// the emblem drawn to fit, or a line saying there is none
	private static JComponent emblemPreview(String[] emblem)
	{
		JComponent c = new JComponent()
		{
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g;
				g2.setColor(Theme.PAGE);
				g2.fillRect(0, 0, getWidth(), getHeight());
				var doc = org.lerot.myreceptionplanner.reports.Emblem.parse(emblem[0]);
				if (doc != null) org.lerot.myreceptionplanner.reports.Emblem.draw(g2, doc, 6, 6, getWidth() - 12, getHeight() - 12);
				else
				{
					g2.setColor(Theme.EDGE);
					String t = "No emblem";
					g2.drawString(t, (getWidth() - g2.getFontMetrics().stringWidth(t)) / 2, getHeight() / 2);
				}
			}
		};
		c.setBorder(BorderFactory.createLineBorder(Theme.GRID));
		return c;
	}

	// the SVG file chosen, read; null if cancelled or not a picture that can be drawn
	private static String importSvg(Component parent)
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Choose an SVG picture for the emblem");
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SVG pictures", "svg"));
		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
		try
		{
			String svg = java.nio.file.Files.readString(fc.getSelectedFile().toPath(), java.nio.charset.StandardCharsets.UTF_8);
			if (org.lerot.myreceptionplanner.reports.Emblem.parse(svg) == null)
			{
				problem(parent, fc.getSelectedFile().getName() + " is not an SVG picture that can be drawn.");
				return null;
			}
			return svg;
		} catch (java.io.IOException | RuntimeException ex)
		{
			problem(parent, "Cannot read " + fc.getSelectedFile().getName() + ": " + ex.getMessage());
			return null;
		}
	}

	// the same tables, by writing them out and reading them back
	private static Plan copy(Plan plan) throws IOException
	{
		Element holder = DocumentHelper.createElement("copy");
		PlanXml.write(plan, holder);
		return PlanXml.read(holder.element("tables"));
	}

	// guests; 0 for no limit
	private static JSpinner capacityField(int value)
	{
		return new JSpinner(new SpinnerNumberModel(value, 0, 10000, 10));
	}

	private static JTextField dateField(LocalDate date)
	{
		JTextField f = new JTextField(date == null ? "" : Occasion.DATE.format(date));
		f.putClientProperty("JTextField.placeholderText", "12 May 2026");
		return f;
	}

	private static JPanel form()
	{
		return new JPanel(new MigLayout("insets 0, wrap 2", "[right][300!,fill]"));
	}

	private static void row(JPanel form, String label, JComponent field)
	{
		form.add(new JLabel(label));
		form.add(field);
	}

	private static void note(JPanel form, String text)
	{
		JLabel l = new JLabel("<html><body style='width:280px'>" + text + "</body></html>");
		l.setForeground(Theme.EDGE);
		form.add(l, "skip, gaptop 6");
	}

	private static JComponent heading(String text)
	{
		JLabel l = new JLabel("<html><b>" + text + "</b></html>");
		l.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.GRID));
		return l;
	}

	private static boolean ok(Component parent, JPanel form, String title)
	{
		return JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
	}

	private static void problem(Component parent, String message)
	{
		JOptionPane.showMessageDialog(parent, message, "Please check", JOptionPane.WARNING_MESSAGE);
	}
}
