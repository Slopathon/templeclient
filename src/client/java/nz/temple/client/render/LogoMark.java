package nz.temple.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;

public final class LogoMark {
	private static final float UNIT = 128f;
	private static final float[][] SHAPES = {
			{64, 4, 126, 50, 126, 68, 64, 24, 2, 68, 2, 50},
			{12, 72, 116, 72, 116, 88, 12, 88},
			{54, 88, 74, 88, 74, 108, 64, 124, 54, 108},
			{92, 88, 112, 88, 112, 106, 104, 114, 92, 114},
			{16, 88, 36, 88, 36, 100, 30, 100, 30, 107, 22, 107, 22, 99, 16, 99},
			{24, 110, 31, 110, 31, 116, 24, 116},
			{33, 118, 38, 118, 38, 123, 33, 123},
			{18, 120, 22, 120, 22, 124, 18, 124},
	};
	private LogoMark() {
	}

	/** The logo's outline as polygons ({x0, y0, x1, y1, ...}) on a 128-unit square. */
	static float[][] shapes() {
		return SHAPES;
	}
	private static final java.util.Map<String, Logo3D> SPINNING = new java.util.HashMap<>();
	private static final long START = System.nanoTime();

	/**
	 * The logo as a turning 3D gold object in the square where {@link #draw} would put the flat one. {@code site} names the place
	 * it is drawn (each gets its own texture).
	 */
	public static void draw3d(GuiGraphicsExtractor g, String site, float x, float y, float size, float alpha) {
		float grow = size * 1.17f;
		SPINNING.computeIfAbsent(site, Logo3D::new).draw(g, x - (grow - size) / 2, y - (grow - size) / 2, grow,
				(System.nanoTime() - START) / 1e9, alpha);
	}

	public static float width(float size) {
		return size;
	}
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/gui/logo.png");
	private static final int TEXTURE_SIZE = 192;
	/** The logo at 192, 96, 48 and 24 pixels: the smallest one at least as big as it is drawn is used, so it stays smooth. */
	private static final Identifier[] LEVELS = {TEXTURE,
			Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/gui/logo_96.png"),
			Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/gui/logo_48.png"),
			Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/gui/logo_24.png")};
	private static final int[] LEVEL_SIZES = {192, 96, 48, 24};
	public static void draw(GuiGraphicsExtractor g, float x, float y, float size, int argb) {
		Matrix3x2fStack pose = g.pose();
		// Whole screen pixels for position and size, so the logo is not resampled across pixel boundaries.
		float gui = (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
		x = Math.round(x * gui) / gui;
		y = Math.round(y * gui) / gui;
		size = Math.max(1, Math.round(size * gui)) / gui;
		pose.pushMatrix();
		pose.translate(x, y);
		blit(g, 0, 0, size, size, 0xFFFFFFFF);
		pose.popMatrix();
	}
	public static void drawSpinning(GuiGraphicsExtractor g, float x, float y, float size, int argb, double time) {
		double angle = time * 0.45;
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float face = Math.max(0.08f, Math.abs(cos));
		int alpha = (argb >>> 24) & 0xFF;
		if (alpha == 0) {
			alpha = 0xFF;
		}
		int side = Math.round(size);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + size / 2, y + size / 2);
		pose.scale(face, 1);
		pose.translate(-size / 2, -size / 2);
		int layers = 10;
		float depth = size * 0.1f * Math.abs(sin) / Math.max(face, 0.08f);
		float direction = Math.signum(sin) * Math.signum(cos == 0 ? 1 : cos);
		for (int i = layers; i >= 1; i--) {
			float offset = depth * i / layers * -direction;
			float shade = 0.20f + 0.55f * (1 - (float) i / layers);
			pose.pushMatrix();
			pose.translate(offset, 0);
			blit(g, 0, 0, side, side, alpha << 24 | shade(shade));
			pose.popMatrix();
		}
		blit(g, 0, 0, side, side, alpha << 24 | 0xFFFFFF);
		pose.popMatrix();

		float gleam = Math.max(0, cos) * Math.abs(sin);
		if (gleam > 0.05f) {
			float band = Math.max(1, size * face * 0.18f);
			float gx = x + size / 2 + size * face * 0.45f * sin - band / 2;
			g.fill(Math.round(gx), Math.round(y + size / 6), Math.round(gx + band), Math.round(y + size - size / 6),
					Math.round(gleam * 38) << 24 | 0xFFFFFF);
		}
	}
	/** Draws the logo {@code size} units wide at (x, y), from the level that fits its size on screen. */
	private static void blit(GuiGraphicsExtractor g, float x, float y, float w, float h, int tint) {
		int level = MipLevels.pick(LEVEL_SIZES, MipLevels.screenPixels(g, w));
		int tex = LEVEL_SIZES[level];
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w / tex, h / tex);
		g.blit(RenderPipelines.GUI_TEXTURED, LEVELS[level], 0, 0, 0, 0, tex, tex, tex, tex, tex, tex, tint);
		pose.popMatrix();
	}

	private static int shade(float brightness) {
		int v = Math.max(0, Math.min(255, Math.round(brightness * 255)));
		return v << 16 | v << 8 | v;
	}
	public static void drawGeometry(GuiGraphicsExtractor g, float x, float y, float size, int argb) {
		float scale = size / UNIT;
		for (float[] shape : SHAPES) {
			fillPolygon(g, shape, x, y, scale, argb);
		}
	}

	private static int tint(int argb) {
		int alpha = (argb >>> 24) & 0xFF;
		return (alpha == 0 ? 0xFF : alpha) << 24 | 0xFFFFFF;
	}

	private static void fillPolygon(GuiGraphicsExtractor g, float[] poly, float ox, float oy, float scale, int argb) {
		int points = poly.length / 2;
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int i = 0; i < points; i++) {
			float py = oy + poly[i * 2 + 1] * scale;
			minY = Math.min(minY, py);
			maxY = Math.max(maxY, py);
		}
		int y0 = (int) Math.floor(minY);
		int y1 = (int) Math.ceil(maxY);
		float[] crossings = new float[points];
		for (int y = y0; y < y1; y++) {
			float sampleY = y + 0.5f;
			int found = 0;
			for (int i = 0; i < points; i++) {
				int j = (i + 1) % points;
				float ay = oy + poly[i * 2 + 1] * scale;
				float by = oy + poly[j * 2 + 1] * scale;
				if (ay == by || sampleY < Math.min(ay, by) || sampleY >= Math.max(ay, by)) {
					continue;
				}
				float ax = ox + poly[i * 2] * scale;
				float bx = ox + poly[j * 2] * scale;
				crossings[found++] = ax + (sampleY - ay) / (by - ay) * (bx - ax);
			}
			if (found < 2) {
				continue;
			}
			java.util.Arrays.sort(crossings, 0, found);
			for (int i = 0; i + 1 < found; i += 2) {
				int left = Math.round(crossings[i]);
				int right = Math.round(crossings[i + 1]);
				if (right > left) {
					g.fill(left, y, right, y + 1, argb);
				}
			}
		}
	}
}
