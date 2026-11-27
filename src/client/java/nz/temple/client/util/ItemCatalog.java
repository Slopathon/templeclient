package nz.temple.client.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemCatalog {
	private static List<EntityCatalog.Entry> entries;
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	private ItemCatalog() {
	}

	public static synchronized List<EntityCatalog.Entry> entries() {
		if (entries == null) {
			List<EntityCatalog.Entry> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) {
					continue;
				}
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				String name = new ItemStack(item).getHoverName().getString();
				list.add(new EntityCatalog.Entry(id, name, item instanceof BlockItem ? "block" : "item"));
			}
			list.sort(Comparator.comparing(EntityCatalog.Entry::name));
			entries = List.copyOf(list);
		}
		return entries;
	}

	public static ItemStack stack(String id) {
		return STACKS.computeIfAbsent(id, key -> {
			var item = BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.tryParse(key));
			return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
		});
	}
}
