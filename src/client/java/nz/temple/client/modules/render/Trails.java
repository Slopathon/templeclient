package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.Anchor;
import nz.temple.client.cosmetic.GlowLook;
import nz.temple.client.cosmetic.trail.TrailParticles;
import nz.temple.client.cosmetic.trail.TrailParticles.Kind;
import nz.temple.client.modules.cosmetic.Previewable;
import nz.temple.client.render.BodySnapshot;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.cosmetic.Colours;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Trails extends Module implements Previewable, nz.temple.client.render.FxSource, nz.temple.client.render.GlowUser {
	private static final int SOURCES = 6;
	private final ModeSetting type;
	private final nz.temple.setting.BoolSetting realism;
	private static final java.util.Set<String> ORIGINAL_STYLES = java.util.Set.of("Tube", "Ribbon", "Beads", "Sparkles", "Wisps", "Shards");
	private final ModeSetting element;
	private final ModeSetting source;
	private final ModeSetting who;
	private final NumberSetting length;
	private final NumberSetting density;
	private final NumberSetting width;
	private final NumberSetting spacing;
	private final BoolSetting onlyMoving;
	private final BoolSetting firstPerson;
	private final GlowLook look;
	private final TrailParticles particles = new TrailParticles(1600);
	private final Map<Integer, Mover> movers = new HashMap<>();
	private ClientLevel level;
	private long lastNanos = System.nanoTime();
	private double previewBorn = -1;
	private Vec3 previewAt;
	private Mover previewMover = new Mover();
	private Vec3 previewLast;
	private static final class Mover {
		double x, y, z;
		boolean init;
		double carried;
		double stepCarry;
		double airTime;
		final ArrayDeque<double[]> steps = new ArrayDeque<>();
		@SuppressWarnings("unchecked")
		final ArrayDeque<double[]>[] ribbons = new ArrayDeque[SOURCES];
		final ArrayDeque<Object[]> images = new ArrayDeque<>();
		double lastImage;
		double moving;
		Mover() {
			for (int i = 0; i < SOURCES; i++) {
				ribbons[i] = new ArrayDeque<>();
			}
		}
	}
	public Trails() {
		super("Trails", Category.COSMETICS, "Trails behind players: fire, frost, stardust, smoke or lightning left in the world, "
				+ "ribbons of light from the hands, feet or back, or afterimages of the body.");
		type = add(new ModeSetting("Type", List.of("Tube", "Ribbon", "Beads", "Sparkles", "Wisps", "Shards", "Afterimages", "Elemental",
				"Energy"), "Energy")
				.describe("Tube, Ribbon, Beads, Sparkles, Wisps and Shards are trails of light behind you. Afterimages leaves see-through "
						+ "copies of your body. Elemental leaves fire, frost, stardust, smoke or lightning behind you. Energy draws "
						+ "ribbons of light from your limbs."));
		realism = add(new nz.temple.setting.BoolSetting("Realism", false).describe("Afterimages copy your real body, with your skin and "
				+ "pose. Off uses the original glowing figures.").visibleWhen(() -> type.is("Afterimages")));
		element = add(new ModeSetting("Element", List.of("Fire", "Frost", "Stardust", "Smoke", "Lightning"), "Fire")
				.describe("Fire: flames that rise and cool to smoke, with bouncing embers. Frost: ice that grows on the ground and melts, "
						+ "with low mist. Stardust: twinkling motes drifting up. Smoke: thick curling smoke. Lightning: arcs between footsteps.")
				.visibleWhen(() -> type.is("Elemental")));
		source = add(new ModeSetting("Ribbons from", List.of("Hands", "Feet", "Back", "All"), "All")
				.describe("Where the ribbons of light stream from.")
				.visibleWhen(() -> type.is("Energy")));
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Self")
				.describe("Self shows only your trail. Players shows other players. Both shows everyone."));
		length = add(new NumberSetting("Length", 2, 0.3, 3, 0.05, " s").describe("How long the trail lasts."));
		density = add(new NumberSetting("Density", 100, 30, 200, 5, "%").describe("How much is shed as you move.")
				.visibleWhen(() -> type.is("Elemental")));
		width = add(new NumberSetting("Ribbon width", 0.24, 0.02, 0.4, 0.01, " b").describe("How wide the ribbons are at their head.")
				.visibleWhen(() -> type.is("Energy")));
		spacing = add(new NumberSetting("Afterimage spacing", 0.17, 0.04, 0.4, 0.01, " s").describe("Time between afterimages.")
				.visibleWhen(() -> type.is("Afterimages")));
		onlyMoving = add(new BoolSetting("Only while moving", true)
				.describe("Only sheds a trail while moving. What is already there still fades out naturally."));
		firstPerson = add(new BoolSetting("Own trail in first person", false).describe("Shows your own trail in first person."));
		group();
		look = new GlowLook(s -> add(s), "Trail", () -> true);
		legacy(new nz.temple.client.modules.legacy.LegacyTrails(),
				() -> ORIGINAL_STYLES.contains(type.get()) || type.is("Afterimages") && !realism.get(), List.of(type, realism), "Style");
	}
	@Override
	public void preview(Vec3 at) {
		if (legacyActive()) {
			((nz.temple.client.modules.legacy.LegacyTrails) legacyModule()).preview(at);
			return;
		}
		previewAt = at;
		previewBorn = Theme.time();
		previewMover = new Mover();
		previewLast = null;
		if (type.is("Afterimages") && BodySnapshot.latest(Minecraft.getInstance().player.getId()) == null
				&& nz.temple.client.TempleNzClient.notifications() != null) {
			nz.temple.client.TempleNzClient.notifications().info("Trails", "Afterimages copy your body, so preview them in third person (F5).");
		}
	}
	@Override
	protected void syncToLegacy() {
		super.syncToLegacy();
		legacyModule().setting("Style").mirror(type);
	}
	public void emit(Fx fx, float partial) {
		if (legacyActive()) {
			((nz.temple.client.modules.legacy.LegacyTrails) legacyModule()).emit(fx, partial);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			clear();
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			clear();
		}
		long nanos = System.nanoTime();
		double dt = Math.min(0.1, (nanos - lastNanos) / 1e9);
		lastNanos = nanos;
		double t = Theme.time();
		boolean seesSelf = !mc.options.getCameraType().isFirstPerson() || nz.temple.client.util.DetachedCamera.active();
		Set<Integer> seen = new HashSet<>();
		int trailing = 0;
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if ((nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) || !player.isAlive() || player.isInvisible() || player.distanceToSqr(mc.player) > 80 * 80) {
				continue;
			}
			if (self && !seesSelf && !firstPerson.get()) {
				continue;
			}
			trailing++;
		}
		double share = trailing > 4 ? 4.0 / trailing : 1;
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if ((nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) || !player.isAlive() || player.isInvisible() || player.distanceToSqr(mc.player) > 80 * 80) {
				continue;
			}
			if (self && !seesSelf && !firstPerson.get()) {
				continue;
			}
			seen.add(player.getId());
			Mover m = movers.computeIfAbsent(player.getId(), k -> new Mover());
			Vec3 p = player.getPosition(partial);
			track(fx, m, player, p, partial, dt, t, share);
		}
		movers.keySet().retainAll(seen);
		previewStep(mc, dt, t);
		particles.step(dt, t);
		particles.draw(fx, fx.mesh(), look, t, mc.player.getEyePosition(partial));
		for (Mover m : movers.values()) {
			drawLightning(fx, m, t);
			drawRibbons(fx, m, t);
			drawImages(m, t);
		}
		drawLightning(fx, previewMover, t);
		drawRibbons(fx, previewMover, t);
		drawImages(previewMover, t);
	}
	private void track(Fx fx, Mover m, Player player, Vec3 p, float partial, double dt, double t, double share) {
		if (!m.init) {
			m.x = p.x;
			m.y = p.y;
			m.z = p.z;
			m.init = true;
		}
		double dx = p.x - m.x, dy = p.y - m.y, dz = p.z - m.z;
		double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (moved > 4) {
			moved = 0;
		}
		m.x = p.x;
		m.y = p.y;
		m.z = p.z;
		double speed = dt > 0 ? moved / dt : 0;
		m.moving += ((speed > 0.6 ? 1 : 0) - m.moving) * (1 - Math.exp(-8 * dt));
		boolean active = m.moving > 0.5 || !onlyMoving.get();
		if (type.is("Elemental")) {
			double rate = density.get() / 100.0 * share;
			m.carried += moved * rate + (active && moved < 0.01 && !onlyMoving.get() ? dt * 0.8 : 0);
			double sprint = Math.min(1.5, speed / 5.6);
			shed(m, player, p, t, sprint);
			if (!player.onGround()) {
				m.airTime += dt;
			} else {
				if (m.airTime > 0.4) {
					burst(p, t);
				}
				m.airTime = 0;
			}
			if (element.is("Lightning")) {
				m.stepCarry += moved;
				if (m.stepCarry > 0.35) {
					m.stepCarry = 0;
					m.steps.add(new double[] {p.x, p.y + 0.05, p.z, t});
				}
			}
		} else if (type.is("Energy")) {
			Anchor a = Anchor.of(player, partial);
			Vector3d[] points = {a.limbEnd(Anchor.RIGHT_ARM), a.limbEnd(Anchor.LEFT_ARM), a.limbEnd(Anchor.RIGHT_LEG), a.limbEnd(Anchor.LEFT_LEG),
					a.bodyPoint(-0.13, -0.15, -0.16), a.bodyPoint(0.13, -0.15, -0.16)};
			for (int i = 0; i < SOURCES; i++) {
				if (active && sourceOn(i)) {
					m.ribbons[i].add(new double[] {points[i].x, points[i].y, points[i].z, t});
					peel(m.ribbons[i], t);
				}
			}
		} else if (active && t - m.lastImage >= spacing.get() && moved > 0.001) {
			BodySnapshot.Snap snap = BodySnapshot.latest(player.getId());
			if (snap != null) {
				m.lastImage = t;
				m.images.add(new Object[] {snap, t});
			}
		}
		prune(m, t);
	}
	private void prune(Mover m, double t) {
		double life = length.get();
		while (!m.steps.isEmpty() && t - m.steps.peekFirst()[3] > life * 0.7) {
			m.steps.removeFirst();
		}
		for (ArrayDeque<double[]> r : m.ribbons) {
			while (!r.isEmpty() && t - r.peekFirst()[3] > life * 0.6) {
				r.removeFirst();
			}
		}
		while (!m.images.isEmpty() && t - (double) m.images.peekFirst()[1] > life) {
			m.images.removeFirst();
		}
	}
	private boolean sourceOn(int i) {
		return switch (source.get()) {
			case "Feet" -> i == 2 || i == 3;
			case "Back" -> i == 4 || i == 5;
			case "All" -> true;
			default -> i == 0 || i == 1;
		};
	}
	private void shed(Mover m, Player player, Vec3 p, double t, double sprint) {
		double unit = switch (element.get()) {
			case "Fire" -> 0.07;
			case "Frost" -> 0.1;
			case "Stardust" -> 0.06;
			case "Smoke" -> 0.09;
			default -> 1e9;
		};
		double life = length.get() * (1 + 0.3 * sprint);
		double ground = p.y;
		while (m.carried >= unit) {
			m.carried -= unit;
			double rx = (Math.random() - 0.5) * 0.4, rz = (Math.random() - 0.5) * 0.4;
			switch (element.get()) {
				case "Fire" -> {
					particles.add(Kind.FLAME, p.x + rx, p.y + 0.1 + Math.random() * 0.7, p.z + rz, rx * 0.4, 0.4 + Math.random() * 0.6, rz * 0.4,
							life * (0.45 + Math.random() * 0.35), 0.11 + Math.random() * 0.09, ground);
					if (Math.random() < 0.18) {
						particles.add(Kind.EMBER, p.x + rx, p.y + 0.4, p.z + rz, (Math.random() - 0.5) * 1.4, 1.2 + Math.random() * 1.2,
								(Math.random() - 0.5) * 1.4, life * 1.4, 0.025 + Math.random() * 0.015, ground);
					}
				}
				case "Frost" -> {
					if (Math.random() < 0.3) {
						particles.crystal(p.x + rx * 1.2, ground, p.z + rz * 1.2, 2.2 + Math.random() * 1.2, 0.07 + Math.random() * 0.08, t);
					}
					particles.add(Kind.MIST, p.x + rx, p.y + 0.05 + Math.random() * 0.25, p.z + rz, rx * 0.8, 0, rz * 0.8, life * (1.4 + Math.random() * 0.7),
							0.35 + Math.random() * 0.3, ground);
					if (Math.random() < 0.7) {
						particles.add(Kind.ICE_DUST, p.x + rx * 1.5, p.y + 0.2 + Math.random() * 1.4, p.z + rz * 1.5, rx * 0.2, 0.05, rz * 0.2,
								life * (1 + Math.random() * 0.6), 0.015 + Math.random() * 0.015, ground);
					}
				}
				case "Stardust" -> {
					particles.add(Kind.MOTE, p.x + rx * 1.6, p.y + 0.2 + Math.random() * 1.4, p.z + rz * 1.6, rx * 0.15, 0.05 + Math.random() * 0.25,
							rz * 0.15, life * (1 + Math.random()), 0.035 + Math.random() * 0.035, ground);
					if (Math.random() < 0.1) {
						particles.add(Kind.HAZE, p.x + rx, p.y + 0.9, p.z + rz, 0, 0.05, 0, life * 2, 0.4 + Math.random() * 0.3, ground);
					}
				}
				case "Smoke" -> particles.add(Kind.SMOKE, p.x + rx, p.y + 0.2 + Math.random() * 1.0, p.z + rz, rx * 0.3, 0.1 + Math.random() * 0.3,
						rz * 0.3, life * (1.2 + Math.random() * 0.8), 0.2 + Math.random() * 0.15, ground);
				default -> {
				}
			}
		}
	}
	private void burst(Vec3 p, double t) {
		double life = length.get();
		for (int i = 0; i < 18; i++) {
			double a = Math.PI * 2 * i / 18;
			double vx = Math.cos(a) * 1.6, vz = Math.sin(a) * 1.6;
			switch (element.get()) {
				case "Fire" -> particles.add(Kind.FLAME, p.x, p.y + 0.1, p.z, vx, 0.6, vz, life * 0.5, 0.14, p.y);
				case "Frost" -> {
					particles.add(Kind.MIST, p.x, p.y + 0.05, p.z, vx, 0, vz, life * 1.6, 0.4, p.y);
					if (i % 3 == 0) {
						particles.crystal(p.x + Math.cos(a) * 0.5, p.y, p.z + Math.sin(a) * 0.5, 2.5, 0.12, t);
					}
				}
				case "Stardust" -> particles.add(Kind.MOTE, p.x, p.y + 0.15, p.z, vx * 0.6, 0.5, vz * 0.6, life * 1.4, 0.05, p.y);
				case "Smoke" -> particles.add(Kind.SMOKE, p.x, p.y + 0.1, p.z, vx * 0.7, 0.1, vz * 0.7, life * 1.6, 0.25, p.y);
				default -> {
				}
			}
		}
	}
	private void peel(ArrayDeque<double[]> r, double t) {
		if (r.size() < 3) {
			return;
		}
		Iterator<double[]> it = r.descendingIterator();
		double[] a = it.next(), b = it.next(), c = it.next();
		double ux = a[0] - b[0], uy = a[1] - b[1], uz = a[2] - b[2];
		double vx = b[0] - c[0], vy = b[1] - c[1], vz = b[2] - c[2];
		double lu = Math.sqrt(ux * ux + uy * uy + uz * uz), lv = Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (lu < 0.01 || lv < 0.01) {
			return;
		}
		double cos = (ux * vx + uy * vy + uz * vz) / (lu * lv);
		if (cos < 0.9 && Math.random() < 0.6) {
			particles.add(Kind.MOTE, a[0], a[1], a[2], ux / lu * 0.8 + (Math.random() - 0.5) * 0.6, 0.2, uz / lu * 0.8 + (Math.random() - 0.5) * 0.6,
					0.5 + Math.random() * 0.4, 0.035, a[1] - 2);
		}
	}
	private void drawRibbons(Fx fx, Mover m, double t) {
		double life = length.get() * 0.6;
		fx.additive(true);
		for (int i = 0; i < SOURCES; i++) {
			ArrayDeque<double[]> r = m.ribbons[i];
			int n = r.size();
			if (n < 3) {
				continue;
			}
			double[] path = new double[n * 3];
			double[] widths = new double[n];
			int[] colours = new int[n];
			int k = 0;
			for (double[] q : r) {
				double age = (t - q[3]) / life;
				double keep = Math.max(0, 1 - age);
				path[k * 3] = q[0];
				path[k * 3 + 1] = q[1];
				path[k * 3 + 2] = q[2];
				widths[k] = width.get() * Math.pow(keep, 1.3);
				colours[k] = look.colour(i * 0.13 + age * 0.4, Math.pow(keep, 1.6));
				k++;
			}
			fx.ribbon(path, n, widths, colours, 0.6);
			double[] halo = new double[n];
			int[] soft = new int[n];
			for (int j = 0; j < n; j++) {
				halo[j] = widths[j] * 2.6;
				soft[j] = nz.temple.client.render.Fx.withAlpha(colours[j], nz.temple.client.render.Fx.alpha(colours[j]) * 0.28f);
			}
			fx.ribbon(path, n, halo, soft, 0);
		}
		fx.additive(false);
	}
	private void drawLightning(Fx fx, Mover m, double t) {
		if (m.steps.size() < 2) {
			return;
		}
		double life = length.get() * 0.7;
		fx.additive(true);
		double[] prev = null;
		long bucket = (long) (t * 18);
		int i = 0;
		for (double[] s : m.steps) {
			double age = (t - s[3]) / life;
			double keep = Math.max(0, 1 - age);
			fx.softPool(s[0], s[1] - 0.04, s[2], new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.35, look.colour(0.3, 0.35 * keep * keep));
			if (prev != null) {
				double h = hash(bucket * 31 + i * 7);
				if (h > 0.25) {
					int core = Colours.withAlpha(Colours.lerp(0xFFFFFFFF, look.colour(0), 0.3), keep * look.alpha());
					int edge = look.colour(0.2, 0.6 * keep);
					fx.arc(prev[0], prev[1] + 0.12, prev[2], s[0], s[1] + 0.12, s[2], 9, 0.14, 0.055, core, edge, bucket * 13.1 + i);
					fx.arc(prev[0], prev[1] + 0.12, prev[2], s[0], s[1] + 0.12, s[2], 9, 0.14, 0.16, Colours.withAlpha(edge, Fx.alpha(edge) / 255.0 * 0.35),
							Colours.withAlpha(edge, 0), bucket * 13.1 + i);
					if (h > 0.6) {
						double mx = (prev[0] + s[0]) / 2, my = (prev[1] + s[1]) / 2 + 0.12, mz = (prev[2] + s[2]) / 2;
						double bx = mx + (hash(bucket + i * 3) - 0.5) * 0.9, by = my + 0.3 + hash(bucket * 7 + i) * 0.5, bz = mz + (hash(bucket * 5 + i) - 0.5) * 0.9;
						fx.arc(mx, my, mz, bx, by, bz, 6, 0.1, 0.02, core, Colours.withAlpha(edge, 0), bucket * 3.7 + i);
					}
					fx.glow((prev[0] + s[0]) / 2, (prev[1] + s[1]) / 2 + 0.15, (prev[2] + s[2]) / 2, 0.5, look.colour(0.1, 0.18 * keep));
				}
			}
			prev = s;
			i++;
		}
		fx.additive(false);
	}
	private void drawImages(Mover m, double t) {
		double life = length.get();
		for (Object[] im : m.images) {
			BodySnapshot.Snap snap = (BodySnapshot.Snap) im[0];
			double age = (t - (double) im[1]) / life;
			double keep = Math.max(0, 1 - age);
			double shimmer = 0.85 + 0.15 * Math.sin(t * 9 + (double) im[1] * 5);
			int c = look.colour(age * 0.5);
			double lift = age * 0.15;
			double ox = im.length > 2 ? (double) im[2] : 0, oz = im.length > 2 ? (double) im[3] : 0;
			BodySnapshot.ghost(snap, ox, lift, oz, 1, Colours.withAlpha(Colours.lerp(c, 0xFFFFFFFF, 0.35), 0.45 * Math.pow(keep, 1.4) * shimmer * look.alpha()));
			BodySnapshot.ghost(snap, ox, lift, oz, 1.06f, Colours.withAlpha(c, 0.18 * Math.pow(keep, 1.2) * look.alpha()));
		}
	}
	private void previewStep(Minecraft mc, double dt, double t) {
		Mover m = previewMover;
		prune(m, t);
		if (previewAt == null || previewBorn < 0) {
			return;
		}
		double age = t - previewBorn;
		if (age > 2.5) {
			previewAt = null;
			return;
		}
		Vec3 p = previewPoint(age);
		Vec3 ahead = previewPoint(age + 0.05).subtract(p);
		Vec3 forward = ahead.horizontalDistanceSqr() < 1e-8 ? new Vec3(0, 0, 1) : new Vec3(ahead.x, 0, ahead.z).normalize();
		Vec3 side = new Vec3(-forward.z, 0, forward.x);
		double moved = previewLast == null ? 0 : p.distanceTo(previewLast);
		previewLast = p;
		if (type.is("Elemental")) {
			m.carried += moved * density.get() / 100.0;
			shed(m, null, p, t, 0.6);
			if (element.is("Lightning")) {
				m.stepCarry += moved;
				if (m.stepCarry > 0.35) {
					m.stepCarry = 0;
					m.steps.add(new double[] {p.x, p.y + 0.05, p.z, t});
				}
			}
		} else if (type.is("Energy")) {
			double swing = Math.sin(age * 9);
			Vec3[] points = {
					p.add(side.scale(0.38)).add(forward.scale(-0.25 * swing)).add(0, 0.75, 0),
					p.add(side.scale(-0.38)).add(forward.scale(0.25 * swing)).add(0, 0.75, 0),
					p.add(side.scale(0.13)).add(forward.scale(0.3 * swing)).add(0, 0.08 + 0.08 * Math.max(0, swing), 0),
					p.add(side.scale(-0.13)).add(forward.scale(-0.3 * swing)).add(0, 0.08 + 0.08 * Math.max(0, -swing), 0),
					p.add(side.scale(-0.13)).add(forward.scale(-0.16)).add(0, 1.2, 0),
					p.add(side.scale(0.13)).add(forward.scale(-0.16)).add(0, 1.2, 0)};
			for (int i = 0; i < SOURCES; i++) {
				if (sourceOn(i)) {
					m.ribbons[i].add(new double[] {points[i].x, points[i].y, points[i].z, t});
					peel(m.ribbons[i], t);
				}
			}
		} else if (t - m.lastImage >= spacing.get() && mc.player != null) {
			BodySnapshot.Snap snap = BodySnapshot.latest(mc.player.getId());
			if (snap != null) {
				m.lastImage = t;
				Vec3 at = mc.player.position();
				m.images.add(new Object[] {snap, t, p.x - at.x, p.z - at.z});
			}
		}
	}
	private Vec3 previewPoint(double age) {
		return new Vec3(previewAt.x + Math.sin(age * 2) * 1.5, previewAt.y, previewAt.z + Math.cos(age * 1.3) * 1.2);
	}
	private void clear() {
		particles.clear();
		movers.clear();
		previewMover = new Mover();
		previewAt = null;
	}
	@Override
	protected void onDisable() {
		clear();
	}
	public boolean glowing() {
		if (legacyActive()) {
			return ((nz.temple.client.modules.legacy.LegacyTrails) legacyModule()).glowing();
		}
		return false;
	}
	private static double hash(double n) {
		double x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
		return x - Math.floor(x);
	}
}
