package nz.temple.client.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import nz.temple.setting.EntitySelection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class BlockCatalog {
	private static List<EntityCatalog.Entry> entries;

	private BlockCatalog() {
	}

	public static synchronized List<EntityCatalog.Entry> entries() {
		if (entries == null) {
			List<EntityCatalog.Entry> list = new ArrayList<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
					continue;
				}
				String id = BuiltInRegistries.BLOCK.getKey(block).toString();
				list.add(new EntityCatalog.Entry(id, block.getName().getString(), block.asItem() == Items.AIR ? "no item" : "block"));
			}
			list.sort(Comparator.comparing(EntityCatalog.Entry::name));
			entries = List.copyOf(list);
		}
		return entries;
	}

	public static ItemStack icon(String blockId) {
		var block = BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.tryParse(blockId));
		if (block.isEmpty() || block.get().asItem() == Items.AIR) {
			return ItemStack.EMPTY;
		}
		return ItemCatalog.stack(BuiltInRegistries.ITEM.getKey(block.get().asItem()).toString());
	}

	public static Collection<String> matching(String pattern) {
		List<String> ids = new ArrayList<>();
		for (Block block : BlockList.parse(pattern)) {
			ids.add(BuiltInRegistries.BLOCK.getKey(block).toString());
		}
		return ids;
	}

	public static EntitySelection selection(String name, String defaults) {
		EntitySelection expanded = new EntitySelection(name, List.of()).blocks(BlockCatalog::matching);
		expanded.trySet(defaults);
		return new EntitySelection(name, expanded.get()).blocks(BlockCatalog::matching);
	}
}
