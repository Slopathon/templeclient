package nz.temple.client.modules.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.modules.cosmetic.Previewable;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
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

public final class LegacyTrails extends Module implements Previewable {
	private final ModeSetting style;
	private final ModeSetting who;
	private final NumberSetting length;
	private final NumberSetting height;
	private final NumberSetting width;
	private final NumberSetting particleSize;
	private final NumberSetting density;
	private final NumberSetting drift;
	private final BoolSetting firstPerson;
	private final BoolSetting onlyMoving;
	private final Look look;
	private final Map<Integer, ArrayDeque<Point>> trails = new HashMap<>();
	private ClientLevel level;
	private long previewBorn;
	private Vec3 previewAt;
	private int remainingParticles;
	private final nz.temple.client.render.BodyFx bodies = new nz.temple.client.render.BodyFx();
	private final java.util.Map<Integer, Vec3> lastImagePos = new java.util.HashMap<>();
	private final Map<Integer, Long> lastImage = new HashMap<>();
	private final java.util.Map<Integer, Object[]> lastFrame = new java.util.HashMap<>();
	private final NumberSetting spacing;
	private final BoolSetting glow;
	private record Point(double x, double y, double z, double h, long nanos) {
	}

	public LegacyTrails() {
		super("Trails", Category.COSMETICS, "Trails that follow players and fade out behind them.");
		style = add(new ModeSetting("Style", List.of("Tube", "Ribbon", "Beads", "Sparkles", "Wisps", "Shards", "Afterimages"), "Tube")
				.describe("Tube draws a tapering trail at waist height, as light or as a solid tube in Glass and Gold. Ribbon draws a lit "
						+ "curtain rising from the feet. Beads draws a string of spheres. Sparkles, Wisps and Shards are particle trails. "
						+ "Afterimages leaves see-through copies of the body behind you."));
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Self")
				.describe("Self shows only your trail. Players shows other players. Both shows everyone."));
		length = add(new NumberSetting("Length", 0.9, 0.2, 4, 0.05, " s").describe("How long a trail lasts before fading."));
		height = add(new NumberSetting("Height", 0.5, 0.05, 2, 0.05, " b").describe("Ribbon height above the feet.")
				.visibleWhen(() -> style.is("Ribbon")));
		width = add(new NumberSetting("Width", 0.08, 0.02, 0.4, 0.01, " b").describe("Tube or bead thickness.")
				.visibleWhen(() -> !style.is("Ribbon")));
		particleSize = add(new NumberSetting("Particle size", 1, 0.2, 3, 0.05, "x").describe("Particle size.")
				.visibleWhen(this::particleStyle));
		density = add(new NumberSetting("Particle density", 100, 20, 250, 5, "%").describe("Particle density.")
				.visibleWhen(this::particleStyle));
		drift = add(new NumberSetting("Particle drift", 0.12, 0, 0.8, 0.02, " b").describe("How far particles drift from the path as they "
				+ "fade.")
				.visibleWhen(this::particleStyle));
		spacing = add(new NumberSetting("Afterimage spacing", 0.13, 0.03, 0.4, 0.01, " s").describe("Time between afterimages. Smaller values "
				+ "leave them closer together.")
				.visibleWhen(() -> style.is("Afterimages")));
		glow = add(new BoolSetting("Afterimage glow", true)
				.describe("Adds a glow around each afterimage.")
				.visibleWhen(() -> style.is("Afterimages")));
		onlyMoving = add(new BoolSetting("Only while moving", true)
				.describe("Only shows the trail while moving. When you stop, it retracts quickly."));
		firstPerson = add(new BoolSetting("Own trail in first person", false)
				.describe("Shows your own trail in first person."));
		group();
		look = new Look(s -> add(s), "Light");
	}
	@Override
	public void preview(Vec3 at) {
		if (style.is("Afterimages")) {
			Minecraft mc = Minecraft.getInstance();
			double yaw = mc.player == null ? 0 : mc.player.getYRot() + 90;
			Vec3 dir = Vec3.directionFromRotation(0, (float) yaw);
			for (int i = 0; i < 6; i++) {
				double swing = Math.cos(i * 1.1) * 0.9;
				bodies.afterimage(new nz.temple.client.render.BodyFx.Body(at.add(dir.scale(-i * 0.45 + 1.2)), yaw, 1.8, 0.6, true, swing),
						look, Math.max(0.6, length.get()) * (1 - i * 0.1));
			}
			return;
		}
		previewAt = at;
		previewBorn = System.nanoTime();
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			trails.clear();
			bodies.clear();
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			trails.clear();
		}
		long now = System.nanoTime();
		remainingParticles = 48;
		long life = (long) (length.get() * 1e9);
		Set<Integer> seen = new HashSet<>();
		boolean seesSelf = !mc.options.getCameraType().isFirstPerson() || nz.temple.client.util.DetachedCamera.active();
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (self ? who.is("Players") : who.is("Self")) {
				continue;
			}
			if (!player.isAlive() || player.isInvisible() || player.distanceToSqr(mc.player) > 96 * 96) {
				continue;
			}
			seen.add(player.getId());
			ArrayDeque<Point> points = trails.computeIfAbsent(player.getId(), k -> new ArrayDeque<>());
			Vec3 p = player.getPosition(partial);
			Point last = points.peekLast();
			if (last == null || Math.abs(last.x() - p.x) + Math.abs(last.y() - p.y) + Math.abs(last.z() - p.z) > 0.05) {
				points.add(new Point(p.x, p.y, p.z, player.getBbHeight(), now));
			}
			Point newest = points.peekLast();
			boolean moving = newest != null && now - newest.nanos() < 120_000_000L;
			long keep = onlyMoving.get() && !moving ? Math.min(life, 250_000_000L) : life;
			while (!points.isEmpty() && now - points.peekFirst().nanos() > keep) {
				points.removeFirst();
			}
			if (self && !seesSelf && !firstPerson.get()) {
				continue;
			}
			if (style.is("Afterimages")) {
				Vec3 v = player.getDeltaMovement();
				nz.temple.client.render.BodyFx.Body body = nz.temple.client.render.BodyFx.Body.of(player, partial);
				Object[] previousFrame = lastFrame.put(player.getId(), new Object[] {now, body});
				long step = (long) (spacing.get() * 1e9);
				if (v.x * v.x + v.z * v.z + v.y * v.y * 0.3 > 0.004) {
					long next = lastImage.getOrDefault(player.getId(), 0L) + step;
					if (previousFrame == null || now - next > step * 4) {
						lastImage.put(player.getId(), now);
						lastImagePos.put(player.getId(), body.feet());
						bodies.afterimage(body, look, length.get());
					} else {
						long before = (Long) previousFrame[0];
						nz.temple.client.render.BodyFx.Body from = (nz.temple.client.render.BodyFx.Body) previousFrame[1];
						while (next <= now) {
							double k = now == before ? 1 : Math.max(0, Math.min(1, (double) (next - before) / (now - before)));
							Vec3 at = from.feet().lerp(body.feet(), k);
							Vec3 prev = lastImagePos.get(player.getId());
							if (prev == null || prev.distanceToSqr(at) >= 0.8 * 0.8) {
								lastImagePos.put(player.getId(), at);
								bodies.afterimage(new nz.temple.client.render.BodyFx.Body(at,
										from.yaw() + net.minecraft.util.Mth.wrapDegrees(body.yaw() - from.yaw()) * k, body.height(), body.width(),
										body.humanoid(), from.swing() + (body.swing() - from.swing()) * k), look, length.get());
							}
							lastImage.put(player.getId(), next);
							next += step;
						}
					}
				}
				continue;
			}
			if (onlyMoving.get() && !moving && points.isEmpty()) {
				continue;
			}
			List<Point> list = new ArrayList<>(points);
			list.add(new Point(p.x, p.y, p.z, player.getBbHeight(), now));
			draw(fx, list, now, life);
		}
		for (Iterator<Integer> it = trails.keySet().iterator(); it.hasNext(); ) {
			if (!seen.contains(it.next())) {
				it.remove();
			}
		}
		lastImage.keySet().retainAll(seen);
		lastFrame.keySet().retainAll(seen);
		bodies.glowingAfterimages = glowing();
		bodies.emit(fx);
		previewDraw(fx, now, life);
	}
	private void previewDraw(Fx fx, long now, long life) {
		if (previewAt == null) {
			return;
		}
		double age = (now - previewBorn) / 1e9;
		if (age > 3) {
			previewAt = null;
			return;
		}
		List<Point> list = new ArrayList<>();
		for (int i = 30; i >= 0; i--) {
			long when = now - (long) (life * i / 30.0);
			double t = Math.max(0, (when - previewBorn) / 1e9 + 1);
			list.add(new Point(previewAt.x + Math.sin(t * 2) * 1.5, previewAt.y, previewAt.z + Math.cos(t * 1.3) * 1.2 - 0.5,
					1.8, when));
		}
		draw(fx, list, now, life);
	}
	private void draw(Fx fx, List<Point> points, long now, long life) {
		int n = points.size();
		if (n < 2) {
			return;
		}
		Meshes mesh = fx.mesh();
		look.apply(mesh, 0.3f);
		double[] path = new double[n * 3];
		double[] radius = new double[n];
		int[] colours = new int[n];
		for (int i = 0; i < n; i++) {
			Point p = points.get(i);
			double age = Math.min(1, (double) (now - p.nanos()) / life);
			double keep = 1 - age;
			path[i * 3] = p.x();
			path[i * 3 + 1] = p.y() + p.h() * 0.45;
			path[i * 3 + 2] = p.z();
			radius[i] = width.get() * (0.15 + 0.85 * keep);
			colours[i] = look.color(i * 0.02, keep * keep);
		}
		switch (style.get()) {
			case "Ribbon" -> ribbon(mesh, points, now, life);
			case "Beads" -> {
				double spacing = Math.max(0.12, width.get() * 3);
				double carried = 0;
				for (int i = n - 1; i > 0; i--) {
					double dx = path[i * 3] - path[(i - 1) * 3];
					double dy = path[i * 3 + 1] - path[(i - 1) * 3 + 1];
					double dz = path[i * 3 + 2] - path[(i - 1) * 3 + 2];
					carried += Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (carried < spacing && i != n - 1) {
						continue;
					}
					carried = 0;
					if (look.solid()) {
						mesh.sphere(path[i * 3], path[i * 3 + 1], path[i * 3 + 2], radius[i], colours[i], 12);
					} else {
						fx.additive(!look.matte());
						fx.light(path[i * 3], path[i * 3 + 1], path[i * 3 + 2], radius[i] * 1.8, colours[i], look.matte() ? 0 : 0.8);
						fx.light(path[i * 3], path[i * 3 + 1], path[i * 3 + 2], radius[i] * 5, Fx.fade(colours[i], 0.2), 0);
						fx.additive(false);
					}
				}
			}
			case "Sparkles", "Wisps", "Shards" -> particles(fx, points, path, colours, now, life);
			default -> nz.temple.client.modules.cosmetic.SoftTrail.trail(fx, look, path, n, radius, colours, 1);
		}
	}
	private void particles(Fx fx, List<Point> points, double[] path, int[] colours, long now, long life) {
		int n = points.size();
		int stride = Math.max(1, (int) Math.round(200.0 / density.get()));
		int drawn = 0;
		for (int i = n - 1; i >= 0 && drawn < 64 && remainingParticles > 0; i -= stride, drawn++, remainingParticles--) {
			Point point = points.get(i);
			double age = Math.min(1, (double) (now - point.nanos()) / life);
			double seed = point.x() * 17.13 + point.y() * 31.7 + point.z() * 11.9 + point.nanos() * 1e-9;
			double amount = drift.get() * age;
			double x = path[i * 3] + Math.sin(seed * 2.1) * amount;
			double y = path[i * 3 + 1] + Math.cos(seed * 1.3) * amount * 0.6;
			double z = path[i * 3 + 2] + Math.sin(seed * 0.8) * amount;
			double size = width.get() * particleSize.get() * (0.35 + 0.65 * (1 - age));
			int color = colours[i];
			switch (style.get()) {
				case "Wisps" -> fx.wisp(x, y, z, size * 3.2, color, seed);
				case "Shards" -> {
					int before = Math.max(0, i - 1);
					double dx = path[i * 3] - path[before * 3];
					double dy = path[i * 3 + 1] - path[before * 3 + 1];
					double dz = path[i * 3 + 2] - path[before * 3 + 2];
					if (dx * dx + dy * dy + dz * dz < 1e-5) {
						dx = Math.cos(seed);
						dy = 0.2;
						dz = Math.sin(seed);
					}
					fx.shard(x, y, z, dx, dy, dz, size * 4, size, color, Fx.fade(color, 0.08));
				}
				default -> fx.star(x, y, z, size * 3.5, color, seed + age * 5);
			}
		}
	}
	private boolean particleStyle() {
		return style.is("Sparkles") || style.is("Wisps") || style.is("Shards");
	}
	private void ribbon(Meshes mesh, List<Point> points, long now, long life) {
		int n = points.size();
		double h = height.get();
		mesh.surface((u, v, o) -> {
			double f = u * (n - 1);
			int i = Math.min(n - 2, (int) f);
			double k = f - i;
			Point a = points.get(i);
			Point b = points.get(i + 1);
			double dx = b.x() - a.x();
			double dz = b.z() - a.z();
			double len = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
			o[0] = a.x() + dx * k;
			o[1] = a.y() + (b.y() - a.y()) * k + h * v;
			o[2] = a.z() + dz * k;
			o[3] = -dz / len;
			o[4] = 0.15;
			o[5] = dx / len;
		}, Math.max(1, n - 1), 3, 0, (u, v) -> {
			Point p = points.get((int) Math.round(u * (n - 1)));
			double keep = 1 - Math.min(1, (double) (now - p.nanos()) / life);
			return look.color(u * 0.4, keep * keep * (1 - v * 0.85));
		});
	}
	@Override
	protected void onDisable() {
		trails.clear();
	}
	public boolean glowing() {
		return isEnabled() && style.is("Afterimages") && glow.get();
	}
}
