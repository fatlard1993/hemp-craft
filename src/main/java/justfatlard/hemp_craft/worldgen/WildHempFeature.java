package justfatlard.hemp_craft.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import justfatlard.hemp_craft.Hemp;
import justfatlard.hemp_craft.HempProps;
import justfatlard.hemp_craft.Strain;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * One wild hemp plant: its rooted base and the stems standing on it, placed together so both take
 * the same variant and their stems meet, which two independently placed blocks would not. How tall
 * it stands varies plant to plant; all of it is in seed.
 */
public record WildHempFeature(Strain strain) implements Feature {

	public static final MapCodec<WildHempFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Strain.CODEC.fieldOf("strain").forGetter(WildHempFeature::strain)
	).apply(instance, WildHempFeature::new));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int variant = random.nextInt(4);
		BlockState base = Hemp.WILD.defaultBlockState()
			.setValue(HempProps.STRAIN, strain)
			.setValue(HempProps.VARIANT, variant);
		BlockPos above = origin.above();
		if (!level.getBlockState(origin).isAir() || !level.getBlockState(above).isAir() || !base.canSurvive(level, origin)) {
			return false;
		}
		level.setBlock(origin, base, Block.UPDATE_CLIENTS);
		level.setBlock(above, Hemp.STEMS.defaultBlockState()
			.setValue(HempProps.STRAIN, strain)
			.setValue(HempProps.STAGE, 1 + random.nextInt(3))
			.setValue(HempProps.BUDS, 3)
			.setValue(HempProps.VARIANT, variant), Block.UPDATE_CLIENTS);
		return true;
	}
}
