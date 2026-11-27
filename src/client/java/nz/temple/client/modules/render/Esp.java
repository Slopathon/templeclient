package nz.temple.client.modules.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import nz.temple.client.util.VisualRange;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Projection;
import nz.temple.client.render.Theme;
import nz.temple.client.render.UiScale;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.Entities;
import nz.temple.client.util.EntityCatalog;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.EntitySelection;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Esp extends Module {
	private static Esp instance;
	private final EntitySelection entities;
	private final NumberSetting range;
	private final ModeSetting mode;
	private final BoolSetting outline;
	private final NumberSetting fill;
	private final NumberSetting lineWidth;
	private final NumberSetting expand;
	private final ModeSetting colorMode;
	private final ColorSetting playerColor;
	private final ColorSetting hostileColor;
	private final ColorSetting passiveColor;
	private final ColorSetting otherColor;
	private final ColorSetting staticColor;
	private final BoolSetting names;
	private final BoolSetting healthBar;
	private final BoolSetting distance;
	private final NumberSetting maxTargets;
	public Esp() {
		super("ESP", Category.RENDER, "Highlights selected entities through walls with boxes, silhouettes or outlines.");
		instance = this;
		entities = add(new EntitySelection("Entities", defaults())
				.describe("Choose which entity types are highlighted."));
		range = add(new NumberSetting("Range", 128, 8, 512, 4, " m").describe("Only highlights entities within this distance."));
		maxTargets = add(new NumberSetting("Target limit", 64, 8, 256, 8, "").describe("Only the nearest entities, up to this many, get "
				+ "boxes and labels."));
		group("Look");
		mode = add(new ModeSetting("Mode", List.of("Box", "Corners", "Hitbox", "Shaded", "Glow"), "Box")
				.describe("Box draws a flat frame with a light fill. Corners draws only the frame's corners. Hitbox draws a 3D wireframe of "
						+ "the hitbox and works at any distance. Shaded fills the entity's silhouette with a thin border. Glow draws only "
						+ "the outline. Shaded and Glow only work on entities that are on screen and within Minecraft's entity render "
						+ "distance."));
		outline = add(new BoolSetting("Outline", true).describe("Draws the border around the silhouette.")
				.visibleWhen(() -> mode.is("Shaded")));
		fill = add(new NumberSetting("Fill opacity", 85, 0, 100, 1, "%").describe("How solid the fill is.")
				.visibleWhen(() -> !mode.is("Glow")));
		lineWidth = add(new NumberSetting("Line width", 1, 1, 3, 0.5, " px").describe("Line width in screen pixels.")
				.visibleWhen(() -> mode.is("Hitbox") || flat()));
		expand = add(new NumberSetting("Box padding", 0, 0, 0.4, 0.02, " b").describe("Expands the hitbox slightly so lines do not sit on "
				+ "the model.")
				.visibleWhen(() -> mode.is("Hitbox")));
		group("Colours");
		colorMode = add(new ModeSetting("Color", List.of("Type", "Theme", "Health", "Distance", "Static"), "Type")
				.describe("Type colours players, hostiles, passives and others separately. Theme uses the HUD accent. Health goes from "
						+ "green to red. Distance goes from red when close to green when far. Static uses one colour."));
		playerColor = add(new ColorSetting("Players", 0xFFFFFFFF).themed().describe("Player colour in Type mode.").visibleWhen(() -> colorMode.is("Type")));
		hostileColor = add(new ColorSetting("Hostiles", 0xFFFF5A5A).describe("Hostile mob colour in Type mode.").visibleWhen(() -> colorMode.is("Type")));
		passiveColor = add(new ColorSetting("Passives", 0xFF34C759).describe("Animal colour in Type mode.").visibleWhen(() -> colorMode.is("Type")));
		otherColor = add(new ColorSetting("Others", 0xFFAEB4BE).describe("Items, vehicles and everything else in Type mode.").visibleWhen(() -> colorMode.is("Type")));
		staticColor = add(new ColorSetting("Static color", 0xFFE9B949).themed().describe("Colour in Static mode.").visibleWhen(() -> colorMode.is("Static")));
		group("Labels");
		names = add(new BoolSetting("Names", true).describe("Shows a name label above each entity."));
		distance = add(new BoolSetting("Distance", true).describe("Shows distance in the label."));
		healthBar = add(new BoolSetting("Health bar", true).describe("Shows a health bar next to living entities."));
	}
	public static Esp instance() {
		return instance;
	}

	private static List<String> defaults() {
		List<String> ids = new ArrayList<>(EntityCatalog.idsInCategory("monster"));
		ids.add("minecraft:player");
		return ids;
	}
	private boolean shows(Entity entity, LocalPlayer self) {
		if (entity == self || !entity.isAlive() || entity.isInvisible() || !entities.has(Entities.id(entity))) {
			return false;
		}
		double reach = VisualRange.of(range.get());
		return entity.distanceToSqr(self) <= reach * reach;
	}
	public boolean glows(Entity entity) {
		if (!isEnabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean wanted = mode.is("Glow") || mode.is("Shaded") && outline.get();
		return wanted && mc.player != null && shows(entity, mc.player);
	}
	public int outlineColor(Entity entity) {
		return colorFor(entity) & 0xFFFFFF;
	}

	public int fillArgb(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !mode.is("Shaded") || fill.get() <= 0 || mc.player == null || !shows(entity, mc.player)) {
			return 0;
		}
		return (Math.round(fill.getFloat() / 100f * 255) << 24) | (colorFor(entity) & 0xFFFFFF);
	}
	private List<Entity> tickTargets = List.of();
	private long tickTargetsAt = Long.MIN_VALUE;
	private net.minecraft.client.multiplayer.ClientLevel tickTargetsLevel;
	private List<Entity> targets(Minecraft mc, LocalPlayer self) {
		long now = mc.level.getGameTime();
		if (now != tickTargetsAt || mc.level != tickTargetsLevel) {
			tickTargets = pickTargets(mc, self);
			tickTargetsAt = now;
			tickTargetsLevel = mc.level;
		} else if (tickTargets.stream().anyMatch(Entity::isRemoved)) {
			tickTargets = tickTargets.stream().filter(e -> !e.isRemoved()).toList();
		}
		return tickTargets;
	}
	private List<Entity> pickTargets(Minecraft mc, LocalPlayer self) {
		List<Entity> result = new ArrayList<>();
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (shows(entity, self)) {
				result.add(entity);
			}
		}
		int limit = (int) Math.round(maxTargets.get());
		result.sort(Comparator.comparingDouble(e -> e.distanceToSqr(self)));
		return result.size() > limit ? result.subList(0, limit) : result;
	}
	public void collect(List<WorldEsp.Box> out) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer self = mc.player;
		if (!isEnabled() || !mode.is("Hitbox") || self == null || mc.level == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int fillAlpha = Math.round(fill.getFloat() / 100f * 90);
		for (Entity entity : targets(mc, self)) {
			int rgb = colorFor(entity) & 0xFFFFFF;
			AABB bounds = entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position())).inflate(expand.get());
			out.add(new WorldEsp.Box(bounds, 0xFF000000 | rgb, fillAlpha > 0 ? (fillAlpha << 24) | rgb : 0,
					lineWidth.getFloat() / 100f));
		}
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer self = mc.player;
		boolean flat = flat();
		if (!isEnabled() || self == null || mc.level == null || !(flat || names.get() || healthBar.get() || distance.get())
				|| !projection.ready()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float uiScale = UiScale.factor();
		float p = 1f / Math.max(1, (float) mc.getWindow().getGuiScale());
		int px = Math.max(1, Math.round(lineWidth.getFloat()));
		List<Entity> list = targets(mc, self);
		int drawn = 0;
		pendingText = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			Entity entity = list.get(i);
			float[] box = screenBox(entity, partial, gw, gh, projection);
			if (box == null) {
				continue;
			}
			if (drawn++ % STRATUM_EVERY == 0) {
				endGroup(g);
				g.nextStratum();
			}
			float x0 = box[0], y0 = box[1], x1 = box[2], y1 = box[3];
			int colour = 0xFF000000 | colorFor(entity);
			if (flat) {
				frame(x0, y0, x1, y1, p, px, colour);
			}
			if (healthBar.get() && entity instanceof LivingEntity) {
				float frac = Entities.healthFraction(entity);
				float bx1 = x0 - 3 * p;
				float bx0 = bx1 - (2 + 2) * p;
				Draw2D.pixelRect(shapes, bx0, y0 - p, bx1, y1 + p, 0xB0000000);
				float top = y1 - (y1 - y0) * frac;
				Draw2D.pixelRect(shapes, bx0 + p, top, bx1 - p, y1, healthColor(frac));
			}
			if (names.get() || distance.get()) {
				label(entity, self, (x0 + x1) / 2, y0 - 3 * p, uiScale, p, colour);
			}
		}
		endGroup(g);
	}
	private static final int STRATUM_EVERY = 12;
	private final nz.temple.client.render.ShapeBatch shapes = new nz.temple.client.render.ShapeBatch();
	private Run[] textRuns = new Run[48];
	private int[] textColours = new int[48];
	private float[] textAt = new float[48 * 3];
	private int pendingText;
	private void queueText(Run run, int colour, float x, float y, float scale) {
		if (pendingText == textRuns.length) {
			textRuns = java.util.Arrays.copyOf(textRuns, pendingText * 2);
			textColours = java.util.Arrays.copyOf(textColours, pendingText * 2);
			textAt = java.util.Arrays.copyOf(textAt, pendingText * 6);
		}
		textRuns[pendingText] = run;
		textColours[pendingText] = colour;
		textAt[pendingText * 3] = x;
		textAt[pendingText * 3 + 1] = y;
		textAt[pendingText * 3 + 2] = scale;
		pendingText++;
	}
	private void endGroup(GuiGraphicsExtractor g) {
		shapes.flush(g);
		if (pendingText == 0) {
			return;
		}
		var font = Minecraft.getInstance().font;
		Matrix3x2fStack pose = g.pose();
		for (int i = 0; i < pendingText; i++) {
			float scale = textAt[i * 3 + 2];
			pose.pushMatrix();
			pose.translate(textAt[i * 3], textAt[i * 3 + 1]);
			pose.scale(scale, scale);
			g.text(font, textRuns[i].text(), 0, 0, textColours[i], false);
			pose.popMatrix();
			textRuns[i] = null;
		}
		pendingText = 0;
	}
	private boolean flat() {
		return mode.is("Box") || mode.is("Corners");
	}

	private void frame(float x0, float y0, float x1, float y1, float p, int px, int colour) {
		int rgb = colour & 0xFFFFFF;
		if (mode.is("Box")) {
			int top = Math.round(fill.getFloat() / 100f * 40);
			if (top > 0) {
				float s = 1 / p;
				shapes.gradient(Math.round(Math.round(x0) * s), Math.round(Math.round(y0) * s), Math.round(Math.round(x1) * s),
						Math.round(Math.round(y1) * s), (top << 24) | rgb, (top / 5 << 24) | rgb);
			}
			Draw2D.pixelOutline(shapes, x0 - p, y0 - p, x1 + p, y1 + p, px + 2, 0x78000000);
			Draw2D.pixelOutline(shapes, x0, y0, x1, y1, px, colour);
		} else {
			float len = Math.min(x1 - x0, y1 - y0) * 0.28f;
			Draw2D.pixelCorners(shapes, x0 - p, y0 - p, x1 + p, y1 + p, px + 2, len + p, 0x78000000);
			Draw2D.pixelCorners(shapes, x0, y0, x1, y1, px, len, colour);
		}
	}
	private static int healthColor(float frac) {
		return frac > 0.5f ? Fx.lerp(0xFFFFD60A, 0xFF34C759, (frac - 0.5f) * 2) : Fx.lerp(0xFFFF453A, 0xFFFFD60A, frac * 2);
	}
	private static float[] screenBox(Entity entity, float partial, int gw, int gh, Projection projection) {
		AABB b = entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position()));
		float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			float[] s = projection.toScreen((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ, gw, gh);
			if (s == null) {
				return null;
			}
			x0 = Math.min(x0, s[0]);
			y0 = Math.min(y0, s[1]);
			x1 = Math.max(x1, s[0]);
			y1 = Math.max(y1, s[1]);
		}
		if (x1 < 0 || y1 < 0 || x0 > gw || y0 > gh || x1 - x0 < 2 || x1 - x0 > gw || y1 - y0 > gh) {
			return null;
		}
		return new float[] {x0, y0, x1, y1};
	}
	private int colorFor(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		int friend = nz.temple.client.util.Friends.tintFor(entity);
		if (friend != 0) {
			return friend;
		}
		return switch (colorMode.get()) {
			case "Theme" -> Theme.color(entity.getId() * 0.037);
			case "Health" -> Fx.lerp(0xFFFF453A, 0xFF34C759, Entities.healthFraction(entity));
			case "Distance" -> Fx.lerp(0xFFFF453A, 0xFF34C759, mc.player == null ? 1 : Math.min(1, Math.sqrt(entity.distanceToSqr(mc.player)) / 48));
			case "Static" -> staticColor.get();
			default -> {
				if (entity instanceof Player) {
					yield playerColor.get();
				}
				MobCategory category = entity.getType().getCategory();
				if (category == MobCategory.MONSTER) {
					yield hostileColor.get();
				}
				yield category == MobCategory.MISC ? otherColor.get() : passiveColor.get();
			}
		};
	}
	private record Run(Component text, int width) {
	}
	private final java.util.Map<String, Run> semibold = new java.util.HashMap<>();
	private final java.util.Map<String, Run> medium = new java.util.HashMap<>();
	private final Run[] labelParts = new Run[3];
	private final int[] labelColours = new int[3];
	private static Run run(java.util.Map<String, Run> cache, String s, net.minecraft.network.chat.FontDescription font) {
		Run run = cache.get(s);
		if (run == null) {
			if (cache.size() > 512) {
				cache.clear();
			}
			Component text = Fonts.text(s, font);
			run = new Run(text, Minecraft.getInstance().font.width(text));
			cache.put(s, run);
		}
		return run;
	}
	@Override
	protected void onEnable() {
		semibold.clear();
		medium.clear();
	}
	private void label(Entity entity, LocalPlayer self, float cx, float bottom, float uiScale, float p, int colour) {
		var font = Minecraft.getInstance().font;
		float scale = 0.5f * uiScale;
		Run[] parts = labelParts;
		int[] colours = labelColours;
		int count = 0;
		if (names.get()) {
			parts[count] = run(semibold, entity.getName().getString(), Fonts.SEMIBOLD);
			colours[count++] = 0xFFFFFFFF;
		}
		if (distance.get()) {
			parts[count] = run(medium, Math.round(Math.sqrt(entity.distanceToSqr(self))) + "m", Fonts.MEDIUM);
			colours[count++] = 0xFFA9AFBA;
		}
		if (entity instanceof LivingEntity living && names.get()) {
			float hp = living.getHealth() + living.getAbsorptionAmount();
			int tenths = Math.round(hp * 10);
			parts[count] = run(semibold, hp >= 10 ? Integer.toString(Math.round(hp)) : tenths / 10 + "." + tenths % 10, Fonts.SEMIBOLD);
			colours[count++] = healthColor(Entities.healthFraction(entity));
		}
		if (count == 0) {
			return;
		}
		float gap = 3.5f * uiScale;
		float dot = 3 * uiScale;
		float textW = 0;
		for (int i = 0; i < count; i++) {
			textW += parts[i].width() * scale;
		}
		textW += gap * (count - 1);
		float padX = 4 * uiScale;
		float h = font.lineHeight * scale + 3.5f * uiScale;
		float w = padX * 2 + dot + gap + textW;
		float x = cx - w / 2;
		float y = bottom - h;
		Draw2D.pixelRound(shapes, x - p, y - p, x + w + p, y + h + p, 4, 0x40000000);
		Draw2D.pixelRound(shapes, x, y, x + w, y + h, 3, 0xD0121418);
		float mid = y + h / 2;
		Draw2D.pixelRound(shapes, x + padX, mid - dot / 2, x + padX + dot, mid + dot / 2, 2, colour);
		float tx = x + padX + dot + gap;
		float ty = mid - font.lineHeight * scale / 2 + 0.5f * uiScale;
		for (int i = 0; i < count; i++) {
			queueText(parts[i], colours[i], tx, ty, scale);
			tx += parts[i].width() * scale + gap;
		}
	}
	@SuppressWarnings("unused")
	private static Vec3 center(Entity e) {
		return e.getBoundingBox().getCenter();
	}
}
