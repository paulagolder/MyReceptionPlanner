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
		List<Checks.SpareSeats> spare = Checks.spareSeats(event.getPlan(), event.getSeating());
		List<Checks.Unseated> unseated = Checks.unseated(occasion.getGuests(), event.getSeating());

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
