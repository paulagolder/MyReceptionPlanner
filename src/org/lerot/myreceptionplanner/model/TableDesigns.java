package org.lerot.myreceptionplanner.model;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/*
 * Table designs: a table in a file of its own, to be added to any event's
 * plan. The file is one <table>, written as in an event's file (see
 * PlanXml) but with no id, number or place on the floor:
 *
 *   <table shape="composed" head="true">
 *     <tables size="2 1" count="5" at="0 0" bare="near left"/>
 *     ...
 *   </table>
 *
 * Any shape will do; the designs that come with the planner (a T and an E,
 * in the program's resources under designs/, listed in designs/files.txt)
 * are composed of a venue's tables. They are copied into the designs folder
 * the first time it is wanted, and not again, so a design changed or removed
 * there stays as it was left.
 */
public class TableDesigns
{
	public static final String FOLDER = "Table designs";
	private static final String RESOURCES = "/org/lerot/myreceptionplanner/designs/";

	// the table, and whether the design would have it be the head table
	public record Design(Table table, boolean head) {}

	// the folder, made with the planner's own designs in it if it was not there
	public static Path folder(Path root) throws IOException
	{
		Path folder = root.resolve(FOLDER);
		if (Files.isDirectory(folder)) return folder;
		Files.createDirectories(folder);
		for (String file : files())
		{
			try (InputStream in = TableDesigns.class.getResourceAsStream(RESOURCES + file))
			{
				if (in == null) throw new IOException("The design " + file + " is missing from the program");
				Files.copy(in, folder.resolve(file));
			}
		}
		return folder;
	}

	// the design in the file, as a table with this id, not yet the head table
	public static Design load(Path file, int id) throws IOException
	{
		Document doc;
		try
		{
			doc = new SAXReader().read(file.toFile());
		} catch (DocumentException e)
		{
			throw new IOException(file.getFileName() + " is not a table design: " + e.getMessage());
		}
		return read(doc.getRootElement(), id, file.getFileName().toString());
	}

	static Design read(Element root, int id, String what) throws IOException
	{
		if (!root.getName().equals("table"))
			throw new IOException(what + " is not a table design: it should be one <table>, not <" + root.getName() + ">");
		Table t;
		try
		{
			t = PlanXml.table(root, id);
		} catch (IOException e)
		{
			// the file, not a table number no one has seen yet
			throw new IOException(what + ": " + e.getMessage().replaceFirst("^Table " + id + ":? ", ""));
		}
		boolean head = t.isHead();
		t.markHead(false);
		t.setNumber(0);
		return new Design(t, head);
	}

	static List<String> files() throws IOException
	{
		try (InputStream in = TableDesigns.class.getResourceAsStream(RESOURCES + "files.txt"))
		{
			if (in == null) throw new IOException("The table designs are missing from the program");
			List<String> files = new ArrayList<>();
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"))
			{
				line = line.strip();
				if (!line.isEmpty() && !line.startsWith("#")) files.add(line);
			}
			return files;
		}
	}
}
