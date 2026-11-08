package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Pew;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.RectTable;
import org.lerot.myreceptionplanner.model.RoundTable;
import org.lerot.myreceptionplanner.model.Table;

import javax.swing.*;
import java.awt.*;

/*
 * Edits a table's own name, places, turn and whether it is the head table.
 * The name is optional: left empty, the table is called "Head table" or by
 * the number that follows where it stands.
 */
public class TableDialog
{
	// true if OK was pressed and the table changed
	public static boolean edit(Component parent, Plan plan, Table table)
	{
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 2", "[right][180!]"));
		JTextField name = new JTextField(table.getName());
		String fallback = table.isHead() ? "Head table"
				: table.getNumber() > 0 ? "Table " + table.getNumber() : "the next table number";
		name.putClientProperty("JTextField.placeholderText", fallback);
		form.add(new JLabel("Own name"));
		form.add(name, "growx");
		JLabel nameHint = new JLabel("<html>Leave empty to use " + (table.isHead() ? "“Head table”" : "the number")
				+ ".<br>Table id " + table.getId() + ", which never changes.</html>");
		nameHint.setForeground(Theme.EDGE);
		form.add(nameHint, "skip, gapbottom 6");

		JSpinner places = null, side = null, end = null;
		if (table instanceof Pew pew)
		{
			places = new JSpinner(new SpinnerNumberModel(pew.places(), 1, 40, 1));
			form.add(new JLabel("Seats"));
			form.add(places, "growx");
		} else if (table instanceof RoundTable round)
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
		if (!(table instanceof Pew)) form.add(head, "skip");
		boolean seatsAsStarted = table instanceof Pew ? table.reservedPlaces() == 0 : table.getOpenSeats().isEmpty();
		if (table.getFirstSeat() != 0 || !table.getDisabledSeats().isEmpty() || !seatsAsStarted)
		{
			JLabel note = new JLabel("<html>Changing the places puts seat 1 back at the start<br>"
					+ (table.isHead() && table instanceof RectTable
							? "and disables the far side again"
							: "and enables every seat again")
					+ (table instanceof Pew ? ", with every seat open.</html>" : ", every seat reserved.</html>"));
			note.setForeground(Theme.EDGE);
			form.add(note, "span 2, gaptop 6");
		}

		int answer = JOptionPane.showConfirmDialog(parent, form, plan.label(table),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (answer != JOptionPane.OK_OPTION) return false;

		table.setName(name.getText());
		if (table instanceof Pew pew)
			pew.setPlaces((Integer) places.getValue());
		else if (table instanceof RoundTable round)
			round.setPlaces((Integer) places.getValue());
		else if (table instanceof RectTable rect)
			rect.setPlaces((Integer) side.getValue(), (Integer) end.getValue());
		table.setRotation((Integer) turn.getValue());
		if (head.isSelected()) plan.setHead(table);
		else if (table.isHead()) plan.setHead(null);
		return true;
	}
}
