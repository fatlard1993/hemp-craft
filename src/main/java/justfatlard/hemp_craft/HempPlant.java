package justfatlard.hemp_craft;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One hemp plant: the record its root keeps, and the clock it grows by.
 *
 * <p>The plant counts days the way a real one does, by light and dark. A cycle is a stretch of
 * real light followed by real darkness; lamps left burning at night spoil the dark half, and the
 * cycle does not count. Ten cycles of vegetative growth and it flowers for five more. Kept from
 * the dark, it never flips: it goes on growing on its daylight alone, ever slower, as big as
 * patience allows. What it looks like at any moment is {@link PlantShape}'s to say.
 */
public class HempPlant extends BlockEntity {

	private static final int SAMPLE_TICKS = 20;
	/** Light this bright counts as day, whether the sun's or a lamp's. */
	private static final int LIGHT_LEVEL = 12;
	/**
	 * Light this dim counts as night: an open sky after sunset, a room with the lamps off, or
	 * either with some light spilling in. A lamp or glowstone within four blocks keeps a plant from
	 * its night, and a torch within three; the torches a farm is lit by, a few blocks off, do not.
	 */
	private static final int DARK_LEVEL = 10;
	/**
	 * A cycle is a spell of light followed by a spell of dark, each at least this long: about a
	 * minute. A natural day and night make one, whatever their length; a lamp on a fast timer makes
	 * one every couple of minutes, which is how a small plant is forced to flower while it is small.
	 */
	private static final int CYCLE_LIGHT = 1200;
	private static final int CYCLE_DARK = 1200;
	/** Two days' light with no night between: a plant being held growing, and worth saying so. */
	private static final long HELD_LIGHT = 24000;
	/** An untrimmed plant's fibre per block, by bud stage: most the stage before it goes to seed. */
	private static final int[] LANKY_FIBRE = {3, 4, 7, 4};
	/** Ticks of light that make one day of vegetative growth. */
	private static final float LIGHT_PER_DAY = 10000.0F;
	/** A span of the clock longer than this passing between samples was skipped, not lived. */
	private static final int DAY_TICKS = 24000;
	private static final int DAYTIME_TICKS = 12000;

	static final int VEG_CYCLES = 10;
	static final int FLOWER_CYCLES = 5;
	/** Days of light a seedling clump waits for shears before it grows lanky. */
	static final float CLUSTER_DAYS = 3.2F;

	long genome;
	int lightTicks;
	int darkTicks;
	long vegLight;
	int cycles;
	int flowerCycles;
	boolean flowering;
	/** Strains this plant has caught pollen from while flowering, as bits of their ordinals. */
	int pollen;
	/**
	 * Light taken since the last cycle, uncapped: a plant that keeps getting days and never a
	 * night is one being held growing, and the only way to tell that to a player.
	 */
	long lightSinceCycle;
	/** Its vegetative light when it was thinned from a clump, or -1 if it never was. */
	long thinnedAt = -1;
	/** Blocks this plant has placed, relative to its root, as packed positions. */
	final LongOpenHashSet mine = new LongOpenHashSet();
	/** Blocks a player cut away, which it will not grow back. */
	final LongOpenHashSet pruned = new LongOpenHashSet();

	private long lastGameTime = -1;
	private long lastClock;

	public HempPlant(BlockPos pos, BlockState state) {
		super(Hemp.PLANT, pos, state);
	}

	public Strain strain() {
		return getBlockState().getValue(HempProps.STRAIN);
	}

	/** Days of light grown while vegetative; frozen once the plant flowers. */
	float vegDays() {
		return vegLight / LIGHT_PER_DAY;
	}

	/** Days of light it had grown when it was thinned from a clump, or -1 if it never was. */
	float thinnedDays() {
		return thinnedAt < 0 ? -1.0F : thinnedAt / LIGHT_PER_DAY;
	}

	/** Flowering cycles done, with the one under way counted in part. */
	float flowerDays() {
		if (!flowering) return 0.0F;
		float partial = Math.min(1.0F, Math.min(lightTicks / (float) CYCLE_LIGHT, darkTicks / (float) CYCLE_DARK));
		return Math.min(FLOWER_CYCLES, flowerCycles + partial);
	}

	/** How strongly this one grows against the rest of its strain, from 0.9 to 1.1. */
	float vigor() {
		return 0.9F + 0.2F * ((genome >>> 8) & 0xFF) / 255.0F;
	}

	void sow(long genome) {
		if (this.genome == 0) {
			this.genome = genome == 0 ? 1 : genome;
			setChanged();
		}
	}

	void serverTick(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel server)) return;
		if (Math.floorMod(server.getGameTime() + pos.asLong(), SAMPLE_TICKS) != 0) return;
		if (genome == 0) sow(server.getRandom().nextLong());
		sample(server, pos);
		if (flowering && flowerDays() < FLOWER_CYCLES) pollen |= Pollen.shed(server, pos, strain());
		grow(server, pos);
		setChanged();
	}

	/**
	 * Credit the time since the last look to light, dark, or neither. Ticks the plant lived are
	 * judged by the light it sees now, so it grows under lamps whether or not the sun moves. Time
	 * the chunk spent unloaded is not credited: the plant was not there to see it, as no crop grows
	 * while unloaded. Clock the world skipped, as when everyone sleeps, is judged by what the plant
	 * would have seen.
	 */
	private void sample(ServerLevel level, BlockPos pos) {
		long game = level.getGameTime();
		long clock = level.getDefaultClockTime();
		boolean fresh = lastGameTime < 0 || game - lastGameTime > SAMPLE_TICKS * 3L;
		long lived = game - lastGameTime;
		long skipped = clock - lastClock - lived;
		long from = lastClock + lived;
		lastGameTime = game;
		lastClock = clock;
		if (fresh || lived <= 0) return;

		int seen = level.getRawBrightness(pos, level.getSkyDarken());
		credit(seen >= LIGHT_LEVEL ? lived : 0, seen <= DARK_LEVEL ? lived : 0);
		if (skipped <= SAMPLE_TICKS) return;

		int block = level.getBrightness(LightLayer.BLOCK, pos);
		int sky = level.getBrightness(LightLayer.SKY, pos);
		long day = daytimeWithin(from, skipped);
		long night = skipped - day;
		int byDay = Math.max(block, sky);
		long light = (byDay >= LIGHT_LEVEL ? day : 0) + (block >= LIGHT_LEVEL ? night : 0);
		long dark = (byDay <= DARK_LEVEL ? day : 0) + (block <= DARK_LEVEL ? night : 0);
		credit(light, dark);
	}

	/** Ticks of daytime in the span of clock from {@code from} lasting {@code length}. */
	static long daytimeWithin(long from, long length) {
		long total = (length / DAY_TICKS) * DAYTIME_TICKS;
		long start = Math.floorMod(from, DAY_TICKS);
		long rest = length % DAY_TICKS;
		long end = start + rest;
		total += overlap(start, end, 0, DAYTIME_TICKS) + overlap(start, end, DAY_TICKS, DAY_TICKS + DAYTIME_TICKS);
		return total;
	}

	private static long overlap(long a0, long a1, long b0, long b1) {
		return Math.max(0, Math.min(a1, b1) - Math.max(a0, b0));
	}

	private void credit(long light, long dark) {
		if (!flowering) vegLight += light;
		lightSinceCycle += light;
		lightTicks = (int) Math.min(Integer.MAX_VALUE / 2, lightTicks + light);
		darkTicks = (int) Math.min(Integer.MAX_VALUE / 2, darkTicks + dark);
		// A cycle is a day and a night, each whole: what is left over of either starts nothing, or
		// the tail of one night and the start of the next would count as a second.
		if (lightTicks >= CYCLE_LIGHT && darkTicks >= CYCLE_DARK) {
			lightTicks = 0;
			darkTicks = 0;
			lightSinceCycle = 0;
			if (flowering) {
				flowerCycles = Math.min(FLOWER_CYCLES, flowerCycles + 1);
			} else if (++cycles >= VEG_CYCLES) {
				flowering = true;
			}
		}
		lightTicks = Math.min(lightTicks, CYCLE_LIGHT);
		// Dark comes only after light: a dark spell with no day before it is not the night of a
		// cycle, or a plant left in a dark room would bank its nights against the next lamp.
		if (lightTicks < CYCLE_LIGHT) darkTicks = 0;
		darkTicks = Math.min(darkTicks, CYCLE_DARK);
	}

	/** Bring the blocks in the world into line with what the plant is now. */
	private void grow(ServerLevel level, BlockPos pos) {
		List<PlantShape.Cell> cells = PlantShape.of(this);
		boolean[] standing = new boolean[cells.size()];
		for (int k = 0; k < cells.size(); k++) {
			PlantShape.Cell cell = cells.get(k);
			long rel = BlockPos.asLong(cell.dx(), cell.dy(), cell.dz());
			if (k > 0 && (pruned.contains(rel) || cell.parent() < 0 || !standing[cell.parent()])) continue;
			BlockPos at = pos.offset(cell.dx(), cell.dy(), cell.dz());
			if (k > 0 && !level.isLoaded(at)) continue;
			BlockState now = level.getBlockState(at);
			if (now == cell.state()) {
				standing[k] = true;
				continue;
			}
			boolean ours = k == 0 || (mine.contains(rel) && now.getBlock() instanceof HempPartBlock);
			if (ours || (now.canBeReplaced() && now.getFluidState().isEmpty())) {
				level.setBlock(at, cell.state(), Block.UPDATE_ALL);
				if (k > 0) mine.add(rel);
				standing[k] = true;
			}
		}
	}

	/**
	 * Whether bone meal would do anything: it hurries a plant along its days, not past its end, so
	 * not a ripe plant or an untrimmed one gone to seed.
	 */
	boolean canFeed() {
		if (!flowering) return true;
		if (getBlockState().getBlock() instanceof HempCropBlock) return PlantShape.budStage(this) < 3;
		return flowerCycles < FLOWER_CYCLES;
	}

	/**
	 * A dose of bone meal: a day's growth while vegetative, a day of flowering once in flower. It
	 * feeds growth, not the light and dark that make a cycle, so it never flips a plant into flower.
	 */
	void feed(ServerLevel level) {
		if (flowering) {
			flowerCycles = Math.min(FLOWER_CYCLES, flowerCycles + 1);
		} else {
			vegLight += (long) LIGHT_PER_DAY;
		}
		grow(level, worldPosition);
		setChanged();
	}

	/** Remember that a player cut this piece away. */
	void prune(BlockPos at) {
		long rel = BlockPos.asLong(at.getX() - worldPosition.getX(), at.getY() - worldPosition.getY(),
			at.getZ() - worldPosition.getZ());
		if (rel == BlockPos.asLong(0, 0, 0)) return;
		pruned.add(rel);
		mine.remove(rel);
		setChanged();
	}

	/**
	 * What the plant yields, by how it was grown.
	 *
	 * <p>Left untrimmed, hemp is a fibre and seed crop. Its stems give the most fibre the stage
	 * before it goes to seed, while the pods are still small; then the pods swell and take the
	 * stalks' strength, and it gives seed by the handful instead. It almost never gives a flower.
	 * Thinned, it grows for its flowers, as many as its size and ripeness carry, a little fibre
	 * from its stalk, and seed only rarely, more if it caught pollen. Cut before it flowered, any plant gives back the
	 * seed it came from. Seed from a plant that caught another strain's pollen is half hybrid.
	 */
	List<ItemStack> harvest(RandomSource random) {
		Strain strain = strain();
		if (isSeedling()) return List.of(new ItemStack(strain.seeds()));

		int blocks = 0;
		List<PlantShape.Cell> cells = PlantShape.of(this);
		for (int k = 0; k < cells.size(); k++) {
			PlantShape.Cell cell = cells.get(k);
			if (k > 0 && !mine.contains(BlockPos.asLong(cell.dx(), cell.dy(), cell.dz()))) continue;
			blocks++;
		}

		boolean crossed = strain != Strain.HYBRID && (pollen & ~(1 << strain.ordinal())) != 0;
		int stage = PlantShape.budStage(this);
		int fibre;
		int seeds;
		int flowers;
		if (getBlockState().getBlock() instanceof HempCropBlock) {
			fibre = blocks * LANKY_FIBRE[stage];
			seeds = stage == 3 ? 4 + 5 * blocks : stage == 2 ? blocks : 1;
			flowers = stage >= 2 && random.nextFloat() < 0.04F ? 1 : 0;
		} else {
			fibre = blocks;
			// A bud at every branch tip: the more plant, the more flower. A small plant forced into
			// flower early flowers small.
			flowers = stage == 0 ? 0 : Math.max(1, Math.round(stage / 3.0F * PlantShape.height(this) / 5.0F));
			if (flowers == 0) {
				seeds = 1;
			} else if (crossed) {
				seeds = Math.max(1, flowers / 3);
			} else {
				seeds = random.nextFloat() < 0.2F ? 1 : 0;
			}
		}

		int hybrid;
		if (strain == Strain.HYBRID) {
			hybrid = seeds;
		} else if (crossed) {
			hybrid = seeds / 2 + random.nextInt(seeds % 2 + 1);
		} else {
			hybrid = 0;
		}

		List<ItemStack> drops = new ArrayList<>();
		if (fibre > 0) drops.add(new ItemStack(Hemp.HEMP, fibre));
		if (seeds - hybrid > 0) drops.add(new ItemStack(strain.seeds(), seeds - hybrid));
		if (hybrid > 0) drops.add(new ItemStack(Hemp.HYBRID_SEEDS, hybrid));
		if (flowers > 0) drops.add(new ItemStack(strain.flower(), flowers));
		return drops;
	}

	/** A detached copy of the record, to carry across the clump being replaced by its keeper. */
	HempPlant snapshot() {
		HempPlant copy = new HempPlant(worldPosition, getBlockState());
		copy.copyRecord(this);
		return copy;
	}

	/** Take up a clump's record as the one seedling kept from it. */
	void trimFrom(HempPlant clump) {
		copyRecord(clump);
		thinnedAt = clump.vegLight;
		setChanged();
	}

	private void copyRecord(HempPlant other) {
		genome = other.genome;
		lightTicks = other.lightTicks;
		darkTicks = other.darkTicks;
		vegLight = other.vegLight;
		cycles = other.cycles;
		flowerCycles = other.flowerCycles;
		flowering = other.flowering;
		pollen = other.pollen;
		lightSinceCycle = other.lightSinceCycle;
		thinnedAt = other.thinnedAt;
	}

	/**
	 * Where this plant is in its life, in a few words a player can act on: how far through its
	 * cycles it is, whether a lamp is holding it from its nights, and whether it is ripe. Thinning
	 * is left for players to find: nothing here tells a clump from a thinned plant, or says shears.
	 */
	public String status(Level level) {
		BlockState base = getBlockState();
		boolean clump = base.getBlock() instanceof HempCropBlock;
		if (isSeedling()) return "Coming up";
		Strain strain = strain();
		boolean crossed = strain != Strain.HYBRID && (pollen & ~(1 << strain.ordinal())) != 0;
		int stage = PlantShape.budStage(this);
		if (clump && stage == 3) return crossed ? "Gone to seed, crossed: break the root" : "Gone to seed: break the root";
		if (flowering && flowerCycles >= FLOWER_CYCLES) {
			return crossed ? "Ripe and crossed: break the root" : "Ripe: break the root";
		}
		if (clump && stage == 2) return "Most fibre now, or wait for seed";
		if (lightSinceCycle > HELD_LIGHT) return flowering ? "No real night: flowering paused" : "No real night: held growing";
		if (level.isBrightOutside() && level.getRawBrightness(worldPosition, level.getSkyDarken()) < LIGHT_LEVEL) {
			return "Too dim to grow";
		}
		if (flowering) {
			return "Flowering, day " + (flowerCycles + 1) + " of " + FLOWER_CYCLES + (crossed ? ", crossed" : "");
		}
		return "Growing, cycle " + (cycles + 1) + " of " + VEG_CYCLES;
	}

	/** Still a clump of seedlings, not yet thinned and not yet grown lanky. */
	public boolean isSeedling() {
		BlockState base = getBlockState();
		return base.getBlock() instanceof HempCropBlock && base.getValue(HempProps.FORM) == HempProps.Form.CLUSTER
			&& vegDays() < CLUSTER_DAYS;
	}

	/** The plant a piece of hemp belongs to, found by walking back down its stalks to the root. */
	public static HempPlant rootOf(BlockGetter level, BlockPos pos) {
		BlockPos at = pos;
		for (int step = 0; step < 256; step++) {
			BlockState state = level.getBlockState(at);
			Block block = state.getBlock();
			if (block instanceof HempBaseBlock) {
				return level.getBlockEntity(at) instanceof HempPlant plant ? plant : null;
			} else if (block instanceof HempStalkBlock || block instanceof HempStemsBlock) {
				at = at.below();
			} else {
				return null;
			}
		}
		return null;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("Genome", genome);
		output.putInt("Light", lightTicks);
		output.putInt("Dark", darkTicks);
		output.putLong("VegLight", vegLight);
		output.putInt("Cycles", cycles);
		output.putInt("FlowerCycles", flowerCycles);
		output.putBoolean("Flowering", flowering);
		output.putInt("Pollen", pollen);
		output.putLong("LightSinceCycle", lightSinceCycle);
		output.putLong("ThinnedAt", thinnedAt);
		output.store("Mine", Codec.LONG.listOf(), List.copyOf(mine));
		output.store("Pruned", Codec.LONG.listOf(), List.copyOf(pruned));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		genome = input.getLongOr("Genome", 0L);
		lightTicks = input.getIntOr("Light", 0);
		darkTicks = input.getIntOr("Dark", 0);
		vegLight = input.getLongOr("VegLight", 0L);
		cycles = input.getIntOr("Cycles", 0);
		flowerCycles = input.getIntOr("FlowerCycles", 0);
		flowering = input.getBooleanOr("Flowering", false);
		pollen = input.getIntOr("Pollen", 0);
		lightSinceCycle = input.getLongOr("LightSinceCycle", 0L);
		thinnedAt = input.getLongOr("ThinnedAt", -1L);
		mine.clear();
		input.read("Mine", Codec.LONG.listOf()).ifPresent(mine::addAll);
		pruned.clear();
		input.read("Pruned", Codec.LONG.listOf()).ifPresent(pruned::addAll);
	}
}
