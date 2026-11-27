package nz.temple.client.render;

import net.minecraft.client.Minecraft;

public final class UiScale {
	public static final float REFERENCE = 2;
	private UiScale() {
	}
	private static final float REFERENCE_W = 1920;
	private static final float REFERENCE_H = 1080;

	public static float factor() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return 1;
		}
		double scale = mc.getWindow().getGuiScale();
		if (scale <= 0) {
			return 1;
		}
		float fit = Math.min(mc.getWindow().getWidth() / REFERENCE_W, mc.getWindow().getHeight() / REFERENCE_H);
		fit = Math.max(0.45f, Math.min(2.5f, fit));
		return (float) (REFERENCE / scale) * fit;
	}
}
