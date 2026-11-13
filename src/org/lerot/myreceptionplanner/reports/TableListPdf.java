package org.lerot.myreceptionplanner.reports;

import com.github.weisj.jsvg.SVGDocument;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.tables.Plan;
import org.lerot.myreceptionplanner.model.tables.Table;

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
 * The cards (Card): A5 or A6, portrait, for a stand; or folded down the
 * middle like a greetings card, A4 landscape folding to A5 or A5 landscape
 * folding to A6, the list on the front and again on the back so it reads
 * from both sides of the table. One table's card is printed on a page its
 * own size. Every table at once goes on A4, to cut (or tear, on perforated
 * card) and fold: A5 two to a sheet, A6 four, folded A5 two, folded A4 one.
 * Short marks at the edges show where to cut (darker, solid) and fold
 * (lighter, dashed), so no line crosses a card.
 */
public class TableListPdf
{
	public enum Card
	{
		A5("A5 card, for a stand"),
		A4_FOLDED("A4 folded to A5, front and back"),
		A6("A6 card, for a stand (4 to an A4 sheet)"),
		A5_FOLDED("A5 folded to A6, front and back (2 to an A4 sheet)");

		private final String label;

		Card(String label) { this.label = label; }

		@Override
		public String toString() { return label; }
	}

	// where one face of a card goes on the sheet, and which of the sheet's tables it shows
	private record Face(float x, float y, float width, float height, int table) {}

	// a sheet: its size, how many tables it takes, their faces, and the marks at its edges
	private record Sheet(Rectangle page, int tables, List<Face> faces, List<float[]> folds, List<float[]> cuts) {}

	// a guest's line: seat number, name, and whether a placeholder (Ann Smith+1)
	public record Entry(int seat, String name, boolean placeholder) {}

	private static final float MARGIN = 36;
	private static final float MAX_NAME = 22, MIN_NAME = 8;
	private static final BaseColor TEXT = new BaseColor(0x2B, 0x2B, 0x2B);
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor RULE = new BaseColor(0xB8, 0x86, 0x0B);

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

	// "Table 3" under a table with a name of its own, to find it on the plan by; not the head
	// table's, nor a part of tables combined, which goes by its name; else ""
	public static String subtitle(Table t)
	{
		return t.hasName() && !t.isHead() && t.getGroup() == 0 && t.getNumber() > 0 ? t.series() + " " + t.getNumber() : "";
	}

	public static void write(Occasion occasion, Event event, Table table, Card card, Path file) throws IOException
	{
		write(occasion, event, List.of(table), sheet(card, false), file);
	}

	// a card for each of the tables, in the order given, as many to a sheet of A4 as fit
	public static void writeAll(Occasion occasion, Event event, List<Table> tables, Card card, Path file)
			throws IOException
	{
		if (tables.isEmpty()) throw new IOException("There are no tables to list");
		write(occasion, event, tables, sheet(card, true), file);
	}

	// how the card is set out: on a page its own size, or as many as fit on A4
	private static Sheet sheet(Card card, boolean onA4)
	{
		Rectangle a4 = PageSize.A4, a4Across = PageSize.A4.rotate();
		return switch (card)
		{
			case A5 -> onA4 ? across(a4Across, false) : whole(PageSize.A5);
			case A4_FOLDED -> across(a4Across, true);
			case A5_FOLDED -> onA4 ? foldedPair(a4) : across(PageSize.A5.rotate(), true);
			case A6 -> onA4 ? four(a4) : whole(PageSize.A6);
		};
	}

	private static Sheet whole(Rectangle page)
	{
		return new Sheet(page, 1, List.of(new Face(0, 0, page.getWidth(), page.getHeight(), 0)), List.of(), List.of());
	}

	// two faces side by side: one table front and back (folded), or two tables
	private static Sheet across(Rectangle page, boolean folded)
	{
		float w = page.getWidth() / 2, h = page.getHeight();
		List<float[]> middle = down(w, 0, h);
		return new Sheet(page, folded ? 1 : 2, List.of(new Face(0, 0, w, h, 0), new Face(w, 0, w, h, folded ? 0 : 1)),
				folded ? middle : List.of(), folded ? List.of() : middle);
	}

	// four cards, two across and two down, the first at the top left
	private static Sheet four(Rectangle page)
	{
		float w = page.getWidth() / 2, h = page.getHeight() / 2;
		List<float[]> cuts = new ArrayList<>(down(w, 0, page.getHeight()));
		cuts.addAll(across(h, 0, page.getWidth()));
		return new Sheet(page, 4, List.of(new Face(0, h, w, h, 0), new Face(w, h, w, h, 1),
				new Face(0, 0, w, h, 2), new Face(w, 0, w, h, 3)), List.of(), cuts);
	}

	// two folded cards, one above the other, each folding down its middle
	private static Sheet foldedPair(Rectangle page)
	{
		float w = page.getWidth() / 2, h = page.getHeight() / 2;
		List<float[]> folds = new ArrayList<>(down(w, h, page.getHeight()));
		folds.addAll(down(w, 0, h));
		return new Sheet(page, 2, List.of(new Face(0, h, w, h, 0), new Face(w, h, w, h, 0),
				new Face(0, 0, w, h, 1), new Face(w, 0, w, h, 1)), folds, across(h, 0, page.getWidth()));
	}

	private static final float MARK = 12;

	// marks at the top and bottom of a line down the sheet at x, from y to top
	private static List<float[]> down(float x, float y, float top)
	{
		return List.of(new float[] { x, top, x, top - MARK }, new float[] { x, y, x, y + MARK });
	}

	// marks at the left and right ends of a line across the sheet at y
	private static List<float[]> across(float y, float x, float right)
	{
		return List.of(new float[] { x, y, x + MARK, y }, new float[] { right, y, right - MARK, y });
	}

	private static void write(Occasion occasion, Event event, List<Table> tables, Sheet sheet, Path file)
			throws IOException
	{
		try
		{
			Fonts f = new Fonts();
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(sheet.page());
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				doc.addTitle(occasion.getName() + " – " + event.getName()
						+ (tables.size() == 1 ? " – " + title(event.getPlan(), tables.get(0)) : " – table lists"));
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				PdfContentByte cb = writer.getDirectContent();
				SVGDocument emblem = Emblem.parse(occasion.getEmblem());
				for (int first = 0; first < tables.size(); first += sheet.tables())
				{
					if (first > 0) doc.newPage();
					for (Face face : sheet.faces())
					{
						if (first + face.table() >= tables.size()) continue;  // the last sheet, not full
						Table t = tables.get(first + face.table());
						panel(cb, f, occasion, event, t, entries(t, event.getSeating()), emblem, face);
					}
					PlaceCardsPdf.drawMarks(cb, sheet.folds(), sheet.cuts());
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

	// one face of a card; everything on it in proportion to an A5 card's
	private static void panel(PdfContentByte cb, Fonts f, Occasion occasion, Event event, Table table,
			List<Entry> entries, SVGDocument emblem, Face face) throws DocumentException
	{
		float k = Math.min(face.width() / PageSize.A5.getWidth(), face.height() / PageSize.A5.getHeight());
		float margin = MARGIN * k;
		float room = face.width() - 2 * margin;
		float cx = face.x() + face.width() / 2;
		float y = face.y() + face.height() - margin;

		if (emblem != null)
		{
			float h = 64 * k;
			com.itextpdf.text.Image img = Emblem.pdfImage(cb, emblem, h);
			if (img.getWidth() > room) img.scaleToFit(room, h);
			img.setAbsolutePosition(cx - img.getScaledWidth() / 2, y - img.getScaledHeight());
			cb.addImage(img);
			y -= img.getScaledHeight() + 14 * k;
		}
		y = line(cb, f.bold, 14 * k, TEXT, occasion.getName(), cx, y, room);
		String date = occasion.dateOf(event) == null ? "" : Occasion.format(occasion.dateOf(event));
		if (!date.isEmpty()) y = line(cb, f.regular, 11 * k, GREY, date, cx, y, room);
		y = line(cb, f.regular, 12 * k, GREY, event.getName(), cx, y - 2 * k, room);

		y -= 12 * k;
		rule(cb, cx, y, room * 0.4f, k);
		y -= 10 * k;
		y = line(cb, f.bold, 30 * k, TEXT, title(event.getPlan(), table), cx, y, room);
		String sub = subtitle(table);
		if (!sub.isEmpty()) y = line(cb, f.regular, 14 * k, GREY, sub, cx, y, room);
		y -= 8 * k;
		rule(cb, cx, y, room * 0.4f, k);
		y -= 14 * k;

		if (entries.isEmpty())
		{
			line(cb, f.italic, 12 * k, GREY, "No one is seated here yet", cx, y, room);
			return;
		}
		// the names as large as lets them all fit, in a block centred across
		float bottom = face.y() + margin;
		float size = Math.min(MAX_NAME * k, (y - bottom) / (entries.size() * 1.45f));
		float widestName = 0, widestSeat = 0;
		for (Entry e : entries)
		{
			widestName = Math.max(widestName, (e.placeholder() ? f.italic : f.regular).getWidthPoint(e.name(), 1));
			widestSeat = Math.max(widestSeat, f.bold.getWidthPoint(Integer.toString(e.seat()), 1));
		}
		float gap = 0.8f;  // between seat and name, in font sizes
		size = Math.min(size, room / (widestSeat + gap + widestName));
		size = Math.max(MIN_NAME * k, size);
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
		if (w > room) size = Math.max(size * 0.5f, size * room / w);
		float baseline = top - size;
		text(cb, font, size, colour, fit(font, s, size, room), cx, baseline, PdfContentByte.ALIGN_CENTER);
		return baseline - size * 0.45f;
	}

	private static void rule(PdfContentByte cb, float cx, float y, float length, float k)
	{
		cb.saveState();
		cb.setColorStroke(RULE);
		cb.setLineWidth(0.8f * k);
		cb.moveTo(cx - length / 2, y);
		cb.lineTo(cx + length / 2, y);
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
