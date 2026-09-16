package justfatlard.hemp_craft;

import java.util.Set;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * The plant's blocks, its record, the seeds of each strain, and the harvest.
 *
 * <p>No block shares its id with an item: Pandorical gives a client item the block of the same
 * id to place, and a harvest or a seed that predicted placing the wrong thing would flicker.
 */
public final class Hemp {

	public static final Block CROP = block("hemp_crop", HempCropBlock::new, true);
	public static final Block ROOT = block("hemp_root", HempRootBlock::new, true);
	public static final Block STALK = block("hemp_stalk", HempStalkBlock::new, false);
	public static final Block STEMS = block("hemp_stems", HempStemsBlock::new, false);
	public static final Block WILD = block("wild_hemp", WildHempBlock::new, true);

	public static final BlockEntityType<HempPlant> PLANT = new BlockEntityType<>(HempPlant::new, Set.of(CROP, ROOT));

	public static final Item INDICA_SEEDS = seeds("indica_seeds", Strain.INDICA);
	public static final Item SATIVA_SEEDS = seeds("sativa_seeds", Strain.SATIVA);
	public static final Item HYBRID_SEEDS = seeds("hybrid_seeds", Strain.HYBRID);

	public static final Item HEMP = item("hemp", new Item(new Item.Properties()
		.setId(itemKey("hemp"))
		.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM)));

	public static final Item INDICA_FLOWER = flower("indica_flower");
	public static final Item SATIVA_FLOWER = flower("sativa_flower");
	public static final Item HYBRID_FLOWER = flower("hybrid_flower");

	public static final Item INDICA_JOINT = joint("indica_joint");
	public static final Item SATIVA_JOINT = joint("sativa_joint");
	public static final Item HYBRID_JOINT = joint("hybrid_joint");

	/** Between a cookie and a pie as food, and three minutes of luck besides. */
	public static final Item BROWNIE = item("brownie", new Item(new Item.Properties()
		.setId(itemKey("brownie"))
		.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).build(),
			Consumable.builder()
				.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.LUCK, 3 * 60 * 20)))
				.build())
		.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH)));

	private Hemp() {}

	/** A joint burns a hundred points, lit by its first puff; see {@link JointItem} for its food. */
	private static Item joint(String name) {
		return item(name, new JointItem(new Item.Properties()
			.setId(itemKey(name))
			.durability(100)
			.food(new FoodProperties.Builder().nutrition(0).saturationModifier(0.0F).alwaysEdible().build(),
				Consumable.builder()
					.consumeSeconds(JointItem.EAT_SECONDS)
					.animation(ItemUseAnimation.EAT)
					.hasConsumeParticles(false)
					.build())));
	}

	private static Item flower(String name) {
		return item(name, new Item(new Item.Properties()
			.setId(itemKey(name))
			.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH)));
	}

	/**
	 * Every piece of the plant is soft and walked through, gone at a touch, pushed off by a
	 * piston, and dropped by its own rule: the root harvests the plant, the rest drop nothing.
	 */
	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, boolean drops) {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.PLANT)
			.noCollision()
			.noOcclusion()
			.instabreak()
			.sound(SoundType.CROP)
			.pushReaction(PushReaction.POPPED)
			.setId(blockKey(name));
		if (!drops) properties = properties.noLootTable();
		return Registry.register(BuiltInRegistries.BLOCK, blockKey(name), factory.apply(properties));
	}

	private static Item seeds(String name, Strain strain) {
		return item(name, new HempSeedsItem(CROP, strain, new Item.Properties()
			.setId(itemKey(name))
			.useItemDescriptionPrefix()
			.compostable(ContextIntProviders.COMPOSTABLE_LOW)));
	}

	private static Item item(String name, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, itemKey(name), item);
	}

	/** Blocks and items register as the class loads; this is what loads it. */
	static void register() {
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Main.id("hemp_plant"), PLANT);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
			.register(output -> output.insertAfter(Items.WHEAT_SEEDS, INDICA_SEEDS, SATIVA_SEEDS, HYBRID_SEEDS));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
			.register(output -> output.insertAfter(Items.WHEAT, HEMP, INDICA_FLOWER, SATIVA_FLOWER, HYBRID_FLOWER));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
			.register(output -> {
				output.insertAfter(Items.COOKIE, BROWNIE);
				output.insertAfter(Items.SUSPICIOUS_STEW, INDICA_JOINT, SATIVA_JOINT, HYBRID_JOINT);
			});
	}

	private static ResourceKey<Block> blockKey(String name) {
		return ResourceKey.create(Registries.BLOCK, Main.id(name));
	}

	private static ResourceKey<Item> itemKey(String name) {
		return ResourceKey.create(Registries.ITEM, Main.id(name));
	}
}
