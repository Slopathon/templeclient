package nz.temple.client.modules.render;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import nz.temple.TempleNz;
import nz.temple.client.render.Theme;
import nz.temple.client.render.UserTexture;
import nz.temple.client.util.FilePicker;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.EntitySelection;
import nz.temple.setting.FileSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Chams extends Module {
	public static final Identifier DEFAULT_IMAGE = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/chams/dunes.png");
	private final EntitySelection entities;
	private final NumberSetting range;
	private final ModeSetting style;
	private final ModeSetting mode;
	private final BoolSetting glow;
	private final NumberSetting glowStrength;
	private final NumberSetting glowSize;
	private final BoolSetting themed;
	private final ColorSetting visibleColor;
	private final ColorSetting wallColor;
	private final NumberSetting opacity;
	private final BoolSetting pulse;
	private final FileSetting image;
	private final ColorSetting imageTint;
	private final NumberSetting tintStrength;
	private final UserTexture picture;
	private final BoolSetting self;
	private final BoolSetting outline;
	private final ColorSetting outlineColor;
	private final NumberSetting outlineWidth;
	private final BoolSetting heldItems;
	private final ModeSetting heldWhich;
	private static Chams instance;
	public Chams() {
		super("Chams", Category.RENDER, "Draws entities in flat colour or with an image, visible through walls. Rendered with the model "
				+ "itself, so there is no delay.");
		entities = add(new EntitySelection("Entities", List.of("minecraft:player"))
				.describe("Choose which entity types get chams. Players only by default."));
		self = add(new BoolSetting("Self", false)
				.describe("Also applies chams to your own body in third person, Freecam or Freelook."));
		range = add(new NumberSetting("Range", 128, 8, 512, 4, " m")
				.describe("Only applies within this distance."));
		group("Style");
		style = add(new ModeSetting("Style", List.of("Color", "Image", "Astral", "Void", "Plasma", "Hologram", "Topographic"), "Color")
				.describe("Color is a flat colour. Image shows a picture. Astral is a starfield with a bright rim. Void is black with a "
						+ "ring around the edge. Plasma is dark glass with veins of light. Hologram flickers with scanlines. Topographic is a flat colour crossed by dark contour lines, like a map."));
		glow = add(new BoolSetting("Glow aura", true)
				.describe("Adds layered glow around the body. Most visible in the dark."));
		glowStrength = add(new NumberSetting("Glow strength", 60, 10, 100, 5, "%").describe("Glow brightness.")
				.visibleWhen(glow::get));
		glowSize = add(new NumberSetting("Glow size", 12, 3, 30, 1, "").describe("How far the glow extends.")
				.visibleWhen(glow::get));
		mode = add(new ModeSetting("Mode", List.of("Both", "Visible", "Walls"), "Both")
				.describe("Visible only shows where the body is in view. Walls also shows it through terrain. Both uses one colour in view "
						+ "and another through walls."));
		opacity = add(new NumberSetting("Opacity", 100, 10, 100, 5, "%")
				.describe("Opacity. Lower values let the real model show through."));
		pulse = add(new BoolSetting("Pulse", false)
				.describe("Slowly pulses the opacity and theme colour. Off keeps chams steady."));
		outline = add(new BoolSetting("Outline", true)
				.describe("A thin outline around the model. Its thickness is fixed on the entity, so it stays thin at a distance."));
		outlineWidth = add(new NumberSetting("Outline width", 3, 1, 10, 0.5, " cm")
				.describe("Outline thickness in centimetres on the entity (a block is 100 cm).")
				.visibleWhen(() -> outline.get()));
		outlineColor = add(new ColorSetting("Outline color", 0xFFFFE3A1).themedSecondary()
				.describe("Outline colour.")
				.visibleWhen(() -> outline.get() && !themedOn()));
		group("Colours");
		themed = add(new BoolSetting("Use theme colour", true)
				.describe("Uses the client accent colour (gold by default). Turn off to choose your own."));
		visibleColor = add(new ColorSetting("Visible color", 0xFFE9B949).themed().describe("Colour where the body is in view.")
				.visibleWhen(() -> (style.is("Color") || energyLook() != 0) && !themed.get()));
		wallColor = add(new ColorSetting("Wall color", 0xFF8A5CF6).themedSecondary().describe("Colour where the body is behind terrain.")
				.visibleWhen(() -> style.is("Color") && !themed.get() && !mode.is("Visible")));
		image = add(new FileSetting("Image", List.of("png", "jpg", "jpeg", "bmp"))
				.describe("Image shown on bodies. The default is desert dunes. The file reloads automatically when edited.")
				.visibleWhen(() -> style.is("Image")));
		imageTint = add(new ColorSetting("Image hue", 0xFFE9B949).themed()
				.describe("Hue the image is shifted toward.")
				.visibleWhen(() -> style.is("Image") && !themed.get()));
		tintStrength = add(new NumberSetting("Hue strength", 70, 0, 100, 5, "%")
				.describe("0 keeps the image's own colours; 100 recolours it fully to your hue.")
				.visibleWhen(() -> style.is("Image")));
		group("Held items");
		heldItems = add(new BoolSetting("Held items", true)
				.describe("Also applies chams to held items, using the same look as the body."));
		heldWhich = add(new ModeSetting("Held item types", List.of("Weapons & tools", "Weapons", "Everything"), "Weapons & tools")
				.describe("Weapons & tools covers swords, axes, maces, tridents, spears, bows, crossbows and tools. Weapons covers weapons "
						+ "only. Everything covers any held item.")
				.visibleWhen(heldItems::get));
		picture = new UserTexture("chams_image", () -> FilePicker.path(image));
		instance = this;
	}
	public static Chams instance() {
		return instance;
	}

	public boolean appliesTo(net.minecraft.world.entity.Entity entity) {
		if (!isEnabled() || !(entity instanceof net.minecraft.world.entity.LivingEntity) || entity.isInvisible()) {
			return false;
		}
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (entity == mc.player && !self.get()) {
			return false;
		}
		if (!entities.has(nz.temple.client.util.Entities.id(entity))) {
			return false;
		}
		double r = nz.temple.client.util.VisualRange.of(range.get());
		net.minecraft.world.entity.Entity viewer = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
		return viewer == null || entity == viewer || entity.distanceToSqr(viewer) <= r * r;
	}
	private boolean themedOn() {
		return themed.get();
	}

	public boolean outlined() {
		return isEnabled() && outline.get();
	}

	public int outlineThousandths() {
		return Math.round(outlineWidth.getFloat() * 10);
	}
	public int outlineArgb() {
		return 0xFF000000 | outlineRgb();
	}
	public int outlineRgb() {
		if (!themed.get()) {
			return outlineColor.get() & 0xFFFFFF;
		}
		int a = accent();
		int r = Math.min(255, ((a >> 16) & 0xFF) + 40);
		int g = Math.min(255, ((a >> 8) & 0xFF) + 50);
		int b = Math.min(255, (a & 0xFF) + 70);
		return (r << 16) | (g << 8) | b;
	}
	public int energyLook() {
		return switch (style.get()) {
			case "Astral" -> 1;
			case "Void" -> 2;
			case "Plasma" -> 3;
			case "Hologram" -> 4;
			case "Topographic" -> 5;
			default -> 0;
		};
	}
	public boolean glowOn() {
		return isEnabled() && glow.get();
	}

	public float glowSpread() {
		return glowSize.getFloat() / 12f;
	}
	public int glowArgb() {
		int a = Math.round(glowStrength.getFloat() / 100f * 255);
		return (Math.max(0, Math.min(255, a)) << 24) | ((themed.get() ? accent() : visibleColor.get()) & 0xFFFFFF);
	}
	public boolean imageOn() {
		return style.is("Image");
	}
	public Identifier imageId() {
		return !image.isDefault() && picture.ready() ? picture.id() : DEFAULT_IMAGE;
	}
	public int imageArgb() {
		return alpha() | ((themed.get() ? accent() : imageTint.get()) & 0xFFFFFF);
	}
	public int imageStrength() {
		return Math.round(tintStrength.getFloat());
	}
	public boolean visibleOn() {
		return mode.is("Both") || mode.is("Visible");
	}

	public boolean wallsOn() {
		return mode.is("Both") || mode.is("Walls");
	}

	public int withAlpha(int rgb) {
		return alpha() | (rgb & 0xFFFFFF);
	}
	public int visibleArgb() {
		return alpha() | ((themed.get() ? accent() : visibleColor.get()) & 0xFFFFFF);
	}

	public int wallArgb() {
		if (themed.get()) {
			int gold = accent();
			int r = ((gold >> 16) & 0xFF) * 7 / 10;
			int g = ((gold >> 8) & 0xFF) * 5 / 10;
			int b = (gold & 0xFF) * 4 / 10;
			return alpha() | (r << 16) | (g << 8) | b;
		}
		return alpha() | (wallColor.get() & 0xFFFFFF);
	}
	public nz.temple.client.render.HandChamsCollector.Source heldSource() {
		if (!isEnabled() || !heldItems.get()) {
			return null;
		}
		return held;
	}
	private final nz.temple.client.render.HandChamsCollector.Source held = new nz.temple.client.render.HandChamsCollector.Source() {
		@Override
		public boolean handOn() {
			return false;
		}
		@Override
		public boolean weaponOn(net.minecraft.world.item.ItemStack stack) {
			return HandChams.matches(stack, heldWhich.get());
		}
		@Override
		public boolean replaces(boolean arm) {
			return false;
		}
		@Override
		public int overlay(boolean arm) {
			int look = switch (style.get()) {
				case "Image" -> 1;
				case "Astral" -> 7;
				case "Void" -> 8;
				case "Plasma" -> 9;
				case "Hologram" -> 10;
				case "Topographic" -> 18;
				default -> 0;
			};
			return look | (100 << 16);
		}
		@Override
		public int argb(boolean arm) {
			return imageOn() ? imageArgb() : visibleArgb();
		}
		@Override
		public boolean glowOn() {
			return false;
		}
		@Override
		public int[] glowLayers() {
			return new int[0];
		}
		@Override
		public int glowArgb(boolean arm, int layer) {
			return 0;
		}
		@Override
		public boolean throughWalls() {
			return wallsOn();
		}
	};
	private int accent() {
		return pulse.get() ? Theme.color(0) : Theme.primary();
	}
	private int alpha() {
		float breathe = pulse.get() ? (float) (0.8 + 0.2 * Math.sin(Theme.time() * 2.4)) : 1f;
		return Math.round(opacity.getFloat() / 100f * breathe * 255) << 24;
	}
}
