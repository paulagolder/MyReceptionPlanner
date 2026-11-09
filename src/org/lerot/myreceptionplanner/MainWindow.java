package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Messages;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.OccasionXml;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Progress;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
	// " 1.2.0" from the jar's manifest; "" when run from the build
	private static final String VERSION = MainWindow.class.getPackage().getImplementationVersion() == null ? ""
			: " " + MainWindow.class.getPackage().getImplementationVersion();

	private Occasion occasion;
	private Event event;          // the one shown
	private Path folder;          // null until first saved
	private final Set<String> droppedFiles = new HashSet<>();  // of events deleted, to remove on saving
	private boolean unsaved;
	private PlanPanel.Mode mode = PlanPanel.Mode.LAYOUT;

	private final JPanel planHolder = new JPanel(new BorderLayout());
	private final CardLayout views = new CardLayout();
	private final JPanel viewHolder = new JPanel(views);  // the event's plan, or the guest list
	private GuestsPanel guestsPanel;
	private boolean guestView;
	private final JToggleButton eventViewButton = new JToggleButton("Event");
	private final JToggleButton guestViewButton = new JToggleButton("Guests");
	private final List<JComponent> eventOnly = new ArrayList<>();  // toolbar items hidden in the Guests view
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
	private final JMenuItem duplicateItem = new JMenuItem("Duplicate");
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
		viewHolder.add(planHolder, "event");
		add(viewHolder, BorderLayout.CENTER);
		status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		add(status, BorderLayout.SOUTH);
		showOccasion(blankOccasion(), null);
		openLastOccasion();
		java.net.URL icon = MainWindow.class.getResource("icon.png");
		if (icon != null) setIconImage(new ImageIcon(icon).getImage());
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

		JPopupMenu occasionMenu = new JPopupMenu();
		occasionMenu.add("Progress…").addActionListener(e -> ProgressDialog.show(this, occasion, this::progressStep));
		occasionMenu.addSeparator();
		occasionMenu.add("Occasion details…").addActionListener(e -> editOccasion());
		occasionMenu.add("Invitations…").addActionListener(e -> invitations());
		occasionMenu.addSeparator();
		occasionMenu.add("New occasion…").addActionListener(e -> newOccasion());
		occasionMenu.add("Open occasion…").addActionListener(e -> openOccasion());
		occasionMenu.addSeparator();
		occasionMenu.add("Save…").addActionListener(e -> save());
		bar.add(dropDown("Occasion", occasionMenu));
		bar.addSeparator();

		ButtonGroup viewGroup = new ButtonGroup();
		viewGroup.add(eventViewButton);
		viewGroup.add(guestViewButton);
		eventViewButton.setSelected(true);
		eventViewButton.setToolTipText("An event: its tables, seating and menu");
		guestViewButton.setToolTipText("The guest list of the whole occasion: parties, guests and invitations");
		eventViewButton.addActionListener(e -> setGuestView(false));
		guestViewButton.addActionListener(e -> setGuestView(true));
		bar.add(eventViewButton);
		bar.add(guestViewButton);
		eventChooser.setToolTipText("The event whose tables and seating are shown");
		eventChooser.setMaximumSize(new Dimension(220, 40));
		eventChooser.addActionListener(e ->
		{
			if (!fillingChooser && eventChooser.getSelectedItem() instanceof Event chosen && chosen != event)
				showEvent(chosen);
		});
		bar.add(Box.createHorizontalStrut(4));
		bar.add(eventOnly(eventChooser));

		JPopupMenu eventsMenu = new JPopupMenu();
		eventsMenu.add("Event details…").addActionListener(e -> editEvent());
		eventsMenu.add("Menu…").addActionListener(e ->
		{
			if (MenuDialog.edit(this, occasion, event)) changed();
		});
		eventsMenu.add("Menu choices and dietary needs…").addActionListener(e ->
		{
			if (MenuChoicesDialog.show(this, event))
			{
				seatingPanel.refresh();
				changed();
			}
		});
		eventsMenu.addSeparator();
		eventsMenu.add("New event…").addActionListener(e -> newEvent());
		deleteEventItem.addActionListener(e -> deleteEvent());
		eventsMenu.add(deleteEventItem);
		bar.add(eventOnly(dropDown("Events", eventsMenu)));
		bar.add(eventOnly(separator()));
		ButtonGroup modes = new ButtonGroup();
		modes.add(layoutButton);
		modes.add(seatingButton);
		layoutButton.setSelected(true);
		layoutButton.setToolTipText("Set out the tables");
		seatingButton.setToolTipText("Seat the guests");
		layoutButton.addActionListener(e -> setMode(PlanPanel.Mode.LAYOUT));
		seatingButton.addActionListener(e -> setMode(PlanPanel.Mode.SEATING));
		bar.add(eventOnly(layoutButton));
		bar.add(eventOnly(seatingButton));
		bar.add(eventOnly(separator()));

		JPopupMenu add = new JPopupMenu();
		add.add("Round table").addActionListener(e -> addTable(true));
		add.add("Rectangular table").addActionListener(e -> addTable(false));
		add.add("Pew (a row of seats)").addActionListener(e -> addPew());
		add.addSeparator();
		add.add("Altar / speakers' table").addActionListener(e -> addAltar());
		addTableButton = dropDown("Add table", add);
		bar.add(addTableButton);

		editItem.addActionListener(e -> editRequested(planPanel.getSelected()));
		turnLeftItem.addActionListener(e -> planPanel.turn(-PlanPanel.ROTATE_STEP));
		turnRightItem.addActionListener(e -> planPanel.turn(PlanPanel.ROTATE_STEP));
		enableSeatsItem.addActionListener(e -> planPanel.enableAllSeats());
		deleteItem.addActionListener(e -> deleteRequested(planPanel.getSelected()));
		duplicateItem.addActionListener(e -> duplicateRequested(planPanel.getSelected()));
		duplicateItem.setAccelerator(KeyStroke.getKeyStroke("ctrl D"));
		turnLeftItem.setAccelerator(KeyStroke.getKeyStroke("shift R"));
		turnRightItem.setAccelerator(KeyStroke.getKeyStroke("R"));
		editItem.setAccelerator(KeyStroke.getKeyStroke("ENTER"));
		deleteItem.setAccelerator(KeyStroke.getKeyStroke("DELETE"));
		JPopupMenu edit = new JPopupMenu();
		edit.add(editItem);
		edit.add(duplicateItem);
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
		JMenuItem alike = reports.add("Names alike…");
		alike.setToolTipText("Guests whose names suggest they are on the list twice");
		alike.addActionListener(e ->
		{
			if (NamesAlikeDialog.show(this, occasion.getGuests())) changed();
		});
		reports.add("Catering list…").addActionListener(e -> showReport(
				new org.lerot.myreceptionplanner.reports.CateringReport(occasion, event), "Catering", "catering", null));
		reports.add("Kitchen quantities…").addActionListener(e -> showReport(
				new org.lerot.myreceptionplanner.reports.KitchenQuantities(occasion, event), "Kitchen quantities",
				"kitchen-quantities", null));
		reports.add("Service by table…").addActionListener(e -> showReport(
				new org.lerot.myreceptionplanner.reports.ServiceByTable(occasion, event), "Service by table",
				"service-by-table", "Save as PDF, a page per table…"));
		reports.addSeparator();
		reports.add("Table plan (PDF)…").addActionListener(e -> tablePlanPdf());
		bar.add(dropDown("Reports", reports));
		JPopupMenu messages = new JPopupMenu();
		messages.add("Write to guests…").addActionListener(e ->
		{
			if (MessagesDialog.show(this, occasion, event, this::choosePdf, this::open)) changed();
		});
		messages.add("Message templates…").addActionListener(e ->
		{
			if (TemplatesDialog.edit(this, occasion)) changed();
		});
		bar.add(dropDown("Messages", messages));
		return bar;
	}

	// the plan's scroll pane with zoom buttons floating in its bottom right corner
	private JComponent withZoom(JScrollPane scroll)
	{
		JPanel zoom = new JPanel(new GridLayout(1, 2, 4, 0));
		zoom.setOpaque(false);
		for (String[] z : new String[][] { { "−", "Zoom out (or Ctrl + mouse wheel)" }, { "+", "Zoom in (or Ctrl + mouse wheel)" } })
		{
			JButton b = new JButton(z[0]);
			b.setToolTipText(z[1]);
			b.setFont(b.getFont().deriveFont(Font.BOLD, b.getFont().getSize2D() + 3f));
			b.setFocusable(false);
			b.setPreferredSize(new Dimension(36, 32));
			double factor = z[0].equals("+") ? 1.2 : 1 / 1.2;
			b.addActionListener(e -> planPanel.zoom(factor));
			zoom.add(b);
		}
		JLayeredPane layers = new JLayeredPane();
		layers.setLayout(new LayoutManager()
		{
			public void addLayoutComponent(String name, Component c) {}

			public void removeLayoutComponent(Component c) {}

			public Dimension preferredLayoutSize(Container parent) { return scroll.getPreferredSize(); }

			public Dimension minimumLayoutSize(Container parent) { return scroll.getMinimumSize(); }

			public void layoutContainer(Container parent)
			{
				scroll.setBounds(0, 0, parent.getWidth(), parent.getHeight());
				Dimension d = zoom.getPreferredSize();
				int right = scroll.getVerticalScrollBar().isVisible() ? scroll.getVerticalScrollBar().getWidth() : 0;
				int bottom = scroll.getHorizontalScrollBar().isVisible() ? scroll.getHorizontalScrollBar().getHeight() : 0;
				zoom.setBounds(parent.getWidth() - right - d.width - 12, parent.getHeight() - bottom - d.height - 12,
						d.width, d.height);
			}
		});
		layers.add(scroll, JLayeredPane.DEFAULT_LAYER);
		layers.add(zoom, JLayeredPane.PALETTE_LAYER);
		scroll.getVerticalScrollBar().addComponentListener(new java.awt.event.ComponentAdapter()
		{
			public void componentShown(java.awt.event.ComponentEvent e) { layers.revalidate(); }

			public void componentHidden(java.awt.event.ComponentEvent e) { layers.revalidate(); }
		});
		scroll.getHorizontalScrollBar().addComponentListener(new java.awt.event.ComponentAdapter()
		{
			public void componentShown(java.awt.event.ComponentEvent e) { layers.revalidate(); }

			public void componentHidden(java.awt.event.ComponentEvent e) { layers.revalidate(); }
		});
		return layers;
	}

	private <C extends JComponent> C eventOnly(C c)
	{
		eventOnly.add(c);
		return c;
	}

	private static JComponent separator()
	{
		JToolBar.Separator s = new JToolBar.Separator();
		s.setOrientation(SwingConstants.VERTICAL);
		return s;
	}

	/*
	 * The Guests view (the whole occasion's guest list) or the Event view (the
	 * plan of the event chosen); the plan's own controls show only with it.
	 */
	private void setGuestView(boolean guests)
	{
		guestView = guests;
		(guests ? guestViewButton : eventViewButton).setSelected(true);
		if (guests)
		{
			if (guestsPanel != null) viewHolder.remove(guestsPanel);
			guestsPanel = new GuestsPanel(occasion, this::guestListChanged);
			viewHolder.add(guestsPanel, "guests");
		} else
		{
			seatingPanel.refresh();
			planPanel.repaint();
		}
		views.show(viewHolder, guests ? "guests" : "event");
		for (JComponent c : eventOnly) c.setVisible(!guests);
		if (guests)
		{
			addTableButton.setVisible(false);
			tableButton.setVisible(false);
		} else setMode(mode);
		updateTitle();
	}

	private void guestListChanged()
	{
		changed();
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
		if (guestView) setGuestView(true);
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
		planHolder.add(withZoom(scroll), BorderLayout.CENTER);
		planHolder.add(seatingPanel, BorderLayout.EAST);
		setMode(mode);
		selectionChanged(null);
		updateTitle();
	}

	private void editOccasion()
	{
		if (OccasionDialogs.editOccasion(this, occasion)) changed();
	}

	// a stage clicked on the progress screen; true to close it
	private boolean progressStep(Progress.Step step, Event e)
	{
		switch (step)
		{
			case OCCASION -> editOccasion();
			case EVENTS ->
			{
				Event first = occasion.events().stream().filter(x -> x.getPlace().isEmpty() || x.getTime().isEmpty())
						.findFirst().orElse(event);
				if (first != event) showEvent(first);
				editEvent();
			}
			case GUESTS ->
			{
				setGuestView(true);
				return true;
			}
			case INVITATIONS -> writeToGuests(Messages.INVITATION, 3);
			case MENUS_SENT -> writeToGuests(Messages.MENU_CHOICES, 4);
			case TABLES ->
			{
				showEvent(e);
				if (guestView) setGuestView(false);
				setMode(PlanPanel.Mode.LAYOUT);
				return true;
			}
			case MENU ->
			{
				if (MenuDialog.edit(this, occasion, e)) changed();
			}
			case SEATS ->
			{
				showEvent(e);
				if (guestView) setGuestView(false);
				setMode(PlanPanel.Mode.SEATING);
				return true;
			}
			case CHOICES ->
			{
				if (MenuChoicesDialog.show(this, e))
				{
					seatingPanel.refresh();
					changed();
				}
			}
			default -> { }
		}
		return false;
	}

	private void writeToGuests(String template, int showing)
	{
		if (MessagesDialog.show(this, occasion, event, this::choosePdf, this::open, template, showing)) changed();
	}

	private void invitations()
	{
		if (!InvitationsDialog.show(this, occasion)) return;
		seatingPanel.refresh();
		planPanel.repaint();
		changed();
	}

	private void editEvent()
	{
		if (!OccasionDialogs.editEvent(this, occasion, event)) return;
		fillEventChooser();
		showEvent(event);
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
		if (guestView && guestsPanel != null) guestsPanel.refresh();
		updateTitle();
	}

	// ---- the tables ----

	// an unreserved party has no seating, so only its layout
	private void setMode(PlanPanel.Mode newMode)
	{
		seatingButton.setEnabled(event.isSeated());
		seatingButton.setToolTipText(event.isSeated() ? "Seat the guests"
				: event.getName() + " is a party, unreserved: no one is given a seat");
		if (!event.isSeated()) newMode = PlanPanel.Mode.LAYOUT;
		mode = newMode;
		boolean seating = mode == PlanPanel.Mode.SEATING;
		(seating ? seatingButton : layoutButton).setSelected(true);
		addTableButton.setVisible(!seating && !guestView);
		tableButton.setVisible(!seating && !guestView);
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

	// no chairs: only to show which way the room faces
	private void addAltar()
	{
		place(new org.lerot.myreceptionplanner.model.Altar(plan().nextId(), "", 4));
	}

	private void addPew()
	{
		place(new org.lerot.myreceptionplanner.model.Pew(plan().nextId(), "", 8));
	}

	// in view, a little apart from the last one added, and chosen
	private void place(Table t)
	{
		Plan plan = plan();
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
						+ "left to right along each row; pews are numbered on their own the same way.\n"
						+ "The head table and tables with their own name keep their names. Guests keep their seats.",
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
		duplicateItem.setEnabled(selected != null);
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

	// a copy of the table a little below and to the right, chosen to drag into place
	@Override
	public void duplicateRequested(Table table)
	{
		if (table == null) return;
		Plan plan = plan();
		Table copy = table.duplicate(plan.nextId(), 80, 80);
		plan.add(copy);
		plan.numberUnnumbered();
		planPanel.select(copy);
		planChanged();
		planPanel.planUpdated();
		planPanel.requestFocusInWindow();
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
				+ "Occasion ▾ → Occasion details and Events ▾ → Event details.</html>");
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

	// a report on screen, with a choice to save it as a PDF (with a page for
	// each section, for service by table, when perSectionPage names that choice)
	private void showReport(org.lerot.myreceptionplanner.reports.Report report, String title, String file,
			String perSectionPage)
	{
		JEditorPane page = new JEditorPane("text/html", report.html());
		page.setEditable(false);
		page.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(page);
		scroll.setPreferredSize(new Dimension(820, 580));
		Object[] options = perSectionPage == null ? new Object[] { "Save as PDF…", "Close" }
				: new Object[] { "Save as PDF…", perSectionPage, "Close" };
		int choice = JOptionPane.showOptionDialog(this, scroll, title + ": " + event.getName(),
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[options.length - 1]);
		if (choice < 0 || choice == options.length - 1) return;
		Path to = choosePdf(file + "-" + event.getFile().replaceFirst("\\.xml$", "") + ".pdf");
		if (to == null) return;
		try
		{
			org.lerot.myreceptionplanner.reports.ReportPdf.write(report, to, choice == 1);
			open(to);
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
		setTitle((unsaved ? "• " : "") + occasion.getName() + " — " + (guestView ? "Guests" : event.getName())
				+ " — MyReceptionPlanner" + VERSION);
		if (guestView)
		{
			status.setForeground(UIManager.getColor("Label.foreground"));
			status.setText(occasion.heading());
			return;
		}
		Plan plan = plan();
		String head = plan.head().map(t -> "head table: " + plan.label(t)).orElse("no head table");
		String people = switch (event.getKind())
		{
			case SEATED -> event.invited().size() + " invited, " + event.getSeating().seatedCount() + " seated";
			case HALL -> "hall: " + event.invited().size() + " invited, " + plan.reservedPlaces() + " reserved seats ("
					+ event.getSeating().seatedCount() + " given), " + (plan.places() - plan.reservedPlaces()) + " open";
			case UNRESERVED -> "party, unreserved: " + event.invited().size() + " invited"
					+ (event.getCapacity() > 0 ? " of a capacity of " + event.getCapacity() : "");
		};
		if (event.overCapacity() > 0) people += "  ⚠ " + event.overCapacity() + " more than the "
				+ (event.getKind() == Event.Kind.HALL ? "seats" : "capacity");
		status.setForeground(event.overCapacity() > 0 ? Theme.HEAD.darker() : UIManager.getColor("Label.foreground"));
		status.setText(occasion.heading() + "   ·   " + occasion.headingOf(event) + "   ·   "
				+ plan.tables().size() + " tables, " + plan.places() + " places, " + head + ",   " + people);
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
