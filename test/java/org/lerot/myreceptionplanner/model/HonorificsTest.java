package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HonorificsTest
{
	private final Honorifics standard = Honorifics.standard();

	@Test
	void aTitleKeepsTheNameAfterIt()
	{
		assertEquals("County Anselme", standard.greeting("County Anselme"));
		assertEquals("Friar Laurence", standard.greeting("Friar Laurence"));
		assertEquals("Lady Lucrezia", standard.greeting("Lady Lucrezia Capulet"));
		assertEquals("Widow of Vitruvio", standard.greeting("Widow of Vitruvio"));
		assertEquals("Count", standard.greeting("Count"));
	}

	@Test
	void someTitlesTakeTheSurname()
	{
		assertEquals("Dr Smith", standard.greeting("Dr John Smith"));
		assertEquals("Dr. Smith", standard.greeting("Dr. John Smith"));
		assertEquals("The Reverend Green", standard.greeting("The Reverend Ann Green"));
	}

	@Test
	void withoutATitleTheFirstName()
	{
		assertEquals("Ann", standard.greeting("Ann Smith"));
		assertEquals("Leonardo", standard.greeting("Leonardo da Vinci"));
		assertEquals("Michelangelo", standard.greeting("Michelangelo"));
	}

	@Test
	void anOccasionsOwnListComesFirst(@TempDir Path root) throws Exception
	{
		Path occasion = Files.createDirectory(root.resolve("an-occasion"));
		assertEquals("Count Paris", Honorifics.find(occasion).greeting("Count Paris"));
		Files.writeString(root.resolve(Honorifics.FILE), "with-name: [Signior]  # shared\nwith-surname:\n  - \"Mr.\"\n");
		assertEquals("Count", Honorifics.find(occasion).greeting("Count Paris"));
		assertEquals("Signior Martino", Honorifics.find(occasion).greeting("Signior Martino"));
		assertEquals("Mr Brown", Honorifics.find(occasion).greeting("Mr Tom Brown"));
		Files.writeString(occasion.resolve(Honorifics.FILE), "with-name:\n  - Count\n");
		assertEquals("Count Paris", Honorifics.find(occasion).greeting("Count Paris"));
		assertEquals("Signior", Honorifics.find(occasion).greeting("Signior Martino"));
	}
}
