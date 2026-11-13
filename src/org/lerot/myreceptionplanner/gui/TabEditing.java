package org.lerot.myreceptionplanner.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.function.Function;
import java.util.function.IntFunction;

/*
 * Tab (and Shift+Tab) in a table being typed into: the cell typed is kept and
 * the next cell along the same row that can be edited is opened, so a name is
 * typed title, Tab, forenames, Tab, family name. Past the row's last such cell
 * Tab does what a table always does. The row is found again by what it is
 * (keyOf, rowOf, in the model's rows), as keeping an edit may redraw the table.
 */
public final class TabEditing
{
	private TabEditing() {}

	public static void install(JTable table, IntFunction<Object> keyOf, Function<Object, Integer> rowOf)
	{
		// an editor holding the focus would otherwise take Tab to the next control
		table.addContainerListener(new ContainerAdapter()
		{
			public void componentAdded(ContainerEvent e) { keepTab(e.getChild()); }
		});
		wrap(table, "selectNextColumnCell", 1, keyOf, rowOf);
		wrap(table, "selectPreviousColumnCell", -1, keyOf, rowOf);
	}

	private static void keepTab(Component c)
	{
		c.setFocusTraversalKeysEnabled(false);
		if (c instanceof Container k) for (Component inner : k.getComponents()) keepTab(inner);
	}

	private static void wrap(JTable table, String name, int step, IntFunction<Object> keyOf, Function<Object, Integer> rowOf)
	{
		Action tableAction = table.getActionMap().get(name);
		table.getActionMap().put(name, new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				int viewRow = table.getSelectedRow(), viewCol = table.getSelectedColumn();
				if (!table.isEditing() || viewRow < 0 || viewCol < 0)
				{
					tableAction.actionPerformed(e);
					return;
				}
				int modelRow = table.convertRowIndexToModel(viewRow);
				int next = -1;
				for (int c = viewCol + step; c >= 0 && c < table.getColumnCount(); c += step)
					if (table.getModel().isCellEditable(modelRow, table.convertColumnIndexToModel(c)))
					{
						next = table.convertColumnIndexToModel(c);
						break;
					}
				if (next < 0)
				{
					tableAction.actionPerformed(e);
					return;
				}
				Object key = keyOf.apply(modelRow);
				if (!table.getCellEditor().stopCellEditing()) return;
				int col = next;
				// after anything the edit set going has redrawn the table
				SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() ->
				{
					Integer m = rowOf.apply(key);
					if (m == null || m < 0) return;
					int r = table.convertRowIndexToView(m), c = table.convertColumnIndexToView(col);
					if (r < 0 || c < 0) return;
					table.changeSelection(r, c, false, false);
					if (table.editCellAt(r, c) && table.getEditorComponent() != null)
						table.getEditorComponent().requestFocusInWindow();
				}));
			}
		});
	}
}
