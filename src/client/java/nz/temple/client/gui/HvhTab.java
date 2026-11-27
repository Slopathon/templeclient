package nz.temple.client.gui;

import java.util.Set;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;

/**
 * The HvH tab: the modules in {@link #MODULES} are moved into it at startup, whatever tab they were made for. Every
 * other module that was made for the HvH tab goes to Combat. The tab can be hidden with Menu → "Show HvH tab"; hiding
 * it only hides the tab, it never turns modules off.
 *
 * To move a module into or out of the HvH tab, add or remove its name (exactly as shown in the menu) below.
 */
public final class HvhTab {
	public static final Set<String> MODULES = Set.of(
			// Combat
			"Velocity", "Reach", "Criticals", "Auto Web", "Auto Trap", "Anchor Aura", "Hole Filler", "Surround",
			"Pearl Trap", "TP Aura", "Crystal Aura",
			// Movement
			"Blink", "Boat Fly", "Elytra Autopilot", "Elytra Boost", "Entity Speed", "Fast Fall", "Fast Ladder", "Fly",
			"Glide", "Ground Strafe", "High Jump", "Jesus", "No Fall", "Parkour", "Timer", "Step", "Spider",
			"Reverse Step", "No Slow", "Noclip", "Long Jump",
			// World
			"Air Place", "Auto Farm", "Auto Tunnel", "Flatten", "Bed Breaker", "Baritone", "Nuker", "Scaffold",
			"Vein Miner", "Tower", "Speed Mine", "No Block Interact");

	private HvhTab() {
	}

	/** Puts every module in its tab. Called once, after all modules are registered. */
	public static void apply(ModuleManager modules) {
		for (Module m : modules.all()) {
			if (MODULES.contains(m.name())) {
				m.moveTo(Category.HVH);
			} else if (m.category() == Category.HVH) {
				m.moveTo(Category.COMBAT);
			}
		}
	}

	public static boolean shown() {
		var modules = TempleNzClient.modules();
		MenuModule menu = modules == null ? null : modules.get(MenuModule.class);
		return menu == null || menu.showHvhTab();
	}

	/** Whether the menus list this module (false only for HvH modules while the HvH tab is hidden). */
	public static boolean listed(Module m) {
		return m.category() != Category.HVH || shown();
	}
}
