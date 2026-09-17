package justfatlard.hemp_craft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A length of hemp rope hanging from what is above it.
 *
 * <p>One thick braided core down the middle of the block, walked through rather than stood on,
 * and climbed: the climbing is the {@code #minecraft:climbable} tag, the walking through is the
 * empty collision the block is built with, and the core is all the shape there is to catch a
 * cursor.
 *
 * <p>It hangs rather than stands. A rope needs a solid face above it or another rope, so a coil
 * dropped down a shaft grows from the top down and goes when its anchor does - the same rule a
 * chain lives by, which is the one players already know.
 */
public class RopeBlock extends Block {

	/** The braided core: six pixels through the middle, which is what the model draws. */
	private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

	public RopeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState above = level.getBlockState(pos.above());
		return above.is(this) || above.isFaceSturdy(level, pos.above(), Direction.DOWN);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbour,
			RandomSource random) {
		if (direction == Direction.UP && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	/** Rope comes back whole; there is nothing to lose by taking it down. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(this));
	}
}
