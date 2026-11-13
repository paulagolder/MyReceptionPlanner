package org.lerot.myreceptionplanner.model.tables;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Oval and polygon tables, made from walks
class ShapesTest
{
	private static final double U = Table.SEAT_SPACING;

	@Test
	void anOvalFitsItsBoxWithEighteenSeatsIn8By4()
	{
		OvalTable t = new OvalTable(1, "", 8, 4);
		assertEquals(18, t.places());
		Rectangle2D box = t.top().getBounds2D();
		assertEquals(8 * U, box.getWidth(), 1);
		assertEquals(4 * U, box.getHeight(), 1);
		for (Point2D s : t.seats())
			assertFalse(t.top().contains(s));
		assertTrue(t.warnings().isEmpty(), t.warnings().toString());
	}

	@Test
	void seatOneOfAnOvalIsOnTheFarSideToTheLeft()
	{
		Point2D first = new OvalTable(1, "", 8, 4).seats().get(0);
		assertTrue(first.getX() < 0 && first.getY() < 0, first.toString());
	}

	@Test
	void anOvalAsWideAsItIsLongIsRound()
	{
		OvalTable t = new OvalTable(1, "", 4, 4);
		assertEquals(12, t.places());
		assertEquals(t.top().getBounds2D().getWidth(), t.top().getBounds2D().getHeight(), 0.5);
	}

	@Test
	void anOvalWiderThanItIsLongIsRefused()
	{
		assertThrows(IllegalArgumentException.class, () -> new OvalTable(1, "", 3, 4));
	}

	@Test
	void ovalsOfEverySizeComeOutWithoutCrowding()
	{
		for (int length = 1; length <= 30; length++)
		{
			for (int width = 1; width <= length; width++)
			{
				OvalTable t = new OvalTable(1, "", length, width);
				assertTrue(t.places() <= 2 * length + 2 * width, length + "x" + width);
				assertTrue(t.warnings().isEmpty(), length + "x" + width + ": " + t.warnings());
			}
		}
	}

	@Test
	void aHexagonHasItsSidesTimesItsSeats()
	{
		PolygonTable t = new PolygonTable(1, "", 6, 2);
		assertEquals(12, t.places());
		assertEquals(7, new PolygonTable(2, "", 7, 1).places());
		// side 1 flat along the top
		assertEquals(t.seats().get(0).getY(), t.seats().get(1).getY(), 0.01);
		assertTrue(t.seats().get(0).getY() < 0);
	}

	@Test
	void resizingStartsTheSeatsAgain()
	{
		PolygonTable t = new PolygonTable(1, "", 6, 1);
		t.setFirstSeat(2);
		t.setDisabled(4, true);
		t.setSides(8, 1);
		assertEquals(8, t.places());
		assertEquals(0, t.getFirstSeat());
		assertTrue(t.getDisabledSeats().isEmpty());
		OvalTable o = new OvalTable(2, "", 8, 4);
		o.setBox(10, 4);
		assertEquals(10, o.getLength());
	}

	@Test
	void theyGoInTheEventsFileBySizeNotByWalk() throws IOException
	{
		Plan plan = new Plan();
		OvalTable oval = new OvalTable(1, "", 8, 4);
		oval.setDisabled(5, true);
		plan.add(oval);
		PolygonTable hex = new PolygonTable(2, "Hex", 6, 2);
		hex.setRotation(30);
		plan.add(hex);
		Element parent = DocumentHelper.createElement("event");
		PlanXml.write(plan, parent);
		String xml = parent.asXML();
		assertTrue(xml.contains("shape=\"oval\" box=\"8 4\""), xml);
		assertTrue(xml.contains("shape=\"polygon\" sides=\"6\" perside=\"2\""), xml);
		assertFalse(xml.contains("<arc") || xml.contains("<step"), xml);

		Plan back = PlanXml.read(parent.element("tables"));
		OvalTable o = (OvalTable) back.find(1).orElseThrow();
		assertEquals(8, o.getLength());
		assertEquals(Set.of(5), o.getDisabledSeats());
		PolygonTable p = (PolygonTable) back.find(2).orElseThrow();
		assertEquals(12, p.places());
		assertEquals(30, p.getRotation(), 0.001);
	}

	@Test
	void aBadSizeInTheFileSaysWhichTable() throws Exception
	{
		Element tables = DocumentHelper.parseText("<tables><table id=\"3\" shape=\"oval\" box=\"2 5\"/></tables>")
				.getRootElement();
		assertTrue(assertThrows(IOException.class, () -> PlanXml.read(tables)).getMessage().startsWith("Table 3:"));
		Element words = DocumentHelper.parseText("<tables><table id=\"4\" shape=\"oval\" box=\"long wide\"/></tables>")
				.getRootElement();
		assertThrows(IOException.class, () -> PlanXml.read(words));
	}

	@Test
	void aDuplicateKeepsItsShape()
	{
		OvalTable t = new OvalTable(1, "", 10, 4);
		Table copy = t.duplicate(2, 100, 0);
		assertInstanceOf(OvalTable.class, copy);
		assertEquals(t.places(), copy.places());
	}
}
