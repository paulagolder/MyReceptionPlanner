package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Table;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/*
 * The floor plan. Drag a table to move it, drag the round handle above the
 * selected table to turn it (in steps of 15 degrees; hold Shift for any angle),
 * double-click a table to edit it. With a table selected: arrow keys nudge it,
 * R and Shift+R turn it, Delete removes it. Ctrl + mouse wheel zooms.
 * Right-click a chair to make it seat 1 or to disable it; right-click a table
 * for its other actions. Seat 1 is drawn green and disabled chairs greyed out.
 */
public class PlanPanel extends JPanel
{
	public interface Listener
	{
		void selectionChanged(Table selected);

		void planChanged();

		void editRequested(Table table);

		void deleteRequested(Table table);
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
	private final Listener listener;
	private double scale = 0.6;   // pixels per cm
	private Table selected;

	// the drag in progress
	private enum Drag { NONE, MOVE, TURN }
	private Drag drag = Drag.NONE;
	private Point2D grabOffset;
	private boolean dragged;

	public PlanPanel(Plan plan, Listener listener)
	{
		this.plan = plan;
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

	public void select(Table table)
	{
		if (table == selected) return;
		selected = table;
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
		for (Table t : plan.tables())
			paintTable(g, t);
		if (selected != null) paintHandle(g, selected);
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
				g.setColor(Theme.FIRST_FILL);
				g.fill(chair);
				g.setColor(Theme.FIRST);
				g.setStroke(new BasicStroke((float) (2.5 / scale)));
				g.draw(chair);
			} else
			{
				g.setColor(Theme.SEAT);
				g.fill(chair);
				g.setColor(Theme.EDGE);
				g.setStroke(new BasicStroke((float) (1.2 / scale)));
				g.draw(chair);
			}
		}
		Shape top = t.top();
		g.setColor(Theme.TABLE);
		g.fill(top);
		Color edge = t == selected ? Theme.ACCENT : t.isHead() ? Theme.HEAD : Theme.EDGE;
		float width = t == selected || t.isHead() ? 3f : 1.5f;
		g.setColor(edge);
		g.setStroke(new BasicStroke((float) (width / scale)));
		g.draw(top);
		g.dispose();
		paintName(g0, t);
	}

	// the name upright in the middle of the table, whichever way it is turned
	private void paintName(Graphics2D g, Table t)
	{
		Font font = getFont().deriveFont(Font.BOLD);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		String[] lines = t.isHead()
				? new String[] { t.getName(), "head table" }
				: new String[] { t.getName(), placesText(t) };
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

	private static String placesText(Table t)
	{
		int off = t.places() - t.usablePlaces();
		return t.usablePlaces() + " places" + (off > 0 ? " (" + off + " off)" : "");
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
				selected.moveTo(Math.round(x), Math.round(y));
			} else
			{
				double dx = floor.getX() - selected.getX();
				double dy = floor.getY() - selected.getY();
				double angle = Math.toDegrees(Math.atan2(dx, -dy));
				if (!e.isShiftDown()) angle = Math.round(angle / ROTATE_STEP) * ROTATE_STEP;
				selected.setRotation(Math.round(angle));
			}
			dragged = true;
			revalidate();
			repaint();
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			if (e.isPopupTrigger()) showMenu(e);  // where the menu comes on release
			if (dragged) listener.planChanged();
			drag = Drag.NONE;
			dragged = false;
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
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
			JMenuItem disable = menu.add(off ? "Enable this seat" : "Disable this seat");
			disable.setEnabled(!first);
			if (first) disable.setToolTipText("Seat 1 cannot be disabled");
			disable.addActionListener(a -> seatChanged(() -> t.setDisabled(chair, !off)));
			menu.addSeparator();
		}
		menu.add("Edit…").addActionListener(a -> listener.editRequested(t));
		menu.add("Turn left 15°").addActionListener(a -> turn(-ROTATE_STEP));
		menu.add("Turn right 15°").addActionListener(a -> turn(ROTATE_STEP));
		JMenuItem enableAll = menu.add("Enable all seats");
		enableAll.setEnabled(!t.getDisabledSeats().isEmpty());
		enableAll.addActionListener(a -> enableAllSeats());
		menu.add("Delete").addActionListener(a -> listener.deleteRequested(t));
		menu.show(this, e.getX(), e.getY());
	}

	private void seatChanged(Runnable change)
	{
		change.run();
		listener.planChanged();
		planUpdated();
	}

	public void enableAllSeats()
	{
		if (selected != null) seatChanged(selected::enableAllSeats);
	}

	// turns the selected table clockwise by this many degrees (negative: anticlockwise)
	public void turn(double degrees)
	{
		if (selected == null) return;
		selected.setRotation(selected.getRotation() + degrees);
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
			if (selected == null) return;
			switch (e.getKeyCode())
			{
				case KeyEvent.VK_LEFT -> nudge(-NUDGE, 0);
				case KeyEvent.VK_RIGHT -> nudge(NUDGE, 0);
				case KeyEvent.VK_UP -> nudge(0, -NUDGE);
				case KeyEvent.VK_DOWN -> nudge(0, NUDGE);
				case KeyEvent.VK_R -> turn(e.isShiftDown() ? -ROTATE_STEP : ROTATE_STEP);
				case KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> listener.deleteRequested(selected);
				case KeyEvent.VK_ENTER -> listener.editRequested(selected);
				default -> { return; }
			}
			e.consume();
		}

		private void nudge(double dx, double dy)
		{
			selected.moveTo(Math.max(0, selected.getX() + dx), Math.max(0, selected.getY() + dy));
			listener.planChanged();
			planUpdated();
		}
	}
}
