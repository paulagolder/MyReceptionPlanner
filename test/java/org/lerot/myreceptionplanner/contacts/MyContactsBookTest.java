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
