package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.client.util.DetachedCamera;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Contrails extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private final ModeSetting who;
	private final ModeSetting style;
	private final nz.temple.setting.BoolSetting realism;
	private final BoolSetting elytraOnly;
	private final NumberSetting minSpeed;
	private final NumberSetting length;
	private final NumberSetting width;
	private final NumberSetting spread;
	private final BoolSetting firstPerson;
	private final BoolSetting speedColor;
	private final nz.temple.client.modules.cosmetic.Look look;
	private long previewBorn;
	private Vec3 previewAt;
	private final Map<Integer, ArrayDeque<Sample>> trails = new HashMap<>();
	private ClientLevel level;
	private record Sample(double lx, double ly, double lz, double rx, double ry, double rz, float speed, long nanos) {
	}
	public Contrails() {
		super("Contrails", Category.COSMETICS, "Vapour trails from both shoulders while flying, thicker at higher speed. Made for elytra "
				+ "flight.");
		who = add(new ModeSetting("Who", List.of("Both", "Self", "Players"), "Both")
				.describe("Whose flight leaves trails."));
		style = add(new ModeSetting("Style", List.of("Vapour", "Streamers", "Embers"), "Vapour")
				.describe("Vapour leaves puffs that spread out behind you. Streamers draws two tapering tubes. Embers leaves glowing beads "
						+ "from each shoulder."));
		realism = add(new nz.temple.setting.BoolSetting("Realism", false).describe("Uses the rebuilt vapour, with wake vortex threads and a denser trail. Off uses the "
				+ "original.").visibleWhen(() -> style.is("Vapour")));
		group();
		elytraOnly = add(new BoolSetting("Elytra only", false)
				.describe("Only shows trails while gliding. Off also shows them on anyone moving faster than Min speed."));
		minSpeed = add(new NumberSetting("Min speed", 0.35, 0.05, 2, 0.05, " b/t")
				.describe("Minimum speed before trails appear.")
				.visibleWhen(() -> !elytraOnly.get()));
		length = add(new NumberSetting("Length", 2.6, 0.2, 6, 0.1, " s").describe("How long trails last."));
		width = add(new NumberSetting("Width", 0.24, 0.03, 0.8, 0.01, " b").describe("Trail thickness."));
		spread = add(new NumberSetting("Wing spread", 0.45, 0.1, 1.5, 0.05, " b").describe("Distance between the two trails."));
		group();
		speedColor = add(new BoolSetting("Speed colour", true)
				.describe("White at cruising speed, shifting to gold as you speed up. Off uses the colour settings below."));
		firstPerson = add(new BoolSetting("Show own in first person", true)
				.describe("Shows your own trails in first person. They are behind you, so you mostly see them when turning."));
		look = new nz.temple.client.modules.cosmetic.Look(x -> add(x), "Matte");
		legacy(new nz.temple.client.modules.legacy.LegacyContrails(), () -> style.is("Vapour") && !realism.get(), List.of(style, realism));
	}
	public void emit(Fx fx, float partial) {
		if (legacyActive()) {
			((nz.temple.client.modules.legacy.LegacyContrails) legacyModule()).emit(fx, partial);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			trails.clear();
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			trails.clear();
		}
		long now = System.nanoTime();
		long life = (long) (length.get() * 1e9);
		Set<Integer> seen = new HashSet<>();
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) {
				continue;
			}
			if (!player.isAlive() || player.isInvisible() || player.distanceToSqr(mc.player) > 128 * 128) {
				continue;
			}
			float speed = (float) player.getDeltaMovement().length();
			boolean flying = player.isFallFlying();
			boolean qualifies = elytraOnly.get() ? flying : flying || speed >= minSpeed.get();
			ArrayDeque<Sample> samples = trails.computeIfAbsent(player.getId(), k -> new ArrayDeque<>());
			seen.add(player.getId());
			if (qualifies) {
				Vec3 p = player.getPosition(partial);
				float yaw = player.getYRot() * Mth.DEG_TO_RAD;
				double rx = -Math.cos(yaw) * spread.get();
				double rz = -Math.sin(yaw) * spread.get();
				double y = p.y + player.getBbHeight() * (flying ? 0.55 : 0.75);
				Sample last = samples.peekLast();
				if (last == null || Math.abs(last.lx() - (p.x + rx)) + Math.abs(last.ly() - y) + Math.abs(last.lz() - (p.z + rz)) > 0.05) {
					samples.add(new Sample(p.x + rx, y, p.z + rz, p.x - rx, y, p.z - rz, speed, now));
				}
			}
			while (!samples.isEmpty() && now - samples.peekFirst().nanos() > life) {
				samples.removeFirst();
			}
			boolean seesSelf = !mc.options.getCameraType().isFirstPerson() || DetachedCamera.active();
			if (self && !seesSelf && !firstPerson.get()) {
				continue;
			}
			draw(fx, samples, now, life);
		}
		trails.keySet().removeIf(id -> !seen.contains(id));
		if (previewAt != null) {
			double age = (now - previewBorn) / 1e9;
			if (age > 3) {
				previewAt = null;
			} else {
				ArrayDeque<Sample> fake = new ArrayDeque<>();
				for (int i = 30; i >= 0; i--) {
					long when = now - (long) (life * i / 30.0);
					double k = (when - previewBorn) / 1e9 + 2;
					double x = previewAt.x - 3 + k * 2.5;
					double y = previewAt.y + 1.4 + Math.sin(k * 2) * 0.3;
					fake.add(new Sample(x, y, previewAt.z - 0.45, x, y, previewAt.z + 0.45, 1.2f, when));
				}
				draw(fx, fake, now, life);
			}
		}
	}
	@Override
	public void preview(Vec3 at) {
		if (legacyActive()) {
			((nz.temple.client.modules.legacy.LegacyContrails) legacyModule()).preview(at);
			return;
		}
		previewAt = at;
		previewBorn = System.nanoTime();
	}
	private int shade(float speed, double age, double fade) {
		if (speedColor.get()) {
			double heat = Math.max(0, Math.min(1, (speed - 0.6) / 1.6));
			int c = Fx.lerp(0xFFF4F8FF, 0xFFFFC46A, heat * (1 - age * 0.6));
			return Fx.withAlpha(c, (float) (255 * look.alpha() * Math.max(0, Math.min(1, fade))));
		}
		return look.color(age * 0.5, fade);
	}
	private void draw(Fx fx, ArrayDeque<Sample> samples, long now, long life) {
		int n = samples.size();
		if (n < 2) {
			return;
		}
		nz.temple.client.render.Meshes mesh = fx.mesh();
		double w = width.get();
		double[] left = new double[n * 3];
		double[] right = new double[n * 3];
		double[] radius = new double[n];
		double[] ages = new double[n];
		double[] speeds = new double[n];
		int[] colours = new int[n];
		int i = 0;
		for (Sample s : samples) {
			double age = Math.min(1, (double) (now - s.nanos()) / life);
			double keep = (1 - age) * (1 - age);
			left[i * 3] = s.lx();
			left[i * 3 + 1] = s.ly();
			left[i * 3 + 2] = s.lz();
			right[i * 3] = s.rx();
			right[i * 3 + 1] = s.ry();
			right[i * 3 + 2] = s.rz();
			radius[i] = w * 0.35 * (0.2 + 0.8 * (1 - age));
			ages[i] = age;
			speeds[i] = s.speed();
			colours[i] = shade(s.speed(), age, keep);
			i++;
		}
		switch (style.get()) {
			case "Streamers" -> {
				double[] thin = new double[n];
				for (int k = 0; k < n; k++) {
					thin[k] = radius[k] * 0.6;
				}
				nz.temple.client.modules.cosmetic.SoftTrail.trail(fx, look, left, n, thin, colours, 1);
				nz.temple.client.modules.cosmetic.SoftTrail.trail(fx, look, right, n, thin, colours, 1);
			}
			case "Embers" -> {
				fx.additive(!look.matte());
				int stride = Math.max(1, n / 60);
				for (int k = n - 1; k >= 0; k -= stride) {
					double age = ages[k];
					double sink = age * age * 0.6;
					double r = w * 0.35 * (1 - age * 0.6);
					int c = colours[k];
					for (int side = 0; side < 2; side++) {
						double[] from = side == 0 ? left : right;
						double j = Math.sin(k * 2.3 + side) * w * age;
						double x = from[k * 3] + j;
						double y = from[k * 3 + 1] - sink;
						double z = from[k * 3 + 2] + Math.cos(k * 1.7 + side) * w * age;
						fx.light(x, y, z, r, c, 0.85);
						if (k % 5 == 0) {
							fx.light(x, y, z, r * 4, Fx.fade(c, 0.25), 0);
						}
					}
				}
				fx.additive(false);
			}
			default -> {
				fx.additive(false);
				int budget = 700;
				for (int k = n - 2; k >= 0 && budget > 0; k--) {
					double dx = left[k * 3 + 3] - left[k * 3];
					double dy = left[k * 3 + 4] - left[k * 3 + 1];
					double dz = left[k * 3 + 5] - left[k * 3 + 2];
					double gap = Math.sqrt(dx * dx + dy * dy + dz * dz);
					double size = w * (0.8 + ages[k] * 3.6 + ages[k] * ages[k] * 2);
					int subs = Math.max(1, Math.min(6, (int) Math.ceil(gap / (size * 0.8))));
					for (int sub = 0; sub < subs && budget > 0; sub++, budget -= 2) {
						double q = (double) sub / subs;
						double age = ages[k] + (ages[k + 1] - ages[k]) * q;
						double kk = k + q;
						double dense = Math.min(1, 0.45 + (speeds[k] + (speeds[k + 1] - speeds[k]) * q) * 0.45);
						double puff = w * (0.8 + age * 3.6 + age * age * 2);
						double a = 0.55 * dense * Math.pow(1 - age, 1.25) * Math.min(1, age * 12 + 0.35) / Math.sqrt(subs);
						int soft = Fx.fade(Fx.lerp(colours[k], colours[k + 1], q), a);
						double twist = age * 14 + kk * 0.05;
						double coil = w * 0.55 * (1 - age);
						double sink = age * age * 0.9;
						for (int side = 0; side < 2; side++) {
							double[] from = side == 0 ? left : right;
							int sign = side == 0 ? 1 : -1;
							double fx0 = from[k * 3] + (from[k * 3 + 3] - from[k * 3]) * q;
							double fy0 = from[k * 3 + 1] + (from[k * 3 + 4] - from[k * 3 + 1]) * q;
							double fz0 = from[k * 3 + 2] + (from[k * 3 + 5] - from[k * 3 + 2]) * q;
							double ox = from[k * 3] - (left[k * 3] + right[k * 3]) / 2;
							double oz = from[k * 3 + 2] - (left[k * 3 + 2] + right[k * 3 + 2]) / 2;
							double ol = Math.max(1e-6, Math.sqrt(ox * ox + oz * oz));
							double apart = age * 0.5;
							double x = fx0 + ox / ol * apart + Math.cos(twist * sign) * coil * 0.6;
							double y = fy0 - sink + Math.sin(twist * sign) * coil;
							double z = fz0 + oz / ol * apart + Math.cos(twist * sign + 1.3) * coil * 0.6;
							fx.smoke(x, y, z, puff, soft, kk * (side == 0 ? 1.37 : 2.11) + side * 5, sign * (kk * 0.4 + age * 1.2));
						}
					}
				}
				double[] core = new double[n];
				int[] line = new int[n];
				for (int k = 0; k < n; k++) {
					double fresh = Math.max(0, 1 - ages[k] * 4);
					core[k] = w * 0.3 * fresh;
					line[k] = Fx.fade(colours[k], fresh * 0.9);
				}
				fx.ribbon(left, n, core, line, 0.5);
				fx.ribbon(right, n, core, line, 0.5);
				fx.additive(true);
				for (int side = 0; side < 2; side++) {
					double[] from = side == 0 ? left : right;
					int sign = side == 0 ? 1 : -1;
					for (int strand = 0; strand < 2; strand++) {
						int m = 0;
						for (int k = n - 1; k >= 0 && ages[k] < 0.3; k--) {
							m++;
						}
						if (m < 3) {
							continue;
						}
						double[] path = new double[m * 3];
						double[] tw = new double[m];
						int[] tc = new int[m];
						for (int j = 0; j < m; j++) {
							int k = n - m + j;
							double age = ages[k];
							double twist = age * 30 * sign + strand * Math.PI + k * 0.08;
							double coil = w * (0.2 + age * 2.4);
							path[j * 3] = from[k * 3] + Math.cos(twist) * coil * 0.7;
							path[j * 3 + 1] = from[k * 3 + 1] + Math.sin(twist) * coil - age * age * 0.9;
							path[j * 3 + 2] = from[k * 3 + 2] + Math.cos(twist + 1.3) * coil * 0.7;
							double fresh = 1 - age / 0.3;
							tw[j] = w * 0.07 * (0.4 + fresh);
							tc[j] = Fx.fade(colours[k], 0.7 * fresh * fresh);
						}
						fx.ribbon(path, m, tw, tc, 0.6);
					}
				}
				fx.additive(false);
			}
		}
	}
	@Override
	protected void onDisable() {
		trails.clear();
	}
}
