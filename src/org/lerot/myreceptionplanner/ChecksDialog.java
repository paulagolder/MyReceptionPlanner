package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Checks;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Occasion;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/*
 * The checks on an event, side by side: tables with seats free, and parties
 * with guests not yet seated, each with its total.
 */
public class ChecksDialog
{
	public static void show(Component parent, Occasion occasion, Event event)
	{
		if (!event.isSeated())
		{
			showUnreserved(parent, occasion, event);
			return;
		}
		if (event.getKind() == Event.Kind.HALL)
		{
			showHall(parent, occasion, event);
			return;
		}
		List<Checks.SpareSeats> spare = Checks.spareSeats(event.getPlan(), event.getSeating());
		List<Checks.Unseated> unseated = Checks.unseated(occasion.getGuests(), event);

		JPanel p = new JPanel(new MigLayout("insets 0, wrap 2, gapx 20", "[grow,fill][grow,fill]", "[][grow,fill][]"));
		int spareTotal = spare.stream().mapToInt(Checks.SpareSeats::spare).sum();
		int unseatedTotal = unseated.stream().mapToInt(Checks.Unseated::unseated).sum();
		p.add(heading("Spare places"));
		p.add(heading("Guests not seated"));

		DefaultTableModel spareModel = model("Table", "Spare", "of");
		spare.forEach(s -> spareModel.addRow(new Object[] { s.label(), s.spare(), s.usable() }));
		DefaultTableModel unseatedModel = model("Party", "Not seated", "of");
		unseated.forEach(u -> unseatedModel.addRow(new Object[] { u.name(), u.unseated(), u.places() }));
		p.add(list(spareModel, "Every table is full."), "w 300::, h 260::");
		p.add(list(unseatedModel, "Everyone has a seat."), "w 300::");

		p.add(total(spareTotal == 0 ? "" : spareTotal + " spare " + (spareTotal == 1 ? "place" : "places")
				+ " at " + spare.size() + (spare.size() == 1 ? " table" : " tables")));
		p.add(total(unseatedTotal == 0 ? "" : unseatedTotal + (unseatedTotal == 1 ? " guest" : " guests")
				+ " in " + unseated.size() + (unseated.size() == 1 ? " party" : " parties")
				+ " to seat"));

		addMenuChecks(p, event, 2);
		JOptionPane.showMessageDialog(parent, p, "Check: " + event.getName(), JOptionPane.PLAIN_MESSAGE);
	}

	// for an event with a menu: who has yet to choose, and choices that do not suit
	private static void addMenuChecks(JPanel p, Event event, int span)
	{
		if (!event.hasMenu()) return;
		int notChosen = org.lerot.myreceptionplanner.model.Catering.notChosen(event).size();
		int bad = org.lerot.myreceptionplanner.model.Catering.unsuitable(event).size();
		int unnamed = org.lerot.myreceptionplanner.model.Catering.notNamed(event);
		JLabel menu = new JLabel(notChosen == 0 && bad == 0 ? "Menu: everyone invited has chosen, all suitably."
				: "Menu: " + (notChosen > 0 ? notChosen + " not yet chosen" : "")
				+ (notChosen > 0 && bad > 0 ? "; " : "") + (bad > 0 ? "⚠ " + bad + " with a dish unsuited to their needs" : "")
				+ (unnamed > 0 ? "; " + unnamed + " places not yet named" : "")
				+ "  (Reports ▾ → Catering list)");
		menu.setForeground(notChosen == 0 && bad == 0 ? Theme.FIRST : Theme.HEAD.darker());
		p.add(menu, "span " + span + ", gaptop 8");
	}

	// a hall: reserved seats not yet given, and the seats against those invited
	private static void showHall(Component parent, Occasion occasion, Event event)
	{
		var plan = event.getPlan();
		List<Checks.SpareSeats> spare = Checks.spareSeats(plan, event.getSeating());
		DefaultTableModel model = model("Pew or table", "Not given", "of");
		spare.forEach(s -> model.addRow(new Object[] { s.label(), s.spare(), s.usable() }));
		JPanel p = new JPanel(new MigLayout("insets 0, wrap 1", "[grow,fill]", "[][grow,fill][][]"));
		p.add(heading("Reserved seats not yet given to a guest"));
		p.add(list(model, "Every reserved seat has a guest."), "w 340::, h 200::");
		int reserved = plan.reservedPlaces(), seats = plan.places();
		int given = event.getSeating().seatedCount(), invited = event.invited().size();
		p.add(total(reserved + " reserved seats, " + given + " given;  " + (seats - reserved) + " open seats"));
		JLabel fit = new JLabel(invited + " invited for " + seats + " seats in all"
				+ (event.overCapacity() > 0 ? ": " + event.overCapacity() + " too many" : ""));
		fit.setForeground(event.overCapacity() > 0 ? Theme.HEAD.darker() : Theme.FIRST);
		p.add(fit);
		addMenuChecks(p, event, 1);
		JOptionPane.showMessageDialog(parent, p, "Check: " + event.getName(), JOptionPane.PLAIN_MESSAGE);
	}

	// a party with no seats: who is invited, party by party, against its capacity
	private static void showUnreserved(Component parent, Occasion occasion, Event event)
	{
		var guests = occasion.getGuests();
		DefaultTableModel model = model("Party", "Invited", "of");
		for (var p : guests.parties())
		{
			int n = event.invitedIn(p, guests);
			if (n > 0) model.addRow(new Object[] { p.getName(), n, guests.placesOf(p) });
		}
		long loners = guests.guests().stream().filter(g -> g.getParty() == null && event.isInvited(g)).count();
		if (loners > 0) model.addRow(new Object[] { "No party", (int) loners,
				(int) guests.guests().stream().filter(g -> g.getParty() == null).count() });
		JPanel p = new JPanel(new MigLayout("insets 0, wrap 1", "[grow,fill]", "[][grow,fill][]"));
		p.add(heading("Invited to " + event.getName() + " (party, unreserved)"));
		p.add(list(model, "No one is invited yet."), "w 340::, h 220::");
		int n = event.invited().size();
		String summary = n + (n == 1 ? " guest" : " guests") + " invited";
		if (event.getCapacity() > 0) summary += ", capacity " + event.getCapacity();
		JLabel total = new JLabel(summary);
		if (event.overCapacity() > 0)
		{
			total.setText(summary + ": " + event.overCapacity() + " over capacity");
			total.setForeground(Theme.HEAD.darker());
		} else total.setForeground(event.getCapacity() > 0 ? Theme.FIRST : Theme.EDGE);
		p.add(total);
		addMenuChecks(p, event, 1);
		JOptionPane.showMessageDialog(parent, p, "Check: " + event.getName(), JOptionPane.PLAIN_MESSAGE);
	}

	private static JComponent heading(String text)
	{
		JLabel l = new JLabel("<html><b>" + text + "</b></html>");
		l.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.GRID));
		return l;
	}

	private static JComponent total(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(Theme.EDGE);
		return l;
	}

	private static DefaultTableModel model(String... columns)
	{
		return new DefaultTableModel(columns, 0)
		{
			public boolean isCellEditable(int row, int col) { return false; }

			public Class<?> getColumnClass(int col) { return col == 0 ? String.class : Integer.class; }
		};
	}

	// the table, or a line saying all is well when it is empty
	private static JComponent list(DefaultTableModel model, String allWell)
	{
		if (model.getRowCount() == 0)
		{
			JLabel ok = new JLabel(allWell, SwingConstants.CENTER);
			ok.setForeground(Theme.FIRST);
			return ok;
		}
		JTable t = new JTable(model);
		t.setRowHeight(Math.max(t.getRowHeight(), 22));
		t.setFillsViewportHeight(true);
		t.setAutoCreateRowSorter(true);
		t.getColumnModel().getColumn(1).setMaxWidth(80);
		t.getColumnModel().getColumn(2).setMaxWidth(50);
		DefaultTableCellRenderer grey = new DefaultTableCellRenderer();
		grey.setForeground(Theme.EDGE);
		grey.setHorizontalAlignment(SwingConstants.RIGHT);
		t.getColumnModel().getColumn(2).setCellRenderer(grey);
		JScrollPane scroll = new JScrollPane(t);
		scroll.setPreferredSize(new Dimension(300, Math.min(400, 30 + model.getRowCount() * t.getRowHeight())));
		return scroll;
	}
}
