package org.lerot.myreceptionplanner.model;

/*
 * Where a guest stands with one event: not invited; to be invited (decided,
 * not yet sent); invited (the invitation sent); accepted; or declined (or
 * cancelled). Those to be invited, invited or accepted are expected: they
 * can be seated, are catered for and count against the capacity. Once the
 * invitation is sent (invited or accepted) the guest cannot be taken off the
 * list until it is declined or cancelled.
 */
public enum Invitation
{
	NONE("", "Not invited"),
	TO_INVITE("decided", "To be invited"),
	INVITED("invited", "Invited (sent)"),
	ACCEPTED("accepted", "Accepted"),
	DECLINED("declined", "Declined or cancelled");

	private final String code;   // in the event's file
	private final String label;

	Invitation(String code, String label)
	{
		this.code = code;
		this.label = label;
	}

	public String code() { return code; }

	@Override
	public String toString() { return label; }

	public boolean expected() { return this == TO_INVITE || this == INVITED || this == ACCEPTED; }

	// the invitation has gone out and stands
	public boolean sent() { return this == INVITED || this == ACCEPTED; }
}
