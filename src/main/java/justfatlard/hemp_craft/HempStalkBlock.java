package justfatlard.hemp_craft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A block of stalk above the root, carrying its nodes' leaves and side branches. */
public class HempStalkBlock extends HempPartBlock {

	public HempStalkBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		addStalkProperties(builder);
	}

	static void addStalkProperties(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HempProps.STRAIN, HempProps.THICKNESS, HempProps.LEAVES, HempProps.BUDS,
			HempProps.FILL, HempProps.VARIANT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return COLUMN[state.getValue(HempProps.FILL)];
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return isStem(level.getBlockState(pos.below()));
	}
}
