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
 * occasion, then what the planner is for) and Help (MyReceptionPlanner 101),
 * after the pattern of the other Lerot apps.
 */
public class InfoDialogs
{
	static final String ABOUT_TEXT = """
			MyReceptionPlanner plans the guests, tables, seating, menus and messages
			for an occasion of a few events: a ceremony, a wedding breakfast, a party.
			Each occasion is a folder of its own; guests' details can be copied from
			a contacts file exported from Google, Apple or Outlook.""";

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
				{ "Developed by", "Paul Golder, with Claude (Anthropic) as AI pair programmer" },
				{ "Java", System.getProperty("java.version") + "  (" + System.getProperty("java.vendor") + ")" },
				{ "Occasion", occasion.getName() },
				{ "Guests", occasion.getGuests().guests().size() + " in " + occasion.getGuests().parties().size()
						+ " parties, " + occasion.events().size() + (occasion.events().size() == 1 ? " event" : " events") },
				{ "Folder", folder == null ? "not saved yet" : folder.toString() } };
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

	// MyReceptionPlanner 101, from help.html in the program, in a window that can be resized
	public static void help(Component parent)
	{
		JEditorPane page = new JEditorPane();
		page.setEditable(false);
		page.setContentType("text/html");
		page.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);  // the app's own font
		try (java.io.InputStream in = InfoDialogs.class.getResourceAsStream("help.html"))
		{
			page.setText(in == null ? "<p>The help page is missing from the program.</p>"
					: new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
		} catch (java.io.IOException e)
		{
			page.setText("<p>Cannot read the help page: " + e.getMessage() + "</p>");
		}
		page.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(page);
		scroll.setPreferredSize(new Dimension(640, 560));
		Object[] close = { "Close" };
		ResizableDialog.show(parent, new JOptionPane(scroll, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
				null, close, close[0]), "MyReceptionPlanner Help", "helpDialog");
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
