package justfatlard.hemp_craft;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Hempcrete: hemp bound with clay and cast into a thin wall, which joins the hempcrete beside it
 * straight on or corner to corner (see {@link HempcreteBlock}).
 *
 * <p>Undyed it is a pale clay flecked with hurd. Dyed it is any of the sixteen dyes, made by putting
 * a dye and undyed hempcrete together in the crafting grid; dyed hempcrete can't be dyed again, so
 * the colour is the one it was made in.
 *
 * <p>The ids say paneling, the name it shipped under: worlds saved since hold those ids, and a
 * renamed id would load as air.
 */
public final class Hempcrete {

	/** Undyed: the pale clay of the binder. Its client stand-in is white wool. */
	public static final Block PLAIN = wall("hemp_paneling", MapColor.QUARTZ, "white");

	/** One per dye, in the order the dyes are listed. Each stands in as its dye's wool. */
	public static final List<Block> DYED = Arrays.stream(DyeColor.values())
		.map(dye -> wall(dye.getSerializedName() + "_hemp_paneling", dye.getMapColor(), dye.getSerializedName()))
		.toList();

	private Hempcrete() {}

	/** Blocks and items register as the class loads; this is what loads it. */
	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
			.register(output -> output.insertAfter(Items.PACKED_MUD, items()));
	}

	private static Item[] items() {
		return Stream.concat(Stream.of(PLAIN), DYED.stream())
			.map(Block::asItem)
			.toArray(Item[]::new);
	}

	/**
	 * A wall and its item, under one id: the item is meant to place the block, the way rope's
	 * does. The stand-in is the wool of the same colour, so a client without the mod sees the
	 * colour and nothing else of it.
	 */
	private static Block wall(String name, MapColor colour, String standIn) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Main.id(name));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Main.id(name));

		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new HempcreteBlock(BlockBehaviour.Properties.of()
			.mapColor(colour)
			.strength(1.0F)
			.sound(SoundType.WOOL)
			.setId(blockKey)));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties()
			.setId(itemKey)
			.useBlockDescriptionPrefix()));

		PandoricalApi.content().registerBlock(Main.MOD_ID + ":" + name, new BlockRegistration()
			.baseBlock("minecraft:" + standIn + "_wool")
			.strength(1.0F));
		PandoricalApi.content().registerItem(Main.MOD_ID + ":" + name, new ItemRegistration()
			.model(Main.MOD_ID + ":block/" + name + "_inventory"));
		return block;
	}
}
