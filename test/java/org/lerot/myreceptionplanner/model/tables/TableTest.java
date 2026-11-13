package org.lerot.myreceptionplanner.model.tables;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Table shapes: places, chairs and finding a table under the mouse
class TableTest
{
	@Test
	void roundTableHasAChairForEachPlaceStartingAtTheTop()
	{
		RoundTable t = new RoundTable(1, "Table 1", 10);
		List<Point2D> seats = t.seats();
		assertEquals(10, seats.size());
		assertEquals(0, seats.get(0).getX(), 0.001);
		assertTrue(seats.get(0).getY() < 0, "seat 1 is above the centre");
		for (Point2D s : seats)
			assertFalse(t.top().contains(s), "chairs are round the table, not on it");
	}

	@Test
	void roundTableGrowsWithItsPlaces()
	{
		assertTrue(new RoundTable(1, "", 12).diameter() > new RoundTable(2, "", 8).diameter());
	}

	@Test
	void rectTableHasPlacesOnBothSidesAndBothEnds()
	{
		RectTable t = new RectTable(1, "Top", 6, 1);
		assertEquals(14, t.places());
		assertEquals(14, t.seats().size());
		assertEquals(360, t.length(), 0.001);
	}

	@Test
	void rectTableCanHaveNoEndPlaces()
	{
		RectTable t = new RectTable(1, "Top", 4, 0);
		assertEquals(8, t.seats().size());
	}

	@Test
	void containsFollowsTheTableWhenItIsTurned()
	{
		// 240 cm long and 80 wide, turned a quarter so it runs top to bottom
		RectTable t = new RectTable(1, "Long", 4, 0);
		t.moveTo(500, 500);
		t.setRotation(90);
		assertTrue(t.contains(new Point2D.Double(500, 600)));
		assertFalse(t.contains(new Point2D.Double(600, 500)));
	}

	@Test
	void rotationStaysBetween0And360()
	{
		RoundTable t = new RoundTable(1, "", 8);
		t.setRotation(-15);
		assertEquals(345, t.getRotation(), 0.001);
		t.setRotation(375);
		assertEquals(15, t.getRotation(), 0.001);
	}

	@Test
	void thereIsOnlyOneHeadTable()
	{
		Plan plan = new Plan();
		Table a = new RoundTable(1, "A", 8);
		Table b = new RectTable(2, "B", 5, 0);
		plan.add(a);
		plan.add(b);
		plan.setHead(a);
		plan.setHead(b);
		assertFalse(a.isHead());
		assertTrue(b.isHead());
		assertEquals(b, plan.head().orElseThrow());
	}

	@Test
	void seatsAreNumberedClockwiseFromSeatOne()
	{
		RoundTable t = new RoundTable(1, "", 8);
		assertEquals(1, t.seatNumber(0));
		t.setFirstSeat(6);
		assertEquals(1, t.seatNumber(6));
		assertEquals(2, t.seatNumber(7));
		assertEquals(3, t.seatNumber(0));
		assertEquals(8, t.seatNumber(5));
	}

	@Test
	void disabledSeatsAreNotUsablePlaces()
	{
		Plan plan = new Plan();
		RectTable t = new RectTable(1, "", 4, 1);
		plan.add(t);
		t.setDisabled(3, true);
		t.setDisabled(4, true);
		assertTrue(t.isDisabled(3));
		assertEquals(10, t.places());
		assertEquals(8, t.usablePlaces());
		assertEquals(8, plan.places());
		t.setDisabled(3, false);
		assertEquals(9, t.usablePlaces());
	}

	@Test
	void seatOneCannotBeDisabled()
	{
		RoundTable t = new RoundTable(1, "", 8);
		t.setDisabled(2, true);
		t.setFirstSeat(2);
		assertFalse(t.isDisabled(2), "making a seat seat 1 enables it");
		t.setDisabled(2, true);
		assertFalse(t.isDisabled(2));
	}

	@Test
	void changingThePlacesResetsTheSeats()
	{
		RoundTable t = new RoundTable(1, "", 8);
		t.setFirstSeat(3);
		t.setDisabled(5, true);
		t.setPlaces(8);  // no change
		assertEquals(3, t.getFirstSeat());
		t.setPlaces(10);
		assertEquals(0, t.getFirstSeat());
		assertTrue(t.getDisabledSeats().isEmpty());
	}

	@Test
	void chairAtFindsTheChairUnderTheMouseWhenTurned()
	{
		RoundTable t = new RoundTable(1, "", 8);
		t.moveTo(500, 500);
		t.setRotation(90);  // the starting chair is now on the right
		Point2D first = t.seatOnFloor(0);
		assertTrue(first.getX() > 500);
		assertEquals(0, t.chairAt(first));
		assertEquals(-1, t.chairAt(new Point2D.Double(500, 500)));
	}

	@Test
	void aNewHeadTableSeatsOnlySeatOnesSideAndTheEnds()
	{
		Plan plan = new Plan();
		RectTable t = new RectTable(1, "Top", 4, 1);  // chairs: 0-3 far side, 4 right, 5-8 near side, 9 left
		plan.add(t);
		plan.setHead(t);
		assertEquals(java.util.Set.of(5, 6, 7, 8), t.getDisabledSeats());
		assertEquals(6, t.usablePlaces());
	}

	@Test
	void theHeadTableDisablesTheSideAcrossFromSeatOne()
	{
		Plan plan = new Plan();
		RectTable t = new RectTable(1, "Top", 3, 0);  // 0-2 far side, 3-5 near side
		plan.add(t);
		t.setFirstSeat(4);
		plan.setHead(t);
		assertEquals(java.util.Set.of(0, 1, 2), t.getDisabledSeats());
	}

	@Test
	void theHeadTableKeepsItsSeatingWhenItsPlacesChange()
	{
		Plan plan = new Plan();
		RectTable t = new RectTable(1, "Top", 3, 0);
		plan.add(t);
		plan.setHead(t);
		t.setDisabled(4, false);
		t.setPlaces(5, 0);
		assertEquals(java.util.Set.of(5, 6, 7, 8, 9), t.getDisabledSeats());
	}

	@Test
	void loadingAHeadTableKeepsTheSeatsAsSaved() throws Exception
	{
		org.dom4j.Element tables = org.dom4j.DocumentHelper.parseText("<tables><table id=\"1\" shape=\"rectangle\" "
				+ "sideplaces=\"3\" endplaces=\"0\" head=\"true\" disabled=\"4\"/></tables>").getRootElement();
		Table t = PlanXml.read(tables).find(1).orElseThrow();
		assertEquals(java.util.Set.of(3), t.getDisabledSeats());
	}

	@Test
	void aRoundHeadTableKeepsAllItsSeats()
	{
		Plan plan = new Plan();
		RoundTable t = new RoundTable(1, "", 8);
		plan.add(t);
		plan.setHead(t);
		assertTrue(t.getDisabledSeats().isEmpty());
	}

	@Test
	void renumberingGoesInRowsFromTheTopLeftToRight()
	{
		Plan plan = new Plan();
		RoundTable a = at(plan, 1, 600, 300);
		RoundTable b = at(plan, 2, 200, 340);  // a little lower, same row
		RoundTable c = at(plan, 3, 400, 700);
		RoundTable d = at(plan, 4, 100, 720);
		plan.renumber();
		assertEquals(2, a.getNumber());
		assertEquals(1, b.getNumber());
		assertEquals(4, c.getNumber());
		assertEquals(3, d.getNumber());
		assertEquals("Table 3", plan.label(d));
	}

	@Test
	void numbersStayWhenTablesMoveUntilRenumbered()
	{
		Plan plan = new Plan();
		RoundTable a = at(plan, 1, 200, 300);
		RoundTable b = at(plan, 2, 600, 300);
		plan.renumber();
		a.moveTo(900, 300);
		assertEquals(1, a.getNumber());
		plan.renumber();
		assertEquals(2, a.getNumber());
		assertEquals(1, b.getNumber());
		assertEquals(1, a.getId(), "the id stays");
	}

	@Test
	void aNewTableTakesTheNextNumber()
	{
		Plan plan = new Plan();
		at(plan, 1, 200, 300);
		RoundTable b = at(plan, 2, 600, 300);
		plan.renumber();
		RoundTable c = at(plan, 3, 50, 50);
		plan.numberUnnumbered();
		assertEquals(3, c.getNumber());
		assertEquals(2, b.getNumber());
	}

	@Test
	void theHeadTableAndNamedTablesHaveANumberBehindTheirName()
	{
		Plan plan = new Plan();
		RectTable head = new RectTable(1, "", 5, 0);
		head.moveTo(500, 100);
		plan.add(head);
		plan.setHead(head);
		RoundTable smiths = at(plan, 2, 200, 400);
		RoundTable other = at(plan, 3, 600, 400);
		smiths.setName("  Smith family ");
		plan.renumber();
		assertEquals("Head table", plan.label(head));
		assertEquals(1, head.getNumber());
		assertEquals("Smith family", plan.label(smiths));
		assertEquals(2, smiths.getNumber());
		assertEquals("Table 3", plan.label(other), "the numbers shown skip those behind names");
		smiths.setName("");
		plan.numberUnnumbered();
		assertEquals("Table 2", plan.label(smiths), "losing its name, it goes by its number");
		head.setName("Top table");
		assertEquals("Top table", plan.label(head));
		assertEquals(1, head.getNumber());
		assertFalse(head.goesByNumber());
		assertTrue(other.goesByNumber());
	}

	private static RoundTable at(Plan plan, int id, double x, double y)
	{
		RoundTable t = new RoundTable(id, "", 8);
		t.moveTo(x, y);
		plan.add(t);
		return t;
	}

	@Test
	void aDuplicateIsTheSameTableOffsetAndGoesItsOwnWay()
	{
		Plan plan = new Plan();
		RectTable head = new RectTable(1, "Top table", 4, 1);
		head.moveTo(500, 300);
		head.setRotation(30);
		plan.add(head);
		plan.setHead(head);           // disables the far side
		head.setFirstSeat(6);
		plan.renumber();

		Table copy = head.duplicate(plan.nextId(), 80, 80);
		plan.add(copy);
		plan.numberUnnumbered();
		assertEquals(2, copy.getId());
		assertEquals(head.arrangement(), copy.arrangement());
		assertEquals(580, copy.getX());
		assertEquals(380, copy.getY());
		assertEquals(30, copy.getRotation());
		assertEquals(6, copy.getFirstSeat());
		assertEquals(head.getDisabledSeats(), copy.getDisabledSeats());
		assertFalse(copy.isHead(), "only one head table");
		assertFalse(copy.hasName());
		assertEquals("Table 2", plan.label(copy), "the next number after the head table's");

		((RectTable) copy).setPlaces(6, 0);
		copy.setRotation(0);
		assertEquals(4, head.getSidePlaces(), "the original is not changed");
		assertEquals(30, head.getRotation());
	}
}
