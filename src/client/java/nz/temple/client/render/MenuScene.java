package nz.temple.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class MenuScene {
	private MenuScene() {
	}
	private static final int SPARKS = 64;
	private static final float[] SPARK_X = new float[SPARKS];
	private static final float[] SPARK_Y = new float[SPARKS];
	private static final float[] SPARK_VX = new float[SPARKS];
	private static final float[] SPARK_VY = new float[SPARKS];
	private static final long[] SPARK_BORN = new long[SPARKS];
	private static final float[] SPARK_SEED = new float[SPARKS];
	private static int nextSpark;
	private static float lastMouseX = -1;
	private static float lastMouseY = -1;
	private static long lastSparkTime;
	public static void pointer(float mx, float my) {
		long now = System.nanoTime();
		float dt = lastSparkTime == 0 ? 0 : Math.min(0.1f, (now - lastSparkTime) / 1e9f);
		lastSparkTime = now;
		if (lastMouseX < 0) {
			lastMouseX = mx;
			lastMouseY = my;
			return;
		}
		float moved = (float) Math.hypot(mx - lastMouseX, my - lastMouseY);
		lastMouseX = mx;
		lastMouseY = my;
		if (moved < 1.5f || dt <= 0) {
			return;
		}
		int count = (int) Math.min(3, 1 + moved / 18);
		for (int i = 0; i < count; i++) {
			int k = nextSpark;
			nextSpark = (nextSpark + 1) % SPARKS;
			float jitter = (float) (Math.random() - 0.5);
			SPARK_X[k] = mx + jitter * 6;
			SPARK_Y[k] = my + (float) (Math.random() - 0.5) * 6;
			SPARK_VX[k] = (float) (Math.random() - 0.5) * 26 - (mx - lastMouseX) * 2;
			SPARK_VY[k] = (float) (Math.random() - 0.5) * 26 - 14;
			SPARK_BORN[k] = now;
			SPARK_SEED[k] = (float) Math.random();
		}
	}
	private static void sparkles(GuiGraphicsExtractor g, double t) {
		long now = System.nanoTime();
		for (int i = 0; i < SPARKS; i++) {
			if (SPARK_BORN[i] == 0) {
				continue;
			}
			double age = (now - SPARK_BORN[i]) / 1e9;
			double life = 0.9 + SPARK_SEED[i] * 0.8;
			if (age > life) {
				SPARK_BORN[i] = 0;
				continue;
			}
			float p = (float) (age / life);
			float x = SPARK_X[i] + SPARK_VX[i] * (float) age * (1 - p * 0.55f);
			float y = SPARK_Y[i] + SPARK_VY[i] * (float) age * (1 - p * 0.55f) + 26 * (float) (age * age);
			float twinkle = (float) (0.55 + 0.45 * Math.sin(t * 14 + SPARK_SEED[i] * 12));
            float fade = (1 - p) * (1 - p);
			int alpha = Math.max(0, Math.min(255, Math.round(fade * twinkle * 235)));
			if (alpha < 6) {
				continue;
			}
			int colour = SPARK_SEED[i] > 0.75f ? 0xFFFFFF : 0xFFE3A1;
			int ix = Math.round(x);
			int iy = Math.round(y);
			int size = SPARK_SEED[i] > 0.9f ? 2 : 1;
			g.fill(ix, iy, ix + size, iy + size, alpha << 24 | colour);
			if (SPARK_SEED[i] > 0.82f && alpha > 90) {
				int soft = alpha / 3 << 24 | colour;
				g.fill(ix - 2, iy, ix + size + 2, iy + 1, soft);
				g.fill(ix, iy - 2, ix + 1, iy + size + 2, soft);
			}
		}
	}
	public static void draw(GuiGraphicsExtractor g, String theme, int w, int h) {
		double t = (System.nanoTime() % 600_000_000_000L) / 1e9;
		boolean hq = nz.temple.client.modules.client.MenuModule.sceneHighQuality();
		if (nz.temple.client.render.title.SongBackdrop.active()) {
			nz.temple.client.render.title.SongBackdrop.request(1);
			if (nz.temple.client.render.title.SongBackdrop.covers()) {
				return;
			}
		}
		if ("Black hole".equals(theme)) {
			nz.temple.client.render.title.BlackHoleScene hole = nz.temple.client.render.title.BlackHoleScene.get();
			if (hole.available()) {
				hole.request(1f, 0.18f, hq);
				return;
			}
		} else {
			nz.temple.client.render.title.TitleScene scene = nz.temple.client.render.title.TitleScene.get();
			if (scene.available()) {
				scene.request(0.5f, 1f, 0.18f, hq);
				return;
			}
		}
		temple(g, t, w, h);
		vignette(g, w, h);
		sparkles(g, t);
	}
	private static void temple(GuiGraphicsExtractor g, double t, int w, int h) {
		g.fillGradient(0, 0, w, h, 0xFF06070E, 0xFF120C06);
		stars(g, t, w, h, 220, 1f);
		int horizon = (int) (h * 0.72);
		g.fillGradient(0, horizon, w, h, 0x00E9B949, 0x3AE9B949);
		g.fillGradient(0, horizon - h / 6, w, horizon, 0x00E9B949, 0x1AE9B949);
		float size = Math.min(w, h) * 0.42f;
		float lift = (float) Math.sin(t * 0.35) * size * 0.02f;
		LogoMark.draw(g, w * 0.62f, h * 0.30f + lift, size, 0x2CFFFFFF);
	}
	private static void stars(GuiGraphicsExtractor g, double t, int w, int h, int count, float brightness) {
		for (int i = 0; i < count; i++) {
			int x = (int) (hash(i * 7 + 1) * w);
			int y = (int) (hash(i * 11 + 3) * h);
			double twinkle = 0.45 + 0.55 * Math.sin(t * (0.6 + hash(i * 3) * 1.6) + i);
			int alpha = Math.max(0, (int) (twinkle * 150 * brightness));
			int size = hash(i * 17) > 0.93 ? 2 : 1;
			g.fill(x, y, x + size, y + size, (alpha << 24) | 0xFFFFFF);
		}
	}
	private static void vignette(GuiGraphicsExtractor g, int w, int h) {
		int band = Math.max(24, h / 6);
		g.fillGradient(0, 0, w, band, 0x66000000, 0x00000000);
		g.fillGradient(0, h - band, w, h, 0x00000000, 0x77000000);
	}

	private static double hash(int i) {
		double v = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
		return v - Math.floor(v);
	}
}
