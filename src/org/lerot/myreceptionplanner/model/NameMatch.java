package org.lerot.myreceptionplanner.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/*
 * Whether two names might be the same person, and why: "Tom Perks" and
 * "Thomas Perks", "T Perks", "Tom Parks", "Perks Tom". Names are compared
 * without titles (Mr, Dr, Revd ...), accents, punctuation or case; the first
 * word is the first name and the last the surname. A name of one word, or of
 * more than one person ("Tim & Claire Perks"), is only ever like the same.
 */
public final class NameMatch
{
	private NameMatch() {}

	private static final Set<String> TITLES = Set.of("mr", "mrs", "ms", "miss", "mx", "dr", "rev", "revd",
			"reverend", "fr", "father", "sir", "dame", "lady", "lord", "prof", "professor", "capt", "captain",
			"master", "jr", "jnr", "sr", "snr", "junior", "senior", "ii", "iii");

	// forms of the same first name; a name may be in more than one group
	private static final String[][] FORMS = {
			{ "thomas", "tom", "tommy", "thom" },
			{ "william", "will", "bill", "billy", "willy", "liam", "wills" },
			{ "robert", "rob", "bob", "bobby", "robbie", "bert", "robin" },
			{ "richard", "rick", "dick", "rich", "richie", "ricky" },
			{ "james", "jim", "jimmy", "jamie", "jem" },
			{ "michael", "mike", "mick", "micky", "mikey" },
			{ "catherine", "katherine", "kathryn", "kate", "katie", "kath", "cath", "cathy", "kathy", "kat", "kitty" },
			{ "elizabeth", "liz", "lizzie", "beth", "betty", "eliza", "libby", "bess", "lisa", "elisabeth" },
			{ "susan", "sue", "susie", "suzanne", "suzy" },
			{ "jennifer", "jen", "jenny" },
			{ "christopher", "chris", "kit", "topher" },
			{ "christine", "christina", "chris", "chrissie", "tina" },
			{ "alexander", "alex", "sandy", "alec", "xander" },
			{ "alexandra", "alex", "sandra", "sasha", "lexi" },
			{ "andrew", "andy", "drew" },
			{ "anthony", "antony", "tony" },
			{ "david", "dave", "davy", "davie" },
			{ "daniel", "dan", "danny" },
			{ "edward", "ed", "eddie", "ted", "teddy", "ned" },
			{ "edmund", "ed", "eddie", "ned" },
			{ "henry", "harry", "hal", "hank" },
			{ "john", "jack", "johnny", "jon", "jonny" },
			{ "jonathan", "jon", "jonny", "nathan" },
			{ "joseph", "joe", "joey", "jos" },
			{ "joanna", "joanne", "jo", "joan" },
			{ "josephine", "jo", "josie" },
			{ "benjamin", "ben", "benny", "benji" },
			{ "samuel", "sam", "sammy" },
			{ "samantha", "sam", "sammy" },
			{ "nicholas", "nick", "nicky", "nico" },
			{ "nicola", "nicky", "nikki" },
			{ "matthew", "matt", "matty" },
			{ "patrick", "pat", "paddy" },
			{ "patricia", "pat", "patty", "trish", "tricia" },
			{ "margaret", "maggie", "meg", "peg", "peggy", "marge", "madge", "greta", "rita", "maisie" },
			{ "ann", "anne", "anna", "annie", "nan", "nancy", "hannah" },
			{ "peter", "pete" },
			{ "philip", "phillip", "phil", "pip" },
			{ "philippa", "pippa", "pip" },
			{ "stephen", "steven", "steve", "stevie" },
			{ "timothy", "tim", "timmy" },
			{ "frederick", "fred", "freddie", "freddy" },
			{ "charles", "charlie", "chas", "chuck" },
			{ "charlotte", "charlie", "lottie" },
			{ "gregory", "greg" },
			{ "geoffrey", "jeffrey", "geoff", "jeff" },
			{ "kenneth", "ken", "kenny" },
			{ "lawrence", "laurence", "larry", "laurie" },
			{ "leonard", "len", "lenny", "leo" },
			{ "nathaniel", "nathan", "nat", "nate" },
			{ "natalie", "nat", "natasha", "tasha" },
			{ "ronald", "ron", "ronnie" },
			{ "raymond", "ray" },
			{ "sidney", "sydney", "sid" },
			{ "victor", "vic" },
			{ "victoria", "vicky", "vikki", "tori" },
			{ "deborah", "debra", "deb", "debbie" },
			{ "diana", "diane", "di" },
			{ "valerie", "val" },
			{ "gillian", "jillian", "gill", "jill" },
			{ "amanda", "mandy" },
			{ "rebecca", "becky", "becca", "bex" },
			{ "abigail", "abby", "abbie", "gail" },
			{ "eleanor", "elinor", "ellie", "nell", "nellie", "elle" },
			{ "helen", "helena", "nell", "ellie" },
			{ "mary", "molly", "polly", "mae", "may" },
			{ "sarah", "sara", "sally", "sadie" },
			{ "theresa", "teresa", "tess", "tessa", "terri" },
			{ "louise", "louisa", "lou", "lulu" },
			{ "louis", "lewis", "lou" },
			{ "frances", "fran", "frankie", "fanny" },
			{ "francis", "frank", "frankie" },
			{ "angus", "gus" },
			{ "augustus", "gus" },
			{ "archibald", "archie" },
			{ "albert", "bert", "bertie", "al", "alby" },
			{ "herbert", "bert", "herb" },
			{ "alfred", "alf", "alfie", "fred" },
			{ "oliver", "ollie", "olly" },
			{ "olivia", "liv", "livvy", "ollie" },
			{ "isabel", "isabella", "isobel", "izzy", "bella", "issy" },
			{ "isaac", "ike", "izzy", "zac" },
			{ "lydia", "liddy" },
			{ "jacqueline", "jackie", "jacqui" },
			{ "barbara", "barb", "babs" },
			{ "dorothy", "dot", "dottie", "dora" },
			{ "gerald", "gerard", "gerry", "jerry", "ged" },
			{ "terence", "terrence", "terry" },
			{ "douglas", "doug", "dougie" },
			{ "donald", "don", "donnie" },
			{ "gordon", "gordy" },
			{ "malcolm", "mal" },
			{ "graham", "grahame", "gray" },
			{ "jessica", "jess", "jessie" },
			{ "emily", "em", "emmy", "millie" },
			{ "emma", "em", "emmy" },
			{ "amelia", "millie", "amy" },
			{ "mildred", "millie" },
			{ "caroline", "carol", "carrie", "caro" },
			{ "penelope", "penny", "pen" },
			{ "rosemary", "rose", "rosie" },
			{ "rosalind", "ros", "roz", "rosie" },
			{ "zachary", "zach", "zac", "zack" },
	};

	private static final Map<String, List<Integer>> GROUPS = new HashMap<>();

	static
	{
		for (int i = 0; i < FORMS.length; i++)
			for (String n : FORMS[i])
				GROUPS.computeIfAbsent(n, k -> new ArrayList<>()).add(i);
	}

	// "Tim & Claire Perks", "the Smith family": more than one person, so
	// only ever like the same name
	static boolean isCouple(String name)
	{
		String s = " " + name.toLowerCase(Locale.UK) + " ";
		return s.contains("&") || s.contains(" and ") || s.contains(" family ") || s.contains(" + ");
	}

	// lower case, no accents, titles or punctuation, in words
	static List<String> words(String name)
	{
		if (name == null) return List.of();
		String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.UK).replaceAll("['’.]", "").replaceAll("[^a-z\\s-]", " ").replace('-', ' ');
		List<String> words = new ArrayList<>(Arrays.asList(s.trim().split("\\s+")));
		words.removeIf(w -> w.isEmpty());
		while (words.size() > 1 && TITLES.contains(words.get(0))) words.remove(0);
		while (words.size() > 1 && TITLES.contains(words.get(words.size() - 1))) words.remove(words.size() - 1);
		return words;
	}

	/*
	 * Why the two names might be the same person, or empty if they seem not
	 * to be. The reason reads after the names, e.g. "Tom and Thomas are forms
	 * of one name".
	 */
	public static Optional<String> why(String a, String b)
	{
		List<String> wa = words(a), wb = words(b);
		if (wa.isEmpty() || wb.isEmpty()) return Optional.empty();
		if (wa.equals(wb)) return Optional.of("the same name");
		if (wa.size() == 1 || wb.size() == 1 || isCouple(a) || isCouple(b)) return Optional.empty();
		String fa = wa.get(0), fb = wb.get(0), sa = wa.get(wa.size() - 1), sb = wb.get(wb.size() - 1);
		if (fa.equals(sb) && sa.equals(fb)) return Optional.of("the same names the other way round");
		String firsts = firsts(fa, fb, cap(wa.get(0), a), cap(wb.get(0), b));
		if (sa.equals(sb))
		{
			if (fa.equals(fb)) return Optional.of("the same first name and surname");
			return Optional.ofNullable(firsts);
		}
		boolean surnamesAlike = Math.min(sa.length(), sb.length()) >= 4 && distance(sa, sb) <= 1;
		if (surnamesAlike && (fa.equals(fb) || (firsts != null && firsts.contains("forms"))))
			return Optional.of("surnames spelt alike" + (fa.equals(fb) ? "" : ", and " + firsts));
		return Optional.empty();
	}

	// how two first names might be the same, or null
	private static String firsts(String a, String b, String showA, String showB)
	{
		if (a.equals(b)) return "the same first name";
		if (sameForms(a, b)) return showA + " and " + showB + " are forms of one name";
		if (a.length() == 1 && b.startsWith(a)) return showA + " could stand for " + showB;
		if (b.length() == 1 && a.startsWith(b)) return showB + " could stand for " + showA;
		String shorter = a.length() <= b.length() ? a : b, longer = shorter == a ? b : a;
		if (shorter.length() >= 3 && longer.length() - shorter.length() >= 2 && longer.startsWith(shorter))
			return (shorter == a ? showA : showB) + " could be short for " + (shorter == a ? showB : showA);
		int min = Math.min(a.length(), b.length());
		if (min >= 4 && distance(a, b) <= (min >= 6 ? 2 : 1)) return showA + " and " + showB + " are spelt alike";
		return null;
	}

	private static boolean sameForms(String a, String b)
	{
		List<Integer> ga = GROUPS.get(a), gb = GROUPS.get(b);
		return ga != null && gb != null && ga.stream().anyMatch(gb::contains);
	}

	// the word as it was written in the name, for showing in a reason
	private static String cap(String word, String name)
	{
		for (String w : name.trim().split("[\\s-]+"))
		{
			String plain = Normalizer.normalize(w, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
					.toLowerCase(Locale.UK).replaceAll("[^a-z]", "");
			if (plain.equals(word)) return w.replaceAll("[.,]$", "");
		}
		return word;
	}

	// the fewest letters added, taken away or changed to turn one into the other
	static int distance(String a, String b)
	{
		int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) prev[j] = j;
		for (int i = 1; i <= a.length(); i++)
		{
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++)
			{
				int change = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + change);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}
}
