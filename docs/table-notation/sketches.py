#!/usr/bin/env python3
"""
Draws the sketches for docs/table-notation.md. Every table is turned into a
walk round its edge (steps, turns and arcs, and a walk round each hole),
and drawn and counted from that walk alone: the shapes and the composed
tables by the rules in the notes, as a test that they come out right.
Sizes are in seat units (60 cm). Run from anywhere:

    python3 docs/table-notation/sketches.py
"""
import math
import os
from collections import Counter

OUT = os.path.dirname(os.path.abspath(__file__))
PX = 32           # pixels to a seat unit
OFF = 0.45        # a seat's centre beyond the table's edge, in units
R = 0.32          # a seat's radius, in units


# ---- the walk ----
#
# A walk starts at its own (0, 0) heading right, and goes clockwise: the
# table on the right, the seats on the left. A hole's walk starts at its
# "at" point heading right and also goes clockwise, so there the table is
# on the left and the seats on the right, facing into the hole.
#   ("step", length, seats)     straight on; seats spread evenly along it
#   ("turn", degrees)           on the spot, clockwise (negative: anticlockwise)
#   ("arc", degrees, seats, radius)   bending clockwise as it goes

def rotate(h, degrees):
    t = math.radians(degrees)
    return (h[0] * math.cos(t) - h[1] * math.sin(t), h[0] * math.sin(t) + h[1] * math.cos(t))


def trace(start, steps, hole):
    side = -1 if hole else 1                      # +1: seats on the left of the way
    p, h = start, (1.0, 0.0)
    outline, seats = [p], []
    for s in steps:
        if s[0] == "turn":
            h = rotate(h, s[1])
        elif s[0] == "step":
            _, length, n = s
            left = (h[1], -h[0])
            for k in range(n):
                f = (k + 0.5) * length / n
                seats.append((p[0] + h[0] * f + side * left[0] * OFF, p[1] + h[1] * f + side * left[1] * OFF))
            p = (p[0] + h[0] * length, p[1] + h[1] * length)
            outline.append(p)
        elif s[0] == "arc":
            _, degrees, n, radius = s
            right = (-h[1], h[0])
            c = (p[0] + right[0] * radius, p[1] + right[1] * radius)
            a0 = math.atan2(p[1] - c[1], p[0] - c[0])
            sweep = math.radians(degrees)
            for k in range(n):
                a = a0 + sweep * (k + 0.5) / n
                rr = radius + side * OFF
                seats.append((c[0] + rr * math.cos(a), c[1] + rr * math.sin(a)))
            parts = max(8, int(abs(degrees) / 4))
            for k in range(1, parts + 1):
                a = a0 + sweep * k / parts
                outline.append((c[0] + radius * math.cos(a), c[1] + radius * math.sin(a)))
            p = outline[-1]
            h = rotate(h, degrees)
    if math.dist(p, start) > 1e-6 or math.dist(h, (1.0, 0.0)) > 1e-6:
        raise ValueError(f"the walk does not come back to its start: it ends at {p}, heading {h}")
    return outline[:-1], seats


def walk(steps, holes=()):
    """The table's outlines and its seats, in walking order: round the
    outside, then round each hole."""
    outline, seats = trace((0.0, 0.0), steps, False)
    outlines = [outline]
    for (hx, hy), hsteps in holes:
        o, s = trace((hx, hy), hsteps, True)
        outlines.append(o)
        seats += s
    crowded = [(i + 1, j + 1) for i in range(len(seats)) for j in range(i + 1, len(seats))
               if math.dist(seats[i], seats[j]) < 2 * R + 0.02]
    if crowded:
        raise ValueError(f"seats too close together: {crowded}")
    return outlines, seats


def written(steps, holes=()):
    """The walk in words, as the notes write it."""
    def words(ss):
        out = []
        for s in ss:
            if s[0] == "turn":
                out.append(f"turn {s[1]:g}")
            elif s[0] == "step":
                out.append(f"step {s[1]:g}" + (f" ({s[2]} seat{'s' * (s[2] != 1)})" if s[2] else ""))
            else:
                out.append(f"arc {s[1]:g}, radius {s[3]:.2f}" + (f" ({s[2]} seat{'s' * (s[2] != 1)})" if s[2] else ""))
        return " · ".join(out)
    text = words(steps)
    for (hx, hy), hs in holes:
        text += f"; hole at {hx:g} {hy:g}: " + words(hs)
    return text


# ---- the shapes, as walks; each also gives its box, from the walk's start ----

def rectangle(x, y, seated=("far", "right", "near", "left")):
    s = lambda edge, n: n if edge in seated else 0
    steps = [("step", x, s("far", x)), ("turn", 90), ("step", y, s("right", y)), ("turn", 90),
             ("step", x, s("near", x)), ("turn", 90), ("step", y, s("left", y)), ("turn", 90)]
    return steps, (), None


def round_table(diameter=None, places=None):
    if places is None:
        places = math.floor(math.pi * diameter + 1e-9)
    else:
        diameter = places / math.pi
    r = diameter / 2
    return [("arc", 360, places, r)], (), (-r, 0, diameter, diameter)


def stadium(x, y):
    r = y / 2
    ends = math.floor(math.pi * r + 1e-9)
    steps = [("step", x - y, x - y), ("arc", 180, ends, r), ("step", x - y, x - y), ("arc", 180, ends, r)]
    return steps, (), (-r, 0, x, y)


def oval(x, y):
    """Four arcs fitting the box: flat ones along the sides, tight ones at the
    ends, each holding as many seats as its length."""
    a, b = x / 2, y / 2
    r2 = (b * b / a + b) / 2                      # the ends
    r1 = ((a - r2) ** 2 + b * b - r2 * r2) / (2 * (b - r2))   # the sides
    phi = math.degrees(math.atan2(r1 - b, a - r2))           # half an end's turn
    side = 180 - 2 * phi
    n_side = math.floor(r1 * math.radians(side) + 1e-9)
    n_end = math.floor(r2 * math.radians(2 * phi) + 1e-9)
    steps = [("turn", -side / 2), ("arc", side, n_side, r1), ("arc", 2 * phi, n_end, r2),
             ("arc", side, n_side, r1), ("arc", 2 * phi, n_end, r2), ("turn", side / 2)]
    # the start, where the left end meets the top side
    cx, cy = a, r1
    ux, uy = (r2 - a), (b - r1)
    ln = math.hypot(ux, uy)
    sx, sy = cx + r1 * ux / ln, cy + r1 * uy / ln
    return steps, (), (-sx, -sy, x, y)


def polygon(sides, perside):
    steps = []
    for _ in range(sides):
        steps += [("step", perside, perside), ("turn", 360 / sides)]
    return steps, (), None


def ring(places, hole):
    """A round table with a round hole: two walks."""
    r = places / (2 * math.pi)
    return [("arc", 360, places, r)], [((0, r - hole / 2), [("arc", 360, 0, hole / 2)])], (-r, 0, 2 * r, 2 * r)


# ---- composed tables: the venue's tables pushed together ----
#
# rows: (size "w h" as placed, count, at (x, y), going "right"/"down", bare
# sides from far, near, left, right). The squares they cover give the
# outline; every unit of it is seated unless its table says that side is
# bare, holes only if seat_holes; at an inside corner where both edges are
# seated, the later seat (walking) is left out. The outline is then written
# as a walk.

SIDES = {(0, -1): "far", (1, 0): "right", (0, 1): "near", (-1, 0): "left"}


def composed(rows, seat_holes=False):
    cells, bare, stock = set(), set(), Counter()
    for (w, h), count, (x, y), going, bare_sides in rows:
        for i in range(count):
            ox, oy = (x + i * w, y) if going == "right" else (x, y + i * h)
            stock[tuple(sorted((w, h), reverse=True))] += 1
            for cx in range(ox, ox + w):
                for cy in range(oy, oy + h):
                    if (cx, cy) in cells:
                        raise ValueError(f"two tables on the same square {cx} {cy}")
                    cells.add((cx, cy))
                    for d, name in SIDES.items():
                        inside_table = ox <= cx + d[0] < ox + w and oy <= cy + d[1] < oy + h
                        if not inside_table and name in bare_sides:
                            bare.add(((cx, cy), d))
    # unit edges of the outline, the table on the right
    edges = {}
    for (cx, cy) in cells:
        for d, a, b in (((0, -1), (cx, cy), (cx + 1, cy)), ((1, 0), (cx + 1, cy), (cx + 1, cy + 1)),
                        ((0, 1), (cx + 1, cy + 1), (cx, cy + 1)), ((-1, 0), (cx, cy + 1), (cx, cy))):
            if (cx + d[0], cy + d[1]) not in cells:
                edges.setdefault(a, []).append((a, b, ((cx, cy), d) not in bare))
    loops = []
    while edges:
        a = min(edges, key=lambda v: (v[1], v[0]))
        loop = []
        while a in edges:
            e = edges[a].pop()
            if not edges[a]:
                del edges[a]
            loop.append(e)
            a = e[1]
        loops.append(loop)

    def area(loop):
        return sum(a[0] * b[1] - b[0] * a[1] for a, b, _ in loop)

    outer = [l for l in loops if area(l) > 0]
    holes = [l for l in loops if area(l) < 0]
    if len(outer) != 1:
        raise ValueError("the tables are not all joined together")

    def as_walk(loop, hole):
        if hole:   # walk it clockwise too, the table on the left
            loop = [(b, a, s and seat_holes) for a, b, s in reversed(loop)]
        # start at the top left, heading right
        i0 = min(range(len(loop)), key=lambda i: (loop[i][0][1], loop[i][0][0], loop[i][1][0] <= loop[i][0][0]))
        loop = loop[i0:] + loop[:i0]
        heading = [(b[0] - a[0], b[1] - a[1]) for a, b, _ in loop]
        seated = [s for _, _, s in loop]
        for i in list(range(1, len(loop))) + [0]:   # round to the start again
            cross = heading[i - 1][0] * heading[i][1] - heading[i - 1][1] * heading[i][0]
            inside_corner = cross > 0 if hole else cross < 0
            if inside_corner and seated[i - 1] and seated[i]:
                seated[i] = False
        steps, i = [], 0
        while i < len(loop):
            j = i
            while j + 1 < len(loop) and heading[j + 1] == heading[i] and seated[j + 1] == seated[i]:
                j += 1
            n = j - i + 1
            steps.append(("step", n, n if seated[i] else 0))
            nxt = heading[(j + 1) % len(loop)]
            if nxt != heading[i]:
                cross = heading[i][0] * nxt[1] - heading[i][1] * nxt[0]
                steps.append(("turn", 90 if cross > 0 else -90))
            i = j + 1
        return loop[0][0], steps

    start, steps = as_walk(outer[0], False)
    hole_walks = []
    for l in holes:
        (hx, hy), hs = as_walk(l, True)
        hole_walks.append(((hx - start[0], hy - start[1]), hs))
    return steps, hole_walks, None, stock


# ---- drawing ----

def svg(name, title, shape):
    steps, holes, box = shape[:3]
    stock = shape[3] if len(shape) > 3 else None
    outlines, seats = walk(steps, holes)
    pts = [p for o in outlines for p in o] + seats
    minx = min(p[0] for p in pts) - 1.2
    maxx = max(p[0] for p in pts) + 1.2
    miny = min(p[1] for p in pts) - 1.2
    maxy = max(p[1] for p in pts) + 1.0
    lines = [f"{title}: {len(seats)} seats"]
    if stock:
        lines.append("tables: " + ", ".join(f"{n} × {w}×{h}" for (w, h), n in sorted(stock.items(), reverse=True)))
    w = max((maxx - minx) * PX, 7.5 * max(len(l) for l in lines) + 20)
    h = (maxy - miny) * PX + 20 + 18 * len(lines)
    X = lambda v: (v - minx) * PX + (w - (maxx - minx) * PX) / 2
    Y = lambda v: (v - miny) * PX
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w:.0f}" height="{h:.0f}" '
         f'viewBox="0 0 {w:.0f} {h:.0f}" font-family="sans-serif">',
         '<rect width="100%" height="100%" fill="#ffffff"/>']
    if box:
        bx, by, bw, bh = box
        o.append(f'<rect x="{X(bx):.1f}" y="{Y(by):.1f}" width="{bw * PX:.1f}" height="{bh * PX:.1f}" '
                 'fill="none" stroke="#9aa5b1" stroke-dasharray="4 3"/>')
    path = " ".join("M " + " L ".join(f"{X(x):.1f} {Y(y):.1f}" for x, y in ol) + " Z" for ol in outlines)
    o.append(f'<path d="{path}" fill="#e8dcc4" stroke="#7a5c2e" stroke-width="1.5" fill-rule="evenodd"/>')
    for i, (x, y) in enumerate(seats, 1):
        o.append(f'<circle cx="{X(x):.1f}" cy="{Y(y):.1f}" r="{R * PX:.1f}" fill="#ffffff" stroke="#2f6fdb" stroke-width="1.5"/>')
        o.append(f'<text x="{X(x):.1f}" y="{Y(y) + 3.5:.1f}" font-size="10" text-anchor="middle" fill="#1d3557">{i}</text>')
    for k, line in enumerate(lines):
        o.append(f'<text x="{w / 2:.0f}" y="{(maxy - miny) * PX + 18 + 18 * k:.0f}" font-size="13" '
                 f'text-anchor="middle" fill="#222">{line}</text>')
    o.append('</svg>')
    with open(os.path.join(OUT, name + ".svg"), "w") as f:
        f.write("\n".join(o) + "\n")
    return len(seats), written(steps, holes), stock


def row(size, count, at, going="right", bare=""):
    return (size, count, at, going, set(bare.split()))


if __name__ == "__main__":
    horseshoe = [row((2, 1), 5, (0, 0), bare="near"),
                 row((1, 2), 3, (0, 1), "down"), row((1, 1), 1, (0, 7), bare="near"),
                 row((1, 2), 3, (9, 1), "down"), row((1, 1), 1, (9, 7), bare="near")]
    horseshoe_out = [row((2, 1), 5, (0, 0), bare="near"),
                     row((1, 2), 3, (0, 1), "down", bare="right"), row((1, 1), 1, (0, 7), bare="right near"),
                     row((1, 2), 3, (9, 1), "down", bare="left"), row((1, 1), 1, (9, 7), bare="left near")]
    square = [row((2, 1), 4, (0, 0)), row((1, 2), 3, (7, 1), "down"),
              row((2, 1), 4, (0, 7)), row((1, 2), 3, (0, 1), "down")]
    tee = [row((2, 1), 5, (0, 0), bare="near left"), row((1, 1), 1, (10, 0), bare="near right"),
           row((1, 2), 4, (5, 1), "down", bare="near")]
    e_top = lambda bare_l, bare_m, bare_r: [row((2, 1), 3, (0, 0), bare=bare_l), row((1, 1), 1, (6, 0), bare=bare_m),
                                           row((2, 1), 3, (7, 0), bare=bare_r)]
    prongs = [row((1, 2), 4, (x, 1), "down", bare="near") for x in (0, 6, 12)]
    sketches = [
        ("walk-top-table", "step 20, turn, step 2, turn, step 20, turn, step 2",
         ([("step", 20, 20), ("turn", 90), ("step", 2, 0), ("turn", 90), ("step", 20, 0), ("turn", 90),
           ("step", 2, 0), ("turn", 90)], (), None)),
        ("ring", "a ring table for 20, with a hole 3 across", ring(20, 3)),
        ("rectangle", "rectangle 6 × 2", rectangle(6, 2)),
        ("top-table", "rectangle 10 × 1, seated far", rectangle(10, 1, ("far",))),
        ("banqueting", "rectangle 14 × 1, seated sides", rectangle(14, 1, ("far", "near"))),
        ("round", "round, 4 across", round_table(4)),
        ("oval", "oval 8 × 4", oval(8, 4)),
        ("stadium", "stadium 8 × 4", stadium(8, 4)),
        ("hexagon", "polygon, 6 sides × 2", polygon(6, 2)),
        ("heptagon", "polygon, 7 sides × 1", polygon(7, 1)),
        ("horseshoe", "horseshoe, inside seats on the arms", composed(horseshoe)),
        ("horseshoe-outside", "horseshoe, outside only", composed(horseshoe_out)),
        ("hollow-square", "hollow square, outside only", composed(square)),
        ("hollow-square-inside", "hollow square, the hole seated too", composed(square, seat_holes=True)),
        ("t", "T: top 11, a stem of 8", composed(tee)),
        ("e", "E: top 13, three prongs of 8", composed(e_top("near left", "near", "near right") + prongs)),
        ("e-inside", "E, the top's inside seated too", composed(e_top("left", "", "right") + prongs)),
    ]
    for name, title, shape in sketches:
        n, words, stock = svg(name, title, shape)
        print(f"{name:21} {n:3}  {words}")
