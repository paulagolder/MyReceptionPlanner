package org.lerot.myreceptionplanner.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Menus as text: a menu file in YAML, to keep and share,
 *
 *   menu: Wedding Breakfast
 *   note: |
 *     All our food is sourced locally.
 *   courses:
 *     - name: Starter
 *       dishes:
 *         - Leek and potato soup [v gf]
 *         - Chicken liver pâté
 *
 * (a dish's diets in brackets, as the planner's codes), and a caterer's menu
 * pasted as plain text, its courses found from headings ("Starters",
 * "MAIN COURSE", "Desserts:") and its diets from the marks caterers use:
 * (V), (VG), (GF), (DF) ... Either way the menu is a new one, dishes
 * numbered from 1.
 */
public class MenuText
{
	// ---- the YAML file ----

	public static String toYaml(Menu menu, String title)
	{
		StringBuilder b = new StringBuilder();
		b.append("# A menu for MyReceptionPlanner (Catering ▾ → Import menu…, or Import… in Menu…).\n");
		b.append("# Each dish's diets are in brackets:");
		for (Diet d : Diet.values()) b.append(' ').append(d.code()).append(' ').append(d.toString().toLowerCase(Locale.ROOT)).append(',');
		b.setLength(b.length() - 1);
		b.append(".\n");
		if (title != null && !title.isBlank()) b.append("menu: ").append(quoted(title.strip())).append('\n');
		if (!menu.getNote().isEmpty())
		{
			b.append("note: |\n");
			for (String line : menu.getNote().split("\n", -1))
				b.append(line.isBlank() ? "" : "  " + line.stripTrailing()).append('\n');
		}
		b.append("courses:\n");
		for (Menu.Course c : menu.courses())
		{
			b.append("  - name: ").append(quoted(c.getName())).append('\n');
			b.append("    dishes:\n");
			for (Menu.Dish d : c.dishes())
				b.append("      - ").append(quoted(d.withDiets().replaceFirst(" \\(([^()]*)\\)$", " [$1]"))).append('\n');
		}
		return b.toString();
	}

	public static void save(Menu menu, String title, Path file) throws IOException
	{
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.writeString(file, toYaml(menu, title), StandardCharsets.UTF_8);
	}

	// a YAML menu file, or else an XML menu file or event file
	public static Menu load(Path file) throws IOException
	{
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!name.endsWith(".yml") && !name.endsWith(".yaml")) return OccasionXml.loadMenu(file);
		Menu m = fromYaml(Files.readString(file, StandardCharsets.UTF_8));
		if (m.isEmpty()) throw new IOException(file.getFileName() + " has no courses in it.");
		return m;
	}

	/*
	 * The file as written above, and the forms another program (or a person)
	 * might write it in: a dish as "- name: Soup" with "suits: [v, gf]" under
	 * it, a course as a key with its dishes listed under it ("Starter:").
	 */
	public static Menu fromYaml(String yaml)
	{
		Menu m = new Menu();
		int id = 1;
		boolean inCourses = false;
		Menu.Course course = null;
		Menu.Dish dish = null;
		int dishesIndent = -1;  // where the dishes of the course are listed, or -1
		int suitsIndent = -1;   // where a dish's diets are listed, or -1
		String[] lines = yaml.split("\\R", -1);
		for (int i = 0; i < lines.length; i++)
		{
			String text = withoutComment(lines[i]);
			if (text.isBlank() || text.strip().equals("---")) continue;
			int indent = indentOf(text);
			String s = text.strip();
			boolean item = s.startsWith("- ") || s.equals("-");
			if (item)
			{
				s = s.substring(1).strip();
				indent += 2;
			}
			if (item && suitsIndent >= 0 && indent >= suitsIndent && dish != null)
			{
				dish.suits().addAll(diets(unquoted(s)));
				continue;
			}
			suitsIndent = -1;
			String[] kv = keyValue(s);
			if (item && dishesIndent >= 0 && indent > dishesIndent && course != null
					&& (kv == null || kv[0].equals("name") || kv[0].equals("dish")))
			{
				String named = kv == null ? unquoted(s) : unquoted(kv[1]);
				dish = new Menu.Dish(id++, "");
				dish.setName(withoutDiets(named, dish.suits()));
				course.dishes().add(dish);
				continue;
			}
			if (kv == null) continue;
			String key = kv[0], value = kv[1];
			if (dishesIndent >= 0 && indent <= dishesIndent && !item && !key.equals("dishes"))
			{
				// left the dishes: a course's own key, or the next course as a key
				dishesIndent = -1;
				dish = null;
			}
			if (dish != null && dishesIndent >= 0 && (key.equals("suits") || key.equals("diets") || key.equals("dietary")))
			{
				if (value.isEmpty()) suitsIndent = indent + 1;
				else dish.suits().addAll(diets(unquoted(value)));
				continue;
			}
			switch (key)
			{
				case "note", "notes" ->
				{
					if (value.startsWith("|") || value.startsWith(">"))
					{
						List<String> block = new ArrayList<>();
						int j = i + 1;
						while (j < lines.length && (lines[j].isBlank() || indentOf(lines[j]) > indentOf(text)))
							block.add(lines[j++]);
						i = j - 1;
						m.setNote(block(block, value.startsWith(">")));
					}
					else if (course == null) m.setNote(unquoted(value));
				}
				case "courses", "sections" -> inCourses = true;
				case "name", "course", "section" ->
				{
					if (inCourses)
					{
						course = new Menu.Course(unquoted(value));
						m.courses().add(course);
						dish = null;
					}
				}
				case "dishes", "items" ->
				{
					if (course != null) dishesIndent = indent;
				}
				case "menu", "title" -> { }
				default ->
				{
					// "Starter:" with its dishes under it
					if (inCourses && value.isEmpty())
					{
						course = new Menu.Course(unquoted(kv[2]));
						m.courses().add(course);
						dish = null;
						dishesIndent = indent;
					}
				}
			}
		}
		m.courses().removeIf(c -> c.dishes().isEmpty());
		return m;
	}

	// ---- a menu pasted in ----

	private static final Pattern BULLET = Pattern.compile("^([-•*·–—▪◦‣]+|\\d{1,2}[.)])\\s+");
	private static final Pattern PRICE = Pattern.compile("\\s*[-–—]?\\s*[£€$]\\s?\\d+([.,]\\d{1,2})?(pp| per head| each)?\\s*$");
	private static final Pattern COURSE = Pattern.compile("(the )?(to start|to begin|to follow|to finish|for the table"
			+ "|(first|second|third|fourth|fifth|fish|main|cheese|soup|meat|dessert|pudding) course"
			+ "|starters?|appeti[sz]ers?|mains?|entr[ée]es?|soups?|fish|meat|desserts?|puddings?|sweets?|afters"
			+ "|cheeses?|cheeseboard|canap[ée]s?|sides|side dishes|vegetables|coffee|tea and coffee|coffee and tea"
			+ "|salads?|intermezzo|sorbets?|palate cleanser|amuse[- ]bouches?|antipasti|primi|secondi|dolci"
			+ "|nibbles|buffet|evening buffet|bowl food|grazing table|course \\d+)");

	/*
	 * Line by line: a heading starts a course, a line under it is a dish (its
	 * diet marks and any price taken off), a line starting in lower case goes
	 * on with the dish above ("with salsa verde"), a full sentence goes in
	 * the note, as does anything before the first heading. With no headings
	 * at all, each block of lines (between blank lines) is a course.
	 */
	public static Menu fromText(String text)
	{
		List<String> lines = new ArrayList<>();
		for (String raw : text.split("\\R"))
			lines.add(PRICE.matcher(BULLET.matcher(raw.strip()).replaceFirst("")).replaceFirst("").strip());
		boolean headings = lines.stream().anyMatch(MenuText::isHeading);
		Menu m = new Menu();
		List<String> note = new ArrayList<>();
		Menu.Course course = null;
		Menu.Dish dish = null;
		boolean blank = true;
		int id = 1;
		for (String s : lines)
		{
			if (s.isEmpty() || !s.matches(".*\\p{L}.*") || s.equalsIgnoreCase("or") || isKey(s))
			{
				if (s.isEmpty())
				{
					blank = true;
					if (!headings) course = null;
				}
				continue;
			}
			if (headings ? isHeading(s) : course == null)
			{
				if (!headings || !isTitle(s) || course != null)
				{
					if (headings)
					{
						course = new Menu.Course(s.replaceFirst("\\s*:$", "").replaceAll("\\s+", " "));
						m.courses().add(course);
						if (isUpper(course.getName())) course.setName(capitalised(course.getName()));
						dish = null;
						blank = false;
						continue;
					}
					course = new Menu.Course("Course " + (m.courses().size() + 1));
					m.courses().add(course);
				}
				else continue;  // "MENU", or "Wedding Breakfast Menu"
			}
			if (course == null || isSentence(s))
			{
				note.add(s);
				blank = false;
				continue;
			}
			Set<Diet> diets = EnumSet.noneOf(Diet.class);
			String name = withoutDiets(s, diets);
			if (dish != null && !blank && (Character.isLowerCase(s.codePointAt(0)) || name.isEmpty()))
			{
				if (!name.isEmpty()) dish.setName(dish.getName() + " " + name);
				dish.suits().addAll(diets);
			}
			else if (!name.isEmpty())
			{
				dish = new Menu.Dish(id++, name);
				dish.suits().addAll(diets);
				course.dishes().add(dish);
			}
			blank = false;
		}
		m.courses().removeIf(c -> c.dishes().isEmpty());
		m.setNote(String.join("\n", note));
		return m;
	}

	private static boolean isHeading(String s)
	{
		if (s.isEmpty() || hasDiets(s)) return false;
		String plain = s.replaceFirst("\\s*:$", "").replaceFirst("\\s*\\([^()]*\\)$", "").strip();
		int words = plain.split("\\s+").length;
		if (words > 6) return false;
		if (s.endsWith(":")) return true;
		if (COURSE.matcher(plain.toLowerCase(Locale.ROOT)).matches()) return true;
		return isUpper(plain) && plain.replaceAll("[^\\p{L}]", "").length() >= 3;
	}

	// a heading naming the menu rather than a course
	private static boolean isTitle(String s)
	{
		return s.toLowerCase(Locale.ROOT).matches(".*\\bmenus?\\b.*")
				&& !COURSE.matcher(s.replaceFirst("\\s*:$", "").toLowerCase(Locale.ROOT)).matches();
	}

	private static boolean isSentence(String s)
	{
		return s.matches(".*[.!?]$") && s.split("\\s+").length >= 8;
	}

	private static boolean isUpper(String s)
	{
		return s.equals(s.toUpperCase(Locale.ROOT)) && !s.equals(s.toLowerCase(Locale.ROOT));
	}

	private static String capitalised(String s)
	{
		String lower = s.toLowerCase(Locale.ROOT);
		return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
	}

	// a key to the diet marks: "(V) Vegetarian  (GF) Gluten free"
	private static boolean isKey(String s)
	{
		Set<Diet> found = EnumSet.noneOf(Diet.class);
		String rest = s;
		for (String group : groups(s))
		{
			Set<Diet> d = diets(group);
			if (d.isEmpty()) return false;
			found.addAll(d);
			rest = rest.replace(group, " ");
		}
		Set<Diet> words = diets(rest.replaceAll("[()\\[\\]=:\\-–,;/|]", " "));
		return !found.isEmpty() && (rest.isBlank() || !words.isEmpty() && onlyDietWords(rest));
	}

	private static boolean onlyDietWords(String s)
	{
		for (String w : normal(s).split("[\\s,;/&+=:|()\\[\\]–-]+"))
			if (!w.isEmpty() && !w.equals("and") && !w.equals("suitable") && !w.equals("for") && !w.equals("key")
					&& diet(w) == null) return false;
		return true;
	}

	private static List<String> groups(String s)
	{
		List<String> out = new ArrayList<>();
		Matcher g = Pattern.compile("[(\\[][^()\\[\\]]*[)\\]]").matcher(s);
		while (g.find()) out.add(g.group());
		return out;
	}

	// ---- diet marks ----

	private static final Pattern TRAILING_GROUP = Pattern.compile("\\s*[(\\[]([^()\\[\\]]*)[)\\]]\\s*\\*?$");
	private static final Pattern TRAILING_CODE = Pattern.compile("\\s+(V|VG|VE|GF|DF|NF|H|K|Ve|Vg)\\*?$");

	// the name with its diet marks taken off (and put in diets)
	static String withoutDiets(String s, Set<Diet> into)
	{
		String name = s.strip();
		while (true)
		{
			Matcher g = TRAILING_GROUP.matcher(name);
			if (g.find() && !diets(g.group(1)).isEmpty() && onlyDietWords(g.group(1)))
			{
				into.addAll(diets(g.group(1)));
				name = name.substring(0, g.start()).strip();
				continue;
			}
			Matcher c = TRAILING_CODE.matcher(name);
			if (c.find() && name.substring(0, c.start()).strip().contains(" "))
			{
				into.addAll(diets(c.group(1)));
				name = name.substring(0, c.start()).strip();
				continue;
			}
			break;
		}
		return name.replaceFirst("[\\s,;:–-]+$", "");
	}

	private static boolean hasDiets(String s)
	{
		Set<Diet> d = EnumSet.noneOf(Diet.class);
		withoutDiets(s, d);
		return !d.isEmpty();
	}

	// "v, gf", "Vegan / Gluten free", "[v, gf]"
	static Set<Diet> diets(String s)
	{
		Set<Diet> out = EnumSet.noneOf(Diet.class);
		for (String w : normal(s).split("[\\s,;/&+=:|()\\[\\]]+"))
		{
			Diet d = diet(w);
			if (d != null) out.add(d);
		}
		return out;
	}

	private static String normal(String s)
	{
		return s.toLowerCase(Locale.ROOT).replace('’', '\'')
				.replaceAll("gluten[- ]free|coeliac|celiac", "gf")
				.replaceAll("dairy[- ]free|lactose[- ]free", "df")
				.replaceAll("nut[- ]free", "nf")
				.replaceAll("plant[- ]based", "vg");
	}

	private static Diet diet(String w)
	{
		return switch (w.replaceAll("\\.", ""))
		{
			case "v", "veg", "vegetarian" -> Diet.VEGETARIAN;
			case "vg", "ve", "vegan" -> Diet.VEGAN;
			case "gf" -> Diet.GLUTEN_FREE;
			case "df" -> Diet.DAIRY_FREE;
			case "nf" -> Diet.NUT_FREE;
			case "h", "halal" -> Diet.HALAL;
			case "k", "kosher" -> Diet.KOSHER;
			default -> null;
		};
	}

	// ---- bits of YAML ----

	private static final Pattern PLAIN = Pattern.compile("[^-?:,\\[\\]{}#&*!|>'\"%@`\\s].*");

	private static String quoted(String s)
	{
		boolean plain = PLAIN.matcher(s).matches() && !s.contains(": ") && !s.contains(" #") && !s.endsWith(":")
				&& !s.toLowerCase(Locale.ROOT).matches("true|false|yes|no|on|off|null|~|[-+.\\d][\\d.e+-]*");
		return plain ? s : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String unquoted(String s)
	{
		s = s.strip();
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
			return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
		if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'"))
			return s.substring(1, s.length() - 1).replace("''", "'");
		return s;
	}

	// {key in lower case, value, key as written}, or null if not "key: value"
	private static String[] keyValue(String s)
	{
		if (s.startsWith("\"") || s.startsWith("'")) return null;
		int colon = s.indexOf(": ");
		if (colon < 0 && s.endsWith(":")) colon = s.length() - 1;
		if (colon <= 0) return null;
		String key = s.substring(0, colon).strip();
		return new String[] {key.toLowerCase(Locale.ROOT), s.substring(colon + 1).strip(), key};
	}

	// the line without a "# comment", minding quotes
	private static String withoutComment(String line)
	{
		char quote = 0;
		for (int i = 0; i < line.length(); i++)
		{
			char c = line.charAt(i);
			if (quote != 0)
			{
				if (c == '\\' && quote == '"') i++;
				else if (c == quote) quote = 0;
			}
			else if (c == '"' || c == '\'')
			{
				if (i == 0 || " :-[,".indexOf(line.charAt(i - 1)) >= 0) quote = c;
			}
			else if (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1))))
				return line.substring(0, i).stripTrailing();
		}
		return line.stripTrailing();
	}

	private static int indentOf(String line)
	{
		int n = 0;
		while (n < line.length() && line.charAt(n) == ' ') n++;
		return n;
	}

	// a | or > block, its indent taken off
	private static String block(List<String> lines, boolean folded)
	{
		int indent = lines.stream().filter(l -> !l.isBlank()).mapToInt(MenuText::indentOf).min().orElse(0);
		List<String> out = new ArrayList<>();
		for (String l : lines) out.add(l.isBlank() ? "" : l.substring(indent).stripTrailing());
		String s = String.join("\n", out).strip();
		return folded ? s.replaceAll("(?<!\n)\n(?!\n)", " ").replaceAll("\n\n", "\n") : s;
	}
}
