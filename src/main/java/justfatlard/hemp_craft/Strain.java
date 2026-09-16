package justfatlard.hemp_craft;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

/**
 * The three kinds of hemp and the growth rules each carries.
 *
 * <p>Indica is short and bushy. Sativa is tall and lanky: it grows faster and stretches far more
 * once it flowers. Hybrid, which only comes of the two flowering together, sits between. How each
 * branches and leafs is the models' to draw (generate_models.py).
 */
public enum Strain implements StringRepresentable {
	INDICA("indica", 2.4F, 0.25F, 26),
	SATIVA("sativa", 3.2F, 0.6F, 32),
	HYBRID("hybrid", 2.8F, 0.4F, 29);

	public static final Codec<Strain> CODEC = StringRepresentable.fromEnum(Strain::values);

	private final String name;
	/** Pixels of stalk a day of light adds while the plant is vegetative. */
	final float vegRate;
	/** How much flowering stretches the plant, as a share of its height when it flipped. */
	final float stretch;

	/** How tall an untrimmed planting's stems climb, in pixels: indica stays low, sativa reaches two blocks. */
	final int lankyTop;

	Strain(String name, float vegRate, float stretch, int lankyTop) {
		this.name = name;
		this.vegRate = vegRate;
		this.stretch = stretch;
		this.lankyTop = lankyTop;
	}

	public Item seeds() {
		return switch (this) {
			case INDICA -> Hemp.INDICA_SEEDS;
			case SATIVA -> Hemp.SATIVA_SEEDS;
			case HYBRID -> Hemp.HYBRID_SEEDS;
		};
	}

	public Item flower() {
		return switch (this) {
			case INDICA -> Hemp.INDICA_FLOWER;
			case SATIVA -> Hemp.SATIVA_FLOWER;
			case HYBRID -> Hemp.HYBRID_FLOWER;
		};
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
