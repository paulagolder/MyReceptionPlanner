package org.lerot.myreceptionplanner.reports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lerot.myreceptionplanner.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Setting out the table plan on a page
class TablePlanLayoutTest
{
	private static final TablePlanLayout.Measure MEASURE = (s, size, bold) -> s.length() * size * 0.55;

	@TempDir
	Path folder;

	@Test
	void guestsAreListedInSeatOrderWithoutTheEmptySeats()
	{
		Plan plan = new Plan();
		RoundTable t = table(plan, 1, 300, 300, 6);
		t.setFirstSeat(4);
		GuestList guests = new GuestList();
		Seating seating = new Seating();
		seating.seat(guests.addGuest("Chair 0", null, null), new Seat(1, 0));
		seating.seat(guests.addGuest("Chair 4", null, null), new Seat(1, 4));
		seating.seat(guests.addGuest("Chair 5", null, null), new Seat(1, 5));
		assertEquals(List.of("Chair 4", "Chair 5", "Chair 0"),
				TablePlanLayout.seated(t, seating).stream().map(TablePlanLayout.Line::text).toList());
	}

	@Test
	void placesNotNamedAreMarked()
	{
		Plan plan = new Plan();
		table(plan, 1, 300, 300, 4);
		GuestList guests = new GuestList();
		Party p = guests.createParty("Ann", 2, "");
		Seating seating = new Seating();
		guests.membersOf(p).forEach(g -> seating.seat(g, new Seat(1, g.getPlus())));
		List<TablePlanLayout.Line> lines = TablePlanLayout.seated(plan.find(1).orElseThrow(), seating);
		assertFalse(lines.get(0).placeholder());
		assertEquals("Ann+1", lines.get(1).text());
		assertTrue(lines.get(1).placeholder());
	}

	@Test
	void panelsFollowTheRoomAndNeverOverlap()
	{
		Plan plan = new Plan();
		RectTable head = new RectTable(1, "", 6, 0);
		head.moveTo(700, 100);
		plan.add(head);
		plan.setHead(head);
		for (int i = 0; i < 6; i++)
			table(plan, 2 + i, 200 + 250 * i, 400, 10);   // a row of six
		for (int i = 0; i < 3; i++)
			table(plan, 8 + i, 300 + 400 * i, 700, 10);   // a row of three below
		plan.renumber();
		GuestList guests = new GuestList();
		Seating seating = new Seating();
		for (Table t : plan.tables())
			for (int c = 0; c < t.places(); c++)
				if (!t.isDisabled(c)) seating.seat(guests.addGuest("Guest " + t.getId() + "-" + c, null, null), new Seat(t.getId(), c));

		double w = 1100, h = 760;  // about A3 landscape inside the margins
		TablePlanLayout.Layout layout = TablePlanLayout.layout(plan, seating, w, h, MEASURE);
		assertTrue(layout.fits());
		List<TablePlanLayout.Panel> panels = layout.panels();
		assertEquals(10, panels.size());
		for (TablePlanLayout.Panel a : panels)
		{
			assertTrue(a.x() >= 0 && a.x() + a.width() <= w + 0.01, "inside the page across");
			assertTrue(a.y() >= 0 && a.y() + a.height() <= h + 0.01, "inside the page down");
			for (TablePlanLayout.Panel b : panels)
			{
				if (a == b) continue;
				boolean apart = a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x()
						|| a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y();
				assertTrue(apart, a.title() + " and " + b.title() + " overlap");
			}
		}
		// the head table above the rest; table 1 left of table 2; the row of six above the three
		TablePlanLayout.Panel headPanel = find(panels, "Head table");
		assertTrue(headPanel.y() < find(panels, "Table 1").y());
		assertTrue(find(panels, "Table 1").x() < find(panels, "Table 2").x());
		assertTrue(find(panels, "Table 6").y() < find(panels, "Table 7").y());
		// the head table is in the middle across, as in the room
		double middle = headPanel.x() + headPanel.width() / 2;
		assertEquals(w / 2, middle, w * 0.1);
	}

	@Test
	void aPartyWithNoSeatsShowsEachTablesPlaces()
	{
		Plan plan = new Plan();
		RoundTable t = table(plan, 1, 300, 300, 10);
		t.setDisabled(3, true);
		plan.renumber();
		TablePlanLayout.Layout layout = TablePlanLayout.layout(plan, new Seating(), false, 800, 600, MEASURE);
		assertEquals(List.of("9 places"), layout.panels().get(0).lines().stream().map(TablePlanLayout.Line::text).toList());
	}

	@Test
	void aPdfIsWritten() throws Exception
	{
		Occasion o = new Occasion("Lydia and Isaac's Wedding", new GuestList());
		o.setDate(Occasion.parseDate("12 May 2026"));
		Event e = new Event("Wedding Breakfast", "wb.xml", new Plan(), new Seating());
		o.addEvent(e);
		table(e.getPlan(), 1, 300, 300, 8);
		e.getPlan().renumber();
		e.getSeating().seat(o.getGuests().addGuest("Zoë Brontë", null, null), new Seat(1, 0));
		Path file = folder.resolve("plan.pdf");
		assertTrue(TablePlanPdf.write(o, e, com.itextpdf.text.PageSize.A3.rotate(), file));
		assertTrue(Files.readString(file, java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF"));
	}

	private static TablePlanLayout.Panel find(List<TablePlanLayout.Panel> panels, String title)
	{
		return panels.stream().filter(p -> p.title().equals(title)).findFirst().orElseThrow();
	}

	private static RoundTable table(Plan plan, int id, double x, double y, int places)
	{
		RoundTable t = new RoundTable(id, "", places);
		t.moveTo(x, y);
		plan.add(t);
		return t;
	}
}
