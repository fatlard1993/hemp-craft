package justfatlard.hemp_craft;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;

/**
 * Hemp nobody planted: an untrimmed tangle of thin stems gone to flower, rooted in grass, with
 * {@link HempStemsBlock} standing on it. It does not grow; breaking it gives a little fibre, a
 * handful of the seed of whichever strain the climate grew, and very rarely a flower.
 */
public class WildHempBlock extends HempPartBlock {

	public WildHempBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HempProps.STRAIN, HempProps.VARIANT);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(BlockTags.SUPPORTS_VEGETATION);
	}

	/** Chance of a second seed: wild hemp is found, not farmed, so it starts a crop and no more. */
	private static final float SECOND_SEED = 0.35F;
	/** Chance of a flower: one plant in two hundred, a find rather than a harvest. */
	private static final float FLOWER = 0.005F;

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		RandomSource random = params.getLevel().getRandom();
		Strain strain = state.getValue(HempProps.STRAIN);
		ItemStack seeds = new ItemStack(strain.seeds(), random.nextFloat() < SECOND_SEED ? 2 : 1);
		ItemStack fibre = new ItemStack(Hemp.HEMP, 2 + random.nextInt(2));
		if (random.nextFloat() < FLOWER) return List.of(fibre, seeds, new ItemStack(strain.flower()));
		return List.of(fibre, seeds);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(state.getValue(HempProps.STRAIN).seeds());
	}
}
