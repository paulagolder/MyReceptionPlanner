package org.lerot.myreceptionplanner.reports;

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
import org.lerot.myreceptionplanner.model.Invitation;
import org.lerot.myreceptionplanner.model.Occasion;
import org.lerot.myreceptionplanner.model.Party;
import org.lerot.myreceptionplanner.model.Plan;
import org.lerot.myreceptionplanner.model.Seat;
import org.lerot.myreceptionplanner.model.Table;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/*
 * A door list, for those on the door of a big event where most have no seat
 * (a ball, a hall): everyone expected, by surname as on the seat finder (the
 * last word of the name, printed as written, under a letter for each
 * initial), each with a box to tick as they come in. A lead brings the
 * places not yet named in their party ("+2"); anyone else in a party has the
 * party's name beside theirs, so the door can tell who came with whom. Those
 * with a reserved seat have it in bold, so they can be shown to it. Guests
 * who have not replied yet are marked so. A4 portrait, two columns, as many
 * pages as it takes; it may be split in two at a letter (A–K, L–Z) for two
 * people on the door. The totals are at the end.
 */
public class DoorListPdf
{
	/*
	 * A guest's line: the name as written, their unnamed places, the party
	 * they came with ("" for a lead or no party), their reserved seat ("" for
	 * none), whether they have yet to reply, and the letter they go under.
	 */
	public record Entry(String name, int places, String party, String seat, boolean noReply, String letter) {}

	// one line of a column: a letter heading, or a guest
	private record Row(String letter, Entry entry) {}

	private static final float MARGIN = 36;
	private static final float GAP = 24;      // between the columns
	private static final float FONT = 9;
	private static final float LINE = 1.45f;  // line height, in font sizes
	private static final BaseColor TEXT = new BaseColor(0x2B, 0x2B, 0x2B);
	private static final BaseColor GREY = new BaseColor(0x6E, 0x6E, 0x72);
	private static final BaseColor LIGHT = new BaseColor(0x8A, 0x8A, 0x8E);
	private static final BaseColor GOLD = new BaseColor(0xB8, 0x86, 0x0B);

	// everyone expected, by surname and then the whole name; unnamed places go with their lead
	public static List<Entry> entries(Event event, List<Guest> guests)
	{
		Plan plan = event.getPlan();
		List<Entry> out = new ArrayList<>();
		for (Guest g : guests)
		{
			if (!event.isInvited(g)) continue;
			Party p = g.getParty();
			boolean withLead = p != null && p.getLead() != null && event.isInvited(p.getLead());
			if (g.isPlaceholder() && withLead) continue;  // counted on the lead's line
			int places = 0;
			if (g.isLead())
				places = (int) guests.stream().filter(o -> o.getParty() == p && o.isPlaceholder() && event.isInvited(o)).count();
			String party = p == null || g.isLead() || p.getName().equals(g.displayName()) ? "" : p.getName();
			String seat = "";
			Seat s = event.getSeating().seatOf(g);
			Table t = s == null ? null : plan.find(s.tableId()).orElse(null);
			if (t != null && s.chair() < t.places()) seat = plan.label(t) + " · " + t.seatNumber(s.chair());
			String name = g.displayName();
			out.add(new Entry(name, places, party, seat, event.invitation(g) != Invitation.ACCEPTED,
					SeatFinderPdf.letter(SeatFinderPdf.surname(name))));
		}
		Collator order = Collator.getInstance(Locale.UK);
		order.setStrength(Collator.PRIMARY);
		out.sort(Comparator.comparing((Entry x) -> SeatFinderPdf.surname(x.name()), order)
				.thenComparing(Entry::name, order));
		return out;
	}

	// the letter that starts the second half, splitting the list as evenly as whole letters allow; null if it cannot be split
	public static String splitLetter(List<Entry> entries)
	{
		String best = null;
		int bestOff = Integer.MAX_VALUE;
		for (int i = 1; i < entries.size(); i++)
		{
			if (entries.get(i).letter().equals(entries.get(i - 1).letter())) continue;
			int off = Math.abs(entries.size() - 2 * i);
			if (off < bestOff)
			{
				bestOff = off;
				best = entries.get(i).letter();
			}
		}
		return best;
	}

	// "A–L" and "M–Z" when the second half starts at M, as for a sign on the door
	public static String[] halves(String at)
	{
		return new String[] { "A–" + (char) (at.charAt(0) - 1), at + "–Z" };
	}

	public static void write(Occasion occasion, Event event, boolean split, Path file) throws IOException
	{
		List<Entry> entries = entries(event, occasion.getGuests().guests());
		List<List<Entry>> parts = new ArrayList<>();
		String at = split ? splitLetter(entries) : null;
		if (at == null) parts.add(entries);
		else
		{
			int i = 0;
			while (!entries.get(i).letter().equals(at)) i++;
			parts.add(entries.subList(0, i));
			parts.add(entries.subList(i, entries.size()));
		}
		try
		{
			BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont italic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			Files.createDirectories(file.toAbsolutePath().getParent());
			Rectangle page = PageSize.A4;
			Document doc = new Document(page);
			try (OutputStream out = Files.newOutputStream(file))
			{
				PdfWriter writer = PdfWriter.getInstance(doc, out);
				doc.addTitle(occasion.getName() + " – " + event.getName() + " – door list");
				doc.addCreator("MyReceptionPlanner");
				doc.open();
				PdfContentByte cb = writer.getDirectContent();
				float w = page.getWidth(), h = page.getHeight();
				float colWidth = (w - 2 * MARGIN - GAP) / 2;
				float listTop = h - MARGIN - 52, bottom = MARGIN + 14;
				int fit = (int) Math.floor((listTop - bottom) / (FONT * LINE));
				for (int p = 0; p < parts.size(); p++)
				{
					List<Entry> part = parts.get(p);
					String range = parts.size() == 1 ? "" : halves(at)[p];
					List<List<Row>> pages = pages(rows(part), fit);
					// the totals, at the end, need a few lines of their own
					boolean last = p == parts.size() - 1;
					List<String> totals = last ? totals(event, entries) : List.of();
					int sheet = 0;
					for (List<Row> rows : pages)
					{
						if (p > 0 || sheet > 0) doc.newPage();
						sheet++;
						heading(cb, regular, bold, occasion, event, range, w, h);
						text(cb, regular, 7.5f, LIGHT, (range.isEmpty() ? "" : range + "  ·  ") + "page " + sheet + " of " + pages.size(),
								w - MARGIN, MARGIN, PdfContentByte.ALIGN_RIGHT);
						List<Row> left = rows.subList(0, Math.min(fit, rows.size()));
						List<Row> right = rows.subList(left.size(), rows.size());
						column(cb, regular, bold, italic, left, MARGIN, listTop, colWidth);
						column(cb, regular, bold, italic, right, MARGIN + colWidth + GAP, listTop, colWidth);
					}
					if (last)
					{
						// on the last page, under the right column if there is room, or else a page of their own
						List<Row> lastRows = pages.get(pages.size() - 1);
						int used = Math.max(0, lastRows.size() - fit);
						float y = listTop - (used + 1) * FONT * LINE;
						float x = MARGIN + colWidth + GAP;
						if (lastRows.size() <= fit) y = listTop - FONT;
						if (y - totals.size() * FONT * LINE < bottom)
						{
							doc.newPage();
							heading(cb, regular, bold, occasion, event, range, w, h);
							y = listTop - FONT;
							x = MARGIN;
						}
						cb.setColorStroke(LIGHT);
						cb.setLineWidth(0.5f);
						cb.moveTo(x, y + FONT * 1.2f);
						cb.lineTo(x + colWidth, y + FONT * 1.2f);
						cb.stroke();
						for (String line : totals)
						{
							y -= FONT * LINE;
							text(cb, regular, FONT, TEXT, line, x, y, PdfContentByte.ALIGN_LEFT);
						}
					}
				}
				doc.close();
			}
		} catch (DocumentException e)
		{
			throw new IOException("Cannot make the PDF: " + e.getMessage(), e);
		}
	}

	// how many are expected, in a few lines
	static List<String> totals(Event event, List<Entry> entries)
	{
		int names = entries.size();
		int places = entries.stream().mapToInt(Entry::places).sum();
		int noReply = (int) entries.stream().filter(Entry::noReply).count();
		int seats = (int) entries.stream().filter(e -> !e.seat().isEmpty()).count();
		List<String> out = new ArrayList<>();
		out.add((names + places) + " expected: " + names + (names == 1 ? " name" : " names")
				+ (places > 0 ? " and " + places + (places == 1 ? " place" : " places") + " not named (+)" : ""));
		if (noReply > 0) out.add(noReply + " not yet replied (no reply)");
		if (seats > 0) out.add(seats + " with a reserved seat (in bold)");
		if (event.capacityLimit() > 0) out.add("Capacity " + event.capacityLimit()
				+ (event.overCapacity() > 0 ? ": " + event.overCapacity() + " over" : ""));
		return out;
	}

	private static void heading(PdfContentByte cb, BaseFont regular, BaseFont bold, Occasion occasion, Event event,
			String range, float w, float h)
	{
		float top = h - MARGIN;
		text(cb, bold, 16, TEXT, occasion.heading(), w / 2, top - 16, PdfContentByte.ALIGN_CENTER);
		text(cb, regular, 10, GREY, occasion.headingOf(event) + "  ·  Door list" + (range.isEmpty() ? "" : "  ·  " + range),
				w / 2, top - 32, PdfContentByte.ALIGN_CENTER);
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

	// pages of two columns of fit rows; a letter never left at the foot of a column
	private static List<List<Row>> pages(List<Row> rows, int fit)
	{
		List<List<Row>> pages = new ArrayList<>();
		List<Row> page = new ArrayList<>();
		int inColumn = 0;
		for (Row r : rows)
		{
			if (inColumn == fit || r.letter() != null && inColumn == fit - 1)
			{
				while (inColumn < fit) { page.add(new Row(null, null)); inColumn++; }  // fill out the column
				inColumn = 0;
				if (page.size() >= 2 * fit)
				{
					pages.add(page);
					page = new ArrayList<>();
				}
			}
			page.add(r);
			inColumn++;
		}
		if (!page.isEmpty() || pages.isEmpty()) pages.add(page);
		return pages;
	}

	private static void column(PdfContentByte cb, BaseFont regular, BaseFont bold, BaseFont italic, List<Row> rows,
			float x, float top, float width)
	{
		float y = top - FONT;
		float box = FONT * 0.85f;
		float nameX = x + box + FONT * 0.6f;
		for (Row r : rows)
		{
			if (r.letter() != null)
			{
				text(cb, bold, FONT * 1.1f, GOLD, r.letter(), x, y, PdfContentByte.ALIGN_LEFT);
			} else if (r.entry() != null)
			{
				Entry e = r.entry();
				cb.setColorStroke(GREY);
				cb.setLineWidth(0.6f);
				cb.rectangle(x, y - FONT * 0.1f, box, box);
				cb.stroke();
				// the seat, or "no reply", at the right
				String right = !e.seat().isEmpty() ? e.seat() : e.noReply() ? "no reply" : "";
				BaseFont rightFont = !e.seat().isEmpty() ? bold : italic;
				float rightWidth = rightFont.getWidthPoint(right, FONT);
				text(cb, rightFont, FONT, e.seat().isEmpty() ? LIGHT : TEXT, right, x + width, y, PdfContentByte.ALIGN_RIGHT);
				float room = x + width - nameX - (right.isEmpty() ? 0 : rightWidth + FONT);
				// the name whole if it can be, leaving the party what room is over
				String name = fit(regular, e.name(), FONT, room - (e.places() > 0 ? bold.getWidthPoint(" +9", FONT) : 0));
				text(cb, regular, FONT, TEXT, name, nameX, y, PdfContentByte.ALIGN_LEFT);
				float after = nameX + regular.getWidthPoint(name, FONT);
				if (e.places() > 0)
				{
					String plus = " +" + e.places();
					text(cb, bold, FONT, TEXT, plus, after, y, PdfContentByte.ALIGN_LEFT);
					after += bold.getWidthPoint(plus, FONT);
				}
				if (!e.party().isEmpty())
				{
					float gap = FONT * 0.6f;
					String party = fit(italic, e.party(), FONT * 0.9f, nameX + room - after - gap);
					text(cb, italic, FONT * 0.9f, GREY, party, after + gap, y, PdfContentByte.ALIGN_LEFT);
				}
			}
			y -= FONT * LINE;
		}
	}

	// cut short with "…" to fit the room
	private static String fit(BaseFont font, String s, float size, float room)
	{
		if (font.getWidthPoint(s, size) <= room) return s;
		while (s.length() > 1 && font.getWidthPoint(s + "…", size) > room) s = s.substring(0, s.length() - 1);
		return s.length() <= 1 ? "" : s.strip() + "…";
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
