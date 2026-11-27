package nz.temple.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;
import nz.temple.anim.Spring;
import nz.temple.client.input.KeyNames;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Ui;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Temple's HUD: frosted glass pills and cards tinted by the HUD theme, rolling numbers, a gliding module list and a glass hotbar.
 * The design (layout, sizes, colours and the glass recipe) is by x.monolith, rebuilt on Temple's renderer so it works the same
 * on OpenGL and Vulkan. Every size here is in HUD units; {@code k} turns them into GUI pixels.
 */
public final class AzuraHud {
	private static final DateTimeFormatter CLOCK_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter CLOCK_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
	private static final FontDescription MEDIUM = Fonts.MEDIUM;
	private static final FontDescription SEMIBOLD = Fonts.SEMIBOLD;
	private static final FontDescription BOLD = Fonts.BOLD;
	public static final float LIFT = 6;
	/** The Dark style's panel colour. */
	private static final int DARK = 0xE80D0E12;

	private static HudModule active;

	private final HudOverlay host;
	private final Ui ui;
	private final HudModule settings;
	private final ModuleManager modules;
	private float now;
	private float dt;
	private float light = 0.5f;

	AzuraHud(HudOverlay host, Ui ui, HudModule settings, ModuleManager modules) {
		this.host = host;
		this.ui = ui;
		this.settings = settings;
		this.modules = modules;
		active = settings;
	}

	/** A value that eases toward its target. */
	private static final class Smooth {
		float v;
		float target;
		final float speed;
		boolean placed;

		Smooth(float v, float speed) {
			this.v = v;
			this.target = v;
			this.speed = speed;
		}

		float to(float t, float dt) {
			target = t;
			if (!placed) {
				v = t;
				placed = true;
			}
			v += (target - v) * (1 - (float) Math.exp(-speed * dt));
			return v;
		}

		void snap(float t) {
			v = t;
			target = t;
			placed = true;
		}
	}

	void frame(float dt, float light) {
		this.dt = Math.max(0, Math.min(0.25f, dt));
		this.now += this.dt;
		this.light = light;
		settings.syncTheme();
		HudTheme.update(settings.hudTheme.get(), settings.hudColor.get());
		float s = settings.panelTint.getFloat() / 100f;
		int tint = HudTheme.tint();
		nz.temple.client.render.GlassRenderer.hudGlassColours(HudTheme.alpha(HudTheme.mix(tint, 0xFFFFFFFF, 0.18f), 0.20f * s),
				HudTheme.alpha(HudTheme.mix(tint, 0xFF000000, 0.25f), 0.32f * s), HudTheme.light(), 0.2f * s);
	}

	/* ---------------------------------------------------------------- the glass ---------------------------------------- */

	/**
	 * The panel material: Temple's frosted glass, then (as in x.monolith's glass pass) a dark shade, a theme tint that is
	 * lighter at the top and darker at the bottom, a faint top highlight, a light rim that is brighter along the top edge, and
	 * a soft drop shadow underneath.
	 */
	void panel(Ui ui, float x, float y, float w, float h, float r) {
		float s = settings.panelTint.getFloat() / 100f;
		if (ui.alpha() <= 0.003f) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		if (dark()) {
			// Matte black: one flat colour, no rim, and a soft shadow to lift it off bright scenes.
			ui.shadow(x, y + 2, w, h, r, 8, 0x40000000);
			ui.fill(x, y, w, h, r, 0xF20B0B0B);
			return;
		}
		ui.shadow(x, y + 5, w, h, r, 10, HudTheme.alpha(0xFF02060F, 0.30f * s));
		ui.hudGlass(x, y, w, h, r);
	}

	/* ---------------------------------------------------------------- helpers ------------------------------------------ */

	private void icon(GuiGraphicsExtractor g, Icons.Icon icon, float cx, float cy, float size, int argb) {
		Icons.draw(g, icon, ui.mapX(cx), ui.mapY(cy), size * scale(), HudTheme.alpha(argb, ui.alpha()));
	}

	private float scale() {
		return ui.mapX(1) - ui.mapX(0);
	}

	private void line(float x, float y, float w, int argb) {
		ui.fill(x, y, w, 1, 0, argb);
	}

	private void vline(float x, float cy, float half, int argb) {
		ui.fill(x, cy - half, 1, half * 2, 0.5f, argb);
	}

	/** Upper-case text with extra letter spacing. */
	private void tracked(String s, float x, float cy, float size, float spacing, int argb, FontDescription font) {
		float cursor = x;
		for (int i = 0; i < s.length(); i++) {
			String c = String.valueOf(s.charAt(i));
			ui.textMiddle(c, cursor, cy, size, argb, font);
			cursor += ui.textWidth(c, font, size) + spacing;
		}
	}

	private float trackedWidth(String s, float size, float spacing, FontDescription font) {
		float w = 0;
		for (int i = 0; i < s.length(); i++) {
			w += ui.textWidth(String.valueOf(s.charAt(i)), font, size) + spacing;
		}
		return Math.max(0, w - spacing);
	}

	private String fit(String s, float max, float size, FontDescription font) {
		if (ui.textWidth(s, font, size) <= max) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && ui.textWidth(out + "…", font, size) > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out + "…";
	}

	private void head(GuiGraphicsExtractor g, PlayerSkin skin, float x, float y, float size) {
		if (skin == null) {
			return;
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(ui.mapX(x), ui.mapY(y));
		float px = size * scale() / 8f;
		pose.scale(px, px);
		PlayerFaceExtractor.extractRenderState(g, skin, 0, 0, 8);
		pose.popMatrix();
	}

	private static int latency(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) {
			return 0;
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	/* ---------------------------------------------------------------- info bar ----------------------------------------- */

	private final RollingText fps = new RollingText();
	private final RollingText ping = new RollingText();
	private final RollingText clock = new RollingText();
	private final RollingText speedText = new RollingText();
	private final Smooth infoWidth = new Smooth(0, 16);
	private float sampleTimer = 1;

	private record Segment(Icons.Icon icon, RollingText rolling, String suffix, String text) {
	}

	private final RollingText tpsText = new RollingText();
	/** Where the next centred top widget goes by default (GUI pixels), set by the overlay as it stacks them. */
	float stackY;

	boolean dark() {
		return settings.darkHud();
	}

	/** What the info bar can show, in their default order. */
	public enum BarItem {
		LOGO("Logo", Icons.Icon.SPARKLES), PLAYER("Player", Icons.Icon.USER), PING("Ping", Icons.Icon.WIFI), FPS("FPS", Icons.Icon.MONITOR),
		SPEED("Speed", Icons.Icon.GAUGE), SERVER("Server", Icons.Icon.CLOUD), TPS("TPS", Icons.Icon.SIGNAL),
		COORDS("Coords", Icons.Icon.NAVIGATION), DATE("Date", Icons.Icon.LAYOUT), TIME("Time", Icons.Icon.CLOCK);

		public final String label;
		public final Icons.Icon icon;

		BarItem(String label, Icons.Icon icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
	private final RollingText coordsText = new RollingText();
	private final Map<BarItem, Smooth> itemX = new java.util.EnumMap<>(BarItem.class);
	/** This frame's item spots on screen: {left, right} in GUI pixels, and the bar and its grip and tray. */
	private final Map<BarItem, float[]> itemRects = new java.util.EnumMap<>(BarItem.class);
	private final Map<BarItem, float[]> trayRects = new java.util.EnumMap<>(BarItem.class);
	private float[] barRect;
	private float[] gripRect;
	private BarItem dragItem;
	private boolean dragFromTray;
	private double dragX;
	private double dragY;

	private nz.temple.setting.BoolSetting shownSetting(BarItem item) {
		return switch (item) {
			case LOGO -> settings.infoLogo;
			case PLAYER -> settings.infoUser;
			case PING -> settings.infoPing;
			case FPS -> settings.fps;
			case SPEED -> settings.speed;
			case SERVER -> settings.infoServer;
			case TPS -> settings.infoTps;
			case COORDS -> settings.infoCoords;
			case DATE -> settings.infoDate;
			case TIME -> settings.infoTime;
		};
	}

	private double order(BarItem item) {
		var config = host.config();
		return config == null ? item.ordinal() : config.getExtra("hud.bar.order." + item.name(), item.ordinal());
	}

	/** The items shown on the bar, in order. */
	public List<BarItem> barItems() {
		List<BarItem> out = new ArrayList<>();
		for (BarItem item : BarItem.values()) {
			if (shownSetting(item).get()) {
				out.add(item);
			}
		}
		out.sort(Comparator.comparingDouble(this::order));
		return out;
	}

	/** The items not on the bar (shown in the HUD editor's tray). */
	public List<BarItem> hiddenItems() {
		List<BarItem> out = new ArrayList<>();
		for (BarItem item : BarItem.values()) {
			if (!shownSetting(item).get()) {
				out.add(item);
			}
		}
		return out;
	}

	private void saveOrder(List<BarItem> items) {
		var config = host.config();
		if (config == null) {
			return;
		}
		for (int i = 0; i < items.size(); i++) {
			config.putExtra("hud.bar.order." + items.get(i).name(), i);
		}
	}

	/** The bar item, or tray item, under the mouse (GUI pixels), or null. */
	public BarItem itemAt(double mx, double my) {
		if (barRect != null && my >= barRect[1] && my <= barRect[1] + barRect[3]) {
			for (Map.Entry<BarItem, float[]> e : itemRects.entrySet()) {
				if (mx >= e.getValue()[0] && mx <= e.getValue()[1]) {
					return e.getKey();
				}
			}
		}
		for (Map.Entry<BarItem, float[]> e : trayRects.entrySet()) {
			float[] r = e.getValue();
			if (mx >= r[0] && my >= r[1] && mx <= r[0] + r[2] && my <= r[1] + r[3]) {
				return e.getKey();
			}
		}
		return null;
	}

	/** The bar this frame ({x, y, w, h} in GUI pixels), or null. */
	public float[] barRect() {
		return barRect;
	}

	/** Where an item is on the bar ({left, right}) or in the tray ({x, y, w, h}) this frame, or null. */
	public float[] itemRect(BarItem item) {
		float[] r = itemRects.get(item);
		return r != null ? r : trayRects.get(item);
	}

	/** Whether (mx, my) is on the bar's grip, which moves the whole bar. */
	public boolean onGrip(double mx, double my) {
		float[] r = gripRect;
		return r != null && mx >= r[0] && my >= r[1] && mx <= r[0] + r[2] && my <= r[1] + r[3];
	}

	public void beginItemDrag(BarItem item, double mx, double my) {
		dragItem = item;
		dragFromTray = !shownSetting(item).get();
		dragX = mx;
		dragY = my;
	}

	public void dragItem(double mx, double my) {
		dragX = mx;
		dragY = my;
	}

	public boolean draggingItem() {
		return dragItem != null;
	}

	/** Drops the dragged item: onto the bar at the spot it was shown in, or off the bar to hide it. */
	public void dropItem(double mx, double my) {
		if (dragItem == null) {
			return;
		}
		BarItem item = dragItem;
		dragItem = null;
		if (overBar(mx, my)) {
			List<BarItem> items = barItems();
			items.remove(item);
			items.add(Math.min(items.size(), insertIndex(mx)), item);
			shownSetting(item).set(true);
			saveOrder(items);
		} else if (!dragFromTray && barItems().size() > 1) {
			shownSetting(item).set(false);
		}
	}

	private boolean overBar(double mx, double my) {
		return barRect != null && mx >= barRect[0] - 40 && mx <= barRect[0] + barRect[2] + 40
				&& Math.abs(my - (barRect[1] + barRect[3] / 2)) <= barRect[3] * 1.4f;
	}

	/** Where the dragged item would go: the number of other items whose middle is left of the mouse. */
	private int insertIndex(double mx) {
		int index = 0;
		for (Map.Entry<BarItem, float[]> e : itemRects.entrySet()) {
			if (e.getKey() != dragItem && (e.getValue()[0] + e.getValue()[1]) / 2 < mx) {
				index++;
			}
		}
		return index;
	}

	/**
	 * The info bar: a row of items (logo, player, ping, FPS, speed, server, TPS, coordinates, date, time) in the order you set by
	 * dragging them in the HUD editor. Glass separates them with fine dividers; Dark sits at the very top of the screen.
	 */
	void infoBar(GuiGraphicsExtractor g, Minecraft mc, int gw, int gh, float k) {
		boolean dark = dark();
		boolean editing = HudOverlay.editing();
		final float height = 28;
		final float pad = 5;
		final float size = dark ? 11.5f : 11;
		final float gap = dark ? 7 : 11;
		final float iconSize = 12;
		sampleTimer += dt;
		if (sampleTimer >= 0.4f || fps.text().isEmpty()) {
			sampleTimer = 0;
			fps.set(Integer.toString(mc.getFps()), now);
			ping.set(Integer.toString(latency(mc)), now);
			double dx = mc.player.getX() - mc.player.xo;
			double dz = mc.player.getZ() - mc.player.zo;
			speedText.set(String.format(Locale.ROOT, "%.1f", Math.sqrt(dx * dx + dz * dz) * 20), now);
			tpsText.set(String.format(Locale.ROOT, "%.0f", nz.temple.client.util.ServerTps.tps()), now);
		}
		clock.set(LocalTime.now().format(settings.twentyFourHour.get() ? CLOCK_24 : CLOCK_12), now);
		coordsText.set(nz.temple.client.modules.client.StreamerMode.x((int) Math.floor(mc.player.getX())) + ", "
				+ (int) Math.floor(mc.player.getY()) + ", " + nz.temple.client.modules.client.StreamerMode.z((int) Math.floor(mc.player.getZ())), now);
		ServerData data = mc.getCurrentServer();
		String server = data != null ? data.ip : mc.hasSingleplayerServer() ? "Singleplayer" : "Offline";
		server = nz.temple.client.modules.client.StreamerMode.hidesCoords() ? "Hidden" : server;
		String name = nz.temple.client.modules.client.StreamerMode.myName(mc.player.getName().getString());
		String date = java.time.LocalDate.now().format(DATE);

		// The items in order; ping and TPS only mean something on a server, but stay visible in the editor so they can be moved.
		List<BarItem> items = barItems();
		if (dragItem != null) {
			items.remove(dragItem);
			if (overBar(dragX, dragY)) {
				items.add(Math.min(items.size(), insertIndex(dragX)), dragItem);
			}
		}
		if (!editing) {
			items.removeIf(item -> (item == BarItem.PING || item == BarItem.TPS) && data == null);
		}
		Map<BarItem, Segment> segs = new java.util.EnumMap<>(BarItem.class);
		for (BarItem item : items) {
			segs.put(item, switch (item) {
				case PING -> new Segment(Icons.Icon.WIFI, ping, "ms", null);
				case FPS -> new Segment(Icons.Icon.MONITOR, fps, "FPS", null);
				case SPEED -> new Segment(Icons.Icon.GAUGE, speedText, "b/s", null);
				case SERVER -> new Segment(Icons.Icon.CLOUD, null, null, fit(server, 150, size, SEMIBOLD));
				case TPS -> new Segment(Icons.Icon.SIGNAL, tpsText, "TPS", null);
				case COORDS -> new Segment(Icons.Icon.NAVIGATION, coordsText, null, null);
				case DATE -> new Segment(Icons.Icon.LAYOUT, null, null, date);
				case TIME -> new Segment(Icons.Icon.CLOCK, clock, null, null);
				default -> null;
			});
		}
		float logoW = 20;
		float[] widths = new float[items.size()];
		float target = pad * 2 + 6;
		for (int i = 0; i < items.size(); i++) {
			BarItem item = items.get(i);
			widths[i] = switch (item) {
				case LOGO -> logoW;
				case PLAYER -> 17 + 7 + ui.textWidth(name, SEMIBOLD, size);
				default -> segmentWidth(segs.get(item), size, iconSize);
			};
			target += widths[i] + (i == 0 ? 0 : dark ? gap * 2 : gap * 2 + 1);
		}
		if (items.isEmpty()) {
			target = 60;
		}
		float w = infoWidth.to(target, dt);
		float[] p = host.placeAt("watermark", (gw - w * k) / 2, (dark ? 4 : 38) * k, w * k, height * k, gw, gh);
		barRect = new float[] {p[0], p[1], w * k, height * k};
		itemRects.clear();
		ui.transform(p[0], p[1], k, k);
		ui.glass(0, 0, w, height, dark ? 9 : height / 2);
		ui.clip(0, 0, w, height);
		float cy = height / 2;
		float cursor = items.isEmpty() || items.get(0) == BarItem.LOGO ? pad : pad + 6;
		for (int i = 0; i < items.size(); i++) {
			BarItem item = items.get(i);
			if (i > 0) {
				cursor += gap;
				if (!dark) {
					float dividerX = cursor;
					vline(dividerX, cy, 6.5f, 0x29FFFFFF);
					cursor += 1;
				}
				cursor += gap;
			}
			float x = itemX.computeIfAbsent(item, key -> new Smooth(0, 16)).to(cursor, dt);
			boolean ghost = item == dragItem;
			float wasAlpha = ui.alpha();
			if (ghost) {
				ui.alpha(wasAlpha * 0.35f);
			}
			drawItem(g, mc, item, segs.get(item), x, cy, size, iconSize, logoW, name, dark);
			if (ghost) {
				ui.alpha(wasAlpha);
			}
			itemRects.put(item, new float[] {ui.mapX(x - gap / 2), ui.mapX(x + widths[i] + gap / 2)});
			cursor += widths[i];
		}
		if (items.isEmpty()) {
			ui.textMiddle("Empty", 14, cy, 10, HudTheme.TEXT_FAINT, MEDIUM);
		}
		ui.unclip();
		if (editing) {
			drawGrip(g, w, k);
		} else {
			gripRect = null;
		}
		ui.resetTransform();
		trayAnchor = editing ? new float[] {p[0] + w * k / 2, p[1] + height * k + 8 * k, k} : null;
		if (!editing) {
			trayRects.clear();
		}
	}

	private float[] trayAnchor;

	/** The HUD editor's tray and the dragged item, drawn after every widget so nothing covers them. */
	void editorOverlay(GuiGraphicsExtractor g, float below) {
		if (trayAnchor == null) {
			trayRects.clear();
			return;
		}
		drawTray(g, trayAnchor[0], Math.max(trayAnchor[1], below), trayAnchor[2]);
		if (dragItem != null) {
			drawFloating(g, trayAnchor[2]);
		}
	}

	private void drawItem(GuiGraphicsExtractor g, Minecraft mc, BarItem item, Segment s, float x, float cy, float size, float iconSize,
			float logoW, String name, boolean dark) {
		switch (item) {
			case LOGO -> {
				if (!dark) {
					ui.gradient(x, cy - 10, logoW, 20, 6, 0x33E9B949, 0x14E9B949, 1);
					ui.ring(x, cy - 10, logoW, 20, 6, 0.75f, 0x40E9B949);
				}
				float logo = 14;
				nz.temple.client.render.LogoMark.draw3d(g, "hud", ui.mapX(x + (logoW - logo) / 2), ui.mapY(cy - logo / 2), logo * scale(),
						ui.alpha());
			}
			case PLAYER -> {
				ui.fill(x, cy - 8.5f, 17, 17, 4.5f, 0x33FFFFFF);
				head(g, mc.player.getSkin(), x, cy - 8.5f, 17);
				ui.textMiddle(name, x + 24, cy, size, HudTheme.TEXT, SEMIBOLD);
			}
			default -> {
				icon(g, s.icon(), x + iconSize / 2, cy, iconSize + 1, HudTheme.alpha(HudTheme.light(), dark ? 1 : 0.9f));
				float c = x + iconSize + 6;
				if (s.rolling() != null) {
					s.rolling().draw(ui, c, cy, size, SEMIBOLD, HudTheme.TEXT, false, now);
					c += s.rolling().width(ui, size, SEMIBOLD);
				} else {
					ui.textMiddle(s.text(), c, cy, size, HudTheme.TEXT, SEMIBOLD);
				}
				if (s.suffix() != null) {
					ui.textMiddle(s.suffix(), c + 4, cy, size, HudTheme.TEXT, SEMIBOLD);
				}
			}
		}
	}

	/** The grip above the bar in the HUD editor: drag it to move the whole bar. */
	private void drawGrip(GuiGraphicsExtractor g, float w, float k) {
		float gw2 = 34;
		float gh2 = 10;
		float gx = (w - gw2) / 2;
		float gy = -gh2 - 3;
		ui.fill(gx, gy, gw2, gh2, 5, HudTheme.alpha(HudTheme.deep(), 0.9f));
		for (int i = 0; i < 3; i++) {
			ui.fill(gx + 11 + i * 4.5f, gy + 4, 2.4f, 2.4f, 1.2f, 0xD9FFFFFF);
		}
		gripRect = new float[] {ui.mapX(gx), ui.mapY(gy), gw2 * k, gh2 * k};
	}

	/** The tray under the bar in the HUD editor: items that are not on the bar, ready to be dragged onto it. */
	private void drawTray(GuiGraphicsExtractor g, float centreX, float top, float k) {
		trayRects.clear();
		List<BarItem> hidden = hiddenItems();
		if (dragItem != null && !dragFromTray) {
			hidden.remove(dragItem);
		}
		float h = 18;
		float gap = 5;
		float total = 0;
		float[] widths = new float[hidden.size()];
		for (int i = 0; i < hidden.size(); i++) {
			widths[i] = 22 + ui.textWidth(hidden.get(i).label, MEDIUM, 9.5f);
			total += widths[i] + (i == 0 ? 0 : gap);
		}
		boolean dropping = dragItem != null && !dragFromTray && !overBar(dragX, dragY);
		ui.transform(0, 0, k, k);
		float cx = centreX / k;
		float ty = top / k;
		if (hidden.isEmpty()) {
			String hint = dropping ? "Let go to hide it" : "Drag items off the bar to hide them";
			float hw = ui.textWidth(hint, MEDIUM, 9.5f) + 20;
			ui.fill(cx - hw / 2, ty, hw, h, h / 2, dropping ? HudTheme.alpha(HudTheme.BAD, 0.25f) : 0x8C0D0E12);
			ui.ring(cx - hw / 2, ty, hw, h, h / 2, 0.75f, 0x33FFFFFF);
			ui.textMiddle(hint, cx - hw / 2 + 10, ty + h / 2, 9.5f, HudTheme.TEXT_DIM, MEDIUM);
		} else {
			String label = dropping ? "Let go to hide" : "Hidden";
			float lw = ui.textWidth(label, SEMIBOLD, 9) + 10;
			float x = cx - (total + lw) / 2 + lw;
			ui.fill(x - lw - 6, ty - 4, total + lw + 12, h + 8, (h + 8) / 2,
					dropping ? HudTheme.alpha(HudTheme.BAD, 0.30f) : 0xCC0A0B0E);
			ui.ring(x - lw - 6, ty - 4, total + lw + 12, h + 8, (h + 8) / 2, 0.75f, 0x26FFFFFF);
			ui.textMiddle(label, x - lw + 2, ty + h / 2, 9, HudTheme.TEXT_DIM, SEMIBOLD);
			for (int i = 0; i < hidden.size(); i++) {
				BarItem item = hidden.get(i);
				ui.fill(x, ty, widths[i], h, h / 2, 0xB30D0E12);
				ui.ring(x, ty, widths[i], h, h / 2, 0.75f, HudTheme.alpha(HudTheme.light(), 0.45f));
				icon(g, item.icon, x + 10, ty + h / 2, 9, HudTheme.light());
				ui.textMiddle(item.label, x + 18, ty + h / 2, 9.5f, HudTheme.TEXT, MEDIUM);
				trayRects.put(item, new float[] {ui.mapX(x), ui.mapY(ty), widths[i] * k, h * k});
				x += widths[i] + gap;
			}
		}
		ui.resetTransform();
	}

	/** The item being dragged, following the mouse. */
	private void drawFloating(GuiGraphicsExtractor g, float k) {
		float h = 20;
		float w = 26 + ui.textWidth(dragItem.label, SEMIBOLD, 10.5f);
		ui.transform((float) dragX - w * k / 2, (float) dragY - h * k / 2, k, k);
		ui.shadow(0, 2, w, h, h / 2, 8, 0x66000000);
		ui.gradient(0, 0, w, h, h / 2, HudTheme.alpha(HudTheme.light(), 0.95f), HudTheme.alpha(HudTheme.deep(), 0.95f), 3);
		icon(g, dragItem.icon, 11, h / 2, 10, 0xFFFFFFFF);
		ui.textMiddle(dragItem.label, 20, h / 2, 10.5f, 0xFFFFFFFF, SEMIBOLD);
		ui.resetTransform();
	}

	private float segmentWidth(Segment s, float size, float iconSize) {
		float value = s.rolling() != null ? s.rolling().width(ui, size, SEMIBOLD) : ui.textWidth(s.text(), SEMIBOLD, size);
		float tail = s.suffix() != null ? 4 + ui.textWidth(s.suffix(), SEMIBOLD, size) : 0;
		return iconSize + 6 + value + tail;
	}

	/* ---------------------------------------------------------------- coordinates -------------------------------------- */

	private final RollingText cx = new RollingText();
	private final RollingText cyText = new RollingText();
	private final RollingText cz = new RollingText();
	private final Smooth coordWidth = new Smooth(0, 16);
	private float compass = Float.NaN;

	void coordinates(GuiGraphicsExtractor g, Minecraft mc, int gw, int gh, float k, float partial) {
		if (dark()) {
			darkCoordinates(g, mc, gw, gh, k, partial);
			return;
		}
		final float height = 24;
		final float pad = 12;
		final float label = 9.5f;
		final float value = 11;
		cx.set(nz.temple.client.modules.client.StreamerMode.x((int) Math.floor(mc.player.getX())), now);
		cyText.set(Integer.toString((int) Math.floor(mc.player.getY())), now);
		cz.set(nz.temple.client.modules.client.StreamerMode.z((int) Math.floor(mc.player.getZ())), now);
		float wanted = 180 - mc.player.getViewYRot(partial);
		if (Float.isNaN(compass)) {
			compass = wanted;
		}
		float diff = ((wanted - compass) % 360 + 540) % 360 - 180;
		compass += diff * (1 - (float) Math.exp(-16 * dt));
		float target = pad * 2 + 12 + 8 + axisWidth("x", cx, label, value) + 12 + axisWidth("y", cyText, label, value) + 12
				+ axisWidth("z", cz, label, value);
		float w = coordWidth.to(target, dt);
		float[] p = host.placeAt("info", (gw - w * k) / 2, 8 * k, w * k, height * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		ui.glass(0, 0, w, height, height / 2);
		ui.clip(0, 0, w, height);
		float mid = height / 2;
		float cursor = pad;
		var pose = g.pose();
		pose.pushMatrix();
		float ax = ui.mapX(cursor + 6);
		float ay = ui.mapY(mid);
		pose.translate(ax, ay);
		pose.rotate((float) Math.toRadians(compass));
		pose.translate(-ax, -ay);
		icon(g, Icons.Icon.NAVIGATION, cursor + 6, mid, 12, HudTheme.light());
		pose.popMatrix();
		cursor += 20;
		cursor = axis("x", cx, cursor, mid, label, value) + 12;
		cursor = axis("y", cyText, cursor, mid, label, value) + 12;
		axis("z", cz, cursor, mid, label, value);
		ui.unclip();
		ui.resetTransform();
	}

	private float axisWidth(String label, RollingText v, float ls, float vs) {
		return ui.textWidth(label, SEMIBOLD, ls) + 4 + v.width(ui, vs, BOLD);
	}

	private float axis(String label, RollingText v, float cursor, float cy, float ls, float vs) {
		ui.textMiddle(label, cursor, cy, ls, HudTheme.alpha(HudTheme.light(), 0.85f), SEMIBOLD);
		float lw = ui.textWidth(label, SEMIBOLD, ls) + 4;
		v.draw(ui, cursor + lw, cy, vs, BOLD, HudTheme.TEXT, false, now);
		return cursor + lw + v.width(ui, vs, BOLD);
	}

	/* ---------------------------------------------------------------- module list -------------------------------------- */

	private static final class Row {
		final Smooth presence = new Smooth(0, 14);
		final Smooth position = new Smooth(0, 16);
		float width;
		boolean wanted;
	}

	private final Map<Module, Row> rows = new LinkedHashMap<>();
	private final Smooth listWidth = new Smooth(60, 16);
	private final Smooth listHeight = new Smooth(17, 16);

	private String info(Module m) {
		if (!settings.moduleInfo.get()) {
			return null;
		}
		String info = m.info();
		return info == null || info.isBlank() ? null : info;
	}

	private float labelWidth(Module m, float size) {
		float w = ui.textWidth(m.displayName(), SEMIBOLD, size);
		String info = info(m);
		return info == null ? w : w + size * 0.4f + ui.textWidth(info, MEDIUM, size);
	}

	void moduleList(int gw, int gh, float k) {
		final float rowH = 17;
		final float size = 10.5f;
		final float pad = 8;
		List<Module> wanted = settings.moduleList.get() ? new ArrayList<>(modules.moduleList()) : new ArrayList<>();
		if (settings.listHideVisuals.get()) {
			wanted.removeIf(m -> m.category() == nz.temple.module.Category.RENDER || m.category() == nz.temple.module.Category.COSMETICS);
		}
		if (wanted.isEmpty() && HudOverlay.editing() && settings.moduleList.get()) {
			wanted.add(settings);
		}
		ui.transform(0, 0, k, k);
		Map<Module, Float> widths = new HashMap<>();
		for (Module m : wanted) {
			widths.put(m, labelWidth(m, size) + pad * 2);
		}
		if (settings.listSort.is("A-Z")) {
			wanted.sort(Comparator.comparing(Module::displayName));
		} else {
			wanted.sort(Comparator.comparingDouble((Module m) -> -widths.get(m)));
		}
		rows.values().forEach(r -> r.wanted = false);
		float maxW = 40;
		for (int i = 0; i < wanted.size(); i++) {
			Module m = wanted.get(i);
			Row r = rows.computeIfAbsent(m, key -> new Row());
			if (!r.presence.placed) {
				r.position.snap(i);
				r.presence.snap(0);
			}
			r.wanted = true;
			r.position.target = i;
			r.width = widths.get(m);
			maxW = Math.max(maxW, r.width);
		}
		for (Row r : rows.values()) {
			r.presence.to(r.wanted ? 1 : 0, dt);
			r.position.to(r.position.target, dt);
		}
		rows.entrySet().removeIf(e -> !e.getValue().wanted && e.getValue().presence.v < 0.02f);
		float w = listWidth.to(maxW, dt);
		float h = listHeight.to(Math.max(1, wanted.size()) * rowH, dt);
		ui.resetTransform();
		if (rows.isEmpty()) {
			host.placeAt("modules", gw - (w + 10) * k, 10 * k, w * k, h * k, gw, gh);
			return;
		}
		float[] p = host.placeAt("modules", gw - (w + 10) * k, 10 * k, w * k, h * k, gw, gh);
		boolean right = p[0] + w * k / 2 > gw / 2f;
		for (Map.Entry<Module, Row> e : rows.entrySet()) {
			Module m = e.getKey();
			Row r = e.getValue();
			float presence = Math.max(0, Math.min(1, r.presence.v));
			if (presence < 0.01f) {
				continue;
			}
			float rowY = r.position.v * rowH;
			float rowW = r.width;
			float rowX = right ? w - rowW : 0;
			float phase = now * 1.1f - r.position.v * 0.55f;
			float wave = (float) (Math.sin(phase) + 1) * 0.5f;
			float swing = (float) (Math.sin(phase * 0.5f + 1.3f) + 1) * 0.5f;
			int color = HudTheme.mix(HudTheme.mix(HudTheme.light(), HudTheme.accent(), wave), HudTheme.deep(), swing * 0.4f);
			color = HudTheme.mix(color, 0xFFFFFFFF, dark() ? 0.22f : 0.38f);
			ui.transform(p[0] + (right ? 1 : -1) * (1 - presence) * 14 * k, p[1], k, k);
			ui.alpha(presence);
			ui.glass(rowX, rowY, rowW, rowH - 2, (rowH - 2) / 2);
			float tx = rowX + pad;
			float cy = rowY + (rowH - 2) / 2;
			ui.textMiddle(m.displayName(), tx, cy, size, color, SEMIBOLD);
			String info = info(m);
			if (info != null) {
				ui.textMiddle(info, tx + ui.textWidth(m.displayName(), SEMIBOLD, size) + size * 0.4f, cy, size, HudTheme.TEXT_DIM, MEDIUM);
			}
		}
		ui.alpha(1);
		ui.resetTransform();
	}

	/* ---------------------------------------------------------------- keybinds ----------------------------------------- */

	private List<Module> bound = List.of();
	private long boundAt;
	private final Smooth keysHeight = new Smooth(40, 16);

	float keybinds(GuiGraphicsExtractor g, float top, int gw, int gh, float k) {
		if (dark()) {
			return darkKeybinds(g, top, gw, gh, k);
		}
		long nanos = System.nanoTime();
		if (nanos - boundAt > 250_000_000L) {
			boundAt = nanos;
			bound = modules.all().stream().filter(m -> m.keybind().isBound() && (!settings.keybindsOnlyOn.get() || m.isEnabled()))
					.sorted(Comparator.comparing(Module::displayName)).limit(Math.round(settings.keybindsMax.get())).toList();
		}
		if (bound.isEmpty() && !HudOverlay.editing()) {
			return top - 6 * k;
		}
		final float header = 24;
		final float row = 17;
		final float size = 10;
		final float keySize = 8.5f;
		float w = 128;
		for (Module m : bound) {
			w = Math.max(w, 11 + ui.textWidth(m.displayName(), MEDIUM, size) + 14
					+ ui.textWidth(KeyNames.name(m.keybind().get()), SEMIBOLD, keySize) + 12 + 10);
		}
		float h = keysHeight.to(header + 6 + Math.max(1, bound.size()) * row + 4, dt);
		float[] p = host.placeAt("keybinds", 14 * k, 8 * k, w * k, h * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		ui.glass(0, 0, w, h, 12);
		tracked("KEYBINDS", 11, header / 2 + 0.5f, 8.5f, 0.5f, HudTheme.TEXT, BOLD);
		long on = bound.stream().filter(Module::isEnabled).count();
		String count = on + " on";
		ui.textMiddle(count, w - 11 - ui.textWidth(count, SEMIBOLD, 8), header / 2 + 0.5f, 8, HudTheme.alpha(HudTheme.light(), 0.85f), SEMIBOLD);
		line(10, header, w - 20, 0x24FFFFFF);
		ui.clip(0, header + 1, w, h);
		float ry = header + 4;
		if (bound.isEmpty()) {
			ui.textMiddle("No modules bound", 11, ry + row / 2, size, HudTheme.TEXT_DIM, MEDIUM);
		}
		for (Module m : bound) {
			boolean enabled = m.isEnabled();
			float cy = ry + row / 2;
			ui.textMiddle(m.displayName(), 11, cy, size, enabled ? HudTheme.TEXT : HudTheme.TEXT_FAINT, MEDIUM);
			String key = KeyNames.name(m.keybind().get());
			float kw = Math.max(16, ui.textWidth(key, SEMIBOLD, keySize) + 12);
			float kh = 12;
			float kx = w - 10 - kw;
			ui.fill(kx, cy - kh / 2, kw, kh, 4, enabled ? HudTheme.alpha(HudTheme.light(), 0.20f) : 0x14FFFFFF);
			if (enabled) {
				ui.ring(kx, cy - kh / 2, kw, kh, 4, 1, HudTheme.alpha(HudTheme.light(), 0.55f));
			}
			ui.textMiddle(key, kx + (kw - ui.textWidth(key, SEMIBOLD, keySize)) / 2, cy, keySize,
					enabled ? HudTheme.light() : HudTheme.TEXT_DIM, SEMIBOLD);
			ry += row;
		}
		ui.unclip();
		ui.resetTransform();
		return p[1] + h * k;
	}

	/* ---------------------------------------------------------------- effects ------------------------------------------ */

	private static final class EffectRow {
		final Smooth presence = new Smooth(0, 14);
		final Smooth position = new Smooth(0, 16);
		final RollingText time = new RollingText();
		String name = "";
		boolean ending;
		boolean wanted;
		Holder<MobEffect> effect;
	}

	private final Map<Holder<MobEffect>, EffectRow> effectRows = new LinkedHashMap<>();
	private final Smooth effectsHeight = new Smooth(24, 16);

	void effects(GuiGraphicsExtractor g, Minecraft mc, float top, int gw, int gh, float k) {
		if (dark()) {
			darkEffects(g, mc, top, gw, gh, k);
			return;
		}
		List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
		effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
		effectRows.values().forEach(r -> r.wanted = false);
		for (int i = 0; i < effects.size(); i++) {
			MobEffectInstance e = effects.get(i);
			EffectRow r = effectRows.computeIfAbsent(e.getEffect(), key -> new EffectRow());
			if (!r.presence.placed) {
				r.position.snap(i);
				r.presence.snap(0);
			}
			r.wanted = true;
			r.effect = e.getEffect();
			r.position.target = i;
			r.name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
			if (e.isInfiniteDuration()) {
				r.time.set("∞", now);
				r.ending = false;
			} else {
				int seconds = e.getDuration() / 20;
				r.time.set(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), now);
				r.ending = e.getDuration() < 200;
			}
		}
		for (EffectRow r : effectRows.values()) {
			r.presence.to(r.wanted ? 1 : 0, dt);
			r.position.to(r.position.target, dt);
		}
		effectRows.entrySet().removeIf(e -> !e.getValue().wanted && e.getValue().presence.v < 0.02f);
		if (effectRows.isEmpty() && !HudOverlay.editing()) {
			effectsHeight.snap(24);
			return;
		}
		final float width = 142;
		final float header = 24;
		final float row = 17;
		float h = effectsHeight.to(header + 6 + Math.max(1, effects.size()) * row + 4, dt);
		float[] p = host.placeAt("potions", 14 * k, gh - h * k - 76 * k, width * k, h * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		ui.glass(0, 0, width, h, 12);
		tracked("EFFECTS LIST", 11, header / 2 + 0.5f, 8.5f, 0.5f, HudTheme.TEXT, BOLD);
		line(10, header, width - 20, 0x24FFFFFF);
		if (effectRows.isEmpty()) {
			ui.textMiddle("No active effects", 11, header + 4 + row / 2, 10, HudTheme.TEXT_DIM, MEDIUM);
		}
		ui.clip(0, header + 1, width, h);
		for (EffectRow r : effectRows.values()) {
			float presence = Math.max(0, Math.min(1, r.presence.v));
			float rowY = header + 4 + r.position.v * row;
			float shift = (1 - presence) * -8;
			ui.alpha(presence);
			float cy = rowY + row / 2;
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ui.mapX(10 + shift), ui.mapY(cy - 7));
			float s = 14 * scale() / 18f;
			pose.scale(s, s);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(r.effect), 0, 0, 18, 18,
					HudTheme.alpha(0xFFFFFFFF, presence));
			pose.popMatrix();
			float tx = 30 + shift;
			String name = fit(r.name, width - 30 - 46, 10, MEDIUM);
			ui.textMiddle(name, tx, cy, 10, HudTheme.TEXT, MEDIUM);
			r.time.draw(ui, width - 10 + shift, cy, 9.5f, SEMIBOLD, r.ending ? HudTheme.WARN : HudTheme.light(), true, now);
		}
		ui.alpha(1);
		ui.unclip();
		ui.resetTransform();
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(n);
		};
	}

	/* ---------------------------------------------------------------- speed graph -------------------------------------- */

	private static final int SAMPLES = 48;
	private final float[] series = new float[SAMPLES + 1];
	private int seriesCount;
	private float seriesTimer = 0.25f;
	private final RollingText speedValue = new RollingText();

	void speedGraph(GuiGraphicsExtractor g, Minecraft mc, int gw, int gh, float k) {
		if (dark()) {
			sampleSeries(mc);
			darkSpeed(g, gw, gh, k);
			return;
		}
		sampleSeries(mc);
		final float width = 118;
		final float height = 46;
		final float size = 10.5f;
		float[] p = host.placeAt("speedgraph", 14 * k, gh - height * k - 24 * k, width * k, height * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		ui.glass(0, 0, width, height, 11);
		float cy = 14;
		String lead = "Speed - ";
		float cursor = 11;
		ui.textMiddle(lead, cursor, cy, size, HudTheme.alpha(HudTheme.TEXT, 0.72f), MEDIUM);
		cursor += ui.textWidth(lead, MEDIUM, size);
		speedValue.draw(ui, cursor, cy, size, BOLD, HudTheme.TEXT, false, now);
		cursor += speedValue.width(ui, size, BOLD);
		ui.textMiddle(" b/s", cursor, cy, size, HudTheme.alpha(HudTheme.TEXT, 0.72f), MEDIUM);
		float gx = 11;
		float gy = 24;
		float gw2 = width - 22;
		float gh2 = height - 33;
		drawSeries(g, gx, gy, gw2, gh2);
		ui.resetTransform();
	}

	private void sampleSeries(Minecraft mc) {
		seriesTimer += dt;
		if (seriesTimer >= 0.25f) {
			seriesTimer = 0;
			double dx = mc.player.getX() - mc.player.xo;
			double dy = mc.player.isFallFlying() ? mc.player.getY() - mc.player.yo : 0;
			double dz = mc.player.getZ() - mc.player.zo;
			float sample = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 20);
			if (seriesCount < series.length) {
				series[seriesCount++] = sample;
			} else {
				System.arraycopy(series, 1, series, 0, series.length - 1);
				series[series.length - 1] = sample;
			}
			speedValue.set(String.format(Locale.ROOT, "%.1f", sample), now);
		}
	}

	/**
	 * Draws a line graph straight in screen pixels (one column per pixel: a fading fill under a 2-pixel line, and a dot at the
	 * newest end). No texture, so nothing is uploaded or re-created as it scrolls.
	 */
	private void plot(GuiGraphicsExtractor g, float x, float y, float w, float h, float[] values, int count, int colour) {
		float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(1 / s, 1 / s);
		int x0 = Math.round(x * s);
		int y0 = Math.round(y * s);
		int pw = Math.max(2, Math.round(w * s));
		int ph = Math.max(2, Math.round(h * s));
		int bottom = y0 + ph;
		float a = ui.alpha();
		int line = HudTheme.alpha(colour, a);
		int fillTop = HudTheme.alpha(colour, 0.38f * a);
		int fillBottom = HudTheme.alpha(colour, 0);
		int thick = Math.max(2, Math.round(s * 0.55f));
		int prev = -1;
		int lastY = bottom;
		for (int c = 0; c < pw; c++) {
			float f = c / (float) (pw - 1) * (count - 1);
			int i = Math.min(count - 2, (int) f);
			float v = values[i] + (values[i + 1] - values[i]) * (f - i);
			int ly = Math.round(bottom - Math.max(0, Math.min(1, v)) * ph);
			g.fillGradient(x0 + c, ly, x0 + c + 1, bottom, fillTop, fillBottom);
			int top = prev < 0 ? ly : Math.min(prev, ly);
			int low = prev < 0 ? ly : Math.max(prev, ly);
			g.fill(x0 + c, top - thick / 2, x0 + c + 1, low + (thick + 1) / 2, line);
			prev = ly;
			lastY = ly;
		}
		int r = thick + 1;
		g.fill(x0 + pw - r, lastY - r, x0 + pw + r, lastY + r, HudTheme.alpha(0xFFFFFFFF, a));
		pose.popMatrix();
	}

	private void drawSeries(GuiGraphicsExtractor g, float gx, float gy, float gw2, float gh2) {
		if (seriesCount >= 3) {
			float max = 0;
			for (int i = 0; i < seriesCount; i++) {
				max = Math.max(max, series[i]);
			}
			max = Math.max(6, max * 1.15f);
			float frac = Math.min(1, seriesTimer / 0.25f);
			int segments = seriesCount - 1;
			float step = gw2 / Math.max(1, SAMPLES - 1);
			int columns = Math.max(3, Math.round(gw2 * scale() / 1.5f));
			float[] values = new float[columns];
			int count = 0;
			float firstX = gx + gw2;
			for (int c = 0; c < columns; c++) {
				float x = gx + gw2 * c / (columns - 1f);
				float age = (gx + gw2 - x) / step;
				float idx = segments - age + frac;
				// Start of the history: hold the oldest sample, so the graph is always the full width (its texture keeps one size).
				idx = Math.max(0, idx);
				if (count == 0) {
					firstX = x;
				}
				int i0 = Math.min(segments, (int) Math.floor(idx));
				int i1 = Math.min(segments, i0 + 1);
				float f = idx - i0;
				float t = f * f * (3 - 2 * f);
				values[count++] = Math.min(1, (series[i0] + (series[i1] - series[i0]) * t) / max);
			}
			if (count >= 2) {
				plot(g, ui.mapX(firstX), ui.mapY(gy), (gx + gw2 - firstX) * scale(), gh2 * scale(), values, count, HudTheme.light());
			}
		}
	}

	/* ---------------------------------------------------------------- target ------------------------------------------- */

	private final Spring targetHealth = new Spring(1, 240, 17);
	private final Smooth targetTrail = new Smooth(1, 3.5f);
	private final RollingText targetPoints = new RollingText();
	private String lastTargetName;
	static final float TARGET_W = 164;
	static final float TARGET_H = 46;

	/** Draws the target card at {@code x, y} (GUI pixels), faded in by {@code a}. */
	void target(GuiGraphicsExtractor g, TargetInfo t, LivingEntity entity, float x, float y, float a, float k) {
		float max = Math.max(1, t.maxHealth());
		float ratio = Math.max(0, Math.min(1, t.health() / max));
		if (!t.name().equals(lastTargetName)) {
			lastTargetName = t.name();
			targetHealth.snap(ratio);
			targetTrail.snap(ratio);
		}
		targetHealth.target = ratio;
		Spring.advance(dt, List.of(targetHealth));
		float trail = targetTrail.to(ratio, dt);
		targetPoints.set(String.valueOf((int) Math.ceil(t.health())), now);
		ui.transform(x - (1 - a) * 16 * k, y, k, k);
		ui.alpha(a);
		ui.glass(0, 0, TARGET_W, TARGET_H, 13);
		float hx = 8;
		float hy = 8;
		if (t.skin() != null) {
			head(g, t.skin(), hx, hy, 30);
		} else {
			ui.fill(hx, hy, 30, 30, 8, 0x1FFFFFFF);
			if (entity == null) {
				ui.textMiddle(t.name().substring(0, 1).toUpperCase(Locale.ROOT), hx + 11, hy + 15, 14, HudTheme.TEXT_DIM, BOLD);
			}
		}
		float textX = 46;
		String name = fit(t.name(), TARGET_W - 46 - 46, 12, BOLD);
		ui.textMiddle(name, textX, 15, 12, HudTheme.TEXT, BOLD);
		targetPoints.draw(ui, TARGET_W - 28, 15, 9.5f, SEMIBOLD, HudTheme.TEXT_DIM, true, now);
		ui.textMiddle("HP", TARGET_W - 10 - ui.textWidth("HP", SEMIBOLD, 9.5f), 15, 9.5f, HudTheme.TEXT_FAINT, SEMIBOLD);
		float barY = 27;
		float barW = TARGET_W - 46 - 10;
		float shown = Math.max(0, Math.min(1, targetHealth.xf()));
		ui.fill(textX, barY, barW, 6, 3, 0x24FFFFFF);
		ui.fill(textX, barY, Math.max(6, barW * trail), 6, 3, 0x47FFFFFF);
		int color = HudTheme.health(shown);
		ui.gradient(textX, barY, Math.max(6, barW * shown), 6, 3, HudTheme.mix(color, 0xFF000000, 0.18f), color, 2);
		String meta = String.format(Locale.ROOT, "%.1fm  ·  %dms", t.distance(), t.ping());
		ui.textMiddle(meta, textX, 39.5f, 7.5f, HudTheme.TEXT_FAINT, MEDIUM);
		ui.alpha(1);
		ui.resetTransform();
	}

	public static boolean effectsActive() {
		return active != null && active.isEnabled() && active.potions.get();
	}

	public static boolean lyricsBarActive() {
		return active != null && active.isEnabled() && active.lyricsBar.get()
				&& nz.temple.client.modules.render.Music.instance() != null && nz.temple.client.modules.render.Music.instance().isEnabled();
	}

	/* ================================================================ Dark style ===================================== */

	/** A dark pill or card in the Dark style (or glass in the Glass style), through the panel painter. */
	private void pill(float x, float y, float w, float h, float r) {
		ui.glass(x, y, w, h, r);
	}

	private final RollingText darkX = new RollingText();
	private final RollingText darkY = new RollingText();
	private final RollingText darkZ = new RollingText();
	private final Smooth darkCoordWidth = new Smooth(0, 16);

	/** Dark coordinates: a pill with a turning arrow and "x, y, z". */
	private void darkCoordinates(GuiGraphicsExtractor g, Minecraft mc, int gw, int gh, float k, float partial) {
		final float height = 22;
		final float size = 11;
		darkX.set(nz.temple.client.modules.client.StreamerMode.x((int) Math.floor(mc.player.getX())), now);
		darkY.set(Integer.toString((int) Math.floor(mc.player.getY())), now);
		darkZ.set(nz.temple.client.modules.client.StreamerMode.z((int) Math.floor(mc.player.getZ())), now);
		float wanted = 180 - mc.player.getViewYRot(partial);
		if (Float.isNaN(compass)) {
			compass = wanted;
		}
		float diff = ((wanted - compass) % 360 + 540) % 360 - 180;
		compass += diff * (1 - (float) Math.exp(-16 * dt));
		float comma = ui.textWidth(", ", SEMIBOLD, size);
		float target = 9 + 12 + 6 + darkX.width(ui, size, SEMIBOLD) + comma + darkY.width(ui, size, SEMIBOLD) + comma
				+ darkZ.width(ui, size, SEMIBOLD) + 10;
		float w = darkCoordWidth.to(target, dt);
		float[] p = host.placeAt("info", (gw - w * k) / 2, stackY, w * k, height * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		pill(0, 0, w, height, 8);
		ui.clip(0, 0, w, height);
		float cy = height / 2;
		var pose = g.pose();
		pose.pushMatrix();
		float ax = ui.mapX(9 + 6);
		float ay = ui.mapY(cy);
		pose.translate(ax, ay);
		pose.rotate((float) Math.toRadians(compass));
		pose.translate(-ax, -ay);
		icon(g, Icons.Icon.NAVIGATION, 9 + 6, cy, 11, HudTheme.light());
		pose.popMatrix();
		float c = 9 + 12 + 6;
		darkX.draw(ui, c, cy, size, SEMIBOLD, HudTheme.TEXT, false, now);
		c += darkX.width(ui, size, SEMIBOLD);
		ui.textMiddle(",", c, cy, size, HudTheme.TEXT_DIM, SEMIBOLD);
		c += comma;
		darkY.draw(ui, c, cy, size, SEMIBOLD, HudTheme.TEXT, false, now);
		c += darkY.width(ui, size, SEMIBOLD);
		ui.textMiddle(",", c, cy, size, HudTheme.TEXT_DIM, SEMIBOLD);
		c += comma;
		darkZ.draw(ui, c, cy, size, SEMIBOLD, HudTheme.TEXT, false, now);
		ui.unclip();
		ui.resetTransform();
	}

	/** A header pill: a bold title on the left and an icon in the theme colour on the right. */
	private void header(GuiGraphicsExtractor g, String title, Icons.Icon icon, float w) {
		pill(0, 0, w, 24, 8);
		ui.textMiddle(title, 10, 12, 12, HudTheme.TEXT, BOLD);
		ui.fill(w - 24, 4, 18, 16, 5, HudTheme.alpha(HudTheme.light(), 0.14f));
		icon(g, icon, w - 15, 12, 11, HudTheme.light());
	}

	private final Map<String, Smooth> keyLit = new HashMap<>();

	/** Dark keybinds: a header pill, then each bound module as its own pill with its key in a chip beside it. */
	private float darkKeybinds(GuiGraphicsExtractor g, float top, int gw, int gh, float k) {
		long nanos = System.nanoTime();
		if (nanos - boundAt > 250_000_000L) {
			boundAt = nanos;
			bound = modules.all().stream().filter(m -> m.keybind().isBound() && (!settings.keybindsOnlyOn.get() || m.isEnabled()))
					.sorted(Comparator.comparing(Module::displayName)).limit(Math.round(settings.keybindsMax.get())).toList();
		}
		if (bound.isEmpty() && !HudOverlay.editing()) {
			return top - 6 * k;
		}
		final float row = 23;
		final float gap = 4;
		final float size = 11.5f;
		final float keySize = 10.5f;
		float w = 104;
		for (Module m : bound) {
			float kw = Math.max(23, ui.textWidth(KeyNames.name(m.keybind().get()), BOLD, keySize) + 14);
			w = Math.max(w, ui.textWidth(m.displayName(), SEMIBOLD, size) + 20 + gap + kw);
		}
		float h = 24 + Math.max(1, bound.size()) * (row + gap);
		float[] p = host.placeAt("keybinds", 14 * k, 8 * k, w * k, h * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		header(g, "Keybinds", Icons.Icon.KEYBOARD, w);
		float y = 24 + gap;
		if (bound.isEmpty()) {
			pill(0, y, 120, row, 7);
			ui.textMiddle("No modules bound", 10, y + row / 2, size, HudTheme.TEXT_DIM, MEDIUM);
		}
		for (Module m : bound) {
			float lit = keyLit.computeIfAbsent(m.name(), n -> new Smooth(0, 10)).to(m.isEnabled() ? 1 : 0, dt);
			String name = m.displayName();
			float nw = ui.textWidth(name, SEMIBOLD, size) + 20;
			pill(0, y, nw, row, 7);
			ui.textMiddle(name, 10, y + row / 2, size, HudTheme.mix(0x8CF4F8FF, HudTheme.TEXT, lit), SEMIBOLD);
			String key = KeyNames.name(m.keybind().get());
			float kw = Math.max(23, ui.textWidth(key, BOLD, keySize) + 14);
			float kx = nw + gap;
			pill(kx, y, kw, row, 7);
			if (lit > 0.01f) {
				ui.fill(kx, y, kw, row, 7, HudTheme.alpha(HudTheme.accent(), 0.85f * lit));
			}
			ui.textMiddle(key, kx + (kw - ui.textWidth(key, BOLD, keySize)) / 2, y + row / 2, keySize,
					HudTheme.mix(0x8CF4F8FF, 0xFFFFFFFF, lit), BOLD);
			y += row + gap;
		}
		ui.resetTransform();
		return p[1] + h * k;
	}

	private final Map<Holder<MobEffect>, Integer> longest = new HashMap<>();

	/** Dark effects ("Potions"): a header pill, then per effect an icon chip, a name pill and a ring showing the time left. */
	private void darkEffects(GuiGraphicsExtractor g, Minecraft mc, float top, int gw, int gh, float k) {
		List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
		effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
		if (effects.isEmpty() && !HudOverlay.editing()) {
			longest.clear();
			return;
		}
		final float row = 23;
		final float gap = 4;
		final float size = 11.5f;
		List<String> names = new ArrayList<>();
		float w = 110;
		for (MobEffectInstance e : effects) {
			String name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
			String time = e.isInfiniteDuration() ? "∞" : String.format(Locale.ROOT, "%d:%02d", e.getDuration() / 1200, e.getDuration() / 20 % 60);
			names.add(name + "\u0000" + time);
			w = Math.max(w, row + gap + ui.textWidth(name, SEMIBOLD, size) + 8 + ui.textWidth(time, MEDIUM, 9) + 18 + gap + row);
		}
		float h = 24 + Math.max(1, effects.size()) * (row + gap);
		float[] p = host.placeAt("potions", 14 * k, top, w * k, h * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		header(g, "Potions", Icons.Icon.DROPLET, w);
		float y = 24 + gap;
		if (effects.isEmpty()) {
			pill(0, y, 110, row, 6);
			ui.textMiddle("No active effects", 9, y + row / 2, size, HudTheme.TEXT_DIM, MEDIUM);
		}
		for (int i = 0; i < effects.size(); i++) {
			MobEffectInstance e = effects.get(i);
			String[] parts = names.get(i).split("\u0000");
			pill(0, y, row, row, 6);
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ui.mapX(4.5f), ui.mapY(y + 4.5f));
			float sc = 14 * scale() / 18f;
			pose.scale(sc, sc);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(e.getEffect()), 0, 0, 18, 18,
					HudTheme.alpha(0xFFFFFFFF, ui.alpha()));
			pose.popMatrix();
			float nx = row + gap;
			float nw = ui.textWidth(parts[0], SEMIBOLD, size) + 8 + ui.textWidth(parts[1], MEDIUM, 9) + 18;
			pill(nx, y, nw, row, 6);
			ui.textMiddle(parts[0], nx + 9, y + row / 2, size, HudTheme.TEXT, SEMIBOLD);
			boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
			ui.textMiddle(parts[1], nx + 9 + ui.textWidth(parts[0], SEMIBOLD, size) + 8, y + row / 2, 9,
					ending ? HudTheme.WARN : HudTheme.TEXT_DIM, MEDIUM);
			float rx = nx + nw + gap;
			pill(rx, y, row, row, 6);
			int max = Math.max(e.getDuration(), longest.getOrDefault(e.getEffect(), 0));
			longest.put(e.getEffect(), max);
			float left = e.isInfiniteDuration() ? 1 : e.getDuration() / (float) Math.max(1, max);
			arc(rx + row / 2, y + row / 2, 5.5f, left, ending ? HudTheme.WARN : HudTheme.light());
			y += row + gap;
		}
		ui.resetTransform();
	}

	/** A ring with the first {@code fraction} of it (clockwise from the top) in {@code argb}. */
	private void arc(float cx, float cy, float r, float fraction, int argb) {
		ui.ring(cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2, r + 1, 1.6f, 0x2EFFFFFF);
		int dots = 40;
		int lit = Math.round(dots * Math.max(0, Math.min(1, fraction)));
		for (int i = 0; i < lit; i++) {
			double a = -Math.PI / 2 + i / (double) dots * Math.PI * 2;
			float x = cx + (float) Math.cos(a) * r;
			float y = cy + (float) Math.sin(a) * r;
			ui.fill(x - 1.0f, y - 1.0f, 2.0f, 2.0f, 1.0f, argb);
		}
	}

	/** Dark speed card ("BPS"): the speed in bold and a graph under it. */
	private void darkSpeed(GuiGraphicsExtractor g, int gw, int gh, float k) {
		final float width = 108;
		final float height = 48;
		float[] p = host.placeAt("speedgraph", gw - width * k - 14 * k, gh * 0.55f, width * k, height * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		pill(0, 0, width, height, 8);
		speedValue.draw(ui, 9, 12, 11, BOLD, HudTheme.TEXT, false, now);
		ui.textMiddle("BPS", 9 + speedValue.width(ui, 11, BOLD) + 4, 12, 11, HudTheme.TEXT, BOLD);
		icon(g, Icons.Icon.GAUGE, width - 13, 12, 11, HudTheme.light());
		line(8, 22, width - 16, 0x1AFFFFFF);
		drawSeries(g, 8, 26, width - 16, height - 32);
		ui.resetTransform();
	}


	/* ================================================================ shared new widgets ============================= */

	private String lyricShown = "";
	private String lyricOld = "";
	private float lyricChanged = -10;
	private final Smooth lyricWidth = new Smooth(0, 14);

	/** The lyrics bar: an animated equaliser, then the line being sung, sliding in as lines change. */
	void lyricsBar(GuiGraphicsExtractor g, Minecraft mc, int gw, int gh, float k) {
		var music = nz.temple.client.modules.render.Music.instance();
		String line = music == null ? null : music.currentLine();
		if (line == null && HudOverlay.editing()) {
			line = "Lyrics show here";
		}
		if (line == null) {
			lyricShown = "";
			lyricWidth.snap(0);
			return;
		}
		if (!line.equals(lyricShown)) {
			lyricOld = lyricShown;
			lyricShown = line;
			lyricChanged = now;
		}
		boolean dark = dark();
		final float height = dark ? 22 : 24;
		final float size = 10.5f;
		float textW = Math.max(ui.textWidth(lyricShown, SEMIBOLD, size), 30);
		float w = lyricWidth.to(10 + 14 + 8 + 1 + 8 + textW + 12, dt);
		float[] p = host.placeAt("lyrics", (gw - w * k) / 2, stackY, w * k, height * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		pill(0, 0, w, height, dark ? 8 : height / 2);
		float cy = height / 2;
		boolean playing = !lyricShown.isEmpty();
		for (int i = 0; i < 4; i++) {
			float wave = playing ? (float) (0.5 + 0.5 * Math.sin(now * (5.5 + i * 1.7) + i * 1.3)) : 0.2f;
			float bh = 3 + 7 * wave;
			ui.fill(10 + i * 3.6f, cy + 5 - bh, 2.2f, bh, 1.1f, HudTheme.light());
		}
		vline(10 + 14 + 8, cy, 6, 0x29FFFFFF);
		float tx = 10 + 14 + 8 + 1 + 8;
		float t = Math.min(1, (now - lyricChanged) / 0.35f);
		float e = 1 - (1 - t) * (1 - t) * (1 - t);
		ui.clip(tx - 2, 0, w - 4, height);
		if (e < 1 && !lyricOld.isEmpty()) {
			ui.textMiddle(lyricOld, tx, cy - e * 9, size, HudTheme.alpha(HudTheme.TEXT, 1 - e), SEMIBOLD);
		}
		ui.textMiddle(lyricShown.isEmpty() ? "♪" : lyricShown, tx, cy + (1 - e) * 9, size, HudTheme.alpha(HudTheme.TEXT, e), SEMIBOLD);
		ui.unclip();
		ui.resetTransform();
	}

	private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {net.minecraft.world.entity.EquipmentSlot.HEAD,
			net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
			net.minecraft.world.entity.EquipmentSlot.FEET};

	/** Your armour in a pill next to the hotbar, each piece with a durability bar under it. */
	void armorBox(GuiGraphicsExtractor g, Minecraft mc, int gw, int gh) {
		List<net.minecraft.world.item.ItemStack> pieces = new ArrayList<>();
		for (var slot : ARMOR_SLOTS) {
			var stack = mc.player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				pieces.add(stack);
			}
		}
		if (pieces.isEmpty() && !HudOverlay.editing()) {
			return;
		}
		boolean glassBar = false;
		float h = 26;
		float w = Math.max(1, pieces.size()) * 20 + 6;
		boolean offRight = mc.player.getMainArm().getOpposite() == HumanoidArm.RIGHT && !mc.player.getOffhandItem().isEmpty();
		float defX = gw / 2f + (glassBar ? 96 : 91) + 6 + (offRight ? 28 : 0);
		float defY = glassBar ? gh - LIFT - 24 : gh - 23;
		float[] p = host.placeAt("armor", defX, defY, w, h, gw, gh);
		ui.resetTransform();
		ui.glass(p[0], p[1], w, h, dark() ? 7 : 9);
		float x = p[0] + 5;
		for (var stack : pieces) {
			g.item(stack, Math.round(x), Math.round(p[1] + 4));
			if (stack.isDamageableItem()) {
				float left = 1 - stack.getDamageValue() / (float) Math.max(1, stack.getMaxDamage());
				ui.fill(x + 2, p[1] + h - 4, 12, 1.5f, 0.75f, 0x59000000);
				ui.fill(x + 2, p[1] + h - 4, Math.max(1.5f, 12 * left), 1.5f, 0.75f, HudTheme.health(left));
			}
			x += 20;
		}
	}

	/* ---------------------------------------------------------------- keystrokes -------------------------------------- */

	private final Map<String, Smooth> keyDown = new HashMap<>();

	/** W, A, S, D, the mouse buttons and space as small keys that light up in the theme colour while held. */
	void keystrokes(GuiGraphicsExtractor g, Minecraft mc, int gw, int gh, float k) {
		final float key = 22;
		final float gap = 4;
		boolean mouse = settings.keysMouse.get();
		boolean space = settings.keysSpace.get();
		float w = key * 3 + gap * 2;
		float h = key * 2 + gap + (mouse ? key + gap : 0) + (space ? 12 + gap : 0);
		float[] p = host.placeAt("keystrokes", 14 * k, gh * 0.42f, w * k, h * k, gw, gh);
		ui.transform(p[0], p[1], k, k);
		var o = mc.options;
		keyCap("W", key + gap, 0, key, key, o.keyUp.isDown());
		keyCap("A", 0, key + gap, key, key, o.keyLeft.isDown());
		keyCap("S", key + gap, key + gap, key, key, o.keyDown.isDown());
		keyCap("D", (key + gap) * 2, key + gap, key, key, o.keyRight.isDown());
		float my = (key + gap) * 2;
		if (mouse) {
			keyCap("LMB", 0, my, key, key, o.keyAttack.isDown());
			keyCap("•", key + gap, my, key, key, o.keyPickItem.isDown());
			keyCap("RMB", (key + gap) * 2, my, key, key, o.keyUse.isDown());
			my += key + gap;
		}
		if (space) {
			keyCap("SPACE", 0, my, w, 12, o.keyJump.isDown());
		}
		ui.resetTransform();
	}

	private void keyCap(String label, float x, float y, float w, float h, boolean down) {
		float lit = keyDown.computeIfAbsent(label, l -> new Smooth(0, 18)).to(down ? 1 : 0, dt);
		float r = Math.min(7, h / 2);
		pill(x, y, w, h, r);
		if (lit > 0.01f) {
			ui.fill(x, y, w, h, r, HudTheme.alpha(HudTheme.light(), 0.85f * lit));
		}
		float size = label.length() > 1 && !label.equals("•") ? (h < 16 ? 6.5f : 7.5f) : 10.5f;
		int colour = HudTheme.mix(HudTheme.TEXT, 0xFF0B0D10, lit);
		ui.textMiddle(label, x + (w - ui.textWidth(label, BOLD, size)) / 2, y + h / 2, size, colour, BOLD);
	}
}
