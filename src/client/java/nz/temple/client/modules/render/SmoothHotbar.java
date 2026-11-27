package nz.temple.client.modules.render;

import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class SmoothHotbar extends Module {
	private static SmoothHotbar instance;
	private final NumberSetting speed;
	private float shown = Float.NaN;
	private long last;

	public SmoothHotbar() {
		super("Smooth Hotbar", Category.RENDER, "The hotbar selection slides to the new slot instead of jumping.");
		speed = add(new NumberSetting("Glide speed", 18, 4, 40, 1, "")
				.describe("How fast the selection moves. Higher is faster."));
		instance = this;
	}

	public static SmoothHotbar active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	public float x(int target) {
		long now = System.nanoTime();
		float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		if (Float.isNaN(shown) || dt == 0) {
			shown = target;
		}
		shown += (target - shown) * (1 - (float) Math.exp(-speed.getFloat() * dt));
		if (Math.abs(target - shown) < 0.02f) {
			shown = target;
		}
		return shown;
	}
}
