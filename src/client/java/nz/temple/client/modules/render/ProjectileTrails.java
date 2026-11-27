package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.VisualRange;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectileTrails extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private static volatile ProjectileTrails instance;
	public static boolean replacesArrowSparks() {
		ProjectileTrails m = instance;
		return m != null && m.isEnabled() && m.arrows.get();
	}
	private final ModeSetting style;
	private final BoolSetting arrows;
	private final BoolSetting pearls;
	private final BoolSetting thrown;
	private final BoolSetting other;
	private final NumberSetting length;
	private final NumberSetting width;
	private final NumberSetting range;
	private final BoolSetting impact;
	private final BoolSetting typeColors;
	private final nz.temple.client.modules.cosmetic.Look look;
	private long previewBorn;
	private Vec3 previewAt;
	private final Map<Integer, ArrayDeque<Point>> trails = new HashMap<>();
	private final List<Impact> impacts = new ArrayList<>();
	private ClientLevel level;
	private record Point(double x, double y, double z, long nanos) {
	}
	private record Impact(double x, double y, double z, int color, long born) {
	}
	static {
		nz.temple.client.util.VanillaFx.arrowSparks = ProjectileTrails::replacesArrowSparks;
	}

	public ProjectileTrails() {
		super("Projectile Trails", Category.COSMETICS, "Trails behind arrows, pearls, tridents and fireballs, with an effect where they "
				+ "land.");
		style = add(new ModeSetting("Style", List.of("Tube", "Comet", "Spiral"), "Comet")
				.describe("Tube draws a tapering tube. Comet draws a bright head with a tail and beads. Spiral winds a thin tube around the "
						+ "path."));
		group();
		arrows = add(new BoolSetting("Arrows", true).describe("Arrows and spectral arrows."));
		instance = this;
		pearls = add(new BoolSetting("Pearls", true).describe("Ender pearls and eyes of ender."));
		thrown = add(new BoolSetting("Thrown items", true).describe("Tridents, snowballs, eggs and potions."));
		other = add(new BoolSetting("Everything else", true).describe("Fireballs, charges and any other projectile."));
		group();
		length = add(new NumberSetting("Length", 1.2, 0.2, 5, 0.1, " s").describe("How long the trail lasts."));
		width = add(new NumberSetting("Width", 0.12, 0.02, 0.6, 0.01, " b").describe("Trail thickness."));
		range = add(new NumberSetting("Range", 96, 16, 256, 8, " m").describe("Only shows trails within this distance."));
		impact = add(new BoolSetting("Impact burst", true).describe("A flash and ring where the projectile lands."));
		group();
		typeColors = add(new BoolSetting("Colour by type", true)
				.describe("Purple for pearls, gold for arrows, cyan for tridents and orange for fireballs. Off uses the colour settings "
						+ "below."));
		look = new nz.temple.client.modules.cosmetic.Look(x -> add(x), "Light");
	}
	private boolean wants(String id) {
		if (id.contains("arrow")) {
			return arrows.get();
		}
		if (id.contains("pearl") || id.contains("eye_of_ender")) {
			return pearls.get();
		}
		if (id.contains("trident") || id.contains("snowball") || id.contains("egg") || id.contains("potion")
				|| id.contains("experience_bottle")) {
			return thrown.get();
		}
		return other.get();
	}
	private int color(String id, double offset, double fade) {
		if (!typeColors.get() || id.isEmpty()) {
			return look.color(offset, fade);
		}
		return Fx.withAlpha(typeColor(id), (float) (255 * look.alpha() * Math.max(0, Math.min(1, fade))));
	}
	private static int typeColor(String id) {
		if (id.contains("pearl") || id.contains("eye_of_ender")) {
			return 0xFFAF52DE;
		}
		if (id.contains("trident")) {
			return 0xFF5AC8FA;
		}
		if (id.contains("fireball") || id.contains("charge")) {
			return 0xFFFF7A2F;
		}
		if (id.contains("potion") || id.contains("experience")) {
			return 0xFF34C759;
		}
		return 0xFFE9B949;
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			trails.clear();
			impacts.clear();
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			trails.clear();
			impacts.clear();
		}
		long now = System.nanoTime();
		long life = (long) (length.get() * 1e9);
		double maxSq = VisualRange.of(range.get()) * VisualRange.of(range.get());
		float bright = 1;
		Set<Integer> seen = new HashSet<>();
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof Projectile) || entity.distanceToSqr(mc.player) > maxSq) {
				continue;
			}
			String id = EntityType.getKey(entity.getType()).getPath();
			if (!wants(id)) {
				continue;
			}
			seen.add(entity.getId());
			ArrayDeque<Point> points = trails.computeIfAbsent(entity.getId(), k -> new ArrayDeque<>());
			Vec3 p = entity.getPosition(partial);
			Point last = points.peekLast();
			if (last == null || Math.abs(last.x() - p.x) + Math.abs(last.y() - p.y) + Math.abs(last.z() - p.z) > 0.03) {
				points.add(new Point(p.x, p.y, p.z, now));
			}
			while (!points.isEmpty() && now - points.peekFirst().nanos() > life) {
				points.removeFirst();
			}
			draw(fx, points, id, now, life, bright);
		}
		for (Iterator<Map.Entry<Integer, ArrayDeque<Point>>> it = trails.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, ArrayDeque<Point>> e = it.next();
			if (seen.contains(e.getKey())) {
				continue;
			}
			ArrayDeque<Point> points = e.getValue();
			Point last = points.peekLast();
			if (impact.get() && last != null && impacts.size() < 24 && now - last.nanos() < 300_000_000L) {
				impacts.add(new Impact(last.x(), last.y(), last.z(), color("", 0, 1), now));
			}
			it.remove();
		}
		nz.temple.client.render.Meshes mesh = fx.mesh();
		impacts.removeIf(i -> now - i.born() > 550_000_000L);
		for (Impact i : impacts) {
			double t = (now - i.born()) / 0.55e9;
			double r = 0.15 + (1 - Math.pow(1 - t, 3)) * 0.9;
			fx.additive(!look.matte());
			fx.light(i.x(), i.y(), i.z(), 0.5 * (1 - t * 0.5), Fx.fade(i.color(), (1 - t) * (1 - t)), 0.7);
			fx.softRing(i.x(), i.y(), i.z(), r * 1.2, Fx.fade(i.color(), 0.8 * (1 - t)), 0.4);
			fx.additive(false);
			if (look.solid()) {
				mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
				mesh.emission(0.15f);
				mesh.sphere(i.x(), i.y(), i.z(), r * 0.6, Fx.fade(i.color(), 0.5 * (1 - t)), 14);
			}
		}
		if (previewAt != null) {
			double age = (now - previewBorn) / 1e9;
			if (age > 2.5) {
				previewAt = null;
			} else {
				ArrayDeque<Point> fake = new ArrayDeque<>();
				double head = Math.min(age, 1.6);
				for (int k = 0; k <= 30; k++) {
					double tt = head - (30 - k) / 30.0 * Math.min(head, length.get());
					fake.add(new Point(previewAt.x - 4 + tt * 5, previewAt.y + 1.2 + tt * 1.6 - tt * tt * 1, previewAt.z,
							now - (long) ((head - tt) * 1e9)));
				}
				draw(fx, fake, "arrow", now, life, 1);
			}
		}
	}
	@Override
	public void preview(Vec3 at) {
		previewAt = at;
		previewBorn = System.nanoTime();
	}
	private void draw(Fx fx, ArrayDeque<Point> points, String id, long now, long life, float bright) {
		int n = points.size();
		if (n < 2) {
			return;
		}
		nz.temple.client.render.Meshes mesh = fx.mesh();
		double w = width.get();
		double[] path = new double[n * 3];
		double[] radius = new double[n];
		int[] colours = new int[n];
		int i = 0;
		for (Point point : points) {
			double keep = Math.max(0, 1 - (double) (now - point.nanos()) / life);
			path[i * 3] = point.x();
			path[i * 3 + 1] = point.y();
			path[i * 3 + 2] = point.z();
			radius[i] = w * 0.5 * (0.1 + 0.9 * keep);
			colours[i] = color(id, i * 0.03, keep * keep);
			i++;
		}
		look.apply(mesh, 0.35f);
		int last = (n - 1) * 3;
		switch (style.get()) {
			case "Spiral" -> {
				double[] spiral = new double[n * 3];
				double[] thin = new double[n];
				for (int k = 0; k < n; k++) {
					int a = Math.max(0, k - 1);
					int b = Math.min(n - 1, k + 1);
					double dx = path[b * 3] - path[a * 3];
					double dy = path[b * 3 + 1] - path[a * 3 + 1];
					double dz = path[b * 3 + 2] - path[a * 3 + 2];
					double len = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
					double ux = -dz / len;
					double uz = dx / len;
					double ul = Math.max(1e-6, Math.sqrt(ux * ux + uz * uz));
					ux /= ul;
					uz /= ul;
					double vx = dy / len * uz;
					double vy = (dz * ux - dx * uz) / len;
					double vz = -dy / len * ux;
					double angle = k * 0.8 - now / 1e9 * 6;
					double r = w * 1.4 * (radius[k] / (w * 0.5));
					spiral[k * 3] = path[k * 3] + (ux * Math.cos(angle) + vx * Math.sin(angle)) * r;
					spiral[k * 3 + 1] = path[k * 3 + 1] + vy * Math.sin(angle) * r;
					spiral[k * 3 + 2] = path[k * 3 + 2] + (uz * Math.cos(angle) + vz * Math.sin(angle)) * r;
					thin[k] = radius[k] * 0.35;
				}
				nz.temple.client.modules.cosmetic.SoftTrail.trail(fx, look, spiral, n, thin, colours, 0.8);
				nz.temple.client.modules.cosmetic.SoftTrail.trail(fx, look, path, n, thin, colours, 0.8);
			}
			case "Comet" -> {
				nz.temple.client.modules.cosmetic.SoftTrail.trail(fx, look, path, n, radius, colours, 1);
				fx.additive(!look.matte());
				for (int k = n - 3; k >= 0; k -= 3) {
					double sink = (n - k) * 0.004;
					fx.light(path[k * 3], path[k * 3 + 1] - sink, path[k * 3 + 2], radius[k] * 1.2, colours[k], 0.8);
				}
				fx.additive(false);
				nz.temple.client.modules.cosmetic.SoftTrail.head(fx, look, path[last], path[last + 1], path[last + 2], w * 0.9, color(id, 0, 1));
				fx.additive(!look.matte());
				fx.sparkle(path[last], path[last + 1], path[last + 2], w * 3.2, color(id, 0, 0.8), now / 1e9 * 4);
				fx.additive(false);
			}
			default -> nz.temple.client.modules.cosmetic.SoftTrail.trail(fx, look, path, n, radius, colours, 1);
		}
	}
	@Override
	protected void onDisable() {
		trails.clear();
		impacts.clear();
	}
}
