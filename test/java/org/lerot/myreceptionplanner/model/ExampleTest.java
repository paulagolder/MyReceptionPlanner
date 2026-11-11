package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleTest
{
	@TempDir
	Path root;

	@Test
	void theExampleOpensWhole() throws Exception
	{
		assertTrue(Example.noOccasionsIn(root.resolve("MyReceptions")));
		Path f = Example.install(root.resolve("MyReceptions"));
		assertEquals(Example.FOLDER, f.getFileName().toString());
		assertFalse(Example.noOccasionsIn(root.resolve("MyReceptions")));
		Occasion o = OccasionXml.load(f);
		assertEquals("Romeo and Juliette's Wedding", o.getName());
		assertEquals(3, o.events().size());
		assertEquals(67, o.getGuests().guests().size());
		assertNotNull(o.getEmblem());
		assertTrue(o.sent().isEmpty());
		// no one's real address: only example.com
		for (Party p : o.getGuests().parties())
		{
			String email = p.getLead().getContact().getEmail();
			assertTrue(email.isEmpty() || email.endsWith("@example.com"), email);
		}
		assertTrue(o.getReplyTo().endsWith("@example.com"));
	}

	@Test
	void anotherCopyGoesBesideTheFirst() throws Exception
	{
		Path first = Example.install(root);
		Files.writeString(first.resolve("occasion.xml"), Files.readString(first.resolve("occasion.xml")).replace("Romeo", "Romeo!"));
		Path second = Example.install(root);
		assertEquals(Example.FOLDER + "-2", second.getFileName().toString());
		assertTrue(Files.readString(first.resolve("occasion.xml")).contains("Romeo!"));  // left as it was
		assertEquals("Romeo and Juliette's Wedding", OccasionXml.load(second).getName());
	}
}
