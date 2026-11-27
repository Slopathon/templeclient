package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Theme;
import nz.temple.client.util.TotemTracker;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class TotemPops extends Module implements nz.temple.client.render.FxSource {
	private static final long EFFIGY_NANOS = 1_900_000_000L;
	private static final long SHIELD_NANOS = 900_000_000L;
	private static final double SHATTER = 0.62;
	private static volatile TotemPops instance;
	private final ModeSetting who;
	private final BoolSetting effect;
	private final ModeSetting style;
	private final BoolSetting spirit;
	private final BoolSetting vanillaParticles;
	private final ModeSetting screenTotem;
	private final NumberSetting screenSize;
	private final BoolSetting chat;
	private final BoolSetting toast;
	private final BoolSetting sound;
	private final NumberSetting size;
	private final ModeSetting colors;
	private final ColorSetting custom;
	private final NumberSetting brightness;
	private final BoolSetting throughWalls;
	private final nz.temple.client.render.BodyFx bodies = new nz.temple.client.render.BodyFx();
	private final nz.temple.client.render.Paint paint = new nz.temple.client.render.Paint() {
		@Override
		public int color(double t) {
			return TotemPops.this.color(t);
		}
		@Override
		public float alpha() {
			return brightness.getFloat() / 100f;
		}

		@Override
		public void apply(Meshes mesh, float extraGlow) {
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.35f + extraGlow * 0.5f);
		}
		@Override
		public float bloom() {
			return 0.6f;
		}
	};
	private final Set<Long> announced = new HashSet<>();
	private final Map<Long, double[]> burstAt = new HashMap<>();
	private ClientLevel level;

	static {
		nz.temple.client.util.VanillaFx.totemParticles = TotemPops::hidesVanillaParticles;
		nz.temple.client.util.VanillaFx.totemScale = TotemPops::screenTotemScale;
	}

	public TotemPops() {
		super("Totem Pops", Category.RENDER, "Counts how many totems each player has used and shows the count on their nametag. Replaces "
				+ "Minecraft's totem particles with a 3D effect, and can shrink or hide the totem animation on your screen.");
		who = add(new ModeSetting("Who", List.of("Players", "Everything", "Self"), "Players")
				.describe("Whose totem pops to track."));
		group();
		effect = add(new BoolSetting("World effect", true).describe("Shows an effect where the totem popped."));
		style = add(new ModeSetting("Style", List.of("Effigy", "Shield"), "Effigy")
				.describe("Effigy shows a gold totem rising out of the player, spinning above their head and shattering into gold shards "
						+ "and emeralds. Shield shows a glass bubble with a gold ring and shards thrown upward.")
				.visibleWhen(effect::get));
		spirit = add(new BoolSetting("Spirit", true)
				.describe("A gold copy of the player rises and fades out.")
				.visibleWhen(effect::get));
		vanillaParticles = add(new BoolSetting("Vanilla particles", false)
				.describe("Keeps Minecraft's totem particles. Off shows only this module's effect, which is easier to see in a fight.")
				.visibleWhen(effect::get));
		screenTotem = add(new ModeSetting("Screen totem", List.of("Vanilla", "Smaller", "Hidden"), "Smaller")
				.describe("The totem animation shown on your screen when your own totem pops. Smaller keeps it but makes it less intrusive."));
		screenSize = add(new NumberSetting("Screen totem size", 45, 10, 100, 5, "%")
				.describe("Size of the screen totem.").visibleWhen(() -> screenTotem.is("Smaller")));
		chat = add(new BoolSetting("Chat message", false).describe("Sends the pop and total count to chat."));
		toast = add(new BoolSetting("Notification", true).describe("Shows a notification with the total count."));
		sound = add(new BoolSetting("Sound", true).describe("Plays a sound so you notice pops you did not see."));
		group();
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Effect size."));
		colors = add(new ModeSetting("Color", List.of("Totem", "Theme", "Custom"), "Theme")
				.describe("Totem uses the item's gold and green. Theme uses the HUD accent. Custom uses one colour."));
		custom = add(new ColorSetting("Custom color", 0xFFE9B949).themed().describe("Colour used when Color is Custom.")
				.visibleWhen(() -> colors.is("Custom")));
		brightness = add(new NumberSetting("Brightness", 95, 10, 100, 5, "%").describe("Overall brightness of the effect."));
		throughWalls = add(new BoolSetting("Through walls", true).describe("Shows the effect through terrain."));
		on(TickEvent.class, e -> tick());
		instance = this;
	}
	public static boolean hidesVanillaParticles(Entity entity) {
		TotemPops m = instance;
		return m != null && m.isEnabled() && m.effect.get() && !m.vanillaParticles.get() && entity != null
				&& m.matches(entity, Minecraft.getInstance());
	}
	public static float screenTotemScale() {
		TotemPops m = instance;
		if (m == null || !m.isEnabled()) {
			return 1;
		}
		return switch (m.screenTotem.get()) {
			case "Hidden" -> 0;
			case "Smaller" -> m.screenSize.getFloat() / 100f;
			default -> 1;
		};
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			bodies.clear();
			burstAt.clear();
			TotemTracker.clear();
			announced.clear();
			return;
		}
		for (TotemTracker.Pop pop : TotemTracker.recent(6_000_000_000L)) {
			if (!announced.add(pop.nanos())) {
				continue;
			}
			Entity entity = mc.level.getEntity(pop.entityId());
			if (entity == null || !matches(entity, mc)) {
				continue;
			}
			if (effect.get() && spirit.get() && entity instanceof net.minecraft.world.entity.LivingEntity living) {
				bodies.ghost(nz.temple.client.render.BodyFx.Body.of(living, 1), paint, 2.2, 0.8, true);
			}
			int count = TotemTracker.count(pop.entityId());
			String name = entity.getName().getString();
			if (chat.get() && mc.player != null) {
				nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §f" + name + " §7popped a totem §8(§f" + count + "§8)"));
			}
			if (toast.get() && TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info(name + " popped", count + (count == 1 ? " totem" : " totems"));
			}
			if (sound.get()) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 0.5f));
			}
		}
		if (announced.size() > 64) {
			announced.clear();
		}
	}
	private boolean matches(Entity e, Minecraft mc) {
		return switch (who.get()) {
			case "Self" -> nz.temple.client.util.Wearer.mine(e);
			case "Everything" -> true;
			default -> e instanceof Player;
		};
	}
	private int color(double offset) {
		return switch (colors.get()) {
			case "Theme" -> Theme.color(offset);
			case "Custom" -> custom.get();
			default -> Fx.lerp(HudModule.GOLD, 0xFF6BE07A, (Math.sin(offset * Math.PI * 2) + 1) / 2);
		};
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !effect.get() || mc.level == null || level != mc.level) {
			return;
		}
		fx.throughWalls(throughWalls.get());
		bodies.emit(fx);
		long now = System.nanoTime();
		boolean effigy = style.is("Effigy");
		long span = effigy ? EFFIGY_NANOS : SHIELD_NANOS;
		for (TotemTracker.Pop pop : TotemTracker.recent(span)) {
			Entity entity = mc.level.getEntity(pop.entityId());
			if (entity != null && !matches(entity, mc)) {
				continue;
			}
			double t = (now - pop.nanos()) / (double) span;
			if (t < 0 || t > 1) {
				continue;
			}
			if (effigy) {
				effigy(fx, pop, entity, t, partial);
			} else {
				shield(fx, pop, t);
			}
		}
		burstAt.keySet().removeIf(n -> now - n > EFFIGY_NANOS);
	}
	private void shield(Fx fx, TotemTracker.Pop pop, double t) {
		float bright = 255f * brightness.getFloat() / 100f;
		double s = size.get();
		float a = (float) (1 - t);
		int c = color(t);
		double r = (0.5 + easeOut(t) * 1.9) * s;
		Meshes mesh = fx.mesh();
		mesh.bloom(false, 0);
		mesh.material(Meshes.Material.GLASS);
		mesh.emission(0.25f);
		mesh.ellipsoid(pop.x(), pop.y() - 0.1, pop.z(), r * 0.75, r * 0.9, r * 0.75, null, Fx.withAlpha(c, bright * a * 0.45f), 18);
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.45f);
		mesh.torus(pop.x(), pop.y() - 0.85, pop.z(), null, r, 0.035 * s * (0.4 + a * 0.6), Fx.withAlpha(c, bright * a), 48, null);
		for (int i = 0; i < 8; i++) {
			double ang = Math.PI * 2 * i / 8 + t * 1.5;
			double lift = t * 2.2 * s;
			fx.shard(pop.x() + Math.cos(ang) * r * 0.55, pop.y() - 0.3 + lift, pop.z() + Math.sin(ang) * r * 0.55,
					Math.cos(ang) * 0.35, 1, Math.sin(ang) * 0.35, 0.45 * s * a, 0.09 * s, Fx.withAlpha(c, bright * a), c & 0xFFFFFF);
		}
		fx.glow(pop.x(), pop.y(), pop.z(), 1.1 * s * a, Fx.withAlpha(c, bright * a * a * 0.6f));
	}
	private void effigy(Fx fx, TotemTracker.Pop pop, Entity entity, double t, float partial) {
		Minecraft mc = Minecraft.getInstance();
		float bright = brightness.getFloat() / 100f;
		double s = size.get();
		double bx = pop.x(), by = pop.y(), bz = pop.z();
		double floor = pop.y() - 0.9;
		if (entity != null) {
			Vec3 p = entity.getPosition(partial);
			bx = p.x;
			by = p.y + entity.getBbHeight() * 0.5;
			bz = p.z;
			floor = p.y;
		}
		boolean natural = colors.is("Totem");
		int gold = natural ? 0xFFF2C14E : color(0.0);
		int dark = Fx.lerp(gold, 0xFF3A2408, 0.42);
		int light = Fx.lerp(gold, 0xFFFFF6D8, 0.45);
		int eye = 0xFF2FD46C;
		Meshes mesh = fx.mesh();
		mesh.bloom(false, 0);
		long key = pop.nanos();
		if (t < SHATTER) {
			double rise = easeOut(Math.min(1, t / 0.32));
			double grow = backOut(Math.min(1, t / 0.2));
			double x = bx;
			double y = by + rise * (1.2 + 0.45 * s) + Math.sin(t * 18) * 0.03 * rise;
			double z = bz;
			Vec3 cam = mc.gameRenderer.mainCamera().position();
			double facing = Math.atan2(cam.x - x, cam.z - z);
			double spin = facing + Math.PI * 6 * Math.pow(1 - Math.min(1, t / 0.4), 3);
			double heat = smooth(0.4, SHATTER, t);
			double shake = heat * heat * 0.035 * s;
			x += Math.sin(t * 173) * shake;
			y += Math.sin(t * 211 + 1) * shake;
			z += Math.cos(t * 157) * shake;
			float alpha = 255 * bright;
			drawEffigy(mesh, x, y, z, spin, 0.95 * s * grow, Fx.withAlpha(Fx.lerp(gold, 0xFFFFFBEA, heat * 0.8), alpha),
					Fx.withAlpha(Fx.lerp(dark, 0xFFFFF0C0, heat * 0.7), alpha), Fx.withAlpha(Fx.lerp(light, 0xFFFFFFFF, heat * 0.8), alpha),
					Fx.withAlpha(eye, alpha), (float) (0.2 + heat * 0.7));
			double ring = smooth(0.18, 0.32, t) * (1 - heat * 0.6);
			if (ring > 0.01) {
				Quaternionf tilt = new Quaternionf().rotationY((float) (t * 9)).rotateX(0.35f);
				mesh.material(Meshes.Material.METAL);
				mesh.emission(0.6f);
				mesh.torus(x, y, z, tilt, 0.62 * s * ring, 0.015 * s, Fx.withAlpha(light, alpha * (float) ring), 40, null);
			}
			fx.glow(x, y, z, (0.45 + heat * 0.9) * s * grow, Fx.withAlpha(Fx.lerp(gold, 0xFFFFFFFF, heat), alpha * (float) (0.25 + heat * 0.5)));
			burstAt.put(key, new double[] {x, y, z, floor});
			return;
		}
		double[] at = burstAt.computeIfAbsent(key, k -> new double[] {pop.x(), pop.y() + 1.1, pop.z(), pop.y() - 0.9});
		double age = (t - SHATTER) * EFFIGY_NANOS / 1e9;
		double life = (1 - SHATTER) * EFFIGY_NANOS / 1e9;
		float fade = (float) (1 - smooth(0.55, 1, age / life));
		double flash = Math.max(0, 1 - age / 0.22);
		if (flash > 0) {
			fx.glow(at[0], at[1], at[2], 1.6 * s * (0.6 + 0.4 * flash), Fx.withAlpha(0xFFFFF4D0, 255 * bright * (float) flash));
		}
		double wave = Math.min(1, age / 0.45);
		fx.softRing(at[0], at[1], at[2], (0.3 + easeOut(wave) * 1.8) * s, Fx.withAlpha(light, 255 * bright * (float) (1 - wave) * 0.8f), 0.6);
		Random rnd = new Random(key);
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.3f + (float) flash * 0.5f);
		for (int i = 0; i < 26; i++) {
			double[] p = fall(rnd, at, age, s, 3.2);
			float turn = (float) (age * (6 + rnd.nextDouble() * 10) * (p[3] > 0 ? 0.4 : 1));
			Quaternionf q = new Quaternionf().rotationAxis(turn,
					new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalize());
			double sz = (0.018 + rnd.nextDouble() * 0.03) * s;
			double tall = 0.5 + rnd.nextDouble();
			int c = i % 3 == 0 ? light : i % 3 == 1 ? gold : dark;
			mesh.box(p[0], p[1] + sz * 0.5, p[2], q, sz, sz * tall, sz * 0.7, Fx.withAlpha(c, 255 * bright * fade));
		}
		mesh.material(Meshes.Material.GLASS);
		mesh.emission(0.7f);
		for (int i = 0; i < 5; i++) {
			double[] p = fall(rnd, at, age, s, 2.4);
			mesh.sphere(p[0], p[1] + 0.025 * s, p[2], 0.026 * s, Fx.withAlpha(eye, 255 * bright * fade), 10);
			fx.glow(p[0], p[1] + 0.025 * s, p[2], 0.09 * s, Fx.withAlpha(eye, 120 * bright * fade));
		}
	}
	private static double[] fall(Random rnd, double[] at, double age, double s, double speed) {
		double ang = rnd.nextDouble() * Math.PI * 2;
		double out = (0.6 + rnd.nextDouble()) * speed * Math.sqrt(s);
		double vx = Math.cos(ang) * out, vz = Math.sin(ang) * out;
		double vy = (0.8 + rnd.nextDouble() * 2.6) * Math.sqrt(s);
		double g = 14;
		double h = at[1] - at[3];
		double hit = (vy + Math.sqrt(vy * vy + 2 * g * Math.max(0, h))) / g;
		double x, y, z;
		boolean landed = false;
		if (age < hit) {
			x = at[0] + vx * age;
			y = at[1] + vy * age - 0.5 * g * age * age;
			z = at[2] + vz * age;
		} else {
			double up = (g * hit - vy) * 0.35;
			double after = age - hit;
			double bounce = 2 * up / g;
			double slide = Math.min(after, bounce + 0.25);
			x = at[0] + vx * hit + vx * 0.45 * slide;
			z = at[2] + vz * hit + vz * 0.45 * slide;
			y = at[3] + (after < bounce ? up * after - 0.5 * g * after * after : 0);
			landed = after >= bounce;
		}
		return new double[] {x, y, z, landed ? 1 : 0};
	}
	private static void drawEffigy(Meshes mesh, double x, double y, double z, double yaw, double height, int gold, int dark, int light,
			int eye, float glow) {
		if (height <= 0.001) {
			return;
		}
		Quaternionf q = new Quaternionf().rotationY((float) yaw);
		double px = height / 16;
		mesh.material(Meshes.Material.METAL);
		mesh.emission(glow);
		part(mesh, q, x, y, z, px, 0, 1.6, 0, 3.6, 1.6, 2.3, dark);
		part(mesh, q, x, y, z, px, 0, 5.0, 0, 2.3, 2.2, 1.6, gold);
		part(mesh, q, x, y, z, px, 0, 3.3, 0, 2.7, 0.45, 1.8, dark);
		part(mesh, q, x, y, z, px, 0, 7.7, 0, 6.2, 0.9, 1.0, gold);
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			part(mesh, q, x, y, z, px, sgn * 6.5, 9.3, 0, 0.9, 1.9, 0.9, light);
			part(mesh, q, x, y, z, px, sgn * 4.2, 6.4, 0, 0.8, 0.8, 0.8, dark);
		}
		part(mesh, q, x, y, z, px, 0, 11.9, 0, 3.4, 3.4, 3.0, gold);
		part(mesh, q, x, y, z, px, 0, 13.6, 3.02, 2.7, 0.35, 0.12, dark);
		part(mesh, q, x, y, z, px, 0, 10.2, 3.02, 1.3, 0.3, 0.12, dark);
		part(mesh, q, x, y, z, px, 0, 15.6, 0, 1.7, 0.5, 1.6, light);
		mesh.material(Meshes.Material.MATTE);
		mesh.emission(0.65f);
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			part(mesh, q, x, y, z, px, sgn * 1.45, 12.3, 3.05, 0.75, 0.95, 0.14, eye);
		}
	}
	private static void part(Meshes mesh, Quaternionf q, double x, double y, double z, double px, double cx, double cy, double cz, double hx,
			double hy, double hz, int colour) {
		Vector3f c = q.transform(new Vector3f((float) (cx * px), (float) ((cy - 8) * px), (float) (cz * px)));
		mesh.box(x + c.x, y + c.y, z + c.z, q, hx * px, hy * px, hz * px, colour);
	}
	private static double easeOut(double t) {
		return 1 - Math.pow(1 - t, 3);
	}
	private static double backOut(double t) {
		double c = 1.9;
		return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
	}

	private static double smooth(double a, double b, double x) {
		double k = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return k * k * (3 - 2 * k);
	}
	@Override
	protected void onDisable() {
		announced.clear();
		burstAt.clear();
	}
}
