package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.GuestXml;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.PlanXml;
import org.lerot.myreceptionplanner.model.RectTable;
import org.lerot.myreceptionplanner.model.RoundTable;
import org.lerot.myreceptionplanner.model.Table;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/*
 * The main window: a toolbar over the floor plan. It works in two modes:
 * Layout, to set out the tables, and Seating, where a side panel lists the
 * seats of the table clicked and the guest list. Each reception is a folder
 * (an "event") holding tables.xml and guests.xml; the last one used is
 * opened at startup.
 */
public class MainWindow extends JFrame implements PlanPanel.Listener, SeatingPanel.Listener
{
	private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);
	private static final String LAST_EVENT = "lastEvent";

	private Plan plan = new Plan();
	private GuestList guests = new GuestList();
	private PlanPanel.Mode mode = PlanPanel.Mode.LAYOUT;
	private Path eventFolder;
	private boolean unsaved;

	private final JPanel planHolder = new JPanel(new BorderLayout());
	private PlanPanel planPanel;
	private SeatingPanel seatingPanel;
	private final JToggleButton layoutButton = new JToggleButton("Layout");
	private final JToggleButton seatingButton = new JToggleButton("Seating");
	private JButton addTableButton;
	private JButton tableButton;
	private final JLabel status = new JLabel();
	private final JMenuItem editItem = new JMenuItem("Edit…");
	private final JMenuItem turnLeftItem = new JMenuItem("Turn left 15°");
	private final JMenuItem turnRightItem = new JMenuItem("Turn right 15°");
	private final JMenuItem enableSeatsItem = new JMenuItem("Enable all seats");
	private final JMenuItem deleteItem = new JMenuItem("Delete");
	private final JMenuItem renumberItem = new JMenuItem("Renumber tables…");

	public MainWindow()
	{
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				if (okToDiscard()) System.exit(0);
			}
		});
		add(toolbar(), BorderLayout.NORTH);
		add(planHolder, BorderLayout.CENTER);
		status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		add(status, BorderLayout.SOUTH);
		showPlan(new Plan(), new GuestList(), null);
		openLastEvent();
		setSize(1100, 750);
		setLocationRelativeTo(null);
	}

	private JToolBar toolbar()
	{
		JToolBar bar = new JToolBar();
		bar.setFloatable(false);
		ButtonGroup modes = new ButtonGroup();
		modes.add(layoutButton);
		modes.add(seatingButton);
		layoutButton.setSelected(true);
		layoutButton.setToolTipText("Set out the tables");
		seatingButton.setToolTipText("Seat the guests");
		layoutButton.addActionListener(e -> setMode(PlanPanel.Mode.LAYOUT));
		seatingButton.addActionListener(e -> setMode(PlanPanel.Mode.SEATING));
		bar.add(layoutButton);
		bar.add(seatingButton);
		bar.addSeparator();

		JPopupMenu add = new JPopupMenu();
		add.add("Round table").addActionListener(e -> addTable(true));
		add.add("Rectangular table").addActionListener(e -> addTable(false));
		addTableButton = dropDown("Add table", add);
		bar.add(addTableButton);

		editItem.addActionListener(e -> editRequested(planPanel.getSelected()));
		turnLeftItem.addActionListener(e -> planPanel.turn(-PlanPanel.ROTATE_STEP));
		turnRightItem.addActionListener(e -> planPanel.turn(PlanPanel.ROTATE_STEP));
		enableSeatsItem.addActionListener(e -> planPanel.enableAllSeats());
		deleteItem.addActionListener(e -> deleteRequested(planPanel.getSelected()));
		turnLeftItem.setAccelerator(KeyStroke.getKeyStroke("shift R"));
		turnRightItem.setAccelerator(KeyStroke.getKeyStroke("R"));
		editItem.setAccelerator(KeyStroke.getKeyStroke("ENTER"));
		deleteItem.setAccelerator(KeyStroke.getKeyStroke("DELETE"));
		JPopupMenu edit = new JPopupMenu();
		edit.add(editItem);
		edit.add(turnLeftItem);
		edit.add(turnRightItem);
		edit.addSeparator();
		edit.add(enableSeatsItem);
		edit.addSeparator();
		edit.add(deleteItem);
		edit.addSeparator();
		renumberItem.addActionListener(e -> renumber());
		edit.add(renumberItem);
		tableButton = dropDown("Table", edit);
		bar.add(tableButton);
		bar.addSeparator();
		JButton check = button("Check…", () -> ChecksDialog.show(this, plan, guests));
		check.setToolTipText("Spare places at the tables, and guests not yet seated");
		bar.add(check);
		bar.addSeparator();
		bar.add(button("Zoom in", () -> planPanel.zoom(1.2)));
		bar.add(button("Zoom out", () -> planPanel.zoom(1 / 1.2)));
		bar.add(Box.createHorizontalGlue());
		bar.add(button("Open event…", this::openEvent));
		bar.add(button("Save…", this::save));
		return bar;
	}

	// a toolbar button that opens its menu below it
	private static JButton dropDown(String text, JPopupMenu menu)
	{
		JButton b = new JButton(text + " ▾");
		b.addActionListener(e -> menu.show(b, 0, b.getHeight()));
		return b;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.addActionListener(e -> action.run());
		return b;
	}

	private void showPlan(Plan newPlan, GuestList newGuests, Path folder)
	{
		plan = newPlan;
		guests = newGuests;
		eventFolder = folder;
		unsaved = false;
		planPanel = new PlanPanel(plan, guests, this);
		seatingPanel = new SeatingPanel(plan, guests, this);
		planHolder.removeAll();
		JScrollPane scroll = new JScrollPane(planPanel);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getHorizontalScrollBar().setUnitIncrement(16);
		planHolder.add(scroll, BorderLayout.CENTER);
		planHolder.add(seatingPanel, BorderLayout.EAST);
		setMode(mode);
		selectionChanged(null);
		updateTitle();
	}

	private void setMode(PlanPanel.Mode newMode)
	{
		mode = newMode;
		boolean seating = mode == PlanPanel.Mode.SEATING;
		(seating ? seatingButton : layoutButton).setSelected(true);
		addTableButton.setVisible(!seating);
		tableButton.setVisible(!seating);
		planPanel.setMode(mode);
		seatingPanel.setVisible(seating);
		if (seating)
		{
			seatingPanel.refresh();
			seatingPanel.showTable(planPanel.getSelected(), -1);
		}
		planHolder.revalidate();
		planHolder.repaint();
	}

	private void addTable(boolean round)
	{
		int id = plan.nextId();
		Table t = round
				? new RoundTable(id, "", 8)
				: new RectTable(id, "", 4, 0);
		// in view, a little apart from the last one added
		Point2D c = planPanel.visibleCentre();
		double step = 40 * (plan.tables().size() % 5);
		t.moveTo(Math.round(c.getX() + step), Math.round(c.getY() + step));
		plan.add(t);
		plan.numberUnnumbered();
		planPanel.select(t);
		planChanged();
		planPanel.planUpdated();
		planPanel.requestFocusInWindow();
	}

	// numbers the tables afresh by where they stand, after asking
	private void renumber()
	{
		int answer = JOptionPane.showConfirmDialog(this,
				"Number the tables afresh by where they stand: in rows from the top of the plan,\n"
						+ "left to right along each row. The head table and tables with their own name\n"
						+ "keep their names. Guests keep their seats.",
				"Renumber tables", JOptionPane.OK_CANCEL_OPTION);
		if (answer != JOptionPane.OK_OPTION) return;
		plan.renumber();
		planChanged();
		planPanel.planUpdated();
	}

	// ---- PlanPanel.Listener ----

	@Override
	public void selectionChanged(Table selected)
	{
		editItem.setEnabled(selected != null);
		turnLeftItem.setEnabled(selected != null);
		turnRightItem.setEnabled(selected != null);
		deleteItem.setEnabled(selected != null);
		enableSeatsItem.setEnabled(selected != null && !selected.getDisabledSeats().isEmpty());
	}

	@Override
	public void chairChosen(Table table, int chair)
	{
		seatingPanel.showTable(table, chair);
	}

	@Override
	public void planChanged()
	{
		selectionChanged(planPanel.getSelected());  // its seats may have changed
		unsaved = true;
		updateTitle();
	}

	@Override
	public void editRequested(Table table)
	{
		if (table == null) return;
		String before = table.arrangement();
		if (!TableDialog.edit(this, plan, table)) return;
		plan.numberUnnumbered();
		// when the chairs are set out afresh no one keeps their seat; anyone
		// on a chair that is now disabled (a new head table) loses theirs
		int unseated = before.equals(table.arrangement()) ? 0 : guests.unseatTable(table.getId());
		unseated += guests.tidy(plan);
		planChanged();
		planPanel.planUpdated();
		if (unseated > 0)
			JOptionPane.showMessageDialog(this, unseated + (unseated == 1 ? " guest has" : " guests have")
					+ " lost their seat at " + plan.label(table) + " and gone back to the guest list.",
					"Seats changed", JOptionPane.INFORMATION_MESSAGE);
	}

	@Override
	public void deleteRequested(Table table)
	{
		if (table == null) return;
		int seated = guests.seatedAt(table.getId()).size();
		String question = "Remove " + plan.label(table) + "?" + (seated == 0 ? ""
				: "\nIts " + seated + (seated == 1 ? " guest goes" : " guests go") + " back to the guest list.");
		int answer = JOptionPane.showConfirmDialog(this, question, "Delete table", JOptionPane.OK_CANCEL_OPTION);
		if (answer != JOptionPane.OK_OPTION) return;
		guests.unseatTable(table.getId());
		plan.remove(table);
		planChanged();
		planPanel.planUpdated();
	}

	// ---- SeatingPanel.Listener ----

	@Override
	public void guestsChanged()
	{
		unsaved = true;
		updateTitle();
		planPanel.repaint();
	}

	@Override
	public void seatSelected(Table table, int chair)
	{
		planPanel.showChair(table, chair);
	}

	// ---- files ----

	private void openLastEvent()
	{
		String last = PREFS.get(LAST_EVENT, null);
		if (last == null) return;
		Path folder = Path.of(last);
		if (Files.isRegularFile(folder.resolve(PlanXml.FILE))) load(folder);
	}

	// choose a folder; if it has no tables.xml yet, the plan starts empty there
	private void openEvent()
	{
		if (!okToDiscard()) return;
		Path folder = chooseFolder("Open or start an event");
		if (folder == null) return;
		if (Files.isRegularFile(folder.resolve(PlanXml.FILE))) load(folder);
		else
		{
			showPlan(new Plan(), new GuestList(), folder);
			remember(folder);
		}
	}

	private void load(Path folder)
	{
		try
		{
			Plan loaded = PlanXml.load(folder.resolve(PlanXml.FILE));
			Path guestFile = folder.resolve(GuestXml.FILE);
			GuestList loadedGuests = Files.isRegularFile(guestFile) ? GuestXml.load(guestFile) : new GuestList();
			int unseated = loadedGuests.tidy(loaded);
			showPlan(loaded, loadedGuests, folder);
			remember(folder);
			if (unseated > 0)
			{
				planChanged();
				JOptionPane.showMessageDialog(this, unseated + " guests were seated at chairs that are no "
						+ "longer there, and have gone back to the guest list.", "Seats changed",
						JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot open event", JOptionPane.ERROR_MESSAGE);
		}
	}

	// Save on the toolbar: shows where the plan will go, with a chance to
	// choose another folder, before writing it
	private boolean save()
	{
		Path folder = eventFolder != null ? eventFolder : chooseFolder("Choose a folder for this event");
		while (folder != null)
		{
			int answer = confirmSave(folder);
			if (answer == 0) return writePlan(folder);
			if (answer != 1) return false;
			Path other = chooseFolder("Choose a folder for this event");
			if (other != null) folder = other;
		}
		return false;
	}

	// 0 to save, 1 to choose another folder, anything else to cancel
	private int confirmSave(Path folder)
	{
		Path file = folder.toAbsolutePath().resolve(PlanXml.FILE);
		String place = folder.toAbsolutePath().toString();
		JPanel message = new JPanel(new MigLayout("insets 0, wrap 1"));
		message.add(new JLabel("Save the table plan (" + PlanXml.FILE + ") and the guest list ("
				+ GuestXml.FILE + ") in the folder:"));
		JTextField where = new JTextField(place);
		where.setEditable(false);
		where.setColumns(Math.min(60, Math.max(30, place.length())));
		message.add(where, "growx");
		if (!folder.equals(eventFolder) && Files.isRegularFile(file))
		{
			JLabel warn = new JLabel("This folder already has a table plan; saving replaces it.");
			warn.setForeground(Theme.HEAD);
			message.add(warn, "gaptop 4");
		}
		String[] options = { "Save", "Choose another folder…", "Cancel" };
		return JOptionPane.showOptionDialog(this, message, "Save",
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
	}

	private boolean writePlan(Path folder)
	{
		try
		{
			PlanXml.save(plan, folder.resolve(PlanXml.FILE));
			GuestXml.save(guests, folder.resolve(GuestXml.FILE));
			eventFolder = folder;
			unsaved = false;
			remember(eventFolder);
			updateTitle();
			return true;
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot save", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private Path chooseFolder(String title)
	{
		File start = eventFolder != null ? eventFolder.toFile() : defaultFolder();
		start.mkdirs();
		JFileChooser fc = new JFileChooser(start);
		fc.setDialogTitle(title);
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setApproveButtonText("Use this folder");
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
		return fc.getSelectedFile().toPath();  // made when the plan is saved there
	}

	private static File defaultFolder()
	{
		return new File(System.getProperty("user.home"), "Documents/MyReceptions");
	}

	private static void remember(Path folder)
	{
		PREFS.put(LAST_EVENT, folder.toAbsolutePath().toString());
	}

	// true if there is nothing unsaved, or the user saves it or lets it go
	private boolean okToDiscard()
	{
		if (!unsaved) return true;
		String question = eventFolder == null
				? "Save the changes to this plan?"
				: "Save the changes to this plan in\n" + eventFolder.toAbsolutePath() + "?";
		int answer = JOptionPane.showConfirmDialog(this, question,
				"Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);
		if (answer == JOptionPane.YES_OPTION)
			return eventFolder != null ? writePlan(eventFolder) : save();
		return answer == JOptionPane.NO_OPTION;
	}

	private void updateTitle()
	{
		String event = eventFolder == null ? "New event" : eventFolder.getFileName().toString();
		setTitle((unsaved ? "• " : "") + event + " — MyReceptionPlanner");
		String head = plan.head().map(t -> "head table: " + plan.label(t)).orElse("no head table");
		status.setText(plan.tables().size() + " tables, " + plan.places() + " places, " + head
				+ ",   " + guests.guests().size() + " guests, " + guests.seatedCount() + " seated"
				+ (eventFolder == null ? "" : "   ·   " + eventFolder));
	}
}
