package org.lerot.myreceptionplanner.model;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tables pushed together and combined, each keeping its own identity
class CombineTest
{
	// an L: table 2 on end against the long side of table 1, as in the Table Test event
	private static Plan anL()
	{
		Plan plan = new Plan();
		RectTable one = new RectTable(1, "", 10, 2);
		one.moveTo(935, 249);
		plan.add(one);
		RectTable two = new RectTable(2, "", 10, 2);
		two.moveTo(1170, 609);
		two.setRotation(90);
		plan.add(two);
		plan.renumber();
		return plan;
	}

	@Test
	void tablesPushedTogetherCanBeCombinedAndThenDoNotOverlap()
	{
		Plan plan = anL();
		Table one = plan.find(1).orElseThrow(), two = plan.find(2).orElseThrow();
		assertEquals(List.of(two), plan.touching(one));
		assertEquals(1, plan.overlaps().size(), "pushed together, their chairs are under each other");
		Map<Table, Set<Integer>> blocked = plan.blockedIfCombined(one, two);
		assertEquals(3, blocked.get(one).size(), "the two chairs under table 2's end, and the one at the inside corner");
		assertEquals(3, blocked.get(two).size(), "its end chairs, and the one at the inside corner: both of a clashing pair");

		plan.combine(one, two);
		assertNotEquals(0, one.getGroup());
		assertEquals(one.getGroup(), two.getGroup());
		assertEquals(List.of(one, two), plan.groupOf(two));
		assertTrue(plan.overlaps().isEmpty());
		assertEquals(21, one.usablePlaces());
		assertEquals(21, two.usablePlaces());
		assertTrue(plan.touching(one).isEmpty(), "already combined");
		assertEquals("Table 1", plan.label(one), "each keeps its own number");
		assertEquals("Table 2", plan.label(two));
	}

	@Test
	void combinedTablesMoveAndTurnTogether()
	{
		Plan plan = anL();
		Table one = plan.find(1).orElseThrow(), two = plan.find(2).orElseThrow();
		plan.combine(one, two);
		double dx = two.getX() - one.getX(), dy = two.getY() - one.getY();
		plan.moveGroup(two, 100, 50);
		assertEquals(1035, one.getX(), 1e-9);
		assertEquals(dx, two.getX() - one.getX(), 1e-9);
		plan.turnGroup(one, 90);
		assertEquals(90, one.getRotation(), 1e-9);
		assertEquals(180, two.getRotation(), 1e-9);
		assertEquals(Math.hypot(dx, dy), Math.hypot(two.getX() - one.getX(), two.getY() - one.getY()), 1e-6,
				"still the same distance apart");
		assertTrue(plan.touching(one).isEmpty() && plan.blocked(plan.groupOf(one)).get(two).size() == 3,
				"still pushed together, the same chairs blocked");
	}

	@Test
	void separatingBringsTheBlockedChairsBackButNotThoseTurnedOffByHand()
	{
		Plan plan = anL();
		Table one = plan.find(1).orElseThrow(), two = plan.find(2).orElseThrow();
		one.setDisabled(5, true);  // by hand
		plan.combine(one, two);
		plan.separate(two);
		assertEquals(0, one.getGroup());
		assertEquals(0, two.getGroup());
		assertEquals(Set.of(5), one.getDisabledSeats());
		assertTrue(two.getDisabledSeats().isEmpty());
		assertEquals(1, plan.overlaps().size(), "pushed together but no longer combined");
	}

	@Test
	void deletingOneLeavesTheRestWithTheirChairsBack()
	{
		Plan plan = anL();
		Table one = plan.find(1).orElseThrow(), two = plan.find(2).orElseThrow();
		plan.combine(one, two);
		plan.remove(two);
		assertEquals(0, one.getGroup());
		assertTrue(one.getDisabledSeats().isEmpty());
	}

	@Test
	void pewsAndAltarsAreNotCombined()
	{
		Plan plan = new Plan();
		Pew pew = new Pew(1, "", 6);
		pew.moveTo(300, 300);
		plan.add(pew);
		Pew next = new Pew(2, "", 6);
		next.moveTo(300 + pew.topOnFloor().getBounds2D().getWidth(), 300);
		plan.add(next);
		assertTrue(plan.touching(pew).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> plan.combine(pew, next));
	}

	@Test
	void combiningIsKeptInTheFile() throws Exception
	{
		Plan plan = anL();
		plan.combine(plan.find(1).orElseThrow(), plan.find(2).orElseThrow());
		Element root = DocumentHelper.createDocument().addElement("event");
		PlanXml.write(plan, root);
		assertTrue(root.asXML().contains("combined=\"1\""), root.asXML());
		Plan read = PlanXml.read(root.element("tables"));
		assertEquals(read.find(1).orElseThrow().getGroup(), read.find(2).orElseThrow().getGroup());
		assertNotEquals(0, read.find(1).orElseThrow().getGroup());
		assertTrue(read.overlaps().isEmpty());
	}

	// table 2 at 45° to table 1, its end laid well over table 1's side
	private static Plan aChevronTooFarIn()
	{
		Plan plan = new Plan();
		RectTable one = new RectTable(1, "", 10, 2);
		one.moveTo(600, 400);
		plan.add(one);
		RectTable two = new RectTable(2, "", 7, 1);
		two.setRotation(45);
		two.moveTo(600 + 100, 400 - 230);
		plan.add(two);
		return plan;
	}

	private static boolean topsOverlap(Table a, Table b)
	{
		java.awt.geom.Area x = new java.awt.geom.Area(a.topOnFloor());
		x.intersect(new java.awt.geom.Area(b.topOnFloor()));
		return !x.isEmpty();
	}

	@Test
	void pushingTogetherAtAnAngleLeavesTheCornerOnTheEdge()
	{
		Plan plan = aChevronTooFarIn();
		Table one = plan.find(1).orElseThrow(), two = plan.find(2).orElseThrow();
		assertTrue(topsOverlap(one, two), "laid over it");
		double rotation = two.getRotation();
		assertTrue(plan.pushTogether(two, one));
		assertFalse(topsOverlap(one, two), "drawn back off it");
		assertEquals(List.of(one), plan.touching(two), "but still against it");
		assertEquals(rotation, two.getRotation(), "at the same angle");
		assertEquals(0, one.getX() - 600, 1e-9, "only the one pushed moves");

		two.moveTo(two.getX() + 3, two.getY() - 3);  // a little apart
		assertTrue(plan.pushTogether(two, one));
		assertFalse(topsOverlap(one, two));
		assertEquals(List.of(one), plan.touching(two), "pushed up to it");
	}

	@Test
	void combinedTablesWhoseTopsLieOverEachOtherAreFlagged()
	{
		Plan plan = aChevronTooFarIn();
		Table one = plan.find(1).orElseThrow(), two = plan.find(2).orElseThrow();
		plan.combine(one, two);
		assertEquals(1, plan.overlaps().size(), "combined, but one top over the other");
		assertEquals(List.of(one), plan.lyingOver(two));
		assertTrue(plan.pushTogether(two, one));
		assertTrue(plan.overlaps().isEmpty());
		assertTrue(plan.lyingOver(two).isEmpty());
	}
}
