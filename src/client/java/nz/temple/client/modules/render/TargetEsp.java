package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.client.util.CombatTracker;
import nz.temple.client.util.Entities;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TargetEsp extends Module implements nz.temple.client.render.FxSource {
	private final ModeSetting style;
	private final BoolSetting auraTargets;
	private final NumberSetting lastHit;
	private final ModeSetting colors;
	private final ColorSetting customA;
	private final ColorSetting customB;
	private final NumberSetting speed;
	private final NumberSetting size;
	private final NumberSetting brightness;
	private final NumberSetting ghosts;
	private final NumberSetting trail;
	private final BoolSetting throughWalls;
	private final Map<LivingEntity, float[]> fades = new IdentityHashMap<>();
	private long lastNanos = System.nanoTime();

	public TargetEsp() {
		super("Target ESP", Category.RENDER, "Marks your current target with a moving ring, orbiting trails or a halo.");
		style = add(new ModeSetting("Style", List.of("Souls", "Crystals", "Circle", "Jello", "Ring", "Ghosts", "Halo"), "Souls")
				.describe("Souls: streams of light circling the target. Crystals: a ring of glowing crystals pointing at it. Circle: a "
						+ "dashed ring of light bobbing up and down. Jello: a glowing curtain of light around it. Ring slides a band up "
						+ "and down. Ghosts orbits orbs with light trails. Halo shows a ground ring, head halo and rising sparks."));
		auraTargets = add(new BoolSetting("Kill Aura targets", true)
				.describe("Marks everything Kill Aura is attacking."));
		lastHit = add(new NumberSetting("Manual hits", 3, 0, 10, 0.5, " s")
				.describe("Also marks the entity you last hit yourself, for this long. 0 disables it."));
		group();
		colors = add(new ModeSetting("Color", List.of("Theme", "Custom", "Health"), "Theme")
				.describe("Theme uses the accent colours from Client > HUD. Custom uses the two colours below. Health goes from green to "
						+ "red as the target loses health."));
		customA = add(new ColorSetting("Color A", 0xFF5AC8FA).themed().describe("First colour of the custom gradient.")
				.visibleWhen(() -> colors.is("Custom")));
		customB = add(new ColorSetting("Color B", 0xFFAF52DE).themedSecondary().describe("Second colour of the custom gradient.")
				.visibleWhen(() -> colors.is("Custom")));
		brightness = add(new NumberSetting("Brightness", 85, 10, 100, 5, "%").describe("How bright the glow is."));
		speed = add(new NumberSetting("Speed", 1, 0.2, 3, 0.1, "x").describe("Animation speed."));
		size = add(new NumberSetting("Size", 1, 0.5, 2, 0.05, "x").describe("Effect size relative to the target's hitbox."));
		ghosts = add(new NumberSetting("Ghosts", 3, 1, 6, 1, "").describe("Number of orbs.")
				.visibleWhen(() -> style.is("Ghosts")));
		trail = add(new NumberSetting("Trail length", 18, 4, 40, 1, "").describe("Length of each orb's trail.")
				.visibleWhen(() -> style.is("Ghosts")));
		throughWalls = add(new BoolSetting("Through walls", false)
				.describe("Shows the effect through blocks."));
	}

	public void emit(Fx fx, float partial) {
		long now = System.nanoTime();
		float dt = (float) Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			fades.clear();
			return;
		}
		Set<LivingEntity> wanted = wanted();
		for (LivingEntity e : wanted) {
			fades.computeIfAbsent(e, k -> new float[1]);
		}
		fx.throughWalls(throughWalls.get());
		for (Iterator<Map.Entry<LivingEntity, float[]>> it = fades.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, float[]> entry = it.next();
			LivingEntity e = entry.getKey();
			boolean on = wanted.contains(e) && e.isAlive() && e.level() == mc.level;
			float[] f = entry.getValue();
			f[0] = on ? Math.min(1, f[0] + dt * 4) : Math.max(0, f[0] - dt * 3);
			if (f[0] <= 0 && !on) {
				it.remove();
				continue;
			}
			float appear = 1 - (1 - f[0]) * (1 - f[0]);
			boolean light = style.is("Souls") || style.is("Circle") || style.is("Jello") || style.is("Crystals");
			fx.additive(light);

			switch (style.get()) {
				case "Souls" -> souls(fx, e, partial, appear);
				case "Crystals" -> crystals(fx, e, partial, appear);
				case "Circle" -> circle(fx, e, partial, appear);
				case "Jello" -> jello(fx, e, partial, appear);
				case "Ring" -> ring(fx, e, partial, appear);
				case "Halo" -> halo(fx, e, partial, appear);
				default -> ghosts(fx, e, partial, appear);
			}
		}
	}

	/* ---- Souls, Crystals, Circle and Jello: light built from soft glowing points (after x.monolith's target ESP). ---- */

	private double spin() {
		return System.nanoTime() / 1e9 * 160 * speed.getFloat();
	}

	private int glowColor(LivingEntity e, double offset, float alpha) {
		return Fx.withAlpha(color(e, offset), 255f * Math.max(0, Math.min(1, alpha * brightness.getFloat() / 100f)));
	}

	private void souls(Fx fx, LivingEntity e, float partial, float visible) {
		var p = e.getPosition(partial);
		double width = e.getBbWidth() * 1.5 * size.get();
		double height = e.getBbHeight();
		double angle = spin();
		double radius = Math.max(0.5, 1.2 - 0.5 * visible);
		int step = 0;
		int skip = 0;
		for (int i = 0; i < 360; i += 2) {
			if (skip > 0) {
				skip -= 2;
				continue;
			}
			step += 2;
			if (step > 50) {
				skip = 100;
				step = 0;
				continue;
			}
			double small = (0.23 + 0.005 * step) * size.get();
			double large = (0.7 + 0.005 * step) * size.get();
			double theta = Math.toRadians(i + angle);
			double x = p.x + Math.sin(theta) * width * radius;
			double z = p.z + Math.cos(theta) * width * radius;
			double y = p.y + height / 1.5 + height / 3.0 * Math.sin(Math.toRadians(i / 2.0 + angle / 5.0));
			fx.glow(x, y, z, large, glowColor(e, i / 360.0, visible * 0.05f));
			fx.glow(x, y, z, small * 0.7, glowColor(e, i / 360.0, visible * 0.6f));
			fx.glow(x, y, z, small * 0.22, glowColor(e, i / 360.0, visible * 0.9f) | 0xFFFFFF);
		}
	}

	private static final double[][] CRYSTAL = {{0, 1.5, 0}, {0, -1.5, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
	private static final int[][] FACES = {{0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2}, {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}};
	private static final float[] SHADE = {1.0f, 0.8f, 0.6f, 0.9f, 0.7f, 0.5f, 0.4f, 0.6f};

	private void crystals(Fx fx, LivingEntity e, float partial, float visible) {
		var p = e.getPosition(partial);
		double width = e.getBbWidth() * 1.5 * size.get();
		double height = e.getBbHeight();
		double spread = 1.2 - 0.5 * visible;
		double angle = spin();
		double cx = p.x;
		double cy = p.y + height / 2;
		double cz = p.z;
		for (int i = 0; i < 360; i += 20) {
			double theta = Math.toRadians(i + angle * 0.3);
			double ax = p.x + Math.sin(theta) * width * spread;
			double az = p.z + Math.cos(theta) * width * spread;
			double ay = p.y + 0.1 + height * Math.abs(Math.sin(i));
			// The crystal's long axis points at the target's middle.
			double tx = cx - ax;
			double ty = cy - (ay + 0.9);
			double tz = cz - az;
			double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
			tx /= tl;
			ty /= tl;
			tz /= tl;
			// Two axes across it.
			double ux = -tz;
			double uz = tx;
			double ul = Math.sqrt(ux * ux + uz * uz);
			if (ul < 1e-4) {
				ux = 1;
				uz = 0;
				ul = 1;
			}
			ux /= ul;
			uz /= ul;
			double vx = ty * uz;
			double vy = tz * ux - tx * uz;
			double vz = -ty * ux;
			double s = 0.1 * size.get();
			double[][] pts = new double[6][];
			for (int v = 0; v < 6; v++) {
				double[] c = CRYSTAL[v];
				pts[v] = new double[] {ax + (ux * c[0] + tx * c[1] + vx * c[2]) * s, ay + (ty * c[1] + vy * c[2]) * s,
						az + (uz * c[0] + tz * c[1] + vz * c[2]) * s};
			}
			int base = color(e, i / 360.0);
			for (int f = 0; f < FACES.length; f++) {
				int c = Fx.withAlpha(shade(base, SHADE[f]), 255f * visible * brightness.getFloat() / 100f);
				double[] a = pts[FACES[f][0]];
				double[] b = pts[FACES[f][1]];
				double[] d = pts[FACES[f][2]];
				fx.quad(a[0], a[1], a[2], c, b[0], b[1], b[2], c, d[0], d[1], d[2], c, a[0], a[1], a[2], c);
			}
			fx.glow(ax, ay, az, 0.5 * size.get(), glowColor(e, i / 360.0, visible * 0.25f));
		}
	}

	private static int shade(int color, float factor) {
		int r = Math.min(255, Math.round(((color >> 16) & 255) * factor));
		int g = Math.min(255, Math.round(((color >> 8) & 255) * factor));
		int b = Math.min(255, Math.round((color & 255) * factor));
		return (color & 0xFF000000) | r << 16 | g << 8 | b;
	}

	private void circle(Fx fx, LivingEntity e, float partial, float visible) {
		var p = e.getPosition(partial);
		double width = e.getBbWidth() * 1.5 * size.get();
		double height = e.getBbHeight();
		double angle = spin();
		double flash = e.hurtTime > 0 ? 1 : 0;
		double y = p.y + height / 1.75 + height / 2.0 * Math.sin(Math.toRadians(angle / 2.0));
		double radius = Math.max(0.5, 0.7 - 0.2 * flash + 0.2 - 0.2 * visible);
		for (int i = 0; i < 360; i += 2) {
			if (i / 45 % 2 == 0) {
				continue;
			}
			double theta = Math.toRadians(i + angle);
			double x = p.x + Math.sin(theta) * width * radius;
			double z = p.z + Math.cos(theta) * width * radius;
			fx.glow(x, y, z, 0.4 * size.get(), glowColor(e, i / 360.0, visible * 0.05f));
			fx.glow(x, y, z, 0.15 * size.get(), glowColor(e, i / 360.0, visible * 0.8f));
		}
	}

	private void jello(Fx fx, LivingEntity e, float partial, float visible) {
		var p = e.getPosition(partial);
		double width = e.getBbWidth() * 1.45 * size.get();
		double height = e.getBbHeight();
		double angle = spin();
		double radius = Math.max(0.5, 0.9 - 0.2 * visible);
		for (int i = 0; i < 360; i += 3) {
			double theta = Math.toRadians(i + angle);
			double x = p.x + Math.sin(theta) * width * radius;
			double z = p.z + Math.cos(theta) * width * radius;
			for (int layer = 0; layer < 12; layer++) {
				double y = p.y + height / 1.75 + height / 2.0 * Math.sin(Math.toRadians(angle / 1.5 + layer * 2.5));
				fx.glow(x, y, z, 0.1 * size.get(), glowColor(e, i / 360.0, visible * (layer / 12f) * 0.08f));
			}
			double top = p.y + height / 1.75 + height / 2.0 * Math.sin(Math.toRadians(angle / 1.5 + 30));
			fx.glow(x, top, z, 0.1 * size.get(), glowColor(e, i / 360.0, visible * 0.35f));
		}
	}

	private Set<LivingEntity> wanted() {
		Set<LivingEntity> set = new LinkedHashSet<>();
		LivingEntity auraTarget = nz.temple.client.util.CombatTarget.current();
		if (auraTargets.get() && auraTarget != null) {
			set.add(auraTarget);
		}
		if (lastHit.get() > 0) {
			LivingEntity recent = CombatTracker.recent((long) (lastHit.get() * 1000));
			if (recent != null) {
				set.add(recent);
			}
		}
		return set;
	}

	private int color(LivingEntity e, double offset) {
		return switch (colors.get()) {
			case "Custom" -> Theme.flow(customA.get(), customB.get(), offset);
			case "Health" -> Fx.lerp(0xFFFF453A, 0xFF34C759, Entities.healthFraction(e));
			default -> Theme.color(offset);
		};
	}

	private float intensity(float appear) {
		return 255f * brightness.getFloat() / 100f * appear;
	}

	private void ring(Fx fx, LivingEntity e, float partial, float appear) {
		Vec3 p = e.getPosition(partial);
		double h = e.getBbHeight();
		double r = e.getBbWidth() * 0.78 * size.get();
		double t = Theme.time() * speed.get() * 2.1;
		double wave = (Math.sin(t) + 1) / 2;
		double y = p.y + h * wave * wave * (3 - 2 * wave);
		double tail = h * 0.3 * (0.35 + 0.65 * Math.abs(Math.cos(t)));
		boolean rising = Math.cos(t) > 0;
		double y0 = rising ? y - tail : y;
		double y1 = rising ? y : y + tail;
		float a = intensity(appear);
		int segments = 56;
		int[] ring = new int[segments + 1];
		for (int i = 0; i <= segments; i++) {
			ring[i] = color(e, (double) i / segments);
		}
		fx.wall(p.x, y0, y1, p.z, r, 0, Math.PI * 2, segments,
				i -> Fx.withAlpha(ring[i], rising ? 0 : a * 0.55f),
				i -> Fx.withAlpha(ring[i], rising ? a * 0.55f : 0));
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			ringSegment(fx, p.x, y, p.z, r - 0.02, r + 0.02, a0, a1, Fx.withAlpha(ring[i], a), Fx.withAlpha(ring[i + 1], a),
					Fx.withAlpha(ring[i], a), Fx.withAlpha(ring[i + 1], a));
			ringSegment(fx, p.x, y, p.z, r + 0.02, r + 0.2 * size.get(), a0, a1, Fx.withAlpha(ring[i], a * 0.4f),
					Fx.withAlpha(ring[i + 1], a * 0.4f), ring[i] & 0xFFFFFF, ring[i + 1] & 0xFFFFFF);
		}
	}

	private static void ringSegment(Fx fx, double x, double y, double z, double r0, double r1, double a0, double a1,
			int inner0, int inner1, int outer0, int outer1) {
		double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
		fx.quad(x + c0 * r0, y, z + s0 * r0, inner0, x + c1 * r0, y, z + s1 * r0, inner1,
				x + c1 * r1, y, z + s1 * r1, outer1, x + c0 * r1, y, z + s0 * r1, outer0);
	}

	private void ghosts(Fx fx, LivingEntity e, float partial, float appear) {
		Vec3 p = e.getPosition(partial);
		double h = e.getBbHeight();
		double r = e.getBbWidth() * 0.95 * size.get();
		double t = Theme.time() * speed.get() * 1.6;
		int count = (int) Math.round(ghosts.get());
		int length = (int) Math.round(trail.get());
		float a = intensity(appear);
		for (int g = 0; g < count; g++) {
			double phase = Math.PI * 2 * g / count;
			for (int k = length - 1; k >= 0; k--) {
				double tt = t - k * 0.035;
				double angle = tt * 2.3 + phase;
				double y = p.y + h * 0.55 + Math.sin(tt * 1.9 + phase * 1.7) * h * 0.36;
				double x = p.x + Math.cos(angle) * r;
				double z = p.z + Math.sin(angle) * r;
				double fade = 1 - (double) k / length;
				double radius = 0.2 * size.get() * (0.3 + 0.7 * fade);
				fx.orb(x, y, z, radius, Fx.withAlpha(color(e, (double) g / count + k * 0.012), (float) (a * fade * fade)));
			}
		}
	}

	private void halo(Fx fx, LivingEntity e, float partial, float appear) {
		Vec3 p = e.getPosition(partial);
		double h = e.getBbHeight();
		double r = e.getBbWidth() * 0.85 * size.get();
		double t = Theme.time() * speed.get();
		float a = intensity(appear);
		int base = color(e, 0);
		int accent = color(e, 0.5);
		double ground = p.y + 0.03;
		double pulse = 0.94 + 0.06 * Math.sin(t * 2.6);
		fx.dome(p.x, ground, p.z, r * 1.35 * pulse, (h + 0.35) * pulse, 7, 28,
				Fx.withAlpha(base, a * 0.30f), Fx.withAlpha(accent, a * 0.05f));
		fx.ring(p.x, ground, p.z, r - 0.025, r + 0.025, Fx.withAlpha(base, a), Fx.withAlpha(base, a));
		fx.ring(p.x, ground, p.z, r + 0.025, r + 0.35 * size.get(), Fx.withAlpha(base, a * 0.35f), base & 0xFFFFFF, 48);
		fx.ring(p.x, ground, p.z, 0, r - 0.025, base & 0xFFFFFF, Fx.withAlpha(base, a * 0.18f), 48);
		for (int d = 0; d < 3; d++) {
			double from = t * 2.4 + Math.PI * 2 * d / 3;
			int c = Fx.withAlpha(accent, a * 0.8f);
			fx.wall(p.x, ground, ground + 0.22 * size.get(), p.z, r + 0.06, from, 0.9, 10,
					i -> Fx.fade(c, Math.sin(Math.PI * i / 10.0)), i -> c & 0xFFFFFF);
		}
		double headY = p.y + h + 0.16;
		double hr = e.getBbWidth() * 0.5 * size.get();
		fx.ring(p.x, headY, p.z, hr - 0.02, hr + 0.02, Fx.withAlpha(accent, a), Fx.withAlpha(accent, a));
		fx.ring(p.x, headY, p.z, hr + 0.02, hr + 0.16, Fx.withAlpha(accent, a * 0.4f), accent & 0xFFFFFF, 40);
		for (int i = 0; i < 8; i++) {
			double phase = (t * 0.55 + i / 8.0) % 1.0;
			double angle = Math.PI * 2 * i / 8 + t * 0.7;
			double y = p.y + phase * h * 1.15;
			float fade = (float) Math.sin(phase * Math.PI);
			fx.orb(p.x + Math.cos(angle) * r * 0.9, y, p.z + Math.sin(angle) * r * 0.9, 0.12 * size.get(),
					Fx.withAlpha(color(e, i / 8.0), a * fade));
		}
	}

	@Override
	protected void onDisable() {
		fades.clear();
	}
}
