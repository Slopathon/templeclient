package nz.temple.client.modules.render;

import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class RealisticLighting extends Module {
	public record Look(float shadows, float softness, float occlusion, float warmth, float shafts, float dust, float bloom,
			float flare, float sunSize, int quality) {
	}
	private static volatile RealisticLighting instance;
	private final NumberSetting shadows;
	private final NumberSetting softness;
	private final NumberSetting occlusion;
	private final NumberSetting warmth;
	private final NumberSetting shafts;
	private final NumberSetting dust;
	private final NumberSetting bloom;
	private final NumberSetting flare;
	private final NumberSetting sunSize;
	private final ModeSetting quality;
	public RealisticLighting() {
		super("Realistic Lighting", Category.COSMETICS, "Ray-traced sun and moon shadows, soft shading in corners, light shafts, "
				+ "lit dust, sun glare and lens flare.");
		shadows = add(new NumberSetting("Shadows", 90, 0, 100, 5, "%").describe("How dark shadows are."));
		softness = add(new NumberSetting("Shadow softness", 40, 0, 100, 5, "%").describe("How soft shadow edges are."));
		occlusion = add(new NumberSetting("Ambient occlusion", 50, 0, 100, 5, "%").describe("How much corners and hollows "
				+ "darken."));
		warmth = add(new NumberSetting("Sun warmth", 75, 0, 100, 5, "%").describe("How warm sunlit surfaces look, and how cool "
				+ "shade looks."));
		shafts = add(new NumberSetting("Light shafts", 85, 0, 100, 5, "%").describe("Beams of sunlight in the air, broken by "
				+ "whatever stands in the way."));
		dust = add(new NumberSetting("Dust", 75, 0, 100, 5, "%").describe("Specks of dust drifting in the air, lit where the sun "
				+ "reaches them."));
		bloom = add(new NumberSetting("Bloom", 80, 0, 100, 5, "%").describe("Glare around the sun when you look toward it, and a "
				+ "glow around bright surfaces."));
		flare = add(new NumberSetting("Lens flare", 70, 0, 100, 5, "%").describe("The star and the row of hexagons a camera "
				+ "lens throws across the picture when it points toward the sun."));
		sunSize = add(new NumberSetting("Sun size", 50, 10, 200, 5, "%").describe("How much of the view the sun's glare and "
				+ "star take up. Light shafts are not affected."));
		quality = add(new ModeSetting("Quality", List.of("Low", "Medium", "High"), "Medium").describe("Low traces shorter rays. "
				+ "High traces longer rays at a finer resolution. Lower is faster."));
		instance = this;
	}
	public static Look look() {
		RealisticLighting m = instance;
		if (m == null || !m.isEnabled()) {
			return null;
		}
		return new Look(m.shadows.getFloat() / 100f, m.softness.getFloat() / 100f, m.occlusion.getFloat() / 100f,
				m.warmth.getFloat() / 100f, m.shafts.getFloat() / 100f, m.dust.getFloat() / 100f, m.bloom.getFloat() / 100f,
				m.flare.getFloat() / 100f, m.sunSize.getFloat() / 100f, m.quality.is("Low") ? 0 : m.quality.is("High") ? 2 : 1);
	}
}
