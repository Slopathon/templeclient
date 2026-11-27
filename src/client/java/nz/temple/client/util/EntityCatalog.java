package nz.temple.client.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EntityCatalog {
	public record Entry(String id, String name, String category) {
	}

	private static List<Entry> entries;

	private EntityCatalog() {
	}

	public static synchronized List<Entry> entries() {
		if (entries == null) {
			List<Entry> list = new ArrayList<>();
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				String id = EntityType.getKey(type).toString();
				list.add(new Entry(id, type.getDescription().getString(), type.getCategory().getName()));
			}
			list.sort(Comparator.comparing(Entry::category).thenComparing(Entry::name));
			entries = List.copyOf(list);
		}
		return entries;
	}

	public static List<String> idsInCategory(String category) {
		List<String> ids = new ArrayList<>();
		for (Entry e : entries()) {
			if (e.category().equals(category)) {
				ids.add(e.id());
			}
		}
		return ids;
	}
}
