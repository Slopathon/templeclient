package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.VisualRange;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.EntitySelection;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public final class ItemESP extends Module {
	private static volatile ItemESP instance;
	private final ModeSetting which;
	private final EntitySelection selected;
	private final BoolSetting xp;
	private final BoolSetting arrows;
	private final ModeSetting style;
	private final ModeSetting colors;
	private final ColorSetting custom;
	private final NumberSetting range;
	private final BoolSetting beams;
	private final NumberSetting height;
	private final NumberSetting width;
	private final BoolSetting halo;
	private final BoolSetting pulse;
	private final NumberSetting brightness;
	private final BoolSetting beamsThroughWalls;
	public ItemESP() {
		super("Item ESP", Category.RENDER, "Highlights dropped items through walls, either all of them or only the ones you choose, with "
				+ "optional boxes and beams.");
		group("Items");
		which = add(new ModeSetting("Highlight", List.of("All items", "Selected items"), "All items")
				.describe("All items highlights every drop. Selected items only highlights items in your list."));
		selected = add(new EntitySelection("Selected items", defaultItems()).items()
				.describe("Items highlighted when Highlight is set to Selected items.")
				.visibleWhen(() -> which.is("Selected items")));
		xp = add(new BoolSetting("XP orbs", false).describe("Also highlights experience orbs."));
		arrows = add(new BoolSetting("Arrows", false).describe("Also highlights arrows stuck in the ground."));
		range = add(new NumberSetting("Range", 64, 8, 512, 8, " m").describe("Only highlights items within this distance."));
		group("Look");
		style = add(new ModeSetting("Style", List.of("Glow", "Box", "Glow + box"), "Glow")
				.describe("Glow draws an outline around the item through walls. Box draws a box around it. Both draws both."));
		colors = add(new ModeSetting("Color", List.of("Theme", "Custom", "Rarity"), "Theme")
				.describe("Theme uses your accent colours. Custom uses the colour below. Rarity uses gold for valuable items and vanilla "
						+ "rarity colours for the rest."));
		custom = add(new ColorSetting("Custom color", 0xFF34C759).describe("Colour used when Color is Custom.")
				.visibleWhen(() -> colors.is("Custom")));
		group("Beams");
		beams = add(new BoolSetting("Beams", false).describe("A beam above each highlighted item, visible from far away."));
		height = add(new NumberSetting("Beam height", 1.4, 0.3, 6, 0.1, " b").describe("How tall the beams are.").visibleWhen(beams::get));
		width = add(new NumberSetting("Beam width", 0.14, 0.02, 0.6, 0.02, " b").describe("How thick the beams are.").visibleWhen(beams::get));
		halo = add(new BoolSetting("Halo", true).describe("A glow and ring around the item at the base of the beam.").visibleWhen(beams::get));
		pulse = add(new BoolSetting("Pulse", false).describe("Slowly pulses the beams.").visibleWhen(beams::get));
		brightness = add(new NumberSetting("Brightness", 80, 10, 100, 5, "%").describe("How bright the beams are.").visibleWhen(beams::get));
		beamsThroughWalls = add(new BoolSetting("Beams through walls", true).describe("Shows beams through terrain.").visibleWhen(beams::get));
		group();
		instance = this;
	}
	private static List<String> defaultItems() {
		List<String> ids = new ArrayList<>(List.of("minecraft:totem_of_undying", "minecraft:elytra", "minecraft:enchanted_golden_apple",
				"minecraft:golden_apple", "minecraft:diamond", "minecraft:netherite_ingot", "minecraft:netherite_scrap",
				"minecraft:ancient_debris", "minecraft:nether_star", "minecraft:end_crystal", "minecraft:ender_pearl",
				"minecraft:experience_bottle", "minecraft:shulker_box", "minecraft:netherite_helmet", "minecraft:netherite_chestplate",
				"minecraft:netherite_leggings", "minecraft:netherite_boots", "minecraft:netherite_sword", "minecraft:netherite_pickaxe"));
		for (String colour : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
				"purple", "blue", "brown", "green", "red", "black")) {
			ids.add("minecraft:" + colour + "_shulker_box");
		}
		return ids;
	}
	private boolean matches(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !entity.isAlive()) {
			return false;
		}
		double r = VisualRange.of(range.get());
		if (entity.distanceToSqr(mc.player) > r * r) {
			return false;
		}
		if (entity instanceof ItemEntity item) {
			ItemStack stack = item.getItem();
			return !stack.isEmpty() && (which.is("All items") || selected.has(nz.temple.client.util.Entities.id(stack.getItem())));
		}
		if (entity instanceof ExperienceOrb) {
			return xp.get();
		}
		return entity instanceof AbstractArrow && arrows.get();
	}
	public static boolean glows(Entity entity) {
		ItemESP m = instance;
		return m != null && m.isEnabled() && !m.style.is("Box") && m.matches(entity);
	}
	public static int outlineColor(Entity entity) {
		ItemESP m = instance;
		return m == null ? 0xFFFFFF : m.colour(entity, entity.getId() * 0.017) & 0xFFFFFF;
	}
	private int colour(Entity entity, double offset) {
		return switch (colors.get()) {
			case "Custom" -> custom.get();
			case "Rarity" -> entity instanceof ItemEntity item ? rarity(item.getItem()) : Theme.color(offset);
			default -> Theme.color(offset);
		};
	}
	private static int rarity(ItemStack stack) {
		if (valuable(stack)) {
			return 0xFFE9B949;
		}
		Rarity rarity = stack.getRarity();
		if (rarity == Rarity.EPIC) {
			return 0xFFE080FF;
		}
		if (rarity == Rarity.RARE) {
			return 0xFF6FE8FF;
		}
		if (rarity == Rarity.UNCOMMON) {
			return 0xFFF2F26A;
		}
		return 0xFFDCE6F2;
	}
	private static boolean valuable(ItemStack stack) {
		if (stack.isEnchanted()) {
			return true;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return id.equals("totem_of_undying") || id.equals("elytra") || id.equals("enchanted_golden_apple")
				|| id.equals("nether_star") || id.equals("ancient_debris") || id.equals("dragon_egg")
				|| id.endsWith("shulker_box") || id.startsWith("netherite_") || id.startsWith("diamond_")
				|| id.equals("diamond") || id.equals("diamond_block");
	}
	public void collect(List<WorldEsp.Box> out) {
		if (!isEnabled() || style.is("Glow")) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer self = mc.player;
		if (self == null || mc.level == null) {
			return;
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!matches(entity)) {
				continue;
			}
			Vec3 now = entity.position();
			AABB b = entity.getBoundingBox().move(entity.getPosition(1f).subtract(now));
			int c = colour(entity, entity.getId() * 0.017);
			out.add(new WorldEsp.Box(b, 0xFF000000 | (c & 0xFFFFFF), (70 << 24) | (c & 0xFFFFFF), 0.008f));
		}
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !beams.get() || mc.level == null || mc.player == null) {
			return;
		}
		fx.throughWalls(beamsThroughWalls.get());
		fx.additive(true);
		float bright = 255f * brightness.getFloat() / 100f;
		double t = (System.nanoTime() % 60_000_000_000L) / 1e9;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof ItemEntity item) || !matches(entity)) {
				continue;
			}
			Vec3 p = item.getPosition(partial);
			int c = colour(entity, entity.getId() * 0.017);
			float age = Math.min(1, item.getAge() / 8f);
			float breathe = pulse.get() ? (float) (0.82 + 0.18 * Math.sin(t * 2.2 + entity.getId())) : 1f;
			float alpha = bright * breathe * (0.35f + 0.65f * age);
			double h = height.get() * (0.4 + 0.6 * age) * (2 - breathe);
			double w = width.get();
			fx.softBeam(p.x, p.y + 0.08, p.z, p.x, p.y + 0.08 + h, p.z, w, Fx.withAlpha(c, alpha), Fx.withAlpha(c, 0));
			fx.softBeam(p.x, p.y + 0.08, p.z, p.x, p.y + 0.08 + h * 0.55, p.z, w * 2.6, Fx.withAlpha(c, alpha * 0.22f), Fx.withAlpha(c, 0));
			if (halo.get()) {
				fx.orb(p.x, p.y + 0.22, p.z, 0.2 + 0.06 * breathe, Fx.withAlpha(c, alpha * 0.8f));
				double r = 0.34 + 0.05 * Math.sin(t * 1.7 + entity.getId());
				fx.ring(p.x, p.y + 0.03, p.z, r - 0.05, r, c & 0xFFFFFF, Fx.withAlpha(c, alpha * 0.5f), 28);
			}
		}
		fx.additive(false);
	}
}
