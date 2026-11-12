package org.lerot.myreceptionplanner;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/*
 * A box for finding guests in a long list: the list narrows as you type
 * (see Guest.matches); Esc clears it.
 */
public class FindField extends JTextField
{
	public FindField(Runnable changed)
	{
		super(14);
		putClientProperty("JTextField.placeholderText", "Find…");
		putClientProperty("JTextField.showClearButton", true);
		setToolTipText("Show only the guests whose name, party or role has these words; Esc to show all");
		getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { changed.run(); }

			public void removeUpdate(DocumentEvent e) { changed.run(); }

			public void changedUpdate(DocumentEvent e) { changed.run(); }
		});
		getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
		getActionMap().put("clear", new AbstractAction()
		{
			public void actionPerformed(java.awt.event.ActionEvent e) { setText(""); }
		});
	}
}
