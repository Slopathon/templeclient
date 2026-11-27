package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;

public final class VisualRange {
	private VisualRange() {
	}
	public static double of(double setting) {
		var modules = TempleNzClient.modules();
		HudModule hud = modules == null ? null : modules.get(HudModule.class);
		if (hud == null || !hud.visualReach.get()) {
			return setting;
		}
		int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
		return Math.max(setting, chunks * 16.0 + 8);
	}
}
