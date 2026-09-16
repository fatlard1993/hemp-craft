package justfatlard.hemp_craft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Any block a hemp plant is made of. Each stands only while what holds it up stands, so taking
 * the root takes the plant; light passes through all of them, so the leaves overhead never
 * shade the root out of the daylight it counts. Bone meal on any of them feeds the whole plant.
 */
public abstract class HempPartBlock extends Block implements BonemealableBlock {

	/** Outline columns by height in quarters, index 1 to 4. */
	protected static final VoxelShape[] COLUMN = {
		Block.column(12.0, 0.0, 2.0), Block.column(12.0, 0.0, 4.0), Block.column(12.0, 0.0, 8.0),
		Block.column(12.0, 0.0, 12.0), Block.column(12.0, 0.0, 16.0),
	};

	protected HempPartBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return COLUMN[4];
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
	}

	/** A player cutting a piece off prunes it: the plant will not grow that piece back. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()) {
			HempPlant plant = HempPlant.rootOf(level, pos);
			if (plant != null) plant.prune(pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		HempPlant plant = HempPlant.rootOf(level, pos);
		return plant != null && plant.canFeed();
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	/**
	 * Feeds the plant and shows it: a client's stand-in for this block is not one bone meal knows,
	 * so the client draws no sparkles of its own.
	 */
	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		HempPlant plant = HempPlant.rootOf(level, pos);
		if (plant == null) return;
		plant.feed(level);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.0);
	}

	/** Whether this is stalk that a stalk block can rise from. */
	static boolean isStem(BlockState state) {
		return state.getBlock() instanceof HempStalkBlock || state.getBlock() instanceof HempRootBlock;
	}
}
