package justfatlard.hemp_craft;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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

	/** One dimension's flowering plants, filed by chunk section so a plant only reads its neighbours. */
	private static final class Field {
		final Map<Long, Map<BlockPos, Shed>> sections = new HashMap<>();
		long nextSweep;
	}

	private static final Map<ResourceKey<Level>, Field> FLOWERING = new HashMap<>();

	private Pollen() {}

	/** Report this plant in flower; returns the other strains flowering in range, as ordinal bits. */
	static int shed(ServerLevel level, BlockPos pos, Strain strain) {
		long now = level.getGameTime();
		Field field = FLOWERING.computeIfAbsent(level.dimension(), key -> new Field());
		if (now >= field.nextSweep) {
			sweep(field, now);
			field.nextSweep = now + FRESH_TICKS;
		}

		field.sections.computeIfAbsent(SectionPos.asLong(pos), key -> new HashMap<>())
			.put(pos.immutable(), new Shed(strain, now));

		int caught = 0;
		int minX = SectionPos.blockToSectionCoord(pos.getX() - RANGE), maxX = SectionPos.blockToSectionCoord(pos.getX() + RANGE);
		int minY = SectionPos.blockToSectionCoord(pos.getY() - RANGE), maxY = SectionPos.blockToSectionCoord(pos.getY() + RANGE);
		int minZ = SectionPos.blockToSectionCoord(pos.getZ() - RANGE), maxZ = SectionPos.blockToSectionCoord(pos.getZ() + RANGE);
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					Map<BlockPos, Shed> section = field.sections.get(SectionPos.asLong(x, y, z));
					if (section == null) continue;
					for (Iterator<Map.Entry<BlockPos, Shed>> it = section.entrySet().iterator(); it.hasNext();) {
						Map.Entry<BlockPos, Shed> other = it.next();
						if (now - other.getValue().at() > FRESH_TICKS) {
							it.remove();
						} else if (other.getValue().strain() != strain && other.getKey().closerThan(pos, RANGE)) {
							caught |= 1 << other.getValue().strain().ordinal();
						}
					}
				}
			}
		}
		return caught;
	}

	/** Drop reports nobody nearby has renewed, so harvested and unloaded fields do not pile up. */
	private static void sweep(Field field, long now) {
		for (Iterator<Map<BlockPos, Shed>> sections = field.sections.values().iterator(); sections.hasNext();) {
			Map<BlockPos, Shed> section = sections.next();
			section.values().removeIf(shed -> now - shed.at() > FRESH_TICKS);
			if (section.isEmpty()) sections.remove();
		}
	}

	static void clear() {
		FLOWERING.clear();
	}
}
