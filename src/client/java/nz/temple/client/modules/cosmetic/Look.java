package nz.temple.client.modules.cosmetic;

import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Theme;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.Setting;

import java.util.List;
import java.util.function.Consumer;

public final class Look implements nz.temple.client.render.Paint {
	private final ModeSetting material;
	private final ModeSetting colors;
	private final ColorSetting first;
	private final ColorSetting second;
	private final NumberSetting brightness;
	private final BoolSetting pulse;
	public Look(Consumer<Setting<?>> add, String defaultMaterial) {
		material = new ModeSetting("Material", List.of("Glass", "Gold", "Light", "Matte", "Glow"), defaultMaterial)
				.describe("Glass refracts the world behind it. Gold is polished metal. Light is bright and emissive. Matte is solid colour. "
						+ "Glow is neon with a bloom halo.");
		colors = new ModeSetting("Color", List.of("Theme", "Custom", "Rainbow"), "Theme")
				.describe("Theme uses the HUD accent. Custom uses the two colours below. Rainbow cycles through colours.");
		first = new ColorSetting("Color A", 0xFFE9B949).themed().describe("Main colour.").visibleWhen(() -> colors.is("Custom"));
		second = new ColorSetting("Color B", 0xFFFFE3A1).themedSecondary().describe("Second colour, used for gradients.")
				.visibleWhen(() -> colors.is("Custom"));
		brightness = new NumberSetting("Brightness", 90, 10, 100, 5, "%").describe("How bright and opaque the effect is.");
		pulse = new BoolSetting("Pulse", false).describe("Slowly pulses the brightness.");
		add.accept(material);
		add.accept(colors);
		add.accept(first);
		add.accept(second);
		add.accept(brightness);
		add.accept(pulse);
	}
	@Override
	public int color(double t) {
		int frame = Fx.frame();
		if (frame != tableFrame || --tableChecks <= 0) {
			tableFrame = frame;
			tableChecks = 1 << 16;
			tableAsked = 0;
			tableReady = false;
		}
		if (!tableReady && ++tableAsked > TABLE / 2) {
			tablePeriod = colors.is("Rainbow") ? 1 / 0.35 : 1;
			for (int i = 0; i < TABLE; i++) {
				table[i] = compute(i * tablePeriod / TABLE);
			}
			tableReady = true;
		}
		if (tableReady) {
			double w = t / tablePeriod;
			return table[(int) ((w - Math.floor(w)) * TABLE) & (TABLE - 1)];
		}
		return compute(t);
	}
	private static final int TABLE = 512;
	private final int[] table = new int[TABLE];
	private double tablePeriod = 1;
	private boolean tableReady;
	private int tableFrame = -1;
	private int tableChecks;
	private int tableAsked;
	private int compute(double t) {
		int c = baseColor(t);
		if (material.is("Glow")) {
			int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
			int max = Math.max(r, Math.max(g, b));
			if (max > 0) {
				float k = 255f / max;
				r = Math.min(255, Math.round(r * k));
				g = Math.min(255, Math.round(g * k));
				b = Math.min(255, Math.round(b * k));
			}
			c = Fx.lerp(0xFF000000 | (r << 16) | (g << 8) | b, 0xFFFFFFFF, 0.18);
		}
		return c;
	}
	private int baseColor(double t) {
		return switch (colors.get()) {
			case "Custom" -> Theme.flow(first.get(), second.get(), t);
			case "Rainbow" -> Fx.hue(Theme.time() * 0.08 + t * 0.35);
			default -> Theme.color(t);
		};
	}
	@Override
	public int color(double t, double fade) {
		return Fx.withAlpha(color(t), (float) (255 * alpha() * Math.max(0, Math.min(1, fade))));
	}
	@Override
	public float alpha() {
		return brightness.getFloat() / 100f * pulseFactor();
	}
	private float pulseFactor() {
		return pulse.get() ? (float) (0.78 + 0.22 * Math.sin(Theme.time() * 2.4)) : 1f;
	}
	public boolean glass() {
		return material.is("Glass");
	}
	public boolean light() {
		return material.is("Light") || material.is("Glow");
	}
	public boolean solid() {
		return material.is("Glass") || material.is("Gold");
	}
	public boolean matte() {
		return material.is("Matte");
	}
	public boolean neon() {
		return material.is("Glow");
	}
	@Override
	public void apply(Meshes mesh, float extraGlow) {
		float glow = extraGlow * pulseFactor();
		mesh.bloom(material.is("Glow"), alpha() * (material.is("Glow") ? 1.6f : 1f));
		if (material.is("Glow")) {
			lastGlow = System.nanoTime();
		}
		switch (material.get()) {
			case "Glass" -> {
				mesh.material(Meshes.Material.GLASS);
				mesh.emission(0.05f + glow * 0.4f);
			}
			case "Gold" -> {
				mesh.material(Meshes.Material.METAL);
				mesh.emission(0.12f + glow * 0.5f);
			}
			case "Light" -> {
				mesh.material(Meshes.Material.MATTE);
				mesh.emission(0.5f);
			}
			case "Glow" -> {
				mesh.material(Meshes.Material.NEON);
				mesh.emission(0.12f + glow * 0.2f);
			}
			default -> {
				mesh.material(Meshes.Material.MATTE);
				mesh.emission(glow * 0.5f);
			}
		}
	}
	private static volatile long lastGlow;

	public static void markGlow() {
		lastGlow = System.nanoTime();
	}
	public static boolean glowingRecently() {
		return System.nanoTime() - lastGlow < 500_000_000L;
	}
	@Override
	public float bloom() {
		return pulseFactor() * switch (material.get()) {
			case "Glow" -> 1.7f;
			case "Light" -> 1f;
			case "Gold" -> 0.45f;
			case "Glass" -> 0.3f;
			default -> 0.15f;
		};
	}
}
