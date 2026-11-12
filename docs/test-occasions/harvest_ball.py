#!/usr/bin/env python3
"""Generate "The Harvest Ball", a large test occasion for MyReceptionPlanner.

About 470 guests: a few celebrities (in parties of 1-3) who dine at the Gala
Dinner and have reserved tables at the Ball, patrons who dine too, and the
whole village in families of 1-6, some with unnamed "+n" places.

  python3 harvest_ball.py <folder>

The Ball is a hall: three reserved tables, open tables round the dance
floor, and a capacity of 500 for those standing too.

Fixed random seed, so the same occasion each time.
"""
import random
import sys
from pathlib import Path
from xml.sax.saxutils import quoteattr as q

random.seed(1841)

MEN = ["Thomas", "William", "John", "George", "Henry", "Albert", "Arthur", "Edward", "Frederick", "Walter",
       "Harry", "Samuel", "Joseph", "Charles", "Alfred", "Ernest", "Frank", "Robert", "James", "Richard",
       "Daniel", "Oliver", "Jack", "Noah", "Leo", "Oscar", "Archie", "Theo", "Finn", "Seán", "Rhys", "Dafydd",
       "Ewan", "Callum", "Ravi", "Tomasz", "Kwame", "Matteo", "Jonas", "Piotr"]
WOMEN = ["Mary", "Elizabeth", "Sarah", "Ann", "Emily", "Alice", "Florence", "Edith", "Ada", "Martha",
         "Ellen", "Grace", "Rose", "Lily", "Ivy", "Mabel", "Olive", "Violet", "Agnes", "Harriet",
         "Sophie", "Chloe", "Amelia", "Isla", "Freya", "Zoë", "Niamh", "Siân", "Aoife", "Priya",
         "Agnieszka", "Ama", "Giulia", "Ingrid", "Maja", "Elin", "Bethan", "Catrin", "Ffion", "Hannah"]
SURNAMES = ["Smith", "Baker", "Miller", "Thatcher", "Cooper", "Fletcher", "Shepherd", "Carter", "Wheeler",
            "Turner", "Mason", "Tanner", "Weaver", "Fisher", "Hunter", "Archer", "Chandler", "Draper", "Dyer",
            "Brewer", "Butcher", "Farmer", "Gardener", "Harper", "Potter", "Sawyer", "Slater", "Taylor",
            "Walker", "Ward", "Wood", "Hill", "Green", "Brook", "Field", "Marsh", "Moor", "Ashby", "Thornton",
            "O'Neill", "McAllister", "Fitzgerald", "Smith-Jones", "Ap Rhys", "Van der Berg", "Nowak",
            "Kowalski", "Mensah", "Patel", "Rossi", "De la Mare", "Le Fevre", "Ó Briain", "Lloyd", "Price"]
COLOURS = ["#1F77B4", "#FF7F0E", "#2CA02C", "#D62728", "#9467BD", "#8C564B", "#E377C2", "#7F7F7F",
           "#BCBD22", "#17BECF", "#B22222", "#1F4E9C", "#2E8B57", "#DAA520", "#6A5ACD", "#CD5C5C"]
DIETS = ["v", "vg", "gf", "df", "nf", "h", "k"]

# fictional celebrities: ((title, forenames, family), gender, role); a list is one party, its first the lead
CELEBS = [
    [(("Dame", "Margot", "Fairweather"), "f", "Prima ballerina"), (("Sir", "Anton", "Fairweather"), "m", "Conductor")],
    [(("", "Rudolf", "Kestrel"), "m", "Principal dancer")],
    [(("Lady", "Imogen", "Ashcombe"), "f", "Patron"), (("Lord", "", "Ashcombe"), "m", "Patron"), (("Hon.", "Clara", "Ashcombe"), "f", "")],
    [(("", "Celeste", "Moreau"), "f", "Choreographer"), (None, "u", "")],
    [(("", "Vikram", "Sethi"), "m", "Film director"), (("", "Anjali", "Sethi"), "f", "")],
    [(("The Mayor of Thornbury", "", ""), "u", "Mayor"), (("The Mayoress", "", ""), "f", "Mayoress")],
    [(("Bishop", "Ambrose", "Penhallow"), "m", "Bishop")],
    [(("", "Olga", "Brandt"), "f", "Ballet mistress"), (("", "Kurt", "Brandt"), "m", "")],
    [(("", "Jasper", "Quill"), "m", "Critic")],
    [(("Signora", "Lucia", "Bellini"), "f", "Soprano"), (None, "u", "")],
    [(("Dr", "Hamish", "McTavish"), "m", "Festival director"), (("", "Morag", "McTavish"), "f", "")],
    [(("", "Elodie", "Vance"), "f", "Television presenter"), (("", "Marcus", "Vance"), "m", ""), (None, "u", "")],
    [(("Sir", "Percival", "Hawkes"), "m", "Sponsor"), (("Lady", "", "Hawkes"), "f", "Sponsor")],
    [(("", "Ines", "Carvalho"), "f", "Costume designer")],
]

guest_id = 0
party_id = 0
parties = []          # dicts: id, name, colour, lead, guests, kind
families_by_surname = {}


def new_guest(name, gender, role="", plus=0):
    """name: (title, forenames, family), or None for a place not named."""
    global guest_id
    guest_id += 1
    g = {"id": guest_id, "name": name, "gender": gender, "role": role, "plus": plus,
         "diet": "", "contact": None}
    if random.random() < 0.08:
        g["diet"] = " ".join(sorted(random.sample(DIETS, random.choice([1, 1, 1, 2]))))
    return g


def new_party(name, guests, kind):
    global party_id
    party_id += 1
    lead = guests[0]
    first = (lead["name"][1] or lead["name"][2] or "guest").split()[0].lower().replace("'", "").replace(" ", "")
    lead["contact"] = {"email": f"{first}.{party_id}@example.com"}
    if random.random() < 0.6:
        lead["contact"]["mobile"] = f"07700 9{random.randint(0, 99999):05d}"
    if random.random() < 0.3:
        lead["contact"]["address"] = [f"{random.randint(1, 120)} {random.choice(['High Street', 'Mill Lane', 'Church Road', 'The Green', 'Orchard Way'])}", "Thornbury"]
    p = {"id": party_id, "name": name, "colour": random.choice(COLOURS), "lead": lead["id"],
         "guests": guests, "kind": kind}
    # unnamed places are the lead's +1, +2 ...
    n = 0
    for g in guests:
        if g["name"] is None:
            n += 1
            g["plus"] = n
    parties.append(p)
    return p


# celebrities
for members in CELEBS:
    gs = [new_guest(n, s, r) for n, s, r in members]
    new_party("", gs, "celeb")

# patrons: couples who dine at the Gala
for _ in range(22):
    sur = random.choice(SURNAMES)
    gs = [new_guest(("", random.choice(MEN), sur), "m", "Patron"), new_guest(("", random.choice(WOMEN), sur), "f", "Patron")]
    random.shuffle(gs)
    new_party("", gs, "patron")

# the village: families of 1-6, a few with unnamed places, some sharing a surname
while guest_id < 470:
    sur = random.choice(SURNAMES)
    size = random.choices([1, 2, 3, 4, 5, 6], weights=[14, 30, 18, 20, 10, 8])[0]
    gs = []
    if size == 1:
        sex = random.choice("mf")
        gs.append(new_guest(("", random.choice(MEN if sex == 'm' else WOMEN), sur), sex))
    else:
        gs.append(new_guest(("", random.choice(MEN), sur), "m"))
        gs.append(new_guest(("", random.choice(WOMEN), sur), "f"))
        for _ in range(size - 2):
            if random.random() < 0.25:
                gs.append(new_guest(None, "u"))
            else:
                sex = random.choice("mf")
                gs.append(new_guest(("", random.choice(MEN if sex == 'm' else WOMEN), sur), sex))
        if random.random() < 0.5:
            gs[0], gs[1] = gs[1], gs[0]
    count = families_by_surname.get(sur, 0) + 1
    families_by_surname[sur] = count
    name = f"The {sur} family" if size > 2 and random.random() < 0.6 else ""
    new_party(name, gs, "village")

# a few look-alikes to try the duplicate check: the same person put in twice
village = [p for p in parties if p["kind"] == "village"]
for p in random.sample(village, 4):
    src = p["guests"][0]
    _, first, family = src["name"]
    alias = {"Thomas": "Tom", "William": "Bill", "Elizabeth": "Liz", "Robert": "Bob", "Edward": "Ted",
             "Richard": "Dick", "Margaret": "Peggy", "Samuel": "Sam", "Frederick": "Fred"}.get(first, first)
    new_party("", [new_guest(("", alias, family), src["gender"])], "village")

all_guests = [g for p in parties for g in p["guests"]]
print(f"{len(parties)} parties, {len(all_guests)} guests "
      f"({sum(1 for g in all_guests if g['name'] is None)} unnamed places)")


def write_guests(path):
    out = ['<?xml version="1.0" encoding="UTF-8"?>', "", "<guests>"]
    for p in parties:
        attrs = f' id="{p["id"]}"' + (f' name={q(p["name"])}' if p["name"] else "") \
                + f' colour="{p["colour"]}" lead="{p["lead"]}"'
        out.append(f"  <party{attrs}>")
        for g in p["guests"]:
            a = f'id="{g["id"]}"'
            if g["name"]:
                for key, part in zip(("title", "forenames", "family"), g["name"]):
                    if part:
                        a += f' {key}={q(part)}'
            a += f' gender="{g["gender"]}"'
            if g["role"]:
                a += f' role={q(g["role"])}'
            if g["name"] is None:
                a += f' plus="{g["plus"]}"'
            if g["diet"]:
                a += f' diet="{g["diet"]}"'
            c = g["contact"]
            if not c:
                out.append(f"    <guest {a}/>")
                continue
            ca = "".join(f" {k}={q(v)}" for k, v in c.items() if k != "address")
            out.append(f"    <guest {a}>")
            if "address" in c:
                out.append(f"      <contact{ca}>")
                out.append("        <address>" + "".join(f"<line>{l}</line>" for l in c["address"]) + "</address>")
                out.append("      </contact>")
            else:
                out.append(f"      <contact{ca}/>")
            out.append("    </guest>")
        out.append("  </party>")
    out.append("</guests>")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def invitations(party_list):
    """Each party's state; the lead is expected whenever anyone is."""
    states = {"decided": [], "invited": [], "accepted": [], "declined": []}
    for p in party_list:
        state = random.choices(["accepted", "invited", "declined", "decided"], weights=[76, 14, 5, 5])[0]
        for g in p["guests"]:
            # now and then one of an accepting family can't come
            s = "declined" if state == "accepted" and g["id"] != p["lead"] and random.random() < 0.05 else state
            states[s].append(g["id"])
    return states


def inv_xml(states):
    return "  <invitations " + " ".join(f'{k}="{" ".join(map(str, v))}"' for k, v in states.items() if v) + "/>"


def write_gala(path):
    diners = [p for p in parties if p["kind"] in ("celeb", "patron")]
    st = invitations(diners)
    # celebrities all accept
    for p in diners:
        if p["kind"] == "celeb":
            for g in p["guests"]:
                for k in st:
                    if g["id"] in st[k]:
                        st[k].remove(g["id"])
                st["accepted"].append(g["id"])
    accepted = set(st["accepted"])
    tables = ['    <table id="1" shape="rectangle" sideplaces="10" endplaces="0" x="1200" y="150" rotation="0" head="true" disabled="11 12 13 14 15 16 17 18 19 20"/>']
    for i in range(10):
        x = 400 + (i % 5) * 400
        y = 550 + (i // 5) * 400
        tables.append(f'    <table id="{i + 2}" number="{i + 1}" shape="round" places="10" x="{x}" y="{y}" rotation="0"/>')
    seats = []
    head = [g for p in diners if p["kind"] == "celeb" for g in p["guests"]][:10]
    for i, g in enumerate(head):
        if g["id"] in accepted:
            seats.append((g["id"], 1, i + 1))
    seated = {s[0] for s in seats}
    table, chair = 2, 1
    for p in diners:
        members = [g for g in p["guests"] if g["id"] in accepted and g["id"] not in seated]
        if not members:
            continue
        if chair + len(members) - 1 > 10:
            table, chair = table + 1, 1
        if table > 11:
            break  # the rest left for seating by hand
        for g in members:
            seats.append((g["id"], table, chair))
            chair += 1
    out = ['<?xml version="1.0" encoding="UTF-8"?>', "",
           '<event name="Gala Dinner" type="seated" catered="yes" place="The Assembly Rooms, Thornbury" time="18:30">',
           inv_xml(st), "  <tables>", *tables, "  </tables>", "  <seating>"]
    out += [f'    <seat guest="{g}" table="{t}" chair="{c}"/>' for g, t, c in seats]
    out += ["  </seating>", "  <choices/>", "</event>"]
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    return len(seats), len(accepted)


def write_ball(path):
    st = invitations(parties)
    for p in parties:
        if p["kind"] == "celeb":
            for g in p["guests"]:
                for k in st:
                    if g["id"] in st[k]:
                        st[k].remove(g["id"])
                st["accepted"].append(g["id"])
    tables = ['    <table id="1" name="Bandstand" shape="altar" length="8" x="1500" y="120" rotation="0"/>']
    tid = 1
    # three reserved tables in front of the band
    for i in range(3):
        tid += 1
        tables.append(f'    <table id="{tid}" name="Reserved {chr(65 + i)}" shape="round" places="10" x="{1000 + i * 500}" y="450" rotation="0"/>')
    # open tables round the dance floor, every seat open
    open_all = " ".join(str(n) for n in range(1, 11))
    n = 0
    for side in (300, 2700):
        for row in range(6):
            for col in range(2):
                tid += 1
                n += 1
                tables.append(f'    <table id="{tid}" number="{n}" shape="round" places="10" x="{side + (col * 300 if side < 1500 else -col * 300)}" y="{800 + row * 300}" rotation="0" open="{open_all}"/>')
    seats = []
    accepted = set(st["accepted"])
    celebs = [g for p in parties if p["kind"] == "celeb" for g in p["guests"] if g["id"] in accepted]
    for i, g in enumerate(celebs[:30]):
        seats.append((g["id"], 2 + i // 10, i % 10 + 1))
    out = ['<?xml version="1.0" encoding="UTF-8"?>', "",
           '<event name="Harvest Ball" type="hall" capacity="500" catered="no" place="The Corn Exchange, Thornbury" time="21:00">',
           inv_xml(st), "  <tables>", *tables, "  </tables>", "  <seating>"]
    out += [f'    <seat guest="{g}" table="{t}" chair="{c}"/>' for g, t, c in seats]
    out += ["  </seating>", "  <choices/>", "</event>"]
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    expected = len(st["accepted"]) + len(st["invited"]) + len(st["decided"])
    return len(seats), expected


folder = Path(sys.argv[1])
folder.mkdir(parents=True, exist_ok=True)
write_guests(folder / "guests.xml")
gala = write_gala(folder / "gala-dinner.xml")
ball = write_ball(folder / "harvest-ball.xml")
(folder / "occasion.xml").write_text(
    '<?xml version="1.0" encoding="UTF-8"?>\n\n'
    '<occasion name="The Harvest Ball" date="2027-09-25" from="The Thornbury Harvest Committee" '
    'reply="harvest@example.com" countrycode="44">\n'
    '  <event file="gala-dinner.xml"/>\n  <event file="harvest-ball.xml"/>\n  <communications/>\n</occasion>\n',
    encoding="utf-8")
print(f"Gala Dinner: {gala[0]} seated of {gala[1]} accepted")
print(f"Harvest Ball: {ball[0]} at reserved tables, {ball[1]} expected")
