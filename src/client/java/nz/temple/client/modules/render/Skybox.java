package nz.temple.client.modules.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.client.render.BundledTexture;
import nz.temple.client.render.SkyboxRenderer;
import nz.temple.client.render.UserTexture;
import nz.temple.client.util.FilePicker;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.FileSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Skybox extends Module {
	private static final float CONSTELLATION_AZIMUTH = 2.6f;
	private final ModeSetting style;
	private final ColorSetting skyColor;
	private final ColorSetting skyAccent;
	private final FileSetting image;
	private final ModeSetting dimensions;
	private final NumberSetting brightness;
	private final BoolSetting followDaylight;
	private final BoolSetting celestials;
	private final BoolSetting noClouds;
	private final NumberSetting spin;
	private final NumberSetting tilt;
	private final NumberSetting twinkle;
	private final BoolSetting tinted;
	private final ColorSetting tint;
	private final BoolSetting aurora;
	private final NumberSetting auroraStrength;
	private final ModeSetting auroraPalette;
	private final UserTexture picture;
	private final BoolSetting constellation;
	private final BoolSetting themeShader;
	private final BundledTexture painting;
	private final BundledTexture blank;
	public Skybox() {
		super("Skybox", Category.COSMETICS, "Replaces the sky with a painted sky, animated shader skies, a storm or a nebula, optionally "
				+ "with an aurora, or with any 2:1 panorama image.");
		style = add(new ModeSetting("Style", List.of("Painted", "Topographic", "Sunset", "Daydream", "Galaxy", "Northern Lights", "Eclipse", "Synthwave", "Tendrils", "Silk", "Void",
				"Black Hole", "Plasma", "Storm", "Nebula"), "Painted")
				.describe("Painted is a gold night sky with a constellation shaped like the Temple logo. Tendrils shows cells of light "
						+ "behind dark tendrils. Silk shows drifting iridescent folds. Void shows a still black hole with a bright accretion "
						+ "disk. Black Hole is the same hole, animated: the disk swirls, slowly wobbles and its glow breathes. Plasma shows electric filaments. Storm shows clouds lit by lightning. Nebula shows slowly turning gas "
						+ "clouds. Sunset is a warm golden-hour sky with side-lit clouds. Daydream is a bright summer sky with drifting "
						+ "clouds. Galaxy hangs a spiral galaxy in a deep night sky. Northern Lights is a cold night with strong aurora "
						+ "curtains. Eclipse is a black sun with a glowing corona. Synthwave is a striped retro sun on a magenta horizon. "
						+ "A sky image below overrides all of these."));
		themeShader = add(new BoolSetting("Use sky colors", false)
				.describe("Uses the two sky colours below for the shader skies (Tendrils, Silk, Void, Plasma) instead of their own colours.")
				.visibleWhen(this::shaderStyle));
		skyColor = add(new ColorSetting("Sky color", 0xFFE9B949).themed()
				.describe("Main colour for the storm, nebula, theme-coloured aurora, and shader skies when Use sky colors is on. Follows "
						+ "the theme until changed."));
		skyAccent = add(new ColorSetting("Sky accent", 0xFFFFE3A1).themedSecondary()
				.describe("Second colour, used for lightning, the nebula's second gas, the top of the aurora and shader sky highlights."));
		image = add(new FileSetting("Sky image", List.of("png", "jpg", "jpeg", "bmp"))
				.describe("A 2:1 equirectangular image with the horizon across the middle. Leave on Default for the Temple sky."));
		dimensions = add(new ModeSetting("Dimensions", List.of("Overworld", "Overworld + End"), "Overworld")
				.describe("Where the sky is replaced. The Nether has no sky."));
		group();
		brightness = add(new NumberSetting("Brightness", 100, 20, 160, 5, "%")
				.describe("How bright the sky is."));
		followDaylight = add(new BoolSetting("Follow daylight", false)
				.describe("Dims the sky at night and during storms."));
		celestials = add(new BoolSetting("Sun, moon and stars", false)
				.describe("Draws vanilla's sun, moon and stars over the sky."));
		noClouds = add(new BoolSetting("Hide clouds", true)
				.describe("Hides vanilla clouds, which are coloured for the vanilla sky and look dark against custom skies at night."));
		twinkle = add(new NumberSetting("Twinkle", 60, 0, 100, 5, "%")
				.describe("How much the built-in stars twinkle."));
		twinkle.visibleWhen(() -> style.is("Painted") || style.is("Nebula") || style.is("Void") || style.is("Black Hole"));
		constellation = add(new BoolSetting("Temple constellation", true)
				.describe("A constellation shaped like the Temple logo in the Painted sky.").visibleWhen(() -> style.is("Painted")));
		group();
		spin = add(new NumberSetting("Rotation speed", 4, 0, 60, 1, "")
				.describe("Sky rotation speed. 0 stops it."));
		tilt = add(new NumberSetting("Tilt", 0, -30, 30, 1, "°")
				.describe("Tilts the sky."));
		tinted = add(new BoolSetting("Tint", false)
				.describe("Tints the sky."));
		tint = add(new ColorSetting("Tint color", 0xFFE9B949).themed()
				.describe("Tint colour. Defaults to the theme gold.")
				.visibleWhen(tinted::get));
		group();
		aurora = add(new BoolSetting("Aurora", true)
				.describe("Draws an aurora across the sky."));
		aurora.visibleWhen(() -> !shaderStyle() && !style.is("Storm"));
		auroraStrength = add(new NumberSetting("Aurora strength", 70, 10, 100, 5, "%")
				.describe("Aurora brightness.").visibleWhen(aurora::get));
		auroraPalette = add(new ModeSetting("Aurora colors", List.of("Theme", "Natural", "Arctic", "Crimson"), "Theme")
				.describe("Theme uses the HUD accent. Natural is green to violet. Arctic is cyan to deep blue. Crimson is red.")
				.visibleWhen(aurora::get));
		picture = new UserTexture("sky_image", () -> FilePicker.path(image));
		painting = new BundledTexture("sky_painted", "/assets/templenz/textures/sky/painted.jpg");
		blank = new BundledTexture("sky_blank", "/assets/templenz/textures/sky/blank.png");
	}
	private boolean shaderStyle() {
		return style.is("Tendrils") || style.is("Silk") || style.is("Void") || style.is("Black Hole") || style.is("Plasma");
	}
	private static Skybox get() {
		var modules = TempleNzClient.modules();
		return modules == null ? null : modules.get(Skybox.class);
	}
	public static boolean active(boolean end) {
		Skybox s = get();
		return s != null && s.isEnabled() && (!end || s.dimensions.is("Overworld + End"));
	}
	public static org.joml.Vector4f horizonFog() {
		Skybox s = get();
		var level = net.minecraft.client.Minecraft.getInstance().level;
		if (s == null || !s.isEnabled() || level == null || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
				|| !s.image.isDefault()) {
			return null;
		}
		float k = s.brightness.getFloat() / 100f;
		float[] a = channels(s.skyColor.get());
		float[] b = channels(s.skyAccent.get());
		boolean own = !s.themeShader.get();
		float[] c = switch (s.style.get()) {
			case "Painted" -> new float[] {0.016f + a[0] * 0.23f + b[0] * 0.05f, 0.020f + a[1] * 0.23f + b[1] * 0.05f,
					0.048f + a[2] * 0.23f + b[2] * 0.05f};
			case "Storm" -> new float[] {0.075f + a[0] * 0.0875f, 0.079f + a[1] * 0.0875f, 0.094f + a[2] * 0.0875f};
			case "Tendrils" -> own ? new float[] {0.20f, 0.14f, 0.34f} : new float[] {a[0] * 0.25f, a[1] * 0.25f, a[2] * 0.25f};
			case "Silk" -> own ? new float[] {0.14f, 0.07f, 0.22f} : new float[] {a[0] * 0.2f, a[1] * 0.2f, a[2] * 0.2f};
			case "Plasma" -> own ? new float[] {0.03f, 0.05f, 0.10f} : new float[] {a[0] * 0.08f, a[1] * 0.08f, a[2] * 0.08f};
			case "Void", "Black Hole" -> new float[] {0.012f, 0.012f, 0.022f};
			case "Sunset" -> new float[] {0.62f, 0.33f, 0.26f};
			case "Daydream" -> new float[] {0.66f, 0.78f, 0.95f};
			case "Galaxy" -> new float[] {0.012f, 0.014f, 0.03f};
			case "Northern Lights" -> new float[] {0.025f, 0.055f, 0.085f};
			case "Topographic" -> {
				int t = s.topoColor(true);
				yield new float[] {((t >> 16) & 255) / 255f * 0.42f, ((t >> 8) & 255) / 255f * 0.42f, (t & 255) / 255f * 0.42f};
			}
			case "Eclipse" -> new float[] {0.05f, 0.055f, 0.085f};
			case "Synthwave" -> new float[] {0.55f, 0.14f, 0.32f};
			default -> new float[] {0.010f, 0.010f, 0.020f};
		};
		return new org.joml.Vector4f(c[0] * k, c[1] * k, c[2] * k, 1f);
	}
	private static float[] channels(int rgb) {
		return new float[] {((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
	}
	public static boolean hideClouds() {
		Skybox s = get();
		return s != null && s.isEnabled() && s.noClouds.get();
	}
	public static boolean keepCelestials() {
		Skybox s = get();
		return s == null || s.celestials.get();
	}
	public static boolean render(RenderTarget target, int skyColor) {
		Skybox s = get();
		if (s == null) {
			return false;
		}
		float light = 1;
		if (s.followDaylight.get() && skyColor != 0) {
			int r = (skyColor >> 16) & 0xFF;
			int g = (skyColor >> 8) & 0xFF;
			int b = skyColor & 0xFF;
			float lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
			light = 0.3f + 0.7f * Math.min(1, lum / 0.55f);
		}
		double seconds = System.nanoTime() / 1e9;
		float turn = (float) (seconds * s.spin.getFloat() * 0.0015 % (Math.PI * 2));
		int tint = s.tinted.get() ? blend(s.tint.get()) : 0xFFFFFF;
		boolean own = !s.image.isDefault() && s.picture.ready();
		int shaderStyle;
		Identifier texture;
		if (own) {
			shaderStyle = 0;
			texture = s.picture.id();
		} else if (s.style.is("Painted") && s.painting.ready()) {
			shaderStyle = 5;
			texture = s.painting.id();
		} else if (s.blank.ready()) {
			shaderStyle = switch (s.style.get()) {
				case "Storm" -> 3;
				case "Nebula" -> 4;
				case "Tendrils" -> 6;
				case "Silk" -> 7;
				case "Void" -> 8;
				case "Plasma" -> 9;
				case "Black Hole" -> 10;
				case "Sunset" -> 11;
				case "Galaxy" -> 12;
				case "Synthwave" -> 13;
				case "Eclipse" -> 14;
				case "Daydream" -> 15;
				case "Northern Lights" -> 16;
				case "Topographic" -> 17;
				default -> 4;
			};
			texture = s.blank.id();
		} else {
			return false;
		}
		Ambience ambience = Ambience.active();
		int fogColor = ambience != null && ambience.customFog() ? rgb(ambience.fogColor()) : 0;
		float skyFog = ambience != null && ambience.customFog() ? ambience.skyFog() : 0;
		return SkyboxRenderer.draw(target, texture, tint, s.brightness.getFloat() / 100f * light, turn,
				(float) Math.toRadians(s.tilt.getFloat()), s.twinkle.getFloat() / 100f, false, fogColor, skyFog,
				shaderStyle, s.aurora.get() ? s.auroraStrength.getFloat() / 100f : 0, s.paletteIndex(),
				CONSTELLATION_AZIMUTH, shaderStyle == 5 && s.constellation.get() ? 1 : 0,
				shaderStyle == 17 ? s.topoColor(true) : s.skyColor.get(), shaderStyle == 17 ? s.topoColor(false) : s.skyAccent.get(),
				s.themeShader.get());
	}
	/** The Topographic sky's colours: the HUD theme's light and deep colours, or the sky colours set here. */
	private int topoColor(boolean main) {
		if (themeShader.get()) {
			return main ? skyColor.get() : skyAccent.get();
		}
		return main ? nz.temple.client.hud.HudTheme.light() : nz.temple.client.hud.HudTheme.deep();
	}

	private int paletteIndex() {
		return switch (auroraPalette.get()) {
			case "Natural" -> 0;
			case "Arctic" -> 1;
			case "Crimson" -> 2;
			default -> 3;
		};
	}

	private static int rgb(org.joml.Vector4f color) {
		return Math.round(color.x * 255) << 16 | Math.round(color.y * 255) << 8 | Math.round(color.z * 255);
	}
	private static int blend(int argb) {
		int r = (255 + ((argb >> 16) & 0xFF)) / 2;
		int g = (255 + ((argb >> 8) & 0xFF)) / 2;
		int b = (255 + (argb & 0xFF)) / 2;
		return (r << 16) | (g << 8) | b;
	}
}
