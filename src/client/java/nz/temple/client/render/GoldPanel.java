package nz.temple.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;

public final class GoldPanel {
	private GoldPanel() {
	}
	public static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float alpha) {
		int w = x1 - x0;
		int h = y1 - y0;
		if (w <= 2 || h <= 2) {
			return;
		}
		float a = Math.max(0, Math.min(1, alpha));
		int gold = HudModule.GOLD & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		GlassRenderer glass = TempleNzClient.glass();
		if (glass == null || !glass.available() || !glass.hasOverlayRoom(1, 6)) {
			fallback(g, x0, y0, x1, y1, a, gold, light);
			return;
		}
		float r = Math.min(9, Math.min(w, h) / 2f);
		glass.overlayGlass(x0, y0, w, h, r, a);
		glass.overlayRect(x0, y0, w, h, r, argb(0.6f * a, mix(0x201608, gold, 0.16f)), 0, 0);
		glass.placeOverlayLayer(g, x0, y0, x1, y1);
	}
	public static void smoke(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float radius, float alpha) {
		int w = x1 - x0;
		int h = y1 - y0;
		if (w <= 2 || h <= 2) {
			return;
		}
		float a = Math.max(0, Math.min(1, alpha));
		GlassRenderer glass = TempleNzClient.glass();
		float r = Math.min(radius, Math.min(w, h) / 2f);
		if (glass == null || !glass.available() || !glass.hasOverlayRoom(1, 6)) {
			Draw2D.pixelRound(g, x0, y0, x1, y1, r * (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale(),
					argb(0.9f * a, 0x15171C));
			return;
		}
		glass.overlayGlass(x0, y0, w, h, r, a);
		glass.overlayRect(x0, y0, w, h, r, argb(0.66f * a, 0x0E1014), 0, 0);
		glass.placeOverlayLayer(g, x0, y0, x1, y1);
	}
	private static void fallback(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float a, int gold, int light) {
		g.fill(x0, y0, x1, y1, argb(0.95f * a, 0x241A08));
		g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, argb(0.85f * a, 0x4A360F), argb(0.9f * a, 0x140D04));
		g.fill(x0, y0, x1, y0 + 1, argb(0.35f * a, light));
	}
	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int gr = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (gr << 8) | bl;
	}
	private static int argb(float alpha, int rgb) {
		return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb;
	}
}
