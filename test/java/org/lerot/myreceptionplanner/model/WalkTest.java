package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The walk round a table's edge that ovals and polygons are made from: seats and outline
class WalkTest
{
	private static final double U = Table.SEAT_SPACING;

	// the user's top table: 20 seats along the far side, 2 deep
	private static Walk topTable()
	{
		return new Walk().step(20, 20).turn(90).step(2, 0).turn(90).step(20, 0).turn(90).step(2, 0);
	}

	// a table of any walk, as ovals and polygons are
	private static WalkTable table(Walk walk)
	{
		return new WalkTable(1, "", walk)
		{
			public String shape() { return "test"; }

			public String arrangement() { return "test " + walk; }

			protected Table sameShape(int id) { return null; }
		};
	}

	@Test
	void aTopTableHasItsSeatsAlongTheFarSideInOrder()
	{
		WalkTable t = table(topTable());
		assertEquals(20, t.places());
		Rectangle2D top = t.top().getBounds2D();
		assertEquals(20 * U, top.getWidth(), 0.01);
		assertEquals(2 * U, top.getHeight(), 0.01);
		assertEquals(0, top.getCenterX(), 0.01, "about its centre");
		List<Point2D> seats = t.seats();
		for (int i = 0; i < seats.size(); i++)
		{
			assertTrue(seats.get(i).getY() < top.getMinY(), "seat " + (i + 1) + " is beyond the far side");
			assertEquals(top.getMinX() + (i + 0.5) * U, seats.get(i).getX(), 0.01);
		}
		assertTrue(t.warnings().isEmpty());
	}

	@Test
	void theLastTurnCanBeLeftOut()
	{
		assertEquals(20, table(topTable().turn(90)).places());
	}

	@Test
	void aWalkThatDoesNotComeBackIsRefused()
	{
		Walk w = new Walk().step(20, 20).turn(90).step(2, 0).turn(90).step(19, 0).turn(90).step(2, 0);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> table(w));
		assertTrue(e.getMessage().contains("ends 1 across"), e.getMessage());
	}

	@Test
	void aStepHoldsNoMoreSeatsThanItsLength()
	{
		assertThrows(IllegalArgumentException.class, () -> new Walk().step(3, 4));
		new Walk().step(3, 2);  // fewer is fine
	}

	@Test
	void aRoundTableIsOneArcAndFilled()
	{
		WalkTable t = table(new Walk().arc(360, 10));
		assertEquals(10, t.places());
		assertTrue(t.top().contains(0, 0), "the middle is table");
		for (Point2D s : t.seats())
			assertFalse(t.top().contains(s));
		assertEquals(10 / Math.PI * U, t.top().getBounds2D().getWidth(), 0.5);
	}

	@Test
	void seatsTooCloseTogetherAreWarnedOf()
	{
		// an L: both edges of the inside corner seated
		Walk w = new Walk().step(4, 4).turn(90).step(4, 4).turn(90).step(2, 2).turn(90).step(2, 2)
				.turn(-90).step(2, 2).turn(90).step(2, 2);
		WalkTable t = table(w);
		assertFalse(t.warnings().isEmpty());
		assertTrue(t.warnings().get(0).startsWith("Seats 12 and 13"), t.warnings().toString());
	}

	@Test
	void olderShapesAreLeftOutOnReading() throws Exception
	{
		org.dom4j.Element tables = org.dom4j.DocumentHelper.parseText("""
				<tables><table id="1" shape="walk"><step seats="20"/></table>
				<table id="2" shape="composed"><tables size="2 1" count="3"/></table>
				<table id="3" shape="round" places="8"/></tables>""").getRootElement();
		assertEquals(List.of(3), PlanXml.read(tables).tables().stream().map(Table::getId).toList());
	}

	@Test
	void aWalkedTableMovesAndTurns()
	{
		WalkTable t = table(topTable());
		t.moveTo(1000, 500);
		t.setRotation(90);
		Point2D first = t.seatOnFloor(0);
		assertTrue(first.getX() > 1000, "turned clockwise, the far side is on the right");
		assertTrue(first.getY() < 500, "and seat 1 at the top");
		assertTrue(t.contains(new Point2D.Double(1000, 1000)));
		assertFalse(t.contains(new Point2D.Double(1500, 500)));
	}
}
