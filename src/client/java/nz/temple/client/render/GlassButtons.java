package nz.temple.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.modules.client.MenuModule;

import java.util.Map;
import java.util.WeakHashMap;

public final class GlassButtons {
	private static final Map<AbstractButton, State> STATES = new WeakHashMap<>();
	private static final float STEP = 1 / 240f;
	private static final int RIPPLES = 4;
	private static final double RIPPLE_LIFE = 0.75;
	private static int mouseX = -1;
	private static int mouseY = -1;
	private static final class State {
		float hover;
		long splash;
		long last = System.nanoTime();
		boolean seeded;
		boolean wasHovered;
		float bx, by, vx, vy;
		float tx, ty, tvx, tvy;
		float wob, wobV;
		float stretch, stretchV, lastVx, lastVy;
		float axisX = 1, axisY;
		float lastMx, lastMy, travel;
		final float[] rippleX = new float[RIPPLES];
		final float[] rippleY = new float[RIPPLES];
		final long[] rippleBorn = new long[RIPPLES];
		int nextRipple;
	}
	private GlassButtons() {
	}
	public static boolean enabled() {
		if (nz.temple.client.render.streamproof.StreamproofRenderer.cleanPass()) {
			return false;
		}
		var modules = TempleNzClient.modules();
		MenuModule menu = modules == null ? null : modules.get(MenuModule.class);
		return menu != null && menu.glassButtons();
	}
	public static void mouse(int x, int y) {
		mouseX = x;
		mouseY = y;
	}
	public static void clicked(AbstractButton button) {
		if (!button.isActive()) {
			return;
		}
		State s = STATES.computeIfAbsent(button, k -> new State());
		long now = System.nanoTime();
		s.splash = now;
		s.wobV += 14;
		ripple(s, mouseX - button.getX(), mouseY - button.getY(), now);
	}
	public static boolean draw(AbstractButton button, GuiGraphicsExtractor g) {
		GlassRenderer glass = TempleNzClient.glass();
		if (!enabled() || glass == null || !glass.available()) {
			return false;
		}
		int x = button.getX();
		int y = button.getY();
		int w = button.getWidth();
		int h = button.getHeight();
		if (w < 8 || h < 8) {
			return false;
		}
		if (!g.containsPointInScissor(x, y) || !g.containsPointInScissor(x + w - 1, y + h - 1)) {
			return false;
		}
		if (!glass.hasRoom(1, 20)) {
			return false;
		}
		glass.placeLayer(g);
		State s = STATES.computeIfAbsent(button, k -> new State());
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - s.last) / 1e9f);
		s.last = now;
		boolean active = button.isActive();
		boolean hovered = active && button.isHoveredOrFocused();
		s.hover += ((hovered ? 1 : 0) - s.hover) * Math.min(1, dt * 12);
		simulate(s, x, y, w, h, hovered, dt, now);
		float alpha = Math.max(0, Math.min(1, button.getAlpha()));
		VisualTheme theme = GlassWidgets.themed();
		if (theme != null) {
			// The visual theme's own button: its material, border and accent, lit on hover.
			Ui u = GlassWidgets.ui(g, glass, active ? alpha : alpha * 0.45f);
			float lift = s.hover;
			theme.surface(u, VisualTheme.Surface.BUTTON, x, y - lift * 0.6f, w, h, Math.min(h / 2f, 6), active ? lift * 0.85f : 0, lift);
			return true;
		}
		int gold = HudModule.GOLD & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		float pw = w * (1 + 0.02f * s.wob);
		float ph = h * (1 - 0.08f * s.wob);
		float px = cx - pw / 2;
		float py = cy - ph / 2;
		float pr = ph / 2;
		glass.glass(px, py, pw, ph, pr, alpha);
		if (!active) {
			fill(glass, px, py, pw, ph, pr, argb(0.32f * alpha, 0x000000));
			return true;
		}
		float hv = s.hover;
		fill(glass, px, py, pw, ph, pr, argb((0.06f + 0.10f * hv) * alpha, gold));
		ring(glass, px, py, pw, ph, pr, 1f, argb((0.18f + 0.40f * hv) * alpha, light));
		if (hv > 0.02f || s.splash != 0) {
			ripples(glass, s, x, y, px, py, pw, ph, now, alpha, light, gold);
		}
		if (hv > 0.02f) {
			bead(glass, s, x, y, px, py, pw, ph, hv, alpha, now, gold, light);
		}
		double age = (now - s.splash) / 1e9;
		if (s.splash != 0 && age < 0.85) {
			splash(glass, x, y, w, h, (float) (age / 0.85), alpha, gold, light);
		}
		return true;
	}
	private static void simulate(State s, int x, int y, int w, int h, boolean hovered, float dt, long now) {
		boolean inside = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		float tx = inside ? mouseX - x : w / 2f;
		float ty = inside ? mouseY - y : h / 2f;
		if (!s.seeded) {
			s.bx = tx;
			s.by = ty;
			s.tx = tx;
			s.ty = ty;
			s.lastMx = mouseX;
			s.lastMy = mouseY;
			s.seeded = true;
		}
		if (hovered && !s.wasHovered) {
			s.bx = tx;
			s.by = ty;
			s.tx = tx;
			s.ty = ty;
			s.vx = 0;
			s.vy = 0;
			s.tvx = 0;
			s.tvy = 0;
			s.wobV += 7;
			ripple(s, tx, ty, now);
		} else if (!hovered && s.wasHovered) {
			s.wobV -= 4;
		}
		s.wasHovered = hovered;
		if (hovered && inside) {
			float moved = (float) Math.hypot(mouseX - s.lastMx, mouseY - s.lastMy);
			s.wobV += Math.min(moved, 24) * 0.09f;
			s.travel += moved;
			if (s.travel > 16) {
				s.travel = 0;
				ripple(s, tx, ty, now);
			}
		}
		s.lastMx = mouseX;
		s.lastMy = mouseY;
		boolean settled = !hovered && Math.abs(s.wob) < 0.002f && Math.abs(s.wobV) < 0.01f
				&& Math.abs(s.vx) < 0.5f && Math.abs(s.vy) < 0.5f && Math.abs(s.stretchV) < 0.01f;
		if (settled) {
			return;
		}
		for (float left = dt; left > 1e-5f; left -= STEP) {
			float step = Math.min(STEP, left);
			s.vx += ((tx - s.bx) * 190 - s.vx * 12) * step;
			s.vy += ((ty - s.by) * 190 - s.vy * 12) * step;
			s.bx += s.vx * step;
			s.by += s.vy * step;
			s.tvx += ((s.bx - s.tx) * 95 - s.tvx * 9) * step;
			s.tvy += ((s.by - s.ty) * 95 - s.tvy * 9) * step;
			s.tx += s.tvx * step;
			s.ty += s.tvy * step;
			s.wobV += (-s.wob * 340 - s.wobV * 8) * step;
			s.wob += s.wobV * step;
			float ax = (s.vx - s.lastVx) / Math.max(step, 1e-4f);
			float ay = (s.vy - s.lastVy) / Math.max(step, 1e-4f);
			s.lastVx = s.vx;
			s.lastVy = s.vy;
			float shove = (float) Math.hypot(ax, ay);
			if (shove > 1) {
				float len = Math.max(1e-4f, (float) Math.hypot(s.vx, s.vy));
				s.axisX += (s.vx / len - s.axisX) * Math.min(1, step * 18);
				s.axisY += (s.vy / len - s.axisY) * Math.min(1, step * 18);
				s.stretchV += Math.min(shove * 2.2e-4f, 9f);
			}
			s.stretchV += (-s.stretch * 260 - s.stretchV * 7.5f) * step;
			s.stretch += s.stretchV * step;
		}
		s.stretch = Math.max(-0.45f, Math.min(0.75f, s.stretch));
		s.wob = Math.max(-1.2f, Math.min(1.2f, s.wob));
	}
	private static void bead(GlassRenderer glass, State s, int x, int y, float px, float py, float pw, float ph, float hv,
			float alpha, long now, int gold, int light) {
		float breathe = 1 + 0.03f * (float) Math.sin(now / 1e9 * 5.5);
		float d = ph * 0.86f * (0.6f + 0.4f * hv) * breathe;
		float along = Math.abs(s.axisX);
		float across = Math.abs(s.axisY);
		float stretch = s.stretch;
		float bw = d * (1 + stretch * along - stretch * across * 0.6f) * (1 + 0.1f * s.wob);
		float bh = d * (1 + stretch * across - stretch * along * 0.6f) * (1 - 0.12f * s.wob);
		bw = Math.min(bw, pw - 2);
		bh = Math.min(bh, ph - 1.5f);
		float wantX = x + s.bx;
		float wantY = y + s.by;
		float bxc = clamp(wantX, px + bw / 2 + 1, px + pw - bw / 2 - 1);
		float byc = clamp(wantY, py + bh / 2 + 0.5f, py + ph - bh / 2 - 0.5f);
		float squashX = Math.min(0.42f, Math.abs(wantX - bxc) / Math.max(bw, 1e-3f));
		float squashY = Math.min(0.42f, Math.abs(wantY - byc) / Math.max(bh, 1e-3f));
		bw = bw * (1 - squashX) * (1 + squashY * 0.8f);
		bh = bh * (1 - squashY) * (1 + squashX * 0.8f);
		float lx = bxc - bw / 2;
		float ly = byc - bh / 2;
		float lr = Math.min(bw, bh) / 2;
		float gap = (float) Math.hypot(s.bx - s.tx, s.by - s.ty);
		if (gap > bw * 0.22f) {
			float td = Math.min(bh * 0.7f, bw * 0.45f) * Math.min(1, gap / (bw * 0.9f));
			float tlx = clamp(x + s.tx - td / 2, px + 2, px + pw - td - 2);
			float tly = clamp(y + s.ty - td / 2, py + 1.5f, py + ph - td - 1.5f);
			fill(glass, tlx, tly + 0.6f, td, td, td / 2, argb(0.16f * hv * alpha, 0x000000));
			if (glass.hasLensRoom(1)) {
				glass.lens(tlx, tly, td, td, td / 2, 0.9f * hv * alpha, 0.85f * hv);
			}
		}

		fill(glass, lx - bw * 0.06f, ly + bh * 0.16f, bw * 1.12f, bh, lr, argb(0.20f * hv * alpha, 0x000000));
		if (glass.hasLensRoom(1)) {
			glass.lens(lx, ly, bw, bh, lr, hv * alpha, 1f);
		}
		fill(glass, lx, ly, bw, bh, lr, argb(0.07f * hv * alpha, gold));
		float mw = bw * 0.66f;
		fill(glass, bxc - mw / 2, ly + bh - 1.2f, mw, 1.1f, 0.55f, argb(0.30f * hv * alpha, light));
		float gw = bw * 0.2f;
		float gh = bh * 0.15f;
		float gx = clamp(lx + bw * 0.24f - s.vx * 0.01f, lx + 1, lx + bw - gw - 1);
		float gy = clamp(ly + bh * 0.16f - s.vy * 0.006f, ly + 0.5f, ly + bh - gh - 0.5f);
		fill(glass, gx, gy, gw, gh, gh / 2, argb(0.8f * hv * alpha, 0xFFFFFF));
	}
	private static void ripples(GlassRenderer glass, State s, int x, int y, float px, float py, float pw, float ph, long now,
			float alpha, int light, int gold) {
		for (int i = 0; i < RIPPLES; i++) {
			if (s.rippleBorn[i] == 0) {
				continue;
			}
			double age = (now - s.rippleBorn[i]) / 1e9;
			if (age > RIPPLE_LIFE) {
				s.rippleBorn[i] = 0;
				continue;
			}
			float t = (float) (age / RIPPLE_LIFE);
			for (int crest = 0; crest < 3; crest++) {
				float ct = t - crest * 0.16f;
				if (ct <= 0) {
					continue;
				}
				float e = 1 - (1 - Math.min(1, ct)) * (1 - Math.min(1, ct));
				float rh = Math.min(ph - 1, ph * (0.2f + 0.85f * e));
				float rw = Math.min(pw - 1, rh * (1.15f + 2.8f * e));
				float rx = clamp(x + s.rippleX[i], px + rw / 2 + 0.5f, px + pw - rw / 2 - 0.5f);
				float ry = clamp(y + s.rippleY[i], py + rh / 2 + 0.5f, py + ph - rh / 2 - 0.5f);
				float fade = (1 - Math.min(1, ct)) * (1 - crest * 0.28f) * alpha;
				if (fade <= 0.01f) {
					continue;
				}
				float thickness = 2.6f * (1 - e * 0.6f) + 0.6f;
				if (glass.hasLensRoom(1)) {
					glass.lensRing(rx - rw / 2, ry - rh / 2, rw, rh, rh / 2, Math.min(1, 0.9f * fade + 0.25f), 0.75f, thickness);
				} else {
					ring(glass, rx - rw / 2, ry - rh / 2, rw, rh, rh / 2, thickness, argb(0.3f * fade, light));
				}
				if (crest == 0) {
					fill(glass, rx - rw / 2, ry - rh / 2, rw, rh, rh / 2, argb(0.05f * fade, gold));
				}
			}
		}
	}
	private static void ripple(State s, float x, float y, long now) {
		int i = s.nextRipple;
		s.nextRipple = (i + 1) % RIPPLES;
		s.rippleX[i] = x;
		s.rippleY[i] = y;
		s.rippleBorn[i] = now;
	}
	private static void splash(GlassRenderer glass, float x, float y, float w, float h, float t, float alpha, int gold, int light) {
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		float grow = 1 - (1 - t) * (1 - t);
		float fade = (1 - t) * alpha;
		float rw = w * (1 + grow * 0.14f);
		float rh = h * (1 + grow * 0.9f);
		ring(glass, cx - rw / 2, cy - rh / 2, rw, rh, rh / 2, 1.2f, argb(0.7f * fade, light));
		for (int i = 0; i < 6; i++) {
			float u = (i + 0.5f) / 6f;
			float lift = (float) Math.sin(t * Math.PI) * h * (0.55f + 0.25f * (i % 2));
			float spread = (u - 0.5f) * w * 0.22f * grow;
			float ds = h * 0.22f * (1 - t * 0.6f);
			float px = x + w * u + spread - ds / 2;
			float py = y - lift - ds / 2;
			fill(glass, px, py, ds, ds, ds / 2, argb(0.55f * fade, gold));
			if (glass.hasLensRoom(1)) {
				glass.lens(px, py, ds, ds, ds / 2, fade, 0.7f);
			}
		}
	}
	private static float clamp(float v, float lo, float hi) {
		return hi < lo ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, v));
	}
	private static int argb(float alpha, int rgb) {
		return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb;
	}
	private static void fill(GlassRenderer glass, float x, float y, float w, float h, float r, int argb) {
		glass.rect(x, y, w, h, r, argb, 0, 0, null);
	}

	private static void ring(GlassRenderer glass, float x, float y, float w, float h, float r, float width, int argb) {
		glass.rect(x, y, w, h, r, argb, width, 0, null);
	}
}
