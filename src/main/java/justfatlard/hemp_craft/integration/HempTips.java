package justfatlard.hemp_craft.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.hemp_craft.HempPartBlock;
import justfatlard.hemp_craft.HempPlant;
import justfatlard.hemp_craft.HempProps;
import justfatlard.hemp_craft.Main;
import justfatlard.hemp_craft.WildHempBlock;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * A hemp plant on the block tip: its strain as the card's name, where it is in its life as the
 * line, and how far along it is as the bar. A plant's clock is otherwise invisible, and a lamp
 * holding it from its nights would look like a plant that simply never flowers.
 *
 * <p>Only loaded when block-tip is here: this class imports its API, and a class that mentions a
 * missing one cannot be loaded.
 */
public final class HempTips {
	private HempTips() {}

	public static void register() {
		for (String block : new String[] {"hemp_crop", "hemp_root", "hemp_stalk", "hemp_stems", "wild_hemp"}) {
			BlockTipApi.icon(Main.MOD_ID + ":" + block, Main.MOD_ID + ":hemp");
		}

		BlockTipApi.name((level, pos, state, player) -> {
			if (!(state.getBlock() instanceof HempPartBlock)) return null;
			String strain = title(state.getValue(HempProps.STRAIN).getSerializedName());
			if (state.getBlock() instanceof WildHempBlock
					|| level.getBlockState(pos.below()).getBlock() instanceof WildHempBlock) {
				return "Wild " + strain;
			}
			HempPlant plant = HempPlant.rootOf(level, pos);
			return strain + (plant != null && plant.isSeedling() ? " Seedlings" : " Hemp");
		});

		// The plant's days, where every other crop in the game has an age property. Asked from any
		// piece of it, so a stalk fills the bar the same as the root it stands on.
		BlockTipApi.growth((level, pos, state, player) -> {
			if (!(state.getBlock() instanceof HempPartBlock) || state.getBlock() instanceof WildHempBlock) {
				return -1.0F;
			}
			HempPlant plant = HempPlant.rootOf(level, pos);
			return plant == null ? -1.0F : plant.grown();
		});

		BlockTipApi.illustrate((level, pos, state, player) -> {
			if (!(state.getBlock() instanceof HempPartBlock) || state.getBlock() instanceof WildHempBlock) return null;
			HempPlant plant = HempPlant.rootOf(level, pos);
			if (plant == null) return null;
			return new BlockTipApi.Tip(plant.status(level),
				BuiltInRegistries.ITEM.getKey(plant.strain().seeds()).toString());
		});
	}

	private static String title(String word) {
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}
}
