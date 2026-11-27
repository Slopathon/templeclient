package nz.temple.client.render;

import net.minecraft.client.gui.screens.Screen;

public final class ScreenTransition {
	private static Screen current;
	private static long started;
	private ScreenTransition() {
	}

	public static float progress(Screen screen, float durationMillis) {
		if (screen != current) {
			current = screen;
			started = System.nanoTime();
		}
		if (durationMillis <= 0) {
			return 1;
		}
		float t = (float) ((System.nanoTime() - started) / 1e6 / durationMillis);
		if (t >= 1) {
			return 1;
		}
		t = Math.max(0, t);
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}
}
