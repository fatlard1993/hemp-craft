package justfatlard.hemp_craft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.block.state.BlockState;

/**
 * What a plant is made of at this moment, worked out from its record alone: the same plant at
 * the same age is always the same shape, so it can be rebuilt, harvested, or regrown after damage
 * without remembering anything but its clock and its genome.
 *
 * <p>A trimmed plant is a main stalk of {@link Strain}-set height, tapering to its tip, its side
 * branches drawn by each block's models. Its growth follows two curves: steady through the ten
 * vegetative days, then a logarithmic crawl if it is kept from flowering; and once flowering, a
 * stretch over the first day and a half before buds set and fill out over the rest.
 */
final class PlantShape {

	/** A block of the plant, relative to its root; its parent is the cell it stands on. */
	record Cell(int dx, int dy, int dz, BlockState state, int parent) {}

	/** Pixels of seedling a planting starts from. */
	private static final float START = 6.0F;
	/** How gently growth tails off past the vegetative days: the log curve's scale, in days. */
	private static final float TAPER = 4.0F;
	private static final float LANKY_DAYS = 6.0F;
	/** Pixels high the clump's seedlings stand at each of its stages, as its models draw them. */
	private static final int[] SEEDLING_PX = {1, 3, 5, 7};
	/** Days over which a thinned seedling closes on the height of a plant grown single from seed. */
	private static final float CATCH_UP = 2.5F;

	private PlantShape() {}

	static List<Cell> of(HempPlant plant) {
		List<Cell> cells = new ArrayList<>();
		BlockState base = plant.getBlockState();
		if (base.getBlock() instanceof HempRootBlock) {
			single(plant, cells);
		} else if (base.getValue(HempProps.FORM) == HempProps.Form.CLUSTER && plant.vegDays() < HempPlant.CLUSTER_DAYS) {
			cluster(plant, cells);
		} else {
			lanky(plant, cells);
		}
		return cells;
	}

	private static void cluster(HempPlant plant, List<Cell> cells) {
		int stage = clumpStage(plant.vegDays());
		cells.add(new Cell(0, 0, 0, Hemp.CROP.defaultBlockState()
			.setValue(HempProps.STRAIN, plant.strain())
			.setValue(HempProps.FORM, HempProps.Form.CLUSTER)
			.setValue(HempProps.STAGE, stage)
			.setValue(HempProps.VARIANT, variant(plant, 0)), -1));
	}

	/**
	 * Untrimmed: a tangle of thin stems climbing to their strain's height, then setting small
	 * seedy flowers along their tops. Both blocks take the same variant, so their stems meet.
	 */
	private static void lanky(HempPlant plant, List<Cell> cells) {
		int topPx = plant.strain().lankyTop;
		float grown = Math.max(0.0F, plant.vegDays() - HempPlant.CLUSTER_DAYS) / LANKY_DAYS;
		int height = Math.round(Math.min(topPx, 12 + (topPx - 12) * Math.min(1.0F, grown)));
		int buds = budStage(plant);
		int variant = variant(plant, 0);
		cells.add(new Cell(0, 0, 0, Hemp.CROP.defaultBlockState()
			.setValue(HempProps.STRAIN, plant.strain())
			.setValue(HempProps.FORM, HempProps.Form.LANKY)
			.setValue(HempProps.STAGE, quarters(Math.min(height, 16)) - 1)
			.setValue(HempProps.BUDS, buds)
			.setValue(HempProps.VARIANT, variant), -1));
		if (height > 16) {
			cells.add(new Cell(0, 1, 0, Hemp.STEMS.defaultBlockState()
				.setValue(HempProps.STRAIN, plant.strain())
				.setValue(HempProps.STAGE, quarters(height - 16) - 1)
				.setValue(HempProps.BUDS, buds)
				.setValue(HempProps.VARIANT, variant), 0));
		}
	}

	/**
	 * A thinned plant: its main stalk, block by block, each block's taper counted from the tip.
	 * The side branches are in each block's models, growing longer the further down the taper the
	 * block sits, so the plant fills out into its tree shape as it gains height.
	 */
	private static void single(HempPlant plant, List<Cell> cells) {
		Strain strain = plant.strain();
		int height = Math.round(height(plant));
		int buds = budStage(plant);
		boolean oldLeavesFall = plant.flowerDays() > 3.5F;
		int top = (height - 1) / 16;
		for (int i = 0; i <= top; i++) {
			BlockState state = stalk(i == 0 ? Hemp.ROOT.defaultBlockState() : Hemp.STALK.defaultBlockState(),
				strain, height - 16 * i, top - i, buds)
				.setValue(HempProps.THICKNESS, Math.min(3, top - i))
				.setValue(HempProps.VARIANT, variant(plant, i));
			if (i == 0 && top >= 2 && oldLeavesFall) state = state.setValue(HempProps.LEAVES, 0);
			cells.add(new Cell(0, i, 0, state, i - 1));
		}
	}

	/** Which way a block of this plant's stalk is set, from its genome and its height. */
	private static int variant(HempPlant plant, int block) {
		long mixed = (plant.genome ^ (block * 0x9E3779B97F4A7C15L)) * 0xBF58476D1CE4E5B9L;
		return (int) ((mixed >>> 60) & 3);
	}

	/** The parts of a stalk block every stem shares: how high it reaches, its leaves, its buds. */
	private static BlockState stalk(BlockState state, Strain strain, int remaining, int fromTop, int buds) {
		return state
			.setValue(HempProps.STRAIN, strain)
			.setValue(HempProps.FILL, fromTop > 0 ? 4 : Math.min(3, quarters(remaining)))
			.setValue(HempProps.LEAVES, fromTop > 0 ? 2 : 1)
			.setValue(HempProps.BUDS, Math.max(0, buds - fromTop));
	}

	/**
	 * The main stalk's height in pixels. Steady through the vegetative days, then a logarithm
	 * past them so a plant kept from flowering keeps gaining, slower and slower. A plant thinned
	 * from a clump starts from the seedling it was, and makes up the difference over the next few
	 * days. Flowering adds a stretch, a share of the height at the flip, eased in over a day and a
	 * half, and a little more slow growth after.
	 */
	static float height(HempPlant plant) {
		Strain strain = plant.strain();
		float rate = strain.vegRate * plant.vigor();
		float days = plant.vegDays();
		float veg = vegHeight(rate, days);
		float thinned = plant.thinnedDays();
		if (thinned >= 0.0F) {
			float behind = vegHeight(rate, thinned) - SEEDLING_PX[clumpStage(thinned)];
			if (behind > 0.0F) veg -= behind * (float) Math.exp(-(days - thinned) / CATCH_UP);
		}
		if (!plant.flowering) return veg;
		float flower = plant.flowerDays();
		float eased = smoothstep(Math.min(1.0F, flower / 1.5F));
		return veg * (1.0F + strain.stretch * eased) + rate * 0.3F * (float) Math.log1p(flower);
	}

	/** Height grown single from seed after this many days of light. */
	private static float vegHeight(float rate, float days) {
		float veg = START + rate * Math.min(days, HempPlant.VEG_CYCLES);
		if (days > HempPlant.VEG_CYCLES) veg += rate * TAPER * (float) Math.log1p((days - HempPlant.VEG_CYCLES) / TAPER);
		return veg;
	}

	/** Which of its four stages a clump of seedlings has reached after this many days of light. */
	private static int clumpStage(float days) {
		return Math.min(3, (int) (days / (HempPlant.CLUSTER_DAYS / 4)));
	}

	/** None through the stretch, then spiky, forming, and full by the end of the fifth day. */
	static int budStage(HempPlant plant) {
		if (!plant.flowering) return 0;
		float flower = plant.flowerDays();
		if (flower < 1.0F) return 0;
		if (flower < 2.5F) return 1;
		if (flower < 4.0F) return 2;
		return 3;
	}

	private static int quarters(int pixels) {
		return Math.max(1, Math.min(4, (pixels + 3) / 4));
	}

	private static float smoothstep(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
