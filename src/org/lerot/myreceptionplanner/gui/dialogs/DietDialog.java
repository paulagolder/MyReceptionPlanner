package org.lerot.myreceptionplanner.gui.dialogs;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.gui.Theme;
import org.lerot.myreceptionplanner.model.Diet;
import org.lerot.myreceptionplanner.model.Guest;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/*
 * A guest's dietary needs, ticked from the list, with anything more in words
 * (allergies, dislikes). They are the guest's own, so hold at every event.
 */
public class DietDialog
{
	// as edit, but a place not yet named is told to be named first
	public static boolean editNamed(Component parent, Guest guest)
	{
		if (guest.isPlaceholder())
		{
			JOptionPane.showMessageDialog(parent, guest.displayName() + " is a place not yet named. Give the guest "
					+ "a name first; dietary needs and menu choices belong to a named person.", "Dietary needs",
					JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		return edit(parent, guest);
	}

	// true if changed
	public static boolean edit(Component parent, Guest guest)
	{
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 2", "[][]"));
		Map<Diet, JCheckBox> boxes = new EnumMap<>(Diet.class);
		for (Diet d : Diet.values())
		{
			JCheckBox box = new JCheckBox(d + "  (" + d.code() + ")", guest.diets().contains(d));
			boxes.put(d, box);
			form.add(box);
		}
		JTextField note = new JTextField(guest.getDietNote(), 30);
		note.putClientProperty("JTextField.placeholderText", "allergies, dislikes …");
		form.add(new JLabel("Note"), "newline, gaptop 8");
		form.add(note, "span, growx");
		JLabel hint = new JLabel("These hold at every event; a note for one event only goes in Menu choices.");
		hint.setForeground(Theme.EDGE);
		form.add(hint, "span");
		if (JOptionPane.showConfirmDialog(parent, form, "Dietary needs: " + guest.displayName(),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return false;
		String before = guest.dietText();
		guest.diets().clear();
		boxes.forEach((d, b) -> { if (b.isSelected()) guest.diets().add(d); });
		guest.setDietNote(note.getText());
		return !before.equals(guest.dietText());
	}
}
