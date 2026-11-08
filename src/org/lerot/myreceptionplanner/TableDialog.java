package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.RectTable;
import org.lerot.myreceptionplanner.model.RoundTable;
import org.lerot.myreceptionplanner.model.Table;

import javax.swing.*;
import java.awt.*;

// Edits a table's name, places, turn and whether it is the head table
public class TableDialog
{
	// true if OK was pressed and the table changed
	public static boolean edit(Component parent, Plan plan, Table table)
	{
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 2", "[right][150!]"));
		JTextField name = new JTextField(table.getName());
		form.add(new JLabel("Name"));
		form.add(name, "growx");

		JSpinner places = null, side = null, end = null;
		if (table instanceof RoundTable round)
		{
			places = new JSpinner(new SpinnerNumberModel(round.places(), 1, 30, 1));
			form.add(new JLabel("Places"));
			form.add(places, "growx");
		} else if (table instanceof RectTable rect)
		{
			side = new JSpinner(new SpinnerNumberModel(rect.getSidePlaces(), 1, 30, 1));
			end = new JSpinner(new SpinnerNumberModel(rect.getEndPlaces(), 0, 6, 1));
			form.add(new JLabel("Places on each side"));
			form.add(side, "growx");
			form.add(new JLabel("Places at each end"));
			form.add(end, "growx");
		}
		JSpinner turn = new JSpinner(new SpinnerNumberModel(
				(int) Math.round(table.getRotation()), 0, 359, (int) PlanPanel.ROTATE_STEP));
		form.add(new JLabel("Turned (degrees clockwise)"));
		form.add(turn, "growx");
		JCheckBox head = new JCheckBox("Head table", table.isHead());
		form.add(head, "skip");
		if (table.getFirstSeat() != 0 || !table.getDisabledSeats().isEmpty())
		{
			JLabel note = new JLabel("<html>Changing the places puts seat 1 back at the start<br>"
					+ (table.isHead() && table instanceof RectTable
							? "and disables the far side again.</html>"
							: "and enables every seat again.</html>"));
			note.setForeground(Theme.EDGE);
			form.add(note, "span 2, gaptop 6");
		}

		int answer = JOptionPane.showConfirmDialog(parent, form, "Table",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (answer != JOptionPane.OK_OPTION) return false;

		String newName = name.getText().trim();
		if (!newName.isEmpty()) table.setName(newName);
		if (table instanceof RoundTable round)
			round.setPlaces((Integer) places.getValue());
		else if (table instanceof RectTable rect)
			rect.setPlaces((Integer) side.getValue(), (Integer) end.getValue());
		table.setRotation((Integer) turn.getValue());
		if (head.isSelected()) plan.setHead(table);
		else if (table.isHead()) plan.setHead(null);
		return true;
	}
}
