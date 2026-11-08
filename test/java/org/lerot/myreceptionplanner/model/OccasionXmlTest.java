package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

// The occasion's folder: occasion.xml and a file for each event
class OccasionXmlTest
{
	@TempDir
	Path folder;

	@Test
	void aSavedOccasionLoadsTheSame() throws IOException
	{
		Occasion o = new Occasion("Lydia and Isaac's Wedding", new GuestList());
		o.setDate(LocalDate.of(2026, 5, 12));
		GuestList list = o.getGuests();
		Party smiths = list.createParty("Ann Smith", 3, "Smith & family");
		Guest ann = smiths.getLead();
		ann.setGender(Gender.FEMALE);
		Contact c = ann.getContact();
		c.setAddress("1 High Street\nLichfield\nWS13 6AA");
		c.setPhone("01543 000000");
		c.setMobile("07700 900123");
		c.setWhatsApp("07700 900123");
		c.setEmail("ann@example.com");
		c.setSource("MyContacts");
		Guest plus2 = list.membersOf(smiths).get(2);
		list.rename(list.membersOf(smiths).get(1), "Bob Smith");
		Guest priest = list.addGuest("Father Brown", Gender.MALE, null);
		list.createParty("Cath Jones", 1, "");

		Plan breakfastPlan = new Plan();
		RoundTable family = new RoundTable(1, "", 10);
		family.moveTo(300, 250);
		RectTable top = new RectTable(2, "", 6, 0);
		top.moveTo(700, 120);
		top.setRotation(45);
		breakfastPlan.add(family);
		breakfastPlan.add(top);
		breakfastPlan.setHead(top);
		top.setFirstSeat(3);
		breakfastPlan.renumber();
		Event breakfast = new Event("Wedding Breakfast", o.fileFor("Wedding Breakfast"), breakfastPlan, new Seating());
		breakfast.setPlace("The Old Barn, Lichfield");
		breakfast.setTime("2pm");
		breakfast.getSeating().seat(ann, new Seat(2, 3));
		breakfast.getSeating().seat(plus2, new Seat(1, 5));
		o.addEvent(breakfast);
		Event evening = new Event("Evening Reception", o.fileFor("Evening Reception"), new Plan(), new Seating());
		evening.setDate(LocalDate.of(2026, 5, 13));
		o.addEvent(evening);

		o.tidy();  // everyone new is invited to every event
		OccasionXml.save(o, folder);
		assertTrue(Files.isRegularFile(folder.resolve("occasion.xml")));
		assertTrue(Files.isRegularFile(folder.resolve("wedding-breakfast.xml")));
		assertTrue(Files.isRegularFile(folder.resolve("evening-reception.xml")));

		Occasion loaded = OccasionXml.load(folder);
		assertEquals("Lydia and Isaac's Wedding", loaded.getName());
		assertEquals(LocalDate.of(2026, 5, 12), loaded.getDate());
		assertEquals(2, loaded.events().size());
		Event b = loaded.events().get(0);
		assertEquals("Wedding Breakfast", b.getName());
		assertEquals("The Old Barn, Lichfield", b.getPlace());
		assertEquals("2pm", b.getTime());
		assertNull(b.getDate());
		assertEquals(LocalDate.of(2026, 5, 13), loaded.events().get(1).getDate());

		// the tables
		RectTable t = (RectTable) b.getPlan().find(2).orElseThrow();
		assertEquals(45, t.getRotation());
		assertEquals(3, t.getFirstSeat());
		assertTrue(t.isHead());
		assertEquals(1, b.getPlan().find(1).orElseThrow().getNumber());

		// the guests and their seats
		GuestList g = loaded.getGuests();
		assertEquals(2, g.parties().size());
		Party p = g.parties().get(0);
		assertEquals("Smith & family", p.getName());
		assertEquals(smiths.getColour(), p.getColour());
		assertFalse(g.parties().get(1).hasOwnName());
		Guest a = b.getSeating().guestAt(2, 3).orElseThrow();
		assertSame(p.getLead(), a);
		assertEquals(Gender.FEMALE, a.getGender());
		assertEquals("1 High Street\nLichfield\nWS13 6AA", a.getContact().getAddress());
		assertEquals("07700 900123", a.getContact().getWhatsApp());
		assertEquals("ann@example.com", a.getContact().getEmail());
		assertEquals("MyContacts", a.getContact().getSource());
		assertEquals("Ann Smith+2", b.getSeating().guestAt(1, 5).orElseThrow().displayName());
		assertEquals(priest.getId(), g.guests().stream().filter(x -> x.getName().equals("Father Brown"))
				.findFirst().orElseThrow().getId());
		assertEquals(0, loaded.events().get(1).getSeating().seatedCount());
	}

	@Test
	void eventFilesAreNamedOnceAndNotReused()
	{
		Occasion o = new Occasion("W", new GuestList());
		o.addEvent(new Event("Wedding Breakfast", o.fileFor("Wedding Breakfast"), new Plan(), new Seating()));
		assertEquals("wedding-breakfast-2.xml", o.fileFor("Wedding  Breakfast!"));
		assertEquals("event.xml", o.fileFor("Occasion"));
	}

	@Test
	void headingsSayWhatWhereAndWhen()
	{
		Occasion o = new Occasion("Lydia and Isaac's Wedding", new GuestList());
		o.setDate(Occasion.parseDate("12 May 2026"));
		Event e = new Event("Wedding Breakfast", "wb.xml", new Plan(), new Seating());
		e.setPlace("The Old Barn");
		e.setTime("2pm");
		assertEquals("Lydia and Isaac's Wedding  ·  12 May 2026", o.heading());
		assertEquals("Wedding Breakfast  ·  The Old Barn  ·  2pm", o.headingOf(e));
		e.setDate(Occasion.parseDate("13/5/2026"));
		assertEquals("Wedding Breakfast  ·  The Old Barn  ·  13 May 2026 2pm", o.headingOf(e));
		assertThrows(IllegalArgumentException.class, () -> Occasion.parseDate("next Tuesday"));
	}

	@Test
	void aMissingEventFileIsReported() throws IOException
	{
		Files.writeString(folder.resolve("occasion.xml"), "<occasion name=\"W\"><event file=\"gone.xml\"/></occasion>");
		IOException e = assertThrows(IOException.class, () -> OccasionXml.load(folder));
		assertTrue(e.getMessage().contains("gone.xml"));
	}

	@Test
	void anUnknownTableShapeIsReported() throws IOException
	{
		Files.writeString(folder.resolve("occasion.xml"), "<occasion name=\"W\"><event file=\"e.xml\"/></occasion>");
		Files.writeString(folder.resolve("e.xml"), "<event name=\"E\"><tables><table id=\"1\" shape=\"hexagon\"/></tables></event>");
		IOException e = assertThrows(IOException.class, () -> OccasionXml.load(folder));
		assertTrue(e.getMessage().contains("hexagon"));
	}
}
