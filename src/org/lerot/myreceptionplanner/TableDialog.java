package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Altar;
import org.lerot.myreceptionplanner.model.Pew;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.ComposedTable;
import org.lerot.myreceptionplanner.model.OvalTable;
import org.lerot.myreceptionplanner.model.PolygonTable;
import org.lerot.myreceptionplanner.model.RectTable;
import org.lerot.myreceptionplanner.model.RoundTable;
import org.lerot.myreceptionplanner.model.WalkTable;
import org.lerot.myreceptionplanner.model.Table;

import javax.swing.*;
import java.awt.*;

/*
 * Edits a table's own name, places, turn and whether it is the head table.
 * The name is optional: left empty, the table is called "Head table" or by
 * the number that follows where it stands. A table combined with others
 * always has a name, each part of it being a place of its own to the
 * caterers; left empty, it is given one. No two tables share a name.
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
		boolean branch = table.getGroup() != 0 && !table.isHead();
		JLabel nameHint = new JLabel("<html>" + (branch ? "Combined: each part goes by its own name,<br>"
				+ "such as “Window” (left empty, it keeps the one it has)."
				: "Leave empty to use " + (table.isHead() ? "“Head table”" : "the number") + ".")
				+ "<br>Table id " + table.getId() + ", which never changes.</html>");
		nameHint.setForeground(Theme.EDGE);
		form.add(nameHint, "skip, gapbottom 6");

		JSpinner places = null, side = null, end = null;
		if (table instanceof Altar altar)
		{
			places = new JSpinner(new SpinnerNumberModel(altar.getLength(), 1, 30, 1));
			form.add(new JLabel("Length (seats along the front)"));
			form.add(places, "growx");
		} else if (table instanceof Pew pew)
		{
			places = new JSpinner(new SpinnerNumberModel(pew.places(), 1, 40, 1));
			form.add(new JLabel("Seats"));
			form.add(places, "growx");
		} else if (table instanceof RoundTable round)
		{
			places = new JSpinner(new SpinnerNumberModel(round.places(), 1, 30, 1));
			form.add(new JLabel("Places"));
			form.add(places, "growx");
		} else if (table instanceof OvalTable oval)
		{
			side = new JSpinner(new SpinnerNumberModel(oval.getLength(), 1, 30, 1));
			end = new JSpinner(new SpinnerNumberModel(oval.getWidth(), 1, 30, 1));
			form.add(new JLabel("Length (seats)"));
			form.add(side, "growx");
			form.add(new JLabel("Width (seats)"));
			form.add(end, "growx");
			JLabel hint = new JLabel("<html>Now " + oval.places() + " places round it. As wide as it is long, it is round.</html>");
			hint.setForeground(Theme.EDGE);
			form.add(hint, "skip");
		} else if (table instanceof PolygonTable polygon)
		{
			side = new JSpinner(new SpinnerNumberModel(polygon.getSides(), 3, 12, 1));
			end = new JSpinner(new SpinnerNumberModel(polygon.getPerSide(), 1, 10, 1));
			form.add(new JLabel("Sides"));
			form.add(side, "growx");
			form.add(new JLabel("Places on each side"));
			form.add(end, "growx");
		} else if (table instanceof RectTable rect)
		{
			side = new JSpinner(new SpinnerNumberModel(rect.getSidePlaces(), 1, 30, 1));
			end = new JSpinner(new SpinnerNumberModel(rect.getEndPlaces(), 0, 6, 1));
			form.add(new JLabel("Places on each side"));
			form.add(side, "growx");
			form.add(new JLabel("Places at each end"));
			form.add(end, "growx");
		}
		if (table instanceof ComposedTable composed)
		{
			form.add(new JLabel("Made of"));
			form.add(new JLabel(composed.pickListText() + " tables"), "growx");
		}
		if (table instanceof WalkTable walked && !walked.warnings().isEmpty())
		{
			JLabel warnings = new JLabel("<html>" + String.join("<br>", walked.warnings()) + "</html>");
			warnings.setForeground(Theme.HEAD);
			form.add(warnings, "skip");
		}
		JSpinner turn = new JSpinner(new SpinnerNumberModel(
				(int) Math.round(table.getRotation()), 0, 359, (int) PlanPanel.ROTATE_STEP));
		form.add(new JLabel("Turned (degrees clockwise)"));
		form.add(turn, "growx");
		JCheckBox head = new JCheckBox("Head table", table.isHead());
		if (!(table instanceof Pew) && !(table instanceof Altar)) form.add(head, "skip");
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

		while (true)
		{
			int answer = JOptionPane.showConfirmDialog(parent, form, plan.label(table),
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (answer != JOptionPane.OK_OPTION) return false;
			if (!plan.nameTaken(name.getText(), table)) break;
			JOptionPane.showMessageDialog(parent, "Another table is already called “" + name.getText().trim()
					+ "”.\nEach table needs a name of its own.", "Name taken", JOptionPane.WARNING_MESSAGE);
		}

		String typed = name.getText().trim();
		// a part of tables combined left empty keeps the name it had
		if (!typed.equals(table.getName()) && !(branch && typed.isEmpty())) table.setName(typed);
		if (branch && !table.hasName()) plan.giveBranchName(table);
		if (table instanceof Altar altar)
			altar.setLength((Integer) places.getValue());
		else if (table instanceof Pew pew)
			pew.setPlaces((Integer) places.getValue());
		else if (table instanceof RoundTable round)
			round.setPlaces((Integer) places.getValue());
		else if (table instanceof OvalTable oval)
		{
			// the longer way is its length
			int a = (Integer) side.getValue(), b = (Integer) end.getValue();
			oval.setBox(Math.max(a, b), Math.min(a, b));
		} else if (table instanceof PolygonTable polygon)
			polygon.setSides((Integer) side.getValue(), (Integer) end.getValue());
		else if (table instanceof RectTable rect)
			rect.setPlaces((Integer) side.getValue(), (Integer) end.getValue());
		table.setRotation((Integer) turn.getValue());
		if (head.isSelected()) plan.setHead(table);
		else if (table.isHead()) plan.setHead(null);
		return true;
	}
}
