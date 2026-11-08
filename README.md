# MyReceptionPlanner

Plan the tables at a reception: lay out the tables, seat the guests, and print
the table plan, a list for each table and a place card for each guest.

## Commands (from this folder)

    ./gradlew run     start the app
    ./gradlew test    run the automated tests

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

On the right of the toolbar:

- **Event** chooses which event's tables and seating are shown.
- **Occasion ▾** → *Occasion details…* (name and date), *Invitations…*
  (below), *Event details…*
  (name, place, date and time; leave the date empty if it is on the
  occasion's day), *New event…* (starting with an empty room or a copy of
  another event's tables, and inviting everyone, no one or the guests of
  another event), *Delete this event…*, *New occasion…* and
  *Open occasion…*.
- **Save…** saves the whole occasion.

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
seat, you can instead seat the guest already on the list. **Reports ▾ →
Names alike…** lists every pair alike across the occasion; mark a pair *Not
the same person* to leave it out from now on, or rename or remove a real
duplicate in the Guests tab.

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
  limit): the status bar, *Invitations…* and *Check…* warn when more guests
  are invited than it allows. Its table plan PDF shows each table with its
  number of places instead of names. Making a seated event unreserved clears
  its seats, after asking.

New events invite everyone to start with (or no one, or another event's
guests).

### Invitations

Each event has its own list of who is invited, and only those invited can
be seated there. **Occasion ▾ → Invitations…** shows every party with its
guests down the side and a column of ticks for each event: click a guest's
tick to invite or uninvite them, or a party's to do the whole party (it is
half ticked when only some are invited). Whenever anyone of a party is
invited, so is its lead; uninviting the lead uninvites the party. Nothing
changes until OK, which says first if anyone would lose a seat.

A new party is invited to every event. A new place in a party goes wherever
the party is already invited. In the Guests tab, the *Invited* column ticks
who is invited to the event shown (and can change it), the party list shows
how many of each party are invited there, and *Not seated* lists those
invited but not yet seated.

The plan works in two modes, chosen at the left of the toolbar:

- **Layout**: set out the tables (below).
- **Seating**: seat the guests. The tables stay put; click a table, or one of
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
  party's guests. Places can be added, or taken away (those not seated go
  first, the highest numbered first), but never below the number of named
  guests. The Places and Named columns show how many of each.
- The lead's **address, phone, mobile, WhatsApp and email** can be typed in,
  or copied from MyContacts with **Find in MyContacts…**. The details are
  copied once and not kept in step: MyContacts is only read, never changed.
  Someone with no address or home phone of their own (Tim Perks, say) takes
  them from the household or group contact they belong to in MyContacts
  (Tim & Claire Perks); the dialog says where they came from. Mobile and
  email are only ever their own. (MyContacts has no WhatsApp field; *Same as
  mobile* fills it in.)
- The lead can't be removed or moved to another party. Removing a party
  removes its places not named yet; its named guests stay, in no party.
- On an empty seat in the Table tab, choosing a party seats one of its places
  not seated yet (a new place if they are all seated).
- A seat with a guest in it can't be disabled. If a table's places are changed,
  or a table is deleted, its guests go back to the guest list.

### Layout

- **Add table ▾** → Round table / Rectangular table / Pew puts a new one in
  view. A **pew** is a straight row of seats all facing forward (up the plan
  until turned), with its backrest behind; use pews for a church, or for
  rows of seats round the walls at a party. It turns, moves and duplicates
  like a table. Pews are numbered on their own ("Pew 1", "Pew 2" …), across
  each row and then the next row back; their seats are numbered from seat 1
  towards the far end, so make the seat by the aisle seat 1.
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
- Ctrl + mouse wheel, or Zoom in / Zoom out, zooms.

### Reports

**Reports ▾** on the toolbar (in either mode):

- **Table plan (PDF)…**: one page to print and put up, with a panel for each
  table where it stands in the room, its number or name at the top and its
  guests in seat order (empty seats left out; places not named yet, such as
  *Ann Smith+1*, in grey italics). The panels follow the same rows as
  Renumber tables, so after renumbering they read in number order. It is
  headed with the occasion and its date, then the event with its place and
  time. Choose the paper (A4 to A1) and the orientation; the type is made as
  large as fits. It is saved as `table-plan-<event>.pdf` in the occasion's
  folder unless you choose otherwise, and opened.
- **Check…** lists what is still to settle at the event shown (for an
  unreserved party: who is invited, party by party, against its capacity):

  - **Spare places**: each table with seats free, how many, and out of how
    many usable seats (disabled seats don't count). The head table comes
    first, then the numbered tables, then those with names of their own.
  - **Guests not seated**: each party with guests invited but still to
    seat, how many, and out of how many invited; guests in no party are
    listed together.

### Files

Each occasion is a folder (by default under `~/Documents/MyReceptions`):

- `occasion.xml`: the occasion's name and date, its list of events, and the
  guest list: the parties, their leads and contact details, their guests,
  and pairs of guests with names alike who are different people
- one file for each event, named after it when it was made (such as
  `wedding-breakfast.xml`): its name, type, place, date and time, who is
  invited, its tables and pews (shapes, positions, turns, numbers, seat 1,
  disabled and open seats) and who sits where

**Save…** shows the folder it will save in and asks before writing. Choose
another folder there to save a copy somewhere else; from then on the occasion
saves to that folder. A new occasion asks for a folder the first time it is
saved. When closing or opening another occasion with unsaved changes, you're
asked whether to save them.

A rectangular table has *N* places along each long side and *M* at each end.
