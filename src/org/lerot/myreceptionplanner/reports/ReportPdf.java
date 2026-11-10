package org.lerot.myreceptionplanner.reports;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/*
 * A report (see Report) as an A4 PDF: portrait, or landscape when its tables
 * are wide; each section kept on one page where it fits, or each on a page
 * of its own (service by table, one sheet for each table).
 */
public class ReportPdf
{
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor BAND = new BaseColor(0xF4, 0xF1, 0xEA);

	// each column as wide as its longest cell (within reason), so a seat number takes little room
	private static float[] widths(Report.Section s, int cols)
	{
		float[] w = new float[cols];
		for (int c = 0; c < cols; c++)
		{
			int most = c < s.columns().size() ? Math.min(s.columns().get(c).length() + 2, 16) : 1;
			for (List<String> r : s.rows())
				if (c < r.size()) most = Math.max(most, r.get(c).length());
			w[c] = Math.max(5, Math.min(30, most));
		}
		return w;
	}

	public static void write(Report report, Path file) throws IOException
	{
		write(report, file, false);
	}

	public static void write(Report report, Path file, boolean pagePerSection) throws IOException
	{
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Font title = new Font(bold, 18), sub = new Font(regular, 12, Font.NORMAL, GREY),
					heading = new Font(bold, 13), head = new Font(bold, 10), cell = new Font(regular, 10),
					note = new Font(regular, 10, Font.NORMAL, GREY);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(report.sections.stream().anyMatch(ReportPdf::wide)
					? PageSize.A4.rotate() : PageSize.A4, 40, 40, 40, 40);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				var picture = Emblem.parse(report.emblem);
				// the headings keep clear of the emblem, wrapping rather than running under it
				float clear = picture == null ? 0 : Emblem.widthFor(picture, 44) + 12;
				if (picture != null)
				{
					// the emblem at the top right of every page
					writer.setPageEvent(new com.itextpdf.text.pdf.PdfPageEventHelper()
					{
						public void onEndPage(PdfWriter w, Document d)
						{
							try
							{
								com.itextpdf.text.Image img = Emblem.pdfImage(w.getDirectContent(), picture, 44);
								img.setAbsolutePosition(d.right() - img.getWidth(), d.top() - img.getHeight() + 8);
								w.getDirectContent().addImage(img);
							} catch (DocumentException ignored)
							{
							}
						}
					});
				}
				doc.addTitle(report.subtitle);
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				Paragraph top = new Paragraph(keepTogether(report.title), title);
				top.setIndentationRight(clear);
				doc.add(top);
				Paragraph p = new Paragraph(report.subtitle, sub);
				p.setIndentationRight(clear);
				p.setSpacingAfter(10);
				doc.add(p);
				boolean first = true;
				for (Report.Section s : report.sections)
				{
					if (pagePerSection && !first)
					{
						doc.newPage();
						Paragraph again = new Paragraph(report.title + "  ·  " + report.subtitle, sub);
						again.setIndentationRight(clear);
						doc.add(again);
					}
					first = false;
					PdfPTable t = null;
					if (!s.rows().isEmpty())
					{
						int cols = s.columns().isEmpty() ? s.rows().get(0).size() : s.columns().size();
						t = new PdfPTable(cols);
						t.setWidthPercentage(100);
						t.setWidths(widths(s, cols));
						t.setHorizontalAlignment(PdfPTable.ALIGN_LEFT);
						t.setHeaderRows(s.columns().isEmpty() ? 0 : 1);
						t.setKeepTogether(true);
						boolean[] numbers = numberColumns(s, cols);
						for (int ci = 0; ci < s.columns().size(); ci++)
						{
							PdfPCell pc = new PdfPCell(new Phrase(s.columns().get(ci), head));
							if (ci < cols && numbers[ci]) pc.setHorizontalAlignment(Element.ALIGN_CENTER);
							pc.setBackgroundColor(BAND);
							pc.setBorderColor(BAND);
							pc.setPadding(4);
							t.addCell(pc);
						}
						for (List<String> r : s.rows())
							for (int ci = 0; ci < r.size(); ci++)
							{
								String c = r.get(ci);
								PdfPCell pc = new PdfPCell(new Phrase(c, cell));
								if (ci < cols && numbers[ci]) pc.setHorizontalAlignment(Element.ALIGN_CENTER);
								pc.setBorderColor(BAND);
								pc.setPadding(4);
								t.addCell(pc);
							}
					}
					// the heading goes with its table: if what follows it will not fit
					// below, both start the next page
					if (t != null && writer.getVerticalPosition(true) - doc.bottom() < HEADING_ROOM + firstPart(t, doc))
						doc.newPage();
					Paragraph h = new Paragraph(s.heading().replace("⚠ ", "! "), heading);
					h.setKeepTogether(true);
					h.setSpacingBefore(10);
					h.setSpacingAfter(4);
					doc.add(h);
					if (t != null) doc.add(t);
					if (!s.footnote().isEmpty())
					{
						Paragraph f = new Paragraph(s.footnote(), note);
						f.setSpacingBefore(3);
						doc.add(f);
					}
				}
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	private static final float HEADING_ROOM = 48;  // a section's heading with the space round it, and a little to spare

	// the height of the table that must follow its heading on the same page:
	// all of it if it fits on a page (it is kept together), or else its
	// header and first few rows
	private static float firstPart(PdfPTable t, Document doc)
	{
		t.setTotalWidth(doc.right() - doc.left());
		float all = t.getTotalHeight();
		if (all <= doc.top() - doc.bottom() - HEADING_ROOM) return all;
		return rowsHeight(t, Math.min(t.size(), t.getHeaderRows() + 3));
	}

	private static float rowsHeight(PdfPTable t, int rows)
	{
		float h = 0;
		for (int i = 0; i < rows; i++) h += t.getRowHeight(i);
		return h;
	}

	// too wide for an upright page: more than 6 columns, unless nearly all are
	// numbers (a grid of counts, which take little room), or more than 14
	private static boolean wide(Report.Section s)
	{
		int cols = s.columns().isEmpty() ? (s.rows().isEmpty() ? 0 : s.rows().get(0).size()) : s.columns().size();
		boolean[] numbers = numberColumns(s, cols);
		int words = 0;
		for (boolean n : numbers) if (!n) words++;
		return (cols > 6 && words > 3) || cols > 14;
	}

	// the columns holding only numbers (or nothing), which are centred
	private static boolean[] numberColumns(Report.Section s, int cols)
	{
		boolean[] out = new boolean[cols];
		for (int c = 0; c < cols; c++)
		{
			boolean any = false, all = true;
			for (List<String> r : s.rows())
			{
				String v = c < r.size() ? r.get(c).strip() : "";
				if (v.isEmpty()) continue;
				any = true;
				if (!v.chars().allMatch(Character::isDigit)) all = false;
			}
			out[c] = any && all;
		}
		return out;
	}

	// "Name  ·  Wednesday 16 July 1595" never wraps inside the date (or any
	// part after the name: its spaces made non-breaking); the name may wrap
	private static String keepTogether(String heading)
	{
		String[] parts = heading.split("  ·  ");
		for (int i = 1; i < parts.length; i++) parts[i] = parts[i].replace(' ', '\u00A0');
		return String.join("  ·  ", parts);
	}
}
