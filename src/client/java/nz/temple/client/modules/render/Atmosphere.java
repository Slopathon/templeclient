package nz.temple.client.modules.render;

import nz.temple.client.hud.HudTheme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

/**
 * A thick, tinted atmosphere over the world and the sky in the HUD theme's colours (after x.monolith's fog): the world fades
 * into a colour gradient with distance, broken up by slowly drifting mist.
 */
public final class Atmosphere extends Module {
	private static Atmosphere instance;
	private final ModeSetting colors;
	private final ColorSetting color;
	private final NumberSetting start;
	private final NumberSetting density;
	private final NumberSetting opacity;
	private final NumberSetting mist;
	private final BoolSetting sky;

	public Atmosphere() {
		super("Atmosphere", Category.RENDER, "Thick, tinted fog over the world and the sky in your HUD theme's colours, with drifting mist.");
		instance = this;
		colors = add(new ModeSetting("Colors", List.of("Theme", "Custom"), "Theme")
				.describe("Theme follows the HUD theme (all four of its colours, as a gradient across the screen)."));
		color = add(new ColorSetting("Color", 0xFFB03CD0).fixed().describe("The fog's colour.").visibleWhen(() -> colors.is("Custom")));
		start = add(new NumberSetting("Start", 2, 0, 50, 1, "%").describe("How far away the fog starts, as a share of your render distance."));
		density = add(new NumberSetting("Density", 1, 0.2, 3, 0.05, "x").describe("How quickly it thickens with distance."));
		opacity = add(new NumberSetting("Opacity", 90, 5, 100, 5, "%").describe("How solid the fog gets at its thickest."));
		mist = add(new NumberSetting("Mist", 50, 0, 100, 5, "%").describe("How much the fog is broken up by drifting mist."));
		sky = add(new BoolSetting("Tint sky", true).describe("Lays the fog's colour over the sky as well."));
	}

	public static Atmosphere active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	public float start() {
		return start.getFloat() / 100f;
	}

	public float density() {
		return density.getFloat();
	}

	public float opacity() {
		return opacity.getFloat() / 100f;
	}

	public float mist() {
		return mist.getFloat() / 100f;
	}

	public boolean sky() {
		return sky.get();
	}

	/** The gradient's four colours: accent, light, deep and tint of the HUD theme, or the custom colour four times. */
	public int[] gradient() {
		if (colors.is("Theme")) {
			return new int[] {HudTheme.accent(), HudTheme.light(), HudTheme.deep(), HudTheme.tint()};
		}
		int c = color.get() | 0xFF000000;
		return new int[] {c, c, c, c};
	}
}
