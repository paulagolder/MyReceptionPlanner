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
- **Occasion ▾** → *Occasion details…* (name and date), *Event details…*
  (name, place, date and time; leave the date empty if it is on the
  occasion's day), *New event…* (starting with an empty room or a copy of
  another event's tables), *Delete this event…*, *New occasion…* and
  *Open occasion…*.
- **Save…** saves the whole occasion.

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
  double-click its swatch). Tick *Unseated only* to see who still needs a
  seat; double-click a guest's seat to find it on the plan.

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

- **Add table ▾** → Round table / Rectangular table puts a new table in view.
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
- **Check…** lists what is still to settle at the event shown:

  - **Spare places**: each table with seats free, how many, and out of how
    many usable seats (disabled seats don't count). The head table comes
    first, then the numbered tables, then those with names of their own.
  - **Guests not seated**: each party with guests still to seat, how many,
    and out of its places; guests in no party are listed together.

### Files

Each occasion is a folder (by default under `~/Documents/MyReceptions`):

- `occasion.xml`: the occasion's name and date, its list of events, and the
  guest list: the parties, their leads and contact details, and their guests
- one file for each event, named after it when it was made (such as
  `wedding-breakfast.xml`): its name, place, date and time, its tables
  (shapes, positions, turns, numbers, seat 1 and disabled chairs) and who
  sits where

**Save…** shows the folder it will save in and asks before writing. Choose
another folder there to save a copy somewhere else; from then on the occasion
saves to that folder. A new occasion asks for a folder the first time it is
saved. When closing or opening another occasion with unsaved changes, you're
asked whether to save them.

A rectangular table has *N* places along each long side and *M* at each end.
