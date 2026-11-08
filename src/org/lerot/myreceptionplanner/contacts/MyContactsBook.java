package org.lerot.myreceptionplanner.contacts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/*
 * Reads contacts from MyContacts' database, only ever reading. MyContacts
 * keeps its settings in ~/.mccontacts/properties.xml, whose "database" entry
 * names the SQLite file in that folder. Each contact is rows of
 * attributeValues(cid, root, qualifier, value): root is the field (name,
 * address, phone, mobile, email ...) and qualifier tells apart several of
 * one kind ("" for the main one, "home", "alt1" ...). Names and addresses are
 * stored as { key:'value', ... }.
 *
 * A person may belong to a household or other group, itself a contact: a
 * memberof row on the person or a hasmember row on the group holds the other's
 * id ("000107"). Someone with no address or home phone of their own takes the
 * first group's that has one, as MyContacts does; mobile and email stay their own.
 */
public class MyContactsBook
{
	// a contact found in MyContacts, with what the planner copies from it
	// addressFrom names the group the address (and phone) came from, or is ""
	public record Entry(int cid, String name, String address, String phone, String mobile, String email,
			String addressFrom)
	{
		// the line before the last of the address (usually the town), to tell
		// people of the same name apart
		public String place()
		{
			String[] lines = address.split("\n");
			return lines.length > 1 ? lines[lines.length - 2] : "";
		}
	}

	private static final String[] NAME_PARTS = { "Title", "Fn", "Mn", "Sn" };
	private static final String[] ADDRESS_PARTS = { "pobox", "building", "street", "city", "county", "postcode", "country" };

	private final Path database;

	public MyContactsBook(Path database)
	{
		this.database = database;
	}

	// the database MyContacts is set to use
	public static MyContactsBook standard() throws IOException
	{
		Path folder = Path.of(System.getProperty("user.home"), ".mccontacts");
		Properties props = new Properties();
		Path settings = folder.resolve("properties.xml");
		if (Files.isRegularFile(settings))
		{
			try (InputStream in = Files.newInputStream(settings))
			{
				props.loadFromXML(in);
			}
		}
		Path db = folder.resolve(props.getProperty("database", "mcdb.sqlite"));
		if (!Files.isRegularFile(db)) throw new IOException("MyContacts' database is not at " + db);
		return new MyContactsBook(db);
	}

	public Path getDatabase() { return database; }

	// everyone, by name
	public List<Entry> all() throws IOException
	{
		Map<Integer, Map<String, String>> fields = new LinkedHashMap<>();
		Map<Integer, List<Integer>> groups = new HashMap<>();  // person -> groups, in the order found
		try (Connection c = open();
			 PreparedStatement st = c.prepareStatement("select cid, root, qualifier, value from attributeValues "
					 + "where root in ('name', 'tid', 'address', 'phone', 'mobile', 'email', 'memberof', 'hasmember') "
					 + "order by cid"))
		{
			ResultSet rs = st.executeQuery();
			while (rs.next())
			{
				int cid = rs.getInt(1);
				String root = rs.getString(2).toLowerCase();
				String qualifier = rs.getString(3) == null ? "" : rs.getString(3).trim().toLowerCase();
				String value = rs.getString(4);
				if (root.equals("memberof") || root.equals("hasmember"))
				{
					Integer other = linkedId(value, qualifier);
					if (other == null) continue;
					if (root.equals("memberof")) addGroup(groups, cid, other);
					else addGroup(groups, other, cid);
					continue;
				}
				fields.computeIfAbsent(cid, k -> new LinkedHashMap<>()).put(root + "|" + qualifier, value);
			}
		} catch (SQLException e)
		{
			throw new IOException("Cannot read MyContacts' database " + database + ": " + e.getMessage(), e);
		}
		List<Entry> entries = new ArrayList<>();
		for (var f : fields.entrySet())
		{
			Map<String, String> m = f.getValue();
			String name = name(pick(m, "name"));
			if (name.isEmpty()) name = pick(m, "tid");
			if (name.isEmpty()) continue;
			String address = address(pick(m, "address", "", "home"));
			String phone = plain(pick(m, "phone", "", "home"));
			String from = "";
			for (int gid : groups.getOrDefault(f.getKey(), List.of()))
			{
				if (!address.isEmpty() && !phone.isEmpty()) break;
				Map<String, String> g = fields.get(gid);
				if (g == null || gid == f.getKey()) continue;
				String groupAddress = address(pick(g, "address", "", "home"));
				String groupPhone = plain(pick(g, "phone", "", "home"));
				boolean used = false;
				if (address.isEmpty() && !groupAddress.isEmpty())
				{
					address = groupAddress;
					used = true;
				}
				if (phone.isEmpty() && !groupPhone.isEmpty())
				{
					phone = groupPhone;
					used = true;
				}
				if (used && from.isEmpty()) from = groupName(g);
			}
			entries.add(new Entry(f.getKey(), name, address, phone, plain(pick(m, "mobile")), plain(pick(m, "email")), from));
		}
		entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
		return entries;
	}

	private static void addGroup(Map<Integer, List<Integer>> groups, int member, int group)
	{
		List<Integer> list = groups.computeIfAbsent(member, k -> new ArrayList<>());
		if (!list.contains(group)) list.add(group);
	}

	// the other contact's id, "000107", in the value or else the qualifier
	private static Integer linkedId(String value, String qualifier)
	{
		for (String s : new String[] { value, qualifier })
		{
			if (s != null && s.trim().matches("\\d+")) return Integer.valueOf(s.trim());
		}
		return null;
	}

	private static String groupName(Map<String, String> g)
	{
		String name = name(pick(g, "name"));
		return name.isEmpty() ? pick(g, "tid") : name;
	}

	private Connection open() throws SQLException
	{
		Properties p = new Properties();
		p.setProperty("open_mode", "1");  // SQLITE_OPEN_READONLY
		Connection c = DriverManager.getConnection("jdbc:sqlite:" + database, p);
		c.setReadOnly(true);
		return c;
	}

	// the value for the first of these qualifiers present, else any of the root
	private static String pick(Map<String, String> m, String root, String... qualifiers)
	{
		for (String q : qualifiers.length == 0 ? new String[] { "" } : qualifiers)
		{
			String v = m.get(root + "|" + q);
			if (v != null && !v.isBlank()) return v;
		}
		for (var e : m.entrySet())
		{
			if (e.getKey().startsWith(root + "|") && e.getValue() != null && !e.getValue().isBlank()
					&& !e.getKey().endsWith("|old") && !e.getKey().endsWith("|wrong"))
				return e.getValue();
		}
		return "";
	}

	// "Paul Golder" from { Sn:'Golder', Fn:'Paul' } (no title), or the value as it is
	static String name(String value)
	{
		Map<String, String> parts = parse(value);
		if (parts == null) return value.trim();
		List<String> words = new ArrayList<>();
		for (String key : new String[] { "Fn", "Mn", "Sn" })
		{
			String v = get(parts, key);
			if (!v.isEmpty()) words.add(v);
		}
		return words.isEmpty() ? get(parts, NAME_PARTS[0]) : String.join(" ", words);
	}

	// one line for each part present
	static String address(String value)
	{
		Map<String, String> parts = parse(value);
		if (parts == null) return value.trim().replaceAll("\\s*[,;]\\s*", "\n");
		List<String> lines = new ArrayList<>();
		for (String key : ADDRESS_PARTS)
		{
			String v = get(parts, key);
			if (!v.isEmpty()) lines.add(v);
		}
		return String.join("\n", lines);
	}

	// a phone number or email; if stored as { ... }, its first value
	static String plain(String value)
	{
		Map<String, String> parts = parse(value);
		if (parts == null) return value.trim();
		return parts.values().stream().filter(v -> !v.isBlank()).findFirst().orElse("").trim();
	}

	private static String get(Map<String, String> parts, String key)
	{
		for (var e : parts.entrySet())
		{
			if (e.getKey().equalsIgnoreCase(key)) return e.getValue().trim();
		}
		return "";
	}

	/*
	 * { key:'value', key2:"value 2" } (or key=value) to a map; null if the
	 * value is not in braces. Quotes may hold commas; \ escapes within them.
	 */
	static Map<String, String> parse(String value)
	{
		if (value == null) return null;
		String s = value.trim();
		if (!s.startsWith("{") || !s.endsWith("}")) return null;
		s = s.substring(1, s.length() - 1);
		Map<String, String> out = new LinkedHashMap<>();
		StringBuilder token = new StringBuilder();
		char quote = 0;
		for (int i = 0; i <= s.length(); i++)
		{
			char c = i < s.length() ? s.charAt(i) : ',';
			if (quote != 0)
			{
				if (c == '\\' && i + 1 < s.length()) token.append(s.charAt(++i));
				else if (c == quote) quote = 0;
				else token.append(c);
			} else if (c == '\'' || c == '"') quote = c;
			else if (c == ',')
			{
				String t = token.toString();
				int k = t.indexOf(':') >= 0 ? t.indexOf(':') : t.indexOf('=');
				if (k > 0) out.put(t.substring(0, k).trim(), t.substring(k + 1).trim());
				token.setLength(0);
			} else token.append(c);
		}
		return out;
	}
}
