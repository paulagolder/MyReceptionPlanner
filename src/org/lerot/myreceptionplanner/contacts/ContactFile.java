package org.lerot.myreceptionplanner.contacts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Reads contacts exported from an address book to a file, only ever reading:
 * a vCard file (.vcf: Google Contacts, Apple, Thunderbird, Outlook) or a CSV
 * file (Google Contacts' "Google CSV", old or new style, or Outlook's). Each
 * contact gives a name, address, phone, mobile and email, the first of each
 * kind; one with no name goes by their email, and one with neither is left out. Nothing reaches the network: the
 * file is exported by hand, and read again whenever it is searched.
 */
public class ContactFile
{
	// a contact found, with what the planner copies from it
	public record Entry(String name, String address, String phone, String mobile, String email)
	{
		// the line before the last of the address (usually the town), to tell
		// people of the same name apart
		public String place()
		{
			String[] lines = address.split("\n");
			return lines.length > 1 ? lines[lines.length - 2] : "";
		}
	}

	public static List<Entry> read(Path file) throws IOException
	{
		String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		if (text.startsWith("﻿")) text = text.substring(1);
		List<Fields> found = text.stripLeading().regionMatches(true, 0, "BEGIN:VCARD", 0, 11) ? vcards(text) : csv(text);
		List<Entry> out = new ArrayList<>();
		for (Fields f : found)
		{
			if (f.name.isBlank()) f.name = f.email;  // known only by their address
			if (f.name.isBlank()) continue;
			out.add(new Entry(f.name.strip(), f.address.strip(), f.phone, f.mobile, f.email));
		}
		out.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static class Fields
	{
		String name = "", address = "", phone = "", mobile = "", email = "";

		// a number goes to mobile or phone by its label, the first of each kind
		void number(String label, String value)
		{
			value = first(value);
			if (value.isEmpty()) return;
			String l = label.toLowerCase(Locale.ROOT);
			if (l.contains("mobile") || l.contains("cell"))
			{
				if (mobile.isEmpty()) mobile = value;
			} else if (phone.isEmpty()) phone = value;
		}
	}

	// Google keeps several values in one cell: "a@x.com ::: b@y.com"
	private static String first(String value)
	{
		if (value == null) return "";
		int at = value.indexOf(":::");
		return (at >= 0 ? value.substring(0, at) : value).strip();
	}

	private static String lines(String... parts)
	{
		StringBuilder sb = new StringBuilder();
		for (String p : parts)
		{
			if (p == null || p.isBlank()) continue;
			if (!sb.isEmpty()) sb.append('\n');
			sb.append(p.strip());
		}
		return sb.toString();
	}

	private static String words(String... parts)
	{
		return lines(parts).replace('\n', ' ');
	}

	// ---- vCard ----

	private static final Pattern PROPERTY = Pattern.compile("^(?:[\\w-]+\\.)?([\\w-]+)((?:;[^:]*)?):(.*)$");

	private static List<Fields> vcards(String text)
	{
		List<Fields> out = new ArrayList<>();
		Fields f = null;
		String fullName = "", structured = "";
		for (String line : unfold(text))
		{
			Matcher m = PROPERTY.matcher(line);
			if (!m.matches()) continue;
			String key = m.group(1).toUpperCase(Locale.ROOT);
			String params = m.group(2).toUpperCase(Locale.ROOT);
			String value = m.group(3);
			if (params.contains("QUOTED-PRINTABLE")) value = quotedPrintable(value);
			switch (key)
			{
				case "BEGIN" ->
				{
					f = new Fields();
					fullName = structured = "";
				}
				case "END" ->
				{
					if (f == null) break;
					f.name = !fullName.isBlank() ? fullName : structured;
					out.add(f);
					f = null;
				}
				case "FN" -> fullName = unescape(value);
				case "N" ->
				{
					// family; given; additional; prefix; suffix
					List<String> n = split(value);
					structured = words(at(n, 3), at(n, 1), at(n, 2), at(n, 0), at(n, 4));
				}
				case "EMAIL" -> { if (f != null && f.email.isEmpty()) f.email = unescape(value).strip(); }
				case "TEL" -> { if (f != null) f.number(params, unescape(value).replaceFirst("(?i)^tel:", "")); }
				case "ADR" ->
				{
					// PO box; extended; street; town; region; postcode; country (Google adds more after: left out)
					if (f == null || !f.address.isEmpty()) break;
					List<String> a = split(value);
					f.address = lines(at(a, 0), at(a, 1), at(a, 2), at(a, 3), at(a, 4), at(a, 5), at(a, 6));
				}
				default -> { }
			}
		}
		return out;
	}

	// a long line goes on in lines that start with a space or tab
	private static List<String> unfold(String text)
	{
		List<String> out = new ArrayList<>();
		for (String line : text.split("\\r?\\n"))
		{
			if (!out.isEmpty() && (line.startsWith(" ") || line.startsWith("\t")))
				out.set(out.size() - 1, out.get(out.size() - 1) + line.substring(1));
			else if (!out.isEmpty() && out.get(out.size() - 1).endsWith("=")
					&& out.get(out.size() - 1).toUpperCase(Locale.ROOT).contains("QUOTED-PRINTABLE"))
				out.set(out.size() - 1, out.get(out.size() - 1) + line);  // a soft line break
			else out.add(line);
		}
		return out;
	}

	// the parts between unescaped semicolons, each unescaped
	private static List<String> split(String value)
	{
		List<String> parts = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			if (c == '\\' && i + 1 < value.length())
			{
				sb.append(c).append(value.charAt(++i));
			} else if (c == ';')
			{
				parts.add(unescape(sb.toString()));
				sb.setLength(0);
			} else sb.append(c);
		}
		parts.add(unescape(sb.toString()));
		return parts;
	}

	private static String unescape(String s)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length())
			{
				char n = s.charAt(++i);
				sb.append(n == 'n' || n == 'N' ? '\n' : n);
			} else sb.append(c);
		}
		return sb.toString();
	}

	private static String quotedPrintable(String s)
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (c == '=' && i + 2 < s.length())
			{
				try
				{
					bytes.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
					i += 2;
					continue;
				} catch (NumberFormatException e)
				{
					// not an escape after all
				}
			}
			if (c == '=' && i == s.length() - 1) continue;  // a soft break
			bytes.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
		}
		return bytes.toString(StandardCharsets.UTF_8);
	}

	private static String at(List<String> parts, int i)
	{
		return i < parts.size() ? parts.get(i) : "";
	}

	// ---- CSV ----

	private static List<Fields> csv(String text)
	{
		List<List<String>> rows = rows(text);
		List<Fields> out = new ArrayList<>();
		if (rows.isEmpty()) return out;
		Map<String, Integer> col = new HashMap<>();
		List<String> header = rows.get(0);
		for (int i = 0; i < header.size(); i++) col.putIfAbsent(header.get(i).strip().toLowerCase(Locale.ROOT), i);
		for (List<String> row : rows.subList(1, rows.size()))
		{
			Fields f = new Fields();
			java.util.function.Function<String, String> v = name ->
			{
				Integer i = col.get(name);
				return i == null || i >= row.size() ? "" : row.get(i).strip();
			};
			f.name = v.apply("name");
			if (f.name.isEmpty())
				f.name = words(v.apply("name prefix"), v.apply("first name"), v.apply("given name"),
						v.apply("middle name"), v.apply("additional name"), v.apply("last name"),
						v.apply("family name"), v.apply("name suffix"));
			if (f.name.isEmpty()) f.name = first(v.apply("organization name"));
			if (f.name.isEmpty()) f.name = first(v.apply("organization 1 - name"));
			if (f.name.isEmpty()) f.name = v.apply("company");
			for (int n = 1; n <= 9 && f.email.isEmpty(); n++) f.email = first(v.apply("e-mail " + n + " - value"));
			if (f.email.isEmpty()) f.email = first(v.apply("e-mail address"));
			for (int n = 1; n <= 9; n++)
			{
				String label = v.apply("phone " + n + " - label");
				if (label.isEmpty()) label = v.apply("phone " + n + " - type");
				f.number(label, v.apply("phone " + n + " - value"));
			}
			f.number("mobile", v.apply("mobile phone"));
			f.number("home", v.apply("home phone"));
			f.number("work", v.apply("business phone"));
			for (int n = 1; n <= 9 && f.address.isEmpty(); n++)
			{
				String a = "address " + n + " - ";
				f.address = v.apply(a + "formatted");
				if (f.address.isEmpty())
					f.address = lines(v.apply(a + "po box"), v.apply(a + "street"), v.apply(a + "extended address"),
							v.apply(a + "city"), v.apply(a + "region"), v.apply(a + "postal code"), v.apply(a + "country"));
			}
			if (f.address.isEmpty())
				f.address = lines(v.apply("home street"), v.apply("home city"), v.apply("home state"),
						v.apply("home postal code"), v.apply("home country/region"));
			out.add(f);
		}
		return out;
	}

	// quoted fields may hold commas, doubled quotes and line breaks
	private static List<List<String>> rows(String text)
	{
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (quoted)
			{
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"')
				{
					cell.append('"');
					i++;
				} else if (c == '"') quoted = false;
				else cell.append(c);
			} else if (c == '"') quoted = true;
			else if (c == ',')
			{
				row.add(cell.toString());
				cell.setLength(0);
			} else if (c == '\n' || c == '\r')
			{
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
				row.add(cell.toString());
				cell.setLength(0);
				if (row.stream().anyMatch(s -> !s.isBlank())) rows.add(row);
				row = new ArrayList<>();
			} else cell.append(c);
		}
		row.add(cell.toString());
		if (row.stream().anyMatch(s -> !s.isBlank())) rows.add(row);
		return rows;
	}
}
