package nz.temple.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import nz.temple.client.render.streamproof.StreamproofRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import nz.temple.TempleNz;
import nz.temple.anim.Spring;
import nz.temple.client.gui.TempleScreen;
import nz.temple.client.input.KeyNames;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.LogoMark;
import nz.temple.client.render.Projection;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.config.ConfigManager;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class HudOverlay {
	private static final float EDGE = 10;
	private static final float CHIP_H = 17;
	private static final float CHIP_GAP = 4;
	private static final float CARD_W = AzuraHud.TARGET_W;
	private static final float CARD_H = AzuraHud.TARGET_H;
	private static final int PANEL = 0xD90B0D12;
	private static final int[] EQUIPMENT_ROW = {3, 2, 1, 0};
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
	private static final class Chip {
		final Spring appear = new Spring(0, 260, 16);
		final Spring y = new Spring(0, 260, 26);
		boolean placed;
	}
	private final Ui ui;
	private final ModuleManager modules;
	private final HudModule settings;
	private final Notifications notifications;
	private final Map<Module, Chip> chips = new LinkedHashMap<>();
	private final Spring cardAppear = new Spring(0, 200, 16);
	private final Spring cardX = new Spring(0, 240, 24);
	private final Spring cardY = new Spring(0, 240, 24);
	private boolean cardPlaced;
	private Projection projection;
	private final Map<String, float[]> rects = new HashMap<>();
	private Supplier<TargetInfo> targetSource = () -> null;
	private Supplier<LivingEntity> entitySource = () -> null;
	private java.util.function.Function<Boolean, BlockInfo> blockSource = placeholder -> null;
	private final Spring blockAppear = new Spring(0, 220, 18);
	private final Spring blockX = new Spring(0, 260, 26);
	private final Spring blockY = new Spring(0, 260, 26);
	private final Spring blockW = new Spring(0, 300, 30);
	private boolean blockPlaced;
	private BlockInfo lastBlock;
	private Supplier<List<nz.temple.client.modules.player.StaffDisplay.Staff>> staffSource = List::of;
	private java.util.function.BooleanSupplier staffShowEmpty = () -> false;
	private java.util.function.BooleanSupplier staffEnabled = () -> false;
	private ConfigManager config;
	private TargetInfo lastTarget;
	private LivingEntity lastEntity;
	private float shownHealth;
	private float trailHealth;
	private long lastNanos = System.nanoTime();
	private String dragging;
	private double dragDX;
	private double dragDY;
	private float k = 1;
	private float baseK = 1;
	private final float[] speedSamples = new float[30 * 20];
	private int speedHead;
	private int speedCount;
	private int lastSpeedTick = Integer.MIN_VALUE;
	private final Spring elytraAppear = new Spring(0, 240, 20);
	private double flightSpeed;
	private double flightClimb;
	private nz.temple.social.FriendManager friends;
	private float light = 0.5f;
	private float cardExtra;
	public HudOverlay(Ui ui, ModuleManager modules, HudModule settings, Notifications notifications) {
		this.ui = ui;
		this.modules = modules;
		this.settings = settings;
		this.notifications = notifications;
		this.azura = new AzuraHud(this, ui, settings, modules);
	}
	private final AzuraHud azura;

	ConfigManager config() {
		return config;
	}

	/** Starts dragging widget {@code id} by a point outside its rectangle (such as the info bar's grip). */
	public void pressWidget(String id, double mx, double my) {
		float[] r = rects.get(id);
		if (r == null) {
			return;
		}
		dragging = id;
		dragDX = mx - r[0];
		dragDY = my - r[1];
	}

	/** The info bar's items, for the HUD editor. */
	public AzuraHud bar() {
		return azura;
	}

	float[] placeAt(String id, float defX, float defY, float w, float h, int gw, int gh) {
		return place(id, defX, defY, w, h, gw, gh);
	}
	public void register() {
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "glass_layer"), StreamproofRenderer.hud("glass_layer", (g, delta) -> {

			var screen = Minecraft.getInstance().gui.screen();
			if (screen == null || !nz.temple.client.render.GlassButtons.enabled()
					|| screen instanceof net.minecraft.client.gui.screens.ChatScreen
					|| screen instanceof TempleScreen || screen instanceof nz.temple.client.gui.HudEditorScreen
							&& Minecraft.getInstance().level != null) {
				ui.glassRenderer().placeLayer(g);
			}
		}));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "overlay"), StreamproofRenderer.hud("overlay", this::extract));
	}
	public void setTargetSource(Supplier<TargetInfo> source) {
		targetSource = source;
	}

	public void setStaffSource(Supplier<List<nz.temple.client.modules.player.StaffDisplay.Staff>> source,
			java.util.function.BooleanSupplier showEmpty, java.util.function.BooleanSupplier enabled) {
		staffSource = source;
		staffShowEmpty = showEmpty;
		staffEnabled = enabled;
	}
	public void setBlockSource(java.util.function.Function<Boolean, BlockInfo> source) {
		blockSource = source;
	}
	public void setEntitySource(Supplier<LivingEntity> source) {
		entitySource = source;
	}

	public void setFriends(nz.temple.social.FriendManager friends) {
		this.friends = friends;
	}

	public void setConfig(ConfigManager config) {
		this.config = config;
	}

	public void setProjection(Projection projection) {
		this.projection = projection;
	}

	private void aimCard(GuiGraphicsExtractor g, DeltaTracker delta, boolean dark) {
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float w = dark ? 168 * k : CARD_W * k;
		float h = dark ? 58 * k : CARD_H * k;
		float tx = gw / 2f + 36 * k;
		float ty = gh / 2f - h / 2;
		if (lastEntity != null && projection != null && projection.ready()) {
			float partial = delta.getGameTimeDeltaPartialTick(false);
			Vec3 p = lastEntity.getPosition(partial);
			double cy = p.y + lastEntity.getBbHeight() * 0.6;
			float[] s = projection.toScreen(p.x, cy, p.z, gw, gh);
			if (s != null) {
				float r = Math.max(10, projection.screenRadius(p.x, cy, p.z, lastEntity.getBbWidth() * 0.6, gw, gh));
				boolean left = s[0] > gw * 0.62f;
				tx = left ? s[0] - r - 12 * k - w : s[0] + r + 12 * k;
				ty = s[1] - h / 2;
			}
		}
		tx = Math.max(4 * k, Math.min(gw - w - 4 * k, tx));
		ty = Math.max(4 * k, Math.min(gh - h - 4 * k, ty));
		if (!cardPlaced || cardAppear.x < 0.02) {
			cardX.snap(tx);
			cardY.snap(ty);
			cardPlaced = true;
		} else {
			cardX.target = tx;
			cardY.target = ty;
		}
	}
	public boolean press(double mx, double my) {
		for (Map.Entry<String, float[]> e : rects.entrySet()) {
			float[] r = e.getValue();
			if (mx >= r[0] && my >= r[1] && mx <= r[0] + r[2] && my <= r[1] + r[3]) {
				dragging = e.getKey();
				dragDX = mx - r[0];
				dragDY = my - r[1];
				return true;
			}
		}
		return false;
	}
	public void drag(double mx, double my) {
		float[] r = dragging == null ? null : rects.get(dragging);
		if (r == null || config == null) {
			return;
		}
		var window = Minecraft.getInstance().getWindow();
		int gw = window.getGuiScaledWidth();
		int gh = window.getGuiScaledHeight();
		double x = Math.max(0, Math.min(gw - r[2], mx - dragDX));
		double centred = (gw - r[2]) / 2.0;
		float unit = baseK / Math.max(0.01f, settings.hudScale.getFloat() / 100f);
		snapped = Math.abs(x - centred) <= 5 * unit;
		if (snapped) {
			x = centred;
		}
		double y = Math.max(0, Math.min(gh - r[3], my - dragDY));
		// Snap to the edges of the other widgets (lining up with them, or sitting right beside them), with a guide line.
		guides.clear();
		float tol = 5 * unit;
		double bestX = tol;
		double bestY = tol;
		double snapX = x;
		double snapY = y;
		float[] guideX = null;
		float[] guideY = null;
		if (!snapped) {
			for (Map.Entry<String, float[]> e : rects.entrySet()) {
				if (e.getKey().equals(dragging)) {
					continue;
				}
				float[] o = e.getValue();
				double[][] xs = {{o[0], o[0]}, {o[0] + o[2] - r[2], o[0] + o[2]}, {o[0] + o[2], o[0] + o[2]}, {o[0] - r[2], o[0]}};
				for (double[] c : xs) {
					if (Math.abs(x - c[0]) < bestX) {
						bestX = Math.abs(x - c[0]);
						snapX = c[0];
						guideX = new float[] {(float) c[1], Math.min(o[1], (float) y) - 6 * unit, (float) c[1], Math.max(o[1] + o[3], (float) y + r[3]) + 6 * unit};
					}
				}
			}
		}
		for (Map.Entry<String, float[]> e : rects.entrySet()) {
			if (e.getKey().equals(dragging)) {
				continue;
			}
			float[] o = e.getValue();
			double[][] ys = {{o[1], o[1]}, {o[1] + o[3] - r[3], o[1] + o[3]}, {o[1] + o[3] + 4 * unit, o[1] + o[3] + 2 * unit},
					{o[1] - r[3] - 4 * unit, o[1] - 2 * unit}};
			for (double[] c : ys) {
				if (Math.abs(y - c[0]) < bestY) {
					bestY = Math.abs(y - c[0]);
					snapY = c[0];
					guideY = new float[] {Math.min(o[0], (float) snapX) - 6 * unit, (float) c[1], Math.max(o[0] + o[2], (float) snapX + r[2]) + 6 * unit, (float) c[1]};
				}
			}
		}
		x = snapX;
		y = snapY;
		if (guideX != null) {
			guides.add(guideX);
		}
		if (guideY != null) {
			guides.add(guideY);
		}
		boolean right = x + r[2] / 2 > gw / 2.0;
		boolean bottom = y + r[3] / 2 > gh / 2.0;
		double f = UiScale.factor();
		config.putExtra("hud." + dragging + ".set", 1);
		config.putExtra("hud." + dragging + ".h", (right ? gw - x - r[2] : x) / f);
		config.putExtra("hud." + dragging + ".v", (bottom ? gh - y - r[3] : y) / f);
		config.putExtra("hud." + dragging + ".right", right ? 1 : 0);
		config.putExtra("hud." + dragging + ".bottom", bottom ? 1 : 0);
	}
	private boolean snapped;
	/** Guide lines shown while a dragged widget is snapped to another one: {x0, y0, x1, y1} in GUI pixels. */
	private final List<float[]> guides = new ArrayList<>();

	public void release() {
		dragging = null;
		snapped = false;
		guides.clear();
	}
	private static final float MIN_SIZE = 0.5f;
	private static final float MAX_SIZE = 2.5f;

	private float size(String id) {
		return config == null ? 1 : (float) Math.max(MIN_SIZE, Math.min(MAX_SIZE, config.getExtra("hud." + id + ".scale", 1)));
	}
	private float kFor(String id) {
		return baseK * size(id);
	}
	public boolean scroll(double mx, double my, double amount) {
		for (Map.Entry<String, float[]> e : rects.entrySet()) {
			float[] r = e.getValue();
			if (mx >= r[0] && my >= r[1] && mx <= r[0] + r[2] && my <= r[1] + r[3]) {
				resize(e.getKey(), amount > 0 ? 0.1f : -0.1f);
				return true;
			}
		}
		return false;
	}
	/** Element {@code id}'s size (1 = normal). */
	public float sizeOf(String id) {
		return size(id);
	}

	public void setSize(String id, float value) {
		if (config != null) {
			config.putExtra("hud." + id + ".scale", Math.max(MIN_SIZE, Math.min(MAX_SIZE, Math.round(value * 20) / 20f)));
		}
	}

	/** Element {@code id}'s opacity, 0.15 to 1. */
	public float opacity(String id) {
		return config == null ? 1 : (float) Math.max(0.15, Math.min(1, config.getExtra("hud." + id + ".opacity", 1)));
	}

	public void setOpacity(String id, float value) {
		if (config != null) {
			config.putExtra("hud." + id + ".opacity", Math.max(0.15f, Math.min(1, Math.round(value * 20) / 20f)));
		}
	}

	/** Puts element {@code id} back in its default spot. */
	public void resetPosition(String id) {
		if (config != null) {
			config.putExtra("hud." + id + ".set", 0);
		}
	}

	public static float minSize() {
		return MIN_SIZE;
	}

	public static float maxSize() {
		return MAX_SIZE;
	}

	public void resize(String id, float step) {
		if (config == null) {
			return;
		}
		float next = Math.round((size(id) + step) * 10) / 10f;
		config.putExtra("hud." + id + ".scale", Math.max(MIN_SIZE, Math.min(MAX_SIZE, next)));
	}

	public String widgetAt(double mx, double my) {
		for (Map.Entry<String, float[]> e : rects.entrySet()) {
			float[] r = e.getValue();
			if (mx >= r[0] && my >= r[1] && mx <= r[0] + r[2] && my <= r[1] + r[3]) {
				return e.getKey();
			}
		}
		return null;
	}
	private float[] place(String id, float defX, float defY, float w, float h, int gw, int gh) {
		float x = defX;
		float y = defY;
		if (config != null && config.getExtra("hud." + id + ".set", 0) == 1) {
			double f = UiScale.factor();
			double hOff = config.getExtra("hud." + id + ".h", 0) * f;
			double vOff = config.getExtra("hud." + id + ".v", 0) * f;
			x = (float) (config.getExtra("hud." + id + ".right", 0) == 1 ? gw - hOff - w : hOff);
			y = (float) (config.getExtra("hud." + id + ".bottom", 0) == 1 ? gh - vOff - h : vOff);
		}
		x = Math.max(0, Math.min(gw - w, x));
		y = Math.max(0, Math.min(gh - h, y));
		if (config == null || config.getExtra("hud." + id + ".set", 0) != 1) {
			float gap = 5 * baseK;
			for (int tries = 0; tries < 24; tries++) {
				float[] hit = null;
				for (Map.Entry<String, float[]> e : rects.entrySet()) {
					float[] r = e.getValue();
					if (!e.getKey().equals(id) && x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1]) {
						hit = r;
						break;
					}
				}
				if (hit == null) {
					break;
				}
				float[][] moves = {
						{x, hit[1] - h - gap},
						{x, hit[1] + hit[3] + gap},
						{x + w / 2 > gw / 2f ? hit[0] - w - gap : hit[0] + hit[2] + gap, y}};
				float bestX = x;
				float bestY = y;
				float best = Float.MAX_VALUE;
				for (float[] m : moves) {
					if (m[0] < 0 || m[1] < 0 || m[0] + w > gw || m[1] + h > gh) {
						continue;
					}
					float d = Math.abs(m[0] - x) + Math.abs(m[1] - y);
					if (d < best) {
						best = d;
						bestX = m[0];
						bestY = m[1];
					}
				}
				if (best == Float.MAX_VALUE) {
					break;
				}
				x = bestX;
				y = bestY;
			}
		}
		rects.put(id, new float[] {x, y, w, h});
		return new float[] {x, y};
	}
	private void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		try {
			// Dark panels are solid, so text needs no shadow; over see-through glass a soft shadow keeps it readable on bright skies.
			ui.textShadow(!azura.dark());
			extractHud(g, delta);
		} finally {
			ui.textShadow(true);
			ui.panelPainter(null);
			ui.resetTransform();
			ui.alpha(1);
		}
	}
	private void extractHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		long now = System.nanoTime();
		double elapsed = Math.min((now - lastNanos) / 1e9, 0.5);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		boolean dark = false;
		boolean sidebar = false;

		// HUD units are 1/540 of the screen's height (in GUI pixels), so the HUD keeps its proportions at every GUI scale.
		var window = mc.getWindow();
		baseK = settings.hudScale.getFloat() / 100f * (float) (window.getHeight() / 540.0 / window.getGuiScale());
		rects.clear();
		k = kFor("modules");
		List<Module> wanted = settings.moduleList.get() ? new ArrayList<>(modules.moduleList()) : new ArrayList<>();
		wanted.sort(java.util.Comparator.comparingDouble((Module m) -> -labelWidth(m, 10.5f)));
		float rowStep = sidebar ? 12.5f * k : dark ? 20 * k : (CHIP_H + CHIP_GAP) * k;
		float y = 0;
		for (Module m : wanted) {
			Chip c = chips.computeIfAbsent(m, key -> new Chip());
			c.appear.target = 1;
			if (!c.placed) {
				c.y.snap(y);
				c.placed = true;
			} else {
				c.y.target = y;
			}
			y += rowStep;
		}
		List<Spring> springs = new ArrayList<>();
		chips.forEach((m, c) -> {
			if (!wanted.contains(m)) {
				c.appear.target = 0;
			}
			springs.add(c.appear);
			springs.add(c.y);
		});
		springs.addAll(notifications.springs());
		TargetInfo target = settings.targetHud.get() ? targetSource.get() : null;
		if (target == null && settings.targetHud.get() && editing() && mc.player != null) {
			// A preview card in the HUD editor, so the target HUD can be placed without a fight.
			target = new TargetInfo(mc.player.getName().getString(), mc.player.getSkin(), 14, 20, 3.2f, 24);
		}
		if (target != null) {
			lastTarget = target;
			lastEntity = entitySource.get();
		}
		cardAppear.target = target != null ? 1 : 0;
		springs.add(cardAppear);
		elytraAppear.target = settings.elytraHud.get() && mc.player != null && (mc.player.isFallFlying() || editing()) ? 1 : 0;
		springs.add(elytraAppear);
		sampleSpeed(mc);
		updateLight(mc, (float) elapsed);
		azura.frame((float) elapsed, light);
		frameDt = (float) elapsed;
		boolean following = settings.targetPosition.is("Follow target");
		float moduleK = k;
		k = kFor("target");
		if (lastTarget != null && following) {
			aimCard(g, delta, dark);
			springs.add(cardX);
			springs.add(cardY);
		}
		boolean blockFixed = settings.blockInfoPosition.is("Fixed");
		k = kFor("blockinfo");
		BlockInfo block = blockSource.apply(editing() && blockFixed);
		if (block != null) {
			lastBlock = block;
		}
		blockAppear.target = block != null ? 1 : 0;
		springs.add(blockAppear);
		springs.add(blockW);
		if (lastBlock != null) {
			blockW.target = blockWidth(lastBlock, dark);
			if (!blockFixed) {
				aimBlock(g, delta, dark, block != null);
				springs.add(blockX);
				springs.add(blockY);
			}
		}
		Spring.advance(elapsed, springs);
		chips.entrySet().removeIf(e -> e.getValue().appear.target == 0 && e.getValue().appear.x < 0.02);
		ui.begin(g);
		ui.panelTheme(null);
		ui.panelPainter(azura::panel);
		var screen = mc.gui.screen();
		if (screen != null && !(screen instanceof net.minecraft.client.gui.screens.ChatScreen) && !editing()) {
			notifications.render(ui);
			return;
		}
		ui.masking(screen instanceof TempleScreen);
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		k = moduleK;
		ui.master(opacity("modules"));
		azura.moduleList(gw, gh, k);
		ui.master(1);
		notifications.render(ui);
		k = kFor("target");
		if (lastTarget != null && cardAppear.x > 0.02) {
			float cw = dark ? 168 * k : CARD_W * k;
			float ch = dark ? (settings.targetArmor.get() ? 44 : 58) * k : CARD_H * k;
			cardExtra = targetPanelHeight(lastEntity, cw);
			float[] pos = following ? new float[] {cardX.xf(), cardY.xf()}
					: place("target", (gw - cw) / 2, gh / 2f + 70 * k - ch / 2, cw, ch + cardExtra, gw, gh);
			float a = Math.max(0, Math.min(1, cardAppear.xf())) * opacity("target");
			azura.target(g, lastTarget, lastEntity, pos[0], pos[1], a, k);
			if (lastTarget.skin() == null && lastEntity != null && a > 0.4f) {
				mobHead(g, lastEntity, pos[0] - (1 - a) * 16 * k + 8 * k, pos[1] + 8 * k, 30 * k);
			}
			pops(g, lastEntity, pos[0] + 2 * k, pos[1] - 8 * k, k, a);
			float panelX = dark ? pos[0] + (1 - a) * 18 * k : pos[0] - (1 - a) * 24 * k;
			targetPanel(g, lastEntity, panelX, pos[1] + ch, cw, a);
		}
		k = kFor("blockinfo");
		if (lastBlock != null && blockAppear.x > 0.02) {
			if (blockW.x < 1) {
				blockW.snap(blockW.target);
			}
			float bw = blockW.xf();
			float bh = blockHeight(lastBlock);
			float[] pos = blockFixed ? place("blockinfo", gw / 2f + 24 * k, gh / 2f + 24 * k, bw, bh, gw, gh)
					: new float[] {blockX.xf(), blockY.xf()};
			ui.master(opacity("blockinfo"));
			blockCard(g, lastBlock, Math.max(0, Math.min(1, blockAppear.xf())), pos, bw, bh, dark);
			ui.master(1);
		} else if (blockAppear.x <= 0.02) {
			lastBlock = null;
			blockPlaced = false;
		}
		if (mc.player != null) {
			float leftY = EDGE * baseK;
			// Top of the screen, in stacking order: what is placed first keeps its spot and later widgets move below it.
			boolean darkStyle = azura.dark();
			if (!darkStyle && appear("info", settings.coordinates.get())) {
				k = kFor("info");
				azura.coordinates(g, mc, gw, gh, k, delta.getGameTimeDeltaPartialTick(true));
				ui.master(1);
			}
			if (appear("watermark", settings.watermark.get())) {
				k = kFor("watermark");
				azura.infoBar(g, mc, gw, gh, k);
				ui.master(1);
			}
			azura.stackY = stackBelow(darkStyle ? 4 : 70, "watermark");
			if (AzuraHud.lyricsBarActive()) {
				k = kFor("lyrics");
				ui.master(opacity("lyrics"));
				azura.lyricsBar(g, mc, gw, gh, k);
				ui.master(1);
				azura.stackY = stackBelow(azura.stackY / baseK, "lyrics");
			}
			nz.temple.client.modules.render.Music topMusic = nz.temple.client.modules.render.Music.instance();
			if (topMusic != null && topMusic.cardWanted(editing())) {
				k = kFor("music");
				float mw = topMusic.cardWidth(k);
				float mh = topMusic.cardHeight(k);
				float[] mp = place("music", (gw - mw) / 2, azura.stackY, mw, mh, gw, gh);
				ui.master(opacity("music"));
				topMusic.drawCard(g, ui, mp[0], mp[1], k, editing());
				ui.master(1);
				azura.stackY = stackBelow(azura.stackY / baseK, "music");
			}
			if (darkStyle && appear("info", settings.coordinates.get())) {
				k = kFor("info");
				azura.coordinates(g, mc, gw, gh, k, delta.getGameTimeDeltaPartialTick(true));
				ui.master(1);
			}
			if (appear("keybinds", settings.keybinds.get())) {
				k = kFor("keybinds");
				leftY = azura.keybinds(g, leftY, gw, gh, k) + 6 * k;
				ui.master(1);
			}
			if (appear("potions", settings.potions.get())) {
				k = kFor("potions");
				azura.effects(g, mc, leftY, gw, gh, k);
				ui.master(1);
			}
			if (appear("armor", settings.armor.get())) {
				azura.armorBox(g, mc, gw, gh);
				ui.master(1);
			}
			if (appear("keystrokes", settings.keystrokes.get())) {
				k = kFor("keystrokes");
				azura.keystrokes(g, mc, gw, gh, k);
				ui.master(1);
			}
			k = kFor("staff");
			ui.master(opacity("staff"));
			staffList(g, dark, gw, gh);
			k = kFor("region");
			ui.master(opacity("region"));
			regionWidget(g, dark, gw, gh);
			ui.master(1);
			if (appear("speedgraph", settings.speedGraph.get())) {
				k = kFor("speedgraph");
				azura.speedGraph(g, mc, gw, gh, k);
				ui.master(1);
			}
			k = kFor("elytra");
			ui.master(opacity("elytra"));
			elytraHud(g, mc, dark, gw, gh);
			if (settings.enemyArmor.get()) {
				k = kFor("enemies");
				ui.master(opacity("enemies"));
				enemies(g, mc, dark, gw, gh);
			}
			ui.master(1);
			nz.temple.client.modules.render.WorldMap map = nz.temple.client.modules.render.WorldMap.instance();
			if (map != null && map.minimapOn()) {
				k = kFor("minimap");
				float size = map.minimapSize() * k;
				float[] list = rects.get("modules");
				float defY = list != null && list[0] + list[2] > gw / 2f ? list[1] + list[3] + 8 * k : EDGE * k;
				float[] p = place("minimap", gw - size - EDGE * k, defY, size, size + 18 * k, gw, gh);
				ui.master(opacity("minimap"));
				map.drawMinimap(g, ui, p[0], p[1], size, k, delta.getGameTimeDeltaPartialTick(true));
				ui.master(1);
			}
		}
		float chatRight = (float) (net.minecraft.client.gui.components.ChatComponent.getWidth(mc.options.chatWidth().get()) * mc.options.chatScale().get()) + 8;
		float top = Float.MAX_VALUE;
		for (float[] r : rects.values()) {
			if (r[0] < chatRight && r[1] > gh * 0.5f) {
				top = Math.min(top, r[1]);
			}
		}
		bottomLeftTop = top;
		if (editing()) {
			drawHandles(g, mc);
			float below = 0;
			for (String id : new String[] {"watermark", "lyrics", "music", "info"}) {
				float[] r = rects.get(id);
				if (r != null && r[1] < 140 * baseK && Math.abs(r[0] + r[2] / 2 - g.guiWidth() / 2f) < 6) {
					below = Math.max(below, r[1] + r[3] + 10 * baseK);
				}
			}
			azura.editorOverlay(g, below);
		}
		// The editor's toolbar and add menu go last, on their own layer, so no widget draws over them.
		if (mc.gui.screen() instanceof nz.temple.client.gui.HudEditorScreen editor) {
			g.nextStratum();
			editor.drawOnTop(g);
		}
	}
	private static volatile float bottomLeftTop = Float.MAX_VALUE;
	public static float chatLift(int guiHeight) {
		Minecraft mc = Minecraft.getInstance();
		if (bottomLeftTop == Float.MAX_VALUE || mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
			return 0;
		}
		return Math.max(0, (guiHeight - 40) - (bottomLeftTop - 4));
	}

	/** Just below the centred top widget {@code id} if it is in its default top spot, else {@code fallback} HUD units down. */
	private float stackBelow(float fallback, String id) {
		float[] r = rects.get(id);
		float gw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		if (r == null || r[1] > 120 * baseK || Math.abs(r[0] + r[2] / 2 - gw / 2) > 4) {
			return fallback * baseK;
		}
		return r[1] + r[3] + 5 * baseK;
	}

	private final Map<String, float[]> presence = new HashMap<>();
	private float frameDt;

	/**
	 * Fades widget {@code id} in or out as it is turned on or off. Returns whether to draw it this frame; while it does, everything
	 * it draws is faded by its presence (the caller resets this with {@code ui.master(1)}).
	 */
	private boolean appear(String id, boolean on) {
		float[] p = presence.computeIfAbsent(id, key -> new float[] {on ? 1 : 0});
		p[0] += ((on ? 1 : 0) - p[0]) * (1 - (float) Math.exp(-12 * frameDt));
		if (!on && p[0] < 0.01f) {
			p[0] = 0;
			return false;
		}
		ui.master(Math.max(0, Math.min(1, p[0])) * opacity(id));
		return true;
	}

	public static boolean editing() {
		var screen = Minecraft.getInstance().gui.screen();
		return screen instanceof nz.temple.client.gui.HudEditorScreen || screen instanceof TempleScreen ts && ts.isEditingHud();
	}
	/** Where each widget was drawn this frame, in GUI pixels: id to {x, y, width, height}. */
	public java.util.Map<String, float[]> widgetRects() {
		return new java.util.LinkedHashMap<>(rects);
	}
	public void resetLayout() {
		if (config == null) {
			return;
		}
		for (String id : WIDGETS.keySet()) {
			config.putExtra("hud." + id + ".set", 0);
			config.putExtra("hud." + id + ".scale", 1);
			config.putExtra("hud." + id + ".opacity", 1);
		}
	}
	private static final Map<String, String> WIDGETS = Map.ofEntries(Map.entry("modules", "Module list"),
			Map.entry("target", "Target HUD"), Map.entry("watermark", "Info bar"), Map.entry("keybinds", "Keybinds"),
			Map.entry("potions", "Effects list"), Map.entry("armor", "Armor"), Map.entry("info", "Coordinates"), Map.entry("blockinfo", "Block info"),
			Map.entry("staff", "Staff"), Map.entry("region", "Donut Region"), Map.entry("speedgraph", "Speed graph"),
			Map.entry("elytra", "Elytra"), Map.entry("enemies", "Enemy armor"), Map.entry("minimap", "Minimap"),
			Map.entry("music", "Music"), Map.entry("lyrics", "Lyrics"), Map.entry("keystrokes", "Keystrokes"));
	/** Each element's outline in the HUD editor: theme-coloured on hover or while dragged, with its name above it. */
	private void drawHandles(GuiGraphicsExtractor g, Minecraft mc) {
		double scale = mc.getWindow().getGuiScale();
		double mx = mc.mouseHandler.xpos() / scale;
		double my = mc.mouseHandler.ypos() / scale;
		float u = baseK / Math.max(0.01f, settings.hudScale.getFloat() / 100f);
		float pad = 4 * u;
		String hover = null;
		if (dragging == null) {
			for (Map.Entry<String, float[]> e : rects.entrySet()) {
				float[] r = e.getValue();
				if (mx >= r[0] - pad && my >= r[1] - pad && mx <= r[0] + r[2] + pad && my <= r[1] + r[3] + pad) {
					hover = e.getKey();
				}
			}
		}
		String labelled = dragging != null ? dragging : hover;
		for (Map.Entry<String, float[]> e : rects.entrySet()) {
			float[] r = e.getValue();
			boolean active = e.getKey().equals(dragging);
			boolean over = e.getKey().equals(hover);
			int color = active ? HudTheme.light() : over ? HudTheme.accent() : 0x73FFFFFF;
			float x = r[0] - pad;
			float y = r[1] - pad;
			float w = r[2] + pad * 2;
			float h = r[3] + pad * 2;
			if (active || over) {
				ui.fill(x, y, w, h, 12 * u, HudTheme.alpha(HudTheme.accent(), active ? 0.14f : 0.08f));
			}
			ui.ring(x, y, w, h, 12 * u, (active ? 1.6f : 1.1f) * u, color);
		}
		if (dragging != null) {
			for (float[] gl : guides) {
				boolean vertical = gl[0] == gl[2];
				ui.fill(gl[0] - (vertical ? 0.5f * u : 0), gl[1] - (vertical ? 0 : 0.5f * u), vertical ? u : gl[2] - gl[0],
						vertical ? gl[3] - gl[1] : u, 0, HudTheme.alpha(HudTheme.light(), 0.75f));
			}
		}
		if (dragging != null && snapped) {
			ui.fill(g.guiWidth() / 2f - 0.5f * u, 0, u, g.guiHeight(), 0, HudTheme.alpha(HudTheme.light(), 0.6f));
		}
		if (labelled != null && rects.containsKey(labelled)) {
			float[] r = rects.get(labelled);
			String name = WIDGETS.getOrDefault(labelled, labelled);
			float lw = ui.textWidth(name, Fonts.SEMIBOLD, 9.5f * u) + 16 * u;
			float lx = r[0] - pad;
			float ly = r[1] - pad - 22 * u < 4 * u ? r[1] + r[3] + pad + 4 * u : r[1] - pad - 22 * u;
			ui.fill(lx, ly, lw, 18 * u, 7 * u, HudTheme.alpha(HudTheme.deep(), 0.92f));
			g.nextStratum();
			ui.textMiddle(name, lx + 8 * u, ly + 9 * u, 9.5f * u, 0xFFFFFFFF, Fonts.SEMIBOLD);
		}
	}

	private void darkModuleList(GuiGraphicsExtractor g, int gw, int gh, float totalH, boolean sidebar) {
		float size = 10 * k;
		float maxW = 40 * k;
		for (Module m : chips.keySet()) {
			maxW = Math.max(maxW, labelWidth(m, size) + (sidebar ? 12 : 18) * k);
		}
		float[] p = place("modules", gw - EDGE * 0.5f * k - maxW, EDGE * 0.5f * k, maxW, Math.max(totalH, 12 * k), gw, gh);
		boolean right = p[0] + maxW / 2 > gw / 2f;
		int index = 0;
		for (Map.Entry<Module, Chip> e : chips.entrySet()) {
			Chip c = e.getValue();
			float a = Math.max(0, Math.min(1, c.appear.xf()));
			Module m = e.getKey();
			float w = labelWidth(m, size) + (sidebar ? 12 : 18) * k;
			float rowY = p[1] + c.y.xf();
			float slide = (1 - a) * (w + 12 * k);
			float x = right ? p[0] + maxW - w + slide : p[0] - slide;
			int alpha = Math.round(a * 255);
			int accent = Theme.color(index * 0.07);
			if (sidebar) {
				float h = 12.5f * k;
				Draw2D.rect(g, x, rowY, x + w, rowY + h, fade(0xB80B0D12, a));
				float barX = right ? x + w - 1.5f * k : x;
				Draw2D.rect(g, barX, rowY, barX + 1.5f * k, rowY + h, (alpha << 24) | (accent & 0xFFFFFF));
				float glowX = right ? x + w - 7 * k : x + 1.5f * k;
				Draw2D.rect(g, glowX, rowY, glowX + 5.5f * k, rowY + h, (Math.round(a * 34) << 24) | (accent & 0xFFFFFF));
				int textColor = Ui.lerpColor(0xFFFFFFFF, 0xFF000000 | accent, 0.4f);
				label(m, x + (right ? 5 : 7) * k, rowY + h / 2, size, fade(textColor, a));
			} else {
				float h = 16 * k;
				ui.alpha(a);
				ui.fill(x, rowY, w, h, 5 * k, PANEL);
				ui.alpha(1);
				Draw2D.rect(g, x + 5 * k, rowY + h - 1.5f * k, x + w - 5 * k, rowY + h - 0.5f * k, (alpha << 24) | (accent & 0xFFFFFF));
				label(m, x + 9 * k, rowY + h / 2, size, fade(0xFFFFFFFF, a));
			}
			index++;
		}
	}
	private String info(Module m) {
		if (!settings.moduleInfo.get()) {
			return null;
		}
		String info = m.info();
		return info == null || info.isBlank() ? null : info;
	}
	private float labelWidth(Module m, float size) {
		float w = ui.textWidth(m.displayName(), Fonts.SEMIBOLD, size);
		String info = info(m);
		return info == null ? w : w + size * 0.4f + ui.textWidth(info, Fonts.MEDIUM, size);
	}

	private void label(Module m, float x, float cy, float size, int color) {
		String name = m.displayName();
		ui.textMiddle(name, x, cy, size, color, Fonts.SEMIBOLD);
		String info = info(m);
		if (info != null) {
			int dim = (Math.round((color >>> 24) * 0.7f) << 24) | 0xB4BAC4;
			ui.textMiddle(info, x + ui.textWidth(name, Fonts.SEMIBOLD, size) + size * 0.4f, cy, size, dim, Fonts.MEDIUM);
		}
	}
	private void glassModuleList(int gw, int gh, float totalH) {
		float maxW = 44;
		for (Module m : chips.keySet()) {
			maxW = Math.max(maxW, labelWidth(m, 10.5f) + 18);
		}
		float[] p = place("modules", gw - EDGE * k - maxW * k, EDGE * k, maxW * k, Math.max(totalH, CHIP_H * k), gw, gh);
		float right = p[0] + maxW * k;
		int index = 0;
		for (Map.Entry<Module, Chip> e : chips.entrySet()) {
			Module m = e.getKey();
			Chip c = e.getValue();
			float a = c.appear.xf();
			float w = labelWidth(m, 10.5f) + 18;
			float sx = (0.6f + 0.4f * a) * k;
			float sy = (0.4f + 0.6f * a) * k;
			ui.transform(right - w * sx + (1 - a) * 18 * k, p[1] + c.y.xf() + CHIP_H * k / 2 * (1 - sy / k), sx, sy);
			ui.alpha(Math.max(0, Math.min(1, a)));
			ui.glass(0, 0, w, CHIP_H, CHIP_H / 2);
			ui.fill(0, 0, w, CHIP_H, CHIP_H / 2, smoke());
			label(m, (w - labelWidth(m, 10.5f)) / 2, CHIP_H / 2, 10.5f, 0xFFFFFFFF);
			index++;
		}
		ui.resetTransform();
		ui.alpha(1);
	}
	private String[] watermarkInfo;
	private long watermarkAt;
	private float watermark(GuiGraphicsExtractor g, Minecraft mc, boolean dark, int gw, int gh) {
		long nowNanos = System.nanoTime();
		if (watermarkInfo == null || nowNanos - watermarkAt > 250_000_000L) {
			watermarkAt = nowNanos;
			int ping0 = 0;
			if (mc.getConnection() != null) {
				PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (pi != null) {
					ping0 = pi.getLatency();
				}
			}
			LocalTime now0 = LocalTime.now();
			watermarkInfo = java.util.stream.Stream.of(
					nz.temple.client.modules.client.StreamerMode.myName(mc.player.getName().getString()),
					settings.fps.get() ? mc.getFps() + " fps" : null,
					ping0 + " ms",
					String.format(Locale.ROOT, "%02d:%02d", now0.getHour(), now0.getMinute())
			).filter(java.util.Objects::nonNull).toArray(String[]::new);
		}
		String[] info = watermarkInfo;
		if (!dark) {
			float sz = 10f * k;
			float h = 18 * k;
			String brand = "Temple";
			String suffix = ".nz";
			float w = (10 + 13) * k + ui.textWidth(brand, Fonts.SEMIBOLD, sz) + ui.textWidth(suffix, Fonts.SEMIBOLD, sz);
			for (String part : info) {
				w += 12 * k + ui.textWidth(part, Fonts.MEDIUM, sz);
			}
			w += 8 * k;
			float[] p = place("watermark", EDGE * k, EDGE * k, w, h, gw, gh);
			glassPanel(p[0], p[1], w, h, h / 2);
			float cx = p[0] + 9 * k;
			float cy = p[1] + h / 2;
			float markSize = 11 * k;
			LogoMark.draw(g, ui.mapX(cx - 0.5f * k), ui.mapY(cy - markSize / 2), ui.mapY(markSize) - ui.mapY(0), HudModule.GOLD);
			cx += 13 * k;
			ui.textMiddle(brand, cx, cy, sz, 0xFFFFFFFF, Fonts.SEMIBOLD);
			cx += ui.textWidth(brand, Fonts.SEMIBOLD, sz);
			ui.textMiddle(suffix, cx, cy, sz, HudModule.GOLD, Fonts.SEMIBOLD);
			cx += ui.textWidth(suffix, Fonts.SEMIBOLD, sz);
			for (String part : info) {
				cx += 6 * k;
				ui.fill(cx, cy - 5 * k, Math.max(0.6f, 0.9f * k), 10 * k, 0.45f * k, 0x2BFFFFFF);
				cx += 6 * k;
				ui.textMiddle(part, cx, cy, sz, 0xDDFFFFFF, Fonts.MEDIUM);
				cx += ui.textWidth(part, Fonts.MEDIUM, sz);
			}
			return p[1] + h;
		}
		float size = 10 * k;
		float h = 19 * k;
		String[] parts = info;
		String brand = "Temple";
		String suffix = ".nz";
		float brandW = ui.textWidth(brand, Fonts.SEMIBOLD, size) + ui.textWidth(suffix, Fonts.SEMIBOLD, size);
		float w = 9 * k + 10 * k + 6 * k + brandW;
		for (String part : parts) {
			w += 13 * k + ui.textWidth(part, Fonts.MEDIUM, size);
		}
		w += 9 * k;
		float[] p = place("watermark", EDGE * 0.7f * k, EDGE * 0.7f * k, w, h, gw, gh);
		float x = p[0];
		float y = p[1];
		panel(x, y, w, h, 5 * k);
		gradientLine(g, x + 4 * k, y, w - 8 * k, 1.2f * k, 0);
		float cx = x + 9 * k;
		ui.fill(cx, y + h / 2 - 5 * k, 10 * k, 10 * k, 3 * k, Theme.color(0));
		ui.textMiddle("T", cx + 2.3f * k, y + h / 2, 8.5f * k, 0xFF0B0D12, Fonts.SEMIBOLD);
		cx += 16 * k;
		ui.textMiddle(brand, cx, y + h / 2, size, 0xFFFFFFFF, Fonts.SEMIBOLD);
		cx += ui.textWidth(brand, Fonts.SEMIBOLD, size);
		ui.textMiddle(suffix, cx, y + h / 2, size, Theme.color(0.2), Fonts.SEMIBOLD);
		cx += ui.textWidth(suffix, Fonts.SEMIBOLD, size);
		for (String part : parts) {
			cx += 6 * k;
			Draw2D.rect(g, cx, y + 5 * k, cx + Math.max(1, k * 0.8f), y + h - 5 * k, 0x33FFFFFF);
			cx += 7 * k;
			ui.textMiddle(part, cx, y + h / 2, size, 0xDDFFFFFF, Fonts.MEDIUM);
			cx += ui.textWidth(part, Fonts.MEDIUM, size);
		}
		return y + h;
	}
	private List<Module> boundModules = List.of();
	private long boundAt;
	private float keybinds(GuiGraphicsExtractor g, boolean dark, float top, int gw, int gh) {
		long nowNanos = System.nanoTime();
		if (nowNanos - boundAt > 250_000_000L) {
			boundAt = nowNanos;
			boundModules = modules.all().stream().filter(m -> m.keybind().isBound())
					.sorted(Comparator.comparing(Module::displayName)).limit(14).toList();
		}
		List<Module> bound = boundModules;
		boolean editing = editing();
		if (bound.isEmpty() && !editing) {
			return top - 6 * k;
		}
		float size = 9.5f * k;
		float keySize = 8.5f * k;
		float rowH = 15 * k;
		float headH = 20 * k;
		float w = 120 * k;
		for (Module m : bound) {
			w = Math.max(w, ui.textWidth(m.displayName(), Fonts.MEDIUM, size) + ui.textWidth(KeyNames.name(m.keybind().get()), Fonts.SEMIBOLD, keySize)
					+ 44 * k);
		}
		float h = headH + Math.max(1, bound.size()) * rowH + 6 * k;
		float[] p = place("keybinds", EDGE * 0.7f * k, top, w, h, gw, gh);
		float x = p[0];
		float y = p[1];
		if (dark) {
			panel(x, y, w, h, 7 * k);
			gradientLine(g, x + 4 * k, y, w - 8 * k, 1.2f * k, 0.3);
		} else {
			glassPanel(x, y, w, h, 11 * k);
		}
		long on = bound.stream().filter(Module::isEnabled).count();
		int accent = 0xFF000000 | (Theme.color(0) & 0xFFFFFF);
		Icons.draw(g, Icons.Icon.KEYBOARD, x + 13 * k, y + headH / 2 + 0.5f * k, 9.5f * k, accent);
		ui.textMiddle("Keybinds", x + 22 * k, y + headH / 2 + 0.5f * k, 9.5f * k, 0xFFFFFFFF, Fonts.SEMIBOLD);
		String count = on + " on";
		ui.textMiddle(count, x + w - 9 * k - ui.textWidth(count, Fonts.MEDIUM, keySize), y + headH / 2 + 0.5f * k, keySize, 0xB3FFFFFF,
				Fonts.MEDIUM);
		ui.fill(x + 9 * k, y + headH, w - 18 * k, Math.max(0.6f, 0.6f * k), 0, 0x24FFFFFF);
		float ry = y + headH + 3 * k;
		if (bound.isEmpty()) {
			ui.textMiddle("No modules bound", x + 9 * k, ry + rowH / 2, size, 0xB3FFFFFF, Fonts.MEDIUM);
		}
		for (Module m : bound) {
			boolean enabled = m.isEnabled();
			float cy = ry + rowH / 2;
			float dot = 5 * k;
			ui.fill(x + 10 * k, cy - dot / 2, dot, dot, dot / 2, enabled ? accent : 0x40FFFFFF);
			ui.textMiddle(m.displayName(), x + 20 * k, cy, size, enabled ? 0xFFFFFFFF : 0xB3FFFFFF, Fonts.MEDIUM);
			String key = KeyNames.name(m.keybind().get());
			float kw = Math.max(14 * k, ui.textWidth(key, Fonts.SEMIBOLD, keySize) + 9 * k);
			float kh = 11 * k;
			float kx = x + w - 9 * k - kw;
			ui.fill(kx, cy - kh / 2, kw, kh, 3.5f * k, enabled ? (0x40000000 | (accent & 0xFFFFFF)) : 0x1FFFFFFF);
			ui.textMiddle(key, kx + (kw - ui.textWidth(key, Fonts.SEMIBOLD, keySize)) / 2, cy, keySize, 0xFFFFFFFF, Fonts.SEMIBOLD);
			ry += rowH;
		}
		return y + h;
	}
	private void potions(GuiGraphicsExtractor g, Minecraft mc, boolean dark, float top, int gw, int gh) {
		List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
		if (effects.isEmpty()) {
			return;
		}
		effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
		float size = 9.5f * k;
		float rowH = 13 * k;
		float w = 100 * k;
		List<String[]> rows = new ArrayList<>();
		for (MobEffectInstance e : effects) {
			String name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
			String left;
			if (e.isInfiniteDuration()) {
				left = "∞";
			} else {
				int seconds = e.getDuration() / 20;
				left = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
			}
			rows.add(new String[] {name, left});
			w = Math.max(w, ui.textWidth(name, Fonts.MEDIUM, size) + ui.textWidth(left, Fonts.SEMIBOLD, size) + 30 * k);
		}
		float h = rows.size() * rowH + 8 * k;
		float[] p = place("potions", EDGE * 0.7f * k, top, w, h, gw, gh);
		if (dark) {
			panel(p[0], p[1], w, h, 5 * k);
		} else {
			glassPanel(p[0], p[1], w, h, 10 * k);
		}
		float ry = p[1] + 4 * k;
		for (int i = 0; i < rows.size(); i++) {
			int color = 0xFF000000 | effects.get(i).getEffect().value().getColor();
			float cy = ry + rowH / 2;
			Draw2D.rect(g, p[0] + 7 * k, cy - 3 * k, p[0] + 9 * k, cy + 3 * k, color);
			ui.textMiddle(rows.get(i)[0], p[0] + 14 * k, cy, size, 0xF0FFFFFF, Fonts.MEDIUM);
			String left = rows.get(i)[1];
			boolean ending = !effects.get(i).isInfiniteDuration() && effects.get(i).getDuration() < 200;
			ui.textMiddle(left, p[0] + w - 7 * k - ui.textWidth(left, Fonts.SEMIBOLD, size), cy, size,
					ending ? 0xFFFF6B5A : 0xB0FFFFFF, Fonts.SEMIBOLD);
			ry += rowH;
		}
	}
	private void armor(GuiGraphicsExtractor g, Minecraft mc, int gw, int gh) {
		float w = 4 * 18 * k;
		float h = 18 * k;
		float[] p = place("armor", gw / 2f - w / 2, gh - 66 * k, w, h, gw, gh);
		var pose = g.pose();
		for (int i = 0; i < ARMOR.length; i++) {
			ItemStack stack = mc.player.getItemBySlot(ARMOR[EQUIPMENT_ROW[i]]);
			if (stack.isEmpty()) {
				continue;
			}
			pose.pushMatrix();
			pose.translate(p[0] + (i * 18 + 1) * k, p[1] + 1 * k);
			pose.scale(k, k);
			g.item(stack, 0, 0);
			g.itemDecorations(mc.font, stack, 0, 0);
			pose.popMatrix();
		}
	}
	private void infoStack(GuiGraphicsExtractor g, Minecraft mc, boolean dark, int gw, int gh) {
		List<String> lines = new ArrayList<>();
		if (settings.coordinates.get()) {
			int x = (int) Math.floor(mc.player.getX());
			int y = (int) Math.floor(mc.player.getY());
			int z = (int) Math.floor(mc.player.getZ());
			boolean nether = mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.NETHER;
			lines.add(nether ? nz.temple.client.modules.client.StreamerMode.xyz(x, y, z) + "   Overworld "
					+ nz.temple.client.modules.client.StreamerMode.xz(x * 8, z * 8)
					: nz.temple.client.modules.client.StreamerMode.xyz(x, y, z) + "   Nether "
					+ nz.temple.client.modules.client.StreamerMode.xz(x / 8, z / 8));
		}
		if (settings.speed.get()) {
			double dx = mc.player.getX() - mc.player.xo;
			double dz = mc.player.getZ() - mc.player.zo;
			lines.add(String.format(Locale.ROOT, "%.1f b/s", Math.sqrt(dx * dx + dz * dz) * 20));
		}
		if (settings.fps.get() && !settings.watermark.get()) {
			lines.add(mc.getFps() + " fps");
		}
		if (lines.isEmpty()) {
			return;
		}
		float size = dark ? 9.5f * k : 10.5f * k;
		float rowH = dark ? 13 * k : 20 * k;
		float w = 0;
		for (String line : lines) {
			w = Math.max(w, ui.textWidth(line, dark ? Fonts.MEDIUM : Fonts.SEMIBOLD, size) + (dark ? 14 : 16) * k);
		}
		float h = lines.size() * rowH + (dark ? 6 * k : 0);
		float[] p = place("info", EDGE * 0.7f * k, gh - h - EDGE * 0.7f * k, w, h, gw, gh);
		if (dark) {
			panel(p[0], p[1], w, h, 5 * k);
			float ry = p[1] + 3 * k;
			for (String line : lines) {
				ui.textMiddle(line, p[0] + 7 * k, ry + rowH / 2, size, 0xE0FFFFFF, Fonts.MEDIUM);
				ry += rowH;
			}
		} else {
			float ry = p[1];
			for (String line : lines) {
				float lw = ui.textWidth(line, Fonts.SEMIBOLD, size) + 16 * k;
				glassPanel(p[0], ry, lw, 17 * k, 8.5f * k);
				ui.textMiddle(line, p[0] + 8 * k, ry + 8.5f * k, size, 0xFFFFFFFF, Fonts.SEMIBOLD);
				ry += rowH;
			}
		}
	}
	private void darkCard(GuiGraphicsExtractor g, TargetInfo t, LivingEntity entity, float a, float elapsed, float[] p) {
		float max = Math.max(t.maxHealth(), 1);
		shownHealth += (t.health() - shownHealth) * Math.min(1, elapsed * 12);
		if (trailHealth < shownHealth) {
			trailHealth = shownHealth;
		} else {
			trailHealth += (shownHealth - trailHealth) * Math.min(1, elapsed * 2.2f);
		}
		float w = 168 * k;
		float h = (settings.targetArmor.get() ? 44 : 58) * k;
		float x = p[0] + (1 - a) * 18 * k;
		float y = p[1];
		ui.alpha(a);
		panel(x, y, w, h + cardExtra, 7 * k);
		ui.alpha(1);
		int alpha = Math.round(a * 255);
		gradientLine(g, x + 6 * k, y, w - 12 * k, 1.2f * k, 0.5, alpha);
		float face = 34 * k;
		float fx = x + 8 * k;
		float fy = y + 8 * k;
		if (entity != null && entity.hurtTime > 0) {
			float flash = entity.hurtTime / 10f;
			Draw2D.rect(g, fx - 1, fy - 1, fx + face + 1, fy + face + 1, (Math.round(a * flash * 200) << 24) | 0xFF3B30);
		}
		if (t.skin() != null) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(fx, fy);
			pose.scale(face / 32f, face / 32f);
			PlayerFaceExtractor.extractRenderState(g, t.skin(), 0, 0, 32);
			pose.popMatrix();
		} else {
			ui.fill(fx, fy, face, face, 5 * k, 0x33FFFFFF);
			if (entity != null) {
				if (a > 0.4f) {
					mobHead(g, entity, fx, fy, face);
				}
			} else {
				ui.textMiddle(t.name().substring(0, 1).toUpperCase(Locale.ROOT), fx + face / 2 - 3.5f * k, fy + face / 2, 14 * k, fade(0xFFFFFFFF, a), Fonts.SEMIBOLD);
			}
		}
		float tx = fx + face + 8 * k;
		float right = x + w - 8 * k;
		ui.text(t.name(), tx, y + 7 * k, 10.5f * k, fade(0xFFFFFFFF, a), Fonts.SEMIBOLD);
		pops(g, entity, tx + ui.textWidth(t.name(), Fonts.SEMIBOLD, 10.5f * k) + 5 * k, y + 7 * k + 6.5f * k, k, a);
		String dist = String.format(Locale.ROOT, "%.1fm", t.distance());
		ui.text(dist, right - ui.textWidth(dist, Fonts.MEDIUM, 9 * k), y + 8 * k, 9 * k, fade(0x99FFFFFF, a), Fonts.MEDIUM);
		float barY = y + 22 * k;
		float barW = right - tx;
		float barH = 4.5f * k;
		float frac = Math.max(0, Math.min(1, shownHealth / max));
		float trail = Math.max(0, Math.min(1, trailHealth / max));
		Draw2D.rect(g, tx, barY, tx + barW, barY + barH, (Math.round(a * 40) << 24) | 0xFFFFFF);
		Draw2D.rect(g, tx, barY, tx + barW * trail, barY + barH, (Math.round(a * 150) << 24) | 0xFFFFFF);
		int slices = Math.max(1, Math.round(barW * frac / (3 * k)));
		for (int i = 0; i < slices; i++) {
			float s0 = tx + barW * frac * i / slices;
			float s1 = tx + barW * frac * (i + 1) / slices;
			Draw2D.rect(g, s0, barY, s1 + 0.5f, barY + barH, (alpha << 24) | (Theme.color(i * 0.5 / slices) & 0xFFFFFF));
		}
		String hp = String.format(Locale.ROOT, "%.1f", shownHealth);
		ui.text(hp, tx, barY + barH + 3 * k, 9 * k, fade(0xFFFFFFFF, a), Fonts.SEMIBOLD);
		float cursor = tx + ui.textWidth(hp, Fonts.SEMIBOLD, 9 * k) + 3 * k;
		if (entity != null && entity.getAbsorptionAmount() > 0) {
			String abs = String.format(Locale.ROOT, "+%.0f", entity.getAbsorptionAmount());
			ui.text(abs, cursor, barY + barH + 3 * k, 9 * k, fade(0xFFFFD60A, a), Fonts.SEMIBOLD);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			float mine = mc.player.getHealth() + mc.player.getAbsorptionAmount();
			float theirs = t.health() + (entity == null ? 0 : entity.getAbsorptionAmount());
			boolean winning = mine >= theirs;
			String verdict = winning ? "WINNING" : "LOSING";
			ui.text(verdict, right - ui.textWidth(verdict, Fonts.SEMIBOLD, 8.5f * k), barY + barH + 3.5f * k, 8.5f * k,
					fade(winning ? 0xFF34C759 : 0xFFFF453A, a), Fonts.SEMIBOLD);
		}
		if (entity != null && !settings.targetArmor.get()) {
			float itemScale = 0.62f * k;
			float ix = tx;
			float iy = y + h - 7 * k - 16 * itemScale;
			ItemStack[] stacks = {entity.getMainHandItem(), entity.getItemBySlot(EquipmentSlot.HEAD), entity.getItemBySlot(EquipmentSlot.CHEST),
					entity.getItemBySlot(EquipmentSlot.LEGS), entity.getItemBySlot(EquipmentSlot.FEET), entity.getItemBySlot(EquipmentSlot.OFFHAND)};
			var pose = g.pose();
			for (ItemStack stack : stacks) {
				if (!stack.isEmpty()) {
					pose.pushMatrix();
					pose.translate(ix, iy);
					pose.scale(itemScale, itemScale);
					g.item(stack, 0, 0);
					g.itemDecorations(mc.font, stack, 0, 0);
					pose.popMatrix();
				}
				ix += 17 * itemScale;
			}
		}
	}
	private void glassCard(GuiGraphicsExtractor g, TargetInfo t, float a, float elapsed, float[] p) {
		float max = Math.max(t.maxHealth(), 1);
		shownHealth += (t.health() - shownHealth) * Math.min(1, elapsed * 10);
		float frac = Math.max(0, Math.min(1, shownHealth / max));
		float sx = (0.8f + 0.2f * a) * k;
		float sy = (0.6f + 0.4f * a) * k;
		ui.transform(p[0] - (1 - a) * 24 * k, p[1] + CARD_H * k / 2 * (1 - sy / k), sx, sy);
		ui.alpha(Math.max(0, Math.min(1, a)));
		ui.glass(0, 0, CARD_W, CARD_H + cardExtra / k, 14);
		ui.fill(0, 0, CARD_W, CARD_H + cardExtra / k, 14, smoke());
		float face = 34;
		if (t.skin() != null) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ui.mapX(10), ui.mapY(10));
			pose.scale(sx * face / 32f, sy * face / 32f);
			PlayerFaceExtractor.extractRenderState(g, t.skin(), 0, 0, 32);
			pose.popMatrix();
		} else {
			ui.fill(10, 10, face, face, 9, 0x33FFFFFF);
			if (lastEntity == null) {
				ui.textMiddle(t.name().substring(0, 1).toUpperCase(Locale.ROOT), 10 + face / 2 - 4, 10 + face / 2, 14, 0xFFFFFFFF, Fonts.SEMIBOLD);
			}
		}
		float tx = 10 + face + 8;
		ui.text(t.name(), tx, 9, 11, 0xFFFFFFFF, Fonts.SEMIBOLD);
		float nameEnd = tx + ui.textWidth(t.name(), Fonts.SEMIBOLD, 11) + 5;
		float popX = ui.mapX(nameEnd);
		float popY = ui.mapY(15.5f);
		float barW = CARD_W - tx - 10;
		ui.fill(tx, 25, barW, 4.5f, 2.25f, 0x5914181E);
		ui.fill(tx, 25, barW * frac, 4.5f, 2.25f, Ui.lerpColor(0xFFFF453A, 0xFF34C759, frac));
		String meta = String.format(Locale.ROOT, "%.1f hp   %.1f m   %d ms", shownHealth, t.distance(), t.ping());
		ui.text(meta, tx, 34, 9.5f, 0xE0FFFFFF, Fonts.MEDIUM);
		float headX = ui.mapX(10);
		float headY = ui.mapY(10);
		float headSize = ui.mapX(10 + face) - headX;
		ui.resetTransform();
		ui.alpha(1);
		if (t.skin() == null && lastEntity != null && a > 0.4f) {
			mobHead(g, lastEntity, headX, headY, headSize);
		}
		pops(g, lastEntity, popX, popY, sy, a);
	}
	static void mobHead(GuiGraphicsExtractor g, LivingEntity entity, float x, float y, float size) {
		int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + size), y1 = Math.round(y + size);
		if (x1 - x0 < 4) {
			return;
		}
		float scale = Math.max(0.05f, entity.getScale());
		double head = Math.max(0.5, Math.min(1.6, entity.getBbWidth() * 1.1));
		double headY = Math.min(entity.getEyeHeight() + head * 0.1, entity.getBbHeight() - head * 0.35);
		float yOffset = (float) ((headY - entity.getBbHeight() / 2) / scale);
		int pixels = Math.max(1, Math.round((float) ((y1 - y0) / head * scale)));
		net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, pixels, yOffset,
				(x0 + x1) / 2f, (y0 + y1) / 2f, entity);
	}
	private void pops(GuiGraphicsExtractor g, LivingEntity entity, float x, float cy, float scale, float a) {
		if (entity == null) {
			return;
		}
		int count = nz.temple.client.util.TotemTracker.count(entity.getId());
		if (count <= 0) {
			return;
		}
		long since = Math.max(0, System.currentTimeMillis() - nz.temple.client.util.TotemTracker.lastPopMillis(entity.getId()));
		String ago = since < 1000 ? "now" : since < 60_000 ? since / 1000 + "s" : since / 60_000 + "m";
		String label = count + (count == 1 ? " pop" : " pops") + "  ·  " + ago;
		float flare = Math.max(0, 1 - since / 900f);
		float size = 8 * scale;
		float icon = 9 * scale;
		float w = icon + 4 * scale + ui.textWidth(label, Fonts.SEMIBOLD, size) + 8 * scale;
		float h = 11 * scale;
		ui.fill(x, cy - h / 2, w, h, h / 2, fade((Math.round(0x40 + 0x70 * flare) << 24) | (HudModule.GOLD & 0xFFFFFF), a));
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + 3 * scale, cy - icon / 2);
		pose.scale(icon / 16f, icon / 16f);
		g.item(new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING), 0, 0);
		pose.popMatrix();
		ui.textMiddle(label, x + 4 * scale + icon, cy, size, fade(Ui.lerpColor(0xFFFFE3A1, 0xFFFFFFFF, flare), a), Fonts.SEMIBOLD);
	}
	private void staffList(GuiGraphicsExtractor g, boolean dark, int gw, int gh) {
		if (!staffEnabled.getAsBoolean()) {
			return;
		}
		List<nz.temple.client.modules.player.StaffDisplay.Staff> staff = staffSource.get();
		boolean editing = editing();
		if (staff.isEmpty() && !staffShowEmpty.getAsBoolean() && !editing) {
			return;
		}
		float title = 9.5f * k;
		float size = 8.5f * k;
		float small = 7.5f * k;
		float rowH = 12 * k;
		float pad = 7 * k;
		String head = staff.isEmpty() ? "No staff online" : "Staff  ·  " + staff.size();
		float w = ui.textWidth(head, Fonts.SEMIBOLD, title) + 16 * k;
		for (var s : staff) {
			float rw = ui.textWidth(s.name(), Fonts.MEDIUM, size) + ui.textWidth(tag(s), Fonts.SEMIBOLD, small) + 20 * k;
			w = Math.max(w, rw);
		}
		w += pad * 2;
		float h = pad * 2 + 11 * k + staff.size() * rowH;
		float[] p = place("staff", gw - EDGE * k - w, gh * 0.38f, w, h, gw, gh);
		float x = p[0];
		float y = p[1];
		if (dark) {
			panel(x, y, w, h, 7 * k);
			gradientLine(g, x + 6 * k, y, w - 12 * k, 1.2f * k, 0.6);
		} else {
			ui.glass(x, y, w, h, 10 * k);
		}
		nz.temple.client.render.Icons.draw(g, nz.temple.client.render.Icons.Icon.EYE, x + pad + 4 * k, y + pad + 4.5f * k, 9 * k,
				staff.isEmpty() ? 0x99FFFFFF : 0xFFFFE3A1);
		ui.textMiddle(head, x + pad + 12 * k, y + pad + 5.5f * k, title, staff.isEmpty() ? 0xB3FFFFFF : 0xFFFFFFFF, Fonts.SEMIBOLD);
		float ry = y + pad + 11 * k + rowH / 2 + 1 * k;
		for (var s : staff) {
			int dot = s.spectator() ? 0xFF8E8E93 : 0xFF34C759;
			ui.fill(x + pad + 1 * k, ry - 2 * k, 4 * k, 4 * k, 2 * k, dot);
			ui.textMiddle(s.name(), x + pad + 8 * k, ry, size, 0xFFFFFFFF, Fonts.MEDIUM);
			String tag = tag(s);
			float tw = ui.textWidth(tag, Fonts.SEMIBOLD, small);
			ui.textMiddle(tag, x + w - pad - tw, ry, small, s.distance() >= 0 ? 0xFFFF9F0A : 0xFFFFE3A1, Fonts.SEMIBOLD);
			ry += rowH;
		}
	}
	private static String tag(nz.temple.client.modules.player.StaffDisplay.Staff s) {
		return s.distance() >= 0 ? s.role() + "  " + Math.round(s.distance()) + "m" : s.role();
	}
	private static int regionColor(String region) {
		return switch (region) {
			case "NA East" -> 0x4C8DFF;
			case "NA West" -> 0x3FBF7F;
			case "EU Central" -> 0xE9B949;
			case "EU West" -> 0xF08A3C;
			case "Asia" -> 0xE5534B;
			case "Oceania" -> 0x9B6BDF;
			default -> 0x8E8E93;
		};
	}
	private void regionWidget(GuiGraphicsExtractor g, boolean dark, int gw, int gh) {
		var region = modules.get(nz.temple.client.modules.player.DonutRegion.class);
		if (region == null || !region.isEnabled()) {
			return;
		}
		int cell = region.cell();
		String where = cell == 0 ? "Off the map" : region.region() + "  ·  " + nz.temple.util.DonutRegions.server(cell);
		String rtp = region.rtpActive() ? "RTP to Goliath " + region.targetGoliath()
				+ "  ·  try " + Math.max(1, region.attempts()) : null;
		float pad = 7 * k;
		float title = 9 * k;
		float small = 7.5f * k;
		if (!region.showMap()) {
			float w = 20 * k + ui.textWidth(where, Fonts.SEMIBOLD, title);
			if (rtp != null) {
				w = Math.max(w, 20 * k + ui.textWidth(rtp, Fonts.MEDIUM, small));
			}
			float h = (rtp != null ? 27 : 17) * k;
			float[] p = place("region", gw / 2f - w / 2, EDGE * k, w, h, gw, gh);
			if (dark) {
				panel(p[0], p[1], w, h, 6 * k);
			} else {
				ui.glass(p[0], p[1], w, h, 8.5f * k);
			}
			ui.fill(p[0] + 7 * k, p[1] + 8.5f * k - 2.5f * k, 5 * k, 5 * k, 2.5f * k, 0xFF000000 | regionColor(region.region()));
			ui.textMiddle(where, p[0] + 15 * k, p[1] + 8.5f * k, title, 0xFFFFFFFF, Fonts.SEMIBOLD);
			if (rtp != null) {
				ui.textMiddle(rtp, p[0] + 15 * k, p[1] + 19.5f * k, small, 0xFFFFE3A1, Fonts.MEDIUM);
			}
			return;
		}
		int n = nz.temple.util.DonutRegions.GRID;
		float gridW = region.mapWidth() * k;
		float cs = gridW / n;
		float head = 13 * k + (rtp != null ? 10 * k : 0);
		float legendRow = 10 * k;
		float legendH = legendRow * 3;
		float w = Math.max(gridW + pad * 2, ui.textWidth(where, Fonts.SEMIBOLD, title) + pad * 2);
		float h = pad + head + gridW + 5 * k + legendH + pad;
		float[] p = place("region", gw - EDGE * k - w, gh * 0.55f, w, h, gw, gh);
		float x = p[0];
		float y = p[1];
		if (dark) {
			panel(x, y, w, h, 7 * k);
		} else {
			ui.glass(x, y, w, h, 10 * k);
		}
		ui.textMiddle(where, x + pad, y + pad + 4.5f * k, title, cell == 0 ? 0xB3FFFFFF : 0xFFFFFFFF, Fonts.SEMIBOLD);
		if (rtp != null) {
			ui.textMiddle(rtp, x + pad, y + pad + 14.5f * k, small, 0xFFFFE3A1, Fonts.MEDIUM);
		}
		float gx = x + (w - gridW) / 2;
		float gy = y + pad + head;
		double s = Minecraft.getInstance().getWindow().getGuiScale();
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale((float) (1 / s), (float) (1 / s));
		int[] xs = new int[n + 1];
		int[] ys = new int[n + 1];
		for (int i = 0; i <= n; i++) {
			xs[i] = (int) Math.round((gx + i * cs) * s);
			ys[i] = (int) Math.round((gy + i * cs) * s);
		}
		g.fill(xs[0] - 1, ys[0] - 1, xs[n] + 1, ys[n] + 1, 0x80000000);
		for (int row = 0; row < n; row++) {
			for (int col = 0; col < n; col++) {
				g.fill(xs[col], ys[row], xs[col + 1], ys[row + 1],
						0xA6000000 | regionColor(nz.temple.util.DonutRegions.regionAt(col, row)));
			}
		}
		for (int row = 0; row < n; row++) {
			for (int col = 0; col < n; col++) {
				String here = nz.temple.util.DonutRegions.regionAt(col, row);
				if (col + 1 < n) {
					boolean edge = !here.equals(nz.temple.util.DonutRegions.regionAt(col + 1, row));
					g.fill(xs[col + 1] - (edge ? 1 : 0), ys[row], xs[col + 1] + 1, ys[row + 1], edge ? 0xB0000000 : 0x2E000000);
				}
				if (row + 1 < n) {
					boolean edge = !here.equals(nz.temple.util.DonutRegions.regionAt(col, row + 1));
					g.fill(xs[col], ys[row + 1] - (edge ? 1 : 0), xs[col + 1], ys[row + 1] + 1, edge ? 0xB0000000 : 0x2E000000);
				}
			}
		}
		if (region.rtpActive()) {
			int t = region.targetCell() - 1;
			float pulse = (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * 5));
			frame(g, xs[t % n], ys[t / n], xs[t % n + 1], ys[t / n + 1], Math.max(1, (int) Math.round(s)),
					(Math.round(0x80 + 0x7F * pulse) << 24) | 0xFFFFFF);
		}
		if (cell != 0) {
			int c = cell - 1;
			int x0 = xs[c % n], y0 = ys[c / n], x1 = xs[c % n + 1], y1 = ys[c / n + 1];
			g.fill(x0, y0, x1, y1, 0x4DFFFFFF);
			frame(g, x0, y0, x1, y1, Math.max(1, (int) Math.round(s)), 0xFF000000 | (HudModule.GOLD_LIGHT & 0xFFFFFF));
			double span = nz.temple.util.DonutRegions.MAX - nz.temple.util.DonutRegions.MIN;
			double dx = (gx + (region.playerX() - nz.temple.util.DonutRegions.MIN) / span * gridW) * s;
			double dy = (gy + (region.playerZ() - nz.temple.util.DonutRegions.MIN) / span * gridW) * s;
			double r = 2.6 * k * s;
			float pulse = (float) ((System.nanoTime() / 1e9 * 0.8) % 1.0);
			ring(g, dx, dy, r * (1.2 + 1.6 * pulse), Math.max(1, s * 0.6), (Math.round(0xC0 * (1 - pulse)) << 24) | 0xFFFFFF);
			disc(g, dx, dy + s * 0.4, r + s * 0.9, 0x70000000);
			disc(g, dx, dy, r + s * 0.7, 0xFF1C1C1E);
			disc(g, dx, dy, r, 0xFFFFFFFF);
		}
		pose.popMatrix();
		float numberSize = cs * 0.48f;
		if (numberSize * s >= 7) {
			for (int row = 0; row < n; row++) {
				for (int col = 0; col < n; col++) {
					if (cell == 1 + col + row * n) {
						continue;
					}
					String number = Integer.toString(nz.temple.util.DonutRegions.goliathAt(col, row));
					float tw = ui.textWidth(number, Fonts.SEMIBOLD, numberSize);
					ui.textMiddle(number, gx + col * cs + (cs - tw) / 2, gy + row * cs + cs / 2, numberSize, 0xE6FFFFFF,
							Fonts.SEMIBOLD);
				}
			}
		}
		float ly = gy + gridW + 5 * k;
		float colW = gridW / 2;
		String[] names = nz.temple.util.DonutRegions.REGIONS;
		for (int i = 0; i < names.length; i++) {
			float lx = gx + (i % 2) * colW;
			float cy = ly + (i / 2) * legendRow + legendRow / 2;
			boolean here = names[i].equals(region.region());
			Draw2D.rect(g, lx, cy - 2.5f * k, lx + 5 * k, cy + 2.5f * k, 0xFF000000 | regionColor(names[i]));
			ui.textMiddle(ellipsizeHud(names[i], colW - 9 * k, 7 * k), lx + 8 * k, cy, 7 * k,
					here ? 0xFFFFFFFF : 0xA6FFFFFF, here ? Fonts.SEMIBOLD : Fonts.MEDIUM);
		}
	}
	private static void frame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int t, int argb) {
		g.fill(x0, y0, x1, y0 + t, argb);
		g.fill(x0, y1 - t, x1, y1, argb);
		g.fill(x0, y0 + t, x0 + t, y1 - t, argb);
		g.fill(x1 - t, y0 + t, x1, y1 - t, argb);
	}
	private static void disc(GuiGraphicsExtractor g, double cx, double cy, double r, int argb) {
		int top = (int) Math.floor(cy - r);
		int bottom = (int) Math.ceil(cy + r);
		for (int y = top; y < bottom; y++) {
			double dy = y + 0.5 - cy;
			if (Math.abs(dy) > r) {
				continue;
			}
			double half = Math.sqrt(r * r - dy * dy);
			g.fill((int) Math.round(cx - half), y, (int) Math.round(cx + half), y + 1, argb);
		}
	}

	private static void ring(GuiGraphicsExtractor g, double cx, double cy, double r, double width, int argb) {
		int top = (int) Math.floor(cy - r);
		int bottom = (int) Math.ceil(cy + r);
		double inner = Math.max(0, r - width);
		for (int y = top; y < bottom; y++) {
			double dy = y + 0.5 - cy;
			if (Math.abs(dy) > r) {
				continue;
			}
			double outerHalf = Math.sqrt(r * r - dy * dy);
			if (Math.abs(dy) >= inner) {
				g.fill((int) Math.round(cx - outerHalf), y, (int) Math.round(cx + outerHalf), y + 1, argb);
				continue;
			}
			double innerHalf = Math.sqrt(inner * inner - dy * dy);
			g.fill((int) Math.round(cx - outerHalf), y, (int) Math.round(cx - innerHalf), y + 1, argb);
			g.fill((int) Math.round(cx + innerHalf), y, (int) Math.round(cx + outerHalf), y + 1, argb);
		}
	}
	private String ellipsizeHud(String s, float max, float size) {
		if (ui.textWidth(s, Fonts.MEDIUM, size) <= max) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && ui.textWidth(out + "…", Fonts.MEDIUM, size) > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out + "…";
	}
	private static final float BLOCK_PAD = 8;
	private static final float BLOCK_ICON = 20;
	private List<String> blockLines(BlockInfo b) {
		List<String> lines = new ArrayList<>(4);
		for (String s : new String[] {b.mining(), b.details()}) {
			if (s != null) {
				lines.add(s);
			}
		}
		return lines;
	}
	private float blockWidth(BlockInfo b, boolean dark) {
		float w = ui.textWidth(b.name(), Fonts.SEMIBOLD, 10.5f);
		if (b.coords() != null) {
			w = Math.max(w, ui.textWidth(b.name(), Fonts.SEMIBOLD, 10.5f) + 8 + ui.textWidth(b.coords(), Fonts.MEDIUM, 8));
		}
		for (String line : blockLines(b)) {
			w = Math.max(w, ui.textWidth(line, Fonts.MEDIUM, 8.5f));
		}
		return (BLOCK_PAD * 2 + BLOCK_ICON + 8 + w) * k;
	}
	private float blockHeight(BlockInfo b) {
		int lines = blockLines(b).size();
		return (BLOCK_PAD * 2 + Math.max(BLOCK_ICON, 13 + lines * 11)) * k;
	}
	private void aimBlock(GuiGraphicsExtractor g, DeltaTracker delta, boolean dark, boolean live) {
		if (projection == null || !projection.ready() || lastBlock == null) {
			return;
		}
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float w = blockW.target == 0 ? blockWidth(lastBlock, dark) : (float) blockW.target;
		float h = blockHeight(lastBlock);
		Vec3 c = Vec3.atCenterOf(lastBlock.pos());
		float[] s = projection.toScreen(c.x, c.y, c.z, gw, gh);
		if (s == null) {
			blockAppear.target = 0;
			return;
		}
		float r = Math.max(8, projection.screenRadius(c.x, c.y, c.z, 0.55, gw, gh));
		boolean left = s[0] > gw * 0.62f;
		float tx = left ? s[0] - r - 10 * k - w : s[0] + r + 10 * k;
		float ty = s[1] - h / 2;
		tx = Math.max(4 * k, Math.min(gw - w - 4 * k, tx));
		ty = Math.max(4 * k, Math.min(gh - h - 4 * k, ty));
		if (!blockPlaced || blockAppear.x < 0.02) {
			blockX.snap(tx);
			blockY.snap(ty);
			blockPlaced = true;
		} else if (live) {
			blockX.target = tx;
			blockY.target = ty;
		}
	}
	private void blockCard(GuiGraphicsExtractor g, BlockInfo b, float a, float[] p, float w, float h, boolean dark) {
		float x = p[0] + (1 - a) * 14 * k;
		float y = p[1];
		float sy = 0.7f + 0.3f * a;
		float cy = y + h / 2;
		float top = cy - h * sy / 2;
		float hh = h * sy;
		ui.alpha(a);
		if (dark) {
			panel(x, top, w, hh, 7 * k);
		} else {
			ui.glass(x, top, w, hh, 12 * k);
		}
		ui.alpha(1);
		if (dark) {
			gradientLine(g, x + 6 * k, top, w - 12 * k, 1.2f * k, 0.2, Math.round(a * 255));
		}
		if (a < 0.35f) {
			return;
		}
		float ta = (a - 0.35f) / 0.65f;
		float pad = BLOCK_PAD * k;
		float icon = BLOCK_ICON * k;
		float ix = x + pad;
		float iy = y + pad;
		ui.fill(ix - 2 * k, iy - 2 * k, icon + 4 * k, icon + 4 * k, 6 * k, fade(0x1FFFFFFF, ta));
		if (!b.icon().isEmpty()) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ix + 2 * k, iy + 2 * k);
			pose.scale(icon / 20f, icon / 20f);
			g.item(b.icon(), 0, 0);
			pose.popMatrix();
		}
		float tx = ix + icon + 8 * k;
		float right = x + w - pad;
		ui.textMiddle(b.name(), tx, iy + 5 * k, 10.5f * k, fade(0xFFFFFFFF, ta), Fonts.SEMIBOLD);
		if (b.coords() != null) {
			float cw = ui.textWidth(b.coords(), Fonts.MEDIUM, 8 * k);
			ui.textMiddle(b.coords(), right - cw, iy + 5.5f * k, 8 * k, fade(0x8CFFFFFF, ta), Fonts.MEDIUM);
		}
		float ly = iy + 5 * k + 12 * k;
		for (String line : blockLines(b)) {
			int colour = 0xCCFFFFFF;
			if (line == b.mining()) {
				colour = b.miningGood() ? 0xFFFFFFFF : 0xFFFF8A80;
				ui.fill(tx, ly - 2 * k, 4 * k, 4 * k, 2 * k, fade(b.miningGood() ? 0xFF34C759 : 0xFFFF453A, ta));
				ui.textMiddle(line, tx + 7 * k, ly, 8.5f * k, fade(colour, ta), Fonts.MEDIUM);
			} else {
				ui.textMiddle(line, tx, ly, 8.5f * k, fade(colour, ta), Fonts.MEDIUM);
			}
			ly += 11 * k;
		}
	}
	private void updateLight(Minecraft mc, float dt) {
		float want = 0.35f;
		if (mc.level != null && mc.player != null) {
			boolean sky = mc.level.dimensionType().hasSkyLight();
			float day = sky ? 1 - mc.level.getSkyDarken() / 11f : 0.25f;
			var eye = net.minecraft.core.BlockPos.containing(mc.player.getEyePosition());
			float open = sky ? mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eye) / 15f : 1;
			float up = Math.max(0, -mc.player.getXRot() / 90f);
			want = Math.min(1, day * open * (0.85f + 0.3f * up));
		}
		light += (want - light) * Math.min(1, dt * 2.5f);
	}
	/** HUD panels carry their own shade (see {@link AzuraHud#panel}), so no extra smoke on top. */
	private int smoke() {
		return 0;
	}

	private void glassPanel(float x, float y, float w, float h, float radius) {
		ui.glass(x, y, w, h, radius);
		ui.fill(x, y, w, h, radius, smoke());
	}
	private void sampleSpeed(Minecraft mc) {
		if (mc.player == null) {
			speedCount = 0;
			return;
		}
		int tick = mc.player.tickCount;
		if (tick == lastSpeedTick) {
			return;
		}
		if (tick < lastSpeedTick || tick - lastSpeedTick > 40) {
			speedCount = 0;
		}
		lastSpeedTick = tick;
		double dx = mc.player.getX() - mc.player.xo;
		double dy = mc.player.isFallFlying() ? mc.player.getY() - mc.player.yo : 0;
		double dz = mc.player.getZ() - mc.player.zo;
		speedSamples[speedHead] = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 20);
		speedHead = (speedHead + 1) % speedSamples.length;
		speedCount = Math.min(speedSamples.length, speedCount + 1);
	}

	private float smoothSample(float age) {
		int i = (int) Math.floor(age);
		float f = age - i;
		float a = (speedSample(Math.max(0, i - 1)) + speedSample(i) * 2 + speedSample(Math.min(speedCount - 1, i + 1))) / 4;
		int j = Math.min(speedCount - 1, i + 1);
		float b = (speedSample(Math.max(0, j - 1)) + speedSample(j) * 2 + speedSample(Math.min(speedCount - 1, j + 1))) / 4;
		return a + (b - a) * f;
	}
	private float speedSample(int age) {
		return speedSamples[Math.floorMod(speedHead - 1 - age, speedSamples.length)];
	}
	private final nz.temple.client.render.SmoothGraph speedGraphTexture = new nz.temple.client.render.SmoothGraph("speed");
	private float speedScale = 4;
	private long speedScaleNanos;
	private void speedGraph(GuiGraphicsExtractor g, boolean dark, int gw, int gh, float partial) {
		int span = Math.min(speedSamples.length, (int) Math.round(settings.graphLength.get() * 20));
		int n = Math.min(speedCount, span);
		float w = 132 * k;
		float h = 50 * k;
		float[] p = place("speedgraph", EDGE * 0.7f * k, gh - h - 64 * k, w, h, gw, gh);
		float radius = 9 * k;
		if (dark) {
			panel(p[0], p[1], w, h, 6 * k);
		} else {
			glassPanel(p[0], p[1], w, h, radius);
		}
		float now = n > 0 ? smoothSample(Math.min(n - 1, 1)) : 0;
		float peak = 0;
		for (int i = 0; i < n; i++) {
			peak = Math.max(peak, speedSample(i));
		}
		int accent = Theme.color(0) & 0xFFFFFF;
		String value = String.format(Locale.ROOT, "%.1f", now);
		float hy = p[1] + 10.5f * k;
		ui.textMiddle(value, p[0] + 9 * k, hy, 10.5f * k, 0xFFFFFFFF, Fonts.SEMIBOLD);
		ui.textMiddle("b/s", p[0] + 11.5f * k + ui.textWidth(value, Fonts.SEMIBOLD, 10.5f * k), hy + 0.5f * k, 7.5f * k, 0x8CFFFFFF, Fonts.MEDIUM);
		String top = String.format(Locale.ROOT, "max %.1f", peak);
		ui.textMiddle(top, p[0] + w - 9 * k - ui.textWidth(top, Fonts.MEDIUM, 7.5f * k), hy + 0.5f * k, 7.5f * k, 0x73FFFFFF, Fonts.MEDIUM);
		float gx0 = p[0] + 7 * k;
		float gx1 = p[0] + w - 7 * k;
		float gy0 = p[1] + 21 * k;
		float gy1 = p[1] + h - 6 * k;
		float target = Math.max(4, peak * 1.15f);
		long nanos = System.nanoTime();
		float dt = speedScaleNanos == 0 ? 1 : Math.min(0.1f, (nanos - speedScaleNanos) / 1e9f);
		speedScaleNanos = nanos;
		speedScale = target > speedScale ? speedScale + (target - speedScale) * (1 - (float) Math.exp(-14 * dt))
				: speedScale + (target - speedScale) * (1 - (float) Math.exp(-2.5f * dt));
		float max = Math.max(target / 1.15f, speedScale);
		Draw2D.rect(g, gx0, gy1, gx1, gy1 + Math.max(0.5f, 0.5f * k), 0x1FFFFFFF);
		if (n < 2) {
			return;
		}
		float step = (gx1 - gx0) / Math.max(1, span - 1);
		float shift = Math.max(0, Math.min(1, partial));
		int columns = Math.max(2, Math.round((gx1 - gx0) / 1.5f));
		float[] values = new float[columns];
		int count = 0;
		float firstX = gx0;

		float colAges = (gx1 - gx0) / (columns - 1f) / step;
		float half = Math.max(0.75f, colAges * 0.75f);
		int subs = Math.max(3, (int) Math.ceil(half * 2 / 0.25f));
		for (int i = 0; i < columns; i++) {
			float x = gx0 + (gx1 - gx0) * i / (columns - 1f);
			float age = (gx1 - x) / step + shift;
			if (age > n - 1) {
				continue;
			}
			if (count == 0) {
				firstX = x;
			}
			float sum = 0;
			float weights = 0;
			for (int s = 0; s < subs; s++) {
				float u = (s + 0.5f) / subs * 2 - 1;
				float a = Math.max(0, Math.min(n - 1, age + u * half));
				float wgt = 1 - Math.abs(u) * 0.6f;
				sum += smoothSample(a) * wgt;
				weights += wgt;
			}
			values[count++] = Math.min(1, sum / weights / max);
		}
		speedGraphTexture.draw(g, firstX, gy0 - 3 * k, gx1 - firstX + 3 * k, gy1 - gy0 + 3 * k, values, count, accent, 1.1f * k);
	}
	private static float vanillaBarsTop(Minecraft mc, int gh) {
		var player = mc.player;
		if (player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
			return gh - 29;
		}
		float hearts = (float) Math.ceil((player.getMaxHealth() + player.getAbsorptionAmount()) / 2f / 10f);
		int heartRow = Math.max(10 - ((int) hearts - 2), 3);
		float left = 39 + (hearts - 1) * heartRow + (player.getArmorValue() > 0 ? 10 : 0);
		float right = 39 + (player.getAirSupply() < player.getMaxAirSupply() || player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) ? 10 : 0);
		return gh - Math.max(left, right) - 10;
	}
	private void elytraHud(GuiGraphicsExtractor g, Minecraft mc, boolean dark, int gw, int gh) {
		if (elytraAppear.x < 0.02) {
			return;
		}
		float a = Math.max(0, Math.min(1, elytraAppear.xf()));
		var player = mc.player;
		Vec3 v = player.getDeltaMovement();
		boolean flying = player.isFallFlying();
		if (flying) {
			flightSpeed = v.length() * 20;
			flightClimb = v.y * 20;
		}
		boolean sample = !flying && editing();
		double speed = sample ? 33.6 : flightSpeed;
		double vy = sample ? -2.4 : flightClimb;
		int bx0 = (int) Math.floor(player.getX());
		int bz0 = (int) Math.floor(player.getZ());
		double ground = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx0, bz0);
		double above = Math.max(0, player.getY() - ground);
		double impact = vy < -0.5 ? above / -vy : Double.POSITIVE_INFINITY;
		boolean danger = flying && impact < 2.5 && above < 48;
		ItemStack elytra = player.getItemBySlot(EquipmentSlot.CHEST);
		int rockets = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(net.minecraft.world.item.Items.FIREWORK_ROCKET)) {
				rockets += stack.getCount();
			}
		}
		float w = 156 * k;
		float h = 62 * k;
		float[] pos = place("elytra", gw / 2f - w / 2, vanillaBarsTop(mc, gh) - h - 6, w, h, gw, gh);
		float px = pos[0];
		float py = pos[1] + (1 - a) * 12 * k;
		ui.alpha(a);
		if (dark) {
			panel(px, py, w, h, 7 * k);
		} else {
			glassPanel(px, py, w, h, 13 * k);
		}
		ui.alpha(1);
		if (danger) {
			float pulse = (float) (0.55 + 0.45 * Math.sin(System.nanoTime() / 1e9 * 14));
			ui.fill(px, py, w, h, 13 * k, fade((Math.round(0x40 * pulse) << 24) | 0xFF3B30, a));
			ui.ring(px, py, w, h, 13 * k, 1.2f * k, fade(0xD0FF453A, a));
		}
		int accent = 0xFF000000 | (Theme.color(0) & 0xFFFFFF);
		Icons.draw(g, Icons.Icon.ROCKET, px + 12 * k, py + 11 * k, 9.5f * k, fade(danger ? 0xFFFF453A : accent, a));
		String title = danger ? String.format(Locale.ROOT, "PULL UP  %.1fs", impact) : "ELYTRA";
		ui.textMiddle(title, px + 21 * k, py + 11 * k, 8 * k, fade(danger ? 0xFFFF6B5A : 0xB0FFFFFF, a), Fonts.SEMIBOLD);
		String count = rockets + (rockets == 1 ? " rocket" : " rockets");
		float cw = ui.textWidth(count, Fonts.MEDIUM, 8 * k);
		ui.textMiddle(count, px + w - 9 * k - cw, py + 11 * k, 8 * k, fade(rockets == 0 ? 0xB0FFFFFF : 0xE0FFFFFF, a), Fonts.MEDIUM);
		String big = String.format(Locale.ROOT, "%.1f", speed);
		ui.textMiddle(big, px + 11 * k, py + 31 * k, 18 * k, fade(0xFFFFFFFF, a), Fonts.SEMIBOLD);
		ui.textMiddle("b/s", px + 13 * k + ui.textWidth(big, Fonts.SEMIBOLD, 18 * k), py + 33.5f * k, 8.5f * k, fade(0x99FFFFFF, a),
				Fonts.MEDIUM);
		String height = String.format(Locale.ROOT, "Y %d", (int) Math.floor(player.getY()));
		String rate = String.format(Locale.ROOT, "%s %.1f", vy >= 0 ? "climb" : "sink", Math.abs(vy));
		float rx = px + w - 9 * k;
		ui.textMiddle(height, rx - ui.textWidth(height, Fonts.SEMIBOLD, 9.5f * k), py + 26 * k, 9.5f * k, fade(0xF0FFFFFF, a), Fonts.SEMIBOLD);
		int rateColour = vy >= 0.3 ? 0xFF34C759 : vy < -8 ? 0xFFFF6B5A : 0xB0FFFFFF;
		ui.textMiddle(rate, rx - ui.textWidth(rate, Fonts.MEDIUM, 8 * k), py + 37 * k, 8 * k, fade(rateColour, a), Fonts.MEDIUM);
		float by = py + h - 11 * k;
		if (!elytra.isEmpty()) {
			item(g, elytra, px + 8 * k, by - 5.5f * k, 11 * k);
		}
		float frac = elytra.isDamageableItem() ? 1 - (float) elytra.getDamageValue() / elytra.getMaxDamage() : 1;
		String pct = Math.round(frac * 100) + "%";
		float pw = ui.textWidth(pct, Fonts.SEMIBOLD, 8 * k);
		float bx = px + 23 * k;
		float bw = w - 23 * k - 13 * k - pw;
		ui.fill(bx, by - 1.75f * k, bw, 3.5f * k, 1.75f * k, fade(0x33FFFFFF, a));
		ui.fill(bx, by - 1.75f * k, Math.max(3.5f * k, bw * frac), 3.5f * k, 1.75f * k,
				fade(Ui.lerpColor(0xFFFF453A, 0xFF34C759, Math.min(1, frac * 1.6f)), a));
		ui.textMiddle(pct, px + w - 9 * k - pw, by, 8 * k, fade(frac < 0.1f ? 0xFFFF6B5A : 0xE0FFFFFF, a), Fonts.SEMIBOLD);
	}
	private void enemies(GuiGraphicsExtractor g, Minecraft mc, boolean dark, int gw, int gh) {
		double range = settings.enemyRange.get();
		List<net.minecraft.world.entity.player.Player> list = new ArrayList<>();
		for (var p : mc.level.players()) {
			if (p == mc.player || !p.isAlive() || p.isSpectator() || p.distanceTo(mc.player) > range) {
				continue;
			}
			if (!settings.enemyFriends.get() && friends != null && friends.isFriend(p.getName().getString())) {
				continue;
			}
			if (p == lastEntity && settings.targetHud.get() && settings.targetArmor.get() && cardAppear.x > 0.5) {
				continue;
			}
			list.add(p);
		}
		boolean sample = list.isEmpty() && editing();
		if (sample) {
			list.add(mc.player);
		}
		if (list.isEmpty()) {
			return;
		}
		list.sort(Comparator.comparingDouble(p -> p.distanceTo(mc.player)));
		int shown = Math.min(list.size(), (int) Math.round(settings.enemyCount.get()));
		float w = 188 * k;
		float head = 20 * k;
		float rowH = 46 * k;
		float h = head + shown * rowH + 2 * k;
		float[] p = place("enemies", gw - w - EDGE * 0.7f * k, gh / 2f - h / 2, w, h, gw, gh);
		if (dark) {
			panel(p[0], p[1], w, h, 7 * k);
		} else {
			ui.glass(p[0], p[1], w, h, 13 * k);
		}
		Icons.draw(g, Icons.Icon.CROSSHAIR, p[0] + 12 * k, p[1] + 11 * k, 9.5f * k, 0xFF000000 | (Theme.color(0) & 0xFFFFFF));
		ui.textMiddle("NEARBY", p[0] + 21 * k, p[1] + 11 * k, 8 * k, 0xB0FFFFFF, Fonts.SEMIBOLD);
		int players = sample ? 0 : list.size();
		String meta = players + (players == 1 ? " player" : " players") + " in " + Math.round(range) + "m";
		ui.textMiddle(meta, p[0] + w - 9 * k - ui.textWidth(meta, Fonts.MEDIUM, 8 * k), p[1] + 11 * k, 8 * k, 0x80FFFFFF, Fonts.MEDIUM);
		float ry = p[1] + head;
		for (int i = 0; i < shown; i++) {
			if (i > 0) {
				Draw2D.rect(g, p[0] + 9 * k, ry, p[0] + w - 9 * k, ry + Math.max(0.5f, 0.5f * k), 0x1FFFFFFF);
			}
			enemyRow(g, mc, list.get(i), p[0], ry, w);
			ry += rowH;
		}
	}
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	private void enemyRow(GuiGraphicsExtractor g, Minecraft mc, net.minecraft.world.entity.player.Player player, float x, float y, float w) {
		float face = 24 * k;
		float fx = x + 9 * k;
		float fy = y + 6 * k;
		if (player.hurtTime > 0) {
			ui.fill(fx - 1.5f * k, fy - 1.5f * k, face + 3 * k, face + 3 * k, 5 * k, (Math.round(player.hurtTime / 10f * 200) << 24) | 0xFF3B30);
		}
		if (player instanceof net.minecraft.client.player.AbstractClientPlayer client) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(fx, fy);
			pose.scale(face / 32f, face / 32f);
			PlayerFaceExtractor.extractRenderState(g, client.getSkin(), 0, 0, 32);
			pose.popMatrix();
		}
		float tx = fx + face + 8 * k;
		float right = x + w - 9 * k;
		String name = player.getName().getString();
		boolean friend = friends != null && friends.isFriend(name);
		ui.textMiddle(name, tx, y + 11 * k, 10 * k, friend ? 0xFF7CE0A0 : 0xFFFFFFFF, Fonts.SEMIBOLD);
		pops(g, player, tx + ui.textWidth(name, Fonts.SEMIBOLD, 10 * k) + 5 * k, y + 11 * k, 0.9f * k, 1);
		String dist = String.format(Locale.ROOT, "%.1fm", player == mc.player ? 0 : player.distanceTo(mc.player));
		ui.textMiddle(dist, right - ui.textWidth(dist, Fonts.MEDIUM, 8 * k), y + 11 * k, 8 * k, 0x99FFFFFF, Fonts.MEDIUM);
		float health = player.getHealth() + player.getAbsorptionAmount();
		float frac = Math.max(0, Math.min(1, player.getHealth() / Math.max(1, player.getMaxHealth())));
		String hp = String.valueOf(Math.round(health));
		String armor = String.valueOf(player.getArmorValue());
		float statsW = 11 * k + ui.textWidth(hp, Fonts.SEMIBOLD, 8 * k) + 6 * k + 11 * k + ui.textWidth(armor, Fonts.SEMIBOLD, 8 * k);
		float barY = y + 21 * k;
		float barW = right - tx - statsW - 7 * k;
		ui.fill(tx, barY - 1.5f * k, barW, 3 * k, 1.5f * k, 0x33FFFFFF);
		ui.fill(tx, barY - 1.5f * k, Math.max(3 * k, barW * frac), 3 * k, 1.5f * k, Ui.lerpColor(0xFFFF453A, 0xFF34C759, frac));
		if (player.getAbsorptionAmount() > 0) {
			float af = Math.min(1, player.getAbsorptionAmount() / Math.max(1, player.getMaxHealth()));
			ui.fill(tx, barY - 1.5f * k, Math.max(3 * k, barW * af), 3 * k, 1.5f * k, 0xC0FFD60A);
		}
		float sx = right - statsW;
		Icons.draw(g, Icons.Icon.HEART, sx + 4 * k, barY, 8 * k, 0xFFFF6B7A);
		ui.textMiddle(hp, sx + 10 * k, barY, 8 * k, 0xF0FFFFFF, Fonts.SEMIBOLD);
		sx += 11 * k + ui.textWidth(hp, Fonts.SEMIBOLD, 8 * k) + 6 * k;
		Icons.draw(g, Icons.Icon.SHIELD, sx + 4 * k, barY, 8 * k, 0xFF9CC8FF);
		ui.textMiddle(armor, sx + 10 * k, barY, 8 * k, 0xF0FFFFFF, Fonts.SEMIBOLD);
		float slot = 17 * k;
		float ix = tx;
		float iy = y + 27 * k;
		for (EquipmentSlot s : WORN) {
			if (s == EquipmentSlot.MAINHAND) {
				ix += 5 * k;
			}
			ItemStack stack = player.getItemBySlot(s);
			boolean totem = stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
			ui.fill(ix, iy, slot - 2 * k, slot - 2 * k, 4 * k, totem ? 0x48E9B949 : 0x16FFFFFF);
			if (!stack.isEmpty()) {
				item(g, stack, ix + 1 * k, iy + 0.5f * k, slot - 4 * k);
				if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
					float df = 1 - (float) stack.getDamageValue() / stack.getMaxDamage();
					float bw = slot - 6 * k;
					float bY = iy + slot - 4.2f * k;
					ui.fill(ix + 2 * k, bY, bw, 1.6f * k, 0.8f * k, 0x50000000);
					ui.fill(ix + 2 * k, bY, Math.max(1.6f * k, bw * df), 1.6f * k, 0.8f * k,
							Ui.lerpColor(0xFFFF453A, 0xFF34C759, Math.min(1, df * 1.6f)));
				} else if (stack.getCount() > 1) {
					String c = String.valueOf(stack.getCount());
					ui.text(c, ix + slot - 3 * k - ui.textWidth(c, Fonts.SEMIBOLD, 6.5f * k), iy + slot - 9.5f * k, 6.5f * k, 0xFFFFFFFF,
							Fonts.SEMIBOLD);
				}
			}
			ix += slot;
		}
	}

	private float targetPanelHeight(LivingEntity entity, float w) {
		if (entity == null) {
			return 0;
		}
		boolean armor = settings.targetArmor.get();
		int effects = settings.targetEffects.get() ? nz.temple.client.util.TargetEffects.of(entity).size() : 0;
		if (!armor && effects == 0) {
			return 0;
		}
		int perRow = Math.max(1, (int) ((w - 12 * k) / (30 * k)));
		int effectRows = effects == 0 ? 0 : (effects + perRow - 1) / perRow;
		return 4 * k + (armor ? 20 * k : 0) + effectRows * 20 * k + 2 * k;
	}
	private void targetPanel(GuiGraphicsExtractor g, LivingEntity entity, float x, float y, float w, float a) {
		if (entity == null || a < 0.02f || cardExtra <= 0) {
			return;
		}
		boolean armor = settings.targetArmor.get();
		List<nz.temple.client.util.TargetEffects.Shown> effects = settings.targetEffects.get()
				? nz.temple.client.util.TargetEffects.of(entity) : List.of();
		if (!armor && effects.isEmpty()) {
			return;
		}
		float slot = 17 * k;
		float rowH = 20 * k;
		int perRow = Math.max(1, (int) ((w - 12 * k) / (30 * k)));
		int effectRows = effects.isEmpty() ? 0 : (effects.size() + perRow - 1) / perRow;
		float px = x;
		ui.fill(px + 9 * k, y, w - 18 * k, Math.max(0.6f, 0.6f * k), 0, fade(0x24FFFFFF, a));
		float cy = y + 4 * k;
		if (armor) {
			float ix = px + 8 * k;
			for (EquipmentSlot s : WORN) {
				if (s == EquipmentSlot.MAINHAND) {
					ix += 5 * k;
				}
				ItemStack stack = entity.getItemBySlot(s);
				boolean totem = stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
				ui.fill(ix, cy, slot - 2 * k, slot - 2 * k, 4 * k, fade(totem ? 0x48E9B949 : 0x16FFFFFF, a));
				if (!stack.isEmpty()) {
					item(g, stack, ix + 1 * k, cy + 0.5f * k, slot - 4 * k);
					durability(stack, ix, cy, slot, a);
				}
				ix += slot;
			}
			if (entity instanceof net.minecraft.world.entity.player.Player player) {
				String points = String.valueOf(player.getArmorValue());
				float tw = ui.textWidth(points, Fonts.SEMIBOLD, 8.5f * k);
				float rx = px + w - 9 * k - tw;
				Icons.draw(g, Icons.Icon.SHIELD, rx - 6 * k, cy + slot / 2 - 1 * k, 8.5f * k, fade(0xFF9CC8FF, a));
				ui.textMiddle(points, rx, cy + slot / 2 - 1 * k, 8.5f * k, fade(0xF0FFFFFF, a), Fonts.SEMIBOLD);
			}
			cy += rowH;
		}
		for (int i = 0; i < effects.size(); i++) {
			var e = effects.get(i);
			float ex = px + 8 * k + (i % perRow) * 30 * k;
			float ey = cy + (i / perRow) * rowH;
			boolean harmful = !e.effect().value().isBeneficial();
			ui.fill(ex, ey, 27 * k, slot - 2 * k, 4 * k, fade(harmful ? 0x30FF453A : 0x1CFFFFFF, a));
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ex + 1.5f * k, ey + 1 * k);
			float iconScale = (slot - 4 * k) / 18f;
			pose.scale(iconScale, iconScale);
			g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(e.effect()),
					0, 0, 18, 18, fade(0xFFFFFFFF, a));
			pose.popMatrix();
			String label = e.level() > 1 ? roman(e.level()) : e.level() == 1 ? "I" : "";
			if (e.ticks() >= 0) {
				int seconds = e.ticks() / 20;
				label = seconds >= 60 ? seconds / 60 + "m" : seconds + "s";
			}
			if (!label.isEmpty()) {
				ui.textMiddle(label, ex + slot - 1 * k, ey + (slot - 2 * k) / 2, 6.5f * k, fade(0xE6FFFFFF, a), Fonts.SEMIBOLD);
			}
		}
	}
	private void durability(ItemStack stack, float ix, float iy, float slot, float a) {
		if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return;
		}
		float df = 1 - (float) stack.getDamageValue() / stack.getMaxDamage();
		float bw = slot - 6 * k;
		float bY = iy + slot - 4.2f * k;
		ui.fill(ix + 2 * k, bY, bw, 1.6f * k, 0.8f * k, fade(0x50000000, a));
		ui.fill(ix + 2 * k, bY, Math.max(1.6f * k, bw * df), 1.6f * k, 0.8f * k,
				fade(Ui.lerpColor(0xFFFF453A, 0xFF34C759, Math.min(1, df * 1.6f)), a));
	}
	private static void item(GuiGraphicsExtractor g, ItemStack stack, float x, float y, float size) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / 16f, size / 16f);
		g.item(stack, 0, 0);
		pose.popMatrix();
	}
	private void panel(float x, float y, float w, float h, float radius) {
		ui.shadow(x, y + 2 * k, w, h, radius, 7 * k, 0x48000000);
		ui.fill(x, y, w, h, radius, PANEL);
	}
	private void gradientLine(GuiGraphicsExtractor g, float x, float y, float w, float h, double offset) {
		gradientLine(g, x, y, w, h, offset, 255);
	}

	private void gradientLine(GuiGraphicsExtractor g, float x, float y, float w, float h, double offset, int alpha) {
		int slices = Math.max(4, Math.round(w / (4 * k)));
		for (int i = 0; i < slices; i++) {
			float s0 = x + w * i / slices;
			float s1 = x + w * (i + 1) / slices;
			float edge = Math.min(1, Math.min(i + 1, slices - i) / (slices * 0.15f));
			Draw2D.rect(g, s0, y, s1 + 0.5f, y + Math.max(1, h), (Math.round(alpha * edge) << 24) | (Theme.color(offset + (double) i / slices * 0.5) & 0xFFFFFF));
		}
	}
	private static int fade(int argb, float a) {
		return (Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a))) << 24) | (argb & 0xFFFFFF);
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
}
