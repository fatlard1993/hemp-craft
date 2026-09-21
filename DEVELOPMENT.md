# Hemp Craft - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients
need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API)
and `fabric.mod.json` (Java).

## How a plant works

A plant is many blocks with one record. The root, on the farmland, keeps a `HempPlant` block entity:
its genome, its light and dark clock, whether it is flowering, the pollen it has caught, and which
blocks it has placed or had pruned. Its strain and its form (clump, lanky, or thinned) are the root
block's own state. Every second the plant samples the light, advances its clock, and asks
`PlantShape` what it should look like now; `PlantShape` answers from the record alone, so the same
plant at the same age is always the same shape, and a harvest counts what that shape is made of.

The world only sees ordinary blocks: `hemp_root` and a column of `hemp_stalk` for a thinned plant,
`hemp_crop` for a clump or lanky planting and `hemp_stems` above it, `wild_hemp` on grass. A stalk
block knows its place in the taper and which of four ways the genome set it; its branches are in its
models, so a plant is only ever one block wide in the world. Each block stands only while what holds
it up stands, so breaking the root takes the whole plant. A plant only grows into air or into blocks
it placed itself, so two plants never fight over a block.

There are two clocks, and which one a plant runs on is its form. A single stalk runs on cycles: a
spell of light and a spell of dark, ten of them and it flowers for five more, which is what the
lamp tricks play with. A clump nobody thinned runs on `vegLight` alone - days of light, no cycles,
no nights - and `PlantShape.clumpBuds` reads its seed stage straight off those days, so a crop
standing in a lit field still goes to seed. `credit` is where the two part: an untrimmed plant is
never flipped into flower, and one saved as flowering (from before this was so) has the flag
dropped and its days resume. It still counts cycles while it is a clump, so a clump thinned young
hands the plant it becomes the days it has already lived. Its pollen window is its pods setting
rather than its flowering, which is `HempPlant.shedding`.

Thinning - what a clump does under shears while it is young - is the one thing a player is meant to
find rather than read, so it is written down beside the code that does it (`HempCropBlock`) and
nowhere else. The readme only hints, and `HempPlant.status` never tells a clump from a thinned plant
or mentions shears; keep all three that way.

## Models and art

Each block's look is assembled from parts by a multipart blockstate, the way cloud-kingdoms builds
its beanstalk: stalk quarters, and at each of the strain's nodes a pair of fan leaves with a side
branch from each leaf's joint; a crown or a flowering cola at the top; buds at the nodes and along
and at the tips of the branches; seedling clumps and lanky stems. The leafy part of a node and its
bud part work their branches out from the same seed, so buds always sit on their branch.
`generate_models.py` writes all of them; each strain's traits (node spacing, blade count, leaf
breadth and reach, bud shape, branch length, angle and curl) are tables at its top. Blades and
branches are tilted and turned with the model format's x/y/z element rotation, so the models need a
game new enough to read it. Branches reach past their own block, as a model may, but no element's
corners may pass -16 or 32 before it is turned: the script lays an element that would the other way
round, caps how high a branch tip climbs, and nudges an overrunning bud back inside.

Wild hemp is placed by its own feature (`worldgen/WildHempFeature`), which sets the base and its
stems in one go so both take the same variant; an untrimmed crop's two blocks share one from its
genome for the same reason. Their stems are laid out per strain and variant in `LANKY` and
`lanky_layout` in the model script.

Bone meal works on every piece of a plant (`HempPartBlock` is `BonemealableBlock`) and feeds the
root's record. A client's stand-in is not a bone-mealable block, so it draws no sparkles; the server
sends them.

Block-tip is optional: `integration/HempTips` reads `HempPlant.status` for the card's line and
`HempPlant.grown` for its growth bar, and loads only when block-tip is installed. The bar is a
fraction of the way from sown to ready, which is gone to seed for a crop and ripe for a plant
somebody raised; block-tip reads every other crop's off an age property, and hemp has none to read,
so it answers through `BlockTipApi.growth`. That hook landed in block-tip 1.1.0, which is why
`fabric.mod.json` carries a `breaks` floor as well as the suggestion. For a dev server that shows
the tips, drop block-tip's jar into `run/mods`.

Useful-hoe is optional too, and takes no code: `data/useful-hoe/tags/block/harvested_whole.json`
puts `hemp_crop` and `hemp_root` in the tag of crops it takes whole, and it reads a plant's
readiness off `isValidBonemealTarget`, which `HempPlant.canFeed` makes false exactly when a plant is
ripe or a clump has gone to seed. So the hoe harvests on the same terms bone meal stops working on,
and a clump at its most fibre it leaves standing. The tag is inert when useful-hoe is absent.

`generate_textures.py` draws the 32x32 sheet those parts are cut from, and owns its layout, which the
model script imports. It also draws the item sprites and the icon, every colour read out of the
vanilla jar. Both scripts are deterministic; re-run them after a Minecraft version bump, textures
first.

A dev server pauses once it has been empty a minute, and a paused server ticks no block entities:
set `pause-when-empty-seconds=0` in `run/server.properties` before testing plants with no client
connected, or they will sit still.

Test against a client without this mod installed, one carrying only Pandorical (`./gradlew runClient
-x jar -PquickJoin=localhost:25565` in ../pandorical does it). A dev client with the mod on its
classpath builds the real blocks rather than Pandorical's stand-ins and reads the mod's own assets,
so it cannot see a stand-in that fails to build.
