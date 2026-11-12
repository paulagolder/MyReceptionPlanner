package org.lerot.myreceptionplanner.model;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tables composed of the venue's tables, and table designs from files;
// the counts are those of docs/table-notation.md
class ComposedTest
{
	private static ComposedTable.Piece row(int w, int h, int count, int x, int y, String bare)
	{
		return new ComposedTable.Piece(w, h, count, x, y, false, bare.isEmpty() ? Set.of() : Set.of(bare.split(" ")));
	}

	private static ComposedTable.Piece column(int w, int h, int count, int x, int y, String bare)
	{
		return new ComposedTable.Piece(w, h, count, x, y, true, bare.isEmpty() ? Set.of() : Set.of(bare.split(" ")));
	}

	// arms of three 1 × 2s and a 1 × 1; the bare sides of each arm as given
	private static List<ComposedTable.Piece> horseshoe(String leftArm, String rightArm)
	{
		return List.of(row(2, 1, 5, 0, 0, "near"),
				new ComposedTable.Piece(1, 2, 3, 0, 1, true, union("", leftArm)),
				new ComposedTable.Piece(1, 1, 1, 0, 7, false, union("near", leftArm)),
				new ComposedTable.Piece(1, 2, 3, 9, 1, true, union("", rightArm)),
				new ComposedTable.Piece(1, 1, 1, 9, 7, false, union("near", rightArm)));
	}

	private static Set<String> union(String a, String b)
	{
		java.util.HashSet<String> s = new java.util.HashSet<>(List.of(a.split(" ")));
		if (!b.isEmpty()) s.addAll(List.of(b.split(" ")));
		s.remove("");
		return s;
	}

	private static List<ComposedTable.Piece> square()
	{
		return List.of(row(2, 1, 4, 0, 0, ""), column(1, 2, 3, 7, 1, ""), row(2, 1, 4, 0, 7, ""), column(1, 2, 3, 0, 1, ""));
	}

	private static List<ComposedTable.Piece> tee()
	{
		return List.of(row(2, 1, 5, 0, 0, "near left"), row(1, 1, 1, 10, 0, "near right"), column(1, 2, 4, 5, 1, "near"));
	}

	private static List<ComposedTable.Piece> e(String left, String middle, String right)
	{
		return List.of(row(2, 1, 3, 0, 0, left), row(1, 1, 1, 6, 0, middle), row(2, 1, 3, 7, 0, right),
				column(1, 2, 4, 0, 1, "near"), column(1, 2, 4, 6, 1, "near"), column(1, 2, 4, 12, 1, "near"));
	}

	private static int seats(List<ComposedTable.Piece> pieces, boolean holes)
	{
		ComposedTable t = new ComposedTable(1, "", pieces, holes);
		assertTrue(t.warnings().isEmpty(), t.warnings().toString());
		return t.places();
	}

	@Test
	void theDraftsCountsComeOut()
	{
		assertEquals(40, seats(horseshoe("", ""), false));
		assertEquals(26, seats(horseshoe("right", "left"), false));
		assertEquals(32, seats(square(), false));
		assertEquals(52, seats(square(), true));
		assertEquals(27, seats(tee(), false));
		assertEquals(61, seats(e("near left", "near", "near right"), false));
		assertEquals(67, seats(e("left", "", "right"), false));
	}

	@Test
	void theTWalksAsTheDraftSays()
	{
		assertEquals("step 11 (11 seats) · turn 90 · step 1 · turn 90 · step 5 · turn -90 · step 8 (8 seats) · turn 90 · "
						+ "step 1 · turn 90 · step 8 (8 seats) · turn -90 · step 5 · turn 90 · step 1 · turn 90",
				new ComposedTable(1, "", tee(), false).getWalk().toString());
	}

	@Test
	void theHorseshoeStartsAtTheTopLeftAlongTheTopTable()
	{
		ComposedTable t = new ComposedTable(1, "", horseshoe("", ""), false);
		assertTrue(t.getWalk().toString().startsWith("step 10 (10 seats) · turn 90 · step 8 (8 seats)"), t.getWalk().toString());
		// seat 1 at the left end of the top table, along its far side
		assertTrue(t.seats().get(0).getX() < t.seats().get(9).getX());
		assertEquals(t.seats().get(0).getY(), t.seats().get(9).getY(), 0.01);
	}

	@Test
	void aHollowSquareHasAHoleLeftOpen()
	{
		ComposedTable t = new ComposedTable(1, "", square(), false);
		assertEquals(2, t.getWalk().loops().size());
		assertFalse(t.top().contains(0, 0));
	}

	@Test
	void thePickListCountsTheTablesWhicheverWayRound()
	{
		assertEquals(Map.of("2×1", 9, "1×1", 1), new ComposedTable(1, "", tee(), false).pickList());
		assertEquals("9 × 2×1, 1 × 1×1", new ComposedTable(1, "", tee(), false).pickListText());
		assertEquals("11 × 2×1, 2 × 1×1", new ComposedTable(1, "", horseshoe("", ""), false).pickListText());
	}

	@Test
	void tablesApartOrOnTopOfEachOtherAreRefused()
	{
		IllegalArgumentException apart = assertThrows(IllegalArgumentException.class,
				() -> new ComposedTable(1, "", List.of(row(2, 1, 1, 0, 0, ""), row(2, 1, 1, 5, 0, "")), false));
		assertTrue(apart.getMessage().contains("not all joined"));
		IllegalArgumentException over = assertThrows(IllegalArgumentException.class,
				() -> new ComposedTable(1, "", List.of(row(2, 1, 2, 0, 0, ""), row(1, 1, 1, 1, 0, "")), false));
		assertTrue(over.getMessage().contains("same square, 1 0"), over.getMessage());
	}

	@Test
	void aComposedTableGoesInTheEventsFileByItsTables() throws IOException
	{
		Plan plan = new Plan();
		ComposedTable t = new ComposedTable(3, "Top", square(), true);
		t.setDisabled(40, true);
		plan.add(t);
		Element parent = DocumentHelper.createElement("event");
		PlanXml.write(plan, parent);
		String xml = parent.asXML();
		assertTrue(xml.contains("seated=\"holes\""), xml);
		assertTrue(xml.contains("<tables size=\"1 2\" count=\"3\" at=\"7 1\" going=\"down\"/>"), xml);

		ComposedTable back = (ComposedTable) PlanXml.read(parent.element("tables")).find(3).orElseThrow();
		assertEquals(52, back.places());
		assertEquals(Set.of(40), back.getDisabledSeats());
		assertEquals(t.getWalk().toString(), back.getWalk().toString());
	}

	@Test
	void aBadComposedTableSaysWhatIsWrong() throws Exception
	{
		Element tables = DocumentHelper.parseText("""
				<tables><table id="4" shape="composed"><tables size="2" at="0 0"/></table></tables>""").getRootElement();
		assertTrue(assertThrows(IOException.class, () -> PlanXml.read(tables)).getMessage()
				.contains("size=\"2\" should be two whole numbers"));
		Element side = DocumentHelper.parseText("""
				<tables><table id="4" shape="composed"><tables size="2 1" bare="top"/></table></tables>""").getRootElement();
		assertTrue(assertThrows(IOException.class, () -> PlanXml.read(side)).getMessage().startsWith("Table 4:"));
	}

	// ---- designs ----

	@Test
	void thePlannersOwnDesignsAreTheTAndTheE(@TempDir Path root) throws IOException
	{
		Path folder = TableDesigns.folder(root);
		TableDesigns.Design t = TableDesigns.load(folder.resolve("t-top-table.xml"), 12);
		assertEquals(27, t.table().places());
		assertEquals(12, t.table().getId());
		assertTrue(t.head(), "the design would have it be the head table");
		assertFalse(t.table().isHead(), "but that is for the plan to say");
		assertEquals(61, TableDesigns.load(folder.resolve("e-top-table.xml"), 13).table().places());
	}

	@Test
	void theDesignsAreCopiedOnceAndThenLeftAlone(@TempDir Path root) throws IOException
	{
		Path folder = TableDesigns.folder(root);
		Files.delete(folder.resolve("e-top-table.xml"));
		Files.writeString(folder.resolve("t-top-table.xml"), "<table shape=\"round\" places=\"6\"/>");
		TableDesigns.folder(root);
		assertFalse(Files.exists(folder.resolve("e-top-table.xml")));
		assertEquals(6, TableDesigns.load(folder.resolve("t-top-table.xml"), 1).table().places());
	}

	@Test
	void aBadDesignNamesItsFile(@TempDir Path root) throws IOException
	{
		Path odd = root.resolve("odd.xml");
		Files.writeString(odd, "<tables/>");
		assertTrue(assertThrows(IOException.class, () -> TableDesigns.load(odd, 1)).getMessage().startsWith("odd.xml is not"));
		Path apart = root.resolve("apart.xml");
		Files.writeString(apart, "<table shape=\"composed\"><tables size=\"1 1\"/><tables size=\"1 1\" at=\"3 0\"/></table>");
		String message = assertThrows(IOException.class, () -> TableDesigns.load(apart, 9)).getMessage();
		assertEquals("apart.xml: the tables are not all joined together", message);
	}
}
