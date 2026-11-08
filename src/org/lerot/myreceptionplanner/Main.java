package org.lerot.myreceptionplanner;

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
