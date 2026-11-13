package org.lerot.myreceptionplanner.model;

import com.itextpdf.text.PageSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lerot.myreceptionplanner.model.tables.Plan;
import org.lerot.myreceptionplanner.model.tables.RectTable;
import org.lerot.myreceptionplanner.model.tables.RoundTable;
import org.lerot.myreceptionplanner.model.tables.Table;
import org.lerot.myreceptionplanner.reports.LayoutPlanPdf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

// The table layout signed off for setting up, undone by any change to it
class LayoutApprovalTest
{
	@TempDir
	Path dir;

	private static Event seated()
	{
		Plan plan = new Plan();
		RoundTable a = new RoundTable(1, "", 8);
		a.moveTo(300, 300);
		plan.add(a);
		RectTable b = new RectTable(2, "", 4, 1);
		b.moveTo(700, 300);
		plan.add(b);
		plan.renumber();
		return new Event("Dinner", "dinner.xml", plan, new Seating());
	}

	@Test
	void anyChangeToTheLayoutUndoesTheApprovalAndPuttingItBackRestoresIt()
	{
		Event e = seated();
		Table a = e.getPlan().find(1).orElseThrow();
		assertFalse(e.isLayoutApproved());
		assertEquals("not yet approved", Progress.layout(e).note());
		e.approveLayout("Jane Smith", LocalDate.of(2026, 11, 13));
		assertTrue(e.isLayoutApproved());
		assertEquals(Progress.State.DONE, Progress.layout(e).state());
		assertEquals("approved 13 Nov 2026 by Jane Smith", Progress.layout(e).note());

		a.moveTo(310, 300);
		assertFalse(e.isLayoutApproved());
		assertTrue(e.layoutChangedSinceApproval());
		assertTrue(Progress.layout(e).warn());
		a.moveTo(300, 300);
		assertTrue(e.isLayoutApproved(), "put back exactly");

		a.setDisabled(3, true);
		assertFalse(e.isLayoutApproved(), "a chair turned off");
		a.setDisabled(3, false);
		a.setName("Family");
		assertFalse(e.isLayoutApproved(), "renamed: the name is on the print");
		a.setName("");
		assertTrue(e.isLayoutApproved());
	}

	@Test
	void seatingGuestsAndSeatOneDoNotCount()
	{
		Event e = seated();
		Table a = e.getPlan().find(1).orElseThrow();
		e.approveLayout("", LocalDate.of(2026, 11, 13));
		a.setFirstSeat(4);
		a.setOpen(2, true);
		assertTrue(e.isLayoutApproved());
		assertEquals("13 Nov 2026", e.getApproval().text());
	}

	@Test
	void theApprovalIsKeptInTheEventsFile() throws Exception
	{
		Path from = Example.install(dir);
		Occasion o = OccasionXml.load(from);
		Event e = o.events().stream().filter(x -> !x.getPlan().tables().isEmpty()).findFirst().orElseThrow();
		e.approveLayout("Jane Smith", LocalDate.of(2026, 11, 13));
		OccasionXml.saveEvent(e, from);
		Event read = OccasionXml.load(from).events().stream().filter(x -> x.getFile().equals(e.getFile())).findFirst().orElseThrow();
		assertTrue(read.isLayoutApproved());
		assertEquals("Jane Smith", read.getApproval().by());

		Path pdf = dir.resolve("layout.pdf");
		LayoutPlanPdf.write(o, read, PageSize.A3.rotate(), pdf);
		assertTrue(Files.size(pdf) > 1000);
		assertEquals("Approved 13 Nov 2026 by Jane Smith", LayoutPlanPdf.stamp(read));
	}

	@Test
	void theCountsAreWhatIsToBeSetOut()
	{
		Event e = seated();
		e.getPlan().find(1).orElseThrow().setDisabled(2, true);
		assertEquals("2 tables, 17 chairs", LayoutPlanPdf.counts(e.getPlan()));
	}
}
