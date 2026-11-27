package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class StormEffects extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private static final int MAX = 10;
	private static final double STROKE_TAU = 0.055;
	private static volatile StormEffects instance;
	private final ModeSetting style;
	private final NumberSetting size;
	private final NumberSetting branches;
	private final NumberSetting restrikes;
	private final NumberSetting duration;
	private final BoolSetting skyFlash;
	private final NumberSetting flashStrength;
	private final BoolSetting scorch;
	private final BoolSetting sparks;
	private final ModeSetting colour;
	private final BoolSetting skyLightning;
	private final NumberSetting skyRate;
	private final BoolSetting skyStormOnly;
	private final Look look;
	private final Set<Integer> seen = new HashSet<>();
	private final List<Strike> strikes = new ArrayList<>();
	private ClientLevel level;
	private record Seg(double ax, double ay, double az, double bx, double by, double bz, double arrive, int depth) {
	}
	private static final class Strike {
		final double x, y, z;
		final long born;
		final List<Seg> segs = new ArrayList<>();
		double ground;
		double[] strokes;
		double[] gains;
		final long seed;
		double topX, topY, topZ;
		/** A cloud-to-cloud bolt in the sky: no ground strike, scorch or sparks. */
		boolean air;
		Strike(double x, double y, double z, long born, long seed) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.born = born;
			this.seed = seed;
		}
		double lastStroke() {
			return strokes[strokes.length - 1];
		}
	}
	static {
		nz.temple.client.util.VanillaFx.boltHidden = StormEffects::hidesVanillaBolt;
	}
	public StormEffects() {
		super("Storm Effects", Category.COSMETICS, "Replaces lightning with a realistic strike: a branching bolt that builds down from the "
				+ "sky, flashes, re-strikes and fades, lighting up the sky and scorching the ground.");
		style = add(new ModeSetting("Style", List.of("Realistic", "Column", "Spear"), "Realistic")
				.describe("Realistic is a jagged, branching bolt. Column is a straight, thick channel of light. Spear is a straight bolt "
						+ "ending in a crystal driven into the ground."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Thickness and brightness of the bolt."));
		branches = add(new NumberSetting("Branches", 7, 0, 16, 1, "").describe("Number of branches off the main bolt.")
				.visibleWhen(() -> style.is("Realistic")));
		restrikes = add(new NumberSetting("Re-strikes", 3, 0, 6, 1, "")
				.describe("Up to this many extra flashes down the same channel after the first stroke, as real lightning does."));
		duration = add(new NumberSetting("Scorch time", 4, 1, 12, 0.5, " s").describe("How long the scorch mark and smoke last."));
		group();
		skyFlash = add(new BoolSetting("Sky flash", true).describe("Briefly brightens the whole screen with each stroke."));
		flashStrength = add(new NumberSetting("Flash strength", 50, 5, 100, 5, "%").describe("How bright the sky flash is.")
				.visibleWhen(skyFlash::get));
		scorch = add(new BoolSetting("Scorch", true).describe("Leaves a smoking scorch mark with glowing embers where the bolt lands."));
		sparks = add(new BoolSetting("Sparks", true).describe("Throws sparks from the impact point."));
		colour = add(new ModeSetting("Colour", List.of("Violet", "Blue", "Custom"), "Violet")
				.describe("Violet is a white core with a violet glow. Blue is a white core with a blue glow. Custom uses the colour "
						+ "settings below."));
		group();
		skyLightning = add(new BoolSetting("Sky lightning", true)
				.describe("Decorative bolts that branch across the clouds in the distance, with a soft flash."));
		skyRate = add(new NumberSetting("Sky lightning rate", 6, 1, 30, 1, "/min").describe("About how many sky bolts a minute.")
				.visibleWhen(skyLightning::get));
		skyStormOnly = add(new BoolSetting("Only in storms", true).describe("Only while it rains or thunders. Off: any time.")
				.visibleWhen(skyLightning::get));
		look = new Look(x -> add(x), "Light");
		on(TickEvent.class, e -> tick());
		instance = this;
	}
	public static boolean hidesVanillaBolt() {
		StormEffects m = instance;
		return m != null && m.isEnabled();
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			seen.clear();
			strikes.clear();
			return;
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof LightningBolt) || !seen.add(entity.getId())) {
				continue;
			}
			if (entity.distanceToSqr(mc.player) > 400 * 400) {
				continue;
			}
			add(entity.getX(), entity.getY(), entity.getZ(), entity.getId() * 2654435761L + System.nanoTime());
		}
		seen.removeIf(id -> mc.level.getEntity(id) == null);
		boolean stormy = mc.level.getRainLevel(1) > 0.2f || mc.level.getThunderLevel(1) > 0.2f;
		if (skyLightning.get() && (stormy || !skyStormOnly.get()) && Math.random() < skyRate.get() / 1200.0) {
			double a = Math.random() * Math.PI * 2;
			double d = 90 + Math.random() * 70;
			double y = Math.max(mc.player.getY() + 60, 150) + Math.random() * 40;
			if (strikes.size() >= MAX) {
				strikes.remove(0);
			}
			strikes.add(buildAir(mc.player.getX() + Math.cos(a) * d, y, mc.player.getZ() + Math.sin(a) * d, System.nanoTime()));
		}
	}

	/** A bolt that branches across the sky from (x, y, z), mostly sideways and a little down. */
	private Strike buildAir(double x, double y, double z, long seed) {
		Random r = new Random(seed);
		double dir = r.nextDouble() * Math.PI * 2;
		double len = 35 + r.nextDouble() * 50;
		double ex = x + Math.cos(dir) * len;
		double ey = y - 8 - r.nextDouble() * 30;
		double ez = z + Math.sin(dir) * len;
		Strike st = new Strike(ex, ey, ez, System.nanoTime(), seed);
		st.air = true;
		st.topX = x;
		st.topY = y;
		st.topZ = z;
		List<double[]> main = new ArrayList<>();
		main.add(new double[] {x, y, z});
		main.add(new double[] {ex, ey, ez});
		main = fractal(main, 0.22, 7, r);
		double leaderTime = 0.09 + r.nextDouble() * 0.06;
		double speed = length(main) / leaderTime;
		double walked = 0;
		double[] arrive = new double[main.size()];
		for (int i = 0; i < main.size() - 1; i++) {
			double[] a = main.get(i), b = main.get(i + 1);
			arrive[i] = walked / speed;
			st.segs.add(new Seg(a[0], a[1], a[2], b[0], b[1], b[2], arrive[i], 0));
			walked += dist(a, b);
		}
		st.ground = leaderTime;
		int count = 3 + (int) Math.round(branches.get() * 0.8);
		for (int k = 0; k < count; k++) {
			double u = 0.05 + r.nextDouble() * 0.85;
			int i = Math.min(main.size() - 2, (int) (u * (main.size() - 1)));
			branch(st, main.get(i), main.get(i + 1), arrive[i], speed * 0.8, (6 + r.nextDouble() * 18) * (1 - u * 0.4), 1, r);
		}
		int n = 1 + r.nextInt(3);
		st.strokes = new double[n];
		st.gains = new double[n];
		double t = st.ground;
		for (int k = 0; k < n; k++) {
			st.strokes[k] = t;
			st.gains[k] = k == 0 ? 1.1 : 0.5 + r.nextDouble() * 0.4;
			t += 0.05 + r.nextDouble() * 0.12;
		}
		return st;
	}
	@Override
	public void preview(Vec3 at) {
		level = Minecraft.getInstance().level;
		add(at.x, at.y, at.z, System.nanoTime());
	}

	private void add(double x, double y, double z, long seed) {
		if (strikes.size() >= MAX) {
			strikes.remove(0);
		}
		strikes.add(build(x, y, z, seed));
	}
	private Strike build(double x, double y, double z, long seed) {
		Random r = new Random(seed);
		Strike st = new Strike(x, y, z, System.nanoTime(), seed);
		double height = 60 + r.nextDouble() * 28;
		double lean = 4 + r.nextDouble() * 9;
		double leanAngle = r.nextDouble() * Math.PI * 2;
		st.topX = x + Math.cos(leanAngle) * lean;
		st.topY = y + height;
		st.topZ = z + Math.sin(leanAngle) * lean;
		boolean straight = !style.is("Realistic");
		List<double[]> main = new ArrayList<>();
		main.add(new double[] {st.topX, st.topY, st.topZ});
		main.add(new double[] {x, y, z});
		main = fractal(main, straight ? 0.03 : 0.2, straight ? 4 : 7, r);
		double leaderTime = 0.13 + r.nextDouble() * 0.08;
		double total = length(main);
		double speed = total / leaderTime;
		double walked = 0;
		double[] arrive = new double[main.size()];
		for (int i = 0; i < main.size() - 1; i++) {
			double[] a = main.get(i), b = main.get(i + 1);
			arrive[i] = walked / speed;
			st.segs.add(new Seg(a[0], a[1], a[2], b[0], b[1], b[2], arrive[i], 0));
			walked += dist(a, b);
		}
		st.ground = leaderTime;
		if (!straight) {
			int count = (int) Math.round(branches.get());
			for (int k = 0; k < count; k++) {
				double u = 0.06 + Math.pow(r.nextDouble(), 1.4) * 0.8;
				int i = Math.min(main.size() - 2, (int) (u * (main.size() - 1)));
				double reach = (10 + r.nextDouble() * 26) * (1 - u * 0.5);
				branch(st, main.get(i), main.get(i + 1), arrive[i], speed * 0.8, reach, 1, r);
			}
		}
		int extra = (int) Math.round(restrikes.get());
		int n = 1 + (extra == 0 ? 0 : 1 + r.nextInt(extra));
		n = Math.min(n, 1 + extra);
		st.strokes = new double[n];
		st.gains = new double[n];
		double t = st.ground;
		for (int k = 0; k < n; k++) {
			st.strokes[k] = t;
			st.gains[k] = k == 0 ? 1.25 : 0.6 + r.nextDouble() * 0.45;
			t += 0.045 + r.nextDouble() * 0.1;
		}
		return st;
	}
	private void branch(Strike st, double[] a, double[] b, double startTime, double speed, double reach, int depth, Random r) {
		double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
		double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double ang = r.nextDouble() * Math.PI * 2;
		double ox = Math.cos(ang), oz = Math.sin(ang);
		double fx = dx / dl * 0.35 + ox * 0.8, fy = dy / dl * 0.35 - 0.55 - r.nextDouble() * 0.3, fz = dz / dl * 0.35 + oz * 0.8;
		double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
		double[] end = {a[0] + fx / fl * reach, a[1] + fy / fl * reach, a[2] + fz / fl * reach};
		List<double[]> path = new ArrayList<>();
		path.add(a);
		path.add(end);
		path = fractal(path, 0.24, depth == 1 ? 5 : 4, r);
		double walked = 0;
		for (int i = 0; i < path.size() - 1; i++) {
			double[] p = path.get(i), q = path.get(i + 1);
			double at = startTime + walked / speed;
			st.segs.add(new Seg(p[0], p[1], p[2], q[0], q[1], q[2], at, depth));
			walked += dist(p, q);
			if (depth < 3 && i > 2 && r.nextDouble() < (depth == 1 ? 0.16 : 0.07)) {
				branch(st, p, q, at, speed * 0.85, reach * (0.3 + r.nextDouble() * 0.35), depth + 1, r);
			}
		}
	}
	private static List<double[]> fractal(List<double[]> path, double rough, int passes, Random r) {
		for (int pass = 0; pass < passes; pass++) {
			List<double[]> next = new ArrayList<>(path.size() * 2);
			for (int i = 0; i < path.size() - 1; i++) {
				double[] a = path.get(i), b = path.get(i + 1);
				double len = dist(a, b);
				double[] m = {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
				double push = len * rough * (0.4 + r.nextDouble() * 0.9);
				double ang = r.nextDouble() * Math.PI * 2;
				m[0] += Math.cos(ang) * push;
				m[2] += Math.sin(ang) * push;
				m[1] += (r.nextDouble() - 0.5) * push * 0.6;
				next.add(a);
				next.add(m);
			}
			next.add(path.get(path.size() - 1));
			path = next;
		}
		return path;
	}
	private static double strokeLight(Strike st, double t) {
		double light = 0;
		for (int k = 0; k < st.strokes.length; k++) {
			double dt = t - st.strokes[k];
			if (dt >= 0) {
				light += st.gains[k] * Math.exp(-dt / STROKE_TAU);
			}
		}
		if (t >= st.ground) {
			double after = Math.max(0, t - st.lastStroke());
			light += 0.14 * Math.exp(-after / 0.18);
		}
		return Math.min(1.5, light);
	}
	private double life(Strike st) {
		if (st.air) {
			return st.lastStroke() + 0.9;
		}
		return Math.max(st.lastStroke() + 0.9, scorch.get() ? st.ground + duration.get() : 0);
	}

	private int core(double a) {
		int c = colour.is("Violet") ? 0xFFFBF6FF : colour.is("Blue") ? 0xFFF4F7FF : Fx.lerp(0xFFFFFFFF, look.color(0.0), 0.25);
		return Fx.withAlpha(c, (float) (255 * Math.max(0, Math.min(1, a)) * look.alpha()));
	}
	private int glow(double a) {
		int c = colour.is("Violet") ? 0xFFC6A2FF : colour.is("Blue") ? 0xFF9FC0FF : look.color(0.2);
		return Fx.withAlpha(c, (float) (255 * Math.max(0, Math.min(1, a)) * look.alpha()));
	}
	private int halo(double a) {
		int c = colour.is("Violet") ? 0xFF9A6CFF : colour.is("Blue") ? 0xFF8A78FF : look.color(0.6);
		return Fx.withAlpha(c, (float) (255 * Math.max(0, Math.min(1, a)) * look.alpha()));
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || level != mc.level) {
			strikes.clear();
			return;
		}
		long now = System.nanoTime();
		strikes.removeIf(st -> (now - st.born) / 1e9 > life(st));
		double s = size.get();
		double flash = skyFlash.get() ? flashLevel(mc, now) : 0;
		if (flash > 0.01) {
			int c = colour.is("Violet") ? 0xFFE2D6FF : colour.is("Blue") ? 0xFFD4DEFF : look.color(0.0);
			fx.screenFlash(Fx.withAlpha(c, (float) (255 * 0.5 * flashStrength.get() / 100 * flash)));
		}
		for (Strike st : strikes) {
			double t = (now - st.born) / 1e9;
			fx.additive(true);
			drawChannel(fx, st, t, s);
			if (!st.air) {
				drawLight(fx, st, t, s);
			} else if (t >= st.ground) {
				double light = Math.min(1, strokeLight(st, t));
				fx.glow((st.topX + st.x) / 2, (st.topY + st.y) / 2, (st.topZ + st.z) / 2, 40 * s, halo(light * 0.18));
			}
			fx.additive(false);
			if (t >= st.ground && !st.air) {
				if (sparks.get()) {
					drawSparks(fx, st, t - st.ground, s);
				}
				if (scorch.get()) {
					drawScorch(fx, st, t - st.ground, s);
				}
				if (style.is("Spear")) {
					drawSpear(fx, st, t - st.ground, s);
				}
			}
		}
	}
	private void drawChannel(Fx fx, Strike st, double t, double s) {
		double main = t < st.ground ? 0 : strokeLight(st, t);
		double branchLight = t < st.ground ? 0 : 0.9 * Math.exp(-(t - st.ground) / 0.075);
		boolean column = style.is("Column");
		double width = (column ? 2.6 : 1) * s;
		Look.markGlow();
		for (Seg g : st.segs) {
			double light;
			if (t < st.ground) {
				if (g.arrive() > t) {
					continue;
				}
				double tip = t - g.arrive() < 0.02 ? 0.35 + 0.35 * flicker(st.seed, g.arrive(), t) : 0;
				light = (0.14 + tip) * (g.depth() == 0 ? 1 : 0.7);
			} else {
				light = g.depth() == 0 ? main : branchLight / g.depth();
				if (g.depth() > 0) {
					light = Math.max(light, 0.1 * Math.exp(-(t - st.ground) / 0.08));
				}
			}
			if (light < 0.004) {
				continue;
			}
			double scale = g.depth() == 0 ? 1 : g.depth() == 1 ? 0.55 : 0.35;
			double w = width * scale;
			double bright = Math.min(1, light);
			fx.softBeam(g.ax(), g.ay(), g.az(), g.bx(), g.by(), g.bz(), (0.12 + 0.2 * bright) * w, core(bright), core(bright));
			fx.softBeam(g.ax(), g.ay(), g.az(), g.bx(), g.by(), g.bz(), 1.1 * w, glow(light * 0.6), glow(light * 0.6));
			if (g.depth() < 2 && light > 0.15) {
				fx.bloom(true);
				fx.softBeam(g.ax(), g.ay(), g.az(), g.bx(), g.by(), g.bz(), 4.0 * w, halo(light * 0.25), halo(light * 0.25));
				fx.bloom(false);
			}
		}
	}
	private void drawLight(Fx fx, Strike st, double t, double s) {
		if (t < st.ground) {
			return;
		}
		double light = Math.min(1, strokeLight(st, t));
		if (light < 0.01) {
			return;
		}
		int n = 8;
		for (int k = 0; k <= n; k++) {
			double f = (double) k / n;
			double x = st.topX + (st.x - st.topX) * f, y = st.topY + (st.y - st.topY) * f, z = st.topZ + (st.z - st.topZ) * f;
			fx.glow(x, y, z, 7 * s, glow(light * 0.12));
		}
		fx.glow(st.topX, st.topY, st.topZ, 30 * s, halo(light * 0.25));
		fx.softPool(st.x, st.y + 0.05, st.z, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 9 * s, glow(light * 0.5));
		fx.light(st.x, st.y + 0.4, st.z, 2.2 * s, core(light * 0.9), 0.8);
	}
	private void drawSparks(Fx fx, Strike st, double age, double s) {
		if (age > 1.2) {
			return;
		}
		Random r = new Random(st.seed ^ 0x5DEECE66DL);
		for (int i = 0; i < 34; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double out = (2 + r.nextDouble() * 7) * Math.sqrt(s);
			double up = (2 + r.nextDouble() * 6) * Math.sqrt(s);
			double life = 0.4 + r.nextDouble() * 0.7;
			if (age > life) {
				continue;
			}
			double vx = Math.cos(ang) * out, vz = Math.sin(ang) * out;
			double vy = up - 22 * age;
			double x = st.x + vx * age, y = Math.max(st.y + 0.02, st.y + 0.1 + up * age - 11 * age * age), z = st.z + vz * age;
			double fade = 1 - age / life;
			int c = Fx.lerp(0xFFFFF6E0, colour.is("Violet") ? 0xFFC6A2FF : colour.is("Blue") ? 0xFFFFB45A : look.color(0.3), age / life);
			fx.streak(x, y, z, vx, vy, vz, 0.12 + 0.25 * fade, 0.05 * s, Fx.withAlpha(c, (float) (255 * fade)));
		}
	}
	private void drawScorch(Fx fx, Strike st, double age, double s) {
		double total = duration.get();
		if (age > total) {
			return;
		}
		double fade = 1 - Math.pow(age / total, 2);
		Meshes mesh = fx.mesh();
		mesh.bloom(false, 0);
		mesh.material(Meshes.Material.MATTE);
		double heat = Math.exp(-age / 0.6);
		mesh.emission((float) (heat * 0.9));
		int colour = Fx.lerp(0xFF0D0A08, 0xFFFF8A3A, heat * 0.8);
		mesh.ellipsoid(st.x, st.y + 0.012, st.z, 1.1 * s, 0.006, 1.1 * s, null, Fx.withAlpha(colour, (float) (210 * fade)), 14);
		Random r = new Random(st.seed ^ 0x2545F491L);
		fx.additive(true);
		for (int i = 0; i < 9; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double d = r.nextDouble() * 0.9 * s;
			double life = 0.8 + r.nextDouble() * total * 0.5;
			if (age > life) {
				continue;
			}
			double flick = 0.6 + 0.4 * Math.sin(age * (7 + i) + i);
			double a = (1 - age / life) * flick;
			fx.glow(st.x + Math.cos(ang) * d, st.y + 0.05, st.z + Math.sin(ang) * d, 0.08 * s, Fx.withAlpha(0xFFFF7A2A, (float) (220 * a)));
		}
		fx.additive(false);
		for (int i = 0; i < 7; i++) {
			double start = i * 0.18;
			double k = age - start;
			double life = 2.2 + r.nextDouble() * 1.5;
			if (k < 0 || k > life) {
				continue;
			}
			double f = k / life;
			double rise = k * (0.6 + r.nextDouble() * 0.4);
			double drift = Math.sin(k * 0.8 + i) * 0.3 * f;
			double a = Math.sin(Math.PI * f) * 0.55;
			fx.smoke(st.x + drift, st.y + 0.3 + rise, st.z + Math.cos(k * 0.7 + i) * 0.3 * f, (0.35 + f * 0.9) * s,
					Fx.withAlpha(0xFF3A3836, (float) (255 * a * fade)), i * 1.7, k * 0.3);
		}
	}
	private void drawSpear(Fx fx, Strike st, double age, double s) {
		double total = Math.max(1.5, duration.get());
		if (age > total) {
			return;
		}
		Meshes mesh = fx.mesh();
		look.apply(mesh, (float) Math.min(1, strokeLight(st, st.ground + age)));
		double fade = 1 - Math.pow(age / total, 3);
		mesh.crystal(st.x, st.y + 1.0 * s, st.z, Meshes.pointing(0, -1, 0), 2.2 * s, 0.32 * s, glow(fade), 6);
	}
	private static double flicker(long seed, double arrive, double t) {
		double v = Math.sin(seed * 1e-6 + arrive * 917 + Math.floor(t * 120) * 3.7) * 43758.5453;
		return v - Math.floor(v);
	}

	private double flashLevel(Minecraft mc, long now) {
		double flash = 0;
		for (Strike st : strikes) {
			double t = (now - st.born) / 1e9;
			if (t < st.ground) {
				continue;
			}
			double d = Math.sqrt(mc.player.distanceToSqr(st.x, st.y, st.z));
			double near = Math.max(0.12, 1 - d / 260);
			flash = Math.max(flash, Math.min(1, strokeLight(st, t)) * near * (st.air ? 0.45 : 1));
		}
		return flash;
	}
	private static double dist(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	private static double length(List<double[]> path) {
		double l = 0;
		for (int i = 0; i < path.size() - 1; i++) {
			l += dist(path.get(i), path.get(i + 1));
		}
		return l;
	}
	@Override
	protected void onDisable() {
		strikes.clear();
		seen.clear();
	}
}
