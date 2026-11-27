package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageEffects extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource, nz.temple.client.render.GlowUser {
	private static final int MAX_BURSTS = 24;
	private static final int MAX_SPARKS = 400;
	private final ModeSetting who;
	private final ModeSetting style;
	private final NumberSetting sparkCount;
	private final NumberSetting size;
	private final NumberSetting lifetime;
	private final BoolSetting lowHealth;
	private final NumberSetting lowHealthAt;
	private final BoolSetting damageRed;
	private final nz.temple.client.modules.cosmetic.Look look;
	private final List<double[]> splats = new ArrayList<>();
	private final List<double[]> mists = new ArrayList<>();
	private final Map<Integer, Integer> hurtFrames = new HashMap<>();
	private final List<Burst> bursts = new ArrayList<>();
	private final List<Spark> sparks = new ArrayList<>();
	private ClientLevel level;
	private long lastNanos = System.nanoTime();
	private int ticks;
	private record Burst(Vec3 at, double height, long born) {
	}
	private static final class Spark {
		double x, y, z, vx, vy, vz;
		final long born = System.nanoTime();
		final double life;
		final double phase;

		double drop = 1;
		double spin = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
		final double spinRate = ThreadLocalRandom.current().nextDouble(-16, 16);
		Spark(double x, double y, double z, double vx, double vy, double vz, double life, double phase) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.life = life;
			this.phase = phase;
		}
	}
	public DamageEffects() {
		super("Damage Effects", Category.COSMETICS, "Shows an effect where damage is dealt, and a warning pulse around you when your health "
				+ "is low.");
		who = add(new ModeSetting("Who", List.of("Players", "Everything", "Self"), "Everything")
				.describe("Whose damage to show."));
		style = add(new ModeSetting("Style", List.of("Bleed", "Shockwave", "Shatter"), "Bleed")
				.describe("Bleed sprays drops from the hit that streak as they fly and splash on the ground, with a fine mist. Shockwave "
						+ "rolls a ring of light out round the body at the height of the hit. Shatter bursts glinting splinters off the "
						+ "body."));
		group();
		sparkCount = add(new NumberSetting("Pieces", 12, 2, 40, 1, "").describe("Drops or fragments per hit.")
				.visibleWhen(() -> !style.is("Shockwave")));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Effect size."));
		lifetime = add(new NumberSetting("Lifetime", 0.7, 0.2, 2.5, 0.05, " s").describe("How long the effect lasts."));
		group();
		lowHealth = add(new BoolSetting("Low health pulse", true)
				.describe("A pulsing ring around you when your health is below the threshold."));
		lowHealthAt = add(new NumberSetting("Low health at", 8, 1, 19, 1, " hp")
				.describe("Health at which the warning starts.")
				.visibleWhen(lowHealth::get));
		group();
		damageRed = add(new BoolSetting("Damage red", true).describe("Uses dark red. Turn off to use the colour settings below."));
		look = new nz.temple.client.modules.cosmetic.Look(s -> add(s), "Light");
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			clear();
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			boolean self = nz.temple.client.util.Wearer.mine(living);
			if (!matches(living, self) || living.distanceToSqr(mc.player) > 64 * 64) {
				continue;
			}
			int previous = hurtFrames.getOrDefault(living.getId(), 0);
			int current = living.hurtTime;
			hurtFrames.put(living.getId(), current);
			if (current > previous && previous == 0) {
				spawn(living);
			}
		}
		if (++ticks % 40 == 0) {
			hurtFrames.keySet().removeIf(id -> mc.level.getEntity(id) == null);
		}
	}
	private boolean matches(LivingEntity e, boolean self) {
		return switch (who.get()) {
			case "Players" -> e instanceof Player;
			case "Self" -> self;
			default -> true;
		};
	}
	@Override
	public void preview(Vec3 at) {
		level = Minecraft.getInstance().level;
		burst(at.add(0, 0.9, 0), 1.8);
	}

	private void spawn(LivingEntity e) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 c = e.getBoundingBox().getCenter();
		Vec3 away = e == mc.player ? null : c.subtract(mc.player.getEyePosition()).multiply(1, 0, 1);
		burst(c, e.getBbHeight(), away == null || away.lengthSqr() < 1e-6 ? null : away.normalize());
	}
	private void burst(Vec3 c, double height) {
		burst(c, height, null);
	}
	private void burst(Vec3 c, double height, Vec3 away) {
		if (bursts.size() < MAX_BURSTS) {
			bursts.add(new Burst(c, height, System.nanoTime()));
		}
		if (style.is("Shockwave")) {
			return;
		}
		ThreadLocalRandom rand = ThreadLocalRandom.current();
		int n = (int) Math.round(sparkCount.get());
		if (style.is("Bleed")) {
			double ax = away == null ? 0 : away.x, az = away == null ? 0 : away.z;
			if (mists.size() < 32) {
				mists.add(new double[] {c.x, c.y, c.z, ax, az, System.nanoTime(), rand.nextDouble(100)});
			}
			for (int i = 0; i < n * 2 && sparks.size() < MAX_SPARKS; i++) {
				double drop = Math.pow(rand.nextDouble(), 1.8);
				double speed = rand.nextDouble(1.5, 4.5) * (1.3 - drop * 0.6);
				double yaw = rand.nextDouble(Math.PI * 2);
				double spread = away == null ? 1 : 0.55;
				double vx = Math.cos(yaw) * spread + ax * 1.2, vz = Math.sin(yaw) * spread + az * 1.2;
				double l = Math.sqrt(vx * vx + vz * vz);
				Spark sp = new Spark(c.x + ax * 0.25, c.y + rand.nextDouble(-0.2, 0.25), c.z + az * 0.25, vx / l * speed,
						rand.nextDouble(0.5, 3.5), vz / l * speed, lifetime.get() * rand.nextDouble(0.9, 1.6) + 1, rand.nextDouble(Math.PI * 2));
				sp.drop = 0.35 + drop * 0.65;
				sparks.add(sp);
			}
			return;
		}
		for (int i = 0; i < n && sparks.size() < MAX_SPARKS; i++) {
			double yaw = rand.nextDouble(Math.PI * 2);
			double pitch = rand.nextDouble(-0.6, 1.0);
			double speed = rand.nextDouble(2.5, 6);
			sparks.add(new Spark(c.x, c.y, c.z,
					Math.cos(yaw) * Math.cos(pitch) * speed,
					Math.sin(pitch) * speed + 1.5,
					Math.sin(yaw) * Math.cos(pitch) * speed,
					lifetime.get() * rand.nextDouble(0.9, 1.6), rand.nextDouble(Math.PI * 2)));
		}
	}
	private int color(double offset, double fade) {
		if (damageRed.get()) {
			int red = Fx.lerp(0xFF9A0F14, 0xFFE0303A, (Math.sin(offset * 6.283) + 1) / 2);
			return Fx.withAlpha(red, (float) (255 * look.alpha() * Math.max(0, Math.min(1, fade))));
		}
		return look.color(offset, fade);
	}
	public boolean glowing() {
		return isEnabled() && (!bursts.isEmpty() || !sparks.isEmpty() || lowHealth.get());
	}
	public void emit(Fx fx, float partial) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null || level != mc.level) {
			clear();
			return;
		}
		fx.throughWalls(false);
		double s = size.get();
		float alpha = look.alpha();
		Vec3 ax = new Vec3(1, 0, 0), az = new Vec3(0, 0, 1);
		net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
		bursts.removeIf(b -> now - b.born() > 700_000_000L);
		for (Burst b : bursts) {
			double t = (now - b.born()) / 0.7e9;
			double e = 1 - Math.pow(1 - t, 3);
			Vec3 at = b.at();
			if (style.is("Shockwave")) {
				fx.additive(true);
				if (t < 0.18) {
					double f = 1 - t / 0.18;
					fx.light(at.x, at.y, at.z, 0.35 * s * (0.5 + 0.5 * f), Fx.withAlpha(0xFFFFFFFF, (float) (255 * alpha * f)), 1);
					fx.bloom(true);
					fx.glow(at.x, at.y, at.z, 0.9 * s, color(0.1, f));
					fx.bloom(false);
				}
				double radius = (0.3 + e * 1.7) * s;
				int ringColour = Fx.withAlpha(Fx.lerp(0xFFFFFFFF, color(0, 1), Math.min(1, t * 2.5)), (float) (255 * alpha * (1 - t) * (1 - t)));
				fx.softRing(at.x, at.y, at.z, ax, az, radius, ringColour, 0.5 * (1 - t));
				fx.bloom(true);
				fx.softRing(at.x, at.y, at.z, ax, az, radius * 1.04, Fx.fade(ringColour, 0.7), 0);
				fx.bloom(false);
				if (t > 0.12) {
					double t2 = (t - 0.12) / 0.88;
					double r2 = (0.3 + (1 - Math.pow(1 - t2, 3)) * 1.25) * s;
					fx.softRing(at.x, at.y, at.z, ax, az, r2, color(0.3, 0.55 * (1 - t2) * (1 - t2)), 0.2);
				}
				double floor = at.y - b.height() / 2 + 0.03;
				fx.softRing(at.x, floor, at.z, ax, az, radius * 0.85, color(0.2, 0.5 * (1 - t)), 0.1);
				for (int i = 0; i < 10; i++) {
					double a = Math.PI * 2 * i / 10 + b.born() % 997 * 0.01;
					double rx = Math.cos(a), rz = Math.sin(a);
					fx.streak(at.x + rx * radius, at.y, at.z + rz * radius, rx, 0, rz, 0.4 * s * (1 - t), 0.03 * s,
							Fx.withAlpha(0xFFFFFFFF, (float) (200 * alpha * (1 - t) * (1 - t))));
				}
				fx.additive(false);
			} else if (t < 0.14) {
				double f = 1 - t / 0.14;
				fx.additive(true);
				fx.bloom(true);
				fx.glow(at.x, at.y, at.z, 0.6 * s, color(0.1, 0.8 * f));
				fx.bloom(false);
				fx.additive(false);
			}
		}
		for (int i = sparks.size() - 1; i >= 0; i--) {
			Spark sp = sparks.get(i);
			double age = (now - sp.born) / 1e9;
			if (age > sp.life) {
				sparks.remove(i);
				continue;
			}
			double t = age / sp.life;
			boolean bleed = style.is("Bleed");
			sp.vy -= (bleed ? 16 : 12) * dt;
			double drag = Math.pow(bleed ? 0.3 : 0.4, dt);
			sp.vx *= drag;
			sp.vz *= drag;
			double ny = sp.y + sp.vy * dt;
			cursor.set(sp.x, ny, sp.z);
			var shape = mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor);
			if (sp.vy < 0 && !shape.isEmpty()) {
				double top = cursor.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
				if (bleed) {
					if (splats.size() < 160) {
						double ang = Math.atan2(sp.vz, sp.vx);
						double sp2 = Math.sqrt(sp.vx * sp.vx + sp.vz * sp.vz);
						splats.add(new double[] {sp.x, top + 0.004, sp.z, now, 0.04 + sp.drop * 0.08, ang, Math.min(2.4, 1 + sp2 * 0.3), sp.phase});
					}
					sparks.remove(i);
					continue;
				}
				sp.vy = -sp.vy * 0.3;
				sp.vx *= 0.5;
				sp.vz *= 0.5;
				ny = top + 0.01;
			}
			sp.x += sp.vx * dt;
			sp.y = ny;
			sp.z += sp.vz * dt;
			sp.spin += sp.spinRate * dt;
			double fade = Math.min(1, (1 - t) * 3);
			if (bleed) {
				drop(fx, sp, s, fade);
			} else {
				splinter(fx, sp, s, fade);
			}
		}
		double splatLife = 3 + lifetime.get() * 2;
		Vec3 gx = new Vec3(1, 0, 0), gz = new Vec3(0, 0, 1);
		fx.additive(false);
		for (int i = splats.size() - 1; i >= 0; i--) {
			double[] sp = splats.get(i);
			double age = (now - (long) sp[3]) / 1e9;
			if (age > splatLife) {
				splats.remove(i);
				continue;
			}
			double grow = 1 - Math.pow(1 - Math.min(1, age * 7), 3);
			double fade = age < splatLife - 1 ? 1 : splatLife - age;
			double dry = Math.min(1, age / splatLife * 1.4);
			int wet = damageRed.get() ? Fx.lerp(0xFF7A0A10, 0xFF320507, dry) : color(sp[7], 1);
			int col = Fx.withAlpha(wet, (float) (230 * alpha * fade));
			double r = sp[4] * s * grow, ang = sp[5], stretch = sp[6], seed = sp[7];
			double ca = Math.cos(ang), sa = Math.sin(ang);
			Vec3 along = new Vec3(ca, 0, sa), across = new Vec3(-sa, 0, ca);
			fx.disc(sp[0], sp[1], sp[2], along, across, r * stretch, r, col);
			if (dry < 0.7) {
				fx.additive(true);
				fx.disc(sp[0] - ca * r * 0.25, sp[1] + 0.001, sp[2] - sa * r * 0.25, along, across, r * stretch * 0.35, r * 0.25,
						Fx.withAlpha(0xFFFFFFFF, (float) (60 * alpha * fade * (1 - dry / 0.7))));
				fx.additive(false);
			}
			for (int k = 0; k < 5; k++) {
				double h = Math.sin(seed * 31 + k * 17.3) * 43758.5453;
				h -= Math.floor(h);
				double off = r * (1.3 + h * 2) * stretch;
				double side = (h - 0.5) * r * 1.6;
				double cx = sp[0] + ca * off - sa * side;
				double cz = sp[2] + sa * off + ca * side;
				double rr = r * (0.12 + h * 0.22);
				fx.disc(cx, sp[1], cz, gx, gz, rr, rr, col);
			}
		}
		mists.removeIf(m -> now - (long) m[5] > 500_000_000L);
		for (double[] m : mists) {
			double age = (now - (long) m[5]) / 1e9;
			double k = age / 0.5;
			int c = damageRed.get() ? 0xFF8A0C14 : color(0.2, 1);
			for (int j = 0; j < 3; j++) {
				double spreadOut = k * (0.5 + j * 0.25);
				fx.smoke(m[0] + m[3] * spreadOut + Math.sin(m[6] + j) * 0.1 * k, m[1] + j * 0.08 - k * 0.1,
						m[2] + m[4] * spreadOut + Math.cos(m[6] + j) * 0.1 * k, (0.18 + k * 0.45) * s,
						Fx.withAlpha(c, (float) (120 * alpha * (1 - k) * (1 - k))), m[6] + j, age + j);
			}
		}
		if (lowHealth.get() && mc.player.getHealth() <= lowHealthAt.get() && mc.player.isAlive()) {
			Vec3 p = mc.player.getPosition(partial);
			double clock = (now / 1e9) % 1.1;
			double pulse = Math.exp(-Math.pow((clock - 0.1) * 14, 2)) + 0.7 * Math.exp(-Math.pow((clock - 0.32) * 14, 2));
			fx.additive(true);
			fx.softPool(p.x, p.y + 0.03, p.z, ax, az, 1.1 * s, color(0.1, 0.25 + 0.35 * pulse));
			for (double beatAt : new double[] {0.1, 0.32}) {
				double since = clock - beatAt;
				if (since > 0 && since < 0.7) {
					double k = since / 0.7;
					fx.softRing(p.x, p.y + 0.04, p.z, ax, az, (0.4 + k * 1.1) * s, color(0, (1 - k) * (1 - k) * (beatAt < 0.2 ? 0.9 : 0.6)), 0.3);
				}
			}
			fx.bloom(true);
			fx.softRing(p.x, p.y + 0.04, p.z, ax, az, (0.7 + pulse * 0.15) * s, color(0, 0.6 * pulse), 0.2);
			fx.bloom(false);
			fx.additive(false);
		}
	}
	private void drop(Fx fx, Spark sp, double s, double fade) {
		double speed = Math.sqrt(sp.vx * sp.vx + sp.vy * sp.vy + sp.vz * sp.vz);
		if (speed < 1e-4) {
			return;
		}
		double w = 0.05 * s * sp.drop;
		double len = w + Math.min(0.35, speed * 0.04) * s;
		double ux = sp.vx / speed, uy = sp.vy / speed, uz = sp.vz / speed;
		int body = damageRed.get() ? Fx.lerp(0xFF8E0C12, 0xFFC0141E, sp.phase / (Math.PI * 2)) : color(sp.phase, 1);
		fx.additive(false);
		fx.streak(sp.x - ux * len / 2, sp.y - uy * len / 2, sp.z - uz * len / 2, sp.vx, sp.vy, sp.vz, len, w,
				Fx.withAlpha(body, (float) (255 * look.alpha() * fade)));
		fx.additive(true);
		fx.light(sp.x, sp.y, sp.z, w * 0.4, Fx.withAlpha(0xFFFFFFFF, (float) (110 * look.alpha() * fade)), 1);
		fx.additive(false);
	}
	private void splinter(Fx fx, Spark sp, double s, double fade) {
		Vec3 right = fx.cameraRight(), up = fx.cameraUp();
		double dx = right.x * Math.cos(sp.spin) + up.x * Math.sin(sp.spin);
		double dy = right.y * Math.cos(sp.spin) + up.y * Math.sin(sp.spin);
		double dz = right.z * Math.cos(sp.spin) + up.z * Math.sin(sp.spin);
		double face = Math.abs(Math.sin(sp.spin * 1.7 + sp.phase * 3));
		double len = 0.2 * s;
		int tint = color(sp.phase, 1);
		fx.additive(true);
		fx.shard(sp.x - dx * len / 2, sp.y - dy * len / 2, sp.z - dz * len / 2, dx, dy, dz, len, 0.06 * s * (0.25 + 0.75 * face),
				Fx.withAlpha(Fx.lerp(tint, 0xFFFFFFFF, 0.3), (float) (230 * look.alpha() * fade)),
				Fx.withAlpha(0xFFFFFFFF, (float) (200 * look.alpha() * fade)));
		if (face > 0.92) {
			fx.bloom(true);
			fx.sparkle(sp.x, sp.y, sp.z, 0.14 * s * (face - 0.92) / 0.08, Fx.withAlpha(0xFFFFFFFF, (float) (255 * fade)), sp.spin);
			fx.bloom(false);
		}
		fx.additive(false);
	}
	private void clear() {
		mists.clear();
		bursts.clear();
		sparks.clear();
		splats.clear();
		hurtFrames.clear();
	}
	@Override
	protected void onDisable() {
		clear();
	}
}
