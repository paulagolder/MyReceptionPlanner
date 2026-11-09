package org.lerot.myreceptionplanner.reports;

import org.lerot.myreceptionplanner.model.Contact;
import org.lerot.myreceptionplanner.model.Messages;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * A message to go to some parties by hand, as a list: who, every way to
 * reach their lead, and a box to tick when done; then each party's message
 * in full, to read out or copy. For contacting guests in person, or trying
 * out the messages without sending anything.
 */
public class ContactList extends Report
{
	public ContactList(Occasion occasion, String what, Map<Party, Messages.Message> messages)
	{
		super(occasion.heading(), what + ": to contact by hand, " + Occasion.DATE.format(LocalDate.now()));
		emblem = occasion.getEmblem();

		List<List<String>> rows = new ArrayList<>();
		for (Map.Entry<Party, Messages.Message> m : messages.entrySet())
		{
			Party p = m.getKey();
			Contact c = p.getLead() == null ? new Contact() : p.getLead().getContact();
			List<String> phones = new ArrayList<>();
			if (!c.getMobile().isEmpty()) phones.add(c.getMobile() + " (mobile)");
			if (!c.getPhone().isEmpty()) phones.add(c.getPhone());
			if (!c.getWhatsApp().isEmpty() && !c.getWhatsApp().equals(c.getMobile())) phones.add(c.getWhatsApp() + " (WhatsApp)");
			rows.add(List.of("[   ]", p.getName(), p.getLead() == null ? "" : p.getLead().getName(), c.getEmail(),
					String.join(", ", phones), c.getAddress().replace("\n", ", ")));
		}
		int n = messages.size();
		sections.add(new Section("Who to contact", List.of("Done", "Party", "Lead", "Email", "Phone", "Address"), rows,
				n + (n == 1 ? " party" : " parties") + "; noted as sent \"by list\" on " + Occasion.DATE.format(LocalDate.now())));

		for (Map.Entry<Party, Messages.Message> m : messages.entrySet())
		{
			String subject = m.getValue().subject();
			sections.add(new Section(m.getKey().getName() + (subject.isEmpty() ? "" : ": " + subject), List.of(),
					List.of(List.of(m.getValue().body())), ""));
		}
	}
}
