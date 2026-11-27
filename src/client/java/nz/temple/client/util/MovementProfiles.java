package nz.temple.client.util;

import nz.temple.client.TempleNzClient;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;

import java.util.List;
import java.util.Set;

public final class MovementProfiles {
	private static final List<Set<String>> CONFLICTS = List.of(
			Set.of("Fly", "Noclip", "Glide", "Elytra Boost", "Fast Fall", "High Jump", "Long Jump"),
			Set.of("Speed", "Ground Strafe", "Long Jump"),
			Set.of("Scaffold", "Tower"), Set.of("Spider", "Fast Ladder"));
	private static final Set<String> CORE = Set.of("Safe Walk", "Parkour", "Step", "Speed", "Fly", "Elytra Boost");
	private MovementProfiles() {
	}
	public static boolean core(Module module) {
		return CORE.contains(module.name());
	}
	public static void register(ModuleManager modules) {
		modules.addToggleListener(enabled -> {
			if (!enabled.isEnabled()) {
				return;
			}
			for (Set<String> family : CONFLICTS) {
				if (!family.contains(enabled.name())) {
					continue;
				}
				for (Module other : modules.all()) {
					if (other != enabled && family.contains(other.name()) && other.isEnabled()) {
						other.setEnabled(false);
						if (TempleNzClient.notifications() != null) {
							TempleNzClient.notifications().info(other.name() + " turned off",
									"It fights " + enabled.name() + " over your movement; only one can run at a time.");
						}
					}
				}
			}
		});
	}
}
