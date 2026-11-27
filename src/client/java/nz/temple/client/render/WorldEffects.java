package nz.temple.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.modules.render.Ambience;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World effects: tones for the overworld, the way a shader pack restyles it. Each one sets the time of day you see
 * (the client's clock only; the server and everyone else keep the real time), the weather, the fog and mist, how sky
 * light and torch light colour the world, and a colour grade; Storm adds lightning. The sky itself is left as the
 * time of day makes it (the sky settings in Ambience still apply). Intensity scales how far the tone goes. Chosen in
 * Ambience (see {@link Ambience#worldEffect()}).
 */
public final class WorldEffects {
	/**
	 * One tone, at full intensity. Colours are 0xRRGGBB; fog distances in blocks; ambient tints vanilla's base light;
	 * grade is {brightness, contrast, saturation, warmth, tint, vignette, grain} as in Ambience's sliders.
	 */
	public record Effect(String name, int time, boolean fullMoon, int fog, float fogStart, float fogEnd, float fogThickness,
			float fogGradient, float rain, float thunder, boolean groundFog, float groundDepth, float groundDensity, float skyMin,
			Vector3fc skyLight, Vector3fc ambient, Vector3fc blockTint, float[] grade, int tint) {
	}

	public static final List<Effect> EFFECTS = List.of(
			// A clear moonlit night: cool blue mist far off, moonlight you can see by, torches warm.
			new Effect("Midnight", 18000, true, 0x0C1A33, 16, 150, 0.72f, 70, 0, 0, false, 0, 0,
					0.2f, new Vector3f(0.5f, 0.62f, 1f), new Vector3f(0.75f, 0.82f, 1f), new Vector3f(1f, 0.7f, 0.4f),
					new float[] {-0.02f, 0.12f, -0.15f, -0.25f, 0.1f, 0.35f, 0.05f}, 0x4A6CFF),
			// The last of the sun: long warm light and a soft orange haze.
			new Effect("Golden hour", 12250, false, 0xF2A267, 30, 260, 0.45f, 85, 0, 0, false, 0, 0,
					0, new Vector3f(1f, 0.78f, 0.52f), new Vector3f(1f, 0.9f, 0.8f), new Vector3f(1f, 0.8f, 0.55f),
					new float[] {0.03f, 0.12f, 0.18f, 0.4f, 0.12f, 0.25f, 0.05f}, 0xFFB066),
			// A red night, crimson fog closing in, everything lit the colour of blood.
			new Effect("Blood moon", 14800, true, 0x3A0A0C, 12, 120, 0.84f, 60, 0, 0, false, 0, 0,
					0.2f, new Vector3f(1f, 0.35f, 0.3f), new Vector3f(1f, 0.6f, 0.6f), new Vector3f(1f, 0.55f, 0.35f),
					new float[] {-0.03f, 0.2f, -0.05f, 0.25f, 0.35f, 0.45f, 0.08f}, 0xFF2A1E),
			// Dark overcast, heavy rain, grey-green light, the distance lost in fog, lightning.
			new Effect("Storm", 6500, false, 0x59605C, 6, 80, 0.93f, 55, 1, 1, false, 0, 0,
					0, new Vector3f(0.78f, 0.85f, 0.82f), new Vector3f(0.9f, 0.95f, 0.92f), new Vector3f(1f, 0.85f, 0.65f),
					new float[] {-0.1f, 0.15f, -0.35f, -0.08f, 0.1f, 0.4f, 0.12f}, 0x7FA08A),
			// A pale pink sunrise, thick mist lying in the valleys.
			new Effect("Dawn mist", 23300, false, 0xF0C9C0, 20, 200, 0.4f, 80, 0, 0, true, 3.5f, 45,
					0, new Vector3f(1f, 0.86f, 0.86f), new Vector3f(1f, 0.93f, 0.95f), new Vector3f(1f, 0.82f, 0.6f),
					new float[] {0.04f, -0.05f, -0.05f, 0.12f, 0.15f, 0.2f, 0.03f}, 0xFFB3C6),
			// Polar twilight: icy blue-white light and crisp, clear air.
			new Effect("Arctic", 14300, false, 0x9FC4D8, 20, 220, 0.5f, 75, 0, 0, false, 0, 0,
					0.3f, new Vector3f(0.75f, 0.9f, 1f), new Vector3f(0.88f, 0.95f, 1f), new Vector3f(1f, 0.85f, 0.7f),
					new float[] {0.02f, 0.1f, -0.2f, -0.35f, 0.12f, 0.25f, 0}, 0x9EE6FF));

	private static double nextBolt;
	private static Bolt bolt;

	private record Bolt(double[] path, int points, long born) {
	}

	private WorldEffects() {
	}

	/** How far the tone goes: Ambience's Intensity, 0 (none) to 2 (twice as strong). */
	public static float intensity() {
		Ambience a = Ambience.active();
		return a == null ? 0 : a.effectIntensity();
	}

	/** {@code full} scaled by intensity from {@code none} (0) through full (1) and beyond. */
	public static float scale(float none, float full) {
		return none + (full - none) * intensity();
	}

	private static Vector3f scale(Vector3fc none, Vector3fc full) {
		float k = intensity();
		return new Vector3f(Math.max(0, none.x() + (full.x() - none.x()) * k), Math.max(0, none.y() + (full.y() - none.y()) * k),
				Math.max(0, none.z() + (full.z() - none.z()) * k));
	}

	/** The effect in use, or null: Ambience on with an effect chosen, and you in the overworld. */
	public static Effect current() {
		Ambience a = Ambience.active();
		Minecraft mc = Minecraft.getInstance();
		if (a == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
			return null;
		}
		String name = a.worldEffect();
		for (Effect e : EFFECTS) {
			if (e.name().equals(name)) {
				return e;
			}
		}
		return null;
	}

	/** The clock the client shows: the real day, at the effect's time of day (on a full moon when it wants one). */
	public static long clock(long realTicks) {
		Effect e = current();
		if (e == null) {
			return realTicks;
		}
		long day = Math.floorDiv(realTicks, 24000L);
		if (e.fullMoon()) {
			day = Math.floorDiv(day, 8L) * 8L;
		}
		return day * 24000L + e.time();
	}

	/** Lightmap: moonlight you can see by, the effect's sky and ambient colour, torch light warmed or cooled. */
	//? if >=26 {
	public static void light(net.minecraft.client.renderer.state.LightmapRenderState state) {
		if (current() == null) {
			return;
		}
		state.skyFactor = skyFactor(state.skyFactor);
		state.skyLightColor = skyLight(state.skyLightColor);
		state.ambientColor = ambient(state.ambientColor);
		state.blockLightTint = blockTint(state.blockLightTint);
		state.needsUpdate = true;
	}
	//?}

	// The lightmap's inputs one at a time (1.21.11 writes them straight into the lightmap's uniforms); unchanged with
	// no effect on.

	public static float skyFactor(float vanilla) {
		Effect e = current();
		return e == null ? vanilla : Math.max(vanilla, e.skyMin() * Math.min(1, intensity()));
	}

	public static Vector3fc skyLight(Vector3fc vanilla) {
		Effect e = current();
		return e == null ? vanilla : scale(vanilla, e.skyLight());
	}

	/** Ambient is the light every block has at least: tint vanilla's own, never raise it to daylight. */
	public static Vector3fc ambient(Vector3fc vanilla) {
		Effect e = current();
		return e == null ? vanilla : new Vector3f(vanilla).mul(scale(new Vector3f(1), new Vector3f(e.ambient()).mul(1.3f)));
	}

	public static Vector3fc blockTint(Vector3fc vanilla) {
		Effect e = current();
		return e == null ? vanilla : scale(vanilla, e.blockTint());
	}

	/** Draws what the effect puts in the sky, and runs a storm's lightning. */
	public static void emit(Fx fx, float partial) {
		Effect e = current();
		if (e == null) {
			bolt = null;
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = fx.cameraPos();
		double t = Theme.time();
		fx.throughWalls(false);
		if (e.name().equals("Storm") && intensity() > 0.05f) {
			storm(fx, mc, cam, t);
		}
	}

	/** Lightning now and then: the sky flashes, a bolt forks down somewhere in the distance, thunder follows. */
	private static void storm(Fx fx, Minecraft mc, Vec3 cam, double t) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		if (nextBolt == 0) {
			nextBolt = t + 4 + r.nextDouble(6);
		}
		if (t >= nextBolt) {
			nextBolt = t + 6 + r.nextDouble(9);
			double a = r.nextDouble(Math.PI * 2);
			double dist = 90 + r.nextDouble(140);
			double x = cam.x + Math.cos(a) * dist, z = cam.z + Math.sin(a) * dist;
			double top = cam.y + 110, bottom = cam.y - 10;
			int n = 22;
			double[] path = new double[n * 3];
			double px = x, pz = z;
			for (int i = 0; i < n; i++) {
				double k = i / (double) (n - 1);
				path[i * 3] = px;
				path[i * 3 + 1] = top + (bottom - top) * k;
				path[i * 3 + 2] = pz;
				px += r.nextGaussian() * 3.5;
				pz += r.nextGaussian() * 3.5;
			}
			bolt = new Bolt(path, n, System.nanoTime());
			if (mc.level != null) {
				mc.level.setSkyFlashTime(2);
				mc.level.playLocalSound(x, cam.y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10000, 0.8f + r.nextFloat() * 0.2f,
						true);
			}
		}
		if (bolt != null) {
			double age = (System.nanoTime() - bolt.born()) / 1e9;
			if (age > 0.45) {
				bolt = null;
				return;
			}
			// Bright, then a flicker as the channel restrikes, then gone.
			double flicker = age < 0.08 ? 1 : age < 0.16 ? 0.25 : age < 0.24 ? 0.85 : Math.max(0, 1 - (age - 0.24) / 0.2);
			fx.additive(true);
			double[] p = bolt.path();
			for (int i = 0; i + 1 < bolt.points(); i++) {
				int a = i * 3, b = a + 3;
				fx.softBeam(p[a], p[a + 1], p[a + 2], p[b], p[b + 1], p[b + 2], 1.6, Fx.withAlpha(0xFFE8F0FF, (float) (240 * flicker)),
						Fx.withAlpha(0xFFE8F0FF, (float) (240 * flicker)));
				fx.softBeam(p[a], p[a + 1], p[a + 2], p[b], p[b + 1], p[b + 2], 7, Fx.withAlpha(0xFF8AA8FF, (float) (70 * flicker)),
						Fx.withAlpha(0xFF8AA8FF, (float) (70 * flicker)));
			}
			fx.additive(false);
		}
	}
}
