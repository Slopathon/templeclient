package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Footsteps extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private final ModeSetting style;
	private final nz.temple.setting.BoolSetting realism;
	private final ModeSetting who;
	private final NumberSetting stride;
	private final NumberSetting size;
	private final NumberSetting lifetime;
	private final NumberSetting maxPrints;

	private final nz.temple.client.modules.cosmetic.Look look;
	private final ArrayDeque<Print> prints = new ArrayDeque<>();
	private final Map<Integer, double[]> last = new HashMap<>();
	private final Map<Integer, Boolean> foot = new HashMap<>();
	private ClientLevel level;

	private final BoolSetting impact;

	/** One footprint. {@code dust} is the colour of the block it was made on; {@code right} which foot made it. */
	private record Print(double x, double y, double z, float yaw, long born, double offset, int dust, boolean right) {
	}

	public Footsteps() {
		super("Footsteps", Category.COSMETICS, "Leaves marks where players walk: glowing prints, ripples, sparks, flames, frost, flowers and more.");
		style = add(new ModeSetting("Style", List.of("Glow", "Prints", "Ripples", "Sparks", "Flames", "Frost", "Blossom", "Petals", "Puddles"), "Glow")
				.describe("Glow outlines each shoe print in light that flashes as the foot lands, then settles and fades. Prints presses "
						+ "a dark shoe print into the ground. Ripples sends rings out across the ground like water. Sparks crackles "
						+ "small bolts of lightning out from each step. Flames burns briefly and leaves a scorch mark. Frost grows an "
						+ "ice crystal that melts. Blossom grows a flower that opens and wilts. Petals scatters falling petals. Puddles "
						+ "leaves small glass puddles."));
		realism = add(new nz.temple.setting.BoolSetting("Realism", true).internal());
		impact = add(new BoolSetting("Impact", true)
				.describe("Each step kicks up a puff of dust the colour of the ground, with a quick ring of light."));
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Self")
				.describe("Whose steps leave prints."));
		group();
		stride = add(new NumberSetting("Stride", 0.75, 0.3, 3, 0.05, " b").describe("Distance between prints."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Print size."));
		lifetime = add(new NumberSetting("Lifetime", 3, 0.5, 10, 0.5, " s").describe("How long prints last."));
		maxPrints = add(new NumberSetting("Max prints", 60, 10, 200, 10, "").describe("Maximum prints. The oldest are removed first."));
		group();
		look = new nz.temple.client.modules.cosmetic.Look(x -> add(x), "Light");
		on(TickEvent.class, e -> tick());
		legacy(new nz.temple.client.modules.legacy.LegacyFootsteps(), () -> false, List.of());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			prints.clear();
			last.clear();
			foot.clear();
		}
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) {
				continue;
			}
			if (!player.isAlive() || !player.onGround() || player.distanceToSqr(mc.player) > 64 * 64) {
				continue;
			}
			double[] previous = last.get(player.getId());
			if (previous == null) {
				last.put(player.getId(), new double[] {player.getX(), player.getZ()});
				continue;
			}
			double dx = player.getX() - previous[0];
			double dz = player.getZ() - previous[1];
			if (dx * dx + dz * dz < stride.get() * stride.get()) {
				continue;
			}
			previous[0] = player.getX();
			previous[1] = player.getZ();
			boolean right = foot.getOrDefault(player.getId(), false);
			foot.put(player.getId(), !right);
			float walked = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float yaw = walked * Mth.DEG_TO_RAD;
			double sideX = -Math.cos(yaw) * (right ? FOOT : -FOOT);
			double sideZ = -Math.sin(yaw) * (right ? FOOT : -FOOT);
			while (prints.size() >= maxPrints.get()) {
				prints.removeFirst();
			}
			double px = player.getX() + sideX;
			double pz = player.getZ() + sideZ;
			prints.add(new Print(px, player.getY() + 0.01, pz, walked, System.nanoTime(), (System.nanoTime() % 10_000) / 10_000.0,
					groundColour(mc.level, px, player.getY(), pz), right));
		}
		last.keySet().removeIf(id -> mc.level.getEntity(id) == null);
	}

	@Override
	public void preview(net.minecraft.world.phys.Vec3 at) {
		if (legacyActive()) {
			((nz.temple.client.modules.legacy.LegacyFootsteps) legacyModule()).preview(at);
			return;
		}
		level = Minecraft.getInstance().level;
		long now = System.nanoTime();
		int dust = groundColour(level, at.x, at.y, at.z);
		for (int i = 0; i < 8; i++) {
			boolean right = i % 2 == 0;
			double side = right ? FOOT : -FOOT;
			prints.add(new Print(at.x - side, at.y + 0.01, at.z - 2 + i * 0.55, 0, now - (8 - i) * 160_000_000L, i * 0.1, dust, right));
		}
	}

	public void emit(Fx fx, float partial) {
		if (legacyActive()) {
			((nz.temple.client.modules.legacy.LegacyFootsteps) legacyModule()).emit(fx, partial);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || level != mc.level) {
			prints.clear();
			return;
		}
		long now = System.nanoTime();
		double life = lifetime.get() * 1e9;
		prints.removeIf(p -> now - p.born() > life);
		nz.temple.client.render.Meshes mesh = fx.mesh();
		double s = size.get();
		double ageSec;
		for (Print p : prints) {
			ageSec = (now - p.born()) / 1e9;
			if (impact.get() && ageSec < 0.7) {
				impact(fx, p, ageSec, s);
			}
			double t = (now - p.born()) / life;
			double fade = t < 0.7 ? 1 : 1 - (t - 0.7) / 0.3;
			org.joml.Quaternionf facing = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-p.yaw()));
			switch (style.get()) {
				case "Puddles" -> {
					double grow = Math.min(1, t * 6);
					mesh.finish(nz.temple.cosmetic.Finishes.GLASS);
					int water = Fx.withAlpha(Fx.lerp(0xFF9DB8CC, look.color(p.offset()), 0.15), (float) (230 * fade));
					mesh.ellipsoid(p.x(), p.y(), p.z(), 0.16 * s * grow, 0.008, 0.22 * s * grow, facing, water, 12);
					double k = Math.min(1, t * 2.5);
					if (k < 1) {
						look.apply(mesh, 0.2f);
						mesh.torus(p.x(), p.y() + 0.01, p.z(), null, (0.1 + k * 0.35) * s, 0.012, 0, 28,
								(u, v) -> look.color(p.offset() + u * 0.3, 1 - k));
					}
				}
				case "Petals" -> {
					look.apply(mesh, 0.25f);
					for (int i = 0; i < 3; i++) {
						double fall = Math.max(0, 1 - t * 4);
						double gust = smooth(0.55 + i * 0.06, 1, t);
						double a = p.offset() * 40 + i * 2.1;
						double wind = gust * gust * 1.4 * s;
						double x = p.x() + Math.cos(a) * 0.12 * s + Math.sin(t * 12 + i) * 0.05 * fall + wind;
						double z = p.z() + Math.sin(a) * 0.12 * s + wind * 0.4 * Math.sin(i + p.offset() * 9);
						double y = p.y() + 0.01 + fall * 0.5 * s + gust * (0.35 + 0.25 * Math.sin(t * 9 + i)) * s;
						org.joml.Quaternionf spin = new org.joml.Quaternionf().rotationY((float) (a + t * 3 * fall + gust * t * 9))
								.rotateZ((float) (fall * Math.sin(t * 15 + i) * 0.8 + gust * Math.sin(t * 13 + i) * 1.2));
						mesh.blade(x, y, z, spin, 0.12 * s, 0.08 * s, 0.006, 0.01, look.color(p.offset() + i * 0.1, fade),
								look.color(p.offset() + i * 0.1 + 0.2, fade * 0.8));
					}
				}
				case "Flames" -> flames(fx, mesh, p, t, fade, s * 1.3);
				case "Frost" -> frost(fx, mesh, p, t, fade, s * 1.3, facing);
				case "Blossom" -> blossom(mesh, p, t, fade, s * 1.3);
				case "Prints" -> pressed(fx, p, t, fade, s);
				case "Ripples" -> ripples(fx, p, ageSec, fade, s);
				case "Sparks" -> sparks(fx, p, ageSec, fade, s);
				default -> glow(fx, p, ageSec, t, fade, s);
			}
		}
	}

	public static final double FOOT = 0.125;

	// ---------------------------------------------------------------- shared shapes

	/** The colour of the block under (x, y, z), lightened a little for dust; grey when unknown. */
	private static int groundColour(ClientLevel level, double x, double y, double z) {
		if (level == null) {
			return 0xFFB0A898;
		}
		try {
			var pos = net.minecraft.core.BlockPos.containing(x, y - 0.2, z);
			int col = level.getBlockState(pos).getMapColor(level, pos).col;
			return col == 0 ? 0xFFB0A898 : Fx.lerp(0xFF000000 | col, 0xFFFFFFFF, 0.25);
		} catch (RuntimeException e) {
			return 0xFFB0A898;
		}
	}

	/**
	 * The outline of a shoe print, in world space: a rounded sole joined to a smaller heel, with the inner side straighter
	 * (the arch), mirrored for the left foot. Closed loop: the last point equals the first.
	 */
	private static double[] shoe(Print p, double s, double y, int n) {
		double yaw = Math.toRadians(-p.yaw());
		double cs = Math.cos(yaw), sn = Math.sin(yaw);
		double side = p.right() ? 1 : -1;
		double[] out = new double[(n + 1) * 3];
		for (int i = 0; i <= n; i++) {
			double a = Math.PI * 2 * i / n;
			double c = Math.cos(a), si = Math.sin(a);
			// Front half (sin > 0) is the ball of the foot, the back half the heel.
			double lz = si > 0 ? 0.04 + si * 0.13 : -0.02 + si * 0.12;
			double width = si > 0 ? 0.075 : 0.058 + 0.012 * (1 + si);
			double lx = c * width;
			if (c * side < 0) {
				// Inner edge: pulled in around the arch.
				lx *= 1 - 0.28 * Math.exp(-Math.pow((lz + 0.02) / 0.06, 2));
			}
			lx *= side;
			lx *= s * 1.15;
			lz *= s * 1.15;
			out[i * 3] = p.x() + lx * cs + lz * sn;
			out[i * 3 + 1] = y;
			out[i * 3 + 2] = p.z() - lx * sn + lz * cs;
		}
		return out;
	}

	/** A flat line on the ground from a to b (y from a). */
	private static void flatLine(Fx fx, double ax, double az, double bx, double bz, double y, double width, int ca, int cb) {
		double dx = bx - ax, dz = bz - az;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-6) {
			return;
		}
		double nx = -dz / len * width / 2, nz = dx / len * width / 2;
		fx.quad(ax - nx, y, az - nz, ca, bx - nx, y, bz - nz, cb, bx + nx, y, bz + nz, cb, ax + nx, y, az + nz, ca);
	}

	private static void loop(Fx fx, double[] pts, double width, int colour) {
		int n = pts.length / 3 - 1;
		for (int i = 0; i < n; i++) {
			flatLine(fx, pts[i * 3], pts[i * 3 + 2], pts[i * 3 + 3], pts[i * 3 + 5], pts[i * 3 + 1], width, colour, colour);
		}
	}

	/** The moment of contact: a dust puff in the ground's colour and a fast ring of light. */
	private void impact(Fx fx, Print p, double age, double s) {
		double u = age / 0.7;
		for (int k = 0; k < 5; k++) {
			double a = p.offset() * 40 + k * 1.26;
			double out = (0.08 + 0.22 * Math.sqrt(u)) * s;
			double x = p.x() + Math.cos(a) * out;
			double z = p.z() + Math.sin(a) * out;
			double y = p.y() + 0.05 + u * 0.18 * s + (k % 2) * 0.03;
			int alpha = (int) (110 * (1 - u) * Math.min(1, age / 0.05));
			fx.smoke(x, y, z, (0.07 + 0.12 * u) * s, Fx.withAlpha(p.dust(), alpha), p.offset() * 31 + k, a + u);
		}
		double ru = Math.min(1, age / 0.35);
		if (ru < 1) {
			fx.additive(true);
			double r = (0.1 + 0.42 * (1 - (1 - ru) * (1 - ru))) * s;
			int c = look.color(p.offset(), (1 - ru) * 0.8);
			fx.ring(p.x(), p.y() + 0.012, p.z(), r * 0.86, r, Fx.withAlpha(c, 0), c, 40);
			fx.ring(p.x(), p.y() + 0.012, p.z(), r, r * 1.08, c, Fx.withAlpha(c, 0), 40);
			fx.additive(false);
		}
	}

	// ---------------------------------------------------------------- styles

	/** A shoe print drawn in light: it flashes as the foot lands, then settles to a soft glow and fades. */
	private void glow(Fx fx, Print p, double age, double t, double fade, double s) {
		double flash = Math.exp(-age / 0.18);
		double y = p.y() + 0.006;
		double bright = (0.55 + 0.45 * flash) * fade;
		double shimmer = 0.9 + 0.1 * Math.sin(Theme.time() * 3 + p.offset() * 20);
		fx.additive(true);
		double[] outline = shoe(p, s, y, 40);
		int base = look.color(p.offset(), bright * shimmer);
		int core = Fx.withAlpha(Fx.lerp(look.color(p.offset()), 0xFFFFFFFF, 0.45 + 0.4 * flash), (float) (255 * bright));
		loop(fx, outline, 0.05 * s, Fx.withAlpha(base, (float) (60 * bright)));
		loop(fx, outline, 0.014 * s, core);
		// A dimmer inner line traces the sole's tread.
		double[] inner = shoe(p, s * 0.62, y + 0.001, 28);
		loop(fx, inner, 0.008 * s, look.color(p.offset() + 0.1, bright * 0.35));
		fx.softPool(p.x(), y, p.z(), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.2 * s, look.color(p.offset(), bright * 0.22));
		if (flash > 0.05) {
			fx.light(p.x(), y + 0.03, p.z(), 0.14 * s * flash, Fx.withAlpha(0xFFFFFFFF, (float) (200 * flash * fade)), 0.8);
		}
		// Motes drift up off the print for a moment.
		for (int k = 0; k < 4; k++) {
			double mu = (age * 0.9 + k * 0.25 + p.offset()) % 1;
			if (age > 1.4) {
				break;
			}
			double mx = p.x() + Math.sin(p.offset() * 50 + k * 1.9) * 0.08 * s;
			double mz = p.z() + Math.cos(p.offset() * 37 + k * 2.3) * 0.12 * s;
			fx.light(mx, y + mu * 0.35 * s, mz, 0.018 * s, look.color(p.offset() + k * 0.1, (1 - mu) * fade * 0.9), 0.9);
		}
		fx.additive(false);
	}

	/** A dark shoe print pressed into the ground, with a lighter rim of pushed-up dirt. */
	private void pressed(Fx fx, Print p, double t, double fade, double s) {
		double y = p.y() + 0.004;
		double[] outline = shoe(p, s, y, 36);
		int n = outline.length / 3 - 1;
		int dark = Fx.withAlpha(Fx.lerp(p.dust(), 0xFF000000, 0.65), (float) (170 * fade));
		int mid = Fx.withAlpha(Fx.lerp(p.dust(), 0xFF000000, 0.45), (float) (120 * fade));
		for (int i = 0; i < n; i++) {
			fx.quad(p.x(), y, p.z(), dark, outline[i * 3], y, outline[i * 3 + 2], mid, outline[i * 3 + 3], y, outline[i * 3 + 5], mid,
					p.x(), y, p.z(), dark);
		}
		loop(fx, shoe(p, s * 1.08, y + 0.001, 36), 0.02 * s, Fx.withAlpha(Fx.lerp(p.dust(), 0xFFFFFFFF, 0.2), (float) (110 * fade)));
		// Tread lines across the sole.
		double yaw = Math.toRadians(-p.yaw());
		double cs = Math.cos(yaw), sn = Math.sin(yaw);
		for (int k = 0; k < 4; k++) {
			double lz = (0.02 + k * 0.035) * s * 1.15;
			double hw = 0.05 * s * 1.15;
			double ax = p.x() + -hw * cs + lz * sn, az = p.z() + hw * sn + lz * cs;
			double bx = p.x() + hw * cs + lz * sn, bz = p.z() - hw * sn + lz * cs;
			flatLine(fx, ax, az, bx, bz, y + 0.002, 0.012 * s, Fx.withAlpha(0xFF000000, (float) (70 * fade)), Fx.withAlpha(0xFF000000, (float) (70 * fade)));
		}
	}

	/** Rings spreading out over the ground like ripples on water, with a glint at the centre. */
	private void ripples(Fx fx, Print p, double age, double fade, double s) {
		fx.additive(true);
		for (int k = 0; k < 3; k++) {
			double u = (age - k * 0.22) / 1.6;
			if (u <= 0 || u >= 1) {
				continue;
			}
			double r = (0.08 + 0.75 * (1 - Math.pow(1 - u, 2))) * s;
			double a = (1 - u) * (1 - u) * fade * (k == 0 ? 1 : 0.7);
			int c = look.color(p.offset() + k * 0.12, a);
			double w = (0.012 + 0.03 * (1 - u)) * s;
			fx.ring(p.x(), p.y() + 0.01, p.z(), r - w, r, Fx.withAlpha(c, 0), c, 48);
			fx.ring(p.x(), p.y() + 0.01, p.z(), r, r + w * 0.6, c, Fx.withAlpha(c, 0), 48);
		}
		double glint = Math.exp(-age / 0.3) * fade;
		if (glint > 0.02) {
			fx.sparkle(p.x(), p.y() + 0.05, p.z(), 0.12 * s * glint, Fx.withAlpha(0xFFFFFFFF, (float) (230 * glint)), p.offset() * 6);
		}
		fx.softPool(p.x(), p.y() + 0.008, p.z(), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.25 * s, look.color(p.offset(), 0.15 * fade));
		fx.additive(false);
	}

	/** Small bolts of lightning crackling out across the ground from the step, re-forking a few times a second. */
	private void sparks(Fx fx, Print p, double age, double fade, double s) {
		double live = Math.min(1, 0.25 + Math.exp(-age / 0.9)) * fade;
		if (live < 0.02) {
			return;
		}
		fx.additive(true);
		long frame = (long) Math.floor(Theme.time() * 14);
		double y = p.y() + 0.01;
		for (int b = 0; b < 5; b++) {
			java.util.Random r = new java.util.Random(frame * 131 + b * 7919 + (long) (p.offset() * 1e6));
			double a = p.offset() * 30 + b * Math.PI * 2 / 5 + (r.nextDouble() - 0.5) * 0.6;
			double reach = (0.25 + r.nextDouble() * 0.3) * s * (0.6 + 0.4 * Math.min(1, age * 6));
			double px = p.x(), pz = p.z();
			int steps = 5;
			for (int i = 1; i <= steps; i++) {
				double d = reach * i / steps;
				double jitter = (r.nextDouble() - 0.5) * 0.12 * s;
				double nx = p.x() + Math.cos(a) * d - Math.sin(a) * jitter;
				double nz = p.z() + Math.sin(a) * d + Math.cos(a) * jitter;
				double fadeOut = 1 - (double) (i - 1) / steps;
				int core = Fx.withAlpha(0xFFFFFFFF, (float) (230 * live * fadeOut));
				int halo = look.color(p.offset() + b * 0.1, live * fadeOut * 0.6);
				flatLine(fx, px, pz, nx, nz, y, 0.07 * s, halo, halo);
				flatLine(fx, px, pz, nx, nz, y + 0.001, 0.018 * s, core, core);
				px = nx;
				pz = nz;
			}
		}
		fx.light(p.x(), y + 0.04, p.z(), 0.12 * s * live, Fx.withAlpha(0xFFFFFFFF, (float) (200 * live)), 0.8);
		fx.softPool(p.x(), y, p.z(), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.35 * s, look.color(p.offset(), live * 0.3));
		fx.additive(false);
	}

	private void print(Fx fx, nz.temple.client.render.Meshes mesh, Print p, double t, double fade, double s) {
		double yaw = Math.toRadians(-p.yaw());
		double cs = Math.cos(yaw), sn = Math.sin(yaw);
		double y = p.y() + 0.004;
		double h = FOOT * s;
		double px = h / 2;
		mesh.finish(nz.temple.cosmetic.Finishes.ASH);
		mesh.pattern(nz.temple.client.render.Meshes.PATTERN_GRAIN, 3);
		float depth = (float) (150 * fade * (0.7 + 0.3 * (1 - t)));
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				double n = Math.abs(Math.sin(p.offset() * 91.7 + i * 12.9898 + j * 78.233) * 43758.5453) % 1;
				int c = Fx.withAlpha(Fx.lerp(0xFF0E0A08, 0xFF2A2119, n * 0.7), depth);
				double x0 = -h + i * px, z0 = -h + j * px;
				mesh.quad(corner(p, cs, sn, x0, y, z0), corner(p, cs, sn, x0, y, z0 + px), corner(p, cs, sn, x0 + px, y, z0 + px),
						corner(p, cs, sn, x0 + px, y, z0), c, c, c, c);
			}
		}
		double rim = Math.max(0, 1 - t / 0.45) * fade;
		if (rim > 0.01) {
			double[][] edge = {corner(p, cs, sn, -h, y, -h), corner(p, cs, sn, h, y, -h), corner(p, cs, sn, h, y, h), corner(p, cs, sn, -h, y, h)};
			fx.additive(true);
			int glow = look.color(p.offset(), rim);
			for (int i = 0; i < edge.length; i++) {
				double[] a = edge[i], b = edge[(i + 1) % edge.length];
				double dx = b[0] - a[0], dz = b[2] - a[2];
				double len = Math.sqrt(dx * dx + dz * dz);
				if (len < 1e-6) {
					continue;
				}
				double nx = -dz / len * 0.008 * s, nz = dx / len * 0.008 * s;
				fx.quad(a[0] - nx, y + 0.002, a[2] - nz, glow, b[0] - nx, y + 0.002, b[2] - nz, glow, b[0] + nx, y + 0.002, b[2] + nz, glow,
						a[0] + nx, y + 0.002, a[2] + nz, glow);
			}
			fx.softPool(p.x(), y + 0.001, p.z(), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.16 * s, look.color(p.offset(), rim * 0.25));
			fx.additive(false);
		}
	}
	private static double[] corner(Print p, double cs, double sn, double lx, double y, double lz) {
		return new double[] {p.x() + lx * cs + lz * sn, y, p.z() - lx * sn + lz * cs, 0, 1, 0};
	}
	private void flames(Fx fx, nz.temple.client.render.Meshes mesh, Print p, double t, double fade, double s) {
		double time = Theme.time();
		double burn = 1 - smooth(0.35, 0.7, t);
		double ignite = Math.min(1, t * 10);
		org.joml.Quaternionf facing = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-p.yaw()));
		mesh.material(nz.temple.client.render.Meshes.Material.MATTE);
		mesh.emission(0);
		mesh.bloom(false, 0);
		int scorch = Fx.withAlpha(0xFF0B0907, (float) (170 * fade * smooth(0.05, 0.4, t)));
		org.joml.Vector3f toe = facing.transform(new org.joml.Vector3f(0, 0, (float) (0.04 * s)));
		mesh.ellipsoid(p.x() + toe.x, p.y() - 0.004, p.z() + toe.z, 0.05 * s, 0.002, 0.09 * s, facing, scorch, 10);
		if (burn > 0.01) {
			int hot = saturate(look.color(p.offset()));
			int deep = Fx.lerp(hot, 0xFF9A1C06, 0.55);
			fx.softPool(p.x(), p.y() + 0.015, p.z(), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.3 * s * ignite,
					Fx.withAlpha(hot, (float) (150 * look.alpha() * burn)));
			for (int k = 0; k < 7; k++) {
				double phase = (time * (2.4 + k * 0.31) + p.offset() * 7 + k / 7.0) % 1;
				double jx = Math.sin(p.offset() * 31 + k * 2.4) * 0.05 * s;
				double jz = Math.cos(p.offset() * 17 + k * 1.7) * 0.075 * s;
				double sway = Math.sin(time * 9 + k + p.offset() * 10) * 0.025 * s * phase;
				double r = (k < 3 ? 0.07 : 0.045) * s * (1 - phase * 0.8) * burn * ignite;
				int c = phase < 0.25 ? Fx.lerp(0xFFFFF6D2, hot, phase / 0.25) : Fx.lerp(hot, deep, (phase - 0.25) / 0.75);
				int fc = Fx.withAlpha(c, (float) (255 * look.alpha() * Math.min(1, (1 - phase) * 1.4) * burn));
				double fy = p.y() + 0.03 + phase * (k < 3 ? 0.22 : 0.36) * s * burn;
				fx.streak(p.x() + jx + sway, fy + r, p.z() + jz, sway * 4, 1, 0, r * 3, r * 1.1, fc);
				fx.light(p.x() + jx + sway, fy, p.z() + jz, r * 0.6, fc, phase < 0.3 ? 0.7 : 0.2);
			}
		}
		double ember = smooth(0.3, 0.5, t) * fade;
		for (int k = 0; k < 3 && ember > 0.01; k++) {
			double flick = 0.6 + 0.4 * Math.sin(time * (5 + k * 2.3) + k + p.offset() * 9);
			double ex = p.x() + Math.sin(p.offset() * 53 + k * 2.1) * 0.05 * s;
			double ez = p.z() + Math.cos(p.offset() * 41 + k * 1.3) * 0.08 * s;
			fx.glow(ex, p.y() + 0.02, ez, 0.03 * s, Fx.withAlpha(Fx.lerp(0xFFFF8A2A, look.color(p.offset()), 0.4), (float) (220 * ember * flick)));
		}
	}
	private void frost(Fx fx, nz.temple.client.render.Meshes mesh, Print p, double t, double fade, double s, org.joml.Quaternionf facing) {
		double grow = 1 - Math.pow(1 - Math.min(1, t * 5), 3);
		double melt = 1 - smooth(0.6, 1, t);
		double k = grow * (0.7 + 0.3 * melt);
		look.apply(mesh, 0.3f);
		float a = (float) (255 * look.alpha() * fade);
		int ice = Fx.withAlpha(Fx.lerp(look.color(p.offset()), 0xFFD8EEFF, 0.8), a);
		int tip = Fx.withAlpha(Fx.lerp(ice, 0xFFFFFFFF, 0.5), a * 0.8f);
		double spin = p.offset() * Math.PI * 2;
		for (int arm = 0; arm < 6; arm++) {
			double ang = spin + arm * Math.PI / 3;
			double len = 0.17 * s * k;
			mesh.blade(p.x(), p.y() + 0.012, p.z(), new org.joml.Quaternionf().rotationY((float) ang), len, 0.04 * s * k, 0.008, 0, ice, tip);
			for (int side = -1; side <= 1; side += 2) {
				double bx = p.x() + Math.cos(ang) * len * 0.55;
				double bz = p.z() - Math.sin(ang) * len * 0.55;
				mesh.blade(bx, p.y() + 0.013, bz, new org.joml.Quaternionf().rotationY((float) (ang + side * 1.05)), len * 0.42, 0.025 * s * k,
						0.006, 0, ice, tip);
			}
		}
		mesh.ellipsoid(p.x(), p.y() + 0.012, p.z(), 0.035 * s * k, 0.01, 0.035 * s * k, facing, Fx.withAlpha(tip, a), 10);
		double glint = Math.max(0, Math.sin(Theme.time() * 2.2 + p.offset() * 20));
		if (glint > 0.7) {
			fx.sparkle(p.x(), p.y() + 0.03, p.z(), 0.08 * s * (glint - 0.7) / 0.3, Fx.withAlpha(0xFFFFFFFF, (float) (220 * fade)), p.offset() * 6);
		}
	}
	private void blossom(nz.temple.client.render.Meshes mesh, Print p, double t, double fade, double s) {
		double open = Math.min(1, t * 4);
		double bloom = 1 + 2.4 * Math.pow(open - 1, 3) + 1.4 * Math.pow(open - 1, 2);
		double wilt = smooth(0.65, 1, t);
		double tilt = 1.25 - bloom * 1.05 - wilt * 0.3;
		double k = s * Math.min(1, open * 1.5) * (1 - wilt * 0.25);
		look.apply(mesh, 0.15f);
		if (!look.neon()) {
			mesh.emission(0.12f);
		}
		int petals = 5;
		double spin = p.offset() * Math.PI * 2;
		for (int i = 0; i < petals; i++) {
			double ang = spin + i * Math.PI * 2 / petals;
			org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY((float) ang).rotateZ((float) tilt);
			mesh.blade(p.x(), p.y() + 0.03 * k, p.z(), q, 0.14 * k, 0.1 * k, 0.006, -0.016 * (1 - wilt),
					Fx.withAlpha(Fx.lerp(0xFFFFFFFF, look.color(p.offset() + i * 0.04), 0.4), (float) (255 * look.alpha() * fade)),
					look.color(p.offset() + 0.25, fade * 0.9));
		}
		for (int i = 0; i < petals; i++) {
			double ang = spin + (i + 0.5) * Math.PI * 2 / petals;
			org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY((float) ang).rotateZ((float) (tilt + 0.35));
			mesh.blade(p.x(), p.y() + 0.035 * k, p.z(), q, 0.09 * k, 0.065 * k, 0.006, -0.01, look.color(p.offset() + 0.5, fade),
					look.color(p.offset() + 0.6, fade * 0.9));
		}
		mesh.material(nz.temple.client.render.Meshes.Material.MATTE);
		mesh.emission(0.35f);
		mesh.sphere(p.x(), p.y() + 0.04 * k, p.z(), 0.022 * k, Fx.withAlpha(0xFFFFD45A, (float) (255 * look.alpha() * fade)), 10);
	}
	private static int saturate(int argb) {
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		if (max == 0) {
			return 0xFFFF8A2A;
		}
		double span = Math.max(1, max - min);
		r = (int) Math.round(255 * Math.pow((r - min) / span, 0.8) * 0.85 + 255 * 0.15 * r / max);
		g = (int) Math.round(255 * Math.pow((g - min) / span, 0.8) * 0.85 + 255 * 0.15 * g / max);
		b = (int) Math.round(255 * Math.pow((b - min) / span, 0.8) * 0.85 + 255 * 0.15 * b / max);
		return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
	}
	private static double smooth(double a, double b, double x) {
		double k = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return k * k * (3 - 2 * k);
	}
	@Override
	protected void onDisable() {
		prints.clear();
		last.clear();
		foot.clear();
	}
}
