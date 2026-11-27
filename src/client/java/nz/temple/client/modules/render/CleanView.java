package nz.temple.client.modules.render;

import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;

import java.util.List;

public final class CleanView extends Module {
	private static CleanView instance;
	private final BoolSetting vignette;
	private final BoolSetting pumpkin;
	private final BoolSetting portal;
	private final ModeSetting fire;

	public CleanView() {
		super("Clean View", Category.RENDER, "Removes screen clutter: the vignette, pumpkin overlay, nether portal effect and fire overlay.");
		vignette = add(new BoolSetting("No vignette", true).describe("Removes the darkening at the edges of the screen."));
		pumpkin = add(new BoolSetting("No pumpkin mask", true).describe("Removes the overlay from wearing a carved pumpkin."));
		portal = add(new BoolSetting("No portal swirl", true).describe("Removes the tint and distortion from nether portals and nausea."));
		fire = add(new ModeSetting("Fire", List.of("Low", "Hidden", "Vanilla"), "Low")
				.describe("Low moves the flames to the bottom of the screen. Hidden removes them. Vanilla is unchanged."));
		instance = this;
	}

	private static CleanView active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	public static boolean noVignette() {
		CleanView c = active();
		return c != null && c.vignette.get();
	}

	public static boolean noPumpkin() {
		CleanView c = active();
		return c != null && c.pumpkin.get();
	}

	public static boolean noPortal() {
		CleanView c = active();
		return c != null && c.portal.get();
	}

	public static float fireDrop() {
		CleanView c = active();
		if (c == null || c.fire.is("Vanilla")) {
			return 0;
		}
		return c.fire.is("Hidden") ? -1 : 0.35f;
	}
}
