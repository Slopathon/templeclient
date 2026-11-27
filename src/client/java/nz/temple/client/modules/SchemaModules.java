package nz.temple.client.modules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Name lookup for schema-configured modules (they reference each other by name) and their start-of-tick hook. */
public final class SchemaModules {
	/** Menu tab for modules that are not listed in their own category (Combat, Movement, Client, or Player for utility). */
	static final Map<String, nz.temple.module.Category> CATEGORY = Map.of(
			"Auto Crystal", nz.temple.module.Category.HVH,
			"Auto Hit Crystal", nz.temple.module.Category.HVH,
			"Auto Anchor", nz.temple.module.Category.HVH,
			"Cart Assist", nz.temple.module.Category.HVH,
			"Auto Xbow", nz.temple.module.Category.HVH,
			"Auto Trap", nz.temple.module.Category.PLAYER);
	private static final Map<String, SchemaModule> modules = new LinkedHashMap<>();
	private static boolean tickRegistered;

	private SchemaModules() {
	}

	public static void register(SchemaModule module) {
		modules.put(module.getName(), module);
	}

	public static SchemaModule get(String name) {
		return modules.get(name);
	}

	public static List<SchemaModule> all() {
		return List.copyOf(modules.values());
	}

	public static void registerTickEvent() {
		if (tickRegistered) {
			return;
		}
		tickRegistered = true;
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			for (SchemaModule m : modules.values()) {
				if (m.isEnabled()) {
					try {
						m.onTick();
					} catch (Throwable ignored) {
					}
				}
			}
		});
	}
}
