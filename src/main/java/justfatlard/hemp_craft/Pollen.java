package justfatlard.hemp_craft;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Which plants are in flower, and where. A flowering plant reports itself each time it looks at
 * the light and hears back which other strains are flowering in the same field. A report goes
 * stale if it is not renewed, so a plant that is harvested or unloaded simply stops counting.
 */
final class Pollen {

	/** Blocks apart two plants can be and still cross: a field, not a farm. */
	private static final int RANGE = 12;
	private static final long FRESH_TICKS = 200;

	private record Shed(Strain strain, long at) {}

	private static final Map<ResourceKey<Level>, Map<BlockPos, Shed>> FLOWERING = new HashMap<>();

	private Pollen() {}

	/** Report this plant in flower; returns the other strains flowering in range, as ordinal bits. */
	static int shed(ServerLevel level, BlockPos pos, Strain strain) {
		long now = level.getGameTime();
		Map<BlockPos, Shed> here = FLOWERING.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		here.put(pos.immutable(), new Shed(strain, now));
		int caught = 0;
		for (Iterator<Map.Entry<BlockPos, Shed>> it = here.entrySet().iterator(); it.hasNext();) {
			Map.Entry<BlockPos, Shed> other = it.next();
			if (now - other.getValue().at() > FRESH_TICKS) {
				it.remove();
			} else if (other.getValue().strain() != strain && other.getKey().closerThan(pos, RANGE)) {
				caught |= 1 << other.getValue().strain().ordinal();
			}
		}
		return caught;
	}

	static void clear() {
		FLOWERING.clear();
	}
}
