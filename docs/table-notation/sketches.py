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


# ---- joined tables: built of seat-unit squares ----
#
# Each run is a strip of squares to the right of its outside edge, walked
# clockwise; each branch a strip off a run's inside edge. The table's
# outline is then walked clockwise (holes after), a seat on each unit of
# seated edge, and where two seated edges meet at an inside corner the
# later seat (counting clockwise) is left out: the two would collide.

SIDE = {(0, -1): "N", (1, 0): "E", (0, 1): "S", (-1, 0): "W"}


def turned(d, degrees):
    for _ in range((degrees // 90) % 4):
        d = (-d[1], d[0])                          # a right turn, y down the page
    return d


def joined(runs, branches=(), closed=False):
    """runs: (length, seated[, turn]) with seated from outside, inside, start,
    end; branches: (run, at, length, seated) off that run's inside edge, at
    seat units from its start, seated from left, right, end (walking out)."""
    cells, labels, starts = set(), {}, []

    def label(cell, d, seated, priority):
        key = (cell, SIDE[d])
        if key not in labels or labels[key][0] < priority:
            labels[key] = (priority, seated)

    def square(origin, along, across, k, j):
        xs = [origin[0] + along[0] * (k + a) + across[0] * (j + b) for a in (0, 1) for b in (0, 1)]
        ys = [origin[1] + along[1] * (k + a) + across[1] * (j + b) for a in (0, 1) for b in (0, 1)]
        return (min(xs), min(ys))

    d, at = (0, -1), (0, 0)
    for i, run in enumerate(runs):
        length, seated = run[0], run[1]
        # the first run goes up the page unless turned; each after turns right
        d = turned(d, run[2] if len(run) > 2 else (90 if i > 0 else 0))
        n = (-d[1], d[0])
        starts.append((at, d, n))
        for k in range(length):
            for j in range(DEPTH):
                c = square(at, d, n, k, j)
                cells.add(c)
                if j == 0: label(c, (-n[0], -n[1]), "outside" in seated, 2)
                if j == DEPTH - 1: label(c, n, "inside" in seated, 2)
                if k == 0: label(c, (-d[0], -d[1]), "start" in seated, 1)
                if k == length - 1: label(c, d, "end" in seated, 1)
        at = (at[0] + d[0] * length, at[1] + d[1] * length)

    for run, offset, length, seated in branches:
        p, d, n = starts[run]
        q = (p[0] + d[0] * offset + n[0] * DEPTH, p[1] + d[1] * offset + n[1] * DEPTH)
        left = (n[1], -n[0])                        # walking out along n
        for k in range(length):
            for j in range(DEPTH):
                c = square(q, n, d, k, j)
                cells.add(c)
                if j == 0: label(c, (-d[0], -d[1]), ("left" if left == (-d[0], -d[1]) else "right") in seated, 2)
                if j == DEPTH - 1: label(c, d, ("left" if left == d else "right") in seated, 2)
                if k == length - 1: label(c, n, "end" in seated, 1)

    # the outline's unit edges, the table on the right of each
    edges = {}
    for (cx, cy) in cells:
        for d, a, b in (((0, -1), (cx, cy), (cx + 1, cy)), ((1, 0), (cx + 1, cy), (cx + 1, cy + 1)),
                        ((0, 1), (cx + 1, cy + 1), (cx, cy + 1)), ((-1, 0), (cx, cy + 1), (cx, cy))):
            if (cx + d[0], cy + d[1]) not in cells:
                edges[a] = (a, b, labels.get(((cx, cy), SIDE[d]), (0, False))[1])
    p0, d0, n0 = starts[0]
    first = square(p0, d0, n0, 0, 0)
    first_edge = {(0, -1): (first[0], first[1]), (1, 0): (first[0] + 1, first[1]),
                  (0, 1): (first[0] + 1, first[1] + 1), (-1, 0): (first[0], first[1] + 1)}[(-n0[0], -n0[1])]
    loops = []
    while edges:
        a = first_edge if first_edge in edges else min(edges, key=lambda v: (v[1], v[0]))
        loop = []
        while a in edges:
            e = edges.pop(a)
            loop.append(e)
            a = e[1]
        loops.append(loop)

    seats, outlines = [], []
    for loop in loops:
        heading = [(e[1][0] - e[0][0], e[1][1] - e[0][1]) for e in loop]
        for i, (a, b, seated) in enumerate(loop):
            h, before = heading[i], heading[i - 1]
            inside_corner = before[0] * h[1] - before[1] * h[0] < 0
            if not seated or (inside_corner and loop[i - 1][2]):
                continue
            mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            seats.append((mx + h[1] * OFF, my - h[0] * OFF))
        outlines.append([e[0] for i, e in enumerate(loop) if heading[i] != heading[i - 1]])
    return seats, outlines


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
        "hollow-square-inside": svg("hollow-square-inside", "hollow square 8 + 8 + 8 + 8, inside too",
                                    *joined([(8, {"outside", "inside"})] * 4, closed=True)),
        "t": svg("t", "T: top 11, a stem of 8 from its middle",
                 *joined([(11, {"outside"}, 90)], [(0, 5, 8, {"left", "right"})])),
        "e": svg("e", "E: top 13, three prongs of 8",
                 *joined([(13, {"outside"}, 90)], [(0, 0, 8, {"left", "right"}), (0, 6, 8, {"left", "right"}),
                                                      (0, 12, 8, {"left", "right"})])),
        "e-inside": svg("e-inside", "E with the top's inside seated too",
                        *joined([(13, {"outside", "inside"}, 90)], [(0, 0, 8, {"left", "right"}),
                                                                   (0, 6, 8, {"left", "right"}),
                                                                   (0, 12, 8, {"left", "right"})])),
    }
    for k, v in counts.items():
        print(f"{k:18} {v}")
