package justfatlard.hemp_craft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The upper block of an untrimmed planting's thin stems, on a lanky crop or on wild hemp. */
public class HempStemsBlock extends HempPartBlock {

	public HempStemsBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HempProps.STRAIN, HempProps.STAGE, HempProps.BUDS, HempProps.VARIANT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return COLUMN[state.getValue(HempProps.STAGE) + 1];
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.getBlock() instanceof WildHempBlock
			|| (below.getBlock() instanceof HempCropBlock && below.getValue(HempProps.FORM) == HempProps.Form.LANKY);
	}
}
