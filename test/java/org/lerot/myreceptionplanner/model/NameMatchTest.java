package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Names that might be the same person
class NameMatchTest
{
	@TempDir
	Path folder;

	private static String why(String a, String b)
	{
		return NameMatch.why(a, b).orElse(null);
	}

	@Test
	void likelyTheSamePerson()
	{
		assertEquals("Tom and Thomas are forms of one name", why("Tom Perks", "Thomas Perks"));
		assertEquals("T could stand for Thomas", why("T. Perks", "Thomas Perks"));
		assertEquals("Chris and Christopher are forms of one name", why("Chris Evans", "Christopher Evans"));
		assertEquals("Jos could be short for Joshua", why("Jos Evans", "Joshua Evans"));
		assertEquals("Ed and Ted are forms of one name", why("Ed Lee", "Ted Lee"));
		assertEquals("Clair and Claire are spelt alike", why("Clair Perks", "Claire Perks"));
		assertEquals("the same name", why("Mrs Ann Smith", "ann  smith"));
		assertEquals("the same name", why("Zoë Brontë", "Zoe Bronte"));
		assertEquals("the same first name and surname", why("Ann Mary Smith", "Ann Smith"));
		assertEquals("the same names the other way round", why("Perks Tom", "Tom Perks"));
		assertEquals("surnames spelt alike", why("Tom Perks", "Tom Parks"));
		assertEquals("Bill and William are forms of one name", why("Bill O'Brien", "William OBrien"));
		assertEquals("Kate and Catherine are forms of one name", why("Kate Featherstonehaugh-Smythe", "Catherine Featherstonehaugh Smythe"));
		assertEquals("Steven and Stephen are forms of one name", why("Steven Hall", "Stephen Hall"));
	}

	@Test
	void notTheSamePerson()
	{
		assertNull(why("Tom Perks", "Claire Perks"), "one family, two people");
		assertNull(why("Ann Smith", "Ann Jones"));
		assertNull(why("Tom Perks", "Thomas Jones"));
		assertNull(why("Tom Perks", "Ben Parks"));
		assertNull(why("Granny", "Granny Smith"), "one word is only like the same word");
		assertNull(why("Tim Perks", "Tim & Claire Perks"), "a couple is not one of them");
		assertNull(why("Tim Perks", "Tim and Claire Perks"));
		assertEquals("the same name", why("Tim & Claire Perks", "tim & claire perks"));
	}

	@Test
	void theGuestListFindsThoseAlikeButNotThoseSaidToDiffer() throws IOException
	{
		Occasion o = new Occasion("W", new GuestList());
		GuestList g = o.getGuests();
		Party perks = g.createParty("Tim Perks", 3, "");
		Guest thomas = g.addGuest("Thomas Perks", Gender.MALE, perks);
		Guest claire = g.addGuest("Claire Perks", Gender.FEMALE, perks);
		g.createParty("Tom Perks", 1, "");   // the same person, in another party

		List<GuestList.Likeness> like = g.like("Tommy Perks", null);
		assertEquals(2, like.size());
		assertTrue(g.like("Claire Perks", claire).isEmpty(), "not like themselves");
		assertEquals(1, g.alike().size());
		GuestList.Likeness pair = g.alike().get(0);
		g.markDistinct(pair.a(), pair.b());
		assertTrue(g.alike().isEmpty());

		OccasionXml.save(o, folder);
		GuestList loaded = OccasionXml.load(folder).getGuests();
		assertTrue(loaded.alike().isEmpty(), "said to differ, after saving too");
		assertTrue(loaded.like("Tom Perks", loaded.guests().stream()
				.filter(x -> x.getId() == thomas.getId()).findFirst().orElseThrow()).isEmpty());

		g.removeGuest(thomas);
		assertTrue(g.distinctPairs().isEmpty(), "forgotten when one of them goes");
	}

	@Test
	void distanceCountsChangedLetters()
	{
		assertEquals(1, NameMatch.distance("perks", "parks"));
		assertEquals(1, NameMatch.distance("clair", "claire"));
		assertEquals(3, NameMatch.distance("kitten", "sitting"));
	}
}
