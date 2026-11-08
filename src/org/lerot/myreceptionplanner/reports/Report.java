package org.lerot.myreceptionplanner.reports;

import java.util.ArrayList;
import java.util.List;

/*
 * A report as headed sections of rows, for showing on screen (html) or
 * writing as a PDF (ReportPdf): the catering list, the kitchen quantities,
 * service by table.
 */
public class Report
{
	// a heading, column names (may be empty), rows of cells, and a line under it
	public record Section(String heading, List<String> columns, List<List<String>> rows, String footnote) {}

	public final String title;
	public final String subtitle;
	public String emblem;  // the occasion's SVG emblem, for the top of the page; null for none
	public final List<Section> sections = new ArrayList<>();

	protected Report(String title, String subtitle)
	{
		this.title = title;
		this.subtitle = subtitle;
	}

	// for showing in a dialog
	public String html()
	{
		StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif; margin:8px'>");
		h.append("<h2 style='margin:0'>").append(esc(title)).append("</h2>");
		h.append("<p style='margin-top:2px; color:#6E6E72'>").append(esc(subtitle)).append("</p>");
		for (Section s : sections)
		{
			h.append("<h3 style='margin-bottom:4px'>").append(esc(s.heading())).append("</h3>");
			if (!s.rows().isEmpty())
			{
				h.append("<table border='0' cellspacing='0' cellpadding='3'>");
				if (!s.columns().isEmpty())
				{
					h.append("<tr>");
					for (String c : s.columns()) h.append("<th align='left' bgcolor='#F4F1EA'>").append(esc(c)).append("</th>");
					h.append("</tr>");
				}
				for (List<String> r : s.rows())
				{
					h.append("<tr>");
					for (String c : r) h.append("<td>").append(esc(c)).append("</td>");
					h.append("</tr>");
				}
				h.append("</table>");
			}
			if (!s.footnote().isEmpty())
				h.append("<p style='margin-top:4px; color:#6E6E72'>").append(esc(s.footnote())).append("</p>");
		}
		return h.append("</body></html>").toString();
	}

	protected static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
