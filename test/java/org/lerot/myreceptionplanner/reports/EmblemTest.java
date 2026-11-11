package org.lerot.myreceptionplanner.reports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lerot.myreceptionplanner.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The occasion's emblem
class EmblemTest
{
	@TempDir
	Path folder;

	@Test
	void theSamplesCanBeDrawn() throws Exception
	{
		for (String s : Emblem.SAMPLES)
		{
			var doc = Emblem.parse(Emblem.sample(s));
			assertNotNull(doc, s);
			assertTrue(Emblem.widthFor(doc, 100) > 100, "wider than tall");
		}
		assertNull(Emblem.parse("not a picture"));
		assertNull(Emblem.parse(null));
	}

	@Test
	void theEmblemIsKeptBesideTheOccasion() throws Exception
	{
		Occasion o = new Occasion("Wedding", new GuestList());
		o.addEvent(new Event("Breakfast", "b.xml", new Plan(), new Seating()));
		o.setEmblem(Emblem.sample("hearts"));
		OccasionXml.save(o, folder);
		assertTrue(Files.isRegularFile(folder.resolve("emblem.svg")));
		assertEquals(o.getEmblem(), OccasionXml.load(folder).getEmblem());
		o.setEmblem(null);
		OccasionXml.save(o, folder);
		assertFalse(Files.exists(folder.resolve("emblem.svg")));
		assertNull(OccasionXml.load(folder).getEmblem());
	}

	@Test
	void printedDocumentsCarryIt() throws Exception
	{
		Occasion o = new Occasion("Janet and John's Wedding", new GuestList());
		o.setEmblem(Emblem.sample("hearts"));
		Event e = new Event("Wedding Breakfast", "wb.xml", new Plan(), new Seating());
		o.addEvent(e);
		RoundTable t = new RoundTable(1, "", 8);
		t.moveTo(300, 300);
		e.getPlan().add(t);
		e.getPlan().renumber();
		Party p = o.getGuests().createParty("Ann Smith", 1, "");
		o.tidy();
		Path plan = folder.resolve("plan.pdf"), letters = folder.resolve("letters.pdf"), report = folder.resolve("report.pdf");
		TablePlanPdf.write(o, e, com.itextpdf.text.PageSize.A4.rotate(), plan);
		LettersPdf.letters(o, List.of(new LettersPdf.Letter(p, Messages.fill(o.templates().get(0), o, p, null))), letters);
		ReportPdf.write(new CateringReport(o, e), report);
		for (Path f : List.of(plan, letters, report))
			assertTrue(Files.size(f) > 2000, f.getFileName() + " has the picture in it");
	}
}
