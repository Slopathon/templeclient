package nz.temple.client.modules.hud;

import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.Setting;

/**
 * One HUD element (Info Bar, Coordinates, Hotbar and so on) as its own module in the HUD category: turning the module on or off
 * shows or hides the element, and the element's own options are the module's settings.
 */
public final class HudElementModule extends Module {
	private final BoolSetting shown;

	public HudElementModule(String name, String description, BoolSetting shown, Setting<?>... settings) {
		super(name, Category.HUD, description);
		this.shown = shown;
		for (Setting<?> setting : settings) {
			add(setting);
		}
		visible().set(false);
		setEnabled(shown.get());
		shown.addListener(() -> {
			if (isEnabled() != shown.get()) {
				setEnabled(shown.get());
			}
		});
	}

	/** The setting that shows the element. */
	public BoolSetting shown() {
		return shown;
	}

	/** Edited from the HUD editor (right-click an element), so it is not listed in the click GUI. */
	@Override
	public boolean listedInCategory() {
		return false;
	}

	@Override
	protected void onEnable() {
		shown.set(true);
	}

	@Override
	protected void onDisable() {
		shown.set(false);
	}
}
