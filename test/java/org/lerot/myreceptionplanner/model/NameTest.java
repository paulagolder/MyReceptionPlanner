package org.lerot.myreceptionplanner.model;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// A name in its parts: title, forenames, family name
class NameTest
{
	private final Honorifics h = Honorifics.standard();

	@Test
	void thePartsMakeEveryForm()
	{
		Name n = new Name(" Dame ", "Margot  Rose", "Fairweather");
		assertEquals("Dame Margot Rose Fairweather", n.full());
		assertEquals("Margot Rose Fairweather", n.plain());
		assertEquals("Margot", n.firstName());
		assertEquals("Tybalt", new Name("", "Tybalt", "").full());
		assertEquals("The Mayor of Thornbury", new Name("The Mayor of Thornbury", "", "").plain());
		assertTrue(new Name(" ", null, "").isEmpty());
	}

	@Test
	void aWholeNameIsSplitAsBestItCanBe()
	{
		assertEquals(new Name("", "Ann", "Smith"), Name.guess("Ann Smith", h));
		assertEquals(new Name("", "Mary Ann", "Smith"), Name.guess("Mary Ann Smith", h));
		assertEquals(new Name("Dame", "Margot", "Fairweather"), Name.guess("Dame Margot Fairweather", h));
		assertEquals(new Name("Count", "Paris", ""), Name.guess("Count Paris", h), "Count goes with the name");
		assertEquals(new Name("Dr", "", "Smith"), Name.guess("Dr Smith", h), "Dr goes with the surname");
		assertEquals(new Name("", "Tybalt", ""), Name.guess("Tybalt", h));
		assertEquals(new Name("", "Agnes Van der", "Berg"), Name.guess("Agnes Van der Berg", h), "only a guess");
		assertEquals(Name.NONE, Name.guess("  ", h));
	}

	@Test
	void greetingsComeFromTheParts()
	{
		assertEquals("Ann", h.greeting(new Name("", "Ann", "Smith")));
		assertEquals("Count Paris", h.greeting(new Name("Count", "Paris", "")));
		assertEquals("Sir John", h.greeting(new Name("Sir", "John Edward", "Falstaff")));
		assertEquals("Dr Smith", h.greeting(new Name("Dr", "John", "Smith")));
		assertEquals("Chef Ann", h.greeting(new Name("Chef", "Ann", "Jones")), "a title not listed goes with the name");
		assertEquals("The Mayor", h.greeting(new Name("The Mayor", "", "")));
		assertTrue(h.titles().contains("Dame"));
	}

	@Test
	void thePartsAreKeptInTheFileAndAnOldWholeNameIsSplit() throws Exception
	{
		GuestList list = new GuestList();
		Party p = list.createParty(new Name("Dame", "Margot", "Fairweather"), 2, "");
		list.addGuest(new Name("", "Agnes", "Van der Berg"), Gender.FEMALE, p);
		Element holder = DocumentHelper.createElement("x");
		GuestXml.write(list, holder);
		String xml = holder.asXML();
		assertTrue(xml.contains("title=\"Dame\" forenames=\"Margot\" family=\"Fairweather\""), xml);
		assertFalse(xml.contains(" name=\"Dame"), xml);
		GuestList back = GuestXml.read(holder.element("guests"));
		assertEquals(new Name("", "Agnes", "Van der Berg"), back.guests().get(2).getNameParts());

		Element old = DocumentHelper.parseText("<guests><guest id=\"1\" name=\"Lady Capulet\" gender=\"f\"/></guests>").getRootElement();
		assertEquals(new Name("Lady", "Capulet", ""), GuestXml.read(old).guests().get(0).getNameParts());
	}
}
