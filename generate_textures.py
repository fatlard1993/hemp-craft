#!/usr/bin/env python3
"""Draw Hemp Craft's textures and mod menu icon.

Every colour is read out of the vanilla jar: the greens from carrot tops, the flowering tops and
cut fibre from wheat's straw, the seed coat from gravel, the pistils from carrot and glow berries,
a dried flower's frost from sugar and indica's purple from chorus fruit. The fan leaf is
the thing anyone reads as hemp at a glance, so everything is built around it. The crop and wild
hemp are models (generate_models.py) cut from one sheet drawn here: stalk, petiole, each strain's
leaflet top and underside and its buds, an untrimmed plant's seed pods green and ripe, and a seed
leaf. The harvest is a tied bundle of cut green stalks; each strain's seed takes beetroot's round
shape in hemp's grey-brown, its flower is a dried bud in the strain's shape, and its joint is a
stubby cone that burns down through four looks; the icon is one fan leaf filling the frame.

Pure stdlib PNG reader and writer (zlib + struct) so it runs without Pillow, the same script
generated art approach as the rest of the suite. Deterministic: re-running produces identical
bytes.

Usage: python3 generate_textures.py [path/to/minecraft.jar]
"""

import glob
import math
import os
import random
import struct
import sys
import zipfile
import zlib
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "src/main/resources/assets/hemp-craft-justfatlard")

CLEAR = (0, 0, 0, 0)
_JAR = None


def minecraft_version():
    """The version this mod targets, so the art is cut from the same jar the mod is built
    against rather than whatever happens to be cached."""
    path = os.path.join(HERE, "gradle.properties")
    if not os.path.exists(path):
        return None
    for line in open(path):
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            return value.strip()
    return None


def find_jar():
    """Loom caches the remapped Minecraft jars after a build; that is where the vanilla art
    comes from. Override with an argument or $MINECRAFT_JAR."""
    global _JAR
    if _JAR:
        return _JAR
    if len(sys.argv) > 1:
        _JAR = sys.argv[1]
        return _JAR
    if os.environ.get("MINECRAFT_JAR"):
        _JAR = os.environ["MINECRAFT_JAR"]
        return _JAR
    cache = os.path.expanduser("~/.gradle/caches/fabric-loom")
    names = ("minecraft-merged.jar", "minecraft-client.jar")
    found = []
    version = minecraft_version()
    if version:
        for name in names:
            found += glob.glob(os.path.join(cache, version, name))
    if not found:
        for name in names:
            found += glob.glob(os.path.join(cache, "*", name))
    if not found:
        sys.exit("no cached Minecraft jar found: build the mod once, "
                 "or pass a jar path as the first argument")
    _JAR = max(found, key=os.path.getmtime)
    return _JAR


def vanilla(name):
    """Read assets/minecraft/textures/<name> out of the vanilla jar."""
    with zipfile.ZipFile(find_jar()) as jar:
        return decode_png(jar.read("assets/minecraft/textures/" + name))


def decode_png(data):
    """Minimal PNG reader: no interlacing, every colour type and bit depth vanilla actually
    ships. Returns rows of RGBA tuples."""
    pos = 8
    idat = b""
    width = height = depth = ctype = None
    palette = trns = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNG not supported"
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (width * channels * depth + 7) // 8
    step = max(1, (channels * depth) // 8)
    raw = zlib.decompress(idat)
    out = bytearray(stride * height)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if filt == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = prev[i]
                c = prev[i - step] if i >= step else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    pixels = []
    if depth < 8:
        per = 8 // depth
        mask = (1 << depth) - 1
        for y in range(height):
            base = y * stride
            row = []
            for x in range(width):
                i = x * channels
                value = (out[base + i // per] >> (8 - depth * (i % per + 1))) & mask
                if ctype == 3:
                    r, g, b = palette[value * 3:value * 3 + 3]
                    a = trns[value] if trns and value < len(trns) else 255
                    row.append((r, g, b, a))
                else:
                    v = value * 255 // mask
                    row.append((v, v, v, 255))
            pixels.append(row)
        return pixels

    for y in range(height):
        base = y * stride
        row = []
        for x in range(width):
            i = base + x * channels
            if ctype == 6:
                row.append(tuple(out[i:i + 4]))
            elif ctype == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 255))
            else:
                r, g, b = palette[out[i] * 3:out[i] * 3 + 3]
                a = trns[out[i]] if trns and out[i] < len(trns) else 255
                row.append((r, g, b, a))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (os.path.relpath(path, HERE), width, height))


def scale(pixels, n):
    """Nearest neighbour only: these are pixel textures, never smooth them."""
    return [[px for px in row for _ in range(n)] for row in pixels for _ in range(n)]


def blank(size=16):
    return [[CLEAR] * size for _ in range(size)]


def put(sprite, x, y, px):
    if 0 <= y < len(sprite) and 0 <= x < len(sprite[0]):
        sprite[y][x] = px


def luma(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def tones(name, count):
    """The most common opaque colours of a vanilla texture, darkest first."""
    counts = Counter(px for row in vanilla(name) for px in row if px[3])
    return sorted((px for px, _ in counts.most_common(count)), key=luma)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3)) + (255,)


def recolour(name, ramp):
    """A vanilla sprite's shape in new colours: its distinct colours, ranked by brightness, each
    become the ramp colour at the same rank."""
    sprite = vanilla(name)
    shades = sorted({px for row in sprite for px in row if px[3]}, key=luma)
    lookup = {px: ramp[round(i * (len(ramp) - 1) / max(1, len(shades) - 1))]
              for i, px in enumerate(shades)}
    return [[lookup.get(px, CLEAR) if px[3] else CLEAR for px in row] for row in sprite]


class Palette:
    def __init__(self):
        # Carrot tops are vanilla's most leaf-like greens; wheat's are a seedling neon.
        leaf = tones("block/carrots_stage3.png", 8)
        greens = [px for px in leaf if px[1] > px[0] + 20]
        self.shade, self.stalk_dark, self.stalk, self.leaf, self.leaf_light = greens[:5]
        # Ripe wheat's straw, pulled toward the leaf, for the flowering tops and the cut fibre.
        straw = tones("item/wheat.png", 6)
        # A fan leaf is lit on one side and shaded on the other; with the two sides close in tone,
        # leaflets from the crop's crossing planes run together into one mass.
        self.leaf_shadow = mix(self.stalk, self.leaf, 0.3)
        self.leaf_lit = mix(self.leaf_light, straw[-1], 0.2)
        self.flower = mix(straw[-1], self.leaf_light, 0.35)
        self.flower_dark = mix(straw[-3], self.leaf, 0.45)
        self.fibre = [self.shade, self.stalk_dark, self.stalk, self.leaf,
                      mix(self.leaf, straw[-2], 0.5), mix(self.leaf_light, straw[-1], 0.6)]
        # Hemp seed is a grey-brown coat mottled darker: gravel, warmed by the straw.
        coat = tones("block/gravel.png", 8)
        self.seed = [mix(coat[0], straw[0], 0.5), mix(coat[2], straw[0], 0.25),
                     coat[4], mix(coat[-1], straw[-1], 0.2)]
        # A hemp leaf's underside is paler and greyer than its top, and its midrib paler still.
        self.underside_dark = mix(self.leaf_shadow, coat[-1], 0.3)
        self.underside = mix(self.leaf, coat[-1], 0.3)
        self.midrib = mix(self.leaf_lit, self.flower, 0.5)
        self.coat_light = coat[-1]
        # Pistils are the orange hairs threading a ripe bud: carrot's orange, dulled by straw.
        orange = [px for px in tones("block/carrots_stage3.png", 8) if px[0] > px[1]]
        self.pistil = mix(orange[-1], straw[0], 0.25)



# The plant

# The crop and wild hemp are built as models out of this one sheet, as cloud-kingdoms builds its
# beanstalk; generate_models.py reads where each part sits from SHEET. Each strain has its own
# leaflet, a blade pointed at the tip (top row) and shaded on one side of a pale midrib: indica's
# broad and dark, sativa's a narrow pale sliver, hybrid's between with serrated edges. Each has
# its own bud: indica's dense, sativa's airy and spiked, both threaded with orange pistils.
SHEET_SIZE = 32
SHEET = {
    "stalk": (0, 0, 2, 16),
    "petiole": (2, 0, 4, 16),
    "leaflet_indica": (4, 0, 8, 16),
    "under_indica": (8, 0, 12, 16),
    "leaflet_sativa": (12, 0, 16, 16),
    "under_sativa": (16, 0, 20, 16),
    "leaflet_hybrid": (20, 0, 24, 16),
    "under_hybrid": (24, 0, 28, 16),
    "buds_indica": (28, 0, 32, 8),
    "buds_sativa": (28, 8, 32, 16),
    "buds_hybrid": (0, 16, 4, 24),
    "seed_leaf": (4, 16, 8, 20),
    "cap": (8, 16, 12, 20),
    "pod_green": (12, 16, 16, 20),
    "pod_ripe": (16, 16, 20, 20),
}
LEAFLETS = {
    "indica": [
        ".ll.",
        "Lllm",
        "LLml",
        "LLml",
        "LLml",
        "LLml",
        "LLml",
        "LLml",
        "LLml",
        "LLml",
        ".Lml",
        ".Lml",
        ".Lm.",
        ".Lm.",
        "..m.",
        "..m.",
    ],
    "sativa": [
        "..l.",
        "..l.",
        ".Lm.",
        ".Lml",
        ".Lm.",
        ".Lml",
        ".Lm.",
        ".Lml",
        ".Lm.",
        ".Lml",
        ".Lm.",
        ".Lm.",
        "..m.",
        "..m.",
        "..m.",
        "..m.",
    ],
    "hybrid": [
        ".ll.",
        ".Ll.",
        "LLml",
        ".Lml",
        "LLml",
        "LLm.",
        "LLml",
        ".Lml",
        "LLml",
        "LLm.",
        "LLml",
        ".Lml",
        ".Lm.",
        ".Lm.",
        "..m.",
        "..m.",
    ],
}
# An untrimmed plant flowers small and seedy: sprigs of little pods, each a green bract, and once
# ripe the grey-brown seed showing through it.
PODS = {
    "pod_green": [
        ".lL.",
        "lLLL",
        "LLLd",
        ".Ld.",
    ],
    "pod_ripe": [
        ".lL.",
        "lsSL",
        "LSsd",
        ".Ld.",
    ],
}
# The first pair of leaves a seed puts up, round rather than fingered.
SEED_LEAF = [
    ".ll.",
    "Llll",
    "LLll",
    ".LL.",
]


def leaflet_colours(pal, strain):
    """(shaded, lit, midrib) for a strain's leaflet: indica dark, sativa pale, hybrid between."""
    if strain == "indica":
        return mix(pal.shade, pal.leaf_shadow, 0.5), pal.leaf, mix(pal.leaf, pal.midrib, 0.5)
    if strain == "sativa":
        return pal.leaf, mix(pal.leaf_lit, pal.flower, 0.2), pal.midrib
    return pal.leaf_shadow, pal.leaf_lit, pal.midrib


def buds(sprite, region, pal, rng, airy, pistils):
    """A bud's surface: flowers packed with a fleck of leaf and orange pistils. An airy one is
    gapped, so a box of it reads as spikes rather than a lump."""
    x0, y0, x1, y1 = region
    for y in range(y0, y1):
        for x in range(x0, x1):
            roll = rng.random()
            if airy and roll < 0.22:
                continue
            if roll < 0.22 + pistils:
                colour = pal.pistil
            elif roll < 0.5:
                colour = pal.flower_dark
            elif roll < 0.58:
                colour = pal.leaf_shadow
            else:
                colour = pal.flower
            put(sprite, x, y, colour)


def plant_sheet(pal):
    sprite = blank(SHEET_SIZE)
    x0, y0, _, y1 = SHEET["stalk"]
    for y in range(y0, y1):
        node = y % 4 == 3
        put(sprite, x0, y, pal.shade if node else pal.stalk_dark)
        put(sprite, x0 + 1, y, pal.stalk_dark if node else pal.stalk)
    x0, y0, _, y1 = SHEET["petiole"]
    for y in range(y0, y1):
        put(sprite, x0, y, pal.leaf_shadow)
        put(sprite, x0 + 1, y, pal.leaf)
    for strain, mask in LEAFLETS.items():
        shaded, lit, midrib = leaflet_colours(pal, strain)
        top = {"L": shaded, "l": lit, "m": midrib}
        under = {c: mix(v, pal.coat_light, 0.3) for c, v in top.items()}
        tx, ty = SHEET["leaflet_" + strain][:2]
        ux, uy = SHEET["under_" + strain][:2]
        for y, row in enumerate(mask):
            for x, ch in enumerate(row):
                if ch != ".":
                    put(sprite, tx + x, ty + y, top[ch])
                    put(sprite, ux + x, uy + y, under[ch])
    rng = random.Random(420)
    buds(sprite, SHEET["buds_indica"], pal, rng, airy=False, pistils=0.06)
    buds(sprite, SHEET["buds_sativa"], pal, rng, airy=True, pistils=0.12)
    buds(sprite, SHEET["buds_hybrid"], pal, rng, airy=False, pistils=0.09)
    x0, y0 = SHEET["seed_leaf"][:2]
    for y, row in enumerate(SEED_LEAF):
        for x, ch in enumerate(row):
            if ch != ".":
                put(sprite, x0 + x, y0 + y, {"L": pal.leaf_shadow, "l": pal.leaf_lit}[ch])
    for name, mask in PODS.items():
        key = {"l": pal.leaf_lit, "L": pal.leaf, "d": pal.leaf_shadow, "s": pal.seed[2], "S": pal.seed[1]}
        x0, y0 = SHEET[name][:2]
        for y, row in enumerate(mask):
            for x, ch in enumerate(row):
                if ch != ".":
                    put(sprite, x0 + x, y0 + y, key[ch])
    x0, y0, x1, y1 = SHEET["cap"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(sprite, x, y, pal.stalk if (x + y) % 3 else pal.stalk_dark)
    return sprite


# Items

# The bundle's stalks, upper left first: each two pixels wide along the diagonal x + y = c, from
# one x to another. Between neighbours is a line of shadow, so each stalk reads on its own, and
# further than BUNDLE_FAN from the tie the outer two lean a pixel further out.
BUNDLE = [(11, 1, 10), (14, 2, 12), (17, 4, 13)]
BUNDLE_FAN = 6


def hemp_bundle(pal):
    """Cut green stalks tied in a bundle, lying corner to corner as vanilla's sticks do: each lit
    along its upper edge and pale where it was cut, bound round the middle with a twist of their
    own fibre, and fanning apart toward both ends."""
    twine, twine_dark = mix(pal.seed[3], pal.flower, 0.3), mix(pal.seed[1], pal.flower_dark, 0.3)
    sprite = blank()
    for k, (c, first, last) in enumerate(BUNDLE):
        for x in range(first, last + 1):
            lean = (k - 1) if abs(2 * x - c) > BUNDLE_FAN else 0
            y = c - x + lean
            for dx, lit in ((0, True), (1, False)):
                if x == first:
                    shade = pal.fibre[5] if lit else pal.fibre[4]
                else:
                    shade = pal.leaf_lit if lit else pal.leaf
                put(sprite, x + dx, y, shade)
    for y in range(16):
        for x in range(16):
            if BUNDLE[0][0] <= x + y <= BUNDLE[-1][0] + 1 and -2 <= x - y <= 0:
                sprite[y][x] = twine if x - y < 0 else twine_dark
    outline(sprite, pal.shade)
    return sprite


ROPE_CORE = (5, 11)


def rope_braid(pal):
    """A hank of hemp laid up into one thick cord.

    Strands wound round a core six pixels wide - the width of the block model's own core - each a
    band running corner to corner: a lit edge where the strand turns toward you, its body, the
    shadow where it turns away, and the dark line of the lay between it and the next. Six rows to
    a turn, which divides into sixteen unevenly on purpose: stacked ropes read as one long lay
    rather than a repeat. The colour is wheat straw, not leaf - rope is retted fibre, dried.
    """
    left, right = ROPE_CORE
    straw = tones("item/wheat.png", 6)
    lit, body = straw[-1], straw[-2]
    shade = mix(straw[-4], pal.stalk, 0.25)
    lay = mix(straw[1], pal.shade, 0.35)

    sprite = blank()
    for y in range(16):
        for x in range(left, right):
            put(sprite, x, y, (lit, body, body, shade, shade, lay)[(y + x - left) % 6])
        put(sprite, left - 1, y, shade if (y + 2) % 6 else lay)
        put(sprite, right, y, shade if (y + 5) % 6 else lay)
        # A few fibres standing proud of the lay, as every handled rope has.
        if y % 7 == 3:
            put(sprite, left - 2, y, shade)
        if y % 7 == 6:
            put(sprite, right + 1, y, shade)
    return sprite


def line(x0, y0, x1, y1):
    """Every pixel from one end to the other, Bresenham's way."""
    points = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        points.append((x0, y0))
        if (x0, y0) == (x1, y1):
            return points
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


# A dried flower for each strain: its outline hand-placed, its calyxes shaded over it. # is bud,
# f frost on it, p a pistil and o a pistil's bright tip, g and G a sugar leaf's shaded and lit
# side, S the stem. Indica's is a dense round nug; sativa's a long spear; hybrid's between.
FLOWERS = {
    "indica": [
        "................",
        ".......o........",
        "......p..o......",
        ".....p##p.......",
        "....####p##.....",
        "...#########o...",
        "..###f####p##...",
        "..##p########...",
        ".g###########...",
        "..####f####p#G..",
        ".op####p####....",
        "...#########....",
        "....g#####G.....",
        "......S##.......",
        ".....S..........",
        "................",
    ],
    "sativa": [
        "........o.......",
        ".......p#.......",
        ".......##o......",
        "......##p.......",
        "......#f#G......",
        ".....p###.......",
        "....o####.......",
        "....g##p##......",
        ".....####p......",
        ".....##f##G.....",
        "....g#####......",
        "....op#p##......",
        "......###g......",
        ".......S........",
        "......S.........",
        "................",
    ],
    "hybrid": [
        "................",
        "........o.......",
        ".......p#.o.....",
        "......###p......",
        ".....##f###.....",
        "....o####p#.....",
        "....p#######....",
        "...g##p#####....",
        "....###f###p....",
        "....#########o..",
        "....g####p##G...",
        ".....#######....",
        "......####......",
        ".......S........",
        "......S.........",
        "................",
    ],
}
# Calyxes sit on an offset grid: pixels across, and down between rows.
CALYX_STEP = (3.0, 2.5)


def flower_ramp(pal, strain):
    """Shadow, dark, mid, light and frost for a dried flower: sage going to straw, sativa's
    paler, indica's shadows purpling as indica's do."""
    purple = tones("item/chorus_fruit.png", 6)
    frost = tones("item/sugar.png", 5)[-2]
    shadow, dark = mix(pal.shade, pal.leaf_shadow, 0.45), pal.leaf_shadow
    mid, light = mix(pal.leaf, pal.flower_dark, 0.55), mix(pal.flower_dark, pal.flower, 0.55)
    if strain == "indica":
        shadow, dark = mix(shadow, purple[1], 0.3), mix(dark, purple[3], 0.25)
    elif strain == "sativa":
        mid, light = mix(mid, pal.flower, 0.2), mix(light, pal.flower, 0.35)
    return shadow, dark, mid, light, mix(light, frost, 0.6)


def flower_item(pal, strain):
    """A strain's dried flower: a cluster of calyxes, each lit on its upper left and shadowed
    where the next sits over it, the whole lit the same way, frosted, and threaded with pistils
    that curl out past its edge."""
    mask = FLOWERS[strain]
    shadow, dark, mid, light, frost = flower_ramp(pal, strain)
    pistil_tip = tones("item/glow_berries.png", 6)[4]
    bud = [(x, y) for y, row in enumerate(mask) for x, ch in enumerate(row) if ch in "#fp"]
    xs, ys = [x for x, _ in bud], [y for _, y in bud]
    cx, cy = (min(xs) + max(xs) + 1) / 2, (min(ys) + max(ys) + 1) / 2
    w, h = (max(xs) - min(xs) + 1) / 2, (max(ys) - min(ys) + 1) / 2
    sprite = blank()
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            px, py = x + 0.5, y + 0.5
            if ch in "#fp":
                j = round((py - cy) / CALYX_STEP[1])
                off = CALYX_STEP[0] / 2 if j % 2 else 0.0
                i = round((px - cx - off) / CALYX_STEP[0])
                dx, dy = px - (cx + off + i * CALYX_STEP[0]), py - (cy + j * CALYX_STEP[1])
                lit = -(dx + dy) / 1.6 * 0.7 - ((px - cx) / w + (py - cy) / h) * 0.7
                if math.hypot(dx, dy) > 1.55:
                    colour = shadow if lit < 0.3 else dark
                elif lit > 0.45:
                    colour = light
                elif lit > -0.35:
                    colour = mid
                else:
                    colour = dark
                if ch == "f":
                    colour = frost
                elif ch == "p":
                    colour = pal.pistil
            elif ch == "o":
                colour = pistil_tip
            elif ch == "g":
                colour = pal.stalk_dark
            elif ch == "G":
                colour = pal.leaf
            elif ch == "S":
                colour = pal.stalk_dark
            else:
                continue
            put(sprite, x, y, colour)
    outline(sprite, mix(shadow, pal.shade, 0.5))
    return sprite


# A joint lies corner to corner, crutch at the lower left: where its axis starts, and its
# half-width at the crutch and at the far end of a whole one, in pixels. It widens only a little:
# any more and the far end reads as a bulb.
JOINT_FROM = (3.2, 12.8)
JOINT_HALF = (0.95, 1.25)
JOINT_LENGTH = 10.5
JOINT_CRUTCH = 2.3
# How much of a joint is left at each of the looks its item switches between as it burns: unlit,
# lit, half gone, a stub. Pixels along the axis from the crutch end.
JOINT_LEFT = {"joint": 10.5, "joint_lit": 9.7, "joint_half": 6.6, "joint_stub": 3.6}
# Pixels of a lit joint's end that are ash and ember.
JOINT_EMBER = 1.2


def joint_item(pal, strain, look):
    """A slim roll of paper, a touch wider toward its far end: a pale crutch at the near end, and
    at the far end either a twist with the strain's flower showing or, once lit, ash over a
    glowing ember that creeps back toward the crutch as it burns."""
    paper = tones("item/paper.png", 6)
    light, mid, shadow = paper[-1], paper[len(paper) // 2], paper[1]
    crutch = mix(pal.fibre[-1], light, 0.5)
    embers = tones("item/blaze_powder.png", 6)
    ember, glow = embers[len(embers) // 2], embers[-1]
    ash = pal.coat_light
    left = JOINT_LEFT[look]
    lit = look != "joint"
    root = math.sqrt(0.5)
    sprite = blank()
    body = {}
    for y in range(16):
        for x in range(16):
            rx, ry = x + 0.5 - JOINT_FROM[0], y + 0.5 - JOINT_FROM[1]
            along = (rx - ry) * root
            across = (rx + ry) * root
            half = JOINT_HALF[0] + (JOINT_HALF[1] - JOINT_HALF[0]) * min(along, JOINT_LENGTH) / JOINT_LENGTH
            side = across / half
            if 0.0 < along < left and abs(side) <= 1.0:
                body[(x, y)] = (along, side)
                if along < 0.8:
                    colour = mix(crutch, shadow, 0.55)
                elif along < JOINT_CRUTCH:
                    colour = mix(crutch, shadow, 0.35) if side > 0.3 else crutch
                elif side < -0.35:
                    colour = light
                elif side > 0.4:
                    colour = mix(mid, shadow, 0.4)
                else:
                    colour = mix(light, mid, 0.5)
            elif not lit and left <= along < left + 1.4 and abs(across) <= 0.3 + 0.6 * (1 - (along - left) / 1.4):
                colour = light
            else:
                continue
            sprite[y][x] = colour
    if lit:
        # The burning end, drawn from the pixels actually there so it shows at every length: ash
        # over the upper side, ember under it, and the very end glowing.
        end = max(along for along, _ in body.values())
        for (x, y), (along, side) in body.items():
            if along > end - JOINT_EMBER:
                sprite[y][x] = ash if side < -0.2 else ember
        x, y = max(body, key=lambda p: body[p][0])
        sprite[y][x] = glow
    else:
        tip = JOINT_FROM[0] + (left - 0.4) * root, JOINT_FROM[1] - (left - 0.4) * root
        put(sprite, int(tip[0]), int(tip[1]), leaflet_colours(pal, strain)[1])
    outline(sprite, mix(shadow, pal.shade, 0.5))
    return sprite


# A brownie is a slab seen from the front and a little above: its front face's corner, size, and
# how deep it runs back, each step of depth a pixel up and right.
BROWNIE_AT = (2, 7)
BROWNIE_SIZE = (10, 5)
BROWNIE_DEPTH = 3


def brownie(pal):
    """A square of brownie: a glossy crust in a cookie's baked browns cracked across its top, a
    dense crumb in cocoa's browns on its faces, and a fleck or two of green from what went into
    it. Kept a shade lighter than a real one, or it is a dark blot in a slot."""
    darkest, dark, mid, light = tones("item/cocoa_beans.png", 4)
    baked = tones("item/cookie.png", 7)
    crust, crust_lit, crack = baked[3], baked[4], baked[2]
    rng = random.Random(21)
    x0, y0 = BROWNIE_AT
    width, height = BROWNIE_SIZE
    sprite = blank()
    for d in range(1, BROWNIE_DEPTH + 1):
        for y in range(y0 - d + 1, y0 + height - d):
            put(sprite, x0 + width - 1 + d, y, dark)
        for x in range(x0 + d, x0 + width + d):
            y = y0 - d
            if (x * 3 + y * 5) % 7 == 0:
                colour = crack
            else:
                colour = crust_lit if rng.random() < 0.3 else crust
            put(sprite, x, y, colour)
    for y in range(y0, y0 + height):
        for x in range(x0, x0 + width):
            speck = rng.random()
            colour = dark if speck < 0.2 else (light if speck > 0.88 else mid)
            put(sprite, x, y, dark if y == y0 + height - 1 else colour)
    for x, y in ((4, 9), (9, 5)):
        put(sprite, x, y, pal.flower_dark)
    outline(sprite, darkest)
    return sprite


def hemp_seeds(pal, strain):
    """Beetroot seeds are round, and so is hemp seed: the shape, in hemp's grey-brown; indica's
    darker, sativa's paler, hybrid's the plain coat between them."""
    toward = {"indica": pal.shade, "sativa": pal.coat_light, "hybrid": None}[strain]
    ramp = pal.seed if toward is None else [mix(c, toward, 0.3) for c in pal.seed]
    return recolour("item/beetroot_seeds.png", ramp)


# The icon

def outline(sprite, colour):
    """Darken the clear pixels bordering the art, as vanilla rims an item."""
    edge = [(x, y) for y in range(16) for x in range(16) if sprite[y][x] == CLEAR and any(
        0 <= x + dx < 16 and 0 <= y + dy < 16 and sprite[y + dy][x + dx] != CLEAR
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in edge:
        sprite[y][x] = colour


# At icon size the fan can be drawn rather than placed. Seven fingers from one point, the middle
# longest: (angle from upright, length).
FINGERS = [(0, 10.5), (-30, 9.0), (30, 9.0), (-60, 7.0), (60, 7.0), (-92, 4.5), (92, 4.5)]


def fan_leaf(pal):
    """A single fan leaf, drawn at 16x16 and scaled like every icon in the suite. Each finger is
    a narrow lance with a pale midrib; a darker rim keeps neighbouring fingers apart."""
    origin = (7.5, 12.0)
    body = {}
    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5, y + 0.5
            for angle, length in FINGERS:
                a = math.radians(angle)
                ux, uy = math.sin(a), -math.cos(a)
                along = (cx - origin[0]) * ux + (cy - origin[1]) * uy
                across = abs((cx - origin[0]) * -uy + (cy - origin[1]) * ux)
                if not 0.0 < along < length:
                    continue
                half = 0.2 + 1.0 * math.sin(math.pi * (along / length) ** 0.8)
                if across <= half:
                    vein = across < 0.5 and along > 1.0
                    if body.get((x, y)) != "vein":
                        body[(x, y)] = "vein" if vein else "leaf"
    sprite = blank()
    for (x, y), part in body.items():
        sprite[y][x] = pal.leaf_light if part == "vein" else pal.leaf
    outline(sprite, pal.shade)
    for y in range(12, 16):
        put(sprite, 7, y, pal.stalk_dark)
        put(sprite, 8, y, pal.shade)
    return sprite


if __name__ == "__main__":
    pal = Palette()
    write_png(os.path.join(ASSETS, "textures/block/hemp_plant.png"), plant_sheet(pal))
    write_png(os.path.join(ASSETS, "textures/item/hemp.png"), hemp_bundle(pal))
    write_png(os.path.join(ASSETS, "textures/block/rope.png"), rope_braid(pal))
    for strain in ("indica", "sativa", "hybrid"):
        write_png(os.path.join(ASSETS, "textures/item/%s_seeds.png" % strain), hemp_seeds(pal, strain))
        write_png(os.path.join(ASSETS, "textures/item/%s_flower.png" % strain), flower_item(pal, strain))
        for look in JOINT_LEFT:
            write_png(os.path.join(ASSETS, "textures/item/%s_%s.png" % (strain, look)), joint_item(pal, strain, look))
    write_png(os.path.join(ASSETS, "textures/item/brownie.png"), brownie(pal))
    icon = scale(fan_leaf(pal), 8)
    assert len(icon) == 128 and len(icon[0]) == 128, "mod menu icons are 128x128"
    write_png(os.path.join(ASSETS, "icon.png"), icon)
