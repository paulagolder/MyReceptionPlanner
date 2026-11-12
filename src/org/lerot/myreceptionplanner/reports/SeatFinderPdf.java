package org.lerot.myreceptionplanner.reports;

import com.github.weisj.jsvg.SVGDocument;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;
import org.lerot.myreceptionplanner.model.Altar;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Pew;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Seat;
import org.lerot.myreceptionplanner.model.Table;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 * A seat finder, for the board at the door: the room in the middle, each
 * table as it stands with its name or number and each chair's seat number
 * (no guests' names, so it stays clear when enlarged); down the left, and on
 * down the right, every seated guest in order of surname with their table
 * and seat. The surname is taken as the last word of the name as written
 * ("Juliet Capulet" under C), and names are printed as written, under a
 * letter for each initial. Guests not yet seated are left out and counted
 * at the foot.
 */
public class SeatFinderPdf
{
	// a guest's line: the name as written, where they sit, and the letter they go under
	public record Entry(String name, String place, String letter, boolean placeholder) {}

	// one line of a column: a letter heading, or a guest
	private record Row(String letter, Entry entry) {}

	private static final float MARGIN = 30;
	private static final float GAP = 18;           // between columns, and between them and the plan
	private static final float LINE = 1.3f;        // line height, in font sizes
	private static final float MAX_FONT = 11, MIN_FONT = 4.5f;
	private static final BaseColor TEXT = new BaseColor(0x2B, 0x2B, 0x2B);
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor GOLD = new BaseColor(0xB8, 0x86, 0x0B);
	private static final Color EDGE = new Color(0x8A, 0x8A, 0x8E);
	private static final Color HEAD = new Color(0xB8, 0x86, 0x0B);
	private static final Color TOP = new Color(0xFA, 0xF8, 0xF3);
	private static final Color PEW = new Color(0xE8, 0xE4, 0xDA);
	private static final Color ALTAR = new Color(0xEF, 0xE6, 0xD2);
	private static final Color INK = new Color(0x2B, 0x2B, 0x2B);

	// every seated guest, by surname, then by the whole name
	public static List<Entry> entries(Event event)
	{
		Plan plan = event.getPlan();
		Collator order = Collator.getInstance(Locale.UK);
		order.setStrength(Collator.PRIMARY);
		List<Entry> out = new ArrayList<>();
		for (Map.Entry<Guest, Seat> e : event.getSeating().all().entrySet())
		{
			Table t = plan.find(e.getValue().tableId()).orElse(null);
			if (t == null || e.getValue().chair() >= t.places()) continue;
			Guest g = e.getKey();
			String name = g.displayName();
			out.add(new Entry(name, plan.label(t) + " · " + t.seatNumber(e.getValue().chair()),
					letter(surname(name)), g.isPlaceholder()));
		}
		out.sort(Comparator.comparing((Entry x) -> surname(x.name()), order)
				.thenComparing(Entry::name, order));
		return out;
	}

	// the last word of the name, without a placeholder's +1
	static String surname(String name)
	{
		String s = name.strip().replaceFirst("\\+\\d+$", "").strip();
		int space = s.lastIndexOf(' ');
		return space < 0 ? s : s.substring(space + 1);
	}

	// the letter it goes under, as it sorts: "D" for d'Este, "O" for O'Neill, "E" for Élise
	private static String letter(String surname)
	{
		for (char c : surname.toCharArray())
		{
			if (Character.isLetter(c))
				return java.text.Normalizer.normalize(String.valueOf(Character.toUpperCase(c)),
						java.text.Normalizer.Form.NFD).substring(0, 1);
		}
		return "#";
	}

	// the guests expected at the event who have no seat yet
	public static int unseated(Event event)
	{
		return (int) event.invited().stream().filter(g -> !event.getSeating().isSeated(g)).count();
	}

	public static void write(Occasion occasion, Event event, Rectangle page, Path file) throws IOException
	{
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont italic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(page);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				doc.addTitle(occasion.getName() + " – " + event.getName() + " – seat finder");
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				PdfContentByte cb = writer.getDirectContent();
				float w = page.getWidth(), h = page.getHeight();
				float k = w / 842f;  // in proportion to A4 across

				// the heading, as on the table plan
				float titleSize = 20 * k, subSize = 12 * k;
				float top = h - MARGIN * k;
				SVGDocument picture = Emblem.parse(occasion.getEmblem());
				if (picture != null)
				{
					com.itextpdf.text.Image img = Emblem.pdfImage(cb, picture, titleSize + subSize * 2.4f);
					img.setAbsolutePosition(MARGIN * k, top - img.getHeight());
					cb.addImage(img);
				}
				text(cb, bold, titleSize, TEXT, occasion.heading(), w / 2, top - titleSize, PdfContentByte.ALIGN_CENTER);
				text(cb, regular, subSize, GREY, occasion.headingOf(event) + "  ·  Find your seat", w / 2,
						top - titleSize - subSize * 1.6f, PdfContentByte.ALIGN_CENTER);
				float areaTop = top - titleSize - subSize * 3.4f;

				// a note of anyone not yet seated, at the foot
				int unseated = unseated(event);
				float bottom = MARGIN * k;
				if (unseated > 0)
				{
					text(cb, italic, 8 * k, GREY, unseated + (unseated == 1 ? " guest is" : " guests are")
							+ " not seated yet, so not on this list.", w / 2, bottom, PdfContentByte.ALIGN_CENTER);
					bottom += 14 * k;
				}

				List<Entry> entries = entries(event);
				Columns cols = columns(entries, regular, bold, italic, w - 2 * MARGIN * k, areaTop - bottom, k);
				float left = MARGIN * k;
				float planLeft = left + cols.perSide() * (cols.width() + GAP * k);
				float planRight = w - MARGIN * k - cols.perSide() * (cols.width() + GAP * k);
				drawColumns(cb, cols, regular, bold, italic, left, planRight + GAP * k, areaTop, k);
				drawPlan(cb, event.getPlan(), planLeft, bottom, planRight - planLeft, areaTop - bottom);
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	// ---- the list ----

	// perSide columns on each side, each this wide, the type this size, the rows of each column
	private record Columns(int perSide, float width, float font, List<List<Row>> columns, float placeWidth) {}

	/*
	 * Lays the list out in 2, 4 or 6 columns, half each side of the plan,
	 * choosing the one with the largest type that leaves the plan at least
	 * half the width.
	 */
	private static Columns columns(List<Entry> entries, BaseFont regular, BaseFont bold, BaseFont italic,
			float width, float height, float k)
	{
		float widestName = 0, widestPlace = 0;
		for (Entry e : entries)
		{
			widestName = Math.max(widestName, (e.placeholder() ? italic : regular).getWidthPoint(e.name(), 1));
			widestPlace = Math.max(widestPlace, regular.getWidthPoint(e.place(), 1));
		}
		float perFont = widestName + 1.5f + widestPlace;  // a column's width, at size 1
		Columns best = null;
		for (int perSide = 1; perSide <= 3; perSide++)
		{
			int n = 2 * perSide;
			List<Row> rows = rows(entries);
			int tall = (rows.size() + n - 1) / n + 1;  // a letter may move to the next column
			float byHeight = height / (tall * LINE);
			float byWidth = (width / 2 - n * GAP * k) / (n * perFont);
			float font = Math.min(MAX_FONT * k, Math.min(byHeight, byWidth));
			if (best == null || font > best.font() + 0.01f)
				best = new Columns(perSide, perFont * font, font, split(rows, n, (int) Math.floor(height / (font * LINE))),
						widestPlace * font);
		}
		if (best.font() < MIN_FONT * k)  // too many to fit well: as small as it may go
		{
			float font = MIN_FONT * k;
			int n = 2 * best.perSide();
			best = new Columns(best.perSide(), perFont * font, font,
					split(rows(entries), n, (int) Math.floor(height / (font * LINE))), widestPlace * font);
		}
		return best;
	}

	// the guests with a letter heading before each new initial
	private static List<Row> rows(List<Entry> entries)
	{
		List<Row> rows = new ArrayList<>();
		String last = null;
		for (Entry e : entries)
		{
			if (!e.letter().equals(last)) rows.add(new Row(e.letter(), null));
			last = e.letter();
			rows.add(new Row(null, e));
		}
		return rows;
	}

	// into n columns of about the same length, at most fit rows each; a
	// letter never left at the foot of a column without a name under it
	private static List<List<Row>> split(List<Row> rows, int n, int fit)
	{
		int each = Math.max(1, Math.min(fit, (rows.size() + n - 1) / n));
		List<List<Row>> columns = new ArrayList<>();
		List<Row> column = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++)
		{
			Row r = rows.get(i);
			boolean full = column.size() >= each || r.letter() != null && column.size() >= each - 1;
			if (full && columns.size() < n - 1 && !column.isEmpty())
			{
				columns.add(column);
				column = new ArrayList<>();
			}
			column.add(r);
		}
		columns.add(column);
		while (columns.size() < n) columns.add(List.of());
		return columns;
	}

	private static void drawColumns(PdfContentByte cb, Columns cols, BaseFont regular, BaseFont bold,
			BaseFont italic, float left, float rightStart, float top, float k)
	{
		float f = cols.font();
		for (int c = 0; c < cols.columns().size(); c++)
		{
			int side = c / cols.perSide(), within = c % cols.perSide();
			float x = (side == 0 ? left : rightStart) + within * (cols.width() + GAP * k);
			float y = top - f;
			for (Row r : cols.columns().get(c))
			{
				if (r.letter() != null)
				{
					text(cb, bold, f * 1.05f, GOLD, r.letter(), x, y, PdfContentByte.ALIGN_LEFT);
				} else
				{
					Entry e = r.entry();
					BaseFont font = e.placeholder() ? italic : regular;
					text(cb, font, f, e.placeholder() ? GREY : TEXT, e.name(), x, y, PdfContentByte.ALIGN_LEFT);
					text(cb, regular, f, GREY, e.place(), x + cols.width(), y, PdfContentByte.ALIGN_RIGHT);
				}
				y -= f * LINE;
			}
		}
	}

	// ---- the plan ----

	// the tables in the box, as large as fits, centred
	private static void drawPlan(PdfContentByte cb, Plan plan, float x, float y, float width, float height)
	{
		if (plan.tables().isEmpty() || width <= 0 || height <= 0) return;
		Rectangle2D room = null;
		for (Table t : plan.tables())
		{
			if (room == null) room = t.bounds();
			else room.add(t.bounds());
		}
		double scale = Math.min(width / room.getWidth(), height / room.getHeight());
		PdfTemplate tpl = cb.createTemplate(width, height);
		Graphics2D g = new PdfGraphics2D(tpl, width, height, true);  // text as outlines
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.translate((width - room.getWidth() * scale) / 2, (height - room.getHeight() * scale) / 2);
		g.scale(scale, scale);
		g.translate(-room.getX(), -room.getY());
		for (Table t : plan.tables())
			drawTable(g, plan, t);
		g.dispose();
		cb.addTemplate(tpl, x, y);
	}

	// in centimetres, as the tables are
	private static void drawTable(Graphics2D g, Plan plan, Table t)
	{
		AffineTransform floor = t.toFloor();
		Shape top = floor.createTransformedShape(t.top());
		g.setColor(t instanceof Pew ? PEW : t instanceof Altar ? ALTAR : TOP);
		g.fill(top);
		g.setColor(t.isHead() ? HEAD : EDGE);
		g.setStroke(new BasicStroke(t.isHead() ? 3f : 1.5f));
		g.draw(top);

		// each chair, with its seat number
		Font number = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
		double r = Table.SEAT_RADIUS;
		for (int chair = 0; chair < t.places(); chair++)
		{
			Point2D c = t.seatOnFloor(chair);
			Ellipse2D circle = new Ellipse2D.Double(c.getX() - r, c.getY() - r, 2 * r, 2 * r);
			if (t.isDisabled(chair))
			{
				g.setColor(EDGE);
				g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 4, 4 }, 0));
				g.draw(circle);
				continue;
			}
			g.setColor(Color.WHITE);
			g.fill(circle);
			g.setColor(EDGE);
			g.setStroke(new BasicStroke(1.2f));
			g.draw(circle);
			centred(g, number, INK, Integer.toString(t.seatNumber(chair)), c.getX(), c.getY(), 2 * r - 6);
		}

		// its name or number, level, in the middle, as large as the table
		// allows; a pew's just behind its backrest, clear of the seats
		String label = plan.label(t);
		Rectangle2D local = t.top().getBounds2D();
		if (t instanceof Pew)
		{
			Point2D at = t.toFloor().transform(new Point2D.Double(0, local.getMaxY() + 14), null);
			centred(g, new Font(Font.SANS_SERIF, Font.PLAIN, 16), EDGE, label, at.getX(), at.getY(), local.getWidth());
			return;
		}
		double room = Math.max(local.getWidth(), local.getHeight()) * 0.8;
		centred(g, new Font(Font.SANS_SERIF, Font.BOLD, 30), INK, label, t.getX(), t.getY(), room);
	}

	// text centred on a point, made smaller to fit the room across
	private static void centred(Graphics2D g, Font font, Color colour, String s, double x, double y, double room)
	{
		FontMetrics m = g.getFontMetrics(font);
		double w = m.stringWidth(s);
		if (w > room && w > 0) font = font.deriveFont((float) (font.getSize2D() * room / w));
		g.setFont(font);
		m = g.getFontMetrics(font);
		g.setColor(colour);
		g.drawString(s, (float) (x - m.stringWidth(s) / 2.0), (float) (y + (m.getAscent() - m.getDescent()) / 2.0));
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
}
