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
 * The titles that go with a name: offered when a guest's title is typed, used
 * to split a name typed in one piece (see Name.guess), and for how a guest is
 * greeted ({leadfirst}): "Dear Count Paris", not "Dear Count". Kept in
 * honorifics.yml, a short YAML file of two lists:
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
 * standard list. A guest with no title is greeted by their first forename;
 * a title not on either list is taken as one with the name. The lists need
 * not be complete: any title may be typed.
 */
public class Honorifics
{
	public static final String FILE = "honorifics.yml";

	static final Set<String> JOINS = Set.of("of", "de", "di", "da", "del", "della", "von", "van", "du", "la", "le");

	private final List<List<String>> withName = new ArrayList<>();
	private final List<List<String>> withSurname = new ArrayList<>();
	private final List<String> titles = new ArrayList<>();  // as written in the file, both lists

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
				if (into != null) h.add(into, line.substring(1));
				continue;
			}
			int colon = line.indexOf(':');
			if (colon < 0) continue;
			String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
			into = key.equals("with-name") ? h.withName : key.equals("with-surname") ? h.withSurname : null;
			String rest = line.substring(colon + 1).strip();
			if (into != null && rest.startsWith("["))
				for (String item : rest.replaceAll("^\\[|]$", "").split(","))
					h.add(into, item);
		}
		return h;
	}

	private void add(List<List<String>> into, String item)
	{
		String title = item.strip().replaceAll("^[\"']|[\"']$", "").strip();
		if (!title.isEmpty())
		{
			into.add(words(title));
			if (!titles.contains(title)) titles.add(title);
		}
	}

	// every title, as written, in the file's order: for choosing one
	public List<String> titles() { return List.copyOf(titles); }

	// how many words at the start of a whole name are a known title, or 0
	public int titleLength(String name)
	{
		List<String> lower = words(name);
		return Math.max(longest(withName, lower), longest(withSurname, lower));
	}

	// "Dr", "Mrs": greeted with the family name, Dr Smith
	public boolean greetsBySurname(String title)
	{
		List<String> t = words(title);
		return !title.isBlank() && withSurname.contains(t);
	}

	/*
	 * What a guest is greeted by: "Count Paris" (a title with the name),
	 * "Dr Smith" (a title with the surname), "Ann" (no title: the first
	 * forename); a guest with only a title, or only one part, by what there is.
	 */
	public String greeting(Name n)
	{
		if (n.title().isEmpty()) return n.firstName().isEmpty() ? n.full() : n.firstName();
		if (n.forenames().isEmpty() && n.family().isEmpty()) return n.title();
		if (greetsBySurname(n.title()))
			return n.title() + " " + (n.family().isEmpty() ? n.forenames() : n.family());
		return n.title() + " " + n.firstName();
	}

	private static List<String> words(String s)
	{
		return Arrays.stream(s.strip().split("\\s+"))
				.map(w -> w.replaceFirst("\\.$", "").toLowerCase(Locale.ROOT)).toList();
	}

	// what a whole name, typed in one piece, is greeted by
	public String greeting(String name) { return greeting(Name.guess(name, this)); }

	// how many words of the name the longest title it starts with takes, or 0
	private static int longest(List<List<String>> titles, List<String> name)
	{
		int best = 0;
		for (List<String> t : titles)
			if (t.size() > best && t.size() <= name.size() && name.subList(0, t.size()).equals(t)) best = t.size();
		return best;
	}

}
