package nz.temple.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import nz.temple.client.TempleNzClient;
import nz.temple.client.audio.UiSounds;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Minecraft's Options, in Temple's look: one glass panel with a card for each kind of setting (an icon, a name and a
 * line on what is inside), the field of view slider in its header, and Done at the foot. The cards open Minecraft's
 * own screens, which Themed settings letters in the client's font.
 *
 * <p>It extends the vanilla options screen so everything that looks for "the options screen" still finds it.
 */
public final class TempleOptionsScreen extends OptionsScreen {
	private static final float PANEL_W = 600;
	private static final float CARD_H = 48;
	private static final float GAP = 10;

	private record Card(String title, String detail, Icons.Icon icon, Supplier<Screen> open, boolean enabled) {
	}

	private record Hit(float x0, float y0, float x1, float y1, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x0 && x <= x1 && y >= y0 && y <= y1;
		}
	}

	private final Screen parent;
	private final boolean inWorld;
	private final List<Card> cards = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private float[] lit = new float[0];
	/** The field of view slider: whether it is being dragged, how lit it is, and the value it is showing. */
	private boolean fovDrag;
	private float fovLit;
	private float fovShown = -1;
	private float fovX;
	private float fovW = 1;
	private double time;
	private long lastNanos = System.nanoTime();
	private float frameDt;
	private float s = 1;
	private float mouseX;
	private float mouseY;
	private int hovered = -1;
	private float doneLit;
	private float sideLit;
	private String pressed;
	private double pressedAt = -100;

	public TempleOptionsScreen(Screen parent, boolean inWorld) {
		//? if >=26.3 {
		/*super(parent, net.minecraft.client.Minecraft.getInstance().options);
		*///?} else if >=26 {
		super(parent, net.minecraft.client.Minecraft.getInstance().options, inWorld);
		//?} else {
		/*super(parent, net.minecraft.client.Minecraft.getInstance().options);
		*///?}
		this.parent = parent;
		this.inWorld = inWorld;
	}

	private Screen worldOptions() {
		//? if >=26 {
		return new net.minecraft.client.gui.screens.options.WorldOptionsScreen(this, Objects.requireNonNull(minecraft.level));
		//?} else {
		/*return new OnlineOptionsScreen(this, minecraft.options);
		*///?}
	}

	@Override
	protected void init() {
		cards.clear();
		cards.add(new Card("Video", "Graphics, render distance and frame rate", Icons.Icon.EYE,
				() -> new VideoSettingsScreen(this, minecraft, minecraft.options), true));
		cards.add(new Card("Controls", "Key binds, mouse and toggles", Icons.Icon.KEYBOARD,
				() -> new ControlsScreen(this, minecraft.options), true));
		cards.add(new Card("Music & sounds", "Volume for each kind of sound", Icons.Icon.MUSIC,
				() -> new SoundOptionsScreen(this, minecraft.options), true));
		cards.add(new Card("Skin", "Which layers of your skin show", Icons.Icon.USER,
				() -> new SkinCustomizationScreen(this, minecraft.options), true));
		cards.add(new Card("Chat", "Visibility, size, colours and links", Icons.Icon.USERS,
				() -> new ChatOptionsScreen(this, minecraft.options), true));
		cards.add(new Card("Language", "The game's language", Icons.Icon.GLOBE,
				() -> new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()), true));
		cards.add(new Card("Resource packs", "Textures, sounds and models", Icons.Icon.PACKAGE,
				() -> new PackSelectionScreen(minecraft.getResourcePackRepository(), this::applyPacks, minecraft.getResourcePackDirectory(),
						Component.translatable("resourcePack.title")), true));
		cards.add(new Card("Accessibility", "Narrator, contrast and motion", Icons.Icon.HEART,
				() -> new AccessibilityOptionsScreen(this, minecraft.options), true));
		cards.add(new Card("Telemetry", "What data Minecraft sends", Icons.Icon.SIGNAL,
				() -> new TelemetryInfoScreen(this, minecraft.options), minecraft.allowsTelemetry()));
		cards.add(new Card("Credits", "The people behind Temple", Icons.Icon.INFO,
				() -> new TempleCreditsScreen(this), true));
		if (lit.length != cards.size()) {
			lit = new float[cards.size()];
		}
	}

	private void applyPacks(PackRepository repository) {
		minecraft.options.updateResourcePacks(repository);
		minecraft.gui.setScreen(this);
	}

	private Ui ui() {
		return TempleNzClient.ui();
	}

	private int accent() {
		int c = Theme.primary();
		return c == 0 ? HudModule.GOLD : c;
	}

	// ---- frame ------------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		long now = System.nanoTime();
		frameDt = (float) Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		time += frameDt;
		s = UiScale.factor() * 1.08f;
		mouseX = mx / s;
		mouseY = my / s;
		hits.clear();

		Ui ui = ui();
		ui.begin(g);
		ui.transform(0, 0, s, s);
		float dw = width / s;
		float dh = height / s;
		float pw = Math.min(PANEL_W, dw - 48);
		float cols = pw >= 520 ? 2 : 1;
		int rows = (int) Math.ceil(cards.size() / cols);
		float ph = Math.min(dh - 40, 96 + rows * (CARD_H + GAP) - GAP + 64);
		float px = (dw - pw) / 2;
		float py = (dh - ph) / 2;
		float intro = ease(clamp((float) (time / 0.45)));
		float lift = (1 - intro) * 10;

		ui.alpha(intro);
		ui.glass(px, py + lift, pw, ph, 22);
		ui.fill(px, py + lift, pw, ph, 22, 0xB00E1014);
		ui.ring(px, py + lift, pw, ph, 22, 1, 0x1FFFFFFF);

		// Header: the title on the left, the field of view and the world / online button on the right.
		ui.textMiddle("Options", px + 26, py + 30 + lift, 18, 0xFFFFFFFF, Fonts.SEMIBOLD);
		ui.textMiddle(inWorld ? "Changes apply straight away" : "Minecraft's settings", px + 26, py + 50 + lift, 9, 0x8CFFFFFF, Fonts.MEDIUM);
		float fw = 190;
		float fx = px + pw - 26 - fw;
		float fy = py + 26 + lift;
		fovSlider(ui, fx, fy, fw, 22, intro);
		// 1.21.11 has no world options screen (difficulty sits on the options screen itself): the side button is Online there.
		//? if >=26 {
		boolean worldSide = inWorld;
		//?} else {
		/*boolean worldSide = false;
		*///?}
		String side = worldSide ? "World" : "Online";
		float sw = 36 + ui.textWidth(side, Fonts.SEMIBOLD, 9.5f);
		float sx = fx - 10 - sw;
		boolean overSide = inside(sx, fy, sw, 22);
		sideLit = approach(sideLit, overSide ? 1 : 0, 14);
		float in = press(side);
		ui.fill(sx + in, fy + in, sw - in * 2, 22 - in * 2, 11 - in, mix(0x1AFFFFFF, 0x33FFFFFF, sideLit));
		Icons.draw(g, worldSide ? Icons.Icon.MOUNTAIN : Icons.Icon.LINK, (sx + 14) * s, (fy + 11) * s, 11 * s, fade(0xE0FFFFFF, intro));
		ui.textMiddle(side, sx + 24, fy + 11, 9.5f, 0xE0FFFFFF, Fonts.SEMIBOLD);
		hits.add(new Hit(sx, fy, sx + sw, fy + 22, () -> tap(side, () -> minecraft.gui.setScreen(worldSide
				? worldOptions()
				: new OnlineOptionsScreen(this, minecraft.options)))));

		// The cards, rising in one after another.
		float gx = px + 18;
		float gy = py + 76 + lift;
		float cw = (pw - 36 - (cols - 1) * GAP) / cols;
		int hover = -1;
		for (int i = 0; i < cards.size(); i++) {
			Card card = cards.get(i);
			int col = (int) (i % cols);
			int row = (int) (i / cols);
			float stagger = ease(clamp((float) ((time - 0.08 - i * 0.035) / 0.4)));
			float cx = gx + col * (cw + GAP);
			float cy = gy + row * (CARD_H + GAP) + (1 - stagger) * 8;
			boolean over = card.enabled() && inside(cx, cy, cw, CARD_H);
			if (over) {
				hover = i;
			}
			lit[i] = approach(lit[i], over ? 1 : 0, 14);
			float l = lit[i];
			float push = press(card.title());
			ui.alpha(intro * stagger * (card.enabled() ? 1 : 0.45f));
			ui.fill(cx + push, cy + push, cw - push * 2, CARD_H - push * 2, 14, mix(0x0FFFFFFF, 0x1FFFFFFF, l));
			if (l > 0.01f) {
				ui.ring(cx + push, cy + push, cw - push * 2, CARD_H - push * 2, 14, 1, fade(0x33FFFFFF, l));
			}
			float ix = cx + 10;
			float iy = cy + (CARD_H - 28) / 2;
			ui.fill(ix, iy, 28, 28, 9, (Math.round(0x26 + 0x1A * l) << 24) | (accent() & 0xFFFFFF));
			Icons.draw(g, card.icon(), (ix + 14) * s, (iy + 14) * s, 13 * s, fade(0xFF000000 | (accent() & 0xFFFFFF), ui.alpha()));
			float tx = ix + 28 + 12 + l * 2;
			ui.textMiddle(card.title(), tx, cy + 18, 11, 0xFFFFFFFF, Fonts.SEMIBOLD);
			ui.textMiddle(card.enabled() ? card.detail() : "Not available on this account", tx, cy + 32, 8.5f, 0x8CFFFFFF, Fonts.MEDIUM);
			Icons.draw(g, Icons.Icon.CHEVRON_RIGHT, (cx + cw - 16 + l * 3) * s, (cy + CARD_H / 2) * s, 11 * s,
					fade(0x59FFFFFF + (Math.round(0x70 * l) << 24), ui.alpha()));
			if (card.enabled()) {
				hits.add(new Hit(cx, cy, cx + cw, cy + CARD_H, () -> tap(card.title(), () -> minecraft.gui.setScreen(card.open().get()))));
			}
		}
		if (hover != hovered && hover >= 0) {
			UiSounds.hover();
		}
		hovered = hover;

		// Foot: a quiet hint and Done.
		ui.alpha(intro);
		float footY = py + ph - 46 + lift;
		ui.textMiddle("Esc goes back", px + 26, footY + 15, 8.5f, 0x66FFFFFF, Fonts.MEDIUM);
		float dwid = 96;
		float dx = px + pw - 26 - dwid;
		boolean overDone = inside(dx, footY, dwid, 30);
		doneLit = approach(doneLit, overDone ? 1 : 0, 14);
		float dIn = press("Done");
		ui.fill(dx + dIn, footY + dIn, dwid - dIn * 2, 30 - dIn * 2, 15 - dIn, (Math.round(0xD9 + 0x19 * doneLit) << 24) | (accent() & 0xFFFFFF));
		float dtw = ui.textWidth("Done", Fonts.SEMIBOLD, 10.5f);
		ui.textMiddle("Done", dx + (dwid - dtw) / 2, footY + 15, 10.5f, 0xFF15110A, Fonts.SEMIBOLD);
		hits.add(new Hit(dx, footY, dx + dwid, footY + 30, () -> tap("Done", this::onClose)));
		ui.alpha(1);
		ui.resetTransform();
	}

	/**
	 * Field of view, as a slider in the panel's own style: a pill trough with the name on the left and the value on
	 * the right, filled in the accent up to where the handle sits. Drag or click anywhere along it.
	 */
	private void fovSlider(Ui ui, float x, float y, float w, float h, float alpha) {
		int fov = minecraft.options.fov().get();
		float value = (fov - 30) / 80f;
		if (fovShown < 0) {
			fovShown = value;
		}
		fovShown = approach(fovShown, value, 26);
		boolean over = inside(x, y, w, h);
		fovLit = approach(fovLit, over || fovDrag ? 1 : 0, 14);
		fovX = x;
		fovW = w;
		ui.fill(x, y, w, h, h / 2, mix(0x1AFFFFFF, 0x26FFFFFF, fovLit));
		float fill = Math.max(h, w * fovShown);
		ui.fill(x, y, fill, h, h / 2, (Math.round(0x40 + 0x26 * fovLit) << 24) | (accent() & 0xFFFFFF));
		float knob = h - 6;
		float kx = x + 3 + (w - 6 - knob) * fovShown;
		ui.fill(kx, y + 3, knob, knob, knob / 2, 0xF2FFFFFF);
		String shown = fov == 70 ? "Normal" : fov == 110 ? "Quake Pro" : String.valueOf(fov);
		ui.textMiddle("Field of view", x + 12, y + h / 2, 9.5f, 0xE0FFFFFF, Fonts.SEMIBOLD);
		float vw = ui.textWidth(shown, Fonts.SEMIBOLD, 9.5f);
		ui.textMiddle(shown, x + w - 12 - vw, y + h / 2, 9.5f, 0xFFFFFFFF, Fonts.SEMIBOLD);
		hits.add(new Hit(x, y, x + w, y + h, () -> {
			fovDrag = true;
			setFov(mouseX);
		}));
	}

	private void setFov(float at) {
		float t = Math.max(0, Math.min(1, (at - fovX - 3) / Math.max(1, fovW - 6)));
		int fov = Math.round(30 + t * 80);
		if (fov != minecraft.options.fov().get()) {
			minecraft.options.fov().set(fov);
			UiSounds.slider(t);
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (fovDrag) {
			setFov((float) (event.x() / s));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		fovDrag = false;
		return super.mouseReleased(event);
	}

	// ---- input ------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			double x = event.x() / s;
			double y = event.y() / s;
			for (int i = hits.size() - 1; i >= 0; i--) {
				if (hits.get(i).contains(x, y)) {
					hits.get(i).action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void tap(String key, Runnable action) {
		pressed = key;
		pressedAt = time;
		UiSounds.soft();
		action.run();
	}

	/** How far the thing called {@code key} is pushed in right now (it springs back over a fifth of a second). */
	private float press(String key) {
		if (!key.equals(pressed)) {
			return 0;
		}
		float t = (float) ((time - pressedAt) / 0.2);
		return t >= 1 ? 0 : 1.2f * (float) Math.sin(Math.PI * Math.min(1, t * 1.6f)) * (1 - t);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	private boolean inside(float x, float y, float w, float h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}

	private float approach(float value, float target, float rate) {
		return value + (target - value) * (1 - (float) Math.exp(-rate * frameDt));
	}

	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}

	private static float ease(float t) {
		float u = 1 - clamp(t);
		return 1 - u * u * u;
	}

	private static int fade(int argb, float a) {
		return (Math.round(((argb >>> 24) & 0xFF) * clamp(a)) << 24) | (argb & 0xFFFFFF);
	}

	private static int mix(int a, int b, float t) {
		t = clamp(t);
		int out = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}
}
