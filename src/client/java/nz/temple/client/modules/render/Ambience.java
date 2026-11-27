package nz.temple.client.modules.render;

import org.joml.Vector4f;
import nz.temple.client.TempleNzClient;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Ambience extends Module {
	private static Ambience instance;
	private final BoolSetting fog;
	private final ColorSetting fogColor;
	private final NumberSetting fogStart;
	private final NumberSetting fogEnd;
	private final NumberSetting fogThickness;
	private final BoolSetting fogInFluids;
	private final NumberSetting fogGradient;
	private final BoolSetting groundFog;
	private final ModeSetting groundLevel;
	private final NumberSetting groundY;
	private final NumberSetting groundDepth;
	private final NumberSetting groundDensity;
	private final NumberSetting groundReach;
	private final NumberSetting groundWisps;
	private final NumberSetting groundDrift;
	private final ModeSetting groundTint;
	private final ColorSetting groundColor;
	private final ModeSetting filter;
	private final NumberSetting intensity;
	private final NumberSetting brightness;
	private final NumberSetting contrast;
	private final NumberSetting saturation;
	private final NumberSetting warmth;
	private final NumberSetting tintAmount;
	private final ColorSetting tintColor;
	private final NumberSetting vignette;
	private final NumberSetting grain;
	private final BoolSetting filterHand;
	private double followY = Double.NaN;
	private final ModeSetting clouds;
	private final ColorSetting cloudColor;
	private final ModeSetting sky;
	private final ColorSetting skyColor;
	private final NumberSetting stars;
	private final ModeSetting weather;
	public Ambience() {
		super("Ambience", Category.RENDER, "World effects that restyle the overworld like a shader pack, or your own fog, clouds, sky, "
				+ "stars and weather.");
		instance = this;
		fog = add(new BoolSetting("Custom fog", true).describe("Uses your own fog colour and distance."));
		fogColor = add(new ColorSetting("Fog color", 0xFF8A789F).describe("Colour of distance fog.").visibleWhen(fog::get));
		fogStart = add(new NumberSetting("Fog start", 2, 0, 256, 1, " b").describe("Distance where fog begins.").visibleWhen(fog::get));
		fogEnd = add(new NumberSetting("Fog end", 32, 2, 512, 1, " b").describe("Distance at which fog reaches full thickness.").visibleWhen(fog::get));
		fogThickness = add(new NumberSetting("Fog thickness", 70, 0, 100, 5, "%")
				.describe("How opaque the fog becomes, including the sky. This replaces Minecraft's fog: 0 is clear, 100 hides everything "
						+ "past Fog end.")
				.visibleWhen(fog::get));
		fogGradient = add(new NumberSetting("Fog gradient", 50, 0, 100, 1, "%")
				.describe("How gradually fog builds up between Fog start and Fog end. High keeps it thin for most of the distance, so it "
						+ "can start close and still let you see far. Low makes it thicken just past Fog start.")
				.visibleWhen(fog::get));
		fogInFluids = add(new BoolSetting("Fog in fluids", false).describe("Also replaces water, lava and powder snow fog.").visibleWhen(fog::get));
		group("Ground fog");
		groundFog = add(new BoolSetting("Ground fog", false).describe("Low fog that sits on the ground and collects in low areas, thinning "
				+ "as it rises, with slowly moving patches."));
		groundLevel = add(new ModeSetting("Lies at", List.of("On the ground", "Custom height"), "On the ground")
				.describe("On the ground lies on the terrain everywhere: in valleys, on hills and on water, but not in caves or under "
						+ "roofs. Custom height is one flat layer at the Y level you set.")
				.visibleWhen(groundFog::get));
		groundY = add(new NumberSetting("Fog Y level", 63, -64, 320, 1, "").describe("Fog height when using Custom height.")
				.visibleWhen(() -> groundFog.get() && groundLevel.is("Custom height")));
		groundDepth = add(new NumberSetting("Fog depth", 4, 0.5, 24, 0.5, " b").describe("How high the fog extends above its base before it "
				+ "fades out.")
				.visibleWhen(groundFog::get));
		groundDensity = add(new NumberSetting("Fog density", 60, 1, 100, 1, "%").describe("Fog thickness at the bottom.")
				.visibleWhen(groundFog::get));
		groundReach = add(new NumberSetting("Fog reach", 160, 16, 512, 8, " b").describe("How far out the ground fog extends.")
				.visibleWhen(groundFog::get));
		groundWisps = add(new NumberSetting("Wisps", 60, 0, 100, 5, "%").describe("How patchy the fog is. Higher gives denser patches with "
				+ "gaps between them.")
				.visibleWhen(groundFog::get));
		groundDrift = add(new NumberSetting("Drift", 0.6, 0, 4, 0.1, " b/s").describe("How fast the patches move.")
				.visibleWhen(groundFog::get));
		groundTint = add(new ModeSetting("Ground fog color", List.of("From the sky", "Custom"), "From the sky")
				.describe("From the sky lights the fog using the sky and sun colour, darker at night. Custom uses your own colour.")
				.visibleWhen(groundFog::get));
		groundColor = add(new ColorSetting("Ground fog tint", 0xFFB9C4C9).describe("Ground fog colour when set to Custom.")
				.visibleWhen(() -> groundFog.get() && groundTint.is("Custom")));
		group("World effect");
		filter = add(new ModeSetting("World effect", List.of("None", "Midnight", "Golden hour", "Blood moon", "Storm", "Dawn mist", "Arctic"),
				"None")
				.describe("Tones the whole overworld the way a shader pack does: time of day, weather, fog, light and colour, leaving the "
						+ "sky as the time of day makes it. Midnight is a clear moonlit night. Golden hour is the last warm light of the sun. "
						+ "Blood moon is a red night. Storm is dark, rainy and full of lightning. Dawn mist is a pink sunrise with mist in "
						+ "the valleys. Arctic is icy twilight. Only you see it; the server keeps its real time and weather."));
		intensity = add(new NumberSetting("Intensity", 100, 0, 200, 5, "%").describe("How far the effect goes: its colour, light, fog and "
				+ "mist. Above 100% pushes it further.").visibleWhen(() -> !filter.is("None")));
		brightness = add(new NumberSetting("Brightness", 0, -50, 50, 1, "").describe("Overall brightness, on top of the effect."));
		contrast = add(new NumberSetting("Contrast", 0, -50, 50, 1, "").describe("Contrast between darks and lights."));
		saturation = add(new NumberSetting("Saturation", 0, -100, 100, 1, "").describe("Colour intensity. -100 is greyscale."));
		warmth = add(new NumberSetting("Warmth", 0, -50, 50, 1, "").describe("Shifts colours warmer (orange) or cooler (blue)."));
		tintAmount = add(new NumberSetting("Tint", 0, 0, 100, 1, "%").describe("Shifts the image toward the tint colour."));
		tintColor = add(new ColorSetting("Tint color", 0xFF6FA8FF).describe("Colour used by Tint.").visibleWhen(() -> tintAmount.get() > 0));
		vignette = add(new NumberSetting("Vignette", 0, 0, 100, 1, "%").describe("Darkens the edges of the screen."));
		grain = add(new NumberSetting("Film grain", 0, 0, 100, 1, "%").describe("Adds animated film grain."));
		filterHand = add(new BoolSetting("Filter your hand", true).describe("Also applies the effect's colour to your hand and held item."));
		group();
		clouds = add(new ModeSetting("Clouds", List.of("Vanilla", "Custom", "Hidden"), "Vanilla")
				.describe("Keep, recolour or hide vanilla clouds."));
		cloudColor = add(new ColorSetting("Cloud color", 0xFFFFD9A8).describe("Cloud tint.").visibleWhen(() -> clouds.is("Custom")));
		group();
		sky = add(new ModeSetting("Sky", List.of("Vanilla", "Custom"), "Vanilla").describe("Keep the vanilla sky colour or replace it."));
		skyColor = add(new ColorSetting("Sky color", 0xFF3C315E).describe("Overworld sky colour.").visibleWhen(() -> sky.is("Custom")));
		stars = add(new NumberSetting("Star brightness", 100, 0, 300, 5, "%").describe("Brightness multiplier for vanilla stars."));
		weather = add(new ModeSetting("Weather", List.of("Vanilla", "Hidden"), "Vanilla")
				.describe("Hides rain and snow on your screen without changing the server's weather or sounds."));
		for (nz.temple.setting.Setting<?> taken : List.of(fog, fogColor, fogStart, fogEnd, fogThickness, fogGradient, fogInFluids, groundFog,
				groundLevel, groundY, groundDepth, groundDensity, groundReach, groundWisps, groundDrift, groundTint, groundColor, weather)) {
			taken.andVisibleWhen(() -> filter.is("None"));
		}
	}
	public String worldEffect() {
		return filter.get();
	}
	public float effectIntensity() {
		return intensity.getFloat() / 100f;
	}
	private static nz.temple.client.render.WorldEffects.Effect effect() {
		return nz.temple.client.render.WorldEffects.current();
	}

	public static Ambience active() {
		Ambience ambience = TempleNzClient.modules() == null ? instance : TempleNzClient.modules().get(Ambience.class);
		return ambience != null && ambience.isEnabled() ? ambience : null;
	}
	public boolean customFog() {
		return effect() != null || fog.get();
	}

	public boolean fogInFluids() {
		return effect() == null && fogInFluids.get();
	}

	private float rawStart() {
		var e = effect();
		return e != null ? e.fogStart() : fogStart.getFloat();
	}
	private float rawEnd() {
		var e = effect();
		return e != null ? e.fogEnd() / Math.max(1, (float) Math.sqrt(nz.temple.client.render.WorldEffects.intensity())) : fogEnd.getFloat();
	}
	public float fogStart() {
		float end = Math.max(rawStart() + 0.5f, rawEnd());
		float thickness = thickness();
		return end - (end - Math.min(end - 0.5f, rawStart())) * thickness;
	}
	public float fogEnd() {
		return Math.max(rawStart() + 0.5f, rawEnd());
	}

	public Vector4f fogColor() {
		var e = effect();
		int color = e != null ? e.fog() : fogColor.get();
		return new Vector4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1);
	}
	public float gradient() {
		var e = effect();
		return e != null ? e.fogGradient() : fogGradient.getFloat();
	}
	public float thickness() {
		var e = effect();
		return e != null ? Math.min(1, e.fogThickness() * nz.temple.client.render.WorldEffects.intensity()) : fogThickness.getFloat() / 100f;
	}
	public float exponentialStart() {
		return Math.min(rawStart(), fogEnd() - 0.5f);
	}
	public float density() {
		return 3f / Math.max(1f, fogEnd() - exponentialStart());
	}
	public float skyFog() {
		return thickness();
	}
	public String clouds() {
		return clouds.get();
	}

	public int cloudColor(int vanilla) {
		return vanilla >>> 24 << 24 | cloudColor.get() & 0xFFFFFF;
	}

	public boolean customSky() {
		return sky.is("Custom");
	}

	public int skyColor() {
		return 0xFF000000 | skyColor.get() & 0xFFFFFF;
	}

	public float starMultiplier() {
		return stars.getFloat() / 100f;
	}

	public boolean hideWeather() {
		return effect() == null && weather.is("Hidden");
	}
	public boolean groundFog() {
		var e = effect();
		return e != null ? e.groundFog() : groundFog.get();
	}
	public boolean groundOnTerrain() {
		return effect() != null || !groundLevel.is("Custom height");
	}
	public double groundBase(net.minecraft.client.Minecraft mc, float dt) {
		return switch (effect() != null ? "Around you" : groundLevel.get()) {
			case "Sea level" -> 63;
			case "Custom height" -> groundY.get();
			default -> {
				double feet = mc.player == null ? 63 : mc.player.getY();
				if (mc.player != null && !mc.player.onGround()) {
					feet = Double.isNaN(followY) ? feet : Math.min(followY, feet);
				}
				followY = Double.isNaN(followY) || Math.abs(followY - feet) > 40 ? feet
						: followY + (feet - followY) * (1 - Math.exp(-dt / 2.5));
				yield followY - 0.4;
			}
		};
	}
	public float groundDepth() {
		var e = effect();
		return e != null ? e.groundDepth() : groundDepth.getFloat();
	}
	public float groundDensity() {
		var e = effect();
		float d = (e != null ? Math.min(100, e.groundDensity() * nz.temple.client.render.WorldEffects.intensity()) : groundDensity.getFloat()) / 100f;
		return 0.9f * d * d;
	}
	public float groundReach() {
		return groundReach.getFloat();
	}

	public float groundWisps() {
		return groundWisps.getFloat() / 100f;
	}

	public float groundDrift() {
		return groundDrift.getFloat();
	}

	public int groundColor() {
		return effect() == null && groundTint.is("Custom") ? 0xFF000000 | groundColor.get() : 0;
	}
	public float[] grade() {
		var e = effect();
		float[] look = new float[7];
		if (e != null) {
			float k = nz.temple.client.render.WorldEffects.intensity();
			for (int i = 0; i < 7; i++) {
				look[i] = e.grade()[i] * k;
			}
		}
		float[] out = new float[7];
		out[0] = look[0] + brightness.getFloat() / 100f;
		out[1] = look[1] + contrast.getFloat() / 100f;
		out[2] = look[2] + saturation.getFloat() / 100f;
		out[3] = look[3] + warmth.getFloat() / 100f;
		out[4] = Math.min(1, look[4] + tintAmount.getFloat() / 100f);
		out[5] = Math.min(1, look[5] + vignette.getFloat() / 100f);
		out[6] = Math.min(1, look[6] + grain.getFloat() / 100f);
		for (float v : out) {
			if (v != 0) {
				return out;
			}
		}
		return null;
	}
	public int tintColor() {
		var e = effect();
		return e != null && tintAmount.get() == 0 ? 0xFF000000 | e.tint() : 0xFF000000 | tintColor.get();
	}
	public boolean filterHand() {
		return filterHand.get();
	}
}
