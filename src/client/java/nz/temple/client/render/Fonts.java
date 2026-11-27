package nz.temple.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;

public final class Fonts {
	public static final float DESIGN_SIZE = 16f;
	public static final FontDescription MEDIUM = new FontDescription.Resource(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "inter_medium"));
	public static final FontDescription SEMIBOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "inter_semibold"));
	public static final FontDescription BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "inter_bold"));
	public static final FontDescription UI_MEDIUM = new FontDescription.Resource(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "inter_ui_medium"));
	public static final FontDescription UI_SEMIBOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "inter_ui_semibold"));

	/**
	 * Pixel sizes each font is pre-rendered at: every size up to 20 px, then small steps. Text is drawn at exactly one of
	 * these sizes (see {@link #raster}), so its glyphs map 1:1 onto screen pixels instead of being resampled.
	 */
	private static final int[] RASTERS = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 22, 24, 26, 28, 30, 32, 35,
			38, 41, 44, 48, 52, 56, 60, 64, 70, 76, 82, 88, 96};
	private static final FontDescription[] UI_MEDIUM_SCALES = uiCuts("inter_ui_medium");
	private static final FontDescription[] UI_SEMIBOLD_SCALES = uiCuts("inter_ui_semibold");
	private static final FontDescription[] MEDIUM_CUTS = cuts("inter_medium", MEDIUM);
	private static final FontDescription[] SEMIBOLD_CUTS = cuts("inter_semibold", SEMIBOLD);
	private static final FontDescription[] BOLD_CUTS = cuts("inter_bold", BOLD);
	private Fonts() {
	}

	private static final java.util.Set<Object> SMOOTH = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	private static volatile Object lastSmooth;
	public static void smooth(Object atlasView) {
		synchronized (SMOOTH) {
			SMOOTH.add(atlasView);
		}
		RENDER_CACHE.clear();
	}
	public static boolean isSmooth(Object atlasView) {
		if (atlasView == lastSmooth) {
			return true;
		}
		synchronized (SMOOTH) {
			if (SMOOTH.contains(atlasView)) {
				lastSmooth = atlasView;
				return true;
			}
			return false;
		}
	}
	private record Measured(Component text, String shown, int width) {
	}

	private record Key(String s, FontDescription font) {
	}
	private static final int CACHE_LIMIT = 2048;
	private static final java.util.Map<Key, Measured> RENDER_CACHE = new java.util.HashMap<>();
	private static Measured measured(String s, FontDescription font) {
		String shown = nz.temple.client.modules.render.Spoof.apply(s);
		Key key = new Key(s, font);
		Measured m = RENDER_CACHE.get(key);
		if (m != null && m.shown.equals(shown)) {
			return m;
		}
		if (RENDER_CACHE.size() >= CACHE_LIMIT) {
			RENDER_CACHE.clear();
		}
		Component text = Component.literal(s).withStyle(Style.EMPTY.withFont(font));
		m = new Measured(text, shown, Minecraft.getInstance().font.width(text));
		RENDER_CACHE.put(key, m);
		return m;
	}
	private static boolean cacheable() {
		return com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread();
	}
	private static FontDescription[] cuts(String name, FontDescription base) {
		FontDescription[] out = new FontDescription[RASTERS.length];
		for (int i = 0; i < RASTERS.length; i++) {
			out[i] = RASTERS[i] == 48 ? base
					: new FontDescription.Resource(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, name + "_" + RASTERS[i]));
		}
		return out;
	}

	private static FontDescription[] uiCuts(String name) {
		FontDescription[] out = new FontDescription[10];
		for (int i = 0; i < out.length; i++) {
			out[i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, name + "_" + (i + 1)));
		}
		return out;
	}

	/** Whether {@code font} has pre-rendered pixel sizes (Temple's own text fonts). */
	public static boolean hasRasters(FontDescription font) {
		return font == MEDIUM || font == SEMIBOLD || font == BOLD;
	}

	/** The pre-rendered pixel size closest to {@code pixels}. */
	public static int raster(float pixels) {
		int best = RASTERS[0];
		for (int r : RASTERS) {
			if (Math.abs(r - pixels) < Math.abs(best - pixels) - 1e-4f) {
				best = r;
			}
		}
		return best;
	}

	/** The cut of {@code font} rendered at {@code raster} pixels (one of {@link #raster}'s results). */
	public static FontDescription cut(FontDescription font, int raster) {
		FontDescription[] cuts = font == MEDIUM ? MEDIUM_CUTS : font == SEMIBOLD ? SEMIBOLD_CUTS : font == BOLD ? BOLD_CUTS : null;
		if (cuts == null) {
			return font;
		}
		for (int i = 0; i < RASTERS.length; i++) {
			if (RASTERS[i] == raster) {
				return cuts[i];
			}
		}
		return sized(font, raster);
	}

	public static FontDescription sized(FontDescription font, float pixels) {
		FontDescription[] cuts = font == MEDIUM ? MEDIUM_CUTS : font == SEMIBOLD ? SEMIBOLD_CUTS : font == BOLD ? BOLD_CUTS : null;
		if (cuts == null) {
			return font;
		}
		int r = raster(pixels);
		for (int i = 0; i < RASTERS.length; i++) {
			if (RASTERS[i] == r) {
				return cuts[i];
			}
		}
		return cuts[cuts.length - 1];
	}
	public static Component text(String s, FontDescription font) {
		if (s != null && cacheable()) {
			return measured(s, font).text;
		}
		return Component.literal(s).withStyle(Style.EMPTY.withFont(font));
	}

	public static Component widget(Component label, boolean bold) {
		if (label == null || !nz.temple.client.modules.client.MenuModule.themedSettings()) {
			return label;
		}
		FontDescription current = label.getStyle().getFont();
		boolean ours = current instanceof FontDescription.Resource r && r.id().getPath().startsWith("inter_ui_");
		if (!FontDescription.DEFAULT.equals(current) && !ours) {
			return label;
		}
		// Rendered at the current GUI scale, so Minecraft's buttons and labels are drawn 1:1 instead of stretched.
		int scale = Math.max(1, Math.min(10, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale())));
		FontDescription font = (bold ? UI_SEMIBOLD_SCALES : UI_MEDIUM_SCALES)[scale - 1];
		return label.copy().withStyle(style -> style.withFont(font));
	}
	public static float width(String s, FontDescription font, float size) {
		if (s != null && cacheable()) {
			return measured(s, font).width * size / DESIGN_SIZE;
		}
		return Minecraft.getInstance().font.width(text(s, font)) * size / DESIGN_SIZE;
	}
}
