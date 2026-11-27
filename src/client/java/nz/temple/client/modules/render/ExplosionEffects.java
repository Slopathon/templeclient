package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.client.render.WorldPost;
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

public final class ExplosionEffects extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private static final int MAX = 24;
	private final BoolSetting crystals;
	private final BoolSetting tnt;
	private final ModeSetting style;
	private final NumberSetting size;
	private final NumberSetting duration;
	private final NumberSetting debris;
	private final BoolSetting smoke;
	private final BoolSetting others;
	private final BoolSetting impact;
	private final ModeSetting impactStyle;
	private final NumberSetting impactLength;
	private final NumberSetting impactRange;
	private final NumberSetting impactGap;
	private final BoolSetting shockwave;
	private final NumberSetting shockSpeed;
	private final NumberSetting shockWidth;
	private final NumberSetting shockGlow;
	private final BoolSetting shockGrid;
	private static volatile ExplosionEffects instance;
	private final java.util.Set<Integer> exploded = new java.util.HashSet<>();
	private long lastImpact;
	private final BoolSetting autoColors;
	private final nz.temple.client.modules.cosmetic.Look look;
	private final Map<Integer, Entity> tracked = new HashMap<>();
	private final Map<Integer, Integer> fuses = new HashMap<>();
	private final List<Blast> blasts = new ArrayList<>();
	private ClientLevel level;
	private record Blast(double x, double y, double z, boolean crystal, long born, int seed, boolean impact) {
	}
	static {
		nz.temple.client.util.VanillaFx.explosion = ExplosionEffects::onExplosion;
		nz.temple.client.util.VanillaFx.explosionReplaced = ExplosionEffects::replaces;
	}
	public ExplosionEffects() {
		super("Explosion Effects", Category.COSMETICS, "Replaces crystal and TNT explosions with a fireball, implosion or nova effect.");
		crystals = add(new BoolSetting("End crystals", true).describe("Show an effect when an end crystal explodes."));
		tnt = add(new BoolSetting("TNT", true).describe("Show an effect when TNT explodes."));
		others = add(new BoolSetting("Other explosions", true).describe("Creepers, beds, respawn anchors, TNT minecarts and any other "
				+ "explosion the server reports."));
		group();
		style = add(new ModeSetting("Style", List.of("Realistic", "Implosion", "Nova"), "Realistic")
				.describe("Realistic is a fireball of rolling flame that cools into rising smoke, with a dust ring, debris and sparks. "
						+ "Implosion pulls crystals inward, then bursts outward. Nova shows angled shock rings, a glass dome and spikes of light."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Blast size."));
		duration = add(new NumberSetting("Duration", 1.2, 0.4, 4, 0.1, " s").describe("How long a blast lasts."));
		debris = add(new NumberSetting("Debris", 14, 0, 40, 1, "").describe("Debris pieces per explosion."));
		smoke = add(new BoolSetting("Smoke", true).describe("Adds a smoke column after a Fireball.")
				.visibleWhen(() -> style.is("Realistic")));
		group();
		autoColors = add(new BoolSetting("Blast colours", true)
				.describe("Violet for crystals and orange for TNT. Off uses the colour settings below."));
		look = new nz.temple.client.modules.cosmetic.Look(x -> add(x), "Light");
		group("Impact frames");
		impact = add(new BoolSetting("Impact frames", false).describe("Flashes a few frames of black-and-white ink around the explosion, in "
				+ "the style of an anime impact frame, with a burst, light cracks and speed lines."));
		impactStyle = add(new ModeSetting("Ink", List.of("Manga", "Negative", "Colour"), "Manga")
				.describe("Manga uses black ink on white, flipping to negative and back. Negative starts inverted. Colour uses the "
						+ "explosion's colour.").visibleWhen(impact::get));
		impactLength = add(new NumberSetting("Length", 0.28, 0.1, 0.8, 0.02, " s").describe("How long the impact frames last.")
				.visibleWhen(impact::get));
		impactRange = add(new NumberSetting("Range", 24, 4, 96, 1, " b").describe("Only explosions within this distance trigger impact "
				+ "frames.").visibleWhen(impact::get));
		impactGap = add(new NumberSetting("Cooldown", 1.2, 0, 5, 0.1, " s").describe("Minimum time between impact frames, so repeated "
				+ "explosions do not strobe.").visibleWhen(impact::get));
		group("Shockwave");
		shockwave = add(new BoolSetting("Shockwave", false).describe("A ring of light travels outward from the explosion across the "
				+ "terrain, up to your render distance."));
		shockSpeed = add(new NumberSetting("Wave speed", 90, 20, 300, 5, " b/s").describe("Ring speed.")
				.visibleWhen(shockwave::get));
		shockWidth = add(new NumberSetting("Wave width", 2.5, 0.5, 10, 0.25, " b").describe("Ring width.")
				.visibleWhen(shockwave::get));
		shockGlow = add(new NumberSetting("Wave brightness", 85, 10, 100, 5, "%").describe("Ring brightness.")
				.visibleWhen(shockwave::get));
		shockGrid = add(new BoolSetting("Block grid", true).describe("Lights up block edges as the ring passes.").visibleWhen(shockwave::get));
		instance = this;
		on(TickEvent.class, e -> tick());
	}
	public static boolean replaces(net.minecraft.world.phys.Vec3 center) {
		ExplosionEffects m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.isEnabled() || mc.player == null || mc.level == null) {
			return false;
		}
		if (m.others.get()) {
			return true;
		}
		for (Entity e : m.tracked.values()) {
			if (e.position().distanceToSqr(center) < 2.25) {
				return true;
			}
		}
		return false;
	}
	public static void onExplosion(net.minecraft.world.phys.Vec3 center) {
		ExplosionEffects m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.isEnabled() || mc.player == null || mc.level == null || m.level != mc.level) {
			return;
		}
		Entity source = null;
		for (Entity e : m.tracked.values()) {
			if (e.position().distanceToSqr(center) < 2.25 && !m.exploded.contains(e.getId())) {
				source = e;
				break;
			}
		}
		if (source == null && !m.others.get()) {
			return;
		}
		if (source != null) {
			m.exploded.add(source.getId());
		}
		m.add(center.x, center.y, center.z, source instanceof EndCrystal, source == null ? 0 : source.getId());
	}
	private void add(double x, double y, double z, boolean crystal, int id) {
		long now = System.nanoTime();
		for (Blast b : blasts) {
			if (now - b.born() < 250_000_000L && (b.x() - x) * (b.x() - x) + (b.y() - y) * (b.y() - y) + (b.z() - z) * (b.z() - z) < 2.25) {
				return;
			}
		}
		if (blasts.size() >= MAX) {
			blasts.remove(0);
		}
		Minecraft mc = Minecraft.getInstance();
		boolean cut = false;
		if (impact.get() && mc.player != null && mc.player.position().distanceToSqr(x, y, z) <= impactRange.get() * impactRange.get()
				&& now - lastImpact >= impactGap.get() * 1e9) {
			cut = true;
			lastImpact = now;
		}
		blasts.add(new Blast(x, y, z, crystal, now, id * 7919 + (int) (now & 0xFFFF), cut));
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			tracked.clear();
			fuses.clear();
			blasts.clear();
			exploded.clear();
			return;
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			boolean watch = entity instanceof EndCrystal ? crystals.get() : entity instanceof PrimedTnt && tnt.get();
			if (watch && entity.distanceToSqr(mc.player) < 96 * 96) {
				tracked.putIfAbsent(entity.getId(), entity);
			}
		}
		var it = tracked.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			Entity entity = entry.getValue();
			if (entity instanceof PrimedTnt primed && !primed.isRemoved()) {
				fuses.put(entry.getKey(), primed.getFuse());
			}
			if (!entity.isRemoved()) {
				continue;
			}
			it.remove();
			Integer fuse = fuses.remove(entry.getKey());
			if (exploded.remove(entry.getKey())) {
				continue;
			}
			Entity.RemovalReason reason = entity.getRemovalReason();
			if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
					|| reason == Entity.RemovalReason.CHANGED_DIMENSION) {
				continue;
			}
			if (entity instanceof PrimedTnt && (fuse == null || fuse > 4)) {
				continue;
			}
			add(entity.getX(), entity.getY(), entity.getZ(), entity instanceof EndCrystal, entity.getId());
		}
	}
	private int hot(Blast b, double t, double fade) {
		int to = !autoColors.get() ? look.color(0) : b.crystal() ? 0xFFE58BFF : 0xFFFFB02E;
		int from = !autoColors.get() ? 0xFFFFFFFF : b.crystal() ? 0xFFFFF0FF : 0xFFFFF6D8;
		return Fx.withAlpha(Fx.lerp(from, to, Math.min(1, t * 3)), (float) (255 * look.alpha() * clamp(fade)));
	}
	private int edge(Blast b, double fade) {
		int c = !autoColors.get() ? look.color(0.5) : b.crystal() ? 0xFF7A3DFF : 0xFFFF4A12;
		return Fx.withAlpha(c, (float) (255 * look.alpha() * clamp(fade)));
	}

	private static double clamp(double v) {
		return Math.max(0, Math.min(1, v));
	}
	@Override
	public void preview(net.minecraft.world.phys.Vec3 at) {
		level = Minecraft.getInstance().level;
		lastImpact = 0;
		add(at.x, at.y + 0.8, at.z, false, (int) (System.nanoTime() & 0xFFFF));
	}
	private static final org.joml.Quaternionf FLAT = new org.joml.Quaternionf();
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || level != mc.level) {
			blasts.clear();
			return;
		}
		long now = System.nanoTime();
		double life = duration.get() * 1e9;
		double reach = mc.options.renderDistance().get() * 16 * 1.15;
		double waveLife = shockwave.get() ? reach / shockSpeed.get() * 1e9 : 0;
		double cutLife = impact.get() ? impactLength.get() * 1e9 : 0;
		double keep = Math.max(style.is("Realistic") ? realLife() * 1e9 : life, Math.max(waveLife, cutLife));
		blasts.removeIf(b -> now - b.born() > keep);
		nz.temple.client.render.Meshes mesh = fx.mesh();
		for (Blast b : blasts) {
			double t = (now - b.born()) / life;
			double seconds = (now - b.born()) / 1e9;
			double s = size.get() * (b.crystal() ? 1 : 0.85);
			if (style.is("Realistic") ? seconds <= realLife() : t <= 1) {
				switch (style.get()) {
					case "Implosion" -> implosion(fx, mesh, b, t, s);
					case "Nova" -> nova(fx, mesh, b, t, s);
					default -> realistic(fx, mesh, b, seconds, s);
				}
			}
			if (shockwave.get()) {
				double radius = 0.5 + seconds * shockSpeed.get();
				if (radius < reach) {
					float fade = (float) Math.pow(1 - radius / reach, 0.6) * Math.min(1, (float) seconds * 12);
					WorldPost.shock(b.x(), b.y(), b.z(), (float) radius, edge(b, 1), fade * shockGlow.getFloat() / 100f * look.alpha(),
							shockWidth.getFloat(), shockGrid.get());
				}
			}
			if (b.impact() && impact.get() && seconds < impactLength.get()) {
				double k = seconds / impactLength.get();
				float strength = (float) (k < 0.66 ? 1 : 1 - (k - 0.66) / 0.34);
				int frame = (int) (seconds / 0.05);
				int ink = switch (impactStyle.get()) {
					case "Negative" -> 1;
					case "Colour" -> 2;
					default -> 0;
				};
				WorldPost.impact(b.x(), b.y(), b.z(), strength, b.seed(), frame, ink, edge(b, 1));
			}
		}
	}
	private static double noise(int seed, int i) {
		double v = Math.sin(seed * 12.9898 + i * 78.233) * 43758.5453;
		return v - Math.floor(v);
	}
	private void debris(nz.temple.client.render.Meshes mesh, Blast b, double seconds, double t, double s) {
		int n = (int) Math.round(debris.get());
		for (int i = 0; i < n; i++) {
			double yaw = noise(b.seed(), i) * Math.PI * 2;
			double pitch = 0.2 + noise(b.seed(), i + 50) * 1.1;
			double speed = (5 + noise(b.seed(), i + 90) * 7) * s;
			double x = b.x() + Math.cos(yaw) * Math.cos(pitch) * speed * seconds;
			double y = b.y() + Math.sin(pitch) * speed * seconds - 9 * seconds * seconds;
			double z = b.z() + Math.sin(yaw) * Math.cos(pitch) * speed * seconds;
			org.joml.Quaternionf spin = new org.joml.Quaternionf().rotationXYZ((float) (seconds * 7 + i), (float) (seconds * 5), (float) i);
			double piece = (0.08 + noise(b.seed(), i + 130) * 0.1) * s * (1 - t * 0.6);
			mesh.material(nz.temple.client.render.Meshes.Material.MATTE);
			mesh.emission((float) Math.max(0, 0.9 - t * 1.6));
			int c = Fx.lerp(hot(b, t, 1), 0xFF2A2320, Math.min(1, t * 1.8));
			mesh.box(x, y, z, spin, piece, piece * 0.8, piece * 0.7, Fx.fade(c, 1 - t * t));
		}
	}
	private double realLife() {
		return duration.get() * (smoke.get() ? 3 : 1);
	}
	private void realistic(Fx fx, nz.temple.client.render.Meshes mesh, Blast b, double sec, double s) {
		Minecraft mc = Minecraft.getInstance();
		double k = duration.get() / 1.2;
		double tt = sec / k;
		java.util.Random r = new java.util.Random(b.seed());
		boolean crystal = autoColors.get() && b.crystal();
		int white = 0xFFFFF7E6;
		int yellow = crystal ? 0xFFFFB8F0 : 0xFFFFD27A;
		int orange = crystal ? 0xFFE86BFF : 0xFFFF8A2A;
		int red = crystal ? 0xFF7A2AB8 : 0xFFB0300E;
		if (!autoColors.get()) {
			yellow = Fx.lerp(look.color(0), 0xFFFFFFFF, 0.4);
			orange = look.color(0.3);
			red = Fx.lerp(look.color(0.6), 0xFF200A08, 0.5);
		}
		float alpha = look.alpha();
		if (tt < 0.14) {
			double f = 1 - tt / 0.14;
			fx.additive(true);
			fx.light(b.x(), b.y(), b.z(), 3.5 * s * (0.6 + f * 0.4), Fx.withAlpha(white, 255 * alpha * (float) f), 1);
			fx.additive(false);
			double d = mc.player == null ? 99 : Math.sqrt(mc.player.distanceToSqr(b.x(), b.y(), b.z()));
			if (d < 28) {
				fx.screenFlash(Fx.withAlpha(0xFFFFE8C8, (float) (90 * f * f * (1 - d / 28) * alpha)));
			}
		}
		double ground = groundBelow(b);
		if (smoke.get()) {
			fx.additive(false);
			int puffs = 34;
			for (int i = 0; i < puffs; i++) {
				double[] dir = sphere(r, 0.35);
				double start = 0.12 + r.nextDouble() * 0.25;
				double life = 1.8 + r.nextDouble() * 1.6;
				double rise = 0.9 + r.nextDouble() * 1.4;
				double age = tt - start;
				if (age < 0 || age > life) {
					continue;
				}
				double f = age / life;
				double up = rise * (1 - Math.exp(-age * 0.9)) * 2.4 * s;
				double out = (0.8 + 1.4 * (1 - Math.exp(-age * 2))) * s * (0.6 + (i % 4) * 0.2);
				double x = b.x() + dir[0] * out, y = b.y() + dir[1] * out * 0.6 + up, z = b.z() + dir[2] * out;
				double size = (0.5 + f * 1.9) * s * (0.7 + (i % 5) * 0.15);
				int lit = Fx.lerp(0xFF5A3A26, 0xFF3A3836, Math.min(1, age / 0.6));
				int c = Fx.lerp(lit, 0xFF6E6C6A, f * 0.7);
				double a = Math.min(1, age / 0.25) * Math.pow(1 - f, 1.3) * 0.7;
				fx.smoke(x, y, z, size, Fx.withAlpha(c, (float) (255 * a * alpha)), i * 3.1, age * 0.25 + i);
			}
		}
		if (!Double.isNaN(ground) && tt < 1.6) {
			fx.additive(false);
			double f = tt / 1.6;
			double radius = (0.8 + 4.5 * (1 - Math.exp(-tt * 3))) * s;
			for (int i = 0; i < 18; i++) {
				double ang = Math.PI * 2 * i / 18 + r.nextDouble() * 0.3;
				double x = b.x() + Math.cos(ang) * radius, z = b.z() + Math.sin(ang) * radius;
				double a = Math.sin(Math.PI * Math.min(1, f * 1.4)) * 0.5;
				fx.smoke(x, ground + 0.35 * s, z, (0.6 + f * 1.1) * s, Fx.withAlpha(0xFF8A7B66, (float) (255 * a * alpha)), i * 2.3, ang);
			}
		}
		fx.additive(true);
		int flames = 44;
		for (int i = 0; i < flames; i++) {
			double[] dir = sphere(r, 0.25);
			double reach = (1.0 + r.nextDouble() * 1.4) * s;
			double life = 0.45 + r.nextDouble() * 0.4;
			double delay = r.nextDouble() * 0.06;
			double age = tt - delay;
			if (age < 0 || age > life) {
				continue;
			}
			double f = age / life;
			double out = reach * (1 - Math.exp(-age / 0.07));
			double lift = age * age * 2.5 * s;
			double x = b.x() + dir[0] * out, y = b.y() + dir[1] * out + lift, z = b.z() + dir[2] * out;
			int c = f < 0.06 ? Fx.lerp(white, yellow, f / 0.06) : f < 0.3 ? Fx.lerp(yellow, orange, (f - 0.06) / 0.24)
					: Fx.lerp(orange, red, (f - 0.3) / 0.7);
			double a = Math.pow(1 - f, 1.4) * 0.6;
			double size = (0.45 + f * 1.1) * s * (0.7 + r.nextDouble() * 0.6);
			fx.smoke(x, y, z, size, Fx.withAlpha(c, (float) (255 * a * alpha)), i * 1.7, age * 1.5 + i);
		}
		if (tt < 0.5) {
			double f = tt / 0.5;
			fx.light(b.x(), b.y() + tt * 0.6 * s, b.z(), (1.6 + f * 1.6) * s, Fx.withAlpha(Fx.lerp(yellow, orange, f), (float) (230 * (1 - f) * alpha)), 0.6);
		}
		for (int i = 0; i < 32; i++) {
			double[] dir = sphere(r, 0.1);
			double speed = (6 + r.nextDouble() * 10) * Math.sqrt(s);
			double life = 0.35 + r.nextDouble() * 0.6;
			if (tt > life) {
				continue;
			}
			double vx = dir[0] * speed, vy = dir[1] * speed - 16 * tt, vz = dir[2] * speed;
			double x = b.x() + dir[0] * speed * tt, y = b.y() + dir[1] * speed * tt - 8 * tt * tt, z = b.z() + dir[2] * speed * tt;
			double f = tt / life;
			fx.streak(x, y, z, vx, vy, vz, 0.15 + 0.35 * (1 - f), 0.05 * s, Fx.withAlpha(Fx.lerp(0xFFFFF0C0, orange, f), (float) (255 * (1 - f) * alpha)));
		}
		fx.additive(false);
		int n = (int) Math.round(debris.get());
		double lifeDebris = 1.4;
		if (tt < lifeDebris) {
			for (int i = 0; i < n; i++) {
				double yaw = noise(b.seed(), i) * Math.PI * 2;
				double pitch = 0.25 + noise(b.seed(), i + 50) * 1.0;
				double speed = (5 + noise(b.seed(), i + 90) * 7) * s;
				for (int trail = 4; trail >= 0; trail--) {
					double ts = Math.max(0, tt - trail * 0.045);
					double x = b.x() + Math.cos(yaw) * Math.cos(pitch) * speed * ts;
					double y = b.y() + Math.sin(pitch) * speed * ts - 9 * ts * ts;
					double z = b.z() + Math.sin(yaw) * Math.cos(pitch) * speed * ts;
					if (!Double.isNaN(ground) && y < ground + 0.05) {
						y = ground + 0.05;
					}
					if (trail == 0) {
						org.joml.Quaternionf spin = new org.joml.Quaternionf().rotationXYZ((float) (ts * 7 + i), (float) (ts * 5), (float) i);
						double piece = (0.06 + noise(b.seed(), i + 130) * 0.08) * s;
						mesh.material(nz.temple.client.render.Meshes.Material.MATTE);
						mesh.emission((float) Math.max(0, 0.9 - tt * 1.5));
						int c = Fx.lerp(Fx.lerp(white, orange, Math.min(1, tt * 4)), 0xFF2A2320, Math.min(1, tt * 1.5));
						mesh.box(x, y, z, spin, piece, piece * 0.8, piece * 0.7, Fx.fade(c, 1 - Math.pow(tt / lifeDebris, 3)));
					} else if (smoke.get()) {
						double a = (1 - tt / lifeDebris) * (1 - trail / 5.0) * 0.45;
						fx.smoke(x, y, z, (0.12 + trail * 0.05) * s, Fx.withAlpha(0xFF3C3936, (float) (255 * a * alpha)), i + trail, trail);
					}
				}
			}
		}
	}
	private static double[] sphere(java.util.Random r, double lift) {
		double u = r.nextDouble() * 2 - 1;
		double a = r.nextDouble() * Math.PI * 2;
		double h = Math.sqrt(1 - u * u);
		double x = h * Math.cos(a), y = u * 0.8 + lift, z = h * Math.sin(a);
		double l = Math.sqrt(x * x + y * y + z * z);
		return new double[] {x / l, y / l, z / l};
	}
	private static double groundBelow(Blast b) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return Double.NaN;
		}
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		int x = (int) Math.floor(b.x()), z = (int) Math.floor(b.z());
		for (int y = (int) Math.floor(b.y()); y >= Math.floor(b.y()) - 4; y--) {
			pos.set(x, y, z);
			var state = mc.level.getBlockState(pos);
			if (!state.getCollisionShape(mc.level, pos).isEmpty()) {
				return y + state.getCollisionShape(mc.level, pos).max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return Double.NaN;
	}
	private void implosion(Fx fx, nz.temple.client.render.Meshes mesh, Blast b, double t, double s) {
		int n = 16;
		double gather = 0.35;
		look.apply(mesh, 0.5f);
		for (int i = 0; i < n; i++) {
			double yaw = noise(b.seed(), i) * Math.PI * 2;
			double pitch = (noise(b.seed(), i + 10) - 0.5) * 2.4;
			double dist;
			if (t < gather) {
				double k = t / gather;
				dist = (3.5 - k * k * 3.3) * s;
			} else {
				double k = (t - gather) / (1 - gather);
				dist = (0.2 + (1 - Math.pow(1 - k, 3)) * 5) * s;
			}
			double dx = Math.cos(yaw) * Math.cos(pitch);
			double dy = Math.sin(pitch);
			double dz = Math.sin(yaw) * Math.cos(pitch);
			org.joml.Quaternionf along = nz.temple.client.render.Meshes.pointing(dx, dy, dz);
			double fade = t < gather ? Math.min(1, t * 8) : 1 - (t - gather) / (1 - gather);
			mesh.crystal(b.x() + dx * dist, b.y() + dy * dist, b.z() + dz * dist, along, 0.4 * s, 0.07 * s, edge(b, fade), 5);
		}
		if (t >= gather) {
			double k = (t - gather) / (1 - gather);
			mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
			mesh.emission(0.2f);
			mesh.sphere(b.x(), b.y(), b.z(), (0.3 + k * 3.5) * s, hot(b, k, 0.5 * (1 - k)), 16);
			mesh.material(nz.temple.client.render.Meshes.Material.MATTE);
			mesh.emission(1);
			mesh.sphere(b.x(), b.y(), b.z(), 0.5 * s * (1 - k), hot(b, k, 1 - k), 10);
		} else {
			mesh.material(nz.temple.client.render.Meshes.Material.MATTE);
			mesh.emission(1);
			mesh.sphere(b.x(), b.y(), b.z(), 0.15 * s * (t / gather), hot(b, 0, t / gather), 8);
		}
		fx.additive(true);
		fx.glow(b.x(), b.y(), b.z(), 1.6 * s, hot(b, t, t < gather ? t / gather * 0.4 : 0.6 * (1 - t)));
		fx.additive(false);
	}
	private void nova(Fx fx, nz.temple.client.render.Meshes mesh, Blast b, double t, double s) {
		double grow = 1 - Math.pow(1 - t, 3);
		double fade = 1 - t;
		look.apply(mesh, 0.6f);
		for (int i = 0; i < 3; i++) {
			org.joml.Quaternionf tilt = new org.joml.Quaternionf().rotationY((float) (i * 2.1)).rotateX((float) (i * 0.55));
			int index = i;
			mesh.torus(b.x(), b.y(), b.z(), tilt, (0.5 + grow * (3.5 - i * 0.6)) * s, 0.06 * fade * s + 0.01, 0, 56,
					(u, v) -> Fx.lerp(hot(b, t, fade), edge(b, fade), u * 0.5 + index * 0.1));
		}
		mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
		mesh.emission(0.1f);
		mesh.ellipsoid(b.x(), b.y() - 0.5, b.z(), grow * 3 * s, grow * 1.6 * s, grow * 3 * s, null, edge(b, 0.35 * fade), 16);
		look.apply(mesh, 0.9f);
		for (int i = 0; i < 12; i++) {
			double yaw = Math.PI * 2 * i / 12 + noise(b.seed(), i) * 0.3;
			double pitch = (noise(b.seed(), i + 7) - 0.3) * 1.2;
			double dx = Math.cos(yaw) * Math.cos(pitch);
			double dy = Math.sin(pitch);
			double dz = Math.sin(yaw) * Math.cos(pitch);
			double len = (0.6 + grow * 2.5) * s * (0.7 + noise(b.seed(), i + 3) * 0.6);
			org.joml.Quaternionf along = nz.temple.client.render.Meshes.pointing(dx, dy, dz);
			mesh.crystal(b.x() + dx * len * 0.5, b.y() + dy * len * 0.5, b.z() + dz * len * 0.5, along, len, 0.05 * s,
					hot(b, t, fade * fade), 4);
		}
		fx.additive(true);
		fx.glow(b.x(), b.y(), b.z(), 2 * s * (0.5 + grow), hot(b, t, fade * 0.5));
		fx.additive(false);
	}
	@Override
	protected void onDisable() {
		tracked.clear();
		fuses.clear();
		blasts.clear();
		exploded.clear();
	}
}
