package nz.temple.client.modules.render;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.render.Theme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class HandChams extends Module implements nz.temple.client.render.HandChamsCollector.Source {
	public static final List<String> STYLES = List.of("Flat", "Tinted", "Gradient", "Rainbow", "Glass", "Chrome", "Liquid",
			"Galaxy", "Void", "Plasma", "Hologram", "Fire", "Electric", "Crystal", "Matrix", "Wireframe", "Ghost", "Toon",
			"Topographic");
	private static final String STYLE_HELP = "Flat is one solid colour. Tinted recolours your skin or item with a moving sheen. "
			+ "Gradient blends two colours. Rainbow cycles through colours. Glass is clear with a bright rim. Chrome is mirrored. "
			+ "Liquid looks like flowing molten metal. Galaxy shows a starfield. Void is black with a lit edge. Plasma has veins of "
			+ "light. Hologram flickers. Fire shows rising flames. Electric shows moving arcs. Crystal has faceted sparkles. Matrix "
			+ "shows falling code. Wireframe outlines the pixel grid. Ghost shows only the edges. Toon is cel-shaded with an outline. Topographic is a flat colour crossed by dark contour lines, like a map.";
	private final BoolSetting hands;
	private final ModeSetting handStyle;
	private final ColorSetting handColor;
	private final ModeSetting handBlend;
	private final BoolSetting weapons;
	private final ModeSetting items;
	private final ModeSetting weaponStyle;
	private final ColorSetting weaponColor;
	private final ModeSetting weaponBlend;
	private final BoolSetting themed;
	private final NumberSetting opacity;
	private final NumberSetting speed;
	private final BoolSetting glow;
	private final NumberSetting glowStrength;
	private final NumberSetting glowSize;
	private final BoolSetting thirdPerson;
	private static HandChams instance;
	public HandChams() {
		super("Hand Chams", Category.COSMETICS, "Restyles your first-person arm and held weapon with one of eighteen looks, from chrome and "
				+ "crystal to fire and galaxy, with an optional glow.");
		hands = add(new BoolSetting("Hand", true).describe("Applies chams to your arm and sleeve."));
		handStyle = add(new ModeSetting("Hand style", STYLES, "Galaxy").describe(STYLE_HELP).visibleWhen(hands::get));
		handBlend = add(new ModeSetting("Hand blend", List.of("Replace", "Overlay"), "Replace")
				.describe("Replace draws only the chams arm. Overlay draws it over your real arm, so lowering Opacity shows the skin "
						+ "underneath.")
				.visibleWhen(hands::get));
		thirdPerson = add(new BoolSetting("Third person", true)
				.describe("Also applies in third person, Freecam and Freelook: your body uses the hand look and your held item uses the "
						+ "weapon look. Chams with Self on overrides this for your body."));
		group("Weapon & tool");
		weapons = add(new BoolSetting("Weapon & tool", true).describe("Applies chams to the weapon or tool in your hand."));
		items = add(new ModeSetting("Items", List.of("Weapons & tools", "Weapons", "Everything"), "Weapons & tools")
				.describe("Weapons & tools covers swords, axes, maces, tridents, spears, bows, crossbows, pickaxes, shovels, hoes, shears, "
						+ "fishing rods, flint and steel, and brushes. Weapons covers weapons only. Everything covers any held item, "
						+ "including blocks.")
				.visibleWhen(weapons::get));
		weaponStyle = add(new ModeSetting("Weapon style", STYLES, "Chrome").describe(STYLE_HELP).visibleWhen(weapons::get));
		weaponBlend = add(new ModeSetting("Weapon blend", List.of("Replace", "Overlay"), "Replace")
				.describe("Replace draws only the chams item. Overlay draws it over the real item.")
				.visibleWhen(weapons::get));
		group("Colours");
		themed = add(new BoolSetting("Use theme colour", true)
				.describe("Uses the client accent colour instead of the two colours below."));
		handColor = add(new ColorSetting("Hand color", 0xFF8A5CF6).describe("Main colour of the arm's look.")
				.visibleWhen(() -> hands.get() && !themed.get()));
		weaponColor = add(new ColorSetting("Weapon color", 0xFF7FD8FF).describe("Main colour of the weapon's look.")
				.visibleWhen(() -> weapons.get() && !themed.get()));
		opacity = add(new NumberSetting("Opacity", 100, 10, 100, 5, "%").describe("How solid the chams are. Lower values are see-through."));
		speed = add(new NumberSetting("Animation speed", 100, 0, 300, 10, "%")
				.describe("Speed of the animated looks. 0 freezes them."));
		group("Glow");
		glow = add(new BoolSetting("Glow", true).describe("A glow around the arm and weapon."));
		glowStrength = add(new NumberSetting("Glow strength", 55, 10, 100, 5, "%").describe("Glow brightness.")
				.visibleWhen(glow::get));
		glowSize = add(new NumberSetting("Glow size", 1.2, 0.3, 3, 0.1, " cm").describe("How far the glow extends from the surface.")
				.visibleWhen(glow::get));
		instance = this;
	}
	public static HandChams active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	public boolean handOn() {
		return hands.get();
	}

	public boolean thirdPersonOn() {
		return thirdPerson.get();
	}
	public boolean weaponOn(ItemStack stack) {
		return weapons.get() && matches(stack, items.get());
	}
	public static boolean matches(ItemStack stack, String which) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (which.equals("Everything")) {
			return true;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		boolean weapon = id.endsWith("_sword") || id.endsWith("_axe") || id.endsWith("_spear") || id.equals("mace") || id.equals("trident")
				|| id.equals("bow") || id.equals("crossbow");
		if (weapon || !which.equals("Weapons & tools")) {
			return weapon;
		}
		return id.endsWith("_pickaxe") || id.endsWith("_shovel") || id.endsWith("_hoe") || id.equals("shears") || id.equals("fishing_rod")
				|| id.equals("flint_and_steel") || id.equals("brush") || id.equals("carrot_on_a_stick") || id.equals("warped_fungus_on_a_stick");
	}
	public boolean replaces(boolean hand) {
		return (hand ? handBlend : weaponBlend).is("Replace");
	}

	public int overlay(boolean hand) {
		int look = Math.max(0, STYLES.indexOf((hand ? handStyle : weaponStyle).get()));
		return look | (Math.round(speed.getFloat()) << 16);
	}
	public int argb(boolean hand) {
		int alpha = Math.round(opacity.getFloat() / 100f * 255);
		return (alpha << 24) | (colour(hand) & 0xFFFFFF);
	}
	private int colour(boolean hand) {
		return themed.get() ? Theme.primary() : (hand ? handColor : weaponColor).get();
	}

	public boolean glowOn() {
		return glow.get();
	}

	public int[] glowLayers() {
		float cm = glowSize.getFloat();
		return new int[] {Math.round(cm * 3.5f), Math.round(cm * 7.5f), Math.round(cm * 13f)};
	}
	public int glowArgb(boolean hand, int layer) {
		float strength = glowStrength.getFloat() / 100f * (layer == 0 ? 0.55f : layer == 1 ? 0.32f : 0.16f);
		int a = Math.max(0, Math.min(255, Math.round(strength * 255)));
		return (a << 24) | (colour(hand) & 0xFFFFFF);
	}
}
