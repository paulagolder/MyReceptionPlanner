package org.lerot.myreceptionplanner.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/*
 * The example occasion that comes with the planner, Romeo and Juliette's
 * Wedding (three events, 67 guests, menus, seating and an emblem), kept in
 * the program's resources (example/, listed in example/files.txt since a jar
 * cannot list a folder). It is copied into the occasions folder the first
 * time the planner finds none there, and again whenever asked, each time
 * into a folder of its own: nothing already there is written over.
 */
public class Example
{
	public static final String FOLDER = "romeo-and-juliettes-wedding";
	private static final String RESOURCES = "/org/lerot/myreceptionplanner/example/";

	// whether the occasions folder holds no occasion yet (or is not there)
	public static boolean noOccasionsIn(Path root) throws IOException
	{
		if (!Files.isDirectory(root)) return true;
		try (DirectoryStream<Path> in = Files.newDirectoryStream(root, Files::isDirectory))
		{
			for (Path p : in)
				if (OccasionXml.isOccasion(p)) return false;
		}
		return true;
	}

	// copies the example into root, as romeo-and-juliettes-wedding, or -2, -3 ...
	// if that is taken; the folder it went into
	public static Path install(Path root) throws IOException
	{
		Path to = root.resolve(FOLDER);
		for (int n = 2; Files.exists(to); n++) to = root.resolve(FOLDER + "-" + n);
		Files.createDirectories(to);
		for (String file : files())
		{
			try (InputStream in = Example.class.getResourceAsStream(RESOURCES + file))
			{
				if (in == null) throw new IOException("The example's " + file + " is missing from the program");
				Files.copy(in, to.resolve(file));
			}
		}
		return to;
	}

	static List<String> files() throws IOException
	{
		try (InputStream in = Example.class.getResourceAsStream(RESOURCES + "files.txt"))
		{
			if (in == null) throw new IOException("The example is missing from the program");
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
