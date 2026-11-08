package org.lerot.myreceptionplanner;

import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.GuestList;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/*
 * The warning when a name typed in looks like someone already on the guest
 * list ("Tom Perks" when there is a "Thomas Perks"): each guest it is like,
 * their party and why. The answer is that they are different people (which
 * is then remembered for those two), to use the guest already there instead
 * (where that makes sense), or to go back.
 */
public final class NameCheck
{
	public enum Answer { DIFFERENT, USE_EXISTING, BACK }

	private NameCheck() {}

	/*
	 * Asks about a name given to an existing guest (self) or to a new one
	 * (self null). DIFFERENT if there is nothing like it, or the user says
	 * they are different people; then, for an existing guest, the pairs are
	 * remembered. Offer the one guest already there when useExisting is set.
	 */
	public static Answer ask(Component parent, GuestList guests, String name, Guest self, boolean useExisting)
	{
		List<GuestList.Likeness> like = guests.like(name, self);
		if (like.isEmpty()) return Answer.DIFFERENT;
		Answer a = ask(parent, name, like, useExisting && like.size() == 1);
		if (a == Answer.DIFFERENT && self != null) remember(guests, self, like);
		return a;
	}

	// they are different people from those they were like
	public static void remember(GuestList guests, Guest self, List<GuestList.Likeness> like)
	{
		for (GuestList.Likeness l : like)
			guests.markDistinct(self, l.b());
	}

	public static Answer ask(Component parent, String name, List<GuestList.Likeness> like, boolean offerExisting)
	{
		StringBuilder html = new StringBuilder("<html><body style='width:360px'><b>")
				.append(escape(name)).append("</b> looks like ")
				.append(like.size() == 1 ? "someone" : "people").append(" already on the guest list:<ul>");
		for (GuestList.Likeness l : like)
			html.append("<li><b>").append(escape(l.b().getName())).append("</b>")
					.append(l.b().getParty() == null ? "" : " (" + escape(l.b().getParty().getName()) + ")")
					.append(" — ").append(escape(l.why())).append("</li>");
		html.append("</ul>Each person should be on the list only once, in one party.</body></html>");
		JLabel message = new JLabel(html.toString());

		String different = like.size() == 1 ? "A different person" : "Different people";
		Object[] options = offerExisting
				? new Object[] { different, "Use " + like.get(0).b().getName(), "Go back" }
				: new Object[] { different, "Go back" };
		int choice = JOptionPane.showOptionDialog(parent, message, "Already on the guest list?",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[options.length - 1]);
		if (choice == 0) return Answer.DIFFERENT;
		if (offerExisting && choice == 1) return Answer.USE_EXISTING;
		return Answer.BACK;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
