package nz.temple.client.hud;

import nz.temple.anim.Spring;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.GoldPanel;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.module.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public final class Notifications {
	public enum Kind { ENABLED, DISABLED, ERROR, INFO }

	private static final float EDGE = 12;
	private static final float BOTTOM = 52;
	private static final float HEIGHT = 34;
	private static final float MIN_WIDTH = 150;
	private static final float MAX_WIDTH = 280;
	private static final float GAP = 5;
	private static final float HOTBAR_HALF_WIDTH = 91;
	private static final float HOTBAR_CLEARANCE = 40;
	private static final int MAX = 4;
	private static final class Toast {
		final String title;
		final String detail;
		final Kind kind;
		final long born;
		final Spring appear = new Spring(0, 260, 16);
		final Spring y = new Spring(0, 260, 26);
		boolean placed;
		boolean leaving;
		Toast(String title, String detail, Kind kind, long born) {
			this.title = title;
			this.detail = detail;
			this.kind = kind;
			this.born = born;
		}
		long life() {
			return switch (kind) {
				case ERROR -> 5000;
				case INFO -> 4000;
				default -> 1800;
			};
		}
	}
	private final List<Toast> toasts = new ArrayList<>();
	private final LongSupplier clockMillis;
	private final BooleanSupplier enabled;
	private BooleanSupplier dark = () -> false;
	private DoubleSupplier size = () -> 1;
	public Notifications(LongSupplier clockMillis, BooleanSupplier enabled) {
		this.clockMillis = clockMillis;
		this.enabled = enabled;
	}

	public void setDarkStyle(BooleanSupplier dark) {
		this.dark = dark;
	}

	public void setSize(DoubleSupplier size) {
		this.size = size;
	}

	private float scale() {
		return (float) Math.max(0.1, size.getAsDouble()) * UiScale.factor();
	}

	public void toggled(Module m) {
		push(m.name(), m.isEnabled() ? "enabled" : "disabled", m.isEnabled() ? Kind.ENABLED : Kind.DISABLED);
	}

	public void error(String title, String detail) {
		push(title, detail, Kind.ERROR);
	}

	public void info(String title, String detail) {
		push(title, detail, Kind.INFO);
	}

	public interface Redirect {
		boolean take(String title, String detail, Kind kind);
	}

	private Redirect redirect;
	public void setRedirect(Redirect redirect) {
		this.redirect = redirect;
	}
	private void push(String title, String detail, Kind kind) {
		if (kind != Kind.ERROR && !enabled.getAsBoolean()) {
			return;
		}
		if (redirect != null && redirect.take(title, detail, kind)) {
			return;
		}
		toasts.add(new Toast(title, detail, kind, clockMillis.getAsLong()));
		List<Toast> live = toasts.stream().filter(t -> !t.leaving).toList();
		for (int i = 0; i < live.size() - MAX; i++) {
			live.get(i).leaving = true;
		}
	}
	public List<Spring> springs() {
		long now = clockMillis.getAsLong();
		float k = scale();
		List<Spring> list = new ArrayList<>();
		float y = 0;
		for (int i = toasts.size() - 1; i >= 0; i--) {
			Toast t = toasts.get(i);
			if (now - t.born > t.life()) {
				t.leaving = true;
			}
			t.appear.target = t.leaving ? 0 : 1;
			if (!t.leaving) {
				if (!t.placed) {
					t.y.snap(y);
					t.placed = true;
				} else {
					t.y.target = y;
				}
				y += (HEIGHT + GAP) * k;
			}
			list.add(t.appear);
			list.add(t.y);
		}
		return list;
	}
	public void render(Ui ui) {
		toasts.removeIf(t -> t.leaving && t.appear.x < 0.02);
		GuiGraphicsExtractor g = ui.graphics();
		if (g == null) {
			return;
		}
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		long now = clockMillis.getAsLong();
		float k = scale();
		float h = HEIGHT * k;
		float edge = EDGE * k;
		float titleSize = 12 * k;
		float detailSize = 10.5f * k;
		float w = MIN_WIDTH * k;
		for (Toast t : toasts) {
			w = Math.max(w, width(ui, t, titleSize, detailSize, k));
		}
		float bottom = BOTTOM * k;
		if (gw - edge - w < gw / 2f + HOTBAR_HALF_WIDTH) {
			bottom = Math.max(bottom, HOTBAR_CLEARANCE);
		}
		float p = 1f / Math.max(1, (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
		for (Toast t : toasts) {
			float a = Math.max(0, Math.min(1, t.appear.xf()));
			float x = gw - edge - w + (1 - a) * (w + edge);
			float y = gh - bottom - h - t.y.xf();
			int accent = switch (t.kind) {
				case ENABLED -> 0xFF34C759;
				case DISABLED -> 0xFF8E8E93;
				case ERROR -> 0xFFFF453A;
				case INFO -> HudTheme.light();
			};
			Icons.Icon icon = switch (t.kind) {
				case ENABLED -> Icons.Icon.CHECK;
				case DISABLED -> Icons.Icon.CLOSE;
				case ERROR, INFO -> Icons.Icon.INFO;
			};
			ui.resetTransform();
			ui.alpha(a);
			ui.glass(x, y, w, h, 10 * k);
			int alpha = Math.round(a * 255);
			float iconR = 10 * k;
			float icx = x + 8 * k + iconR;
			float icy = y + h / 2;
			ui.fill(icx - iconR, icy - iconR, iconR * 2, iconR * 2, 6 * k, 0x2E000000 | (accent & 0xFFFFFF));
			Icons.draw(g, icon, icx, icy, 11 * k, fade(accent, a));
			float tx = icx + iconR + 8 * k;
			float right = x + w - 12 * k;
			ui.textMiddle(ellipsize(ui, t.title, Fonts.SEMIBOLD, titleSize, right - tx), tx, y + 10 * k, titleSize, fade(0xFFFFFFFF, a),
					Fonts.SEMIBOLD);
			ui.textMiddle(ellipsize(ui, t.detail, Fonts.MEDIUM, detailSize, right - tx), tx, y + 21 * k, detailSize, fade(0xB3FFFFFF, a),
					Fonts.MEDIUM);
			float life = Math.max(0, 1 - (float) (now - t.born) / t.life());
			float barY = y + h - 5 * k;
			float bh = Math.max(p * 2, 1.5f * k);
			ui.fill(tx, barY, right - tx, bh, bh / 2, 0x22FFFFFF);
			if (life > 0.01f) {
				ui.gradient(tx, barY, (right - tx) * life, bh, bh / 2, accent | 0xFF000000, HudTheme.mix(accent | 0xFF000000, 0xFFFFFFFF, 0.35f), 2);
			}
		}
		ui.resetTransform();
		ui.alpha(1);
	}
	private static int fade(int argb, float a) {
		return (Math.round(((argb >>> 24) & 0xFF) * a) << 24) | (argb & 0xFFFFFF);
	}
	private static String ellipsize(Ui ui, String text, net.minecraft.network.chat.FontDescription font, float size, float max) {
		if (ui.textWidth(text, font, size) <= max) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && ui.textWidth(s + "…", font, size) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}
	private static float width(Ui ui, Toast t, float titleSize, float detailSize, float k) {
		float text = Math.max(ui.textWidth(t.title, Fonts.SEMIBOLD, titleSize), ui.textWidth(t.detail, Fonts.MEDIUM, detailSize));
		return Math.min(MAX_WIDTH * k, 8 * k + 20 * k + 8 * k + text + 14 * k);
	}
}
