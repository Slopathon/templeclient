package nz.temple.client.render;

import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;

/**
 * The visual theme picked in the HUD editor (HUD → "Visual theme"). A theme is a whole style, not a colour: each one
 * has its own material (glass or solid), gradient fill, gradient border, corner shape, glow, highlights, accent stripe
 * and accent colour. It is applied to every HUD panel and to the Modern click GUI's window and cards.
 */
public enum VisualTheme {
	/** Temple's original look: blurred glass with a smoked tint. */
	GLASS("Glass"),
	/** The Legacy GUI's look: clear blurred glass with a warm gold tint and gold hairlines. */
	LEGACY("Legacy"),
	/** Frosted acrylic: bright sheen, a lit top edge, a white border fading downwards, very round corners, ice accent. */
	FROST("Frost"),
	/** Matte and sharp: near-square corners, solid gradient body, accent stripe down the left edge, hard shadow, no blur. */
	ONYX("Onyx"),
	/** Lit glass: diagonal accent-to-dark gradient, accent border fading across, outer accent glow. */
	AMBIENT("Ambient"),
	/** Luxe: warm black-to-bronze gradient, gold trim, soft top sheen, large corners, no blur. */
	GILDED("Gilded"),
	/** Holographic: dark glass with a flowing rainbow border and top edge, and a chroma glow. */
	PRISM("Prism");

	private final String label;

	VisualTheme(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public static VisualTheme byLabel(String label) {
		for (VisualTheme t : values()) {
			if (t.label.equalsIgnoreCase(label)) {
				return t;
			}
		}
		return GLASS;
	}

	public static VisualTheme current() {
		var modules = TempleNzClient.modules();
		HudModule hud = modules == null ? null : modules.get(HudModule.class);
		return hud == null ? GLASS : byLabel(hud.visualTheme());
	}

	/** Whether panels show the blurred glass behind them. */
	/** Glass and Legacy keep Temple's liquid-glass buttons, sliders and checkboxes in Minecraft's menus. */
	public boolean classicWidgets() {
		return this == GLASS || this == LEGACY;
	}

	public boolean glass() {
		return this != ONYX && this != GILDED;
	}

	// ------------------------------------------------------------ colours

	private static final int ICE = 0xFF9FDBFF;
	private static final int GOLD = 0xFFF5C451;

	private static double time() {
		return Theme.time();
	}

	private static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float r = Math.abs(h * 6 - 3) - 1;
		float g = 2 - Math.abs(h * 6 - 2);
		float b = 2 - Math.abs(h * 6 - 4);
		r = v * (1 - s + s * Math.max(0, Math.min(1, r)));
		g = v * (1 - s + s * Math.max(0, Math.min(1, g)));
		b = v * (1 - s + s * Math.max(0, Math.min(1, b)));
		return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
	}

	/** Prism's colour at phase {@code offset} (0..1 around the hue circle), drifting over time. */
	private static int chroma(double offset) {
		return hsv((float) (time() * 0.08 + offset), 0.6f, 1f);
	}

	/** The theme's accent colour. */
	public int accent() {
		return switch (this) {
			case GILDED -> GOLD;
			case LEGACY -> 0xFFE9B949;
			case PRISM -> chroma(0);
			default -> 0xFF000000 | (Theme.primary() & 0xFFFFFF);
		};
	}

	private static int a(int argb, int alpha) {
		return Math.max(0, Math.min(255, alpha)) << 24 | (argb & 0xFFFFFF);
	}

	private static int mix(int x, int y, float t) {
		return Ui.lerpColor(x, y, t);
	}

	/** A hairline in the current {@link Ui} transform: one screen pixel wide. */
	private static float hair(Ui ui) {
		float gui = (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
		float k = Math.abs(ui.mapX(1) - ui.mapX(0));
		return 1f / Math.max(1e-3f, gui * k);
	}

	// ------------------------------------------------------------ surfaces

	/** What a surface is: the outer click GUI window, a card inside it, or a HUD panel. */
	public enum Surface {
		WINDOW, CARD, HUD,
		/** A button, slider or field in Minecraft's own menus: a card with the theme's glass and a dark base under it. */
		BUTTON
	}

	/** The corner radius this theme gives a surface designed with radius {@code r}. */
	public float radius(float r, float w, float h) {
		float out = switch (this) {
			case FROST -> r * 1.25f;
			case ONYX -> Math.max(1.5f, r * 0.3f);
			case GILDED -> r * 1.35f;
			default -> r;
		};
		return Math.min(out, Math.min(w, h) / 2);
	}

	/**
	 * Draws a surface in this theme's style. {@code lit} (0..1) is how strongly it is highlighted (a module that is on),
	 * {@code hover} how far the pointer is over it. The caller draws its content on top.
	 */
	public void surface(Ui ui, Surface kind, float x, float y, float w, float h, float r, float lit, float hover) {
		r = radius(r, w, h);
		float hair = hair(ui);
		if (kind == Surface.BUTTON) {
			// Menus can sit over a bright world: give glass themes their blur and a dark base so labels stay readable.
			if (glass()) {
				ui.glass(x, y, w, h, r);
			}
			ui.fill(x, y, w, h, r, this == FROST ? 0x590A0E16 : 0x8C0B0C10);
			kind = Surface.CARD;
		}
		int acc = accent();
		boolean window = kind == Surface.WINDOW;
		boolean card = kind == Surface.CARD;
		switch (this) {
			case GLASS -> {
				if (!card) {
					ui.glass(x, y, w, h, r);
				}
				if (window) {
					ui.fill(x, y, w, h, r, 0xF00B0C10);
				} else if (card) {
					ui.fill(x, y, w, h, r, mix(0xFF121318, 0xFF17181E, hover));
				}
				if (!kind.equals(Surface.HUD)) {
					ui.ring(x, y, w, h, r, hair, mix(a(0xFFFFFF, 0x12 + Math.round(0x0C * hover)), a(acc, 0x80), lit));
				}
			}
			case LEGACY -> {
				int gold = 0xFFE9B949;
				int warm = mix(0xFF201608, gold, 0.16f);
				if (!card) {
					ui.glass(x, y, w, h, r);
				}
				int body = window ? a(warm, 0xA6) : card ? mix(a(0xFFFFFF, 0x0A + Math.round(0x0A * hover)), a(gold, 0x26), lit) : a(warm, 0x66);
				ui.fill(x, y, w, h, r, body);
				if (!card) {
					ui.gradient(x, y, w, Math.min(12, h * 0.35f), r, 0x14FFFFFF, 0x00FFFFFF, 1);
				}
				int edge = window ? 0x4DFFE3A1 : card ? a(0xFFE3A1, 0x1F + Math.round(0x70 * lit) + Math.round(0x14 * hover)) : 0x4DFFE3A1;
				ui.ring(x, y, w, h, r, hair, edge);
			}
			case FROST -> {
				if (window || kind == Surface.HUD) {
					ui.shadow(x, y, w, h, r, window ? 30 : 10, a(0xFFFFFF, window ? 0x18 : 0x12));
				}
				if (!card) {
					ui.glass(x, y, w, h, r);
				}
				int top = window ? 0xB8262729 : card ? a(0xFFFFFF, 0x14 + Math.round(0x0C * hover)) : 0x38FFFFFF;
				int bottom = window ? 0xD60D0D0F : card ? a(0xFFFFFF, 0x06 + Math.round(0x06 * hover)) : 0x12FFFFFF;
				ui.gradient(x, y, w, h, r, top, bottom, 1);
				// Lit top edge, fading out to both sides.
				float sheen = Math.min(w * 0.42f, w / 2 - r);
				if (sheen > 2) {
					ui.gradient(x + r, y + hair, sheen, hair * 1.5f, 0, 0x00FFFFFF, card ? 0x40FFFFFF : 0x80FFFFFF, 2);
					ui.gradient(x + w - r - sheen, y + hair, sheen, hair * 1.5f, 0, card ? 0x40FFFFFF : 0x80FFFFFF, 0x00FFFFFF, 2);
				}
				int edgeTop = mix(a(0xFFFFFF, card ? 0x33 : 0x73), a(acc, 0xE6), lit);
				ui.gradientRing(x, y, w, h, r, hair * (lit > 0.5f ? 1.5f : 1), edgeTop, a(0xFFFFFF, card ? 0x0D : 0x1F), 1);
			}
			case ONYX -> {
				ui.shadow(x, y + (window ? 3 : 1.5f), w, h, r, window ? 14 : 3, window ? 0xB3000000 : 0x99000000);
				int top = window ? 0xFC0C0C0F : card ? mix(0xFF111114, 0xFF16161A, hover) : 0xF2141418;
				int bottom = window ? 0xFC060607 : card ? mix(0xFF0B0B0D, 0xFF101013, hover) : 0xF208080A;
				ui.gradient(x, y, w, h, r, top, bottom, 1);
				ui.ring(x, y, w, h, r, hair, a(0xFFFFFF, window ? 0x14 : 0x10));
				if (kind == Surface.HUD || lit > 0.01f) {
					float stripe = Math.max(hair * 2, 1.6f);
					int alpha = kind == Surface.HUD ? 255 : Math.round(255 * lit);
					ui.gradient(x, y, stripe, h, Math.min(r, stripe / 2), a(acc, alpha), a(mix(acc, 0xFF000000, 0.45f), alpha), 1);
				}
			}
			case AMBIENT -> {
				int glow = window ? a(acc, 0x4D) : card ? a(acc, Math.round(0x40 * lit)) : a(acc, 0x73);
				if ((glow >>> 24) > 2) {
					ui.shadow(x, y, w, h, r, window ? 48 : card ? 10 : 12, glow);
				}
				if (!card) {
					ui.glass(x, y, w, h, r);
				}
				int dark = window ? 0xF20B0C10 : card ? 0xFF111217 : 0xA60B0D12;
				int lift = window ? a(mix(0xFF0B0C10, acc, 0.16f), 0xF2) : card
						? mix(mix(0xFF111217, acc, 0.07f + 0.05f * hover), mix(0xFF111217, acc, 0.16f), lit)
						: a(mix(0xFF0B0D12, acc, 0.6f), 0xB3);
				ui.gradient(x, y, w, h, r, lift, dark, 3);
				ui.gradientRing(x, y, w, h, r, hair * (card ? 1 : 1.5f), a(acc, card ? 0x2E + Math.round(0x60 * lit) : 0xE6),
						a(acc, card ? 0x0A : 0x33), 3);
			}
			case GILDED -> {
				ui.shadow(x, y + 2, w, h, r, window ? 26 : 8, window ? 0x99140C02 : 0x800E0802);
				int top = window ? 0xF8261C0C : card ? mix(0xFF2A2010, 0xFF332714, hover) : 0xF02B200E;
				int bottom = window ? 0xF80A0805 : card ? mix(0xFF141008, 0xFF19140A, hover) : 0xF00E0B06;
				ui.gradient(x, y, w, h, r, top, bottom, 3);
				float sheen = Math.min(10, h * 0.35f);
				ui.gradient(x, y, w, sheen, r, 0x14FFFFFF, 0x00FFFFFF, 1);
				int trim = mix(card ? 0x4DF5C451 : 0xB3F5C451, 0xFFFFE08A, lit);
				ui.gradientRing(x, y, w, h, r, hair * (lit > 0.5f ? 1.5f : 1), trim, card ? 0x26A8741E : 0x66A8741E, 2);
			}
			case PRISM -> {
				int c0 = chroma(0);
				int c1 = chroma(0.33);
				int c2 = chroma(0.66);
				if (window || kind == Surface.HUD) {
					ui.shadow(x, y, w, h, r, window ? 40 : 10, a(mix(c0, c2, 0.5f), window ? 0x38 : 0x2A));
				} else if (lit > 0.01f) {
					ui.shadow(x, y, w, h, r, 8, a(c1, Math.round(0x40 * lit)));
				}
				if (!card) {
					ui.glass(x, y, w, h, r);
				}
				int body = window ? 0xF00A0B10 : card ? mix(0xFF101117, 0xFF15161D, hover) : 0x990A0B10;
				ui.fill(x, y, w, h, r, body);
				if (!card || lit > 0.01f) {
					int alpha = card ? Math.round(255 * lit) : 0xFF;
					ui.gradientRing(x, y, w, h, r, hair * 1.4f, a(c0, alpha), a(c1, alpha), 2);
					float edge = Math.max(hair * 2, 1.2f);
					ui.gradient(x + r, y, w - 2 * r, edge, edge / 2, a(c1, alpha), a(c2, alpha), 2);
				} else {
					ui.ring(x, y, w, h, r, hair, a(0xFFFFFF, 0x10 + Math.round(0x0C * hover)));
				}
			}
		}
	}

	/** Draws a HUD panel (what {@code ui.glass} becomes while the HUD is drawn). */
	public void hudPanel(Ui ui, float x, float y, float w, float h, float r) {
		surface(ui, Surface.HUD, x, y, w, h, r, 0, 0);
	}

	// ------------------------------------------------------------ Modern click GUI details

	public int sidebar() {
		return switch (this) {
			case FROST -> 0x0FFFFFFF;
			case LEGACY -> 0x0AFFE3A1;
			case GILDED -> 0x12F5C451;
			case AMBIENT -> a(accent(), 0x0F);
			case ONYX -> 0x08FFFFFF;
			case PRISM -> 0x06FFFFFF;
			default -> 0x05FFFFFF;
		};
	}

	public int line() {
		return switch (this) {
			case AMBIENT -> a(accent(), 0x2E);
			case FROST -> 0x26FFFFFF;
			case LEGACY -> 0x33E9B949;
			case GILDED -> 0x33F5C451;
			case PRISM -> a(chroma(0.2), 0x40);
			case ONYX -> 0x14FFFFFF;
			default -> 0x12FFFFFF;
		};
	}

	/** Whether this theme labels sections in capitals. */
	public boolean caps() {
		return this == ONYX || this == PRISM;
	}

	/** A small swatch that shows the theme in the theme picker. */
	public int swatch() {
		return switch (this) {
			case GLASS -> 0xFFB8C4D6;
			case LEGACY -> 0xFFD8C08A;
			case ONYX -> 0xFF3A3A40;
			case AMBIENT -> accent();
			case FROST -> 0xFFE6E8EC;
			case GILDED -> GOLD;
			case PRISM -> chroma(0);
		};
	}
}
