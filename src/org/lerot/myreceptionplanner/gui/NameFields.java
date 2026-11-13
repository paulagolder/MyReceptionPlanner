package org.lerot.myreceptionplanner.gui;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Honorifics;
import org.lerot.myreceptionplanner.model.Name;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/*
 * Typing a name in its parts, side by side: the title (any, or one of the
 * honorifics offered), the forenames and the family name, with Tab between
 * them. Also the title chooser on its own, for a table's title column, and a
 * small dialog asking for a name.
 */
public class NameFields extends JPanel
{
	private final JComboBox<String> title;
	private final JTextField forenames = new JTextField(12);
	private final JTextField family = new JTextField(12);

	public NameFields(Honorifics h)
	{
		super(new MigLayout("insets 0, gapx 4", "[90!][grow,fill][grow,fill]"));
		title = titleBox(h);
		forenames.putClientProperty("JTextField.placeholderText", "Forenames");
		family.putClientProperty("JTextField.placeholderText", "Family name");
		title.setToolTipText("Title, if any: choose one or type your own");
		forenames.setToolTipText("Forenames, as they are to be printed");
		family.setToolTipText("Family name, as lists sort by it: \"Van der Berg\" goes under V, \"Berg\" under B");
		add(title);
		add(forenames);
		add(family);
	}

	public Name get()
	{
		Object t = title.isEditable() ? title.getEditor().getItem() : title.getSelectedItem();
		return new Name(t == null ? "" : t.toString(), forenames.getText(), family.getText());
	}

	public void set(Name n)
	{
		title.setSelectedItem(n.title());
		forenames.setText(n.forenames());
		family.setText(n.family());
	}

	// called on every keystroke in any part
	public void onChange(Runnable changed)
	{
		DocumentListener l = new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { changed.run(); }

			public void removeUpdate(DocumentEvent e) { changed.run(); }

			public void changedUpdate(DocumentEvent e) { changed.run(); }
		};
		forenames.getDocument().addDocumentListener(l);
		family.getDocument().addDocumentListener(l);
		((JTextField) title.getEditor().getEditorComponent()).getDocument().addDocumentListener(l);
	}

	public void focusForenames() { forenames.requestFocusInWindow(); }

	// the honorifics to choose from, or any title typed
	public static JComboBox<String> titleBox(Honorifics h)
	{
		JComboBox<String> box = new JComboBox<>();
		box.addItem("");
		for (String t : h.titles()) box.addItem(t);
		box.setEditable(true);
		box.setMaximumRowCount(16);
		return box;
	}

	// for a table's title column
	public static DefaultCellEditor titleEditor(Honorifics h)
	{
		DefaultCellEditor editor = new DefaultCellEditor(titleBox(h));
		editor.setClickCountToStart(2);
		return editor;
	}

	/*
	 * For a name column in a narrow table: a double-click (or typing) asks for
	 * the name in its parts, starting from the row's (nameOf), and the cell's
	 * new value is the Name.
	 */
	public static javax.swing.table.TableCellEditor dialogEditor(Honorifics h, String heading,
			java.util.function.IntFunction<Name> nameOf)
	{
		class Editor extends AbstractCellEditor implements javax.swing.table.TableCellEditor
		{
			private Name value = Name.NONE;

			public Object getCellEditorValue() { return value; }

			public boolean isCellEditable(java.util.EventObject e)
			{
				return !(e instanceof java.awt.event.MouseEvent m) || m.getClickCount() >= 2;
			}

			public Component getTableCellEditorComponent(JTable table, Object shown, boolean selected, int row, int col)
			{
				Name start = nameOf.apply(table.convertRowIndexToModel(row));
				value = start == null ? Name.NONE : start;
				SwingUtilities.invokeLater(() ->
				{
					Name n = ask(table, heading, value, h);
					if (n == null) cancelCellEditing();
					else
					{
						value = n;
						stopCellEditing();
					}
				});
				return new JLabel(value.full());
			}
		}
		return new Editor();
	}

	// a name in its parts, starting from start; null if cancelled
	public static Name ask(Component parent, String heading, Name start, Honorifics h)
	{
		NameFields fields = new NameFields(h);
		fields.set(start);
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.add(fields, BorderLayout.CENTER);
		JLabel note = new JLabel("Title (if any), forenames, family name");
		note.setForeground(Theme.EDGE);
		p.add(note, BorderLayout.SOUTH);
		SwingUtilities.invokeLater(fields::focusForenames);
		return JOptionPane.showConfirmDialog(parent, p, heading, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION ? fields.get() : null;
	}
}
