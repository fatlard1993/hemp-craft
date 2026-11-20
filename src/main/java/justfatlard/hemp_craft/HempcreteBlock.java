package justfatlard.hemp_craft;

import java.util.List;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A hempcrete wall, four pixels through: thinner than a stone wall, thicker than a pane.
 *
 * <p>It runs like a pane, joining the hempcrete and the solid faces beside it, and reaches into a
 * wall or fence beside it to stand against its post. It also runs corner to corner: two pieces
 * touching only at a corner join across it, unless hempcrete already stands in either of the
 * squares between them, so an L-shaped run stays an L and never grows a third side.
 */
public class HempcreteBlock extends Block {

	/** How far an arm reaches toward a side: not at all, to the block's edge, or on to a neighbour's post. */
	public enum Arm implements StringRepresentable {
		NONE("none"), EDGE("edge"), POST("post");

		private final String name;

		Arm(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Arm> NORTH = EnumProperty.create("north", Arm.class);
	public static final EnumProperty<Arm> EAST = EnumProperty.create("east", Arm.class);
	public static final EnumProperty<Arm> SOUTH = EnumProperty.create("south", Arm.class);
	public static final EnumProperty<Arm> WEST = EnumProperty.create("west", Arm.class);

	public static final BooleanProperty NORTH_EAST = BooleanProperty.create("north_east");
	public static final BooleanProperty SOUTH_EAST = BooleanProperty.create("south_east");
	public static final BooleanProperty SOUTH_WEST = BooleanProperty.create("south_west");
	public static final BooleanProperty NORTH_WEST = BooleanProperty.create("north_west");

	private record Side(Direction direction, EnumProperty<Arm> property) {}

	private record Corner(Direction a, Direction b, BooleanProperty property) {
		BlockPos from(BlockPos pos) {
			return pos.relative(a).relative(b);
		}
	}

	private static final List<Side> SIDES = List.of(
		new Side(Direction.NORTH, NORTH),
		new Side(Direction.EAST, EAST),
		new Side(Direction.SOUTH, SOUTH),
		new Side(Direction.WEST, WEST));

	private static final List<Corner> CORNERS = List.of(
		new Corner(Direction.NORTH, Direction.EAST, NORTH_EAST),
		new Corner(Direction.SOUTH, Direction.EAST, SOUTH_EAST),
		new Corner(Direction.SOUTH, Direction.WEST, SOUTH_WEST),
		new Corner(Direction.NORTH, Direction.WEST, NORTH_WEST));

	private static final VoxelShape POST = Block.box(6, 0, 6, 10, 16, 10);

	private final Function<BlockState, VoxelShape> shapes;

	public HempcreteBlock(Properties properties) {
		super(properties);
		BlockState lone = stateDefinition.any();
		for (Side side : SIDES) lone = lone.setValue(side.property, Arm.NONE);
		for (Corner corner : CORNERS) lone = lone.setValue(corner.property, false);
		registerDefaultState(lone);
		shapes = getShapeForEachState(HempcreteBlock::shapeOf);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		for (Side side : SIDES) builder.add(side.property);
		for (Corner corner : CORNERS) builder.add(corner.property);
	}

	/**
	 * Boxes can't lie at an angle, so a corner run is caught by a staircase of them: close enough
	 * to aim at and to stop a player, who couldn't squeeze between the steps anyway. An arm reaching
	 * on to a post is boxed only to the edge; past it is the neighbour's own box.
	 */
	private static VoxelShape shapeOf(BlockState state) {
		VoxelShape shape = POST;
		for (Side side : SIDES) {
			if (state.getValue(side.property) == Arm.NONE) continue;
			int dx = side.direction.getStepX();
			int dz = side.direction.getStepZ();
			shape = Shapes.or(shape, Block.box(
				dx > 0 ? 8 : dx < 0 ? 0 : 6, 0, dz > 0 ? 8 : dz < 0 ? 0 : 6,
				dx > 0 ? 16 : dx < 0 ? 8 : 10, 16, dz > 0 ? 16 : dz < 0 ? 8 : 10));
		}
		for (Corner corner : CORNERS) {
			if (!state.getValue(corner.property)) continue;
			int dx = corner.a.getStepX() + corner.b.getStepX();
			int dz = corner.a.getStepZ() + corner.b.getStepZ();
			for (int t = 2; t <= 6; t += 2) {
				int x = 8 + dx * t;
				int z = 8 + dz * t;
				shape = Shapes.or(shape, Block.box(x - 2, 0, z - 2, x + 2, 16, z + 2));
			}
		}
		return shape;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.apply(state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return joined(context.getLevel(), context.getClickedPos(), defaultBlockState());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbour,
			RandomSource random) {
		return direction.getAxis().isHorizontal() ? joined(level, pos, state) : state;
	}

	/** A corner neighbour hears nothing of a block changing here, so it is told. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		super.onPlace(state, level, pos, old, movedByPiston);
		if (!(old.getBlock() instanceof HempcreteBlock)) rejoinCorners(level, pos);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		rejoinCorners(level, pos);
	}

	private static void rejoinCorners(Level level, BlockPos pos) {
		for (Corner corner : CORNERS) {
			BlockPos at = corner.from(pos);
			BlockState there = level.getBlockState(at);
			if (!(there.getBlock() instanceof HempcreteBlock)) continue;
			BlockState rejoined = joined(level, at, there);
			if (rejoined != there) level.setBlock(at, rejoined, Block.UPDATE_CLIENTS);
		}
	}

	private static BlockState joined(BlockGetter level, BlockPos pos, BlockState state) {
		for (Side side : SIDES) {
			BlockPos at = pos.relative(side.direction);
			BlockState there = level.getBlockState(at);
			state = state.setValue(side.property, armToward(level, at, there, side.direction));
		}
		for (Corner corner : CORNERS) {
			boolean between = isHempcrete(level.getBlockState(pos.relative(corner.a)))
				|| isHempcrete(level.getBlockState(pos.relative(corner.b)));
			state = state.setValue(corner.property, !between && isHempcrete(level.getBlockState(corner.from(pos))));
		}
		return state;
	}

	private static Arm armToward(BlockGetter level, BlockPos at, BlockState there, Direction direction) {
		if (isHempcrete(there)) return Arm.EDGE;
		if (there.is(BlockTags.WALLS) || there.is(BlockTags.FENCES)) return Arm.POST;
		// A gate's end post stands at its edge, and only the ends of its run face a neighbour.
		if (there.getBlock() instanceof FenceGateBlock) {
			return FenceGateBlock.connectsToDirection(there, direction) ? Arm.EDGE : Arm.NONE;
		}
		boolean sturdy = there.isFaceSturdy(level, at, direction.getOpposite());
		return sturdy && !isExceptionForConnection(there) ? Arm.EDGE : Arm.NONE;
	}

	private static boolean isHempcrete(BlockState state) {
		return state.getBlock() instanceof HempcreteBlock;
	}

	/** Hempcrete comes back whole; there is nothing to lose by taking it down. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(this));
	}
}
