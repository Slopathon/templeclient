package nz.temple.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BodyFx {
	private static final int MAX_PIECES = 2400;
	private static final double[][] HUMANOID = {
			{0, 28, 0, 4, 4, 4},
			{0, 18, 0, 4, 6, 2},
			{-6, 18, 0, 2, 6, 2},
			{6, 18, 0, 2, 6, 2},
			{-2, 6, 0, 2, 6, 2},
			{2, 6, 0, 2, 6, 2},
	};
	public record Body(Vec3 feet, double yaw, double height, double width, boolean humanoid, double swing) {
		public static Body of(LivingEntity e, float partial) {
			double h = e.getBbHeight();
			double w = e.getBbWidth();
			boolean humanoid = h >= 1.3 && h <= 2.3 && w <= 0.8;
			double pos = e.walkAnimation.position(partial);
			double speed = Math.min(1, e.walkAnimation.speed(partial));
			double swing = Math.cos(pos * 0.6662) * 1.1 * speed;
			return new Body(e.getPosition(partial), e.yBodyRot, h, w, humanoid, swing);
		}
		public static Body standing(Vec3 feet, double yaw) {
			return new Body(feet, yaw, 1.8, 0.6, true, 0);
		}
	}
	public enum Breakup {
		CRUMBLE,
		DISSOLVE
	}

	private record Ghost(Body body, Paint look, double life, double rise, boolean echoes, double seed, double driftX,
			double driftZ, long born) {
	}
	private record Afterimage(Body body, Paint look, double life, double seed, long born) {
	}
	private static final class Piece {
		double x, y, z, vx, vy, vz;
		final double hx, hy, hz;
		final double release;
		final double size;
		final int color;
		final float spinX;
		final float spinY;
		final Quaternionf rot = new Quaternionf();
		boolean freed;
		double landedAt = -1;

		Piece(double x, double y, double z, double release, double size, int color, float spinX, float spinY) {
			this.x = this.hx = x;
			this.y = this.hy = y;
			this.z = this.hz = z;
			this.release = release;
			this.size = size;
			this.color = color;
			this.spinX = spinX;
			this.spinY = spinY;
		}
	}
	private record Swarm(Body body, Paint look, List<Piece> pieces, Breakup breakup, boolean assembling, double time,
			long born) {
	}
	private final List<Ghost> ghosts = new ArrayList<>();
	private final List<Afterimage> afterimages = new ArrayList<>();
	public boolean glowingAfterimages;
	private final List<Swarm> swarms = new ArrayList<>();
	private int pieceTotal;
	private long lastNanos = System.nanoTime();
	public boolean isEmpty() {
		return ghosts.isEmpty() && afterimages.isEmpty() && swarms.isEmpty();
	}
	public void clear() {
		ghosts.clear();
		afterimages.clear();
		swarms.clear();
		pieceTotal = 0;
	}

	public void ghost(Body body, Paint look, double life, double rise, boolean echoes) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		double drift = r.nextDouble(Math.PI * 2);
		ghosts.add(new Ghost(body, look, life, rise, echoes, r.nextDouble(100), Math.cos(drift), Math.sin(drift), System.nanoTime()));
	}
	public void breakUp(Body body, Paint look, int count, double spread, String from, Breakup breakup) {
		breakUp(body, look, count, spread, from, breakup, null);
	}

	public void breakUp(Body body, Paint look, int count, double spread, String from, Breakup breakup, int[] palette) {
		List<Piece> pieces = sample(body, look, count, spread, from, palette);
		if (!pieces.isEmpty()) {
			swarms.add(new Swarm(body, look, pieces, breakup, false, spread, System.nanoTime()));
		}
	}

	public void assemble(Body body, Paint look, int count, double time) {
		List<Piece> pieces = sample(body, look, count, time * 0.6, "Feet", null);
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (Piece p : pieces) {
			double a = r.nextDouble(Math.PI * 2);
			double d = r.nextDouble(0.8, 2.2) * body.height() / 1.8;
			p.x = p.hx + Math.cos(a) * d;
			p.y = p.hy + r.nextDouble(-0.4, 1.4);
			p.z = p.hz + Math.sin(a) * d;
		}
		if (!pieces.isEmpty()) {
			swarms.add(new Swarm(body, look, pieces, Breakup.DISSOLVE, true, time, System.nanoTime()));
		}
	}

	public void afterimage(Body body, Paint look, double life) {
		if (afterimages.size() < 64) {
			afterimages.add(new Afterimage(body, look, life, ThreadLocalRandom.current().nextDouble(100), System.nanoTime()));
		}
	}
	private List<Piece> sample(Body b, Paint look, int count, double spread, String from, int[] palette) {
		int n = Math.min(count, MAX_PIECES - pieceTotal);
		List<Piece> pieces = new ArrayList<>(Math.max(0, n));
		if (n <= 0) {
			return pieces;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Quaternionf body = new Quaternionf().rotationY((float) -Math.toRadians(b.yaw()));
		double px = b.height() / 32;
		Vector3f v = new Vector3f();
		for (int i = 0; i < n; i++) {
			double lx;
			double ly;
			double lz;
			if (b.humanoid()) {

				double[] part = pickPart(r);
				double[] at = {r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)};
				int axis = r.nextInt(3);
				at[axis] = at[axis] < 0 ? -0.85 : 0.85;
				lx = (part[0] + at[0] * part[3]) * px;
				ly = (part[1] + at[1] * part[4]) * px;
				lz = (part[2] + at[2] * part[5]) * px;
			} else {
				lx = r.nextDouble(-0.5, 0.5) * b.width();
				ly = r.nextDouble(0, 1) * b.height();
				lz = r.nextDouble(-0.5, 0.5) * b.width();
			}
			v.set((float) lx, (float) ly, (float) lz);
			body.transform(v);
			double hFrac = Math.max(0, Math.min(1, ly / b.height()));
			double order = switch (from) {
				case "Feet" -> hFrac;
				case "Everywhere" -> r.nextDouble();
				default -> 1 - hFrac;
			};
			double release = 0.12 + order * spread + r.nextDouble(0, spread * 0.22);
			int base = palette == null ? look.color(hFrac * 0.6)
					: Fx.lerp(palette[r.nextInt(palette.length)], look.color(hFrac * 0.6), 0.15);
			int c = Fx.lerp(base, 0xFF000000 | ((base & 0xFFFFFF) >> 1 & 0x7F7F7F), r.nextDouble(0, 0.35));
			pieces.add(new Piece(b.feet().x + v.x, b.feet().y + v.y, b.feet().z + v.z, release,
					px * r.nextDouble(1.1, 1.9), c, (float) r.nextDouble(-9, 9), (float) r.nextDouble(-9, 9)));
		}
		pieceTotal += pieces.size();
		return pieces;
	}
	private static double[] pickPart(ThreadLocalRandom r) {
		double total = 0;
		for (double[] p : HUMANOID) {
			total += p[3] * p[4] * p[5];
		}
		double pick = r.nextDouble(total);
		for (double[] p : HUMANOID) {
			pick -= p[3] * p[4] * p[5];
			if (pick <= 0) {
				return p;
			}
		}
		return HUMANOID[1];
	}

	public void emit(Fx fx) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			clear();
			return;
		}
		Meshes mesh = fx.mesh();
		ghosts.removeIf(gh -> (now - gh.born()) / 1e9 > gh.life() + 0.6);
		for (Ghost gh : ghosts) {
			ghost(fx, mesh, gh, (now - gh.born()) / 1e9);
		}
		afterimages.removeIf(a -> (now - a.born()) / 1e9 > a.life());
		for (Afterimage a : afterimages) {
			afterimage(mesh, a, (now - a.born()) / 1e9);
		}
		swarms(fx, mesh, mc, now, dt);
	}
	private static double smooth(double x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}
	private static int pale(Paint look, double t, double amount) {
		return Fx.lerp(look.color(t), 0xFFFFFFFF, amount);
	}
	private static Vector3d ghostRoot(Ghost gh, double t) {
		Body b = gh.body();
		double u = Math.max(0, Math.min(1, t / gh.life()));
		double seed = gh.seed();

		double rise = (0.25 * smooth(t / 0.6) + (u * u * 0.65 + u * 0.35) * 3.2) * gh.rise() * (b.height() / 1.8);
		double sway = 0.22 + u * 0.35;
		double dx = Math.sin(t * 1.25 + seed) * sway * u + gh.driftX() * u * u * 1.6 + Math.sin(t * 2.9 + seed * 3) * 0.04;
		double dz = Math.cos(t * 1.05 + seed * 1.7) * sway * u + gh.driftZ() * u * u * 1.6 + Math.cos(t * 3.3 + seed * 2) * 0.04;
		double bob = Math.sin(t * 2.4 + seed) * 0.07 * smooth(t / 0.5);
		return new Vector3d(b.feet().x + dx, b.feet().y + b.height() * 0.5625 + rise + bob, b.feet().z + dz);
	}
	private void ghost(Fx fx, Meshes mesh, Ghost gh, double t) {
		if (gh.echoes()) {
			for (int echo = 3; echo >= 1; echo--) {
				double te = t - echo * 0.11;
				if (te > 0) {
					drawGhost(fx, mesh, gh, te, 0.22 / echo, true);
				}
			}
		}
		drawGhost(fx, mesh, gh, t, 1, false);
	}
	private void drawGhost(Fx fx, Meshes mesh, Ghost gh, double t, double strength, boolean echo) {
		Body b = gh.body();
		Paint look = gh.look();
		double life = gh.life();
		double u = t / life;
		if (u >= 1) {
			return;
		}
		double seed = gh.seed();
		double alpha = smooth(t / 0.3) * (u < 0.5 ? 1 : 1 - smooth((u - 0.5) / 0.5)) * strength;
		if (alpha <= 0.01) {
			return;
		}
		Vector3d root = ghostRoot(gh, t);
		double px = b.height() / 32;
		double yaw = Math.toRadians(b.yaw() + Math.sin(t * 0.8 + seed) * 14 + u * 40 * (seed > 50 ? 1 : -1));
		double pitch = Math.toRadians(Math.sin(t * 1.6 + seed) * 6 - 10 * smooth(t / 1.2));
		double roll = Math.toRadians(Math.sin(t * 1.3 + seed * 2) * 8);
		Quaternionf body = new Quaternionf().rotationY((float) -yaw).rotateX((float) pitch).rotateZ((float) roll);
		double breathe = 1 + Math.sin(t * 3.1 + seed) * 0.035;
		double stretch = 1 + u * 0.3;
		if (echo) {
			mesh.material(Meshes.Material.MATTE);
			mesh.emission(0.9f);
		} else {
			mesh.material(Meshes.Material.GLASS);
			mesh.emission(0.45f);
		}
		int top = pale(look, 0.2, 0.6);
		int low = pale(look, 0.6, 0.35);
		int shade = (int) Math.round(alpha * 0.62 * look.alpha() * 255);
		if (!b.humanoid()) {
			mesh.ellipsoid(root.x, root.y, root.z, b.width() * 0.5 * breathe, b.height() * 0.45 * stretch, b.width() * 0.5 * breathe,
					body, Fx.withAlpha(top, shade), 10);
		} else {

			Quaternionf head = new Quaternionf().rotationX((float) Math.toRadians(-14 * smooth(t / 1.0)));
			part(mesh, root, body, px, 0, 6, 0, head, 4 * breathe, 4 * breathe, 4 * breathe, -4, Fx.withAlpha(top, shade));
			part(mesh, root, body, px, 0, 6, 0, new Quaternionf(), 4 * breathe, 6 * stretch, 2 * breathe, 6 * stretch,
					Fx.withAlpha(Fx.lerp(top, low, 0.4), shade));
			for (int side = -1; side <= 1; side += 2) {
				double spread = Math.toRadians(18 + 62 * smooth(t / (life * 0.5)) + Math.sin(t * 2.1 + seed + side) * 14);
				double swing = Math.toRadians(Math.sin(t * 1.7 + seed * 0.5 + side * 1.3) * 22 - 12);
				Quaternionf arm = new Quaternionf().rotationZ((float) (spread * -side)).rotateX((float) swing);
				part(mesh, root, body, px, 6 * -side, 4, 0, arm, 2 * breathe, 6 * stretch, 2 * breathe, 6 * stretch,
						Fx.withAlpha(Fx.lerp(top, low, 0.5), (int) (shade * 0.9)));
			}
			double legFade = Math.max(0, 1 - u * 1.6);
			for (int side = -1; side <= 1; side += 2) {
				double trail = Math.toRadians(22 + Math.sin(t * 2.3 + seed + side * 0.7) * 12 + u * 25);
				Quaternionf leg = new Quaternionf().rotationX((float) trail).rotateZ((float) Math.toRadians(side * -4));
				double taper = 0.55 + 0.45 * legFade;
				part(mesh, root, body, px, 2 * -side, -6, 0, leg, 2 * taper, 6 * stretch, 2 * taper, 6 * stretch,
						Fx.withAlpha(low, (int) (shade * (0.35 + 0.65 * legFade))));
			}
		}
		if (echo) {
			return;
		}
		int samples = 14;
		double[] path = new double[samples * 3];
		double[] radius = new double[samples];
		int[] colours = new int[samples];
		for (int i = 0; i < samples; i++) {
			double back = (samples - 1 - i) * 0.07;
			Vector3d r = ghostRoot(gh, Math.max(0, t - back));
			double w = i / (double) (samples - 1);
			path[i * 3] = r.x;
			path[i * 3 + 1] = r.y - b.height() * 0.32 - back * 0.6;
			path[i * 3 + 2] = r.z;
			radius[i] = b.width() * 0.32 * w * w;
			colours[i] = Fx.withAlpha(low, (int) (shade * 0.55 * w));
		}
		mesh.material(Meshes.Material.MATTE);
		mesh.emission(0.8f);
		mesh.tube(path, samples, radius, colours, 7);
		fx.additive(true);
		fx.glow(root.x, root.y, root.z, 1.1 * b.height() / 1.8, Fx.withAlpha(top, (int) (alpha * 70 * Math.max(0.4f, look.bloom()))));
		for (int i = 0; i < 3; i++) {
			double k = Math.floor(t * 3) - i;
			if (k < 0) {
				continue;
			}
			double born = k / 3;
			double age = t - born;
			if (age > 1.2) {
				continue;
			}
			Vector3d at = ghostRoot(gh, born);
			double a = seed * 7 + k * 2.39;
			double wx = at.x + Math.cos(a) * b.width() * 0.6;
			double wy = at.y + Math.sin(a * 1.7) * b.height() * 0.3 + age * 0.35;
			double wz = at.z + Math.sin(a) * b.width() * 0.6;
			fx.wisp(wx, wy, wz, 0.18 + age * 0.2, Fx.withAlpha(top, (int) (90 * alpha * (1 - age / 1.2))), a);
		}
		fx.additive(false);
	}
	private void afterglow(Fx fx, Afterimage a, double t) {
		Body b = a.body();
		double u = t / a.life();
		double alpha = (1 - smooth(u)) * smooth(t / 0.08);
		if (alpha <= 0.02) {
			return;
		}
		double px = b.height() / 32;
		Quaternionf yaw = new Quaternionf().rotationY((float) -Math.toRadians(b.yaw()));
		double lift = u * 0.25;
		int c = Fx.withAlpha(a.look().color(0.2), (float) (alpha * 235));
		fx.bloom(true);
		if (!b.humanoid()) {
			fx.glow(b.feet().x, b.feet().y + b.height() * 0.5 + lift, b.feet().z, b.height() * 0.45, c);
			fx.bloom(false);
			return;
		}
		double[][] limbs = {
				{0, 12, 0, 24, 4.2},
				{-6, 23, -6 + b.swing() * 2, 13, 2.4},
				{6, 23, 6 - b.swing() * 2, 13, 2.4},
				{-2, 12, -2 - b.swing() * 2, 1, 2.6},
				{2, 12, 2 + b.swing() * 2, 1, 2.6},
		};
		for (double[] l : limbs) {
			Vector3f p0 = yaw.transform(new Vector3f((float) (l[0] * px), (float) (l[1] * px), 0));
			Vector3f p1 = yaw.transform(new Vector3f((float) (l[2] * px), (float) (l[3] * px), 0));
			fx.softBeam(b.feet().x + p0.x, b.feet().y + p0.y + lift, b.feet().z + p0.z, b.feet().x + p1.x, b.feet().y + p1.y + lift,
					b.feet().z + p1.z, l[4] * px * 1.6, c, c);
		}
		fx.glow(b.feet().x, b.feet().y + 28 * px + lift, b.feet().z, 5 * px, c);
		fx.bloom(false);
	}
	private void afterimage(Meshes mesh, Afterimage a, double t) {
		Body b = a.body();
		Paint look = a.look();
		double u = t / a.life();
		double alpha = Math.pow(1 - u, 2.2) * smooth(t / 0.08);
		if (alpha <= 0.01) {
			return;
		}
		double lift = u * 0.25;
		Vector3d root = new Vector3d(b.feet().x, b.feet().y + b.height() * 0.5625 + lift, b.feet().z);
		double px = b.height() / 32;
		Quaternionf body = new Quaternionf().rotationY((float) -Math.toRadians(b.yaw()));
		int shade = (int) Math.round(alpha * 0.36 * look.alpha() * 255);
		look.apply(mesh, 0.35f);
		int top = pale(look, 0.1 + a.seed() * 0.001, 0.25);
		int low = pale(look, 0.5, 0.1);
		double swell = 1 + u * 0.18;
		if (!b.humanoid()) {
			mesh.ellipsoid(root.x, root.y, root.z, b.width() * 0.5 * swell, b.height() * 0.45, b.width() * 0.5 * swell,
					body, Fx.withAlpha(top, shade), 8);
			return;
		}
		part(mesh, root, body, px, 0, 6, 0, new Quaternionf(), 4 * swell, 4 * swell, 4 * swell, -4, Fx.withAlpha(top, shade));
		part(mesh, root, body, px, 0, 6, 0, new Quaternionf(), 4 * swell, 6, 2 * swell, 6, Fx.withAlpha(Fx.lerp(top, low, 0.4), shade));
		for (int side = -1; side <= 1; side += 2) {
			Quaternionf arm = new Quaternionf().rotationX((float) (b.swing() * side));
			part(mesh, root, body, px, 6 * -side, 4, 0, arm, 2 * swell, 6, 2 * swell, 6, Fx.withAlpha(Fx.lerp(top, low, 0.5), shade));
			double legFade = Math.max(0, 1 - u * 1.8);
			Quaternionf leg = new Quaternionf().rotationX((float) (-b.swing() * side));
			part(mesh, root, body, px, 2 * -side, -6, 0, leg, 2 * swell, 6, 2 * swell, 6,
					Fx.withAlpha(low, (int) (shade * legFade)));
		}
	}
	public static void statue(Fx fx, Body b, Paint look, double alpha, double time) {
		if (alpha <= 0.01) {
			return;
		}
		Meshes mesh = fx.mesh();
		double breathe = 1 + Math.sin(time * 1.8) * 0.02;
		double hover = 0.04 + Math.sin(time * 1.3) * 0.03;
		Vector3d root = new Vector3d(b.feet().x, b.feet().y + b.height() * 0.5625 + hover, b.feet().z);
		double px = b.height() / 32;
		Quaternionf body = new Quaternionf().rotationY((float) -Math.toRadians(b.yaw()));
		int shade = (int) Math.round(alpha * 0.55 * look.alpha() * 255);
		look.apply(mesh, 0.25f);
		double shimmer = (Math.sin(time * 2.2) + 1) * 0.15;
		int top = pale(look, 0.1 + shimmer, 0.35);
		int low = pale(look, 0.55, 0.15);
		if (!b.humanoid()) {
			mesh.ellipsoid(root.x, root.y, root.z, b.width() * 0.5 * breathe, b.height() * 0.45, b.width() * 0.5 * breathe,
					body, Fx.withAlpha(top, shade), 10);
		} else {
			part(mesh, root, body, px, 0, 6, 0, new Quaternionf().rotationX((float) (Math.sin(time * 0.7) * 0.08)),
					4 * breathe, 4 * breathe, 4 * breathe, -4, Fx.withAlpha(top, shade));
			part(mesh, root, body, px, 0, 6, 0, new Quaternionf(), 4 * breathe, 6, 2 * breathe, 6, Fx.withAlpha(Fx.lerp(top, low, 0.4), shade));
			for (int side = -1; side <= 1; side += 2) {
				Quaternionf arm = new Quaternionf().rotationZ((float) (side * -0.06 - side * Math.sin(time * 1.1) * 0.03));
				part(mesh, root, body, px, 6 * -side, 4, 0, arm, 2 * breathe, 6, 2 * breathe, 6, Fx.withAlpha(Fx.lerp(top, low, 0.5), shade));
				part(mesh, root, body, px, 2 * -side, -6, 0, new Quaternionf(), 2 * breathe, 6, 2 * breathe, 6, Fx.withAlpha(low, shade));
			}
		}
		fx.additive(true);
		double ring = b.width() * 0.9 + Math.sin(time * 1.8) * 0.05;
		int c = Fx.withAlpha(look.color(0.3), (float) (alpha * 90 * Math.max(0.4f, look.bloom())));
		fx.ring(b.feet().x, b.feet().y + 0.02, b.feet().z, ring * 0.7, ring, c, Fx.withAlpha(c, 0), 40);
		fx.additive(false);
	}
	private static void part(Meshes mesh, Vector3d root, Quaternionf body, double px, double ox, double oy, double oz,
			Quaternionf local, double hx, double hy, double hz, double hang, int argb) {
		Quaternionf q = new Quaternionf(body).mul(local);
		Vector3f offset = new Vector3f(0, (float) (-hang * px), 0);
		q.transform(offset);
		Vector3f pivot = new Vector3f((float) (ox * px), (float) (oy * px), (float) (oz * px));
		body.transform(pivot);
		mesh.box(root.x + pivot.x + offset.x, root.y + pivot.y + offset.y, root.z + pivot.z + offset.z, q,
				hx * px, hy * px, hz * px, argb);
	}
	private void swarms(Fx fx, Meshes mesh, Minecraft mc, long now, double dt) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int ci = swarms.size() - 1; ci >= 0; ci--) {
			Swarm sw = swarms.get(ci);
			double t = (now - sw.born()) / 1e9;
			boolean alive = sw.assembling() ? assembling(fx, mesh, sw, t) : breaking(mesh, mc, sw, t, dt, cursor);
			if (!alive && t > 0.5) {
				pieceTotal -= sw.pieces().size();
				swarms.remove(ci);
			}
		}
	}
	private boolean breaking(Meshes mesh, Minecraft mc, Swarm sw, double t, double dt, BlockPos.MutableBlockPos cursor) {
		Paint look = sw.look();
		Vec3 centre = sw.body().feet().add(0, sw.body().height() * 0.5, 0);
		boolean dissolve = sw.breakup() == Breakup.DISSOLVE;
		boolean alive = false;
		look.apply(mesh, dissolve ? 0.4f : 0.05f);
		for (Piece p : sw.pieces()) {
			double fade = 1;
			if (t < p.release) {
				double shake = Math.max(0, 1 - (p.release - t) / 0.35) * p.size * 0.35;
				double jx = shake * Math.sin(t * 61 + p.spinX);
				double jz = shake * Math.cos(t * 57 + p.spinY);
				mesh.box(p.x + jx, p.y, p.z + jz, p.rot, p.size / 2, p.size / 2, p.size / 2, shade(look, p.color, 1));
				alive = true;
				continue;
			}
			if (!p.freed) {
				p.freed = true;
				ThreadLocalRandom r = ThreadLocalRandom.current();
				double ox = p.x - centre.x;
				double oz = p.z - centre.z;
				double len = Math.max(0.05, Math.sqrt(ox * ox + oz * oz));
				double push = dissolve ? r.nextDouble(0.1, 0.5) : r.nextDouble(0.3, 1.3);
				p.vx = ox / len * push + r.nextDouble(-0.3, 0.3);
				p.vz = oz / len * push + r.nextDouble(-0.3, 0.3);
				p.vy = dissolve ? r.nextDouble(0.3, 1.1) : r.nextDouble(0.2, 1.6);
			}
			double age = t - p.release;
			if (dissolve) {
				p.vy += 1.4 * dt;
				p.vx += Math.sin(t * 3 + p.spinX) * 0.8 * dt;
				p.vz += Math.cos(t * 2.6 + p.spinY) * 0.8 * dt;
				double drag = Math.pow(0.35, dt);
				p.vx *= drag;
				p.vz *= drag;
				p.x += p.vx * dt;
				p.y += p.vy * dt;
				p.z += p.vz * dt;
				p.rot.rotateX(p.spinX * 0.4f * (float) dt).rotateY(p.spinY * 0.4f * (float) dt);
				fade = 1 - smooth(age / 1.6);
			} else if (p.landedAt < 0) {
				p.vy -= 13 * dt;
				double drag = Math.pow(0.6, dt);
				p.vx *= drag;
				p.vz *= drag;
				double nx = p.x + p.vx * dt;
				double ny = p.y + p.vy * dt;
				double nz = p.z + p.vz * dt;
				cursor.set(nx, ny - p.size / 2, nz);
				if (p.vy < 0 && !mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
					ny = Math.floor(ny - p.size / 2) + 1 + p.size / 2;
					if (Math.abs(p.vy) > 1.4) {
						p.vy = -p.vy * 0.3;
						p.vx *= 0.6;
						p.vz *= 0.6;
					} else {
						p.vy = 0;
						p.landedAt = t;
					}
				}
				p.x = nx;
				p.y = ny;
				p.z = nz;
				p.rot.rotateX(p.spinX * (float) dt).rotateY(p.spinY * (float) dt);
			} else {
				fade = 1 - smooth((t - p.landedAt - 0.8) / 1.2);
			}
			if (age > 5) {
				fade = 0;
			}
			if (fade <= 0.01) {
				continue;
			}
			alive = true;
			double s = p.size / 2 * (0.4 + 0.6 * fade);
			int c = dissolve ? Fx.lerp(p.color, 0xFFFFFFFF, Math.min(1, age * 0.6)) : p.color;
			mesh.box(p.x, p.y, p.z, p.rot, s, s, s, shade(look, c, fade));
		}
		return alive;
	}
	private boolean assembling(Fx fx, Meshes mesh, Swarm sw, double t) {
		Paint look = sw.look();
		double arrive = sw.time();
		double hold = 0.35;
		double fadeOut = 0.5;
		if (t > arrive + hold + fadeOut + 0.2) {
			return false;
		}
		double bodyFade = t < arrive + hold ? 1 : 1 - smooth((t - arrive - hold) / fadeOut);
		look.apply(mesh, 0.3f);
		for (Piece p : sw.pieces()) {
			double k = smooth((t - p.release * 0.5) / Math.max(0.15, arrive - p.release * 0.5));
			double arc = Math.sin(k * Math.PI) * 0.35;
			double x = p.x + (p.hx - p.x) * k + Math.cos(p.spinX) * arc;
			double y = p.y + (p.hy - p.y) * k + arc * 0.5;
			double z = p.z + (p.hz - p.z) * k + Math.sin(p.spinY) * arc;
			double s = p.size / 2 * (0.5 + 0.5 * k);
			double appear = smooth(t / 0.15);
			Quaternionf rot = new Quaternionf().rotationXYZ((float) ((1 - k) * p.spinX), (float) ((1 - k) * p.spinY), 0);
			mesh.box(x, y, z, rot, s, s, s, shade(look, Fx.lerp(p.color, 0xFFFFFFFF, (1 - k) * 0.5), appear * bodyFade));
		}
		if (t > arrive * 0.8 && look.bloom() > 0.1f) {
			Body b = sw.body();
			double flash = Math.max(0, 1 - Math.abs(t - arrive) / 0.35);
			fx.additive(true);
			fx.glow(b.feet().x, b.feet().y + b.height() * 0.55, b.feet().z, b.height() * 0.8,
					look.color(0.3, flash * 0.6 * Math.max(0.4f, look.bloom())));
			fx.additive(false);
		}
		return true;
	}
	private static int shade(Paint look, int argb, double fade) {
		return Fx.withAlpha(argb, (float) (255 * look.alpha() * Math.max(0, Math.min(1, fade))));
	}
}
