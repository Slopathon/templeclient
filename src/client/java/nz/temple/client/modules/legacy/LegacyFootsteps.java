package nz.temple.client.modules.legacy;

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

public final class LegacyFootsteps extends Module implements nz.temple.client.modules.cosmetic.Previewable {
	private final ModeSetting style;
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
	private static final double FOOT = nz.temple.client.modules.render.Footsteps.FOOT;

	private record Print(double x, double y, double z, float yaw, long born, double offset) {
	}

	public LegacyFootsteps() {
		super("Footsteps", Category.COSMETICS, "Leaves marks where players walk: prints, puddles, petals, flames, frost or flowers.");
		style = add(new ModeSetting("Style", List.of("Prints", "Puddles", "Petals", "Flames", "Frost", "Blossom"), "Prints")
				.describe("Prints leaves square marks the shape of a Minecraft foot, turned the way you walked. Puddles leaves small glass puddles with a "
						+ "ripple. Petals scatters falling petals. Flames burns briefly and leaves a scorch mark. Frost grows an ice "
						+ "crystal that melts. Blossom grows a flower that opens and then wilts."));
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Self")
				.describe("Whose steps leave prints."));
		group();
		stride = add(new NumberSetting("Stride", 0.9, 0.3, 3, 0.05, " b").describe("Distance between prints."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Print size."));
		lifetime = add(new NumberSetting("Lifetime", 2.5, 0.5, 10, 0.5, " s").describe("How long prints last."));
		maxPrints = add(new NumberSetting("Max prints", 60, 10, 200, 10, "").describe("Maximum prints. The oldest are removed first."));
		group();
		look = new nz.temple.client.modules.cosmetic.Look(x -> add(x), "Light");
		on(TickEvent.class, e -> tick());
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
			if (self ? who.is("Players") : who.is("Self")) {
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
			prints.add(new Print(player.getX() + sideX, player.getY() + 0.01, player.getZ() + sideZ,
					walked, System.nanoTime(), (System.nanoTime() % 10_000) / 10_000.0));
		}
		last.keySet().removeIf(id -> mc.level.getEntity(id) == null);
	}

	@Override
	public void preview(net.minecraft.world.phys.Vec3 at) {
		level = Minecraft.getInstance().level;
		long now = System.nanoTime();
		for (int i = 0; i < 8; i++) {
			double side = i % 2 == 0 ? FOOT : -FOOT;
			prints.add(new Print(at.x + side, at.y + 0.01, at.z - 2 + i * 0.6, 0, now - (8 - i) * 120_000_000L, i * 0.1));
		}
	}

	public void emit(Fx fx, float partial) {
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
		for (Print p : prints) {
			double t = (now - p.born()) / life;
			double fade = t < 0.7 ? 1 : 1 - (t - 0.7) / 0.3;
			org.joml.Quaternionf facing = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-p.yaw()));
			switch (style.get()) {
				case "Puddles" -> {
					double grow = Math.min(1, t * 6);
					look.apply(mesh, 0.1f);
					mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
					mesh.ellipsoid(p.x(), p.y(), p.z(), 0.16 * s * grow, 0.012, 0.22 * s * grow, facing, look.color(p.offset(), fade), 10);
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
						double a = p.offset() * 40 + i * 2.1;
						double x = p.x() + Math.cos(a) * 0.12 * s + Math.sin(t * 12 + i) * 0.05 * fall;
						double z = p.z() + Math.sin(a) * 0.12 * s;
						double y = p.y() + 0.01 + fall * 0.5 * s;
						org.joml.Quaternionf spin = new org.joml.Quaternionf().rotationY((float) (a + t * 3 * fall))
								.rotateZ((float) (fall * Math.sin(t * 15 + i) * 0.8));
						mesh.blade(x, y, z, spin, 0.12 * s, 0.08 * s, 0.006, 0.01, look.color(p.offset() + i * 0.1, fade),
								look.color(p.offset() + i * 0.1 + 0.2, fade * 0.8));
					}
				}
				case "Flames" -> flames(fx, mesh, p, t, fade, s);
				case "Frost" -> frost(fx, mesh, p, t, fade, s, facing);
				case "Blossom" -> blossom(mesh, p, t, fade, s);
				default -> {
					look.apply(mesh, (float) (0.8 * fade));
					mesh.box(p.x(), p.y(), p.z(), facing, FOOT * s, 0.01, FOOT * s, look.color(p.offset(), fade));
				}
			}
		}
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
				fx.light(p.x() + jx + sway, p.y() + 0.03 + phase * (k < 3 ? 0.22 : 0.36) * s * burn, p.z() + jz, r,
						Fx.withAlpha(c, (float) (255 * look.alpha() * Math.min(1, (1 - phase) * 1.4) * burn)), phase < 0.3 ? 0.7 : 0.2);
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
		int ice = Fx.withAlpha(Fx.lerp(look.color(p.offset()), 0xFFE4F6FF, 0.45), a);
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
