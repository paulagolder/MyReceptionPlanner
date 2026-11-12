package org.lerot.myreceptionplanner.reports;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import com.github.weisj.jsvg.SVGDocument;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Table;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
 * A table's list of guests, to stand on the table: the occasion's emblem,
 * the occasion and the event, the table's name or number large, then the
 * guests in seat order with their seat numbers. Empty and disabled seats are
 * left out, and so are dietary needs (those are for the catering lists).
 *
 * On an A5 card, portrait, for a stand; or on A4 landscape folded down the
 * middle like a greetings card, the list on the front and again on the back
 * so it reads from both sides of the table.
 */
public class TableListPdf
{
	public enum Card
	{
		A5("A5 card, for a stand"),
		A4_FOLDED("A4 folded card, front and back");

		private final String label;

		Card(String label) { this.label = label; }

		@Override
		public String toString() { return label; }
	}

	// a guest's line: seat number, name, and whether a placeholder (Ann Smith+1)
	public record Entry(int seat, String name, boolean placeholder) {}

	private static final float MARGIN = 36;
	private static final float MAX_NAME = 22, MIN_NAME = 8;
	private static final BaseColor TEXT = new BaseColor(0x2B, 0x2B, 0x2B);
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor RULE = new BaseColor(0xB8, 0x86, 0x0B);
	private static final BaseColor FOLD = new BaseColor(0xC8, 0xC8, 0xCC);

	// the guests at the table in seat order, seat 1 first
	public static List<Entry> entries(Table t, org.lerot.myreceptionplanner.model.Seating seating)
	{
		List<Entry> out = new ArrayList<>();
		for (int n = 1; n <= t.places(); n++)
		{
			int chair = t.chairOfSeat(n);
			if (t.isDisabled(chair)) continue;
			Optional<Guest> g = seating.guestAt(t.getId(), chair);
			if (g.isPresent()) out.add(new Entry(n, g.get().displayName(), g.get().isPlaceholder()));
		}
		return out;
	}

	// the table's own name, "Head table" or "Table 3"
	public static String title(Plan plan, Table t) { return plan.label(t); }

	// "Table 3" under a table with a name of its own, if it has a number too; else ""
	public static String subtitle(Table t)
	{
		return (t.hasName() || t.isHead()) && t.getNumber() > 0 ? t.series() + " " + t.getNumber() : "";
	}

	public static void write(Occasion occasion, Event event, Table table, Card card, Path file) throws IOException
	{
		try
		{
			Fonts f = new Fonts();
			Rectangle page = card == Card.A5 ? PageSize.A5 : PageSize.A4.rotate();
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(page);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				doc.addTitle(occasion.getName() + " – " + event.getName() + " – " + title(event.getPlan(), table));
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				PdfContentByte cb = writer.getDirectContent();
				List<Entry> entries = entries(table, event.getSeating());
				SVGDocument emblem = Emblem.parse(occasion.getEmblem());
				if (card == Card.A5)
				{
					panel(cb, f, occasion, event, table, entries, emblem, 0, page.getWidth(), page.getHeight());
				} else
				{
					float half = page.getWidth() / 2;
					// the back on the left, the front on the right, when folded
					panel(cb, f, occasion, event, table, entries, emblem, 0, half, page.getHeight());
					panel(cb, f, occasion, event, table, entries, emblem, half, half, page.getHeight());
					foldMarks(cb, half, page.getHeight());
				}
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	private static final class Fonts
	{
		final BaseFont regular, bold, italic;

		Fonts() throws DocumentException, IOException
		{
			regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			italic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
		}
	}

	// one face of the card, left at x, this wide and high
	private static void panel(PdfContentByte cb, Fonts f, Occasion occasion, Event event, Table table,
			List<Entry> entries, SVGDocument emblem, float x, float width, float height) throws DocumentException
	{
		float room = width - 2 * MARGIN;
		float cx = x + width / 2;
		float y = height - MARGIN;

		if (emblem != null)
		{
			float h = 64;
			com.itextpdf.text.Image img = Emblem.pdfImage(cb, emblem, h);
			if (img.getWidth() > room) img.scaleToFit(room, h);
			img.setAbsolutePosition(cx - img.getScaledWidth() / 2, y - img.getScaledHeight());
			cb.addImage(img);
			y -= img.getScaledHeight() + 14;
		}
		y = line(cb, f.bold, 14, TEXT, occasion.getName(), cx, y, room);
		String date = occasion.dateOf(event) == null ? "" : Occasion.format(occasion.dateOf(event));
		if (!date.isEmpty()) y = line(cb, f.regular, 11, GREY, date, cx, y, room);
		y = line(cb, f.regular, 12, GREY, event.getName(), cx, y - 2, room);

		y -= 12;
		rule(cb, cx, y, room * 0.4f);
		y -= 10;
		y = line(cb, f.bold, 30, TEXT, title(event.getPlan(), table), cx, y, room);
		String sub = subtitle(table);
		if (!sub.isEmpty()) y = line(cb, f.regular, 14, GREY, sub, cx, y, room);
		y -= 8;
		rule(cb, cx, y, room * 0.4f);
		y -= 14;

		if (entries.isEmpty())
		{
			line(cb, f.italic, 12, GREY, "No one is seated here yet", cx, y, room);
			return;
		}
		// the names as large as lets them all fit, in a block centred across
		float bottom = MARGIN;
		float size = Math.min(MAX_NAME, (y - bottom) / (entries.size() * 1.45f));
		float widestName = 0, widestSeat = 0;
		for (Entry e : entries)
		{
			widestName = Math.max(widestName, (e.placeholder() ? f.italic : f.regular).getWidthPoint(e.name(), 1));
			widestSeat = Math.max(widestSeat, f.bold.getWidthPoint(Integer.toString(e.seat()), 1));
		}
		float gap = 0.8f;  // between seat and name, in font sizes
		size = Math.min(size, room / (widestSeat + gap + widestName));
		size = Math.max(MIN_NAME, size);
		float block = size * (widestSeat + gap + widestName);
		float seatRight = cx - Math.min(block, room) / 2 + size * widestSeat;
		float lineHeight = size * 1.45f;
		// a short list sits in the middle of the space, a little high
		float spare = (y - bottom) - entries.size() * lineHeight;
		if (spare > 0) y -= spare * 0.4f;
		y -= size;
		for (Entry e : entries)
		{
			text(cb, f.bold, size, RULE, Integer.toString(e.seat()), seatRight, y, PdfContentByte.ALIGN_RIGHT);
			BaseFont font = e.placeholder() ? f.italic : f.regular;
			text(cb, font, size, e.placeholder() ? GREY : TEXT,
					fit(font, e.name(), size, room - size * (widestSeat + gap)), seatRight + size * gap, y,
					PdfContentByte.ALIGN_LEFT);
			y -= lineHeight;
		}
	}

	// a centred line, made smaller if need be to fit across; the baseline of the next
	private static float line(PdfContentByte cb, BaseFont font, float size, BaseColor colour, String s,
			float cx, float top, float room)
	{
		if (s == null || s.isBlank()) return top;
		float w = font.getWidthPoint(s, size);
		if (w > room) size = Math.max(8, size * room / w);
		float baseline = top - size;
		text(cb, font, size, colour, fit(font, s, size, room), cx, baseline, PdfContentByte.ALIGN_CENTER);
		return baseline - size * 0.45f;
	}

	private static void rule(PdfContentByte cb, float cx, float y, float length)
	{
		cb.saveState();
		cb.setColorStroke(RULE);
		cb.setLineWidth(0.8f);
		cb.moveTo(cx - length / 2, y);
		cb.lineTo(cx + length / 2, y);
		cb.stroke();
		cb.restoreState();
	}

	// short marks at the top and bottom of the fold, not across the card
	private static void foldMarks(PdfContentByte cb, float x, float height)
	{
		cb.saveState();
		cb.setColorStroke(FOLD);
		cb.setLineWidth(0.5f);
		cb.moveTo(x, height);
		cb.lineTo(x, height - 12);
		cb.moveTo(x, 0);
		cb.lineTo(x, 12);
		cb.stroke();
		cb.restoreState();
	}

	private static void text(PdfContentByte cb, BaseFont font, float size, BaseColor colour, String s,
			float x, float y, int align)
	{
		cb.beginText();
		cb.setFontAndSize(font, size);
		cb.setColorFill(colour);
		cb.showTextAligned(align, s, x, y, 0);
		cb.endText();
	}

	// shortened with … if too wide
	private static String fit(BaseFont font, String s, float size, float room)
	{
		if (font.getWidthPoint(s, size) <= room) return s;
		String t = s;
		while (t.length() > 1 && font.getWidthPoint(t + "…", size) > room)
			t = t.substring(0, t.length() - 1);
		return t.strip() + "…";
	}
}
