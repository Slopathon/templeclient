package nz.temple.client.modules.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Theme;
import nz.temple.client.render.UiScale;
import nz.temple.client.util.HitConfirm;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class HitMarkers extends Module implements nz.temple.client.render.FxSource, nz.temple.client.render.HudSource {
	private final ModeSetting placement;
	private final ModeSetting playerStyle;
	private final ModeSetting finish;
	private final NumberSetting markerSize;
	private final NumberSetting ringArcs;
	private final NumberSetting ringGap;
	private final NumberSetting ringWidth;
	private final BoolSetting ringLock;
	private final NumberSetting border;
	private final NumberSetting roundness;
	private final NumberSetting arms;
	private final NumberSetting spin;
	private final NumberSetting sparks;
	private final BoolSetting throughWalls;
	private final ModeSetting style;
	private final NumberSetting size;
	private final NumberSetting duration;
	private final NumberSetting thickness;
	private final NumberSetting flakeCount;
	private final NumberSetting spread;
	private final ModeSetting colors;
	private final ColorSetting custom;
	private final ColorSetting killColor;
	private final BoolSetting killMarker;
	private final List<Mark> marks = new ArrayList<>();
	private record Flake(double angle, double speed, double spin, double scale) {
	}

	private static final class Mark {
		final long born;
		final boolean kill;
		final List<Flake> flakes;
		final int entity;
		final Vec3 offset;
		final double seed;
		Vec3 last;
		Mark(long born, boolean kill, List<Flake> flakes, int entity, Vec3 offset, Vec3 last) {
			this.born = born;
			this.kill = kill;
			this.flakes = flakes;
			this.entity = entity;
			this.offset = offset;
			this.last = last;
			this.seed = ThreadLocalRandom.current().nextDouble();
		}
	}
	public HitMarkers() {
		super("Hit Markers", Category.COSMETICS, "Shows a marker when a hit lands, either on the spot you hit or at your crosshair, with a "
				+ "larger marker for kills.");
		placement = add(new ModeSetting("Placement", List.of("On player", "On crosshair"), "On player")
				.describe("On player places the marker where your hit landed and it moves with the target. On crosshair places it at the "
						+ "centre of your screen."));
		group("On player");
		playerStyle = add(new ModeSetting("Marker", List.of("Ring", "Box", "Vortex", "Glow"), "Ring")
				.describe("Ring closes arcs around the spot. Box shows a small rounded box. Vortex spirals arms of light inward. Glow shows "
						+ "a burst of light with rays.")
				.visibleWhen(this::onPlayer));
		finish = add(new ModeSetting("Finish", List.of("Neon", "Glass", "Matte", "Metal", "Frosted"), "Neon")
				.describe("Neon has a bright core with a coloured glow. Glass refracts the world behind it. Matte is solid colour without "
						+ "glow. Metal is polished and shaded. Frosted is milky and softly lit.")
				.visibleWhen(this::onPlayer));
		markerSize = add(new NumberSetting("Marker size", 0.5, 0.15, 1.2, 0.05, " b")
				.describe("Marker size on the target. A player is 1.8 blocks tall.")
				.visibleWhen(this::onPlayer));
		ringArcs = add(new NumberSetting("Arcs", 4, 2, 8, 1, "").describe("Number of arcs in the ring.")
				.visibleWhen(() -> onPlayer() && playerStyle.is("Ring")));
		ringGap = add(new NumberSetting("Gap", 30, 5, 70, 5, "%").describe("Space between the arcs.")
				.visibleWhen(() -> onPlayer() && playerStyle.is("Ring")));
		ringWidth = add(new NumberSetting("Arc width", 3.5, 1, 8, 0.25, " cm").describe("Arc thickness.")
				.visibleWhen(() -> onPlayer() && playerStyle.is("Ring")));
		ringLock = add(new BoolSetting("Lock-on turn", true).describe("The arcs rotate a quarter turn and close in as they appear.")
				.visibleWhen(() -> onPlayer() && playerStyle.is("Ring")));
		border = add(new NumberSetting("Border", 2, 0.5, 6, 0.25, " cm").describe("Box border thickness.")
				.visibleWhen(() -> onPlayer() && playerStyle.is("Box")));
		roundness = add(new NumberSetting("Roundness", 35, 0, 100, 5, "%").describe("Box corner rounding. 100 is a circle.")
				.visibleWhen(() -> onPlayer() && playerStyle.is("Box")));
		arms = add(new NumberSetting("Arms", 3, 2, 6, 1, "").describe("Number of vortex arms.")
				.visibleWhen(() -> onPlayer() && playerStyle.is("Vortex")));
		spin = add(new NumberSetting("Spin", 100, 20, 300, 10, "%").describe("Vortex rotation speed.")
				.visibleWhen(() -> onPlayer() && playerStyle.is("Vortex")));
		sparks = add(new NumberSetting("Sparks", 10, 0, 24, 1, "").describe("Small particles pulled into the vortex or thrown from the "
				+ "burst.")
				.visibleWhen(() -> onPlayer() && (playerStyle.is("Vortex") || playerStyle.is("Glow"))));
		throughWalls = add(new BoolSetting("Through walls", true).describe("Shows markers through terrain.")
				.visibleWhen(this::onPlayer));
		group("On crosshair");
		style = add(new ModeSetting("Style", List.of("Flakes", "Cross", "Corners", "Dots"), "Flakes")
				.describe("Flakes bursts into spinning gold fragments. Cross, Corners and Dots are compact marker shapes.")
				.visibleWhen(() -> !onPlayer()));
		size = add(new NumberSetting("Size", 5, 2, 14, 0.5, " px").describe("Distance of the marker from the centre.")
				.visibleWhen(() -> !onPlayer()));
		thickness = add(new NumberSetting("Thickness", 1.6, 0.5, 4, 0.1, " px").describe("Line thickness.")
				.visibleWhen(() -> !onPlayer()));
		flakeCount = add(new NumberSetting("Flakes", 14, 4, 36, 1, "").describe("Fragments per hit.")
				.visibleWhen(() -> !onPlayer() && style.is("Flakes")));
		spread = add(new NumberSetting("Explosion spread", 18, 6, 48, 1, " px").describe("How far the fragments spread from the crosshair.")
				.visibleWhen(() -> !onPlayer() && style.is("Flakes")));
		group();
		duration = add(new NumberSetting("Duration", 0.7, 0.1, 2.5, 0.05, " s").describe("How long a marker lasts."));
		colors = add(new ModeSetting("Color", List.of("Theme", "Custom"), "Theme")
				.describe("Theme uses the HUD accent. Custom uses the colours below."));
		custom = add(new ColorSetting("Hit color", 0xFFE9B949).themed().describe("Colour for a normal hit.")
				.visibleWhen(() -> colors.is("Custom")));
		killMarker = add(new BoolSetting("Kill marker", true).describe("A larger marker when the target dies."));
		killColor = add(new ColorSetting("Kill color", 0xFFFF453A).describe("Kill marker colour.")
				.visibleWhen(killMarker::get));
		HitConfirm.listen(new HitConfirm.Listener() {
			@Override
			public void hit(LivingEntity target, Vec3 offset) {
				if (isEnabled()) {
					addMark(false, target, offset);
				}
			}
			@Override
			public void kill(Entity target, int entityId, Vec3 offset) {
				if (!isEnabled() || !killMarker.get()) {
					return;
				}
				Mark previous = latestOn(entityId);
				addMark(true, target, offset);
				if (target == null && previous != null) {
					marks.get(marks.size() - 1).last = previous.last;
				}
			}
		});
	}
	private boolean onPlayer() {
		return placement.is("On player");
	}
	private Mark latestOn(int entity) {
		for (int i = marks.size() - 1; i >= 0; i--) {
			if (marks.get(i).entity == entity) {
				return marks.get(i);
			}
		}
		return null;
	}
	private void addMark(boolean kill, Entity entity, Vec3 offset) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int count = (int) Math.round(flakeCount.get() * (kill ? 1.4 : 1));
		List<Flake> flakes = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			flakes.add(new Flake(random.nextDouble(Math.PI * 2), random.nextDouble(0.55, 1.15),
					random.nextDouble(-5, 5), random.nextDouble(0.65, 1.35)));
		}
		if (marks.size() >= 10) {
			marks.remove(0);
		}
		Vec3 off = offset == null ? new Vec3(0, entity == null ? 1 : entity.getBbHeight() * 0.6, 0) : offset;
		Vec3 last = entity == null ? null : entity.position().add(off);
		marks.add(new Mark(System.nanoTime(), kill, List.copyOf(flakes), entity == null ? -1 : entity.getId(), off, last));
	}
	private int baseColor(boolean kill, double offset) {
		return kill ? killColor.get() : colors.is("Theme") ? Theme.color(offset) : custom.get();
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !onPlayer() || mc.level == null) {
			return;
		}
		long now = System.nanoTime();
		double life = duration.get() * 1e9;
		marks.removeIf(m -> now - m.born > life);
		Vec3 right = fx.cameraRight();
		Vec3 up = fx.cameraUp();
		Vec3 cam = fx.cameraPos();
		fx.throughWalls(throughWalls.get());
		for (Mark m : marks) {
			Entity entity = m.entity < 0 ? null : mc.level.getEntity(m.entity);
			if (entity != null && entity.isAlive()) {
				m.last = entity.getPosition(partial).add(m.offset);
			}
			if (m.last == null) {
				continue;
			}
			Vec3 toCam = cam.subtract(m.last);
			Vec3 at = toCam.lengthSqr() < 1e-6 ? m.last : m.last.add(toCam.normalize().scale(0.06));
			double t = (now - m.born) / life;
			float fade = (float) Math.min(1, (1 - t) / 0.4);
			int c = baseColor(m.kill, m.seed * 0.2);
			double r = markerSize.get() / 2 * (m.kill ? 1.35 : 1);
			switch (playerStyle.get()) {
				case "Box" -> box(fx, at, right, up, r, t, fade, c, m.kill);
				case "Vortex" -> vortex(fx, at, right, up, r, t, fade, c, m);
				case "Glow" -> glow(fx, at, right, up, r, t, fade, c, m);
				default -> ring(fx, at, right, up, r, t, fade, c, m);
			}
		}
		fx.throughWalls(false);
		fx.additive(false);
	}
	private static double pop(double t) {
		double u = Math.min(1, t / 0.18);
		double c = 1.8;
		double back = 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
		return 1.25 - 0.25 * back;
	}

	private boolean flat() {
		return !finish.is("Glass") && !finish.is("Metal");
	}
	private void meshFinish(Meshes mesh) {
		if (finish.is("Glass")) {
			mesh.material(Meshes.Material.GLASS);
			mesh.emission(0.08f);
		} else {
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.15f);
		}
	}
	private static Vec3 onPlane(Vec3 at, Vec3 right, Vec3 up, double x, double y) {
		return at.add(right.scale(x)).add(up.scale(y));
	}
	private void ring(Fx fx, Vec3 at, Vec3 right, Vec3 up, double r, double t, float fade, int c, Mark m) {
		int count = (int) Math.round(ringArcs.get());
		double gap = ringGap.get() / 100.0;
		double width = ringWidth.get() / 100.0 * (m.kill ? 1.25 : 1);

		double close = 1 - Math.pow(1 - Math.min(1, t / 0.22), 3);
		double radius = r * (1.45 - 0.45 * close + 0.12 * Math.max(0, t - 0.6) / 0.4);
		double turn = Math.PI / count + (ringLock.get() ? (1 - close) * Math.PI / 2 : 0);
		drawArcs(fx, at, right, up, radius, width, count, gap, turn, c, fade);
		if (m.kill) {
			double out = 1 - Math.pow(1 - Math.min(1, t / 0.5), 2);
			drawArcs(fx, at, right, up, radius * (1.35 + 0.5 * out), width * 0.7, count, gap + 0.1, -turn * 1.5, c,
					fade * (float) (1 - out * 0.7));
			fx.additive(true);
			for (int k = 0; k < count; k++) {
				double a = Math.PI * 2 * k / count + turn - Math.PI / count;
				Vec3 dir = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
				Vec3 p = at.add(dir.scale(radius * (1 + 1.6 * out)));
				fx.streak(p.x, p.y, p.z, dir.x, dir.y, dir.z, r * 0.7 * (1 - out * 0.5), r * 0.16, Fx.withAlpha(c, 255 * fade * 0.9f));
			}
			fx.additive(false);
		}
		fx.additive(!finish.is("Matte"));
		double flash = Math.exp(-t * 9);
		fx.light(at.x, at.y, at.z, radius * 0.9, Fx.withAlpha(c, 255 * fade * (float) (0.08 + 0.3 * flash)), 0);
		fx.light(at.x, at.y, at.z, radius * 0.25, Fx.withAlpha(c, 255 * fade * (float) (0.6 * flash)), 0.8);
		fx.additive(false);
	}
	private void drawArcs(Fx fx, Vec3 at, Vec3 right, Vec3 up, double radius, double width, int count, double gap, double turn,
			int c, float fade) {
		if (fade <= 0.01f) {
			return;
		}
		double span = Math.PI * 2 / count * (1 - gap);
		int steps = 14;
		for (int k = 0; k < count; k++) {
			double mid = turn + Math.PI * 2 * k / count;
			double[] path = new double[(steps + 1) * 3];
			for (int i = 0; i <= steps; i++) {
				double a = mid - span / 2 + span * i / steps;
				Vec3 p = onPlane(at, right, up, Math.cos(a) * radius, Math.sin(a) * radius);
				path[i * 3] = p.x;
				path[i * 3 + 1] = p.y;
				path[i * 3 + 2] = p.z;
			}
			Vec3 a0 = new Vec3(path[0], path[1], path[2]);
			Vec3 a1 = new Vec3(path[steps * 3], path[steps * 3 + 1], path[steps * 3 + 2]);
			if (!flat()) {
				Meshes mesh = fx.mesh();
				meshFinish(mesh);
				double[] rad = new double[steps + 1];
				int[] cols = new int[steps + 1];
				int col = Fx.withAlpha(finish.is("Glass") ? Fx.lerp(c, 0xFFFFFFFF, 0.3) : c, 255 * fade * (finish.is("Glass") ? 0.8f : 1f));
				for (int i = 0; i <= steps; i++) {
					double e = Math.min(i, steps - i) / 2.0;
					rad[i] = width / 2 * Math.sqrt(Math.min(1, 0.15 + e));
					cols[i] = col;
				}
				mesh.tube(path, steps + 1, rad, cols, 12);
				fx.additive(true);
				fx.ribbon(path, steps + 1, width * 5, width * 5, Fx.withAlpha(c, 255 * fade * 0.18f), Fx.withAlpha(c, 255 * fade * 0.18f), 0);
				fx.additive(false);
				continue;
			}
			switch (finish.get()) {
				case "Matte" -> {
					fx.additive(false);
					int col = Fx.withAlpha(c, 255 * fade * 0.95f);
					fx.ribbon(path, steps + 1, width * 1.4, width * 1.4, col, col, 0);
					fx.ribbon(path, steps + 1, width, width, col, col, 0);
					cap(fx, a0, a1, width * 0.6, col, 0);
				}
				case "Frosted" -> {
					fx.additive(false);
					int milk = Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.55), 255 * fade * 0.85f);
					fx.ribbon(path, steps + 1, width * 1.5, width * 1.5, milk, milk, 0.3);
					fx.ribbon(path, steps + 1, width * 4, width * 4, Fx.fade(milk, 0.3), Fx.fade(milk, 0.3), 0);
					cap(fx, a0, a1, width * 0.7, milk, 0.3);
				}
				default -> {
					int body = Fx.withAlpha(c, 255 * fade * 0.85f);
					fx.additive(false);
					fx.ribbon(path, steps + 1, width * 1.6, width * 1.6, body, body, 0);
					fx.additive(true);
					int hot = Fx.withAlpha(c, 255 * fade);
					fx.ribbon(path, steps + 1, width * 1.3, width * 1.3, hot, hot, 0.85);
					fx.ribbon(path, steps + 1, width * 6, width * 6, Fx.withAlpha(c, 255 * fade * 0.3f), Fx.withAlpha(c, 255 * fade * 0.3f), 0);
					cap(fx, a0, a1, width * 0.8, hot, 0.6);
					fx.additive(false);
				}
			}
		}
	}
	private static void cap(Fx fx, Vec3 a, Vec3 b, double r, int c, double core) {
		fx.light(a.x, a.y, a.z, r, c, core);
		fx.light(b.x, b.y, b.z, r, c, core);
	}
	private void box(Fx fx, Vec3 at, Vec3 right, Vec3 up, double r, double t, float fade, int c, boolean kill) {
		double half = r * pop(t);
		double width = border.get() / 100.0;
		double round = roundness.get() / 100.0 * half;
		frame(fx, at, right, up, half, round, width, c, fade);
		if (kill) {
			double grow = half * (1.25 + 0.6 * Math.min(1, t * 1.6));
			frame(fx, at, right, up, grow, roundness.get() / 100.0 * grow, width * 0.7, c, fade * (float) (1 - t));
		}
		if (flat()) {
			fx.additive(false);
			int pane = finish.is("Frosted") ? Fx.lerp(c, 0xFFFFFFFF, 0.6) : c;
			float paneAlpha = finish.is("Frosted") ? 0.22f : finish.is("Matte") ? 0.12f : 0.07f;
			fill(fx, at, right, up, contour(half - width / 2, Math.max(0, round - width / 2)), Fx.withAlpha(pane, 255 * paneAlpha * fade));
		}
	}
	private void frame(Fx fx, Vec3 at, Vec3 right, Vec3 up, double half, double round, double width, int c, float fade) {
		if (fade <= 0.01f) {
			return;
		}
		double[] outer = contour(half + width / 2, round + width / 2);
		double[] inner = contour(Math.max(0.001, half - width / 2), Math.max(0, round - width / 2));
		if (!flat()) {
			Meshes mesh = fx.mesh();
			meshFinish(mesh);
			double[] mid = contour(half, round);
			int n = mid.length / 2;
			double[] path = new double[(n + 1) * 3];
			double[] radius = new double[n + 1];
			int[] colours = new int[n + 1];
			int col = Fx.withAlpha(finish.is("Glass") ? Fx.lerp(c, 0xFFFFFFFF, 0.3) : c, 255 * fade * (finish.is("Glass") ? 0.75f : 1f));
			for (int i = 0; i <= n; i++) {
				int k = i % n;
				Vec3 p = at.add(right.scale(mid[k * 2])).add(up.scale(mid[k * 2 + 1]));
				path[i * 3] = p.x;
				path[i * 3 + 1] = p.y;
				path[i * 3 + 2] = p.z;
				radius[i] = width / 2;
				colours[i] = col;
			}
			mesh.tube(path, n + 1, radius, colours, 8);
			fx.additive(true);
			ring(fx, at, right, up, outer, contour(half + width * 3, round + width * 3), Fx.withAlpha(c, 255 * fade * 0.25f), c & 0xFFFFFF);
			fx.additive(false);
			return;
		}
		switch (finish.get()) {
			case "Matte" -> {
				fx.additive(false);
				ring(fx, at, right, up, inner, outer, Fx.withAlpha(c, 255 * fade * 0.95f), Fx.withAlpha(c, 255 * fade * 0.95f));
			}
			case "Frosted" -> {
				fx.additive(false);
				int milk = Fx.lerp(c, 0xFFFFFFFF, 0.55);
				ring(fx, at, right, up, inner, outer, Fx.withAlpha(milk, 255 * fade * 0.85f), Fx.withAlpha(milk, 255 * fade * 0.85f));
				ring(fx, at, right, up, outer, contour(half + width * 2.5, round + width * 2.5), Fx.withAlpha(milk, 255 * fade * 0.3f), milk & 0xFFFFFF);
			}
			default -> {
				fx.additive(true);
				double[] coreIn = contour(Math.max(0.001, half - width * 0.2), Math.max(0, round - width * 0.2));
				double[] coreOut = contour(half + width * 0.2, round + width * 0.2);
				ring(fx, at, right, up, inner, outer, Fx.withAlpha(c, 255 * fade), Fx.withAlpha(c, 255 * fade));
				ring(fx, at, right, up, coreIn, coreOut, Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.75), 255 * fade),
						Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.75), 255 * fade));
				ring(fx, at, right, up, outer, contour(half + width * 4, round + width * 4), Fx.withAlpha(c, 255 * fade * 0.45f), c & 0xFFFFFF);
				ring(fx, at, right, up, contour(Math.max(0.001, half - width * 3.5), Math.max(0, round - width * 3.5)), inner,
						c & 0xFFFFFF, Fx.withAlpha(c, 255 * fade * 0.35f));
				fx.additive(false);
			}
		}
	}
	private static double[] contour(double half, double round) {
		int perCorner = 7;
		double rc = Math.min(round, half);
		double[] out = new double[4 * perCorner * 2];
		int i = 0;
		double[][] corners = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
		for (int k = 0; k < 4; k++) {
			double cx = corners[k][0] * (half - rc);
			double cy = corners[k][1] * (half - rc);
			double start = k * Math.PI / 2;
			for (int j = 0; j < perCorner; j++) {
				double a = start + Math.PI / 2 * j / (perCorner - 1);
				out[i++] = cx + Math.cos(a) * rc;
				out[i++] = cy + Math.sin(a) * rc;
			}
		}
		return out;
	}
	private static void ring(Fx fx, Vec3 at, Vec3 right, Vec3 up, double[] a, double[] b, int ca, int cb) {
		int n = a.length / 2;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			Vec3 a0 = at.add(right.scale(a[i * 2])).add(up.scale(a[i * 2 + 1]));
			Vec3 a1 = at.add(right.scale(a[j * 2])).add(up.scale(a[j * 2 + 1]));
			Vec3 b1 = at.add(right.scale(b[j * 2])).add(up.scale(b[j * 2 + 1]));
			Vec3 b0 = at.add(right.scale(b[i * 2])).add(up.scale(b[i * 2 + 1]));
			fx.quad(a0.x, a0.y, a0.z, ca, a1.x, a1.y, a1.z, ca, b1.x, b1.y, b1.z, cb, b0.x, b0.y, b0.z, cb);
		}
	}
	private static void fill(Fx fx, Vec3 at, Vec3 right, Vec3 up, double[] a, int argb) {
		int n = a.length / 2;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			Vec3 p0 = at.add(right.scale(a[i * 2])).add(up.scale(a[i * 2 + 1]));
			Vec3 p1 = at.add(right.scale(a[j * 2])).add(up.scale(a[j * 2 + 1]));
			fx.quad(at.x, at.y, at.z, argb, p0.x, p0.y, p0.z, argb, p1.x, p1.y, p1.z, argb, at.x, at.y, at.z, argb);
		}
	}
	private void vortex(Fx fx, Vec3 at, Vec3 right, Vec3 up, double r, double t, float fade, int c, Mark m) {
		int count = (int) Math.round(arms.get());
		double turn = (m.seed * 6.283) + (t * 3.2 + t * t * 4) * spin.get() / 100.0 * (m.kill ? 1.4 : 1);
		double reach = r * (1.25 - 0.55 * t) * pop(t);
		int steps = 22;
		int accent = Fx.lerp(c, 0xFFFFFFFF, 0.35);
		for (int k = 0; k < count; k++) {
			double base = turn + k * Math.PI * 2 / count;
			double[] path = new double[(steps + 1) * 3];
			double[] radius = new double[steps + 1];
			int[] colours = new int[steps + 1];
			double width = r * 0.18;
			for (int i = 0; i <= steps; i++) {
				double f = 1 - (double) i / steps;
				double rad = reach * (0.1 + 0.9 * f);
				double ang = base - f * (2.4 + t * 1.5);
				Vec3 p = onPlane(at, right, up, Math.cos(ang) * rad, Math.sin(ang) * rad);
				path[i * 3] = p.x;
				path[i * 3 + 1] = p.y;
				path[i * 3 + 2] = p.z;
				double body = Math.sin(Math.min(1, (1 - f) * 1.4) * Math.PI * 0.5) * (0.35 + 0.65 * f);
				radius[i] = Math.max(0.002, width * body);
				colours[i] = Fx.withAlpha(Fx.lerp(c, accent, 1 - f), 255 * fade * (float) (0.35 + 0.65 * (1 - f * 0.7)));
			}
			if (!flat()) {
				Meshes mesh = fx.mesh();
				meshFinish(mesh);
				double[] half = new double[steps + 1];
				for (int i = 0; i <= steps; i++) {
					half[i] = radius[i] * 0.5;
				}
				mesh.tube(path, steps + 1, half, colours, 12);
				fx.additive(true);
				fx.ribbon(path, steps + 1, scale(radius, 3), fadeAll(colours, 0.2), 0);
				fx.additive(false);
				continue;
			}
			switch (finish.get()) {
				case "Matte" -> {
					fx.additive(false);
					fx.ribbon(path, steps + 1, radius, colours, 0);
				}
				case "Frosted" -> {
					fx.additive(false);
					int[] milk = new int[colours.length];
					for (int i = 0; i < milk.length; i++) {
						milk[i] = Fx.withAlpha(Fx.lerp(colours[i], 0xFFFFFFFF, 0.55), Fx.alpha(colours[i]));
					}
					fx.ribbon(path, steps + 1, radius, milk, 0.25);
					fx.ribbon(path, steps + 1, scale(radius, 2.5), fadeAll(milk, 0.25), 0);
				}
				default -> {
					fx.additive(false);
					fx.ribbon(path, steps + 1, scale(radius, 0.9), fadeAll(colours, 0.6), 0);
					fx.additive(true);
					fx.ribbon(path, steps + 1, radius, colours, 0.8);
					fx.ribbon(path, steps + 1, scale(radius, 3.5), fadeAll(colours, 0.22), 0);
				}
			}
		}
		fx.additive(!finish.is("Matte"));
		int sparkCount = (int) Math.round(sparks.get() * (m.kill ? 1.5 : 1));
		for (int i = 0; i < sparkCount; i++) {
			double s = hash(m.seed, i);
			double start = s * 0.45;
			double u = (t - start) / 0.45;
			if (u <= 0 || u >= 1) {
				continue;
			}
			double f = 1 - u * u;
			double ang = turn + hash(m.seed, i + 40) * Math.PI * 2 - f * 2.8;
			double rad = reach * (0.08 + 1.05 * f);
			Vec3 p = onPlane(at, right, up, Math.cos(ang) * rad, Math.sin(ang) * rad);
			float a = fade * (float) Math.sin(u * Math.PI);
			fx.light(p.x, p.y, p.z, r * 0.12, Fx.withAlpha(accent, 255 * a), 0.9);
		}
		double ring = reach * (0.85 - 0.5 * Math.min(1, t * 1.3));
		fx.softRing(at.x, at.y, at.z, ring, Fx.withAlpha(c, 255 * fade * 0.55f), 0.4);
		fx.light(at.x, at.y, at.z, r * 0.55, Fx.withAlpha(c, 255 * fade * 0.5f), 0);
		fx.light(at.x, at.y, at.z, r * 0.2, Fx.withAlpha(accent, 255 * fade), 1);
		double flare = r * (0.9 + 0.3 * Math.sin(t * 20));
		for (int k = 0; k < 2; k++) {
			double a = turn * 0.3 + k * Math.PI / 2;
			Vec3 d = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
			fx.streak(at.x, at.y, at.z, d.x, d.y, d.z, flare * 2, r * 0.12, Fx.withAlpha(accent, 255 * fade * 0.7f));
		}
		fx.additive(false);
	}
	private static double[] scale(double[] values, double k) {
		double[] out = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = values[i] * k;
		}
		return out;
	}
	private static int[] fadeAll(int[] colours, double k) {
		int[] out = new int[colours.length];
		for (int i = 0; i < colours.length; i++) {
			out[i] = (Math.round(Fx.alpha(colours[i]) * (float) k) << 24) | (colours[i] & 0xFFFFFF);
		}
		return out;
	}
	private static double hash(double seed, int i) {
		double v = Math.sin(seed * 91.17 + i * 12.9898) * 43758.5453;
		return v - Math.floor(v);
	}
	private void glow(Fx fx, Vec3 at, Vec3 right, Vec3 up, double r, double t, float fade, int c, Mark m) {
		double swell = r * (0.7 + 0.5 * Math.sin(Math.min(1, t * 2.2) * Math.PI / 2)) * pop(t);
		int accent = Fx.lerp(c, 0xFFFFFFFF, 0.4);
		double flash = Math.exp(-t * 7);
		boolean matte = finish.is("Matte");
		boolean frosted = finish.is("Frosted");
		if (finish.is("Glass") || finish.is("Metal")) {
			Meshes mesh = fx.mesh();
			meshFinish(mesh);
			mesh.sphere(at.x, at.y, at.z, swell * 0.32, Fx.withAlpha(finish.is("Glass") ? Fx.lerp(c, 0xFFFFFFFF, 0.3) : c,
					255 * fade * (finish.is("Glass") ? 0.7f : 1f)), 14);
		}
		int tone = frosted ? Fx.lerp(c, 0xFFFFFFFF, 0.55) : c;
		fx.additive(!matte && !frosted);
		fx.light(at.x, at.y, at.z, swell * 1.5, Fx.withAlpha(tone, 255 * fade * 0.3f), 0);
		fx.light(at.x, at.y, at.z, swell * 0.75, Fx.withAlpha(tone, 255 * fade * (matte ? 0.75f : 0.7f)), matte ? 0 : 0.3);
		fx.light(at.x, at.y, at.z, swell * (0.25 + 0.35 * flash), Fx.withAlpha(accent, 255 * fade), matte ? 0.2 : 1);

		int rays = m.kill ? 10 : 7;
		double length = r * (0.6 + 1.6 * Math.sin(Math.min(1, t * 3) * Math.PI / 2)) * (1 - 0.5 * t);
		for (int k = 0; k < rays; k++) {
			double a = m.seed * 6.283 + k * Math.PI * 2 / rays + (hash(m.seed, k) - 0.5) * 0.4;
			double len = length * (0.6 + 0.6 * hash(m.seed, k + 20));
			Vec3 d = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
			Vec3 mid = at.add(d.scale(len * 0.5 + swell * 0.2));
			fx.streak(mid.x, mid.y, mid.z, d.x, d.y, d.z, len, r * 0.1, Fx.withAlpha(accent, 255 * fade * 0.55f));
		}
		double wave = Math.min(1, t * 2.2);
		fx.softRing(at.x, at.y, at.z, r * (0.5 + 2.2 * wave), Fx.withAlpha(tone, 255 * fade * (float) (0.7 * (1 - wave))), 0.5);
		if (m.kill) {
			double wave2 = Math.min(1, Math.max(0, t - 0.12) * 1.8);
			fx.softRing(at.x, at.y, at.z, r * (0.5 + 3.2 * wave2), Fx.withAlpha(tone, 255 * fade * (float) (0.5 * (1 - wave2))), 0.3);
		}
		double glint = Math.sin(Math.min(1, t * 4) * Math.PI);
		if (glint > 0.01) {
			fx.sparkle(at.x, at.y, at.z, r * 1.1 * glint, Fx.withAlpha(0xFFFFFFFF, 255 * fade * (float) glint * 0.8f), m.seed * 3 + t);
		}
		int motes = (int) Math.round(sparks.get() * (m.kill ? 1.5 : 1));
		for (int i = 0; i < motes; i++) {
			double a = hash(m.seed, i + 60) * Math.PI * 2;
			double speed = 0.5 + hash(m.seed, i + 80);
			double out = (1 - Math.exp(-t * 4)) * r * 1.4 * speed;
			double rise = t * t * r * 1.5;
			Vec3 p = onPlane(at, right, up, Math.cos(a) * out, Math.sin(a) * out + rise);
			float a2 = fade * (float) (1 - t);
			fx.light(p.x, p.y, p.z, r * 0.09 * (0.6 + speed * 0.4), Fx.withAlpha(accent, 255 * a2), 0.8);
		}
		fx.additive(false);
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, nz.temple.client.render.Projection projection) {
		if (!isEnabled() || Minecraft.getInstance().player == null) {
			marks.clear();
			return;
		}
		if (onPlayer()) {
			return;
		}
		long now = System.nanoTime();
		double life = duration.get() * 1e9;
		marks.removeIf(m -> now - m.born > life);
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight() / 2f;
		float uiScale = UiScale.factor();
		for (Mark m : marks) {
			double t = (now - m.born) / life;
			float a = (float) Math.min(1, (1 - t) * 2.2);
			double punch = t < 0.2 ? 1.5 - 0.5 * (t / 0.2) : 1 - (t - 0.2) / 0.8 * 0.25;
			float r = (float) (size.get() * punch * (m.kill ? 1.45 : 1)) * uiScale;
			int base = baseColor(m.kill, 0);
			int color = (Math.round(a * 255) << 24) | (base & 0xFFFFFF);
			float thick = thickness.getFloat() * (m.kill ? 1.35f : 1) * uiScale;
			switch (style.get()) {
				case "Flakes" -> flakes(g, m, cx, cy, t, color, thick, uiScale);
				case "Dots" -> {
					float d = thick * 1.2f;
					Draw2D.pixelRect(g, cx - r - d, cy - d, cx - r + d, cy + d, color);
					Draw2D.pixelRect(g, cx + r - d, cy - d, cx + r + d, cy + d, color);
					Draw2D.pixelRect(g, cx - d, cy - r - d, cx + d, cy - r + d, color);
					Draw2D.pixelRect(g, cx - d, cy + r - d, cx + d, cy + r + d, color);
				}
				case "Corners" -> {
					float len = r * 0.8f;
					Draw2D.pixelRect(g, cx - r, cy - r, cx - r + len, cy - r + thick, color);
					Draw2D.pixelRect(g, cx - r, cy - r, cx - r + thick, cy - r + len, color);
					Draw2D.pixelRect(g, cx + r - len, cy - r, cx + r, cy - r + thick, color);
					Draw2D.pixelRect(g, cx + r - thick, cy - r, cx + r, cy - r + len, color);
					Draw2D.pixelRect(g, cx - r, cy + r - thick, cx - r + len, cy + r, color);
					Draw2D.pixelRect(g, cx - r, cy + r - len, cx - r + thick, cy + r, color);
					Draw2D.pixelRect(g, cx + r - len, cy + r - thick, cx + r, cy + r, color);
					Draw2D.pixelRect(g, cx + r - thick, cy + r - len, cx + r, cy + r, color);
				}
				default -> {
					float inner = r * 0.45f;
					Draw2D.line(g, cx - r, cy - r, cx - inner, cy - inner, thick, color);
					Draw2D.line(g, cx + r, cy - r, cx + inner, cy - inner, thick, color);
					Draw2D.line(g, cx - r, cy + r, cx - inner, cy + inner, thick, color);
					Draw2D.line(g, cx + r, cy + r, cx + inner, cy + inner, thick, color);
				}
			}
		}
	}
	private void flakes(GuiGraphicsExtractor g, Mark mark, float cx, float cy, double t, int color, float thickness, float uiScale) {
		double travel = 1 - Math.exp(-5 * t);
		float alpha = (float) ((1 - t) * (1 - t));
		int flakeColor = Math.round(alpha * 255) << 24 | color & 0xFFFFFF;
		for (Flake flake : mark.flakes) {
			double distance = spread.get() * flake.speed() * travel * (mark.kill ? 1.25 : 1) * uiScale;
			float x = (float) (cx + Math.cos(flake.angle()) * distance);
			float y = (float) (cy + Math.sin(flake.angle()) * distance + t * t * 8 * uiScale);
			double rotation = flake.angle() + flake.spin() * t;
			float arm = (float) (size.get() * 0.32 * flake.scale() * (1 - t * 0.55)) * uiScale;
			for (int branch = 0; branch < 3; branch++) {
				double angle = rotation + branch * Math.PI / 3;
				float dx = (float) Math.cos(angle) * arm;
				float dy = (float) Math.sin(angle) * arm;
				Draw2D.line(g, x - dx, y - dy, x + dx, y + dy, Math.max(0.7f, thickness * 0.65f), flakeColor);
			}
		}
	}
	public void previewOn(Entity entity) {
		if (entity != null) {
			addMark(false, entity, new Vec3(0, entity.getBbHeight() * 0.6, 0));
		}
	}
	@Override
	protected void onDisable() {
		marks.clear();
	}
}
