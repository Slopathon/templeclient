package nz.temple.client.cosmetic;

import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Theme;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finish;
import nz.temple.cosmetic.Finishes;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.Setting;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class PieceLook {
	private final List<Finish> finishes;
	private final ModeSetting finish;
	private final BoolSetting natural;
	private final ColorSetting colour;
	private final NumberSetting glow;
	private final ColorSetting glowColour;
	private static final int STEPS = 64;
	private final int[] ramp = new int[STEPS + 1];
	private final int[] glowRamp = new int[STEPS];
	private Finish cachedFinish;
	private Finish rampFinish;
	private String cachedFinishName;
	private boolean cachedNatural;
	private int cachedColour;
	private boolean rampValid;
	private int checkedFrame = -1;
	private int checksLeft;
	private int glowFrame = -1;
	private int glowChecksLeft;
	private double cachedGlow;
	public PieceLook(Consumer<Setting<?>> add, String piece, List<Finish> finishes, Finish defaultFinish, BooleanSupplier visible) {
		this(add, piece, finishes, defaultFinish, 60, visible);
	}
	public PieceLook(Consumer<Setting<?>> add, String piece, List<Finish> finishes, Finish defaultFinish, int defaultGlow,
			BooleanSupplier visible) {
		this.finishes = finishes;
		finish = new ModeSetting(piece + " finish", finishes.stream().map(Finish::name).toList(), defaultFinish.name())
				.describe("What the " + piece.toLowerCase() + " is made of.")
				.visibleWhen(visible);
		natural = new BoolSetting(piece + " natural colours", false)
				.describe("Uses the real colours of the finish instead of the colour below.")
				.visibleWhen(visible);
		colour = new ColorSetting(piece + " colour", 0xFFE9B949).themed()
				.describe("Main colour. Follows the theme until you pick one.")
				.visibleWhen(() -> visible.getAsBoolean() && !natural.get());
		glow = new NumberSetting(piece + " glow", defaultGlow, 0, 100, 5, "%")
				.describe("How strongly the magic glow shows on the " + piece.toLowerCase() + ". 0 leaves the bare material.")
				.visibleWhen(visible);
		glowColour = new ColorSetting(piece + " glow colour", 0xFFE9B949).themed()
				.describe("Colour of the glow. Follows the theme, made more vivid, until you pick one.")
				.visibleWhen(() -> visible.getAsBoolean() && glow.get() > 0);
		add.accept(finish);
		add.accept(natural);
		add.accept(colour);
		add.accept(glow);
		add.accept(glowColour);
	}
	public Finish finish() {
		String name = finish.get();
		if (name != cachedFinishName) {
			cachedFinishName = name;
			cachedFinish = finishes.get(0);
			for (Finish f : finishes) {
				if (f.name().equals(name)) {
					cachedFinish = f;
					break;
				}
			}
		}
		return cachedFinish;
	}
	private void refresh() {
		int frame = nz.temple.client.render.Fx.frame();
		if (rampValid && frame == checkedFrame && --checksLeft > 0) {
			return;
		}
		checkedFrame = frame;
		checksLeft = 4096;
		Finish f = finish();
		boolean nat = natural.get();
		int c = colour.get() | 0xFF000000;
		cachedGlow = glow.get() / 100.0 * Theme.intensity();
		if (rampValid && f == rampFinish && nat == cachedNatural && c == cachedColour) {
			return;
		}
		rampFinish = f;
		cachedNatural = nat;
		cachedColour = c;
		for (int i = 0; i <= STEPS; i++) {
			double t = (double) i / STEPS;
			ramp[i] = nat ? f.natural(t) : f.tinted(t, c);
		}
		rampValid = true;
	}
	public boolean is(String finishName) {
		return finish.is(finishName);
	}
	public int colour(double t) {
		refresh();
		return ramp[(int) Math.round(Math.max(0, Math.min(1, t)) * STEPS)];
	}
	public int tint(int real) {
		if (natural.get()) {
			return real;
		}
		int c = colour.get() | 0xFF000000;
		double lum = Colours.luminance(real);
		double cl = Math.max(0.05, Colours.luminance(c));
		return Colours.lerp(Colours.scale(c, Math.min(2.4, lum / cl)), real, 0.25) & 0x00FFFFFF | (real & 0xFF000000);
	}

	public int glowColour(double t) {
		int frame = nz.temple.client.render.Fx.frame();
		if (frame != glowFrame || --glowChecksLeft <= 0) {
			glowFrame = frame;
			glowChecksLeft = 4096;
			int c = glowColour.get() | 0xFF000000;
			if (glowColour.followsTheme()) {
				c = Colours.vivid(c);
			}
			int light = Colours.lerp(c, 0xFFFFFFFF, 0.3);
			for (int i = 0; i < STEPS; i++) {
				glowRamp[i] = Theme.flow(c, light, (double) i / STEPS);
			}
		}
		double w = t - Math.floor(t);
		return glowRamp[(int) (w * STEPS) % STEPS];
	}
	public double glow() {
		refresh();
		return cachedGlow;
	}
	public void apply(Meshes mesh) {
		mesh.finish(finish());
		if (glow() > 0) {
			Look.markGlow();
		}
	}

	public void glowOn(Meshes mesh, double weight, double t) {
		mesh.accent(weight * glow(), glowColour(t));
	}

	public static List<Finish> of(Finish... finishes) {
		for (Finish f : finishes) {
			Finishes.index(f);
		}
		return List.of(finishes);
	}
}
