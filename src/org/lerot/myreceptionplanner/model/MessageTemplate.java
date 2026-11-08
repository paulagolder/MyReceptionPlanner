package org.lerot.myreceptionplanner.model;

/*
 * A message to send to each party's lead, with fill-ins in braces that
 * Messages completes for each party ({leadfirst}, {events}, {missing} ...).
 */
public class MessageTemplate
{
	private String name;
	private String subject;
	private String body;

	public MessageTemplate(String name, String subject, String body)
	{
		setName(name);
		setSubject(subject);
		setBody(body);
	}

	public String getName() { return name; }

	public void setName(String name) { this.name = name == null || name.isBlank() ? "Message" : name.strip(); }

	public String getSubject() { return subject; }

	public void setSubject(String subject) { this.subject = subject == null ? "" : subject.strip(); }

	public String getBody() { return body; }

	public void setBody(String body) { this.body = body == null ? "" : body.replace("\r\n", "\n").stripTrailing(); }

	@Override
	public String toString() { return name; }
}
