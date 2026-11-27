package nz.temple.client.modules.render;

import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class MotionBlurModule extends Module {
	private final NumberSetting strength;

	public MotionBlurModule() {
		super("Motion Blur", Category.RENDER, "Adds a short motion blur to fast camera turns and movement. Your hand and the HUD are not "
				+ "blurred.");
		strength = add(new NumberSetting("Strength", 55, 10, 90, 5, "%")
				.describe("Blur strength. Looks the same at any frame rate."));
	}

	public double strength() {
		return isEnabled() ? strength.get() / 100 : -1;
	}
}
