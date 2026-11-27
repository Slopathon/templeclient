package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import nz.temple.TempleNz;
import nz.temple.anim.Spring;
import nz.temple.client.TempleNzClient;
import nz.temple.client.input.KeyNames;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Fx;
import nz.temple.client.render.GlassRenderer;
import nz.temple.client.render.GoldPanel;
import nz.temple.client.render.Icons;
import nz.temple.client.render.MenuScene;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.util.EntityCatalog;
import nz.temple.client.util.FilePicker;
import nz.temple.config.ConfigManager;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.EntitySelection;
import nz.temple.setting.FileSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NamespaceListSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.Setting;
import nz.temple.setting.TextSetting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleSupplier;

public final class TempleScreen extends Screen {
	private static final float WIN_W = 116;
	private static final float HEADER = 20;
	private static final float ROW = 15;
	private static final float SET_W = 240;
	private static final float SROW = 15;
	private static final float PICKER_H = 62;
	private static final float PICK_W = 190;
	private static final float RADIUS = 9;
	private static final float TEXT = 9.5f;
	private static final float SMALL = 8f;
	private interface Drag {
		void move(double mx, double my);
		default void release() {
		}
	}
	@FunctionalInterface
	private interface Handler {
		Drag press(int button, double mx, double my);
	}
	private record Hit(float x, float y, float w, float h, Handler handler) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx <= x + w && my <= y + h;
		}
	}
	private static final class Panel {
		final Category category;
		final String key;
		double x;
		double y;
		boolean expanded = true;
		final Spring open = new Spring(1, 320, 30);
		float scroll;
		Panel(Category category, String key) {
			this.category = category;
			this.key = key;
		}
		String title() {
			return category == null ? "Search" : category.displayName();
		}
	}
	private record SRow(Setting<?> setting, String special, float y, float h, boolean separator) {
	}

	private static final class Blob {
		final Module module;
		final boolean enabling;
		final long born = System.nanoTime();
		Blob(Module module, boolean enabling) {
			this.module = module;
			this.enabling = enabling;
		}
		double age() {
			return (System.nanoTime() - born) / 1e9;
		}
	}
	private final ModuleManager modules;
	private final ConfigManager config;
	private final Ui ui;
	private final DoubleSupplier menuScale;
	private final Screen returnScreen;
	private final boolean suggestionPrompt;
	private final List<Panel> panels = new ArrayList<>();
	private final Panel searchPanel = new Panel(null, "search");
	private final StringBuilder search = new StringBuilder();
	private final Spring appear = new Spring(0, 260, 24);
	private final Spring focus = new Spring(0, 300, 30);
	private final List<Hit> hits = new ArrayList<>();
	private final List<Blob> blobs = new ArrayList<>();
	private final Map<ColorSetting, float[]> hsv = new IdentityHashMap<>();
	private final java.util.function.Consumer<Module> toggleListener = this::onToggle;
	private static final Map<String, Float> lastScroll = new java.util.HashMap<>();
	private static Module lastSettings;
	private static float lastSetScroll;
	private boolean restored;
	private GuiGraphicsExtractor g;
	private boolean closing;
	private boolean closeScheduled;
	private long lastNanos = System.nanoTime();
	private double time;
	private float base = 1;
	private float k = 1;
	private float tx;
	private float ty;
	private double mouseX;
	private double mouseY;
	private float[] clip;
	private Drag activeDrag;
	private boolean draggingHud;
	private boolean editHud;
	private final Spring hudEdit = new Spring(0, 300, 30);
	private final Spring searchFocus = new Spring(0, 300, 30);
	private float[] searchRect;
	private boolean searchFocused;
	private StringBuilder suggestion;
	private double suggestionBorn;
	private String suggestionStatus;
	private boolean suggestionSending;
	private boolean suggestionSent;
	private boolean suggestionIntro;
	private double introBorn;
	private Module settingsModule;
	private double setX;
	private double setY;
	private float setScroll;
	private Setting<?> spotlight;
	private double spotlightAt;
	private boolean scrollToSpotlight;
	private float setContentH;
	private KeybindSetting listening;
	private boolean swallowChar;
	private TextSetting editing;
	private ModeSetting dropdown;
	private float[] dropdownAnchor;
	private ColorSetting colorOpen;
	private EntitySelection picker;
	private final StringBuilder pickerTerm = new StringBuilder();
	private float pickerScroll;
	private double pickX;
	private double pickY;
	private record Confirm(String title, String body, String yes, int yesTint, String no, Runnable action) {
	}
	private Confirm confirm;

	private enum Share { NONE, PUBLISH, LIBRARY }
	private Share share = Share.NONE;
	private double shareBorn;
	private String importError;
	private String importDone;
	private String undoCode;
	private final StringBuilder pubTitle = new StringBuilder();
	private final StringBuilder pubDesc = new StringBuilder();
	private boolean pubOnDesc;
	private boolean pubSending;
	private String pubCode;
	private String pubError;
	private final StringBuilder libQuery = new StringBuilder();
	private double libQueryChanged = -1;
	private String libSort = "top";
	private boolean libMine;
	private boolean libSortOpen;
	private final List<JsonObject> libItems = new ArrayList<>();
	private boolean libLoading;
	private boolean libMore;
	private int libPage;
	private int libRequest;
	private String libError;
	private String libStatus;
	private float libScroll;
	private JsonObject libOpen;
	private float libDetailScroll;
	private final StringBuilder libComment = new StringBuilder();
	private boolean libBusy;
	private float libScrollShown;
	private float libDetailScrollShown;
	private double libAnimTime;
	private double libClosing = -1;
	private boolean libWasDetail;
	private double libViewBorn;
	private float libSortEase;
	private final Map<String, Double> libRowBorn = new java.util.HashMap<>();
	private Object hoverKey;
	private Object shownKey;
	private String hoverTitle;
	private String hoverText;
	private double hoverSince;
	private float tipX = Float.MIN_VALUE;
	private float tipY;
	private double tipBorn;
	private double tipSeen;
	private float tipAnchorX = Float.NaN;
	private float tipAnchorY;
	public TempleScreen(ModuleManager modules, ConfigManager config, Ui ui, DoubleSupplier menuScale,
			Screen returnScreen, boolean suggestionPrompt) {
		super(Component.literal(TempleNz.NAME));
		this.modules = modules;
		this.config = config;
		this.ui = ui;
		this.menuScale = menuScale;
		this.returnScreen = returnScreen;
		this.suggestionPrompt = suggestionPrompt;
		this.suggestionIntro = suggestionPrompt;
		for (Category c : Category.values()) {
			panels.add(new Panel(c, c.name().toLowerCase(Locale.ROOT)));
		}
		appear.target = 1;
		modules.addToggleListener(toggleListener);
	}
	private void onToggle(Module m) {
		if (!m.isAlwaysOn()) {
			blobs.add(new Blob(m, m.isEnabled()));
			boolean now = m.isEnabled();
			nz.temple.setting.SettingHistory.record(m, m.name() + (now ? " on" : " off"),
					() -> m.setEnabled(!now), () -> m.setEnabled(now));
		}
	}

	private float baseScale() {
		return (float) (menuScale.getAsDouble() / 0.65) * UiScale.factor();
	}
	@Override
	protected void init() {
		base = baseScale();
		float dw = width / base;
		float dh = height / base;
		float x = 8;
		int column = 0;
		float[] bottoms = new float[panels.size()];
		java.util.Arrays.fill(bottoms, 34 - 12);
		for (Panel p : panels) {
			if (x + WIN_W > dw - 4 && column > 0) {
				x = 8;
				column = 0;
			}
			float y = Math.min(bottoms[column] + 12, Math.max(34, dh - HEADER - 90));
			p.x = config.getExtra("gui." + p.key + ".x", x);
			p.y = config.getExtra("gui." + p.key + ".y", y);
			p.expanded = config.getExtra("gui." + p.key + ".open", 1) == 1;
			p.open.snap(p.expanded ? 1 : 0);
			float full = Math.max(1, modulesOf(p).size()) * ROW + 4;
			bottoms[column] = y + HEADER + Math.min(full, Math.max(60, dh - y - HEADER - 10));
			x += WIN_W + 6;
			column++;
		}
		for (Panel p : panels) {
			if (Double.isNaN(config.getExtra("gui." + p.key + ".x", Double.NaN)) && p.category == Category.COSMETICS) {
				Panel client = panels.stream().filter(o -> o.category == Category.CLIENT).findFirst().orElse(null);
				if (client != null && !Double.isNaN(config.getExtra("gui." + client.key + ".x", Double.NaN))) {
					p.x = client.x;
					p.y = client.y + HEADER + modules.inCategory(Category.CLIENT).size() * ROW + 16;
				}
			}
		}
		searchPanel.x = (dw - WIN_W) / 2;
		searchPanel.y = 30;
	}
	@Override
	public void added() {
		ui.glassRenderer().reseed();
		nz.temple.setting.SettingHistory.start();
		nz.temple.client.compat.Input.startTyping(this);
	}
	@Override
	public void removed() {
		if (!popupOnly) {
			for (Panel p : panels) {
				lastScroll.put(p.key, p.scroll);
			}
			lastSettings = settingsModule;
			lastSetScroll = setScroll;
		}
		ui.setMask(List.of(), 1);
		modules.removeToggleListener(toggleListener);
		nz.temple.setting.SettingHistory.stop();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
		if (minecraft.level == null) {
			String theme = MenuModule.theme();
			if (theme == null || "Default".equals(theme)) {
				super.extractBackground(graphics, mx, my, delta);
			} else {
				MenuScene.pointer(mx, my);
				MenuScene.draw(graphics, theme, graphics.guiWidth(), graphics.guiHeight());
			}
		}
	}
	private void close() {
		if (!closing) {
			closing = true;
			appear.target = 0;
			listening = null;
			dropdown = null;
		}
	}
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
		long now = System.nanoTime();
		double elapsed = Math.min((now - lastNanos) / 1e9, 0.5);
		lastNanos = now;
		time += elapsed;
		if (!restored) {
			restored = true;
			if (!popupOnly) {
				for (Panel p : panels) {
					p.scroll = lastScroll.getOrDefault(p.key, 0f);
				}
				if (settingsModule == null && lastSettings != null && modules.all().contains(lastSettings)) {
					openSettings(lastSettings);
					setScroll = lastSetScroll;
				}
			}
		}
		focus.target = (settingsModule != null || picker != null) ? 1 : 0;
		List<Spring> springs = new ArrayList<>();
		springs.add(appear);
		springs.add(focus);
		for (Panel p : panels) {
			springs.add(p.open);
		}
		springs.add(searchPanel.open);
		hudEdit.target = editHud ? 1 : 0;
		springs.add(hudEdit);
		searchFocus.target = search.isEmpty() ? 0 : 1;
		springs.add(searchFocus);
		Spring.advance(elapsed, springs);
		blobs.removeIf(b -> b.age() > 0.85);
		if (closing && appear.x < 0.08 && !closeScheduled) {
			closeScheduled = true;
			minecraft.execute(() -> minecraft.gui.setScreen(returnScreen));
		}
		g = graphics;
		base = baseScale();
		float a = clamp01(appear.xf());
		k = base * (0.94f + 0.06f * a);
		tx = width / 2f - (width / 2f / base) * k;
		ty = height / 2f - (height / 2f / base) * k;
		mouseX = (mx - tx) / k;
		mouseY = (my - ty) / k;
		hits.clear();
		hoverKey = null;
		tipAnchorX = Float.NaN;
		windows.clear();
		ui.begin(graphics);
		ui.glassRenderer().placeLayer(graphics);
		ui.transform(tx, ty, k, k);
		ui.alpha(a);
		float edit = clamp01(hudEdit.xf());
		float focused = clamp01(focus.xf());
		float searching = clamp01(searchFocus.xf());
		float dim = (1 - 0.6f * focused) * (1 - edit);
		boolean interactable = focus.x < 0.5 && !editHud && suggestion == null && !themes && !suggestionIntro;
		occluders.clear();
		if (settingsModule != null && settingsRect != null && edit < 0.98f) {
			occluders.add(settingsRect);
		}
		if (picker != null && pickerRect != null && edit < 0.98f) {
			occluders.add(pickerRect);
		}
		boolean showSearch = !search.isEmpty() && searchRect != null;
		if (showSearch) {
			occluders.add(searchRect);
		}
		ui.alpha(a * dim * (1 - 0.6f * searching));
		if (dim > 0.05f && !popupOnly) {
			drawSuggestions(interactable);
			drawThemeButton(interactable);
			drawStreamproofButton(interactable, 30);
			if (minecraft.level != null && minecraft.player != null) {
				drawCornerButton(interactable, 53, Icons.Icon.MAP, "map", "Map",
						"The world you have explored, your path and your waypoints. Zoom as far as you like.",
						() -> minecraft.gui.setScreen(new MapScreen(this)));
			}
		}
		for (int i = 0; i < panels.size() && !popupOnly; i++) {
			drawPanel(panels.get(i), panels.get(i).category.ordinal(), interactable);
		}
		occluders.remove(searchRect);
		if (searching > 0.01f && edit < 0.98f) {
			ui.alpha(a * (1 - edit));
			ui.sheet(Math.round(0x8C * searching * (1 - focused)) << 24);
		}
		ui.alpha(a * dim);
		if (!search.isEmpty()) {
			drawPanel(searchPanel, 0, interactable);
		} else {
			searchRect = null;
		}
		if (edit < 0.98f && !popupOnly) {
			ui.alpha(a * (1 - edit) * (1 - 0.6f * focused));
			drawSearchBar();
		}
		if (focused > 0.01f && edit < 0.98f) {
			ui.alpha(a * (1 - edit));
			ui.sheet(Math.round(0x8C * focused) << 24);
		}
		ui.alpha(a * (1 - edit));
		occluders.remove(settingsRect);
		if (settingsModule != null && edit < 0.98f) {
			drawSettings();
		}
		occluders.clear();
		if (picker != null && edit < 0.98f) {
			drawPicker();
		}
		ui.alpha(a);
		graphics.nextStratum();
		if (dropdown != null) {
			drawDropdown();
		}
		if (editHud) {
			hits.clear();
		}
		if (edit > 0.02f) {
			drawHudToolbar(edit);
		}
		if (editHud) {
		} else if (confirm != null) {
			drawConfirm();
		} else if (share != Share.NONE) {
			drawShare();
		} else if (suggestion != null) {
			drawSuggestionPopup();
		} else if (suggestionIntro) {
			drawSuggestionIntro();
		} else if (themes) {
			drawThemes();
		} else if (popupOnly) {
			popupOnly = false;
			minecraft.execute(() -> minecraft.gui.setScreen(returnScreen));
		} else {
			drawTooltip();
		}
		ui.setMask(editHud ? List.of() : windows, editHud ? 1 : 1 - 0.7f * focused);
		ui.resetTransform();
		ui.alpha(1);
		super.extractRenderState(graphics, mx, my, delta);
	}
	private int fade(int argb) {
		return (Math.round(((argb >>> 24) & 0xFF) * ui.alpha()) << 24) | (argb & 0xFFFFFF);
	}
	private final List<float[]> occluders = new ArrayList<>();
	private final List<float[]> windows = new ArrayList<>();
	private float[] settingsRect;
	private float[] pickerRect;
	private void window(float x, float y, float w, float h) {
		windows.add(new float[] {ui.mapX(x), ui.mapY(y), ui.mapX(x + w), ui.mapY(y + h)});
	}

	private boolean occluded(float x0, float y0, float x1, float y1) {
		for (float[] o : occluders) {
			if (x0 < o[2] && o[0] < x1 && y0 < o[3] && o[1] < y1) {
				return true;
			}
		}
		return false;
	}
	private List<float[]> visiblePieces(float x0, float y0, float x1, float y1) {
		List<float[]> pieces = new ArrayList<>();
		pieces.add(new float[] {x0, y0, x1, y1});
		for (float[] o : occluders) {
			List<float[]> next = new ArrayList<>();
			for (float[] p : pieces) {
				if (!(p[0] < o[2] && o[0] < p[2] && p[1] < o[3] && o[1] < p[3])) {
					next.add(p);
					continue;
				}
				if (p[1] < o[1]) {
					next.add(new float[] {p[0], p[1], p[2], o[1]});
				}
				if (o[3] < p[3]) {
					next.add(new float[] {p[0], o[3], p[2], p[3]});
				}
				float my0 = Math.max(p[1], o[1]);
				float my1 = Math.min(p[3], o[3]);
				if (p[0] < o[0]) {
					next.add(new float[] {p[0], my0, o[0], my1});
				}
				if (o[2] < p[2]) {
					next.add(new float[] {o[2], my0, p[2], my1});
				}
			}
			pieces = next;
		}
		return pieces;
	}
	private void drawVisible(float x0, float y0, float x1, float y1, Runnable draw) {
		if (occluders.isEmpty() || !occluded(x0, y0, x1, y1)) {
			draw.run();
			return;
		}
		for (float[] p : visiblePieces(x0, y0, x1, y1)) {
			if (p[2] - p[0] < 0.05f || p[3] - p[1] < 0.05f) {
				continue;
			}
			ui.clip(p[0], p[1], p[2], p[3], true);
			draw.run();
			ui.unclip();
		}
	}
	private void rect(float x, float y, float w, float h, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int c = fade(argb);
		if (occluders.isEmpty() || !occluded(x, y, x + w, y + h)) {
			Draw2D.rect(g, ui.mapX(x), ui.mapY(y), ui.mapX(x + w), ui.mapY(y + h), c);
			return;
		}
		for (float[] p : visiblePieces(x, y, x + w, y + h)) {
			Draw2D.rect(g, ui.mapX(p[0]), ui.mapY(p[1]), ui.mapX(p[2]), ui.mapY(p[3]), c);
		}
	}
	private void vgrad(float x, float y, float w, float h, int top, int bottom) {
		drawVisible(x, y, x + w, y + h, () -> g.fillGradient(Math.round(ui.mapX(x)), Math.round(ui.mapY(y)),
				Math.round(ui.mapX(x + w)), Math.round(ui.mapY(y + h)), fade(top), fade(bottom)));
	}
	private void softPanel(float x, float y, float w, float h, int argb) {
		softPanel(x, y, w, h, argb, true);
	}

	private void softPanel(float x, float y, float w, float h, int argb, boolean gold) {
		float r = 2.5f;
		rect(x + r, y, w - r * 2, h, argb);
		rect(x, y + r, w, h - r * 2, argb);
		rect(x + r / 2, y + r / 2, w - r, h - r, argb);
		if (!gold) {
			return;
		}
		int warm = HudModule.GOLD & 0xFFFFFF;
		vgrad(x + r / 2, y + r / 2, w - r, h * 0.55f, 0x2E000000 | warm, 0x08000000 | warm);
		rect(x + r, y, w - r * 2, Math.max(0.6f, 1.2f / k), 0xCC000000 | warm);
		rect(x + r, y + h - Math.max(0.6f, 1.2f / k), w - r * 2, Math.max(0.6f, 1.2f / k), 0x59000000 | warm);
		rect(x, y + r, Math.max(0.6f, 1.2f / k), h - r * 2, 0x73000000 | warm);
		rect(x + w - Math.max(0.6f, 1.2f / k), y + r, Math.max(0.6f, 1.2f / k), h - r * 2, 0x73000000 | warm);
	}
	private void goldPanel(float x, float y, float w, float h, float alpha) {
		GuiGraphicsExtractor g = ui.graphics();
		if (g == null) {
			softPanel(x, y, w, h, 0xF61A140A);
			return;
		}
		GoldPanel.draw(g, Math.round(ui.mapX(x)), Math.round(ui.mapY(y)),
				Math.round(ui.mapX(x + w)), Math.round(ui.mapY(y + h)), alpha);
	}

	private void overlayFill(float x, float y, float w, float h, float radius, int argb, float ring) {
		GlassRenderer glass = TempleNzClient.glass();
		if (glass == null || !glass.available() || !glass.hasOverlayRoom(0, 1)) {
			if (ring == 0) {
				softPanel(x, y, w, h, argb, false);
			}
			return;
		}
		glass.overlayRect(ui.mapX(x), ui.mapY(y), w * k, h * k, radius * k, fade(argb), ring, 0);
	}
	private final Map<String, float[]> hoverEases = new java.util.HashMap<>();
	private float hoverEase(String key, boolean over) {
		float[] state = hoverEases.computeIfAbsent(key, k -> new float[] {0, (float) time});
		float dt = (float) Math.min(0.1, time - state[1]);
		state[1] = (float) time;
		state[0] += ((over ? 1 : 0) - state[0]) * (1 - (float) Math.exp(-14 * dt));
		return state[0];
	}

	private void outline(float x, float y, float w, float h, int argb) {
		float t = 1f / k;
		rect(x, y, w, t, argb);
		rect(x, y + h - t, w, t, argb);
		rect(x, y, t, h, argb);
		rect(x + w - t, y, t, h, argb);
	}
	private void text(String s, float x, float cy, float size, int argb, FontDescription font) {
		if (occluders.isEmpty()) {
			ui.textMiddle(s, x, cy, size, argb, font);
			return;
		}
		drawVisible(x - 1, cy - size * 0.6f, x + tw(s, font, size) + 1, cy + size * 0.6f,
				() -> ui.textMiddle(s, x, cy, size, argb, font));
	}
	private float tw(String s, FontDescription font, float size) {
		return ui.textWidth(s, font, size);
	}
	private String ellipsize(String s, FontDescription font, float size, float max) {
		if (tw(s, font, size) <= max) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && tw(out + "…", font, size) > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out + "…";
	}

	private void accentLine(float x, float y, float w, double offset) {
		int slices = Math.max(6, Math.round(w / 5));
		float t = 1.2f / k;
		for (int i = 0; i < slices; i++) {
			float edge = Math.min(1, Math.min(i + 1, slices - i) / (slices * 0.18f));
			int c = Theme.color(offset + (double) i / slices * 0.45);
			rect(x + w * i / slices, y, w / slices + 0.6f / k, t, (Math.round(255 * edge) << 24) | (c & 0xFFFFFF));
		}
	}
	private void checkbox(float x, float cy, boolean on, double offset) {
		float s = 9f;
		float y = cy - s / 2;
		if (on) {
			int c = Theme.color(offset);
			ui.fill(x, y, s, s, 3f, 0xFF000000 | c);
			ui.fill(x + 2.6f, y + 2.6f, s - 5.2f, s - 5.2f, 1.2f, 0xF2FFFFFF);
		} else {
			ui.fill(x, y, s, s, 3f, 0x1AFFFFFF);
			ui.ring(x, y, s, s, 3f, Math.max(0.6f, 1f / k), 0x55FFFFFF);
		}
	}
	private void toggleSwitch(Object key, float x, float cy, boolean on) {
		float w = 18;
		float h = 10;
		float y = cy - h / 2;
		float t = hoverEase("switch" + System.identityHashCode(key), on);
		int track = Ui.lerpColor(0x33FFFFFF, 0xFF000000 | (HudModule.GOLD & 0xFFFFFF), t);
		ui.fill(x, y, w, h, h / 2, track);
		if (t < 0.98f) {
			ui.ring(x, y, w, h, h / 2, Math.max(0.6f, 1f / k), (Math.round(0x55 * (1 - t)) << 24) | 0xFFFFFF);
		}
		float knob = h - 3;
		float kx = x + 1.5f + (w - 3 - knob) * t;
		ui.shadow(kx, y + 2, knob, knob, knob / 2, 1.5f, 0x40000000);
		ui.fill(kx, y + 1.5f, knob, knob, knob / 2, 0xFFFFFFFF);
	}
	private void icon(Icons.Icon icon, float cx, float cy, float size, int argb) {
		int c = fade(argb);
		drawVisible(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2,
				() -> Icons.draw(g, icon, ui.mapX(cx), ui.mapY(cy), size * k, c));
	}
	private void triangle(float cx, float cy) {
		icon(Icons.Icon.CHEVRON_DOWN, cx, cy, 8, 0xCCFFFFFF);
	}
	private void closeGlyph(float cx, float cy, boolean hovered) {
		float glow = hoverEase("close" + Math.round(cx) + ":" + Math.round(cy), hovered);
		float r = 6.5f;
		ui.fill(cx - r, cy - r, r * 2, r * 2, r, (Math.round(0x14 + 0x38 * glow) << 24) | 0xFFFFFF);
		if (glow > 0.02f) {
			ui.ring(cx - r, cy - r, r * 2, r * 2, r, Math.max(0.6f, 1f / k),
					(Math.round(0x70 * glow) << 24) | (HudModule.GOLD_LIGHT & 0xFFFFFF));
		}
		icon(Icons.Icon.CLOSE, cx, cy, 8.5f, Ui.lerpColor(0xA6FFFFFF, 0xFFFFFFFF, glow));
	}
	private void clip(float x0, float y0, float x1, float y1) {
		ui.clip(x0, y0, x1, y1);
		clip = new float[] {x0, y0, x1, y1};
	}
	private void unclip() {
		ui.unclip();
		clip = null;
	}
	private void hit(float x, float y, float w, float h, Handler handler) {
		if (clip != null) {
			float x0 = Math.max(x, clip[0]);
			float y0 = Math.max(y, clip[1]);
			float x1 = Math.min(x + w, clip[2]);
			float y1 = Math.min(y + h, clip[3]);
			if (x1 <= x0 || y1 <= y0) {
				return;
			}
			hits.add(new Hit(x0, y0, x1 - x0, y1 - y0, handler));
		} else {
			hits.add(new Hit(x, y, w, h, handler));
		}
	}
	private boolean hovered(float x, float y, float w, float h) {
		if (clip != null && (mouseX < clip[0] || mouseY < clip[1] || mouseX > clip[2] || mouseY > clip[3])) {
			return false;
		}
		return mouseX >= x && mouseY >= y && mouseX <= x + w && mouseY <= y + h;
	}
	private void hover(Object key, String title, String text) {
		hoverKey = key;
		hoverTitle = title;
		hoverText = text;
	}
	private float designW() {
		return width / base;
	}
	private float designH() {
		return height / base;
	}

	private void drawSearchBar() {
		float w = 180;
		float h = 18;
		float x = (designW() - w) / 2;
		float y = 7;
		float left = x - (h + 5);
		window(left, y, x - left + w + 2 * (h + 4) + 5, h);
		editHudButton(x - h - 5, y, h);
		ui.glass(x, y, w, h, 9);
		String t = search.toString();
		boolean empty = t.isEmpty();
		boolean focusedBar = (searchFocused || !empty) && picker == null && editing == null && share == Share.NONE
				&& suggestion == null;
		if (focusedBar) {
			ui.ring(x, y, w, h, 9, Math.max(0.6f, 1f / k), (0x70 << 24) | (HudModule.GOLD & 0xFFFFFF));
		}
		float cy = y + h / 2;
		float lx = x + 9;
		icon(Icons.Icon.SEARCH, x + 10, cy - 0.8f, 9, empty ? 0x8CFFFFFF : 0xE6FFFFFF);
		lx += 9;
		text(empty ? "Type to search " + modules.all().stream().filter(m -> m.listedInCategory() && HvhTab.listed(m)).count() + " modules" : t, lx, cy, TEXT, empty ? 0x8CFFFFFF : 0xFFFFFFFF, Fonts.MEDIUM);
		if (focusedBar && (time % 1.0) < 0.55) {
			rect(lx + (empty ? -1 : tw(t, Fonts.MEDIUM, TEXT) + 1), cy - 5, 1f / k * 1.2f, 10, 0xE6FFFFFF);
		}
		if (!empty) {
			String count = (modules.search(t).size() + modules.searchSettings(t).size()) + " found";
			text(count, x + w - 9 - tw(count, Fonts.MEDIUM, SMALL), cy, SMALL, 0x99FFFFFF, Fonts.MEDIUM);
		}
		hit(x, y, w, h, (button, mx, my) -> {
			searchFocused = true;
			if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
				search.setLength(0);
			}
			return null;
		});
		shareButton(x + w + 5, y, h, false);
		shareButton(x + w + 5 + h + 4, y, h, true);
	}

	private void editHudButton(float x, float y, float size) {
		boolean inWorld = minecraft.level != null && minecraft.player != null;
		boolean over = hovered(x, y, size, size) && share == Share.NONE && confirm == null && suggestion == null;
		ui.glass(x, y, size, size, size / 2);
		float glow = hoverEase("edit-hud-button", over && inWorld);
		if (glow > 0.02f) {
			ui.fill(x, y, size, size, size / 2, (Math.round(0x30 * glow) << 24) | (HudModule.GOLD & 0xFFFFFF));
		}
		if (over) {
			hover("edit-hud", "Edit HUD", inWorld ? "Drag the HUD widgets to move them and scroll over one to make it bigger or smaller."
					: "Join a world first: the HUD is only drawn in game.");
		}
		icon(Icons.Icon.LAYOUT, x + size / 2, y + size / 2, 10, !inWorld ? 0x66FFFFFF : over ? 0xFFFFE3A1 : 0xE6FFFFFF);
		hit(x, y, size, size, (button, mx, my) -> {
			startHudEdit();
			return null;
		});
	}
	private void drawSuggestions(boolean interactable) {
		float size = 18;
		float x = designW() - size - 7;
		float y = 7;
		boolean active = interactable && hovered(x, y, size, size);
		ui.glass(x, y, size, size, size / 2);
		ui.fill(x + 2, y + 2, size - 4, size - 4, (size - 4) / 2,
				active ? 0x46E9B949 : 0x2EE9B949);
		icon(Icons.Icon.LIGHTBULB, x + size / 2, y + size / 2, 10.5f, active ? 0xFFFFD86A : 0xFFE9B949);
		if (active || interactable && suggestionPrompt && time < 10) {
			hover("suggestions", "Suggestions", "Have an idea for Temple? Click to send it to the team.");
			if (!active) {
				tipAnchorX = x + size;
				tipAnchorY = y + size + 5;
			}
		}
		if (interactable) {
			hit(x, y, size, size, (button, mx, my) -> {
				openSuggestion();
				return null;
			});
		}
	}

	private boolean popupOnly;
	public void popupOnly(String kind) {
		popupOnly = true;
		switch (kind) {
			case "themes" -> {
				themes = true;
				themesBorn = time;
			}
			case "export", "publish" -> openPublish();
			case "import", "library" -> openLibrary();
			case "intro" -> {
				suggestionIntro = true;
				introBorn = time;
			}
			default -> openSuggestion();
		}
	}
	private boolean themes;
	private double themesBorn;
	private void drawCornerButton(boolean interactable, float x, Icons.Icon glyph, String id, String title, String help, Runnable action) {
		float size = 18;
		float y = 7;
		boolean over = interactable && hovered(x, y, size, size);
		float glow = hoverEase("corner-" + id, over);
		ui.glass(x, y, size, size, size / 2);
		if (glow > 0.02f) {
			ui.fill(x, y, size, size, size / 2, (Math.round(0x30 * glow) << 24) | (HudModule.GOLD & 0xFFFFFF));
		}
		icon(glyph, x + size / 2, y + size / 2, 10, over ? 0xFFFFE3A1 : 0xE6FFFFFF);
		if (over) {
			hover(id, title, help);
		}
		if (interactable) {
			hit(x, y, size, size, (button, mx, my) -> {
				minecraft.execute(action);
				return null;
			});
		}
	}

	private void drawStreamproofButton(boolean interactable, float x) {
		nz.temple.client.modules.client.Streamproof streamer = modules.get(nz.temple.client.modules.client.Streamproof.class);
		if (streamer == null) {
			return;
		}
		float size = 18;
		float y = 7;
		boolean on = streamer.isEnabled();
		boolean over = interactable && hovered(x, y, size, size);
		float glow = hoverEase("streamproof-button", over);
		int accent = nz.temple.client.render.Theme.primary() & 0xFFFFFF;
		ui.glass(x, y, size, size, size / 2);
		if (on) {
			ui.fill(x + 2, y + 2, size - 4, size - 4, (size - 4) / 2, (Math.round(0x2E + 0x22 * glow) << 24) | accent);
		} else if (glow > 0.02f) {
			ui.fill(x, y, size, size, size / 2, (Math.round(0x30 * glow) << 24) | (HudModule.GOLD & 0xFFFFFF));
		}
		icon(Icons.Icon.EYE, x + size / 2, y + size / 2, 10, on ? (over ? 0xFFFFFFFF : 0xFF000000 | accent)
				: over ? 0xFFFFE3A1 : 0x99FFFFFF);
		if (over) {
			hover("streamproof", "Streamproof: " + (on ? "on" : "off"), nz.temple.client.modules.client.Streamproof.HELP);
		}
		if (interactable) {
			hit(x, y, size, size, (button, mx, my) -> {
				streamer.toggle();
				return null;
			});
		}
	}
	private void drawThemeButton(boolean interactable) {
		float size = 18;
		float x = 7;
		float y = 7;
		boolean over = interactable && hovered(x, y, size, size);
		float glow = hoverEase("theme-button", over);
		int accent = nz.temple.client.render.Theme.primary() & 0xFFFFFF;
		ui.glass(x, y, size, size, size / 2);
		ui.fill(x + 2, y + 2, size - 4, size - 4, (size - 4) / 2, (Math.round(0x2E + 0x22 * glow) << 24) | accent);
		icon(Icons.Icon.PALETTE, x + size / 2, y + size / 2, 10.5f, over ? 0xFFFFFFFF : 0xFF000000 | accent);
		if (over) {
			hover("themes", "Theme", "Pick your config's colours: the HUD, every effect and every colour setting you have not "
					+ "set yourself follow it.");
		}
		if (interactable) {
			hit(x, y, size, size, (button, mx, my) -> {
				themes = true;
				themesBorn = time;
				dropdown = null;
				editing = null;
				searchFocused = false;
				return null;
			});
		}
	}
	private void drawThemes() {
		HudModule hud = modules.get(HudModule.class);
		float dw = designW();
		float dh = designH();
		float age = (float) (time - themesBorn);
		float t = Math.min(1, age / 0.25f);
		float ease = 1 - (1 - t) * (1 - t) * (1 - t);
		rect(0, 0, dw, dh, Math.round(0x80 * ease) << 24);
		hit(-10000, -10000, 20000, 20000, (button, mx, my) -> {
			themes = false;
			return null;
		});
		int rows = (HudModule.THEMES.length + 1) / 2;
		float cellW = 128;
		float cellH = 26;
		float w = 14 + cellW * 2 + 6 + 14;
		float h = 34 + rows * (cellH + 5) + 36;
		float swell = 0.94f + 0.06f * ease;
		float x = (dw - w) / 2;
		float y = (dh - h) / 2 + (1 - ease) * 8;
		goldPanel(x + w * (1 - swell) / 2, y + h * (1 - swell) / 2, w * swell, h * swell, 0.97f * (0.6f + 0.4f * ease));
		hit(x, y, w, h, (button, mx, my) -> null);
		icon(Icons.Icon.PALETTE, x + 18, y + 16, 10, 0xFF000000 | (nz.temple.client.render.Theme.primary() & 0xFFFFFF));
		text("Theme", x + 28, y + 16, 11.5f, Ui.white(ease), Fonts.SEMIBOLD);
		String hint = "Colours for the HUD, effects and settings";
		text(hint, x + w - 14 - tw(hint, Fonts.MEDIUM, 7.5f), y + 16, 7.5f, (Math.round(0x99 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		int current = hud == null ? -1 : hud.currentTheme();
		for (int i = 0; i < HudModule.THEMES.length; i++) {
			String[] theme = HudModule.THEMES[i];
			int c1 = 0xFF000000 | Integer.parseInt(theme[1], 16);
			int c2 = 0xFF000000 | Integer.parseInt(theme[2], 16);
			float cx = x + 14 + (i % 2) * (cellW + 6);
			float cy = y + 34 + (i / 2) * (cellH + 5);
			boolean over = hovered(cx, cy, cellW, cellH);
			float glow = hoverEase("theme-" + i, over);
			boolean selected = i == current;
			overlayFill(cx, cy, cellW, cellH, 8, (Math.round(0x22 + 0x26 * glow + (selected ? 0x20 : 0)) << 24) | 0xFFFFFF, 0);
			if (selected) {
				overlayFill(cx, cy, cellW, cellH, 8, 0xE0000000 | (c1 & 0xFFFFFF), 1.4f);
			}
			overlayFill(cx + 16, cy + 6, 14, 14, 7, c2, 0);
			overlayFill(cx + 7, cy + 6, 14, 14, 7, c1, 0);
			text(theme[0], cx + 38, cy + cellH / 2, 9, 0xF0FFFFFF, Fonts.SEMIBOLD);
			if (selected) {
				icon(Icons.Icon.CHECK, cx + cellW - 12, cy + cellH / 2, 9, 0xFF000000 | (c1 & 0xFFFFFF));
			}
			int index = i;
			hit(cx, cy, cellW, cellH, (button, mx, my) -> {
				if (hud != null) {
					hud.applyTheme(index);
				}
				return null;
			});
		}
		String note = "Fine-tune the colours under Client, HUD.";
		text(note, x + 14, y + h - 14, 8, (Math.round(0x99 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		confirmButton(x + w - 14 - 60, y + h - 22, 60, "Done", 0x40FFFFFF, () -> themes = false);
	}
	private void openSuggestion() {
		suggestion = new StringBuilder();
		suggestionBorn = time;
		suggestionStatus = null;
		suggestionSending = false;
		suggestionSent = false;
		dropdown = null;
		editing = null;
		searchFocused = false;
	}
	private void submitSuggestion() {
		if (suggestionSending || suggestionSent) {
			return;
		}
		String text = suggestion.toString().trim();
		if (text.length() < Suggestions.MIN_LENGTH) {
			suggestionStatus = "Write a little more: at least " + Suggestions.MIN_LENGTH + " characters.";
			return;
		}
		long wait = Suggestions.cooldownSeconds();
		if (wait > 0) {
			suggestionStatus = "You just sent one. Wait " + wait + " s before sending another.";
			return;
		}
		suggestionSending = true;
		suggestionStatus = null;
		Suggestions.send(text).thenAccept(error -> minecraft.execute(() -> {
			suggestionSending = false;
			if (error == null) {
				suggestionSent = true;
				suggestionBorn = time;
			} else {
				suggestionStatus = error;
			}
		}));
	}
	private List<String> suggestionLines(String text, float maxW) {
		List<String> out = new ArrayList<>();
		for (String para : text.split("\n", -1)) {
			if (para.isEmpty()) {
				out.add("");
				continue;
			}
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ", -1)) {
				String candidate = line.isEmpty() ? word : line + " " + word;
				if (tw(candidate, Fonts.MEDIUM, 8.5f) <= maxW) {
					line = new StringBuilder(candidate);
					continue;
				}
				if (!line.isEmpty()) {
					out.add(line.toString());
				}
				line = new StringBuilder();
				for (int i = 0; i < word.length(); i++) {
					if (tw(line.toString() + word.charAt(i), Fonts.MEDIUM, 8.5f) > maxW) {
						out.add(line.toString());
						line = new StringBuilder();
					}
					line.append(word.charAt(i));
				}
			}
			out.add(line.toString());
		}
		return out;
	}
	private void drawSuggestionIntro() {
		float dw = designW();
		float dh = designH();
		float age = (float) (time - introBorn);
		float t = Math.max(0, Math.min(1, (age - 0.35f) / 0.32f));
		float ease = 1 - (1 - t) * (1 - t) * (1 - t);
		rect(0, 0, dw, dh, Math.round(0x80 * ease) << 24);
		hit(-10000, -10000, 20000, 20000, (button, mx, my) -> null);
		if (t <= 0) {
			return;
		}
		float w = 276;
		List<String> body = wrap("Temple is built from what players ask for. Got an idea for a module, a setting or a fix? "
				+ "Send it to the team from the bulb in the top right corner, any time.", w - 28, 8.5f);
		float h = 44 + body.size() * 10.5f + 40;
		float swell = 0.94f + 0.06f * ease;
		float x = (dw - w) / 2;
		float y = (dh - h) / 2 + (1 - ease) * 8;
		goldPanel(x + w * (1 - swell) / 2, y + h * (1 - swell) / 2, w * swell, h * swell, 0.97f * (0.6f + 0.4f * ease));
		float bob = (float) Math.sin(time * 3) * 0.8f;
		icon(Icons.Icon.LIGHTBULB, x + 18, y + 17 + bob, 10, 0xFFFFD86A);
		text("Help shape Temple", x + 28, y + 17, 11.5f, Ui.white(ease), Fonts.SEMIBOLD);
		float ty = y + 38;
		for (String line : body) {
			text(line, x + 14, ty, 8.5f, (Math.round(0xC8 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
			ty += 10.5f;
		}
		float size = 18;
		float bx = dw - size - 7 + size / 2;
		float by = 7 + size / 2;
		float pulse = (float) ((time * 0.9) % 1.0);
		float ring = size / 2 + 3 + pulse * 9;
		overlayFill(bx - ring, by - ring, ring * 2, ring * 2, ring,
				(Math.round(0x90 * (1 - pulse) * ease) << 24) | (HudModule.GOLD & 0xFFFFFF), 1);
		float bottom = y + h - 22;
		confirmButton(x + w - 14 - 96, bottom, 96, "Write one now", 0x40E9B949, () -> {
			suggestionIntro = false;
			openSuggestion();
		});
		confirmButton(x + 14, bottom, 72, "Maybe later", 0x1AFFFFFF, () -> suggestionIntro = false);
	}
	private void drawSuggestionPopup() {
		float dw = designW();
		float dh = designH();
		float age = (float) (time - suggestionBorn);
		float t = Math.min(1, age / 0.28f);
		float ease = 1 - (1 - t) * (1 - t) * (1 - t);
		rect(0, 0, dw, dh, Math.round(0x99 * ease) << 24);
		hit(-10000, -10000, 20000, 20000, (button, mx, my) -> null);
		float w = 300;
		String warning = "Spam and senseless suggestions get a warning first. Doing it again gets your license revoked.";
		List<String> warn = wrap(warning, w - 44, 8);
		float boxH = 80;
		float h = suggestionSent ? 92 : 30 + 16 + boxH + 10 + warn.size() * 10 + 10 + (suggestionStatus != null ? 12 : 0) + 30;
		float swell = 0.94f + 0.06f * ease;
		float x = (dw - w) / 2;
		float y = (dh - h) / 2 + (1 - ease) * 8;
		goldPanel(x + w * (1 - swell) / 2, y + h * (1 - swell) / 2, w * swell, h * swell, 0.97f * (0.6f + 0.4f * ease));
		icon(Icons.Icon.LIGHTBULB, x + 18, y + 16, 10, 0xFFFFD86A);
		text(suggestionSent ? "Thanks for the idea" : "Send a suggestion", x + 28, y + 16, 11.5f, Ui.white(ease), Fonts.SEMIBOLD);
		if (suggestionSent) {
			float ty = y + 40;
			for (String line : wrap("It reached the team. Good ideas end up in a future update.", w - 28, 8.5f)) {
				text(line, x + 14, ty, 8.5f, (Math.round(0xC8 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
				ty += 10.5f;
			}
			confirmButton(x + w - 14 - 60, y + h - 22, 60, "Done", 0x40E9B949, () -> suggestion = null);
			return;
		}
		String hint = "What would make Temple better? One idea per suggestion works best.";
		text(ellipsize(hint, Fonts.MEDIUM, 8, w - 28), x + 14, y + 36, 8, (Math.round(0xA8 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		float bx = x + 12;
		float by = y + 46;
		float bw = w - 24;
		overlayFill(bx, by, bw, boxH, 8, 0x73000000, 0);
		float pulse = (float) (0.5 + 0.5 * Math.sin(time * 3));
		overlayFill(bx, by, bw, boxH, 8, (Math.round(0x50 + 0x40 * pulse) << 24) | (HudModule.GOLD & 0xFFFFFF), 1);
		String typed = suggestion.toString();
		List<String> lines = suggestionLines(typed, bw - 16);
		int maxLines = 6;
		int first = Math.max(0, lines.size() - maxLines);
		float ly = by + 9;
		if (typed.isEmpty()) {
			text("Type your suggestion here", bx + 8, ly, 8.5f, (Math.round(0x70 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		}
		for (int i = first; i < lines.size(); i++) {
			text(lines.get(i), bx + 8, ly, 8.5f, (Math.round(0xF0 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
			ly += 11;
		}
		if (!suggestionSending && (time % 1.0) < 0.55) {
			String last = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
			float cy = by + 9 + (lines.isEmpty() ? 0 : Math.min(lines.size(), maxLines) - 1) * 11;
			rect(bx + 8 + tw(last, Fonts.MEDIUM, 8.5f) + (typed.isEmpty() ? -1 : 1), cy - 4.5f, 1f / k * 1.2f, 9, 0xE6FFFFFF);
		}
		String count = typed.length() + " / " + Suggestions.MAX_LENGTH;
		text(count, bx + bw - 6 - tw(count, Fonts.MEDIUM, 7), by + boxH - 7, 7, (Math.round(0x80 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		float wy = by + boxH + 10;
		icon(Icons.Icon.INFO, x + 20, wy + 4, 9, 0xFFFF9F0A);
		for (String line : warn) {
			text(line, x + 30, wy + 4, 8, (Math.round(0xE6 * ease) << 24) | 0xFFC56B, Fonts.MEDIUM);
			wy += 10;
		}
		if (suggestionStatus != null) {
			text(ellipsize(suggestionStatus, Fonts.MEDIUM, 8.5f, w - 28), x + 14, wy + 8, 8.5f,
					(Math.round(0xF0 * ease) << 24) | 0xFF6B6B, Fonts.MEDIUM);
		}
		float bottom = y + h - 22;
		confirmButton(x + w - 14 - 64, bottom, 64, suggestionSending ? "Sending…" : "Submit", 0x40E9B949, this::submitSuggestion);
		confirmButton(x + w - 14 - 64 - 66, bottom, 60, "Paste", 0x26FFFFFF, () -> {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null) {
				appendSuggestion(clip);
			}
		});
		confirmButton(x + 14, bottom, 56, "Cancel", 0x1AFFFFFF, () -> suggestion = null);
	}
	private void appendSuggestion(String s) {
		for (int i = 0; i < s.length() && suggestion.length() < Suggestions.MAX_LENGTH; i++) {
			char c = s.charAt(i);
			if (c == '\r') {
				continue;
			}
			suggestion.append(c == '\t' ? ' ' : c);
		}
		suggestionStatus = null;
	}
	private void shareButton(float x, float y, float size, boolean load) {
		boolean over = hovered(x, y, size, size) && share == Share.NONE && confirm == null;
		ui.glass(x, y, size, size, size / 2);
		if (over) {
			ui.fill(x, y, size, size, size / 2, (0x30 << 24) | (HudModule.GOLD & 0xFFFFFF));
			hover(load ? "load-config" : "save-config", load ? "Config library" : "Publish your config",
					load ? "Browse configs other players published, search by code, vote and comment."
							: "Publish your modules and settings to the config library with a title and description.");
		}
		float cx = x + size / 2;
		float cy = y + size / 2;
		float lift = over ? (float) Math.sin(time * 6) * 0.6f : 0;
		icon(load ? Icons.Icon.DOWNLOAD : Icons.Icon.SHARE, cx, cy + lift, 10, over ? 0xFFFFE3A1 : 0xE6FFFFFF);
		hit(x, y, size, size, (button, mx, my) -> {
			if (load) {
				openLibrary();
			} else {
				openPublish();
			}
			return null;
		});
	}
	private void openPublish() {
		share = Share.PUBLISH;
		shareBorn = time;
		pubTitle.setLength(0);
		pubDesc.setLength(0);
		pubOnDesc = false;
		pubSending = false;
		pubCode = null;
		pubError = null;
		dropdown = null;
		libClosing = -1;
	}
	private void openLibrary() {
		share = Share.LIBRARY;
		shareBorn = time;
		libClosing = -1;
		libAnimTime = time;
		libScrollShown = 0;
		libDetailScrollShown = 0;
		libWasDetail = false;
		libViewBorn = time - 1;
		libSortEase = 0;
		libRowBorn.clear();
		libSortOpen = false;
		libQuery.setLength(0);
		libOpen = null;
		libStatus = null;
		importDone = null;
		importError = null;
		dropdown = null;
		libScroll = 0;
		libQueryChanged = -1;
		refreshLibrary(false);
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

	private void refreshLibrary(boolean next) {
		int id = ++libRequest;
		int page = next ? libPage + 1 : 0;
		libLoading = true;
		libError = null;
		ConfigLibrary.browse(libQuery.toString().trim(), libSort, page, libMine).thenAccept(r -> minecraft.execute(() -> {
			if (id != libRequest) {
				return;
			}
			libLoading = false;
			if (!ConfigLibrary.ok(r)) {
				libError = ConfigLibrary.reason(r);
				return;
			}
			if (!next) {
				libItems.clear();
				libRowBorn.clear();
				libScroll = 0;
				libScrollShown = 0;
			}
			int i = 0;
			for (JsonElement el : r.getAsJsonArray("configs")) {
				JsonObject item = el.getAsJsonObject();
				libItems.add(item);
				libRowBorn.put(str(item, "code"), time + Math.min(i++, 10) * 0.035);
			}
			libPage = page;
			libMore = flag(r, "more");
		}));
	}
	private void openConfig(JsonObject summary) {
		libOpen = summary;
		libDetailScroll = 0;
		libDetailScrollShown = 0;
		libComment.setLength(0);
		libStatus = null;
		String code = str(summary, "code");
		ConfigLibrary.get(code, false).thenAccept(r -> minecraft.execute(() -> {
			if (libOpen == null || !code.equals(str(libOpen, "code"))) {
				return;
			}
			if (ConfigLibrary.ok(r)) {
				libOpen = r.getAsJsonObject("config");
				replaceItem(libOpen);
			} else {
				libStatus = ConfigLibrary.reason(r);
			}
		}));
	}
	private void replaceItem(JsonObject fresh) {
		for (int i = 0; i < libItems.size(); i++) {
			if (str(libItems.get(i), "code").equals(str(fresh, "code"))) {
				libItems.set(i, fresh);
			}
		}
	}
	private void loadOpenConfig() {
		if (libOpen == null || libBusy) {
			return;
		}
		String code = str(libOpen, "code");
		String title = str(libOpen, "title");
		libBusy = true;
		libStatus = null;
		ConfigLibrary.get(code, true).thenAccept(r -> minecraft.execute(() -> {
			libBusy = false;
			if (!ConfigLibrary.ok(r)) {
				libStatus = ConfigLibrary.reason(r);
				return;
			}
			JsonObject c = r.getAsJsonObject("config");
			String before = config.exportCode();
			ConfigManager.ImportResult result = config.importCode(str(c, "data"));
			if (!result.ok()) {
				libStatus = result.error();
				return;
			}
			if (libOpen != null && code.equals(str(libOpen, "code"))) {
				libOpen = c;
			}
			replaceItem(c);
			undoCode = before;
			importDone = "Loaded \"" + title + "\": " + result.modules() + (result.modules() == 1 ? " module" : " modules")
					+ " switched to its setup. Undo puts your previous setup back.";
			shareBorn = time;
			if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info("Config loaded", title + " (" + code + ")");
			}
		}));
	}
	private void vote(JsonObject item, int dir) {
		int was = num(item, "vote");
		int now = was == dir ? 0 : dir;
		item.addProperty("vote", now);
		item.addProperty("score", num(item, "score") - was + now);
		ConfigLibrary.vote(str(item, "code"), now).thenAccept(r -> minecraft.execute(() -> {
			if (ConfigLibrary.ok(r)) {
				item.addProperty("score", num(r, "score"));
				item.addProperty("vote", num(r, "vote"));
			} else {
				item.addProperty("vote", was);
				item.addProperty("score", num(item, "score") - now + was);
				libStatus = ConfigLibrary.reason(r);
			}
		}));
	}
	private void sendComment() {
		String text = libComment.toString().trim();
		if (libOpen == null || libBusy || text.length() < 2) {
			return;
		}
		JsonObject target = libOpen;
		libBusy = true;
		ConfigLibrary.comment(str(target, "code"), text).thenAccept(r -> minecraft.execute(() -> {
			libBusy = false;
			if (!ConfigLibrary.ok(r)) {
				libStatus = ConfigLibrary.reason(r);
				return;
			}
			JsonArray fresh = new JsonArray();
			fresh.add(r.getAsJsonObject("comment"));
			if (target.has("comment_list")) {
				fresh.addAll(target.getAsJsonArray("comment_list"));
			}
			target.add("comment_list", fresh);
			target.addProperty("comments", num(target, "comments") + 1);
			libComment.setLength(0);
			libStatus = null;
		}));
	}
	private void deleteComment(JsonObject owner, JsonObject comment) {
		ConfigLibrary.deleteComment(num(comment, "id")).thenAccept(r -> minecraft.execute(() -> {
			if (!ConfigLibrary.ok(r) || !owner.has("comment_list")) {
				return;
			}
			JsonArray kept = new JsonArray();
			for (JsonElement el : owner.getAsJsonArray("comment_list")) {
				if (num(el.getAsJsonObject(), "id") != num(comment, "id")) {
					kept.add(el);
				}
			}
			owner.add("comment_list", kept);
			owner.addProperty("comments", Math.max(0, num(owner, "comments") - 1));
		}));
	}
	private void deleteOpenConfig() {
		if (libOpen == null) {
			return;
		}
		JsonObject target = libOpen;
		confirm = new Confirm("Remove this config?", "\"" + str(target, "title") + "\" leaves the library for everyone, with its votes "
				+ "and comments. This cannot be undone.", "Remove", 0x40FF6B6B, "Keep", () -> ConfigLibrary.delete(str(target, "code"))
				.thenAccept(r -> minecraft.execute(() -> {
					if (ConfigLibrary.ok(r)) {
						libItems.removeIf(o -> str(o, "code").equals(str(target, "code")));
						libOpen = null;
					} else {
						libStatus = ConfigLibrary.reason(r);
					}
				})));
	}
	private void submitPublish() {
		if (pubSending || pubCode != null) {
			return;
		}
		String title = pubTitle.toString().trim();
		if (title.length() < ConfigLibrary.TITLE_MIN) {
			pubError = "Give it a title of at least " + ConfigLibrary.TITLE_MIN + " characters.";
			pubOnDesc = false;
			return;
		}
		int on = (int) modules.all().stream().filter(m -> m.isEnabled() && !m.isAlwaysOn()).count();
		pubSending = true;
		pubError = null;
		ConfigLibrary.publish(title, pubDesc.toString().trim(), config.exportCode(), on).thenAccept(r -> minecraft.execute(() -> {
			pubSending = false;
			if (ConfigLibrary.ok(r)) {
				pubCode = str(r, "code");
				minecraft.keyboardHandler.setClipboard(pubCode);
				shareBorn = time;
			} else {
				pubError = ConfigLibrary.reason(r);
			}
		}));
	}
	private void pasteInLibrary() {
		String clip = minecraft.keyboardHandler.getClipboard();
		if (clip == null || clip.isBlank()) {
			return;
		}
		clip = clip.trim();
		if (libOpen != null) {
			appendLimited(libComment, clip, ConfigLibrary.COMMENT_MAX, false);
			return;
		}
		if (ConfigManager.looksLikeCode(clip) && clip.length() < 20000) {
			String before = config.exportCode();
			ConfigManager.ImportResult result = config.importCode(clip);
			if (!result.ok()) {
				libStatus = result.error();
				return;
			}
			undoCode = before;
			importDone = "Loaded " + result.modules() + (result.modules() == 1 ? " module" : " modules")
					+ " from the pasted code. Undo puts your previous setup back.";
			shareBorn = time;
			return;
		}
		libQuery.setLength(0);
		appendLimited(libQuery, clip, 60, false);
		libQueryChanged = time - 1;
	}
	private static void appendLimited(StringBuilder to, String s, int max, boolean newlines) {
		for (int i = 0; i < s.length() && to.length() < max; i++) {
			char c = s.charAt(i);
			if (c == '\r') {
				continue;
			}
			if (c == '\n' && !newlines) {
				c = ' ';
			}
			to.append(c == '\t' ? ' ' : c);
		}
	}
	private static String ago(long epochSeconds) {
		long s = Math.max(0, System.currentTimeMillis() / 1000 - epochSeconds);
		if (s < 60) {
			return "just now";
		}
		if (s < 3600) {
			return s / 60 + " min ago";
		}
		if (s < 86400) {
			return s / 3600 + " h ago";
		}
		if (s < 86400L * 30) {
			return s / 86400 + " d ago";
		}
		return s / (86400L * 30) + " mo ago";
	}
	private void field(float x, float y, float w, float h, String value, String placeholder, boolean focused, boolean multi, float ease) {
		overlayFill(x, y, w, h, 7, 0x73000000, 0);
		if (focused) {
			float pulse = (float) (0.5 + 0.5 * Math.sin(time * 3));
			overlayFill(x, y, w, h, 7, (Math.round(0x50 + 0x40 * pulse) << 24) | (HudModule.GOLD & 0xFFFFFF), 1);
		}
		List<String> lines = multi ? suggestionLines(value, w - 16) : List.of(value);
		int maxLines = multi ? Math.max(1, (int) ((h - 6) / 11)) : 1;
		int first = Math.max(0, lines.size() - maxLines);
		float ly = multi ? y + 9 : y + h / 2;
		if (value.isEmpty()) {
			text(placeholder, x + 8, ly, 8.5f, (Math.round(0x70 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		}
		String last = "";
		int shown = 0;
		for (int i = first; i < lines.size(); i++) {
			String line = multi ? lines.get(i) : tailFit(lines.get(i), w - 18);
			text(line, x + 8, ly + shown * 11, 8.5f, (Math.round(0xF0 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
			last = line;
			shown++;
		}
		if (focused && (time % 1.0) < 0.55) {
			float cy = ly + Math.max(0, shown - 1) * 11;
			rect(x + 8 + tw(last, Fonts.MEDIUM, 8.5f) + (value.isEmpty() ? -1 : 1), cy - 4.5f, 1f / k * 1.2f, 9, 0xE6FFFFFF);
		}
	}
	private String tailFit(String s, float max) {
		if (tw(s, Fonts.MEDIUM, 8.5f) <= max) {
			return s;
		}
		int start = 0;
		while (start < s.length() && tw("…" + s.substring(start), Fonts.MEDIUM, 8.5f) > max) {
			start++;
		}
		return "…" + s.substring(start);
	}
	private void drawShare() {
		if (importDone != null) {
			drawLoaded();
		} else if (share == Share.PUBLISH) {
			drawPublish();
		} else {
			drawLibrary();
		}
	}
	private float popupEase() {
		float t = Math.min(1, (float) (time - shareBorn) / 0.28f);
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}
	private void popupBackdrop(float ease) {
		rect(0, 0, designW(), designH(), Math.round(0x99 * ease) << 24);
		hit(-10000, -10000, 20000, 20000, (button, mx, my) -> null);
	}
	private void drawLoaded() {
		float ease = popupEase();
		popupBackdrop(ease);
		float w = 268;
		List<String> body = wrap(importDone, w - 28, 8.5f);
		float h = 30 + body.size() * 10.5f + 34;
		float x = (designW() - w) / 2;
		float y = (designH() - h) / 2 + (1 - ease) * 8;
		goldPanel(x, y, w, h, 0.97f * (0.6f + 0.4f * ease));
		icon(Icons.Icon.CHECK, x + 18, y + 16, 10, 0xFF7CE38B);
		text("Config loaded", x + 28, y + 16, 11.5f, Ui.white(ease), Fonts.SEMIBOLD);
		float by = y + 34;
		for (String line : body) {
			text(line, x + 14, by, 8.5f, (Math.round(0xC8 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
			by += 10.5f;
		}
		float bottom = y + h - 22;
		confirmButton(x + w - 14 - 60, bottom, 60, "Done", 0x40E9B949, () -> {
			importDone = null;
			share = Share.NONE;
		});
		confirmButton(x + w - 14 - 60 - 66, bottom, 60, "Undo", 0x26FFFFFF, () -> {
			if (undoCode != null) {
				config.importCode(undoCode);
				undoCode = null;
			}
			importDone = null;
			share = Share.NONE;
		});
		confirmButton(x + 14, bottom, 70, "Library", 0x1AFFFFFF, () -> {
			importDone = null;
			share = Share.LIBRARY;
			shareBorn = time;
		});
	}
	private void drawPublish() {
		float ease = popupEase();
		popupBackdrop(ease);
		float w = 300;
		float x = (designW() - w) / 2;
		if (pubCode != null) {
			float h = 116;
			float y = (designH() - h) / 2 + (1 - ease) * 8;
			goldPanel(x, y, w, h, 0.97f * (0.6f + 0.4f * ease));
			icon(Icons.Icon.CHECK, x + 18, y + 16, 10, 0xFF7CE38B);
			text("Published", x + 28, y + 16, 11.5f, Ui.white(ease), Fonts.SEMIBOLD);
			String copied = "Code copied";
			text(copied, x + w - 14 - tw(copied, Fonts.MEDIUM, 8), y + 16.5f, 8, (Math.round(0xE6 * ease) << 24) | 0x7CE38B, Fonts.MEDIUM);
			overlayFill(x + w / 2 - 60, y + 32, 120, 34, 9, 0x73000000, 0);
			text(pubCode, x + w / 2 - tw(pubCode, Fonts.SEMIBOLD, 18) / 2, y + 49, 18, 0xFFFFE3A1, Fonts.SEMIBOLD);
			float ty = y + 78;
			for (String line : wrap("Anyone can find it in the config library by its title or by this code.", w - 28, 8.5f)) {
				text(line, x + 14, ty, 8.5f, (Math.round(0xC8 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
				ty += 10.5f;
			}
			confirmButton(x + w - 14 - 60, y + h - 22, 60, "Done", 0x40E9B949, () -> share = Share.NONE);
			confirmButton(x + w - 14 - 60 - 82, y + h - 22, 76, "Copy again", 0x26FFFFFF, () -> {
				minecraft.keyboardHandler.setClipboard(pubCode);
				shareBorn = time - 0.12;
			});
			return;
		}
		float descH = 60;
		float h = 34 + 7 + 20 + 14 + 7 + descH + 12 + (pubError != null ? 12 : 0) + 40;
		float y = (designH() - h) / 2 + (1 - ease) * 8;
		goldPanel(x, y, w, h, 0.97f * (0.6f + 0.4f * ease));
		icon(Icons.Icon.SHARE, x + 18, y + 16, 10, 0xFFFFD86A);
		text("Publish your config", x + 28, y + 16, 11.5f, Ui.white(ease), Fonts.SEMIBOLD);
		float fy = y + 34;
		text("Title", x + 14, fy, 8, (Math.round(0xA8 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		String tc = pubTitle.length() + " / " + ConfigLibrary.TITLE_MAX;
		text(tc, x + w - 14 - tw(tc, Fonts.MEDIUM, 7), fy, 7, (Math.round(0x80 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		field(x + 12, fy + 7, w - 24, 20, pubTitle.toString(), "Crystal PvP, Anarchy travel, Legit…", !pubOnDesc, false, ease);
		hit(x + 12, fy + 7, w - 24, 20, (b, mx, my) -> {
			pubOnDesc = false;
			return null;
		});
		fy += 7 + 20 + 14;
		text("Description", x + 14, fy, 8, (Math.round(0xA8 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		String dc = pubDesc.length() + " / " + ConfigLibrary.DESCRIPTION_MAX;
		text(dc, x + w - 14 - tw(dc, Fonts.MEDIUM, 7), fy, 7, (Math.round(0x80 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		field(x + 12, fy + 7, w - 24, descH, pubDesc.toString(), "What it is for, and anything people should know.", pubOnDesc, true, ease);
		hit(x + 12, fy + 7, w - 24, descH, (b, mx, my) -> {
			pubOnDesc = true;
			return null;
		});
		fy += 7 + descH + 10;
		if (pubError != null) {
			text(ellipsize(pubError, Fonts.MEDIUM, 8.5f, w - 28), x + 14, fy, 8.5f, (Math.round(0xF0 * ease) << 24) | 0xFF6B6B, Fonts.MEDIUM);
		}
		String note = "Your modules and settings are published. Keybinds and image files stay on this computer.";
		text(ellipsize(note, Fonts.MEDIUM, 7, w - 28), x + 14, y + h - 32, 7, (Math.round(0x80 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		float bottom = y + h - 22;
		confirmButton(x + w - 14 - 70, bottom, 70, pubSending ? "Publishing…" : "Publish", 0x40E9B949, this::submitPublish);
		confirmButton(x + 14, bottom, 56, "Cancel", 0x1AFFFFFF, () -> share = Share.NONE);
	}
	private static final String[][] SORTS = {{"top", "Most votes"}, {"new", "Newest"}, {"old", "Oldest"}, {"downloads", "Most loaded"},
			{"comments", "Most comments"}};
	private static float approach(float shown, float target, float rate, float dt) {
		float next = shown + (target - shown) * (1 - (float) Math.exp(-rate * dt));
		return Math.abs(target - next) < 0.02f ? target : next;
	}
	private static float easeOut(float t) {
		float u = 1 - Math.max(0, Math.min(1, t));
		return 1 - u * u * u;
	}
	private void closeLibrary() {
		if (libClosing < 0) {
			libClosing = time;
			libSortOpen = false;
		}
	}
	private void drawLibrary() {
		float dt = (float) Math.max(0, Math.min(0.1, time - libAnimTime));
		libAnimTime = time;
		boolean closing = libClosing >= 0;
		float ease;
		if (closing) {
			float t = Math.min(1, (float) (time - libClosing) / 0.2f);
			if (t >= 1) {
				libClosing = -1;
				share = Share.NONE;
				return;
			}
			ease = (1 - t) * (1 - t);
		} else {
			ease = popupEase();
		}
		popupBackdrop(ease);
		if (libQueryChanged >= 0 && time - libQueryChanged > 0.35) {
			libQueryChanged = -1;
			refreshLibrary(false);
		}
		float w = Math.min(designW() - 30, 460);
		float h = Math.min(designH() - 30, 330);
		float x = (designW() - w) / 2;
		float y = (designH() - h) / 2 + (1 - ease) * 8;
		goldPanel(x, y, w, h, closing ? 0.97f * ease : 0.97f * (0.6f + 0.4f * ease));
		boolean detail = libOpen != null;
		if (detail != libWasDetail) {
			libWasDetail = detail;
			libViewBorn = time;
		}
		float view = easeOut((float) (time - libViewBorn) / 0.26f);
		float a0 = ui.alpha();
		ui.alpha(a0 * (closing ? ease : 1) * view);
		float cx = x + (1 - view) * (detail ? 14 : -14);
		if (detail) {
			drawLibraryDetail(cx, y, w, h, ease, dt);
		} else {
			drawLibraryList(cx, y, w, h, ease, dt);
		}
		ui.alpha(a0);
		if (closing) {
			hit(-10000, -10000, 20000, 20000, (button, mx, my) -> null);
		}
	}
	private void drawLibraryList(float x, float y, float w, float h, float ease, float dt) {
		icon(Icons.Icon.PACKAGE, x + 18, y + 16, 10, 0xFFFFD86A);
		text("Config library", x + 28, y + 16, 11.5f, Ui.white(ease), Fonts.SEMIBOLD);
		confirmButton(x + w - 14 - 96, y + 9, 96, "Publish yours", 0x40E9B949, this::openPublish);
		float sy = y + 30;
		String sortLabel = SORTS[0][1];
		float sortW = 0;
		for (String[] s : SORTS) {
			sortW = Math.max(sortW, tw(s[1], Fonts.MEDIUM, 8));
			if (s[0].equals(libSort)) {
				sortLabel = s[1];
			}
		}
		sortW += 28;
		float mineW = tw("Mine", Fonts.MEDIUM, 8) + 16;
		float searchW = w - 24 - sortW - 4 - mineW - 6;
		field(x + 12, sy, searchW, 18, libQuery.toString(), "Search by title, author or code", true, false, ease);
		float cx = x + 12 + searchW + 6;
		float sortX = cx;
		float glowSort = hoverEase("libsort", hovered(cx, sy + 1, sortW, 16));
		overlayFill(cx, sy + 1, sortW, 16, 8, libSortOpen ? 0x40FFFFFF : (Math.round(0x1A + 0x30 * glowSort) << 24) | 0xFFFFFF, 0);
		text(sortLabel, cx + 8, sy + 9, 8, 0xFFFFFFFF, Fonts.MEDIUM);
		triangle(cx + sortW - 9, sy + 9);
		hit(cx, sy + 1, sortW, 16, (b, mx, my) -> {
			libSortOpen = !libSortOpen;
			return null;
		});
		cx += sortW + 4;
		float glowMine = hoverEase("libsortmine", hovered(cx, sy + 1, mineW, 16));
		float mineOn = hoverEase("libmineon", libMine);
		int mineFill = Ui.lerpColor((Math.round(0x1A + 0x30 * glowMine) << 24) | 0xFFFFFF, 0x60000000 | (HudModule.GOLD & 0xFFFFFF), mineOn);
		overlayFill(cx, sy + 1, mineW, 16, 8, mineFill, 0);
		text("Mine", cx + 8, sy + 9, 8, Ui.lerpColor(0xCCFFFFFF, 0xFFFFFFFF, mineOn), Fonts.MEDIUM);
		hit(cx, sy + 1, mineW, 16, (b, mx, my) -> {
			libMine = !libMine;
			libSortOpen = false;
			refreshLibrary(false);
			return null;
		});
		float listH = 0;
		libSortEase = approach(libSortEase, libSortOpen ? 1 : 0, 18, dt);
		if (libSortEase > 0.01f) {
			float open = easeOut(libSortEase);
			listH = (SORTS.length * 12 + 4) * open;
			drawLibrarySorts(sortX, sy + 21, sortW, listH, open);
			listH += 4 * open;
		}
		float lx0 = x + 12;
		float lx1 = x + w - 12;
		float ly0 = sy + 24 + listH;
		float ly1 = y + h - 30;
		float rowH = 44;
		float gap = 4;
		float content = libItems.size() * (rowH + gap) + (libMore ? 24 : 0);
		float maxScroll = Math.max(0, content - (ly1 - ly0));
		libScroll = Math.max(0, Math.min(libScroll, maxScroll));
		libScrollShown = Math.max(0, Math.min(approach(libScrollShown, libScroll, 16, dt), maxScroll));
		clip(lx0, ly0, lx1, ly1);
		float ry = ly0 - libScrollShown;
		for (JsonObject item : libItems) {
			if (ry + rowH >= ly0 && ry <= ly1) {
				drawLibraryRow(item, lx0, ry, lx1 - lx0, rowH, ease);
			}
			ry += rowH + gap;
		}
		if (libMore) {
			String more = libLoading ? "Loading…" : "Show more";
			float glowMore = hoverEase("libmore", hovered(lx0, ry, lx1 - lx0, 20));
			rect(lx0, ry, lx1 - lx0, 20, (Math.round(0x18 + 0x18 * glowMore) << 24) | 0xFFFFFF);
			text(more, (lx0 + lx1) / 2 - tw(more, Fonts.MEDIUM, 8.5f) / 2, ry + 10, 8.5f, 0xCCFFFFFF, Fonts.MEDIUM);
			hit(lx0, ry, lx1 - lx0, 20, (b, mx, my) -> {
				if (!libLoading) {
					refreshLibrary(true);
				}
				return null;
			});
		}
		unclip();
		if (libItems.isEmpty()) {
			String empty = libError != null ? libError : libLoading ? "Loading the library…"
					: libMine ? "You have not published a config yet."
					: libQuery.isEmpty() ? "Nothing here yet. Be the first to publish one." : "No configs match that search.";
			empty = ellipsize(empty, Fonts.MEDIUM, 8.5f, lx1 - lx0 - 20);
			text(empty, (lx0 + lx1) / 2 - tw(empty, Fonts.MEDIUM, 8.5f) / 2, (ly0 + ly1) / 2, 8.5f,
					libError != null ? 0xF0FF6B6B : 0xA8FFFFFF, Fonts.MEDIUM);
		}

		float bottom = y + h - 22;
		confirmButton(x + w - 14 - 60, bottom, 60, "Close", 0x26FFFFFF, this::closeLibrary);
		confirmButton(x + 14, bottom, 70, "Paste code", 0x1AFFFFFF, this::pasteInLibrary);
		String status = libStatus != null ? libStatus : !libItems.isEmpty() ? libError : null;
		if (status != null) {
			text(ellipsize(status, Fonts.MEDIUM, 8, w - 180), x + 92, bottom + 7.5f, 8, 0xF0FF6B6B, Fonts.MEDIUM);
		}
	}
	private void drawLibrarySorts(float x, float y, float w, float h, float open) {
		float optH = 12;
		overlayFill(x, y, w, h, 7, (Math.round(0x30 * open) << 24) | 0xFFFFFF, 0);
		float a0 = ui.alpha();
		ui.alpha(a0 * open);
		for (int i = 0; i < SORTS.length; i++) {
			String key = SORTS[i][0];
			float oy = y + 2 + i * optH;
			if (oy + optH > y + h + 0.5f) {
				break;
			}
			boolean selected = key.equals(libSort);
			float glow = hoverEase("libsortopt" + key, hovered(x + 2, oy, w - 4, optH));
			if (selected || glow > 0.02f) {
				int fill = selected ? 0x60000000 | (HudModule.GOLD & 0xFFFFFF) : (Math.round(0x28 * glow) << 24) | 0xFFFFFF;
				overlayFill(x + 2, oy, w - 4, optH, 6, fill, 0);
			}
			text(SORTS[i][1], x + 8, oy + optH / 2, 8, selected ? 0xFFFFFFFF : 0xC8FFFFFF, Fonts.MEDIUM);
			hit(x + 2, oy, w - 4, optH, (button, mx, my) -> {
				libSort = key;
				libSortOpen = false;
				refreshLibrary(false);
				return null;
			});
		}
		ui.alpha(a0);
	}
	private void drawLibraryRow(JsonObject item, float x, float y, float w, float h, float ease) {
		Double born = libRowBorn.get(str(item, "code"));
		float in = born == null ? 1 : easeOut((float) (time - born) / 0.32f);
		float a0 = ui.alpha();
		if (in < 1) {
			ui.alpha(a0 * in);
			y += (1 - in) * 8;
		}
		drawLibraryRowContent(item, x, y, w, h, ease);
		ui.alpha(a0);
	}

	private void drawLibraryRowContent(JsonObject item, float x, float y, float w, float h, float ease) {
		float glow = hoverEase("librow" + str(item, "code"), hovered(x, y, w, h));
		rect(x, y, w, h, (Math.round(0x22 + 0x22 * glow) << 24) | 0xFFFFFF);
		rect(x, y, 2, h, (Math.round(0x60 + 0x9F * glow) << 24) | (HudModule.GOLD & 0xFFFFFF));
		hit(x, y, w - 36, h, (b, mx, my) -> {
			openConfig(item);
			return null;
		});
		float textW = w - 60;
		String title = ellipsize(str(item, "title"), Fonts.SEMIBOLD, 9.5f, textW - 50);
		text(title, x + 10, y + 11, 9.5f, Ui.white(ease), Fonts.SEMIBOLD);
		text(str(item, "code"), x + 10 + tw(title, Fonts.SEMIBOLD, 9.5f) + 7, y + 11.5f, 7.5f, 0xCCFFE3A1, Fonts.MEDIUM);
		String meta = "by " + str(item, "author") + "  ·  " + num(item, "modules") + " modules  ·  " + num(item, "downloads")
				+ " loads  ·  " + num(item, "comments") + " comments  ·  " + ago(num(item, "created")) + (flag(item, "mine") ? "  ·  yours" : "");
		text(ellipsize(meta, Fonts.MEDIUM, 7.5f, textW), x + 10, y + 23, 7.5f, 0x99FFFFFF, Fonts.MEDIUM);
		String desc = str(item, "description").replace('\n', ' ');
		text(ellipsize(desc.isEmpty() ? "No description" : desc, Fonts.MEDIUM, 8, textW), x + 10, y + 34, 8,
				desc.isEmpty() ? 0x66FFFFFF : 0xB8FFFFFF, Fonts.MEDIUM);
		drawVotes(item, x + w - 20, y + h / 2);
	}
	private void drawVotes(JsonObject item, float cx, float cy) {
		int mine = num(item, "vote");
		int score = num(item, "score");
		int up = mine > 0 ? 0xFF7CE38B : hovered(cx - 9, cy - 19, 18, 13) ? 0xFFFFFFFF : 0x99FFFFFF;
		int down = mine < 0 ? 0xFFFF6B6B : hovered(cx - 9, cy + 6, 18, 13) ? 0xFFFFFFFF : 0x99FFFFFF;
		int upColor = fade(up);
		drawVisible(cx - 5, cy - 18, cx + 5, cy - 7,
				() -> Icons.drawFlipped(g, Icons.Icon.CHEVRON_DOWN, ui.mapX(cx), ui.mapY(cy - 12.5f), 10 * k, upColor));
		icon(Icons.Icon.CHEVRON_DOWN, cx, cy + 12.5f, 10, down);
		String s = String.valueOf(score);
		text(s, cx - tw(s, Fonts.SEMIBOLD, 8.5f) / 2, cy, 8.5f, score > 0 ? 0xFF7CE38B : score < 0 ? 0xFFFF6B6B : 0xDDFFFFFF, Fonts.SEMIBOLD);
		hit(cx - 9, cy - 19, 18, 13, (b, mx, my) -> {
			vote(item, 1);
			return null;
		});
		hit(cx - 9, cy + 6, 18, 13, (b, mx, my) -> {
			vote(item, -1);
			return null;
		});
	}
	private void drawLibraryDetail(float x, float y, float w, float h, float ease, float dt) {
		JsonObject c = libOpen;
		boolean mine = flag(c, "mine");
		icon(Icons.Icon.ARROW_LEFT, x + 18, y + 16, 10, hovered(x + 10, y + 8, 16, 16) ? 0xFFFFFFFF : 0xB0FFFFFF);
		hit(x + 10, y + 8, 16, 16, (b, mx, my) -> {
			libOpen = null;
			libStatus = null;
			return null;
		});
		String title = ellipsize(str(c, "title"), Fonts.SEMIBOLD, 11.5f, w - 150);
		text(title, x + 30, y + 16, 11.5f, Ui.white(ease), Fonts.SEMIBOLD);
		String code = str(c, "code");
		float codeX = x + 30 + tw(title, Fonts.SEMIBOLD, 11.5f) + 8;
		float codeW = tw(code, Fonts.SEMIBOLD, 8.5f) + 8;
		boolean codeOver = hovered(codeX - 4, y + 9, codeW, 14);
		overlayFill(codeX - 4, y + 9, codeW, 14, 7, codeOver ? 0x50E9B949 : 0x30E9B949, 0);
		text(code, codeX, y + 16, 8.5f, 0xFFFFE3A1, Fonts.SEMIBOLD);
		if (codeOver) {
			hover("libcode", "Copy the code", "Anyone can search the library for " + code + " to find this config.");
		}
		hit(codeX - 4, y + 9, codeW, 14, (b, mx, my) -> {
			minecraft.keyboardHandler.setClipboard(code);
			if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info("Code copied", code);
			}
			return null;
		});
		drawVotes(c, x + w - 22, y + 22);
		String meta = "by " + str(c, "author") + "  ·  " + num(c, "modules") + " modules  ·  " + num(c, "downloads") + " loads  ·  "
				+ ago(num(c, "created"));
		text(ellipsize(meta, Fonts.MEDIUM, 8, w - 80), x + 30, y + 30, 8, 0x99FFFFFF, Fonts.MEDIUM);
		float cx0 = x + 14;
		float cx1 = x + w - 14;
		float cy0 = y + 44;
		float cy1 = y + h - 56;
		String description = str(c, "description");
		List<String> desc = wrap(description.isEmpty() ? "No description." : description, cx1 - cx0, 8.5f);
		JsonArray list = c.has("comment_list") ? c.getAsJsonArray("comment_list") : null;
		List<JsonObject> comments = new ArrayList<>();
		List<List<String>> commentLines = new ArrayList<>();
		float content = 6 + desc.size() * 10.5f + 18 + 12;
		if (list != null) {
			for (JsonElement el : list) {
				JsonObject cm = el.getAsJsonObject();
				List<String> lines = wrap(str(cm, "text"), cx1 - cx0 - 16, 8);
				comments.add(cm);
				commentLines.add(lines);
				content += 18 + lines.size() * 10 + 4;
			}
		}
		float maxScroll = Math.max(0, content - (cy1 - cy0));
		libDetailScroll = Math.max(0, Math.min(libDetailScroll, maxScroll));
		libDetailScrollShown = Math.max(0, Math.min(approach(libDetailScrollShown, libDetailScroll, 16, dt), maxScroll));
		clip(cx0, cy0, cx1, cy1);
		float ty = cy0 + 6 - libDetailScrollShown;
		for (String line : desc) {
			text(line, cx0, ty, 8.5f, (Math.round(0xD8 * ease) << 24) | 0xFFFFFF, Fonts.MEDIUM);
			ty += 10.5f;
		}
		ty += 8;
		rect(cx0, ty, cx1 - cx0, 1f / k, 0x30FFFFFF);
		ty += 10;
		int count = num(c, "comments");
		text(list == null ? "Loading comments…" : count + (count == 1 ? " comment" : " comments"), cx0, ty, 8, 0xA8FFFFFF, Fonts.SEMIBOLD);
		ty += 12;
		for (int i = 0; i < comments.size(); i++) {
			JsonObject cm = comments.get(i);
			List<String> lines = commentLines.get(i);
			float rowH = 14 + lines.size() * 10 + 4;
			rect(cx0, ty, cx1 - cx0, rowH, 0x16FFFFFF);
			String who = str(cm, "author");
			text(who, cx0 + 8, ty + 7, 8, 0xFFFFE3A1, Fonts.SEMIBOLD);
			text(ago(num(cm, "created")), cx0 + 8 + tw(who, Fonts.SEMIBOLD, 8) + 6, ty + 7, 7, 0x80FFFFFF, Fonts.MEDIUM);
			if (flag(cm, "mine")) {
				float bx = cx1 - 14;
				icon(Icons.Icon.TRASH, bx, ty + 7, 8, hovered(bx - 6, ty + 1, 12, 12) ? 0xFFFF6B6B : 0x80FFFFFF);
				hit(bx - 6, ty + 1, 12, 12, (b, mx, my) -> {
					deleteComment(c, cm);
					return null;
				});
			}
			float ly = ty + 18;
			for (String line : lines) {
				text(line, cx0 + 8, ly, 8, 0xD0FFFFFF, Fonts.MEDIUM);
				ly += 10;
			}
			ty += rowH + 4;
		}
		unclip();
		float fy = y + h - 50;
		float sendW = 50;
		field(x + 12, fy, w - 24 - sendW - 6, 20, libComment.toString(), "Write a comment", true, false, ease);
		confirmButton(x + w - 12 - sendW, fy + 2.5f, sendW, "Send", 0x26FFFFFF, this::sendComment);
		float bottom = y + h - 22;
		confirmButton(x + w - 14 - 70, bottom, 70, libBusy ? "Loading…" : "Load", 0x40E9B949, this::loadOpenConfig);
		confirmButton(x + w - 14 - 70 - 66, bottom, 60, "Back", 0x26FFFFFF, () -> {
			libOpen = null;
			libStatus = null;
		});
		if (mine) {
			confirmButton(x + 14, bottom, 66, "Remove", 0x30FF6B6B, this::deleteOpenConfig);
		}
		if (libStatus != null) {
			float sx = x + 14 + (mine ? 72 : 0);
			text(ellipsize(libStatus, Fonts.MEDIUM, 8, x + w - 14 - 136 - 8 - sx), sx, bottom + 7.5f, 8, 0xF0FF6B6B, Fonts.MEDIUM);
		}
	}
	private void shareKey(int key, boolean ctrl, boolean shift) {
		boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
		if (importDone != null) {
			if (key == InputConstants.KEY_ESCAPE || enter) {
				importDone = null;
				share = Share.NONE;
			}
			return;
		}
		if (share == Share.PUBLISH) {
			StringBuilder field = pubOnDesc ? pubDesc : pubTitle;
			int max = pubOnDesc ? ConfigLibrary.DESCRIPTION_MAX : ConfigLibrary.TITLE_MAX;
			if (key == InputConstants.KEY_ESCAPE) {
				share = Share.NONE;
			} else if (pubCode != null) {
				if (enter) {
					share = Share.NONE;
				}
			} else if (key == InputConstants.KEY_TAB) {
				pubOnDesc = !pubOnDesc;
			} else if (ctrl && key == InputConstants.KEY_V) {
				String clip = minecraft.keyboardHandler.getClipboard();
				if (clip != null) {
					appendLimited(field, clip, max, pubOnDesc);
				}
			} else if (key == InputConstants.KEY_BACKSPACE && !field.isEmpty()) {
				field.setLength(ctrl ? 0 : field.length() - 1);
				pubError = null;
			} else if (enter) {
				if (!pubOnDesc) {
					pubOnDesc = true;
				} else if (shift) {
					appendLimited(pubDesc, "\n", max, true);
				} else {
					submitPublish();
				}
			}
			return;
		}
		if (libClosing >= 0) {
			return;
		}
		StringBuilder field = libOpen != null ? libComment : libQuery;
		if (key == InputConstants.KEY_ESCAPE) {
			if (libSortOpen) {
				libSortOpen = false;
			} else if (libOpen != null) {
				libOpen = null;
				libStatus = null;
			} else {
				closeLibrary();
			}
		} else if (ctrl && key == InputConstants.KEY_V) {
			pasteInLibrary();
		} else if (key == InputConstants.KEY_BACKSPACE && !field.isEmpty()) {
			field.setLength(ctrl ? 0 : field.length() - 1);
			if (libOpen == null) {
				libQueryChanged = time;
			}
		} else if (enter) {
			if (libOpen != null) {
				sendComment();
			} else {
				libQueryChanged = -1;
				refreshLibrary(false);
			}
		}
	}
	private void shareChar(String c) {
		if (importDone != null || libClosing >= 0) {
			return;
		}
		if (share == Share.PUBLISH) {
			if (pubCode == null && !pubSending) {
				appendLimited(pubOnDesc ? pubDesc : pubTitle, c, pubOnDesc ? ConfigLibrary.DESCRIPTION_MAX : ConfigLibrary.TITLE_MAX, false);
				pubError = null;
			}
		} else if (libOpen != null) {
			appendLimited(libComment, c, ConfigLibrary.COMMENT_MAX, false);
		} else {
			appendLimited(libQuery, c, 60, false);
			libQueryChanged = time;
		}
	}
	private List<Module> modulesOf(Panel p) {
		List<Module> all = p.category == null ? modules.search(search.toString()) : modules.inCategory(p.category);
		return all.stream().filter(HvhTab::listed).toList();
	}
	private void drawPanel(Panel p, int index, boolean interactable) {
		List<Module> list = modulesOf(p);
		if (p.category != null && list.isEmpty()) {
			return;
		}
		List<nz.temple.module.ModuleManager.SettingMatch> settingHits = p == searchPanel
				? modules.searchSettings(search.toString()) : List.of();
		float x = (float) p.x;
		float y = (float) p.y;
		float full = Math.max(1, list.size()) * ROW + 4;
		if (!settingHits.isEmpty()) {
			full = (list.isEmpty() ? 0 : list.size() * ROW + 3) + HEADER_H + settingHits.size() * HIT_ROW + 4;
		}
		float listH = Math.min(full, Math.max(60, designH() - y - HEADER - 10));
		float open = clamp01(p.open.xf());
		float h = HEADER + listH * open;
		double offset = index * 0.12;
		ui.glass(x, y, WIN_W, h, RADIUS);
		window(x, y, WIN_W, h);
		if (p == searchPanel) {
			searchRect = new float[] {x, y, x + WIN_W, y + h};
		}
		icon(Icons.of(p.category), x + 13, y + HEADER / 2 - 1, 10, (0xE6 << 24) | (Theme.color(offset) & 0xFFFFFF));
		text(p.title(), x + 22, y + HEADER / 2 + 0.5f, 10.5f, 0xFFFFFFFF, Fonts.SEMIBOLD);
		long on = list.stream().filter(m -> m.isEnabled() && !m.isAlwaysOn()).count();
		String count = p.category == null ? String.valueOf(list.size() + settingHits.size()) : on + "/" + list.size();
		text(count, x + WIN_W - 8 - tw(count, Fonts.MEDIUM, SMALL), y + HEADER / 2 + 0.5f, SMALL, 0x99FFFFFF, Fonts.MEDIUM);
		if (open > 0.02) {
			accentLine(x + 6, y + HEADER - 1.5f, WIN_W - 12, offset);
		}
		if (interactable) {
		hit(x, y, WIN_W, HEADER, (button, mx, my) -> {
			if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
				p.expanded = !p.expanded;
				p.open.target = p.expanded ? 1 : 0;
				savePanel(p);
				return null;
			}
			if (p.category != null) {
				panels.remove(p);
				panels.add(p);
			}
			double ox = mx - p.x;
			double oy = my - p.y;
			return new Drag() {
				public void move(double dx, double dy) {
					p.x = Math.max(20 - WIN_W, Math.min(designW() - 20, dx - ox));
					p.y = Math.max(0, Math.min(designH() - HEADER, dy - oy));
				}

				public void release() {
					savePanel(p);
				}
			};
		});
		}
		if (open < 0.02) {
			return;
		}
		float top = y + HEADER;
		p.scroll = Math.max(0, Math.min(p.scroll, full - listH));
		clip(x, top, x + WIN_W, y + h);
		if (list.isEmpty() && settingHits.isEmpty()) {
			text(p.category == null ? "No matches" : "Empty", x + 9, top + 2 + ROW / 2, TEXT, 0x80FFFFFF, Fonts.MEDIUM);
		}
		for (int i = 0; i < list.size(); i++) {
			float ry = top + 2 + i * ROW - p.scroll;
			if (ry + ROW < top || ry > y + h) {
				continue;
			}
			Module m = list.get(i);
			boolean active = m.isEnabled() && !m.isAlwaysOn();
			int accent = Theme.color(offset + i * 0.04);
			drawSelection(m, active, accent, x + 3, ry + 0.5f, WIN_W - 6, ROW - 1);
			if (settingsModule == m) {
				ui.ring(x + 3, ry + 0.5f, WIN_W - 6, ROW - 1, (ROW - 1) / 2, 0.75f, 0x55FFFFFF);
			}
			if (interactable && hovered(x + 3, ry, WIN_W - 6, ROW)) {
				ui.fill(x + 3, ry + 0.5f, WIN_W - 6, ROW - 1, (ROW - 1) / 2, 0x16FFFFFF);
				hover(m, m.name(), m.description() + (m instanceof HudModule ? "  Right-click to edit the HUD layout."
						: m.isAlwaysOn() ? "" : "  Left-click toggles, right-click opens settings."));
			}
			float rightEdge = x + WIN_W - 8;
			if (m.keybind().isBound()) {
				String key = KeyNames.name(m.keybind().get());
				float kw = tw(key, Fonts.MEDIUM, 7.5f);
				text(key, rightEdge - kw, ry + ROW / 2, 7.5f, 0x80FFFFFF, Fonts.MEDIUM);
				rightEdge -= kw + 4;
			}
			String name = ellipsize(m.name(), Fonts.MEDIUM, TEXT, rightEdge - x - 10);
			text(name, x + 9, ry + ROW / 2, TEXT, active || m.isAlwaysOn() ? 0xFFFFFFFF : 0xB8FFFFFF, Fonts.MEDIUM);
			if (interactable) {
				hit(x + 3, ry, WIN_W - 6, ROW, (button, mx, my) -> {
					if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && !m.isAlwaysOn()) {
						toggleWithConfirm(m);
					} else if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
						openSettings(m);
					}
					return null;
				});
			}
		}
		if (!settingHits.isEmpty()) {
			drawSettingHits(settingHits, x, top + (list.isEmpty() ? 0 : list.size() * ROW + 3) - p.scroll, top, y + h,
					interactable);
		}
		unclip();
	}
	private static final float HIT_ROW = 21;
	private void drawSettingHits(List<nz.temple.module.ModuleManager.SettingMatch> hits, float x, float startY,
			float top, float bottom, boolean interactable) {
		int gold = HudModule.GOLD & 0xFFFFFF;
		if (startY + HEADER_H >= top && startY <= bottom) {
			String caps = "SETTINGS";
			float size = 6.5f;
			float cy = startY + HEADER_H / 2 + 1;
			text(caps, x + 9, cy, size, (0xD0 << 24) | gold, Fonts.SEMIBOLD);
			float lineX = x + 9 + tw(caps, Fonts.SEMIBOLD, size) + 5;
			rect(lineX, cy, x + WIN_W - 9 - lineX, 1f / k, (0x40 << 24) | gold);
		}
		for (int i = 0; i < hits.size(); i++) {
			float ry = startY + HEADER_H + i * HIT_ROW;
			if (ry + HIT_ROW < top || ry > bottom) {
				continue;
			}
			nz.temple.module.ModuleManager.SettingMatch hit = hits.get(i);
			Module m = hit.module();
			Setting<?> s = hit.setting();
			boolean open = settingsModule == m && spotlight == s;
			boolean over = interactable && hovered(x + 3, ry, WIN_W - 6, HIT_ROW);
			if (open || over) {
				ui.fill(x + 3, ry + 0.5f, WIN_W - 6, HIT_ROW - 1, 7, ((open ? 0x24 : 0x18) << 24) | gold);
			}
			if (open) {
				ui.ring(x + 3, ry + 0.5f, WIN_W - 6, HIT_ROW - 1, 7, 0.75f, (0x80 << 24) | gold);
			}
			if (over) {
				hover(s, s.name() + "  ·  " + m.name(), (s.description().isEmpty() ? "" : s.description() + "  ")
						+ "Click to open it in " + m.name() + ".");
			}
			icon(Icons.Icon.SLIDERS, x + 13, ry + HIT_ROW / 2, 8, (0xE6 << 24) | gold);
			float room = WIN_W - 20 - 8;
			text(ellipsize(s.name(), Fonts.MEDIUM, TEXT, room), x + 20, ry + 7, TEXT, 0xFFF3D38A, Fonts.MEDIUM);
			text(ellipsize(m.name(), Fonts.MEDIUM, 7, room), x + 20, ry + 15.8f, 7, 0x8CFFFFFF, Fonts.MEDIUM);
			if (interactable) {
				hit(x + 3, ry, WIN_W - 6, HIT_ROW, (button, mx, my) -> {
					if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
						openSetting(m, s);
					}
					return null;
				});
			}
		}
	}
	private void drawSelection(Module m, boolean active, int accent, float x, float y, float w, float h) {
		Blob blob = null;
		for (Blob b : blobs) {
			if (b.module == m) {
				blob = b;
			}
		}
		float scaleX = 1;
		float scaleY = 1;
		float fade = active ? 1 : 0;
		float sag = 0;
		if (blob != null) {
			double t = Math.min(1, blob.age() / 0.85);
			double drop = 1 - Math.pow(1 - Math.min(1, t / 0.3), 3);
			double wobble = t < 0.3 ? 0 : Math.sin((t - 0.3) / 0.7 * Math.PI * 2.4) * Math.pow(1 - (t - 0.3) / 0.7, 2);
			if (blob.enabling) {
				scaleX = (float) (0.55 + 0.45 * drop + wobble * 0.10);
				scaleY = (float) (0.30 + 0.70 * drop - wobble * 0.16);
				fade = (float) Math.min(1, drop * 1.4);
			} else {
				scaleX = (float) (1 + drop * 0.30 + wobble * 0.08);
				scaleY = (float) Math.max(0, 1 - drop * 0.95);
				sag = (float) (drop * h * 0.35);
				fade = (float) Math.max(0, 1 - t * 1.25);
			}
		} else if (active) {
			double idle = Theme.time() * 1.7 + (System.identityHashCode(m) % 628) / 100.0;
			scaleX = (float) (1 + Math.sin(idle) * 0.018);
			scaleY = (float) (1 - Math.sin(idle) * 0.022);
		}
		if (fade <= 0.01f) {
			return;
		}
		float cx = x + w / 2;
		float cy = y + h / 2 + sag;
		float bw = w * scaleX;
		float bh = h * scaleY;
		float bx = cx - bw / 2;
		float by = cy - bh / 2;
		float radius = bh / 2;
		int gold = Fx.lerp(accent, HudModule.GOLD, 0.35) & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		float prev = ui.alpha();
		ui.alpha(prev * fade);
		if (!ui.glassRenderer().hasRoom(0, 8)) {
			ui.fill(bx, by, bw, bh, radius, 0x66000000 | gold);
			ui.alpha(prev);
			return;
		}

		ui.shadow(bx + bw * 0.06f, by + bh * 0.22f, bw * 0.88f, bh * 0.9f, radius, 3.5f, 0x4A000000);
		ui.fill(bx, by, bw, bh, radius, 0x33000000 | gold);
		ui.lens(bx, by, bw, bh, radius, 1f, 0.9f);
		ui.ring(bx, by, bw, bh, radius, Math.max(0.5f, 0.9f / k), 0x59000000 | light);
		ui.fill(bx + bw * 0.16f, by + bh * 0.17f, bw * 0.26f, bh * 0.3f, bh * 0.15f, 0x8CFFFFFF);
		ui.fill(bx + bw * 0.12f, by + bh * 0.52f, bw * 0.3f, bh * 0.22f, bh * 0.11f, 0x2EFFFFFF);
		ui.fill(bx + bw * 0.45f, by + bh * 0.66f, bw * 0.42f, bh * 0.26f, bh * 0.13f, 0x40000000 | light);
		ui.fill(bx + bw * 0.12f, by + bh - Math.max(0.6f, 1.1f / k), bw * 0.76f, Math.max(0.6f, 1.1f / k),
				Math.max(0.3f, 0.5f / k), 0x66000000 | light);
		if (blob != null) {
			splash(blob, cx, cy, w, h, gold, light);
		}
		ui.alpha(prev);
	}
	private void splash(Blob blob, float cx, float cy, float w, float h, int gold, int light) {
		double t = Math.min(1, blob.age() / 0.85);
		double impact = blob.enabling ? Math.max(0, (t - 0.18) / 0.82) : t;
		if (impact <= 0 || impact >= 1) {
			return;
		}
		float ring = (float) (1 - Math.pow(1 - impact, 2.2));
		float ringFade = (float) Math.pow(1 - impact, 1.6);
		float rw = w * (0.55f + ring * 0.85f);
		float rh = h * (0.7f + ring * 0.75f);
		float prev = ui.alpha();
		ui.alpha(prev * ringFade);
		ui.ring(cx - rw / 2, cy - rh / 2, rw, rh, rh / 2, Math.max(0.5f, 1.1f / k), 0x8C000000 | light);
		ui.alpha(prev);
		int drops = 6;
		for (int i = 0; i < drops; i++) {
			double angle = Math.PI * 2 * i / drops + (blob.enabling ? 0 : 0.5);
			double reach = (w * 0.34 + h * 0.5) * impact;
			double dx = Math.cos(angle) * reach;
			double dy = Math.sin(angle) * reach * 0.42 - Math.sin(impact * Math.PI) * h * 0.45;
			float ds = (float) (h * (0.34 - 0.2 * impact));
			if (ds < 0.6f) {
				continue;
			}
			float dAlpha = (float) Math.pow(1 - impact, 1.4);
			ui.alpha(prev * dAlpha);
			ui.fill((float) (cx + dx) - ds / 2, (float) (cy + dy) - ds / 2, ds, ds, ds / 2, 0x4D000000 | gold);
			ui.lens((float) (cx + dx) - ds / 2, (float) (cy + dy) - ds / 2, ds, ds, ds / 2, 1f, 0.75f);
			ui.alpha(prev);
		}
	}
	private static void slide(NumberSetting n, double fraction) {
		double before = n.get();
		n.setFraction(fraction);
		if (n.get() != before) {
			nz.temple.client.audio.UiSounds.slider(n.fraction());
		}
	}
	private int lastDetent = Integer.MIN_VALUE;
	private void detent(double fraction, int notch) {
		if (notch != lastDetent) {
			lastDetent = notch;
			nz.temple.client.audio.UiSounds.slider(fraction);
		}
	}

	private void toggleWithConfirm(Module m) {
		if (m.isEnabled() && m.disableWarning() != null) {
			confirm = new Confirm("Turn off " + m.name() + "?", m.disableWarning(), "Turn it off", 0x33FF453A, "Keep on",
					() -> m.setEnabled(false));
			return;
		}
		m.toggle();
		nz.temple.client.audio.UiSounds.toggle(m.isEnabled());
	}
	private void toggleWithConfirm(BoolSetting b) {
		if (!b.get() && b.enableWarning() != null) {
			confirm = new Confirm("Turn on " + b.name() + "?", b.enableWarning(), "Turn it on", 0x40E9B949, "Cancel", () -> {
				b.set(true);
				nz.temple.client.audio.UiSounds.toggle(true);
			});
			return;
		}
		b.toggle();
		nz.temple.client.audio.UiSounds.toggle(b.get());
	}

	private void drawConfirm() {
		Confirm c = confirm;
		float dw = designW();
		float dh = designH();
		rect(0, 0, dw, dh, 0x99000000);
		hit(-10000, -10000, 20000, 20000, (button, mx, my) -> null);
		List<String> body = wrap(c.body(), 218, 9);
		float w = 246;
		float h = 34 + body.size() * 11 + 30;
		float x = (dw - w) / 2;
		float y = (dh - h) / 2;
		goldPanel(x, y, w, h, 0.97f);
		text(c.title(), x + 14, y + 17, 11.5f, 0xFFFFFFFF, Fonts.SEMIBOLD);
		float ty = y + 32;
		for (String line : body) {
			text(line, x + 14, ty + 5, 9, 0xC8FFFFFF, Fonts.MEDIUM);
			ty += 11;
		}
		float by = y + h - 22;
		confirmButton(x + w - 14 - 78, by, 78, c.yes(), c.yesTint(), () -> {
			c.action().run();
			confirm = null;
		});
		confirmButton(x + w - 14 - 78 - 66, by, 60, c.no(), 0x26FFFFFF, () -> confirm = null);
	}

	private void confirmButton(float x, float y, float w, String label, int tint, Runnable action) {
		boolean over = hovered(x, y, w, 15);
		float glow = hoverEase(label + x, over);
		int alpha = Math.min(255, ((tint >>> 24) & 0xFF) + Math.round(0x50 * glow));
		overlayFill(x, y, w, 15, 7.5f, (alpha << 24) | (tint & 0xFFFFFF), 0);
		overlayFill(x, y, w, 15, 7.5f, (Math.round(0x18 + 0x40 * glow) << 24) | 0xFFFFFF, 1);
		text(label, x + (w - tw(label, Fonts.SEMIBOLD, 9)) / 2, y + 8, 9, 0xFFFFFFFF, Fonts.SEMIBOLD);
		hit(x, y, w, 15, (button, mx, my) -> {
			action.run();
			return null;
		});
	}
	private void savePanel(Panel p) {
		if (p.category == null) {
			return;
		}
		config.putExtra("gui." + p.key + ".x", p.x);
		config.putExtra("gui." + p.key + ".y", p.y);
		config.putExtra("gui." + p.key + ".open", p.expanded ? 1 : 0);
	}
	public void editHud() {
		startHudEdit();
	}
	public void showcase(String action, String arg) {
		switch (action) {
			case "search" -> {
				search.setLength(0);
				search.append(arg);
			}
			case "suggest" -> {
				openSuggestion();
				appendSuggestion(arg);
			}
			case "edithud" -> startHudEdit();
			case "library" -> openLibrary();
			case "librarysorts" -> libSortOpen = true;
			case "librarysort" -> {
				libSort = arg;
				libSortOpen = false;
				refreshLibrary(false);
			}
			case "libraryopen" -> {
				for (JsonObject item : libItems) {
					if (str(item, "code").equals(arg)) {
						openConfig(item);
					}
				}
			}
			case "publish" -> {
				openPublish();
				String[] parts = arg.split("[|]", 2);
				pubTitle.append(parts[0]);
				if (parts.length > 1) {
					pubDesc.append(parts[1]);
					pubOnDesc = true;
				}
			}
			case "publishsend" -> submitPublish();
			case "picker" -> {
				String[] parts = arg.split("/", 2);
				Module m = TempleNzClient.modules().byName(parts[0]);
				if (m != null && parts.length == 2) {
					showSettings(m);
					for (Setting<?> s : m.allSettings()) {
						if (s instanceof EntitySelection sel && s.name().equals(parts[1])) {
							openPicker(sel);
						}
					}
				}
			}
			default -> {
			}
		}
	}
	public void showSettings(Module m) {
		if (settingsModule != m) {
			openSettings(m);
		}
	}
	private void openSetting(Module m, Setting<?> s) {
		if (settingsModule != m) {
			openSettings(m);
		}
		spotlight = s;
		spotlightAt = time;
		scrollToSpotlight = true;
	}
	private void openSettings(Module m) {
		if (settingsModule == m) {
			closeSettings();
			return;
		}
		if (m instanceof nz.temple.module.LazyPayloadModule lazy) {
			lazy.load();
		}
		settingsModule = m;
		setScroll = 0;
		spotlight = null;
		settingsRect = null;
		listening = null;
		dropdown = null;
		colorOpen = null;
		picker = null;
		setX = config.getExtra("gui.settings.x", (designW() - SET_W) / 2);
		setY = config.getExtra("gui.settings.y", 40);
		setX = Math.max(0, Math.min(designW() - SET_W, setX));
		setY = Math.max(0, Math.min(designH() - 60, setY));
	}
	private void closeSettings() {
		editing = null;
		settingsModule = null;
		listening = null;
		dropdown = null;
		colorOpen = null;
		picker = null;
	}
	private List<SRow> settingRows() {
		List<SRow> rows = new ArrayList<>();
		float y = 3;
		if (!settingsModule.isAlwaysOn()) {
			rows.add(new SRow(null, "header:Controls", y, HEADER_H, false));
			y += HEADER_H;
			rows.add(new SRow(null, "active", y, SROW, false));
			y += SROW;
			rows.add(new SRow(settingsModule.keybind(), null, y, SROW, false));
			y += SROW;
			if (settingsModule.disableOnKeyRelease().isVisible()) {
				rows.add(new SRow(settingsModule.disableOnKeyRelease(), null, y, SROW, false));
				y += SROW;
			}
			rows.add(new SRow(settingsModule.visible(), null, y, SROW, false));
			y += SROW;
		}
		if (nz.temple.client.security.LazyPayloadBridges.previewable(settingsModule)) {
			rows.add(new SRow(null, "preview", y, SROW, false));
			y += SROW;
		}
		String lastTitle = null;
		List<List<Setting<?>>> groups = settingsModule.groups();
		for (int gi = 0; gi < groups.size(); gi++) {
			List<Setting<?>> visible = groups.get(gi).stream().filter(Setting::isVisible).toList();
			if (visible.isEmpty()) {
				continue;
			}
			String title = settingsModule.groupTitle(gi);
			if (!title.equals(lastTitle)) {
				y += rows.isEmpty() ? 0 : 5;
				rows.add(new SRow(null, "header:" + title, y, HEADER_H, false));
				y += HEADER_H;
				lastTitle = title;
			}
			for (Setting<?> s : visible) {
				float h = SROW + (s == colorOpen ? PICKER_H : 0);
				rows.add(new SRow(s, null, y, h, false));
				y += h;
			}
		}
		setContentH = y + 4;
		return rows;
	}
	private List<String> wrap(String text, float maxW, float size) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && tw(candidate, Fonts.MEDIUM, size) > maxW) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}
	private void drawSettings() {
		Module m = settingsModule;
		List<String> desc = m.description().isEmpty() ? List.of() : wrap(m.description(), SET_W - 20, 8.5f);
		float headerH = 26 + desc.size() * 10.5f + (desc.isEmpty() ? 0 : 5);
		List<SRow> rows = settingRows();
		float bodyH = Math.min(setContentH, Math.max(60, designH() * 0.8f - headerH));
		float x = (float) setX;
		float y = (float) setY;
		float h = headerH + bodyH;
		settingsRect = new float[] {x, y, x + SET_W, y + h};
		window(x, y, SET_W, h);
		ui.glass(x, y, SET_W, h, RADIUS + 2);
		text(m.name(), x + 10, y + 13, 12, 0xFFFFFFFF, Fonts.SEMIBOLD);
		String cat = m.category().displayName();
		text(cat, x + 16 + tw(m.name(), Fonts.SEMIBOLD, 12), y + 13.5f, SMALL, 0x80FFFFFF, Fonts.MEDIUM);
		hit(x, y, SET_W, 24, (button, mx, my) -> {
			double ox = mx - setX;
			double oy = my - setY;
			return new Drag() {
				public void move(double dx, double dy) {
					setX = Math.max(20 - SET_W, Math.min(designW() - 20, dx - ox));
					setY = Math.max(0, Math.min(designH() - 24, dy - oy));
				}
				public void release() {
					config.putExtra("gui.settings.x", setX);
					config.putExtra("gui.settings.y", setY);
				}
			};
		});
		boolean closeHover = hovered(x + SET_W - 20, y + 5, 14, 16);
		closeGlyph(x + SET_W - 13, y + 13, closeHover);
		hit(x + SET_W - 20, y + 5, 14, 16, (button, mx, my) -> {
			closeSettings();
			return null;
		});
		for (int i = 0; i < desc.size(); i++) {
			text(desc.get(i), x + 10, y + 27 + i * 10.5f, 8.5f, 0xA8FFFFFF, Fonts.MEDIUM);
		}
		accentLine(x + 8, y + headerH - 2, SET_W - 16, 0);
		float bodyTop = y + headerH;
		if (scrollToSpotlight) {
			scrollToSpotlight = false;
			for (SRow row : rows) {
				if (row.setting() == spotlight) {
					setScroll = row.y() + row.h() / 2 - bodyH / 2;
				}
			}
		}
		setScroll = Math.max(0, Math.min(setScroll, setContentH - bodyH));
		clip(x, bodyTop, x + SET_W, bodyTop + bodyH);
		dropdownAnchor = null;
		for (SRow row : rows) {
			float ry = bodyTop + row.y() - setScroll;
			if (ry + row.h() < bodyTop || ry > bodyTop + bodyH) {
				continue;
			}
			if (row.separator()) {
				rect(x + 10, ry - 4, SET_W - 20, 1f / k, 0x1FFFFFFF);
			}
			if (row.special() != null && row.special().startsWith("header:")) {
				sectionHeader(row.special().substring(7), x, ry);
				continue;
			}
			if (spotlight != null && row.setting() == spotlight) {
				float glow = (float) Math.max(0, 1 - Math.max(0, time - spotlightAt - 0.9) / 1.4);
				if (glow > 0) {
					int gold = HudModule.GOLD & 0xFFFFFF;
					ui.fill(x + 4, ry, SET_W - 8, SROW, SROW / 2, (Math.round(0x2E * glow) << 24) | gold);
					ui.ring(x + 4, ry, SET_W - 8, SROW, SROW / 2, 0.75f, (Math.round(0x99 * glow) << 24) | gold);
				}
			}
			drawSettingRow(row, x, ry);
		}
		unclip();
		if (dropdown != null && dropdownAnchor == null) {
			dropdown = null;
		}
	}
	private static final float HEADER_H = 13;
	private void sectionHeader(String title, float x, float ry) {
		String caps = title.toUpperCase(java.util.Locale.ROOT);
		float size = 6.5f;
		float cy = ry + HEADER_H / 2 + 1;
		text(caps, x + 10, cy, size, (0xD0 << 24) | (HudModule.GOLD & 0xFFFFFF), Fonts.SEMIBOLD);
		float lineX = x + 10 + tw(caps, Fonts.SEMIBOLD, size) + 5;
		if (lineX < x + SET_W - 12) {
			rect(lineX, cy, x + SET_W - 10 - lineX, 1f / k, 0x1FFFFFFF);
		}
	}
	private void drawSettingRow(SRow row, float x, float ry) {
		Setting<?> s = row.setting();
		float cy = ry + SROW / 2;
		float lx = x + 10;
		float rx = x + SET_W - 10;
		String label = "preview".equals(row.special()) ? "Preview"
				: row.special() != null ? "Active" : s == settingsModule.keybind() ? "Keybind" : s.name();
		boolean rowHover = hovered(x + 4, ry, SET_W - 8, SROW);
		if (rowHover) {
			ui.fill(x + 4, ry, SET_W - 8, SROW, SROW / 2, 0x10FFFFFF);
			if (s != null) {
				hover(s, s.name(), s.description() + "  Right-click resets it.");
			}
		}
		text(ellipsize(label, Fonts.MEDIUM, TEXT, SET_W * 0.44f), lx, cy, TEXT, 0xE6FFFFFF, Fonts.MEDIUM);
		if ("preview".equals(row.special())) {
			boolean on = settingsModule.isEnabled();
			String pill = on ? "Play" : "Turn on to play";
			float bw = tw(pill, Fonts.SEMIBOLD, SMALL) + 14;
			boolean over = hovered(rx - bw, cy - 5.5f, bw, 11);
			float glow = hoverEase("preview-pill", over && on);
			ui.fill(rx - bw, cy - 5.5f, bw, 11, 5.5f, (Math.round((on ? 0.35f + 0.25f * glow : 0.12f) * 255) << 24)
					| (HudModule.GOLD & 0xFFFFFF));
			text(pill, rx - bw + 7, cy, SMALL, on ? 0xFFFFFFFF : 0x99FFFFFF, Fonts.SEMIBOLD);
			if (on) {
				icon(Icons.Icon.PLAY, rx - bw - 7, cy, 7.5f, 0xCCFFFFFF);
			}
			if (rowHover) {
				hover("preview-row", "Preview", "Plays the effect a few blocks in front of you. Close the menu to see it clearly.");
			}
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (settingsModule.isEnabled() && minecraft.player != null) {
					nz.temple.client.security.LazyPayloadBridges.preview(settingsModule,
							nz.temple.client.util.PreviewSpot.inFront(minecraft, 4));
				}
				return null;
			});
			return;
		}
		if (row.special() != null) {
			checkbox(rx - 8.5f, cy, settingsModule.isEnabled(), 0);
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				toggleWithConfirm(settingsModule);
				return null;
			});
			return;
		}
		if (s instanceof nz.temple.setting.ActionSetting act) {
			String pill = act.label();
			float bw = tw(pill, Fonts.SEMIBOLD, SMALL) + 16;
			boolean over = hovered(rx - bw, cy - 5.5f, bw, 11);
			float glow = hoverEase("action-" + System.identityHashCode(act), over);
			ui.fill(rx - bw, cy - 5.5f, bw, 11, 5.5f, (Math.round((0.34f + 0.26f * glow) * 255) << 24) | (HudModule.GOLD & 0xFFFFFF));
			text(pill, rx - bw + 8, cy, SMALL, 0xFFFFFFFF, Fonts.SEMIBOLD);
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
					act.press();
				}
				return null;
			});
		} else if (s instanceof BoolSetting b) {
			checkbox(rx - 8.5f, cy, b.get(), 0);
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					b.reset();
				} else {
					toggleWithConfirm(b);
				}
				return null;
			});
		} else if (s instanceof NumberSetting n) {
			String v = n.display();
			float valueW = 36;
			float s0 = x + SET_W * 0.46f;
			float s1 = rx - valueW - 6;
			float frac = (float) n.fraction();
			int accent = Theme.color(0);
			ui.fill(s0, cy - 1.5f, s1 - s0, 3f, 1.5f, 0x2EFFFFFF);
			ui.fill(s0, cy - 1.5f, (s1 - s0) * frac, 3f, 1.5f, 0xFF000000 | accent);
			ui.fill(s0 + (s1 - s0) * frac - 2f, cy - 4.5f, 4f, 9f, 2f, 0xFFFFFFFF);
			text(v, rx - tw(v, Fonts.SEMIBOLD, SMALL), cy, SMALL, 0xFFFFFFFF, Fonts.SEMIBOLD);
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					n.reset();
					return null;
				}
				if (mx < s0 - 6 || mx > s1 + 6) {
					return null;
				}
				slide(n, (mx - s0) / (s1 - s0));
				return (dx, dy) -> slide(n, (dx - s0) / (s1 - s0));
			});
		} else if (s instanceof ModeSetting mode) {
			float b0 = x + SET_W * 0.46f;
			float bw = rx - b0;
			ui.fill(b0, cy - 5.5f, bw, 11, 5.5f, dropdown == mode ? 0x33FFFFFF : 0x1AFFFFFF);
			text(ellipsize(mode.get(), Fonts.MEDIUM, 8.5f, bw - 16), b0 + 5, cy, 8.5f, 0xFFFFFFFF, Fonts.MEDIUM);
			triangle(rx - 6, cy);
			if (dropdown == mode) {
				dropdownAnchor = new float[] {b0, cy - 5.5f, bw, 11};
			}
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					mode.reset();
					dropdown = null;
				} else {
					dropdown = dropdown == mode ? null : mode;
				}
				return null;
			});
		} else if (s instanceof ColorSetting c) {
			int argb = c.get();
			String hex = c.followsTheme() ? "Theme" : String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
			float sw = 24;
			ui.fill(rx - sw, cy - 4.5f, sw, 9, 3f, 0x40FFFFFF);
			ui.fill(rx - sw, cy - 4.5f, sw, 9, 3f, argb);
			ui.ring(rx - sw, cy - 4.5f, sw, 9, 3f, Math.max(0.6f, 1f / k), 0x70FFFFFF);
			text(hex, rx - sw - 5 - tw(hex, Fonts.MEDIUM, SMALL), cy, SMALL, 0x99FFFFFF, Fonts.MEDIUM);
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					c.reset();
					hsv.remove(c);
				} else {
					colorOpen = colorOpen == c ? null : c;
					if (colorOpen != null) {
						hsv.remove(c);
					}
				}
				return null;
			});
			if (colorOpen == c) {
				drawColorPicker(c, x + 10, ry + SROW + 2, SET_W - 20);
			}
		} else if (s instanceof KeybindSetting kb) {
			String name = listening == kb ? "Press a key…" : KeyNames.name(kb.get());
			float bw = Math.max(40, tw(name, Fonts.SEMIBOLD, SMALL) + 12);
			float pulse = listening == kb ? (float) (0.5 - 0.5 * Math.cos(time * Math.PI * 2)) : 0;
			ui.fill(rx - bw, cy - 5.5f, bw, 11, 5.5f, Ui.white(0.1f + 0.2f * pulse));
			text(name, rx - bw + (bw - tw(name, Fonts.SEMIBOLD, SMALL)) / 2, cy, SMALL, 0xFFFFFFFF, Fonts.SEMIBOLD);
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					kb.set(KeybindSetting.NONE);
					listening = null;
				} else {
					listening = listening == kb ? null : kb;
				}
				return null;
			});
		} else if (s instanceof EntitySelection sel) {
			String v = sel.count() + " selected";
			float vx = rx - tw(v, Fonts.MEDIUM, SMALL) - 12;
			text(v, vx, cy, SMALL, 0xCCFFFFFF, Fonts.MEDIUM);
			icon(Icons.Icon.CHEVRON_RIGHT, rx - 4, cy, 8, 0xCCFFFFFF);
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					sel.reset();
				} else {
					openPicker(sel);
				}
				return null;
			});
		} else if (s instanceof FileSetting file) {
			boolean waiting = FilePicker.isOpen();
			String pill = waiting ? "Choosing…" : file.isDefault() ? "Choose…" : "Change";
			float bw = tw(pill, Fonts.SEMIBOLD, SMALL) + 12;
			boolean over = hovered(rx - bw, cy - 5.5f, bw, 11);
			float pulse = waiting ? (float) (0.5 - 0.5 * Math.cos(time * Math.PI * 2)) : 0;
			ui.fill(rx - bw, cy - 5.5f, bw, 11, 5.5f, (Math.round((over ? 0.55f : 0.34f + 0.3f * pulse) * 255) << 24)
					| (HudModule.GOLD & 0xFFFFFF));
			text(pill, rx - bw + 6, cy, SMALL, 0xFFFFFFFF, Fonts.SEMIBOLD);
			String v = file.isDefault() ? "Default" : ellipsize(file.fileName(), Fonts.MEDIUM, SMALL, SET_W * 0.5f - bw - 10);
			text(v, rx - bw - 6 - tw(v, Fonts.MEDIUM, SMALL), cy, SMALL, file.isDefault() ? 0x80FFFFFF : 0xCCFFFFFF, Fonts.MEDIUM);
			if (rowHover && !file.isDefault()) {
				hover(s, s.name(), file.get() + "  Right-click to go back to the built-in default.");
			}
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					file.reset();
				} else {
					FilePicker.choose(file);
				}
				return null;
			});
		} else if (s instanceof TextSetting text) {
			boolean active = editing == text;
			float fw = SET_W * 0.5f;
			float fx0 = rx - fw;
			ui.fill(fx0, cy - 5.5f, fw, 11, 5.5f, active ? 0x33FFFFFF : 0x1AFFFFFF);
			if (active) {
				ui.ring(fx0, cy - 5.5f, fw, 11, 5.5f, Math.max(0.6f, 1f / k), (0xB0 << 24) | (HudModule.GOLD & 0xFFFFFF));
			}
			String value = text.get();
			boolean empty = value.isEmpty();
			String shown = empty ? text.placeholder() : ellipsize(value, Fonts.MEDIUM, SMALL, fw - 12);
			text(shown, fx0 + 6, cy, SMALL, empty ? 0x70FFFFFF : 0xFFFFFFFF, Fonts.MEDIUM);
			if (active && (time % 1.0) < 0.55) {
				rect(fx0 + 6 + (empty ? 0 : tw(shown, Fonts.MEDIUM, SMALL)) + 0.5f, cy - 4, 1f / k * 1.2f, 8, 0xE6FFFFFF);
			}
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					text.reset();
					editing = null;
				} else {
					editing = active ? null : text;
				}
				return null;
			});
		} else if (s instanceof NamespaceListSetting ns) {
			String v = ellipsize(ns.describeValue(), Fonts.MEDIUM, SMALL, SET_W * 0.48f);
			text(v, rx - tw(v, Fonts.MEDIUM, SMALL), cy, SMALL, 0x99FFFFFF, Fonts.MEDIUM);
			hit(x + 4, ry, SET_W - 8, SROW, (button, mx, my) -> {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					ns.reset();
				}
				return null;
			});
		}
	}
	private float[] hsvOf(ColorSetting c) {
		return hsv.computeIfAbsent(c, key -> {
			int argb = key.get();
			float[] v = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
			return new float[] {v[0], v[1], v[2], ((argb >>> 24) & 0xFF) / 255f};
		});
	}
	private static int hsb(float h, float s, float b) {
		return 0xFF000000 | (Color.HSBtoRGB(h, s, b) & 0xFFFFFF);
	}
	private void applyHsv(ColorSetting c, float[] v) {
		c.set((Math.round(v[3] * 255) << 24) | (hsb(v[0], v[1], v[2]) & 0xFFFFFF));
	}
	private void drawColorPicker(ColorSetting c, float px, float py, float w) {
		float[] v = hsvOf(c);
		float svW = w - 26;
		float svH = 40;
		int columns = 24;
		for (int i = 0; i < columns; i++) {
			float sat = i / (columns - 1f);
			vgrad(px + svW * i / columns, py, svW / columns + 0.8f / k, svH, hsb(v[0], sat, 1), 0xFF000000);
		}
		float markX = px + svW * v[1];
		float markY = py + svH * (1 - v[2]);
		outline(markX - 2, markY - 2, 4, 4, 0xFFFFFFFF);
		float hx = px + svW + 5;
		int bands = 24;
		for (int i = 0; i < bands; i++) {
			vgrad(hx, py + svH * i / bands, 8, svH / bands + 0.8f / k, hsb((float) i / bands, 1, 1), hsb((float) (i + 1) / bands, 1, 1));
		}
		rect(hx - 1, py + svH * v[0] - 0.75f, 10, 1.5f, 0xFFFFFFFF);
		float ax = px + w - 8;
		rect(ax, py, 8, svH, 0x33FFFFFF);
		int rgb = c.get() & 0xFFFFFF;
		vgrad(ax, py, 8, svH, 0xFF000000 | rgb, rgb);
		rect(ax - 1, py + svH * (1 - v[3]) - 0.75f, 10, 1.5f, 0xFFFFFFFF);
		hit(px, py, svW, svH, (button, mx, my) -> {
			Drag d = (dx, dy) -> {
				v[1] = clamp01((float) ((dx - px) / svW));
				v[2] = 1 - clamp01((float) ((dy - py) / svH));
				applyHsv(c, v);
				detent(v[1] * 0.5 + v[2] * 0.5, (int) (v[1] * 16) * 17 + (int) (v[2] * 16));
			};
			d.move(mx, my);
			return d;
		});
		hit(hx - 1, py, 10, svH, (button, mx, my) -> {
			Drag d = (dx, dy) -> {
				v[0] = clamp01((float) ((dy - py) / svH)) * 0.999f;
				applyHsv(c, v);
				detent(1 - v[0], (int) (v[0] * 32));
			};
			d.move(mx, my);
			return d;
		});
		hit(ax - 1, py, 10, svH, (button, mx, my) -> {
			Drag d = (dx, dy) -> {
				v[3] = 1 - clamp01((float) ((dy - py) / svH));
				applyHsv(c, v);
				detent(v[3], (int) (v[3] * 32));
			};
			d.move(mx, my);
			return d;
		});
		float sy = py + svH + 6;
		for (int i = 0; i < ColorSetting.SWATCHES.length; i++) {
			int swatch = ColorSetting.SWATCHES[i];
			float sx = px + i * 15;
			rect(sx, sy, 11, 10, swatch);
			if ((swatch & 0xFFFFFF) == (c.get() & 0xFFFFFF)) {
				outline(sx - 1, sy - 1, 13, 12, 0xFFFFFFFF);
			}
			hit(sx, sy, 11, 10, (button, mx, my) -> {
				c.set(swatch);
				hsv.remove(c);
				return null;
			});
		}
		String alpha = Math.round(v[3] * 100) + "% opacity";
		text(alpha, px + w - tw(alpha, Fonts.MEDIUM, 7.5f), sy + 5, 7.5f, 0x99FFFFFF, Fonts.MEDIUM);
	}
	private void drawDropdown() {
		if (dropdownAnchor == null) {
			return;
		}
		float[] a = dropdownAnchor;
		List<String> options = dropdown.options();
		float optH = 12;
		float h = options.size() * optH + 4;
		float x = a[0];
		float y = a[1] + a[3] + 1;
		if (y + h > designH() - 4) {
			y = a[1] - h - 1;
		}
		hit(-10000, -10000, 20000, 20000, (button, mx, my) -> {
			dropdown = null;
			return null;
		});
		goldPanel(x, y, a[2], h, 0.96f);
		for (int i = 0; i < options.size(); i++) {
			String option = options.get(i);
			float oy = y + 2 + i * optH;
			boolean selected = dropdown.is(option);
			if (selected) {
				rect(x + 2, oy, a[2] - 4, optH, (0x40 << 24) | (Theme.color(i * 0.05) & 0xFFFFFF));
			}
			if (hovered(x + 2, oy, a[2] - 4, optH)) {
				rect(x + 2, oy, a[2] - 4, optH, 0x1CFFFFFF);
			}
			text(option, x + 6, oy + optH / 2, 8.5f, selected ? 0xFFFFFFFF : 0xC8FFFFFF, Fonts.MEDIUM);
			ModeSetting mode = dropdown;
			hit(x + 2, oy, a[2] - 4, optH, (button, mx, my) -> {
				mode.set(option);
				dropdown = null;
				return null;
			});
		}
	}
	private void openPicker(EntitySelection sel) {
		picker = sel;
		pickerTerm.setLength(0);
		pickerScroll = 0;
		pickX = setX + SET_W + 8 + PICK_W > designW() ? setX - PICK_W - 8 : setX + SET_W + 8;
		pickY = setY;
	}
	private List<EntityCatalog.Entry> pickerResults() {
		String t = pickerTerm.toString().trim().toLowerCase(Locale.ROOT);
		List<EntityCatalog.Entry> out = new ArrayList<>();
		for (EntityCatalog.Entry e : picker.isBlocks() ? nz.temple.client.util.BlockCatalog.entries()
				: picker.isItems() ? nz.temple.client.util.ItemCatalog.entries() : EntityCatalog.entries()) {
			if (t.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(t) || e.id().contains(t)) {
				out.add(e);
			}
		}
		return out;
	}
	private void drawPicker() {
		List<EntityCatalog.Entry> results = pickerResults();
		float x = (float) pickX;
		float y = (float) pickY;
		float headerH = 44;
		float rowH = 13;
		float listH = Math.min(results.size() * rowH + 4, Math.max(60, designH() * 0.7f));
		float h = headerH + listH;
		pickerRect = new float[] {x, y, x + PICK_W, y + h};
		window(x, y, PICK_W, h);
		ui.glass(x, y, PICK_W, h, RADIUS);
		String title = picker.isBlocks() ? "Blocks" : picker.isItems() ? "Items" : "Entities";
		text(title, x + 10, y + 12, 11, 0xFFFFFFFF, Fonts.SEMIBOLD);
		text(picker.count() + " selected", x + 16 + tw(title, Fonts.SEMIBOLD, 11), y + 12.5f, SMALL, 0x99FFFFFF, Fonts.MEDIUM);
		closeGlyph(x + PICK_W - 12, y + 12, hovered(x + PICK_W - 19, y + 4, 14, 16));
		hit(x, y, PICK_W, 22, (button, mx, my) -> {
			double ox = mx - pickX;
			double oy = my - pickY;
			return (dx, dy) -> {
				pickX = Math.max(20 - PICK_W, Math.min(designW() - 20, dx - ox));
				pickY = Math.max(0, Math.min(designH() - 22, dy - oy));
			};
		});
		hit(x + PICK_W - 19, y + 4, 14, 16, (button, mx, my) -> {
			picker = null;
			return null;
		});
		String term = pickerTerm.toString();
		ui.fill(x + 8, y + 24, PICK_W - 76, 13, 6.5f, 0x1AFFFFFF);
		text(term.isEmpty() ? "Type to filter" : term, x + 12, y + 30.5f, SMALL, term.isEmpty() ? 0x80FFFFFF : 0xFFFFFFFF, Fonts.MEDIUM);
		if (editing == null && (time % 1.0) < 0.55) {
			rect(x + 12 + (term.isEmpty() ? -1 : tw(term, Fonts.MEDIUM, SMALL) + 0.5f), y + 30.5f - 4, 1f / k * 1.2f, 8, 0xE6FFFFFF);
		}
		button(x + PICK_W - 64, y + 24, 26, "All", () -> {
			List<String> merged = new ArrayList<>(picker.get());
			results.forEach(e -> merged.add(e.id()));
			picker.setAll(merged);
		});
		button(x + PICK_W - 34, y + 24, 26, "None", picker::clear);
		accentLine(x + 8, y + headerH - 2, PICK_W - 16, 0.3);
		float top = y + headerH;
		pickerScroll = Math.max(0, Math.min(pickerScroll, results.size() * rowH + 4 - listH));
		clip(x, top, x + PICK_W, top + listH);
		for (int i = 0; i < results.size(); i++) {
			float ry = top + 2 + i * rowH - pickerScroll;
			if (ry + rowH < top || ry > top + listH) {
				continue;
			}
			EntityCatalog.Entry e = results.get(i);
			boolean on = picker.has(e.id());
			if (hovered(x + 4, ry, PICK_W - 8, rowH)) {
				ui.fill(x + 4, ry, PICK_W - 8, rowH, rowH / 2, 0x12FFFFFF);
			}
			checkbox(x + 9, ry + rowH / 2, on, i * 0.02);
			float nameX = x + 23;
			if (picker.isItems() || picker.isBlocks()) {
				itemIcon(picker.isBlocks() ? nz.temple.client.util.BlockCatalog.icon(e.id()) : nz.temple.client.util.ItemCatalog.stack(e.id()),
						x + 22, ry + 1.5f, rowH - 3);
				nameX += rowH;
			} else {
				mobHead(e.id(), x + 22, ry + 1.5f, rowH - 3);
				nameX += rowH;
			}
			text(ellipsize(e.name(), Fonts.MEDIUM, 8.5f, PICK_W - 80), nameX, ry + rowH / 2, 8.5f, on ? 0xFFFFFFFF : 0xB8FFFFFF, Fonts.MEDIUM);
			String cat = e.category();
			text(cat, x + PICK_W - 10 - tw(cat, Fonts.MEDIUM, 7), ry + rowH / 2, 7, 0x70FFFFFF, Fonts.MEDIUM);
			hit(x + 4, ry, PICK_W - 8, rowH, (button, mx, my) -> {
				picker.toggle(e.id());
				return null;
			});
		}
		unclip();
	}
	private void itemIcon(net.minecraft.world.item.ItemStack stack, float x, float y, float size) {
		if (stack.isEmpty() || g == null) {
			return;
		}
		drawVisible(x, y, x + size, y + size, () -> {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ui.mapX(x), ui.mapY(y));
			float scale = size * k / 16f;
			pose.scale(scale, scale);
			g.item(stack, 0, 0);
			pose.popMatrix();
		});
	}
	private void mobHead(String id, float x, float y, float size) {
		if (g == null) {
			return;
		}
		drawVisible(x, y, x + size, y + size, () -> {
			float px = ui.mapX(x);
			float py = ui.mapY(y);
			float side = size * k;
			MobHeads.draw(g, id, px, py, px + side, py + side);
		});
	}

	private void button(float x, float y, float w, String label, Runnable action) {
		ui.fill(x, y, w, 13, 6.5f, hovered(x, y, w, 13) ? 0x33FFFFFF : 0x1FFFFFFF);
		text(label, x + (w - tw(label, Fonts.SEMIBOLD, 7.5f)) / 2, y + 6.5f, 7.5f, 0xFFFFFFFF, Fonts.SEMIBOLD);
		hit(x, y, w, 13, (button, mx, my) -> {
			action.run();
			return null;
		});
	}
	public boolean isEditingHud() {
		return editHud;
	}
	private void startHudEdit() {
		dropdown = null;
		listening = null;
		editing = null;
		if (minecraft.level == null || minecraft.player == null) {
			if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info("Edit HUD", "join a world first, the HUD is only drawn in game");
			}
			return;
		}
		minecraft.execute(() -> minecraft.gui.setScreen(new HudEditorScreen(this)));
	}
	private void exitHudEdit() {
		editHud = false;
	}
	private void drawHudToolbar(float edit) {
		String message = "Drag widgets to move them, scroll over one to resize it";
		float messageW = tw(message, Fonts.MEDIUM, 8.5f);
		float doneW = toolbarButtonWidth("Done");
		float resetW = toolbarButtonWidth("Reset");
		float h = 22;
		float w = 21 + messageW + 12 + resetW + 5 + doneW + 6;
		float x = (designW() - w) / 2;
		float y = designH() * 0.14f - (1 - edit) * 16;
		float prev = ui.alpha();
		ui.alpha(prev * edit);
		goldPanel(x, y, w, h, 0.96f * edit);
		icon(Icons.Icon.MOVE, x + 12, y + h / 2, 9, 0xFFFFE3A1);
		text(message, x + 21, y + h / 2, 8.5f, 0xFFFFFFFF, Fonts.MEDIUM);
		if (editHud) {
			toolbarButton(x + w - 6 - doneW, y + 3.5f, doneW, Icons.Icon.CHECK, "Done", 0x40E9B949, this::exitHudEdit);
			toolbarButton(x + w - 6 - doneW - 5 - resetW, y + 3.5f, resetW, Icons.Icon.RESET, "Reset", 0x26FFFFFF, () -> {
				if (TempleNzClient.hud() != null) {
					TempleNzClient.hud().resetLayout();
				}
			});
		}
		ui.alpha(prev);
	}
	private float toolbarButtonWidth(String label) {
		return 16 + tw(label, Fonts.SEMIBOLD, 8) + 8;
	}
	private void toolbarButton(float x, float y, float w, Icons.Icon glyph, String label, int tint, Runnable action) {
		boolean over = hovered(x, y, w, 15);
		float glow = hoverEase("tb" + label, over);
		int alpha = Math.min(255, ((tint >>> 24) & 0xFF) + Math.round(0x50 * glow));
		overlayFill(x, y, w, 15, 7.5f, (alpha << 24) | (tint & 0xFFFFFF), 0);
		icon(glyph, x + 9, y + 7.5f, 7.5f, 0xFFFFFFFF);
		text(label, x + 16, y + 8, 8, 0xFFFFFFFF, Fonts.SEMIBOLD);
		hit(x, y, w, 15, (button, mx, my) -> {
			action.run();
			return null;
		});
	}
	private void drawTooltip() {
		if (hoverKey != shownKey) {
			shownKey = hoverKey;
			hoverSince = time;
			tipX = Float.MIN_VALUE;
		}
		if (hoverKey == null || hoverText == null || hoverText.isBlank() || time - hoverSince < 0.5 || activeDrag != null || dropdown != null) {
			return;
		}
		float maxW = 170;
		List<String> lines = wrap(hoverText.trim(), maxW, 8);
		float w = tw(hoverTitle, Fonts.SEMIBOLD, 9);
		for (String l : lines) {
			w = Math.max(w, tw(l, Fonts.MEDIUM, 8));
		}
		w += 14;
		float h = 18 + lines.size() * 10;
		float wantX = Float.isNaN(tipAnchorX) ? (float) mouseX + 10 : tipAnchorX - w;
		float wantY = Float.isNaN(tipAnchorX) ? (float) mouseY + 12 : tipAnchorY;
		if (wantX + w > designW() - 4) {
			wantX = (float) mouseX - w - 8;
		}
		if (wantY + h > designH() - 4) {
			wantY = (float) mouseY - h - 6;
		}
		if (tipX == Float.MIN_VALUE || time - tipSeen > 0.25) {
			tipX = wantX;
			tipY = wantY;
			tipBorn = time;
		}
		float follow = 1 - (float) Math.exp(-7.0 * Math.min(0.1, time - tipSeen));
		tipSeen = time;
		tipX += (wantX - tipX) * follow;
		tipY += (wantY - tipY) * follow;
		float age = (float) (time - tipBorn);
		float t = Math.min(1, age / 0.22f);
		float ease = 1 - (1 - t) * (1 - t) * (1 - t);
		float swell = 0.965f + 0.035f * ease;
		float alpha = Math.min(1, 0.72f + 0.28f * ease);
		float cx = tipX + w / 2;
		float cy = tipY + h / 2;
		float x = cx - w * swell / 2;
		float y = cy - h * swell / 2;
		goldPanel(x, y, w * swell, h * swell, 0.96f * alpha);
		text(hoverTitle, tipX + 7, tipY + 9, 9, Ui.white(alpha), Fonts.SEMIBOLD);
		for (int i = 0; i < lines.size(); i++) {
			text(lines.get(i), tipX + 7, tipY + 19 + i * 10, 8,
					(Math.round(0xCC * alpha) << 24) | 0xFFFFFF, Fonts.MEDIUM);
		}
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (closing) {
			return true;
		}

		if (listening != null && KeyNames.bindable(e.button())) {
			listening.set(KeyNames.mouse(e.button()));
			listening = null;
			return true;
		}
		double mx = (e.x() - tx) / k;
		double my = (e.y() - ty) / k;
		searchFocused = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(mx, my)) {
				activeDrag = hit.handler().press(e.button(), mx, my);
				if (activeDrag == null) {
					nz.temple.client.audio.UiSounds.tap();
				}
				return true;
			}
		}
		listening = null;
		if (editHud && TempleNzClient.hud() != null && e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && TempleNzClient.hud().press(e.x(), e.y())) {
			draggingHud = true;
			return true;
		}
		if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && settingsModule != null && !editHud && !inside(settingsRect, mx, my)
				&& !(picker != null && inside(pickerRect, mx, my))) {
			closeSettings();
		}
		return true;
	}
	private static boolean inside(float[] r, double x, double y) {
		return r != null && x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (draggingHud) {
			TempleNzClient.hud().drag(e.x(), e.y());
		} else if (activeDrag != null) {
			activeDrag.move((e.x() - tx) / k, (e.y() - ty) / k);
		}
		return true;
	}
	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (draggingHud) {
			TempleNzClient.hud().release();
			draggingHud = false;
		}
		if (activeDrag != null) {
			activeDrag.release();
			activeDrag = null;
		}
		return true;
	}
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		double mx = (x - tx) / k;
		double my = (y - ty) / k;
		float amount = (float) (-scrollY * 30);
		if (editHud) {
			if (TempleNzClient.hud() != null && scrollY != 0) {
				TempleNzClient.hud().scroll(x, y, scrollY);
			}
			return true;
		}
		if (share == Share.LIBRARY && importDone == null) {
			if (libOpen != null) {
				libDetailScroll += amount;
			} else {
				libScroll += amount;
			}
			return true;
		}
		if (picker != null && mx >= pickX && mx <= pickX + PICK_W && my >= pickY) {
			pickerScroll += amount;
			return true;
		}
		if (settingsModule != null && mx >= setX && mx <= setX + SET_W && my >= setY) {
			setScroll += amount;
			return true;
		}
		if (!search.isEmpty() && mx >= searchPanel.x && mx <= searchPanel.x + WIN_W && my >= searchPanel.y) {
			searchPanel.scroll += amount;
			return true;
		}
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel p = panels.get(i);
			if (mx >= p.x && mx <= p.x + WIN_W && my >= p.y) {
				p.scroll += amount;
				return true;
			}
		}
		return true;
	}
	@Override
	public boolean keyPressed(KeyEvent e) {
		int key = e.key();
		boolean ctrl = (e.modifiers() & InputConstants.MOD_CONTROL) != 0;
		swallowChar = false;
		if (listening != null) {
			swallowChar = true;
			if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
				listening.set(KeybindSetting.NONE);
			} else if (!nz.temple.client.TempleNzClient.isMenuKey(e)) {
				listening.set(key);
			}
			listening = null;
			return true;
		}
		if (editing != null) {
			if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				editing = null;
			} else if (key == InputConstants.KEY_BACKSPACE && !editing.get().isEmpty()) {
				editing.set(ctrl ? "" : editing.get().substring(0, editing.get().length() - 1));
			} else if (ctrl && key == InputConstants.KEY_V) {
				String clip = minecraft.keyboardHandler.getClipboard();
				if (clip != null) {
					editing.set(editing.get() + clip.trim());
				}
			}
			return true;
		}
		if (themes) {
			if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN) {
				themes = false;
			}
			return true;
		}
		if (suggestionIntro && suggestion == null) {
			if (key == InputConstants.KEY_ESCAPE) {
				suggestionIntro = false;
			} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				suggestionIntro = false;
				openSuggestion();
			}
			return true;
		}
		if (suggestion != null) {
			boolean typing = !suggestionSent && !suggestionSending;
			if (key == InputConstants.KEY_ESCAPE) {
				suggestion = null;
			} else if (suggestionSent && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
				suggestion = null;
			} else if (typing && ctrl && key == InputConstants.KEY_V) {
				String clip = minecraft.keyboardHandler.getClipboard();
				if (clip != null) {
					appendSuggestion(clip);
				}
			} else if (typing && key == InputConstants.KEY_BACKSPACE && !suggestion.isEmpty()) {
				suggestion.setLength(ctrl ? 0 : suggestion.length() - 1);
				suggestionStatus = null;
			} else if (typing && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
				if ((e.modifiers() & InputConstants.MOD_SHIFT) != 0) {
					appendSuggestion("\n");
				} else {
					submitSuggestion();
				}
			}
			return true;
		}
		if (share != Share.NONE) {
			shareKey(key, ctrl, (e.modifiers() & InputConstants.MOD_SHIFT) != 0);
			return true;
		}
		if (ctrl && (key == InputConstants.KEY_Z || key == InputConstants.KEY_Y) && confirm == null && suggestion == null) {
			boolean redo = key == InputConstants.KEY_Y || (e.modifiers() & InputConstants.MOD_SHIFT) != 0;
			String label = redo ? nz.temple.setting.SettingHistory.redo() : nz.temple.setting.SettingHistory.undo();
			hsv.clear();
			dropdown = null;
			if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info(redo ? "Redo" : "Undo", label == null ? "nothing to " + (redo ? "redo" : "undo") : label);
			}
			return true;
		}
		if (editHud) {
			if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
					|| nz.temple.client.TempleNzClient.isMenuKey(e)) {
				exitHudEdit();
			} else if (TempleNzClient.hud() != null && (key == InputConstants.KEY_EQUALS || key == InputConstants.KEY_ADD
					|| key == InputConstants.KEY_MINUS || key == nz.temple.client.compat.Input.KEY_NUMPAD_SUBTRACT)) {
				double scale = minecraft.getWindow().getGuiScale();
				String id = TempleNzClient.hud().widgetAt(minecraft.mouseHandler.xpos() / scale, minecraft.mouseHandler.ypos() / scale);
				if (id != null) {
					TempleNzClient.hud().resize(id, key == InputConstants.KEY_MINUS || key == nz.temple.client.compat.Input.KEY_NUMPAD_SUBTRACT ? -0.1f : 0.1f);
				}
			}
			return true;
		}
		if (nz.temple.client.TempleNzClient.isMenuKey(e)) {
			close();
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			if (confirm != null) {
				confirm = null;
			} else if (dropdown != null) {
				dropdown = null;
			} else if (colorOpen != null) {
				colorOpen = null;
			} else if (picker != null) {
				picker = null;
			} else if (settingsModule != null) {
				closeSettings();
			} else if (!search.isEmpty()) {
				search.setLength(0);
			} else {
				close();
			}
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			StringBuilder target = picker != null ? pickerTerm : search;
			if (!target.isEmpty()) {
				target.setLength(ctrl ? 0 : target.length() - 1);
			}
			return true;
		}
		if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && picker == null && !search.isEmpty()) {
			List<Module> found = modules.search(search.toString()).stream().filter(HvhTab::listed).toList();
			if (!found.isEmpty()) {
				Module first = found.get(0);
				if (first.isAlwaysOn() || ctrl) {
					openSettings(first);
				} else {
					first.toggle();
				}
			} else {
				List<nz.temple.module.ModuleManager.SettingMatch> settings = modules.searchSettings(search.toString());
				if (!settings.isEmpty()) {
					openSetting(settings.get(0).module(), settings.get(0).setting());
				}
			}
			return true;
		}
		return true;
	}
	@Override
	public boolean charTyped(CharacterEvent e) {
		if (!e.isAllowedChatCharacter() || listening != null || editHud) {
			return true;
		}
		if (swallowChar) {
			swallowChar = false;
			return true;
		}
		if (editing != null) {
			editing.set(editing.get() + e.codepointAsString());
			return true;
		}
		if (suggestion != null) {
			if (!suggestionSent && !suggestionSending) {
				appendSuggestion(e.codepointAsString());
			}
			return true;
		}
		if (share != Share.NONE) {
			shareChar(e.codepointAsString());
			return true;
		}
		if (picker != null) {
			pickerTerm.append(e.codepointAsString());
			pickerScroll = 0;
		} else {
			search.append(e.codepointAsString());
			searchPanel.scroll = 0;
		}
		return true;
	}
	private static float clamp01(float v) {
		return Math.max(0, Math.min(1, v));
	}
}
