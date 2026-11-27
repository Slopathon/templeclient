package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.Fx;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AttackEffects extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource, nz.temple.client.render.GlowUser {
	private static final int MAX_SPARKS = 500;
	private final ModeSetting shape;
	private final BoolSetting flash;
	private final NumberSetting count;
	private final NumberSetting size;
	private final NumberSetting lifetime;
	private final NumberSetting spread;
	private final NumberSetting gravity;
	private final BoolSetting bounce;
	private final nz.temple.client.modules.cosmetic.Look look;
	private final List<Spark> sparks = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private ClientLevel level;
	private long lastNanos = System.nanoTime();
	private static final class Spark {
		double x, y, z, vx, vy, vz;
		final long born = System.nanoTime();
		final double life;
		final double seed;
		final double scale;
		boolean splinter;
		double spin;
		double spinRate;

		Spark(double x, double y, double z, double vx, double vy, double vz, double life, double seed, double scale) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.life = life;
			this.seed = seed;
			this.scale = scale;
		}
	}
	private record Hit(Vec3 at, Vec3 dir, double width, long born, boolean crit, double seed, int side) {
	}
	static {
		nz.temple.client.util.VanillaFx.hitParticles = AttackEffects::replacesHitParticles;
		nz.temple.client.util.VanillaFx.hitAt = AttackEffects::replacesAt;
	}
	public AttackEffects() {
		super("Hit Particles", Category.COSMETICS, "A burst of light where your hits land: a starburst and sparks, a slash, lightning "
				+ "or shattering glass. Visual only.");
		shape = add(new ModeSetting("Style", List.of("Impact", "Slash", "Electric", "Shatter"), "Impact")
				.describe("Impact is a white-hot starburst, a shock ring and a spray of sparks. Slash sweeps a sharp crescent through "
						+ "the target. Electric cracks lightning out of the hit. Shatter bursts glass splinters that glint as they "
						+ "spin and fall."));
		flash = add(new BoolSetting("Shock ring", true).describe("A crisp ring of light bursting out from the hit."));
		count = add(new NumberSetting("Sparks", 14, 0, 60, 1, "").describe("Sparks or splinters per hit."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Effect size."));
		lifetime = add(new NumberSetting("Lifetime", 0.6, 0.2, 3, 0.05, " s").describe("How long sparks last."));
		group();
		spread = add(new NumberSetting("Spread", 1, 0.2, 3, 0.05, "x").describe("How fast sparks fly outward."));
		gravity = add(new NumberSetting("Gravity", 1, 0, 3, 0.05, "x").describe("How strongly sparks fall. 0 makes them float."));
		bounce = add(new BoolSetting("Bounce", true).describe("Sparks skip off the ground instead of passing through it."));
		group();
		look = new nz.temple.client.modules.cosmetic.Look(s -> add(s), "Light");
		instance = this;
	}
	@Override
	public void preview(Vec3 at) {
		Vec3 c = at.add(0, 1, 0);
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		Vec3 dir = c.subtract(cam);
		dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
		hit(c.subtract(dir.scale(0.3)), dir, 0.6, true);
	}
	public boolean glowing() {
		return isEnabled() && (!hits.isEmpty() || !sparks.isEmpty());
	}
	private static volatile AttackEffects instance;
	private volatile int lastTarget = -1;
	private volatile long lastHitAt;
	public static boolean replacesHitParticles(Entity e) {
		AttackEffects m = instance;
		return m != null && m.isEnabled() && e != null && e.getId() == m.lastTarget && System.currentTimeMillis() - m.lastHitAt < 800;
	}
	public static boolean replacesAt(double x, double y, double z) {
		AttackEffects m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.isEnabled() || mc.level == null || System.currentTimeMillis() - m.lastHitAt > 800) {
			return false;
		}
		Entity e = mc.level.getEntity(m.lastTarget);
		return e != null && e.getBoundingBox().inflate(1.5).contains(x, y, z);
	}
	public void attacked(Entity target) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null) {
			return;
		}
		lastTarget = target.getId();
		lastHitAt = System.currentTimeMillis();
		if (level != mc.level) {
			sparks.clear();
			hits.clear();
			level = mc.level;
		}
		if (mc.player == null) {
			return;
		}
		Vec3 hit = nz.temple.client.util.HitConfirm.hitPoint(mc, target);
		Vec3 dir = hit.subtract(mc.player.getEyePosition());
		dir = dir.lengthSqr() < 1e-6 ? mc.player.getViewVector(1f) : dir.normalize();
		boolean crit = mc.player.fallDistance > 0 && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater()
				&& !mc.player.isPassenger() && !mc.player.isSprinting();
		hit(hit, dir, target.getBbWidth(), crit);
	}
	private void hit(Vec3 at, Vec3 dir, double width, boolean crit) {
		level = Minecraft.getInstance().level;
		ThreadLocalRandom r = ThreadLocalRandom.current();
		if (hits.size() > 16) {
			hits.remove(0);
		}
		hits.add(new Hit(at, dir, width, System.nanoTime(), crit, r.nextDouble(1000), r.nextBoolean() ? 1 : -1));
		double power = crit ? 1.6 : 1;
		int n = (int) Math.round(count.get() * power);

		Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 up = side.cross(dir).normalize();
		boolean glass = shape.is("Shatter");
		for (int i = 0; i < n && sparks.size() < MAX_SPARKS; i++) {
			double a = r.nextDouble(Math.PI * 2);
			double out = 0.6 + r.nextDouble() * 0.9;
			Vec3 v = dir.scale(r.nextDouble() < 0.2 ? r.nextDouble(0.2, 0.6) : -r.nextDouble(0.1, 0.8)).add(side.scale(Math.cos(a) * out))
					.add(up.scale(Math.sin(a) * out + 0.3));
			double speed = (glass ? 2 + r.nextDouble() * 3 : 4 + r.nextDouble() * 6) * spread.get() * Math.sqrt(power);
			v = v.normalize().scale(speed);
			Spark sp = new Spark(at.x, at.y, at.z, v.x, v.y, v.z, lifetime.get() * (0.45 + r.nextDouble() * 0.55) * (glass ? 1.6 : 1),
					r.nextDouble(), 0.6 + r.nextDouble() * 0.7);
			sp.splinter = glass;
			sp.spin = r.nextDouble(Math.PI * 2);
			sp.spinRate = r.nextDouble(-18, 18);
			sparks.add(sp);
		}
	}
	public void emit(Fx fx, float partial) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || level != mc.level) {
			sparks.clear();
			hits.clear();
			return;
		}
		fx.throughWalls(false);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		double g = 18 * gravity.get();
		double sparkDrag = Math.pow(0.06, dt);
		double glassDrag = Math.pow(0.4, dt);
		sparks.removeIf(sp -> (now - sp.born) / 1e9 > sp.life);
		for (Spark sp : sparks) {
			double drag = sp.splinter ? glassDrag : sparkDrag;
			sp.vx *= drag;
			sp.vy = sp.vy * drag - g * (sp.splinter ? 1 : 0.55) * dt;
			sp.vz *= drag;
			double nx = sp.x + sp.vx * dt, ny = sp.y + sp.vy * dt, nz = sp.z + sp.vz * dt;
			if (bounce.get()) {
				cursor.set(nx, ny, nz);
				if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
					cursor.set(sp.x, ny, sp.z);
					if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
						sp.vy = -sp.vy * 0.35;
						ny = sp.y;
					}
					sp.vx *= 0.55;
					sp.vz *= 0.55;
					cursor.set(nx, sp.y, sp.z);
					if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
						sp.vx = -sp.vx;
						nx = sp.x;
					}
					cursor.set(sp.x, sp.y, nz);
					if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
						sp.vz = -sp.vz;
						nz = sp.z;
					}
				}
			}
			sp.x = nx;
			sp.y = ny;
			sp.z = nz;
			sp.spin += sp.spinRate * dt;
			double t = (now - sp.born) / 1e9 / sp.life;
			if (sp.splinter) {
				splinter(fx, sp, t);
			} else {
				spark(fx, sp, t);
			}
		}
		hits.removeIf(h -> now - h.born() > 450_000_000L);
		for (Hit h : hits) {
			double age = (now - h.born()) / 1e9;
			double scale = size.get() * (h.crit() ? 1.3 : 1) * Math.max(0.75, Math.min(1.5, h.width() * 1.3));
			switch (shape.get()) {
				case "Slash" -> slash(fx, h, age, scale);
				case "Electric" -> electric(fx, h, age, scale);
				case "Shatter" -> starburst(fx, h, age, scale * 0.7, 6);
				default -> starburst(fx, h, age, scale, h.crit() ? 12 : 9);
			}
			if (flash.get()) {
				ring(fx, h, age, scale);
			}
			if (h.crit() && !shape.is("Slash")) {
				cross(fx, h, age, scale);
			}
		}
		fx.bloom(false);
		fx.additive(false);
	}
	private int heat(double seed, double t) {
		int colour = look.color(seed * 0.35);
		int deep = Fx.lerp(colour, 0xFF2A0A04, 0.65);
		return t < 0.25 ? Fx.lerp(0xFFFFFFFF, colour, t / 0.25) : Fx.lerp(colour, deep, (t - 0.25) / 0.75);
	}
	private void spark(Fx fx, Spark sp, double t) {
		double speed = Math.sqrt(sp.vx * sp.vx + sp.vy * sp.vy + sp.vz * sp.vz);
		if (speed < 1e-4) {
			return;
		}
		double k = size.get() * sp.scale;
		double fade = Math.min(1, (1 - t) * 2.5);
		int c = Fx.withAlpha(heat(sp.seed, t), (float) (255 * look.alpha() * fade));
		double len = Math.max(0.04, Math.min(0.7, speed * 0.045)) * k;
		double w = 0.035 * k * (0.4 + 0.6 * (1 - t));
		double ux = sp.vx / speed, uy = sp.vy / speed, uz = sp.vz / speed;
		fx.additive(true);
		fx.streak(sp.x - ux * len / 2, sp.y - uy * len / 2, sp.z - uz * len / 2, sp.vx, sp.vy, sp.vz, len, w, c);
		fx.bloom(true);
		fx.streak(sp.x - ux * len / 2, sp.y - uy * len / 2, sp.z - uz * len / 2, sp.vx, sp.vy, sp.vz, len * 1.2, w * 3, Fx.fade(c, 0.6));
		fx.bloom(false);
	}
	private void splinter(Fx fx, Spark sp, double t) {
		double k = size.get() * sp.scale;
		double fade = Math.min(1, (1 - t) * 3);
		Vec3 right = fx.cameraRight(), up = fx.cameraUp();
		double dx = right.x * Math.cos(sp.spin) + up.x * Math.sin(sp.spin);
		double dy = right.y * Math.cos(sp.spin) + up.y * Math.sin(sp.spin);
		double dz = right.z * Math.cos(sp.spin) + up.z * Math.sin(sp.spin);
		double face = Math.abs(Math.sin(sp.spin * 1.7 + sp.seed * 9));
		double len = 0.16 * k;
		int tint = look.color(sp.seed * 0.35);
		int glassColour = Fx.withAlpha(Fx.lerp(tint, 0xFFE8F4FF, 0.55), (float) (200 * look.alpha() * fade));
		int tip = Fx.withAlpha(0xFFFFFFFF, (float) (230 * look.alpha() * fade));
		fx.additive(true);
		fx.shard(sp.x - dx * len / 2, sp.y - dy * len / 2, sp.z - dz * len / 2, dx, dy, dz, len, 0.05 * k * (0.25 + 0.75 * face), glassColour, tip);
		if (face > 0.93) {
			fx.bloom(true);
			fx.sparkle(sp.x, sp.y, sp.z, 0.12 * k * (face - 0.93) / 0.07, Fx.withAlpha(0xFFFFFFFF, (float) (255 * fade)), sp.spin);
			fx.bloom(false);
		}
	}
	private void starburst(Fx fx, Hit h, double age, double scale, int spikes) {
		if (age < 0) {
			return;
		}
		double life = 0.17;
		double t = age / life;
		if (t >= 1) {
			return;
		}
		Vec3 right = fx.cameraRight(), up = fx.cameraUp();
		Vec3 at = h.at().subtract(h.dir().scale(0.15));
		float alpha = look.alpha();
		int colour = look.color(0);
		double grow = 1 - Math.pow(1 - Math.min(1, t * 3.5), 3);
		fx.additive(true);
		for (int i = 0; i < spikes; i++) {
			double a = Math.PI * 2 * i / spikes + noise(h.seed(), i) * 0.35;
			double reach = (0.32 + 0.45 * Math.abs(noise(h.seed(), i + 40))) * scale * (i % 2 == 0 ? 1 : 0.6);
			double tipAt = reach * grow * (1 + t * 0.35);
			double baseAt = reach * Math.max(0, (t - 0.25) / 0.75) * 1.1;
			double length = tipAt - baseAt;
			if (length <= 0.005) {
				continue;
			}
			double dx = right.x * Math.cos(a) + up.x * Math.sin(a);
			double dy = right.y * Math.cos(a) + up.y * Math.sin(a);
			double dz = right.z * Math.cos(a) + up.z * Math.sin(a);
			double width = 0.07 * scale * (1 - t) * (i % 2 == 0 ? 1 : 0.7);
			int base = Fx.withAlpha(Fx.lerp(0xFFFFFFFF, colour, t * 0.7), 255 * alpha);
			int tip = Fx.withAlpha(colour, (float) (180 * alpha * (1 - t)));
			fx.shard(at.x + dx * baseAt, at.y + dy * baseAt, at.z + dz * baseAt, dx, dy, dz, length, width, base, tip);
			fx.bloom(true);
			fx.shard(at.x + dx * baseAt, at.y + dy * baseAt, at.z + dz * baseAt, dx, dy, dz, length, width * 2.5, Fx.fade(base, 0.5), tip);
			fx.bloom(false);
		}
		if (t < 0.6) {
			double f = 1 - t / 0.6;
			fx.light(at.x, at.y, at.z, 0.22 * scale * (0.6 + 0.4 * f), Fx.withAlpha(0xFFFFFFFF, (float) (255 * alpha * f)), 1);
			fx.bloom(true);
			fx.glow(at.x, at.y, at.z, 0.6 * scale, Fx.withAlpha(colour, (float) (220 * alpha * f)));
			fx.bloom(false);
		}
	}
	private void ring(Fx fx, Hit h, double age, double scale) {
		double life = 0.28;
		double t = age / life;
		if (t >= 1) {
			return;
		}
		Vec3 at = h.at().subtract(h.dir().scale(0.15));
		double e = 1 - Math.pow(1 - t, 3);
		int colour = look.color(0.2);
		float alpha = look.alpha();
		fx.additive(true);
		fx.softRing(at.x, at.y, at.z, (0.12 + 0.55 * e) * scale, Fx.withAlpha(Fx.lerp(0xFFFFFFFF, colour, t), (float) (255 * alpha * (1 - t) * (1 - t))),
				0.6 * (1 - t));
		if (h.crit() && t > 0.15) {
			double t2 = (t - 0.15) / 0.85;
			double e2 = 1 - Math.pow(1 - t2, 3);
			fx.softRing(at.x, at.y, at.z, (0.1 + 0.9 * e2) * scale, Fx.withAlpha(colour, (float) (200 * alpha * (1 - t2) * (1 - t2))), 0.2);
		}
	}
	private void cross(Fx fx, Hit h, double age, double scale) {
		double life = 0.14;
		double t = age / life;
		if (t >= 1) {
			return;
		}
		Vec3 right = fx.cameraRight(), up = fx.cameraUp();
		Vec3 at = h.at().subtract(h.dir().scale(0.2));
		double len = 1.3 * scale * (0.7 + 0.3 * t);
		int c = Fx.withAlpha(Fx.lerp(0xFFFFFFFF, look.color(0), t), (float) (255 * look.alpha() * (1 - t)));
		fx.additive(true);
		for (int k = -1; k <= 1; k += 2) {
			double a = Math.PI / 4 * k + noise(h.seed(), 90) * 0.2;
			double dx = right.x * Math.cos(a) + up.x * Math.sin(a);
			double dy = right.y * Math.cos(a) + up.y * Math.sin(a);
			double dz = right.z * Math.cos(a) + up.z * Math.sin(a);
			fx.streak(at.x, at.y, at.z, dx, dy, dz, len, 0.07 * scale * (1 - t), c);
			fx.bloom(true);
			fx.streak(at.x, at.y, at.z, dx, dy, dz, len, 0.22 * scale * (1 - t), Fx.fade(c, 0.6));
			fx.bloom(false);
		}
	}
	private void slash(Fx fx, Hit h, double age, double scale) {
		drawArc(fx, h, age, scale, 0.62, 0.16, 1, 0);
		drawArc(fx, h, age - 0.035, scale * 1.12, 0.62, 0.05, 0.55, 0.25);
		starburst(fx, h, age - 0.03, scale * 0.6, 6);
	}
	private void drawArc(Fx fx, Hit h, double age, double scale, double radius, double thickness, double strength, double tilt) {
		if (age <= 0) {
			return;
		}
		double sweep = Math.min(1, age / 0.06);
		double fade = age < 0.06 ? 1 : Math.max(0, 1 - (age - 0.06) / 0.22);
		if (fade <= 0) {
			return;
		}
		Vec3 right = fx.cameraRight();
		Vec3 up = fx.cameraUp();
		double angle = noise(h.seed(), 7) * 0.6 + 0.55 * h.side() + tilt * h.side();
		double cs = Math.cos(angle), sn = Math.sin(angle);
		Vec3 e1 = right.scale(cs * h.side()).add(up.scale(sn));
		Vec3 e2 = right.scale(-sn * h.side()).add(up.scale(cs));
		Vec3 c = h.at().subtract(h.dir().scale(0.45));
		double r = radius * scale;
		double head = sweep;
		double tail = Math.max(0, sweep - 0.7 - (1 - fade) * 0.3);
		if (head - tail < 0.02) {
			return;
		}
		int n = 20;
		double[] path = new double[n * 3];
		double[] width = new double[n];
		int[] colours = new int[n];
		int colour = look.color(0);
		float alpha = look.alpha();
		for (int j = 0; j < n; j++) {
			double along = (double) j / (n - 1);
			double phi = ((tail + (head - tail) * along) * 2 - 1) * 1.2;
			Vec3 pt = c.add(e1.scale(Math.sin(phi) * r)).add(e2.scale((Math.cos(phi) - 0.7) * r * 0.5));
			path[j * 3] = pt.x;
			path[j * 3 + 1] = pt.y;
			path[j * 3 + 2] = pt.z;
			width[j] = thickness * scale * Math.pow(Math.sin(Math.PI * Math.pow(along, 0.75)), 1.5) * (0.5 + 0.5 * fade);
			colours[j] = Fx.withAlpha(Fx.lerp(colour, 0xFFFFFFFF, along * 0.8), (float) (255 * alpha * strength * fade * (0.2 + 0.8 * along)));
		}
		fx.additive(true);
		fx.ribbon(path, n, width, colours, 0.9);
		fx.bloom(true);
		double[] wide = width.clone();
		for (int j = 0; j < n; j++) {
			wide[j] *= 2.2;
		}
		fx.ribbon(path, n, wide, colours, 0.3);
		fx.bloom(false);
	}
	private void electric(Fx fx, Hit h, double age, double scale) {
		double life = 0.32;
		double t = age / life;
		if (t >= 1) {
			return;
		}
		Vec3 at = h.at().subtract(h.dir().scale(0.15));
		int colour = look.color(0);
		float alpha = look.alpha();
		int strike = (int) (age * 30);
		double flicker = 0.55 + 0.45 * Math.abs(noise(h.seed() + strike, 3));
		int forks = h.crit() ? 7 : 5;
		fx.additive(true);
		for (int i = 0; i < forks; i++) {
			double s = h.seed() + strike * 13.1 + i * 7.7;
			Vec3 d = new Vec3(noise(s, 1), noise(s, 2) * 0.8, noise(s, 3)).normalize();
			double reach = (0.45 + 0.5 * Math.abs(noise(s, 4))) * scale * (0.6 + 0.4 * Math.min(1, t * 6));
			Vec3 end = at.add(d.scale(reach));
			int bright = Fx.withAlpha(0xFFFFFFFF, (float) (255 * alpha * (1 - t) * flicker));
			int edge = Fx.withAlpha(colour, (float) (230 * alpha * (1 - t)));
			fx.arc(at.x, at.y, at.z, end.x, end.y, end.z, 7, 0.09 * scale, 0.022 * scale, bright, edge, s);
			fx.bloom(true);
			fx.arc(at.x, at.y, at.z, end.x, end.y, end.z, 7, 0.09 * scale, 0.07 * scale, Fx.fade(edge, 0.7), Fx.fade(edge, 0.4), s);
			fx.bloom(false);
			if (Math.abs(noise(s, 5)) > 0.4) {
				Vec3 mid = at.add(end).scale(0.5);
				Vec3 b = mid.add(new Vec3(noise(s, 6), noise(s, 7), noise(s, 8)).normalize().scale(reach * 0.4));
				fx.arc(mid.x, mid.y, mid.z, b.x, b.y, b.z, 4, 0.05 * scale, 0.014 * scale, edge, Fx.fade(edge, 0.3), s + 1);
			}
		}
		fx.light(at.x, at.y, at.z, 0.18 * scale * flicker, Fx.withAlpha(0xFFFFFFFF, (float) (255 * alpha * (1 - t) * flicker)), 1);
		fx.bloom(true);
		fx.glow(at.x, at.y, at.z, 0.7 * scale * flicker, Fx.withAlpha(colour, (float) (200 * alpha * (1 - t))));
		fx.bloom(false);
	}
	private static double noise(double seed, int index) {
		double v = Math.sin(seed * 12.9898 + index * 78.233) * 43758.5453;
		return (v - Math.floor(v)) * 2 - 1;
	}
	@Override
	protected void onDisable() {
		sparks.clear();
		hits.clear();
		level = null;
	}
}
