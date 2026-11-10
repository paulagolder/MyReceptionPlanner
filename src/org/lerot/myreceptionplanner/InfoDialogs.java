package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.model.Occasion;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * The Info menu's dialogs: About (facts about this build and the open
 * occasion, then what the planner is for) and Help (still to be written),
 * after the pattern of the other Lerot apps.
 */
public class InfoDialogs
{
	static final String ABOUT_TEXT = """
			MyReceptionPlanner plans the guests, tables, seating, menus and messages
			for an occasion of a few events: a ceremony, a wedding breakfast, a party.
			Each occasion is a folder of its own; guests' details can be copied from
			a contacts file exported from Google, Apple or Outlook.
			Developed by Paul Golder, with Claude (Anthropic) as AI pair programmer.""";

	public static void about(Component parent, Occasion occasion, Path folder)
	{
		JPanel info = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.gridy = 0;
		c.insets = new Insets(2, 0, 2, 12);
		String version = MainWindow.class.getPackage().getImplementationVersion();
		String[][] facts = {
				{ "Version", version == null ? "development" : version },
				{ "Built", buildDate() },
				{ "Java", System.getProperty("java.version") + "  (" + System.getProperty("java.vendor") + ")" },
				{ "Occasion", occasion.getName() },
				{ "Guests", occasion.getGuests().guests().size() + " in " + occasion.getGuests().parties().size()
						+ " parties, " + occasion.events().size() + (occasion.events().size() == 1 ? " event" : " events") },
				{ "Folder", folder == null ? "not saved yet" : folder.toString() },
				{ "Settings", settings() } };
		for (String[] fact : facts)
		{
			c.gridx = 0;
			JLabel name = new JLabel(fact[0]);
			name.setFont(name.getFont().deriveFont(Font.BOLD));
			info.add(name, c);
			c.gridx = 1;
			info.add(new JLabel(fact[1]), c);
			c.gridy++;
		}
		c.gridx = 0;
		c.gridwidth = 2;
		c.insets = new Insets(12, 0, 0, 0);
		for (String line : ABOUT_TEXT.strip().split("\\R"))
		{
			info.add(new JLabel(line.strip()), c);
			c.gridy++;
			c.insets = new Insets(2, 0, 0, 0);
		}
		JOptionPane.showMessageDialog(parent, info, "About MyReceptionPlanner", JOptionPane.PLAIN_MESSAGE);
	}

	public static void help(Component parent)
	{
		JOptionPane.showMessageDialog(parent, "Help is still to be written.", "MyReceptionPlanner Help",
				JOptionPane.PLAIN_MESSAGE);
	}

	// where Java keeps the planner's preferences: the registry on Windows, a file elsewhere
	private static String settings()
	{
		if (System.getProperty("os.name").startsWith("Windows"))
			return "Registry: HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs\\org\\lerot\\myreceptionplanner";
		return Path.of(System.getProperty("user.home"), ".java", ".userPrefs", "org", "lerot", "myreceptionplanner",
				"prefs.xml").toString();
	}

	// when the jar (or, run from the class folders, this class) was made
	private static String buildDate()
	{
		try
		{
			File f = new File(InfoDialogs.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (!f.isFile()) f = new File(InfoDialogs.class.getResource("InfoDialogs.class").toURI());
			return new SimpleDateFormat("d MMM yyyy, HH:mm").format(new Date(f.lastModified()));
		} catch (Exception e)
		{
			return "unknown";
		}
	}
}
