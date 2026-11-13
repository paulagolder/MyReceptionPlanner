package org.lerot.myreceptionplanner.model;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lerot.myreceptionplanner.reports.TableListPdf;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// A table's list of guests, to stand on the table
class TableListTest
{
	@TempDir
	Path dir;

	private Event banquet(Occasion o)
	{
		return o.events().stream().filter(e -> e.getName().equals("Banquet")).findFirst().orElseThrow();
	}

	@Test
	void theGuestsComeInSeatOrderFromSeatOne() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event e = banquet(o);
		Table t = e.getPlan().tables().stream().filter(x -> x.getNumber() == 1 && !x.isHead()).findFirst().orElseThrow();
		List<TableListPdf.Entry> before = TableListPdf.entries(t, e.getSeating());
		assertEquals(8, before.size());
		assertEquals(1, before.get(0).seat());

		// seat 1 moved on a chair: the same guests, starting from the new seat 1
		t.setFirstSeat(1);
		List<TableListPdf.Entry> after = TableListPdf.entries(t, e.getSeating());
		assertEquals(before.get(1).name(), after.get(0).name());
		for (int i = 1; i < after.size(); i++)
			assertTrue(after.get(i).seat() > after.get(i - 1).seat());
	}

	@Test
	void emptyAndDisabledSeatsAreLeftOut()
	{
		Plan plan = new Plan();
		RoundTable t = new RoundTable(1, "", 6);
		plan.add(t);
		Seating seating = new Seating();
		Guest ann = new Guest(1, new Name("", "Ann", "Smith"), Gender.FEMALE, null);
		Guest bob = new Guest(2, new Name("", "Bob", "Jones"), Gender.MALE, null);
		seating.seat(ann, new Seat(1, 0));
		seating.seat(bob, new Seat(1, 3));
		t.setDisabled(2, true);
		List<TableListPdf.Entry> entries = TableListPdf.entries(t, seating);
		assertEquals(List.of(1, 4), entries.stream().map(TableListPdf.Entry::seat).toList());
		assertEquals("Ann Smith", entries.get(0).name());
	}

	@Test
	void aNamedTableHasItsNumberUnderneath()
	{
		RoundTable t = new RoundTable(1, "Smith family", 8);
		t.setNumber(3);
		assertEquals("Table 3", TableListPdf.subtitle(t));
		RoundTable plain = new RoundTable(2, "", 8);
		plain.setNumber(4);
		assertEquals("", TableListPdf.subtitle(plain), "the number is already its title");
		Plan plan = new Plan();
		RoundTable head = new RoundTable(3, "Top table", 8);
		plan.add(head);
		plan.setHead(head);
		head.setNumber(1);
		assertEquals("", TableListPdf.subtitle(head), "not the head table's");
	}

	@Test
	void theCardsAreA5AndFoldedA4() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event e = banquet(o);
		Table head = e.getPlan().head().orElseThrow();

		Path a5 = dir.resolve("a5.pdf");
		TableListPdf.write(o, e, head, TableListPdf.Card.A5, a5);
		PdfReader r = new PdfReader(a5.toString());
		assertEquals(1, r.getNumberOfPages());
		assertEquals(420, r.getPageSizeWithRotation(1).getWidth(), 1);
		String text = PdfTextExtractor.getTextFromPage(r, 1);
		assertTrue(text.contains("Head table"), text);
		assertTrue(text.contains("Banquet"), text);
		String first = TableListPdf.entries(head, e.getSeating()).get(0).name();
		assertTrue(text.contains(first), text);
		r.close();

		Path a4 = dir.resolve("a4.pdf");
		TableListPdf.write(o, e, head, TableListPdf.Card.A4_FOLDED, a4);
		r = new PdfReader(a4.toString());
		assertEquals(842, r.getPageSizeWithRotation(1).getWidth(), 1);
		text = PdfTextExtractor.getTextFromPage(r, 1);
		assertEquals(2, text.split("Head table", -1).length - 1, "front and back");
		r.close();
	}

	@Test
	void allTablesAreAPageEachFoldedOrTwoA5ToASheet() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event e = banquet(o);
		List<Table> tables = e.getPlan().tables();  // the head table and six others
		assertEquals(7, tables.size());

		Path folded = dir.resolve("folded.pdf");
		TableListPdf.writeAll(o, e, tables, TableListPdf.Card.A4_FOLDED, folded);
		PdfReader r = new PdfReader(folded.toString());
		assertEquals(7, r.getNumberOfPages());
		r.close();

		Path a5 = dir.resolve("a5s.pdf");
		TableListPdf.writeAll(o, e, tables, TableListPdf.Card.A5, a5);
		r = new PdfReader(a5.toString());
		assertEquals(4, r.getNumberOfPages(), "two to a sheet, the last on its own");
		assertEquals(842, r.getPageSizeWithRotation(1).getWidth(), 1);
		String first = PdfTextExtractor.getTextFromPage(r, 1);
		assertTrue(first.contains(e.getPlan().label(tables.get(0))) && first.contains(e.getPlan().label(tables.get(1))), first);
		r.close();
	}

	@Test
	void theSmallerCardsGoFourOrTwoToAnA4Sheet() throws Exception
	{
		Occasion o = OccasionXml.load(Example.install(dir));
		Event e = banquet(o);
		List<Table> tables = e.getPlan().tables();  // 7

		Path a6 = dir.resolve("a6.pdf");
		TableListPdf.writeAll(o, e, tables, TableListPdf.Card.A6, a6);
		PdfReader r = new PdfReader(a6.toString());
		assertEquals(2, r.getNumberOfPages(), "four to a sheet");
		assertEquals(595, r.getPageSizeWithRotation(1).getWidth(), 1, "A4 upright");
		String first = PdfTextExtractor.getTextFromPage(r, 1);
		for (int i = 0; i < 4; i++)
			assertTrue(first.contains(e.getPlan().label(tables.get(i))), first);
		r.close();

		Path folded = dir.resolve("a5folded.pdf");
		TableListPdf.writeAll(o, e, tables, TableListPdf.Card.A5_FOLDED, folded);
		r = new PdfReader(folded.toString());
		assertEquals(4, r.getNumberOfPages(), "two to a sheet");
		first = PdfTextExtractor.getTextFromPage(r, 1);
		assertEquals(2, first.split("Head table", -1).length - 1, "front and back");
		r.close();

		// one table on its own: a page its own size
		Path one = dir.resolve("one.pdf");
		TableListPdf.write(o, e, tables.get(0), TableListPdf.Card.A6, one);
		r = new PdfReader(one.toString());
		assertEquals(298, r.getPageSizeWithRotation(1).getWidth(), 1);
		r.close();
		TableListPdf.write(o, e, tables.get(0), TableListPdf.Card.A5_FOLDED, one);
		r = new PdfReader(one.toString());
		assertEquals(595, r.getPageSizeWithRotation(1).getWidth(), 1, "A5 across");
		r.close();
	}
}
