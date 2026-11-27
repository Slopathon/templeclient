package nz.temple.client.hud;

import nz.temple.client.render.Ui;

/**
 * The HUD's colour themes. Each theme has four colours: accent (the main colour), light (icons, labels, highlights), deep
 * (the dark end of gradients) and tint (the colour mixed into the glass panels). Switching themes eases between them
 * instead of snapping. Layout and colours follow the HUD design by x.monolith.
 */
public final class HudTheme {
	public static final int TEXT = 0xFFF4F8FF;
	public static final int TEXT_DIM = 0xA0F4F8FF;
	public static final int TEXT_FAINT = 0x60F4F8FF;
	public static final int GOOD = 0xFF4ADE80;
	public static final int WARN = 0xFFFACC15;
	public static final int BAD = 0xFFF87171;

	/** Name, accent, light, deep, tint. */
	private static final String[][] PRESETS = {
			{"Azure", "2F9BFF", "5CD0FF", "1D5FE6", "1A4C9A"},
			{"Ocean", "14B8A6", "5EEAD4", "0E7490", "0F5E66"},
			{"Violet", "8B5CF6", "C4B5FD", "5B21B6", "3B2A7A"},
			{"Rose", "F43F5E", "FDA4AF", "BE123C", "7A1F3C"},
			{"Emerald", "22C55E", "86EFAC", "15803D", "14532D"},
			{"Sunset", "FB923C", "FCD34D", "EA580C", "7C2D12"},
			{"Gold", "E9B949", "FFE3A1", "B7791F", "6B4A12"},
			{"Graphite", "94A3B8", "E2E8F0", "475569", "1F2937"},
			{"White", "E8E8E8", "FFFFFF", "9CA3AF", "2A2A2A"},
			{"Red", "E5484D", "FF8A8A", "B91C1C", "6B1A1A"}};

	/** Two-colour themes: name, from, to. Astolfo (pink to blue) is listed with them. */
	private static final String[][] GRADIENTS = {
			{"Aurora", "22C55E", "2F9BFF"}, {"Twilight", "8B5CF6", "3B82F6"}, {"Candy", "FF5C8A", "9B6BFF"},
			{"Fire", "E5484D", "FFB340"}, {"Lagoon", "14B8A6", "3358D4"}, {"Peach", "FB923C", "F43F5E"}, {"Ice", "DDF3FF", "5AC8FA"}};

	/** Single colours, in the order the Settings page lists them. */
	public static final java.util.List<String> SOLIDS = java.util.List.of("White", "Graphite", "Azure", "Ocean", "Violet", "Rose", "Red",
			"Emerald", "Sunset", "Gold", "Custom");
	public static final java.util.List<String> GRADIENT_NAMES = java.util.List.of("Astolfo", "Aurora", "Twilight", "Candy", "Fire", "Lagoon",
			"Peach", "Ice");

	/** Whether {@code name} is a two-colour theme. */
	public static boolean isGradient(String name) {
		return GRADIENT_NAMES.contains(name);
	}

	/** A gradient theme's two ends, {from, to}. */
	public static int[] ends(String name) {
		if ("Astolfo".equals(name)) {
			return new int[] {0xFFFF60A5, 0xFF60A5FA};
		}
		for (String[] g : GRADIENTS) {
			if (g[0].equals(name)) {
				return new int[] {hex(g[1]), hex(g[2])};
			}
		}
		return new int[] {hex("2F9BFF"), hex("5CD0FF")};
	}

	public static final java.util.List<String> NAMES = java.util.stream.Stream.concat(SOLIDS.stream(), GRADIENT_NAMES.stream()).toList();

	private static final float[] accent = rgb(0x2F9BFF);
	private static final float[] light = rgb(0x5CD0FF);
	private static final float[] deep = rgb(0x1D5FE6);
	private static final float[] tint = rgb(0x1A4C9A);
	private static boolean placed;
	private static long last;

	private HudTheme() {
	}

	/** Moves the shown colours toward theme {@code name} ({@code custom} is the colour used by "Custom"). Call once a frame. */
	public static void update(String name, int custom) {
		int[] target = target(name, custom);
		long now = System.nanoTime();
		float dt = last == 0 ? 1 : Math.min(0.25f, (now - last) / 1e9f);
		last = now;
		float f = placed ? 1 - (float) Math.exp(-9 * dt) : 1;
		placed = true;
		ease(accent, target[0], f);
		ease(light, target[1], f);
		ease(deep, target[2], f);
		ease(tint, target[3], f);
	}

	/** The four colours of a theme, without easing: for swatches. */
	public static int[] target(String name, int custom) {
		if ("Astolfo".equals(name)) {
			double t = System.currentTimeMillis() * 0.002;
			float a = (float) (Math.sin(t) + 1) * 0.5f;
			float b = (float) (Math.sin(t + Math.PI / 2) + 1) * 0.5f;
			return new int[] {Ui.lerpColor(0xFFC084FC, 0xFFFF60A5, b), Ui.lerpColor(0xFFFF60A5, 0xFF60A5FA, a),
					Ui.lerpColor(0xFF8B5CF6, 0xFF3B82F6, a), Ui.lerpColor(0xFF4A1D96, 0xFF1E3A8A, b)};
		}
		if ("Custom".equals(name)) {
			int c = 0xFF000000 | custom;
			return new int[] {c, Ui.lerpColor(c, 0xFFFFFFFF, 0.42f), Ui.lerpColor(c, 0xFF000000, 0.35f), Ui.lerpColor(c, 0xFF000000, 0.55f)};
		}
		for (String[] p : PRESETS) {
			if (p[0].equals(name)) {
				return new int[] {hex(p[1]), hex(p[2]), hex(p[3]), hex(p[4])};
			}
		}
		if (isGradient(name) && !"Astolfo".equals(name)) {
			int[] e = ends(name);
			return new int[] {e[0], e[1], Ui.lerpColor(e[0], 0xFF000000, 0.2f), Ui.lerpColor(e[0], 0xFF000000, 0.6f)};
		}
		return target("Azure", custom);
	}

	public static int accent() {
		return pack(accent);
	}

	public static int light() {
		return pack(light);
	}

	public static int deep() {
		return pack(deep);
	}

	public static int tint() {
		return pack(tint);
	}

	/** {@code argb} with its alpha multiplied by {@code a}. */
	public static int alpha(int argb, float a) {
		return (Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a))) << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int from, int to, float t) {
		return Ui.lerpColor(from, to, Math.max(0, Math.min(1, t)));
	}

	/** Red at low health through yellow to green at full. */
	public static int health(float ratio) {
		float r = Math.max(0, Math.min(1, ratio));
		return r >= 0.5f ? mix(WARN, GOOD, (r - 0.5f) * 2) : mix(BAD, WARN, r * 2);
	}

	private static int hex(String s) {
		return 0xFF000000 | Integer.parseInt(s, 16);
	}

	private static float[] rgb(int c) {
		return new float[] {(c >> 16) & 255, (c >> 8) & 255, c & 255};
	}

	private static void ease(float[] shown, int to, float f) {
		shown[0] += (((to >> 16) & 255) - shown[0]) * f;
		shown[1] += (((to >> 8) & 255) - shown[1]) * f;
		shown[2] += ((to & 255) - shown[2]) * f;
	}

	private static int pack(float[] c) {
		return 0xFF000000 | Math.round(c[0]) << 16 | Math.round(c[1]) << 8 | Math.round(c[2]);
	}
}
