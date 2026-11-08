# MyReceptionPlanner

Plan the tables at a reception: lay out the tables, seat the guests, and print
the table plan, a list for each table and a place card for each guest.

## Commands (from this folder)

    ./gradlew run     start the app
    ./gradlew test    run the automated tests

## Using it

The app works in two modes, chosen at the left of the toolbar:

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
- **Table ▾** acts on the selected table: Edit…, Turn left / Turn right (15°),
  Enable all seats and Delete. Right-clicking a table gives the same menu.
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

**Save…** saves both files. It shows the folder they'll go in and asks before writing. Choose
another folder there to save somewhere else, for example a copy under a new
name; from then on the plan saves to that folder. A new plan asks for a folder
first. When closing or opening another event with unsaved changes, you're asked
whether to save them to the current folder.

Each reception is a folder (by default under `~/Documents/MyReceptions`)
holding its files:

- `tables.xml`: the tables, their shapes, positions and turns, seat 1 and
  any disabled chairs
- `guests.xml`: the parties, their leads and contact details, their guests
  and each guest's seat

A rectangular table has *N* places along each long side and *M* at each end.
