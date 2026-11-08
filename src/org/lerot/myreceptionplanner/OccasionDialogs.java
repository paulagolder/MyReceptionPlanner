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
 * an event (its name, place, date and time). Dates are typed, "12 May 2026";
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
		JPanel form = form();
		row(form, "Occasion", name);
		row(form, "Date", date);
		note(form, "These go at the top of the reports and in anything sent to the guests.");
		while (true)
		{
			if (!ok(parent, form, "Occasion")) return false;
			try
			{
				if (name.getText().isBlank()) throw new IllegalArgumentException("The occasion needs a name.");
				LocalDate d = Occasion.parseDate(date.getText());
				occasion.setName(name.getText());
				occasion.setDate(d);
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
		row(form, "Start with", start);
		row(form, "Invite", invite);
		note(form, "Leave the date empty if it is on the occasion's day. Copied tables start with no one seated. "
				+ "Change who is invited later with Occasion ▾ → Invitations….");
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
		JPanel form = form();
		row(form, "Event", name);
		row(form, "Place", place);
		row(form, "Date", date);
		row(form, "Time", time);
		note(form, "Leave the date empty if it is on the occasion's day"
				+ (occasion.getDate() == null ? "." : " (" + Occasion.DATE.format(occasion.getDate()) + ")."));
		while (true)
		{
			if (!ok(parent, form, "Event")) return false;
			try
			{
				if (name.getText().isBlank()) throw new IllegalArgumentException("The event needs a name.");
				LocalDate d = Occasion.parseDate(date.getText());
				event.setName(name.getText());
				event.setPlace(place.getText());
				event.setDate(d);
				event.setTime(time.getText());
				return true;
			} catch (IllegalArgumentException ex)
			{
				problem(parent, ex.getMessage());
			}
		}
	}

	// the same tables, by writing them out and reading them back
	private static Plan copy(Plan plan) throws IOException
	{
		Element holder = DocumentHelper.createElement("copy");
		PlanXml.write(plan, holder);
		return PlanXml.read(holder.element("tables"));
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
