package nz.temple.client.hud;

import net.minecraft.network.chat.FontDescription;
import nz.temple.client.render.Ui;

/** Text whose changed characters roll in from below while the old ones roll out upward, right to left with a short stagger. */
final class RollingText {
	private static final int CAPACITY = 32;
	private static final float DURATION = 0.34f;
	private static final float STAGGER = 0.055f;

	private final char[] current = new char[CAPACITY];
	private final char[] previous = new char[CAPACITY];
	private final float[] started = new float[CAPACITY];
	private String text = "";

	RollingText() {
		java.util.Arrays.fill(started, -10);
	}

	String text() {
		return text;
	}

	void set(String value, float now) {
		if (value.equals(text)) {
			return;
		}
		int oldLength = text.length();
		int newLength = Math.min(value.length(), CAPACITY);
		int span = Math.max(oldLength, newLength);
		int order = 0;
		for (int k = 0; k < span && k < CAPACITY; k++) {
			char before = k < oldLength ? text.charAt(oldLength - 1 - k) : 0;
			char after = k < newLength ? value.charAt(value.length() - 1 - k) : 0;
			if (before == after) {
				continue;
			}
			boolean running = now - started[k] < DURATION;
			previous[k] = running ? current[k] : before;
			current[k] = after;
			started[k] = now + order * STAGGER;
			order++;
		}
		text = value;
	}

	float width(Ui ui, float size, FontDescription font) {
		float total = 0;
		for (int i = 0; i < text.length(); i++) {
			total += advance(ui, text.charAt(i), size, font);
		}
		return total;
	}

	/** Draws the text with its left edge at {@code x} (or its right edge, when {@code right}), centred on {@code cy}. */
	void draw(Ui ui, float x, float cy, float size, FontDescription font, int color, boolean right, float now) {
		float total = width(ui, size, font);
		float cursor = right ? x - total : x;
		float travel = size * 0.7f;
		int length = text.length();
		boolean moving = false;
		for (int k = 0; k < Math.min(length, CAPACITY); k++) {
			moving |= now - started[k] < DURATION;
		}
		if (moving) {
			ui.clip(cursor - 2, cy - size * 0.62f, cursor + total + 2, cy + size * 0.62f);
		}
		for (int i = 0; i < length; i++) {
			char value = text.charAt(i);
			int k = length - 1 - i;
			float step = advance(ui, value, size, font);
			float progress = k < CAPACITY ? Math.max(0, Math.min(1, (now - started[k]) / DURATION)) : 1;
			float mid = cursor + step / 2;
			if (progress >= 1) {
				glyph(ui, value, mid, cy, size, font, color);
			} else {
				float eased = 1 - (1 - progress) * (1 - progress) * (1 - progress);
				char old = previous[k];
				if (old != 0) {
					glyph(ui, old, mid, cy - eased * travel, size, font, HudTheme.alpha(color, 1 - eased));
				}
				if (progress > 0) {
					glyph(ui, value, mid, cy + (1 - eased) * travel, size, font, HudTheme.alpha(color, eased));
				}
			}
			cursor += step;
		}
		if (moving) {
			ui.unclip();
		}
	}

	private static void glyph(Ui ui, char value, float cx, float cy, float size, FontDescription font, int color) {
		if (value == ' ' || value == 0) {
			return;
		}
		String s = String.valueOf(value);
		ui.textMiddle(s, cx - ui.textWidth(s, font, size) / 2, cy, size, color, font);
	}

	private static float advance(Ui ui, char value, float size, FontDescription font) {
		if (value >= '0' && value <= '9') {
			return ui.textWidth("0", font, size);
		}
		if (value == ' ') {
			return ui.textWidth("0", font, size) * 0.5f;
		}
		return ui.textWidth(String.valueOf(value), font, size);
	}
}
