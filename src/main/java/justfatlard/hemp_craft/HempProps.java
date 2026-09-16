package justfatlard.hemp_craft;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * What a piece of a hemp plant shows. The plant itself lives in its root's block entity; these
 * are only what each block needs to pick its models.
 */
public final class HempProps {

	public static final EnumProperty<Strain> STRAIN = EnumProperty.create("strain", Strain.class);
	/**
	 * A stalk block's place in the taper, counted in blocks from the tip: thin at the tip, a
	 * trunk from three down. Its side branches grow with it, none at the tip and longest on the
	 * trunk, which is what gives the plant its tree shape.
	 */
	public static final IntegerProperty THICKNESS = IntegerProperty.create("thickness", 0, 3);
	/** None, young, or grown fan leaves at this block's nodes. */
	public static final IntegerProperty LEAVES = IntegerProperty.create("leaves", 0, 2);
	/** None, spiky, forming, or full buds. */
	public static final IntegerProperty BUDS = IntegerProperty.create("buds", 0, 3);
	/** How far up the block the stalk reaches in quarters; 4 is full and goes on above. */
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 1, 4);
	/** A seedling clump's growth, or a lanky stem's height in quarters of its block. */
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
	/**
	 * Which of the ways this block's branches and leaves can be set, chosen by the plant's genome
	 * so a plant keeps its shape while every plant differs.
	 */
	public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);
	public static final EnumProperty<Form> FORM = EnumProperty.create("form", Form.class);

	private HempProps() {}

	/** An untrimmed planting: a clump of seedlings, or the thin stems it becomes if left. */
	public enum Form implements StringRepresentable {
		CLUSTER("cluster"), LANKY("lanky");

		private final String name;

		Form(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
