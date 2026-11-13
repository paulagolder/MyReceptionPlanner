package org.lerot.myreceptionplanner.gui.dialogs;

import org.lerot.myreceptionplanner.gui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/*
 * Shows a JOptionPane in a dialog that can be resized, and remembers the
 * size it was left at under a key of its own, so it opens that size next
 * time (never larger than the screen).
 */
public class ResizableDialog
{
	private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);

	/* The option chosen, as JOptionPane gives it: an Integer for the standard
	 * buttons, the option itself for buttons of one's own, or null if closed. */
	public static Object show(Component parent, JOptionPane pane, String title, String key)
	{
		JDialog dialog = pane.createDialog(parent, title);
		dialog.setResizable(true);
		Dimension packed = dialog.getSize();
		Rectangle screen = dialog.getGraphicsConfiguration().getBounds();
		int w = Math.min(Math.max(PREFS.getInt(key + ".width", packed.width), packed.width / 2), screen.width);
		int h = Math.min(Math.max(PREFS.getInt(key + ".height", packed.height), packed.height / 2), screen.height);
		dialog.setSize(w, h);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
		PREFS.putInt(key + ".width", dialog.getWidth());
		PREFS.putInt(key + ".height", dialog.getHeight());
		dialog.dispose();
		return pane.getValue();
	}

	public static boolean ok(Component parent, Object message, String title, String key)
	{
		JOptionPane pane = new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		return Integer.valueOf(JOptionPane.OK_OPTION).equals(show(parent, pane, title, key));
	}
}
