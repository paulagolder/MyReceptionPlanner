#!/usr/bin/env python3
"""
Draws the sketches for docs/table-notation.md, working out each table's
seats by the rules there (sizes in seat units; the seats an edge holds
rounded down). Run from anywhere:  python3 docs/table-notation/sketches.py
"""
import math
import os

OUT = os.path.dirname(os.path.abspath(__file__))
PX = 32           # pixels to a seat unit (60 cm)
OFF = 0.45        # a seat's centre outside the table's edge, in units
R = 0.32          # a seat's radius, in units
DEPTH = 1         # a joined table's depth, in units


# ---- the simple shapes: seat positions, clockwise, and the outline ----

def rectangle(x, y, seated=("far", "right", "near", "left")):
    seats = []
    if "far" in seated:
        seats += [(-x / 2 + i + 0.5, -y / 2 - OFF) for i in range(x)]
    if "right" in seated:
        seats += [(x / 2 + OFF, -y / 2 + i + 0.5) for i in range(y)]
    if "near" in seated:
        seats += [(x / 2 - i - 0.5, y / 2 + OFF) for i in range(x)]
    if "left" in seated:
        seats += [(-x / 2 - OFF, y / 2 - i - 0.5) for i in range(y)]
    outline = [(-x / 2, -y / 2), (x / 2, -y / 2), (x / 2, y / 2), (-x / 2, y / 2)]
    return seats, [outline]


def ellipse(x, y):
    a, b = x / 2, y / 2
    # the edge finely, from the top, clockwise, and how far along it each point is
    n = 4000
    pts = [(a * math.sin(2 * math.pi * k / n), -b * math.cos(2 * math.pi * k / n)) for k in range(n + 1)]
    along = [0.0]
    for p, q in zip(pts, pts[1:]):
        along.append(along[-1] + math.dist(p, q))
    perimeter = along[-1]
    count = math.floor(perimeter + 1e-9)
    seats, k = [], 0
    for s in range(count):
        want = s * perimeter / count
        while along[k + 1] < want:
            k += 1
        t = 2 * math.pi * k / n
        px, py = a * math.sin(t), -b * math.cos(t)
        nx, ny = b * math.sin(t), -a * math.cos(t)   # the outward normal
        ln = math.hypot(nx, ny)
        seats.append((px + OFF * nx / ln, py + OFF * ny / ln))
    return seats, [pts[:-1]]


def stadium(x, y):
    r, straight = y / 2, x - y
    seats = [(-straight / 2 + i + 0.5, -r - OFF) for i in range(straight)]
    ends = math.floor(math.pi * r + 1e-9)
    for i in range(ends):                        # the right end, top to bottom
        t = -math.pi / 2 + math.pi * (i + 0.5) / ends
        seats.append((straight / 2 + (r + OFF) * math.cos(t), (r + OFF) * math.sin(t)))
    seats += [(straight / 2 - i - 0.5, r + OFF) for i in range(straight)]
    for i in range(ends):                        # the left end, bottom to top
        t = math.pi / 2 + math.pi * (i + 0.5) / ends
        seats.append((-straight / 2 + (r + OFF) * math.cos(t), (r + OFF) * math.sin(t)))
    outline = []
    for i in range(41):
        t = -math.pi / 2 + math.pi * i / 40
        outline.append((straight / 2 + r * math.cos(t), r * math.sin(t)))
    for i in range(41):
        t = math.pi / 2 + math.pi * i / 40
        outline.append((-straight / 2 + r * math.cos(t), r * math.sin(t)))
    return seats, [outline]


def polygon(sides, perside):
    # side 1 flat along the top; corners clockwise from its left end
    radius = perside / (2 * math.sin(math.pi / sides))
    start = -math.pi / 2 - math.pi / sides
    corners = [(radius * math.cos(start + 2 * math.pi * k / sides),
                radius * math.sin(start + 2 * math.pi * k / sides)) for k in range(sides)]
    seats = []
    for k in range(sides):
        (ax, ay), (bx, by) = corners[k], corners[(k + 1) % sides]
        dx, dy = (bx - ax) / perside, (by - ay) / perside
        nx, ny = dy, -dx                         # outward, going clockwise
        for i in range(perside):
            seats.append((ax + dx * (i + 0.5) + OFF * nx, ay + dy * (i + 0.5) + OFF * ny))
    return seats, [corners]


# ---- joined tables: runs walked round the outside edge, turning right ----

def joined(runs, closed=False):
    """runs: (length, seated) with seated a subset of {"outside", "inside"};
    each run turns 90 degrees right into the next."""
    heading, at = (0.0, -1.0), (0.0, 0.0)        # starting up the page
    outside = [at]
    for length, _ in runs:
        at = (at[0] + heading[0] * length, at[1] + heading[1] * length)
        outside.append(at)
        heading = (-heading[1], heading[0])      # right turn (y down the page)
    if closed:
        outside = outside[:-1]
    m = len(runs)
    corners_before = [(closed or i > 0) for i in range(m)]
    corners_after = [(closed or i < m - 1) for i in range(m)]

    def inward(i):                               # to the right of run i
        (ax, ay), (bx, by) = outside[i], outside[(i + 1) % len(outside)]
        ln = math.dist((ax, ay), (bx, by))
        return (ax, ay), ((bx - ax) / ln, (by - ay) / ln), (-(by - ay) / ln, (bx - ax) / ln)

    out_seats, in_seats = [], []
    inner = []
    for i, (length, seated) in enumerate(runs):
        a, d, n = inward(i)
        lose_before = DEPTH if corners_before[i] else 0
        lose_after = DEPTH if corners_after[i] else 0
        inner.append((a[0] + d[0] * lose_before + n[0] * DEPTH, a[1] + d[1] * lose_before + n[1] * DEPTH))
        if not closed and i == m - 1:
            e = (a[0] + d[0] * length + n[0] * DEPTH, a[1] + d[1] * length + n[1] * DEPTH)
            inner.append(e)
        if "outside" in seated:
            out_seats += [(a[0] + d[0] * (k + 0.5) - n[0] * OFF, a[1] + d[1] * (k + 0.5) - n[1] * OFF)
                          for k in range(math.floor(length))]
        if "inside" in seated:
            free = length - lose_before - lose_after
            run = [(a[0] + d[0] * (lose_before + k + 0.5) + n[0] * (DEPTH + OFF),
                    a[1] + d[1] * (lose_before + k + 0.5) + n[1] * (DEPTH + OFF))
                   for k in range(math.floor(free))]
            in_seats.append(run)
    # the inside seats counted on round, back along the runs
    seats = out_seats + [s for run in reversed(in_seats) for s in reversed(run)]
    if not closed:
        inner[0] = (outside[0][0] + inward(0)[2][0] * DEPTH, outside[0][1] + inward(0)[2][1] * DEPTH)
        return seats, [outside + list(reversed(inner))]
    return seats, [outside, inner]


# ---- drawing ----

def svg(name, title, seats, outlines, box=None):
    pts = [p for o in outlines for p in o] + seats
    minx = min(p[0] for p in pts) - 1.2
    maxx = max(p[0] for p in pts) + 1.2
    miny = min(p[1] for p in pts) - 1.2
    maxy = max(p[1] for p in pts) + 1.0
    w, h = (maxx - minx) * PX, (maxy - miny) * PX + 34
    X = lambda v: (v - minx) * PX
    Y = lambda v: (v - miny) * PX
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w:.0f}" height="{h:.0f}" '
         f'viewBox="0 0 {w:.0f} {h:.0f}" font-family="sans-serif">',
         f'<rect width="100%" height="100%" fill="#ffffff"/>']
    if box:
        bx, by = box
        o.append(f'<rect x="{X(-bx / 2):.1f}" y="{Y(-by / 2):.1f}" width="{bx * PX}" height="{by * PX}" '
                 f'fill="none" stroke="#9aa5b1" stroke-dasharray="4 3"/>')
    path = " ".join("M " + " L ".join(f"{X(x):.1f} {Y(y):.1f}" for x, y in ol) + " Z" for ol in outlines)
    o.append(f'<path d="{path}" fill="#e8dcc4" stroke="#7a5c2e" stroke-width="1.5" fill-rule="evenodd"/>')
    for i, (x, y) in enumerate(seats, 1):
        o.append(f'<circle cx="{X(x):.1f}" cy="{Y(y):.1f}" r="{R * PX:.1f}" fill="#ffffff" stroke="#2f6fdb" stroke-width="1.5"/>')
        o.append(f'<text x="{X(x):.1f}" y="{Y(y) + 3.5:.1f}" font-size="10" text-anchor="middle" fill="#1d3557">{i}</text>')
    o.append(f'<text x="{w / 2:.0f}" y="{h - 12:.0f}" font-size="13" text-anchor="middle" fill="#222">'
             f'{title}: {len(seats)} seats</text>')
    o.append('</svg>')
    with open(os.path.join(OUT, name + ".svg"), "w") as f:
        f.write("\n".join(o) + "\n")
    return len(seats)


if __name__ == "__main__":
    counts = {
        "rectangle": svg("rectangle", "rectangle 6 × 2", *rectangle(6, 2), box=None),
        "top-table": svg("top-table", "rectangle 10 × 1, seated far", *rectangle(10, 1, ("far",))),
        "banqueting": svg("banqueting", "rectangle 14 × 1, seated sides", *rectangle(14, 1, ("far", "near"))),
        "round": svg("round", "round 4 × 4", *ellipse(4, 4), box=(4, 4)),
        "ellipse": svg("ellipse", "ellipse 8 × 4", *ellipse(8, 4), box=(8, 4)),
        "stadium": svg("stadium", "stadium 8 × 4", *stadium(8, 4), box=(8, 4)),
        "hexagon": svg("hexagon", "polygon, 6 sides × 2", *polygon(6, 2)),
        "heptagon": svg("heptagon", "polygon, 7 sides × 1", *polygon(7, 1)),
        "horseshoe": svg("horseshoe", "horseshoe 8 + 10 + 8, inside seats on the arms",
                         *joined([(8, {"outside", "inside"}), (10, {"outside"}), (8, {"outside", "inside"})])),
        "horseshoe-outside": svg("horseshoe-outside", "horseshoe 8 + 10 + 8, outside only",
                                 *joined([(8, {"outside"}), (10, {"outside"}), (8, {"outside"})])),
        "hollow-square": svg("hollow-square", "hollow square 8 + 8 + 8 + 8, outside only",
                             *joined([(8, {"outside"})] * 4, closed=True)),
    }
    for k, v in counts.items():
        print(f"{k:18} {v}")
