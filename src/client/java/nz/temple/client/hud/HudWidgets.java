package nz.temple.client.hud;

import java.util.ArrayList;
import java.util.List;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.render.Icons;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.Setting;

/**
 * The HUD widgets the HUD editor can move and switch on or off: each one's id (as HudOverlay places it), its name, and
 * the setting or module that shows it.
 */
public final class HudWidgets {
	public record Widget(String id, String name, Icons.Icon icon, java.util.function.BooleanSupplier on, java.util.function.Consumer<Boolean> set) {
		public boolean shown() {
			try {
				return on.getAsBoolean();
			} catch (RuntimeException e) {
				return false;
			}
		}
	}

	private HudWidgets() {
	}

	/** Every widget, in the order the add menu lists them. Widgets whose module is missing are left out. */
	public static List<Widget> all() {
		List<Widget> out = new ArrayList<>();
		var modules = TempleNzClient.modules();
		HudModule hud = modules == null ? null : modules.get(HudModule.class);
		if (hud == null) {
			return out;
		}
		out.add(hudSetting("watermark", Icons.Icon.MONITOR, "Info bar", hud.watermark));
		out.add(hudSetting("modules", Icons.Icon.MENU_STYLE, "Module list", hud.moduleList));
		out.add(hudSetting("target", Icons.Icon.CROSSHAIR, "Target HUD", hud.targetHud));
		out.add(hudSetting("keybinds", Icons.Icon.KEYBOARD, "Keybinds", hud.keybinds));
		out.add(hudSetting("potions", Icons.Icon.DROPLET, "Effects list", hud.potions));
		out.add(hudSetting("armor", Icons.Icon.SHIELD, "Armor", hud.armor));
		out.add(hudSetting("lyrics", Icons.Icon.MUSIC, "Lyrics", hud.lyricsBar));
		out.add(hudSetting("keystrokes", Icons.Icon.KEYBOARD, "Keystrokes", hud.keystrokes));
		out.add(hudSetting("info", Icons.Icon.NAVIGATION, "Coordinates", hud.coordinates));
		out.add(hudSetting("speedgraph", Icons.Icon.GAUGE, "Speed graph", hud.speedGraph));
		out.add(hudSetting("elytra", Icons.Icon.ROCKET, "Elytra HUD", hud.elytraHud));
		out.add(hudSetting("enemies", Icons.Icon.USERS, "Enemy armor", hud.enemyArmor));
		addModuleWidget(out, "minimap", Icons.Icon.MOUNTAIN, "Minimap", "Map", "Minimap");
		addModuleWidget(out, "music", Icons.Icon.MUSIC, "Now playing", "Music", "Now playing card");
		addModuleWidget(out, "blockinfo", Icons.Icon.PACKAGE, "Block info", "Block Highlight", "Block info");
		addModuleWidget(out, "staff", Icons.Icon.USER, "Staff list", "Staff Display", null);
		addModuleWidget(out, "region", Icons.Icon.GLOBE, "Region", "Donut Region", null);
		return out;
	}

	public static Widget byId(String id) {
		for (Widget w : all()) {
			if (w.id().equals(id)) {
				return w;
			}
		}
		return null;
	}

	private static Widget hudSetting(String id, Icons.Icon icon, String name, BoolSetting setting) {
		return new Widget(id, name, icon, setting::get, setting::set);
	}

	/** A widget drawn by a module: shown when the module is on (and its own HUD setting, if it has one, is on). */
	private static void addModuleWidget(List<Widget> out, String id, Icons.Icon icon, String name, String moduleName, String settingName) {
		var modules = TempleNzClient.modules();
		Module module = modules == null ? null : modules.byName(moduleName);
		if (module == null) {
			return;
		}
		Setting<?> s = settingName == null ? null : module.setting(settingName);
		BoolSetting toggle = s instanceof BoolSetting b ? b : null;
		out.add(new Widget(id, name, icon,
				() -> module.isEnabled() && (toggle == null || toggle.get()),
				on -> {
					if (on) {
						if (toggle != null) {
							toggle.set(true);
						}
						module.setEnabled(true);
					} else if (toggle != null) {
						toggle.set(false);
					} else {
						module.setEnabled(false);
					}
				}));
	}
}
