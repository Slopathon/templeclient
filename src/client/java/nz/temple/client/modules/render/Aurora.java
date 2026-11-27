package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.modules.cosmetic.Previewable;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class Aurora extends Module implements Previewable, nz.temple.client.render.FxSource {
	private final BoolSetting veil;
	private final BoolSetting crown;
	private final BoolSetting ribbon;
	private final ModeSetting palette;
	private final ModeSetting who;
	private final BoolSetting react;
	private final BoolSetting realism;
	private double vx;
	private double vz;
	private final NumberSetting height;
	private final NumberSetting speed;
	private final NumberSetting brightness;
	private final BoolSetting firstPerson;
	private final Deque<double[]> trail = new ArrayDeque<>();
	public Aurora() {
		super("Aurora", Category.COSMETICS, "Aurora curtains around you, above your head, and along the path you walk.");
		veil = add(new BoolSetting("Veil", true).describe("Curtains in a ring around the player."));
		crown = add(new BoolSetting("Crown", false).describe("A small curtain circling above the head."));
		ribbon = add(new BoolSetting("Ribbon trail", false).describe("A curtain left in the air along the path you walked."));
		group();
		palette = add(new ModeSetting("Palette", List.of("Natural", "Theme", "Arctic", "Crimson"), "Natural")
				.describe("Theme uses the HUD accent. Natural is green to violet. Arctic is cyan to deep blue. Crimson is pink to red."));
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Self").describe("Who the veil, crown and trail are shown on."));
		group();
		height = add(new NumberSetting("Height", 1, 0.1, 2, 0.05, "x").describe("How tall the curtains are."));
		speed = add(new NumberSetting("Speed", 1, 0.2, 3, 0.1, "x").describe("How fast the curtains move."));
		brightness = add(new NumberSetting("Brightness", 65, 10, 100, 5, "%").describe("Brightness of the curtains."));
		realism = add(new BoolSetting("Realism", false).describe("Adds a fainter curtain inside the veil, folds seen through folds. "
				+ "Off uses the original single curtain.").visibleWhen(veil::get));
		react = add(new BoolSetting("React to movement", false)
				.describe("The veil trails back and brightens as you move, and settles when you stop."));
		firstPerson = add(new BoolSetting("Show in first person", false).describe("Shows your own veil and crown in first person. Off by "
				+ "default because the veil surrounds the camera."));
	}
	@Override
	public void preview(Vec3 at) {
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			trail.clear();
			return;
		}
		double t = Theme.time() * speed.get();
		recordTrail(mc.player.getPosition(partial));
		fx.throughWalls(false);
		boolean seesSelf = !mc.options.getCameraType().isFirstPerson() || nz.temple.client.util.DetachedCamera.active();
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) {
				continue;
			}
			if (!player.isAlive() || player.isInvisible() || player.distanceToSqr(mc.player) > 64 * 64) {
				continue;
			}
			Vec3 p = player.getPosition(partial);
			boolean hidden = self && !seesSelf && !firstPerson.get();
			if (veil.get() && !hidden) {
				if (self && react.get()) {
					vx += ((player.getX() - player.xo) - vx) * 0.15;
					vz += ((player.getZ() - player.zo) - vz) * 0.15;
				}
				veil(fx, p, t, player.getId(), self && react.get());
			}
			if (crown.get() && !hidden) {
				crown(fx, p.add(0, player.getBbHeight() + (player.isCrouching() ? 0.15 : 0.35), 0), t, player.getId());
			}
		}
		if (ribbon.get()) {
			ribbon(fx, t);
		}
	}
	private void veil(Fx fx, Vec3 p, double t, int seed, boolean reacting) {
		int strips = 150;
		double h = 2.4 * height.get();
		double[] xs = new double[strips + 1];
		double[] zs = new double[strips + 1];
		double[] y0 = new double[strips + 1];
		double[] y1 = new double[strips + 1];
		double moving = Math.sqrt(vx * vx + vz * vz);
		double dirX = moving > 1e-4 ? vx / moving : 0;
		double dirZ = moving > 1e-4 ? vz / moving : 0;
		double stream = Math.min(1.6, moving * 5.5);
		double boost = reacting ? Math.min(0.6, moving * 2.2) : 0;
		h *= 1 + boost * 0.45;
		for (int i = 0; i <= strips; i++) {
			double a = Math.PI * 2 * i / strips;
			double r = 1.5 + 0.32 * Math.sin(a * 3 - t * 0.7 + seed) + 0.14 * Math.sin(a * 8 + t * 1.3) + 0.05 * Math.sin(a * 19 - t * 2.1);
			double drag = reacting ? Math.pow(Math.max(0, -(Math.cos(a) * dirX + Math.sin(a) * dirZ)), 1.5) * stream : 0;
			xs[i] = p.x + Math.cos(a) * r - dirX * drag;
			zs[i] = p.z + Math.sin(a) * r - dirZ * drag;
			y0[i] = p.y + 0.2 + 0.14 * Math.sin(a * 2 + t * 1.1 + seed * 0.7) + 0.04 * Math.sin(a * 11 - t * 1.9);
			y1[i] = y0[i] + h * (0.5 + 0.5 * rayHeight(i * 0.21 + seed, t));
		}
		curtain(fx, xs, zs, y0, y1, t, seed, 1.0 + boost);
		if (!realism.get()) {
			return;
		}
		double[] ix = new double[strips + 1], iz = new double[strips + 1], iy1 = new double[strips + 1];
		for (int i = 0; i <= strips; i++) {
			ix[i] = p.x + (xs[i] - p.x) * 0.78;
			iz[i] = p.z + (zs[i] - p.z) * 0.78;
			iy1[i] = y0[i] + (y1[i] - y0[i]) * (0.6 + 0.3 * rayHeight(i * 0.37 + seed + 5, t * 1.2));
		}
		curtain(fx, ix, iz, y0, iy1, t * 1.15, seed + 3, 0.45 + boost * 0.5);
	}
	private void crown(Fx fx, Vec3 top, double t, int seed) {
		int strips = 64;
		double h = 0.6 * height.get();
		double[] xs = new double[strips + 1];
		double[] zs = new double[strips + 1];
		double[] y0 = new double[strips + 1];
		double[] y1 = new double[strips + 1];
		for (int i = 0; i <= strips; i++) {
			double a = Math.PI * 2 * i / strips + t * 0.4;
			double r = 0.42 + 0.06 * Math.sin(a * 5 + t * 2.1);
			xs[i] = top.x + Math.cos(a) * r;
			zs[i] = top.z + Math.sin(a) * r;
			y0[i] = top.y + 0.05 * Math.sin(a * 3 - t * 1.6);
			y1[i] = y0[i] + h * (0.55 + 0.45 * rayHeight(i * 0.33 + seed, t));
		}
		curtain(fx, xs, zs, y0, y1, t, seed + 11, 0.9);
	}
	private void ribbon(Fx fx, double t) {
		int n = trail.size();
		if (n < 2) {
			return;
		}
		double[] xs = new double[n];
		double[] zs = new double[n];
		double[] y0 = new double[n];
		double[] y1 = new double[n];
		double[] fade = new double[n];
		double nowSec = System.nanoTime() / 1e9;
		int i = 0;
		for (double[] pt : trail) {
			double age = nowSec - pt[3];
			fade[i] = Mth.clamp(1 - age / 2.2, 0, 1) * Mth.clamp(i / 3.0, 0, 1);
			xs[i] = pt[0];
			zs[i] = pt[2];
			y0[i] = pt[1] + 0.1 + 0.12 * Math.sin(i * 0.5 - t * 2);
			y1[i] = y0[i] + 1.8 * height.get() * (0.5 + 0.5 * rayHeight(i * 0.35, t));
			i++;
		}
		double[] glow = new double[n];
		for (int k = 0; k < n; k++) {
			glow[k] = fade[k] * shimmer(k * 0.13, t);
		}
		for (int k = 0; k < n - 1; k++) {
			if (glow[k] <= 0.01 && glow[k + 1] <= 0.01) {
				continue;
			}
			strip(fx, xs[k], zs[k], y0[k], y1[k], glow[k], xs[k + 1], zs[k + 1], y0[k + 1], y1[k + 1], glow[k + 1]);
		}
	}
	private void curtain(Fx fx, double[] xs, double[] zs, double[] y0, double[] y1, double t, int seed, double strength) {
		double[] glow = new double[xs.length];
		for (int i = 0; i < xs.length; i++) {
			glow[i] = strength * shimmer(i * 0.21 + seed, t);
		}
		for (int i = 0; i < xs.length - 1; i++) {
			strip(fx, xs[i], zs[i], y0[i], y1[i], glow[i], xs[i + 1], zs[i + 1], y0[i + 1], y1[i + 1], glow[i + 1]);
		}
	}
	private double shimmer(double phase, double t) {
		double fine = Math.pow(noise(phase * 23 + t * 0.6), 2.4);
		double coarse = 0.55 + 0.45 * noise(phase * 4.7 - t * 0.35);
		double ray = 0.18 + 0.82 * fine * coarse;
		double pulse = 0.65 + 0.35 * Math.pow(0.5 + 0.5 * Math.sin(phase * 1.6 - t * 1.2), 3);
		return brightness.get() / 100.0 * ray * pulse;
	}
	private static double rayHeight(double phase, double t) {
		return 0.35 * noise(phase * 3.1 + t * 0.3) + 0.65 * Math.pow(noise(phase * 23 + t * 0.6), 1.5);
	}
	private void strip(Fx fx, double ax, double az, double ay0, double ay1, double ga, double bx, double bz, double by0, double by1,
			double gb) {
		if (ga < 0.01 && gb < 0.01) {
			return;
		}
		int[] c = colours();
		double ah = ay1 - ay0, bh = by1 - by0;
		double[] levels = {-0.05, 0, 0.05, 0.3, 0.6, 1.0};
		int[] tone = {c[4], c[0], c[1], c[2], c[3], c[3]};
		double[] strength = {0, 0.75, 1.0, 0.55, 0.22, 0};
		for (int pass = 0; pass < 2; pass++) {
			boolean glow = pass == 1;
			fx.additive(glow);
			double k = glow ? 0.75 : 0.18;
			for (int l = 0; l < levels.length - 1; l++) {
				double ya0 = ay0 + ah * levels[l], ya1 = ay0 + ah * levels[l + 1];
				double yb0 = by0 + bh * levels[l], yb1 = by0 + bh * levels[l + 1];
				int a0 = Fx.withAlpha(tone[l], (float) (255 * ga * k * strength[l]));
				int b0 = Fx.withAlpha(tone[l], (float) (255 * gb * k * strength[l]));
				int a1 = Fx.withAlpha(tone[l + 1], (float) (255 * ga * k * strength[l + 1]));
				int b1 = Fx.withAlpha(tone[l + 1], (float) (255 * gb * k * strength[l + 1]));
				fx.quad(ax, ya0, az, a0, bx, yb0, bz, b0, bx, yb1, bz, b1, ax, ya1, az, a1);
			}
		}
		fx.additive(false);
	}
	private int[] colours() {
		return switch (palette.get()) {
			case "Arctic" -> new int[] {0xFFC8FFF8, 0xFF3FE6E0, 0xFF2F8BFF, 0xFF3A2DB8, 0xFF9A7CFF};
			case "Crimson" -> new int[] {0xFFFFD8EA, 0xFFFF4FA3, 0xFFE0305A, 0xFF7A1040, 0xFFFF7AC8};
			case "Theme" -> new int[] {Fx.lerp(Theme.color(0), 0xFFFFFFFF, 0.5), Theme.color(0), Theme.color(0.35), Theme.color(0.6), Theme.color(0.8)};
			default -> new int[] {0xFFD8FFE6, 0xFF3CFF8A, 0xFF22D58A, 0xFFB0345E, 0xFFFF5AA8};
		};
	}
	private void recordTrail(Vec3 p) {
		double nowSec = System.nanoTime() / 1e9;
		double[] last = trail.peekFirst();
		if (last == null || (p.x - last[0]) * (p.x - last[0]) + (p.z - last[2]) * (p.z - last[2]) > 0.3 * 0.3
				|| Math.abs(p.y - last[1]) > 0.6) {
			trail.addFirst(new double[] {p.x, p.y, p.z, nowSec});
		}
		while (trail.size() > 48 || !trail.isEmpty() && nowSec - trail.peekLast()[3] > 2.4) {
			trail.pollLast();
		}
	}

	private static double noise(double x) {
		double i = Math.floor(x);
		double f = x - i;
		double u = f * f * (3 - 2 * f);
		return Mth.lerp(u, hash(i), hash(i + 1));
	}
	private static double hash(double n) {
		double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
		return s - Math.floor(s);
	}
}
