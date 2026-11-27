package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.LevelSummary;
import nz.temple.anim.Spring;
import nz.temple.client.audio.UiSounds;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.LogoMark;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.render.title.BlackHoleScene;
import nz.temple.client.render.title.TitleScene;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TempleTitleScreen extends TitleScreen {
	private static final float SIDE_X = 30;
	private static final float SIDE_W = 214;
	private static final float ROW_H = 30;
	private static final float PANEL_GAP = 26;
	private static final int ACCENT_FALLBACK = HudModule.GOLD;
	private static final String DISCORD_URL = "https://discord.gg/templ";
	private static final String PARTNER_URL = "https://discord.gg/tCNzVKwRs3";
	private static final float MARK_INSET = 3;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ROOT);
	private static final ExecutorService PINGS = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "Temple.nz server ping");
		t.setDaemon(true);
		return t;
	});
	private enum Panel {
		NONE, WORLDS, SERVERS
	}
	private record Hit(float x0, float y0, float x1, float y1, Runnable action, boolean sound) {
		Hit(float x0, float y0, float x1, float y1, Runnable action) {
			this(x0, y0, x1, y1, action, true);
		}
		boolean contains(double x, double y) {
			return x >= x0 && x <= x1 && y >= y0 && y <= y1;
		}
	}
	private record Item(String label, Icons.Icon icon, Runnable action, Panel panel) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, float[]> buttonCentres = new HashMap<>();
	private final Map<Integer, Hit> rowHits = new HashMap<>();
	private final List<Item> items = new ArrayList<>();
	private final Spring pill = new Spring(0, 320, 26);
	private final Spring pillW = new Spring(0, 320, 28);
	private final Spring pillShow = new Spring(0, 260, 22);
	private final Spring panelOpen = new Spring(0, 200, 22);
	private final Spring scroll = new Spring(0, 260, 30);
	private Panel panel = Panel.NONE;
	private Panel shownPanel = Panel.NONE;
	private double scrollTarget;
	private int selected = -1;
	private long lastClickTime;
	private int lastClickRow = -1;
	private double time;
	private long lastNanos = System.nanoTime();
	private float s = 1;
	private float mouseX;
	private float mouseY;
	private int hoveredItem = -1;
	private int pressedItem = -1;
	private double pressedAt;
	private float frameDt;
	private final float[] lit = new float[8];
	private final Map<String, Float> glow = new HashMap<>();
	private String pressedKey;
	private double pressedKeyAt = -100;
	private boolean orbit;
	private double swappedAt = -100;
	private ServerList servers;
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private final Map<ServerData, FaviconTexture> serverIcons = new HashMap<>();
	private final Map<ServerData, byte[]> serverIconBytes = new HashMap<>();
	private ServerData editing;

	private List<LevelSummary> worlds;
	private boolean worldsLoading;
	private final Map<String, FaviconTexture> worldIcons = new HashMap<>();
	public TempleTitleScreen() {
		super(false);
	}
	private Ui ui() {
		return TempleNzClient.ui();
	}

	@Override
	protected void init() {
		items.clear();
		items.add(new Item("Singleplayer", Icons.Icon.MOUNTAIN, () -> toggle(Panel.WORLDS), Panel.WORLDS));
		items.add(new Item("Multiplayer", Icons.Icon.GLOBE, () -> {
			if (!minecraft.allowsMultiplayer()) {
				return;
			}
			if (!minecraft.options.skipMultiplayerWarning) {
				minecraft.gui.setScreen(new SafetyScreen(this));
				return;
			}
			toggle(Panel.SERVERS);
		}, Panel.SERVERS));
		items.add(new Item("Realms", Icons.Icon.SERVER, () -> minecraft.gui.setScreen(new com.mojang.realmsclient.RealmsMainScreen(this)), Panel.NONE));
		items.add(new Item("Temple", Icons.Icon.SPARKLES, () -> TempleNzClient.openMenu(this), Panel.NONE));
		items.add(new Item("Options", Icons.Icon.SETTINGS, () -> minecraft.gui.setScreen(nz.temple.client.compat.Screens.options(this, minecraft.options, false)), Panel.NONE));
		items.add(new Item("Quit", Icons.Icon.LOG_OUT, () -> minecraft.stop(), Panel.NONE));
		if (panel == Panel.SERVERS) {
			loadServers();
		} else if (panel == Panel.WORLDS) {
			loadWorlds();
		}
	}
	@Override
	public void tick() {
		pinger.tick();
	}
	@Override
	public void removed() {
		pinger.removeAll();
		closeIcons();
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
	public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float a) {

	}
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		time += dt;
		frameDt = (float) dt;
		s = UiScale.factor() * 1.08f;
		mouseX = mx / s;
		mouseY = my / s;
		hits.clear();
		rowHits.clear();
		panelOpen.target = panel == Panel.NONE ? 0 : 1;
		if (panel != Panel.NONE) {
			shownPanel = panel;
		}
		List<Spring> springs = new ArrayList<>(List.of(pill, pillW, pillShow, panelOpen, scroll));
		scroll.target = scrollTarget;
		Spring.advance(dt, springs);
		float open = clamp(panelOpen.xf());
		if (songWanted() && songShow < 1 && nz.temple.client.render.title.SongBackdrop.covers()) {
			songShow = 1;
		}
		songShow = approach(songShow, songWanted() ? 1 : 0, 2.5f);
		float songIn = ease(songShow);
		boolean hole = MenuModule.blackHole();
		if (hole != orbit) {
			orbit = hole;
			Arrays.fill(lit, 0);
			pillShow.snap(0);
		}
		boolean scene = songIn < 0.999f;
		if (!scene) {
		} else if (hole && BlackHoleScene.get().available()) {
			BlackHoleScene.get().pointer(mx, my, width, height);
			BlackHoleScene.get().request(open, 0, MenuModule.sceneHighQuality());
		} else if (!hole && TitleScene.get().available()) {
			TitleScene.get().pointer(mx, my, width, height);
			TitleScene.get().request(0.62f, open, 0, MenuModule.sceneHighQuality());
		} else {
			nz.temple.client.render.MenuScene.draw(g, "", width, height);
		}
		if (songIn > 0.001f) {
			nz.temple.client.render.title.SongBackdrop.request(songIn);
		}
		Ui ui = ui();
		ui.begin(g);
		ui.glassRenderer().placeLayer(g);
		ui.transform(0, 0, s, s);
		float dw = width / s;
		float dh = height / s;
		if (songIn < 0.999f) {
			ui.master(1 - songIn);
			if (hole) {
				drawOrbit(ui, g, dw, dh, open);
			} else {
				ui.shadow(-60, -40, SIDE_X + SIDE_W + 80, dh + 80, 60, 70, 0x59000000);
				drawSide(ui, g, dw, dh);
			}
			ui.master(1);
		}
		if (songIn > 0.001f) {
			drawSong(ui, g, dw, dh, open, songIn);
		}
		if (open > 0.01f) {
			drawPanel(ui, g, dw, dh, open);
		} else {
			shownPanel = Panel.NONE;
		}
		drawCorner(ui, g, dw);
		ui.alpha(1);
		String version = TempleNzClient.versionLabel();
		label(version, dw - 8 - ui.textWidth(version, Fonts.MEDIUM, 6.5f), dh - 8, 6.5f, 0x80FFFFFF, Fonts.MEDIUM);
		ui.resetTransform();
		drawSceneSwap(g);
	}
	private double sceneSwapAt = -100;
	private boolean sceneSwapped = true;
	private static final double SWAP_DOWN = 0.22;
	private static final double SWAP_UP = 0.45;

	private void drawCorner(Ui ui, GuiGraphicsExtractor g, float dw) {
		float size = 20;
		float y = 6;
		float appear = ease(clamp((float) (time - 0.2) / 0.5f));
		ui.alpha(appear);
		float x = dw - 12 - size;
		cornerButton(ui, g, x, y, size, Icons.Icon.LIGHTBULB, "corner:suggest", "Suggestions", true,
				() -> TempleNzClient.openPopup(this, "suggestion"));
		x -= size + 6;
		boolean hole = MenuModule.blackHole();
		cornerButton(ui, g, x, y, size, hole ? Icons.Icon.SUN : Icons.Icon.MOON, "corner:scene",
				hole ? "Landscape" : "Black hole", false, this::swapScene);
	}
	private void cornerButton(Ui ui, GuiGraphicsExtractor g, float x, float y, float size, Icons.Icon icon, String key, String tip,
			boolean gold, Runnable action) {
		boolean over = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
		float hov = glow(key, over);
		float in = pressed(key);
		int a = accent() & 0xFFFFFF;
		ui.glass(x + in, y + in, size - in * 2, size - in * 2, size / 2 - in);
		int tint = gold ? mix((0x2E << 24) | a, (0x55 << 24) | a, hov) : mix(0x24000000, 0x2EFFFFFF, hov);
		ui.fill(x + in, y + in, size - in * 2, size - in * 2, size / 2 - in, tint);
		int colour = gold ? mix(0xFF000000 | a, 0xFFFFFFFF, hov * 0.6f) : mix(0xD9FFFFFF, 0xFFFFFFFF, hov);
		Icons.draw(g, icon, (x + size / 2) * s, (y + size / 2) * s, 11 * s, fade(colour, ui.alpha()));
		if (hov > 0.02f) {
			float tw = ui().textWidth(tip, Fonts.MEDIUM, 8.5f);
			label(tip, x + size / 2 - tw / 2, y + size + 9 + (1 - hov) * -3, 8.5f, fade(0xD9FFFFFF, hov), Fonts.MEDIUM);
		}
		hits.add(new Hit(x, y, x + size, y + size, () -> press(key, action)));
		buttonCentres.put(key, new float[] {x + size / 2, y + size / 2});
	}
	private void swapScene() {
		if (time - sceneSwapAt < SWAP_DOWN + SWAP_UP) {
			return;
		}
		sceneSwapAt = time;
		sceneSwapped = false;
	}

	private void drawSceneSwap(GuiGraphicsExtractor g) {
		double t = time - sceneSwapAt;
		if (t > SWAP_DOWN + SWAP_UP) {
			return;
		}
		float dark;
		if (t < SWAP_DOWN) {
			dark = ease(clamp((float) (t / SWAP_DOWN)));
		} else {
			if (!sceneSwapped) {
				sceneSwapped = true;
				MenuModule.swapScene();
			}
			dark = 1 - ease(clamp((float) ((t - SWAP_DOWN) / SWAP_UP)));
		}
		g.fill(0, 0, width, height, (Math.round(255 * dark) << 24) | 0x05060A);
	}
	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}
	private int accent() {
		int c = Theme.primary();
		return c == 0 ? ACCENT_FALLBACK : c;
	}
	private void drawSide(Ui ui, GuiGraphicsExtractor g, float dw, float dh) {
		float intro = clamp((float) (time / 0.9));
		float top = 34;
		float markSize = 22 * s;
		float appear = ease(clamp((float) (time - 0.05) / 0.5f));
		LogoMark.draw3d(g, "title", (SIDE_X - MARK_INSET) * s, (top - 11) * s + (1 - appear) * -6 * s, markSize, appear);
		ui.alpha(appear);
		label("Temple.nz", SIDE_X + 25, top, 15, 0xFFFFFFFF, Fonts.SEMIBOLD);
		discordChip(ui, g, SIDE_X + 35 + ui.textWidth("Temple.nz", Fonts.SEMIBOLD, 15), top);
		String who = minecraft.getUser() == null ? "" : minecraft.getUser().getName();
		label("Welcome back, " + who, SIDE_X, top + 26, 9, 0x99FFFFFF, Fonts.MEDIUM);
		float y = Math.max(118, dh / 2 - items.size() * ROW_H / 2 - 20);
		int hover = -1;
		for (int i = 0; i < items.size(); i++) {
			float ry = y + i * (ROW_H + 4);
			if (mouseX >= SIDE_X - 8 && mouseX <= SIDE_X + SIDE_W && mouseY >= ry && mouseY <= ry + ROW_H) {
				hover = i;
			}
		}
		if (hover != hoveredItem && hover >= 0) {
			UiSounds.hover();
		}
		hoveredItem = hover;
		int active = -1;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).panel() != Panel.NONE && items.get(i).panel() == panel) {
				active = i;
			}
		}
		int target = hover >= 0 ? hover : active;
		pillShow.target = target >= 0 ? 1 : 0;
		if (target >= 0) {
			float ty = y + target * (ROW_H + 4);
			float tw = 37 + ui.textWidth(items.get(target).label(), Fonts.SEMIBOLD, 12.5f) + 16;
			if (pillShow.x < 0.05) {
				pill.snap(ty);
				pillW.snap(tw);
			}
			pill.target = ty;
			pillW.target = tw;
		}
		float show = clamp(pillShow.xf());
		if (show > 0.01f) {
			float py = pill.xf();
			float pw = Math.max(30, pillW.xf());
			ui.alpha(show);
			ui.glass(SIDE_X - 10, py, pw, ROW_H, ROW_H / 2);
			ui.fill(SIDE_X - 10, py, pw, ROW_H, ROW_H / 2, 0x59000000);
			ui.ring(SIDE_X - 10, py, pw, ROW_H, ROW_H / 2, 1, 0x26FFFFFF);
			ui.fill(SIDE_X - 4, py + ROW_H / 2 - 7, 3, 14, 1.5f, (Math.round(0xFF * show) << 24) | (accent() & 0xFFFFFF));
			ui.alpha(1);
		}
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			float ry = y + i * (ROW_H + 4);
			float t = ease(clamp((float) (time - 0.12 - i * 0.055) / 0.45f));
			float slide = (1 - t) * -26;
			boolean on = i == hover || i == active;
			boolean disabled = item.panel() == Panel.SERVERS && !minecraft.allowsMultiplayer();
			float press = pressedItem == i ? (float) Math.max(0, 1 - (time - pressedAt) / 0.22) : 0;
			if (i < lit.length) {
				lit[i] = approach(lit[i], on ? 1 : 0, 16);
			}
			float l = i < lit.length ? lit[i] : on ? 1 : 0;
			float nudge = 5 * ease(l) - 2 * press * press;
			int colour = disabled ? 0x59FFFFFF : ((Math.round(0xC7 + (0xFF - 0xC7) * l)) << 24) | 0xFFFFFF;
			ui.alpha(t);
			float cy = ry + ROW_H / 2;
			Icons.draw(g, item.icon(), (SIDE_X + 6.5f + slide + nudge) * s, cy * s, 13 * s, fade(colour, t));
			label(item.label(), SIDE_X + 22 + slide + nudge, cy, 12.5f, colour, Fonts.SEMIBOLD);
			if (!disabled) {
				int index = i;
				hits.add(new Hit(SIDE_X - 10, ry, SIDE_X + SIDE_W, ry + ROW_H, () -> {
					pressedItem = index;
					pressedAt = time;
					item.action().run();
				}, item.panel() == Panel.NONE));
			}
		}
		ui.alpha(intro);
		float by = dh - 44;
		float x = SIDE_X;
		String[] links = {"Language", "Accessibility", "Credits"};
		Runnable[] actions = {
				() -> minecraft.gui.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())),
				() -> minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, minecraft.options)),
				() -> minecraft.gui.setScreen(new TempleCreditsScreen(this))};
		for (int i = 0; i < links.length; i++) {
			float w = ui.textWidth(links[i], Fonts.MEDIUM, 8.5f);
			boolean over = mouseX >= x - 2 && mouseX <= x + w + 2 && mouseY >= by - 6 && mouseY <= by + 6;
			label(links[i], x, by, 8.5f, over ? 0xF2FFFFFF : 0x80FFFFFF, Fonts.MEDIUM);
			hits.add(new Hit(x - 2, by - 6, x + w + 2, by + 6, actions[i]));
			x += w + 12;
		}
		String version = "Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Temple.nz";
		label(version, SIDE_X, dh - 24, 8, 0x59FFFFFF, Fonts.MEDIUM);
		partnerLine(ui, g, SIDE_X + ui.textWidth(version, Fonts.MEDIUM, 8) + 16, dh - 24);
		ui.alpha(1);
	}
	private float songShow;
	private String songKey = "";
	private double songKeyAt = -100;
	private float songProgress;
	private int songLine = -1;
	private double songLineAt = -100;

	private nz.temple.music.Lyrics songLyrics;
	private final Spring songScroll = new Spring(-1, 60, 15.5);
	private float songLyricMix;
	private float songDim;
	private final Spring songPill = new Spring(0, 320, 26);
	private final Spring songPillW = new Spring(0, 320, 28);
	private final Spring songPillShow = new Spring(0, 260, 22);
	private static nz.temple.client.modules.render.Music music() {
		return nz.temple.client.modules.render.Music.instance();
	}
	private boolean songWanted() {
		nz.temple.client.modules.render.Music m = music();
		return m != null && m.songMenuActive();
	}
	private void drawSong(Ui ui, GuiGraphicsExtractor g, float dw, float dh, float open, float show) {
		nz.temple.client.modules.render.Music m = music();
		var track = m.nowPlaying();
		if (track == null) {
			return;
		}
		int accent = m.coverAccent();
		if (!track.key().equals(songKey)) {
			songKey = track.key();
			songKeyAt = time;
			songLine = -1;
			songLyrics = null;
		}
		nz.temple.music.Lyrics lyrics = m.songLyrics(track);
		boolean hasLyrics = lyrics != null && !lyrics.isEmpty();
		if (hasLyrics && songLyrics != lyrics) {
			songLyrics = lyrics;
			songScroll.snap(Math.max(-1, startedAt(lyrics, m.lyricTime(track))));
		}
		songLyricMix = approach(songLyricMix, hasLyrics ? 1 : 0, 3);
		float lm = smooth(songLyricMix);
		float change = ease(clamp((float) ((time - songKeyAt) / 0.7)));
		float base = show * (1 - 0.95f * ease(open));
		ui.alpha(base);
		float top = 30;
		LogoMark.draw3d(g, "title-panel", (SIDE_X - MARK_INSET) * s, (top - 11) * s, 22 * s, ui.alpha());
		label("Temple.nz", SIDE_X + 25, top, 15, 0xFFFFFFFF, Fonts.SEMIBOLD);
		float cA = Math.min(dh * 0.46f, dw * 0.3f);
		float cB = Math.min(dh * 0.4f, dw * 0.25f);
		float c = lerp(cA, cB, lm);
		float cxA = Math.max(SIDE_X, dw * 0.1f);
		float cxB = Math.max(SIDE_X, dw * 0.27f - cB / 2);
		float cx = lerp(cxA, cxB, lm);
		float cyA = dh * 0.44f - cA / 2;
		float cyB = Math.max(56, dh * 0.4f - cB / 2 - 40);
		float cy = lerp(cyA, cyB, lm) + (float) Math.sin(time * 0.8) * 2;
		float breathe = track.playing() ? 0.5f + 0.5f * (float) Math.sin(time * 1.6) : 0;
		ui.shadow(cx - 10, cy - 6, c + 20, c + 26, 24, 46 + 10 * breathe, (Math.round((0x70 + 0x30 * breathe) * ui.alpha()) << 24) | (accent & 0xFFFFFF));
		ui.shadow(cx, cy + 8, c, c, 18, 24, 0x80000000);
		net.minecraft.resources.Identifier big = m.bigCover();
		float lift = (1 - change) * 10;
		songDim = approach(songDim, track.playing() ? 0 : 1, 6);
		if (big != null) {
			int px = m.bigCoverSize();
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(cx * s, (cy + lift) * s);
			pose.scale(c * s / px, c * s / px);
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, big, 0, 0, 0, 0, px, px, px, px, px, px,
					fade(mix(0xFFFFFFFF, 0xFFB8B8B8, songDim), ui.alpha() * change));
			pose.popMatrix();
		} else {
			ui.fill(cx, cy + lift, c, c, 16, 0x33FFFFFF);
			Icons.draw(g, Icons.Icon.MUSIC, (cx + c / 2) * s, (cy + c / 2 + lift) * s, 40 * s, fade(0xCCFFFFFF, ui.alpha()));
		}
		float tx = lerp(cx + c + 30, cx, lm);
		float tw = lerp(Math.max(120, dw - (cxA + cA + 30) - 40), Math.max(120, cB + 40), lm);
		float ty = lerp(cy + 14, cy + c + 22, lm) + lift;
		float titleSize = lerp(26, 19, lm);
		ui.alpha(base * change);
		String state = track.playing() ? "NOW PLAYING" : "PAUSED";
		label(state, tx, ty, 8.5f, mix(0xFF000000 | accent, 0xFFFFFFFF, 0.35f), Fonts.SEMIBOLD);
		ty += lerp(22, 17, lm);
		List<String> title = wrapTitle(ui, track.title(), tw, titleSize);
		for (String line : title) {
			label(line, tx, ty, titleSize, 0xFFFFFFFF, Fonts.SEMIBOLD);
			ty += titleSize + 3;
		}
		float artistSize = lerp(14, 12, lm);
		label(ellipsize(ui, track.artist(), Fonts.MEDIUM, artistSize, tw), tx, ty, artistSize, 0xE0FFFFFF, Fonts.MEDIUM);
		ty += 18;
		if (!track.album().isEmpty() && !track.album().equals(track.title())) {
			label(ellipsize(ui, track.album(), Fonts.MEDIUM, 10, tw), tx, ty, 10, 0x8CFFFFFF, Fonts.MEDIUM);
			ty += 14;
		}
		double pos = m.position(track);
		double length = track.duration();
		float barW = lerp(Math.min(tw, 300), cB, lm);
		float by = Math.max(ty + 12, (cy + c - 46) * (1 - lm));
		float target = length > 0 ? (float) clamp((float) (pos / length)) : 0;
		songProgress = approach(songProgress, target, Math.abs(target - songProgress) > 0.05f ? 18 : 10);
		ui.fill(tx, by, barW, 3, 1.5f, 0x2EFFFFFF);
		if (length > 0) {
			ui.fill(tx, by, Math.max(3, barW * songProgress), 3, 1.5f, 0xFF000000 | accent);
			ui.fill(tx + barW * songProgress - 3.5f, by - 2, 7, 7, 3.5f, 0xFFFFFFFF);
			label(clock(pos), tx, by + 12, 8, 0x99FFFFFF, Fonts.MEDIUM);
			String left = "-" + clock(Math.max(0, length - pos));
			label(left, tx + barW - ui.textWidth(left, Fonts.MEDIUM, 8), by + 12, 8, 0x99FFFFFF, Fonts.MEDIUM);
		}
		float ctrlY = by + 36;
		float mid = tx + barW / 2;
		songControl(ui, g, mid - 44, ctrlY, 15, Icons.Icon.SKIP_BACK, "song:prev", false, accent, () -> m.command("prev"));
		songControl(ui, g, mid, ctrlY, 19, track.playing() ? Icons.Icon.PAUSE : Icons.Icon.PLAY, "song:play", true, accent,
				() -> m.command("playpause"));
		songControl(ui, g, mid + 44, ctrlY, 15, Icons.Icon.SKIP_FORWARD, "song:next", false, accent, () -> m.command("next"));
		if (songLyrics != null && lm > 0.01f) {
			ui.alpha(base * lm);
			drawLyricColumn(ui, songLyrics, m.lyricTime(track), Math.max(cxB + cB + 60, dw * 0.5f), 64, dw - 50,
					dh - 76, accent, m.lyricColour(), (1 - lm) * 24);
		}
		drawSongMenu(ui, g, dw, dh, base);
		ui.alpha(1);
	}
	private void drawLyricColumn(Ui ui, nz.temple.music.Lyrics lyrics, double lt, float x0, float y0, float x1, float y1, int accent,
			int colour, float shift) {
		List<nz.temple.music.Lyrics.Line> lines = lyrics.lines();
		int current = startedAt(lyrics, lt);
		int sung = lyrics.indexAt(lt);
		if (current != songLine) {
			songLine = current;
			songLineAt = time;
		}
		songScroll.target = Math.max(-1, current);
		Spring.advance(frameDt, List.of(songScroll));
		float scroll = songScroll.xf();
		float width = x1 - x0;
		float anchor = y0 + (y1 - y0) * 0.38f;
		float base = ui.alpha();
		int whole = (int) Math.floor(scroll);
		int from = Math.max(0, whole - 7);
		int to = Math.min(lines.size() - 1, whole + 10);
		if (to < from) {
			return;
		}
		float[] heights = new float[to - from + 1];
		float[] sizes = new float[heights.length];
		for (int i = from; i <= to; i++) {
			float near = Math.max(0, 1 - Math.abs(i - scroll));
			float size = lerp(15, 21, smooth(near));
			sizes[i - from] = size;
			nz.temple.music.Lyrics.Line line = lines.get(i);
			int rows = line.gap() ? 1 : wrapWords(ui, line, size, width).size();
			heights[i - from] = line.gap() ? size * 1.4f : rows * size * 1.25f + size * 0.75f;
		}
		float before;
		if (whole < from) {
			before = (scroll - from) * heights[0] + heights[0] * 0.5f;
		} else {
			before = 0;
			for (int i = from; i < whole; i++) {
				before += heights[i - from];
			}
			float h = whole <= to ? heights[whole - from] : 0;
			before += h * 0.5f + h * (scroll - whole);
		}
		float y = anchor - before;
		for (int i = from; i <= to; i++) {
			float h = heights[i - from];
			float size = sizes[i - from];
			float d = Math.abs(i - scroll);
			float centre = y + h / 2;
			float edge = Math.min(clamp((centre - y0) / 60), clamp((y1 - centre) / 70));
			float a = lerp(1, 0.34f, clamp(d)) * lerp(1, 0.55f, clamp((d - 1) / 5)) * edge;
			if (a > 0.01f) {
				ui.alpha(base * a);
				nz.temple.music.Lyrics.Line line = lines.get(i);
				float rowY = y + size * 0.6f;
				if (line.gap()) {
					if (i == current) {
						drawGapDots(ui, line, lt, x0 + shift, rowY, size, colour);
					}
				} else {
					boolean live = i == sung;
					drawLyric(ui, line, lt, x0 + shift, rowY, size, width, accent, colour, live, i < current || i == current && !live);
				}
			}
			y += h;
		}
		ui.alpha(base);
	}
	private void drawGapDots(Ui ui, nz.temple.music.Lyrics.Line line, double lt, float x, float y, float size, int colour) {
		double span = Math.max(0.5, line.end() - line.start());
		float p = clamp((float) ((lt - line.start()) / span));
		for (int k = 0; k < 3; k++) {
			float lit = clamp(p * 3 - k);
			float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3 + k * 0.8);
			float r = size * (0.16f + 0.05f * pulse);
			ui.fill(x + k * size * 0.6f, y - r, r * 2, r * 2, r, fade(colour | 0xFF000000, 0.3f + 0.7f * lit));
		}
	}
	private void drawLyric(Ui ui, nz.temple.music.Lyrics.Line line, double time, float x, float y, float size, float width, int accent,
			int colour, boolean live, boolean past) {
		List<List<Integer>> rows = wrapWords(ui, line, size, width);
		List<nz.temple.music.Lyrics.Word> words = line.words();
		float space = ui.textWidth(" ", Fonts.SEMIBOLD, size);
		int at = live ? line.wordAt(time) : -1;
		float rowY = y;
		for (List<Integer> row : rows) {
			float wx = x;
			for (int i : row) {
				String text = words.isEmpty() ? line.text() : words.get(i).text();
				int c;
				float hop = 0;
				if (!live) {
					c = (colour & 0xFFFFFF) | (past ? 0xB3000000 : 0x8C000000);
				} else if (words.isEmpty() || i < at) {
					c = colour | 0xFF000000;
				} else if (i > at) {
					c = (colour & 0xFFFFFF) | 0x73000000;
				} else {
					nz.temple.music.Lyrics.Word w = words.get(i);
					float f = clamp((float) ((time - w.start()) / Math.max(0.05, w.end() - w.start())));
					c = mix((colour & 0xFFFFFF) | 0x8C000000, 0xFF000000 | accent, smooth(f));
					hop = 1.5f * (float) Math.sin(Math.PI * f);
				}
				label(text, wx, rowY - hop, size, c, Fonts.SEMIBOLD);
				wx += ui.textWidth(text, Fonts.SEMIBOLD, size) + space;
			}
			rowY += size * 1.25f;
		}
	}
	private static List<List<Integer>> wrapWords(Ui ui, nz.temple.music.Lyrics.Line line, float size, float width) {
		List<List<Integer>> rows = new ArrayList<>();
		List<nz.temple.music.Lyrics.Word> words = line.words();
		if (words.isEmpty()) {
			rows.add(List.of(0));
			return rows;
		}
		float space = ui.textWidth(" ", Fonts.SEMIBOLD, size);
		List<Integer> row = new ArrayList<>();
		float w = 0;
		for (int i = 0; i < words.size(); i++) {
			float ww = ui.textWidth(words.get(i).text(), Fonts.SEMIBOLD, size);
			if (!row.isEmpty() && w + space + ww > width) {
				rows.add(row);
				row = new ArrayList<>();
				w = 0;
			}
			w += (row.isEmpty() ? 0 : space) + ww;
			row.add(i);
		}
		rows.add(row);
		return rows;
	}
	private static int startedAt(nz.temple.music.Lyrics lyrics, double time) {
		List<nz.temple.music.Lyrics.Line> lines = lyrics.lines();
		int lo = 0;
		int hi = lines.size() - 1;
		int found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).start() <= time) {
				found = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return found;
	}
	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
	private static float smooth(float t) {
		t = clamp(t);
		return t * t * (3 - 2 * t);
	}
	private static List<String> wrapTitle(Ui ui, String title, float max, float size) {
		List<String> out = new ArrayList<>();
		if (ui.textWidth(title, Fonts.SEMIBOLD, size) <= max) {
			out.add(title);
			return out;
		}
		String[] words = title.split(" ");
		StringBuilder line = new StringBuilder();
		int i = 0;
		for (; i < words.length; i++) {
			String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
			if (ui.textWidth(candidate, Fonts.SEMIBOLD, size) > max && !line.isEmpty()) {
				break;
			}
			line = new StringBuilder(candidate);
		}
		out.add(ellipsize(ui, line.toString(), Fonts.SEMIBOLD, size, max));
		if (i < words.length) {
			out.add(ellipsize(ui, String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)), Fonts.SEMIBOLD, size, max));
		}
		return out;
	}
	private float designW() {
		return width / s;
	}
	private void songControl(Ui ui, GuiGraphicsExtractor g, float x, float y, float r, Icons.Icon icon, String key, boolean main, int accent,
			Runnable action) {
		boolean over = Math.hypot(mouseX - x, mouseY - y) <= r;
		float hov = glow(key, over);
		float in = pressed(key);
		if (main) {
			ui.fill(x - r + in, y - r + in, (r - in) * 2, (r - in) * 2, r - in, mix(0xF2FFFFFF, 0xFFFFFFFF, hov));
			Icons.draw(g, icon, x * s, y * s, 14 * s, fade(0xFF000000 | (mix(0xFF000000 | accent, 0xFF000000, 0.55f) & 0xFFFFFF), ui.alpha()));
		} else {
			if (hov > 0.01f) {
				ui.fill(x - r, y - r, r * 2, r * 2, r, (Math.round(0x22 * hov) << 24) | 0xFFFFFF);
			}
			Icons.draw(g, icon, x * s, y * s, 13 * s, fade(mix(0xCCFFFFFF, 0xFFFFFFFF, hov), ui.alpha()));
		}
		hits.add(new Hit(x - r, y - r, x + r, y + r, () -> press(key, action)));
	}
	private static String clock(double seconds) {
		int t = (int) Math.max(0, Math.round(seconds));
		return t / 60 + ":" + String.format(Locale.ROOT, "%02d", t % 60);
	}
	private void drawSongMenu(Ui ui, GuiGraphicsExtractor g, float dw, float dh, float base) {
		float h = 28;
		float gap = 6;
		float[] widths = new float[items.size()];
		float total = -gap;
		for (int i = 0; i < items.size(); i++) {
			widths[i] = 34 + ui.textWidth(items.get(i).label(), Fonts.SEMIBOLD, 11);
			total += widths[i] + gap;
		}
		float y = dh - 30 - h / 2;
		float x = (dw - total) / 2;
		ui.alpha(base);
		ui.fill(x - 8, y - 6, total + 16, h + 12, (h + 12) / 2, 0x66000000);
		ui.ring(x - 8, y - 6, total + 16, h + 12, (h + 12) / 2, 1, 0x1FFFFFFF);
		int hover = -1;
		float bx = x;
		for (int i = 0; i < items.size(); i++) {
			if (mouseX >= bx && mouseX <= bx + widths[i] && mouseY >= y && mouseY <= y + h) {
				hover = i;
			}
			bx += widths[i] + gap;
		}
		if (hover != hoveredItem && hover >= 0) {
			UiSounds.hover();
		}
		hoveredItem = hover;
		int active = -1;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).panel() != Panel.NONE && items.get(i).panel() == panel) {
				active = i;
			}
		}
		int target = hover >= 0 ? hover : active;
		songPillShow.target = target >= 0 ? 1 : 0;
		Spring.advance(frameDt, List.of(songPill, songPillW, songPillShow));
		if (target >= 0) {
			float tx = x;
			for (int i = 0; i < target; i++) {
				tx += widths[i] + gap;
			}
			if (songPillShow.x < 0.05) {
				songPill.snap(tx);
				songPillW.snap(widths[target]);
			}
			songPill.target = tx;
			songPillW.target = widths[target];
		}
		float pillShowNow = clamp(songPillShow.xf());
		int accent = music().coverAccent();
		if (pillShowNow > 0.01f) {
			ui.alpha(base * pillShowNow);
			ui.fill(songPill.xf(), y, songPillW.xf(), h, h / 2, 0x33FFFFFF);
			ui.fill(songPill.xf() + songPillW.xf() / 2 - 8, y + h - 3, 16, 2, 1, 0xFF000000 | accent);
		}
		bx = x;
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			float t = ease(clamp((float) (time - 0.1 - i * 0.05) / 0.45f));
			boolean disabled = item.panel() == Panel.SERVERS && !minecraft.allowsMultiplayer();
			float l = glow("songitem" + i, i == hover || i == active);
			int colour = disabled ? 0x59FFFFFF : ((Math.round(0xC7 + (0xFF - 0xC7) * l)) << 24) | 0xFFFFFF;
			float rise = (1 - t) * 8;
			ui.alpha(base * t);
			Icons.draw(g, item.icon(), (bx + 15) * s, (y + h / 2 + rise) * s, 12 * s, fade(colour, base * t));
			label(item.label(), bx + 26, y + h / 2 + rise, 11, colour, Fonts.SEMIBOLD);
			if (!disabled) {
				int index = i;
				hits.add(new Hit(bx, y, bx + widths[i], y + h, () -> {
					pressedItem = index;
					pressedAt = time;
					item.action().run();
				}, item.panel() == Panel.NONE));
			}
			buttonCentres.put("song:" + item.label(), new float[] {bx + widths[i] / 2, y + h / 2});
			bx += widths[i] + gap;
		}
		ui.alpha(1);
	}
	private void drawOrbit(Ui ui, GuiGraphicsExtractor g, float dw, float dh, float open) {
		float calm = 1 - ease(open);
		float top = Math.max(34, dh * 0.15f);
		float rightX = Math.min(dw - 150, dw * 0.78f);
		float rightY = Math.min(dh - 3 * (ROW_H + 4) - 12, dh * 0.7f);
		float[][] at = new float[items.size()][];
		float[] group = new float[items.size()];
		int lower = 0;
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			if (item.panel() != Panel.NONE) {
				at[i] = new float[] {SIDE_X, top + i * (ROW_H + 4)};
				group[i] = 1;
			} else if (item.label().equals("Quit")) {
				float w = itemWidth(ui, item);
				at[i] = new float[] {dw / 2 - w / 2 + 10, dh / 2 - ROW_H / 2};
				group[i] = calm;
			} else {
				at[i] = new float[] {rightX, rightY + lower++ * (ROW_H + 4)};
				group[i] = calm;
			}
		}
		int hover = -1;
		for (int i = 0; i < items.size(); i++) {
			float w = itemWidth(ui, items.get(i));
			if (group[i] > 0.5f && mouseX >= at[i][0] - 10 && mouseX <= at[i][0] - 10 + w && mouseY >= at[i][1] && mouseY <= at[i][1] + ROW_H) {
				hover = i;
			}
		}
		if (hover != hoveredItem && hover >= 0) {
			UiSounds.hover();
		}
		hoveredItem = hover;
		float appear = ease(clamp((float) (time - 0.05) / 0.6f)) * calm;
		if (appear > 0.01f) {
			float ny = Math.max(top + 2 * (ROW_H + 4) + 40, dh * 0.62f);
			ui.alpha(appear);
			label("Orbiting", SIDE_X, ny, 9, 0x8CFFFFFF, Fonts.MEDIUM);
			LogoMark.draw3d(g, "title-orbit", (SIDE_X - MARK_INSET) * s, (ny + 16) * s, 22 * s, appear);
			label("Temple.nz", SIDE_X + 25, ny + 27, 15, 0xFFFFFFFF, Fonts.SEMIBOLD);
			discordChip(ui, g, SIDE_X + 35 + ui.textWidth("Temple.nz", Fonts.SEMIBOLD, 15), ny + 27);
		}
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			float x = at[i][0];
			float ry = at[i][1];
			float t = ease(clamp((float) (time - 0.12 - i * 0.055) / 0.45f)) * group[i];
			boolean active = item.panel() != Panel.NONE && item.panel() == panel;
			boolean disabled = item.panel() == Panel.SERVERS && !minecraft.allowsMultiplayer();
			float press = pressedItem == i ? (float) Math.max(0, 1 - (time - pressedAt) / 0.22) : 0;
			if (i < lit.length) {
				lit[i] = approach(lit[i], i == hover || active ? 1 : 0, 16);
			}
			float l = i < lit.length ? lit[i] : 0;
			if (t < 0.01f) {
				continue;
			}
			float w = itemWidth(ui, item);
			if (l > 0.01f) {
				ui.alpha(t * l);
				ui.glass(x - 10, ry, w, ROW_H, ROW_H / 2);
				ui.fill(x - 10, ry, w, ROW_H, ROW_H / 2, 0x59000000);
				ui.ring(x - 10, ry, w, ROW_H, ROW_H / 2, 1, 0x26FFFFFF);
				if (active) {
					ui.fill(x - 4, ry + ROW_H / 2 - 7, 3, 14, 1.5f, (Math.round(0xFF * l) << 24) | (accent() & 0xFFFFFF));
				}
			}
			float nudge = (item.label().equals("Quit") ? 0 : 3 * ease(l)) - 2 * press * press;
			int colour = disabled ? 0x59FFFFFF : ((Math.round(0xC7 + (0xFF - 0xC7) * l)) << 24) | 0xFFFFFF;
			ui.alpha(t);
			float cy = ry + ROW_H / 2;
			Icons.draw(g, item.icon(), (x + 6.5f + nudge) * s, cy * s, 13 * s, fade(colour, t));
			label(item.label(), x + 22 + nudge, cy, 12.5f, colour, Fonts.SEMIBOLD);
			if (!disabled && group[i] > 0.5f) {
				int index = i;
				hits.add(new Hit(x - 10, ry, x - 10 + w, ry + ROW_H, () -> {
					pressedItem = index;
					pressedAt = time;
					item.action().run();
				}, item.panel() == Panel.NONE));
			}
		}
		ui.alpha(clamp((float) (time / 0.9)) * calm);
		String version = "Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Temple.nz";
		label(version, SIDE_X, dh - 24, 8, 0x59FFFFFF, Fonts.MEDIUM);
		partnerLine(ui, g, SIDE_X + ui.textWidth(version, Fonts.MEDIUM, 8) + 16, dh - 24);
		ui.alpha(1);
	}
	private void discordChip(Ui ui, GuiGraphicsExtractor g, float x, float cy) {
		String text = "Discord";
		float h = 18;
		float w = 29 + ui.textWidth(text, Fonts.SEMIBOLD, 9);
		float y = cy - h / 2;
		boolean over = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
		float hov = glow("link:discord", over);
		float in = pressed("link:discord");
		ui.glass(x + in, y + in, w - in * 2, h - in * 2, h / 2 - in);
		ui.fill(x + in, y + in, w - in * 2, h - in * 2, h / 2 - in, mix(0x24000000, 0xB35865F2, hov));
		ui.ring(x + in, y + in, w - in * 2, h - in * 2, h / 2 - in, 1, mix(0x26FFFFFF, 0x4DFFFFFF, hov));
		int colour = mix(0xE0FFFFFF, 0xFFFFFFFF, hov);
		Icons.draw(g, Icons.Icon.DISCORD, (x + 13) * s, cy * s, 11 * s, fade(colour, ui.alpha()));
		label(text, x + 22, cy, 9, colour, Fonts.SEMIBOLD);
		hits.add(new Hit(x, y, x + w, y + h, () -> press("link:discord", () -> openLink(DISCORD_URL))));
		buttonCentres.put("link:discord", new float[] {x + w / 2, cy});
	}
	private void partnerLine(Ui ui, GuiGraphicsExtractor g, float x, float cy) {
		String ask = "Want this for Bedrock?";
		label(ask, x, cy, 7f, 0xB8FFFFFF, Fonts.MEDIUM);
		partnerButton(ui, g, x + ui.textWidth(ask, Fonts.MEDIUM, 7f) + 6, cy - 6.5f);
	}
	private void partnerButton(Ui ui, GuiGraphicsExtractor g, float x, float y) {
		String text = "Check out our partner";
		float h = 13;
		float w = 14 + ui.textWidth(text, Fonts.SEMIBOLD, 6.5f);
		boolean over = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
		float hov = glow("link:partner", over);
		float in = pressed("link:partner");
		ui.glass(x + in, y + in, w - in * 2, h - in * 2, h / 2 - in);
		ui.fill(x + in, y + in, w - in * 2, h - in * 2, h / 2 - in, mix(0x24000000, 0x2EFFFFFF, hov));
		ui.ring(x + in, y + in, w - in * 2, h - in * 2, h / 2 - in, 1, mix(0x26FFFFFF, 0x4DFFFFFF, hov));
		label(text, x + 7, y + h / 2, 6.5f, mix(0xD9FFFFFF, 0xFFFFFFFF, hov), Fonts.SEMIBOLD);
		hits.add(new Hit(x, y, x + w, y + h, () -> press("link:partner", () -> openLink(PARTNER_URL))));
		buttonCentres.put("link:partner", new float[] {x + w / 2, y + h / 2});
	}
	private float itemWidth(Ui ui, Item item) {
		return 37 + ui.textWidth(item.label(), Fonts.SEMIBOLD, 12.5f) + 6;
	}
	private void toggle(Panel which) {
		if (panel == which) {
			closePanel();
			return;
		}
		if (panel == Panel.NONE) {
			UiSounds.panel(true);
		} else {
			swappedAt = time;
		}
		panel = which;
		selected = -1;
		scrollTarget = 0;
		scroll.snap(0);
		if (which == Panel.SERVERS) {
			loadServers();
		} else {
			loadWorlds();
		}
	}
	private void closePanel() {
		if (panel != Panel.NONE) {
			UiSounds.panel(false);
			panel = Panel.NONE;
		}
	}
	private float panelX(float dw) {
		return SIDE_X + SIDE_W + PANEL_GAP;
	}
	private void drawPanel(Ui ui, GuiGraphicsExtractor g, float dw, float dh, float open) {
		float e = ease(open);
		float x = panelX(dw) + (1 - e) * 60;
		float y = 28;
		float w = dw - panelX(dw) - 30;
		float h = dh - 56;
		ui.alpha(e);
		ui.glass(x, y, w, h, 20);
		ui.fill(x, y, w, h, 20, 0xB00E1014);
		ui.ring(x, y, w, h, 20, 1, 0x1FFFFFFF);

		hits.add(new Hit(x, y, x + w, y + h, () -> { }, false));
		float swap = clamp((float) ((time - swappedAt) / 0.3));
		float c = e * ease(swap);
		float lift = (1 - ease(swap)) * 6;
		open = Math.min(open, clamp((float) ((time - swappedAt) / 0.55)));
		ui.alpha(c);
		boolean servers = shownPanel == Panel.SERVERS;
		String title = servers ? "Multiplayer" : "Singleplayer";
		int count = servers ? (this.servers == null ? 0 : this.servers.size()) : (worlds == null ? 0 : worlds.size());
		String sub = servers ? count + (count == 1 ? " server" : " servers") : worldsLoading ? "Loading worlds…" : count + (count == 1 ? " world" : " worlds");
		label(title, x + 22, y + 26 + lift, 17, 0xFFFFFFFF, Fonts.SEMIBOLD);
		label(sub, x + 22, y + 44 + lift, 9, 0x8CFFFFFF, Fonts.MEDIUM);
		float ax = x + w - 20;
		List<Object[]> actions = new ArrayList<>();
		if (servers) {
			actions.add(new Object[] {"Refresh", Icons.Icon.REFRESH, (Runnable) this::loadServers});
			actions.add(new Object[] {"Direct", Icons.Icon.LINK, (Runnable) this::directConnect});
			actions.add(new Object[] {"Add server", Icons.Icon.PLUS, (Runnable) this::addServer});
		} else {
			actions.add(new Object[] {"Folder", Icons.Icon.FOLDER, (Runnable) () -> nz.temple.client.compat.Platform
					.openPath(minecraft.getLevelSource().getBaseDir())});
			actions.add(new Object[] {"Create world", Icons.Icon.PLUS, (Runnable) () -> CreateWorldScreen.openFresh(minecraft,
					() -> minecraft.gui.setScreen(this))});
		}
		for (Object[] act : actions) {
			String label = (String) act[0];
			float bw = 33 + ui.textWidth(label, Fonts.SEMIBOLD, 9.5f);
			ax -= bw;
			pillButton(ui, g, ax, y + 20, bw, 26, label, (Icons.Icon) act[1], (Runnable) act[2], label.startsWith("Add") || label.startsWith("Create"));
			ax -= 8;
		}
		float listX = x + 14;
		float listY = y + 64;
		float listW = w - 28;
		float listH = h - 64 - 58;
		int rows = servers ? count : worlds == null ? 0 : worlds.size();
		float rowH = 50;
		float gap = 6;
		float content = rows * (rowH + gap) + 4;
		double max = Math.max(0, content - listH);
		scrollTarget = Math.max(0, Math.min(max, scrollTarget));
		float sc = (float) Math.max(0, Math.min(max, scroll.x));
		ui.clip(listX - 2, listY, listX + listW + 2, listY + listH, true);
		if (rows == 0) {
			String empty = servers ? "No servers yet. Add one to get started." : worldsLoading ? "" : "No worlds yet. Create one to get started.";
			float ew = ui.textWidth(empty, Fonts.MEDIUM, 10);
			label(empty, listX + (listW - ew) / 2, listY + 60, 10, 0x8CFFFFFF, Fonts.MEDIUM);
		}
		for (int i = 0; i < rows; i++) {
			float ry = listY + 3 + i * (rowH + gap) - sc;
			if (ry + rowH < listY || ry > listY + listH) {
				continue;
			}
			float stagger = ease(clamp((open - 0.3f - i * 0.04f) / 0.5f));
			ui.alpha(c * stagger);
			ry += (1 - stagger) * 8;
			boolean over = mouseX >= listX && mouseX <= listX + listW && mouseY >= Math.max(ry, listY) && mouseY <= Math.min(ry + rowH, listY + listH);
			boolean sel = i == selected;
			String key = (servers ? "server:" : "world:") + i;
			float hov = glow(key, over);
			float pick = glow(key + ":selected", sel);
			int rest = mix(0x0FFFFFFF, 0x1FFFFFFF, hov);
			ui.fill(listX, ry, listW, rowH, 12, mix(rest, (0x2E << 24) | (accent() & 0xFFFFFF), pick));
			if (pick > 0.01f) {
				ui.ring(listX, ry, listW, rowH, 12, 1, fade((0x8C << 24) | (accent() & 0xFFFFFF), pick));
			}
			if (servers) {
				serverRow(ui, g, this.servers.get(i), listX, ry, listW, rowH, c * stagger);
			} else {
				worldRow(ui, g, worlds.get(i), listX, ry, listW, rowH, c * stagger);
			}
			int index = i;
			Hit row = new Hit(listX, Math.max(ry, listY), listX + listW, Math.min(ry + rowH, listY + listH), () -> clickRow(index));
			hits.add(row);
			rowHits.put(index, row);
		}
		ui.unclip();
		ui.alpha(c);
		if (max > 0) {
			float track = listH - 8;
			float thumb = Math.max(24, track * listH / content);
			float ty = listY + 4 + (float) (sc / max) * (track - thumb);
			ui.fill(listX + listW + 4, ty, 3, thumb, 1.5f, 0x40FFFFFF);
		}
		float fy = y + h - 44;
		boolean any = selected >= 0 && selected < rows;
		float fx = x + w - 20;
		String primary = servers ? "Join server" : "Play world";
		float pw = 38 + ui.textWidth(primary, Fonts.SEMIBOLD, 10.5f);
		fx -= pw;
		primaryButton(ui, g, fx, fy, pw, 30, primary, Icons.Icon.PLAY, any, () -> activate(selected));
		fx -= 10;
		float ew = 33 + ui.textWidth("Delete", Fonts.SEMIBOLD, 9.5f);
		fx -= ew;
		pillButtonState(ui, g, fx, fy + 2, ew, 26, "Delete", Icons.Icon.TRASH, any, () -> deleteSelected(), false);
		fx -= 8;
		float edw = 33 + ui.textWidth("Edit", Fonts.SEMIBOLD, 9.5f);
		fx -= edw;
		pillButtonState(ui, g, fx, fy + 2, edw, 26, "Edit", Icons.Icon.PENCIL, any, () -> editSelected(), false);
		label(servers ? "Double-click a server to join" : "Double-click a world to play", x + 22, fy + 15, 8.5f, 0x66FFFFFF, Fonts.MEDIUM);
		ui.alpha(1);
	}
	private void pillButton(Ui ui, GuiGraphicsExtractor g, float x, float y, float w, float h, String label, Icons.Icon icon,
			Runnable action, boolean accentTint) {
		pillButtonState(ui, g, x, y, w, h, label, icon, true, action, accentTint);
	}
	private void pillButtonState(Ui ui, GuiGraphicsExtractor g, float x, float y, float w, float h, String label, Icons.Icon icon,
			boolean enabled, Runnable action, boolean accentTint) {
		boolean over = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
		float hov = glow(label, over);
		float live = glow(label + ":enabled", enabled);
		float in = pressed(label);
		int base = accentTint ? (0x33 << 24) | (accent() & 0xFFFFFF) : 0x1AFFFFFF;
		int lit = accentTint ? (0x55 << 24) | (accent() & 0xFFFFFF) : 0x33FFFFFF;
		ui.fill(x + in, y + in, w - in * 2, h - in * 2, h / 2 - in, mix(base, lit, hov));
		int colour = mix(0x4DFFFFFF, mix(0xE0FFFFFF, 0xFFFFFFFF, hov), live);
		Icons.draw(g, icon, (x + 14) * s, (y + h / 2) * s, 11 * s, fade(colour, ui.alpha()));
		label(label, x + 24, y + h / 2, 9.5f, colour, Fonts.SEMIBOLD);
		if (enabled) {
			hits.add(new Hit(x, y, x + w, y + h, () -> press(label, action)));
		}
		buttonCentres.put(label, new float[] {x + w / 2, y + h / 2});
	}
	private void primaryButton(Ui ui, GuiGraphicsExtractor g, float x, float y, float w, float h, String label, Icons.Icon icon,
			boolean enabled, Runnable action) {
		boolean over = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
		int a = accent() & 0xFFFFFF;
		float hov = glow(label, over);
		float live = glow(label + ":enabled", enabled);
		float in = pressed(label);
		int fill = mix(0x1AFFFFFF, (Math.round(0xD9 + (0xF2 - 0xD9) * hov) << 24) | a, live);
		ui.fill(x + in, y + in, w - in * 2, h - in * 2, h / 2 - in, fill);
		int colour = mix(0x4DFFFFFF, 0xFF15110A, live);
		Icons.draw(g, icon, (x + 16) * s, (y + h / 2) * s, 11 * s, fade(colour, ui.alpha()));
		label(label, x + 27, y + h / 2, 10.5f, colour, Fonts.SEMIBOLD);
		if (enabled) {
			hits.add(new Hit(x, y, x + w, y + h, () -> press(label, action)));
		}
	}
	private void serverRow(Ui ui, GuiGraphicsExtractor g, ServerData data, float x, float y, float w, float h, float alpha) {
		float icon = 34;
		float ix = x + 8;
		float iy = y + (h - icon) / 2;
		FaviconTexture tex = serverIcons.computeIfAbsent(data, d -> FaviconTexture.forServer(minecraft.getTextureManager(), d.ip));
		byte[] bytes = data.getIconBytes();
		if (!Arrays.equals(bytes, serverIconBytes.get(data))) {
			serverIconBytes.put(data, bytes);
			try {
				if (bytes == null) {
					tex.clear();
				} else {
					tex.upload(NativeImage.read(bytes));
				}
			} catch (Throwable ignored) {
				data.setIconBytes(null);
				tex.clear();
			}
		}
		blitIcon(g, bytes != null ? tex.textureLocation() : null, ix, iy, icon, alpha, Icons.Icon.SERVER);
		float tx = ix + icon + 10;
		float right = x + w - 12;
		label(ellipsize(ui, data.name, Fonts.SEMIBOLD, 11, right - tx - 110), tx, y + 14, 11, 0xFFFFFFFF, Fonts.SEMIBOLD);
		Component motd = data.motd == null ? CommonComponents.EMPTY : data.motd;
		boolean waiting = (data.state() == ServerData.State.PINGING || data.state() == ServerData.State.INITIAL) && motd.getString().isEmpty();
		if (waiting) {
			motd = Component.literal("Pinging…").withColor(0x99FFFFFF);
		}
		List<FormattedCharSequence> lines = font.split(motd, (int) ((right - tx - 70) / 0.85f));
		var pose = g.pose();
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			pose.pushMatrix();
			pose.translate(tx * s, (y + 26 + i * 9.5f) * s);
			pose.scale(0.85f * s, 0.85f * s);
			g.text(font, lines.get(i), 0, 0, fade(0xD9FFFFFF, alpha), false);
			pose.popMatrix();
		}
		String players = data.players == null ? "" : data.players.online() + " / " + data.players.max();
		float pw = ui.textWidth(players, Fonts.MEDIUM, 9);
		if (!players.isEmpty()) {
			label(players, right - pw, y + 15, 9, 0xB3FFFFFF, Fonts.MEDIUM);
			Icons.draw(g, Icons.Icon.USERS, (right - pw - 9) * s, (y + 15) * s, 10 * s, fade(0x99FFFFFF, alpha));
		}
		int bars = 0;
		int barColour = 0xFF8E8E93;
		String ping = "";
		switch (data.state()) {
			case SUCCESSFUL -> {
				bars = data.ping < 60 ? 5 : data.ping < 120 ? 4 : data.ping < 220 ? 3 : data.ping < 400 ? 2 : 1;
				barColour = bars >= 4 ? 0xFF34C759 : bars == 3 ? 0xFFFFD60A : 0xFFFF9F0A;
				ping = data.ping + " ms";
			}
			case INCOMPATIBLE -> {
				ping = data.version.getString();
				barColour = 0xFFFF453A;
			}
			case UNREACHABLE -> {
				ping = "Offline";
				barColour = 0xFFFF453A;
			}
			default -> {
				if (waiting) {
					bars = (int) (time * 6) % 6;
				} else {
					ping = "Offline";
					barColour = 0xFFFF453A;
				}
			}
		}
		float bx = right - 22;
		for (int i = 0; i < 5; i++) {
			float bh = 3 + i * 2;
			boolean lit = i < bars;
			ui.fill(bx + i * 4.5f, y + 40 - bh, 3, bh, 1, lit ? barColour : 0x26FFFFFF);
		}
		if (!ping.isEmpty()) {
			float qw = ui.textWidth(ping, Fonts.MEDIUM, 8.5f);
			label(ping, bx - 6 - qw, y + 36, 8.5f, 0x99FFFFFF, Fonts.MEDIUM);
		}
	}
	private void worldRow(Ui ui, GuiGraphicsExtractor g, LevelSummary world, float x, float y, float w, float h, float alpha) {
		float icon = 34;
		float ix = x + 8;
		float iy = y + (h - icon) / 2;
		FaviconTexture tex = worldIcons.computeIfAbsent(world.getLevelId(), id -> {
			FaviconTexture t = FaviconTexture.forWorld(minecraft.getTextureManager(), id);
			Path file = world.getIcon();
			if (file != null && Files.isRegularFile(file)) {
				try (InputStream in = Files.newInputStream(file)) {
					t.upload(NativeImage.read(in));
				} catch (Throwable ignored) {
					t.clear();
				}
			}
			return t;
		});
		Path file = world.getIcon();
		boolean hasIcon = file != null && Files.isRegularFile(file);
		blitIcon(g, hasIcon ? tex.textureLocation() : null, ix, iy, icon, alpha, Icons.Icon.MOUNTAIN);
		float tx = ix + icon + 10;
		float right = x + w - 12;
		label(ellipsize(ui, world.getLevelName(), Fonts.SEMIBOLD, 11, right - tx - 120), tx, y + 15, 11, 0xFFFFFFFF, Fonts.SEMIBOLD);
		String mode = world.isHardcore() ? "Hardcore" : cap(world.getGameMode().getName());
		String detail = mode + (world.hasCommands() ? ", cheats" : "") + "  ·  " + world.getWorldVersionName().getString();
		label(ellipsize(ui, detail, Fonts.MEDIUM, 9, right - tx - 20), tx, y + 29, 9, 0xA6FFFFFF, Fonts.MEDIUM);
		label(world.getLevelId(), tx, y + 40, 8, 0x59FFFFFF, Fonts.MEDIUM);
		long played = world.getLastPlayed();
		if (played > 0) {
			String when = DATE.format(Instant.ofEpochMilli(played).atZone(ZoneId.systemDefault()));
			float ww = ui.textWidth(when, Fonts.MEDIUM, 8.5f);
			label(when, right - ww, y + 15, 8.5f, 0x8CFFFFFF, Fonts.MEDIUM);
			Icons.draw(g, Icons.Icon.CLOCK, (right - ww - 9) * s, (y + 15) * s, 9.5f * s, fade(0x80FFFFFF, alpha));
		}
		if (!world.primaryActionActive()) {
			String why = world.getInfo().getString();
			float ww = ui.textWidth(why, Fonts.MEDIUM, 8.5f);
			label(ellipsize(ui, why, Fonts.MEDIUM, 8.5f, 200), right - Math.min(200, ww), y + 36, 8.5f, 0xFFFF9F0A, Fonts.MEDIUM);
		}
	}
	private void blitIcon(GuiGraphicsExtractor g, net.minecraft.resources.Identifier id, float x, float y, float size, float alpha,
			Icons.Icon fallback) {
		if (id == null) {
			Ui ui = ui();
			ui.fill(x, y, size, size, 8, 0x1AFFFFFF);
			Icons.draw(g, fallback, (x + size / 2) * s, (y + size / 2) * s, 16 * s, fade(0x99FFFFFF, alpha));
			return;
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x * s, y * s);
		pose.scale(size * s / 64f, size * s / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, 64, 64, 64, 64, 64, 64, fade(0xFFFFFFFF, alpha));
		pose.popMatrix();
	}
	private void clickRow(int index) {
		long now = System.currentTimeMillis();
		boolean twice = index == lastClickRow && now - lastClickTime < 350;
		lastClickRow = index;
		lastClickTime = now;
		selected = index;
		if (twice) {
			activate(index);
		}
	}
	private void activate(int index) {
		if (shownPanel == Panel.SERVERS && servers != null && index >= 0 && index < servers.size()) {
			ServerData data = servers.get(index);
			ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
		} else if (shownPanel == Panel.WORLDS && worlds != null && index >= 0 && index < worlds.size()) {
			LevelSummary world = worlds.get(index);
			if (world.primaryActionActive()) {
				minecraft.createWorldOpenFlows().openWorld(world.getLevelId(), () -> minecraft.gui.setScreen(this));
			}
		}
	}
	private void addServer() {
		editing = new ServerData("", "", ServerData.Type.OTHER);
		minecraft.gui.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), ok -> {
			if (ok) {
				ServerData hidden = servers.unhide(editing.ip);
				if (hidden != null) {
					hidden.copyNameIconFrom(editing);
				} else {
					servers.add(editing, false);
				}
				servers.save();
			}
			minecraft.gui.setScreen(this);
		}, editing));
	}
	private void directConnect() {
		editing = new ServerData(net.minecraft.client.resources.language.I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
		minecraft.gui.setScreen(new DirectJoinServerScreen(this, ok -> {
			if (ok) {
				ServerData known = servers.get(editing.ip);
				if (known == null) {
					servers.add(editing, true);
					servers.save();
					known = editing;
				}
				ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(known.ip), known, false, null);
			} else {
				minecraft.gui.setScreen(this);
			}
		}, editing));
	}
	private void editSelected() {
		if (shownPanel == Panel.SERVERS && servers != null && selected >= 0 && selected < servers.size()) {
			ServerData current = servers.get(selected);
			editing = new ServerData(current.name, current.ip, ServerData.Type.OTHER);
			editing.copyFrom(current);
			minecraft.gui.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), ok -> {
				if (ok) {
					current.name = editing.name;
					current.ip = editing.ip;
					current.copyFrom(editing);
					servers.save();
				}
				minecraft.gui.setScreen(this);
			}, editing));
		} else if (shownPanel == Panel.WORLDS && worlds != null && selected >= 0 && selected < worlds.size()) {
			String id = worlds.get(selected).getLevelId();
			try {
				var access = minecraft.getLevelSource().validateAndCreateAccess(id);
				minecraft.gui.setScreen(EditWorldScreen.create(minecraft, access, result -> {
					access.safeClose();
					minecraft.gui.setScreen(this);
				}));
			} catch (Exception e) {
				net.minecraft.client.gui.components.toasts.SystemToast.onWorldAccessFailure(minecraft, id);
			}
		}
	}
	private void deleteSelected() {
		if (shownPanel == Panel.SERVERS && servers != null && selected >= 0 && selected < servers.size()) {
			ServerData data = servers.get(selected);
			minecraft.gui.setScreen(new ConfirmScreen(ok -> {
				if (ok) {
					servers.remove(data);
					servers.save();
					selected = -1;
				}
				minecraft.gui.setScreen(this);
			}, Component.translatable("selectServer.deleteQuestion"), Component.translatable("selectServer.deleteWarning", data.name),
					Component.translatable("selectServer.deleteButton"), CommonComponents.GUI_CANCEL));
		} else if (shownPanel == Panel.WORLDS && worlds != null && selected >= 0 && selected < worlds.size()) {
			LevelSummary world = worlds.get(selected);
			minecraft.gui.setScreen(new ConfirmScreen(ok -> {
				if (ok) {
					try (var access = minecraft.getLevelSource().createAccess(world.getLevelId())) {
						access.deleteLevel();
					} catch (Exception e) {
						net.minecraft.client.gui.components.toasts.SystemToast.onWorldDeleteFailure(minecraft, world.getLevelId());
					}
					selected = -1;
					loadWorlds();
				}
				minecraft.gui.setScreen(this);
			}, Component.translatable("selectWorld.deleteQuestion"), Component.translatable("selectWorld.deleteWarning", world.getLevelName()),
					Component.translatable("selectWorld.deleteButton"), CommonComponents.GUI_CANCEL));
		}
	}
	private void loadServers() {
		pinger.removeAll();
		closeIcons();
		servers = new ServerList(minecraft);
		servers.load();
		for (int i = 0; i < servers.size(); i++) {
			ping(servers.get(i));
		}
	}
	private void ping(ServerData data) {
		data.setState(ServerData.State.PINGING);
		data.motd = CommonComponents.EMPTY;
		data.status = CommonComponents.EMPTY;
		PINGS.submit(() -> {
			try {
				pinger.pingServer(data, () -> { }, () -> data.setState(
						data.protocol == SharedConstants.getCurrentVersion().protocolVersion() ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE),
						EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
			} catch (java.net.UnknownHostException e) {
				data.setState(ServerData.State.UNREACHABLE);
				data.motd = Component.translatable("multiplayer.status.cannot_resolve").withColor(0xFFFF6B6B);
			} catch (Exception e) {
				data.setState(ServerData.State.UNREACHABLE);
				data.motd = Component.translatable("multiplayer.status.cannot_connect").withColor(0xFFFF6B6B);
			}
		});
	}
	private void loadWorlds() {
		worldsLoading = true;
		closeWorldIcons();
		try {
			var candidates = minecraft.getLevelSource().findLevelCandidates();
			minecraft.getLevelSource().loadLevelSummaries(candidates).whenComplete((list, error) -> minecraft.execute(() -> {
				worldsLoading = false;
				List<LevelSummary> sorted = new ArrayList<>(list == null ? List.of() : list);
				sorted.sort(null);
				worlds = sorted;
			}));
		} catch (Exception e) {
			worldsLoading = false;
			worlds = List.of();
		}
	}
	private void closeIcons() {
		serverIcons.values().forEach(FaviconTexture::close);
		serverIcons.clear();
		serverIconBytes.clear();
		closeWorldIcons();
	}
	private void closeWorldIcons() {
		worldIcons.values().forEach(FaviconTexture::close);
		worldIcons.clear();
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / s;
		double y = event.y() / s;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(x, y)) {
				if (hit.sound()) {
					UiSounds.soft();
				}
				hit.action().run();
				return true;
			}
		}
		if (panel != Panel.NONE && x > SIDE_X + SIDE_W) {
			closePanel();
			return true;
		}
		if (x > SIDE_X + SIDE_W && songShow < 0.5f && !MenuModule.blackHole() && TitleScene.get().available()) {
			TitleScene.get().press(event.x(), event.y(), width, height);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && !MenuModule.blackHole() && TitleScene.get().available()) {
			TitleScene.get().drag(event.x(), event.y(), width, height);
		}
		return super.mouseDragged(event, dx, dy);
	}
	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && !MenuModule.blackHole() && TitleScene.get().available()) {
			int letter = TitleScene.get().pressedLetter();
			if (TitleScene.get().release() && letter >= 0) {
				UiSounds.note(letter);
			}
		}
		return super.mouseReleased(event);
	}
	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (panel != Panel.NONE) {
			scrollTarget -= sy * 40;
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}
	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (panel != Panel.NONE) {
			int rows = panel == Panel.SERVERS ? (servers == null ? 0 : servers.size()) : worlds == null ? 0 : worlds.size();
			if (key == InputConstants.KEY_ESCAPE) {
				closePanel();
				return true;
			}
			if (key == InputConstants.KEY_DOWN && rows > 0) {
				selected = Math.min(rows - 1, selected + 1);
				return true;
			}
			if (key == InputConstants.KEY_UP && rows > 0) {
				selected = Math.max(0, selected - 1);
				return true;
			}
			if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && selected >= 0) {
				activate(selected);
				return true;
			}
			if (key == InputConstants.KEY_DELETE && selected >= 0) {
				deleteSelected();
				return true;
			}
			if (key == InputConstants.KEY_F5 && panel == Panel.SERVERS) {
				loadServers();
				return true;
			}
		}
		return super.keyPressed(event);
	}
	public void showcase(String action) {
		switch (action.split(":")[0]) {
			case "servers" -> {
				minecraft.options.skipMultiplayerWarning = true;
				toggle(Panel.SERVERS);
			}
			case "worlds" -> toggle(Panel.WORLDS);
			case "close" -> closePanel();
			case "select" -> selected = 0;
			case "jump" -> TitleScene.get().jumpAll();
			case "drag" -> {
				String[] v = action.substring(action.indexOf(':') + 1).split(",");
				TitleScene.get().showcaseDrag(Integer.parseInt(v[0]), Float.parseFloat(v[1]), Float.parseFloat(v[2]));
			}
			case "drop" -> TitleScene.get().release();
			case "state" -> System.out.println("TEMPLE_TITLE letters " + TitleScene.get().showcaseState());
			case "click" -> {
				String[] uv = action.substring(action.indexOf(':') + 1).split(",");
				showcaseClick(Double.parseDouble(uv[0]) * width, Double.parseDouble(uv[1]) * height);
			}
			case "button" -> {
				String label = action.substring(action.indexOf(':') + 1);
				float[] at = buttonCentres.get(label);
				System.out.println("TEMPLE_TITLE button " + label + (at == null ? " not drawn" : " at " + at[0] + "," + at[1]));
				if (at != null) {
					showcaseClick(at[0] * s, at[1] * s);
				}
			}
			case "dblrow" -> {
				String ip = action.substring(action.indexOf(':') + 1);
				int index = -1;
				for (int i = 0; servers != null && i < servers.size(); i++) {
					if (servers.get(i).ip.equals(ip)) {
						index = i;
					}
				}
				Hit row = rowHits.get(index);
				System.out.println("TEMPLE_TITLE row " + ip + " index " + index + (row == null ? " not drawn" : ""));
				if (row != null) {
					double x = (row.x0() + row.x1()) / 2 * s;
					double y = (row.y0() + row.y1()) / 2 * s;
					showcaseClick(x, y);
					showcaseClick(x, y);
				}
			}
			case "tabout" -> TitleScene.get().pointer(-32000, -32000, width, height);
			case "sky", "hover" -> {
				String[] uv = action.substring(action.indexOf(':') + 1).split(",");
				double x = Double.parseDouble(uv[0]) * width;
				double y = Double.parseDouble(uv[1]) * height;
				TitleScene.get().pointer(x, y, width, height);
				if (action.startsWith("sky")) {
					TitleScene.get().click(x, y, width, height);
				}
			}
			default -> {
			}
		}
	}
	private void showcaseClick(double x, double y) {
		var event = new MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0));
		mouseClicked(event, false);
		mouseReleased(event);
		System.out.println("TEMPLE_TITLE clicked " + Math.round(x) + "," + Math.round(y) + " -> " + minecraft.gui.screen().getClass().getSimpleName());
	}
	private void label(String text, float x, float centerY, float size, int argb, net.minecraft.network.chat.FontDescription font) {
		ui().textMiddle(text, x, centerY, size, argb, font);
	}

	private float approach(float value, float target, float rate) {
		return value + (target - value) * (1 - (float) Math.exp(-rate * frameDt));
	}
	private float glow(String key, boolean on) {
		float v = approach(glow.getOrDefault(key, on ? 1f : 0f), on ? 1 : 0, 14);
		glow.put(key, v);
		return v;
	}
	private float pressed(String key) {
		if (!key.equals(pressedKey)) {
			return 0;
		}
		float t = (float) ((time - pressedKeyAt) / 0.2);
		return t >= 1 ? 0 : 1.2f * (float) Math.sin(Math.PI * Math.min(1, t * 1.6f)) * (1 - t);
	}
	private void press(String key, Runnable action) {
		pressedKey = key;
		pressedKeyAt = time;
		action.run();
	}

	private static void openLink(String url) {
		nz.temple.client.compat.Platform.openUri(url);
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
	private static float ease(float t) {
		float u = 1 - clamp(t);
		return 1 - u * u * u;
	}
	private static int fade(int argb, float a) {
		return (Math.round(((argb >>> 24) & 0xFF) * clamp(a)) << 24) | (argb & 0xFFFFFF);
	}
	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
	private static String ellipsize(Ui ui, String text, net.minecraft.network.chat.FontDescription font, float size, float max) {
		if (text == null) {
			return "";
		}
		if (ui.textWidth(text, font, size) <= max) {
			return text;
		}
		String t = text;
		while (t.length() > 1 && ui.textWidth(t + "…", font, size) > max) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}
}
