package org.lerot.myreceptionplanner.gui;

import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Seating;
import org.lerot.myreceptionplanner.model.tables.Altar;
import org.lerot.myreceptionplanner.model.tables.Pew;
import org.lerot.myreceptionplanner.model.tables.Plan;
import org.lerot.myreceptionplanner.model.tables.Table;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Optional;

/*
 * The floor plan. Drag a table to move it, drag the round handle above the
 * selected table to turn it (in steps of 15 degrees; hold Shift for any angle),
 * double-click a table to edit it. With a table selected: arrow keys nudge it,
 * R and Shift+R turn it, Delete removes it. Ctrl + mouse wheel zooms.
 * Right-click a chair to make it seat 1, to disable it, or to reserve it or
 * make it open access; right-click a table for its other actions. Seat 1 is
 * drawn green, disabled chairs greyed out, open seats white and reserved
 * seats filled.
 *
 * That is layout mode. In seating mode the tables stay put: clicking a table
 * or one of its chairs chooses it, for the seating panel to show. Each seat
 * shows its number, and the chosen seat has a ring round it.
 * In both modes a seated guest's chair carries their mark (see Marks).
 */
public class PlanPanel extends JPanel
{
	public enum Mode { LAYOUT, SEATING }

	public interface Listener
	{
		void selectionChanged(Table selected);

		// in seating mode, a click on a table (chair -1) or one of its chairs
		void chairChosen(Table table, int chair);

		void planChanged();

		void editRequested(Table table);

		void deleteRequested(Table table);

		void duplicateRequested(Table table);

		// pushed together: combine the two, or take the table out of those it is combined with
		void combineRequested(Table table, Table with);

		void separateRequested(Table table);
	}

	private static final double GRID = 100;         // cm between grid lines
	private static final double MARGIN = 300;       // cm of floor beyond the tables
	private static final double MIN_FLOOR_W = 1500;
	private static final double MIN_FLOOR_H = 1000;
	private static final double HANDLE_DISTANCE = 30; // cm beyond the chairs
	private static final double HANDLE_RADIUS = 7;    // pixels
	public static final double ROTATE_STEP = 15;
	private static final double NUDGE = 10;

	private final Plan plan;
	private final Seating seating;
	private final Listener listener;
	private Mode mode = Mode.LAYOUT;
	private int chosenChair = -1;  // in seating mode, at the selected table
	private double scale = 0.6;   // pixels per cm
	private Table selected;

	// the drag in progress
	private enum Drag { NONE, MOVE, TURN }
	private Drag drag = Drag.NONE;
	private Point2D grabOffset;
	private boolean dragged;

	public PlanPanel(Plan plan, Seating seating, Listener listener)
	{
		this.plan = plan;
		this.seating = seating;
		this.listener = listener;
		setBackground(Theme.PAGE);
		setFocusable(true);
		MouseAdapter mouse = new Mouse();
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
		addKeyListener(new Keys());
	}

	public Table getSelected() { return selected; }

	public Mode getMode() { return mode; }

	public void setMode(Mode mode)
	{
		this.mode = mode;
		drag = Drag.NONE;
		chosenChair = -1;
		setCursor(Cursor.getDefaultCursor());
		repaint();
	}

	// selects the table, rings the chair (-1 for none) and scrolls them into view
	public void showChair(Table table, int chair)
	{
		select(table);
		chosenChair = chair;
		if (table != null)
		{
			Rectangle2D b = table.bounds();
			scrollRectToVisible(new Rectangle((int) (b.getX() * scale), (int) (b.getY() * scale),
					(int) (b.getWidth() * scale), (int) (b.getHeight() * scale)));
		}
		repaint();
	}

	public void select(Table table)
	{
		if (table == selected) return;
		selected = table;
		chosenChair = -1;
		listener.selectionChanged(table);
		repaint();
	}

	// call after tables are added, removed or changed
	public void planUpdated()
	{
		if (selected != null && !plan.tables().contains(selected)) select(null);
		revalidate();
		repaint();
	}

	// the middle of what can be seen, in floor cm, for placing a new table
	public Point2D visibleCentre()
	{
		Rectangle r = getVisibleRect();
		if (r.isEmpty()) return new Point2D.Double(MIN_FLOOR_W / 2, MIN_FLOOR_H / 2);
		return toFloor(new Point2D.Double(r.getCenterX(), r.getCenterY()));
	}

	@Override
	public Dimension getPreferredSize()
	{
		double w = MIN_FLOOR_W, h = MIN_FLOOR_H;
		for (Table t : plan.tables())
		{
			Rectangle2D b = t.bounds();
			w = Math.max(w, b.getMaxX() + MARGIN);
			h = Math.max(h, b.getMaxY() + MARGIN);
		}
		return new Dimension((int) (w * scale), (int) (h * scale));
	}

	private Point2D toFloor(Point2D screen)
	{
		return new Point2D.Double(screen.getX() / scale, screen.getY() / scale);
	}

	// ---- drawing ----

	@Override
	protected void paintComponent(Graphics g0)
	{
		super.paintComponent(g0);
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		paintGrid(g);
		java.util.List<Plan.Overlap> overlaps = plan.overlaps();
		clashing = new java.util.HashSet<>();
		for (Plan.Overlap o : overlaps)
		{
			clashing.add(o.a());
			clashing.add(o.b());
		}
		for (Table t : plan.tables())
			paintTable(g, t);
		paintOverlaps(g, overlaps);
		if (selected != null && mode == Mode.LAYOUT) paintHandle(g, selected);
		g.dispose();
	}

	// tables standing on each other: where, in red over both
	private java.util.Set<Table> clashing = java.util.Set.of();

	private void paintOverlaps(Graphics2D g0, java.util.List<Plan.Overlap> overlaps)
	{
		if (overlaps.isEmpty()) return;
		Graphics2D g = (Graphics2D) g0.create();
		g.scale(scale, scale);
		g.setColor(new Color(Theme.CLASH.getRed(), Theme.CLASH.getGreen(), Theme.CLASH.getBlue(), 90));
		for (Plan.Overlap o : overlaps)
			g.fill(o.where());
		g.dispose();
	}

	private void paintGrid(Graphics2D g)
	{
		g.setColor(Theme.GRID);
		double step = GRID * scale;
		for (double x = step; x < getWidth(); x += step)
			g.draw(new Line2D.Double(x, 0, x, getHeight()));
		for (double y = step; y < getHeight(); y += step)
			g.draw(new Line2D.Double(0, y, getWidth(), y));
	}

	private void paintTable(Graphics2D g0, Table t)
	{
		Graphics2D g = (Graphics2D) g0.create();
		g.scale(scale, scale);
		g.transform(t.toFloor());
		double r = Table.SEAT_RADIUS;
		var seats = t.seats();
		for (int i = 0; i < seats.size(); i++)
		{
			Point2D s = seats.get(i);
			Shape chair = new Ellipse2D.Double(s.getX() - r, s.getY() - r, 2 * r, 2 * r);
			if (t.isDisabled(i))
			{
				// greyed out: the floor showing through a dashed outline
				g.setColor(Theme.PAGE);
				g.fill(chair);
				g.setColor(Theme.OFF);
				g.setStroke(new BasicStroke((float) (1.2 / scale), BasicStroke.CAP_BUTT,
						BasicStroke.JOIN_ROUND, 1f, new float[] { 4f, 4f }, 0f));
				g.draw(chair);
			} else if (i == t.getFirstSeat())
			{
				g.setColor(t.isOpen(i) ? Theme.TABLE : Theme.FIRST_FILL);
				g.fill(chair);
				g.setColor(Theme.FIRST);
				g.setStroke(new BasicStroke((float) (2.5 / scale)));
				g.draw(chair);
			} else
			{
				// open seats white, reserved seats filled
				g.setColor(t.isOpen(i) ? Theme.TABLE : Theme.SEAT);
				g.fill(chair);
				g.setColor(Theme.EDGE);
				g.setStroke(new BasicStroke((float) (1.2 / scale)));
				g.draw(chair);
			}
		}
		Shape top = t.top();
		g.setColor(t instanceof Pew ? Theme.SEAT : t instanceof Altar ? Theme.ALTAR : Theme.TABLE);
		g.fill(top);
		boolean partner = selected != null && t != selected && t.getGroup() != 0 && t.getGroup() == selected.getGroup();
		Color edge = clashing.contains(t) ? Theme.CLASH : t == selected || partner ? Theme.ACCENT : t.isHead() ? Theme.HEAD : Theme.EDGE;
		float width = t == selected || t.isHead() || clashing.contains(t) ? 3f : partner ? 2f : 1.5f;
		g.setColor(edge);
		g.setStroke(new BasicStroke((float) (width / scale)));
		g.draw(top);
		g.dispose();
		paintName(g0, t);
		paintSeats(g0, t);
	}

	// a mark on each chair with a guest in it; in seating mode the number of
	// each empty seat, and a ring round the chosen one
	private void paintSeats(Graphics2D g0, Table t)
	{
		Graphics2D g = (Graphics2D) g0.create();
		double r = Table.SEAT_RADIUS * scale;
		g.setFont(getFont().deriveFont((float) Math.max(9, Math.min(14, r * 0.9))));
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < t.places(); i++)
		{
			Point2D f = t.seatOnFloor(i);
			double x = f.getX() * scale, y = f.getY() * scale;
			Optional<Guest> guest = seating.guestAt(t.getId(), i);
			if (guest.isPresent())
				Marks.paint(g, guest.get().getGender(), GuestList.colourOf(guest.get()), x, y, r * 0.55);
			else if (mode == Mode.SEATING && t.isReserved(i))
			{
				String n = Integer.toString(t.seatNumber(i));
				g.setColor(Theme.EDGE);
				g.drawString(n, (float) (x - fm.stringWidth(n) / 2.0),
						(float) (y + (fm.getAscent() - fm.getDescent()) / 2.0));
			}
			if (mode == Mode.SEATING && t == selected && i == chosenChair)
			{
				g.setColor(Theme.ACCENT);
				g.setStroke(new BasicStroke(2.5f));
				g.draw(new Ellipse2D.Double(x - r - 3, y - r - 3, 2 * r + 6, 2 * r + 6));
			}
		}
		g.dispose();
	}

	// the name upright in the middle of the table, whichever way it is turned;
	// a pew's on one line just behind it
	private void paintName(Graphics2D g, Table t)
	{
		if (t instanceof Pew)
		{
			g.setFont(getFont().deriveFont(Font.PLAIN, Math.max(9f, getFont().getSize2D() - 1)));
			FontMetrics fm = g.getFontMetrics();
			String text = plan.label(t) + "  ·  " + placesText(t);
			double behind = t.top().getBounds2D().getMaxY();
			Point2D at = t.toFloor().transform(new Point2D.Double(0, behind + 3 + fm.getAscent() / scale), null);
			g.setColor(Theme.EDGE);
			g.drawString(text, (float) (at.getX() * scale - fm.stringWidth(text) / 2.0), (float) (at.getY() * scale));
			return;
		}
		Font font = getFont().deriveFont(Font.BOLD);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		String[] lines = t instanceof Altar ? new String[] { plan.label(t) }
				: new String[] { plan.label(t), t.isHead() && t.hasName() ? "head table" : placesText(t) };
		double cx = t.getX() * scale, cy = t.getY() * scale;
		double top = cy - lines.length * fm.getHeight() / 2.0 + fm.getAscent();
		for (int i = 0; i < lines.length; i++)
		{
			g.setColor(i == 0 ? Theme.TEXT : Theme.EDGE);
			if (i == 1) g.setFont(font.deriveFont(Font.PLAIN));
			int w = g.getFontMetrics().stringWidth(lines[i]);
			g.drawString(lines[i], (float) (cx - w / 2.0), (float) (top + i * fm.getHeight()));
		}
	}

	// "8 places", "8 places (2 off)", "10 seats: 2 reserved, 8 open"
	private static String placesText(Table t)
	{
		int off = t.places() - t.usablePlaces();
		int open = t.getOpenSeats().size();
		String what = t instanceof Pew ? " seats" : " places";
		if (open == 0) return t.usablePlaces() + (t instanceof Pew ? " reserved" : what) + (off > 0 ? " (" + off + " off)" : "");
		if (open == t.usablePlaces()) return open + " open" + (off > 0 ? " (" + off + " off)" : "");
		return t.reservedPlaces() + " reserved, " + open + " open" + (off > 0 ? ", " + off + " off" : "");
	}

	private void paintHandle(Graphics2D g, Table t)
	{
		Point2D handle = handleOnScreen(t);
		Point2D floor = t.toFloor().transform(new Point2D.Double(0, -t.reach()), null);
		Point2D start = new Point2D.Double(floor.getX() * scale, floor.getY() * scale);
		g.setColor(Theme.ACCENT);
		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
				1f, new float[] { 4f, 4f }, 0f));
		g.draw(new Line2D.Double(start, handle));
		double r = HANDLE_RADIUS;
		Shape knob = new Ellipse2D.Double(handle.getX() - r, handle.getY() - r, 2 * r, 2 * r);
		g.setColor(Color.WHITE);
		g.fill(knob);
		g.setColor(Theme.ACCENT);
		g.setStroke(new BasicStroke(2f));
		g.draw(knob);
	}

	// straight "up" from the table as it is turned, just beyond its chairs
	private Point2D handleOnScreen(Table t)
	{
		Point2D local = new Point2D.Double(0, -(t.reach() + HANDLE_DISTANCE));
		Point2D floor = t.toFloor().transform(local, null);
		return new Point2D.Double(floor.getX() * scale, floor.getY() * scale);
	}

	// ---- mouse and keys ----

	private Table tableAt(Point2D floor)
	{
		var tables = plan.tables();
		for (int i = tables.size() - 1; i >= 0; i--)
		{
			if (tables.get(i).contains(floor)) return tables.get(i);
		}
		return null;
	}

	private class Mouse extends MouseAdapter
	{
		@Override
		public void mousePressed(MouseEvent e)
		{
			requestFocusInWindow();
			dragged = false;
			if (mode == Mode.SEATING)
			{
				if (SwingUtilities.isLeftMouseButton(e)) choose(e);
				return;
			}
			if (e.isPopupTrigger())
			{
				showMenu(e);
				return;
			}
			if (!SwingUtilities.isLeftMouseButton(e)) return;
			if (selected != null && handleOnScreen(selected).distance(e.getPoint()) <= HANDLE_RADIUS + 3)
			{
				drag = Drag.TURN;
				return;
			}
			Point2D floor = toFloor(e.getPoint());
			Table t = tableAt(floor);
			select(t);
			if (t == null)
			{
				drag = Drag.NONE;
				return;
			}
			plan.bringToFront(t);
			drag = Drag.MOVE;
			grabOffset = new Point2D.Double(t.getX() - floor.getX(), t.getY() - floor.getY());
			if (e.getClickCount() == 2) listener.editRequested(t);
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			if (selected == null || drag == Drag.NONE) return;
			Point2D floor = toFloor(e.getPoint());
			if (drag == Drag.MOVE)
			{
				double x = Math.max(0, floor.getX() + grabOffset.getX());
				double y = Math.max(0, floor.getY() + grabOffset.getY());
				plan.moveGroup(selected, Math.round(x) - selected.getX(), Math.round(y) - selected.getY());
			} else
			{
				double dx = floor.getX() - selected.getX();
				double dy = floor.getY() - selected.getY();
				double angle = Math.toDegrees(Math.atan2(dx, -dy));
				if (!e.isShiftDown()) angle = Math.round(angle / ROTATE_STEP) * ROTATE_STEP;
				double by = ((Math.round(angle) - selected.getRotation()) % 360 + 540) % 360 - 180;
				if (by != 0) plan.turnGroup(selected, by);
			}
			dragged = true;
			revalidate();
			repaint();
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			if (e.isPopupTrigger() && mode == Mode.LAYOUT) showMenu(e);  // where the menu comes on release
			if (dragged) listener.planChanged();
			drag = Drag.NONE;
			dragged = false;
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
			if (mode == Mode.SEATING)
			{
				setCursor(tableAt(toFloor(e.getPoint())) != null
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
				return;
			}
			boolean onHandle = selected != null
					&& handleOnScreen(selected).distance(e.getPoint()) <= HANDLE_RADIUS + 3;
			if (onHandle) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			else if (tableAt(toFloor(e.getPoint())) != null)
				setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			else setCursor(Cursor.getDefaultCursor());
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e)
		{
			if (!e.isControlDown())
			{
				// let the scroll pane scroll
				Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, PlanPanel.this);
				if (scroll != null)
					scroll.dispatchEvent(SwingUtilities.convertMouseEvent(PlanPanel.this, e, scroll));
				return;
			}
			zoom(e.getWheelRotation() < 0 ? 1.2 : 1 / 1.2);
		}
	}

	// seating mode: choose the table and chair clicked
	private void choose(MouseEvent e)
	{
		Point2D floor = toFloor(e.getPoint());
		Table t = tableAt(floor);
		select(t);
		chosenChair = t == null ? -1 : t.chairAt(floor);
		listener.chairChosen(t, chosenChair);
		repaint();
	}

	// the right-click menu: for a chair, seat 1 and disabling; for any part
	// of a table, editing, turning and removing it
	private void showMenu(MouseEvent e)
	{
		drag = Drag.NONE;
		Point2D floor = toFloor(e.getPoint());
		Table t = tableAt(floor);
		select(t);
		if (t == null) return;
		JPopupMenu menu = new JPopupMenu();
		int chair = t.chairAt(floor);
		if (chair >= 0)
		{
			boolean first = chair == t.getFirstSeat();
			JMenuItem makeFirst = menu.add("Make this seat 1");
			makeFirst.setEnabled(!first);
			makeFirst.addActionListener(a -> seatChanged(() -> t.setFirstSeat(chair)));
			boolean off = t.isDisabled(chair);
			boolean taken = seating.guestAt(t.getId(), chair).isPresent();
			JMenuItem disable = menu.add(off ? "Enable this seat" : "Disable this seat");
			disable.setEnabled(!first && !taken);
			if (first) disable.setToolTipText("Seat 1 cannot be disabled");
			else if (taken) disable.setToolTipText("Someone is seated here");
			disable.addActionListener(a -> seatChanged(() -> t.setDisabled(chair, !off)));
			if (!off)
			{
				boolean open = t.isOpen(chair);
				JMenuItem status = menu.add(open ? "Reserve this seat" : "Make this seat open access");
				status.setEnabled(open || !taken);
				if (taken) status.setToolTipText("Someone is seated here");
				status.addActionListener(a -> seatChanged(() -> t.setOpen(chair, !open)));
			}
			menu.addSeparator();
		}
		menu.add("Edit…").addActionListener(a -> listener.editRequested(t));
		menu.add("Duplicate").addActionListener(a -> listener.duplicateRequested(t));
		menu.add("Turn left 15°").addActionListener(a -> turn(-ROTATE_STEP));
		menu.add("Turn right 15°").addActionListener(a -> turn(ROTATE_STEP));
		JMenuItem enableAll = menu.add("Enable all seats");
		enableAll.setEnabled(!t.getDisabledSeats().isEmpty());
		enableAll.addActionListener(a -> enableAllSeats());
		JMenuItem reserveAll = menu.add("Reserve all seats");
		reserveAll.setEnabled(!t.getOpenSeats().isEmpty());
		reserveAll.addActionListener(a -> seatChanged(() -> t.setAllOpen(false)));
		boolean anyone = !seating.seatedAt(t.getId()).isEmpty();
		JMenuItem openAll = menu.add("Make all seats open access");
		openAll.setEnabled(t.reservedPlaces() > 0 && !anyone);
		if (anyone) openAll.setToolTipText("Guests are seated here");
		openAll.addActionListener(a -> seatChanged(() -> t.setAllOpen(true)));
		menu.add("Delete").addActionListener(a -> listener.deleteRequested(t));
		java.util.List<JMenuItem> combining = combineItems(t);
		if (!combining.isEmpty())
		{
			menu.addSeparator();
			combining.forEach(menu::add);
		}
		menu.show(this, e.getX(), e.getY());
	}

	// "Combine with Table 2" for each table pushed against it; "Separate from …" if combined
	public java.util.List<JMenuItem> combineItems(Table t)
	{
		java.util.List<JMenuItem> items = new java.util.ArrayList<>();
		if (t == null) return items;
		java.util.List<Table> pushable = new java.util.ArrayList<>(plan.touching(t));
		pushable.addAll(plan.lyingOver(t));
		for (Table o : pushable)
		{
			JMenuItem push = new JMenuItem("Push together with " + plan.label(o));
			push.setToolTipText("Slides it until its top just meets " + plan.label(o) + "'s: back off it if over it, up to it if apart; along its length, so at an angle its corner meets the edge");
			push.addActionListener(a ->
			{
				if (plan.pushTogether(t, o))
				{
					listener.planChanged();
					planUpdated();
				}
			});
			items.add(push);
		}
		for (Table o : plan.touching(t))
		{
			JMenuItem item = new JMenuItem("Combine with " + plan.label(o));
			item.setToolTipText("Pushed together: they move and turn as one, each keeping its own name, number and guests; the chairs they block are turned off");
			item.addActionListener(a -> listener.combineRequested(t, o));
			items.add(item);
		}
		java.util.List<Table> others = plan.groupOf(t).stream().filter(o -> o != t).toList();
		if (!others.isEmpty())
		{
			JMenuItem item = new JMenuItem("Separate from " + (others.size() == 1 ? plan.label(others.get(0)) : "the others"));
			item.setToolTipText("No longer combined: it moves on its own, and the chairs the join blocked come back");
			item.addActionListener(a -> listener.separateRequested(t));
			items.add(item);
		}
		return items;
	}

	private void seatChanged(Runnable change)
	{
		change.run();
		listener.planChanged();
		planUpdated();
	}

	public void enableAllSeats()
	{
		if (selected == null) return;
		Table t = selected;
		seatChanged(() ->
		{
			t.enableAllSeats();
			plan.reblock(t);  // those its partners block stay off
		});
	}

	// turns the selected table clockwise by this many degrees (negative: anticlockwise)
	public void turn(double degrees)
	{
		if (selected == null) return;
		plan.turnGroup(selected, degrees);
		listener.planChanged();
		planUpdated();
	}

	public void zoom(double factor)
	{
		scale = Math.max(0.2, Math.min(3, scale * factor));
		revalidate();
		repaint();
	}

	private class Keys extends KeyAdapter
	{
		@Override
		public void keyPressed(KeyEvent e)
		{
			if (selected == null || mode == Mode.SEATING) return;
			switch (e.getKeyCode())
			{
				case KeyEvent.VK_LEFT -> nudge(-NUDGE, 0);
				case KeyEvent.VK_RIGHT -> nudge(NUDGE, 0);
				case KeyEvent.VK_UP -> nudge(0, -NUDGE);
				case KeyEvent.VK_DOWN -> nudge(0, NUDGE);
				case KeyEvent.VK_R -> turn(e.isShiftDown() ? -ROTATE_STEP : ROTATE_STEP);
				case KeyEvent.VK_D ->
				{
					if (!e.isControlDown()) return;
					listener.duplicateRequested(selected);
				}
				case KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> listener.deleteRequested(selected);
				case KeyEvent.VK_ENTER -> listener.editRequested(selected);
				default -> { return; }
			}
			e.consume();
		}

		private void nudge(double dx, double dy)
		{
			plan.moveGroup(selected, dx, dy);
			listener.planChanged();
			planUpdated();
		}
	}
}
