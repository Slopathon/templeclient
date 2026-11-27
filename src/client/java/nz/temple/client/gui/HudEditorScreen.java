package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import nz.temple.client.TempleNzClient;
import nz.temple.client.audio.UiSounds;
import nz.temple.client.hud.HudOverlay;
import nz.temple.client.hud.HudTheme;
import nz.temple.client.hud.HudWidgets;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.GlassRenderer;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Ui;

/**
 * HUD editor: drag elements to move them (they snap to the middle of the screen), scroll over one to resize it, and right-click
 * one for its settings: size, opacity, its own options, reset position and hide. Sizes are in HUD units (1/540 of the
 * screen height).
 */
public final class HudEditorScreen extends Screen {
	private static final float BUTTON_H = 30;
	private static final float RESET_W = 112;
	private static final float DONE_W = 92;

	private final Screen parent;
	private boolean dragging;
	private boolean itemDragging;
	private float u = 1;
	private int lastMouseX;
	private int lastMouseY;
	private float open;
	private long lastNanos = System.nanoTime();
	private float resetX;
	private float doneX;
	private float buttonY;
	private float resetLit;
	private float doneLit;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** No blur: the HUD being edited has to stay visible as it is in game. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private static HudOverlay hud() {
		return TempleNzClient.hud();
	}

	private float mx() {
		return lastMouseX / u;
	}

	private float my() {
		return lastMouseY / u;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		long now = System.nanoTime();
		open = Math.min(1, open + Math.min(0.1f, (now - lastNanos) / 1e9f) / 0.3f);
		lastNanos = now;
		float a = 1 - (1 - open) * (1 - open) * (1 - open);
		g.fill(0, 0, width, height, (Math.round(0x50 * a) << 24) | 0x040C1E);
	}

	/** The hint and the buttons, drawn by the HUD at the end of its pass so they sit above every element. */
	public void drawOnTop(GuiGraphicsExtractor g) {
		Ui ui = TempleNzClient.ui();
		if (ui == null || hud() == null) {
			return;
		}
		var window = minecraft.getWindow();
		u = (float) (window.getHeight() / 540.0 / window.getGuiScale());
		float a = 1 - (1 - open) * (1 - open) * (1 - open);
		float dw = width / u;
		float dh = height / u;
		float total = RESET_W + DONE_W + 10;
		resetX = (dw - total) / 2;
		doneX = resetX + RESET_W + 10;
		buttonY = dh - 24 - BUTTON_H - (1 - a) * 20;
		resetLit += ((over(resetX, RESET_W) ? 1 : 0) - resetLit) * 0.25f;
		doneLit += ((over(doneX, DONE_W) ? 1 : 0) - doneLit) * 0.25f;

		ui.begin(g);
		ui.transform(0, 0, u, u);
		ui.alpha(a);
		GlassRenderer glass = TempleNzClient.glass();
		boolean overlay = glass != null && glass.available() && glass.hasOverlayRoom(2, 6);
		visTop = -1e9f;
		visBottom = 1e9f;
		if (overlay) {
			float r = BUTTON_H / 2 * u;
			glass.overlayGlass(resetX * u, buttonY * u, RESET_W * u, BUTTON_H * u, r, a);
			glass.overlayRect(resetX * u, buttonY * u, RESET_W * u, BUTTON_H * u, r,
					HudTheme.alpha(0xFF0A1424, (0.45f + 0.15f * resetLit) * a), 0, 0);
			glass.overlayRect(resetX * u, buttonY * u, RESET_W * u, BUTTON_H * u, r, HudTheme.alpha(0xFFFFFFFF, 0.18f * a), u, 0);
			glass.overlayGradient(doneX * u, buttonY * u, DONE_W * u, BUTTON_H * u, r,
					HudTheme.alpha(HudTheme.accent(), (0.85f + 0.15f * doneLit) * a), HudTheme.alpha(HudTheme.deep(), (0.85f + 0.15f * doneLit) * a), 3, 0);
			glass.overlayRect(doneX * u, buttonY * u, DONE_W * u, BUTTON_H * u, r, HudTheme.alpha(0xFFFFFFFF, 0.3f * a), u, 0);
			shapePass = true;
			drawPanel(ui, g);
			shapePass = false;
			glass.placeOverlayLayer(g, 0, 0, width, height);
		}
		g.nextStratum();
		String hint = "Drag elements to move them. Right-click one for its settings, or empty space to add elements.";
		ui.textMiddle(hint, (dw - ui.textWidth(hint, Fonts.MEDIUM, 11)) / 2, buttonY - 16, 11, HudTheme.alpha(HudTheme.TEXT, 0.85f), Fonts.MEDIUM);
		label(ui, g, resetX, RESET_W, "Reset", Icons.Icon.RESET);
		label(ui, g, doneX, DONE_W, "Done", Icons.Icon.CHECK);
		drawPanel(ui, g);
		ui.alpha(1);
		ui.resetTransform();
	}

	private void label(Ui ui, GuiGraphicsExtractor g, float x, float w, String text, Icons.Icon icon) {
		float lw = ui.textWidth(text, Fonts.SEMIBOLD, 11.5f);
		float start = x + (w - lw - 20) / 2;
		float cy = buttonY + BUTTON_H / 2;
		Icons.draw(g, icon, ui.mapX(start + 7), ui.mapY(cy), 14 * u, HudTheme.alpha(0xFFFFFFFF, ui.alpha()));
		ui.textMiddle(text, start + 20, cy, 11.5f, 0xFFFFFFFF, Fonts.SEMIBOLD);
	}

	private boolean over(float x, float w) {
		return mx() >= x && mx() <= x + w && my() >= buttonY && my() <= buttonY + BUTTON_H;
	}

	/** Rectangles of elements that are on (the HUD keeps the last spot of ones that were turned off). */
	private Map<String, float[]> shownRects() {
		Map<String, float[]> rects = hud().widgetRects();
		rects.keySet().removeIf(id -> {
			HudWidgets.Widget w = HudWidgets.byId(id);
			return w != null && !w.shown();
		});
		return rects;
	}

	private String widgetAt(double mx, double my) {
		String found = null;
		for (Map.Entry<String, float[]> e : shownRects().entrySet()) {
			float[] r = e.getValue();
			float pad = 4 * u;
			if (mx >= r[0] - pad && my >= r[1] - pad && mx <= r[0] + r[2] + pad && my <= r[1] + r[3] + pad) {
				found = e.getKey();
			}
		}
		return found;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		lastMouseX = (int) e.x();
		lastMouseY = (int) e.y();
		if (panelId != null) {
			if (panelClick(e.button(), mx(), my())) {
				return true;
			}
			closePanel();
			if (e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				return true;
			}
		}
		if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			String id = widgetAt(e.x(), e.y());
			if (id == null && (hud().bar().onGrip(e.x(), e.y()) || hud().bar().itemAt(e.x(), e.y()) != null)) {
				id = "watermark";
			}
			openPanel(id != null ? id : ELEMENTS, mx(), my());
			return true;
		}
		if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
		}
		if (over(resetX, RESET_W)) {
			UiSounds.tap();
			hud().resetLayout();
			return true;
		}
		if (over(doneX, DONE_W)) {
			UiSounds.tap();
			onClose();
			return true;
		}
		var bar = hud().bar();
		if (bar.onGrip(e.x(), e.y())) {
			hud().pressWidget("watermark", e.x(), e.y());
			dragging = true;
			return true;
		}
		var item = bar.itemAt(e.x(), e.y());
		if (item != null) {
			bar.beginItemDrag(item, e.x(), e.y());
			itemDragging = true;
			UiSounds.tap();
			return true;
		}
		if (widgetAt(e.x(), e.y()) != null && hud().press(e.x(), e.y())) {
			dragging = true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (sliding != null) {
			sliding.accept(Math.max(0, Math.min(1, ((float) e.x() / u - slideX) / slideW)));
			return true;
		}
		if (itemDragging) {
			hud().bar().dragItem(e.x(), e.y());
		} else if (dragging) {
			hud().drag(e.x(), e.y());
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		sliding = null;
		if (itemDragging) {
			hud().bar().dropItem(e.x(), e.y());
			itemDragging = false;
			UiSounds.soft();
		}
		if (dragging) {
			hud().release();
			dragging = false;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (panelId != null && panelHits.size() > 0) {
			PanelHit body = panelHits.get(0);
			if (x / u >= body.x0() && x / u <= body.x1() && y / u >= body.y0() && y / u <= body.y1()) {
				panelScroll -= (float) scrollY * 24;
				return true;
			}
		}
		String id = widgetAt(x, y);
		if (id != null && scrollY != 0) {
			hud().resize(id, scrollY > 0 ? 0.1f : -0.1f);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_ESCAPE) {
			if (panelId != null) {
				closePanel();
				return true;
			}
			onClose();
			return true;
		}
		return super.keyPressed(e);
	}
	// ---------------------------------------------------------------- right-click settings panel

	private static final float PANEL_W = 214;
	private static final float PANEL_PAD = 10;
	private static final java.util.Map<String, String> MODULE_OF = java.util.Map.ofEntries(
			java.util.Map.entry("watermark", "Info Bar"), java.util.Map.entry("modules", "ArrayList"), java.util.Map.entry("target", "Target HUD"),
			java.util.Map.entry("keybinds", "Keybinds"), java.util.Map.entry("potions", "Effects List"), java.util.Map.entry("armor", "Armor HUD"),
			java.util.Map.entry("lyrics", "Lyrics"), java.util.Map.entry("keystrokes", "Keystrokes"), java.util.Map.entry("info", "Coordinates"),
			java.util.Map.entry("speedgraph", "Speed Graph"), java.util.Map.entry("elytra", "Elytra HUD"), java.util.Map.entry("enemies", "Enemy Armor"),
			java.util.Map.entry("minimap", "Map"), java.util.Map.entry("music", "Music"), java.util.Map.entry("blockinfo", "Block Highlight"),
			java.util.Map.entry("staff", "Staff Display"), java.util.Map.entry("region", "Donut Region"));
	private static final int[] PANEL_SWATCHES = {0xFFE5484D, 0xFFF76B15, 0xFFFFD60A, 0xFF30A46C, 0xFF5AC8FA, 0xFF3358D4, 0xFF8E4EC6, 0xFFFFFFFF};

	private interface PanelClick {
		void run(int button, float x, float y);
	}

	private record PanelHit(float x0, float y0, float x1, float y1, PanelClick click) {
	}

	/** The panel id for empty space: the list of elements. */
	private static final String ELEMENTS = "__elements";
	private String panelId;
	private float panelX;
	private float panelY;
	private float panelScroll;
	private float panelHeight;
	private float panelOpen;
	private final java.util.List<PanelHit> panelHits = new java.util.ArrayList<>();
	private java.util.function.Consumer<Float> sliding;
	private float slideX;
	private float slideW;
	private final java.util.Map<String, Float> toggles = new java.util.HashMap<>();

	private void openPanel(String id, float x, float y) {
		panelId = id;
		panelX = x;
		panelY = y;
		panelScroll = 0;
		panelOpen = 0;
		UiSounds.soft();
	}

	private void closePanel() {
		panelId = null;
		sliding = null;
	}

	/** The element's own settings: those of its HUD element module, or of the module that draws it. */
	private java.util.List<nz.temple.setting.Setting<?>> elementSettings(String id) {
		java.util.List<nz.temple.setting.Setting<?>> out = new java.util.ArrayList<>();
		String name = MODULE_OF.get(id);
		var modules = TempleNzClient.modules();
		nz.temple.module.Module m = name == null || modules == null ? null : modules.byName(name);
		if (m == null) {
			return out;
		}
		nz.temple.setting.Setting<?> shownSetting = m instanceof nz.temple.client.modules.hud.HudElementModule e ? e.shown() : null;
		for (var group : m.groups()) {
			for (var s : group) {
				if (s == shownSetting || !s.isVisible() || s.isInternal()) {
					continue;
				}
				if (s instanceof nz.temple.setting.BoolSetting || s instanceof nz.temple.setting.NumberSetting || s instanceof nz.temple.setting.ModeSetting
						|| s instanceof nz.temple.setting.ColorSetting || s instanceof nz.temple.setting.ActionSetting) {
					out.add(s);
				}
			}
		}
		return out;
	}

	private float rowHeight(nz.temple.setting.Setting<?> s) {
		return s instanceof nz.temple.setting.NumberSetting ? 34 : 24;
	}

	private void drawPanel(Ui ui, GuiGraphicsExtractor g) {
		if (!shapePass) {
			panelHits.clear();
		}
		if (panelId == null) {
			return;
		}
		if (ELEMENTS.equals(panelId)) {
			drawElements(ui, g);
			return;
		}
		HudWidgets.Widget widget = HudWidgets.byId(panelId);
		String title = widget != null ? widget.name() : panelId;
		java.util.List<nz.temple.setting.Setting<?>> settings = elementSettings(panelId);
		if (!shapePass) {
			panelOpen = Math.min(1, panelOpen + 0.2f);
		}
		float e = 1 - (1 - panelOpen) * (1 - panelOpen) * (1 - panelOpen);
		float dw = width / u;
		float dh = height / u;
		// Content: header, size, opacity, the element's settings, then the two buttons.
		float content = 34 + 34 + 34 + 8;
		for (var s : settings) {
			content += rowHeight(s);
		}
		content += (settings.isEmpty() ? 0 : 6) + 34;
		float maxH = dh - 40;
		panelHeight = Math.min(content, maxH);
		float x = Math.max(8, Math.min(panelX + 8, dw - PANEL_W - 8));
		float y = Math.max(8, Math.min(panelY - 10, dh - panelHeight - 8));
		panelScroll = Math.max(0, Math.min(content - panelHeight, panelScroll));
		ui.alpha(e);
		fade = e;
		sShadow(x, y + 4, PANEL_W, panelHeight, 12, 22, 0x99000000);
		sFill(x, y, PANEL_W, panelHeight, 12, 0xF70B0B0B);
		sRing(x, y, PANEL_W, panelHeight, 12, 1, 0xFF1F1F1F);
		float mx = mx();
		float my = my();
		addHit(new PanelHit(x, y, x + PANEL_W, y + panelHeight, (b, px, py) -> {
		}));

		// Header: icon, name and close.
		float hy = y + 17;
		if (widget != null) {
			sIcon(g, widget.icon(), ui.mapX(x + PANEL_PAD + 5), ui.mapY(hy), 12 * u, HudTheme.alpha(0xFFF2F2F2, e));
		}
		sText(ui, title, x + PANEL_PAD + 16, hy, 11, 0xFFF2F2F2, Fonts.SEMIBOLD);
		boolean closeOver = mx >= x + PANEL_W - 26 && mx <= x + PANEL_W - 8 && my >= hy - 9 && my <= hy + 9;
		sIcon(g, Icons.Icon.CLOSE, ui.mapX(x + PANEL_W - 17), ui.mapY(hy), 10 * u, HudTheme.alpha(closeOver ? 0xFFFFFFFF : 0xFF9A9A9A, e));
		addHit(new PanelHit(x + PANEL_W - 26, hy - 9, x + PANEL_W - 8, hy + 9, (b, px, py) -> closePanel()));
		sFill(x + PANEL_PAD, y + 33, PANEL_W - PANEL_PAD * 2, 1, 0, 0xFF1C1C1C);

		float top = y + 34;
		visTop = top;
		visBottom = y + panelHeight;
		float ry = top + 4 - panelScroll;
		String id = panelId;
		float size = hud().sizeOf(id);
		float min = HudOverlay.minSize();
		float max = HudOverlay.maxSize();
		ry = sliderRow(ui, "Size", Math.round(size * 100) + "%", (size - min) / (max - min), x, ry, v -> hud().setSize(id, min + v * (max - min)));
		float op = hud().opacity(id);
		ry = sliderRow(ui, "Opacity", Math.round(op * 100) + "%", (op - 0.15f) / 0.85f, x, ry, v -> hud().setOpacity(id, 0.15f + v * 0.85f));
		ry += 4;
		if (!settings.isEmpty()) {
			sFill(x + PANEL_PAD, ry, PANEL_W - PANEL_PAD * 2, 1, 0, 0xFF1C1C1C);
			ry += 6;
		}
		for (var s : settings) {
			ry = settingRow(ui, g, s, x, ry);
		}
		ry += 4;
		float bw = (PANEL_W - PANEL_PAD * 2 - 6) / 2;
		panelButton(ui, g, "Reset position", Icons.Icon.RESET, x + PANEL_PAD, ry, bw, () -> hud().resetPosition(id));
		panelButton(ui, g, "Hide", Icons.Icon.EYE, x + PANEL_PAD + bw + 6, ry, bw, () -> {
			if (widget != null) {
				widget.set().accept(false);
			}
			closePanel();
		});
		ui.alpha(1);
	}

	/** The panel for empty space: every element with a switch, so hidden ones can be brought back. */
	private void drawElements(Ui ui, GuiGraphicsExtractor g) {
		java.util.List<HudWidgets.Widget> all = HudWidgets.all();
		if (!shapePass) {
			panelOpen = Math.min(1, panelOpen + 0.2f);
		}
		float e = 1 - (1 - panelOpen) * (1 - panelOpen) * (1 - panelOpen);
		float dw = width / u;
		float dh = height / u;
		float content = 34 + 4 + all.size() * 24 + 6;
		panelHeight = Math.min(content, dh - 40);
		float x = Math.max(8, Math.min(panelX + 8, dw - PANEL_W - 8));
		float y = Math.max(8, Math.min(panelY - 10, dh - panelHeight - 8));
		panelScroll = Math.max(0, Math.min(content - panelHeight, panelScroll));
		ui.alpha(e);
		fade = e;
		sShadow(x, y + 4, PANEL_W, panelHeight, 12, 22, 0x99000000);
		sFill(x, y, PANEL_W, panelHeight, 12, 0xF70B0B0B);
		sRing(x, y, PANEL_W, panelHeight, 12, 1, 0xFF1F1F1F);
		addHit(new PanelHit(x, y, x + PANEL_W, y + panelHeight, (b, px, py) -> {
		}));
		float hy = y + 17;
		sIcon(g, Icons.Icon.LAYOUT_GRID, ui.mapX(x + PANEL_PAD + 5), ui.mapY(hy), 12 * u, HudTheme.alpha(0xFFF2F2F2, e));
		sText(ui, "Elements", x + PANEL_PAD + 16, hy, 11, 0xFFF2F2F2, Fonts.SEMIBOLD);
		sIcon(g, Icons.Icon.CLOSE, ui.mapX(x + PANEL_W - 17), ui.mapY(hy), 10 * u, HudTheme.alpha(0xFF9A9A9A, e));
		addHit(new PanelHit(x + PANEL_W - 26, hy - 9, x + PANEL_W - 8, hy + 9, (b, px, py) -> closePanel()));
		sFill(x + PANEL_PAD, y + 33, PANEL_W - PANEL_PAD * 2, 1, 0, 0xFF1C1C1C);
		float top = y + 34;
		visTop = top;
		visBottom = y + panelHeight;
		float ry = top + 4 - panelScroll;
		for (HudWidgets.Widget w : all) {
			if (!visible(ry, 24)) {
				ry += 24;
				continue;
			}
			float cy = ry + 11;
			float l = x + PANEL_PAD;
			float right = x + PANEL_W - PANEL_PAD;
			sIcon(g, w.icon(), ui.mapX(l + 5), ui.mapY(cy), 10 * u, HudTheme.alpha(0xFFBDBDBD, e));
			sText(ui, w.name(), l + 16, cy, 9.5f, 0xFFF2F2F2, Fonts.SEMIBOLD);
			boolean on = w.shown();
			float t = toggles.merge("el@" + w.id(), on ? 1f : 0f, (old, v) -> old + (v - old) * 0.35f);
			sFill(right - 20, cy - 5.75f, 20, 11.5f, 5.75f, Ui.lerpColor(0xFF262626, 0xFFF2F2F2, t));
			sFill(right - 18.5f + 8.5f * t, cy - 4.25f, 8.5f, 8.5f, 4.25f, Ui.lerpColor(0xFF8A8A8A, 0xFF0A0A0A, t));
			addHit(new PanelHit(l, ry, right, ry + 22, (b, px, py) -> {
				w.set().accept(!w.shown());
				UiSounds.toggle(w.shown());
			}));
			ry += 24;
		}
	}

	private float sliderRow(Ui ui, String label, String value, float frac, float x, float y, java.util.function.Consumer<Float> set) {
		if (!visible(y, 34)) {
			return y + 34;
		}
		float l = x + PANEL_PAD;
		float w = PANEL_W - PANEL_PAD * 2;
		sText(ui, label, l, y + 9, 9.5f, 0xFFF2F2F2, Fonts.SEMIBOLD);
		sText(ui, value, l + w - ui.textWidth(value, Fonts.MEDIUM, 8.5f), y + 9, 8.5f, 0xFF9A9A9A, Fonts.MEDIUM);
		float ty = y + 23;
		frac = Math.max(0, Math.min(1, frac));
		sFill(l, ty - 1.5f, w, 3, 1.5f, 0xFF262626);
		sFill(l, ty - 1.5f, Math.max(3, w * frac), 3, 1.5f, 0xFFEDEDED);
		sFill(l + w * frac - 4.5f, ty - 4.5f, 9, 9, 4.5f, 0xFF0A0A0A);
		sFill(l + w * frac - 3.2f, ty - 3.2f, 6.4f, 6.4f, 3.2f, 0xFFF5F5F5);
		addHit(new PanelHit(l - 4, ty - 7, l + w + 4, ty + 7, (b, px, py) -> {
			sliding = set;
			slideX = l;
			slideW = w;
			set.accept(Math.max(0, Math.min(1, (px - l) / w)));
		}));
		return y + 34;
	}

	private float settingRow(Ui ui, GuiGraphicsExtractor g, nz.temple.setting.Setting<?> s, float x, float y) {
		if (!(s instanceof nz.temple.setting.NumberSetting) && !visible(y, s instanceof nz.temple.setting.ActionSetting ? 28 : 24)) {
			return y + (s instanceof nz.temple.setting.ActionSetting ? 28 : 24);
		}
		float l = x + PANEL_PAD;
		float w = PANEL_W - PANEL_PAD * 2;
		float cy = y + 11;
		String name = s.name();
		while (name.length() > 3 && ui.textWidth(name, Fonts.SEMIBOLD, 9.5f) > w * 0.58f) {
			name = name.substring(0, name.length() - 2) + "…";
		}
		if (s instanceof nz.temple.setting.NumberSetting n) {
			return sliderRow(ui, s.name(), n.display(), (float) n.fraction(), x, y, v -> n.setFraction(v));
		}
		if (s instanceof nz.temple.setting.ActionSetting a) {
			panelButton(ui, g, a.label(), Icons.Icon.ZAP, l, y + 1, w, a::press);
			return y + 28;
		}
		sText(ui, name, l, cy, 9.5f, 0xFFF2F2F2, Fonts.SEMIBOLD);
		float right = l + w;
		if (s instanceof nz.temple.setting.BoolSetting b) {
			float t = toggles.merge(s.key() + "@" + System.identityHashCode(s), b.get() ? 1f : 0f, (old, v) -> old + (v - old) * 0.35f);
			float tw = 20;
			float th = 11.5f;
			sFill(right - tw, cy - th / 2, tw, th, th / 2, Ui.lerpColor(0xFF262626, 0xFFF2F2F2, t));
			float knob = th - 3;
			sFill(right - tw + 1.5f + (tw - knob - 3) * t, cy - th / 2 + 1.5f, knob, knob, knob / 2, Ui.lerpColor(0xFF8A8A8A, 0xFF0A0A0A, t));
			addHit(new PanelHit(l, y, right, y + 22, (bt, px, py) -> {
				b.toggle();
				UiSounds.toggle(b.get());
			}));
		} else if (s instanceof nz.temple.setting.ModeSetting m) {
			String value = m.get();
			float vw = ui.textWidth(value, Fonts.MEDIUM, 8.5f);
			sText(ui, value, right - 10 - vw, cy, 8.5f, 0xFFD2D2D2, Fonts.MEDIUM);
			sIcon(g, Icons.Icon.CHEVRONS_UP_DOWN, ui.mapX(right - 3), ui.mapY(cy), 8 * u, HudTheme.alpha(0xFF9A9A9A, ui.alpha()));
			addHit(new PanelHit(l, y, right, y + 22, (bt, px, py) -> {
				java.util.List<String> options = m.options();
				int i = Math.max(0, options.indexOf(m.get()));
				int step = bt == InputConstants.MOUSE_BUTTON_RIGHT ? options.size() - 1 : 1;
				m.trySet(options.get((i + step) % options.size()));
				UiSounds.tap();
			}));
		} else if (s instanceof nz.temple.setting.ColorSetting c) {
			boolean theme = c.followsTheme();
			String value = theme ? "Theme" : String.format("#%06X", c.get() & 0xFFFFFF);
			float vw = ui.textWidth(value, Fonts.MEDIUM, 8.5f);
			sText(ui, value, right - 16 - vw, cy, 8.5f, 0xFFD2D2D2, Fonts.MEDIUM);
			if (theme) {
				sGrad(right - 11, cy - 5.5f, 11, 11, 5.5f, nz.temple.client.render.Theme.primary(), nz.temple.client.render.Theme.secondary(), 2);
			} else {
				sFill(right - 11, cy - 5.5f, 11, 11, 5.5f, 0xFF000000 | c.get());
			}
			addHit(new PanelHit(l, y, right, y + 22, (bt, px, py) -> {
				// Cycles: theme, then each swatch, then back to the theme.
				int at = -1;
				for (int i = 0; i < PANEL_SWATCHES.length; i++) {
					if (!c.followsTheme() && (PANEL_SWATCHES[i] & 0xFFFFFF) == (c.get() & 0xFFFFFF)) {
						at = i;
					}
				}
				if (c.followsTheme()) {
					c.set(PANEL_SWATCHES[0]);
				} else if (at == PANEL_SWATCHES.length - 1 && c.canFollowTheme()) {
					c.followTheme();
				} else {
					c.set(PANEL_SWATCHES[(at + 1) % PANEL_SWATCHES.length]);
				}
				UiSounds.tap();
			}));
		}
		return y + 24;
	}

	private void panelButton(Ui ui, GuiGraphicsExtractor g, String label, Icons.Icon icon, float x, float y, float w, Runnable action) {
		float h = 24;
		if (!visible(y, h)) {
			return;
		}
		boolean over = mx() >= x && mx() <= x + w && my() >= y && my() <= y + h;
		sFill(x, y, w, h, 7, over ? 0xFF181818 : 0xFF121212);
		sRing(x, y, w, h, 7, 1, over ? 0xFF2E2E2E : 0xFF1F1F1F);
		float lw = ui.textWidth(label, Fonts.SEMIBOLD, 8.8f) + 13;
		sIcon(g, icon, ui.mapX(x + (w - lw) / 2 + 4), ui.mapY(y + h / 2), 9 * u, HudTheme.alpha(0xFFD2D2D2, ui.alpha()));
		sText(ui, label, x + (w - lw) / 2 + 13, y + h / 2, 8.8f, 0xFFD2D2D2, Fonts.SEMIBOLD);
		addHit(new PanelHit(x, y, x + w, y + h, (b, px, py) -> {
			UiSounds.tap();
			action.run();
		}));
	}

	private boolean panelClick(int button, float px, float py) {
		for (int i = panelHits.size() - 1; i >= 0; i--) {
			PanelHit h = panelHits.get(i);
			if (px >= h.x0() && px <= h.x1() && py >= h.y0() && py <= h.y1()) {
				h.click().run(button, px, py);
				return true;
			}
		}
		return false;
	}


	/** While true the panel only submits its shapes (to the overlay layer); otherwise it only draws text, icons and hits. */
	private boolean shapePass;
	private float fade = 1;
	private float visTop = -1e9f;
	private float visBottom = 1e9f;

	private boolean visible(float y, float h) {
		return y >= visTop - 0.5f && y + h <= visBottom + 0.5f;
	}

	private void addHit(PanelHit hit) {
		if (!shapePass) {
			panelHits.add(hit);
		}
	}

	private void overlayShape(float x, float y, float w, float h, float r, int argb, float ring, float soft) {
		if (!shapePass) {
			return;
		}
		GlassRenderer glass = TempleNzClient.glass();
		if (glass != null && glass.available() && glass.hasOverlayRoom(0, 1)) {
			glass.overlayRect(x * u, y * u, w * u, h * u, r * u, HudTheme.alpha(argb, fade), ring * u, soft * u);
		}
	}

	private void sFill(float x, float y, float w, float h, float r, int argb) {
		overlayShape(x, y, w, h, r, argb, 0, 0);
	}

	private void sRing(float x, float y, float w, float h, float r, float width, int argb) {
		overlayShape(x, y, w, h, r, argb, width, 0);
	}

	private void sShadow(float x, float y, float w, float h, float r, float soft, int argb) {
		overlayShape(x, y, w, h, r, argb, 0, soft);
	}

	private void sGrad(float x, float y, float w, float h, float r, int from, int to, int mode) {
		if (!shapePass) {
			return;
		}
		GlassRenderer glass = TempleNzClient.glass();
		if (glass != null && glass.available() && glass.hasOverlayRoom(0, 1)) {
			glass.overlayGradient(x * u, y * u, w * u, h * u, r * u, HudTheme.alpha(from, fade), HudTheme.alpha(to, fade), mode, 0);
		}
	}

	private void sText(Ui ui, String text, float x, float cy, float size, int argb, net.minecraft.network.chat.FontDescription font) {
		if (!shapePass) {
			ui.textMiddle(text, x, cy, size, argb, font);
		}
	}

	private void sIcon(GuiGraphicsExtractor g, Icons.Icon icon, float cx, float cy, float size, int argb) {
		if (!shapePass) {
			Icons.draw(g, icon, cx, cy, size, argb);
		}
	}
}
