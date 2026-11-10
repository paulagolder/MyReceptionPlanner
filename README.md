# MyReceptionPlanner

Plan the tables at a reception: lay out the tables, seat the guests, and print
the table plan, a list for each table and a place card for each guest.

## Installing

On Linux (Debian, Ubuntu, Mint), download `myreceptionplanner_<version>_amd64.deb`
from the latest release on GitHub and install it:

    sudo apt install ./myreceptionplanner_1.0.0_amd64.deb

It brings its own Java, so nothing else is needed. MyReceptionPlanner is then
in the Office menu, and in `/opt/myreceptionplanner`. A newer version
installs over the old; `sudo apt remove myreceptionplanner` takes it away.
Your occasions, in `~/Documents/MyReceptions`, are not touched.

## Commands (from this folder)

    ./gradlew run     start the app
    ./gradlew test    run the automated tests
    ./gradlew packageApp -PappVersion=1.0.0
                      build the installer (a .deb on Linux) in build/package

A release is made by pushing a version tag; GitHub then builds the
installer and puts it on a release page:

    git tag v1.0.0 && git push origin v1.0.0

## Using it

### Occasions and events

The **occasion** is the whole celebration, such as *Lydia and Isaac's
Wedding* on *12 May 2026*. It has one guest list (the parties, their leads
and contact details) shared by its **events**, such as the *Wedding
Breakfast* at *The Old Barn, Lichfield* at *2pm* and the *Evening
Reception*. Each event has its own tables and its own seating, so a guest
can sit in one place at the breakfast and another in the evening. These
names, places, dates and times head the reports and anything sent to the
guests.

A date is typed as "12 May 2026", "12/5/2026" or "2026-05-12", and may
start with a day of the week ("Monday, 16 July 1595"). The planner always
shows the weekday the date fell on, replacing any that was typed; before
14 September 1752 that is the Old Style (Julian) weekday, as in Britain.

The toolbar starts with:

- **Occasion ▾** → *Progress…* (below), *Occasion details…* (name and
  date), *Invitations…* (opens the Guests view), *New occasion…*, *Open occasion…* and
  *Save…* (saves the whole occasion).
- **Event | Guests** switches between the two views. The **Guests** view is
  the guest list of the whole occasion (below); the plan's controls are
  hidden there. The **Event** view shows the plan of one event, with:
  - the **event chooser**, picking which;
  - **Event ▾** → *Event details…* (name, place, date and time, leaving the
    date empty if it is on the occasion's day; type, capacity, and
    **Catered**, ticked when food is served), *Check…* and *Table plan
    (PDF)…* (below), *Export layout…* (below), *Save this event…* (only its
    file; an event or occasion never saved before saves the whole occasion),
    *New event…* (starting with an empty room, a copy of another event's
    tables or a layout file, and inviting everyone, no one or the guests of
    another event) and *Delete this event…*;
  - **Layout ▾** (add a table, pew or altar, or *Import layout…*) and
    **Table ▾** (below);
  - **Seating**, on to seat the guests (it shows blue, and the seat panel
    opens on the right) and off to set out the tables;
  - **Catering ▾**, for a catered event only: *Import menu…* (only while
    the event has no menu), *Menu…*, *Menu choices and dietary needs…*,
    *Catering list…*, *Kitchen quantities…*, *Service by table…* (below)
    and *Export menu…*.
- **Messages ▾** is there in both views.
- **Info ▾**, at the right-hand end, → *Help…* (still to be written) and
  *About MyReceptionPlanner…* (the version, when it was built, the Java it
  runs on, the occasion open and its folder, and where the settings are kept).

### Layouts and menus as files

A room's layout or a menu can be kept in a file of its own, to use again in
another event or occasion: a venue's standard room, say, or a caterer's
menu. Imported, it can be changed like any other.

- **Event ▾ → Export layout…** writes the event's tables to
  `Output/<event>_layout.xml` (such as `wedding-breakfast_layout.xml`)
  unless you choose another name or place.
- **Layout ▾ → Import layout…** puts a layout file's tables in place of the
  event's own (asking first; anyone seated loses their seat). *New event…*
  can also start from a layout file: *Import layout*, under *Start with*.
- **Catering ▾ → Export menu…** writes the menu, with its note, to
  `Output/<event>_menu.xml`.
- **Catering ▾ → Import menu…**, there while the event has no menu, takes a
  menu file and opens it in *Menu…* to change.

An event's own file (`wedding-breakfast.xml`, from this or another
occasion) can be imported as a layout or a menu too.

### The Guests view

The parties on the left (*New…*, *Edit…*, *Colour…*, *Remove*; double-click
to edit, or the colour to change it) and every guest on the right: name
(type over it; a place not named yet shows as the lead's name +1),
role (type it in: who they are at the occasion, "Mother of the bride",
"Guest speaker", up to 20 characters), gender, party, dietary needs (double-click to change), how the party's lead
can be reached (✉ email, ☎ phone, ⌂ address; double-click to edit the
party), and a mark for where they stand with each event (below; click it to
change it). *Only the party chosen* narrows the list to one party; the
totals at the foot count, for each event, those expected, accepted,
awaiting a reply, to invite and declined. Seats are given in the Event view,
in Seating.

Each guest stands with each event in one of five ways:

| Mark | Meaning |
|---|---|
| (nothing) | not invited |
| ○ ring | to be invited: decided on, the invitation not yet sent |
| ● dot | invited: the invitation has been sent |
| ✔ tick | accepted |
| ✘ cross | declined, or cancelled |

Those with a ring, dot or tick are expected: they can be seated, are
catered for and count against the capacity. A new guest gets a ring
wherever their party is invited (a new party, every event). Noting the
*Invitation* message as sent to a party (Messages ▾ → Write to guests…)
turns its rings into dots; writing the Invitation to a party already
invited says a reminder may suit better. Inviting anyone of a party brings
its lead; a lead marked not invited or declined takes the party with them,
asking first if anyone loses a seat. A sent invitation cannot go back to
nothing, only be declined or cancelled.

**Taking guests off the list.** Removing a guest, a party's places not named
yet, or places in the party dialog always asks "are you sure?", and is
refused while an invitation sent to them stands (a dot or tick anywhere):
mark it declined or cancelled first.

### Progress

**Occasion ▾ → Progress…** shows how far the planning has got, following
the workflow. First the occasion's stages: *Define occasion* (date, sender,
how to reply), *Define events* (each with a place and time), *Manage guests*
(places named; leads with no way to reach them; names alike), *Send
invitations* (invitations sent, ● or ✔, of all those decided on), *Send out
menus* (parties sent the *Menu choices* message, or with nothing left to
choose) and *Process replies* (of the invitations sent, those accepted or
declined). Then a
grid with a column for each event: *Tables and seats* (places, or the
capacity, against those invited), *Menu* (a dish in every course for every
invited guest's needs), *Allocate seats* and *Menu choices*.

Each stage has a mark (a green tick when done, an amber half when part done,
a ring when not started, a dash when it does not apply), a bar, a count and
what is left; red means something to put right. Click a stage to go and do
it: the details, the menu and choices and the messages open over the
screen, which then shows the new state; tables and seating take you to that
event's plan.

### One person, once

Each person should be on the guest list only once, in one party. Whenever a
name is typed (in the seat list, the guest list or a party), it is compared
with everyone already on the list, ignoring titles, accents, punctuation and
case. You're warned, with who and why, if it has:

- the same name, or the same first name and surname with a different middle name;
- first names that are forms of one name: Tom / Thomas / Tommy, Kate /
  Catherine / Kath, Bill / William, Steve / Stephen / Steven …;
- an initial for the first name (T Perks / Thomas Perks), or a first name
  that starts the other (Jos / Joshua);
- spellings a slip apart (Clair / Claire, Perks / Parks);
- the names the other way round (Perks Tom).

A name of one word, or of more than one person ("Tim & Claire Perks"), only
matches the same name. Answer *A different person* to go ahead (those two are
then never flagged again), or *Go back*. When typing a new name into an empty
seat, you can instead seat the guest already on the list. **Names
alike…** in the Guests view lists every pair alike across the occasion; mark a pair *Not
the same person* to leave it out from now on, or rename or remove a real
duplicate in the Guests tab.

### Dietary needs and menus

Menus, choices and the kitchen's lists are for **catered** events (tick
*Catered* in the event's details; the Catering ▾ menu shows only then).
Unticking it keeps the menu and choices but stops them being served,
counted or asked for. An older event file with a menu opens as catered.

- **Dietary needs belong to each guest** and hold at every event: Vegetarian
  (v), Vegan (vg), Gluten-free (gf), Dairy-free (df), Nut allergy (nf), Halal
  (h), Kosher (k), and a note in words ("no sesame"). Set them by
  double-clicking the *Diet* column in the Guests tab, or in *Menu choices*.
  They can be gathered long before any menu is written: the catering list
  counts them from the start. Only named guests have needs and menu choices,
  so they follow the person wherever they are seated; a place not yet named
  (Ann Smith+1) chooses once it is named, and a guest whose name is cleared
  loses theirs. The catering list and Check… count places not yet named
  apart.
- **Catering ▾ → Menu…** sets out the shown event's menu: a line for each
  dish, with its course (Starter, Main, Dessert …) and ticks for the diets it
  suits (a vegan dish suits vegetarians too), and a **note** of a few lines
  about the menu as a whole ("All our food is sourced locally"), which goes
  with the menu to the guests (`{menu}`) and on the catering list.
- **Catering ▾ → Menu choices and dietary needs…** lists those invited to
  the event, where they sit, their needs, a choice for each course and a
  note for this event only. A dish that does not suit a guest's needs shows
  in red with ⚠.
- **Catering ▾ → Catering list…** shows, for the event: how many invited
  guests have each need; how many of each dish, and at each table; everyone
  with a need or a note, where they sit and what they chose; choices that do
  not suit; and who has yet to choose. *Save as PDF…* makes a copy for the
  caterers.
- **Catering ▾ → Kitchen quantities…**: course by course, how many of each
  dish to make, and how many of those must meet a dietary need or have a
  note ("Risotto 12, of which 3 vg, 1 nf"), with how many have still to
  choose or are places not yet named, and any choices that do not suit.
- **Catering ▾ → Service by table…**: which dish goes to which seat. For each
  table or pew in turn, a line for each seat with someone in it, in seat
  order: the guest, their dish for each course, their needs and note; under
  it the table's dishes counted, to plate up a table at a time. Invited
  guests without a seat come last. "?" is a dish not yet chosen, "–" a place
  not yet named. *Save as PDF, a page per table…* gives a sheet for each
  table. *Check…* also says who has yet to choose and any unsuitable
  choices, and the table plan PDF gives each guest's needs after their name.

### Seated events, halls and unreserved parties

Each event is one of three types, set in *New event…* or *Event details…*:

- **Seated** (the Wedding Breakfast): guests are invited and each is given
  a seat.
- **Hall / ceremony** (the church): set out mostly with pews whose seats
  are open to all; only the seats you reserve (the front rows, places for
  ushers or for guests who need them) are given to guests in Seating. Its
  capacity is all its usable seats, and you're warned when more guests are
  invited than that. *Check…* lists the reserved seats not yet given and
  the open seats.
- **Party, unreserved** (the evening do): guests are invited, and tables can
  be set out in Layout for the staff preparing the room, but no one is given
  a seat, so Seating is switched off. It can have a **capacity** (0 for no
  limit): the status bar, the Guests view's totals and *Check…* warn when more guests
  are invited than it allows. Its table plan PDF shows each table with its
  number of places instead of names. Making a seated event unreserved clears
  its seats, after asking.

New events invite everyone to start with (or no one, or another event's
guests).

### Invitations

Each event has its own list of who is invited, and only those expected can
be seated there. Invitations and replies are changed only in the **Guests
view** (*Occasion ▾ → Invitations…* opens it): a column of marks for each
event, click one to change it (see The Guests view). The only other place
is *New event…*, which can start the new event with everyone, no one or
another event's guests to be invited.

A new party is invited to every event. A new place in a party goes wherever
the party is already invited. In Seating's Guests tab, only those with an
invitation to the event shown are listed, with the same marks (click one
for what it means; they are changed only in the Guests view), the party list shows how many of each party are expected there, and
*Not seated* lists those expected but not yet seated.

The plan works in two modes, switched by **Seating** on the toolbar:

- **Seating off**: set out the tables (below).
- **Seating on**: seat the guests. The tables stay put; click a table, or one of
  its chairs, and the panel on the right lists its seats, seat 1 first, with
  who sits in each. Seat numbers show on the empty chairs.

In both modes, a chair with a guest in it carries a mark: **● a woman,
■ a man, ◆ not known**, in the colour of their party (grey if in no party).

### Seating

- In the **Table** tab, type a name on an empty seat, or choose a gender or a
  party, and a new guest is put there. Choosing only a party keeps the seat for
  one of its guests whose name isn't known yet; fill in the name later.
- Right-click a seat to seat someone from the guest list there, to clear the
  seat (the guest goes back to the list) or to remove the guest altogether.
- The **Guests** tab lists the parties and every guest. Rename guests, change
  a guest's gender or party, and change a party's colour (Colour…, or
  double-click its swatch). Tick *Not seated* to see who is invited but
  still needs a seat; double-click a guest's seat to find it on the plan.

### Parties

- **New…** makes a party: its **lead guest** (the one to contact), the number
  of **places**, and optionally a party name (otherwise it goes by the lead's
  name). The other places start as *Ann Smith+1*, *Ann Smith+2* … — the plus
  one is built in. Type a real name over a place to name that guest. Places
  not named yet can still be seated.
- **Edit…** (or double-click the party) changes the same things and the
  party's guests, with each one's role. Places can be added, or taken away (those not seated go
  first, the highest numbered first), but never below the number of named
  guests. The Places and Named columns show how many of each.
- The lead's **address, phone, mobile, WhatsApp and email** can be typed in,
  or copied from an address book with **Find in a contacts file…**:
  export your contacts to a file and the planner searches it. From Google
  Contacts (contacts.google.com → Export) choose *vCard* or *Google CSV*;
  Apple Contacts, Thunderbird and Outlook export vCard (.vcf) too, and
  Outlook's CSV is read as well. The details are copied once and not kept
  in step; the dialog says which file they came from. The planner never
  connects to Google or anywhere else: it only reads the file, and
  remembers which one, so the next search goes straight to it (*Other
  file…* picks another). Export again when your contacts change. (*Same
  as mobile* fills in WhatsApp.)
- The lead can't be removed or moved to another party. Removing a party
  removes its places not named yet; its named guests stay, in no party.
- On an empty seat in the Table tab, choosing a party seats one of its places
  not seated yet (a new place if they are all seated).
- A seat with a guest in it can't be disabled. If a table's places are changed,
  or a table is deleted, its guests go back to the guest list.

### Layout

- **Layout ▾** → Round table / Rectangular table / Pew puts a new one in
  view. A **pew** is a straight row of seats all facing forward (up the plan
  until turned), with its backrest behind; use pews for a church, or for
  rows of seats round the walls at a party. It turns, moves and duplicates
  like a table. Pews are numbered on their own ("Pew 1", "Pew 2" …), across
  each row and then the next row back; their seats are numbered from seat 1
  towards the far end, so make the seat by the aisle seat 1.
- **Layout ▾ → Altar / speakers' table** puts down a table with no
  chairs, there to show which way the room faces. Its length is given in
  seats along its front (in Edit), so it lines up with the pews or tables
  around it. It takes no number, plays no part in seating, places or checks,
  and is called "Altar" unless you give it a name (such as "Speakers'
  table"). It shows on the table plan PDF too.
- **Reserved and open seats.** Every seat is *reserved* (it can be given to
  a guest), *open access* (anyone may sit there; no one is given it) or
  disabled. Table chairs start reserved and pew seats open. Right-click a
  seat to reserve it or make it open, or right-click a pew or table to
  *Reserve all seats* or *Make all seats open access*. Open seats are drawn
  white, reserved seats filled. A seat with a guest in it can't be made
  open. Only reserved seats can be given to guests, at any type of event; on
  the table plan PDF each pew or table also gives its number of open seats.
- **Table ▾** acts on the selected table: Edit…, Duplicate, Turn left / Turn
  right (15°), Enable all seats and Delete. Right-clicking a table gives the
  same menu.
- **Duplicate** (Ctrl+D) makes a copy of the table a little below and to the
  right: the same shape, places, turn, seat 1 and disabled chairs, with the
  next number, but not the original's own name, head-table status or guests.
  From then on the two are separate.
  It also has Renumber tables… (see below).
- Drag a table to move it. Turn it about its centre by dragging the round
  handle above the selected table (15° steps; hold Shift for any angle), with
  Table ▾ → Turn left / Turn right, or by typing the angle in Edit.
- Double-click a table (or Edit) to change its places, its turn or its own
  name, or to make it the head table. There is only one head table.
- **What a table is called.** Each table has a number, "Table 3". A new
  table takes the next number up, and numbers stay put when tables are moved.
  When the room is set out, **Table ▾ → Renumber tables…** numbers them afresh
  by where they stand: in rows from the top of the plan down, left to right
  along each row. The head table is called "Head table". Give any table its
  own name (for example the family sitting there) and that name is used
  instead. When renumbering, the head table and named tables take no number,
  so the numbers run on unbroken. Clear a name and the table takes the next
  number up. Behind this every table keeps the id it was given when made, and
  guests' seats refer to that, so renumbering or renaming a table never loses
  anyone's seat. When a rectangular
  table becomes the head table, the long side across from seat 1 is disabled
  so everyone faces the room; any chairs at the ends stay. You can enable
  those chairs again by right-clicking them.
- Right-click a chair to **make it seat 1** (drawn green; the seats are then
  numbered clockwise from it) or to **disable** it (greyed out, for a chair
  no one is to sit in). Seat 1 cannot be disabled. Disabled chairs keep their
  numbers and are left out of the place counts. Changing a table's places puts
  seat 1 back at the start and enables every chair again.
- With a table selected: arrow keys nudge it, R / Shift+R turn it, Delete removes it.
- Ctrl + mouse wheel, or the + and − buttons in the corner of the plan, zooms.

### The occasion's emblem

**Occasion ▾ → Occasion details…** has an *Emblem*: an SVG picture that heads
the table plan, the letters and the reports (catering, kitchen, service),
drawn as lines and shapes so it prints sharp at any size. *Import SVG…* takes
any SVG file (from Inkscape, say); *Hearts* (two entwined hearts with an
initial in each, I and L) and *Bells* are samples to start from. It is kept
as `emblem.svg` in the occasion's folder: open it in Inkscape or a text
editor to change the initials or the colours. Emails and WhatsApp messages
are plain text, so they cannot carry it.

### Messages to the guests

Messages go to each party's lead, from the details in **Occasion ▾ →
Occasion details…**: *From* signs them ("Lydia and Isaac"), *Reply to* says
how to answer, and the *phone country code* (44) turns 07700 900123 into an
international number for WhatsApp.

- **Messages ▾ → Message templates…**: each message has a name, a subject
  and its text, with fill-ins in braces completed for each party:
  `{lead}` `{leadfirst}` `{party}` `{guests}` ("Ann Smith, Bob Smith and a
  guest") `{places}` `{occasion}` `{date}` `{events}` (a line for each event
  the party is invited to) `{event}` `{eventplace}` `{eventwhen}` `{seats}`
  (where each of the party sits at the event chosen) `{menu}` `{missing}`
  (names of +1s, dietary needs and menu choices still needed, or thanks if
  nothing is) `{menuschosen}` (thanks for the choices made, naming the
  events) `{menustochoose}` (every catered event the party is invited to
  with choices still to make: each guest with what they have chosen so far,
  to confirm, and what is left, then the menu) `{from}` `{reply}`. A new
  occasion comes with *Invitation*, *Menu choices* (one message covering
  every event, whenever the party was invited to it), *Request for details*,
  *Reminder* and *Table and arrangements*; change them, copy them or add
  more (*Add the standard ones missing* brings back any you lack).
- **Honorifics**: `{leadfirst}` greets a titled lead by title and name:
  "Dear Count Paris", "Dear Friar Laurence", not "Dear Count"; some titles
  take the surname instead ("Dr John Smith" → "Dear Dr Smith"). The titles
  are listed in `honorifics.yml`, a short YAML file of two lists,
  `with-name:` and `with-surname:`. To change them, put your own
  `honorifics.yml` in the occasion's folder, or in MyReceptions to share it;
  otherwise the standard list is used (it is in the source, under
  `resources/org/lerot/myreceptionplanner/model/`). It is read when the
  occasion opens.
- **Messages ▾ → Write to guests…**: choose the event its event fill-ins
  speak of, the message, and which parties to show (all, invited to the event,
  with details missing, not yet sent this message, with menu choices to
  make, or with invitations to send). Each party's message
  is shown completed, with ticks for whether the lead has an email, a
  WhatsApp or mobile number and an address, and when this message last went
  to them. For the ticked parties (or the one chosen):
  - **List…** makes a list to contact them by hand: a line for each party
    with every way to reach the lead and a box to tick, then each party's
    message in full. Note them as sent (with or without saving the list as
    a PDF) and they count as sent "by list", just as an email would: the
    *Invitation* turns their rings into dots. Use it when contacting guests
    in person or by phone, or to try out the whole process without sending
    anything.
  - **Email…** opens a ready-written email for each in your mail program;
  - **WhatsApp…** opens WhatsApp (app or web) with the message for each;
  - **Letters (PDF)…** makes a letter for each, dated, from you and addressed
    to the lead;
  - **Address labels (PDF)…** makes labels, 21 to an A4 sheet (L7160).

  You check and send each email or WhatsApp yourself; each is noted against
  the party as last sent, with how.

### Reports

*Check…* and the table plan are on **Event ▾**, and the catering reports
on **Catering ▾** (above):

- **Table plan (PDF)…**: one page to print and put up, with a panel for each
  table where it stands in the room, its number or name at the top and its
  guests in seat order (empty seats left out; places not named yet, such as
  *Ann Smith+1*, in grey italics). The panels follow the same rows as
  Renumber tables, so after renumbering they read in number order. It is
  headed with the occasion and its date, then the event with its place and
  time. Choose the paper (A4 to A1) and the orientation; the type is made as
  large as fits. It is saved as `table-plan-<event>.pdf` in the occasion's
  folder's Output unless you choose otherwise, and opened.
- **Check…** lists what is still to settle at the event shown (for an
  unreserved party: who is invited, party by party, against its capacity):

  - **Spare places**: each table with seats free, how many, and out of how
    many usable seats (disabled seats don't count). The head table comes
    first, then the numbered tables, then those with names of their own.
  - **Guests not seated**: each party with guests invited but still to
    seat, how many, and out of how many invited; guests in no party are
    listed together.

### Files

`~/Documents/MyReceptions` holds the occasions, each in a folder of its own
named from the occasion ("Lydia and Isaac's Wedding" is
`lydia-and-isaacs-wedding`), made when it is first saved:

- `emblem.svg`: the occasion's emblem, if it has one
- `occasion.xml`: the occasion's name and date, who messages are from and
  how to reply, its list of events, and the messages sent (under
  `<communications>`)
- `guests.xml`: the guest list: the parties, their leads and contact
  details, their guests with their roles and dietary needs, and pairs of guests with
  names alike who are different people
- `email_templates.xml`: the occasion's message templates. These can be
  shared: in *Message templates…*, **Make these the default** saves them as
  `MyReceptions/email_templates.xml`, which new occasions start with (and
  an occasion without templates of its own uses); **Take the default ones**
  brings that set into the occasion
- one file for each event, named after it when it was made (such as
  `wedding-breakfast.xml`): its name, type, place, date and time, its menu
  and each guest's choices and note, who is invited, its tables and pews (shapes, positions, turns, numbers, seat 1,
  disabled and open seats) and who sits where
- `Output/`: everything printed or written from it (table plans, catering,
  kitchen and service reports, letters, labels, contact lists). The PDF
  chooser opens here with a name filled in.

**Save…** shows the folder it will save in and asks before writing. Choose
another folder there to save a copy somewhere else; from then on the occasion
saves to that folder. A new occasion is saved the first time in its own
folder in MyReceptions (with a warning if that folder already holds an
occasion); renamed later, it keeps its folder. *Open occasion…* starts in
MyReceptions. When closing or opening another occasion with unsaved changes, you're
asked whether to save them.

A rectangular table has *N* places along each long side and *M* at each end.
