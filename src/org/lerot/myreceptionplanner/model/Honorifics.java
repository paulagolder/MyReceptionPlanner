package org.lerot.myreceptionplanner.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * The titles that go with a name when a guest is greeted ({leadfirst}): "Dear
 * Count Paris", not "Dear Count". Kept in honorifics.yml, a short YAML file
 * of two lists:
 *
 *   # the title and the name after it: Count Paris, Friar Laurence, Sir John
 *   with-name:
 *     - Count
 *     - Friar
 *   # the title and the surname, the last word: Dr John Smith → Dr Smith
 *   with-surname: [Mr, Mrs, Dr]
 *
 * A title may be more than one word ("The Reverend"); a full stop after it
 * ("Dr.") and the case do not matter. An occasion takes the file in its own
 * folder, else the one in the folder above it (MyReceptions), else the
 * standard list. A name with no title is greeted by its first word, as
 * before; a title followed by "of" ("Widow of Vitruvio") keeps the rest.
 */
public class Honorifics
{
	public static final String FILE = "honorifics.yml";

	private static final Set<String> JOINS = Set.of("of", "de", "di", "da", "del", "della", "von", "van", "du", "la", "le");

	private final List<List<String>> withName = new ArrayList<>();
	private final List<List<String>> withSurname = new ArrayList<>();

	public static Honorifics standard()
	{
		try (InputStream in = Honorifics.class.getResourceAsStream(FILE))
		{
			return in == null ? new Honorifics() : parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException e)
		{
			return new Honorifics();
		}
	}

	// the occasion's own file, else the one shared in the folder above, else the standard list
	public static Honorifics find(Path occasionFolder) throws IOException
	{
		Path own = occasionFolder.resolve(FILE);
		if (Files.isRegularFile(own)) return parse(Files.readString(own, StandardCharsets.UTF_8));
		Path parent = occasionFolder.toAbsolutePath().getParent();
		if (parent != null && Files.isRegularFile(parent.resolve(FILE)))
			return parse(Files.readString(parent.resolve(FILE), StandardCharsets.UTF_8));
		return standard();
	}

	// just the two lists, as block ("- Count") or flow ("[Count, Friar]") sequences
	public static Honorifics parse(String yaml)
	{
		Honorifics h = new Honorifics();
		List<List<String>> into = null;
		for (String raw : yaml.split("\\R"))
		{
			String line = raw.replaceFirst("\\s+#.*$", "").replaceFirst("^#.*$", "").strip();
			if (line.isEmpty()) continue;
			if (line.startsWith("-"))
			{
				if (into != null) add(into, line.substring(1));
				continue;
			}
			int colon = line.indexOf(':');
			if (colon < 0) continue;
			String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
			into = key.equals("with-name") ? h.withName : key.equals("with-surname") ? h.withSurname : null;
			String rest = line.substring(colon + 1).strip();
			if (into != null && rest.startsWith("["))
				for (String item : rest.replaceAll("^\\[|]$", "").split(","))
					add(into, item);
		}
		return h;
	}

	private static void add(List<List<String>> into, String item)
	{
		String title = item.strip().replaceAll("^[\"']|[\"']$", "").strip();
		if (!title.isEmpty()) into.add(words(title));
	}

	private static List<String> words(String s)
	{
		return Arrays.stream(s.strip().split("\\s+"))
				.map(w -> w.replaceFirst("\\.$", "").toLowerCase(Locale.ROOT)).toList();
	}

	// what a guest is greeted by: "Count Paris", "Dr Smith", "Ann"
	public String greeting(String name)
	{
		String[] w = name.strip().split("\\s+");
		if (w.length <= 1) return name.strip();
		List<String> lower = words(name);
		int n = longest(withName, lower);
		int s = longest(withSurname, lower);
		if (n == 0 && s == 0) return w[0];
		if (s > n)
			return s >= w.length ? name.strip() : join(w, 0, s) + " " + w[w.length - 1];
		if (n >= w.length) return name.strip();
		int end = n + 1;  // the title and the word after it
		if (JOINS.contains(lower.get(n))) end = w.length;  // Widow of Vitruvio
		return join(w, 0, end);
	}

	// how many words of the name the longest title it starts with takes, or 0
	private static int longest(List<List<String>> titles, List<String> name)
	{
		int best = 0;
		for (List<String> t : titles)
			if (t.size() > best && t.size() <= name.size() && name.subList(0, t.size()).equals(t)) best = t.size();
		return best;
	}

	private static String join(String[] w, int from, int to)
	{
		return String.join(" ", Arrays.copyOfRange(w, from, to));
	}
}
