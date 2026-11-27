package nz.temple.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;

public final class GlassWidgets {
	private GlassWidgets() {
	}

	/** A Ui of its own for Minecraft's widgets, in GUI units, so it never disturbs a Temple screen's drawing state. */
	private static Ui widgetUi;

	/** The current visual theme when it restyles Minecraft's widgets (every theme except Glass and Legacy), else null. */
	static VisualTheme themed() {
		VisualTheme t = VisualTheme.current();
		return t.classicWidgets() ? null : t;
	}

	static Ui ui(GuiGraphicsExtractor g, GlassRenderer glass, float alpha) {
		if (widgetUi == null) {
			widgetUi = new Ui(glass);
		}
		widgetUi.begin(g);
		widgetUi.transform(0, 0, 1, 1);
		widgetUi.alpha(alpha);
		return widgetUi;
	}

	private static int withAlpha(int argb, float a) {
		return Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a))) << 24 | (argb & 0xFFFFFF);
	}
	private static GlassRenderer glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int shapes) {
		GlassRenderer glass = TempleNzClient.glass();
		if (glass == null || !glass.available() || w < 6 || h < 6
				|| !g.containsPointInScissor(x, y) || !g.containsPointInScissor(x + w - 1, y + h - 1)
				|| !glass.hasRoom(1, shapes)) {
			return null;
		}
		glass.placeLayer(g);
		return glass;
	}
	public static boolean slider(GuiGraphicsExtractor g, int x, int y, int w, int h, double value, float warm, float grab,
			boolean active, float alpha, Component label) {
		GlassRenderer glass = glass(g, x, y, w, h, 16);
		if (glass == null) {
			return false;
		}
		VisualTheme t = themed();
		if (t != null) {
			Ui u = ui(g, glass, active ? alpha : alpha * 0.5f);
			float r = Math.min(h / 2f, 6);
			t.surface(u, VisualTheme.Surface.BUTTON, x, y, w, h, r, 0.25f * warm + grab, warm);
			int acc = t.accent();
			float fill = (float) Math.max(0, Math.min(1, value)) * (w - 4);
			if (fill > 1) {
				u.gradient(x + 2, y + 2, fill, h - 4, Math.max(1, r - 2), withAlpha(acc, 0.35f), withAlpha(acc, 0.8f), 2);
			}
			float knob = Math.max(4, h * 0.3f);
			float kx = Math.max(x + 2, Math.min(x + w - 2 - knob, x + 2 + fill - knob / 2));
			u.shadow(kx, y + 2, knob, h - 4, knob / 2, 3, 0x66000000);
			u.fill(kx, y + 2, knob, h - 4, knob / 2, 0xFFFFFFFF);
			label(g, label, x + w / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : 0xFFA0A0A0);
			return true;
		}
		int gold = HudModule.GOLD & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		float r = h / 2f;
		float a = active ? alpha : alpha * 0.5f;
		glass.glass(x, y, w, h, r, alpha);
		glass.rect(x, y, w, h, r, argb(0.26f * a, 0x06070B), 0, 0, null);
		glass.rect(x + 1, y + 1, w - 2, h / 2f, r, argb(0.05f * a, 0x000000), 0, 0, null);
		float fill = (float) Math.max(0, Math.min(1, value)) * (w - 4);
		if (fill > 1) {

			glass.rect(x + 2, y + 2, fill, h - 4, Math.max(1, r - 2), argb(0.30f * a, gold), 0, 0, null);
			glass.rect(x + 2, y + 2, fill, (h - 4) * 0.45f, Math.max(1, r - 2), argb(0.16f * a, light), 0, 0, null);
			float neck = Math.min(fill, h * 0.5f);
			glass.rect(x + 2 + fill - neck, y + 1.5f, neck, h - 3, Math.max(1, r - 1.5f), argb(0.18f * a, light), 0, 0, null);
		}
		glass.rect(x, y, w, h, r, argb((0.16f + 0.18f * warm + 0.22f * grab) * a, light), 1f, 0, null);
		float bead = h * (0.96f + 0.1f * warm + 0.42f * grab);
		float bx = x + 2 + fill - bead / 2;
		bx = Math.max(x - bead * 0.12f, Math.min(x + w - bead + bead * 0.12f, bx));
		float by = y + (h - bead) / 2f;
		glass.rect(bx + 0.5f, by + 1.4f, bead, bead, bead / 2, argb(0.20f * a, 0x000000), 0, 0, null);
		glass.rect(bx, by, bead, bead, bead / 2, argb(0.22f * a, gold), 0, 0, null);
		if (glass.hasLensRoom(1)) {
			glass.lens(bx, by, bead, bead, bead / 2, a, 0.25f + 0.1f * warm + 0.65f * grab);
		}
		glass.rect(bx, by, bead, bead, bead / 2, argb((0.42f + 0.18f * warm + 0.3f * grab) * a, light), 1f, 0, null);
		glass.rect(bx + bead * 0.2f, by + bead * 0.14f, bead * 0.36f, bead * 0.26f, bead * 0.13f, argb(0.62f * a, 0xFFFFFF), 0, 0, null);
		glass.rect(bx + bead * 0.28f, by + bead * 0.68f, bead * 0.44f, bead * 0.16f, bead * 0.08f, argb(0.22f * a, light), 0, 0, null);
		label(g, label, x + w / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : 0xFFA0A0A0);
		return true;
	}
	public static boolean checkbox(GuiGraphicsExtractor g, int x, int y, int box, boolean selected, boolean hovered,
			float alpha, Component label) {
		GlassRenderer glass = glass(g, x, y, box, box, 10);
		if (glass == null) {
			return false;
		}
		VisualTheme th = themed();
		if (th != null) {
			Ui u = ui(g, glass, alpha);
			th.surface(u, VisualTheme.Surface.BUTTON, x, y, box, box, box * 0.28f, selected ? 1 : 0, hovered ? 1 : 0);
			if (selected) {
				u.fill(x + 2, y + 2, box - 4, box - 4, box * 0.2f, withAlpha(th.accent(), 0.85f));
				Icons.draw(g, Icons.Icon.CHECK, x + box / 2f, y + box / 2f, box * 0.7f, withAlpha(0xFFFFFFFF, alpha));
			}
			if (label != null) {
				Minecraft mc = Minecraft.getInstance();
				g.text(mc.font, Fonts.widget(label, false), x + box + 6, y + (box - 8) / 2, 0xFFFFFFFF, true);
			}
			return true;
		}
		int gold = HudModule.GOLD & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		float r = box * 0.28f;
		glass.glass(x, y, box, box, r, alpha);
		glass.rect(x, y, box, box, r, argb((selected ? 0.3f : 0.06f) * alpha, gold), 0, 0, null);
		glass.rect(x, y, box, box, r, argb((0.2f + (hovered ? 0.35f : 0f)) * alpha, light), 1f, 0, null);
		if (selected) {
			if (glass.hasLensRoom(1)) {
				glass.lens(x + 1, y + 1, box - 2, box - 2, r, alpha, 0.7f);
			}
			float t = box * 0.16f;
			glass.rect(x + box * 0.22f, y + box * 0.48f, box * 0.28f, t, t / 2, argb(0.95f * alpha, 0xFFFFFF), 0, 0, null);
			glass.rect(x + box * 0.4f, y + box * 0.3f, t, box * 0.38f, t / 2, argb(0.95f * alpha, 0xFFFFFF), 0, 0, null);
		}
		if (label != null) {
			Minecraft mc = Minecraft.getInstance();
			g.text(mc.font, Fonts.widget(label, false), x + box + 6, y + (box - 8) / 2, 0xFFFFFFFF, true);
		}
		return true;
	}
	public static boolean textField(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused, boolean active, float alpha) {
		GlassRenderer glass = glass(g, x, y, w, h, 6);
		if (glass == null) {
			return false;
		}
		VisualTheme t = themed();
		if (t != null) {
			Ui u = ui(g, glass, active ? alpha : alpha * 0.6f);
			t.surface(u, VisualTheme.Surface.BUTTON, x, y, w, h, Math.min(h / 2f, 6), focused ? 1 : 0, 0);
			return true;
		}
		int gold = HudModule.GOLD & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		float r = Math.min(h / 2f, 6);
		float a = active ? alpha : alpha * 0.6f;
		glass.glass(x, y, w, h, r, alpha);
		glass.rect(x, y, w, h, r, argb(0.34f * a, 0x05060A), 0, 0, null);
		glass.rect(x + 1, y + 1, w - 2, h / 2f, r, argb(0.05f * a, light), 0, 0, null);
		glass.rect(x, y, w, h, r, argb((focused ? 0.75f : 0.22f) * a, focused ? light : gold), 1f, 0, null);
		return true;
	}
	public static void listBackground(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fillGradient(x0, y0, x1, y1, 0xC2100D08, 0xD8070609);
		VisualTheme t = themed();
		g.fill(x0, y0, x1, y0 + 1, (0x50 << 24) | (t != null ? t.accent() : HudModule.GOLD) & 0xFFFFFF);
		g.fill(x0, y1 - 1, x1, y1, 0x30000000);
	}

	public static boolean tab(GuiGraphicsExtractor g, int x, int y, int w, int h, float selected, float hover, float alpha) {
		GlassRenderer glass = glass(g, x, y, w, h, 8);
		if (glass == null) {
			return false;
		}
		VisualTheme t = themed();
		if (t != null) {
			Ui u = ui(g, glass, alpha);
			float r = Math.min(h / 2f, 9);
			t.surface(u, VisualTheme.Surface.BUTTON, x, y, w, h, r, selected, hover);
			if (selected > 0.02f) {
				float barW = w * (0.35f + 0.45f * selected);
				u.fill(x + (w - barW) / 2, y + h - 2.5f, barW, 1.6f, 0.8f, withAlpha(t.accent(), selected));
			}
			return true;
		}
		int gold = HudModule.GOLD & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		float r = Math.min(h / 2f, 9);
		glass.glass(x, y, w, h, r, alpha);
		glass.rect(x, y, w, h, r, argb((0.04f + 0.2f * selected + 0.07f * hover) * alpha, gold), 0, 0, null);
		glass.rect(x, y, w, h, r, argb((0.12f + 0.45f * selected + 0.2f * hover) * alpha, light), 1f, 0, null);
		if (selected > 0.02f) {
			if (glass.hasLensRoom(1)) {
				glass.lens(x + 1, y + 1, w - 2, h - 2, r, selected * alpha, 0.5f * selected);
			}
			float barW = w * (0.35f + 0.45f * selected);
			glass.rect(x + (w - barW) / 2, y + h - 2.5f, barW, 1.6f, 0.8f, argb(0.9f * selected * alpha, light), 0, 0, null);
		}
		return true;
	}
	public static boolean selection(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		GlassRenderer glass = glass(g, x, y, w, h, 6);
		if (glass == null) {
			return false;
		}
		VisualTheme t = themed();
		if (t != null) {
			Ui u = ui(g, glass, alpha);
			float r = Math.min(h / 2f, 8);
			u.fill(x, y, w, h, r, withAlpha(t.accent(), 0.2f));
			u.ring(x, y, w, h, r, 1, withAlpha(t.accent(), 0.6f));
			return true;
		}
		int gold = HudModule.GOLD & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		float r = Math.min(h / 2f, 8);
		glass.glass(x, y, w, h, r, alpha);
		glass.rect(x, y, w, h, r, argb(0.16f * alpha, gold), 0, 0, null);
		glass.rect(x, y, w, h, r, argb(0.45f * alpha, light), 1f, 0, null);
		if (glass.hasLensRoom(1)) {
			glass.lens(x, y, w, h, r, alpha * 0.8f, 0.45f);
		}
		return true;
	}
	private static void label(GuiGraphicsExtractor g, Component label, int centreX, int y, int colour) {
		if (label == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		label = Fonts.widget(label, false);
		g.text(mc.font, label, centreX - mc.font.width(label) / 2, y, colour, true);
	}
	private static int argb(float alpha, int rgb) {
		return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb;
	}
}
