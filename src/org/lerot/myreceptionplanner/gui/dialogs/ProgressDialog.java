package org.lerot.myreceptionplanner.gui.dialogs;

import net.miginfocom.swing.MigLayout;
import org.lerot.myreceptionplanner.gui.Theme;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Progress;
import org.lerot.myreceptionplanner.model.Progress.Stage;
import org.lerot.myreceptionplanner.model.Progress.State;
import org.lerot.myreceptionplanner.model.Progress.Step;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/*
 * How far the planning has got: the occasion's stages down the page, then a
 * grid of the event stages with a column for each event. Each stage has a
 * mark (done, part done, not started, not needed), a bar, a count and what is
 * left. Click a stage to go and do it; the screen then shows the new state,
 * unless the stage is done on the plan itself, when it closes.
 */
public class ProgressDialog
{
	// does the stage (for the event, or null); true if the screen should close
	public interface Actions
	{
		boolean open(Step step, Event event);
	}

	private static final Color AMBER = new Color(0xD08A10), WARN = new Color(0xC0392B);

	private final Occasion occasion;
	private final Actions actions;
	private final JPanel content = new JPanel(new BorderLayout());
	private JDialog dialog;

	public static void show(Component parent, Occasion occasion, Actions actions)
	{
		new ProgressDialog(occasion, actions).run(parent);
	}

	private ProgressDialog(Occasion occasion, Actions actions)
	{
		this.occasion = occasion;
		this.actions = actions;
	}

	private void run(Component parent)
	{
		JButton close = new JButton("Close");
		close.addActionListener(e -> dialog.dispose());
		JLabel hint = new JLabel("Click a stage to work on it.");
		hint.setForeground(Theme.EDGE);
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(hint, BorderLayout.WEST);
		bottom.add(close, BorderLayout.EAST);
		JPanel p = new JPanel(new BorderLayout(0, 10));
		p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		p.add(scroll, BorderLayout.CENTER);
		p.add(bottom, BorderLayout.SOUTH);

		dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Progress: " + occasion.getName(),
				Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setContentPane(p);
		dialog.getRootPane().setDefaultButton(close);
		fill();
		dialog.pack();
		Dimension size = dialog.getSize();
		Rectangle screen = parent.getGraphicsConfiguration().getBounds();
		dialog.setSize(Math.min(size.width + 20, screen.width - 80), Math.min(size.height, screen.height - 80));
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private void fill()
	{
		JPanel grid = new JPanel(new MigLayout("insets 0, gapy 2", "[]8[]12[]8[]12[grow]"));
		grid.add(heading("The occasion"), "span, growx, wrap");
		for (Stage s : Progress.ofOccasion(occasion))
		{
			grid.add(new JLabel(mark(s)));
			grid.add(link(bold(s.step().label), s, null));
			grid.add(bar(s), "w 110!");
			grid.add(count(s));
			grid.add(note(s, 0), "wrap");
		}

		List<Event> events = occasion.events();
		StringBuilder cols = new StringBuilder("[]16");
		events.forEach(e -> cols.append("[200::280,fill]16"));
		JPanel eventGrid = new JPanel(new MigLayout("insets 0, gapy 6", cols.toString()));
		eventGrid.add(new JLabel());
		for (int i = 0; i < events.size(); i++)
		{
			Event e = events.get(i);
			JLabel name = new JLabel("<html><b>" + escape(e.getName()) + "</b><br><span style='color:#8A8A8E'>"
					+ escape(e.getKind().toString()) + "</span></html>");
			name.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.GRID));
			eventGrid.add(name, i == events.size() - 1 ? "wrap" : "");
		}
		for (Step step : Step.EVENT_STEPS)
		{
			eventGrid.add(new JLabel(bold(step.label)), "top");
			for (int i = 0; i < events.size(); i++)
			{
				Event e = events.get(i);
				Stage s = Progress.ofEvent(occasion, e).get(Step.EVENT_STEPS.indexOf(step));
				eventGrid.add(cell(s, e), "top" + (i == events.size() - 1 ? ", wrap" : ""));
			}
		}
		grid.add(heading("The events"), "span, growx, gaptop 14, wrap");
		grid.add(eventGrid, "span, wrap");

		content.removeAll();
		content.add(grid, BorderLayout.NORTH);
		content.revalidate();
		content.repaint();
	}

	// an event's stage: mark, bar and count, the note below
	private JComponent cell(Stage s, Event e)
	{
		JPanel c = new JPanel(new MigLayout("insets 4, gap 6 2", "[][110!][]", "[][]"));
		c.add(new JLabel(mark(s)));
		c.add(bar(s), "growx");
		c.add(count(s), "wrap");
		c.add(note(s, 200), "span");
		clickable(c, s, e);
		return c;
	}

	private JComponent link(String text, Stage s, Event e)
	{
		JLabel l = new JLabel(text);
		clickable(l, s, e);
		return l;
	}

	private void clickable(JComponent c, Stage s, Event e)
	{
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		c.setToolTipText("Click to work on " + s.step().label.toLowerCase() + (e == null ? "" : " for " + e.getName()));
		MouseAdapter click = new MouseAdapter()
		{
			public void mouseClicked(MouseEvent m)
			{
				if (actions.open(s.step(), e)) dialog.dispose();
				else fill();
			}
		};
		c.addMouseListener(click);
		for (Component child : c.getComponents())
			child.addMouseListener(click);
	}

	private static JComponent bar(Stage s)
	{
		JProgressBar b = new JProgressBar(0, Math.max(1, s.total()));
		b.setValue(s.state() == State.DONE ? Math.max(1, s.total()) : s.done());
		b.setPreferredSize(new Dimension(110, 8));
		b.setForeground(s.warn() ? WARN : s.state() == State.DONE ? Theme.FIRST : AMBER);
		b.setVisible(s.state() != State.NOT_NEEDED);
		return b;
	}

	private static JComponent count(Stage s)
	{
		JLabel l = new JLabel(s.count());
		l.setForeground(Theme.EDGE);
		return l;
	}

	private static JComponent note(Stage s, int width)
	{
		String text = escape(s.note());
		JLabel l = new JLabel(width > 0 ? "<html><div style='width:" + width + "px'>" + text + "</div></html>" : text);
		l.setForeground(s.warn() ? WARN : s.state() == State.NOT_NEEDED ? Theme.EDGE : Theme.TEXT);
		return l;
	}

	private static JComponent heading(String text)
	{
		JLabel l = new JLabel("<html><b>" + text + "</b></html>");
		l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() + 2f));
		l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.GRID),
				BorderFactory.createEmptyBorder(0, 0, 3, 0)));
		return l;
	}

	private static String bold(String text)
	{
		return "<html><b>" + escape(text) + "</b></html>";
	}

	// a filled green tick for done, an amber half for part done, a grey ring
	// for not started, a dash for not needed; red when something is wrong
	private static Icon mark(Stage s)
	{
		return new Icon()
		{
			public int getIconWidth() { return 14; }

			public int getIconHeight() { return 14; }

			public void paintIcon(Component c, Graphics g0, int x, int y)
			{
				Graphics2D g = (Graphics2D) g0.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.translate(x, y);
				Color colour = s.warn() ? WARN : switch (s.state())
				{
					case DONE -> Theme.FIRST;
					case PART_DONE -> AMBER;
					default -> Theme.OFF;
				};
				g.setColor(colour);
				g.setStroke(new BasicStroke(1.6f));
				switch (s.state())
				{
					case DONE ->
					{
						g.fillOval(0, 0, 13, 13);
						g.setColor(Color.WHITE);
						g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g.drawPolyline(new int[] { 3, 6, 10 }, new int[] { 7, 10, 4 }, 3);
					}
					case PART_DONE ->
					{
						g.drawOval(1, 1, 11, 11);
						g.fillArc(1, 1, 11, 11, 90, -180);
					}
					case NOT_STARTED -> g.drawOval(1, 1, 11, 11);
					case NOT_NEEDED -> g.drawLine(3, 7, 10, 7);
				}
				g.dispose();
			}
		};
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
