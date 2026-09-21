# Hemp Craft

A Minecraft Fabric mod that adds hemp: a fibre crop you grow, shape and breed. Indica, sativa and
the hybrid of the two, grown from seed on farmland through their own days and nights, and cut for
the fibre that makes rope, string and paper.

## Screenshots

![A bed of hemp grown lanky and gone to seed, rows of it standing ready to cut](field.png)
![Wild hemp in a patch on open grass](wild.png)
![Hemp rope hanging from a beam at the lip of a bank, down the face to the ground](rope.png)

## The Three Strains

**Indica** is short and bushy. Its nodes crowd close, its branches spread wide, its leaves are
broad and dark, and its buds dense.

**Sativa** is tall and lanky. It grows faster, stretches far more once it flowers, and climbs with
long steep branches, narrow pale leaves and long airy buds.

**Hybrid** sits between them. It first comes of indica and sativa flowering together; after that,
any plant crossed with it gives hybrid seed too.

## Finding Seed

**Wild hemp** grows in patches on grass: indica in meadows, cherry groves and windswept hills and
forests; sativa in plains, sunflower plains, savannas and sparse jungle. Breaking it gives two or
three hemp and a seed of its strain, sometimes two.

**Wandering traders** sometimes carry indica or sativa seeds (one emerald each) or a bundle of three
hemp (one emerald). They sit in the trader's common pool, so not every trader has them.

## Growing It

**Planting.** Seeds on farmland come up as a clump of seedlings. Left to itself, a clump grows
lanky, the way hemp grows wild: a tangle of thin stems in its strain's manner (indica's low and
crowded, sativa's tall and whippy, two blocks high) that flowers small and seedy, in sprigs of pods
up the tops of its stems. At the last stage the pods explode into growth.

**Light and dark.** A single stalk counts its age in cycles, each a spell of real light followed by
a spell of real darkness, a minute or more of each. A natural day and night make one cycle. Ten
cycles of growth, and it flowers for five more.

- Light is the sun, or a lamp: a plant in an underground room under lamps gets its days.
- Dark is an open night, or a room with the lamps off. Some light at night is fine: a torch four
  blocks off, or a lamp five, still leaves the plant its night. Any nearer spoils it, and that
  cycle does not count.
- So a plant kept lit at night never flowers. It goes on growing on its light alone: steadily for
  the first ten days' worth, then slower and slower, as big as you have the patience for. Lamps on a
  daylight sensor let you choose when it flips.
- A lamp on a fast timer makes a cycle every couple of minutes. That forces a plant into flower
  while it is still small: a quick, short plant where a natural season grows a big one.
- Sleeping through the night counts as the night. Time the plant's chunk spent unloaded does not.
  A plant grows under lamps whether or not the world's day moves.
- None of this reaches a clump left to itself. A crop counts no cycles and waits on no night: it
  runs on its days of light and goes to seed on its own time, under a lamp all night or out in the
  open, however the field is lit. It still wants light to grow by - nothing grows in the dark - it
  simply never waits for the dark. Timing the nights is the business of a plant somebody raised.

**Flowering.** The first day and a half the plant stretches, sativa most of all. Then buds set,
small and spiky, and form and fill out over the rest of the five days while the plant keeps
growing slowly. Late in flower it drops its lowest leaves.

**Shape.** Every single-stalked plant grows its own shape from its seed: a main stalk tapering to
its tip, leaves in opposite pairs each turned a quarter from the last, and a branch from every
leaf's joint, angled out and curling up, longest at the bottom and none at the tip, so the plant
fills out into a cone as it gains height. Every branch flowers at its tip. Branches spread about a
block each way on a grown plant, so plant them a few apart to see each one whole; only the stalk
itself needs the space above it clear.

**Pruning.** Cut a piece off a plant and it stays cut; the plant does not grow it back.

**Bone meal.** Use it on any part of a plant to hurry it along: a day's growth while it is growing,
a day of flowering once it flowers. It never flips a plant into flower; only nights do that. A ripe
plant, or a clump gone to seed, takes no more.

## Harvest and Breeding

Break a plant's root, the block on the farmland, to harvest all of it. What it gives depends on how
it was grown.

**A clump grown lanky** is the fibre and seed crop. Its raw hemp, the fibre for string and paper,
peaks the stage before it goes to seed, while its pods are still small: that is the most fibre hemp
gives. Let it go to seed and the fibre falls away, but it gives seed by the handful.

**A single stalk**, the plant grown on rather than cut as a crop, gives a stalk's worth of fibre
and seed only rarely.

**Wild hemp** gives a little fibre and a seed or two of its strain. Any plant cut before it
flowered gives back the seed it came from.

A plant that flowers within twelve blocks of another strain in flower at the same time catches its
pollen, and half the seed it drops is hybrid: an indica beside a sativa, or either beside a hybrid.
Hybrid seed breeds hybrid, whatever it grew beside.

With [useful-hoe](https://github.com/fatlard1993/useful-hoe) installed, a hoe sweeps a field the
same way a hand does, root and all: it takes the plants that are done, ripe or gone to seed, and
passes over the ones still growing - a clump at its most fibre among them, which is yours to judge
and cut yourself.

Hemp seeds feed chickens and parrots, and seeds and hemp both go in a composter.

## On the Block Tip

With [block-tip](https://github.com/fatlard1993/block-tip) installed, looking at any piece of a plant
names its strain and says where it is: which day it is on, which day of flowering, whether it has
caught another strain's pollen, when it is at its most fibre, when it has gone to seed, and when it
is ripe. The green bar along the bottom of the card fills as it grows, the way it does for wheat,
and is full when the plant is ready to cut; any piece of a plant reads the same, since they are all
the one plant. A plant that has gone two days without a real night says so, which is how you find the
lamp keeping it from flowering.

![Block-tip lines for seedlings, a growing plant, a plant held growing by a lamp, and a ripe one](img5.png)

## Crafting

| Ingredients | Result |
|---|---|
| 4 hemp, in a square | 3 paper |
| 3 hemp, in a row | 2 string |
| 8 hemp, in a ring | a lead |
| 6 hemp, two by three | 3 rope |

The paper square fits the inventory's own crafting grid; the rest need a crafting table. They all
unlock the first time you hold hemp.

**Rope** hangs from whatever is above it, one thick braided cord down the middle of the block. You
walk through it and you climb it, so a coil dropped down a shaft is a way back up. It hangs the way
a chain does: from a solid face or from the rope above, and it comes down when its anchor does.

## Where It Grows Wild

The biomes are the tags `hemp-craft-justfatlard:grows_wild_indica` and `grows_wild_sativa`. A
datapack can write `data/hemp-craft-justfatlard/tags/worldgen/biome/grows_wild_<strain>.json` to add
biomes, another mod's included; with `"replace": true` its list replaces this one instead of joining
it. The change applies from the next server start, in chunks generated after it.

## Pandorical

Hemp Craft runs server-side, and Pandorical is required: the server will not load this mod without it.
Every piece of the plant and every item is mirrored into Pandorical's content registry, and their
textures and models arrive through Pandorical's content sync, so a player needs Pandorical on their
client to join.

## Development

Installing, how a plant is modelled, and the art pipeline are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
