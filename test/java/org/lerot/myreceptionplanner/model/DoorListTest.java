package org.lerot.myreceptionplanner.model;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lerot.myreceptionplanner.reports.DoorListPdf;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The door list: everyone expected, by surname, a box to tick each
class DoorListTest
{
	@TempDir
	Path dir;

	private Event event(Occasion o, String name)
	{
		return o.events().stream().filter(e -> e.getName().equals(name)).findFirst().orElseThrow();
	}

	private DoorListPdf.Entry entry(List<DoorListPdf.Entry> entries, String name)
	{
		return entries.stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void placesGoWithTheLeadAndOthersShowTheirParty()
	{
		GuestList list = new GuestList();
		Party smiths = list.createParty("Ann Smith", 3, "The Smiths");  // Ann, +1, +2
		Guest bob = list.addGuest("Bob Smith", Gender.MALE, smiths);
		Guest zed = list.addGuest("Zed Adams", Gender.MALE, null);
		Plan plan = new Plan();
		RoundTable t = new RoundTable(1, "Top table", 4);
		plan.add(t);
		Event ball = new Event("Ball", "ball.xml", plan, new Seating());
		ball.setKind(Event.Kind.HALL);
		for (Guest g : list.guests()) ball.invite(g);
		ball.setInvitation(list.membersOf(smiths).get(0), Invitation.ACCEPTED, list);
		ball.setInvitation(bob, Invitation.ACCEPTED, list);
		ball.getSeating().seat(zed, new Seat(1, 0));

		List<DoorListPdf.Entry> entries = DoorListPdf.entries(ball, list.guests());
		assertEquals(List.of("Zed Adams", "Ann Smith", "Bob Smith"), entries.stream().map(DoorListPdf.Entry::name).toList(),
				"by surname; the places not named are not lines of their own");
		DoorListPdf.Entry ann = entry(entries, "Ann Smith");
		assertEquals(2, ann.places());
		assertEquals("", ann.party(), "a lead is their party");
		assertFalse(ann.noReply());
		assertEquals("The Smiths", entry(entries, "Bob Smith").party());
		DoorListPdf.Entry z = entry(entries, "Zed Adams");
		assertEquals("Top table · 1", z.seat());
		assertTrue(z.noReply(), "invited, not yet replied");
		assertEquals("A", z.letter());
	}

	@Test
	void theListSplitsAtTheLetterNearestHalfway() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event banquet = event(o, "Banquet");
		List<DoorListPdf.Entry> entries = DoorListPdf.entries(banquet, o.getGuests().guests());
		String at = DoorListPdf.splitLetter(entries);
		assertNotNull(at);
		int first = (int) entries.stream().filter(e -> e.letter().compareTo(at) < 0).count();
		assertTrue(Math.abs(entries.size() - 2 * first) < entries.size() / 3, first + " of " + entries.size());
		assertArrayEquals(new String[] { "A–" + (char) (at.charAt(0) - 1), at + "–Z" }, DoorListPdf.halves(at));
	}

	@Test
	void theSheetsListEveryoneWithTheTotalsAtTheEnd() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event banquet = event(o, "Banquet");
		Path pdf = dir.resolve("door.pdf");
		DoorListPdf.write(o, banquet, true, pdf);
		PdfReader r = new PdfReader(pdf.toString());
		assertEquals(2, r.getNumberOfPages(), "a half on each");
		assertEquals(595, r.getPageSizeWithRotation(1).getWidth(), 1, "A4 portrait");
		String first = PdfTextExtractor.getTextFromPage(r, 1), last = PdfTextExtractor.getTextFromPage(r, 2);
		assertTrue(first.contains("Door list"), first);
		assertTrue(first.contains("Juliet Capulet"), first);
		assertTrue(last.contains("expected:"), last);
		r.close();
	}
}
