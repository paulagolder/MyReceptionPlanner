package org.lerot.myreceptionplanner.contacts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Reading contacts as MyContacts stores them
class MyContactsBookTest
{
	@TempDir
	Path folder;

	@Test
	void namesAreFirstMiddleAndSurname()
	{
		assertEquals("Ann B Smith", MyContactsBook.name("{ Sn:'Smith', Fn:'Ann', Mn:'B', Title:'Mrs' }"));
		assertEquals("Father Brown", MyContactsBook.name("Father Brown"));
	}

	@Test
	void addressesHaveALineForEachPart()
	{
		assertEquals("1 High Street\nLichfield\nWS13 6AA",
				MyContactsBook.address("{ street:'1 High Street', city:'Lichfield', postcode:'WS13 6AA' }"));
		assertEquals("Flat 2, The Close\nYork",
				MyContactsBook.address("{ building:\"Flat 2, The Close\", city:'York' }"), "commas in quotes stay");
	}

	@Test
	void quotesCanBeEscaped()
	{
		assertEquals("O'Brien", MyContactsBook.parse("{ Sn:'O\\'Brien' }").get("Sn"));
	}

	@Test
	void readsEachContactWithTheMainPhoneAndAddress() throws Exception
	{
		Path db = folder.resolve("test.sqlite");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db))
		{
			c.createStatement().executeUpdate("create table attributeValues (cid INT, root TEXT, qualifier TEXT, value TEXT)");
			add(c, 1, "name", "", "{ Sn:'Smith', Fn:'Ann' }");
			add(c, 1, "address", "old", "{ street:'Old Road' }");
			add(c, 1, "address", "", "{ street:'1 High Street', city:'Lichfield' }");
			add(c, 1, "phone", "work", "999");
			add(c, 1, "phone", "home", "01543 000000");
			add(c, 1, "mobile", "", "07700 900123");
			add(c, 1, "email", "", "ann@example.com");
			add(c, 2, "tid", "", "Brown Father");
			add(c, 3, "note", "", "no name, so left out");
		}
		List<MyContactsBook.Entry> all = new MyContactsBook(db).all();
		assertEquals(2, all.size());
		MyContactsBook.Entry ann = all.get(0);
		assertEquals("Ann Smith", ann.name());
		assertEquals("1 High Street\nLichfield", ann.address());
		assertEquals("01543 000000", ann.phone());
		assertEquals("07700 900123", ann.mobile());
		assertEquals("ann@example.com", ann.email());
		assertEquals("1 High Street", ann.place());
		assertEquals("Brown Father", all.get(1).name());
	}

	@Test
	void someoneWithNoAddressTakesTheirHouseholds() throws Exception
	{
		Path db = folder.resolve("test.sqlite");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db))
		{
			c.createStatement().executeUpdate("create table attributeValues (cid INT, root TEXT, qualifier TEXT, value TEXT)");
			add(c, 7, "name", "", "{ Sn:'Perks', Fn:'Tim and Clair' }");
			add(c, 7, "address", "", "{ street:'2 Mill Lane', city:'Tamworth' }");
			add(c, 7, "phone", "", "01827 000000");
			add(c, 7, "email", "", "household@example.com");
			add(c, 11, "name", "", "{ Sn:'Perks', Fn:'Tim' }");
			add(c, 11, "memberof", "000007", "000007");
			add(c, 11, "mobile", "", "07700 900456");
			add(c, 12, "name", "", "{ Sn:'Perks', Fn:'Clair' }");
			add(c, 12, "address", "", "{ street:'Her own flat' }");
			add(c, 7, "hasmember", "000012", "000012");   // linked from the group's side
			add(c, 13, "name", "", "{ Sn:'Nobody', Fn:'Ned' }");
		}
		var all = new MyContactsBook(db).all();
		var tim = all.stream().filter(e -> e.name().equals("Tim Perks")).findFirst().orElseThrow();
		assertEquals("2 Mill Lane\nTamworth", tim.address());
		assertEquals("01827 000000", tim.phone());
		assertEquals("07700 900456", tim.mobile());
		assertEquals("", tim.email(), "email stays their own");
		assertEquals("Tim and Clair Perks", tim.addressFrom());
		var clair = all.stream().filter(e -> e.name().equals("Clair Perks")).findFirst().orElseThrow();
		assertEquals("Her own flat", clair.address(), "an address of one's own comes first");
		assertEquals("01827 000000", clair.phone(), "but the household phone fills the gap");
		var ned = all.stream().filter(e -> e.name().equals("Ned Nobody")).findFirst().orElseThrow();
		assertEquals("", ned.addressFrom());
	}

	@Test
	void aMissingDatabaseIsReported()
	{
		assertThrows(IOException.class, () -> new MyContactsBook(folder.resolve("none.sqlite")).all());
	}

	private static void add(Connection c, int cid, String root, String qualifier, String value) throws SQLException
	{
		try (PreparedStatement st = c.prepareStatement("insert into attributeValues values (?, ?, ?, ?)"))
		{
			st.setInt(1, cid);
			st.setString(2, root);
			st.setString(3, qualifier);
			st.setString(4, value);
			st.executeUpdate();
		}
	}
}
