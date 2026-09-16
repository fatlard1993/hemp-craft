package justfatlard.hemp_craft;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.tags.BlockTags;

/**
 * The block on the farmland that is the plant: it keeps the plant's record and grows everything
 * above and around it, and breaking it harvests the whole plant.
 */
public abstract class HempBaseBlock extends HempPartBlock implements EntityBlock {

	protected HempBaseBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(BlockTags.SUPPORTS_CROPS);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HempPlant(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != Hemp.PLANT) return null;
		return (tickLevel, pos, tickState, plant) -> ((HempPlant) plant).serverTick(tickLevel, pos);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		if (level.getBlockEntity(pos) instanceof HempPlant plant) plant.sow(level.getRandom().nextLong());
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof HempPlant plant) {
			return plant.harvest(params.getLevel().getRandom());
		}
		return List.of(new ItemStack(state.getValue(HempProps.STRAIN).seeds()));
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(state.getValue(HempProps.STRAIN).seeds());
	}
}
