#!/usr/bin/env python3
"""Build every piece of a hemp plant as models, the way cloud-kingdoms builds its beanstalk.

A plant in the world is a column of blocks (see PlantShape.java): a root, then stalk blocks up
the main stem, each knowing its place in the taper and which of four ways the plant's genome set
it. Its look is assembled from parts by a multipart blockstate:

  stalk     a quarter-jointed stalk to the block's fill, as thick as its place in the taper
  node      at each of the strain's nodes below the fill: a pair of fan leaves, each pair turned
            a quarter from the last, every blade a real serrated leaflet splayed from a palm; and
            a side branch from each leaf's axil, angled out and curling up, with small leaves and
            a growing tip, longer the further down the taper, so the plant grows its cone
  node buds once flowering, a knot at the node and buds along and at the tip of each branch
  apex      the young fans crowning a stalk that ends in this block
  cola      the flowering top that replaces them, spiky, then forming, then full

Branches reach well past their own block, as the beanstalk's leaves do; a model may.

The strains differ in every part: indica's nodes crowd close and carry broad dark leaves of
five and seven blades and dense buds; sativa's stand far apart with narrow pale leaves of seven
and nine and long airy buds; hybrid sits between. An untrimmed planting is a clump of seedlings,
then a tangle of thin stems in its strain's manner that flowers small and seedy, in sprigs of pods
rather than colas; wild hemp is that tangle, gone to seed.

A joint's item switches its look as it burns down, and while it is being smoked swaps to a
model held to the lips: the client plays a puff as eating, and eating would otherwise hold the
joint upright in front of the face.

Everything is cut from textures/block/hemp_plant.png, whose layout generate_textures.py owns.
Deterministic: re-running produces identical files.

Usage: python3 generate_models.py
"""

import json
import math
import os
import random

import generate_textures as art

HERE = os.path.dirname(os.path.abspath(__file__))
NAMESPACE = "hemp-craft-justfatlard"
ASSETS = os.path.join(HERE, "src/main/resources/assets", NAMESPACE)
TEXTURE = NAMESPACE + ":block/hemp_plant"
UV = 16 / art.SHEET_SIZE

STRAINS = ("indica", "sativa", "hybrid")

# Per strain: node heights within a stalk block, blades on a young and a grown leaf, how broad a
# blade is against its length, how long its leaves reach, how far they droop, and its flowering
# top as (half width, height) boxes from the bottom up.
TRAITS = {
    "indica": {"nodes": (3, 8, 13), "blades": (5, 7), "breadth": 0.42, "reach": 0.85, "droop": 12,
               "spire": [(1.5, 1.4), (1.9, 1.8), (1.6, 1.6), (1.1, 1.2)]},
    "sativa": {"nodes": (6, 14), "blades": (7, 9), "breadth": 0.22, "reach": 1.2, "droop": 20,
               "spire": [(0.9, 1.6), (1.1, 1.8), (1.0, 1.8), (0.8, 1.6), (0.5, 1.4)]},
    "hybrid": {"nodes": (4, 11), "blades": (7, 7), "breadth": 0.32, "reach": 1.0, "droop": 16,
               "spire": [(1.2, 1.5), (1.5, 1.8), (1.2, 1.6), (0.8, 1.3)]},
}
LEAF_LENGTH = {1: 4.8, 2: 8.0}

# A fan's blades as (turn off the leaf's heading, share of its length), the outer ones short and
# swept back.
BLADES = {
    3: [(0, 1.0), (-38, 0.7), (38, 0.7)],
    5: [(0, 1.0), (-32, 0.78), (32, 0.78), (-64, 0.52), (64, 0.52)],
    7: [(0, 1.0), (-26, 0.84), (26, 0.84), (-52, 0.64), (52, 0.64), (-80, 0.42), (80, 0.42)],
    9: [(0, 1.0), (-20, 0.9), (20, 0.9), (-40, 0.76), (40, 0.76), (-60, 0.58), (60, 0.58),
        (-82, 0.4), (82, 0.4)],
}
STALK_WIDTH = (1.0, 1.4, 1.9, 2.4)
# Per strain, a node's side branches: length by the block's place in the taper (none at the tip),
# angle out from upright where they leave the stalk, and how much each joint curls back up.
# Indica's spread wide and short; sativa's climb steep and long.
LATERAL = {
    "indica": {"length": (0, 9, 15, 20), "angle": 62, "curl": 12},
    "sativa": {"length": (0, 9, 15, 21), "angle": 44, "curl": 9},
    "hybrid": {"length": (0, 9, 15, 20), "angle": 53, "curl": 11},
}
LATERAL_WIDTH = (0, 0.7, 0.85, 1.0)
# The highest a branch tip may reach, leaving room for its buds under the ceiling on a model.
TIP_CEILING = 26.0
# The corners a model element may have, checked before the element is turned.
LIMIT = (-16.0, 32.0)
# How far down from its tip an untrimmed stem carries seed sprigs, by bud stage: a few small green
# pods, then a few more, then the last stage, when the pods explode into growth along most of the
# top of the stem.
SEED_SPAN = (0.0, 5.0, 7.0, 14.0)
# Per strain, an untrimmed planting: how many stems (one more in some), how far each leans per
# block of height and how far from the middle it rises, its girth, the size of its seed pods, the
# space between its nodes, its leaves' length, and the chance of a twig at a node. Indica's tangle
# is low, sturdy and crowded; sativa's is few, whippy stems leaning far apart.
LANKY = {
    "indica": {"stems": 3, "lean": 0.5, "spread": 2.4, "width": 0.75, "pod": 1.15, "node": 4.5, "leaf": 5.0,
               "twig": 0.3},
    "sativa": {"stems": 3, "lean": 1.0, "spread": 2.6, "width": 0.55, "pod": 0.95, "node": 6.0, "leaf": 5.2,
               "twig": 0.18},
    "hybrid": {"stems": 3, "lean": 0.75, "spread": 2.5, "width": 0.65, "pod": 1.05, "node": 5.2, "leaf": 5.0,
               "twig": 0.24},
}


def region(name):
    x0, y0, x1, y1 = art.SHEET[name]
    return x0 * UV, y0 * UV, x1 * UV, y1 * UV


def face(uv):
    return {"uv": [round(v, 3) for v in uv], "texture": "#plant"}


def r(value):
    return round(value, 3)


def hash_of(*parts):
    """A seed from a part's name, stable from run to run as Python's own hash is not."""
    value = 0
    for ch in "|".join(str(p) for p in parts):
        value = (value * 131 + ord(ch)) % (2 ** 31)
    return value


def turn(vector, pitch, yaw):
    """Where a vector points after the model's rotation {"x": pitch, "y": yaw, "z": 0}: the game
    turns about x first, then y."""
    x, y, z = vector
    a, b = math.radians(pitch), math.radians(yaw)
    y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
    x, z = x * math.cos(b) + z * math.sin(b), -x * math.sin(b) + z * math.cos(b)
    return x, y, z


def span(origin_z, length):
    """How to lay an element of this length from origin along z inside the bounds the game
    accepts for a model element's corners, which it checks before turning it: south as it is, or
    north and turned half round, which points the same way. Returns (reversed, length)."""
    if origin_z + length <= LIMIT[1]:
        return False, length
    if origin_z - length >= LIMIT[0]:
        return True, length
    return False, max(0.1, LIMIT[1] - origin_z)


def blade(origin, pitch, yaw, length, width, top, under):
    """A flat blade lying south from origin, then pitched (positive droops it) and turned to its
    heading. Its top shows one part of the sheet and its underside another; the sheet draws the
    tip at the top, and an upward face reads its uv from the north (base) end, so it is flipped.
    Laid north instead, when south would run out of bounds, the faces read the other way round."""
    ox, oy, oz = origin
    u0, v0, u1, v1 = top
    b0, c0, b1, c1 = under
    reverse, length = span(oz, length)
    if reverse:
        z0, z1, pitch, yaw = oz - length, oz, -pitch, yaw + 180
        faces = {"up": face((u0, v0, u1, v1)), "down": face((b0, c1, b1, c0))}
    else:
        z0, z1 = oz, oz + length
        faces = {"up": face((u0, v1, u1, v0)), "down": face(under)}
    return {
        "from": [r(ox - width / 2), r(oy), r(z0)],
        "to": [r(ox + width / 2), r(oy), r(z1)],
        "rotation": {"origin": [r(ox), r(oy), r(oz)], "x": round(pitch, 2), "y": round(yaw, 2), "z": 0},
        "faces": faces,
    }


def box(x0, y0, z0, x1, y1, z1, side, top=None):
    """An upright box, nudged back inside the model's bounds if it would poke past them: a bud at
    the end of the longest branch lands right at the edge."""
    shifts = [max(0.0, LIMIT[0] - lo) - max(0.0, hi - LIMIT[1]) for lo, hi in ((x0, x1), (y0, y1), (z0, z1))]
    x0, x1 = x0 + shifts[0], x1 + shifts[0]
    y0, y1 = y0 + shifts[1], y1 + shifts[1]
    z0, z1 = z0 + shifts[2], z1 + shifts[2]
    faces = {d: face(side) for d in ("north", "south", "east", "west")}
    if top:
        faces["up"] = face(top)
    return {"from": [r(x0), r(y0), r(z0)], "to": [r(x1), r(y1), r(z1)], "faces": faces}


def stalk_side(y0, y1):
    """The stalk's bark for a span of height, clamped to the sheet: a face reading past its
    region fails to bake, and the block draws as the missing-model cube."""
    y0, y1 = max(0.0, min(16.0, y0)), max(0.0, min(16.0, y1))
    u0, v0, u1, _ = region("stalk")
    return u0, v0 + (16 - y1) * 0.5, u1, v0 + (16 - y0) * 0.5


def fan(elements, at, heading, length, blades, strain, rng, rise=25.0, droop=None):
    """One leaf: a petiole held out from the stalk, then its blades splayed from the palm."""
    traits = TRAITS[strain]
    petiole = 0.8 + length * 0.25
    x, y, z = at
    elements.append(blade((x, y, z), -rise, heading, petiole, 0.45, region("petiole"), region("petiole")))
    dx, dy, dz = turn((0, 0, petiole), -rise, heading)
    palm = (x + dx, y + dy, z + dz)
    width = max(0.8, min(2.4, length * traits["breadth"]))
    droop = traits["droop"] + rng.uniform(-4, 4) if droop is None else droop
    for offset, share in BLADES[blades]:
        elements.append(blade(palm, droop + 0.35 * abs(offset), heading + offset,
                              length * share * rng.uniform(0.9, 1.0), width,
                              region("leaflet_" + strain), region("under_" + strain)))


def leaf_pair(elements, at, heading, size, strain, rng):
    traits = TRAITS[strain]
    for side in (0, 180):
        fan(elements, at, heading + side + rng.uniform(-12, 12), LEAF_LENGTH[size] * traits["reach"],
            traits["blades"][size - 1], strain, rng)


def crown(elements, at, strain, rng, scale=1.0):
    """A growing tip: two young fans held up and a folded one between them."""
    heading = rng.uniform(0, 180)
    blades = TRAITS[strain]["blades"][0]
    for side in (0, 180):
        fan(elements, at, heading + side, 2.4 * scale, blades, strain, rng, rise=60, droop=-25)
    elements.append(blade(at, -80, heading + 90, 2.0 * scale, 0.8,
                          region("leaflet_" + strain), region("under_" + strain)))


def spire(elements, at, strain, stage, rng):
    """A flowering top: a tapering stack of buds, small while they set, and sugar leaves poking
    out between them."""
    x, y, z = at
    scale = (0.5, 0.8, 1.0)[stage - 1]
    buds = region("buds_" + strain)
    for half, height in TRAITS[strain]["spire"]:
        half, height = half * scale, height * scale
        elements.append(box(x - half, y, z - half, x + half, y + height, z + half, buds, buds))
        y += height
    for i in range(3):
        h = at[1] + (y - at[1]) * (0.3 + 0.25 * i)
        elements.append(blade((x, h, z), -55, rng.uniform(0, 360), 1.6 + 0.6 * scale, 0.7,
                              region("leaflet_" + strain), region("under_" + strain)))


def knot(elements, at, strain, stage):
    """Buds at a node, where the leaves meet the stalk."""
    x, y, z = at
    half = 0.4 + 0.25 * stage
    buds = region("buds_" + strain)
    elements.append(box(x - half, y, z - half, x + half, y + half * 1.6, z + half, buds, buds))


def nodes_below(strain, fill):
    return [n for n in TRAITS[strain]["nodes"] if n < fill * 4 - 1]


def fills_for(height):
    """The fills of a stalk block that reach past a node at this height."""
    return [f for f in range(1, 5) if height < f * 4 - 1]


def stalk_part(thickness, fill, variant):
    """The stalk in quarter joints, each set a touch off the last; the variant bows it one way or
    the other."""
    elements = []
    width = STALK_WIDTH[thickness]
    top = fill * 4
    bow = ((-0.15, 0.15, -0.15, 0.15), (0.1, 0.3, 0.25, 0.0))[variant]
    for q in range(fill):
        y0, y1 = 4 * q, 4 * q + 4
        shift = bow[q]
        elements.append(box(8 - width / 2 + shift, y0, 8 - width / 2 - shift / 2,
                            8 + width / 2 + shift, y1, 8 + width / 2 - shift / 2,
                            stalk_side(y0, y1), region("cap") if y1 >= top else None))
    return elements


def rod(origin, pitch, yaw, length, width):
    """A round-ish stem segment along its heading: a thin box turned the way a blade is, barked
    on its sides, laid north and turned half round when south would run out of bounds."""
    ox, oy, oz = origin
    u0, v0, u1, _ = region("stalk")
    bark = face((u0, v0 + 1, u1, v0 + 2))
    reverse, length = span(oz, length)
    if reverse:
        z0, z1, pitch, yaw = oz - length, oz, -pitch, yaw + 180
    else:
        z0, z1 = oz, oz + length
    return {
        "from": [r(ox - width / 2), r(oy - width / 2), r(z0)],
        "to": [r(ox + width / 2), r(oy + width / 2), r(z1)],
        "rotation": {"origin": [r(ox), r(oy), r(oz)], "x": round(pitch, 2), "y": round(yaw, 2), "z": 0},
        "faces": {"east": bark, "west": bark, "up": bark, "down": bark},
    }


def node_heading(strain, j, variant):
    """Leaf pairs turn a quarter at each node; the variant nudges the whole set."""
    return 90 * (j % 2) + (17, -23)[variant]


def laterals(strain, j, thickness, variant):
    """The side branches at a node, worked out once so the leafy part and the bud part agree:
    each a list of joints from the stalk to the tip. None at the tip of the taper; longer the
    further down, and longer at the lower nodes of a block, which draws the tree's cone."""
    if thickness == 0:
        return []
    traits = LATERAL[strain]
    rng = random.Random(hash_of("lateral", strain, j, thickness, variant))
    nodes = TRAITS[strain]["nodes"]
    below = len(nodes) - 1 - j
    base_length = traits["length"][thickness] * (1 + 0.18 * below)
    heading = node_heading(strain, j, variant) + 18
    branches = []
    for side in (0, 180):
        length = base_length * rng.uniform(0.8, 1.1)
        elevation = 90 - traits["angle"] - rng.uniform(-6, 6)
        width = LATERAL_WIDTH[thickness]
        at = (8.0, nodes[j], 8.0)
        yaw = heading + side + rng.uniform(-14, 14)
        joints = reach_out(at, length, elevation, yaw, traits["curl"])
        if joints[3][1] > TIP_CEILING:
            # A steep branch high in its block would carry its tip buds past the height a model
            # may reach: shorten it to fit rather than lose the part.
            length *= (TIP_CEILING - at[1]) / (joints[3][1] - at[1])
            joints = reach_out(at, length, elevation, yaw, traits["curl"])
        branches.append({"yaw": yaw, "elevation": elevation, "width": width, "joints": joints,
                         "length": length})
    return branches


def reach_out(at, length, elevation, yaw, curl):
    joints = [at]
    for step in range(3):
        dx, dy, dz = turn((0, 0, length / 3), -min(82, elevation + curl * step), yaw)
        at = (at[0] + dx, at[1] + dy, at[2] + dz)
        joints.append(at)
    return joints


def node_part(strain, j, size, thickness, variant):
    """One node: its pair of fan leaves, grown bigger the older the block, and a side branch from
    each leaf's axil carrying small leaves of its own and a growing tip."""
    rng = random.Random(hash_of("node", strain, j, size, thickness, variant))
    traits = TRAITS[strain]
    height = traits["nodes"][j]
    elements = []
    if size:
        heading = node_heading(strain, j, variant)
        length = LEAF_LENGTH[size] * traits["reach"] * (1 + 0.12 * thickness)
        for side in (0, 180):
            fan(elements, (8, height, 8), heading + side + rng.uniform(-10, 10), length,
                traits["blades"][size - 1], strain, rng)
    for branch in laterals(strain, j, thickness, variant):
        joints = branch["joints"]
        for step in range(3):
            elevation = min(82, branch["elevation"] + LATERAL[strain]["curl"] * step)
            elements.append(rod(joints[step], -elevation, branch["yaw"], branch["length"] / 3 + 0.3,
                                branch["width"]))
        leaf = 3.0 + 0.6 * thickness
        for joint, turn_off in ((1, 90), (2, 0)):
            for side in (turn_off, turn_off - 180):
                fan(elements, joints[joint], branch["yaw"] + side, leaf * traits["reach"] * (1.1 - 0.2 * (joint - 1)),
                    traits["blades"][0], strain, rng)
        crown(elements, joints[3], strain, rng, scale=0.45 + 0.1 * thickness)
    return elements


def node_buds_part(strain, j, thickness, stage, variant):
    """Flowering at a node: a knot where the leaves meet the stalk, and buds at each side
    branch's tip and part way along it, bigger on the longer branches."""
    elements = []
    height = TRAITS[strain]["nodes"][j]
    knot(elements, (8, height, 8), strain, stage)
    rng = random.Random(hash_of("node_buds", strain, j, thickness, stage, variant))
    for branch in laterals(strain, j, thickness, variant):
        joints = branch["joints"]
        tip_buds(elements, joints[3], strain, stage, 0.55 + 0.15 * thickness, rng)
        if thickness >= 2:
            knot(elements, joints[2], strain, max(1, stage - 1))
    return elements


def tip_buds(elements, at, strain, stage, size, rng):
    """A branch's flowering tip: a short stack of buds in the strain's shape, and a sugar leaf."""
    x, y, z = at
    buds = region("buds_" + strain)
    scale = (0.5, 0.8, 1.0)[stage - 1] * size
    for half, height in TRAITS[strain]["spire"][:3]:
        half, height = half * scale, height * scale
        elements.append(box(x - half, y, z - half, x + half, y + height, z + half, buds, buds))
        y += height
    elements.append(blade(at, -60, rng.uniform(0, 360), 1.4 + scale, 0.6,
                          region("leaflet_" + strain), region("under_" + strain)))


def apex_part(strain, fill):
    elements = []
    crown(elements, (8, fill * 4, 8), strain, random.Random(hash_of("apex", strain, fill)))
    return elements


def cola_part(strain, stage, fill):
    elements = []
    spire(elements, (8, fill * 4 - 1, 8), strain, stage, random.Random(hash_of("cola", strain, stage, fill)))
    return elements


def clump_part(strain, stage, variant):
    """Seedlings come up together: seed leaves first, then a stem and a fan apiece. How many and
    where is the variant's."""
    rng = random.Random(hash_of("clump", strain, stage, variant))
    layout = random.Random(hash_of("clump_layout", strain, variant))
    count = 3 + layout.randrange(3)
    elements = []
    for k in range(count):
        angle = 2 * math.pi * k / count + layout.uniform(-0.5, 0.5)
        radius = layout.uniform(1.5, 3.8)
        x, z = 8 + radius * math.cos(angle), 8 + radius * math.sin(angle)
        height = (1, 3, 5, 7)[stage] * layout.uniform(0.7, 1.15)
        elements.append(box(x - 0.3, 0, z - 0.3, x + 0.3, height, z + 0.3, stalk_side(0, height)))
        heading = rng.uniform(0, 180)
        for side in (0, 180):
            elements.append(blade((x, min(height, 2.0), z), -10, heading + side, 1.8, 1.4,
                                  region("seed_leaf"), region("seed_leaf")))
        if stage >= 1:
            blades = 3 if stage == 1 else TRAITS[strain]["blades"][0]
            for side in (0, 180):
                fan(elements, (x, height, z), heading + 90 + side, 1.2 + 0.6 * stage, blades, strain, rng,
                    rise=45, droop=0)
    return elements


def lanky_layout(strain, variant):
    """An untrimmed planting's stems: where each rises from the clump, which way it leans, how
    tall it stands against the tallest, and whether it gave up early. Worked out once per variant
    so both blocks of a plant draw the same stems."""
    traits = LANKY[strain]
    rng = random.Random(hash_of("lanky_layout", strain, variant))
    count = traits["stems"] + rng.randrange(2)
    stems = []
    for k in range(count):
        angle = 2 * math.pi * k / count + rng.uniform(-0.45, 0.45)
        radius = rng.uniform(0.8, traits["spread"])
        lean = traits["lean"] * rng.uniform(0.6, 1.25)
        tilt = angle + rng.uniform(-0.6, 0.6)
        stems.append({
            "x": 8 + radius * math.cos(angle), "z": 8 + radius * math.sin(angle),
            "lx": lean * math.cos(tilt), "lz": lean * math.sin(tilt),
            "share": 1.0 if k == 0 else rng.uniform(0.62, 0.95),
            # One stem in some plants is spent before it reaches the upper block.
            "stop": rng.uniform(9.0, 13.5) if k == count - 1 and variant == 1 else None,
            "phase": rng.uniform(0, 4),
        })
    return stems


def sprig(elements, at, yaw, length, pods, ripe, size, strain, bract=True):
    """A seed sprig: a hair of stem off the main one, angled up, with pods along it, the biggest
    at its end, and a leafy bract over the last while the pods are young. Ripe pods show their
    seed."""
    pod = region("pod_ripe" if ripe else "pod_green")
    elements.append(rod(at, -55, yaw, length, 0.28))
    dx, dy, dz = turn((0, 0, length), -55, yaw)
    end = (at[0] + dx, at[1] + dy, at[2] + dz)
    for k, along in enumerate((1.0, 0.62, 0.3)[:pods]):
        x, y, z = at[0] + dx * along, at[1] + dy * along, at[2] + dz * along
        half = size * (1.0 - 0.18 * k)
        elements.append(box(x - half, y - half * 0.6, z - half, x + half, y + half * 1.2, z + half, pod, pod))
    if bract:
        elements.append(blade(end, -35, yaw + 40, 1.3 + size, 0.7, region("leaflet_" + strain), region("under_" + strain)))


def leaf(elements, at, heading, length, blades, strain, rng):
    """A fan with no leaf stalk of its own, splayed right at the stem: an untrimmed plant carries
    a great many, and the stalk is the part nobody would miss."""
    x, y, z = at
    dx, _, dz = turn((0, 0, 0.6), 0, heading)
    palm = (x + dx, y, z + dz)
    width = max(0.8, min(2.4, length * TRAITS[strain]["breadth"]))
    droop = TRAITS[strain]["droop"] + rng.uniform(-5, 5)
    for offset, share in BLADES[blades]:
        elements.append(blade(palm, droop + 0.35 * abs(offset), heading + offset,
                              length * share * rng.uniform(0.88, 1.0), width,
                              region("leaflet_" + strain), region("under_" + strain)))


def seed_spike(elements, at, strain, stage, rng):
    """A stem's flowering tip, untrimmed: a slim spike of pods, not a cola, and a bract leaf."""
    x, y, z = at
    pod = region("pod_ripe" if stage >= 3 else "pod_green")
    size = (0.45, 0.52, 0.75)[stage - 1] * LANKY[strain]["pod"]
    for step in range(2 + stage):
        half = size * (1.0 - 0.12 * step)
        elements.append(box(x - half, y, z - half, x + half, y + half * 2.2, z + half, pod, pod))
        y += half * 1.8
    elements.append(blade(at, -62, rng.uniform(0, 360), 1.6, 0.6,
                          region("leaflet_" + strain), region("under_" + strain)))


def lanky_part(strain, stage, buds, lower, variant):
    """Untrimmed stems through one block: thin ones leaning apart in the strain's manner, small
    leaves at their nodes, a twig here and there, and once flowering, not buds but seed: sprigs of
    small pods up the top of each stem and a seed spike at each tip, more of them and riper as the
    days go on. The lower block reaches from the ground; the upper carries each stem on from where
    it left the lower, so the two meet. A lower block that is full goes on above and draws no tips
    but a spent stem's."""
    rng = random.Random(hash_of("lanky", strain, stage, buds, lower, variant))
    traits = TRAITS[strain]
    lanky = LANKY[strain]
    elements = []
    fill = (stage + 1) * 4
    base = 0 if lower else 16
    for i, stem in enumerate(lanky_layout(strain, variant)):
        if lower and stage == 3:
            end, tip = (stem["stop"], True) if stem["stop"] else (16.0, False)
        elif lower:
            end, tip = max(2.0, fill * stem["share"]), True
        elif stem["stop"]:
            continue
        else:
            end, tip = max(2.0, fill * stem["share"]), True

        def at(y):
            h = base + y
            return stem["x"] + stem["lx"] * h / 16, stem["z"] + stem["lz"] * h / 16

        y = 0.0
        while y < end:
            y1 = min(end, y + 4)
            (ax, az), (bx, bz) = at(y), at(y1)
            cx, cz = (ax + bx) / 2, (az + bz) / 2
            w = lanky["width"] / 2
            elements.append(box(cx - w, y, cz - w, cx + w, y1, cz + w, stalk_side(y, y1)))
            y = y1
        node = 2.5 + stem["phase"]
        whole = end if lower else end + 16
        # Gone to seed, the seed heads crowd the top of the stem out of leaves.
        leafless = end - SEED_SPAN[3] if buds == 3 and tip else end
        while node < min(end - 1.5, leafless):
            x, z = at(node)
            heading = 90 * int(node / lanky["node"]) + 30 * i
            # Pairs low on the stem, one leaf a node higher up, smaller toward the tip.
            reach = (base + node) / max(whole, 1.0)
            length = lanky["leaf"] * traits["reach"] * (1.0 - 0.35 * reach)
            for side in ((0, 180) if reach < 0.55 else (180 * (int(node / lanky["node"]) % 2),)):
                leaf(elements, (x, node, z), heading + side, length, 5, strain, rng)
            if rng.random() < lanky["twig"]:
                twig_at = (x, node + 0.3, z)
                elements.append(rod(twig_at, -48, heading + 90, 2.4, 0.3))
                dx, dy, dz = turn((0, 0, 2.4), -48, heading + 90)
                leaf(elements, (x + dx, node + 0.3 + dy, z + dz), heading + 90, 2.2, 3, strain, rng)
            node += lanky["node"]
        if buds:
            span = SEED_SPAN[buds]
            gap = (3.5, 2.8, 1.3)[buds - 1]
            h = max(0.5, end - span) if (not lower or stage < 3 or stem["stop"]) else end
            side = rng.uniform(0, 360)
            while h < end - 0.8:
                x, z = at(h)
                sprig(elements, (x, h, z), side, rng.uniform(1.6, 2.4) * (1.2 if buds == 3 else 1.0) * lanky["pod"],
                      (1, 1, 3)[buds - 1], buds == 3, (0.38, 0.42, 0.62)[buds - 1] * lanky["pod"], strain,
                      bract=buds < 3)
                side += 137.5
                h += gap
        if tip:
            x, z = at(end)
            if buds:
                seed_spike(elements, (x, end - 0.3, z), strain, buds, rng)
            else:
                crown(elements, (x, end, z), strain, rng, scale=0.55)
    return elements


def model(elements):
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"plant": TEXTURE, "particle": TEXTURE}, "elements": elements}


def write(path, data):
    """Compact, not indented: hundreds of these ride to every client in Pandorical's sync."""
    path = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, separators=(",", ":"))
        f.write("\n")


def ref(name, y=0):
    out = {"model": "%s:block/%s" % (NAMESPACE, name)}
    if y:
        out["y"] = y
    return out


def by_variant(name, variant):
    """Variants 2 and 3 are 0 and 1 turned half round, so four looks come of two models."""
    return ref("%s_v%d" % (name, variant % 2), 180 if variant >= 2 else 0)


def stalk_multipart():
    parts = []
    for t in range(4):
        for f in range(1, 5):
            for v in range(4):
                parts.append({"when": {"thickness": str(t), "fill": str(f), "variant": str(v)},
                              "apply": by_variant("stalk_t%d_f%d" % (t, f), v)})
    for s in STRAINS:
        for j, height in enumerate(TRAITS[s]["nodes"]):
            fills = "|".join(str(f) for f in fills_for(height))
            if not fills:
                continue
            for size in range(3):
                for t in range(4):
                    if size == 0 and t == 0:
                        continue
                    for v in range(4):
                        parts.append({"when": {"strain": s, "fill": fills, "leaves": str(size),
                                               "thickness": str(t), "variant": str(v)},
                                      "apply": by_variant("node_%s_%d_l%d_t%d" % (s, j, size, t), v)})
            for t in range(4):
                for b in range(1, 4):
                    for v in range(4):
                        parts.append({"when": {"strain": s, "fill": fills, "thickness": str(t),
                                               "buds": str(b), "variant": str(v)},
                                      "apply": by_variant("node_buds_%s_%d_t%d_b%d" % (s, j, t, b), v)})
        for f in range(1, 4):
            parts.append({"when": {"strain": s, "fill": str(f), "buds": "0"}, "apply": ref("apex_%s_f%d" % (s, f))})
            for b in range(1, 4):
                parts.append({"when": {"strain": s, "fill": str(f), "buds": str(b)},
                              "apply": ref("cola_%s_b%d_f%d" % (s, b, f))})
    return {"multipart": parts}


def crop_multipart():
    parts = []
    for s in STRAINS:
        for stage in range(4):
            for v in range(4):
                parts.append({"when": {"strain": s, "form": "cluster", "stage": str(stage), "variant": str(v)},
                              "apply": by_variant("clump_%s_%d" % (s, stage), v)})
                for b in range(4):
                    parts.append({"when": {"strain": s, "form": "lanky", "stage": str(stage), "buds": str(b),
                                           "variant": str(v)},
                                  "apply": by_variant("lanky_%s_%d_b%d" % (s, stage, b), v)})
    return {"multipart": parts}


def stems_multipart():
    parts = []
    for s in STRAINS:
        for stage in range(4):
            for b in range(4):
                for v in range(4):
                    parts.append({"when": {"strain": s, "stage": str(stage), "buds": str(b), "variant": str(v)},
                                  "apply": by_variant("stems_%s_%d_b%d" % (s, stage, b), v)})
    return {"multipart": parts}


def wild_multipart():
    """Wild hemp is a full lower block of a lanky plant gone to seed; its stems stand on it."""
    return {"multipart": [{"when": {"strain": s, "variant": str(v)}, "apply": by_variant("lanky_%s_3_b3" % s, v)}
                          for s in STRAINS for v in range(4)]}


# A joint's looks as it burns, and the share of its durability used before each takes over.
JOINT_BURNT = [("joint_lit", 0.005), ("joint_half", 0.5), ("joint_stub", 0.8)]
# First person, while smoking: the eating pose brings the hand to the middle of the view, and this
# lays the joint in it with the crutch at the lips, at the foot of the view, and the lit end out ahead
# and to the right of the middle. JointItem.PUFF puts the smoke where this puts the lit end.
PUFF = {"rotation": [-75.0, 4.9, -79.9], "translation": [0.47, 6.69, 3.72], "scale": [0.546] * 3}


def joint_models(strain):
    """Each look as a handheld item, again held to the lips, and the item that picks between
    them. The left hand's pose is the right's mirrored: the renderer mirrors rotation and offset
    for the left hand, and the flipped scale mirrors the sprite to match."""
    puff_left = dict(PUFF, scale=[-PUFF["scale"][0]] + PUFF["scale"][1:])

    def look(name, puffing):
        path = "%s:item/%s_%s" % (NAMESPACE, strain, name)
        return {"type": "minecraft:model", "model": path + "_puff" if puffing else path}

    for name in ["joint"] + [name for name, _ in JOINT_BURNT]:
        held = "%s:item/%s_%s" % (NAMESPACE, strain, name)
        write("models/item/%s_%s.json" % (strain, name),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": held}})
        write("models/item/%s_%s_puff.json" % (strain, name),
              {"parent": held, "display": {"firstperson_righthand": PUFF, "firstperson_lefthand": puff_left}})

    def burning(puffing):
        return {"type": "minecraft:range_dispatch", "property": "minecraft:damage", "normalize": True,
                "entries": [{"threshold": at, "model": look(name, puffing)} for name, at in JOINT_BURNT],
                "fallback": look("joint", puffing)}

    write("items/%s_joint.json" % strain,
          {"model": {"type": "minecraft:condition", "property": "minecraft:using_item",
                     "on_true": burning(True), "on_false": burning(False)}})


def main():
    models_dir = os.path.join(ASSETS, "models/block")
    if os.path.isdir(models_dir):
        for old in os.listdir(models_dir):
            os.remove(os.path.join(models_dir, old))
    count = 0
    elements = 0

    def emit(name, parts):
        nonlocal count, elements
        write("models/block/%s.json" % name, model(parts))
        count += 1
        elements += len(parts)

    for t in range(4):
        for f in range(1, 5):
            for v in range(2):
                emit("stalk_t%d_f%d_v%d" % (t, f, v), stalk_part(t, f, v))
    for s in STRAINS:
        for j in range(len(TRAITS[s]["nodes"])):
            for t in range(4):
                for v in range(2):
                    for size in range(3):
                        if size or t:
                            emit("node_%s_%d_l%d_t%d_v%d" % (s, j, size, t, v), node_part(s, j, size, t, v))
                    for b in range(1, 4):
                        emit("node_buds_%s_%d_t%d_b%d_v%d" % (s, j, t, b, v), node_buds_part(s, j, t, b, v))
        for f in range(1, 4):
            emit("apex_%s_f%d" % (s, f), apex_part(s, f))
            for b in range(1, 4):
                emit("cola_%s_b%d_f%d" % (s, b, f), cola_part(s, b, f))
        for stage in range(4):
            for v in range(2):
                emit("clump_%s_%d_v%d" % (s, stage, v), clump_part(s, stage, v))
                for b in range(4):
                    emit("lanky_%s_%d_b%d_v%d" % (s, stage, b, v), lanky_part(s, stage, b, True, v))
                    emit("stems_%s_%d_b%d_v%d" % (s, stage, b, v), lanky_part(s, stage, b, False, v))

    stalk = stalk_multipart()
    write("blockstates/hemp_root.json", stalk)
    write("blockstates/hemp_stalk.json", stalk)
    write("blockstates/hemp_crop.json", crop_multipart())
    write("blockstates/hemp_stems.json", stems_multipart())
    write("blockstates/wild_hemp.json", wild_multipart())
    for s in STRAINS:
        joint_models(s)
    print("wrote %d models (%d elements), 5 blockstates and the joints' items" % (count, elements))


if __name__ == "__main__":
    main()
