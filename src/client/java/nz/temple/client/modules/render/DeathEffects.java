package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.modules.client.HudModule;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class DeathEffects extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private static final int MAX_COINS = 400;
	private final ModeSetting style;
	private final ModeSetting who;
	private final BoolSetting ownDeath;
	private final NumberSetting coinCount;
	private final NumberSetting coinSize;
	private final BoolSetting magnet;
	private final NumberSetting magnetDelay;
	private final NumberSetting magnetSpeed;
	private final BoolSetting chime;
	private final NumberSetting ghostLife;
	private final NumberSetting ghostRise;
	private final BoolSetting ghostEchoes;
	private final BoolSetting realism;
	private final NumberSetting pieceCount;
	private final NumberSetting crumbleTime;
	private final ModeSetting crumbleFrom;
	private final nz.temple.client.render.BodyFx bodies = new nz.temple.client.render.BodyFx();
	private final List<RealBody> realBodies = new ArrayList<>();
	private record RealBody(nz.temple.client.render.BodySnapshot.Snap snap, long born, double life, boolean ghost, Vec3 feet, double height,
			double width, double yaw, double seed, double driftX, double driftZ) {
		static RealBody of(nz.temple.client.render.BodySnapshot.Snap snap, double life, boolean ghost, LivingEntity e) {
			ThreadLocalRandom r = ThreadLocalRandom.current();
			double drift = r.nextDouble(Math.PI * 2);
			return new RealBody(snap, System.nanoTime(), life, ghost, e.position(), e.getBbHeight(), e.getBbWidth(), e.yBodyRot,
					r.nextDouble(100), Math.cos(drift), Math.sin(drift));
		}
	}
	private final nz.temple.client.modules.cosmetic.Look look;
	private final Set<Integer> dead = new HashSet<>();
	private final List<Coin> coins = new ArrayList<>();
	private final List<Burst> bursts = new ArrayList<>();
	private ClientLevel level;
	private long lastNanos = System.nanoTime();
	private long lastChime;
	private int ticks;
	private static final class Coin {
		double x, y, z, vx, vy, vz;
		final double spinSpeed;
		final double phase;
		final long born = System.nanoTime();
		boolean landed;
		Coin(double x, double y, double z, double vx, double vy, double vz, double spinSpeed, double phase) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.spinSpeed = spinSpeed;
			this.phase = phase;
		}
	}
	private record Burst(String kind, Vec3 at, double height, long born) {
	}
	static {
		nz.temple.client.util.VanillaFx.corpseHidden = DeathEffects::hidesCorpse;
		nz.temple.client.util.VanillaFx.poofReplaced = DeathEffects::replacesPoof;
	}

	public DeathEffects() {
		super("Death Effects", Category.COSMETICS, "Effects where players and mobs die: coins, souls, shockwaves, ghosts and more. Coins "
				+ "fly to you like experience.");
		instance = this;
		style = add(new ModeSetting("Style", List.of("Coins", "Soul", "Shockwave", "Ghost", "Crumble", "Ashes"), "Coins")
				.describe("Coins bursts into spinning coins that bounce and then fly to you. Soul sends a glowing spirit spiralling upward. "
						+ "Shockwave sends out a ring and a glass dome with a rising pillar. Ghost lifts a see-through copy of the body "
						+ "that drifts away. Crumble breaks the body apart from the head down. Ashes burns the body away from the feet up."));
		realism = add(new BoolSetting("Realism", false).describe("Uses the body of what died, with its real shape, skin and pose. Off "
				+ "uses the original glowing figure.").visibleWhen(() -> style.is("Ghost") || style.is("Crumble") || style.is("Ashes")));
		who = add(new ModeSetting("Who", List.of("Players", "Hostiles", "Everything"), "Everything")
				.describe("Which deaths show the effect."));
		ownDeath = add(new BoolSetting("Your own death", true)
				.describe("Also shows the effect when you die."));
		group();
		coinCount = add(new NumberSetting("Coins", 18, 4, 60, 1, "").describe("Coins per death.").visibleWhen(() -> style.is("Coins")));
		coinSize = add(new NumberSetting("Coin size", 1, 0.4, 2.5, 0.05, "x").describe("Coin size.").visibleWhen(() -> style.is("Coins")));
		magnet = add(new BoolSetting("Collect coins", true).describe("Coins fly to you like experience orbs after landing.")
				.visibleWhen(() -> style.is("Coins")));
		magnetDelay = add(new NumberSetting("Collect delay", 0.5, 0, 5, 0.1, " s").describe("How long coins wait before flying to you.")
				.visibleWhen(() -> style.is("Coins") && magnet.get()));
		magnetSpeed = add(new NumberSetting("Collect speed", 1, 0.3, 3, 0.1, "x").describe("How strongly coins are pulled toward you.")
				.visibleWhen(() -> style.is("Coins") && magnet.get()));
		chime = add(new BoolSetting("Pickup chime", true).describe("Plays a rising chime as coins reach you.")
				.visibleWhen(() -> style.is("Coins") && magnet.get()));
		ghostLife = add(new NumberSetting("Ghost time", 3.5, 1.5, 8, 0.1, " s").describe("How long the ghost takes to fade.")
				.visibleWhen(() -> style.is("Ghost")));
		ghostRise = add(new NumberSetting("Ghost rise", 1, 0.2, 3, 0.1, "x").describe("How high the ghost rises before fading.")
				.visibleWhen(() -> style.is("Ghost")));
		ghostEchoes = add(new BoolSetting("Afterimages", true).describe("Faint copies trailing behind the ghost.")
				.visibleWhen(() -> style.is("Ghost")));
		pieceCount = add(new NumberSetting("Pieces", 260, 60, 700, 10, "").describe("Number of pieces the body breaks into.")
				.visibleWhen(() -> style.is("Crumble") || style.is("Ashes")));
		crumbleTime = add(new NumberSetting("Crumble time", 1.1, 0.2, 4, 0.1, " s").describe("How long the body takes to break apart.")
				.visibleWhen(() -> style.is("Crumble") || style.is("Ashes")));
		crumbleFrom = add(new ModeSetting("Crumble from", List.of("Head", "Feet", "Everywhere"), "Head")
				.describe("Where the body starts breaking.").visibleWhen(() -> style.is("Crumble")));
		group();
		look = new nz.temple.client.modules.cosmetic.Look(s -> add(s), "Gold");
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
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			boolean self = living == mc.player;
			if (self ? !ownDeath.get() : !matches(living)) {
				continue;
			}
			if ((living.isDeadOrDying() || living.getHealth() <= 0) && living.distanceToSqr(mc.player) < 96 * 96) {
				if (dead.add(living.getId())) {
					spawn(living);
				}
			} else {
				dead.remove(living.getId());
			}
		}
		if (++ticks % 40 == 0) {
			dead.removeIf(id -> mc.level.getEntity(id) == null);
		}
	}
	private static volatile DeathEffects instance;
	public static boolean hidesCorpse(Entity e) {
		DeathEffects m = instance;
		if (m == null || !m.isEnabled() || !(e instanceof LivingEntity living) || !living.isDeadOrDying()) {
			return false;
		}
		String s = m.style.get();
		return (s.equals("Ghost") || s.equals("Crumble") || s.equals("Ashes")) && replacesPoof(e);
	}
	public static boolean replacesPoof(Entity e) {
		DeathEffects m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.isEnabled() || !(e instanceof LivingEntity living) || mc.player == null) {
			return false;
		}
		return living == mc.player ? m.ownDeath.get() : m.matches(living);
	}
	private boolean matches(LivingEntity e) {
		return switch (who.get()) {
			case "Players" -> e instanceof Player;
			case "Hostiles" -> e instanceof Player || e instanceof Enemy;
			default -> true;
		};
	}

	@Override
	public void preview(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		level = mc.level;
		double yaw = mc.player == null ? 0 : mc.player.getYRot() + 180;
		burst(nz.temple.client.render.BodyFx.Body.standing(at, yaw), at.add(0, 0.9, 0), null);
	}
	private void spawn(LivingEntity e) {
		nz.temple.client.render.BodySnapshot.Snap snap = realism.get() ? nz.temple.client.render.BodySnapshot.latest(e.getId()) : null;
		if (snap != null) {
			if (style.is("Ghost")) {
				realBodies.add(RealBody.of(snap, ghostLife.get(), true, e));
				return;
			}
			if (style.is("Crumble") || style.is("Ashes")) {
				realBodies.add(RealBody.of(snap, crumbleTime.get() * 0.45, false, e));
			}
		}
		burst(nz.temple.client.render.BodyFx.Body.of(e, 1), e.getBoundingBox().getCenter(),
				snap == null ? null : nz.temple.client.render.SkinPalette.of(snap.texture()));
	}
	private void drawRealBodies(Fx fx, long now) {
		realBodies.removeIf(b -> (now - b.born) / 1e9 > b.life);
		for (RealBody b : realBodies) {
			double t = (now - b.born) / 1e9;
			double f = t / b.life;
			if (!b.ghost) {
				nz.temple.client.render.BodySnapshot.body(b.snap, 0, 0, 0, Fx.withAlpha(0xFFFFFFFF, (float) (255 * Math.pow(1 - f, 1.5))));
				continue;
			}
			double fade = Math.min(1, t / 0.3) * (f < 0.5 ? 1 : 1 - smooth((f - 0.5) / 0.5));
			double shimmer = 0.85 + 0.15 * Math.sin(Theme.time() * 8 + b.seed);
			int c = color(f, 1);
			float a = (float) (255 * fade * shimmer * look.alpha());
			if (ghostEchoes.get()) {
				for (int k = 3; k >= 1; k--) {
					double te = t - k * 0.11;
					if (te > 0) {
						nz.temple.client.render.BodySnapshot.ghost(b.snap, floatMotion(b, te), 1, Fx.withAlpha(c, a * 0.22f / k));
					}
				}
			}
			org.joml.Matrix4f motion = floatMotion(b, t);
			nz.temple.client.render.BodySnapshot.ghost(b.snap, motion, 1, Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.45), a * 0.6f));
			nz.temple.client.render.BodySnapshot.ghost(b.snap, motion, 1.07f, Fx.withAlpha(c, a * 0.22f));
			Vec3 root = floatRoot(b, t);
			int samples = 14;
			double[] path = new double[samples * 3];
			double[] radius = new double[samples];
			int[] colours = new int[samples];
			int low = Fx.lerp(color(0.6, 1), 0xFFFFFFFF, 0.35);
			for (int i = 0; i < samples; i++) {
				double back = (samples - 1 - i) * 0.07;
				Vec3 r = floatRoot(b, Math.max(0, t - back));
				double w = i / (double) (samples - 1);
				path[i * 3] = r.x;
				path[i * 3 + 1] = r.y - b.height * 0.32 - back * 0.6;
				path[i * 3 + 2] = r.z;
				radius[i] = b.width * 0.32 * w * w;
				colours[i] = Fx.withAlpha(low, a * 0.35f * (float) w);
			}
			nz.temple.client.render.Meshes mesh = fx.mesh();
			mesh.material(nz.temple.client.render.Meshes.Material.MATTE);
			mesh.emission(0.8f);
			mesh.tube(path, samples, radius, colours, 7);
			fx.additive(true);
			fx.glow(root.x, root.y, root.z, 1.1 * b.height / 1.8, Fx.withAlpha(c, a * 0.27f * Math.max(0.4f, look.bloom())));
			for (int k = 0; k < 10; k++) {
				double ph = (f * 1.6 + k * 0.1) % 1;
				double ang = k * 2.399 + f * 3;
				double r = (0.35 + 0.15 * Math.sin(k * 1.7)) * Math.max(0.6, b.width / 0.6);
				fx.light(root.x + Math.cos(ang) * r, root.y - b.height * 0.6 + ph * b.height * 1.2, root.z + Math.sin(ang) * r, 0.03,
						color(k / 10.0, Math.sin(Math.PI * ph) * fade), 0.8);
			}
			fx.additive(false);
		}
	}
	private Vec3 floatRoot(RealBody b, double t) {
		double u = Math.max(0, Math.min(1, t / b.life));
		double seed = b.seed;
		double rise = (0.25 * smooth(t / 0.6) + (u * u * 0.65 + u * 0.35) * 3.2) * ghostRise.get() * Math.max(0.6, b.height / 1.8);
		double sway = 0.22 + u * 0.35;
		double dx = Math.sin(t * 1.25 + seed) * sway * u + b.driftX * u * u * 1.6 + Math.sin(t * 2.9 + seed * 3) * 0.04;
		double dz = Math.cos(t * 1.05 + seed * 1.7) * sway * u + b.driftZ * u * u * 1.6 + Math.cos(t * 3.3 + seed * 2) * 0.04;
		double bob = Math.sin(t * 2.4 + seed) * 0.07 * smooth(t / 0.5);
		return new Vec3(b.feet.x + dx, b.feet.y + b.height * 0.55 + rise + bob, b.feet.z + dz);
	}
	private org.joml.Matrix4f floatMotion(RealBody b, double t) {
		double u = Math.max(0, Math.min(1, t / b.life));
		double seed = b.seed;
		Vec3 root = floatRoot(b, t);
		float facing = (float) Math.toRadians(-b.yaw);
		float turn = (float) Math.toRadians(Math.sin(t * 0.8 + seed) * 14 + u * 40 * (seed > 50 ? 1 : -1));
		float pitch = (float) Math.toRadians(Math.sin(t * 1.6 + seed) * 6 - 10 * smooth(t / 1.2));
		float roll = (float) Math.toRadians(Math.sin(t * 1.3 + seed * 2) * 8);
		float breathe = (float) (1 + Math.sin(t * 3.1 + seed) * 0.035);
		float stretch = (float) (1 + u * 0.18);
		return new org.joml.Matrix4f()
				.translation((float) root.x, (float) root.y, (float) root.z)
				.rotateY(turn)
				.rotateY(facing).rotateX(pitch).rotateZ(roll).rotateY(-facing)
				.scale(breathe, stretch, breathe)
				.translate((float) -b.feet.x, (float) (-b.feet.y - b.height * 0.55), (float) -b.feet.z);
	}
	private static double smooth(double x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}
	private void burst(nz.temple.client.render.BodyFx.Body body, Vec3 c, int[] palette) {
		Vec3 feet = body.feet();
		double height = body.height();
		if (style.is("Ghost")) {
			bodies.ghost(body, look, ghostLife.get(), ghostRise.get(), ghostEchoes.get());
			return;
		}
		if (style.is("Crumble")) {
			bodies.breakUp(body, look, (int) Math.round(pieceCount.get()), crumbleTime.get(), crumbleFrom.get(),
					nz.temple.client.render.BodyFx.Breakup.CRUMBLE, palette);
			return;
		}
		if (style.is("Ashes")) {
			bodies.breakUp(body, look, (int) Math.round(pieceCount.get()), crumbleTime.get() * 1.4, "Feet",
					nz.temple.client.render.BodyFx.Breakup.DISSOLVE, palette);
			return;
		}
		if (!style.is("Coins")) {
			bursts.add(new Burst(style.get(), feet, height, System.nanoTime()));
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int n = (int) Math.round(coinCount.get());
		for (int i = 0; i < n && coins.size() < MAX_COINS; i++) {
			double yaw = r.nextDouble(Math.PI * 2);
			double out = r.nextDouble(1.2, 3.8);
			coins.add(new Coin(c.x, c.y, c.z, Math.cos(yaw) * out, r.nextDouble(4, 8.5), Math.sin(yaw) * out,
					r.nextDouble(5, 11) * (r.nextBoolean() ? 1 : -1), r.nextDouble(Math.PI * 2)));
		}
		bursts.add(new Burst("Flash", c, height, System.nanoTime()));
	}
	private int color(double offset, double fade) {
		return look.color(offset * 0.4, fade);
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
		nz.temple.client.render.Meshes mesh = fx.mesh();
		float bright = 1;
		Vec3 target = mc.player.getPosition(partial).add(0, 0.9, 0);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		double time = now / 1e9;
		for (int i = coins.size() - 1; i >= 0; i--) {
			Coin c = coins.get(i);
			double age = (now - c.born) / 1e9;
			double radius = 0.16 * coinSize.get();
			boolean pulling = magnet.get() && age > magnetDelay.get() + 0.35;
			if (pulling) {
				double dx = target.x - c.x, dy = target.y - c.y, dz = target.z - c.z;
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (dist < 0.9 || dist > 160) {
					if (dist < 0.9) {
						collect(mc, c);
					}
					coins.remove(i);
					continue;
				}
				double pull = magnetSpeed.get() * (26 + 40 / Math.max(dist, 0.6));
				c.vx += dx / dist * pull * dt;
				c.vy += dy / dist * pull * dt;
				c.vz += dz / dist * pull * dt;
				double damp = Math.pow(0.08, dt);
				c.vx *= damp;
				c.vy *= damp;
				c.vz *= damp;
			} else {
				c.vy -= 16 * dt;
				double drag = Math.pow(c.landed ? 0.02 : 0.5, dt);
				c.vx *= drag;
				c.vz *= drag;
			}
			double nx = c.x + c.vx * dt, ny = c.y + c.vy * dt, nz = c.z + c.vz * dt;
			if (!pulling) {
				cursor.set(nx, ny - radius, nz);
				if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty() && c.vy < 0) {
					ny = Math.floor(ny - radius) + 1 + radius;
					c.vy = Math.abs(c.vy) > 1.2 ? -c.vy * 0.42 : 0;
					c.landed = c.vy == 0;
				}
				cursor.set(nx, c.y, c.z);
				if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
					c.vx = -c.vx * 0.4;
					nx = c.x;
				}
				cursor.set(c.x, c.y, nz);
				if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
					c.vz = -c.vz * 0.4;
					nz = c.z;
				}
			}
			c.x = nx;
			c.y = ny;
			c.z = nz;
			double life = magnet.get() ? 30 : 6;
			float fade = (float) Math.min(1, Math.min(age * 6, (life - age) * 2));
			if (fade <= 0) {
				coins.remove(i);
				continue;
			}
			double spin = c.phase + time * (c.landed && !pulling ? c.spinSpeed * 0.35 : c.spinSpeed);
			double bob = c.landed && !pulling ? Math.sin(time * 3 + c.phase) * 0.04 : 0;
			org.joml.Quaternionf turn = new org.joml.Quaternionf().rotationY((float) spin).rotateX((float) (Math.PI / 2));
			look.apply(mesh, pulling ? 0.4f : 0.1f);
			int face = color(c.phase / (Math.PI * 2), fade);
			int rim = Fx.lerp(face, 0xFF3A2400 | (face & 0xFF000000), 0.3);
			mesh.disc(c.x, c.y + bob, c.z, turn, radius, radius * 0.22, face, rim, 20);
			if (pulling && look.bloom() > 0.2f) {
				fx.additive(true);
				fx.glow(c.x, c.y + bob, c.z, radius * 2.6, color(c.phase, fade * 0.3 * look.bloom()));
				fx.additive(false);
			}
		}
		bodies.emit(fx);
		drawRealBodies(fx, now);
		bursts.removeIf(b -> now - b.born() > 2_400_000_000L);
		for (Burst b : bursts) {
			double t = (now - b.born()) / 1e9;
			switch (b.kind()) {
				case "Flash" -> flash(fx, b, t);
				case "Soul" -> soul(fx, mesh, b, t);
				case "Shockwave" -> shockwave(fx, mesh, b, t);
				case "Sparkle" -> sparkle(fx, mesh, b, t);
				default -> {
				}
			}
		}
	}
	private void collect(Minecraft mc, Coin c) {
		bursts.add(new Burst("Sparkle", new Vec3(c.x, c.y, c.z), 0, System.nanoTime()));
		long now = System.currentTimeMillis();
		if (chime.get() && now - lastChime > 45) {
			lastChime = now;
			float pitch = 1.2f + ThreadLocalRandom.current().nextFloat() * 0.7f;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch));
		}
	}
	private static final org.joml.Quaternionf FLAT = new org.joml.Quaternionf();
	private void flash(Fx fx, Burst b, double t) {
		if (t > 0.5 || look.bloom() < 0.2f) {
			return;
		}
		double e = 1 - Math.pow(1 - t / 0.5, 3);
		fx.additive(true);
		fx.glow(b.at().x, b.at().y, b.at().z, 0.8 + e * 1.4, color(0, (1 - t / 0.5) * 0.5 * look.bloom()));
		fx.additive(false);
	}
	private void sparkle(Fx fx, nz.temple.client.render.Meshes mesh, Burst b, double t) {
		if (t > 0.3) {
			return;
		}
		double u = t / 0.3;
		mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
		mesh.emission(0.4f);
		mesh.sphere(b.at().x, b.at().y, b.at().z, 0.08 + u * 0.25, color(0.3, 1 - u), 6);
	}
	private void soul(Fx fx, nz.temple.client.render.Meshes mesh, Burst b, double t) {
		double life = 2.2;
		if (t > life) {
			return;
		}
		double u = t / life;
		double fade = Math.sin(Math.PI * Math.min(1, u * 1.15));
		double baseY = b.at().y + b.height() * 0.35;
		int samples = 24;
		double[] path = new double[samples * 3];
		double[] radius = new double[samples];
		int[] colours = new int[samples];
		for (int i = 0; i < samples; i++) {

			double k = u - (samples - 1 - i) / (double) (samples - 1) * 0.25;
			k = Math.max(0, k);
			double angle = k * 14;
			double r = 0.45 * (1 - k * 0.7);
			double rise = k * k * 3.6 * 1.5 + k * b.height() * 0.4;
			path[i * 3] = b.at().x + Math.cos(angle) * r;
			path[i * 3 + 1] = baseY + rise;
			path[i * 3 + 2] = b.at().z + Math.sin(angle) * r;
			double w = i / (double) (samples - 1);
			radius[i] = 0.07 * w * w;
			colours[i] = color(u + w * 0.3, fade * w);
		}
		int head = (samples - 1) * 3;
		double hx = path[head];
		double hy = path[head + 1];
		double hz = path[head + 2];
		double[] wide = new double[samples];
		int[] soft = new int[samples];
		for (int i = 0; i < samples; i++) {
			double w = i / (double) (samples - 1);
			wide[i] = 0.5 * Math.sin(Math.PI * Math.min(1, w * 1.1)) * w + 0.02;
			soft[i] = color(u + w * 0.3, fade * w * 0.75);
		}
		fx.additive(true);
		fx.ribbon(path, samples, wide, soft, 0.5);
		for (int i = 2; i < samples - 2; i += 3) {
			double w = i / (double) (samples - 1);
			double drift = (1 - w) * 0.25;
			fx.wisp(path[i * 3] + Math.sin(i * 1.7) * drift, path[i * 3 + 1] + drift * 0.5, path[i * 3 + 2] + Math.cos(i * 1.3) * drift,
					0.18 * w + 0.05, color(u + 0.2, fade * w * 0.5), i * 3.3 + t);
		}
		fx.light(hx, hy, hz, 0.42, color(u, fade), 0.75);
		fx.glow(hx, hy, hz, 1.1, color(u, fade * 0.4 * Math.max(0.5f, look.bloom())));
		fx.additive(false);
		look.apply(mesh, 0.7f);
		mesh.ellipsoid(hx, hy, hz, 0.11, 0.15, 0.11, null, color(u, fade * 0.9), 10);
		if (t < 0.7) {
			double e = t / 0.7;
			double r = 0.3 + (1 - Math.pow(1 - e, 3)) * 2;
			look.apply(mesh, 0.2f);
			mesh.torus(b.at().x, b.at().y + 0.04, b.at().z, FLAT, r, 0.04 * (1 - e) + 0.008, 0, 48,
					(uu, vv) -> color(uu * 0.5, 1 - e));
		}
	}
	private void shockwave(Fx fx, nz.temple.client.render.Meshes mesh, Burst b, double t) {
		double life = 1.1;
		if (t > life) {
			return;
		}
		double e = 1 - Math.pow(1 - Math.min(1, t / 0.8), 3);
		double fade = Math.max(0, 1 - t / 0.8);
		double r = 0.4 + e * 3.6;
		look.apply(mesh, 0.4f);
		mesh.torus(b.at().x, b.at().y + 0.05, b.at().z, FLAT, r, 0.08 * fade + 0.01, 0, 64, (u, v) -> color(u, fade));
		mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
		mesh.emission(0.05f);
		double dome = r * 0.5 * Math.sin(Math.min(1, t / 0.8) * Math.PI);
		if (dome > 0.05) {
			mesh.ellipsoid(b.at().x, b.at().y, b.at().z, r, dome, r, null, color(0.3, 0.4 * fade), 14);
		}
		double pillarFade = Math.max(0, 1 - t / life);
		double height = 1 + Math.min(1, t * 3) * 5;
		double width = 0.45 * (0.4 + 0.6 * pillarFade);
		mesh.ellipsoid(b.at().x, b.at().y + height * 0.5, b.at().z, width, height * 0.5, width, null,
				color(0.1, 0.3 * pillarFade), 12);
		if (look.bloom() > 0.2f) {
			fx.additive(true);
			fx.glow(b.at().x, b.at().y + 1, b.at().z, 1.6, color(0, pillarFade * 0.35 * look.bloom()));
			fx.additive(false);
		}
	}
	private void clear() {
		coins.clear();
		bursts.clear();
		dead.clear();
		bodies.clear();
	}
	@Override
	protected void onDisable() {
		clear();
	}
}
