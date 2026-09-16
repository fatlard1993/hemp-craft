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

Thinning is a secret players find for themselves: shears on a clump from its third stage
(`HempCropBlock.TRIM_FROM`) until it grows lanky swap it for a single `hemp_root` that carries on the
clump's record. The record keeps its age at thinning (`ThinnedAt`), so the single starts at the kept
seedling's height and catches up with a plant grown single from seed over the next few days
(`PlantShape.height`); a plant saved without it is taken as never thinned. The README only hints at it, and `HempPlant.status` never tells a clump from a
thinned plant or mentions shears; keep both that way.

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

A joint's puff is a vanilla eat as far as any client knows (`JointItem`): food and a use
animation are what Pandorical tells a client's stand-in about an item, so the joint carries an
always-edible food of no nutrition and a twelve-second eating time, and the server ends each puff
after two seconds, before a client would start its chewing bob, its eating sounds and crumbs, or
eat it. Lit is damaged, and the item definition picks its look by damage, so the burning down needs
nothing on the client. While in use it picks a puff model instead (`joint_models` in the model
script), whose first-person pose lays the joint at the lips under vanilla's eating transform; a
handheld pose there would stand it upright in front of the face.

The smoke off the lit end is sent to each player separately, because each sees the joint somewhere
else: the smoker in first person, everyone else in the hand of the player model. `JointItem.HELD`,
`PUFF` and `MODEL` are where the crutch and lit end sit in each, worked out from vanilla's hand
transforms and the item models' display transforms; change a display transform or the joint sprite
and they move.

Bone meal works on every piece of a plant (`HempPartBlock` is `BonemealableBlock`) and feeds the
root's record. A client's stand-in is not a bone-mealable block, so it draws no sparkles; the server
sends them.

Block-tip is optional: `integration/HempTips` reads `HempPlant.status` and loads only when block-tip
is installed. For a dev server that shows the tips, drop block-tip's jar into `run/mods`.

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
