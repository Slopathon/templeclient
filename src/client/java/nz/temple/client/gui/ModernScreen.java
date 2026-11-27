package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import nz.temple.client.TempleNzClient;
import nz.temple.client.audio.UiSounds;
import nz.temple.client.input.KeyNames;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.GlassRenderer;
import nz.temple.client.render.Icons;
import nz.temple.client.render.LogoMark;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.render.VisualTheme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.Setting;
import nz.temple.setting.TextSetting;
import nz.temple.config.ConfigManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The Modern click GUI: one window with a category sidebar on the left and the category's modules as cards in columns.
 * A card shows the module's name, bind, switch and description; clicking it opens its settings inside the card.
 * The Legit / Blatant switch at the bottom of the sidebar only changes which modules are listed (see ModuleModes).
 */
public final class ModernScreen extends Screen {
	private static final float SIDEBAR = 152;
	private static final float TOP = 34;
	private static final float PAD = 12;
	private static final float GAP = 12;
	private static final float T_TITLE = 7.5f;
	private static final float T_BODY = 6.6f;
	private static final float T_SMALL = 5.8f;
	private static final int FIELD = 0x0DFFFFFF;
	private static final int MUTED = 0x8CFFFFFF;
	private static final int SOFT = 0xB3FFFFFF;
	private static final int GOLD = nz.temple.client.modules.client.HudModule.GOLD;

	/** What the content area shows. */
	private enum View { CATEGORY, ALL, FAVOURITES, RECENT, CONFIGS, THEMES }

	private static Category lastCategory = Category.COMBAT;
	private static View lastView = View.CATEGORY;
	/** Modules toggled or opened recently, newest first. */
	private static final java.util.LinkedList<String> RECENT = new java.util.LinkedList<>();
	private static final Map<Category, Float> lastScroll = new HashMap<>();
	private static final Set<String> lastExpanded = new HashSet<>();

	private final ModuleManager modules;
	private final Ui ui;
	private final Screen returnScreen;
	private Category category;
	private float scroll;
	private float scrollTarget;
	private float contentHeight;
	private final Set<String> expanded = lastExpanded;
	private View view = lastView;
	/** 0 → 1 after the content changes (tab, search): drives the fade and slide-in of the cards. */
	private float switchT = 0;
	/** The sidebar selection pill's y (eased towards the selected row). */
	private float selY = Float.NaN;
	private float dropT;
	private float frameDt = 0.016f;
	private long lastFrameReal;
	private float baseAlpha = 1;
	private int guiMouseX;
	private int guiMouseY;
	/** Search matches: settings, and the modules they belong to (opened automatically). */
	private final Set<Setting<?>> matched = new HashSet<>();
	private final Set<String> matchedModules = new HashSet<>();
	private final Set<String> closedInSearch = new HashSet<>();
	private String lastSearch = "";
	private List<Module> shownCache = List.of();
	/** While true, hit() registers nothing (content still opening). */
	private boolean hitsOff;
	private final StringBuilder search = new StringBuilder();
	private boolean searching;
	private KeybindSetting listening;
	/** Set when a key press was taken as a bind: the character event the same key sends next is dropped. */
	private boolean swallowChar;
	private NumberSetting dragging;
	private float dragX;
	private float dragW;
	private TextSetting editing;
	private final StringBuilder editBuffer = new StringBuilder();
	/** The open dropdown: its setting and the box it opened from, {x, y, w, h}. */
	private ModeSetting dropdown;
	private final float[] dropBox = new float[4];
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, float[]> eases = new HashMap<>();
	private long lastFrame;
	private float appear;
	private float s = 1;
	private float mx;
	private float my;
	/** The scrolling content area, {x0, y0, x1, y1}: hits outside it are dropped. */
	private final float[] content = new float[4];
	private boolean inContent;

	private interface Click {
		void run(int button, float x, float y);
	}

	private record Hit(float x0, float y0, float x1, float y1, Click click) {
	}

	public ModernScreen(ModuleManager modules, Ui ui, Screen returnScreen) {
		super(Component.literal("Temple"));
		this.modules = modules;
		this.ui = ui;
		this.returnScreen = returnScreen;
		this.category = lastCategory;
		this.view = lastView;
		this.scroll = this.scrollTarget = lastScroll.getOrDefault(category, 0f);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		lastCategory = category;
		lastView = view;
		lastScroll.put(category, scrollTarget);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(returnScreen);
	}

	// ---------------------------------------------------------------- layout helpers

	private float tw(String text, FontDescription font, float size) {
		return ui.textWidth(text, font, size);
	}

	private void text(String text, float x, float y, float size, int argb, FontDescription font) {
		ui.textMiddle(text, x, y, size, argb, font);
	}

	private void icon(Icons.Icon icon, float cx, float cy, float size, int argb) {
		// Icons are blitted directly, so they take the current fade (window open, cards sliding in) here.
		int alpha = Math.round((argb >>> 24) * ui.alpha());
		if (alpha < 3) {
			return;
		}
		Icons.draw(ui.graphics(), icon, ui.mapX(cx), ui.mapY(cy), size * s, alpha << 24 | (argb & 0xFFFFFF));
	}

	private boolean over(float x, float y, float w, float h) {
		boolean in = mx >= x && my >= y && mx <= x + w && my <= y + h;
		if (in && inContent) {
			in = mx >= content[0] && my >= content[1] && mx <= content[2] && my <= content[3];
		}
		return in;
	}

	private void hit(float x, float y, float w, float h, Click click) {
		if (hitsOff) {
			return;
		}
		float x0 = x;
		float y0 = y;
		float x1 = x + w;
		float y1 = y + h;
		if (inContent) {
			x0 = Math.max(x0, content[0]);
			y0 = Math.max(y0, content[1]);
			x1 = Math.min(x1, content[2]);
			y1 = Math.min(y1, content[3]);
			if (x1 <= x0 || y1 <= y0) {
				return;
			}
		}
		if (zoom != 1) {
			x0 = zox + (x0 - zox) * zoom;
			y0 = zoy + (y0 - zoy) * zoom;
			x1 = zox + (x1 - zox) * zoom;
			y1 = zoy + (y1 - zoy) * zoom;
		}
		hits.add(new Hit(x0, y0, x1, y1, click));
	}

	/** Eases towards 1 while {@code on}, towards 0 otherwise. */
	private float ease(String key, boolean on, float dt) {
		float[] v = eases.computeIfAbsent(key, k -> new float[] {on ? 1 : 0});
		float target = on ? 1 : 0;
		v[0] += (target - v[0]) * Math.min(1, dt * 16);
		if (Math.abs(target - v[0]) < 0.002f) {
			v[0] = target;
		}
		return v[0];
	}

	/** Eases a stored value towards {@code target} at {@code rate} per second. */
	private float approach(String key, float target, float dt, float rate) {
		float[] v = eases.computeIfAbsent(key, k -> new float[] {target});
		v[0] += (target - v[0]) * Math.min(1, dt * rate);
		if (Math.abs(target - v[0]) < 0.0005f) {
			v[0] = target;
		}
		return v[0];
	}

	private static float easeOut(float t) {
		t = Math.max(0, Math.min(1, t));
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	private void switched() {
		switchT = 0;
		dropdown = null;
	}

	private static void remember(Module m) {
		RECENT.remove(m.name());
		RECENT.addFirst(m.name());
		while (RECENT.size() > 12) {
			RECENT.removeLast();
		}
	}

	private static boolean favourite(Module m) {
		ConfigManager config = TempleNzClient.config();
		return config != null && config.getExtra("fav." + m.name(), 0) == 1;
	}

	private static void setFavourite(Module m, boolean on) {
		ConfigManager config = TempleNzClient.config();
		if (config != null) {
			config.putExtra("fav." + m.name(), on ? 1 : 0);
		}
	}

	private boolean isOpen(Module m) {
		if (matchedModules.contains(m.name())) {
			return !closedInSearch.contains(m.name());
		}
		return !COLLAPSED.contains(m.name());
	}

	/** The visual theme this frame is drawn in (HUD → Visual theme). */
	private static VisualTheme vt = VisualTheme.GLASS;

	private static int accent() {
		return 0xFFF2F2F2;
	}

	private static int withAlpha(int rgb, int alpha) {
		return alpha << 24 | (rgb & 0xFFFFFF);
	}

	/** Splits {@code text} into lines no wider than {@code width}; at most {@code max} lines, the last one cut with "…". */
	private List<String> wrap(String text, float size, float width, int max) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return lines;
		}
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (tw(next, Fonts.MEDIUM, size) <= width || line.isEmpty()) {
				line.setLength(0);
				line.append(next);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
				if (lines.size() == max) {
					break;
				}
			}
		}
		if (lines.size() < max && !line.isEmpty()) {
			lines.add(line.toString());
		} else if (lines.size() == max && !line.isEmpty()) {
			String last = lines.get(max - 1);
			while (!last.isEmpty() && tw(last + "…", Fonts.MEDIUM, size) > width) {
				last = last.substring(0, last.length() - 1);
			}
			lines.set(max - 1, last.stripTrailing() + "…");
		}
		return lines;
	}

	// ---------------------------------------------------------------- modules

	private boolean listed(Module m) {
		return m.listedInCategory() && HvhTab.listed(m);
	}

	private int listedCount(Category c) {
		int n = 0;
		for (Module m : modules.inCategory(c)) {
			if (listed(m)) {
				n++;
			}
		}
		return n;
	}

	private List<Category> categories() {
		List<Category> out = new ArrayList<>();
		for (Category c : Category.values()) {
			for (Module m : modules.inCategory(c)) {
				if (listed(m)) {
					out.add(c);
					break;
				}
			}
		}
		return out;
	}

	private List<Module> shown() {
		return shownCache;
	}

	/** Works out what the content area lists this frame (and which settings match the search). */
	private List<Module> computeShown() {
		List<Module> out = new ArrayList<>();
		String term = search.toString().trim().toLowerCase(Locale.ROOT);
		matched.clear();
		matchedModules.clear();
		if (!term.equals(lastSearch)) {
			lastSearch = term;
			closedInSearch.clear();
		}
		if (!term.isEmpty() && view != View.CONFIGS) {
			for (Module m : modules.all()) {
				if (listed(m) && (m.name().toLowerCase(Locale.ROOT).contains(term)
						|| m.displayName().toLowerCase(Locale.ROOT).contains(term))) {
					out.add(m);
				}
			}
			if (term.length() >= 2) {
				for (ModuleManager.SettingMatch match : modules.searchSettings(term)) {
					Module m = match.module();
					if (!listed(m) || !showable(match.setting())) {
						continue;
					}
					matched.add(match.setting());
					matchedModules.add(m.name());
					if (!out.contains(m)) {
						out.add(m);
					}
				}
			}
			return out;
		}
		switch (view) {
			case FAVOURITES -> {
				for (Module m : modules.all()) {
					if (listed(m) && favourite(m)) {
						out.add(m);
					}
				}
			}
			case RECENT -> {
				for (String name : RECENT) {
					Module m = modules.byName(name);
					if (m != null && listed(m)) {
						out.add(m);
					}
				}
			}
			case ALL -> {
				for (Category c : Category.values()) {
					for (Module m : modules.inCategory(c)) {
						if (listed(m)) {
							out.add(m);
						}
					}
				}
			}
			case CONFIGS, THEMES -> {
			}
			default -> {
				for (Module m : modules.inCategory(category)) {
					if (listed(m)) {
						out.add(m);
					}
				}
			}
		}
		return out;
	}

	private static boolean showable(Setting<?> setting) {
		return setting.isVisible() && !setting.isInternal();
	}

	// ---------------------------------------------------------------- frame

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		frameDt = dt;
		hits.clear();
		guiMouseX = mouseX;
		guiMouseY = mouseY;
		// Real time (not the per-frame cap), so content always finishes animating on time, even at low frame rates.
		float realDt = lastFrameReal == 0 ? 0.016f : Math.min(0.5f, (now - lastFrameReal) / 1e9f);
		lastFrameReal = now;
		switchT = Math.min(1, switchT + realDt / 0.45f);
		shownCache = computeShown();
		float visible = 1;
		ui.begin(g);
		ui.glassRenderer().placeLayer(g);
		s = (float) scaleFactor();
		float dw = width / s;
		float dh = height / s;
		float w = bigWindow ? dw - 24 : Math.min(dw - 40, 820);
		float h = bigWindow ? dh - 24 : Math.min(dh - 40, 480);
		appear = Math.min(1, appear + dt / 0.18f);
		float a = (1 - (1 - appear) * (1 - appear) * (1 - appear)) * visible;
		baseAlpha = a;
		float x = Math.round((dw - w) / 2);
		float y = Math.round((dh - h) / 2 + (1 - a) * 10);
		ui.transform(0, 0, s, s);
		mx = mouseX / s;
		my = mouseY / s;
		inContent = false;
		tipText = null;

		vt = VisualTheme.current();
		applyPalette(vt);
		ui.alpha(a);
		ui.shadow(x, y + 6, w, h, 14, 30, 0xA0000000);
		if (vt != VisualTheme.ONYX) {
			ui.glass(x, y, w, h, 14);
		}
		ui.fill(x, y, w, h, 14, C_WIN);
		ui.clip(x, y, x + SIDEBAR, y + h);
		ui.fill(x, y, SIDEBAR + 14, h, 14, C_SIDE);
		ui.unclip();
		ui.fill(x + SIDEBAR, y, 1, h, 0, C_EDGE);
		ui.ring(x, y, w, h, 14, 1, C_EDGE);
		drawSidebar(x, y, h, dt);
		drawTopBar(x + SIDEBAR + 1, y, w - SIDEBAR - 1, dt);

		scroll += (scrollTarget - scroll) * Math.min(1, dt * 14);
		content[0] = x + SIDEBAR + 1;
		content[1] = y + TOP + 1;
		content[2] = x + w;
		content[3] = y + h - 1;
		ui.clip(content[0], content[1], content[2], content[3]);
		inContent = true;
		if (view == View.CONFIGS) {
			float ox = x + SIDEBAR + 1 + PAD;
			float oy = y + TOP + 1 + PAD;
			beginZoom(PAGE_ZOOM, ox, oy);
			drawConfigs(ox, oy - scroll / PAGE_ZOOM, (w - SIDEBAR - 1 - PAD * 2) / PAGE_ZOOM, dt);
			endZoom();
		} else if (view == View.THEMES && search.isEmpty()) {
			drawThemes(x + SIDEBAR + 1 + PAD, y + TOP + 1 + PAD - scroll, w - SIDEBAR - 1 - PAD * 2, dt);
		} else {
			drawCards(x + SIDEBAR + 1 + PAD, y + TOP + 1 + PAD - scroll, w - SIDEBAR - 1 - PAD * 2, dt);
		}
		ui.alpha(a);
		inContent = false;
		ui.unclip();
		float maxScroll = Math.max(0, contentHeight - (content[3] - content[1]) + PAD * 2);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		if (maxScroll > 0) {
			float trackH = content[3] - content[1] - 8;
			float barH = Math.max(18, trackH * (trackH / (trackH + maxScroll)));
			float barY = content[1] + 4 + (trackH - barH) * (scroll / maxScroll);
			ui.fill(x + w - 5, barY, 2, barH, 1, 0xFF2A2A2A);
		}
		if (dropdown != null) {
			drawDropdown(g);
		} else {
			drawTip();
		}
		{
			float cw = w - SIDEBAR - 1;
			float ccx = x + SIDEBAR + 1 + cw / 2;
			beginZoom(PAGE_ZOOM, ccx, y + h);
			drawToast(ccx - cw / 2 / PAGE_ZOOM, y + h - h / PAGE_ZOOM, cw / PAGE_ZOOM, h / PAGE_ZOOM, dt);
			float keep = contentHeight / PAGE_ZOOM;
			contentHeight = keep;
			endZoom();
		}
	}


	private double scaleFactor() {
		MenuModule menu = TempleNzClient.modules() == null ? null : TempleNzClient.modules().get(MenuModule.class);
		double size = menu == null ? 0.65 : menu.scale();
		return size / 0.65 * UiScale.factor() * 0.92;
	}

	// ---------------------------------------------------------------- palette and sizes (black, white controls)

	private static int C_WIN;
	private static int C_SIDE;
	private static int C_EDGE;
	private static int C_LINE;
	private static int C_CARD;
	private static int C_CARD_EDGE;
	private static int C_FIELD;
	private static int C_FIELD_EDGE;
	private static int C_FIELD_EDGE_HOVER;
	private static int C_FIELD_HOVER;
	private static int C_ROW;
	private static int C_CHIP;
	private static int C_TRACK;
	private static int C_BOX;
	private static int C_SEG_SEL;
	private static int C_SEL_ROW;
	private static int C_HOVER_ROW;
	private static int C_TOGGLE_OFF;
	private static final int C_TEXT = 0xFFF2F2F2;
	private static final int C_TEXT2 = 0xFFD2D2D2;
	private static final int C_MUTE = 0xFF969696;
	private static final int C_LABEL = 0xFF9A9A9A;

	/** The colours each style builds the window from. None of them has a hue: colour comes only from the theme colour. */
	private static void applyPalette(VisualTheme style) {
		switch (style) {
			case GLASS -> {
				C_WIN = 0xC40A0B0E;
				C_SIDE = 0x38000000;
				C_EDGE = 0x26FFFFFF;
				C_LINE = 0x14FFFFFF;
				C_CARD = 0x0AFFFFFF;
				C_CARD_EDGE = 0x1AFFFFFF;
				C_FIELD = 0x0FFFFFFF;
				C_FIELD_EDGE = 0x1AFFFFFF;
				C_FIELD_EDGE_HOVER = 0x2EFFFFFF;
				C_FIELD_HOVER = 0x17FFFFFF;
				C_ROW = 0x0DFFFFFF;
				C_CHIP = 0x17FFFFFF;
				C_TRACK = 0x26FFFFFF;
				C_BOX = 0x0DFFFFFF;
				C_SEG_SEL = 0x59000000;
				C_SEL_ROW = 0x1AFFFFFF;
				C_HOVER_ROW = 0x0FFFFFFF;
				C_TOGGLE_OFF = 0x33FFFFFF;
			}
			case FROST -> {
				C_WIN = 0x8C1C1D21;
				C_SIDE = 0x12FFFFFF;
				C_EDGE = 0x47FFFFFF;
				C_LINE = 0x26FFFFFF;
				C_CARD = 0x14FFFFFF;
				C_CARD_EDGE = 0x2EFFFFFF;
				C_FIELD = 0x1AFFFFFF;
				C_FIELD_EDGE = 0x2EFFFFFF;
				C_FIELD_EDGE_HOVER = 0x47FFFFFF;
				C_FIELD_HOVER = 0x26FFFFFF;
				C_ROW = 0x14FFFFFF;
				C_CHIP = 0x1FFFFFFF;
				C_TRACK = 0x38FFFFFF;
				C_BOX = 0x14FFFFFF;
				C_SEG_SEL = 0x4D000000;
				C_SEL_ROW = 0x26FFFFFF;
				C_HOVER_ROW = 0x14FFFFFF;
				C_TOGGLE_OFF = 0x40FFFFFF;
			}
			default -> {
				C_WIN = 0xFF0A0A0A;
				C_SIDE = 0xFF0E0E0E;
				C_EDGE = 0xFF1C1C1C;
				C_LINE = 0xFF171717;
				C_CARD = 0xFF0A0A0A;
				C_CARD_EDGE = 0xFF1C1C1C;
				C_FIELD = 0xFF0E0E0E;
				C_FIELD_EDGE = 0xFF1F1F1F;
				C_FIELD_EDGE_HOVER = 0xFF2A2A2A;
				C_FIELD_HOVER = 0xFF141414;
				C_ROW = 0xFF101010;
				C_CHIP = 0xFF181818;
				C_TRACK = 0xFF1F1F1F;
				C_BOX = 0xFF121212;
				C_SEG_SEL = 0xFF050505;
				C_SEL_ROW = 0xFF1A1A1A;
				C_HOVER_ROW = 0xFF141414;
				C_TOGGLE_OFF = 0xFF262626;
			}
		}
	}

	static {
		applyPalette(VisualTheme.ONYX);
	}
	private static final float F_CARD = 11f;
	private static final float F_SET = 9.8f;
	private static final float F_DESC = 8.5f;
	private static final float F_CTRL = 9f;
	private static final float F_SIDE = 9.8f;
	private static final float F_LABEL = 8.2f;
	private static final float F_CHIP = 7.8f;
	private static final float DESC_LINE = 10.6f;
	/** Swatches offered under every colour setting: red, orange, yellow, green, light blue, blue, violet. */
	private static final int[] SWATCH = {0xFFE5484D, 0xFFF76B15, 0xFFFFD60A, 0xFF30A46C, 0xFF5AC8FA, 0xFF3358D4, 0xFF8E4EC6};

	/** The Configs and Settings pages are laid out at their original size and drawn this much larger, to match the cards. */
	private static final float PAGE_ZOOM = 1.38f;
	private float zoom = 1;
	private float zox;
	private float zoy;
	private float zoomBaseS;
	private float zoomMx;
	private float zoomMy;
	private final float[] zoomContent = new float[4];

	/** Draws what follows {@code z} times larger about ({@code ox}, {@code oy}); the mouse, clip and clicks follow. */
	private void beginZoom(float z, float ox, float oy) {
		zoom = z;
		zox = ox;
		zoy = oy;
		zoomBaseS = s;
		zoomMx = mx;
		zoomMy = my;
		System.arraycopy(content, 0, zoomContent, 0, 4);
		s = zoomBaseS * z;
		ui.transform(zoomBaseS * ox * (1 - z), zoomBaseS * oy * (1 - z), s, s);
		mx = ox + (mx - ox) / z;
		my = oy + (my - oy) / z;
		content[0] = ox + (content[0] - ox) / z;
		content[1] = oy + (content[1] - oy) / z;
		content[2] = ox + (content[2] - ox) / z;
		content[3] = oy + (content[3] - oy) / z;
	}

	private void endZoom() {
		contentHeight *= zoom;
		s = zoomBaseS;
		ui.transform(0, 0, s, s);
		mx = zoomMx;
		my = zoomMy;
		System.arraycopy(zoomContent, 0, content, 0, 4);
		zoom = 1;
	}

	/** The setting under the mouse's info icon this frame, shown as a tooltip after everything else. */
	private String tipText;
	private float tipX;
	private float tipY;
	/** Cards the user folded shut (cards are open by default). */
	private static final Set<String> COLLAPSED = new HashSet<>();
	/** Wrapped text, keyed by text, size and width: laying out every open card each frame would otherwise measure a lot. */
	private final Map<String, List<String>> wrapCache = new HashMap<>();
	private static boolean bigWindow;

	private List<String> wrapped(String text, float size, float width, int max) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String key = size + "|" + Math.round(width) + "|" + max + "|" + text;
		List<String> lines = wrapCache.get(key);
		if (lines == null) {
			if (wrapCache.size() > 4000) {
				wrapCache.clear();
			}
			lines = wrap(text, size, width, max);
			wrapCache.put(key, lines);
		}
		return lines;
	}

	private static Icons.Icon categoryIcon(Category c) {
		return switch (c) {
			case COMBAT -> Icons.Icon.SWORDS;
			case HVH -> Icons.Icon.SKULL;
			case MOVEMENT -> Icons.Icon.FOOTPRINTS;
			case RENDER -> Icons.Icon.MONITOR;
			case COSMETICS -> Icons.Icon.WAND;
			case WORLD -> Icons.Icon.PICKAXE;
			case PLAYER -> Icons.Icon.USER;
			case HUD -> Icons.Icon.LAYOUT;
			case CLIENT -> Icons.Icon.BOX;
		};
	}

	/** The icon shown beside a setting, by the kind of control it has. */
	private static Icons.Icon settingIcon(Setting<?> setting) {
		if (setting instanceof BoolSetting) {
			return Icons.Icon.TOGGLE;
		}
		if (setting instanceof NumberSetting) {
			return Icons.Icon.SLIDERS;
		}
		if (setting instanceof ModeSetting) {
			return Icons.Icon.LAYERS;
		}
		if (setting instanceof ColorSetting) {
			return Icons.Icon.PALETTE;
		}
		if (setting instanceof KeybindSetting) {
			return Icons.Icon.KEYBOARD;
		}
		if (setting instanceof ActionSetting) {
			return Icons.Icon.ZAP;
		}
		if (setting instanceof TextSetting) {
			return Icons.Icon.TYPE;
		}
		return Icons.Icon.LIST;
	}

	// ---------------------------------------------------------------- sidebar

	private void drawSidebar(float x, float y, float h, float dt) {
		LogoMark.draw3d(ui.graphics(), "menu", ui.mapX(x + 10), ui.mapY(y + 9.5f), 13 * s, ui.alpha());
		text("Temple", x + 28.5f, y + 16, 11, C_TEXT, Fonts.SEMIBOLD);

		List<Object> top = new ArrayList<>();
		top.add(View.ALL);
		top.addAll(categories());
		List<Object> settingsRows = List.of(View.CONFIGS, "Edit HUD", "Legacy GUI");
		// Rows keep the reference spacing when there is room and close up when the window is short.
		float listTop = y + 49;
		float bottomRows = y + h - 100;
		float needed = top.size() + settingsRows.size() + 2.4f;
		float step = Math.max(15, Math.min(21.5f, (bottomRows - listTop - 8) / needed));

		text("Client Categories", x + 10, listTop, F_LABEL, C_LABEL, Fonts.MEDIUM);
		float ry = listTop + step;
		Object selected = !search.isEmpty() && view != View.CONFIGS ? null : view == View.CATEGORY ? category : view;
		for (Object row : top) {
			sideRow(x, ry, step, row, row.equals(selected), dt);
			ry += step;
		}
		ry += step * 0.4f;
		text("Settings", x + 10, ry, F_LABEL, C_LABEL, Fonts.MEDIUM);
		ry += step;
		for (Object row : settingsRows) {
			sideRow(x, ry, step, row, row.equals(selected), dt);
			ry += step;
		}

		// Bottom: Settings (the Themes page) and Search, then the profile.
		float sy = y + h - 90;
		sideRow(x, sy, 21.5f, View.THEMES, selected == View.THEMES, dt);
		drawSearchRow(x, sy + 28, dt);
		float py = y + h - 44;
		float face = 25;
		String name = minecraft.getUser() == null ? "Player" : minecraft.getUser().getName();
		ui.fill(x + 8, py, face, face, 6, 0xFF1C1C1C);
		if (minecraft.player != null) {
			var pose = ui.graphics().pose();
			pose.pushMatrix();
			pose.translate(ui.mapX(x + 8), ui.mapY(py));
			float px = face * s / 8f;
			pose.scale(px, px);
			net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(ui.graphics(), minecraft.player.getSkin(), 0, 0, 8);
			pose.popMatrix();
		} else {
			String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
			text(initial, x + 8 + (face - tw(initial, Fonts.SEMIBOLD, 10)) / 2, py + face / 2, 10, C_TEXT, Fonts.SEMIBOLD);
		}
		text(name, x + 39, py + 8, 10, C_TEXT, Fonts.SEMIBOLD);
		text("temple.nz", x + 39, py + 18.5f, F_LABEL, C_LABEL, Fonts.MEDIUM);
	}

	/** One sidebar row, centred on {@code cy}: a view, a category or a link ("Edit HUD", "Legacy GUI"). */
	private void sideRow(float x, float cy, float step, Object row, boolean selected, float dt) {
		float rh = Math.min(20, step - 1);
		float top = cy - rh / 2;
		boolean over = over(x + 6, top, SIDEBAR - 12, rh);
		float hov = ease("rowh-" + row, over && !selected, dt);
		float lit = ease("rowsel-" + row, selected, dt);
		if (lit > 0.01f) {
			ui.fill(x + 6, top, SIDEBAR - 12, rh, 6, withAlpha(C_SEL_ROW, Math.round((C_SEL_ROW >>> 24) * lit)));
		}
		if (hov > 0.01f) {
			ui.fill(x + 6, top, SIDEBAR - 12, rh, 6, withAlpha(C_HOVER_ROW, Math.round((C_HOVER_ROW >>> 24) * hov)));
		}
		Icons.Icon icon;
		String label;
		Runnable action;
		if (row instanceof Category c) {
			icon = categoryIcon(c);
			label = c.displayName();
			action = () -> selectCategory(c);
		} else if (row == View.ALL) {
			icon = Icons.Icon.LAYOUT_GRID;
			label = "All";
			action = () -> selectView(View.ALL);
		} else if (row == View.CONFIGS) {
			icon = Icons.Icon.CLOUD;
			label = "Configs";
			action = () -> selectView(View.CONFIGS);
		} else if (row == View.THEMES) {
			icon = Icons.Icon.SETTINGS;
			label = "Settings";
			action = () -> selectView(View.THEMES);
		} else if ("Edit HUD".equals(row)) {
			icon = Icons.Icon.LAYOUT;
			label = "Edit HUD";
			action = () -> {
				if (minecraft.level != null) {
					UiSounds.tap();
					minecraft.gui.setScreen(new HudEditorScreen(this));
				}
			};
		} else {
			icon = Icons.Icon.MENU_STYLE;
			label = "Legacy GUI";
			action = () -> {
				UiSounds.tap();
				switchToLegacy();
			};
		}
		int color = Ui.lerpColor(0xFFD9D9D9, C_TEXT, Math.max(lit, hov));
		icon(icon, x + 16, cy, 10, color);
		text(label, x + 27, cy, F_SIDE, color, Fonts.SEMIBOLD);
		hit(x + 6, top, SIDEBAR - 12, rh, (b, px, py) -> action.run());
	}

	private void drawSearchRow(float x, float cy, float dt) {
		boolean active = searching || !search.isEmpty();
		float rh = 20;
		float top = cy - rh / 2;
		boolean over = over(x + 6, top, SIDEBAR - 12, rh);
		float hov = ease("rowh-search", over || active, dt);
		if (active) {
			ui.fill(x + 6, top, SIDEBAR - 12, rh, 6, C_FIELD_HOVER);
			ui.ring(x + 6, top, SIDEBAR - 12, rh, 6, 1, searching ? 0xFF3A3A3A : C_FIELD_EDGE);
		} else if (hov > 0.01f) {
			ui.fill(x + 6, top, SIDEBAR - 12, rh, 6, withAlpha(C_HOVER_ROW, Math.round((C_HOVER_ROW >>> 24) * hov)));
		}
		icon(Icons.Icon.SEARCH, x + 16, cy, 10, active ? C_TEXT : 0xFFD9D9D9);
		String shownText = active ? search + (searching && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : "Search";
		ui.clip(x + 26, top, x + SIDEBAR - 8, top + rh);
		text(shownText, x + 27, cy, F_SIDE, active ? C_TEXT : 0xFFD9D9D9, Fonts.SEMIBOLD);
		ui.unclip();
		hit(x + 6, top, SIDEBAR - 12, rh, (b, px, py) -> {
			searching = true;
			editing = null;
			nz.temple.client.compat.Input.startTyping(this);
		});
	}

	private void selectCategory(Category c) {
		if (c != category || !search.isEmpty() || view != View.CATEGORY) {
			if (view == View.CATEGORY) {
				lastScroll.put(category, scrollTarget);
			}
			view = View.CATEGORY;
			category = c;
			search.setLength(0);
			searching = false;
			scroll = scrollTarget = lastScroll.getOrDefault(c, 0f);
			switched();
			UiSounds.tap();
		}
	}

	private void selectView(View v) {
		if (v != view || !search.isEmpty()) {
			if (view == View.CATEGORY) {
				lastScroll.put(category, scrollTarget);
			}
			view = v;
			search.setLength(0);
			searching = false;
			scroll = scrollTarget = 0;
			switched();
			if (v == View.CONFIGS) {
				configsOpened();
			}
			UiSounds.tap();
		}
	}

	private void switchToLegacy() {
		MenuModule menu = TempleNzClient.modules() == null ? null : TempleNzClient.modules().get(MenuModule.class);
		if (menu != null) {
			menu.setClickGui("Legacy");
		}
		TempleNzClient.openMenu(returnScreen);
	}

	// ---------------------------------------------------------------- top bar

	private void drawTopBar(float x, float y, float w, float dt) {
		ui.fill(x, y + TOP, w, 1, 0, C_LINE);
		float cy = y + TOP / 2;
		boolean searchingModules = !search.isEmpty() && view != View.CONFIGS;
		String title = searchingModules ? "Search" : switch (view) {
			case ALL -> "All";
			case FAVOURITES -> "Favourites";
			case RECENT -> "Recent";
			case CONFIGS -> "Configs";
			case THEMES -> "Settings";
			default -> category.displayName();
		};
		// The panel button folds the sidebar labels away... here it simply toggles the larger window.
		boolean panelOver = over(x + 9, cy - 8, 16, 16);
		if (panelOver) {
			ui.fill(x + 9, cy - 8, 16, 16, 5, C_HOVER_ROW);
		}
		icon(Icons.Icon.PANEL_LEFT, x + 17, cy, 10, panelOver ? C_TEXT : C_TEXT2);
		hit(x + 9, cy - 8, 16, 16, (b, px, py) -> {
			bigWindow = !bigWindow;
			UiSounds.tap();
		});
		ui.fill(x + 32, cy - 6, 1, 12, 0, C_FIELD_EDGE_HOVER);
		text(title, x + 41, cy, F_SET, C_TEXT, Fonts.SEMIBOLD);
		if (searchingModules) {
			String count = shown().size() + " found";
			text(count, x + 47 + tw(title, Fonts.MEDIUM, F_SET), cy, F_DESC, C_MUTE, Fonts.MEDIUM);
		}

		// Right: favourites, recent and window size.
		Object[][] buttons = {
				{Icons.Icon.STAR, (Runnable) () -> selectView(View.FAVOURITES), view == View.FAVOURITES},
				{Icons.Icon.CLOCK, (Runnable) () -> selectView(View.RECENT), view == View.RECENT},
				{bigWindow ? Icons.Icon.MINIMIZE : Icons.Icon.MAXIMIZE, (Runnable) () -> bigWindow = !bigWindow, false}};
		float bx = x + w - 34;
		for (int i = buttons.length - 1; i >= 0; i--) {
			Object[] bt = buttons[i];
			boolean on = (Boolean) bt[2];
			boolean hov = over(bx - 8, cy - 8, 16, 16);
			if (hov || on) {
				ui.fill(bx - 8, cy - 8, 16, 16, 5, on ? C_SEL_ROW : C_HOVER_ROW);
			}
			icon((Icons.Icon) bt[0], bx, cy, 10, hov || on ? C_TEXT : C_TEXT2);
			Runnable run = (Runnable) bt[1];
			hit(bx - 8, cy - 8, 16, 16, (b, px, py) -> {
				UiSounds.tap();
				run.run();
			});
			bx -= 19;
		}
	}

	// ---------------------------------------------------------------- cards

	private void drawCards(float x, float y, float w, float dt) {
		List<Module> list = shown();
		int cols = w >= 560 ? 3 : w >= 360 ? 2 : 1;
		float colW = (w - GAP * (cols - 1)) / cols;
		float[] colY = new float[cols];
		for (int i = 0; i < cols; i++) {
			colY[i] = y;
		}
		if (list.isEmpty()) {
			String msg = !search.isEmpty() ? "Nothing matches \"" + search + "\""
					: view == View.FAVOURITES ? "No favourites yet. Click the star on a module to add it here."
					: view == View.RECENT ? "Modules you turn on or change show up here." : "No modules here";
			text(msg, x + (w - tw(msg, Fonts.MEDIUM, F_SET)) / 2, y + 40, F_SET, C_MUTE, Fonts.MEDIUM);
			contentHeight = 0;
			return;
		}
		int index = 0;
		for (Module m : list) {
			int col = 0;
			for (int i = 1; i < cols; i++) {
				if (colY[i] < colY[col] - 0.5f) {
					col = i;
				}
			}
			float cx = x + col * (colW + GAP);
			float h = card(m, cx, colY[col], colW, false, dt);
			if (colY[col] + h >= content[1] - 4 && colY[col] <= content[3] + 4) {
				float local = easeOut((switchT * 1.6f - Math.min(index, 14) * 0.05f) / 0.55f);
				ui.alpha(baseAlpha * local);
				card(m, cx, colY[col] + (1 - local) * 8, colW, true, dt);
				ui.alpha(baseAlpha);
			}
			index++;
			colY[col] += h + GAP;
		}
		float bottom = y;
		for (float c : colY) {
			bottom = Math.max(bottom, c);
		}
		contentHeight = bottom - y - GAP;
	}

	private static final float HEAD = 37;

	/** Lays out one card; draws it when {@code draw}. Returns its height. */
	private float card(Module m, float x, float y, float w, boolean draw, float dt) {
		boolean open = isOpen(m) && hasSettings(m);
		float inner = w - 23;
		float openT = draw ? approach("open-" + m.name(), open ? 1 : 0, 0, 0) : approach("open-" + m.name(), open ? 1 : 0, dt, 11);
		float reveal = easeOut(openT);
		float bodyH = openT > 0.001f ? settings(m, x + 11.5f, 0, inner, false, dt) : 0;
		float h = HEAD + bodyH * reveal + (openT > 0.001f ? 0 : 0);
		if (!draw) {
			return h;
		}
		boolean overCard = over(x, y, w, h);
		ui.fill(x, y, w, h, 9, C_CARD);
		ui.ring(x, y, w, h, 9, 1, overCard ? C_FIELD_EDGE_HOVER : C_CARD_EDGE);

		// The header folds the card. Registered first: clicks are matched newest first, so the controls win.
		hit(x, y, w, HEAD, (b, px, py) -> {
			if (b == InputConstants.MOUSE_BUTTON_LEFT || b == InputConstants.MOUSE_BUTTON_RIGHT) {
				if (isOpen(m)) {
					COLLAPSED.add(m.name());
					closedInSearch.add(m.name());
				} else {
					COLLAPSED.remove(m.name());
					closedInSearch.remove(m.name());
					remember(m);
				}
				dropdown = null;
				UiSounds.soft();
			}
		});
		boolean on = m.isEnabled();
		float hy = y + 18.5f;
		icon(categoryIcon(m.category()), x + 16.5f, hy, 11, C_TEXT);

		float swW = 20;
		float swX = x + w - 11.5f - swW;
		float starX = swX - 13;
		String bind = listening == m.keybind() ? "…" : m.keybind().isBound() ? KeyNames.name(m.keybind().get()) : "None";
		float chipW = tw(bind, Fonts.MEDIUM, F_CHIP) + 20;
		float chipX = starX - 8 - chipW;
		String name = m.displayName();
		float nameMax = chipX - 6 - (x + 27);
		while (name.length() > 3 && tw(name, Fonts.SEMIBOLD, F_CARD) > nameMax) {
			name = name.substring(0, name.length() - 2) + "…";
		}
		text(name, x + 27, hy, F_CARD, on ? C_TEXT : 0xFFCFCFCF, Fonts.SEMIBOLD);

		boolean chipOver = over(chipX, hy - 7, chipW, 14);
		ui.fill(chipX, hy - 7, chipW, 14, 4.5f, listening == m.keybind() ? C_FIELD_EDGE_HOVER : chipOver ? C_FIELD_HOVER : C_CHIP);
		icon(Icons.Icon.KEYBOARD, chipX + 7.5f, hy, 7, 0xFF8A8A8A);
		text(bind, chipX + 14, hy, F_CHIP, m.keybind().isBound() ? C_TEXT2 : 0xFF8A8A8A, Fonts.MEDIUM);
		hit(chipX, hy - 7, chipW, 14, (b, px, py) -> {
			if (b == InputConstants.MOUSE_BUTTON_RIGHT) {
				m.keybind().set(KeybindSetting.NONE);
				listening = null;
			} else {
				listening = listening == m.keybind() ? null : m.keybind();
			}
			UiSounds.tap();
		});

		boolean fav = favourite(m);
		boolean starOver = over(starX - 6, hy - 6, 12, 12);
		icon(Icons.Icon.STAR, starX, hy, 9, fav ? 0xFFFFD60A : starOver ? C_TEXT : C_TEXT2);
		hit(starX - 6, hy - 6, 12, 12, (b, px, py) -> {
			setFavourite(m, !favourite(m));
			UiSounds.toggle(favourite(m));
		});

		toggleSwitch(swX, hy - 5.75f, swW, 11.5f, on, "mod-" + m.name(), dt);
		hit(swX - 2, hy - 8, swW + 4, 16, (b, px, py) -> {
			if (!m.isAlwaysOn()) {
				m.toggle();
				remember(m);
				UiSounds.toggle(m.isEnabled());
			}
		});

		if (openT > 0.001f) {
			ui.fill(x + 11.5f, y + HEAD - 1, w - 23, 1, 0, C_LINE);
			boolean clipBody = reveal < 0.999f;
			if (clipBody) {
				ui.clip(x, y + HEAD, x + w, y + h);
			}
			boolean wasOff = hitsOff;
			hitsOff = wasOff || reveal < 0.97f;
			settings(m, x + 11.5f, y + HEAD, inner, true, dt);
			hitsOff = wasOff;
			if (clipBody) {
				ui.unclip();
			}
		}
		return h;
	}

	private static boolean hasSettings(Module m) {
		for (List<Setting<?>> group : m.groups()) {
			for (Setting<?> setting : group) {
				if (showable(setting)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A switch: white with a black knob when on, dark grey when off. */
	private void toggleSwitch(float x, float y, float w, float h, boolean on, String key, float dt) {
		float t = ease(key, on, dt);
		ui.fill(x, y, w, h, h / 2, Ui.lerpColor(C_TOGGLE_OFF, 0xFFF2F2F2, t));
		float knob = h - 3;
		ui.fill(x + 1.5f + (w - knob - 3) * t, y + 1.5f, knob, knob, knob / 2, Ui.lerpColor(0xFF8A8A8A, 0xFF0A0A0A, t));
	}

	// ---------------------------------------------------------------- settings

	private float settingsHeight(Module m, float x, float w) {
		return settings(m, x, 0, w, false, 0);
	}

	private void drawSettings(Module m, float x, float y, float w, float dt) {
		settings(m, x, y, w, true, dt);
	}

	/**
	 * The card body. The first group of settings is laid out in full (icon, name, description, control); every later group
	 * gets a heading and its settings in a compact box of rows, with the descriptions behind an info icon.
	 */
	private float settings(Module m, float x, float y, float w, boolean draw, float dt) {
		float start = y;
		y += 9;
		List<List<Setting<?>>> groups = m.groups();
		boolean any = false;
		boolean first = true;
		for (int gi = 0; gi < groups.size(); gi++) {
			List<Setting<?>> group = new ArrayList<>();
			for (Setting<?> setting : groups.get(gi)) {
				if (showable(setting)) {
					group.add(setting);
				}
			}
			if (group.isEmpty()) {
				continue;
			}
			any = true;
			if (first) {
				first = false;
				for (Setting<?> setting : group) {
					y += row(m, setting, x, y, w, draw, dt);
				}
				continue;
			}
			// Heading.
			String title = m.groupTitle(gi);
			if (draw) {
				icon(Icons.Icon.SETTINGS_2, x + 4.5f, y + 7, 9.5f, C_TEXT);
				text(title, x + 15.5f, y + 7, F_SET, C_TEXT, Fonts.SEMIBOLD);
			}
			y += 18;
			float boxTop = y;
			float rowsH = 3;
			for (Setting<?> setting : group) {
				rowsH += compactRow(m, setting, x + 3, boxTop + rowsH, w - 6, false, dt) + 2.5f;
			}
			rowsH += 0.5f;
			if (draw) {
				ui.ring(x, boxTop, w, rowsH, 8, 1, C_FIELD_EDGE);
				float ry = boxTop + 3;
				for (Setting<?> setting : group) {
					ry += compactRow(m, setting, x + 3, ry, w - 6, true, dt) + 2.5f;
				}
			}
			y += rowsH + 11;
		}
		if (!any) {
			if (draw) {
				text("No settings", x, y + 5, F_DESC, C_MUTE, Fonts.MEDIUM);
			}
			y += 14;
		}
		return y - start + 3;
	}

	/** A full setting: icon and name, description underneath, then its control. Returns the height it takes. */
	private float row(Module m, Setting<?> setting, float x, float y, float w, boolean draw, float dt) {
		if (draw && matched.contains(setting)) {
			float rh = row(m, setting, x, y, w, false, dt);
			ui.fill(x - 5, y - 3, w + 10, rh - 4, 6, C_FIELD_HOVER);
			ui.ring(x - 5, y - 3, w + 10, rh - 4, 6, 1, 0xFF333333);
		}
		String key = m.name() + "/" + setting.key();
		List<String> desc = wrapped(setting.description(), F_DESC, w, 3);
		float descH = desc.size() * DESC_LINE;
		float titleY = y + 7;
		float under = titleY + 5 + descH;
		if (draw) {
			icon(settingIcon(setting), x + 4.5f, titleY, 9.5f, C_TEXT);
			text(setting.name(), x + 15.5f, titleY, F_SET, C_TEXT, Fonts.SEMIBOLD);
			float dy = titleY + 12;
			for (String line : desc) {
				text(line, x, dy, F_DESC, C_MUTE, Fonts.MEDIUM);
				dy += DESC_LINE;
			}
		}
		if (setting instanceof BoolSetting b) {
			if (draw) {
				float t = ease(key, b.get(), dt);
				float cb = 10.5f;
				float bx = x + w - cb;
				float by = titleY - cb / 2;
				ui.fill(bx, by, cb, cb, 3, Ui.lerpColor(C_BOX, 0xFFF2F2F2, t));
				if (t < 0.99f) {
					ui.ring(bx, by, cb, cb, 3, 1, withAlpha(0x3A3A3A, Math.round(255 * (1 - t))));
				}
				if (t > 0.3f) {
					icon(Icons.Icon.CHECK, bx + cb / 2, titleY, 7.5f, withAlpha(0x0A0A0A, Math.round(255 * t)));
				}
				hit(x - 4, y, w + 8, under - y + 4, (bt, px, py) -> {
					b.toggle();
					UiSounds.toggle(b.get());
				});
			}
			return under - y + 12;
		}
		if (setting instanceof NumberSetting n) {
			float ty = under + 7;
			if (draw) {
				String value = n.display();
				text(value, x + w - tw(value, Fonts.MEDIUM, F_DESC), titleY, F_DESC, 0xFF9A9A9A, Fonts.MEDIUM);
				slider(n, key, x, ty, w, dt);
			}
			return ty - y + 17;
		}
		if (setting instanceof ModeSetting mode) {
			List<String> options = mode.options();
			boolean segmented = options.size() == 2 && segmentsFit(options, w);
			float by = under + 5.5f;
			float bh = segmented ? 19.5f : 21.5f;
			if (draw) {
				if (segmented) {
					segments(mode, key, x, by, w, bh, dt);
				} else {
					dropdownBox(mode, x, by, w, bh);
				}
			}
			return by + bh - y + 12;
		}
		if (setting instanceof ColorSetting color) {
			float by = under + 5.5f;
			float bh = 24;
			if (draw) {
				int argb = color.get();
				hexChip(argb, x + w, titleY, color.followsTheme());
				swatches(m, color, x, by, w, bh);
			}
			return by + bh - y + 12;
		}
		if (setting instanceof KeybindSetting kb) {
			float by = under + 5.5f;
			float bh = 21.5f;
			if (draw) {
				boolean active = listening == kb;
				boolean hov = over(x, by, w, bh);
				fieldBox(x, by, w, bh, active || hov);
				String name = active ? "Press a key…" : KeyNames.name(kb.get());
				float lw = tw(name, Fonts.MEDIUM, F_CTRL) + 13;
				icon(Icons.Icon.KEYBOARD, x + (w - lw) / 2 + 4, by + bh / 2, 8, 0xFF9A9A9A);
				text(name, x + (w - lw) / 2 + 13, by + bh / 2, F_CTRL, C_TEXT, Fonts.MEDIUM);
				hit(x, by, w, bh, (bt, px, py) -> {
					if (bt == InputConstants.MOUSE_BUTTON_RIGHT) {
						kb.set(KeybindSetting.NONE);
						listening = null;
					} else {
						listening = active ? null : kb;
					}
					UiSounds.tap();
				});
			}
			return by + bh - y + 12;
		}
		if (setting instanceof ActionSetting action) {
			float by = under + 5.5f;
			float bh = 21.5f;
			if (draw) {
				boolean hov = over(x, by, w, bh);
				fieldBox(x, by, w, bh, hov);
				String label = action.label();
				float lw = tw(label, Fonts.MEDIUM, F_CTRL) + 13;
				icon(Icons.Icon.REFRESH, x + (w - lw) / 2 + 4, by + bh / 2, 8, hov ? C_TEXT : C_TEXT2);
				text(label, x + (w - lw) / 2 + 13, by + bh / 2, F_CTRL, hov ? C_TEXT : C_TEXT2, Fonts.MEDIUM);
				hit(x, by, w, bh, (bt, px, py) -> {
					action.press();
					UiSounds.tap();
				});
			}
			return by + bh - y + 12;
		}
		if (setting instanceof TextSetting text) {
			float by = under + 5.5f;
			float bh = 21.5f;
			if (draw) {
				textBox(text, x, by, w, bh);
			}
			return by + bh - y + 12;
		}
		// Lists, files and entity pickers: edited in the Legacy GUI, which has the full editors for them.
		float by = under + 5.5f;
		float bh = 21.5f;
		if (draw) {
			boolean hov = over(x, by, w, bh);
			fieldBox(x, by, w, bh, hov);
			String label = "Edit…";
			float lw = tw(label, Fonts.MEDIUM, F_CTRL) + 13;
			icon(Icons.Icon.PENCIL, x + (w - lw) / 2 + 4, by + bh / 2, 8, C_TEXT2);
			text(label, x + (w - lw) / 2 + 13, by + bh / 2, F_CTRL, C_TEXT2, Fonts.MEDIUM);
			hit(x, by, w, bh, (bt, px, py) -> TempleNzClient.openGlassMenu(this, m));
		}
		return by + bh - y + 12;
	}

	/** A compact setting inside a group box: info icon, name, and a small control on the right. Returns its height. */
	private float compactRow(Module m, Setting<?> setting, float x, float y, float w, boolean draw, float dt) {
		float h = 20;
		if (!draw) {
			return h;
		}
		String key = m.name() + "/" + setting.key();
		float cy = y + h / 2;
		ui.fill(x, y, w, h, 6.5f, C_ROW);
		if (matched.contains(setting)) {
			ui.ring(x, y, w, h, 6.5f, 1, 0xFF3A3A3A);
		}
		boolean infoOver = over(x + 3, cy - 6, 12, 12);
		icon(Icons.Icon.INFO, x + 9.5f, cy, 8.5f, infoOver ? C_TEXT : 0xFFBDBDBD);
		if (infoOver && setting.description() != null && !setting.description().isBlank()) {
			tipText = setting.description();
			tipX = x + 3;
			tipY = y + h + 3;
		}
		float right = x + w - 8;
		float nameMax = w * 0.42f;
		String name = setting.name();
		if (setting instanceof BoolSetting || setting instanceof ActionSetting) {
			nameMax = w - 50;
		}
		while (name.length() > 3 && tw(name, Fonts.MEDIUM, F_CTRL) > nameMax) {
			name = name.substring(0, name.length() - 2) + "…";
		}
		text(name, x + 18, cy, F_CTRL, C_TEXT, Fonts.SEMIBOLD);
		if (setting instanceof BoolSetting b) {
			toggleSwitch(right - 20, cy - 5.75f, 20, 11.5f, b.get(), key, dt);
			hit(x, y, w, h, (bt, px, py) -> {
				b.toggle();
				UiSounds.toggle(b.get());
			});
		} else if (setting instanceof NumberSetting n) {
			String value = n.display();
			float vw = Math.max(tw(value, Fonts.MEDIUM, F_DESC), 22);
			text(value, right - tw(value, Fonts.MEDIUM, F_DESC), cy, F_DESC, 0xFF9A9A9A, Fonts.MEDIUM);
			float sw = Math.max(30, w * 0.3f);
			float sx = right - vw - 8 - sw;
			slider(n, key, sx, cy - 1.5f, sw, dt);
		} else if (setting instanceof ModeSetting mode) {
			boolean open = dropdown == mode;
			String value = mode.get();
			float vw = tw(value, Fonts.MEDIUM, F_DESC);
			text(value, right - 10 - vw, cy, F_DESC, open ? C_TEXT : 0xFFBDBDBD, Fonts.MEDIUM);
			icon(Icons.Icon.CHEVRONS_UP_DOWN, right - 3, cy, 7.5f, 0xFF9A9A9A);
			float bx = right - 14 - vw;
			hit(bx, y, right - bx + 8, h, (bt, px, py) -> openDropdown(mode, bx, y, right - bx + 8, h));
			if (open) {
				dropBox[0] = bx;
				dropBox[1] = y;
				dropBox[2] = right - bx + 8;
				dropBox[3] = h;
			}
		} else if (setting instanceof ColorSetting color) {
			float chipRight = right - 14;
			hexChip(color.get(), chipRight, cy, color.followsTheme());
			icon(Icons.Icon.PIPETTE, right - 3, cy, 8, 0xFFBDBDBD);
			hit(right - 10, y, 18, h, (bt, px, py) -> TempleNzClient.openGlassMenu(this, m));
			int index = 0;
			for (int i = 0; i < SWATCH.length; i++) {
				if (!color.followsTheme() && (SWATCH[i] & 0xFFFFFF) == (color.get() & 0xFFFFFF)) {
					index = i + 1;
				}
			}
			boolean toTheme = index == SWATCH.length && color.canFollowTheme();
			int next = SWATCH[index % SWATCH.length];
			float cw = tw(color.followsTheme() ? "Theme" : String.format("#%06X", color.get() & 0xFFFFFF), Fonts.MEDIUM, F_CHIP) + 9;
			hit(chipRight - cw, y, cw, h, (bt, px, py) -> {
				if (toTheme) {
					color.followTheme();
				} else {
					color.set(0xFF000000 | (next & 0xFFFFFF));
				}
				UiSounds.tap();
			});
		} else if (setting instanceof KeybindSetting kb) {
			boolean active = listening == kb;
			String value = active ? "…" : KeyNames.name(kb.get());
			float cw = tw(value, Fonts.MEDIUM, F_CHIP) + 12;
			ui.fill(right - cw, cy - 6.5f, cw, 13, 4, active ? 0xFF2A2A2A : C_CHIP);
			text(value, right - cw + 6, cy, F_CHIP, C_TEXT2, Fonts.MEDIUM);
			hit(x, y, w, h, (bt, px, py) -> {
				if (bt == InputConstants.MOUSE_BUTTON_RIGHT) {
					kb.set(KeybindSetting.NONE);
					listening = null;
				} else {
					listening = active ? null : kb;
				}
				UiSounds.tap();
			});
		} else if (setting instanceof ActionSetting action) {
			icon(Icons.Icon.REFRESH, right - 3, cy, 8, 0xFFBDBDBD);
			hit(x, y, w, h, (bt, px, py) -> {
				action.press();
				UiSounds.tap();
			});
		} else if (setting instanceof TextSetting text) {
			boolean active = editing == text;
			String value = active ? editBuffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : text.get();
			if (value == null || value.isEmpty()) {
				value = active ? "" : text.placeholder();
			}
			while (value.length() > 2 && tw(value, Fonts.MEDIUM, F_DESC) > w * 0.45f) {
				value = value.substring(1);
			}
			text(value, right - 10 - tw(value, Fonts.MEDIUM, F_DESC), cy, F_DESC, 0xFFBDBDBD, Fonts.MEDIUM);
			icon(Icons.Icon.PENCIL, right - 3, cy, 7.5f, 0xFF9A9A9A);
			hit(x, y, w, h, (bt, px, py) -> {
				commitEdit();
				editing = text;
				editBuffer.setLength(0);
				editBuffer.append(text.get() == null ? "" : text.get());
				searching = false;
				nz.temple.client.compat.Input.startTyping(this);
			});
		} else {
			icon(Icons.Icon.PENCIL, right - 3, cy, 7.5f, 0xFFBDBDBD);
			hit(x, y, w, h, (bt, px, py) -> TempleNzClient.openGlassMenu(this, m));
		}
		return h;
	}

	/** A slider: dark track, white fill, white knob ringed in black. {@code cy} is the track's centre line. */
	private void slider(NumberSetting n, String key, float x, float cy, float w, float dt) {
		float target = (float) Math.max(0, Math.min(1, n.fraction()));
		float frac = dragging == n ? approach(key + "/v", target, 1, 1) : approach(key + "/v", target, dt, 18);
		ui.fill(x, cy - 1.5f, w, 3, 1.5f, C_TRACK);
		ui.fill(x, cy - 1.5f, Math.max(3, w * frac), 3, 1.5f, 0xFFEDEDED);
		float kx = x + w * frac;
		float k = 8.5f;
		ui.fill(kx - k / 2, cy - k / 2, k, k, k / 2, 0xFF0A0A0A);
		ui.fill(kx - k / 2 + 1.3f, cy - k / 2 + 1.3f, k - 2.6f, k - 2.6f, (k - 2.6f) / 2, 0xFFF5F5F5);
		hit(x - 4, cy - 6, w + 8, 12, (bt, px, py) -> {
			dragging = n;
			dragX = x;
			dragW = w;
			dragTo(px);
		});
	}

	private void fieldBox(float x, float y, float w, float h, boolean hover) {
		ui.fill(x, y, w, h, 7, hover ? C_FIELD_HOVER : C_FIELD);
		ui.ring(x, y, w, h, 7, 1, hover ? C_FIELD_EDGE_HOVER : C_FIELD_EDGE);
	}

	private void dropdownBox(ModeSetting mode, float x, float y, float w, float h) {
		boolean open = dropdown == mode;
		boolean hov = over(x, y, w, h);
		fieldBox(x, y, w, h, open || hov);
		text(mode.get(), x + 8, y + h / 2, F_CTRL, C_TEXT, Fonts.SEMIBOLD);
		icon(Icons.Icon.CHEVRONS_UP_DOWN, x + w - 10, y + h / 2, 8.5f, 0xFF9A9A9A);
		hit(x, y, w, h, (bt, px, py) -> openDropdown(mode, x, y, w, h));
		if (open) {
			// Keep the list under the box where it is now (the card may have scrolled since it opened).
			dropBox[0] = x;
			dropBox[1] = y;
			dropBox[2] = w;
			dropBox[3] = h;
		}
	}

	private void openDropdown(ModeSetting mode, float x, float y, float w, float h) {
		if (dropdown == mode) {
			dropdown = null;
		} else {
			dropdown = mode;
			dropT = 0;
			dropBox[0] = x;
			dropBox[1] = y;
			dropBox[2] = w;
			dropBox[3] = h;
		}
		UiSounds.soft();
	}

	private void segments(ModeSetting mode, String key, float x, float y, float w, float h, float dt) {
		List<String> options = mode.options();
		ui.fill(x, y, w, h, 7, C_FIELD_HOVER);
		float seg = (w - 4) / options.size();
		int sel = Math.max(0, mode.index());
		float at = approach(key + "/seg", sel, dt, 16);
		ui.fill(x + 2 + seg * at, y + 2, seg, h - 4, 5.5f, C_SEG_SEL);
		ui.ring(x + 2 + seg * at, y + 2, seg, h - 4, 5.5f, 1, C_FIELD_EDGE);
		for (int i = 0; i < options.size(); i++) {
			String opt = options.get(i);
			float lw = tw(opt, Fonts.MEDIUM, F_DESC + 0.3f);
			float sx = x + 2 + seg * i;
			text(opt, sx + (seg - lw) / 2, y + h / 2, F_DESC + 0.3f, i == sel ? C_TEXT : 0xFF8A8A8A, Fonts.MEDIUM);
			hit(sx, y, seg, h, (bt, px, py) -> {
				mode.trySet(opt);
				UiSounds.tap();
			});
		}
	}

	/** The colour's hex code on a chip tinted with it, right-aligned at {@code right}. */
	private void hexChip(int argb, float right, float cy, boolean theme) {
		String hex = theme ? "Theme" : String.format("#%06X", argb & 0xFFFFFF);
		float cw = tw(hex, Fonts.MEDIUM, F_CHIP) + 9;
		int rgb = 0xFF000000 | argb;
		ui.fill(right - cw, cy - 6, cw, 12, 3.5f, Ui.lerpColor(rgb, 0xFF0A0A0A, 0.72f));
		text(hex, right - cw + 4.5f, cy, F_CHIP, Ui.lerpColor(rgb, 0xFFFFFFFF, 0.2f), Fonts.MEDIUM);
	}

	private void swatches(Module m, ColorSetting color, float x, float y, float w, float h) {
		ui.fill(x, y, w, h, 8, C_BOX);
		int argb = color.get();
		boolean following = color.followsTheme();
		float d = 11.5f;
		float pick = 16;
		int slots = SWATCH.length + (color.canFollowTheme() ? 1 : 0);
		float step = Math.min(21.5f, (w - 16 - pick) / slots);
		float cy = y + h / 2;
		float left = x + 13;
		if (color.canFollowTheme()) {
			// The theme swatch: the theme's two colours, picked to follow the theme.
			float cx = left;
			if (following) {
				ui.fill(cx - d / 2 - 3, cy - d / 2 - 3, d + 6, d + 6, (d + 6) / 2, C_SEL_ROW);
				ui.ring(cx - d / 2 - 3, cy - d / 2 - 3, d + 6, d + 6, (d + 6) / 2, 1, C_FIELD_EDGE_HOVER);
			}
			ui.gradient(cx - d / 2, cy - d / 2, d, d, d / 2, nz.temple.client.render.Theme.primary(),
					nz.temple.client.render.Theme.secondary(), 2);
			icon(Icons.Icon.SPARKLE, cx, cy, 7.5f, 0xFF0A0A0A);
			boolean hov = over(cx - d / 2 - 2, cy - d / 2 - 2, d + 4, d + 4);
			if (hov) {
				tipText = "Follow the theme colour";
				tipX = cx - d / 2;
				tipY = cy + d / 2 + 6;
			}
			hit(cx - d / 2 - 2, cy - d / 2 - 2, d + 4, d + 4, (bt, px, py) -> {
				color.followTheme();
				UiSounds.tap();
			});
			left += step;
		}
		for (int i = 0; i < SWATCH.length; i++) {
			int sw = SWATCH[i];
			float cx = left + i * step;
			boolean selected = !following && (sw & 0xFFFFFF) == (argb & 0xFFFFFF);
			if (selected) {
				ui.fill(cx - d / 2 - 3, cy - d / 2 - 3, d + 6, d + 6, (d + 6) / 2, C_SEL_ROW);
				ui.ring(cx - d / 2 - 3, cy - d / 2 - 3, d + 6, d + 6, (d + 6) / 2, 1, C_FIELD_EDGE_HOVER);
			}
			boolean hov = over(cx - d / 2 - 2, cy - d / 2 - 2, d + 4, d + 4);
			float grow = hov && !selected ? 1 : 0;
			ui.fill(cx - d / 2 - grow / 2, cy - d / 2 - grow / 2, d + grow, d + grow, (d + grow) / 2, sw);
			hit(cx - d / 2 - 2, cy - d / 2 - 2, d + 4, d + 4, (bt, px, py) -> {
				color.set(0xFF000000 | (sw & 0xFFFFFF));
				UiSounds.tap();
			});
		}
		float px = x + w - 11;
		boolean pickOver = over(px - 7, cy - 7, 14, 14);
		icon(Icons.Icon.PIPETTE, px, cy, 9, pickOver ? C_TEXT : 0xFFBDBDBD);
		hit(px - 7, cy - 7, 14, 14, (bt, ppx, ppy) -> TempleNzClient.openGlassMenu(this, m));
	}

	private void textBox(TextSetting text, float x, float y, float w, float h) {
		boolean active = editing == text;
		fieldBox(x, y, w, h, active || over(x, y, w, h));
		String value = active ? editBuffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : text.get();
		boolean empty = !active && (value == null || value.isEmpty());
		ui.clip(x + 4, y, x + w - 4, y + h);
		text(empty ? text.placeholder() : value, x + 8, y + h / 2, F_CTRL, empty ? C_MUTE : C_TEXT, Fonts.MEDIUM);
		ui.unclip();
		hit(x, y, w, h, (bt, px, py) -> {
			commitEdit();
			editing = text;
			editBuffer.setLength(0);
			editBuffer.append(text.get() == null ? "" : text.get());
			searching = false;
			nz.temple.client.compat.Input.startTyping(this);
		});
	}

	private boolean segmentsFit(List<String> options, float w) {
		float seg = (w - 4) / options.size();
		for (String o : options) {
			if (tw(o, Fonts.MEDIUM, F_DESC + 0.3f) > seg - 8) {
				return false;
			}
		}
		return true;
	}

	/** A setting's description, shown when the mouse is on its info icon. */
	private void drawTip() {
		if (tipText == null) {
			return;
		}
		List<String> lines = wrapped(tipText, F_DESC, 170, 6);
		float w = 8;
		for (String line : lines) {
			w = Math.max(w, tw(line, Fonts.MEDIUM, F_DESC) + 14);
		}
		float h = lines.size() * DESC_LINE + 8;
		float x = Math.min(tipX, width / s - w - 6);
		float y = tipY + h > height / s - 6 ? tipY - h - 26 : tipY;
		ui.shadow(x, y + 2, w, h, 7, 10, 0x99000000);
		ui.fill(x, y, w, h, 7, 0xFF131313);
		ui.ring(x, y, w, h, 7, 1, 0xFF2A2A2A);
		float ly = y + 4 + DESC_LINE / 2;
		for (String line : lines) {
			text(line, x + 7, ly, F_DESC, C_TEXT2, Fonts.MEDIUM);
			ly += DESC_LINE;
		}
	}

	private void dragTo(float px) {
		if (dragging != null) {
			double before = dragging.get();
			dragging.setFraction(Math.max(0, Math.min(1, (px - dragX) / dragW)));
			if (dragging.get() != before) {
				UiSounds.slider(dragging.fraction());
			}
		}
	}

	// ---------------------------------------------------------------- dropdown (drawn above everything)

	private float dropRowH() {
		return 17;
	}

	private float[] dropRect() {
		List<String> options = dropdown.options();
		float h = 4 + options.size() * dropRowH();
		float x = dropBox[0];
		float y = dropBox[1] + dropBox[3] + 3;
		float maxY = height / s - 6;
		if (y + h > maxY) {
			y = Math.max(6, dropBox[1] - 3 - h);
		}
		return new float[] {x, y, dropBox[2], h};
	}

	private void drawDropdown(GuiGraphicsExtractor g) {
		dropT = Math.min(1, dropT + frameDt / 0.14f);
		float e = easeOut(dropT);
		float[] r = dropRect();
		r[1] -= (1 - e) * 4;
		List<String> options = dropdown.options();
		int hovered = -1;
		if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] + 2) {
			int i = (int) Math.floor((my - r[1] - 2) / dropRowH());
			hovered = i < options.size() ? i : -1;
		}
		GlassRenderer glass = TempleNzClient.glass();
		boolean overlay = glass != null && glass.available() && glass.hasOverlayRoom(0, 3);
		float k = s;
		if (overlay) {
			glass.overlayRect(ui.mapX(r[0]), ui.mapY(r[1]), r[2] * k, r[3] * k, 7 * k, withAlpha(0xFF101010, Math.round(0xFC * e)), 0, 0);
			glass.overlayRect(ui.mapX(r[0]), ui.mapY(r[1]), r[2] * k, r[3] * k, 7 * k, withAlpha(0xFF2A2A2A, Math.round(0xFF * e)), 1f, 0);
			if (hovered >= 0) {
				glass.overlayRect(ui.mapX(r[0] + 2), ui.mapY(r[1] + 2 + hovered * dropRowH()), (r[2] - 4) * k, dropRowH() * k, 5 * k, 0xFF1C1C1C, 0, 0);
			}
			glass.placeOverlayLayer(g, (int) Math.floor(ui.mapX(r[0])), (int) Math.floor(ui.mapY(r[1])),
					(int) Math.ceil(ui.mapX(r[0] + r[2])), (int) Math.ceil(ui.mapY(r[1] + r[3])));
		} else {
			g.fill(Math.round(ui.mapX(r[0])), Math.round(ui.mapY(r[1])), Math.round(ui.mapX(r[0] + r[2])), Math.round(ui.mapY(r[1] + r[3])), 0xFC101010);
		}
		g.nextStratum();
		ui.alpha(e);
		for (int i = 0; i < options.size(); i++) {
			String opt = options.get(i);
			float cy = r[1] + 2 + i * dropRowH() + dropRowH() / 2;
			boolean selected = opt.equals(dropdown.get());
			text(opt, r[0] + 8, cy, F_CTRL, i == hovered || selected ? C_TEXT : 0xFFB0B0B0, Fonts.MEDIUM);
			if (selected) {
				icon(Icons.Icon.CHECK, r[0] + r[2] - 10, cy, 8, C_TEXT);
			}
		}
		ui.alpha(baseAlpha);
	}


	// ---------------------------------------------------------------- configs

	private boolean cloudTab;
	/** The text field being typed in on the Configs page ("name" or "publish"), or null. */
	private String field;
	private final StringBuilder fieldText = new StringBuilder();
	private String confirmKey;
	private long confirmUntil;
	private final List<JsonObject> cloudItems = new ArrayList<>();
	private boolean cloudLoading;
	private boolean cloudMore;
	private int cloudPage;
	private String cloudError;
	private String cloudSort = "top";
	private boolean cloudMine;
	private int cloudRequest;
	private String cloudQuery = "";
	private long cloudQueryChanged;
	private boolean publishing;
	private boolean publishBusy;
	private String toast;
	private long toastAt;
	private String undoCode;

	private void configsOpened() {
		field = null;
		confirmKey = null;
		if (cloudTab && cloudItems.isEmpty() && !cloudLoading) {
			refreshCloud(false);
		}
	}

	private void showToast(String message, String undo) {
		toast = message;
		toastAt = System.currentTimeMillis();
		undoCode = undo;
	}

	/** True on the second click within a few seconds: used for Load, Overwrite and Delete. */
	private boolean confirm(String key) {
		long now = System.currentTimeMillis();
		if (key.equals(confirmKey) && now < confirmUntil) {
			confirmKey = null;
			return true;
		}
		confirmKey = key;
		confirmUntil = now + 3000;
		UiSounds.soft();
		return false;
	}

	private boolean confirming(String key) {
		return key.equals(confirmKey) && System.currentTimeMillis() < confirmUntil;
	}

	private void applyCode(String code, String title) {
		ConfigManager config = TempleNzClient.config();
		if (config == null || code == null) {
			showToast("Could not load " + title + ".", null);
			return;
		}
		String before = config.exportCode();
		ConfigManager.ImportResult result = config.importCode(code);
		if (!result.ok()) {
			showToast(result.error(), null);
			return;
		}
		showToast("Loaded " + title + ": " + result.modules() + (result.modules() == 1 ? " module" : " modules") + " set up.", before);
		UiSounds.toggle(true);
	}

	private static String str(JsonObject o, String key) {
		return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	private static int num(JsonObject o, String key) {
		return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
	}

	private static boolean flag(JsonObject o, String key) {
		return o != null && o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
	}

	private static String ago(long epochMillis) {
		long sec = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000);
		if (sec < 60) {
			return "just now";
		}
		if (sec < 3600) {
			return sec / 60 + " min ago";
		}
		if (sec < 86400) {
			return sec / 3600 + " h ago";
		}
		return sec / 86400 + (sec / 86400 == 1 ? " day ago" : " days ago");
	}

	private void refreshCloud(boolean next) {
		int id = ++cloudRequest;
		int page = next ? cloudPage + 1 : 0;
		cloudLoading = true;
		cloudError = null;
		cloudQuery = view == View.CONFIGS ? search.toString().trim() : "";
		ConfigLibrary.browse(cloudQuery, cloudSort, page, cloudMine).thenAccept(r -> minecraft.execute(() -> {
			if (id != cloudRequest) {
				return;
			}
			cloudLoading = false;
			if (!ConfigLibrary.ok(r)) {
				cloudError = ConfigLibrary.reason(r);
				return;
			}
			if (!next) {
				cloudItems.clear();
			}
			for (JsonElement el : r.getAsJsonArray("configs")) {
				cloudItems.add(el.getAsJsonObject());
			}
			cloudPage = page;
			cloudMore = flag(r, "more");
			if (!next) {
				switched();
			}
		}));
	}

	private void loadCloud(JsonObject item) {
		String code = str(item, "code");
		String title = "\"" + str(item, "title") + "\"";
		showToast("Loading " + title + "…", null);
		ConfigLibrary.get(code, true).thenAccept(r -> minecraft.execute(() -> {
			if (!ConfigLibrary.ok(r)) {
				showToast(ConfigLibrary.reason(r), null);
				return;
			}
			applyCode(str(r.getAsJsonObject("config"), "data"), title);
		}));
	}

	private void voteCloud(JsonObject item) {
		int was = num(item, "vote");
		int now = was == 1 ? 0 : 1;
		item.addProperty("vote", now);
		item.addProperty("score", num(item, "score") - was + now);
		ConfigLibrary.vote(str(item, "code"), now);
		UiSounds.toggle(now == 1);
	}

	private void publish() {
		ConfigManager config = TempleNzClient.config();
		String title = fieldText.toString().trim();
		if (config == null || publishBusy) {
			return;
		}
		if (title.length() < ConfigLibrary.TITLE_MIN) {
			showToast("Give it a title of at least " + ConfigLibrary.TITLE_MIN + " characters.", null);
			return;
		}
		int on = (int) modules.all().stream().filter(m -> m.isEnabled() && !m.isAlwaysOn()).count();
		publishBusy = true;
		ConfigLibrary.publish(title, "", config.exportCode(), on).thenAccept(r -> minecraft.execute(() -> {
			publishBusy = false;
			if (ConfigLibrary.ok(r)) {
				String code = str(r, "code");
				minecraft.keyboardHandler.setClipboard(code);
				showToast("Published \"" + title + "\". Its code " + code + " is on your clipboard.", null);
				publishing = false;
				field = null;
				fieldText.setLength(0);
				refreshCloud(false);
			} else {
				showToast(ConfigLibrary.reason(r), null);
			}
		}));
	}

	/** A rounded button; {@code strong} fills it with the accent colour. */
	private void button(float x, float y, float w, float h, String label, Icons.Icon icon, boolean strong, Runnable action) {
		boolean hov = over(x, y, w, h);
		float glow = ease("btn-" + label + "@" + Math.round(x) + "," + Math.round(y), hov, frameDt);
		if (strong) {
			ui.fill(x, y, w, h, h / 2.6f, Ui.lerpColor(0xFFE8E8E8, 0xFFFFFFFF, glow));
		} else {
			ui.fill(x, y, w, h, h / 2.6f, Ui.lerpColor(C_FIELD, C_FIELD_HOVER, glow));
			ui.ring(x, y, w, h, h / 2.6f, 1, Ui.lerpColor(C_FIELD_EDGE, 0xFF2E2E2E, glow));
		}
		int fg = strong ? 0xFF0A0A0A : hov ? C_TEXT : C_TEXT2;
		float lw = tw(label, Fonts.MEDIUM, T_SMALL);
		float total = lw + (icon != null ? 10 : 0);
		float lx = x + (w - total) / 2;
		if (icon != null) {
			icon(icon, lx + 3, y + h / 2, 6, fg);
			lx += 10;
		}
		text(label, lx, y + h / 2, T_SMALL, fg, Fonts.MEDIUM);
		hit(x, y, w, h, (b, px, py) -> {
			UiSounds.tap();
			action.run();
		});
	}

	private void plainCard(float x, float y, float w, float h, float hover) {
		ui.fill(x, y, w, h, 7, C_CARD);
		ui.ring(x, y, w, h, 7, 1, Ui.lerpColor(C_CARD_EDGE, C_FIELD_EDGE_HOVER, hover));
	}

	private float buttonW(String label, Icons.Icon icon) {
		return tw(label, Fonts.MEDIUM, T_SMALL) + (icon != null ? 10 : 0) + 16;
	}

	private void textField(String id, float x, float y, float w, float h, String placeholder) {
		boolean active = id.equals(field);
		ui.fill(x, y, w, h, 5, active ? C_FIELD_HOVER : C_FIELD);
		ui.ring(x, y, w, h, 5, 1, active ? 0xFF3A3A3A : C_FIELD_EDGE);
		String value = active ? fieldText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : "";
		boolean empty = !active || fieldText.isEmpty();
		text(empty && !active ? placeholder : value, x + 7, y + h / 2, T_SMALL + 0.3f, empty && !active ? MUTED : 0xFFFFFFFF, Fonts.MEDIUM);
		hit(x, y, w, h, (b, px, py) -> {
			if (!id.equals(field)) {
				field = id;
				fieldText.setLength(0);
			}
			searching = false;
			nz.temple.client.compat.Input.startTyping(this);
		});
	}

	private void submitField() {
		if ("name".equals(field)) {
			String name = ConfigProfiles.clean(fieldText.toString());
			if (name.isEmpty()) {
				showToast("Type a name first.", null);
				return;
			}
			if (ConfigProfiles.save(name)) {
				showToast("Saved \"" + name + "\".", null);
				fieldText.setLength(0);
				field = null;
				switched();
			} else {
				showToast("Could not save the config.", null);
			}
		} else if ("publish".equals(field)) {
			publish();
		}
	}

	/**
	 * The Settings page. Style (Onyx, Glass, Frost) changes only how the menus and the HUD are built, never their colours.
	 * Under it, the theme colour: a single colour or a gradient, which the menus, the HUD and every module colour set to follow
	 * the theme all use. Nothing here changes which modules are on or how they are set up.
	 */
	private void drawThemes(float x, float y, float w, float dt) {
		float top = y;
		var hm = TempleNzClient.hudSettings();
		if (hm == null) {
			return;
		}
		float gap = 10;

		y = section(Icons.Icon.LAYOUT, "Style", "How the menus and the HUD look. A style never changes colours.", x, y, w);
		String[][] styles = {{"Onyx", "Matte black"}, {"Glass", "Dark, blurred glass"}, {"Frost", "Light frosted glass"}};
		float sw = (w - gap * 2) / 3;
		float sh = 92;
		for (int i = 0; i < styles.length; i++) {
			String name = styles[i][0];
			float tx = x + i * (sw + gap);
			boolean active = hm.visualTheme().equals(name);
			float hov = ease("style-" + name, over(tx, y, sw, sh), dt);
			tile(tx, y, sw, sh, hov, active);
			stylePreview(name, tx + 8, y + 8, sw - 16, sh - 42);
			text(name, tx + 10, y + sh - 24, F_SET, C_TEXT, Fonts.SEMIBOLD);
			text(styles[i][1], tx + 10, y + sh - 12, F_DESC, C_MUTE, Fonts.MEDIUM);
			hit(tx, y, sw, sh, (b, px, py) -> {
				hm.setVisualTheme(name);
				UiSounds.tap();
			});
		}
		y += sh + 22;

		String theme = hm.hudTheme.get();
		y = section(Icons.Icon.PALETTE, "Colour", "One colour for the menus, the HUD and every module colour that follows the theme.", x, y, w);
		List<String> solids = nz.temple.client.hud.HudTheme.SOLIDS;
		int cols = w > 520 ? 4 : 3;
		float cw = (w - gap * (cols - 1)) / cols;
		float ch = 28;
		for (int i = 0; i < solids.size(); i++) {
			String name = solids.get(i);
			float tx = x + (i % cols) * (cw + gap);
			float ty = y + (i / cols) * (ch + gap);
			boolean active = name.equals(theme);
			float hov = ease("colour-" + name, over(tx, ty, cw, ch), dt);
			tile(tx, ty, cw, ch, hov, active);
			int c = "Custom".equals(name) ? 0xFF000000 | hm.hudColor.get() : nz.temple.client.hud.HudTheme.target(name, hm.hudColor.get())[0];
			ui.fill(tx + 9, ty + ch / 2 - 6, 12, 12, 6, c);
			text(name, tx + 28, ty + ch / 2, F_CTRL, active ? C_TEXT : C_TEXT2, Fonts.SEMIBOLD);
			if (active) {
				icon(Icons.Icon.CHECK, tx + cw - 12, ty + ch / 2, 8.5f, C_TEXT);
			}
			hit(tx, ty, cw, ch, (b, px, py) -> {
				hm.hudTheme.set(name);
				UiSounds.tap();
			});
		}
		y += ((solids.size() + cols - 1) / cols) * (ch + gap) + 2;
		if ("Custom".equals(theme)) {
			y += row(hm, hm.hudColor, x, y, w, true, dt);
		}
		y += 14;

		y = section(Icons.Icon.SPARKLE, "Gradient", "Two colours that flow into each other across the menus, the HUD and effects.", x, y, w);
		List<String> gradients = nz.temple.client.hud.HudTheme.GRADIENT_NAMES;
		float gh = 44;
		for (int i = 0; i < gradients.size(); i++) {
			String name = gradients.get(i);
			float tx = x + (i % cols) * (cw + gap);
			float ty = y + (i / cols) * (gh + gap);
			boolean active = name.equals(theme);
			float hov = ease("gradient-" + name, over(tx, ty, cw, gh), dt);
			tile(tx, ty, cw, gh, hov, active);
			int[] ends = nz.temple.client.hud.HudTheme.ends(name);
			int a = ends[0];
			int b = ends[1];
			if ("Astolfo".equals(name)) {
				float f = (float) ((Math.sin(System.currentTimeMillis() / 600.0) + 1) / 2);
				a = Ui.lerpColor(0xFFFF60A5, 0xFFC084FC, f);
				b = Ui.lerpColor(0xFFC084FC, 0xFF60A5FA, f);
			}
			ui.gradient(tx + 8, ty + 8, cw - 16, 12, 6, a, b, 2);
			text(name, tx + 9, ty + gh - 12, F_CTRL, active ? C_TEXT : C_TEXT2, Fonts.SEMIBOLD);
			if (active) {
				icon(Icons.Icon.CHECK, tx + cw - 12, ty + gh - 12, 8.5f, C_TEXT);
			}
			hit(tx, ty, cw, gh, (bt, px, py) -> {
				hm.hudTheme.set(name);
				UiSounds.tap();
			});
		}
		y += ((gradients.size() + cols - 1) / cols) * (gh + gap) + 2;
		if (nz.temple.client.hud.HudTheme.isGradient(theme)) {
			y += row(hm, hm.themeSpeed, x, y, w, true, dt);
		}
		if (!hm.darkHud()) {
			y += 10;
			y += row(hm, hm.panelTint, x, y, w, true, dt);
		}
		contentHeight = y - top;
	}

	/** A section heading with its explanation underneath. Returns the y below it. */
	private float section(Icons.Icon icon, String title, String note, float x, float y, float w) {
		icon(icon, x + 5, y + 7, 10, C_TEXT);
		text(title, x + 17, y + 7, F_CARD, C_TEXT, Fonts.SEMIBOLD);
		text(note, x, y + 21, F_DESC, C_MUTE, Fonts.MEDIUM);
		return y + 34;
	}

	private void tile(float x, float y, float w, float h, float hover, boolean active) {
		ui.fill(x, y, w, h, 8, Ui.lerpColor(C_CARD, C_FIELD_HOVER, hover));
		ui.ring(x, y, w, h, 8, active ? 1.4f : 1, active ? C_TEXT : Ui.lerpColor(C_CARD_EDGE, C_FIELD_EDGE_HOVER, hover));
	}

	/** A small window drawn in style {@code name}, without colour: a sidebar, a few rows and a card. */
	private void stylePreview(String name, float x, float y, float w, float h) {
		switch (name) {
			case "Glass" -> {
				ui.glass(x, y, w, h, 6);
				ui.fill(x, y, w, h, 6, 0xB80B0C10);
				ui.ring(x, y, w, h, 6, 1, 0x30FFFFFF);
			}
			case "Frost" -> {
				ui.glass(x, y, w, h, 7);
				ui.gradient(x, y, w, h, 7, 0x8C3A3B40, 0xA61A1B1F, 1);
				ui.ring(x, y, w, h, 7, 1, 0x59FFFFFF);
			}
			default -> {
				ui.fill(x, y, w, h, 6, 0xFF0A0A0A);
				ui.ring(x, y, w, h, 6, 1, 0xFF262626);
			}
		}
		float side = w * 0.28f;
		int bar = "Onyx".equals(name) ? 0xFF2A2A2A : 0x40FFFFFF;
		for (int i = 0; i < 4; i++) {
			ui.fill(x + 6, y + 8 + i * 8, side - 12, 3.5f, 1.75f, i == 1 ? 0xFFE6E6E6 : bar);
		}
		float cx = x + side + 4;
		float cwid = w - side - 10;
		ui.fill(cx, y + 6, cwid, h - 12, 4, "Onyx".equals(name) ? 0xFF0E0E0E : 0x14FFFFFF);
		ui.ring(cx, y + 6, cwid, h - 12, 4, 1, "Onyx".equals(name) ? 0xFF222222 : 0x26FFFFFF);
		ui.fill(cx + 6, y + 12, cwid * 0.45f, 3.5f, 1.75f, 0xFFE6E6E6);
		ui.fill(cx + cwid - 18, y + 11, 12, 6, 3, 0xFFE6E6E6);
		ui.fill(cx + 6, y + 22, cwid - 12, 2.5f, 1.25f, bar);
		ui.fill(cx + 6, y + 22, (cwid - 12) * 0.55f, 2.5f, 1.25f, 0xFFE6E6E6);
	}

	private void drawConfigs(float x, float y, float w, float dt) {
		float top = y;
		float tabsW = 190;
		float seg = (tabsW - 4) / 2;
		ui.fill(x, y, tabsW, 18, 6, FIELD);
		float t = approach("cfg-tab", cloudTab ? 1 : 0, dt, 14);
		ui.fill(x + 2 + seg * t, y + 2, seg, 14, 5, 0x1CFFFFFF);
		String[] tabs = {"My configs", "Cloud library"};
		Icons.Icon[] tabIcons = {Icons.Icon.FOLDER, Icons.Icon.GLOBE};
		for (int i = 0; i < 2; i++) {
			boolean sel = (i == 1) == cloudTab;
			float tx = x + 2 + seg * i;
			float lw = tw(tabs[i], Fonts.MEDIUM, T_BODY) + 11;
			icon(tabIcons[i], tx + (seg - lw) / 2 + 3, y + 9, 6.5f, sel ? 0xFFFFFFFF : MUTED);
			text(tabs[i], tx + (seg - lw) / 2 + 11, y + 9, T_BODY, sel ? 0xFFFFFFFF : MUTED, Fonts.MEDIUM);
			boolean cloud = i == 1;
			hit(tx, y, seg, 18, (b, px, py) -> {
				if (cloudTab != cloud) {
					cloudTab = cloud;
					field = null;
					search.setLength(0);
					scrollTarget = 0;
					switched();
					if (cloud && cloudItems.isEmpty()) {
						refreshCloud(false);
					}
					UiSounds.tap();
				}
			});
		}
		y += 26;
		if (!cloudTab) {
			y = drawLocalConfigs(x, y, w, dt);
		} else {
			y = drawCloudConfigs(x, y, w, dt);
		}
		contentHeight = y - top;
	}

	private float drawLocalConfigs(float x, float y, float w, float dt) {
		float saveW = buttonW("Save current", Icons.Icon.PLUS);
		float importW = buttonW("Import code", Icons.Icon.PASTE);
		textField("name", x, y, w - saveW - importW - 12, 18, "Name your current setup…");
		button(x + w - saveW - importW - 6, y, saveW, 18, "Save current", Icons.Icon.PLUS, true, () -> {
			if (!"name".equals(field)) {
				field = "name";
				fieldText.setLength(0);
				nz.temple.client.compat.Input.startTyping(this);
				showToast("Type a name, then press Save current or Enter.", null);
			} else {
				submitField();
			}
		});
		button(x + w - importW, y, importW, 18, "Import code", Icons.Icon.PASTE, false, () -> {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip == null || !ConfigManager.looksLikeCode(clip.trim())) {
				showToast("Copy a Temple config code first, then press Import code.", null);
				return;
			}
			applyCode(clip.trim(), "the pasted config");
		});
		y += 28;
		String filter = search.toString().trim().toLowerCase(Locale.ROOT);
		List<ConfigProfiles.Profile> list = new ArrayList<>();
		for (ConfigProfiles.Profile p : ConfigProfiles.list()) {
			if (filter.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(filter)) {
				list.add(p);
			}
		}
		if (list.isEmpty()) {
			String msg = filter.isEmpty() ? "No saved configs yet. Name your setup above and press Save current." : "No configs match \"" + filter + "\".";
			text(msg, x + (w - tw(msg, Fonts.MEDIUM, T_BODY)) / 2, y + 24, T_BODY, MUTED, Fonts.MEDIUM);
			return y + 50;
		}
		int index = 0;
		for (ConfigProfiles.Profile p : list) {
			float local = easeOut((switchT * 1.6f - Math.min(index, 14) * 0.05f) / 0.55f);
			ui.alpha(baseAlpha * local);
			float ry = y + (1 - local) * 8;
			float rh = 30;
			boolean hov = over(x, ry, w, rh);
			float hv = ease("cfg-" + p.name(), hov, dt);
			plainCard(x, ry, w, rh, hv);
			ui.fill(x + 8, ry + 7, 16, 16, 5, C_SEL_ROW);
			icon(Icons.Icon.FOLDER, x + 16, ry + 15, 7.5f, C_TEXT);
			text(p.name(), x + 31, ry + 11, T_TITLE, 0xFFFFFFFF, Fonts.SEMIBOLD);
			text("Saved " + ago(p.savedMillis()), x + 31, ry + 21, T_SMALL, MUTED, Fonts.MEDIUM);
			float bx = x + w - 8;
			String delKey = "del:" + p.name();
			String delLabel = confirming(delKey) ? "Delete?" : "";
			float dw = confirming(delKey) ? buttonW("Delete?", Icons.Icon.TRASH) : 20;
			bx -= dw;
			button(bx, ry + 7, dw, 16, delLabel, Icons.Icon.TRASH, false, () -> {
				if (confirm(delKey)) {
					ConfigProfiles.delete(p);
					showToast("Deleted \"" + p.name() + "\".", null);
				}
			});
			bx -= 4 + 20;
			button(bx, ry + 7, 20, 16, "", Icons.Icon.COPY, false, () -> {
				String code = ConfigProfiles.code(p);
				if (code != null) {
					minecraft.keyboardHandler.setClipboard(code);
					showToast("Copied the code for \"" + p.name() + "\". Anyone can import it.", null);
				}
			});
			String saveKey = "over:" + p.name();
			String overLabel = confirming(saveKey) ? "Overwrite?" : "Update";
			float ow = buttonW(overLabel, Icons.Icon.REFRESH);
			bx -= 4 + ow;
			button(bx, ry + 7, ow, 16, overLabel, Icons.Icon.REFRESH, false, () -> {
				if (confirm(saveKey)) {
					ConfigProfiles.save(p.name());
					showToast("Updated \"" + p.name() + "\" with your current setup.", null);
				}
			});
			String loadKey = "load:" + p.name();
			String loadLabel = confirming(loadKey) ? "Load now?" : "Load";
			float lw = buttonW(loadLabel, Icons.Icon.DOWNLOAD);
			bx -= 4 + lw;
			button(bx, ry + 7, lw, 16, loadLabel, Icons.Icon.DOWNLOAD, true, () -> {
				if (confirm(loadKey)) {
					applyCode(ConfigProfiles.code(p), "\"" + p.name() + "\"");
				}
			});
			ui.alpha(baseAlpha);
			y += rh + 6;
			index++;
		}
		return y;
	}

	private float drawCloudConfigs(float x, float y, float w, float dt) {
		// Debounced search: the library is asked again shortly after typing stops.
		String q = search.toString().trim();
		if (!q.equals(cloudQuery)) {
			if (cloudQueryChanged == 0) {
				cloudQueryChanged = System.currentTimeMillis();
			} else if (System.currentTimeMillis() - cloudQueryChanged > 450) {
				cloudQueryChanged = 0;
				refreshCloud(false);
			}
		}
		String[][] sorts = {{"top", "Top"}, {"new", "New"}, {"downloads", "Most loaded"}};
		float sx = x;
		for (String[] sort : sorts) {
			boolean sel = sort[0].equals(cloudSort);
			float bw = tw(sort[1], Fonts.MEDIUM, T_SMALL) + 14;
			boolean hov = over(sx, y, bw, 16);
			ui.fill(sx, y, bw, 16, 8, sel ? 0xFF262626 : hov ? 0x14FFFFFF : FIELD);
			text(sort[1], sx + 7, y + 8, T_SMALL, sel || hov ? 0xFFFFFFFF : SOFT, Fonts.MEDIUM);
			String key = sort[0];
			hit(sx, y, bw, 16, (b, px, py) -> {
				if (!cloudSort.equals(key)) {
					cloudSort = key;
					refreshCloud(false);
					UiSounds.tap();
				}
			});
			sx += bw + 4;
		}
		float mw = tw("Mine", Fonts.MEDIUM, T_SMALL) + 14;
		boolean mhov = over(sx + 4, y, mw, 16);
		ui.fill(sx + 4, y, mw, 16, 8, cloudMine ? 0xFF262626 : mhov ? 0x14FFFFFF : FIELD);
		text("Mine", sx + 11, y + 8, T_SMALL, cloudMine || mhov ? 0xFFFFFFFF : SOFT, Fonts.MEDIUM);
		hit(sx + 4, y, mw, 16, (b, px, py) -> {
			cloudMine = !cloudMine;
			refreshCloud(false);
			UiSounds.tap();
		});
		float pw = buttonW("Publish", Icons.Icon.SHARE);
		button(x + w - pw, y, pw, 16, "Publish", Icons.Icon.SHARE, true, () -> {
			publishing = !publishing;
			field = publishing ? "publish" : null;
			fieldText.setLength(0);
			if (publishing) {
				nz.temple.client.compat.Input.startTyping(this);
			}
		});
		button(x + w - pw - 24, y, 20, 16, "", Icons.Icon.REFRESH, false, () -> refreshCloud(false));
		y += 24;
		if (publishing) {
			float sendW = buttonW(publishBusy ? "Publishing…" : "Publish now", null);
			textField("publish", x, y, w - sendW - 6, 18, "Title for your config…");
			button(x + w - sendW, y, sendW, 18, publishBusy ? "Publishing…" : "Publish now", null, true, this::publish);
			y += 22;
			text("Shares your current setup with everyone. You can remove it later from Mine.", x + 2, y + 4, T_SMALL, MUTED, Fonts.MEDIUM);
			y += 14;
		}
		if (cloudError != null) {
			text(cloudError, x + (w - tw(cloudError, Fonts.MEDIUM, T_BODY)) / 2, y + 24, T_BODY, 0xCCFF8A8A, Fonts.MEDIUM);
			return y + 50;
		}
		if (cloudItems.isEmpty()) {
			String msg = cloudLoading ? "Loading the library…" : "No configs found.";
			text(msg, x + (w - tw(msg, Fonts.MEDIUM, T_BODY)) / 2, y + 24, T_BODY, MUTED, Fonts.MEDIUM);
			return y + 50;
		}
		int index = 0;
		for (JsonObject item : new ArrayList<>(cloudItems)) {
			float local = easeOut((switchT * 1.6f - Math.min(index, 14) * 0.05f) / 0.55f);
			ui.alpha(baseAlpha * local);
			float ry = y + (1 - local) * 8;
			float rh = 32;
			String code = str(item, "code");
			boolean hov = over(x, ry, w, rh);
			float hv = ease("cloud-" + code, hov, dt);
			plainCard(x, ry, w, rh, hv);
			// Votes.
			boolean voted = num(item, "vote") == 1;
			boolean vhov = over(x + 6, ry + 5, 20, 22);
			ui.fill(x + 6, ry + 5, 20, 22, 6, voted ? 0xFF262626 : vhov ? 0x14FFFFFF : 0x08FFFFFF);
			int chevron = voted ? accent() : SOFT;
			Icons.drawFlipped(ui.graphics(), Icons.Icon.CHEVRON_DOWN, ui.mapX(x + 16), ui.mapY(ry + 12), 6 * s,
					Math.round((chevron >>> 24) * ui.alpha()) << 24 | (chevron & 0xFFFFFF));
			String score = String.valueOf(num(item, "score"));
			text(score, x + 16 - tw(score, Fonts.SEMIBOLD, T_SMALL) / 2, ry + 21, T_SMALL, voted ? 0xFFFFFFFF : SOFT, Fonts.SEMIBOLD);
			hit(x + 6, ry + 5, 20, 22, (b, px, py) -> voteCloud(item));
			// Title and details.
			text(str(item, "title"), x + 33, ry + 12, T_TITLE, 0xFFFFFFFF, Fonts.SEMIBOLD);
			String meta = "by " + str(item, "author") + "  ·  " + num(item, "modules") + " modules  ·  " + num(item, "downloads") + " loads  ·  "
					+ ago(num(item, "created") * 1000L) + (flag(item, "mine") ? "  ·  yours" : "");
			text(meta, x + 33, ry + 22, T_SMALL, MUTED, Fonts.MEDIUM);
			float bx = x + w - 8;
			if (flag(item, "mine")) {
				String delKey = "cdel:" + code;
				float dw = confirming(delKey) ? buttonW("Remove?", Icons.Icon.TRASH) : 20;
				bx -= dw;
				button(bx, ry + 8, dw, 16, confirming(delKey) ? "Remove?" : "", Icons.Icon.TRASH, false, () -> {
					if (confirm(delKey)) {
						ConfigLibrary.delete(code).thenAccept(r -> minecraft.execute(() -> {
							showToast(ConfigLibrary.ok(r) ? "Removed from the library." : ConfigLibrary.reason(r), null);
							refreshCloud(false);
						}));
					}
				});
				bx -= 4;
			}
			String loadKey = "cload:" + code;
			String loadLabel = confirming(loadKey) ? "Load now?" : "Load";
			float lw = buttonW(loadLabel, Icons.Icon.DOWNLOAD);
			bx -= lw;
			button(bx, ry + 8, lw, 16, loadLabel, Icons.Icon.DOWNLOAD, true, () -> {
				if (confirm(loadKey)) {
					loadCloud(item);
				}
			});
			ui.alpha(baseAlpha);
			y += rh + 6;
			index++;
		}
		if (cloudMore) {
			float mw2 = buttonW(cloudLoading ? "Loading…" : "Load more", null);
			button(x + (w - mw2) / 2, y + 2, mw2, 16, cloudLoading ? "Loading…" : "Load more", null, false, () -> {
				if (!cloudLoading) {
					refreshCloud(true);
				}
			});
			y += 24;
		}
		return y;
	}

	/** A short message at the bottom of the window, with Undo after a config was loaded. */
	private void drawToast(float x, float y, float w, float h, float dt) {
		if (toast == null) {
			return;
		}
		float age = (System.currentTimeMillis() - toastAt) / 1000f;
		float life = undoCode != null ? 7 : 3.5f;
		if (age > life) {
			toast = null;
			undoCode = null;
			return;
		}
		float e = easeOut(Math.min(age / 0.25f, (life - age) / 0.3f));
		ui.alpha(baseAlpha * e);
		float undoW = undoCode != null ? buttonW("Undo", Icons.Icon.UNDO) + 6 : 0;
		float tw = Math.min(w - 40, tw(toast, Fonts.MEDIUM, T_SMALL + 0.4f) + 22 + undoW);
		float tx = x + (w - tw) / 2;
		float ty = y + h - 30 + (1 - e) * 8;
		ui.shadow(tx, ty + 2, tw, 20, 10, 10, 0x80000000);
		ui.fill(tx, ty, tw, 20, 10, 0xF8131313);
		ui.ring(tx, ty, tw, 20, 10, 1, 0xFF2A2A2A);
		text(toast, tx + 11, ty + 10, T_SMALL + 0.4f, 0xFFFFFFFF, Fonts.MEDIUM);
		if (undoCode != null) {
			String code = undoCode;
			button(tx + tw - undoW - 2, ty + 2, undoW - 4, 16, "Undo", Icons.Icon.UNDO, false, () -> {
				ConfigManager config = TempleNzClient.config();
				if (config != null) {
					config.importCode(code);
					showToast("Your previous setup is back.", null);
				}
			});
		}
		ui.alpha(baseAlpha);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		float px = (float) e.x() / s;
		float py = (float) e.y() / s;
		int button = e.button();
		if (listening != null) {
			if (KeyNames.bindable(button)) {
				listening.set(KeyNames.mouse(button));
				listening = null;
				return true;
			}
			listening = null;
		}
		if (dropdown != null) {
			float[] r = dropRect();
			if (px >= r[0] && px <= r[0] + r[2] && py >= r[1] && py <= r[1] + r[3]) {
				int i = (int) Math.floor((py - r[1] - 2) / dropRowH());
				if (i >= 0 && i < dropdown.options().size()) {
					dropdown.trySet(dropdown.options().get(i));
					UiSounds.tap();
				}
				dropdown = null;
				return true;
			}
			boolean onBox = px >= dropBox[0] && px <= dropBox[0] + dropBox[2] && py >= dropBox[1] && py <= dropBox[1] + dropBox[3];
			if (!onBox) {
				dropdown = null;
				return true;
			}
		}
		boolean wasSearching = searching;
		searching = false;
		commitEdit();
		if (field != null && !"publish".equals(field)) {
			field = null;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (px >= h.x0() && px <= h.x1() && py >= h.y0() && py <= h.y1()) {
				h.click().run(button, px, py);
				return true;
			}
		}
		if (wasSearching && search.isEmpty()) {
			searching = false;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging != null) {
			dragTo((float) e.x() / s);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (dropdown != null) {
			dropdown = null;
		}
		scrollTarget -= (float) scrollY * 34;
		return true;
	}

	private void commitEdit() {
		if (editing != null) {
			editing.set(editBuffer.toString());
			editing = null;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int key = e.key();
		swallowChar = false;
		if (listening != null) {
			listening.set(key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE
					? KeybindSetting.NONE : key);
			listening = null;
			swallowChar = true;
			UiSounds.tap();
			return true;
		}
		if (field != null) {
			if (key == InputConstants.KEY_ESCAPE) {
				field = null;
				publishing = false;
			} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				submitField();
			} else if (key == InputConstants.KEY_BACKSPACE && !fieldText.isEmpty()) {
				fieldText.setLength(fieldText.length() - 1);
			}
			return true;
		}
		if (editing != null) {
			if (key == InputConstants.KEY_ESCAPE) {
				editing = null;
			} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				commitEdit();
			} else if (key == InputConstants.KEY_BACKSPACE && !editBuffer.isEmpty()) {
				editBuffer.setLength(editBuffer.length() - 1);
			}
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			if (dropdown != null) {
				dropdown = null;
			} else if (searching || !search.isEmpty()) {
				searching = false;
				search.setLength(0);
			} else {
				onClose();
			}
			return true;
		}
		if (searching) {
			if (key == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
				search.setLength(search.length() - 1);
				scrollTarget = 0;
			} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				searching = false;
			}
			return true;
		}
		if (TempleNzClient.isMenuKey(e)) {
			onClose();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (swallowChar) {
			swallowChar = false;
			return true;
		}
		if (field != null) {
			int max = "publish".equals(field) ? ConfigLibrary.TITLE_MAX : 32;
			if (fieldText.length() < max) {
				fieldText.append(e.codepointAsString());
			}
			return true;
		}
		if (editing != null) {
			if (editBuffer.length() < Math.max(1, editing.maxLength())) {
				editBuffer.append(e.codepointAsString());
			}
			return true;
		}
		if (listening != null) {
			return true;
		}
		String c = e.codepointAsString();
		if (!searching && !c.isBlank()) {
			searching = true;
		}
		if (searching && search.length() < 32) {
			search.append(c);
			scrollTarget = 0;
			dropdown = null;
			if (view != View.CONFIGS) {
				switched();
			}
		}
		return true;
	}
}
