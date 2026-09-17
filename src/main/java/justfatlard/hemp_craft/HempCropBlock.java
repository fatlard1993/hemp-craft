package justfatlard.hemp_craft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A planting before anyone has chosen for it: a clump of seedlings, which shears can thin to the
 * one worth keeping while they are young enough (stage 2 or 3). Left alone past that it grows on
 * as a tangle of thin stems two blocks high, the way hemp grows wild.
 */
public class HempCropBlock extends HempBaseBlock {

	/**
	 * The stage from which shears thin a clump, and the whole of the knack.
	 *
	 * <p>Shears on a clump from this stage until it grows lanky swap it for a single
	 * {@code hemp_root} carrying on the clump's record. The record keeps its age at thinning
	 * ({@code ThinnedAt}), so the single starts at the kept seedling's height and catches up with
	 * a plant grown single from seed over the next few days ({@code PlantShape.height}); a plant
	 * saved without it is taken as never thinned. Flowers come only this way, which is what makes
	 * it worth finding.
	 *
	 * <p>This is the one part of the mod a player is meant to find rather than read. It is written
	 * here and nowhere else: the readme only hints at it, DEVELOPMENT.md points here instead of
	 * repeating it, and {@link HempPlant#status} never tells a clump from a thinned plant or
	 * mentions shears. Keep all three that way.
	 */
	private static final int TRIM_FROM = 2;

	public HempCropBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HempProps.FORM, HempProps.Form.CLUSTER));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HempProps.STRAIN, HempProps.FORM, HempProps.STAGE, HempProps.BUDS, HempProps.VARIANT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int stage = state.getValue(HempProps.STAGE);
		return state.getValue(HempProps.FORM) == HempProps.Form.CLUSTER ? COLUMN[Math.max(1, stage)] : COLUMN[stage + 1];
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.SHEARS) || state.getValue(HempProps.FORM) != HempProps.Form.CLUSTER
				|| state.getValue(HempProps.STAGE) < TRIM_FROM) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server && server.getBlockEntity(pos) instanceof HempPlant clump) {
			HempPlant kept = clump.snapshot();
			server.setBlock(pos, Hemp.ROOT.defaultBlockState()
				.setValue(HempProps.STRAIN, state.getValue(HempProps.STRAIN)), Block.UPDATE_ALL);
			if (server.getBlockEntity(pos) instanceof HempPlant single) single.trimFrom(kept);
			server.playSound(null, pos, SoundEvents.GROWING_PLANT_CROP, SoundSource.BLOCKS, 1.0F, 1.0F);
			stack.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.SUCCESS;
	}
}
