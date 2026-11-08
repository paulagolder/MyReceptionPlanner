package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.OccasionXml;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.RectTable;
import org.lerot.myreceptionplanner.model.RoundTable;
import org.lerot.myreceptionplanner.model.Seating;
import org.lerot.myreceptionplanner.model.Table;
import org.lerot.myreceptionplanner.reports.TablePlanPdf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/*
 * The main window: a toolbar over the floor plan of one event of an occasion.
 * An occasion (a wedding, say) is a folder holding occasion.xml, with its
 * name, date and guest list, and a file for each of its events (the Wedding
 * Breakfast, the Evening Reception), each with its own tables and seating.
 * The event shown is chosen on the toolbar. The plan works in two modes:
 * Layout, to set out the tables, and Seating, where a side panel lists the
 * seats of the table clicked and the guest list. The last occasion used is
 * opened at startup.
 */
public class MainWindow extends JFrame implements PlanPanel.Listener, SeatingPanel.Listener
{
	private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);
	private static final String LAST_OCCASION = "lastOccasion";

	private Occasion occasion;
	private Event event;          // the one shown
	private Path folder;          // null until first saved
	private final Set<String> droppedFiles = new HashSet<>();  // of events deleted, to remove on saving
	private boolean unsaved;
	private PlanPanel.Mode mode = PlanPanel.Mode.LAYOUT;

	private final JPanel planHolder = new JPanel(new BorderLayout());
	private PlanPanel planPanel;
	private SeatingPanel seatingPanel;
	private final JToggleButton layoutButton = new JToggleButton("Layout");
	private final JToggleButton seatingButton = new JToggleButton("Seating");
	private JButton addTableButton;
	private JButton tableButton;
	private final JComboBox<Event> eventChooser = new JComboBox<>();
	private boolean fillingChooser;
	private final JMenuItem deleteEventItem = new JMenuItem("Delete this event…");
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
		showOccasion(blankOccasion(), null);
		openLastOccasion();
		setSize(1150, 760);
		setLocationRelativeTo(null);
	}

	// what there is before any occasion is opened or made
	private static Occasion blankOccasion()
	{
		Occasion o = new Occasion("New occasion", new GuestList());
		o.addEvent(new Event("Reception", o.fileFor("Reception"), new Plan(), new Seating()));
		return o;
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

		JPopupMenu reports = new JPopupMenu();
		JMenuItem check = reports.add("Check…");
		check.setToolTipText("Spare places at the tables, and guests not yet seated");
		check.addActionListener(e -> ChecksDialog.show(this, occasion, event));
		reports.addSeparator();
		reports.add("Table plan (PDF)…").addActionListener(e -> tablePlanPdf());
		bar.add(dropDown("Reports", reports));
		bar.addSeparator();
		bar.add(button("Zoom in", () -> planPanel.zoom(1.2)));
		bar.add(button("Zoom out", () -> planPanel.zoom(1 / 1.2)));

		bar.add(Box.createHorizontalGlue());
		bar.add(new JLabel("Event "));
		eventChooser.setToolTipText("The event whose tables and seating are shown");
		eventChooser.setMaximumSize(new Dimension(220, 40));
		eventChooser.addActionListener(e ->
		{
			if (!fillingChooser && eventChooser.getSelectedItem() instanceof Event chosen && chosen != event)
				showEvent(chosen);
		});
		bar.add(eventChooser);
		JPopupMenu occasionMenu = new JPopupMenu();
		occasionMenu.add("Occasion details…").addActionListener(e -> editOccasion());
		occasionMenu.addSeparator();
		occasionMenu.add("Event details…").addActionListener(e -> editEvent());
		occasionMenu.add("New event…").addActionListener(e -> newEvent());
		deleteEventItem.addActionListener(e -> deleteEvent());
		occasionMenu.add(deleteEventItem);
		occasionMenu.addSeparator();
		occasionMenu.add("New occasion…").addActionListener(e -> newOccasion());
		occasionMenu.add("Open occasion…").addActionListener(e -> openOccasion());
		bar.add(dropDown("Occasion", occasionMenu));
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

	// ---- the occasion and its events ----

	private void showOccasion(Occasion o, Path from)
	{
		occasion = o;
		folder = from;
		droppedFiles.clear();
		event = o.events().get(0);
		fillEventChooser();
		showEvent(event);
		unsaved = false;
		updateTitle();
	}

	private void fillEventChooser()
	{
		fillingChooser = true;
		eventChooser.removeAllItems();
		for (Event e : occasion.events())
			eventChooser.addItem(e);
		eventChooser.setSelectedItem(event);
		fillingChooser = false;
		deleteEventItem.setEnabled(occasion.events().size() > 1);
	}

	private void showEvent(Event e)
	{
		event = e;
		fillingChooser = true;
		eventChooser.setSelectedItem(e);
		fillingChooser = false;
		planPanel = new PlanPanel(e.getPlan(), e.getSeating(), this);
		seatingPanel = new SeatingPanel(occasion, e, this);
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

	private void editOccasion()
	{
		if (OccasionDialogs.editOccasion(this, occasion)) changed();
	}

	private void editEvent()
	{
		if (!OccasionDialogs.editEvent(this, occasion, event)) return;
		fillEventChooser();
		changed();
	}

	private void newEvent()
	{
		Event e = OccasionDialogs.newEvent(this, occasion, event);
		if (e == null) return;
		occasion.addEvent(e);
		droppedFiles.remove(e.getFile());
		event = e;
		fillEventChooser();
		showEvent(e);
		changed();
	}

	private void deleteEvent()
	{
		if (occasion.events().size() < 2) return;
		int answer = JOptionPane.showConfirmDialog(this, "Delete the event " + event.getName()
						+ ", with its tables and seating?\nThe guest list stays with the occasion.",
				"Delete event", JOptionPane.OK_CANCEL_OPTION);
		if (answer != JOptionPane.OK_OPTION) return;
		occasion.removeEvent(event);
		droppedFiles.add(event.getFile());
		event = occasion.events().get(0);
		fillEventChooser();
		showEvent(event);
		changed();
	}

	private void newOccasion()
	{
		if (!okToDiscard()) return;
		Occasion o = OccasionDialogs.newOccasion(this);
		if (o == null) return;
		showOccasion(o, null);
		changed();
	}

	private void changed()
	{
		unsaved = true;
		updateTitle();
	}

	// ---- the tables ----

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

	private Plan plan() { return event.getPlan(); }

	private void addTable(boolean round)
	{
		Plan plan = plan();
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
		plan().renumber();
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
		changed();
	}

	@Override
	public void editRequested(Table table)
	{
		if (table == null) return;
		Plan plan = plan();
		Seating seating = event.getSeating();
		String before = table.arrangement();
		if (!TableDialog.edit(this, plan, table)) return;
		plan.numberUnnumbered();
		// when the chairs are set out afresh no one keeps their seat; anyone
		// on a chair that is now disabled (a new head table) loses theirs
		int unseated = before.equals(table.arrangement()) ? 0 : seating.unseatTable(table.getId());
		unseated += seating.tidy(plan, occasion.getGuests());
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
		Seating seating = event.getSeating();
		int seated = seating.seatedAt(table.getId()).size();
		String question = "Remove " + plan().label(table) + "?" + (seated == 0 ? ""
				: "\nIts " + seated + (seated == 1 ? " guest goes" : " guests go") + " back to the guest list.");
		int answer = JOptionPane.showConfirmDialog(this, question, "Delete table", JOptionPane.OK_CANCEL_OPTION);
		if (answer != JOptionPane.OK_OPTION) return;
		seating.unseatTable(table.getId());
		plan().remove(table);
		planChanged();
		planPanel.planUpdated();
	}

	// ---- SeatingPanel.Listener ----

	@Override
	public void guestsChanged()
	{
		changed();
		planPanel.repaint();
	}

	@Override
	public void seatSelected(Table table, int chair)
	{
		planPanel.showChair(table, chair);
	}

	// ---- reports ----

	private static final String PAPER = "paper", LANDSCAPE = "landscape";

	// asks for the paper, then where to save, then writes and opens it
	private void tablePlanPdf()
	{
		if (plan().tables().isEmpty())
		{
			JOptionPane.showMessageDialog(this, "There are no tables to print.", "Table plan",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JComboBox<String> paper = new JComboBox<>(new String[] { "A4", "A3", "A2", "A1" });
		paper.setSelectedItem(PREFS.get(PAPER, "A3"));
		JComboBox<String> turn = new JComboBox<>(new String[] { "Landscape", "Portrait" });
		turn.setSelectedIndex(PREFS.getBoolean(LANDSCAPE, true) ? 0 : 1);
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 2", "[right][grow,fill]"));
		form.add(new JLabel("Heading"), "top");
		form.add(new JLabel("<html><b>" + escape(occasion.heading()) + "</b><br>"
				+ escape(occasion.headingOf(event)) + "</html>"));
		form.add(new JLabel("Paper"));
		form.add(paper);
		form.add(new JLabel("Orientation"));
		form.add(turn);
		JLabel note = new JLabel("<html>Each table where it stands, with its guests in seat order.<br>"
				+ "Renumber the tables first if they have moved. The heading comes from<br>"
				+ "Occasion ▾ → Occasion details and Event details.</html>");
		note.setForeground(Theme.EDGE);
		form.add(note, "skip, gaptop 6");
		if (JOptionPane.showConfirmDialog(this, form, "Table plan: " + event.getName(), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		PREFS.put(PAPER, (String) paper.getSelectedItem());
		PREFS.putBoolean(LANDSCAPE, turn.getSelectedIndex() == 0);

		Path file = choosePdf("table-plan-" + event.getFile().replaceFirst("\\.xml$", "") + ".pdf");
		if (file == null) return;
		com.itextpdf.text.Rectangle page = com.itextpdf.text.PageSize.getRectangle((String) paper.getSelectedItem());
		if (turn.getSelectedIndex() == 0) page = page.rotate();
		try
		{
			boolean fits = TablePlanPdf.write(occasion, event, page, file);
			if (!fits)
				JOptionPane.showMessageDialog(this, "The plan is crowded for " + paper.getSelectedItem()
						+ " even with the smallest type; try a larger paper size.", "Table plan",
						JOptionPane.WARNING_MESSAGE);
			open(file);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot make the PDF", JOptionPane.ERROR_MESSAGE);
		}
	}

	// where to save a report: in the occasion's folder unless chosen otherwise
	private Path choosePdf(String name)
	{
		File in = folder != null ? folder.toFile() : defaultFolder();
		JFileChooser fc = new JFileChooser(in);
		fc.setDialogTitle("Save the PDF");
		fc.setSelectedFile(new File(in, name));
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF files", "pdf"));
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
		File f = fc.getSelectedFile();
		if (!f.getName().toLowerCase().endsWith(".pdf")) f = new File(f.getParentFile(), f.getName() + ".pdf");
		if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " is already there. Replace it?",
				"Save the PDF", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
		return f.toPath();
	}

	// in the desktop's PDF viewer, if there is one
	private void open(Path file)
	{
		try
		{
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
			{
				Desktop.getDesktop().open(file.toFile());
				return;
			}
		} catch (IOException | UnsupportedOperationException ignored)
		{
		}
		JOptionPane.showMessageDialog(this, "Saved " + file, "Table plan", JOptionPane.INFORMATION_MESSAGE);
	}

	// ---- files ----

	private void openLastOccasion()
	{
		String last = PREFS.get(LAST_OCCASION, null);
		if (last == null) return;
		Path f = Path.of(last);
		if (OccasionXml.isOccasion(f)) load(f);
	}

	private void openOccasion()
	{
		if (!okToDiscard()) return;
		Path f = chooseFolder("Open an occasion");
		if (f == null) return;
		if (!OccasionXml.isOccasion(f))
		{
			JOptionPane.showMessageDialog(this, f + " has no " + OccasionXml.FILE
					+ ".\nUse Occasion ▾ → New occasion… to start one.", "Open occasion", JOptionPane.WARNING_MESSAGE);
			return;
		}
		load(f);
	}

	private void load(Path f)
	{
		try
		{
			Occasion o = OccasionXml.load(f);
			if (o.events().isEmpty())
				o.addEvent(new Event("Reception", o.fileFor("Reception"), new Plan(), new Seating()));
			showOccasion(o, f);
			remember(f);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot open the occasion", JOptionPane.ERROR_MESSAGE);
		}
	}

	// Save on the toolbar: shows where the occasion will go, with a chance to
	// choose another folder, before writing it
	private boolean save()
	{
		Path to = folder != null ? folder : chooseFolder("Choose a folder for " + occasion.getName());
		while (to != null)
		{
			int answer = confirmSave(to);
			if (answer == 0) return write(to);
			if (answer != 1) return false;
			Path other = chooseFolder("Choose a folder for " + occasion.getName());
			if (other != null) to = other;
		}
		return false;
	}

	// 0 to save, 1 to choose another folder, anything else to cancel
	private int confirmSave(Path to)
	{
		String place = to.toAbsolutePath().toString();
		JPanel message = new JPanel(new MigLayout("insets 0, wrap 1"));
		int n = occasion.events().size();
		message.add(new JLabel("Save " + occasion.getName() + " (" + OccasionXml.FILE + " and "
				+ n + (n == 1 ? " event file" : " event files") + ") in the folder:"));
		JTextField where = new JTextField(place);
		where.setEditable(false);
		where.setColumns(Math.min(60, Math.max(30, place.length())));
		message.add(where, "growx");
		if (!to.equals(folder) && OccasionXml.isOccasion(to))
		{
			JLabel warn = new JLabel("This folder already holds an occasion; saving replaces it.");
			warn.setForeground(Theme.HEAD);
			message.add(warn, "gaptop 4");
		}
		String[] options = { "Save", "Choose another folder…", "Cancel" };
		return JOptionPane.showOptionDialog(this, message, "Save",
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
	}

	private boolean write(Path to)
	{
		try
		{
			OccasionXml.save(occasion, to);
			if (to.equals(folder))
			{
				for (String f : droppedFiles)
					Files.deleteIfExists(to.resolve(f));
			}
			droppedFiles.clear();
			folder = to;
			unsaved = false;
			remember(folder);
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
		File start = folder != null ? folder.toFile() : defaultFolder();
		start.mkdirs();
		JFileChooser fc = new JFileChooser(start);
		fc.setDialogTitle(title);
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setApproveButtonText("Use this folder");
		if (folder == null)
			fc.setSelectedFile(new File(start, occasion.getName().replaceAll("[^A-Za-z0-9]+", "")));
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
		return fc.getSelectedFile().toPath();  // made when the occasion is saved there
	}

	private static File defaultFolder()
	{
		return new File(System.getProperty("user.home"), "Documents/MyReceptions");
	}

	private static void remember(Path f)
	{
		PREFS.put(LAST_OCCASION, f.toAbsolutePath().toString());
	}

	// true if there is nothing unsaved, or the user saves it or lets it go
	private boolean okToDiscard()
	{
		if (!unsaved) return true;
		String question = folder == null
				? "Save the changes to " + occasion.getName() + "?"
				: "Save the changes to " + occasion.getName() + " in\n" + folder.toAbsolutePath() + "?";
		int answer = JOptionPane.showConfirmDialog(this, question,
				"Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);
		if (answer == JOptionPane.YES_OPTION)
			return folder != null ? write(folder) : save();
		return answer == JOptionPane.NO_OPTION;
	}

	private void updateTitle()
	{
		setTitle((unsaved ? "• " : "") + occasion.getName() + " — " + event.getName() + " — MyReceptionPlanner");
		Plan plan = plan();
		String head = plan.head().map(t -> "head table: " + plan.label(t)).orElse("no head table");
		status.setText(occasion.heading() + "   ·   " + occasion.headingOf(event) + "   ·   "
				+ plan.tables().size() + " tables, " + plan.places() + " places, " + head + ",   "
				+ occasion.getGuests().guests().size() + " guests, " + event.getSeating().seatedCount() + " seated");
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
