package org.lerot.myreceptionplanner.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// A backup: the whole occasion folder, as it stands, in a folder of its own
class BackupTest
{
	@TempDir
	Path dir;

	@Test
	void theBackupIsOfferedBesideTheOccasionNumberedOnFromThoseThere() throws Exception
	{
		Path occasion = dir.resolve("wedding");
		assertEquals(dir.resolve("wedding_bak_1"), OccasionXml.nextBackup(occasion));
		Files.createDirectories(dir.resolve("wedding_bak_1"));
		assertEquals(dir.resolve("wedding_bak_2"), OccasionXml.nextBackup(occasion));
	}

	@Test
	void everyFileKeepsItsNameAndUnsavedChangesAreIn() throws Exception
	{
		Path from = Example.install(dir);
		Files.createDirectories(from.resolve(Occasion.OUTPUT));
		Files.writeString(from.resolve(Occasion.OUTPUT).resolve("door-list.pdf"), "a print");
		Occasion o = OccasionXml.load(from);
		Event gone = o.events().get(o.events().size() - 1);
		o.removeEvent(gone);
		o.setName("Renamed, not saved");
		Path to = dir.resolve("wedding_bak_1");
		OccasionXml.backup(o, from, to, Set.of(gone.getFile()));

		assertEquals("a print", Files.readString(to.resolve(Occasion.OUTPUT).resolve("door-list.pdf")), "Output and all");
		assertTrue(Files.isRegularFile(to.resolve(OccasionXml.GUESTS)));
		assertFalse(Files.exists(to.resolve(gone.getFile())), "an event deleted since saving is not in it");
		assertEquals("Renamed, not saved", OccasionXml.load(to).getName(), "as it stands now");
		assertNotEquals("Renamed, not saved", OccasionXml.load(from).getName(), "the occasion's own folder untouched");
	}

	@Test
	void notIntoTheOccasionsOwnFolder() throws Exception
	{
		Path from = Example.install(dir);
		Occasion o = OccasionXml.load(from);
		assertThrows(java.io.IOException.class, () -> OccasionXml.backup(o, from, from, List.of()));
		assertThrows(java.io.IOException.class, () -> OccasionXml.backup(o, from, dir, List.of()));
	}
}
