package org.lerot.myreceptionplanner.model;

import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lerot.myreceptionplanner.reports.SeatFinderPdf;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The seat finder: everyone by surname, with table and seat, round the room
class SeatFinderTest
{
	@TempDir
	Path dir;

	private Event event(Occasion o, String name)
	{
		return o.events().stream().filter(e -> e.getName().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void everyoneSeatedIsListedBySurname() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		List<SeatFinderPdf.Entry> entries = SeatFinderPdf.entries(event(o, "Banquet"));
		assertEquals(67, entries.size());
		List<String> names = entries.stream().map(SeatFinderPdf.Entry::name).toList();
		// "Leonardo da Vinci" under V, "Lorenzo de' Medici" under M, "Isabella d'Este" under D
		assertTrue(names.indexOf("Isabella d'Este") < names.indexOf("Albrecht Dürer"));
		SeatFinderPdf.Entry leonardo = entries.stream().filter(e -> e.name().equals("Leonardo da Vinci")).findFirst().orElseThrow();
		assertEquals("V", leonardo.letter());
		SeatFinderPdf.Entry juliet = entries.stream().filter(e -> e.name().equals("Juliet Capulet")).findFirst().orElseThrow();
		assertTrue(juliet.place().startsWith("Head table · "), juliet.place());
		for (int i = 1; i < entries.size(); i++)
			assertTrue(entries.get(i - 1).letter().compareTo(entries.get(i).letter()) <= 0,
					entries.get(i - 1) + " then " + entries.get(i));
	}

	@Test
	void theSeatNumberIsTheTablesNotTheChairs() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event e = event(o, "Banquet");
		Table head = e.getPlan().head().orElseThrow();
		Guest someone = e.getSeating().seatedAt(head.getId()).get(0);
		int chair = e.getSeating().seatOf(someone).chair();
		head.setFirstSeat((chair + 1) % head.places());
		String place = SeatFinderPdf.entries(e).stream().filter(x -> x.name().equals(someone.displayName()))
				.findFirst().orElseThrow().place();
		assertEquals("Head table · " + head.seatNumber(chair), place);
	}

	@Test
	void theSheetIsLandscapeWithTheNamesAndTheUnseatedCounted() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event ceremony = event(o, "Ceremony");
		assertTrue(SeatFinderPdf.unseated(ceremony) > 0);
		Path pdf = dir.resolve("finder.pdf");
		SeatFinderPdf.write(o, ceremony, PageSize.A3.rotate(), pdf);
		PdfReader r = new PdfReader(pdf.toString());
		assertEquals(1, r.getNumberOfPages());
		assertEquals(1191, r.getPageSizeWithRotation(1).getWidth(), 1);
		String text = PdfTextExtractor.getTextFromPage(r, 1);
		assertTrue(text.contains("Find your seat"), text);
		assertTrue(text.contains("not seated yet"), text);
		assertTrue(text.contains(SeatFinderPdf.entries(ceremony).get(0).name()), text);
		r.close();
	}
}
