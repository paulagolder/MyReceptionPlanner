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
	void onlyATableWithItsOwnNameSavesOne() throws IOException
	{
		Plan plan = new Plan();
		plan.add(new RoundTable(1, "", 8));
		plan.add(new RoundTable(2, "Smith family", 8));
		plan.renumber();
		Path file = folder.resolve(PlanXml.FILE);
		PlanXml.save(plan, file);
		String xml = Files.readString(file);
		assertEquals(1, xml.split("name=", -1).length - 1);
		Plan loaded = PlanXml.load(file);
		assertFalse(loaded.find(1).orElseThrow().hasName());
		assertEquals("Smith family", loaded.find(2).orElseThrow().getName());
	}

	@Test
	void theOldDefaultNameIsReadAsNone() throws IOException
	{
		Path file = folder.resolve(PlanXml.FILE);
		Files.writeString(file, "<tables><table id=\"7\" name=\"Table 7\" shape=\"round\" places=\"8\"/></tables>");
		Table t = PlanXml.load(file).find(7).orElseThrow();
		assertFalse(t.hasName());
		assertEquals(1, t.getNumber(), "an old file is numbered by where its tables stand");
	}

	@Test
	void numbersAreKeptAsSaved() throws IOException
	{
		Path file = folder.resolve(PlanXml.FILE);
		Files.writeString(file, "<tables>"
				+ "<table id=\"1\" number=\"5\" shape=\"round\" places=\"8\" x=\"100\" y=\"100\"/>"
				+ "<table id=\"2\" number=\"2\" shape=\"round\" places=\"8\" x=\"900\" y=\"100\"/>"
				+ "<table id=\"3\" shape=\"round\" places=\"8\" x=\"500\" y=\"100\"/></tables>");
		Plan plan = PlanXml.load(file);
		assertEquals(5, plan.find(1).orElseThrow().getNumber());
		assertEquals(2, plan.find(2).orElseThrow().getNumber());
		assertEquals(6, plan.find(3).orElseThrow().getNumber(), "one without gets the next number");
	}

	@Test
	void nextIdFollowsTheHighest() throws IOException
	{
		Path file = folder.resolve(PlanXml.FILE);
		Files.writeString(file, "<tables><table id=\"7\" shape=\"round\" places=\"8\"/></tables>");
		assertEquals(8, PlanXml.load(file).nextId());
	}
}
