package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tables standing on each other on the plan
class OverlapTest
{
	private static <T extends Table> T at(Plan plan, T t, double x, double y)
	{
		t.moveTo(x, y);
		plan.add(t);
		return t;
	}

	@Test
	void tablesApartDoNotOverlapAndOnTopOfEachOtherDo()
	{
		Plan plan = new Plan();
		RoundTable a = at(plan, new RoundTable(1, "", 8), 200, 200);
		RoundTable b = at(plan, new RoundTable(2, "", 8), 200 + 3 * a.reach(), 200);
		plan.renumber();
		assertTrue(plan.overlaps().isEmpty());
		assertEquals("", plan.overlapText());
		b.moveTo(200 + 1.5 * a.reach(), 200);  // their chairs in each other's
		List<Plan.Overlap> o = plan.overlaps();
		assertEquals(1, o.size());
		assertEquals("Table 1 and Table 2 overlap", plan.overlapText());
		assertFalse(o.get(0).where().isEmpty());
	}

	@Test
	void tablesPushedEndToEndOnlyTouch()
	{
		Plan plan = new Plan();
		RectTable a = at(plan, new RectTable(1, "", 4, 0), 300, 300);
		at(plan, new RectTable(2, "", 4, 0), 300 + a.length(), 300);
		assertTrue(plan.overlaps().isEmpty(), "edge to edge, to make a longer table");
	}

	@Test
	void aChairTurnedOffIsNotThere()
	{
		Plan plan = new Plan();
		RoundTable a = at(plan, new RoundTable(1, "", 8), 200, 200);
		java.awt.geom.Point2D chair = a.seatOnFloor(2);  // not seat 1, which cannot be turned off
		assertTrue(a.footprint().contains(chair));
		a.setDisabled(2, true);
		assertFalse(a.footprint().contains(chair));
		assertTrue(a.footprint().contains(200, 200), "the top still is");
	}
}
