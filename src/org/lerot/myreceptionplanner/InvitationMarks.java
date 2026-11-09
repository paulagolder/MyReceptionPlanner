package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;
import org.lerot.myreceptionplanner.model.Invitation;
import org.lerot.myreceptionplanner.model.Occasion;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

/*
 * Where a guest stands with an event, as a mark: nothing for not invited, a
 * ring for to be invited, a dot for invited (sent), a tick for accepted and a
 * cross for declined or cancelled. Click a mark for a menu to change it.
 * Also the check before anyone is taken off the guest list: not while an
 * invitation sent to them stands, and only after "are you sure?".
 */
public final class InvitationMarks
{
	private InvitationMarks() {}

	private static final Color RING = new Color(0xD08A10), DOT = new Color(0x2F6FDB), TICK = Theme.FIRST,
			CROSS = new Color(0xC0392B);

	public static Icon icon(Invitation state, int size)
	{
		return new Icon()
		{
			public int getIconWidth() { return size; }

			public int getIconHeight() { return size; }

			public void paintIcon(Component c, Graphics g0, int x, int y)
			{
				Graphics2D g = (Graphics2D) g0.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.translate(x, y);
				float s = size;
				g.setStroke(new BasicStroke(s / 7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				switch (state)
				{
					case TO_INVITE ->
					{
						g.setColor(RING);
						g.drawOval(Math.round(s * .2f), Math.round(s * .2f), Math.round(s * .6f), Math.round(s * .6f));
					}
					case INVITED ->
					{
						g.setColor(DOT);
						g.fillOval(Math.round(s * .2f), Math.round(s * .2f), Math.round(s * .6f), Math.round(s * .6f));
					}
					case ACCEPTED ->
					{
						g.setColor(TICK);
						g.drawPolyline(new int[] { Math.round(s * .15f), Math.round(s * .4f), Math.round(s * .85f) },
								new int[] { Math.round(s * .55f), Math.round(s * .8f), Math.round(s * .2f) }, 3);
					}
					case DECLINED ->
					{
						g.setColor(CROSS);
						g.drawLine(Math.round(s * .22f), Math.round(s * .22f), Math.round(s * .78f), Math.round(s * .78f));
						g.drawLine(Math.round(s * .78f), Math.round(s * .22f), Math.round(s * .22f), Math.round(s * .78f));
					}
					default -> { }
				}
				g.dispose();
			}
		};
	}

	// a table cell showing an Invitation as its mark
	public static TableCellRenderer renderer()
	{
		JLabel l = new JLabel();
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setOpaque(true);
		return (t, value, selected, focus, row, col) ->
		{
			Invitation state = value instanceof Invitation i ? i : Invitation.NONE;
			l.setIcon(icon(state, 16));
			l.setToolTipText(state == Invitation.NONE ? "Not invited: click to change" : state + ": click to change");
			l.setBackground(selected ? t.getSelectionBackground() : t.getBackground());
			return l;
		};
	}

	/*
	 * The menu to change where the guest stands with the event, at x, y on
	 * the component; onChange runs after a change. Once the invitation is sent
	 * it can be declined or cancelled, not undone.
	 */
	public static void menu(JComponent on, int x, int y, Occasion occasion, Event event, Guest guest, Runnable onChange)
	{
		Invitation now = event.invitation(guest);
		JPopupMenu menu = new JPopupMenu();
		JLabel title = new JLabel(" " + guest.displayName() + " — " + event.getName());
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		title.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		menu.add(title);
		menu.addSeparator();
		for (Invitation state : Invitation.values())
		{
			JMenuItem item = new JMenuItem(state.toString(), icon(state, 16));
			if (state == now)
			{
				item.setFont(item.getFont().deriveFont(Font.BOLD));
				item.setEnabled(false);
			} else if (state == Invitation.NONE && now.sent())
			{
				item.setEnabled(false);
				item.setToolTipText("The invitation has been sent: mark it declined or cancelled instead");
			}
			item.addActionListener(a ->
			{
				if (!confirm(on, occasion, event, guest, state)) return;
				event.setInvitation(guest, state, occasion.getGuests());
				onChange.run();
			});
			menu.add(item);
		}
		menu.show(on, x, y);
	}

	// asks first when this takes the lead's whole party with them, or seats
	private static boolean confirm(Component parent, Occasion occasion, Event event, Guest g, Invitation state)
	{
		if (state.expected()) return true;
		GuestList guests = occasion.getGuests();
		int others = g.isLead() ? event.invitedIn(g.getParty(), guests) - (event.isInvited(g) ? 1 : 0) : 0;
		boolean seated = event.getSeating().isSeated(g) || (g.isLead()
				&& guests.membersOf(g.getParty()).stream().anyMatch(event.getSeating()::isSeated));
		if (others == 0 && !seated) return true;
		String now = state == Invitation.NONE ? "no longer be invited to " : "be marked as declining ";
		String what = others > 0
				? g.getName() + " leads " + g.getParty().getName() + ", so the whole party (" + (others + 1)
				+ " guests) will " + now + event.getName() + "."
				: g.displayName() + " will " + now + event.getName() + ".";
		return JOptionPane.showConfirmDialog(parent, what + (seated ? "\nTheir seats there are cleared." : ""),
				"Invitation", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
	}

	/*
	 * Whether these may be taken off the guest list: not while an invitation
	 * sent to any of them stands (they are told where); then "are you sure?".
	 */
	public static boolean mayRemove(Component parent, Occasion occasion, List<Guest> these, String question)
	{
		StringBuilder standing = new StringBuilder();
		for (Guest g : these)
		{
			List<Event> at = occasion.invitationsStanding(g);
			if (!at.isEmpty())
				standing.append("\n  • ").append(g.displayName()).append(": ")
						.append(String.join(", ", at.stream().map(Event::getName).toList()));
		}
		if (!standing.isEmpty())
		{
			JOptionPane.showMessageDialog(parent, "An invitation has been sent and still stands for:" + standing
					+ "\n\nMark it declined or cancelled (✘) first.", "Remove", JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		return JOptionPane.showConfirmDialog(parent, question, "Remove", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
	}
}
