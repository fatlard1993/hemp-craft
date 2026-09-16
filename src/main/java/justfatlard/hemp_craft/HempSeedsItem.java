package justfatlard.hemp_craft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Seeds of one strain, planting a clump of seedlings of it.
 *
 * <p>The server plays a placement sound to everyone but the placer, whose own client is meant to
 * have predicted it. Pandorical only builds a client block item for an item sharing its block's
 * id, so these seeds reach the client as a plain item that predicts nothing, and without this the
 * one player who planted is the one who hears nothing.
 */
public class HempSeedsItem extends BlockItem {

	private final Strain strain;

	public HempSeedsItem(Block block, Strain strain, Properties properties) {
		super(block, properties);
		this.strain = strain;
	}

	@Override
	protected BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		return state == null ? null : state.setValue(HempProps.STRAIN, strain);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		InteractionResult result = super.place(context);
		if (result.consumesAction() && context.getPlayer() instanceof ServerPlayer player) {
			BlockPos pos = context.getClickedPos();
			SoundType sound = context.getLevel().getBlockState(pos).getSoundType();
			player.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.getPlaceSound()),
				SoundSource.BLOCKS,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				(sound.getVolume() + 1.0F) / 2.0F,
				sound.getPitch() * 0.8F,
				context.getLevel().getRandom().nextLong()));
		}
		return result;
	}
}
