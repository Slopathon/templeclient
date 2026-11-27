package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.modules.cosmetic.Previewable;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.client.render.GroundQuake;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JumpCircles extends Module implements Previewable, nz.temple.client.render.FxSource {
	private static final int MAX = 32;
	private final ModeSetting style;
	private final ModeSetting who;
	private final ModeSetting trigger;
	private final NumberSetting radius;
	private final NumberSetting duration;
	private final NumberSetting highlightRadius;
	private final NumberSetting outlineWidth;
	private final NumberSetting quakeRadius;
	private final NumberSetting quakeHeight;
	private final NumberSetting waveSpeed;
	private final NumberSetting tumble;
	private final NumberSetting cameraShake;
	private final BoolSetting dust;
	private final BoolSetting thud;
	private final BoolSetting fallScale;
	private final Look look;
	private final ArrayDeque<Circle> circles = new ArrayDeque<>();
	private final Map<Integer, double[]> ground = new HashMap<>();
	private ClientLevel level;
	private record Circle(double x, double y, double z, long born, double seed, boolean landing, double[] cells) {
	}

	public JumpCircles() {
		super("Jump Circles", Category.COSMETICS, "An effect on the ground when you jump or land: a ripple, rings, a shockwave, a crown, "
				+ "lit blocks or an earthquake.");
		style = add(new ModeSetting("Style", List.of("Ripple", "Rings", "Shockwave", "Crown", "Block highlight", "Earthquake"), "Ripple")
				.describe("Ripple throws droplets up in a ring that fall back as it spreads, like a drop landing in water. Rings spreads "
						+ "rings of glass outward. Shockwave sends a band and a dome outward. Crown spins a small crown up off the ground, in "
						+ "the material and colours below. Block highlight lights up nearby blocks in an expanding ring, raising each one as it passes. "
						+ "Earthquake throws the blocks around you up out of the ground to tumble back into place. It only changes what you see: "
						+ "the blocks stay solid."));
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Everyone"), "Self")
				.describe("Self shows only your jumps. Players shows every player. Everyone includes all living entities."));
		trigger = add(new ModeSetting("Trigger", List.of("Jump", "Land", "Both"), "Both")
				.describe("Show when jumping, landing, or both. Landings are larger after longer falls."));
		group();
		radius = add(new NumberSetting("Radius", 1.3, 0.4, 4, 0.1, " b").describe("How far it spreads.").visibleWhen(this::notQuake));
		duration = add(new NumberSetting("Duration", 0.9, 0.3, 3, 0.05, " s").describe("How long it lasts.").visibleWhen(this::notQuake));
		highlightRadius = add(new NumberSetting("Highlight radius", 4, 1.5, 10, 0.5, " b").describe("How far the ring of lit blocks travels.")
				.visibleWhen(() -> style.is("Block highlight")));
		outlineWidth = add(new NumberSetting("Tile outline", 2.5, 0, 8, 0.5, " cm").describe("Outline thickness on each lit block. 0 hides "
				+ "it.")
				.visibleWhen(() -> style.is("Block highlight")));
		quakeRadius = add(new NumberSetting("Quake radius", 6, 2, 12, 0.5, " b").describe("How far out the ground is thrown.")
				.visibleWhen(this::quake));
		quakeHeight = add(new NumberSetting("Quake height", 1.2, 0.2, 3, 0.1, " b").describe("How high the nearest blocks are thrown.")
				.visibleWhen(this::quake));
		waveSpeed = add(new NumberSetting("Wave speed", 14, 4, 40, 1, " b/s").describe("How fast the shock front runs out over the ground.")
				.visibleWhen(this::quake));
		tumble = add(new NumberSetting("Tumble", 60, 0, 100, 5, "%").describe("How much the blocks turn and flip in the air.")
				.visibleWhen(this::quake));
		cameraShake = add(new NumberSetting("Camera shake", 40, 0, 100, 5, "%").describe("How much your view shakes. 0 turns it off.")
				.visibleWhen(this::quake));
		dust = add(new BoolSetting("Dust", true).describe("Puffs of dust where the blocks leave and land.").visibleWhen(this::quake));
		thud = add(new BoolSetting("Thud sound", true).describe("The blocks thud as they land.").visibleWhen(this::quake));
		fallScale = add(new BoolSetting("Scale with fall", true).describe("Landings after longer falls throw the blocks higher and "
				+ "further.").visibleWhen(this::quake));
		group();
		look = new Look(s -> {
			add(s);
			s.andVisibleWhen(this::notQuake);
		}, "Light");
		on(TickEvent.class, e -> tick());
	}
	@Override
	public void preview(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		level = mc.level;
		if (quake() && mc.level != null) {
			net.minecraft.core.BlockPos.MutableBlockPos p = net.minecraft.core.BlockPos.containing(at).mutable();
			for (int i = 0; i < 8 && mc.level.getBlockState(p.below()).getCollisionShape(mc.level, p.below()).isEmpty(); i++) {
				p.move(0, -1, 0);
			}
			quakeAt(at.x, p.getY(), at.z, 1.2);
			return;
		}
		spawn(at.x, at.y, at.z, true);
	}
	private boolean quake() {
		return style.is("Earthquake");
	}

	private boolean notQuake() {
		return !quake();
	}

	private void quakeAt(double x, double y, double z, double strength) {
		GroundQuake.start(x, y, z, strength, new GroundQuake.Settings(quakeRadius.get(), quakeHeight.get(), waveSpeed.get(),
				tumble.get() / 100.0, cameraShake.get() / 100.0, dust.get(), thud.get()));
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			circles.clear();
			ground.clear();
		}
		if (!quake() && GroundQuake.active()) {
			GroundQuake.reset();
		}
		Iterable<? extends Entity> entities = who.is("Self") ? nz.temple.client.util.Wearer.selves()
				: who.is("Players") ? mc.level.players() : mc.level.entitiesForRendering();
		for (Entity entity : entities) {
			if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity.distanceToSqr(mc.player) > 64 * 64) {
				continue;
			}
			boolean onGround = entity.onGround();
			double[] state = ground.get(entity.getId());
			if (state == null) {
				ground.put(entity.getId(), new double[] {onGround ? 1 : 0, entity.getY(), mc.level.getGameTime(), entity.getY()});
				continue;
			}
			boolean was = state[0] == 1;
			boolean quaking = quake() && entity instanceof net.minecraft.world.entity.player.Player;
			if (was && !onGround && entity.getDeltaMovement().y > 0.08 && !trigger.is("Land")) {
				if (quaking) {
					quakeAt(entity.getX(), state[1], entity.getZ(), 0.85);
				} else if (!quake()) {
					spawn(entity.getX(), state[1], entity.getZ(), false);
				}
			} else if (!was && onGround && !trigger.is("Jump")) {
				double fall = Math.max(0, state[3] - entity.getY());
				if (quaking) {
					if (fall > 0.6) {
						quakeAt(entity.getX(), entity.getY(), entity.getZ(), fallScale.get() ? Math.min(2.0, 0.75 + fall * 0.12) : 1);
					}
				} else if (!quake()) {
					spawn(entity.getX(), entity.getY(), entity.getZ(), true);
				}
			}
			state[0] = onGround ? 1 : 0;
			if (onGround) {
				state[1] = entity.getY();
				state[3] = entity.getY();
			} else {
				state[3] = Math.max(state[3], entity.getY());
			}
			state[2] = mc.level.getGameTime();
		}
		long now = mc.level.getGameTime();
		ground.values().removeIf(s -> now - s[2] > 40);
	}
	private void spawn(double x, double y, double z, boolean landing) {
		while (circles.size() >= MAX) {
			circles.removeFirst();
		}
		double[] cells = style.is("Block highlight") ? groundCells(x, y, z) : null;
		circles.add(new Circle(x, y + 0.02, z, System.nanoTime(), Math.random(), landing, cells));
	}
	private double[] groundCells(double x, double y, double z) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return new double[0];
		}
		double reach = highlightRadius.get();
		int r = (int) Math.ceil(reach);
		int baseY = (int) Math.floor(y - 0.01);
		int cx = (int) Math.floor(x);
		int cz = (int) Math.floor(z);
		java.util.List<Double> out = new java.util.ArrayList<>();
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		net.minecraft.core.BlockPos.MutableBlockPos above = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double dist = Math.sqrt((cx + dx + 0.5 - x) * (cx + dx + 0.5 - x) + (cz + dz + 0.5 - z) * (cz + dz + 0.5 - z));
				if (dist > reach) {
					continue;
				}
				for (int dy = 1; dy >= -2; dy--) {
					pos.set(cx + dx, baseY + dy, cz + dz);
					var shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
					if (shape.isEmpty()) {
						continue;
					}
					above.set(pos.getX(), pos.getY() + 1, pos.getZ());
					if (!mc.level.getBlockState(above).getCollisionShape(mc.level, above).isEmpty()) {
						break;
					}
					out.add((double) pos.getX());
					out.add(pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y));
					out.add((double) pos.getZ());
					out.add(dist);
					break;
				}
			}
		}
		double[] cells = new double[out.size()];
		for (int i = 0; i < cells.length; i++) {
			cells[i] = out.get(i);
		}
		return cells;
	}
	public void emit(Fx fx, float partial) {
		if (!isEnabled() || level != Minecraft.getInstance().level && level != null) {
			circles.clear();
			return;
		}
		long now = System.nanoTime();
		double life = duration.get() * 1e9;
		circles.removeIf(c -> now - c.born() > life);
		for (Circle c : circles) {
			double t = (now - c.born()) / life;
			switch (style.get()) {
				case "Rings" -> rings(fx, c, t);
				case "Shockwave" -> shockwave(fx, c, t);
				case "Crown" -> crown(fx, c, t);
				case "Block highlight" -> highlight(fx, c, t);
				case "Earthquake" -> { }
				default -> ripple(fx, c, t);
			}
		}
	}
	private static final Quaternionf FLAT = new Quaternionf();
	private void rings(Fx fx, Circle c, double t) {
		Meshes mesh = fx.mesh();
		look.apply(mesh, 0.1f);
		double r = radius.get() * (c.landing() ? 1 : 0.8);
		for (int k = 0; k < 3; k++) {
			double tk = (t - k * 0.14) / 0.72;
			if (tk <= 0 || tk >= 1) {
				continue;
			}
			double spread = ease(tk);
			double big = r * spread;
			double fade = (1 - tk) * (1 - tk);
			double crest = 0.1 * (1 - k * 0.25) * (1 - tk * 0.7) * r;
			double tube = Math.max(0.03, crest * 0.6);
			int index = k;
			mesh.torus(c.x(), c.y() + crest * 0.35, c.z(), FLAT, big, tube, 0, 48,
					(u, v) -> look.color(c.seed() + u * 0.3 + index * 0.1, fade));
		}
		if (t < 0.45) {
			double f = 1 - t / 0.45;
			mesh.ellipsoid(c.x(), c.y(), c.z(), r * 0.3 * (0.6 + t), 0.03 * f, r * 0.3 * (0.6 + t), null,
					look.color(c.seed(), 0.8 * f), 10);
		}
		glow(fx, c, t, r);
	}
	private void shockwave(Fx fx, Circle c, double t) {
		Meshes mesh = fx.mesh();
		look.apply(mesh, 0.35f);
		double r = radius.get() * (c.landing() ? 1.1 : 0.85) * ease(t);
		double fade = 1 - t;
		mesh.torus(c.x(), c.y() + 0.03, c.z(), FLAT, r, 0.05 * (1 - t) + 0.01, 0, 56, (u, v) -> look.color(c.seed() + u * 0.5, fade));
		mesh.material(Meshes.Material.GLASS);
		mesh.emission(0.05f);
		double h = r * 0.55 * Math.sin(Math.min(1, t * 1.4) * Math.PI);
		if (h > 0.02) {
			mesh.ellipsoid(c.x(), c.y(), c.z(), r, h, r, null, look.color(c.seed() + 0.3, 0.35 * fade), 12);
		}
		look.apply(mesh, 0.2f);
		for (int i = 0; i < 10; i++) {
			double a = Math.PI * 2 * i / 10 + c.seed() * 6;
			double fly = Math.min(1, t * 1.6);
			double out = r * (0.9 + 0.2 * Math.sin(i * 3.1));
			double y = c.y() + Math.sin(fly * Math.PI) * 0.35 * (0.6 + 0.4 * Math.cos(i * 1.7));
			Quaternionf spin = new Quaternionf().rotationXYZ((float) (t * 9 + i), (float) (t * 7), (float) i);
			mesh.crystal(c.x() + Math.cos(a) * out, y, c.z() + Math.sin(a) * out, spin, 0.12, 0.035,
					look.color(c.seed() + i * 0.05, fade), 4);
		}
		glow(fx, c, t, r);
	}
	private void ripple(Fx fx, Circle c, double t) {
		Meshes mesh = fx.mesh();
		look.apply(mesh, 0.15f);
		double r = radius.get() * (c.landing() ? 1 : 0.8);
		double ring = r * 0.5 * ease(t);
		double fade = 1 - t * t;
		mesh.torus(c.x(), c.y() + 0.02, c.z(), FLAT, ring, 0.035 * (1 - t) + 0.008, 0, 48,
				(u, v) -> look.color(c.seed() + u * 0.3, fade));
		int drops = 14;
		for (int i = 0; i < drops; i++) {
			double a = Math.PI * 2 * i / drops + c.seed() * 6.283;
			double vary = 0.7 + 0.3 * Math.sin(i * 2.3 + c.seed() * 9);
			double time = t * duration.get();
			double speedOut = r * 0.9 * vary;
			double up = 2.4 * vary * (c.landing() ? 1 : 0.8);
			double y = c.y() + up * time - 4.9 * time * time;
			if (y < c.y()) {
				continue;
			}
			double out = ring + speedOut * time;
			double vy = up - 9.8 * time;
			double size = 0.05 * vary * (1 - t * 0.5);
			double vx = Math.cos(a) * speedOut;
			double vz = Math.sin(a) * speedOut;
			double v = Math.sqrt(vx * vx + vy * vy + vz * vz);
			double stretch = 1 + Math.min(1.2, v * 0.25);
			Quaternionf along = Meshes.pointing(vx, vy, vz);
			mesh.ellipsoid(c.x() + Math.cos(a) * out, y, c.z() + Math.sin(a) * out, size, size * stretch, size, along,
					look.color(c.seed() + i * 0.04, fade), 6);
		}
		glow(fx, c, t, r);
	}
	private void crown(Fx fx, Circle c, double t) {
		Meshes mesh = fx.mesh();
		look.apply(mesh, 0.15f);
		double grow = ease(Math.min(1, t / 0.25));
		double shrink = t > 0.7 ? 1 - (t - 0.7) / 0.3 : 1;
		double k = grow * shrink;
		if (k < 0.02) {
			return;
		}
		double r = radius.get() * 0.22 * (c.landing() ? 1 : 0.85) * k;
		double band = r * 0.45;
		double peak = r * 0.55;
		double y0 = c.y() + 0.05 + t * 0.35;
		double spin = c.seed() * Math.PI * 2 + t * 5;
		double fade = Math.min(1, (1 - t) * 3);
		int segments = 24;
		int points = 5;
		for (int i = 0; i < segments; i++) {
			double a0 = spin + Math.PI * 2 * i / segments;
			double a1 = spin + Math.PI * 2 * (i + 1) / segments;
			double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
			int col = look.color(c.seed() + (double) i / segments, fade);
			for (int side = -1; side <= 1; side += 2) {
				double[] b0 = {c.x() + c0 * r, y0, c.z() + s0 * r, side * c0, 0, side * s0};
				double[] b1 = {c.x() + c1 * r, y0, c.z() + s1 * r, side * c1, 0, side * s1};
				double[] t1 = {c.x() + c1 * r, y0 + band, c.z() + s1 * r, side * c1, 0, side * s1};
				double[] t0 = {c.x() + c0 * r, y0 + band, c.z() + s0 * r, side * c0, 0, side * s0};
				if (side > 0) {
					mesh.quad(b0, t0, t1, b1, col, col, col, col);
				} else {
					mesh.quad(b0, b1, t1, t0, col, col, col, col);
				}
			}
		}
		double half = Math.PI / points * 0.55;
		for (int i = 0; i < points; i++) {
			double a = spin + Math.PI * 2 * i / points;
			double ca = Math.cos(a), sa = Math.sin(a);
			double cl = Math.cos(a - half), sl = Math.sin(a - half), cr = Math.cos(a + half), sr = Math.sin(a + half);
			int col = look.color(c.seed() + (double) i / points, fade);
			double[] apex = {c.x() + ca * r, y0 + band + peak, c.z() + sa * r};
			for (int side = -1; side <= 1; side += 2) {
				double[] left = {c.x() + cl * r, y0 + band, c.z() + sl * r, side * ca, 0, side * sa};
				double[] right = {c.x() + cr * r, y0 + band, c.z() + sr * r, side * ca, 0, side * sa};
				double[] tip = {apex[0], apex[1], apex[2], side * ca, 0, side * sa};
				if (side > 0) {
					mesh.quad(left, tip, tip, right, col, col, col, col);
				} else {
					mesh.quad(left, right, tip, tip, col, col, col, col);
				}
			}
		}
		glow(fx, c, t, r * 2);
	}
	private void highlight(Fx fx, Circle c, double t) {
		double[] cells = c.cells();
		if (cells == null || cells.length == 0) {
			return;
		}
		double reach = highlightRadius.get() * (c.landing() ? 1 : 0.85);
		double front = (reach + 1.2) * ease(Math.min(1, t * 1.15));
		double band = 1.4;
		double fade = t < 0.75 ? 1 : 1 - (t - 0.75) / 0.25;
		double line = outlineWidth.get() / 100.0;
		Meshes mesh = fx.mesh();
		look.apply(mesh, 0.25f);
		fx.throughWalls(false);
		for (int i = 0; i + 3 < cells.length; i += 4) {
			double d = cells[i + 3];
			if (d > reach || d > front) {
				continue;
			}
			double behind = (front - d) / band;
			double crest = Math.exp(-behind * behind * 2.2);
			double glowLeft = Math.max(0, 1 - behind * 0.35);
			double k = Math.max(crest, glowLeft * 0.35) * fade;
			if (k < 0.02) {
				continue;
			}
			double x0 = cells[i];
			double y = cells[i + 1] + 0.004 + crest * 0.07;
			double z0 = cells[i + 2];
			double tone = c.seed() + d * 0.08;
			int col = look.color(tone, k);
			if (crest > 0.15) {
				mesh.box(x0 + 0.5, y + 0.01, z0 + 0.5, null, 0.47, 0.012 * crest, 0.47, look.color(tone, crest * 0.8 * fade));
			}
			fx.additive(false);
			fx.quad(x0 + 0.02, y, z0 + 0.02, Fx.fade(col, 0.4), x0 + 0.98, y, z0 + 0.02, Fx.fade(col, 0.4),
					x0 + 0.98, y, z0 + 0.98, Fx.fade(col, 0.4), x0 + 0.02, y, z0 + 0.98, Fx.fade(col, 0.4));
			if (line > 0) {
				fx.additive(true);
				double yl = y + 0.006;
				int edge = Fx.fade(col, Math.min(1, 0.55 + crest * 0.45));
				fx.beam(x0, yl, z0, x0 + 1, yl, z0, line, edge, edge);
				fx.beam(x0 + 1, yl, z0, x0 + 1, yl, z0 + 1, line, edge, edge);
				fx.beam(x0 + 1, yl, z0 + 1, x0, yl, z0 + 1, line, edge, edge);
				fx.beam(x0, yl, z0 + 1, x0, yl, z0, line, edge, edge);
				if (crest > 0.3 && look.bloom() > 0.2f) {
					fx.glow(x0 + 0.5, yl + 0.05, z0 + 0.5, 0.55, Fx.fade(col, 0.25 * crest * look.bloom()));
				}
				fx.additive(false);
			}
		}
	}
	private void glow(Fx fx, Circle c, double t, double r) {
		if (look.bloom() < 0.2f) {
			return;
		}
		fx.additive(true);
		fx.ring(c.x(), c.y() + 0.01, c.z(), Math.max(0, r * ease(t) - 0.25), r * ease(t) + 0.25,
				look.color(c.seed(), 0.25 * look.bloom() * (1 - t)), look.color(c.seed(), 0) & 0xFFFFFF, 40);
		fx.additive(false);
	}
	private static double ease(double t) {
		double u = 1 - Math.max(0, Math.min(1, t));
		return 1 - u * u * u;
	}
	@Override
	protected void onDisable() {
		circles.clear();
		ground.clear();
		GroundQuake.reset();
	}
}
