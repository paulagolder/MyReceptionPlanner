package org.lerot.myreceptionplanner;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Example;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Messages;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.OccasionXml;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Progress;
import org.lerot.myreceptionplanner.model.OvalTable;
import org.lerot.myreceptionplanner.model.PolygonTable;
import org.lerot.myreceptionplanner.model.RectTable;
import org.lerot.myreceptionplanner.model.TableDesigns;
import org.lerot.myreceptionplanner.model.WalkTable;
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
	private static final String SEATING_WIDTH = "seatingWidth";  // of the side panel in seating mode, as last dragged
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
	private JSplitPane seatingSplit;  // the plan, and the seating panel beside it
	private boolean placingDivider;   // the divider moved here, not dragged
	private final JToggleButton seatingButton = new JToggleButton("Seating");
	private JButton addTableButton;
	private JButton tableButton;
	private JButton cateringButton;  // only for a catered event
	private final JComboBox<Event> eventChooser = new JComboBox<>();
	private boolean fillingChooser;
	private final JMenuItem deleteEventItem = new JMenuItem("Delete event…");
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
		occasionMenu.add("Invitations…").addActionListener(e -> setGuestView(true));
		occasionMenu.addSeparator();
		occasionMenu.add("New occasion…").addActionListener(e -> newOccasion());
		occasionMenu.add("Open occasion…").addActionListener(e -> openOccasion());
		JMenuItem example = occasionMenu.add("Open the example…");
		example.setToolTipText("A fresh copy of Romeo and Juliette's Wedding, an example occasion to try things on");
		example.addActionListener(e -> openExample());
		occasionMenu.addSeparator();
		occasionMenu.add("Save…").addActionListener(e -> save());
		occasionMenu.addSeparator();
		JMenuItem backup = occasionMenu.add("Backup…");
		backup.setToolTipText("A copy of the whole occasion folder, as it stands now, in a folder of its own");
		backup.addActionListener(e -> backup());
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
		eventsMenu.add("New event…").addActionListener(e -> newEvent());
		eventsMenu.add("View event…").addActionListener(e -> editEvent());
		JMenuItem saveEvent = eventsMenu.add("Save event…");
		saveEvent.setToolTipText("This event's file on its own");
		saveEvent.addActionListener(e -> saveEvent());
		deleteEventItem.addActionListener(e -> deleteEvent());
		eventsMenu.add(deleteEventItem);
		eventsMenu.addSeparator();
		JMenuItem check = eventsMenu.add("Progress…");
		check.setToolTipText("Spare places at the tables, and guests not yet seated");
		check.addActionListener(e -> ChecksDialog.show(this, occasion, event));
		eventsMenu.add("Table plan (PDF)…").addActionListener(e -> tablePlanPdf());
		JMenuItem tableList = eventsMenu.add("Table list (PDF)…");
		tableList.setToolTipText("A card for one table: its name or number and its guests in seat order");
		tableList.addActionListener(e -> tableListPdf());
		JMenuItem finder = eventsMenu.add("Seat finder (PDF)…");
		finder.setToolTipText("For the door: the room with seat numbers, and every guest by family name with table and seat");
		finder.addActionListener(e -> seatFinderPdf());
		JMenuItem door = eventsMenu.add("Door list (PDF)…");
		door.setToolTipText("For those on the door: everyone expected, by family name, with a box to tick");
		door.addActionListener(e -> doorListPdf());
		JMenuItem cards = eventsMenu.add("Place cards (PDF)…");
		cards.setToolTipText("A folded card for each seated guest, table by table, for perforated card");
		cards.addActionListener(e -> placeCardsPdf());
		bar.add(eventOnly(dropDown("Event", eventsMenu)));
		bar.add(eventOnly(separator()));

		JPopupMenu add = new JPopupMenu();
		add.add("Round table").addActionListener(e -> place(new RoundTable(plan().nextId(), "", 8)));
		add.add("Rectangular table").addActionListener(e -> place(new RectTable(plan().nextId(), "", 4, 0)));
		add.add("Oval table").addActionListener(e -> place(new OvalTable(plan().nextId(), "", 8, 4)));
		add.add("Polygon table (hexagon …)").addActionListener(e -> place(new PolygonTable(plan().nextId(), "", 6, 1)));
		add.add("Table design (T, E …)…").addActionListener(e -> addDesign());
		add.add("Pew (a row of seats)").addActionListener(e -> addPew());
		add.addSeparator();
		add.add("Altar / speakers' table").addActionListener(e -> addAltar());
		add.addSeparator();
		add.add("Import layout…").addActionListener(e -> importLayout());
		add.add("Export layout…").addActionListener(e -> exportLayout());
		addTableButton = dropDown("Layout", add);
		addTableButton.setToolTipText("Add a table, pew or altar to the plan");
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

		// on: seat the guests, with the seat panel; off: set out the tables
		seatingButton.putClientProperty("FlatLaf.style", "selectedBackground: #2F6FDB; selectedForeground: #FFFFFF; toolbar.selectedBackground: #2F6FDB");
		seatingButton.addActionListener(e ->
				setMode(seatingButton.isSelected() ? PlanPanel.Mode.SEATING : PlanPanel.Mode.LAYOUT));
		bar.add(eventOnly(seatingButton));

		JPopupMenu catering = new JPopupMenu();
		catering.add("Menu…").addActionListener(e ->
		{
			if (MenuDialog.edit(this, occasion, event, menuFolder(), MenuDialog.Start.MENU)) changed();
		});
		catering.add("Menu choices and dietary needs…").addActionListener(e ->
		{
			if (MenuChoicesDialog.show(this, event))
			{
				seatingPanel.refresh();
				changed();
			}
		});
		catering.addSeparator();
		catering.add("Catering list…").addActionListener(e -> showReport(
				new org.lerot.myreceptionplanner.reports.CateringReport(occasion, event), "Catering", "catering", null));
		catering.add("Kitchen quantities…").addActionListener(e -> showReport(
				new org.lerot.myreceptionplanner.reports.KitchenQuantities(occasion, event), "Kitchen quantities",
				"kitchen-quantities", null));
		catering.add("Service by table…").addActionListener(e -> showReport(
				new org.lerot.myreceptionplanner.reports.ServiceByTable(occasion, event), "Service by table",
				"service-by-table", "Save as PDF, a page per table…"));
		catering.addSeparator();
		JMenuItem importMenu = catering.add("Import menu…");
		importMenu.setToolTipText("Take a menu file (XML or YAML) exported from another event, or another occasion's event file");
		importMenu.addActionListener(e -> importMenu());
		JMenuItem exportMenu = catering.add("Export menu…");
		exportMenu.addActionListener(e -> exportMenu());
		// importing only until there is a menu; exporting once there is
		catering.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
		{
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
			{
				importMenu.setVisible(!event.hasMenu());
				exportMenu.setEnabled(event.hasMenu());
			}

			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}

			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
		});
		cateringButton = dropDown("Catering", catering);
		cateringButton.setToolTipText("The menu, choices and dietary needs, and the kitchen's lists");
		bar.add(cateringButton);
		bar.addSeparator();
		JPopupMenu messages = new JPopupMenu();
		messages.add("Write to guests…").addActionListener(e ->
		{
			if (MessagesDialog.show(this, occasion, event, this::choosePdf, this::open)) changed();
		});
		messages.add("Message templates…").addActionListener(e ->
		{
			if (TemplatesDialog.edit(this, occasion, ROOT.resolve(OccasionXml.TEMPLATES))) changed();
		});
		bar.add(dropDown("Messages", messages));

		JPopupMenu info = new JPopupMenu();
		info.add("Help…").addActionListener(e -> InfoDialogs.help(this));
		info.add("About MyReceptionPlanner…").addActionListener(e -> InfoDialogs.about(this, occasion, folder));
		bar.add(Box.createHorizontalGlue());
		bar.add(dropDown("Info", info));
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
			cateringButton.setVisible(false);
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
		seatingSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, withZoom(scroll), seatingPanel);
		seatingSplit.setResizeWeight(1);  // a wider window widens the plan
		seatingSplit.setContinuousLayout(true);
		seatingSplit.setBorder(null);
		seatingSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, ev ->
		{
			int width = seatingSplit.getWidth() - seatingSplit.getDividerLocation() - seatingSplit.getDividerSize();
			if (!placingDivider && seatingPanel.isVisible() && seatingSplit.isShowing() && width >= SeatingPanel.MIN_WIDTH)
				PREFS.putInt(SEATING_WIDTH, width);
		});
		planHolder.add(seatingSplit, BorderLayout.CENTER);
		setMode(mode);
		selectionChanged(null);
		updateTitle();
	}

	// in seating mode, the seating panel as wide as last dragged (but leaving
	// the plan room) and a divider to drag; otherwise the plan has it all
	private void placeSeatingDivider(boolean seating)
	{
		seatingSplit.setDividerSize(seating ? UIManager.getInt("SplitPane.dividerSize") : 0);
		if (!seating) return;
		if (seatingSplit.getWidth() > 0)
		{
			SwingUtilities.invokeLater(this::setSeatingWidth);
			return;
		}
		// not laid out yet: once it has its size
		seatingSplit.addComponentListener(new java.awt.event.ComponentAdapter()
		{
			public void componentResized(java.awt.event.ComponentEvent e)
			{
				seatingSplit.removeComponentListener(this);
				setSeatingWidth();
			}
		});
	}

	private void setSeatingWidth()
	{
		int total = seatingSplit.getWidth();
		int w = Math.max(SeatingPanel.MIN_WIDTH, Math.min(PREFS.getInt(SEATING_WIDTH, SeatingPanel.START_WIDTH), total - 300));
		placingDivider = true;
		seatingSplit.setDividerLocation(total - w - seatingSplit.getDividerSize());
		placingDivider = false;
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
			case GUESTS, REPLIES ->
			{
				setGuestView(true);
				return true;
			}
			case INVITATIONS -> writeToGuests(Messages.INVITATION, 5);
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
				if (MenuDialog.edit(this, occasion, e, menuFolder(), MenuDialog.Start.MENU)) changed();
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

	private void editEvent()
	{
		if (!OccasionDialogs.editEvent(this, occasion, event)) return;
		fillEventChooser();
		showEvent(event);
		changed();
	}

	private void newEvent()
	{
		Event e = OccasionDialogs.newEvent(this, occasion, event, occasionFolder().resolve(Occasion.OUTPUT));
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
		try
		{
			o.setHonorifics(org.lerot.myreceptionplanner.model.Honorifics.find(ROOT.resolve(o.folderName())));
			List<org.lerot.myreceptionplanner.model.MessageTemplate> shared = OccasionXml.sharedTemplates(ROOT.resolve(o.folderName()));
			if (shared != null)
			{
				o.templates().clear();
				o.templates().addAll(shared);
			}
		} catch (IOException e)
		{
			// the standard templates, then
		}
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
		seatingButton.setToolTipText(event.isSeated() ? "On: seat the guests; off: set out the tables"
				: event.getName() + " is a party, unreserved: no one is given a seat");
		if (!event.isSeated()) newMode = PlanPanel.Mode.LAYOUT;
		mode = newMode;
		boolean seating = mode == PlanPanel.Mode.SEATING;
		seatingButton.setSelected(seating);
		addTableButton.setVisible(!seating && !guestView);
		tableButton.setVisible(!seating && !guestView);
		cateringButton.setVisible(event.isCatered() && !guestView);
		planPanel.setMode(mode);
		seatingPanel.setVisible(seating);
		if (seating)
		{
			seatingPanel.refresh();
			seatingPanel.showTable(planPanel.getSelected(), -1);
		}
		placeSeatingDivider(seating);
		planHolder.revalidate();
		planHolder.repaint();
	}

	private Plan plan() { return event.getPlan(); }

	// no chairs: only to show which way the room faces
	private void addAltar()
	{
		place(new org.lerot.myreceptionplanner.model.Altar(plan().nextId(), "", 4));
	}

	private void addPew()
	{
		place(new org.lerot.myreceptionplanner.model.Pew(plan().nextId(), "", 8));
	}

	// a table from a design file, from the Table designs folder (made, with
	// the planner's own designs, the first time)
	private void addDesign()
	{
		Path folder;
		try
		{
			folder = TableDesigns.folder(ROOT);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Table designs", JOptionPane.ERROR_MESSAGE);
			return;
		}
		JFileChooser fc = new JFileChooser(folder.toFile());
		fc.setDialogTitle("Add a table design");
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Table designs (XML)", "xml"));
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		TableDesigns.Design design;
		try
		{
			design = TableDesigns.load(fc.getSelectedFile().toPath(), plan().nextId());
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot add the design", JOptionPane.ERROR_MESSAGE);
			return;
		}
		Table t = design.table();
		place(t);
		// the design's head table, unless there is one already
		if (design.head() && plan().head().isEmpty())
		{
			plan().setHead(t);
			planChanged();
			planPanel.planUpdated();
		}
		if (t instanceof WalkTable walked && !walked.warnings().isEmpty())
			JOptionPane.showMessageDialog(this, "<html>" + fc.getSelectedFile().getName() + ":<br>"
							+ String.join("<br>", walked.warnings()) + "</html>", "Table design",
					JOptionPane.WARNING_MESSAGE);
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
						+ "The head table and tables with their own name are numbered too, but show their names,\n"
						+ "so some numbers will not be seen. Guests keep their seats.",
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

	private static final String PAPER = "paper", LANDSCAPE = "landscape", CARD = "tableListCard",
			FINDER_PAPER = "seatFinderPaper", DOOR_SPLIT = "doorListSplit",
			DOOR_TITLES = "doorListTitles", FINDER_TITLES = "seatFinderTitles",
			CARD_STOCK = "placeCardStock", CARDS_FOR_STAFF = "placeCardsForStaff";

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
				+ "Occasion ▾ → Occasion details and Event ▾ → View event.</html>");
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

	// a tent card for each seated guest, table by table in seat order
	private void placeCardsPdf()
	{
		if (!event.isSeated() || event.getSeating().seatedCount() == 0)
		{
			JOptionPane.showMessageDialog(this, event.isSeated() ? "No one is seated at " + event.getName() + " yet."
					: event.getName() + " is a party, unreserved: no one is given a seat,\nso there are no place cards.",
					"Place cards", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		List<Table> tables = org.lerot.myreceptionplanner.model.Checks.inGuideOrder(plan()).stream()
				.filter(t -> !event.getSeating().seatedAt(t.getId()).isEmpty()).toList();
		int seated = event.getSeating().seatedCount();
		List<Object> choices = new ArrayList<>();
		choices.add("All tables (" + seated + " cards)");
		choices.addAll(tables);
		JComboBox<Object> which = new JComboBox<>(choices.toArray());
		which.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean focus)
			{
				String text = value instanceof Table t ? plan().label(t) + "  ("
						+ event.getSeating().seatedAt(t.getId()).size() + " cards)" : String.valueOf(value);
				return super.getListCellRendererComponent(list, text, index, selected, focus);
			}
		});
		if (tables.contains(planPanel.getSelected())) which.setSelectedItem(planPanel.getSelected());
		JComboBox<org.lerot.myreceptionplanner.reports.PlaceCardsPdf.Stock> stock =
				new JComboBox<>(org.lerot.myreceptionplanner.reports.PlaceCardsPdf.Stock.values());
		try
		{
			stock.setSelectedItem(org.lerot.myreceptionplanner.reports.PlaceCardsPdf.Stock.valueOf(
					PREFS.get(CARD_STOCK, "DECADRY_6")));
		} catch (IllegalArgumentException ignored)
		{
		}
		JCheckBox forStaff = new JCheckBox("On the back, menu choices and dietary needs for the waiting staff",
				PREFS.getBoolean(CARDS_FOR_STAFF, true));
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 2", "[right][grow,fill]"));
		form.add(new JLabel("Tables"));
		form.add(which);
		form.add(new JLabel("Card"));
		form.add(stock);
		form.add(forStaff, "skip");
		int unseated = org.lerot.myreceptionplanner.reports.SeatFinderPdf.unseated(event);
		JLabel note = new JLabel("<html>The name large on the front, with the table and seat; the name<br>"
				+ "again on the back, upside down so it reads when the card stands.<br>"
				+ "Table by table, in seat order, ready to lay out."
				+ (unseated > 0 ? "<br>" + unseated + (unseated == 1 ? " guest is" : " guests are")
						+ " not seated yet, so have no card." : "")
				+ "<br>Try a sheet on plain paper against the card first.</html>");
		note.setForeground(Theme.EDGE);
		form.add(note, "skip, gaptop 6");
		if (JOptionPane.showConfirmDialog(this, form, "Place cards: " + event.getName(), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		var chosenStock = (org.lerot.myreceptionplanner.reports.PlaceCardsPdf.Stock) stock.getSelectedItem();
		PREFS.put(CARD_STOCK, chosenStock.name());
		PREFS.putBoolean(CARDS_FOR_STAFF, forStaff.isSelected());
		Object chosen = which.getSelectedItem();
		List<Table> printing = chosen instanceof Table t ? List.of(t) : tables;
		String name = "place-cards-" + eventBase() + (chosen instanceof Table t ? "-"
				+ plan().label(t).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") : "");
		Path file = choosePdf(name + ".pdf");
		if (file == null) return;
		try
		{
			org.lerot.myreceptionplanner.reports.PlaceCardsPdf.write(occasion, event, printing, chosenStock,
					forStaff.isSelected(), file);
			open(file);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot make the PDF", JOptionPane.ERROR_MESSAGE);
		}
	}

	// for the board at the door: the room, and everyone by family name with their table and seat
	private void seatFinderPdf()
	{
		if (!event.isSeated())
		{
			JOptionPane.showMessageDialog(this, event.getName() + " is a party, unreserved: no one is given a seat,\n"
					+ "so there is no seat finder.", "Seat finder", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (event.getSeating().seatedCount() == 0)
		{
			JOptionPane.showMessageDialog(this, "No one is seated at " + event.getName() + " yet.", "Seat finder",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JComboBox<String> paper = new JComboBox<>(new String[] { "A4", "A3" });
		paper.setSelectedItem(PREFS.get(FINDER_PAPER, "A3"));
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 2", "[right][grow,fill]"));
		form.add(new JLabel("Paper"));
		form.add(paper);
		JCheckBox titles = titlesBox(FINDER_TITLES);
		form.add(titles, "skip");
		JLabel note = new JLabel("<html>Landscape: the room in the middle with each table's name and<br>"
				+ "seat numbers, and down each side everyone seated, by family name,<br>"
				+ "with their table and seat. A4 can be enlarged when printed.</html>");
		note.setForeground(Theme.EDGE);
		form.add(note, "skip, gaptop 6");
		if (JOptionPane.showConfirmDialog(this, form, "Seat finder: " + event.getName(), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		PREFS.put(FINDER_PAPER, (String) paper.getSelectedItem());
		PREFS.putBoolean(FINDER_TITLES, titles.isSelected());
		Path file = choosePdf("seat-finder-" + eventBase() + ".pdf");
		if (file == null) return;
		try
		{
			org.lerot.myreceptionplanner.reports.SeatFinderPdf.write(occasion, event,
					com.itextpdf.text.PageSize.getRectangle((String) paper.getSelectedItem()).rotate(), titles.isSelected(), file);
			open(file);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot make the PDF", JOptionPane.ERROR_MESSAGE);
		}
	}

	// whether a list by family name gives titles, "Fairweather, Dame Margot"; remembered as key
	private static JCheckBox titlesBox(String key)
	{
		return new JCheckBox("With titles: \"Fairweather, Dame Margot\" rather than \"Fairweather, Margot\"",
				PREFS.getBoolean(key, true));
	}

	// for the door: everyone expected, by family name, with a box to tick as they come in
	private void doorListPdf()
	{
		var entries = org.lerot.myreceptionplanner.reports.DoorListPdf.entries(event, occasion.getGuests().guests(), true);
		if (entries.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "No one is expected at " + event.getName() + " yet.", "Door list",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String at = org.lerot.myreceptionplanner.reports.DoorListPdf.splitLetter(entries);
		String[] halves = at == null ? null : org.lerot.myreceptionplanner.reports.DoorListPdf.halves(at);
		JCheckBox split = new JCheckBox(at == null ? "In two halves" : "In two halves, " + halves[0] + " and "
				+ halves[1] + ", for two people on the door");
		split.setEnabled(at != null);
		split.setSelected(at != null && PREFS.getBoolean(DOOR_SPLIT, false));
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 1"));
		form.add(split);
		JCheckBox titles = titlesBox(DOOR_TITLES);
		form.add(titles);
		JLabel note = new JLabel("<html>A4, two columns: everyone expected by family name, with a box to tick.<br>"
				+ "A lead's places not yet named are on their line (+2), others have<br>"
				+ "their party beside them, reserved seats are in bold, and those who<br>"
				+ "have not replied are marked. The totals are at the end.</html>");
		note.setForeground(Theme.EDGE);
		form.add(note, "gaptop 6");
		if (JOptionPane.showConfirmDialog(this, form, "Door list: " + event.getName(), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		if (at != null) PREFS.putBoolean(DOOR_SPLIT, split.isSelected());
		PREFS.putBoolean(DOOR_TITLES, titles.isSelected());
		Path file = choosePdf("door-list-" + eventBase() + ".pdf");
		if (file == null) return;
		try
		{
			org.lerot.myreceptionplanner.reports.DoorListPdf.write(occasion, event, split.isSelected(), titles.isSelected(), file);
			open(file);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot make the PDF", JOptionPane.ERROR_MESSAGE);
		}
	}

	// a card to stand on one table, listing its guests by seat
	private void tableListPdf()
	{
		if (!event.isSeated())
		{
			JOptionPane.showMessageDialog(this, event.getName() + " is a party, unreserved: no one is given a seat,\n"
					+ "so there are no table lists.", "Table list", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		List<Table> tables = plan().tables().stream()
				.filter(t -> t.places() > 0 && !(t instanceof org.lerot.myreceptionplanner.model.Pew))
				.sorted(java.util.Comparator.comparingInt((Table t) -> t.isHead() ? 0 : 1)
						.thenComparingInt(t -> t.getNumber() == 0 ? Integer.MAX_VALUE : t.getNumber()))
				.toList();
		if (tables.isEmpty())
		{
			JOptionPane.showMessageDialog(this, event.getName() + " has no tables yet.", "Table list",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// every table with someone seated at it, or one table
		List<Table> occupied = tables.stream().filter(t -> !event.getSeating().seatedAt(t.getId()).isEmpty()).toList();
		String all = "All tables (" + occupied.size() + " with guests)";
		List<Object> choices = new ArrayList<>();
		if (!occupied.isEmpty()) choices.add(all);
		choices.addAll(tables);
		JComboBox<Object> which = new JComboBox<>(choices.toArray());
		which.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean focus)
			{
				String text = value instanceof Table t ? plan().label(t) + "  ("
						+ event.getSeating().seatedAt(t.getId()).size() + " seated)" : String.valueOf(value);
				return super.getListCellRendererComponent(list, text, index, selected, focus);
			}
		});
		if (tables.contains(planPanel.getSelected())) which.setSelectedItem(planPanel.getSelected());
		JComboBox<org.lerot.myreceptionplanner.reports.TableListPdf.Card> card =
				new JComboBox<>(org.lerot.myreceptionplanner.reports.TableListPdf.Card.values());
		try
		{
			card.setSelectedItem(org.lerot.myreceptionplanner.reports.TableListPdf.Card.valueOf(PREFS.get(CARD, "A5")));
		} catch (IllegalArgumentException ignored)
		{
		}
		JPanel form = new JPanel(new MigLayout("insets 0, wrap 2", "[right][grow,fill]"));
		form.add(new JLabel("Table"));
		form.add(which);
		form.add(new JLabel("Card"));
		form.add(card);
		JLabel note = new JLabel("<html>The emblem, the occasion and the event, the table large,<br>"
				+ "then its guests in seat order. A folded card has the list<br>"
				+ "on the front and again on the back.<br>"
				+ "All tables go on A4 sheets, as many as fit (to cut, or tear<br>"
				+ "on perforated card); tables with no one seated are left out.</html>");
		note.setForeground(Theme.EDGE);
		form.add(note, "skip, gaptop 6");
		if (JOptionPane.showConfirmDialog(this, form, "Table list: " + event.getName(), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		var chosenCard = (org.lerot.myreceptionplanner.reports.TableListPdf.Card) card.getSelectedItem();
		PREFS.put(CARD, chosenCard.name());
		Object chosen = which.getSelectedItem();

		String name = chosen instanceof Table t
				? "table-list-" + eventBase() + "-"
						+ plan().label(t).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "")
				: "table-lists-" + eventBase();
		Path file = choosePdf(name + ".pdf");
		if (file == null) return;
		try
		{
			if (chosen instanceof Table t)
				org.lerot.myreceptionplanner.reports.TableListPdf.write(occasion, event, t, chosenCard, file);
			else
				org.lerot.myreceptionplanner.reports.TableListPdf.writeAll(occasion, event, occupied, chosenCard, file);
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
	// the occasion's folder: where it is kept, or will be when first saved
	private Path occasionFolder()
	{
		return folder != null ? folder : ROOT.resolve(occasion.folderName());
	}

	// in the Output folder inside the occasion's folder
	private Path choosePdf(String name)
	{
		return chooseOutput(name, "pdf", "PDF files", "Save the PDF");
	}

	// a file to write in the Output folder, the name given filled in; null if cancelled
	private Path chooseOutput(String name, String extension, String kind, String title)
	{
		File in = occasionFolder().resolve(Occasion.OUTPUT).toFile();
		in.mkdirs();
		JFileChooser fc = new JFileChooser(in);
		fc.setDialogTitle(title);
		fc.setSelectedFile(new File(in, name));
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(kind, extension));
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
		File f = fc.getSelectedFile();
		if (!f.getName().toLowerCase().endsWith("." + extension)) f = new File(f.getParentFile(), f.getName() + "." + extension);
		if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " is already there. Replace it?",
				title, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
		return f.toPath();
	}

	// where to look for a menu file: the Output folder, if there is one yet
	private Path menuFolder()
	{
		Path out = occasionFolder().resolve(Occasion.OUTPUT);
		return Files.isDirectory(out) ? out : ROOT;
	}

	// an XML file to read, starting in the Output folder; null if cancelled
	private Path chooseImport(String title)
	{
		File in = occasionFolder().resolve(Occasion.OUTPUT).toFile();
		JFileChooser fc = new JFileChooser(in.isDirectory() ? in : ROOT.toFile());
		fc.setDialogTitle(title);
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Layout, menu or event files (XML)", "xml"));
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
		return fc.getSelectedFile().toPath();
	}

	// the event's name as in its file name: "wedding-breakfast"
	private String eventBase()
	{
		return event.getFile().replaceFirst("\\.xml$", "");
	}

	// ---- layouts and menus to and from files ----

	private void exportLayout()
	{
		if (plan().tables().isEmpty())
		{
			JOptionPane.showMessageDialog(this, event.getName() + " has no tables yet.", "Export layout",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Path to = chooseOutput(eventBase() + "_layout.xml", "xml", "Layout files (XML)", "Export layout");
		if (to == null) return;
		try
		{
			OccasionXml.saveLayout(plan(), to);
			JOptionPane.showMessageDialog(this, "Saved " + to, "Export layout", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot export", JOptionPane.ERROR_MESSAGE);
		}
	}

	// the tables from a file in place of the event's own; everyone seated loses their seat
	private void importLayout()
	{
		Path from = chooseImport("Import a layout");
		if (from == null) return;
		Plan imported;
		try
		{
			imported = OccasionXml.loadLayout(from);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot import", JOptionPane.ERROR_MESSAGE);
			return;
		}
		int n = plan().tables().size(), seated = event.getSeating().seatedCount();
		if (n > 0 && JOptionPane.showConfirmDialog(this, "Replace the " + n + (n == 1 ? " table" : " tables") + " of "
						+ event.getName() + " with the " + imported.tables().size() + " from " + from.getFileName() + "?"
						+ (seated > 0 ? "\nThe " + seated + " seated guests lose their seats." : ""),
				"Import layout", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		for (Guest g : List.copyOf(event.getSeating().all().keySet()))
			event.getSeating().unseat(g);
		for (Table t : List.copyOf(plan().tables()))
			plan().remove(t);
		imported.tables().forEach(plan()::add);
		showEvent(event);
		changed();
	}

	// as XML (all of it, as in the event's file) or YAML (short, to read and edit by hand)
	private void exportMenu()
	{
		File in = occasionFolder().resolve(Occasion.OUTPUT).toFile();
		in.mkdirs();
		JFileChooser fc = new JFileChooser(in);
		fc.setDialogTitle("Export menu");
		javax.swing.filechooser.FileNameExtensionFilter xml =
				new javax.swing.filechooser.FileNameExtensionFilter("Menu files (XML)", "xml");
		javax.swing.filechooser.FileNameExtensionFilter yaml =
				new javax.swing.filechooser.FileNameExtensionFilter("Menu files (YAML, to read and edit)", "yml", "yaml");
		fc.addChoosableFileFilter(xml);
		fc.addChoosableFileFilter(yaml);
		fc.setAcceptAllFileFilterUsed(false);
		fc.setFileFilter(xml);
		fc.setSelectedFile(new File(in, eventBase() + "_menu.xml"));
		// the name follows the format chosen
		fc.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, e ->
		{
			String ext = fc.getFileFilter() == yaml ? ".yml" : ".xml";
			fc.setSelectedFile(new File(fc.getCurrentDirectory(), eventBase() + "_menu" + ext));
		});
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File f = fc.getSelectedFile();
		boolean asYaml = fc.getFileFilter() == yaml || f.getName().toLowerCase().matches(".*\\.ya?ml");
		if (!f.getName().toLowerCase().matches(".*\\.(xml|ya?ml)"))
			f = new File(f.getParentFile(), f.getName() + (asYaml ? ".yml" : ".xml"));
		if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " is already there. Replace it?",
				"Export menu", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		try
		{
			if (asYaml) org.lerot.myreceptionplanner.model.MenuText.save(event.getMenu(), event.getName(), f.toPath());
			else OccasionXml.saveMenu(event.getMenu(), f.toPath());
			JOptionPane.showMessageDialog(this, "Saved " + f, "Export menu", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot export", JOptionPane.ERROR_MESSAGE);
		}
	}

	// a menu from a file, for an event with none yet, opened in Menu… to check
	private void importMenu()
	{
		if (MenuDialog.edit(this, occasion, event, menuFolder(), MenuDialog.Start.IMPORT)) changed();
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

	// the occasion open last time; the first time there are no occasions, the
	// example, copied into the occasions folder
	private void openLastOccasion()
	{
		String last = PREFS.get(LAST_OCCASION, null);
		if (last != null && OccasionXml.isOccasion(Path.of(last)))
		{
			load(Path.of(last));
			return;
		}
		try
		{
			if (Example.noOccasionsIn(ROOT)) load(Example.install(ROOT));
		} catch (IOException e)
		{
			// start without it
		}
	}

	// a fresh copy of the example, beside any copies already there
	private void openExample()
	{
		if (!okToDiscard()) return;
		try
		{
			Path f = Example.install(ROOT);
			load(f);
			status.setText("The example occasion, copied to " + f);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot copy the example", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void openOccasion()
	{
		if (!okToDiscard()) return;
		Path f = chooseFolder("Open an occasion", folder != null ? folder.getParent() : ROOT);
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
	/*
	 * Only the event shown: its file in the occasion's folder. One not saved
	 * before (or an occasion never saved) needs the whole occasion saved.
	 */
	private void saveEvent()
	{
		if (folder == null || !Files.isRegularFile(folder.resolve(event.getFile())))
		{
			JOptionPane.showMessageDialog(this, event.getName() + " has not been saved before, so the whole occasion "
					+ "is saved.", "Save event", JOptionPane.INFORMATION_MESSAGE);
			save();
			return;
		}
		String others = unsaved ? "\nOther changes (the guest list, other events, the occasion's details) are not "
				+ "saved by this." : "";
		if (JOptionPane.showConfirmDialog(this, "Save only " + event.getName() + " (" + event.getFile() + ") in\n"
				+ folder.toAbsolutePath() + "?" + others, "Save event", JOptionPane.OK_CANCEL_OPTION)
				!= JOptionPane.OK_OPTION) return;
		try
		{
			OccasionXml.saveEvent(event, folder);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot save", JOptionPane.ERROR_MESSAGE);
		}
	}

	/*
	 * A copy of the whole occasion, unsaved changes too, in a folder of its
	 * own: offered beside the occasion's folder as "<name>_bak_n". The
	 * occasion stays where it is, still saved or not as before.
	 */
	private void backup()
	{
		Path to = chooseFolder("Back up " + occasion.getName(), OccasionXml.nextBackup(occasionFolder()));
		if (to == null) return;
		try
		{
			boolean full;
			try (var inside = Files.isDirectory(to) ? Files.list(to) : java.util.stream.Stream.<Path>empty())
			{
				full = inside.findAny().isPresent();
			}
			if (full && JOptionPane.showConfirmDialog(this, "The folder " + to + " is not empty.\nBack up into it anyway, replacing files of the same names?",
							"Backup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
				return;
			OccasionXml.backup(occasion, folder, to, droppedFiles);
			JOptionPane.showMessageDialog(this, occasion.getName() + " is backed up in\n" + to.toAbsolutePath(), "Backup",
					JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot back up", JOptionPane.ERROR_MESSAGE);
		}
	}

	// first saved in a folder of its own in MyReceptions, named from the occasion
	private boolean save()
	{
		Path to = occasionFolder();
		while (to != null)
		{
			int answer = confirmSave(to);
			if (answer == 0) return write(to);
			if (answer != 1) return false;
			Path other = chooseFolder("Choose a folder for " + occasion.getName(), to);
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

	// a folder, starting at (or choosing) the one given
	private Path chooseFolder(String title, Path at)
	{
		File start = at.toFile();
		File open = start.isDirectory() ? start : start.getParentFile();
		open.mkdirs();
		JFileChooser fc = new JFileChooser(open);
		fc.setDialogTitle(title);
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setApproveButtonText("Use this folder");
		if (!start.isDirectory()) fc.setSelectedFile(start);
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
		return fc.getSelectedFile().toPath();  // made when the occasion is saved there
	}

	// where the occasions are kept, each in a folder of its own: in Documents
	// (on Windows wherever Documents really is, which may be in OneDrive)
	private static final Path ROOT = (System.getProperty("os.name").startsWith("Windows")
			? javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory().toPath()
			: Path.of(System.getProperty("user.home"), "Documents")).resolve("MyReceptions");

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
					+ event.getSeating().seatedCount() + " given), " + (plan.places() - plan.reservedPlaces()) + " open"
					+ (event.getCapacity() > 0 ? ", capacity " + event.getCapacity() : "");
			case UNRESERVED -> "party, unreserved: " + event.invited().size() + " invited"
					+ (event.getCapacity() > 0 ? " of a capacity of " + event.getCapacity() : "");
		};
		if (event.overCapacity() > 0) people += "  ⚠ " + event.overCapacity() + " more than the " + event.capacityWord();
		status.setForeground(event.overCapacity() > 0 ? Theme.HEAD.darker() : UIManager.getColor("Label.foreground"));
		status.setText(occasion.heading() + "   ·   " + occasion.headingOf(event) + "   ·   "
				+ plan.tables().size() + " tables, " + plan.places() + " places, " + head + ",   " + people);
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
