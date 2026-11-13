package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.gui.MainWindow;
import org.lerot.myreceptionplanner.gui.Theme;

import javax.swing.SwingUtilities;

// Starts MyReceptionPlanner
public class Main
{
	public static void main(String[] args)
	{
		Theme.install();
		SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
	}
}
