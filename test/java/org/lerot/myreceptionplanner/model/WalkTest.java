package org.lerot.myreceptionplanner.model;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tables as a walk round their edge: seats, outline, holes and the event's file
class WalkTest
{
	private static final double U = Table.SEAT_SPACING;

	// the user's top table: 20 seats along the far side, 2 deep
	private static Walk topTable()
	{
		return new Walk().step(20, 20).turn(90).step(2, 0).turn(90).step(20, 0).turn(90).step(2, 0);
	}

	private static Walk ring()
	{
		return new Walk().arc(360, 3.18, 20).hole(0, 1.68).arc(360, 1.5, 0);
	}

	@Test
	void aTopTableHasItsSeatsAlongTheFarSideInOrder()
	{
		WalkTable t = new WalkTable(1, "", topTable());
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
		assertEquals(20, new WalkTable(1, "", topTable().turn(90)).places());
	}

	@Test
	void aWalkThatDoesNotComeBackIsRefused()
	{
		Walk w = new Walk().step(20, 20).turn(90).step(2, 0).turn(90).step(19, 0).turn(90).step(2, 0);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new WalkTable(1, "", w));
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
		WalkTable t = new WalkTable(1, "", new Walk().arc(360, 10));
		assertEquals(10, t.places());
		assertTrue(t.top().contains(0, 0), "the middle is table");
		for (Point2D s : t.seats())
			assertFalse(t.top().contains(s));
		assertEquals(10 / Math.PI * U, t.top().getBounds2D().getWidth(), 0.5);
	}

	@Test
	void aRingHasAHoleAndItsSeatsGoRoundTheOutside()
	{
		WalkTable t = new WalkTable(1, "", ring());
		assertEquals(20, t.places());
		assertFalse(t.top().contains(0, 0), "the hole is not table");
		assertTrue(t.top().contains(2.4 * U, 0), "the ring is");
		for (Point2D s : t.seats())
			assertTrue(s.distance(0, 0) > 3.18 * U, "outside the ring");
	}

	@Test
	void seatsRoundAHoleFaceIntoIt()
	{
		Walk w = new Walk().step(6, 6).turn(90).step(6, 6).turn(90).step(6, 6).turn(90).step(6, 6)
				.hole(2, 2).step(2, 2).turn(90).step(2, 2).turn(90).step(2, 2).turn(90).step(2, 2);
		WalkTable t = new WalkTable(1, "", w);
		assertEquals(32, t.places());
		for (int i = 24; i < 32; i++)
		{
			Point2D s = t.seats().get(i);
			assertTrue(Math.abs(s.getX()) < U && Math.abs(s.getY()) < U, "seat " + (i + 1) + " is in the hole");
		}
	}

	@Test
	void seatsTooCloseTogetherAreWarnedOf()
	{
		// an L: both edges of the inside corner seated
		Walk w = new Walk().step(4, 4).turn(90).step(4, 4).turn(90).step(2, 2).turn(90).step(2, 2)
				.turn(-90).step(2, 2).turn(90).step(2, 2);
		WalkTable t = new WalkTable(1, "", w);
		assertFalse(t.warnings().isEmpty());
		assertTrue(t.warnings().get(0).startsWith("Seats 12 and 13"), t.warnings().toString());
	}

	@Test
	void aWalkedTableMovesAndTurns()
	{
		WalkTable t = new WalkTable(1, "", topTable());
		t.moveTo(1000, 500);
		t.setRotation(90);
		Point2D first = t.seatOnFloor(0);
		assertTrue(first.getX() > 1000, "turned clockwise, the far side is on the right");
		assertTrue(first.getY() < 500, "and seat 1 at the top");
		assertTrue(t.contains(new Point2D.Double(1000, 1000)));
		assertFalse(t.contains(new Point2D.Double(1500, 500)));
	}

	@Test
	void theWalkGoesInTheEventsFileAndComesBack() throws IOException
	{
		Plan plan = new Plan();
		WalkTable t = new WalkTable(5, "Ring", ring());
		t.moveTo(600, 200);
		t.setRotation(30);
		t.setFirstSeat(3);
		t.setDisabled(7, true);
		plan.add(t);
		Element parent = DocumentHelper.createElement("event");
		PlanXml.write(plan, parent);
		assertTrue(parent.asXML().contains("<hole at=\"0 1.68\"><arc by=\"360\" radius=\"1.5\"/></hole>"),
				parent.asXML());

		WalkTable back = (WalkTable) PlanXml.read(parent.element("tables")).find(5).orElseThrow();
		assertEquals("Ring", back.getName());
		assertEquals(ring().toString(), back.getWalk().toString());
		assertEquals(20, back.places());
		assertEquals(3, back.getFirstSeat());
		assertEquals(Set.of(7), back.getDisabledSeats());
		assertEquals(30, back.getRotation(), 0.001);
		assertEquals(t.seatOnFloor(4).distance(back.seatOnFloor(4)), 0, 0.01);
	}

	@Test
	void aWalkCanBeWrittenByHand() throws Exception
	{
		Element tables = DocumentHelper.parseText("""
				<tables><table id="1" shape="walk">
				  <step seats="20"/><turn by="90"/><step length="2"/><turn by="90"/>
				  <step length="20"/><turn by="90"/><step length="2"/>
				</table></tables>""").getRootElement();
		Table t = PlanXml.read(tables).find(1).orElseThrow();
		assertEquals(20, t.places());
	}

	@Test
	void aBadWalkInTheFileSaysWhichTable() throws Exception
	{
		Element tables = DocumentHelper.parseText("""
				<tables><table id="7" shape="walk"><step seats="20"/><turn by="90"/><step length="2"/></table></tables>""")
				.getRootElement();
		IOException e = assertThrows(IOException.class, () -> PlanXml.read(tables));
		assertTrue(e.getMessage().startsWith("Table 7: The walk does not come back"), e.getMessage());

		Element odd = DocumentHelper.parseText("<tables><table id=\"8\" shape=\"walk\"><hop/></table></tables>")
				.getRootElement();
		assertTrue(assertThrows(IOException.class, () -> PlanXml.read(odd)).getMessage().contains("<hop>"));
	}
}
