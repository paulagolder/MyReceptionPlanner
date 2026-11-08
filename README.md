# MyReceptionPlanner

Plan the tables at a reception: lay out the tables, seat the guests, and print
the table plan, a list for each table and a place card for each guest.

## Commands (from this folder)

    ./gradlew run     start the app
    ./gradlew test    run the automated tests

## Using it

- **Add table ▾** → Round table / Rectangular table puts a new table in view.
- **Table ▾** acts on the selected table: Edit…, Turn left / Turn right (15°),
  Enable all seats and Delete. Right-clicking a table gives the same menu.
- Drag a table to move it. Turn it about its centre by dragging the round
  handle above the selected table (15° steps; hold Shift for any angle), with
  Table ▾ → Turn left / Turn right, or by typing the angle in Edit.
- Double-click a table (or Edit) to change its name, its places, its turn, or to
  make it the head table. There is only one head table. When a rectangular
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

**Save…** shows where the plan will be saved and asks before writing it. Choose
another folder there to save somewhere else, for example a copy under a new
name; from then on the plan saves to that folder. A new plan asks for a folder
first. When closing or opening another event with unsaved changes, you're asked
whether to save them to the current folder.

Each reception is a folder (by default under `~/Documents/MyReceptions`)
holding its files:

- `tables.xml`: the tables, their shapes, positions and turns, seat 1 and
  any disabled chairs
- `guests.xml`: the parties, their guests and each guest's seat (to come)

A rectangular table has *N* places along each long side and *M* at each end.
