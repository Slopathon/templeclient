package nz.temple.client.render;

import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;

public final class Theme {
	private static final int FALLBACK = 0xFF5AC8FA;
	private static HudModule hud;
	private Theme() {
	}

	private static HudModule hud() {
		if (hud == null && TempleNzClient.modules() != null) {
			hud = TempleNzClient.modules().get(HudModule.class);
		}
		return hud;
	}
	public static double time() {
		return System.nanoTime() / 1e9;
	}
	public static float intensity() {
		HudModule h = hud();
		return h == null ? 1f : h.effectIntensity.getFloat() / 100f;
	}
	public static int primary() {
		HudModule h = hud();
		return h == null ? FALLBACK : h.accent.get();
	}
	public static int secondary() {
		HudModule h = hud();
		return h == null ? 0xFFAF52DE : h.accent2.get();
	}

	public static int color(double offset) {
		HudModule h = hud();
		if (h == null) {
			return FALLBACK;
		}
		double t = time() * h.themeSpeed.get();
		return switch (h.themeMode.get()) {
			case "Gradient" -> Fx.lerp(h.accent.get(), h.accent2.get(), (Math.sin((t * 0.5 + offset) * Math.PI * 2) + 1) / 2);
			case "Rainbow" -> Fx.hue(t * 0.15 + offset);
			default -> h.accent.get();
		};
	}
	public static int flow(int a, int b, double offset) {
		HudModule h = hud();
		double speed = h == null ? 1 : h.themeSpeed.get();
		return Fx.lerp(a, b, (Math.sin((time() * speed * 0.5 + offset) * Math.PI * 2) + 1) / 2);
	}
}
