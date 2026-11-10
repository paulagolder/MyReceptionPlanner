package org.lerot.myreceptionplanner.model;

/*
 * How to reach a guest (so far, a party's lead guest). It may be typed in or
 * copied once from a contacts file; it is not kept in step with the file after.
 */
public class Contact
{
	private String address = "";   // lines separated by \n
	private String phone = "";
	private String mobile = "";
	private String whatsApp = "";
	private String email = "";
	private String source = "";    // where it was copied from, e.g. "contacts.vcf", or ""

	public String getAddress() { return address; }

	public void setAddress(String address) { this.address = clean(address); }

	public String getPhone() { return phone; }

	public void setPhone(String phone) { this.phone = clean(phone); }

	public String getMobile() { return mobile; }

	public void setMobile(String mobile) { this.mobile = clean(mobile); }

	public String getWhatsApp() { return whatsApp; }

	public void setWhatsApp(String whatsApp) { this.whatsApp = clean(whatsApp); }

	public String getEmail() { return email; }

	public void setEmail(String email) { this.email = clean(email); }

	public String getSource() { return source; }

	public void setSource(String source) { this.source = clean(source); }

	public boolean isEmpty()
	{
		return address.isEmpty() && phone.isEmpty() && mobile.isEmpty() && whatsApp.isEmpty() && email.isEmpty();
	}

	private static String clean(String s)
	{
		return s == null ? "" : s.strip();
	}
}
