package nz.temple.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.FontDescription;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;

public final class Ui {
	private static float textNudge = 0f;
	public static final float CAP_DROP = 0.23f;
	private static final int FALLBACK_GLASS = 0xB01C2026;
	private final GlassRenderer glass;
	private final ArrayDeque<float[]> clipStack = new ArrayDeque<>();
	private GuiGraphicsExtractor g;
	private float tx;
	private float ty;
	private float sx = 1;
	private float sy = 1;
	private float alpha = 1;
	private float master = 1;
	private float[] clip;
	private final java.util.List<float[]> mask = new java.util.ArrayList<>();
	private float maskFade = 1;
	private boolean masking;
	public Ui(GlassRenderer glass) {
		this.glass = glass;
	}
	/** While set (during the HUD pass), glass panels are drawn in this theme's look. */
	private VisualTheme panelTheme;

	public void panelTheme(VisualTheme theme) {
		panelTheme = theme;
	}

	public void begin(GuiGraphicsExtractor graphics) {
		g = graphics;
		panelTheme = null;
		resetTransform();
		alpha = 1;
		master = 1;
		clip = null;
		clipStack.clear();
		masking = false;
	}

	public void setMask(java.util.List<float[]> areas, float fade) {
		mask.clear();
		mask.addAll(areas);
		maskFade = Math.max(0, Math.min(1, fade));
	}
	public void masking(boolean on) {
		masking = on;
	}
	private boolean maskedOut(float x0, float y0, float x1, float y1) {
		for (float[] m : mask) {
			if (x0 < m[2] && m[0] < x1 && y0 < m[3] && m[1] < y1) {
				return true;
			}
		}
		return false;
	}
	public GuiGraphicsExtractor graphics() {
		return g;
	}
	public GlassRenderer glassRenderer() {
		return glass;
	}
	public void transform(float translateX, float translateY, float scaleX, float scaleY) {
		tx = translateX;
		ty = translateY;
		sx = scaleX;
		sy = scaleY;
	}
	public void resetTransform() {
		tx = 0;
		ty = 0;
		sx = 1;
		sy = 1;
	}
	public void alpha(float a) {
		alpha = Math.max(0, Math.min(1, a)) * master;
	}

	public float alpha() {
		return alpha;
	}
	public void master(float m) {
		float was = master;
		master = Math.max(0, Math.min(1, m));
		alpha = was > 0 ? alpha / was * master : master;
	}

	public float mapX(float x) {
		return tx + x * sx;
	}

	public float mapY(float y) {
		return ty + y * sy;
	}
	public float unmapX(double screenX) {
		return (float) ((screenX - tx) / sx);
	}
	public float unmapY(double screenY) {
		return (float) ((screenY - ty) / sy);
	}

	public void clip(float x0, float y0, float x1, float y1) {
		clip(x0, y0, x1, y1, false);
	}

	public void clip(float x0, float y0, float x1, float y1, boolean inner) {
		float[] next = {mapX(x0), mapY(y0), mapX(x1), mapY(y1)};
		if (inner) {
			next[0] = (float) Math.ceil(next[0]);
			next[1] = (float) Math.ceil(next[1]);
			next[2] = (float) Math.floor(next[2]);
			next[3] = (float) Math.floor(next[3]);
		}
		if (clip != null) {
			next[0] = Math.max(next[0], clip[0]);
			next[1] = Math.max(next[1], clip[1]);
			next[2] = Math.min(next[2], clip[2]);
			next[3] = Math.min(next[3], clip[3]);
			clipStack.push(clip);
		}
		clip = next;
		g.enableScissor((int) Math.floor(clip[0]), (int) Math.floor(clip[1]), (int) Math.ceil(clip[2]), (int) Math.ceil(clip[3]));
	}
	public void unclip() {
		if (clip != null) {
			clip = clipStack.isEmpty() ? null : clipStack.pop();
			g.disableScissor();
		}
	}
	public float corner(float radius) {
		return radius * glass.roundness();
	}
	/** Paints a whole panel in place of {@link #glass}: the HUD uses it to give every widget its own panel material. */
	public interface PanelPainter {
		void paint(Ui ui, float x, float y, float w, float h, float radius);
	}

	private PanelPainter painter;

	public void panelPainter(PanelPainter painter) {
		this.painter = painter;
	}

	public void glass(float x, float y, float w, float h, float radius) {
		PanelPainter p = painter;
		if (p != null) {
			painter = null;
			try {
				p.paint(this, x, y, w, h, radius);
			} finally {
				painter = p;
			}
			return;
		}
		VisualTheme theme = panelTheme;
		if (theme != null && theme != VisualTheme.GLASS) {
			panelTheme = null;
			try {
				theme.hudPanel(this, x, y, w, h, radius);
			} finally {
				panelTheme = theme;
			}
			return;
		}
		float X = mapX(x);
		float Y = mapY(y);
		float W = w * sx;
		float H = h * sy;
		if (glass.available() && glass.hasRoom(1, 0)) {
			float round = Math.min(radius * glass.roundness(), Math.min(w, h) / 2);
			glass.glass(X, Y, W, H, round * Math.min(sx, sy), alpha);
		} else {
			g.fill(Math.round(X), Math.round(Y), Math.round(X + W), Math.round(Y + H), fade(FALLBACK_GLASS));
		}
	}
	/** A HUD glass panel (see {@link GlassRenderer#hudGlass}). */
	public void hudGlass(float x, float y, float w, float h, float radius) {
		float X = mapX(x);
		float Y = mapY(y);
		float W = w * sx;
		float H = h * sy;
		if (glass.available() && glass.hasRoom(1, 0)) {
			glass.hudGlass(X, Y, W, H, Math.min(radius, Math.min(w, h) / 2) * Math.min(sx, sy), alpha);
		} else {
			g.fill(Math.round(X), Math.round(Y), Math.round(X + W), Math.round(Y + H), fade(0xD90A1424));
		}
	}

	private boolean textShadow = true;

	/** Whether text gets its soft drop shadow (on by default). */
	public void textShadow(boolean on) {
		textShadow = on;
	}

	public void lens(float x, float y, float w, float h, float radius, float opacity, float boost) {
		float X = mapX(x);
		float Y = mapY(y);
		float W = w * sx;
		float H = h * sy;
		if (clip != null && (X > clip[2] || Y > clip[3] || X + W < clip[0] || Y + H < clip[1])) {
			return;
		}
		if (glass.available()) {
			glass.lens(X, Y, W, H, radius * Math.min(sx, sy), alpha * opacity, boost);
		} else {
			g.fill(Math.round(X), Math.round(Y), Math.round(X + W), Math.round(Y + H), fade(0x40FFFFFF));
		}
	}
	public void sheet(int argb) {
		int c = fade(argb);
		if ((c >>> 24) == 0) {
			return;
		}
		if (glass.available() && glass.hasRoom(0, 1)) {
			glass.sheet(0, 0, g.guiWidth(), g.guiHeight(), c);
		} else {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), c);
		}
	}
	public void fill(float x, float y, float w, float h, float radius, int argb) {
		shape(x, y, w, h, radius, argb, 0, 0);
	}
	/** A fill whose colour runs from {@code from} to {@code to}: mode 1 top to bottom, 2 left to right, 3 diagonal. */
	public void gradient(float x, float y, float w, float h, float radius, int from, int to, int mode) {
		gradientShape(x, y, w, h, radius, from, to, mode, 0);
	}

	/** An outline whose colour runs from {@code from} to {@code to} (see {@link #gradient}). */
	public void gradientRing(float x, float y, float w, float h, float radius, float width, int from, int to, int mode) {
		gradientShape(x, y, w, h, radius, from, to, mode, width);
	}

	private void gradientShape(float x, float y, float w, float h, float radius, int from, int to, int mode, float ring) {
		float X = mapX(x);
		float Y = mapY(y);
		float W = w * sx;
		float H = h * sy;
		if (clip != null && (X > clip[2] || Y > clip[3] || X + W < clip[0] || Y + H < clip[1])) {
			return;
		}
		float k = Math.min(sx, sy);
		if (glass.available() && glass.hasRoom(0, 1)) {
			glass.gradientRect(X, Y, W, H, radius * k, fade(from), fade(to), mode, ring * k, 0, clip);
		} else if (ring == 0) {
			g.fill(Math.round(X), Math.round(Y), Math.round(X + W), Math.round(Y + H), fade(lerpColor(from, to, 0.5f)));
		}
	}

	public void ring(float x, float y, float w, float h, float radius, float width, int argb) {
		shape(x, y, w, h, radius, argb, width, 0);
	}
	public void shadow(float x, float y, float w, float h, float radius, float softness, int argb) {
		shape(x, y, w, h, radius, argb, 0, softness);
	}
	private void shape(float x, float y, float w, float h, float radius, int argb, float ring, float softness) {
		float X = mapX(x);
		float Y = mapY(y);
		float W = w * sx;
		float H = h * sy;
		int c = fade(argb);
		if (clip != null && (X > clip[2] || Y > clip[3] || X + W < clip[0] || Y + H < clip[1])) {
			return;
		}
		float k = Math.min(sx, sy);
		if (glass.available() && glass.hasRoom(0, 1)) {
			glass.rect(X, Y, W, H, radius * k, c, ring * k, softness * k, clip);
		} else if (ring == 0 && softness == 0) {

			g.fill(Math.round(X), Math.round(Y), Math.round(X + W), Math.round(Y + H), c);
		}
	}

	public void text(String s, float x, float y, float size, int argb, FontDescription font) {
		int c = fade(argb);
		float k = size / Fonts.DESIGN_SIZE;
		if (masking) {
			float x0 = mapX(x);
			float y0 = mapY(y);
			if (maskedOut(x0, y0, x0 + Fonts.width(s, font, size) * sx, y0 + Minecraft.getInstance().font.lineHeight * k * sy)) {
				return;
			}
			c = (Math.round((c >>> 24) * maskFade) << 24) | (c & 0xFFFFFF);
		}
		int a = c >>> 24;
		if (a < 4) {
			return;
		}
		Matrix3x2fStack pose = g.pose();
		var mcFont = Minecraft.getInstance().font;
		float gui = guiScale();
		float poseScale = poseScale();
		float px = gui * poseScale;
		float tx = mapX(x);
		float ty = mapY(y);
		float scale;
		var component = Fonts.text(s, Fonts.sized(font, screenPixels(size)));
		if (Fonts.hasRasters(font)) {
			// Draw at exactly a pre-rendered size and on whole screen pixels, so each glyph texel lands on one pixel.
			int raster = Fonts.raster(size * sy * px);
			component = Fonts.text(s, Fonts.cut(font, raster));
			scale = raster / (Fonts.DESIGN_SIZE * px);
			tx = snap(tx, pose.m00(), pose.m20(), gui);
			ty = snap(ty, pose.m11(), pose.m21(), gui);
		} else {
			scale = k * sx;
		}
		float shadow = Math.max(1, Math.round(size * sy * px / 14f)) / px;
		if (textShadow) {
			pose.pushMatrix();
			pose.translate(tx, ty + shadow);
			pose.scale(scale, scale * sy / sx);
			g.text(mcFont, component, 0, 0, Math.round(a * 0.28f) << 24, false);
			pose.popMatrix();
		}
		pose.pushMatrix();
		pose.translate(tx, ty);
		pose.scale(scale, scale * sy / sx);
		g.text(mcFont, component, 0, 0, c, false);
		pose.popMatrix();
	}

	private static float guiScale() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	/** Scale of the current pose (1 unless a caller scaled the matrix stack). */
	private float poseScale() {
		var m = g.pose();
		return (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
	}

	/** Moves a coordinate (in the pose's space) so that it lands on a whole screen pixel. */
	private static float snap(float v, float scale, float offset, float gui) {
		if (Math.abs(scale) < 1e-4f) {
			return v;
		}
		float screen = (v * scale + offset) * gui;
		return (Math.round(screen) / gui - offset) / scale;
	}
	public void textMiddle(String s, float x, float centerY, float size, int argb, FontDescription font) {
		float lineHeight = Minecraft.getInstance().font.lineHeight * (size / Fonts.DESIGN_SIZE);
		text(s, x, centerY - lineHeight / 2 + (CAP_DROP + textNudge) * size, size, argb, font);
	}
	public static void setTextNudge(float nudge) {
		textNudge = Math.max(-0.4f, Math.min(0.4f, nudge));
	}
	public static float textNudge() {
		return textNudge;
	}
	public float textWidth(String s, FontDescription font, float size) {
		if (Fonts.hasRasters(font) && g != null) {
			// Measured the way text() draws it: at the exact pre-rendered size.
			float px = guiScale() * poseScale();
			int raster = Fonts.raster(size * sy * px);
			return Fonts.width(s, Fonts.cut(font, raster), Fonts.DESIGN_SIZE) * raster / (Fonts.DESIGN_SIZE * px * sx);
		}
		return Fonts.width(s, Fonts.sized(font, screenPixels(size)), size);
	}
	private float screenPixels(float size) {
		return size * sy * (float) Minecraft.getInstance().getWindow().getGuiScale();
	}
	private int fade(int argb) {
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0xFFFFFF);
	}
	public static int lerpColor(int from, int to, float t) {
		float u = Math.max(0, Math.min(1, t));
		int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * u);
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * u);
		int gg = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * u);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * u);
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}
	public static int white(float alpha) {
		return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | 0xFFFFFF;
	}
}
