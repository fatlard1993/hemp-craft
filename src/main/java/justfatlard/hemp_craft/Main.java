package justfatlard.hemp_craft;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import justfatlard.hemp_craft.worldgen.WildHempFeature;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hemp: a plant grown, shaped and bred rather than just planted.
 *
 * <p>Server-side; Pandorical carries the client's half. A planting comes up as a clump of
 * seedlings; shears thin it to one keeper, which grows by light and dark cycles into a branching
 * plant of its strain, and flowers. Left untrimmed it grows lanky, the way it grows wild. Where
 * each strain grows wild is a biome tag, {@code hemp-craft-justfatlard:grows_wild_indica} and
 * {@code grows_wild_sativa}, so a datapack can move it.
 */
public class Main implements ModInitializer {

	public static final String MOD_ID = "hemp-craft-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String STAND_IN = "minecraft:sweet_berry_bush";

	@Override
	public void onInitialize() {
		Hemp.register();

		// Every piece is a berry bush to the client: walked through, gone at a touch. Not wheat,
		// whose map colour is worked out from its age: a stand-in built on wheat's settings with no
		// age of its own throws as it is made. The clump alone answers a right-click, to shears.
		part("hemp_crop", new BlockRegistration().baseBlock(STAND_IN).interactive());
		for (String name : new String[] {"hemp_root", "hemp_stalk", "hemp_stems", "wild_hemp"}) {
			part(name, new BlockRegistration().baseBlock(STAND_IN));
		}
		// Rope stands in as a weeping vine, the one vanilla block that hangs, is climbed and is
		// walked through: a vanilla client climbs the rope it can see without being told anything.
		part("rope", new BlockRegistration()
			.baseBlock("minecraft:weeping_vines")
			.strength(0.4F)
			.model(MOD_ID + ":block/rope"));
		for (String name : new String[] {"indica_seeds", "sativa_seeds", "hybrid_seeds", "hemp", "rope",
				"indica_flower", "sativa_flower", "hybrid_flower", "indica_joint", "sativa_joint", "hybrid_joint",
				"brownie"}) {
			PandoricalApi.content().registerItem(MOD_ID + ":" + name,
				new ItemRegistration().model(MOD_ID + ":item/" + name));
		}
		PandoricalApi.content().registerModAssets(MOD_ID);

		Registry.register(BuiltInRegistries.FEATURE_TYPE, id("wild_hemp"), WildHempFeature.CODEC);
		growsWild("indica");
		growsWild("sativa");
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Pollen.clear());

		// Guarded, and the guard is why the call sits behind its own class: naming a block-tip
		// type here would load it whether or not that mod is installed.
		if (FabricLoader.getInstance().isModLoaded("block-tip")) {
			justfatlard.hemp_craft.integration.HempTips.register();
		}

		LOGGER.info("Hemp Craft loaded");
	}

	private static void part(String name, BlockRegistration registration) {
		PandoricalApi.content().registerBlock(MOD_ID + ":" + name, registration);
	}

	private static void growsWild(String strain) {
		BiomeModifications.addFeature(
			BiomeSelectors.tag(TagKey.create(Registries.BIOME, id("grows_wild_" + strain))),
			GenerationStep.Decoration.VEGETAL_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, id("wild_" + strain)));
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
