package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// Saving and loading tables.xml
class PlanXmlTest
{
	@TempDir
	Path folder;

	@Test
	void aSavedPlanLoadsTheSame() throws IOException
	{
		Plan plan = new Plan();
		RoundTable round = new RoundTable(1, "Family", 10);
		round.moveTo(300, 250);
		RectTable top = new RectTable(2, "Top table", 6, 0);
		top.moveTo(700, 120);
		top.setRotation(45);
		plan.add(round);
		plan.add(top);
		plan.setHead(top);
		for (int c = 6; c < 12; c++)
			top.setDisabled(c, false);  // undo the head table seating, then choose our own
		top.setFirstSeat(3);
		top.setDisabled(0, true);
		top.setDisabled(11, true);

		Path file = folder.resolve("SmithWedding").resolve(PlanXml.FILE);
		PlanXml.save(plan, file);
		Plan loaded = PlanXml.load(file);

		assertEquals(2, loaded.tables().size());
		RoundTable r = (RoundTable) loaded.find(1).orElseThrow();
		assertEquals("Family", r.getName());
		assertEquals(10, r.places());
		assertEquals(300, r.getX());
		assertEquals(250, r.getY());
		assertFalse(r.isHead());
		RectTable t = (RectTable) loaded.find(2).orElseThrow();
		assertEquals(6, t.getSidePlaces());
		assertEquals(0, t.getEndPlaces());
		assertEquals(45, t.getRotation());
		assertEquals(3, t.getFirstSeat());
		assertEquals(java.util.Set.of(0, 11), t.getDisabledSeats());
		assertEquals(0, r.getFirstSeat());
		assertTrue(r.getDisabledSeats().isEmpty());
		assertTrue(t.isHead());
	}

	@Test
	void anUnknownShapeIsReported() throws IOException
	{
		Path file = folder.resolve(PlanXml.FILE);
		Files.writeString(file, "<tables><table id=\"1\" shape=\"hexagon\"/></tables>");
		IOException e = assertThrows(IOException.class, () -> PlanXml.load(file));
		assertTrue(e.getMessage().contains("hexagon"));
	}

	@Test
	void aMissingChairIsReported() throws IOException
	{
		Path file = folder.resolve(PlanXml.FILE);
		Files.writeString(file, "<tables><table id=\"1\" shape=\"round\" places=\"8\" disabled=\"9\"/></tables>");
		IOException e = assertThrows(IOException.class, () -> PlanXml.load(file));
		assertTrue(e.getMessage().contains("9"));
	}

	@Test
	void nextIdFollowsTheHighest() throws IOException
	{
		Path file = folder.resolve(PlanXml.FILE);
		Files.writeString(file, "<tables><table id=\"7\" shape=\"round\" places=\"8\"/></tables>");
		assertEquals(8, PlanXml.load(file).nextId());
	}
}
