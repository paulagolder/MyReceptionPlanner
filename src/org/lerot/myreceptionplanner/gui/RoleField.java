package org.lerot.myreceptionplanner.gui;

import org.lerot.myreceptionplanner.model.Guest;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/*
 * Typing a guest's role, "Mother of the bride": a text field that takes no
 * more than Guest.ROLE_LENGTH characters.
 */
public class RoleField
{
	public static JTextField field()
	{
		JTextField f = new JTextField();
		f.putClientProperty("JTextField.placeholderText", "Mother of the bride");
		((AbstractDocument) f.getDocument()).setDocumentFilter(new DocumentFilter()
		{
			public void insertString(FilterBypass fb, int offset, String text, AttributeSet a) throws BadLocationException
			{
				replace(fb, offset, 0, text, a);
			}

			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet a) throws BadLocationException
			{
				if (text == null) text = "";
				int room = Guest.ROLE_LENGTH - (fb.getDocument().getLength() - length);
				if (text.length() > room) text = text.substring(0, Math.max(0, room));
				super.replace(fb, offset, length, text, a);
			}
		});
		return f;
	}

	// for a role column in a table
	public static DefaultCellEditor editor()
	{
		return new DefaultCellEditor(field());
	}
}
