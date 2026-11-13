package org.lerot.myreceptionplanner.reports;

import com.github.weisj.jsvg.SVGDocument;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import org.lerot.myreceptionplanner.model.Event;
import org.lerot.myreceptionplanner.model.Guest;
import org.lerot.myreceptionplanner.model.Menu;
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
 * Place cards: a tent card for each seated guest, folded along its middle to
 * stand on the table. The front (the lower half as printed) has a small
 * emblem, the guest's name large and, small, the table and seat. The back
 * (the upper half, printed upside down so it reads the right way up when the
 * card stands) has the name again for those across the table and, if asked,
 * the guest's menu choices and dietary needs for the waiting staff.
 *
 * The cards come table by table in seat order, the head table first, to lay
 * out as they come. Every name is the same size, the largest at which
 * every guest's name at the event fits in two lines at most, so a table
 * printed on its own matches the rest. A place not yet named (Ann Smith+1) has "Guest of Ann
 * Smith". They are set out on A4 to fit perforated card (Stock), with short
 * marks in the margins where to fold and cut.
 */
public class PlaceCardsPdf
{
	private static final float MM = 72f / 25.4f;

	public enum Stock
	{
		DECADRY_6("6 to an A4 sheet, 85 × 46 mm folded (DECAdry DPOCB3713)", 2, 3, 85, 92),
		LARGE_4("4 to an A4 sheet, 105 × 74 mm folded (plain card, to cut)", 2, 2, 105, 148.5f);

		private final String label;
		final int across, down;
		final float width, height;  // a card flat, in points

		Stock(String label, int across, int down, float widthMm, float heightMm)
		{
			this.label = label;
			this.across = across;
			this.down = down;
			this.width = widthMm * MM;
			this.height = heightMm * MM;
		}

		int perSheet() { return across * down; }

		@Override
		public String toString() { return label; }
	}

	// what goes on one card: the name, "Table 3 · Seat 5", and for the staff the
	// dishes chosen, in short, and the dietary needs (none, or "", if not asked for)
	public record Card(String name, String place, List<String> dishes, String diet) {}

	private static final BaseColor TEXT = new BaseColor(0x2B, 0x2B, 0x2B);
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor GOLD = new BaseColor(0xB8, 0x86, 0x0B);
	static final BaseColor FOLD = new BaseColor(0xC8, 0xC8, 0xCC);   // light and dashed
	static final BaseColor CUT = new BaseColor(0x6E, 0x6E, 0x72);    // darker, heavier, solid

	// the cards for the guests seated at these tables, in the order given, each in seat order
	public static List<Card> cards(Event event, List<Table> tables, boolean forStaff)
	{
		Plan plan = event.getPlan();
		List<Card> cards = new ArrayList<>();
		for (Table t : tables)
		{
			for (int n = 1; n <= t.places(); n++)
			{
				int chair = t.chairOfSeat(n);
				Optional<Guest> g = event.getSeating().guestAt(t.getId(), chair);
				if (g.isEmpty()) continue;
				Guest guest = g.get();
				List<String> dishes = new ArrayList<>();
				String diet = "";
				if (forStaff)
				{
					for (Menu.Course c : event.getMenu().courses())
					{
						Menu.Dish d = event.choice(guest, c);
						if (d != null) dishes.add(shortName(d.getName()));
					}
					diet = guest.dietText();
				}
				cards.add(new Card(name(guest), plan.label(t) + "  ·  Seat " + n,
						dishes, diet));
			}
		}
		return cards;
	}

	// a dish by its name, without the description: "Salted ox tongue" for
	// "Salted ox tongue with mostarda of quince", "Eel of Lake Garda" for
	// "Eel of Lake Garda, marinated with bay and orange"
	public static String shortName(String dish)
	{
		String s = dish.strip();
		int cut = s.length();
		int comma = s.indexOf(',');
		if (comma > 2) cut = comma;
		java.util.regex.Matcher with = java.util.regex.Pattern.compile("(?i)\\s(with|in|on|served)\\s").matcher(s);
		if (with.find() && with.start() > 2) cut = Math.min(cut, with.start());
		return s.substring(0, cut).strip();
	}

	// the name as written; for a place not yet named, "Guest of" the lead
	static String name(Guest g)
	{
		if (!g.isPlaceholder()) return g.displayName();
		Guest lead = g.getParty() == null ? null : g.getParty().getLead();
		return lead != null && lead.isNamed() ? "Guest of " + lead.getName() : g.displayName();
	}

	public static void write(Occasion occasion, Event event, List<Table> tables, Stock stock, boolean forStaff,
			Path file) throws IOException
	{
		List<Card> cards = cards(event, tables, forStaff);
		if (cards.isEmpty()) throw new IOException("No one is seated there yet, so there are no place cards");
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Rectangle page = PageSize.A4;
			Files.createDirectories(file.toAbsolutePath().getParent());
			Document doc = new Document(page);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				doc.addTitle(occasion.getName() + " – " + event.getName() + " – place cards");
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				PdfContentByte cb = writer.getDirectContent();
				SVGDocument picture = Emblem.parse(occasion.getEmblem());
				Image emblem = picture == null ? null : Emblem.pdfImage(cb, picture, stock.height / 2 * 0.17f);
				// sized for every guest at the event, so a table printed on its own
				// matches the others, whenever they are printed
				List<Card> everyone = cards(event, event.getPlan().tables(), forStaff);
				Sizes sizes = sizes(everyone, regular, bold, emblem, forStaff, stock.width, stock.height / 2);

				float left = (page.getWidth() - stock.across * stock.width) / 2;
				float bottom = (page.getHeight() - stock.down * stock.height) / 2;
				for (int first = 0; first < cards.size(); first += stock.perSheet())
				{
					if (first > 0) doc.newPage();
					for (int i = 0; i < stock.perSheet() && first + i < cards.size(); i++)
					{
						int col = i % stock.across, row = i / stock.across;  // from the top left
						float x = left + col * stock.width;
						float y = bottom + (stock.down - 1 - row) * stock.height;
						card(cb, regular, bold, emblem, sizes, cards.get(first + i), forStaff, x, y, stock.width, stock.height);
					}
					marks(cb, page, stock, left, bottom);
				}
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	/*
	 * The same sizes on every card, so no guest's name is printed larger than
	 * another's: the name as large as lets every name fit in two lines at
	 * most, broken between words. pad is kept clear at the card's sides,
	 * edge at its outer edge (where printers falter), fold by the fold.
	 */
	private record Sizes(float pad, float edge, float fold, float name, float back, float place, float small) {}

	private static Sizes sizes(List<Card> cards, BaseFont regular, BaseFont bold, Image emblem, boolean forStaff,
			float w, float half)
	{
		float pad = w * 0.12f, edge = half * 0.17f, fold = half * 0.08f;
		float room = w - 2 * pad;
		float place = half * 0.075f;

		// the front: under the emblem, over the table and seat
		float space = half - edge - fold - (emblem == null ? 0 : emblem.getHeight() + half * 0.05f) - place * 2.4f;
		float name = half * 0.17f;
		for (Card c : cards)
			name = Math.min(name, twoLineSize(bold, c.name(), name, room));
		boolean twoLines = false;
		for (Card c : cards)
			twoLines |= lines(bold, c.name(), name, room).size() > 1;
		if (twoLines) name = Math.min(name, space / (2 * 1.15f));

		// the back: the name again, smaller under the staff's notes
		float back = forStaff ? name * 0.75f : name;
		float small = half * 0.065f;
		if (forStaff)
		{
			int most = 0, nameLines = 1;
			for (Card c : cards)
			{
				most = Math.max(most, c.dishes().size() + (c.diet().isEmpty() ? 0 : 1));
				nameLines = Math.max(nameLines, lines(bold, c.name(), back, room).size());
			}
			float left = half - edge - fold - nameLines * back * 1.15f - place * 2.2f;
			if (most > 0) small = Math.max(4.5f, Math.min(small, left / (most * 1.3f)));
			for (Card c : cards)
			{
				for (String d : c.dishes()) small = Math.min(small, fit(regular, d, small, room));
				if (!c.diet().isEmpty()) small = Math.min(small, fit(bold, c.diet(), small, room));
			}
			small = Math.max(4.5f, small);
		}
		return new Sizes(pad, edge, fold, name, back, place, small);
	}

	// one card, flat, its lower left at x, y: the front below the fold, the back above, upside down
	private static void card(PdfContentByte cb, BaseFont regular, BaseFont bold, Image emblem, Sizes z, Card card,
			boolean forStaff, float x, float y, float w, float h) throws DocumentException
	{
		float half = h / 2;
		float room = w - 2 * z.pad();

		// the front, upright: the emblem at the top (by the fold), the table and seat at the foot
		cb.saveState();
		cb.concatCTM(1, 0, 0, 1, x, y);
		float top = half - z.fold();
		if (emblem != null)
		{
			float eh = emblem.getHeight(), ew = emblem.getWidth();
			cb.addImage(emblem, ew, 0, 0, eh, (w - ew) / 2, top - eh);
			top -= eh + half * 0.05f;
		}
		float foot = z.edge() + z.place() * 2.4f;
		text(cb, regular, z.place(), GREY, card.place(), w / 2, z.edge());
		block(cb, bold, z.name(), TEXT, lines(bold, card.name(), z.name(), room), w / 2, (top + foot) / 2);
		cb.restoreState();

		// the back, turned half round about the card's top half
		cb.saveState();
		cb.concatCTM(-1, 0, 0, -1, x + w, y + h);
		List<String> name = lines(bold, card.name(), z.back(), room);
		if (!forStaff)
		{
			block(cb, bold, z.back(), TEXT, name, w / 2, (half - z.fold() + z.edge()) / 2);
		} else
		{
			// from the top: the name, the table and seat, a line for each dish, the dietary needs
			float yLine = half - z.fold() - z.back();
			for (String line : name)
			{
				text(cb, bold, z.back(), TEXT, line, w / 2, yLine);
				yLine -= z.back() * 1.15f;
			}
			yLine -= z.place() * 0.6f;
			text(cb, regular, z.place(), GREY, card.place(), w / 2, yLine);
			yLine -= z.small() * 0.5f;
			for (String dish : card.dishes())
			{
				yLine -= z.small() * 1.3f;
				text(cb, regular, z.small(), TEXT, dish, w / 2, yLine);
			}
			if (!card.diet().isEmpty())
			{
				yLine -= z.small() * 1.3f;
				text(cb, bold, z.small(), GOLD, card.diet(), w / 2, yLine);
			}
		}
		cb.restoreState();
	}

	// lines centred about a height
	private static void block(PdfContentByte cb, BaseFont font, float size, BaseColor colour, List<String> lines,
			float cx, float middle)
	{
		float lead = size * 1.15f;
		float y = middle + (lines.size() - 1) * lead / 2 - size * 0.35f;
		for (String line : lines)
		{
			text(cb, font, size, colour, line, cx, y);
			y -= lead;
		}
	}

	// the name on one line if it fits, else on two broken between words where most even
	public static List<String> lines(BaseFont font, String s, float size, float room)
	{
		if (font.getWidthPoint(s, size) <= room) return List.of(s);
		String[] words = s.split(" ");
		if (words.length < 2) return List.of(s);
		List<String> best = List.of(s);
		float bestWidth = Float.MAX_VALUE;
		for (int i = 1; i < words.length; i++)
		{
			String a = String.join(" ", java.util.Arrays.copyOfRange(words, 0, i));
			String b = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
			float widest = Math.max(font.getWidthPoint(a, size), font.getWidthPoint(b, size));
			if (widest < bestWidth)
			{
				bestWidth = widest;
				best = List.of(a, b);
			}
		}
		return best;
	}

	// the size, no larger than given, at which the name fits in two lines at most
	private static float twoLineSize(BaseFont font, String s, float size, float room)
	{
		float widest = 0;
		for (String line : lines(font, s, size, room))
			widest = Math.max(widest, font.getWidthPoint(line, size));
		return widest > room ? size * room / widest : size;
	}

	// the size, no larger than given, at which the text fits across
	private static float fit(BaseFont font, String s, float size, float room)
	{
		float w = font.getWidthPoint(s, size);
		return w > room ? size * room / w : size;
	}

	/*
	 * Short marks in the sheet's margins: at each fold and between the rows,
	 * left and right; between the columns, top and bottom. Cards that reach
	 * the sheet's edge have them at the edge itself. A cut is marked more
	 * strongly than a fold.
	 */
	private static void marks(PdfContentByte cb, Rectangle page, Stock stock, float left, float bottom)
	{
		float pw = page.getWidth(), ph = page.getHeight();
		float across = mark(left), down = mark(bottom);
		List<float[]> folds = new ArrayList<>(), cuts = new ArrayList<>();
		for (int r = 1; r < 2 * stock.down; r++)
		{
			float y = bottom + r * stock.height / 2;
			List<float[]> into = r % 2 == 1 ? folds : cuts;  // the middle of a card, or between two
			into.add(new float[] { 0, y, across, y });
			into.add(new float[] { pw, y, pw - across, y });
		}
		for (int c = 1; c < stock.across; c++)
		{
			float x = left + c * stock.width;
			cuts.add(new float[] { x, ph, x, ph - down });
			cuts.add(new float[] { x, 0, x, down });
		}
		drawMarks(cb, folds, cuts);
	}

	// folds light and dashed, cuts darker, heavier and solid
	static void drawMarks(PdfContentByte cb, List<float[]> folds, List<float[]> cuts)
	{
		cb.saveState();
		if (!folds.isEmpty())
		{
			cb.setColorStroke(FOLD);
			cb.setLineWidth(0.5f);
			cb.setLineDash(2, 1.5f, 0);
			for (float[] m : folds)
			{
				cb.moveTo(m[0], m[1]);
				cb.lineTo(m[2], m[3]);
			}
			cb.stroke();
		}
		if (!cuts.isEmpty())
		{
			cb.setColorStroke(CUT);
			cb.setLineWidth(0.9f);
			cb.setLineDash(0);
			for (float[] m : cuts)
			{
				cb.moveTo(m[0], m[1]);
				cb.lineTo(m[2], m[3]);
			}
			cb.stroke();
		}
		cb.restoreState();
	}

	// how long a mark from the sheet's edge: within the margin, clear of the card
	private static float mark(float margin)
	{
		return margin > 16 ? Math.min(14, margin - 4) : 10;
	}

	private static void text(PdfContentByte cb, BaseFont font, float size, BaseColor colour, String s, float x, float y)
	{
		cb.beginText();
		cb.setFontAndSize(font, size);
		cb.setColorFill(colour);
		cb.showTextAligned(PdfContentByte.ALIGN_CENTER, s, x, y, 0);
		cb.endText();
	}
}
