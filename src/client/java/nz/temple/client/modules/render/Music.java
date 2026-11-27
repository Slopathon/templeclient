package nz.temple.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import nz.temple.anim.Spring;
import nz.temple.client.music.LyricsSource;
import nz.temple.client.music.MediaSession;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.hud.HudTheme;
import nz.temple.client.render.GoldPanel;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.render.UserTexture;
import nz.temple.client.util.KeyEdge;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.music.Lyrics;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Music extends Module {
	private static final Path COVER = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
			.resolve(nz.temple.TempleNz.MOD_ID).resolve("music").resolve("cover.png");
	private static final int COVER_PX = 128;
	private static final Path COVER_BIG = COVER.resolveSibling("cover-big.png");
	private static final Path COVER_BLUR = COVER.resolveSibling("cover-blur.png");
	private static final int BIG_PX = 512;
	private static final int BLUR_W = 960;
	private static final int BLUR_H = 540;
	private static Music instance;
	private final BoolSetting card;
	private final BoolSetting controls;
	private final BoolSetting hideWhenPaused;
	private final BoolSetting pauseMenu;
	private final BoolSetting inventory;
	private final BoolSetting containers;
	private final NumberSetting menuSize;
	private final BoolSetting menuLyrics;
	private final BoolSetting songMenu;
	private boolean inMenu;
	private float menuK;
	private final BoolSetting lyricsOn;
	private final ModeSetting lyricStyle;
	private final NumberSetting offset;
	private final BoolSetting karaoke;
	private final NumberSetting wordSize;
	private final NumberSetting worldDistance;
	private final NumberSetting worldSize;
	private final ModeSetting worldExit;
	private final BoolSetting worldDepth;
	private final BoolSetting worldGhost;
	private final ModeSetting colors;
	private final ColorSetting custom;
	private final ColorSetting lyricColor;
	private final KeybindSetting playKey;
	private final KeybindSetting nextKey;
	private final KeybindSetting prevKey;
	private final KeyEdge playEdge;
	private final KeyEdge nextEdge;
	private final KeyEdge prevEdge;
	private final MediaSession session = new MediaSession();
	private final LyricsSource lyrics = new LyricsSource();
	private final UserTexture cover = new UserTexture("music_cover", () -> this.coverReady ? COVER : null);
	private final UserTexture coverBig = new UserTexture("music_cover_big", () -> this.coverReady ? COVER_BIG : null);
	private final UserTexture coverBlur = new UserTexture("music_cover_blur", () -> this.coverReady ? COVER_BLUR : null);
	private volatile boolean coverReady;
	private volatile int coverAccent = 0xFFE9B949;
	private volatile int coverShade = 0xFF101014;
	private long coverVersion = -1;
	private long enabledAt;
	private boolean hinted;
	private long lastRestart;
	private final Spring appear = new Spring(0, 220, 20);
	private long lastPlayingAt;
	private long lastNanos = System.nanoTime();
	private float progress;

	private int barLine = -1;
	private String barPrevious = "";
	private double barChanged;
	private float barScroll;
	private String crossWord = "";
	private String crossPrevious = "";
	private double crossChanged;
	private final float[][] buttons = new float[3][];
	private final List<WorldLine> world = new ArrayList<>();
	private int worldIndex = -1;
	private String worldTrack = "";
	private boolean rightSide;
	private Vec3 camPos = Vec3.ZERO;
	private Vec3 camForward = new Vec3(0, 0, 1);
	private static final class WorldLine {
		final Lyrics.Line line;
		final Vec3 base;
		final float yaw;
		final float scale;
		final List<Glyph> glyphs = new ArrayList<>();
		final float width;
		double exitAt = -1;
		WorldLine(Lyrics.Line line, Vec3 base, float yaw, float scale, float width) {
			this.line = line;
			this.base = base;
			this.yaw = yaw;
			this.scale = scale;
			this.width = width;
		}
	}
	private record Glyph(FormattedCharSequence text, float x, float w, int word, float vx, float vy, float vz, float spin, float delay) {
	}
	public Music() {
		super("Music", Category.RENDER, "Shows the song you are playing and its synced lyrics. Reads Spotify (or any media player) from "
				+ "Windows media controls, so no sign-in is needed. Lyrics can show on the now-playing card, under your crosshair, or in "
				+ "the world in front of you.");
		group("Card");
		card = add(new BoolSetting("Now playing card", true)
				.describe("A card at the top of the screen with album art, song, artist and progress. Move it in Edit HUD."));
		controls = add(new BoolSetting("Controls", true)
				.describe("Previous, play/pause and next buttons on the card. Open chat to click them, or use the keys below.")
				.visibleWhen(card::get));
		hideWhenPaused = add(new BoolSetting("Hide when paused", true)
				.describe("Hides the card a few seconds after playback stops.")
				.visibleWhen(card::get));
		group("Menus");
		pauseMenu = add(new BoolSetting("Pause menu", true)
				.describe("Shows the card at the top of the pause menu, with the menu moved down to make room. Its buttons can be clicked."));
		inventory = add(new BoolSetting("Inventory", true)
				.describe("Shows the card at the top of the screen while your inventory is open."));
		containers = add(new BoolSetting("Other menus", false)
				.describe("Also shows it over chests, furnaces and other containers."));
		menuSize = add(new NumberSetting("Menu size", 100, 60, 160, 10, "%")
				.describe("Size of the card in menus. It shrinks on its own when there is not enough room."));
		songMenu = add(new BoolSetting("Song main menu", true)
				.describe("While a song is playing, the title screen becomes a menu made from it: the album art fills the background, "
						+ "with the song, its progress, controls and the current lyrics. The colours come from the cover."));
		group("Lyrics");
		lyricsOn = add(new BoolSetting("Lyrics", true).describe("Shows synced lyrics."));
		lyricStyle = add(new ModeSetting("Lyric style", List.of("Top bar", "Crosshair", "In world"), "Top bar")
				.describe("Top bar shows each line on the now-playing card, highlighting words as they are sung. Crosshair shows the "
						+ "current word under your crosshair. In world shows each line on a block in front of you, then breaks it apart "
						+ "when the line ends.")
				.visibleWhen(lyricsOn::get));
		offset = add(new NumberSetting("Timing", 0, -3, 3, 0.05, " s")
				.describe("Shifts lyrics earlier (negative) or later (positive) if they are out of sync.")
				.visibleWhen(lyricsOn::get));
		karaoke = add(new BoolSetting("Karaoke fill", true)
				.describe("Highlights words as they are sung and dims the rest of the line.")
				.visibleWhen(() -> lyricsOn.get() && lyricStyle.is("Top bar")));
		wordSize = add(new NumberSetting("Word size", 14, 8, 28, 1, " px").describe("Size of the word under the crosshair.")
				.visibleWhen(() -> lyricsOn.get() && lyricStyle.is("Crosshair")));
		worldDistance = add(new NumberSetting("Max distance", 32, 8, 48, 1, " b")
				.describe("Maximum distance for in-world lyrics (32 is two chunks). Each line is placed as far out as possible with a clear "
						+ "view, and moves closer when something is in the way. Distant lines are scaled up so they stay readable.")
				.visibleWhen(() -> lyricsOn.get() && lyricStyle.is("In world")));
		worldSize = add(new NumberSetting("Text size", 100, 40, 200, 10, "%").describe("Size of in-world lyrics.")
				.visibleWhen(() -> lyricsOn.get() && lyricStyle.is("In world")));
		worldExit = add(new ModeSetting("Line exit", List.of("Crumble", "Fade"), "Crumble")
				.describe("Crumble breaks the letters off and drops them. Fade fades the line out.")
				.visibleWhen(() -> lyricsOn.get() && lyricStyle.is("In world")));
		worldDepth = add(new BoolSetting("3D letters", true).describe("Gives the letters depth.")
				.visibleWhen(() -> lyricsOn.get() && lyricStyle.is("In world")));
		worldGhost = add(new BoolSetting("Show whole line", true)
				.describe("Shows upcoming words faintly so you can read ahead.")
				.visibleWhen(() -> lyricsOn.get() && lyricStyle.is("In world")));
		group("Colours");
		colors = add(new ModeSetting("Color", List.of("Theme", "Custom"), "Theme")
				.describe("Theme uses the HUD accent for the progress bar and current word. Custom uses your colour."));
		custom = add(new ColorSetting("Custom color", 0xFFE9B949).themed().describe("Accent colour when Color is Custom.")
				.visibleWhen(() -> colors.is("Custom")));
		menuLyrics = add(new BoolSetting("Lyrics in menus", true)
				.describe("Keeps the lyric row under the card in menus.")
				.visibleWhen(() -> lyricsOn.get() && lyricStyle.is("Top bar")));
		lyricColor = add(new ColorSetting("Lyric color", 0xFFFFFFFF).describe("Colour of the lyrics in every style. The current word still "
				+ "highlights in the accent colour first.").visibleWhen(lyricsOn::get));
		group("Playback");
		add(new ActionSetting("Play / pause", "Play / pause", () -> session.command("playpause"))
				.describe("Toggles playback."));
		add(new ActionSetting("Next", "Next", () -> session.command("next")).describe("Skips to the next song."));
		add(new ActionSetting("Previous", "Previous", () -> session.command("prev")).describe("Goes back to the previous song."));
		playKey = add(new KeybindSetting("Play / pause key", KeybindSetting.NONE).describe("Toggles playback while in game."));
		nextKey = add(new KeybindSetting("Next key", KeybindSetting.NONE).describe("Skips to the next song while in game."));
		prevKey = add(new KeybindSetting("Previous key", KeybindSetting.NONE).describe("Goes back a song while in game."));
		playEdge = new KeyEdge(playKey);
		nextEdge = new KeyEdge(nextKey);
		prevEdge = new KeyEdge(prevKey);
		on(TickEvent.class, e -> tick());
		LevelRenderEvents.COLLECT_SUBMITS.register(this::submitWorld);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof ChatScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(event.x(), event.y()));
			} else if (menuFor(screen)) {
				initMenu(screen, w, h);
			}
		});
		instance = this;
	}
	public static Music instance() {
		return instance;
	}
	@Override
	protected void onEnable() {
		enabledAt = System.currentTimeMillis();
		hinted = false;
		if (!MediaSession.supported()) {
			nz.temple.client.TempleNzClient.notifications().info("Music", "Music reads your player through Windows, so it only works on Windows.");
			return;
		}
		session.start();
	}
	@Override
	protected void onDisable() {
		session.stop();
		world.clear();
		worldIndex = -1;
	}
	private MediaSession.Track track() {
		MediaSession.Track t = session.track();
		if (t == null || t.title().isEmpty()) {
			return null;
		}
		return t;
	}
	private void tick() {
		if (!isEnabled()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (MediaSession.supported() && !session.running() && now - lastRestart > 5000) {
			lastRestart = now;
			session.start();
		}
		if (!hinted && now - enabledAt > 6000 && track() == null) {
			hinted = true;
			nz.temple.client.TempleNzClient.notifications().info("Music",
					"Nothing playing yet. Start a song in Spotify (or anywhere) and it shows up here.");
		}
		if (playEdge.pressed()) {
			session.command("playpause");
		}
		if (nextEdge.pressed()) {
			session.command("next");
		}
		if (prevEdge.pressed()) {
			session.command("prev");
		}
		long version = session.artVersion();
		if (version != coverVersion && session.art() != null) {
			coverVersion = version;
			Path art = session.art();
			Thread worker = new Thread(() -> bakeCover(art), "temple-music-cover");
			worker.setDaemon(true);
			worker.start();
		}
	}
	private void bakeCover(Path art) {
		try {
			BufferedImage src = ImageIO.read(art.toFile());
			if (src == null) {
				return;
			}
			int side = Math.min(src.getWidth(), src.getHeight());
			BufferedImage out = new BufferedImage(COVER_PX, COVER_PX, BufferedImage.TYPE_INT_ARGB);
			var g = out.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setClip(new RoundRectangle2D.Float(0, 0, COVER_PX, COVER_PX, 34, 34));
			int sx = (src.getWidth() - side) / 2;
			int sy = (src.getHeight() - side) / 2;
			g.drawImage(src, 0, 0, COVER_PX, COVER_PX, sx, sy, sx + side, sy + side, null);
			g.dispose();
			float r = 17;
			for (int y = 0; y < COVER_PX; y++) {
				for (int x = 0; x < COVER_PX; x++) {
					float dx = Math.max(0, Math.max(r - x - 0.5f, x + 0.5f - (COVER_PX - r)));
					float dy = Math.max(0, Math.max(r - y - 0.5f, y + 0.5f - (COVER_PX - r)));
					if (dx == 0 || dy == 0) {
						continue;
					}
					float edge = r - (float) Math.sqrt(dx * dx + dy * dy);
					float a = Math.max(0, Math.min(1, edge + 0.5f));
					int argb = out.getRGB(x, y);
					out.setRGB(x, y, (Math.round(((argb >>> 24) & 0xFF) * a) << 24) | (argb & 0xFFFFFF));
				}
			}
			Path tmp = COVER.resolveSibling("cover.tmp");
			Files.createDirectories(COVER.getParent());
			ImageIO.write(out, "png", tmp.toFile());
			Files.move(tmp, COVER, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			bakeMenu(src, sx, sy, side);
			coverReady = true;
		} catch (Exception ignored) {
		}
	}
	private void bakeMenu(BufferedImage src, int sx, int sy, int side) throws java.io.IOException {
		BufferedImage big = new BufferedImage(BIG_PX, BIG_PX, BufferedImage.TYPE_INT_ARGB);
		var g = big.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setClip(new RoundRectangle2D.Float(0, 0, BIG_PX, BIG_PX, 56, 56));
		g.drawImage(src, 0, 0, BIG_PX, BIG_PX, sx, sy, sx + side, sy + side, null);
		g.dispose();
		write(big, COVER_BIG);
		BufferedImage tiny = new BufferedImage(24, 14, BufferedImage.TYPE_INT_RGB);
		g = tiny.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		int cropH = side * 9 / 16;
		g.drawImage(src, 0, 0, 24, 14, sx, sy + (side - cropH) / 2, sx + side, sy + (side + cropH) / 2, null);
		g.dispose();
		BufferedImage mid = new BufferedImage(120, 68, BufferedImage.TYPE_INT_RGB);
		g = mid.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(tiny, 0, 0, 120, 68, null);
		g.dispose();
		BufferedImage blur = new BufferedImage(BLUR_W, BLUR_H, BufferedImage.TYPE_INT_RGB);
		g = blur.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(mid, 0, 0, BLUR_W, BLUR_H, null);
		g.dispose();
		write(blur, COVER_BLUR);
		float[] hsb = new float[3];
		float best = -1;
		int pick = 0xE9B949;
		long r = 0, gg = 0, b = 0;
		int n = 0;
		for (int y = 0; y < 14; y++) {
			for (int x = 0; x < 24; x++) {
				int c = tiny.getRGB(x, y);
				int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
				r += cr;
				gg += cg;
				b += cb;
				n++;
				java.awt.Color.RGBtoHSB(cr, cg, cb, hsb);
				float score = hsb[1] * (0.35f + hsb[2]) * (hsb[2] > 0.15f && hsb[2] < 0.98f ? 1 : 0.2f);
				if (score > best) {
					best = score;
					pick = c & 0xFFFFFF;
				}
			}
		}
		java.awt.Color.RGBtoHSB((pick >> 16) & 0xFF, (pick >> 8) & 0xFF, pick & 0xFF, hsb);
		int accent = java.awt.Color.HSBtoRGB(hsb[0], Math.min(1, Math.max(hsb[1], 0.45f)), Math.max(hsb[2], 0.78f));
		java.awt.Color.RGBtoHSB((int) (r / n), (int) (gg / n), (int) (b / n), hsb);
		int shade = java.awt.Color.HSBtoRGB(hsb[0], Math.min(1, hsb[1] * 0.9f), Math.min(hsb[2], 0.16f));
		coverAccent = 0xFF000000 | (accent & 0xFFFFFF);
		coverShade = 0xFF000000 | (shade & 0xFFFFFF);
	}
	private static void write(BufferedImage image, Path to) throws java.io.IOException {
		Path tmp = to.resolveSibling(to.getFileName() + ".tmp");
		ImageIO.write(image, "png", tmp.toFile());
		Files.move(tmp, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	public boolean songMenuActive() {
		return isEnabled() && songMenu.get() && track() != null;
	}

	public MediaSession.Track nowPlaying() {
		return track();
	}
	public double position(MediaSession.Track t) {
		return session.position(t);
	}
	public net.minecraft.resources.Identifier bigCover() {
		return coverReady && coverBig.ready() ? coverBig.id() : null;
	}
	public int bigCoverSize() {
		return BIG_PX;
	}
	public net.minecraft.resources.Identifier blurCover() {
		return coverReady && coverBlur.ready() ? coverBlur.id() : null;
	}
	public int[] blurCoverSize() {
		return new int[] {BLUR_W, BLUR_H};
	}
	public long coverVersion() {
		return coverVersion;
	}
	public int coverAccent() {
		return coverAccent;
	}
	public int coverShade() {
		return coverShade;
	}

	public Lyrics songLyrics(MediaSession.Track t) {
		return currentLyrics(t);
	}
	public double lyricTime(MediaSession.Track t) {
		return songTime(t);
	}
	public int lyricColour() {
		return lyricColor.get();
	}

	public void command(String command) {
		session.command(command);
	}
	private int accent() {
		return colors.is("Custom") ? custom.get() : inMenu ? Theme.color(0) : nz.temple.client.hud.HudTheme.light();
	}
	private double songTime(MediaSession.Track t) {
		return session.position(t) + offset.get();
	}
	private Lyrics currentLyrics(MediaSession.Track t) {
		if (t == null || !lyricsOn.get()) {
			return null;
		}
		String fake = System.getProperty("temple.showcase") != null ? System.getProperty("temple.music.fakeLyrics") : null;
		if (fake != null) {
			try {
				return Lyrics.parse(Files.readString(Path.of(fake)), t.duration());
			} catch (java.io.IOException e) {
				return null;
			}
		}
		LyricsSource.Result r = lyrics.get(t);
		return r.state() == LyricsSource.State.FOUND ? r.lyrics() : null;
	}
	private static final float CARD_W = 236;
	private static final float CARD_H = 56;
	private static final float HUD_CARD_H = 72;
	private static final float LYRIC_H = 24;
	public boolean cardWanted(boolean editing) {
		if (!isEnabled() || !card.get()) {
			return false;
		}
		MediaSession.Track t = track();
		long now = System.currentTimeMillis();
		if (t != null && t.playing()) {
			lastPlayingAt = now;
		}
		boolean show = editing || t != null && (t.playing() || !hideWhenPaused.get() || now - lastPlayingAt < 5000);
		appear.target = show ? 1 : 0;
		return show || appear.x > 0.02;
	}
	/** The lyric line being sung now, "" between lines, or null without a song or synced lyrics (for the HUD's lyrics bar). */
	public String currentLine() {
		MediaSession.Track t = track();
		if (!isEnabled() || t == null) {
			return null;
		}
		Lyrics song = currentLyrics(t);
		if (song == null) {
			return null;
		}
		int i = song.indexAt(songTime(t));
		return i < 0 || i >= song.lines().size() ? "" : song.lines().get(i).text();
	}

	private boolean lyricRow() {
		if (!inMenu && nz.temple.client.hud.AzuraHud.lyricsBarActive()) {
			return false;
		}
		return lyricsOn.get() && lyricStyle.is("Top bar") && !(inMenu && !menuLyrics.get());
	}
	private static final float MENU_TOP = 6;
	private static final float MENU_GAP = 6;
	private boolean menuFor(net.minecraft.client.gui.screens.Screen screen) {
		if (!isEnabled() || Minecraft.getInstance().level == null) {
			return false;
		}
		if (screen instanceof net.minecraft.client.gui.screens.PauseScreen pause) {
			return pauseMenu.get() && pause.showsPauseMenu();
		}
		if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
				|| screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
			return inventory.get();
		}
		return screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && containers.get();
	}
	private void initMenu(net.minecraft.client.gui.screens.Screen screen, int width, int height) {
		boolean[] laidOut = {false};
		boolean[] fits = {false};
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
			if (!isEnabled() || track() == null) {
				buttons[0] = buttons[1] = buttons[2] = null;
				return;
			}
			if (!laidOut[0]) {
				laidOut[0] = true;
				fits[0] = layoutMenu(screen, height);
			}
			if (fits[0]) {
				drawMenuCard(g, width);
			}
		});
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(event.x(), event.y()));
	}
	private boolean layoutMenu(net.minecraft.client.gui.screens.Screen screen, int height) {
		inMenu = true;
		float k = menuSize.getFloat() / 100f * UiScale.factor();
		float full = cardHeight(k);
		inMenu = false;
		float fitted;
		if (screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			var widgets = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
			if (widgets.isEmpty()) {
				return false;
			}
			int top = Integer.MAX_VALUE;
			int bottom = 0;
			for (var w : widgets) {
				top = Math.min(top, w.getY());
				bottom = Math.max(bottom, w.getY() + w.getHeight());
			}
			float room = height - 4 - (bottom - top) - MENU_TOP - MENU_GAP;
			fitted = full <= room ? k : k * room / full;
			if (fitted < 0.5f * k) {
				return false;
			}
			int shift = Math.max(0, Math.round(MENU_TOP + full * fitted / k + MENU_GAP) - top);
			for (var w : widgets) {
				w.setY(w.getY() + shift);
			}
		} else {
			int tabs = screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen ? 28 : 0;
			float room = ((nz.temple.client.mixin.ContainerScreenAccessor) screen).temple$topPos() - tabs - MENU_GAP - MENU_TOP;
			fitted = full <= room ? k : k * room / full;
			if (fitted < 0.5f * k) {
				return false;
			}
		}
		menuK = fitted;
		return true;
	}
	private void drawMenuCard(GuiGraphicsExtractor g, int width) {
		Ui ui = nz.temple.client.TempleNzClient.ui();
		appear.target = 1;
		inMenu = true;
		try {
			ui.begin(g);
			drawCard(g, ui, (width - cardWidth(menuK)) / 2, MENU_TOP, menuK, false);
		} finally {
			inMenu = false;
		}
	}
	public float cardWidth(float k) {
		return CARD_W * k;
	}
	public float cardHeight(float k) {
		if (!inMenu) {
			return HUD_CARD_H * k;
		}
		return (CARD_H + (lyricRow() ? LYRIC_H : 0)) * k;
	}

	public void drawCard(GuiGraphicsExtractor g, Ui ui, float x, float y, float k, boolean editing) {
		long nanos = System.nanoTime();
		double dt = Math.min(0.1, (nanos - lastNanos) / 1e9);
		lastNanos = nanos;
		appear.step(dt);
		float a = (float) Math.max(0, Math.min(1, appear.x));
		if (a <= 0.01f) {
			return;
		}
		MediaSession.Track t = track();
		boolean sample = t == null;
		String title = sample ? "Nothing playing" : t.title();
		String artist = sample ? "Start a song in Spotify" : t.artist();
		double pos = sample ? 0 : session.position(t);
		double length = sample ? 0 : t.duration();
		float w = cardWidth(k);
		float h = cardHeight(k);
		float drop = (1 - a) * -10 * k;
		y += drop;
		ui.resetTransform();
		ui.alpha(a);
		if (inMenu) {
			GoldPanel.smoke(g, Math.round(ui.mapX(x)), Math.round(ui.mapY(y)), Math.round(ui.mapX(x + w)), Math.round(ui.mapY(y + h)), 12 * k, a);
		} else {
			ui.glass(x, y, w, h, 10 * k);
			drawHudCard(g, ui, t, sample, title, artist, pos, length, x, y, w, h, k, a, dt);
			ui.alpha(1);
			return;
		}
		float pad = 8 * k;
		float art = 40 * k;
		if (!sample && coverReady && cover.ready()) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(x + pad, y + pad);
			pose.scale(art / COVER_PX, art / COVER_PX);
			g.blit(RenderPipelines.GUI_TEXTURED, cover.id(), 0, 0, 0, 0, COVER_PX, COVER_PX, COVER_PX, COVER_PX, COVER_PX, COVER_PX,
					fade(0xFFFFFFFF, a));
			pose.popMatrix();
		} else {
			float p = 1f / Math.max(1, (float) Minecraft.getInstance().getWindow().getGuiScale());
			Draw2D.pixelRound(g, x + pad, y + pad, x + pad + art, y + pad + art, 7 * k / p, fade(0x33FFFFFF, a));
			Icons.draw(g, Icons.Icon.MUSIC, x + pad + art / 2, y + pad + art / 2, 18 * k, fade(0xCCFFFFFF, a));
		}
		float tx = x + pad + art + 9 * k;
		float controlsW = controls.get() ? 62 * k : 0;
		float textRight = x + w - pad - controlsW;
		ui.textMiddle(ellipsize(ui, title, Fonts.SEMIBOLD, 10.5f * k, textRight - tx), tx, y + 16 * k, 10.5f * k, fade(0xFFFFFFFF, a), Fonts.SEMIBOLD);
		ui.textMiddle(ellipsize(ui, artist, Fonts.MEDIUM, 9 * k, textRight - tx), tx, y + 28 * k, 9 * k, fade(0xA6FFFFFF, a), Fonts.MEDIUM);
		float barY = y + 41 * k;
		float barX1 = x + w - pad;
		float target = length > 0 ? (float) Math.max(0, Math.min(1, pos / length)) : 0;
		progress += (target - progress) * (float) Math.min(1, dt * 10);
		if (Math.abs(target - progress) > 0.05f) {
			progress = target;
		}
		float pxH = Math.max(1.5f * k, 1f / (float) Minecraft.getInstance().getWindow().getGuiScale() * 2);
		Draw2D.pixelRound(g, tx, barY, barX1, barY + pxH, 1, fade(0x26FFFFFF, a));
		if (length > 0) {
			Draw2D.pixelRound(g, tx, barY, tx + (barX1 - tx) * progress, barY + pxH, 1, fade(accent() | 0xFF000000, a));
			String left = clock(pos);
			String right = "-" + clock(Math.max(0, length - pos));
			ui.textMiddle(left, tx, barY + 7 * k, 7 * k, fade(0x80FFFFFF, a), Fonts.MEDIUM);
			ui.textMiddle(right, barX1 - ui.textWidth(right, Fonts.MEDIUM, 7 * k), barY + 7 * k, 7 * k, fade(0x80FFFFFF, a), Fonts.MEDIUM);
		}
		buttons[0] = buttons[1] = buttons[2] = null;
		if (controls.get()) {
			float cy = y + 22 * k;
			float cx = x + w - pad - 10 * k;
			Icons.Icon[] icons = {Icons.Icon.SKIP_BACK, !sample && t.playing() ? Icons.Icon.PAUSE : Icons.Icon.PLAY, Icons.Icon.SKIP_FORWARD};
			double mx = Minecraft.getInstance().mouseHandler.xpos() / Minecraft.getInstance().getWindow().getGuiScale();
			double my = Minecraft.getInstance().mouseHandler.ypos() / Minecraft.getInstance().getWindow().getGuiScale();
			boolean pointer = inMenu || Minecraft.getInstance().gui.screen() instanceof ChatScreen;
			for (int i = 2; i >= 0; i--) {
				float size = (i == 1 ? 13 : 11) * k;
				float r = 9 * k;
				boolean over = pointer && Math.abs(mx - cx) <= r && Math.abs(my - cy) <= r;
				if (i == 1 && !inMenu) {
					ui.fill(cx - r, cy - r, r * 2, r * 2, 6 * k, (Math.round(0x40 + (over ? 0x30 : 0)) << 24) | (accent() & 0xFFFFFF));
				} else if (over) {
					float p = 1f / Math.max(1, (float) Minecraft.getInstance().getWindow().getGuiScale());
					Draw2D.pixelRound(g, cx - r, cy - r, cx + r, cy + r, r / p, fade(0x26FFFFFF, a));
				}
				Icons.draw(g, icons[i], cx, cy, size, fade(over ? 0xFFFFFFFF : 0xD9FFFFFF, a));
				buttons[i] = new float[] {cx - r, cy - r, cx + r, cy + r};
				cx -= 20 * k;
			}
		}
		if (lyricRow()) {
			drawLyricRow(g, ui, t, x, y + CARD_H * k, w, k, a);
		}
		ui.alpha(1);
	}
	/**
	 * The card as the HUD shows it: the cover filling the left side, title and artist, the controls centred under them with
	 * play/pause in a chip of the theme colour, and the progress bar (with the times in the Dark style) along the bottom.
	 */
	private void drawHudCard(GuiGraphicsExtractor g, Ui ui, MediaSession.Track t, boolean sample, String title, String artist, double pos,
			double length, float x, float y, float w, float h, float k, float a, double dt) {
		float pad = 7 * k;
		float art = h - pad * 2;
		if (!sample && coverReady && cover.ready()) {
			ui.fill(x + pad, y + pad, art, art, 6 * k, 0x33000000);
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(x + pad, y + pad);
			pose.scale(art / COVER_PX, art / COVER_PX);
			g.blit(RenderPipelines.GUI_TEXTURED, cover.id(), 0, 0, 0, 0, COVER_PX, COVER_PX, COVER_PX, COVER_PX, COVER_PX, COVER_PX,
					fade(0xFFFFFFFF, a));
			pose.popMatrix();
		} else {
			ui.fill(x + pad, y + pad, art, art, 6 * k, 0x26FFFFFF);
			Icons.draw(g, Icons.Icon.MUSIC, x + pad + art / 2, y + pad + art / 2, 20 * k, fade(0xCCFFFFFF, a));
		}
		float tx = x + pad + art + 10 * k;
		float right = x + w - pad - 2 * k;
		ui.textMiddle(ellipsize(ui, title, Fonts.SEMIBOLD, 10.5f * k, right - tx), tx, y + 15 * k, 10.5f * k, fade(0xFFFFFFFF, a), Fonts.SEMIBOLD);
		ui.textMiddle(ellipsize(ui, artist, Fonts.MEDIUM, 9 * k, right - tx), tx, y + 27 * k, 9 * k, fade(0x8CFFFFFF, a), Fonts.MEDIUM);
		int accent = accent() | 0xFF000000;
		// Controls, centred over the text column.
		float mid = (tx + right) / 2;
		float cy = y + 42 * k;
		buttons[0] = buttons[1] = buttons[2] = null;
		if (controls.get()) {
			double mx = Minecraft.getInstance().mouseHandler.xpos() / Minecraft.getInstance().getWindow().getGuiScale();
			double my = Minecraft.getInstance().mouseHandler.ypos() / Minecraft.getInstance().getWindow().getGuiScale();
			boolean pointer = Minecraft.getInstance().gui.screen() instanceof ChatScreen;
			Icons.Icon[] icons = {Icons.Icon.CHEVRON_RIGHT, !sample && t.playing() ? Icons.Icon.PAUSE : Icons.Icon.PLAY, Icons.Icon.CHEVRON_RIGHT};
			float[] xs = {mid - 22 * k, mid, mid + 22 * k};
			for (int i = 0; i < 3; i++) {
				float r = 8 * k;
				boolean over = pointer && Math.abs(mx - xs[i]) <= r && Math.abs(my - cy) <= r;
				if (i == 1) {
					ui.fill(xs[i] - r, cy - r, r * 2, r * 2, 5 * k, (over ? 0x66000000 : 0x40000000) | (accent & 0xFFFFFF));
					Icons.draw(g, icons[i], xs[i], cy, 9 * k, fade(0xFFFFFFFF, a));
				} else if (i == 0) {
					Icons.drawFlipped(g, icons[i], xs[i], cy, 9 * k, fade(over ? 0xFFFFFFFF : 0xB3FFFFFF, a));
				} else {
					Icons.draw(g, icons[i], xs[i], cy, 9 * k, fade(over ? 0xFFFFFFFF : 0xB3FFFFFF, a));
				}
				buttons[i] = new float[] {xs[i] - r, cy - r, xs[i] + r, cy + r};
			}
		}
		// Progress.
		float target = length > 0 ? (float) Math.max(0, Math.min(1, pos / length)) : 0;
		progress += (target - progress) * (float) Math.min(1, dt * 10);
		if (Math.abs(target - progress) > 0.05f) {
			progress = target;
		}
		boolean dark = nz.temple.client.TempleNzClient.hudSettings() != null && nz.temple.client.TempleNzClient.hudSettings().darkHud();
		float by = y + h - pad - 4 * k;
		float bx0 = tx;
		float bx1 = right;
		if (dark && length > 0) {
			String left = clock(pos);
			String total = clock(length);
			ui.textMiddle(left, tx, by + 1.5f * k, 7 * k, fade(0x8CFFFFFF, a), Fonts.MEDIUM);
			ui.textMiddle(total, right - ui.textWidth(total, Fonts.MEDIUM, 7 * k), by + 1.5f * k, 7 * k, fade(0x8CFFFFFF, a), Fonts.MEDIUM);
			bx0 = tx + ui.textWidth(left, Fonts.MEDIUM, 7 * k) + 6 * k;
			bx1 = right - ui.textWidth(total, Fonts.MEDIUM, 7 * k) - 6 * k;
		}
		float bh = 3 * k;
		ui.fill(bx0, by, bx1 - bx0, bh, bh / 2, 0x2EFFFFFF);
		if (progress > 0.001f) {
			ui.gradient(bx0, by, Math.max(bh, (bx1 - bx0) * progress), bh, bh / 2, HudTheme.mix(accent, 0xFFFFFFFF, 0.25f), accent, 2);
		}
	}

	private void drawLyricRow(GuiGraphicsExtractor g, Ui ui, MediaSession.Track t, float x, float y, float w, float k, float a) {
		float pad = 10 * k;
		float p = 1f / Math.max(1, (float) Minecraft.getInstance().getWindow().getGuiScale());
		Draw2D.pixelRect(g, x + pad, y, x + w - pad, y + p, fade(0x1FFFFFFF, a));
		float size = 9.5f * k;
		float cy = y + LYRIC_H * k / 2;
		double now = System.nanoTime() / 1e9;
		Lyrics song = currentLyrics(t);
		String status = null;
		if (t == null) {
			status = "Lyrics show here";
		} else if (song == null) {
			LyricsSource.State state = lyrics.get(t).state();
			status = state == LyricsSource.State.LOADING ? "Finding lyrics…" : state == LyricsSource.State.UNSYNCED
					? "Only unsynced lyrics for this song" : "No lyrics for this song";
		}
		int index = song == null ? -1 : song.indexAt(songTime(t));
		if (index != barLine) {
			Lyrics.Line old = barLine >= 0 && song != null && barLine < song.lines().size() ? song.lines().get(barLine) : null;
			barPrevious = old == null ? "" : old.text();
			barLine = index;
			barChanged = now;
			barScroll = 0;
		}
		float age = (float) (now - barChanged);
		float ease = 1 - (float) Math.pow(1 - Math.min(1, age / 0.38f), 3);
		int left = Math.round(ui.mapX(x + pad));
		int right = Math.round(ui.mapX(x + w - pad));
		g.enableScissor(left, Math.round(ui.mapY(y + 1)), right, Math.round(ui.mapY(y + LYRIC_H * k)));
		float span = w - pad * 2;
		if (!barPrevious.isEmpty() && ease < 1) {
			float ox = -ease * span * 0.6f;
			float wPrev = ui.textWidth(barPrevious, Fonts.SEMIBOLD, size);
			ui.textMiddle(barPrevious, x + pad + Math.max(0, (span - wPrev) / 2) + ox, cy, size, fade(lyric(0xCC), a * (1 - ease)), Fonts.SEMIBOLD);
		}
		if (status != null) {
			float sw = ui.textWidth(status, Fonts.MEDIUM, 8.5f * k);
			ui.textMiddle(status, x + (w - sw) / 2, cy, 8.5f * k, fade(0x80FFFFFF, a), Fonts.MEDIUM);
		} else if (index < 0) {
			float bob = (float) Math.sin(now * 3) * k;
			Icons.draw(g, Icons.Icon.MUSIC, x + w / 2, cy + bob, 10 * k, fade(0x80FFFFFF, a * ease));
		} else {
			Lyrics.Line line = song.lines().get(index);
			double time = songTime(t);
			int sung = line.wordAt(time);
			float lineW = 0;
			float space = ui.textWidth(" ", Fonts.SEMIBOLD, size);
			float[] wx = new float[line.words().size()];
			for (int i = 0; i < wx.length; i++) {
				wx[i] = lineW;
				lineW += ui.textWidth(line.words().get(i).text(), Fonts.SEMIBOLD, size) + space;
			}
			lineW -= space;
			float start = x + pad + Math.max(0, (span - lineW) / 2);
			if (lineW > span && sung >= 0) {
				float want = Math.max(0, Math.min(lineW - span, wx[sung] - span * 0.4f));
				barScroll += (want - barScroll) * 0.12f;
			}
			float slide = (1 - ease) * span * 0.6f;
			int hot = accent();
			for (int i = 0; i < wx.length; i++) {
				Lyrics.Word word = line.words().get(i);
				int colour;
				if (!karaoke.get() || i < sung) {
					colour = lyric(0xFF);
				} else if (i == sung) {
					float f = (float) Math.max(0, Math.min(1, (time - word.start()) / Math.max(0.05, word.end() - word.start())));
					colour = Ui.lerpColor(Ui.lerpColor(hot | 0xFF000000, lyric(0xFF), 0.35f), lyric(0xFF), f);
				} else {
					colour = lyric(0x61);
				}
				ui.textMiddle(word.text(), start + wx[i] - barScroll + slide, cy, size, fade(colour, a * ease), Fonts.SEMIBOLD);
			}
		}
		g.disableScissor();
	}
	private boolean click(double mx, double my) {
		if (!isEnabled() || !controls.get()) {
			return false;
		}
		String[] commands = {"prev", "playpause", "next"};
		for (int i = 0; i < 3; i++) {
			float[] b = buttons[i];
			if (b != null && mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3]) {
				session.command(commands[i]);
				return true;
			}
		}
		return false;
	}
	public void renderCrosshair(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !lyricsOn.get() || !lyricStyle.is("Crosshair") || mc.player == null) {
			return;
		}
		MediaSession.Track t = track();
		Lyrics song = currentLyrics(t);
		String word = "";
		if (song != null) {
			double time = songTime(t);
			Lyrics.Line line = song.lineAt(time);
			if (line != null) {
				int i = line.wordAt(time);
				if (i >= 0) {
					word = line.words().get(i).text() + "\u0000" + i + "\u0000" + line.start();
				}
			}
		}
		double now = System.nanoTime() / 1e9;
		if (!word.equals(crossWord)) {
			crossPrevious = crossWord;
			crossWord = word;
			crossChanged = now;
		}
		var ui = nz.temple.client.TempleNzClient.ui();
		if (ui == null) {
			return;
		}
		ui.begin(g);
		ui.resetTransform();
		float k = UiScale.factor();
		float size = wordSize.getFloat() * k;
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight() / 2f + 14 * k + size * 0.5f;
		float age = (float) (now - crossChanged);
		float in = Math.min(1, age / 0.16f);
		float easeIn = 1 - (1 - in) * (1 - in) * (1 - in);
		if (!crossPrevious.isEmpty() && in < 1) {
			String prev = crossPrevious.substring(0, crossPrevious.indexOf('\u0000'));
			float pw = ui.textWidth(prev, Fonts.SEMIBOLD, size);
			ui.textMiddle(prev, cx - pw / 2, cy - easeIn * 6 * k, size, fade(lyric(0xFF), 1 - easeIn), Fonts.SEMIBOLD);
		}
		if (!crossWord.isEmpty()) {
			String text = crossWord.substring(0, crossWord.indexOf('\u0000'));
			float pop = 1 + 0.28f * (1 - easeIn);
			float s = size * pop;
			float tw = ui.textWidth(text, Fonts.SEMIBOLD, s);
			float y = cy + (1 - easeIn) * 5 * k;
			int glow = accent();
			ui.textMiddle(text, cx - tw / 2, y + 1.2f * k, s, fade(glow | 0xFF000000, 0.55f * easeIn), Fonts.SEMIBOLD);
			ui.textMiddle(text, cx - tw / 2, y, s, fade(lyric(0xFF), easeIn), Fonts.SEMIBOLD);
		}
	}
	private void submitWorld(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		var camera = ctx.levelState().cameraRenderState;
		camPos = camera.pos;
		Vector3f f = camera.orientation.transform(new Vector3f(0, 0, -1));
		camForward = new Vec3(f.x, f.y, f.z);
		if (!isEnabled() || !lyricsOn.get() || !lyricStyle.is("In world") || mc.level == null || mc.player == null) {
			world.clear();
			worldIndex = -1;
			return;
		}
		MediaSession.Track t = track();
		Lyrics song = currentLyrics(t);
		double time = song == null ? 0 : songTime(t);
		double now = System.nanoTime() / 1e9;
		String key = t == null ? "" : t.key();
		if (!key.equals(worldTrack)) {
			worldTrack = key;
			worldIndex = -1;
			for (WorldLine l : world) {
				l.exitAt = l.exitAt < 0 ? now : l.exitAt;
			}
		}
		int index = song == null ? -1 : song.indexAt(time);
		if (index != worldIndex) {
			for (WorldLine l : world) {
				if (l.exitAt < 0) {
					l.exitAt = now;
				}
			}
			worldIndex = index;
			if (index >= 0) {
				WorldLine placed = place(mc, song.lines().get(index));
				if (placed != null) {
					world.add(placed);
				}
			}
		}
		for (WorldLine l : world) {
			if (l.exitAt < 0 && time >= l.line.end()) {
				l.exitAt = now;
			}
		}
		world.removeIf(l -> l.exitAt >= 0 && now - l.exitAt > 3);
		PoseStack pose = ctx.poseStack();
		Font font = mc.font;
		int accent = accent();
		for (WorldLine l : world) {
			double exit = l.exitAt < 0 ? -1 : now - l.exitAt;
			for (Glyph gl : l.glyphs) {
				Lyrics.Word word = gl.word() < l.line.words().size() ? l.line.words().get(gl.word()) : null;
				double since = word == null ? 1 : time - word.start();
				float rise = (float) Math.max(0, Math.min(1, since / 0.22));
				float riseEase = 1 - (1 - rise) * (1 - rise) * (1 - rise);
				float alpha;
				if (song == null || l.exitAt >= 0) {
					alpha = 1;
				} else if (since >= 0) {
					alpha = 1;
				} else {
					alpha = worldGhost.get() ? 0.2f : 0;
				}
				float lift = since >= 0 || l.exitAt >= 0 ? (1 - riseEase) * -0.35f : 0;
				float fx = 0;
				float fy = lift;
				float fz = 0;
				Quaternionf spin = null;
				if (exit >= 0) {
					double e = exit - gl.delay();
					if (worldExit.is("Crumble")) {
						if (e > 0) {
							float tt = (float) e;
							fx = gl.vx() * tt;
							fy = gl.vy() * tt - 4.5f * tt * tt;
							fz = gl.vz() * tt;
							spin = new Quaternionf().rotationXYZ(gl.spin() * tt, gl.spin() * 0.7f * tt, gl.spin() * 0.4f * tt);
							alpha *= (float) Math.max(0, 1 - e / 1.4);
						}
					} else {
						alpha *= (float) Math.max(0, 1 - exit / 0.7);
						fy += (float) exit * 0.4f;
					}
				}
				if (alpha <= 0.02f) {
					continue;
				}
				int ink = lyric(0xFF);
				int base = since >= 0 && since < 0.5 && l.exitAt < 0 ? Ui.lerpColor(accent | 0xFF000000, ink, (float) (since / 0.5)) : ink;
				pose.pushPose();
				pose.translate(l.base.x - camPos.x, l.base.y - camPos.y, l.base.z - camPos.z);
				pose.mulPose(new Quaternionf().rotationY(l.yaw));
				float s = l.scale;
				float gx = (gl.x() + gl.w() / 2 - l.width / 2) * s;
				pose.translate(gx + fx, fy, fz);
				float grow = since < 0 && l.exitAt < 0 ? 1 : 0.75f + 0.25f * riseEase;
				if (spin != null) {
					pose.translate(0, 4.5f * s, 0);
					pose.mulPose(spin);
					pose.translate(0, -4.5f * s, 0);
				}
				pose.scale(s * grow, -s * grow, s * grow);
				if (worldDepth.get()) {
					int layers = 5;
					for (int i = layers; i >= 1; i--) {
						pose.pushPose();
						pose.translate(0, 0, -i * 0.45f);
						int shade = Ui.lerpColor(0xFF000000 | (base & 0xFFFFFF), 0xFF000000, 0.45f + 0.08f * i);
						ctx.submitNodeCollector().submitText(pose, -gl.w() / 2, -9, gl.text(), false, Font.DisplayMode.NORMAL, 0xF000F0,
								fade(shade, alpha), 0, 0);
						pose.popPose();
					}
				}
				ctx.submitNodeCollector().submitText(pose, -gl.w() / 2, -9, gl.text(), false, Font.DisplayMode.POLYGON_OFFSET, 0xF000F0,
						fade(base, alpha), 0, 0);
				pose.popPose();
			}
		}
	}
	private WorldLine place(Minecraft mc, Lyrics.Line line) {
		Font font = mc.font;
		ThreadLocalRandom rand = ThreadLocalRandom.current();
		List<Glyph> glyphs = new ArrayList<>();
		float cursor = 0;
		float space = font.width(styled(" "));
		for (int w = 0; w < line.words().size(); w++) {
			String word = line.words().get(w).text();
			int i = 0;
			while (i < word.length()) {
				int cp = word.codePointAt(i);
				String ch = new String(Character.toChars(cp));
				Component c = styled(ch);
				float cw = font.width(c);
				glyphs.add(new Glyph(c.getVisualOrderText(), cursor, cw, w, (float) rand.nextDouble(-0.6, 0.6),
						(float) rand.nextDouble(0.4, 1.4), (float) rand.nextDouble(-0.6, 0.6), (float) rand.nextDouble(-5, 5),
						0));
				cursor += cw;
				i += Character.charCount(cp);
			}
			cursor += space;
		}
		float width = Math.max(1, cursor - space);
		Vec3 forward = new Vec3(camForward.x, 0, camForward.z);
		if (forward.lengthSqr() < 1e-6) {
			forward = new Vec3(0, 0, 1);
		}
		forward = forward.normalize();
		rightSide = !rightSide;
		double yawOffset = Math.toRadians((rightSide ? 1 : -1) * rand.nextDouble(6, 16));
		double cos = Math.cos(yawOffset);
		double sin = Math.sin(yawOffset);
		Vec3 dir = new Vec3(forward.x * cos - forward.z * sin, 0, forward.x * sin + forward.z * cos);
		Vec3 across = new Vec3(-dir.z, 0, dir.x);
		Vec3 base = null;
		float scale = 0;
		double max = worldDistance.get();
		for (double d = max; d >= 5; d -= Math.max(2, d * 0.12)) {
			double dist = d * rand.nextDouble(0.95, 1.03);
			Vec3 spot = camPos.add(dir.scale(dist));
			double ground = groundBelow(mc, spot.x, spot.z, camPos.y);
			Vec3 at = Double.isNaN(ground) ? new Vec3(spot.x, camPos.y - 1.2, spot.z) : new Vec3(spot.x, ground, spot.z);
			float s = lineScale(at.distanceTo(camPos), width);
			if (roomFor(mc, at, across, width * s, 10 * s) && inView(mc, at, across, width * s, 10 * s)) {
				base = at;
				scale = s;
				break;
			}
		}
		if (base == null) {
			Vec3 ray = dir.add(0, -0.28, 0).normalize();
			BlockHitResult hit = mc.level.clip(new ClipContext(camPos, camPos.add(ray.scale(max)), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.ANY, mc.player));
			double reach = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(camPos) : max;
			base = hit.getType() == HitResult.Type.BLOCK && reach > 4 ? hit.getLocation()
					: camPos.add(dir.scale(Math.max(5, Math.min(reach - 1, 8)))).add(0, -1.2, 0);
			scale = lineScale(base.distanceTo(camPos), width);
		}
		double dx = camPos.x - base.x;
		double dz = camPos.z - base.z;
		float viewerYaw = (float) Math.atan2(-(-dx), -dz);
		float yaw = (float) Math.PI - viewerYaw;
		WorldLine l = new WorldLine(line, base.add(0, 0.02, 0), yaw, scale, width);
		for (Glyph g : glyphs) {
			l.glyphs.add(new Glyph(g.text(), g.x(), g.w(), g.word(), g.vx(), g.vy(), g.vz(), g.spin(), g.x() / width * 0.45f));
		}
		return l;
	}
	private float lineScale(double dist, float width) {
		float k = worldSize.getFloat() / 100f;
		float letters = (float) (0.0062 * k * dist);
		float fit = (float) (0.95 * dist / width);
		return Math.max(0.02f, Math.min(letters, fit));
	}
	private static double groundBelow(Minecraft mc, double x, double z, double eyeY) {
		var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		boolean air = false;
		for (int y = (int) Math.floor(eyeY) + 6; y >= (int) Math.floor(eyeY) - 24; y--) {
			pos.set(bx, y, bz);
			var shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
			if (shape.isEmpty()) {
				air = true;
			} else if (air) {
				return y + shape.max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return Double.NaN;
	}

	private static boolean roomFor(Minecraft mc, Vec3 at, Vec3 across, double w, double h) {
		var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		int columns = Math.max(2, (int) Math.ceil(w / 0.8));
		int rows = Math.max(1, (int) Math.ceil(h / 0.8));
		for (int i = 0; i <= columns; i++) {
			double side = (i / (double) columns - 0.5) * w;
			for (int j = 0; j <= rows; j++) {
				double up = 0.15 + j / (double) rows * (h + 0.3);
				pos.set(Math.floor(at.x + across.x * side), Math.floor(at.y + up), Math.floor(at.z + across.z * side));
				if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}
	private boolean inView(Minecraft mc, Vec3 at, Vec3 across, double w, double h) {
		int columns = Math.max(4, Math.min(9, (int) Math.ceil(w / 1.5)));
		for (int i = 0; i <= columns; i++) {
			Vec3 foot = at.add(across.scale((i / (double) columns - 0.5) * w * 0.96));
			for (double up : new double[] {0.2, 0.55, 0.95}) {
				Vec3 mark = foot.add(0, h * up, 0);
				BlockHitResult hit = mc.level.clip(new ClipContext(camPos, mark, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
				if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(mark) > 0.6 * 0.6) {
					return false;
				}
			}
		}
		return true;
	}
	private int lyric(int alpha) {
		return (alpha << 24) | (lyricColor.get() & 0xFFFFFF);
	}
	private static Component styled(String s) {
		return Component.literal(s).withStyle(st -> st.withFont(Fonts.SEMIBOLD));
	}
	private static String clock(double seconds) {
		int s = (int) Math.max(0, Math.round(seconds));
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}
	private static int fade(int argb, float a) {
		return (Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a))) << 24) | (argb & 0xFFFFFF);
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
}
