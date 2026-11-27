package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Theme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AirEffects extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource, nz.temple.client.render.SnowSource {
	private static final int MAX = 500;
	private static final String FIREFLIES = "Fireflies", EMBERS = "Embers", SNOW = "Snow", STARDUST = "Stardust", MATRIX = "Matrix",
			LINES = "Light lines", CUBES = "Cubes", RINGS = "Rings", PRISMS = "Prisms", NETWORK = "Network", PILLARS = "Pillars";
	private static final Vec3 AXIS_X = new Vec3(1, 0, 0);
	private static final Vec3 AXIS_Z = new Vec3(0, 0, 1);
	private final Map<String, BoolSetting> effects = new LinkedHashMap<>();
	private final NumberSetting density;
	private final NumberSetting radius;
	private final NumberSetting clearRadius;
	private final NumberSetting size;
	private final NumberSetting speed;
	private final NumberSetting branching;
	private final ModeSetting colors;
	private final ColorSetting custom;
	private final NumberSetting brightness;
	private final BoolSetting bloom;
	private final BoolSetting throughWalls;
	private final BoolSetting volumetric;
	private final BoolSetting drifts;
	private final NumberSetting driftSpeed;
	private final NumberSetting snowfall;
	private final NumberSetting wind;
	private final NumberSetting haze;
	private final ModeSetting quality;
	private final NumberSetting lineLength;
	private final NumberSetting pulseSpeed;
	private final ModeSetting lineShape;
	private final NumberSetting dashDistance;
	private final NumberSetting restTime;
	private final ModeSetting cubeMaterial;
	private final BoolSetting cubeTrail;
	private final NumberSetting linkRange;
	private final Map<String, List<Mote>> pools = new LinkedHashMap<>();
	private long lastNanos = System.nanoTime();
	private int frame;
	private double camX, camY, camZ;
	private double flatRightX = 1, flatRightZ;
	private static final class Mote {
		double x, y, z, vx, vy, vz;
		final double phase;
		final double life;
		final double seed;
		double age;
		double fall;
		double[] path;
		double pathLength;
		double fromX, fromY, fromZ, toX, toY, toZ;
		double dashAt = -1;
		double nextDash;
		double arrived = -10;
		double lastDx = 1, lastDy, lastDz;
		double turnFrom, turnTo;
		Mote(double x, double y, double z, double vx, double vy, double vz, double phase, double life, double age, double seed) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.phase = phase;
			this.life = life;
			this.age = age;
			this.seed = seed;
		}
	}
	public AirEffects() {
		super("Air Effects", Category.COSMETICS, "Particles and shapes floating around you. Any combination can be on at once: fireflies, "
				+ "embers, snow, stardust, falling code, light lines, dashing cubes, rings, prisms, a light network and light pillars.");
		effect(FIREFLIES, true, "Fireflies that drift around and flash occasionally.");
		effect(EMBERS, false, "Embers that rise and fade from white to red.");
		effect(SNOW, false, "Falling snowflakes. Fills the whole sky with Volumetric snow on.");
		effect(STARDUST, false, "Small glinting specks floating in the air.");
		effect(MATRIX, false, "Falling columns of code around you.");
		effect(LINES, false, "Lines in the air with pulses of light travelling along them.");
		effect(CUBES, false, "Small cubes that pause, dash to a new spot, and pause again.");
		effect(RINGS, false, "Slowly turning rings with light travelling around them.");
		effect(PRISMS, false, "Floating prisms that occasionally rotate a third of a turn.");
		effect(NETWORK, false, "Points of light linked to nearby points, with pulses travelling between them.");
		effect(PILLARS, false, "Columns of light with a bright band moving up them.");
		group();
		density = add(new NumberSetting("Density", 70, 10, 500, 10, "").describe("How many of each effect appear."));
		radius = add(new NumberSetting("Radius", 14, 4, 40, 1, " b").describe("How far around you they appear."));
		clearRadius = add(new NumberSetting("Clear radius", 6, 0, 20, 0.5, " b")
				.describe("Nothing spawns within this distance of you, so your view stays clear."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Particle size."));
		speed = add(new NumberSetting("Speed", 1, 0.2, 3, 0.1, "x").describe("How fast they drift or fall."));
		branching = add(new NumberSetting("Branching", 45, 0, 100, 5, "%")
				.describe("How often falling code streams branch off.")
				.visibleWhen(() -> on(MATRIX)));
		lineShape = add(new ModeSetting("Line shape", List.of("Straight", "Bent", "Circuit"), "Bent")
				.describe("Straight draws single lines. Bent draws lines with one corner. Circuit draws lines with two or three corners, "
						+ "like a circuit board trace.")
				.visibleWhen(() -> on(LINES)));
		lineLength = add(new NumberSetting("Line length", 3, 1, 8, 0.25, " b").describe("How long each line runs.")
				.visibleWhen(() -> on(LINES)));
		pulseSpeed = add(new NumberSetting("Pulse speed", 5, 1, 16, 0.5, " b/s")
				.describe("How fast light travels along lines, rings, pillars and the network.")
				.visibleWhen(() -> on(LINES) || on(RINGS) || on(PILLARS) || on(NETWORK)));
		linkRange = add(new NumberSetting("Link range", 3.5, 1.5, 8, 0.25, " b").describe("Maximum distance between two network points that "
				+ "can connect.")
				.visibleWhen(() -> on(NETWORK)));
		dashDistance = add(new NumberSetting("Dash distance", 2.5, 0.5, 6, 0.25, " b").describe("How far a cube travels in one dash.")
				.visibleWhen(() -> on(CUBES)));
		restTime = add(new NumberSetting("Rest time", 1.2, 0.2, 5, 0.1, " s").describe("Average time a cube or prism waits between moves.")
				.visibleWhen(() -> on(CUBES) || on(PRISMS)));
		cubeMaterial = add(new ModeSetting("Cube material", List.of("Glow", "Glass", "Matte", "Metal"), "Glow")
				.describe("Material for cubes and prisms. Glow is emissive. Glass is clear and refracts the world behind it. Matte is solid "
						+ "colour. Metal is polished.")
				.visibleWhen(() -> on(CUBES) || on(PRISMS)));
		cubeTrail = add(new BoolSetting("Dash trail", true).describe("Leaves fading copies behind each dash.")
				.visibleWhen(() -> on(CUBES)));
		group();
		colors = add(new ModeSetting("Color", List.of("Natural", "Theme", "Custom"), "Theme")
				.describe("Natural uses each effect's own colours. Theme uses the HUD accent. Custom uses one colour."));
		custom = add(new ColorSetting("Custom color", 0xFFE9B949).themed().describe("Colour used when Color is Custom.").visibleWhen(() -> colors.is("Custom")));
		brightness = add(new NumberSetting("Brightness", 70, 10, 100, 5, "%").describe("Overall intensity."));
		bloom = add(new BoolSetting("Bloom", true)
				.describe("Adds a soft glow around the brightest parts (firefly flashes, embers, pulses, neon). Off saves a little GPU "
						+ "time."));
		group();
		volumetric = add(new BoolSetting("Volumetric snow", true)
				.describe("Fills the sky with falling snow out to the horizon, kept out from under roofs and fading into haze in the "
						+ "distance.")
				.visibleWhen(() -> on(SNOW)));
		drifts = add(new BoolSetting("Snow drifts", false)
				.describe("Snow settles where you go and builds up over time, from a light dusting to deep drifts behind walls and trees. "
						+ "It does not settle under roofs, and only a little under trees. Players and mobs leave footprints, and torches, "
						+ "fire and lava melt it. The sky looks overcast on your screen while it is on, without rain; the server's weather "
						+ "is unchanged. It clears when you leave.")
				.visibleWhen(() -> on(SNOW)));
		driftSpeed = add(new NumberSetting("Drift speed", 100, 25, 400, 25, "%").describe("How fast settled snow builds up.")
				.visibleWhen(() -> on(SNOW) && drifts.get()));
		snowfall = add(new NumberSetting("Snowfall", 35, 5, 100, 5, "%").describe("How heavy the snowfall is.")
				.visibleWhen(() -> on(SNOW) && volumetric.get()));
		wind = add(new NumberSetting("Wind", 1, 0, 4, 0.1, " b/s").describe("How strongly the wind blows snow sideways and piles drifts "
				+ "behind walls and trees.")
				.visibleWhen(() -> on(SNOW) && (volumetric.get() || drifts.get())));
		haze = add(new NumberSetting("Haze", 35, 0, 100, 5, "%").describe("How much the snow hazes the distance.")
				.visibleWhen(() -> on(SNOW) && volumetric.get()));
		quality = add(new ModeSetting("Quality", List.of("Low", "Medium", "High", "Ultra"), "High")
				.describe("How many layers of snow are drawn. Higher looks fuller and costs more GPU time.")
				.visibleWhen(() -> on(SNOW) && volumetric.get()));
		throughWalls = add(new BoolSetting("Through walls", false)
				.describe("Shows particles through terrain. Off hides them behind blocks."));
	}
	private void effect(String name, boolean on, String description) {
		effects.put(name, add(new BoolSetting(name, on).describe(description)));
	}
	private boolean on(String effect) {
		BoolSetting s = effects.get(effect);
		return s != null && s.get();
	}
	public nz.temple.client.render.snow.SnowDrifts.Settings driftSettings() {
		boolean on = isEnabled() && on(SNOW) && drifts.get();
		float w = wind.getFloat();
		return new nz.temple.client.render.snow.SnowDrifts.Settings(on, driftSpeed.getFloat() / 100f, w * 0.8f, w * 0.45f,
				(colors.is("Custom") ? custom.get() : 0xFFF6F9FF) & 0xFFFFFF);
	}

	public nz.temple.client.render.SnowVolume.Settings snowVolume() {
		boolean on = isEnabled() && on(SNOW) && volumetric.get();
		int shells = switch (quality.get()) {
			case "Low" -> 16;
			case "Medium" -> 24;
			case "Ultra" -> 44;
			default -> 32;
		};
		return new nz.temple.client.render.SnowVolume.Settings(on, snowfall.getFloat() / 100f * 0.85f,
				0.013f * size.getFloat(), wind.getFloat() * speed.getFloat(), 1.25f * speed.getFloat(), haze.getFloat() / 100f * 0.4f,
				brightness.getFloat() / 100f * 1.25f, shells, (colors.is("Custom") ? custom.get() : 0xFFF4F8FF) & 0xFFFFFF);
	}
	@Override
	public void preview(Vec3 at) {
	}
	private static double share(String effect) {
		return switch (effect) {
			case LINES -> 0.45;
			case CUBES, PRISMS -> 0.6;
			case RINGS -> 0.3;
			case NETWORK -> 0.4;
			case PILLARS -> 0.25;
			default -> 1;
		};
	}
	public void emit(Fx fx, float partial) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1) * speed.get();
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.player == null || mc.level == null) {
			pools.clear();
			return;
		}
		Vec3 p = mc.player.getPosition(partial).add(0, mc.player.getEyeHeight() * 0.6, 0);
		Vec3 cam = fx.cameraPos();
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;
		Vec3 right = fx.cameraRight();
		double flat = Math.hypot(right.x, right.z);
		if (flat > 1e-4) {
			flatRightX = right.x / flat;
			flatRightZ = right.z / flat;
		}
		double r = radius.get();
		fx.throughWalls(throughWalls.get());
		float bright = 255f * brightness.getFloat() / 100f;
		double time = now / 1e9;
		frame++;
		for (String effect : effects.keySet()) {
			boolean running = on(effect) && !(effect.equals(SNOW) && volumetric.get());
			if (!running) {
				pools.remove(effect);
				continue;
			}
			List<Mote> motes = pools.computeIfAbsent(effect, k -> new ArrayList<>());
			int want = (int) Math.min(MAX, Math.round(density.get() * share(effect)));
			int spawnBudget = 24;
			while (motes.size() < want && spawnBudget-- > 0) {
				Mote m = spawn(effect, p, r, true);
				if (m != null) {
					motes.add(m);
				}
			}
			while (motes.size() > want) {
				motes.remove(motes.size() - 1);
			}
			run(effect, motes, fx, p, r, dt, time, bright);
		}
		fx.bloom(false);
		fx.additive(false);
	}
	private void run(String effect, List<Mote> motes, Fx fx, Vec3 p, double r, double dt, double time, float bright) {
		Minecraft mc = Minecraft.getInstance();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		ThreadLocalRandom rand = ThreadLocalRandom.current();
		float[] alphas = new float[motes.size()];
		for (int i = 0; i < motes.size(); i++) {
			Mote m = motes.get(i);
			m.age += dt;
			double dx = m.x - p.x;
			double dy = m.y - p.y;
			double dz = m.z - p.z;
			double distSq = dx * dx + dy * dy + dz * dz;
			boolean buried = false;
			if ((i + frame & 3) == 0 && !effect.equals(PILLARS)) {
				cursor.set(m.x, m.y, m.z);
				buried = !mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty();
			}
			if (m.age > m.life || distSq > r * r * 1.6 || buried) {
				Mote next = spawn(effect, p, r, false);
				if (next != null) {
					motes.set(i, next);
				}
				continue;
			}
			step(effect, m, time, dt, rand);
			double u = m.age / m.life;
			double env = Math.sin(Math.PI * Math.min(1, Math.max(0, u)));
			float envelope = (float) (env * env * (3 - 2 * env));
			double dist = Math.sqrt(distSq);
			double edge = Math.max(0, 1 - dist / (r * 1.25));
			double inner = clearRadius.get() <= 0 ? 1 : smooth((dist - clearRadius.get()) / 2.5);
			float a = bright * envelope * (float) Math.min(1, edge * 2.2) * (float) inner;
			alphas[i] = a;
			if (a <= 1) {
				continue;
			}
			draw(effect, fx, m, time, a, u);
		}
		if (effect.equals(NETWORK)) {
			links(fx, motes, alphas, time);
		}
	}
	private static double smooth(double t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}
	private void step(String effect, Mote m, double time, double dt, ThreadLocalRandom rand) {
		switch (effect) {
			case EMBERS -> {
				double heat = 1 - m.age / m.life;
				m.vx += (Math.sin(time * 1.3 + m.phase + m.y * 0.8) * 0.55 - m.vx * 0.7) * dt;
				m.vz += (Math.cos(time * 1.1 + m.phase + m.x * 0.8) * 0.55 - m.vz * 0.7) * dt;
				m.vy += (m.fall * (0.3 + 0.7 * heat) - m.vy) * Math.min(1, dt * 1.5);
			}
			case SNOW -> {
				m.vx = Math.sin(time * 0.6 + m.phase) * 0.25 + Math.sin(time * 2.3 + m.phase * 2) * 0.06;
				m.vz = Math.cos(time * 0.5 + m.phase) * 0.25 + Math.cos(time * 2.1 + m.phase * 3) * 0.06;
				m.vy = m.fall * (1 + 0.35 * Math.sin(time * 2.1 + m.phase));
			}
			case STARDUST -> {
				m.vx = Math.sin(time * 0.13 + m.phase) * 0.05 + Math.sin(time * 0.31 + m.seed) * 0.02;
				m.vz = Math.cos(time * 0.11 + m.phase * 1.3) * 0.05 + Math.cos(time * 0.27 + m.seed) * 0.02;
				m.vy = Math.sin(time * 0.17 + m.seed * 2) * 0.025;
			}
			case MATRIX -> {
			}
			case LINES, RINGS, PILLARS -> {
				return;
			}
			case NETWORK -> {
				m.vx = Math.sin(time * 0.21 + m.phase) * 0.05;
				m.vy = Math.cos(time * 0.17 + m.phase * 1.3) * 0.03;
				m.vz = Math.cos(time * 0.19 + m.phase) * 0.05;
			}
			case CUBES -> {
				dash(m, time, rand);
				return;
			}
			case PRISMS -> {
				turn(m, time, rand);
				return;
			}
			default -> {
				double t = time * 0.35;
				double wantX = (Math.sin(t + m.phase) + 0.5 * Math.sin(t * 2.3 + m.seed)) * 0.45;
				double wantZ = (Math.cos(t * 0.9 + m.phase * 1.7) + 0.5 * Math.cos(t * 1.9 + m.seed * 0.7)) * 0.45;
				double wantY = Math.sin(t * 1.3 + m.seed * 1.3) * 0.15 + flash(m, time) * 0.45;
				double k = Math.min(1, dt * 1.6);
				m.vx += (wantX - m.vx) * k;
				m.vy += (wantY - m.vy) * k;
				m.vz += (wantZ - m.vz) * k;
			}
		}
		m.x += m.vx * dt;
		m.y += m.vy * dt;
		m.z += m.vz * dt;
	}
	private void draw(String effect, Fx fx, Mote m, double time, float a, double u) {
		double s = size.get();
		Meshes mesh = fx.mesh();
		mesh.bloom(false, 1f);
		switch (effect) {
			case EMBERS -> ember(fx, m, time, a, u, s);
			case SNOW -> snowflake(fx, m, time, a, s);
			case STARDUST -> stardust(fx, m, time, a, s);
			case MATRIX -> matrix(fx, m, time, a, s);
			case LINES -> line(fx, m, time, a, s);
			case CUBES -> cube(fx, m, time, a, s);
			case RINGS -> ring(fx, m, time, a, s);
			case PRISMS -> prism(fx, m, time, a, s);
			case NETWORK -> node(fx, m, time, a, s);
			case PILLARS -> pillar(fx, m, time, a, s);
			default -> firefly(fx, m, time, a, s);
		}
	}
	private boolean near(double x, double y, double z, double limit) {
		double dx = x - camX, dy = y - camY, dz = z - camZ;
		return dx * dx + dy * dy + dz * dz < limit * limit;
	}

	private void bloomLight(Fx fx, double x, double y, double z, double r, int argb) {
		if (!bloom.get() || Fx.alpha(argb) < 2) {
			return;
		}
		Look.markGlow();
		fx.bloom(true);
		fx.glow(x, y, z, r, argb);
		fx.bloom(false);
	}
	private int color(int natural, double phase) {
		return switch (colors.get()) {
			case "Theme" -> Theme.color(phase * 0.16);
			case "Custom" -> custom.get();
			default -> natural;
		};
	}
	private static int hash(long a, long b) {
		long h = a * 0x9E3779B97F4A7C15L ^ (b + 0x632BE59BD9B4E019L) * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) (h & 0x7FFFFFFF);
	}

	private static float flash(Mote m, double time) {
		double period = 2.6 + (m.seed % 1) * 2.8;
		double f = ((time + m.seed * 3.1) % period) / period;
		double on = 0.13;
		double k = f < on ? Math.sin(Math.PI * f / on) : 0;
		if ((m.seed * 7) % 1 < 0.35) {
			double f2 = f - on * 1.5;
			if (f2 > 0 && f2 < on * 0.8) {
				k = Math.max(k, 0.7 * Math.sin(Math.PI * f2 / (on * 0.8)));
			}
		}
		return (float) (k * k * (3 - 2 * k));
	}
	private void firefly(Fx fx, Mote m, double time, float a, double s) {
		float flash = flash(m, time);
		float lit = 0.42f + 0.58f * flash;
		int glow = color(0xFFC8F04A, m.phase);
		if (near(m.x, m.y, m.z, 5.5 * s)) {
			Meshes mesh = fx.mesh();
			double heading = Math.atan2(m.vz, m.vx);
			double pitch = Math.max(-0.6, Math.min(0.6, Math.atan2(m.vy, Math.hypot(m.vx, m.vz))));
			Quaternionf dir = new Quaternionf().rotationY((float) -heading).rotateZ((float) pitch);
			Vector3f v = new Vector3f();
			mesh.material(Meshes.Material.MATTE);
			mesh.emission(0.04f);
			int shell = Fx.withAlpha(0xFF2B2119, a);
			dir.transform(v.set((float) (0.036 * s), 0, 0));
			mesh.sphere(m.x + v.x, m.y + v.y, m.z + v.z, 0.011 * s, Fx.withAlpha(0xFF6A3A22, a), 10);
			dir.transform(v.set((float) (0.014 * s), (float) (0.002 * s), 0));
			mesh.ellipsoid(m.x + v.x, m.y + v.y, m.z + v.z, 0.016 * s, 0.012 * s, 0.014 * s, dir, shell, 10);
			mesh.emission(lit);
			int lantern = Fx.withAlpha(Fx.lerp(0xFF8C8A5A, Fx.lerp(glow, 0xFFFFFFFF, 0.35), lit), a);
			dir.transform(v.set((float) (-0.016 * s), (float) (-0.002 * s), 0));
			mesh.ellipsoid(m.x + v.x, m.y + v.y, m.z + v.z, 0.026 * s, 0.014 * s, 0.016 * s, dir, lantern, 10);
			mesh.material(Meshes.Material.GLASS);
			mesh.emission(0.08f);
			dir.transform(v.set((float) (0.012 * s), (float) (0.012 * s), 0));
			for (int pair = 0; pair < 2; pair++) {
				double beat = Math.sin(time * 42 + m.phase + pair * 1.1) * 0.65;
				double sweep = pair == 0 ? -0.35 : 0.35;
				double len = (pair == 0 ? 0.06 : 0.052) * s;
				for (int side = -1; side <= 1; side += 2) {
					Quaternionf wing = new Quaternionf(dir).rotateY((float) (side * (Math.PI / 2 + sweep))).rotateX((float) (side * beat));
					mesh.blade(m.x + v.x, m.y + v.y, m.z + v.z, wing, len, 0.03 * s, 0.002, 0.004 * s,
							Fx.withAlpha(0xFFE8F0FF, a * 0.4f), Fx.withAlpha(0xFFE8F0FF, a * 0.14f));
				}
			}
		}
		Vector3f tail = new Quaternionf().rotationY((float) -Math.atan2(m.vz, m.vx)).transform(new Vector3f((float) (-0.02 * s), 0, 0));
		double lx = m.x + tail.x, ly = m.y + tail.y, lz = m.z + tail.z;
		fx.additive(true);
		fx.light(lx, ly, lz, 0.07 * s, Fx.withAlpha(glow, a * lit), 0.9);
		fx.light(lx, ly, lz, 0.38 * s, Fx.withAlpha(glow, a * 0.4f * lit), 0);
		fx.additive(false);
		if (flash > 0.05f) {
			bloomLight(fx, lx, ly, lz, 0.16 * s, Fx.withAlpha(glow, a * 0.85f * flash));
		}
	}
	private int heatColour(double heat, double phase) {
		heat = Math.max(0, Math.min(1, heat));
		if (colors.is("Natural")) {
			if (heat > 0.66) {
				return Fx.lerp(0xFFFF8A2A, 0xFFFFF2C8, (heat - 0.66) / 0.34);
			}
			if (heat > 0.3) {
				return Fx.lerp(0xFFB0230C, 0xFFFF8A2A, (heat - 0.3) / 0.36);
			}
			return Fx.lerp(0xFF3A2C28, 0xFFB0230C, heat / 0.3);
		}
		int c = color(HudModule.GOLD, phase);
		return heat > 0.6 ? Fx.lerp(c, 0xFFFFFFFF, (heat - 0.6) / 0.4 * 0.75) : Fx.lerp(Fx.lerp(c, 0xFF000000, 0.65), c, heat / 0.6);
	}

	private void ember(Fx fx, Mote m, double time, float a, double u, double s) {
		double flicker = 0.78 + 0.22 * Math.sin(time * 23 + m.seed) * Math.sin(time * 7.3 + m.phase);
		double heat = (1 - u) * flicker;
		int c = heatColour(heat, m.phase);
		double sz = 0.07 * s * (0.7 + (m.seed % 1) * 0.6) * (1 - u * 0.45);
		if (near(m.x, m.y, m.z, 9 * s)) {
			Meshes mesh = fx.mesh();
			mesh.material(Meshes.Material.NEON);
			mesh.emission((float) Math.min(1, heat * 1.2));
			if (heat > 0.45 && bloom.get()) {
				Look.markGlow();
				mesh.bloom(true, (float) heat);
			}
			Quaternionf spin = new Quaternionf().rotationXYZ((float) (time * 3 + m.phase), (float) (time * 2.1 + m.seed), (float) m.phase);
			mesh.crystal(m.x, m.y, m.z, spin, sz, sz * 0.42, Fx.withAlpha(c, a), 5);
			mesh.bloom(false, 1f);
		}
		fx.additive(true);
		double sp = Math.sqrt(m.vx * m.vx + m.vy * m.vy + m.vz * m.vz);
		if (sp > 0.05) {
			double len = sz * 1.5 + sp * 0.12 * s;
			fx.streak(m.x - m.vx / sp * len * 0.4, m.y - m.vy / sp * len * 0.4, m.z - m.vz / sp * len * 0.4, m.vx, m.vy, m.vz,
					len, 0.035 * s, Fx.withAlpha(c, a * 0.55f * (float) heat));
		}
		fx.light(m.x, m.y, m.z, sz * 0.9, Fx.withAlpha(c, a * (float) Math.min(1, heat * 1.3)), 0.6 * heat);
		fx.light(m.x, m.y, m.z, sz * 3.2, Fx.withAlpha(c, a * 0.3f * (float) heat), 0);
		fx.additive(false);
		if (heat > 0.5) {
			bloomLight(fx, m.x, m.y, m.z, sz * 2, Fx.withAlpha(c, a * 0.6f * (float) heat));
		}
	}
	private void snowflake(Fx fx, Mote m, double time, float a, double s) {
		int c = color(0xFFF4FAFF, m.phase);
		Quaternionf q = new Quaternionf().rotationY((float) (time * 0.8 + m.phase))
				.rotateX((float) (0.6 + Math.sin(time * 1.1 + m.phase) * 0.5));
		Vector3f ax = q.transform(new Vector3f(1, 0, 0));
		Vector3f az = q.transform(new Vector3f(0, 0, 1));
		Vector3f n = q.transform(new Vector3f(0, 1, 0));
		double arm = 0.075 * s * (0.8 + (m.seed % 1) * 0.45);
		boolean detail = near(m.x, m.y, m.z, 9 * s);
		int body = Fx.withAlpha(c, a * 0.9f);
		int tip = Fx.withAlpha(c, a * 0.55f);
		for (int k = 0; k < 6; k++) {
			double ang = k * Math.PI / 3;
			double cs = Math.cos(ang), sn = Math.sin(ang);
			double dx = ax.x * cs + az.x * sn, dy = ax.y * cs + az.y * sn, dz = ax.z * cs + az.z * sn;
			double px = -ax.x * sn + az.x * cs, py = -ax.y * sn + az.y * cs, pz = -ax.z * sn + az.z * cs;
			fx.softBeam(m.x, m.y, m.z, m.x + dx * arm, m.y + dy * arm, m.z + dz * arm, 0.012 * s, body, tip);
			if (!detail) {
				continue;
			}
			for (int b = 0; b < 2; b++) {
				double at = b == 0 ? 0.42 : 0.7;
				double len = arm * (b == 0 ? 0.34 : 0.22);
				double bx = m.x + dx * arm * at, by = m.y + dy * arm * at, bz = m.z + dz * arm * at;
				for (int side = -1; side <= 1; side += 2) {
					double ex = dx * 0.5 + px * side * 0.866, ey = dy * 0.5 + py * side * 0.866, ez = dz * 0.5 + pz * side * 0.866;
					fx.softBeam(bx, by, bz, bx + ex * len, by + ey * len, bz + ez * len, 0.008 * s, body, tip);
				}
			}
		}
		if (detail) {
			fx.disc(m.x, m.y, m.z, new Vec3(ax.x, ax.y, ax.z), new Vec3(az.x, az.y, az.z), 0.014 * s, 0.014 * s, Fx.withAlpha(c, a * 0.7f));
		}
		double vx = camX - m.x, vy = camY - m.y, vz = camZ - m.z;
		double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
		double facing = vl < 1e-6 ? 0 : Math.abs((n.x * vx + n.y * vy + n.z * vz) / vl);
		float glint = (float) Math.pow(facing, 18);
		fx.additive(true);
		fx.light(m.x, m.y, m.z, arm * 1.4, Fx.withAlpha(c, a * 0.1f), 0);
		if (glint > 0.04f) {
			fx.sparkle(m.x, m.y, m.z, arm * 1.6 * glint, Fx.withAlpha(0xFFFFFFFF, a * glint), m.phase);
		}
		fx.additive(false);
	}
	private void stardust(Fx fx, Mote m, double time, float a, double s) {
		double sc = 0.6 * Math.sin(time * 2.2 + m.phase * 5) + 0.25 * Math.sin(time * 5.7 + m.seed) + 0.15 * Math.sin(time * 13.1 + m.phase * 3);
		float twinkle = (float) Math.pow(Math.max(0, sc), 3);
		int natural = (m.seed * 0.37) % 1 < 0.3 ? 0xFFCFE0FF : Fx.lerp(HudModule.GOLD_LIGHT, 0xFFFFFFFF, (m.seed % 1) * 0.3);
		int c = color(natural, m.phase);
		if (near(m.x, m.y, m.z, 4 * s)) {
			Meshes mesh = fx.mesh();
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.3f + 0.6f * twinkle);
			mesh.sphere(m.x, m.y, m.z, 0.014 * s, Fx.withAlpha(c, a), 10);
		}
		fx.additive(true);
		fx.light(m.x, m.y, m.z, 0.05 * s, Fx.withAlpha(c, a * (0.6f + 0.4f * twinkle)), 1);
		fx.light(m.x, m.y, m.z, 0.2 * s, Fx.withAlpha(c, a * 0.2f * (0.6f + twinkle)), 0);
		if (twinkle > 0.08f) {
			double spin = m.phase + time * 0.15;
			fx.sparkle(m.x, m.y, m.z, 0.24 * s * twinkle, Fx.withAlpha(c, a * twinkle), spin);
			fx.sparkle(m.x, m.y, m.z, 0.12 * s * twinkle, Fx.withAlpha(c, a * 0.45f * twinkle), spin + Math.PI / 4);
		}
		fx.additive(false);
		if (twinkle > 0.35f) {
			bloomLight(fx, m.x, m.y, m.z, 0.1 * s, Fx.withAlpha(c, a * 0.7f * twinkle));
		}
	}
	private static final double[][] STROKES = {
			{-1, 1.4, 1, 1.4}, {-1, 0, 1, 0}, {-1, -1.4, 1, -1.4},
			{-1, 1.4, -1, 0}, {-1, 0, -1, -1.4}, {1, 1.4, 1, 0}, {1, 0, 1, -1.4},
			{0, 1.4, 0, 0}, {0, 0, 0, -1.4},
			{1, 0, -1, -1.4}, {-1, 1.4, 0, 0}, {0.3, 0.7, 1, 0.7}, {0, 0, 1, -1.4}, {-0.6, 0.7, 0.6, 0.7}};
	private static final int[][] GLYPHS = {
			{0, 5, 9}, {0, 7, 8}, {13, 1, 8}, {3, 0, 5, 1}, {0, 1, 2, 6}, {7, 1, 9}, {10, 12}, {0, 6, 2, 5},
			{11, 13, 8}, {1, 8, 4, 6}, {0, 3, 4, 2}, {13, 9, 6}, {7, 11, 1, 12}, {0, 1, 7, 8}, {5, 6, 1, 4}, {10, 1, 6}};
	private void matrix(Fx fx, Mote m, double time, float a, double s) {
		int c = color(0xFF3BFF7A, m.phase);
		double cell = 0.15 * s;
		double half = 0.034 * s;
		int count = 7 + (int) (m.seed % 6);
		long stream = (long) (m.seed * 1000);
		long headCell = (long) Math.floor(m.y / cell);
		double frac = m.y / cell - headCell;
		boolean detail = near(m.x, m.y, m.z, 12 * s);
		for (int i = 0; i < count; i++) {
			long worldCell = headCell + i;
			double y = worldCell * cell + cell * 0.5;
			float f = (float) Math.max(0, 1 - (i + 1 - frac) / count);
			if (f <= 0) {
				continue;
			}
			double rate = i == 0 ? 14 : 0.6 + (hash(stream, worldCell) % 1000) / 1000.0 * 2.4;
			int pick = hash(stream * 31 + worldCell, (long) Math.floor(time * rate + (hash(worldCell, stream) % 97) / 97.0));
			int[] g = GLYPHS[pick % GLYPHS.length];
			double mirror = (pick >> 8 & 1) == 0 ? 1 : -1;
			int col = i == 0 ? Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.75), a) : Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.25 * f), a * f * f);
			if (!detail) {
				fx.softBeam(m.x, y - half * 1.2, m.z, m.x, y + half * 1.2, m.z, half * 1.6, col, col);
				continue;
			}
			for (int k : g) {
				double[] st = STROKES[k];
				double x0 = st[0] * half * mirror, x1 = st[2] * half * mirror;
				fx.softBeam(m.x + flatRightX * x0, y + st[1] * half, m.z + flatRightZ * x0,
						m.x + flatRightX * x1, y + st[3] * half, m.z + flatRightZ * x1, 0.02 * s, col, col);
			}
		}
		double headY = headCell * cell + cell * 0.5;
		double top = headY + cell * count;
		fx.additive(true);
		fx.softBeam(m.x, headY, m.z, m.x, top, m.z, 0.09 * s, Fx.withAlpha(c, a * 0.09f), Fx.withAlpha(c, 0));
		fx.light(m.x, headY, m.z, 0.13 * s, Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.5), a * 0.55f), 0.7);
		fx.additive(false);
		bloomLight(fx, m.x, headY, m.z, 0.16 * s, Fx.withAlpha(c, a * 0.6f));
		double chance = branching.get() / 100.0;
		if ((m.seed % 1.0) > chance) {
			return;
		}
		double tail = cell * count;
		int branches = 1 + (int) ((m.seed * 7) % 2);
		fx.additive(true);
		for (int b = 0; b < branches; b++) {
			double angle = (m.seed * (13 + b * 7)) % (Math.PI * 2);
			double at = tail * (0.25 + ((m.seed * (3 + b)) % 1.0) * 0.5);
			double len = 0.5 + ((m.seed * (5 + b)) % 1.0) * 1.2;
			double bx = m.x, by = headY + at, bz = m.z;
			double kx = bx + Math.cos(angle) * len * 0.55, kz = bz + Math.sin(angle) * len * 0.55;
			double ex = kx + Math.cos(angle) * len * 0.45, ey = by + len * 0.45, ez = kz + Math.sin(angle) * len * 0.45;
			fx.softBeam(bx, by, bz, kx, by, kz, 0.018 * s, Fx.withAlpha(c, a * 0.55f), Fx.withAlpha(c, a * 0.35f));
			fx.softBeam(kx, by, kz, ex, ey, ez, 0.018 * s, Fx.withAlpha(c, a * 0.35f), Fx.withAlpha(c, a * 0.08f));
			fx.light(kx, by, kz, 0.03 * s, Fx.withAlpha(c, a * 0.6f), 0.6);
			fx.light(ex, ey, ez, 0.035 * s, Fx.withAlpha(c, a * 0.3f), 0.4);
			double pulse = (time * 1.4 + m.seed) % 1.0;
			double px, py, pz;
			if (pulse < 0.55) {
				double t = pulse / 0.55;
				px = bx + (kx - bx) * t;
				py = by;
				pz = bz + (kz - bz) * t;
			} else {
				double t = (pulse - 0.55) / 0.45;
				px = kx + (ex - kx) * t;
				py = by + (ey - by) * t;
				pz = kz + (ez - kz) * t;
			}
			fx.light(px, py, pz, 0.07 * s, Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.4), a * 0.8f), 0.8);
		}
		fx.additive(false);
	}
	private void line(Fx fx, Mote m, double time, float a, double s) {
		double[] path = m.path;
		if (path == null || path.length < 6 || m.pathLength <= 0) {
			return;
		}
		int c = color(0xFF6FE7FF, m.phase);
		int points = path.length / 3;
		fx.additive(true);
		for (int i = 0; i + 1 < points; i++) {
			double ax = path[i * 3], ay = path[i * 3 + 1], az = path[i * 3 + 2];
			double bx = path[i * 3 + 3], by = path[i * 3 + 4], bz = path[i * 3 + 5];
			fx.softBeam(ax, ay, az, bx, by, bz, 0.14 * s, Fx.withAlpha(c, a * 0.09f), Fx.withAlpha(c, a * 0.09f));
			fx.softBeam(ax, ay, az, bx, by, bz, 0.026 * s, Fx.withAlpha(c, a * 0.5f), Fx.withAlpha(c, a * 0.5f));
		}
		for (int i = 0; i < points; i++) {
			boolean end = i == 0 || i == points - 1;
			fx.light(path[i * 3], path[i * 3 + 1], path[i * 3 + 2], (end ? 0.075 : 0.05) * s, Fx.withAlpha(c, a * (end ? 0.7f : 0.5f)), 0.5);
		}
		double tail = Math.min(m.pathLength, 0.9 + m.pathLength * 0.25);
		double gap = 0.6 + (m.seed % 1) * 2.4;
		double cycle = m.pathLength + tail + gap * pulseSpeed.get() / 5;
		double head = (time * pulseSpeed.get() + m.seed * 7.3) % cycle;
		if (head - tail > m.pathLength) {
			fx.additive(false);
			return;
		}
		int samples = 20;
		double from = Math.max(0, head - tail), to = Math.min(m.pathLength, head);
		if (to - from > 1e-3) {
			double[] pts = new double[samples * 3];
			double[] widths = new double[samples];
			double[] halo = new double[samples];
			int[] cols = new int[samples];
			int[] haloCols = new int[samples];
			double[] at = new double[3];
			for (int i = 0; i < samples; i++) {
				double d = from + (to - from) * i / (samples - 1);
				pointAt(m, d, at);
				pts[i * 3] = at[0];
				pts[i * 3 + 1] = at[1];
				pts[i * 3 + 2] = at[2];
				float k = (float) Math.max(0, 1 - (head - d) / tail);
				widths[i] = (0.03 + 0.06 * k) * s;
				halo[i] = (0.1 + 0.2 * k) * s;
				cols[i] = Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.4 * k), a * k * k);
				haloCols[i] = Fx.withAlpha(c, a * 0.25f * k * k);
			}
			fx.ribbon(pts, samples, halo, haloCols, 0);
			fx.ribbon(pts, samples, widths, cols, 0.8);
		}
		if (head >= 0 && head <= m.pathLength) {
			double[] h = new double[3];
			double[] back = new double[3];
			pointAt(m, head, h);
			pointAt(m, Math.max(0, head - 0.05), back);
			int hot = Fx.lerp(c, 0xFFFFFFFF, 0.5);
			fx.light(h[0], h[1], h[2], 0.2 * s, Fx.withAlpha(hot, a * 0.7f), 0.9);
			fx.streak(h[0], h[1], h[2], h[0] - back[0], h[1] - back[1], h[2] - back[2], 0.55 * s, 0.05 * s, Fx.withAlpha(hot, a * 0.6f));
			fx.additive(false);
			bloomLight(fx, h[0], h[1], h[2], 0.22 * s, Fx.withAlpha(c, a * 0.8f));
			return;
		}
		fx.additive(false);
	}
	private static void pointAt(Mote m, double dist, double[] out) {
		double[] p = m.path;
		double left = dist;
		for (int i = 0; i + 1 < p.length / 3; i++) {
			double dx = p[i * 3 + 3] - p[i * 3], dy = p[i * 3 + 4] - p[i * 3 + 1], dz = p[i * 3 + 5] - p[i * 3 + 2];
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (left <= len || i + 2 == p.length / 3) {
				double f = len <= 0 ? 0 : Math.min(1, left / len);
				out[0] = p[i * 3] + dx * f;
				out[1] = p[i * 3 + 1] + dy * f;
				out[2] = p[i * 3 + 2] + dz * f;
				return;
			}
			left -= len;
		}
	}
	private double[] linePath(double x, double y, double z, ThreadLocalRandom rand) {
		Minecraft mc = Minecraft.getInstance();
		int turns = switch (lineShape.get()) {
			case "Straight" -> 0;
			case "Circuit" -> 2 + rand.nextInt(2);
			default -> 1;
		};
		double total = lineLength.get() * rand.nextDouble(0.6, 1.4);
		List<Double> out = new ArrayList<>();
		out.add(x);
		out.add(y);
		out.add(z);
		int lastAxis = -1;
		double left = total;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int leg = 0; leg <= turns && left > 0.2; leg++) {
			int axis;
			do {
				axis = rand.nextDouble() < 0.22 ? 1 : rand.nextBoolean() ? 0 : 2;
			} while (axis == lastAxis);
			lastAxis = axis;
			double dir = rand.nextBoolean() ? 1 : -1;
			double want = leg == turns ? left : left * rand.nextDouble(0.3, 0.6);
			double got = 0;
			while (got + 0.25 <= want) {
				double nx = x + (axis == 0 ? dir * (got + 0.25) : 0);
				double ny = y + (axis == 1 ? dir * (got + 0.25) : 0);
				double nz = z + (axis == 2 ? dir * (got + 0.25) : 0);
				cursor.set(nx, ny, nz);
				if (mc.level == null || !mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
					break;
				}
				got += 0.25;
			}
			if (got < 0.25) {
				continue;
			}
			x += axis == 0 ? dir * got : 0;
			y += axis == 1 ? dir * got : 0;
			z += axis == 2 ? dir * got : 0;
			out.add(x);
			out.add(y);
			out.add(z);
			left -= got;
		}
		if (out.size() < 6) {
			return null;
		}
		double[] path = new double[out.size()];
		for (int i = 0; i < path.length; i++) {
			path[i] = out.get(i);
		}
		return path;
	}
	private static double length(double[] path) {
		double total = 0;
		for (int i = 0; i + 1 < path.length / 3; i++) {
			double dx = path[i * 3 + 3] - path[i * 3], dy = path[i * 3 + 4] - path[i * 3 + 1], dz = path[i * 3 + 5] - path[i * 3 + 2];
			total += Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
		return total;
	}
	private static final double DASH_TIME = 0.22;
	private void dash(Mote m, double time, ThreadLocalRandom rand) {
		if (m.dashAt >= 0) {
			double u = (time - m.dashAt) / (DASH_TIME / speed.get());
			if (u >= 1) {
				m.x = m.toX;
				m.y = m.toY;
				m.z = m.toZ;
				m.dashAt = -1;
				m.arrived = time;
				m.nextDash = time + restTime.get() * rand.nextDouble(0.5, 1.5) / speed.get();
			} else {
				double e = 1 - Math.pow(1 - u, 4);
				m.x = m.fromX + (m.toX - m.fromX) * e;
				m.y = m.fromY + (m.toY - m.fromY) * e;
				m.z = m.fromZ + (m.toZ - m.fromZ) * e;
			}
			return;
		}
		if (m.nextDash == 0) {
			m.nextDash = time + rand.nextDouble(0.1, restTime.get() * 1.5 + 0.1);
		}
		if (time < m.nextDash) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int attempt = 0; attempt < 6; attempt++) {
			double yaw = rand.nextDouble(Math.PI * 2);
			double pitch = rand.nextDouble(-0.5, 0.5);
			double dx = Math.cos(yaw) * Math.cos(pitch), dy = Math.sin(pitch), dz = Math.sin(yaw) * Math.cos(pitch);
			double dist = dashDistance.get() * rand.nextDouble(0.6, 1.3);
			boolean clear = true;
			for (double d = 0.3; d <= dist && clear; d += 0.3) {
				cursor.set(m.x + dx * d, m.y + dy * d, m.z + dz * d);
				clear = mc.level != null && mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty();
			}
			if (!clear) {
				continue;
			}
			m.fromX = m.x;
			m.fromY = m.y;
			m.fromZ = m.z;
			m.toX = m.x + dx * dist;
			m.toY = m.y + dy * dist;
			m.toZ = m.z + dz * dist;
			m.lastDx = dx;
			m.lastDy = dy;
			m.lastDz = dz;
			m.dashAt = time;
			return;
		}
		m.nextDash = time + 0.4;
	}
	private void solidMaterial(Meshes mesh) {
		boolean glow = cubeMaterial.is("Glow");
		mesh.bloom(glow && bloom.get(), 1.2f);
		if (glow && bloom.get()) {
			Look.markGlow();
		}
		switch (cubeMaterial.get()) {
			case "Glass" -> {
				mesh.material(Meshes.Material.GLASS);
				mesh.emission(0.1f);
			}
			case "Metal" -> {
				mesh.material(Meshes.Material.METAL);
				mesh.emission(0.12f);
			}
			case "Matte" -> {
				mesh.material(Meshes.Material.MATTE);
				mesh.emission(0.15f);
			}
			default -> {
				mesh.material(Meshes.Material.NEON);
				mesh.emission(0.2f);
			}
		}
	}
	private int edgeColour(int c, float a) {
		return switch (cubeMaterial.get()) {
			case "Glow" -> Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.55), a * 0.9f);
			case "Glass" -> Fx.withAlpha(0xFFFFFFFF, a * 0.4f);
			case "Metal" -> Fx.withAlpha(0xFFFFFFFF, a * 0.18f);
			default -> 0;
		};
	}
	private static void boxEdges(Fx fx, double x, double y, double z, Quaternionf q, double hx, double hy, double hz, double width, int colour) {
		if (Fx.alpha(colour) < 2) {
			return;
		}
		double[][] corner = new double[8][];
		Vector3f v = new Vector3f();
		double grow = 1.015;
		for (int i = 0; i < 8; i++) {
			q.transform(v.set((float) (((i & 1) == 0 ? -hx : hx) * grow), (float) (((i & 2) == 0 ? -hy : hy) * grow),
					(float) (((i & 4) == 0 ? -hz : hz) * grow)));
			corner[i] = new double[] {x + v.x, y + v.y, z + v.z};
		}
		for (int i = 0; i < 8; i++) {
			for (int bit = 1; bit < 8; bit <<= 1) {
				if ((i & bit) == 0) {
					double[] a = corner[i], b = corner[i | bit];
					fx.softBeam(a[0], a[1], a[2], b[0], b[1], b[2], width, colour, colour);
				}
			}
		}
	}

	private void cube(Fx fx, Mote m, double time, float a, double s) {
		Meshes mesh = fx.mesh();
		int c = color(0xFFBFE9FF, m.phase);
		double half = 0.095 * s * (0.8 + (m.seed % 1) * 0.5);
		double u = m.dashAt < 0 ? -1 : Math.min(1, (time - m.dashAt) / (DASH_TIME / speed.get()));
		double since = m.dashAt < 0 ? time - m.arrived : 0;
		double bob = Math.sin(time * 1.6 + m.phase) * 0.04 * s;
		Quaternionf spin = new Quaternionf()
				.rotationY((float) (time * 0.5 + m.phase))
				.rotateX((float) (0.6 + Math.sin(time * 0.7 + m.phase) * 0.25));
		solidMaterial(mesh);
		int body = bodyColour(c, a);
		int edge = edgeColour(c, a);
		boolean detail = near(m.x, m.y, m.z, 14 * s);
		double cy = m.y + (u >= 0 ? 0 : bob);
		if (u >= 0) {
			double speedNow = 4 * Math.pow(1 - u, 3);
			double stretch = 1 + Math.min(2.2, speedNow * 0.9);
			Quaternionf along = Meshes.pointing(m.lastDx, m.lastDy, m.lastDz);
			double w = half / Math.sqrt(stretch);
			mesh.box(m.x, m.y, m.z, along, w, half * stretch, w, body);
			if (detail) {
				fx.additive(true);
				boxEdges(fx, m.x, m.y, m.z, along, w, half * stretch, w, 0.014 * s, edge);
				fx.additive(false);
			}
			if (cubeTrail.get()) {
				mesh.bloom(false, 1f);
				for (int k = 1; k <= 4; k++) {
					double back = Math.max(0, u - k * 0.08);
					double e = 1 - Math.pow(1 - back, 4);
					double tx = m.fromX + (m.toX - m.fromX) * e;
					double ty = m.fromY + (m.toY - m.fromY) * e;
					double tz = m.fromZ + (m.toZ - m.fromZ) * e;
					double h = half * (1 - k * 0.15);
					mesh.box(tx, ty, tz, along, h, h, h, Fx.withAlpha(c, a * (0.5f - k * 0.1f)));
				}
				fx.additive(true);
				fx.softBeam(m.fromX, m.fromY, m.fromZ, m.x, m.y, m.z, half * 2.2,
						Fx.withAlpha(c, 0), Fx.withAlpha(c, a * 0.35f * (float) (1 - u)));
				fx.softBeam(m.fromX, m.fromY, m.fromZ, m.x, m.y, m.z, half * 0.6,
						Fx.withAlpha(c, 0), Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.5), a * 0.6f * (float) (1 - u)));
				fx.additive(false);
			}
		} else {
			double settle = since < 0.5 ? Math.sin(since * 22) * Math.exp(-since * 7) * 0.5 : 0;
			spin.rotateZ((float) settle);
			mesh.box(m.x, cy, m.z, spin, half, half, half, body);
			if (detail) {
				fx.additive(true);
				boxEdges(fx, m.x, cy, m.z, spin, half, half, half, 0.014 * s, edge);
				if (since < 0.4) {
					float k = (float) (1 - since / 0.4);
					fx.softRing(m.x, cy, m.z, half * (1.6 + since * 9), Fx.withAlpha(c, a * 0.6f * k * k), 0.5);
				}
				fx.additive(false);
			}
		}
		mesh.bloom(false, 1f);
		if (cubeMaterial.is("Glow")) {
			fx.additive(true);
			fx.light(m.x, cy, m.z, half * 4.5, Fx.withAlpha(c, a * 0.3f), 0);
			fx.additive(false);
		}
	}
	private int bodyColour(int c, float a) {
		if (cubeMaterial.is("Glass")) {
			return Fx.withAlpha(c, a * 0.55f);
		}
		return Fx.withAlpha(c, cubeMaterial.is("Glow") ? Math.min(255, a * 1.5f) : a);
	}
	private void ring(Fx fx, Mote m, double time, float a, double s) {
		int c = color(0xFFB08CFF, m.phase);
		double radius = (0.35 + (m.seed % 1) * 0.35) * s;
		Quaternionf q = new Quaternionf()
				.rotationY((float) (m.phase + time * 0.12))
				.rotateX((float) (0.5 + Math.sin(m.seed) * 0.6));
		Vector3f v = new Vector3f();
		int segments = 48;
		double[] rail = new double[(segments + 1) * 3];
		for (int i = 0; i <= segments; i++) {
			double ang = Math.PI * 2 * i / segments;
			q.transform(v.set((float) (Math.cos(ang) * radius), 0, (float) (Math.sin(ang) * radius)));
			rail[i * 3] = m.x + v.x;
			rail[i * 3 + 1] = m.y + v.y;
			rail[i * 3 + 2] = m.z + v.z;
		}
		fx.additive(true);
		fx.ribbon(rail, segments + 1, 0.12 * s, 0.12 * s, Fx.withAlpha(c, a * 0.08f), Fx.withAlpha(c, a * 0.08f), 0);
		fx.ribbon(rail, segments + 1, 0.028 * s, 0.028 * s, Fx.withAlpha(c, a * 0.3f), Fx.withAlpha(c, a * 0.3f), 0);
		double head = (time * pulseSpeed.get() / (Math.PI * 2 * radius / s) + m.seed) % 1.0;
		int arcPoints = 24;
		double[] arc = new double[arcPoints * 3];
		double[] widths = new double[arcPoints];
		double[] halo = new double[arcPoints];
		int[] cols = new int[arcPoints];
		int[] haloCols = new int[arcPoints];
		for (int i = 0; i < arcPoints; i++) {
			double f = (double) i / (arcPoints - 1);
			double ang = (head - 0.33 * (1 - f)) * Math.PI * 2;
			q.transform(v.set((float) (Math.cos(ang) * radius), 0, (float) (Math.sin(ang) * radius)));
			arc[i * 3] = m.x + v.x;
			arc[i * 3 + 1] = m.y + v.y;
			arc[i * 3 + 2] = m.z + v.z;
			float k = (float) (f * f);
			widths[i] = (0.03 + 0.05 * k) * s;
			halo[i] = (0.1 + 0.16 * k) * s;
			cols[i] = Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.4 * k), a * k);
			haloCols[i] = Fx.withAlpha(c, a * 0.22f * k);
		}
		fx.ribbon(arc, arcPoints, halo, haloCols, 0);
		fx.ribbon(arc, arcPoints, widths, cols, 0.8);
		double hx = arc[(arcPoints - 1) * 3], hy = arc[(arcPoints - 1) * 3 + 1], hz = arc[(arcPoints - 1) * 3 + 2];
		int hot = Fx.lerp(c, 0xFFFFFFFF, 0.5);
		fx.light(hx, hy, hz, 0.16 * s, Fx.withAlpha(hot, a * 0.8f), 0.9);
		fx.additive(false);
		bloomLight(fx, hx, hy, hz, 0.2 * s, Fx.withAlpha(c, a * 0.75f));
	}
	private static final double TURN_TIME = 0.35;
	private void turn(Mote m, double time, ThreadLocalRandom rand) {
		if (m.dashAt >= 0) {
			if ((time - m.dashAt) / (TURN_TIME / speed.get()) >= 1) {
				m.turnFrom = m.turnTo;
				m.dashAt = -1;
				m.arrived = time;
				m.nextDash = time + restTime.get() * rand.nextDouble(0.8, 2.2) / speed.get();
			}
			return;
		}
		if (m.nextDash == 0) {
			m.nextDash = time + rand.nextDouble(0.1, restTime.get() * 2 + 0.1);
		}
		if (time >= m.nextDash) {
			m.turnTo = m.turnFrom + (rand.nextBoolean() ? 1 : -1) * Math.PI * 2 / 3;
			m.dashAt = time;
		}
	}
	private void prism(Fx fx, Mote m, double time, float a, double s) {
		Meshes mesh = fx.mesh();
		int c = color(0xFF9FF5D8, m.phase);
		double length = (0.45 + (m.seed % 1) * 0.25) * s;
		double turn = m.turnFrom;
		double wobble = 0;
		float flare = 0;
		if (m.dashAt >= 0) {
			double u = Math.min(1, (time - m.dashAt) / (TURN_TIME / speed.get()));
			double e = 1 - Math.pow(1 - u, 3) * Math.cos(u * Math.PI * 1.5);
			turn = m.turnFrom + (m.turnTo - m.turnFrom) * e;
			flare = (float) Math.sin(Math.PI * u);
		} else {
			double since = time - m.arrived;
			wobble = since < 0.6 ? Math.sin(since * 18) * Math.exp(-since * 6) * 0.08 : 0;
			flare = since < 0.3 ? (float) (1 - since / 0.3) * 0.4f : 0;
		}
		double bob = Math.sin(time * 1.2 + m.phase) * 0.035 * s;
		double y = m.y + bob;
		Quaternionf q = new Quaternionf()
				.rotationY((float) (m.phase + turn))
				.rotateZ((float) (0.25 * Math.sin(m.seed) + wobble));
		solidMaterial(mesh);
		double[][] corners = prismCorners(m.x, y, m.z, q, length, length * 0.24);
		prismMesh(mesh, corners, m.x, y, m.z, bodyColour(c, a));
		mesh.bloom(false, 1f);
		fx.additive(true);
		if (near(m.x, y, m.z, 14 * s)) {
			int edge = edgeColour(c, a * (1 + flare * 0.6f));
			if (Fx.alpha(edge) > 1) {
				for (int k = 0; k < 3; k++) {
					double[] bot = corners[k], top = corners[3 + k], nextBot = corners[(k + 1) % 3], nextTop = corners[3 + (k + 1) % 3];
					fx.softBeam(bot[0], bot[1], bot[2], top[0], top[1], top[2], 0.012 * s, edge, edge);
					fx.softBeam(bot[0], bot[1], bot[2], nextBot[0], nextBot[1], nextBot[2], 0.01 * s, edge, edge);
					fx.softBeam(top[0], top[1], top[2], nextTop[0], nextTop[1], nextTop[2], 0.01 * s, edge, edge);
					fx.softBeam(bot[0], bot[1], bot[2], corners[6][0], corners[6][1], corners[6][2], 0.01 * s, edge, edge);
					fx.softBeam(top[0], top[1], top[2], corners[7][0], corners[7][1], corners[7][2], 0.01 * s, edge, edge);
				}
			}
		}
		if (cubeMaterial.is("Glow")) {
			fx.light(m.x, y, m.z, length * 1.6, Fx.withAlpha(c, a * 0.28f), 0);
		}
		if (flare > 0.02f) {
			Vec3 right = fx.cameraRight();
			int[] spectrum = {0xFFFF5A6A, 0xFF7CFF8A, 0xFF6A8CFF};
			for (int k = 0; k < 3; k++) {
				double off = (k - 1) * 0.06 * s;
				fx.sparkle(m.x + right.x * off, y + right.y * off, m.z + right.z * off, length * 0.55 * flare,
						Fx.withAlpha(spectrum[k], a * 0.6f * flare), m.phase + k * 0.3);
			}
		}
		fx.additive(false);
	}
	private static double[][] prismCorners(double x, double y, double z, Quaternionf q, double length, double radius) {
		double[][] out = new double[8][];
		Vector3f v = new Vector3f();
		double h = length * 0.3;
		for (int k = 0; k < 6; k++) {
			double ang = Math.PI * 2 * (k % 3) / 3;
			q.transform(v.set((float) (Math.cos(ang) * radius), (float) (k < 3 ? -h : h), (float) (Math.sin(ang) * radius)));
			out[k] = new double[] {x + v.x, y + v.y, z + v.z};
		}
		q.transform(v.set(0, (float) (-length * 0.5), 0));
		out[6] = new double[] {x + v.x, y + v.y, z + v.z};
		q.transform(v.set(0, (float) (length * 0.5), 0));
		out[7] = new double[] {x + v.x, y + v.y, z + v.z};
		return out;
	}
	private static void prismMesh(Meshes mesh, double[][] p, double cx, double cy, double cz, int colour) {
		for (int k = 0; k < 3; k++) {
			int n = (k + 1) % 3;
			face(mesh, p[k], p[n], p[3 + n], p[3 + k], cx, cy, cz, colour);
			face(mesh, p[3 + k], p[3 + n], p[7], p[7], cx, cy, cz, colour);
			face(mesh, p[n], p[k], p[6], p[6], cx, cy, cz, colour);
		}
	}
	private static void face(Meshes mesh, double[] a, double[] b, double[] c, double[] d, double cx, double cy, double cz, int colour) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		double mx = (a[0] + b[0] + c[0]) / 3 - cx, my = (a[1] + b[1] + c[1]) / 3 - cy, mz = (a[2] + b[2] + c[2]) / 3 - cz;
		if (nx * mx + ny * my + nz * mz < 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		mesh.quad(new double[] {a[0], a[1], a[2], nx, ny, nz}, new double[] {b[0], b[1], b[2], nx, ny, nz},
				new double[] {c[0], c[1], c[2], nx, ny, nz}, new double[] {d[0], d[1], d[2], nx, ny, nz}, colour, colour, colour, colour);
	}
	private void node(Fx fx, Mote m, double time, float a, double s) {
		int c = color(0xFF7FD8FF, m.phase);
		float breathe = (float) (0.7 + 0.3 * Math.sin(time * 1.4 + m.phase));
		fx.additive(true);
		fx.light(m.x, m.y, m.z, 0.14 * s, Fx.withAlpha(c, a * 0.45f * breathe), 0);
		fx.light(m.x, m.y, m.z, 0.045 * s, Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.5), a * breathe), 1);
		fx.additive(false);
		bloomLight(fx, m.x, m.y, m.z, 0.09 * s, Fx.withAlpha(c, a * 0.5f * breathe));
	}
	private void links(Fx fx, List<Mote> nodes, float[] alphas, double time) {
		double range = linkRange.get();
		double s = size.get();
		int n = nodes.size();
		double[] cutoff = new double[n];
		double[] nearest = new double[4];
		for (int i = 0; i < n; i++) {
			if (alphas[i] <= 1) {
				continue;
			}
			Mote a = nodes.get(i);
			java.util.Arrays.fill(nearest, range);
			for (int j = 0; j < n; j++) {
				if (j == i || alphas[j] <= 1) {
					continue;
				}
				Mote b = nodes.get(j);
				double d = Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z));
				for (int k = 0; k < nearest.length; k++) {
					if (d < nearest[k]) {
						System.arraycopy(nearest, k, nearest, k + 1, nearest.length - k - 1);
						nearest[k] = d;
						break;
					}
				}
			}
			cutoff[i] = nearest[nearest.length - 1];
		}
		double margin = Math.max(0.3, range * 0.15);
		fx.additive(true);
		for (int i = 0; i < n; i++) {
			if (alphas[i] <= 1) {
				continue;
			}
			Mote a = nodes.get(i);
			for (int j = i + 1; j < n; j++) {
				if (alphas[j] <= 1) {
					continue;
				}
				Mote b = nodes.get(j);
				double d = Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z));
				if (d >= range || d < 1e-4) {
					continue;
				}
				double pick = Math.max(Math.min(1, (cutoff[i] - d) / margin), Math.min(1, (cutoff[j] - d) / margin));
				if (pick <= 0) {
					continue;
				}
				float fade = (float) (smooth(pick) * Math.pow(1 - d / range, 1.5) * Math.min(alphas[i], alphas[j]) / 255f);
				if (fade < 0.01f) {
					continue;
				}
				int c = color(0xFF7FD8FF, a.phase);
				fx.softBeam(a.x, a.y, a.z, b.x, b.y, b.z, 0.09 * s, Fx.withAlpha(c, 255 * fade * 0.16f), Fx.withAlpha(c, 255 * fade * 0.16f));
				fx.softBeam(a.x, a.y, a.z, b.x, b.y, b.z, 0.018 * s, Fx.withAlpha(c, 255 * fade * 0.8f), Fx.withAlpha(c, 255 * fade * 0.8f));
				double cycle = d / pulseSpeed.get() + 2.5;
				double at = ((time + (a.seed + b.seed) * 0.37) % cycle) * pulseSpeed.get() / d;
				if (at <= 1) {
					double px = a.x + (b.x - a.x) * at, py = a.y + (b.y - a.y) * at, pz = a.z + (b.z - a.z) * at;
					int hot = Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.5), 255 * fade);
					fx.light(px, py, pz, 0.08 * s, hot, 0.8);
					fx.streak(px, py, pz, b.x - a.x, b.y - a.y, b.z - a.z, 0.35 * s, 0.04 * s, Fx.withAlpha(c, 255 * fade * 0.7f));
				}
			}
		}
		fx.additive(false);
	}
	private void pillar(Fx fx, Mote m, double time, float a, double s) {
		int c = color(0xFFFFD27A, m.phase);
		double height = m.pathLength;
		double width = (0.12 + (m.seed % 1) * 0.08) * s;
		int hot = Fx.lerp(c, 0xFFFFFFFF, 0.5);
		fx.additive(true);
		fx.softBeam(m.x, m.y, m.z, m.x, m.y + height, m.z, width * 2.6, Fx.withAlpha(c, a * 0.16f), Fx.withAlpha(c, 0));
		fx.softBeam(m.x, m.y, m.z, m.x, m.y + height, m.z, width, Fx.withAlpha(c, a * 0.5f), Fx.withAlpha(c, 0));
		fx.softBeam(m.x, m.y, m.z, m.x, m.y + height * 0.65, m.z, width * 0.3, Fx.withAlpha(hot, a * 0.65f), Fx.withAlpha(c, 0));
		double scan = ((time * pulseSpeed.get() * 0.35 + m.seed) % (height + 1.2)) - 0.3;
		if (scan > 0 && scan < height) {
			float k = (float) (1 - scan / height);
			double by = m.y + scan;
			fx.softBeam(m.x, by - 0.2 * s, m.z, m.x, by + 0.2 * s, m.z, width * 1.7, Fx.withAlpha(c, 0), Fx.withAlpha(hot, a * 0.85f * k));
			fx.softRing(m.x, by, m.z, AXIS_X, AXIS_Z, width * 2.4, Fx.withAlpha(hot, a * 0.6f * k), 0.5);
		}
		boolean detail = near(m.x, m.y, m.z, 18 * s);
		if (detail) {
			for (int i = 0; i < 4; i++) {
				double f = (time * 0.22 + (m.seed % 1) + i / 4.0) % 1.0;
				double ang = f * 7 + i * 1.6 + m.phase;
				double rr = width * 0.45;
				float k = (float) (Math.sin(Math.PI * f) * (1 - f));
				fx.light(m.x + Math.cos(ang) * rr, m.y + f * height * 0.9, m.z + Math.sin(ang) * rr, 0.045 * s,
						Fx.withAlpha(hot, a * 0.9f * k), 0.8);
			}
		}
		double ground = m.y + 0.03;
		fx.softPool(m.x, ground, m.z, AXIS_X, AXIS_Z, width * 4, Fx.withAlpha(c, a * 0.4f));
		if (detail) {
			double rf = (time * 0.42 + m.seed) % 1.0;
			float k = (float) ((1 - rf) * (1 - rf));
			fx.softRing(m.x, ground, m.z, AXIS_X, AXIS_Z, width * (1.6 + rf * 6), Fx.withAlpha(c, a * 0.45f * k), 0.3);
		}
		fx.additive(false);
		bloomLight(fx, m.x, m.y + height * 0.25, m.z, width * 1.4, Fx.withAlpha(c, a * 0.45f));
	}
	private Mote spawn(String effect, Vec3 p, double r, boolean anyAge) {
		Minecraft mc = Minecraft.getInstance();
		ThreadLocalRandom rand = ThreadLocalRandom.current();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		double clear = Math.min(clearRadius.get(), Math.max(0.5, r - 0.01));
		for (int attempt = 0; attempt < 8; attempt++) {
			double yaw = rand.nextDouble(Math.PI * 2);
			double pitch = Math.acos(rand.nextDouble(-1, 1));
			double d = Math.cbrt(rand.nextDouble(Math.pow(Math.max(0.5, clear) / r, 3), 1)) * r;
			double x = p.x + Math.sin(pitch) * Math.cos(yaw) * d;
			boolean shape = !(effect.equals(FIREFLIES) || effect.equals(EMBERS) || effect.equals(SNOW) || effect.equals(STARDUST)
					|| effect.equals(MATRIX));
			double flat = shape ? 0.35 : 0.7;
			double y = p.y + Math.cos(pitch) * d * flat;
			double z = p.z + Math.sin(pitch) * Math.sin(yaw) * d;
			cursor.set(x, y, z);
			if (mc.level == null || !mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
				continue;
			}
			double phase = rand.nextDouble(Math.PI * 2);
			double seed = rand.nextDouble(1000);
			if (effect.equals(LINES)) {
				double[] path = linePath(x, y, z, rand);
				if (path == null) {
					continue;
				}
				Mote line = new Mote(x, y, z, 0, 0, 0, phase, rand.nextDouble(10, 22), anyAge ? rand.nextDouble(10) : 0, seed);
				line.path = path;
				line.pathLength = length(path);
				return line;
			}
			if (effect.equals(PILLARS)) {
				double ground = Double.NaN;
				for (int down = 0; down < 8; down++) {
					cursor.set(x, y - down - 1, z);
					if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
						ground = Math.floor(y - down);
						break;
					}
				}
				if (Double.isNaN(ground)) {
					continue;
				}
				Mote pillar = new Mote(x, ground, z, 0, 0, 0, phase, rand.nextDouble(9, 18), anyAge ? rand.nextDouble(9) : 0, seed);
				pillar.pathLength = rand.nextDouble(2.2, 4.5) * size.get();
				return pillar;
			}
			Mote m = switch (effect) {
				case EMBERS -> new Mote(x, y, z, 0, rand.nextDouble(0.4, 1.1), 0, phase, rand.nextDouble(3, 6), anyAge ? rand.nextDouble(3) : 0, seed);
				case SNOW -> new Mote(x, y, z, 0, -rand.nextDouble(0.3, 0.8), 0, phase, rand.nextDouble(6, 12), anyAge ? rand.nextDouble(6) : 0, seed);
				case STARDUST -> new Mote(x, y, z, 0, rand.nextDouble(-0.02, 0.02), 0, phase, rand.nextDouble(5, 10), anyAge ? rand.nextDouble(5) : 0, seed);
				case MATRIX -> new Mote(x, y, z, 0, -rand.nextDouble(1.6, 3.4), 0, phase, rand.nextDouble(4, 9), anyAge ? rand.nextDouble(4) : 0, seed);
				case CUBES, PRISMS -> new Mote(x, y, z, 0, 0, 0, phase, rand.nextDouble(12, 26), anyAge ? rand.nextDouble(12) : 0, seed);
				case RINGS, NETWORK -> new Mote(x, y, z, 0, 0, 0, phase, rand.nextDouble(12, 24), anyAge ? rand.nextDouble(12) : 0, seed);
				default -> new Mote(x, y, z, 0, 0, 0, phase, rand.nextDouble(6, 12), anyAge ? rand.nextDouble(6) : 0, seed);
			};
			m.fall = m.vy;
			return m;
		}
		return null;
	}
	@Override
	protected void onDisable() {
		pools.clear();
	}
}
