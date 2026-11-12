package org.lerot.myreceptionplanner.contacts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContactFileTest
{
	@TempDir
	Path dir;

	private List<ContactFile.Entry> read(String name, String text) throws Exception
	{
		Path f = dir.resolve(name);
		Files.writeString(f, text);
		return ContactFile.read(f);
	}

	@Test
	void aVCardFromGoogle() throws Exception
	{
		List<ContactFile.Entry> e = read("contacts.vcf", """
				BEGIN:VCARD\r
				VERSION:3.0\r
				FN:Ann Smith\r
				N:Smith;Ann;;;\r
				EMAIL;TYPE=INTERNET;TYPE=HOME:ann@example.com\r
				TEL;TYPE=CELL:07700 900123\r
				TEL;TYPE=HOME:01234 567890\r
				ADR;TYPE=HOME:;;1 High Street\\nFlat 2;Mayfield;Staffs;WS13 6AA;United Kingdom\r
				END:VCARD\r
				BEGIN:VCARD\r
				VERSION:3.0\r
				N:Brown;Tom;;Dr;\r
				item1.EMAIL;TYPE=INTERNET:tom@exa\r
				 mple.com\r
				item1.X-ABLabel:\r
				END:VCARD\r
				BEGIN:VCARD\r
				VERSION:3.0\r
				EMAIL:nobody@example.com\r
				END:VCARD\r
				""");
		assertEquals(3, e.size());
		ContactFile.Entry ann = e.get(0);
		assertEquals(new org.lerot.myreceptionplanner.model.Name("", "Ann", "Smith"), ann.parts());
		assertEquals(new org.lerot.myreceptionplanner.model.Name("Dr", "Tom", "Brown"),
				e.stream().filter(x -> x.name().equals("Dr Tom Brown")).findFirst().orElseThrow().parts());
		assertEquals("Ann Smith", ann.name());
		assertEquals("ann@example.com", ann.email());
		assertEquals("07700 900123", ann.mobile());
		assertEquals("01234 567890", ann.phone());
		assertEquals("1 High Street\nFlat 2\nMayfield\nStaffs\nWS13 6AA\nUnited Kingdom", ann.address());
		assertEquals("Dr Tom Brown", e.get(1).name());
		assertEquals("tom@example.com", e.get(1).email());
		assertEquals("nobody@example.com", e.get(2).name());  // known only by their address
	}

	@Test
	void googleCsvNewStyle() throws Exception
	{
		List<ContactFile.Entry> e = read("contacts.csv", """
				First Name,Middle Name,Last Name,Name Prefix,E-mail 1 - Label,E-mail 1 - Value,Phone 1 - Label,Phone 1 - Value,Phone 2 - Label,Phone 2 - Value,Address 1 - Label,Address 1 - Formatted,Address 1 - City
				Ann,,Smith,,* Home,ann@example.com ::: ann2@example.com,Home,01234 567890,Mobile,07700 900123,Home,"1 High Street
				Mayfield
				WS13 6AA",Mayfield
				Paris,,,Count,,,,,,,,,
				""");
		assertEquals(List.of("Ann Smith", "Count Paris"), e.stream().map(ContactFile.Entry::name).toList());
		assertEquals(new org.lerot.myreceptionplanner.model.Name("Count", "Paris", ""), e.get(1).parts(), "the name in its parts");
		ContactFile.Entry ann = e.get(0);
		assertEquals("ann@example.com", ann.email());
		assertEquals("01234 567890", ann.phone());
		assertEquals("07700 900123", ann.mobile());
		assertEquals("1 High Street\nMayfield\nWS13 6AA", ann.address());
		assertEquals("Mayfield", ann.place());
	}

	@Test
	void googleCsvOldStyle() throws Exception
	{
		List<ContactFile.Entry> e = read("google.csv", """
				Name,Given Name,Family Name,E-mail 1 - Type,E-mail 1 - Value,Phone 1 - Type,Phone 1 - Value,Address 1 - Type,Address 1 - Street,Address 1 - City,Address 1 - Postal Code
				"Smith, Ann",Ann,Smith,* Home,ann@example.com,Mobile,07700 900123,Home,1 High Street,Mayfield,WS13 6AA
				""");
		assertEquals("Smith, Ann", e.get(0).name());
		assertEquals("07700 900123", e.get(0).mobile());
		assertEquals("1 High Street\nMayfield\nWS13 6AA", e.get(0).address());
	}

	@Test
	void outlookCsv() throws Exception
	{
		List<ContactFile.Entry> e = read("outlook.csv", """
				First Name,Last Name,E-mail Address,Mobile Phone,Home Phone,Home Street,Home City,Home Postal Code
				Ann,Smith,ann@example.com,07700 900123,01234 567890,1 High Street,Mayfield,WS13 6AA
				""");
		assertEquals("Ann Smith", e.get(0).name());
		assertEquals("ann@example.com", e.get(0).email());
		assertEquals("07700 900123", e.get(0).mobile());
		assertEquals("01234 567890", e.get(0).phone());
		assertEquals("Mayfield", e.get(0).place());
	}
}
