package org.lerot.myreceptionplanner.model;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lerot.myreceptionplanner.model.tables.Table;
import org.lerot.myreceptionplanner.reports.PlaceCardsPdf;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Place cards: one for each seated guest, table by table, on perforated A4 card
class PlaceCardsTest
{
	@TempDir
	Path dir;

	private Event banquet(Occasion o)
	{
		return o.events().stream().filter(e -> e.getName().equals("Banquet")).findFirst().orElseThrow();
	}

	@Test
	void aCardForEachGuestTableByTableInSeatOrder() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event e = banquet(o);
		List<Table> tables = Checks.inGuideOrder(e.getPlan());
		List<PlaceCardsPdf.Card> cards = PlaceCardsPdf.cards(e, tables, false);
		assertEquals(67, cards.size());
		assertEquals("Head table  ·  Seat 1", cards.get(0).place());
		assertTrue(cards.get(0).dishes().isEmpty(), "nothing for the staff unless asked");
		assertTrue(cards.get(13).place().startsWith("Table 1"), cards.get(13).place());
	}

	@Test
	void theBackCanCarryTheDishesInShortAndTheDiet() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event e = banquet(o);
		List<PlaceCardsPdf.Card> cards = PlaceCardsPdf.cards(e, Checks.inGuideOrder(e.getPlan()), true);
		assertTrue(cards.stream().anyMatch(c -> c.dishes().contains("Salted ox tongue")), cards.get(0).toString());
		assertTrue(cards.stream().anyMatch(c -> !c.diet().isEmpty()), "someone has dietary needs");
	}

	@Test
	void aDishIsShortenedToItsName()
	{
		assertEquals("Salted ox tongue", PlaceCardsPdf.shortName("Salted ox tongue with mostarda of quince"));
		assertEquals("Eel of Lake Garda", PlaceCardsPdf.shortName("Eel of Lake Garda, marinated with bay and orange"));
		assertEquals("Roast beef", PlaceCardsPdf.shortName("Roast beef"));
	}

	@Test
	void sixToAnA4SheetForTheDecadryCard() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event e = banquet(o);
		Path pdf = dir.resolve("cards.pdf");
		PlaceCardsPdf.write(o, e, Checks.inGuideOrder(e.getPlan()), PlaceCardsPdf.Stock.DECADRY_6, true, pdf);
		PdfReader r = new PdfReader(pdf.toString());
		assertEquals(12, r.getNumberOfPages(), "67 cards, 6 to a sheet");
		assertEquals(595, r.getPageSizeWithRotation(1).getWidth(), 1);
		String text = PdfTextExtractor.getTextFromPage(r, 1);
		assertTrue(text.contains("Prince Escalus"), text);
		r.close();

		PlaceCardsPdf.write(o, e, Checks.inGuideOrder(e.getPlan()), PlaceCardsPdf.Stock.LARGE_4, false, pdf);
		r = new PdfReader(pdf.toString());
		assertEquals(17, r.getNumberOfPages(), "4 to a sheet");
		r.close();
	}

	@Test
	void aLongNameGoesOnTwoLinesBetweenWords() throws Exception
	{
		com.itextpdf.text.pdf.BaseFont bold = com.itextpdf.text.pdf.BaseFont.createFont(
				com.itextpdf.text.pdf.BaseFont.HELVETICA_BOLD, com.itextpdf.text.pdf.BaseFont.CP1252, false);
		assertEquals(List.of("Lucio"), PlaceCardsPdf.lines(bold, "Lucio", 20, 180));
		assertEquals(List.of("Giovanni da", "Palestrina"), PlaceCardsPdf.lines(bold, "Giovanni da Palestrina", 20, 180));
	}
}
