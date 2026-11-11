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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;

/**
 * Hemp paneling: the fibre pressed flat into a plain building block, six hemp to a panel.
 *
 * <p>Undyed it is an off-white. Dyed it is any of the sixteen dyes, made by putting a dye and an
 * undyed panel together in the crafting grid; a dyed panel can't be dyed again, so the colour is
 * the one it was made in.
 */
public final class Paneling {

	/** Undyed: the off-white of the fibre itself. Its client stand-in is white wool. */
	public static final Block PLAIN = panel("hemp_paneling", MapColor.QUARTZ, "white");

	/** One panel per dye, in the order the dyes are listed. Each stands in as its dye's wool. */
	public static final List<Block> DYED = Arrays.stream(DyeColor.values())
		.map(dye -> panel(dye.getSerializedName() + "_hemp_paneling", dye.getMapColor(), dye.getSerializedName()))
		.toList();

	private Paneling() {}

	/** Every panel comes back whole; there is nothing to lose by taking it down. */
	private static final class Panel extends Block {
		Panel(Properties properties) {
			super(properties);
		}

		@Override
		protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
			return List.of(new ItemStack(this));
		}
	}

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
	 * A panel and its item, under one id: the item is meant to place the block, the way rope's
	 * does. The stand-in is the wool of the same colour, so a client without the mod sees the
	 * colour and nothing else of it.
	 */
	private static Block panel(String name, MapColor colour, String standIn) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Main.id(name));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Main.id(name));

		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new Panel(BlockBehaviour.Properties.of()
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
			.model(Main.MOD_ID + ":block/" + name));
		return block;
	}
}
