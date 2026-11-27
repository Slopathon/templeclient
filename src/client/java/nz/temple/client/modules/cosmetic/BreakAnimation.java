package nz.temple.client.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import nz.temple.client.mixin.GameModeAccessor;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BreakAnimation extends Module implements nz.temple.client.render.FxSource {
	private static final int SHARD = 2;
	private final ModeSetting style;
	private final ModeSetting colors;
	private final ColorSetting custom;
	private final NumberSetting intensity;
	private final BoolSetting shatter;
	private final List<double[]> particles = new ArrayList<>();
	private long lastNanos = System.nanoTime();
	private double time;
	private BlockPos lastPos;
	private float lastProgress;
	private int lastColor;
	private final BlockChips chips = new BlockChips();

	static {
		nz.temple.client.util.VanillaFx.cracksHidden = BreakAnimation::hidesVanillaCracks;
		nz.temple.client.util.VanillaFx.breakAnimationParticles = BreakAnimation::replacesBreakParticles;
	}
	public BreakAnimation() {
		super("Break Animation", Category.COSMETICS, "Shows your progress mining a block, and breaks it apart when it breaks.");
		style = add(new ModeSetting("Style", List.of("Nuker", "Liquid", "Brackets", "Chip"), "Nuker")
				.describe("Nuker grows a box from the centre that changes from green to red. Liquid fills the block with molten gold. "
						+ "Brackets closes corner brackets in with a scan line. Chip breaks the block away piece by piece."));
		colors = add(new ModeSetting("Color", List.of("Natural", "Theme", "Custom"), "Natural")
				.describe("Natural uses each style's own colours. Theme uses the HUD accent. Custom uses one colour."));
		custom = add(new ColorSetting("Custom color", 0xFFE9B949).describe("The effect's colour.").visibleWhen(() -> colors.is("Custom")));
		intensity = add(new NumberSetting("Intensity", 100, 20, 200, 10, "%").describe("How bright the effect is."));
		instance = this;
		shatter = add(new BoolSetting("Shatter", true).describe("The block breaks into pieces of its own colour."));
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			if (isEnabled()) {
				chips.submit(ctx);
			}
		});
	}
	private int color(int natural) {
		return switch (colors.get()) {
			case "Theme" -> Theme.primary();
			case "Custom" -> custom.get();
			default -> natural;
		};
	}
	private float power() {
		return intensity.getFloat() / 100f;
	}
	private static volatile BreakAnimation instance;
	public static boolean hidesVanillaCracks() {
		BreakAnimation m = instance;
		return m != null && m.isEnabled();
	}

	public static boolean replacesBreakParticles(net.minecraft.core.BlockPos pos) {
		BreakAnimation m = instance;
		Minecraft mc = Minecraft.getInstance();
		return m != null && m.isEnabled() && m.shatter.get() && mc.player != null && mc.player.blockPosition().distSqr(pos) < 7 * 7;
	}
	private static double hash(long seed, int i) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 31;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 29;
		return (h & 0xFFFFFFL) / (double) 0xFFFFFF;
	}
	public void emit(Fx fx, float partial) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		time += dt;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null) {
			particles.clear();
			lastPos = null;
			return;
		}
		GameModeAccessor mode = (GameModeAccessor) mc.gameMode;
		float progress = mode.temple$destroyProgress();
		BlockPos pos = mode.temple$destroyPos();
		boolean aiming = pos != null && mc.options.keyAttack.isDown() && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
		if ((progress > 0 || aiming) && pos != null && !mc.level.getBlockState(pos).isAir()) {
			BlockState state = mc.level.getBlockState(pos);
			if (lastPos != null && !lastPos.equals(pos)) {
				chips.stop(false);
			}
			lastPos = pos.immutable();
			lastProgress = progress;
			lastColor = 0xFF000000 | state.getMapColor(mc.level, pos).col;
			fx.throughWalls(false);
			switch (style.get()) {
				case "Liquid" -> liquid(fx, pos, progress);
				case "Brackets" -> brackets(fx, pos, progress);
				case "Chip" -> chips.mine(lastPos, state, progress);
				default -> nuker(fx, pos, progress);
			}
		} else if (lastPos != null) {
			boolean broke = lastProgress > 0.5f && mc.level.getBlockState(lastPos).isAir();
			if (style.is("Chip")) {
				chips.stop(broke && shatter.get());
			} else if (shatter.get() && broke) {
				burst(lastPos, lastColor);
			}
			lastPos = null;
		}
		if (!style.is("Chip")) {
			chips.stop(false);
		}
		stepParticles(fx, dt);
	}
	private void nuker(Fx fx, BlockPos pos, float progress) {
		float p = Math.max(0, Math.min(1, progress));
		double grow = 1 - Math.pow(1 - p, 2);
		double h = 0.16 + 0.345 * grow;
		int c = color(Fx.lerp(0xFF3CFF6E, 0xFFFF3B3B, Math.pow(p, 1.3)));
		double cx = pos.getX() + 0.5;
		double cy = pos.getY() + 0.5;
		double cz = pos.getZ() + 0.5;
		fx.throughWalls(true);
		for (Direction d : Direction.values()) {
			face(fx, cx, cy, cz, d, h, h, Fx.withAlpha(c, 60 * power()));
		}
		edges(fx, cx - h, cy - h, cz - h, cx + h, cy + h, cz + h, Fx.withAlpha(c, 220 * power()), 0.011);
		fx.throughWalls(false);
	}
	private void liquid(Fx fx, BlockPos pos, float progress) {
		int c = color(HudModule.GOLD);
		double x0 = pos.getX() - 0.004;
		double z0 = pos.getZ() - 0.004;
		double x1 = pos.getX() + 1.004;
		double z1 = pos.getZ() + 1.004;
		double y0 = pos.getY() - 0.004;
		double level = y0 + 1.008 * (1 - Math.pow(1 - Math.min(1, progress), 1.4));
		int deep = Fx.withAlpha(Fx.lerp(c, 0xFF3A2400, 0.3), 105 * power());
		int shallow = Fx.withAlpha(c, 45 * power());
		fx.quad(x0, y0, z0, deep, x1, y0, z0, deep, x1, level, z0, shallow, x0, level, z0, shallow);
		fx.quad(x1, y0, z1, deep, x0, y0, z1, deep, x0, level, z1, shallow, x1, level, z1, shallow);
		fx.quad(x0, y0, z1, deep, x0, y0, z0, deep, x0, level, z0, shallow, x0, level, z1, shallow);
		fx.quad(x1, y0, z0, deep, x1, y0, z1, deep, x1, level, z1, shallow, x1, level, z0, shallow);
		int strips = 8;
		for (int i = 0; i < strips; i++) {
			double a = x0 + (x1 - x0) * i / strips;
			double b = x0 + (x1 - x0) * (i + 1) / strips;
			double ya = level + Math.sin(time * 5 + i * 0.9) * 0.012 * (1 - progress * 0.5);
			double yb = level + Math.sin(time * 5 + (i + 1) * 0.9) * 0.012 * (1 - progress * 0.5);
			int top = Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.3), 95 * power());
			fx.quad(a, ya, z0, top, b, yb, z0, top, b, yb, z1, top, a, ya, z1, top);
		}
		fx.additive(true);
		long seed = pos.asLong();
		for (int i = 0; i < 7; i++) {
			double loop = (time * (0.35 + 0.25 * hash(seed, i)) + hash(seed, i + 40)) % 1.0;
			double by = y0 + (level - y0) * loop;
			double bx = x0 + 0.15 + 0.7 * hash(seed, i + 10) + Math.sin(time * 3 + i) * 0.03;
			double bz = z0 + 0.15 + 0.7 * hash(seed, i + 20);
			fx.orb(bx, by, bz, 0.025 + 0.02 * hash(seed, i + 30), Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.6), 150 * power() * (float) (1 - loop)));
		}
		int rim = Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.55), 230 * power());
		fx.softBeam(x0, level, z0, x1, level, z0, 0.02, rim, rim);
		fx.softBeam(x1, level, z0, x1, level, z1, 0.02, rim, rim);
		fx.softBeam(x1, level, z1, x0, level, z1, 0.02, rim, rim);
		fx.softBeam(x0, level, z1, x0, level, z0, 0.02, rim, rim);
		edges(fx, x0, y0, z0, x1, y0 + 1.008, z1, Fx.withAlpha(c, 120 * power()), 0.008);
		fx.additive(false);
	}
	private void brackets(Fx fx, BlockPos pos, float progress) {
		float p = Math.min(1, progress);
		int c = color(Theme.primary());
		double ease = 1 - Math.pow(1 - p, 3);
		double half = 0.5 + 0.28 * (1 - ease) + 0.006;
		double cx = pos.getX() + 0.5;
		double cy = pos.getY() + 0.5;
		double cz = pos.getZ() + 0.5;
		double arm = 0.16 + 0.1 * ease;
		int line = Fx.withAlpha(Fx.lerp(c, 0xFFFFFFFF, 0.2 + 0.6 * Math.pow(p, 3)), 240 * power());
		fx.throughWalls(true);
		fx.additive(true);
		var cam = fx.cameraPos();
		double ox = cam.x - cx;
		double oy = cam.y - cy;
		double oz = cam.z - cz;
		double ax = Math.abs(ox);
		double ay = Math.abs(oy);
		double az = Math.abs(oz);
		int axis = ax >= ay && ax >= az ? 0 : ay >= az ? 1 : 2;
		double sign = (axis == 0 ? ox : axis == 1 ? oy : oz) >= 0 ? 1 : -1;
		double depth = 0.504 * sign;
		for (int i = 0; i < 4; i++) {
			double su = (i & 1) == 0 ? -1 : 1;
			double sv = (i & 2) == 0 ? -1 : 1;
			double[] corner = facePoint(cx, cy, cz, axis, depth, su * half, sv * half);
			double[] alongU = facePoint(cx, cy, cz, axis, depth, su * (half - arm), sv * half);
			double[] alongV = facePoint(cx, cy, cz, axis, depth, su * half, sv * (half - arm));
			fx.softBeam(corner[0], corner[1], corner[2], alongU[0], alongU[1], alongU[2], 0.026, line, line);
			fx.softBeam(corner[0], corner[1], corner[2], alongV[0], alongV[1], alongV[2], 0.026, line, line);
		}
		double sweep = (time * (0.7 + 1.6 * p)) % 1.0;
		double y = pos.getY() - 0.004 + 1.008 * sweep;
		double h = 0.504;
		int scan = Fx.withAlpha(c, (float) (150 * power() * Math.sin(Math.PI * sweep)));
		fx.softBeam(cx - h, y, cz - h, cx + h, y, cz - h, 0.016, scan, scan);
		fx.softBeam(cx + h, y, cz - h, cx + h, y, cz + h, 0.016, scan, scan);
		fx.softBeam(cx + h, y, cz + h, cx - h, y, cz + h, 0.016, scan, scan);
		fx.softBeam(cx - h, y, cz + h, cx - h, y, cz - h, 0.016, scan, scan);
		fx.additive(false);
		fx.throughWalls(false);
	}
	private static double[] facePoint(double cx, double cy, double cz, int axis, double depth, double u, double v) {
		return switch (axis) {
			case 0 -> new double[] {cx + depth, cy + u, cz + v};
			case 1 -> new double[] {cx + u, cy + depth, cz + v};
			default -> new double[] {cx + u, cy + v, cz + depth};
		};
	}

	private static void face(Fx fx, double cx, double cy, double cz, Direction d, double h, double o, int argb) {
		double nx = d.getStepX() * o;
		double ny = d.getStepY() * o;
		double nz = d.getStepZ() * o;
		double[][] corners = switch (d.getAxis()) {
			case X -> new double[][] {{0, -h, -h}, {0, h, -h}, {0, h, h}, {0, -h, h}};
			case Y -> new double[][] {{-h, 0, -h}, {h, 0, -h}, {h, 0, h}, {-h, 0, h}};
			default -> new double[][] {{-h, -h, 0}, {h, -h, 0}, {h, h, 0}, {-h, h, 0}};
		};
		fx.quad(cx + nx + corners[0][0], cy + ny + corners[0][1], cz + nz + corners[0][2], argb,
				cx + nx + corners[1][0], cy + ny + corners[1][1], cz + nz + corners[1][2], argb,
				cx + nx + corners[2][0], cy + ny + corners[2][1], cz + nz + corners[2][2], argb,
				cx + nx + corners[3][0], cy + ny + corners[3][1], cz + nz + corners[3][2], argb);
	}
	private static void edges(Fx fx, double x0, double y0, double z0, double x1, double y1, double z1, int argb, double width) {
		double[] xs = {x0, x1};
		double[] ys = {y0, y1};
		double[] zs = {z0, z1};
		double e = width / 2;
		for (int a = 0; a < 2; a++) {
			for (int b = 0; b < 2; b++) {
				fx.softBeam(x0 - e, ys[a], zs[b], x1 + e, ys[a], zs[b], width, argb, argb);
				fx.softBeam(xs[a], y0 - e, zs[b], xs[a], y1 + e, zs[b], width, argb, argb);
				fx.softBeam(xs[a], ys[b], z0 - e, xs[a], ys[b], z1 + e, width, argb, argb);
			}
		}
	}
	private void burst(BlockPos pos, int color) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int base = colors.is("Natural") ? color : color(color);
		for (int i = 0; i < 28; i++) {
			double dx = r.nextGaussian();
			double dy = r.nextGaussian() * 0.5 + 0.6;
			double dz = r.nextGaussian();
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			dx /= len;
			dy /= len;
			dz /= len;
			double speed = r.nextDouble(0.9, 2.4);
			double shade = r.nextDouble(0.75, 1.15);
			int c = Fx.lerp(0xFF000000, base, Math.min(1, shade));
			if (shade > 1) {
				c = Fx.lerp(base, 0xFFFFFFFF, shade - 1);
			}
			particles.add(new double[] {pos.getX() + 0.5 + dx * 0.3, pos.getY() + 0.5 + dy * 0.3, pos.getZ() + 0.5 + dz * 0.3,
					dx * speed, dy * speed + 1.8, dz * speed, 0, r.nextDouble(0.8, 1.3), c, SHARD,
					r.nextGaussian(), r.nextGaussian(), r.nextGaussian(), r.nextDouble(5, 12), r.nextDouble(0.035, 0.075)});
		}
	}
	private void stepParticles(Fx fx, double dt) {
		if (particles.isEmpty()) {
			return;
		}
		for (int i = particles.size() - 1; i >= 0; i--) {
			double[] p = particles.get(i);
			p[6] += dt;
			if (p[6] > p[7]) {
				particles.remove(i);
				continue;
			}
			p[4] -= 9 * dt;
			p[3] *= Math.pow(0.4, dt);
			p[5] *= Math.pow(0.4, dt);
			p[0] += p[3] * dt;
			p[1] += p[4] * dt;
			p[2] += p[5] * dt;
			float life = (float) (1 - p[6] / p[7]);
			int c = Fx.withAlpha((int) p[8], 255 * Math.min(1, life * 2.5f) * power());
			double angle = p[6] * p[13];
			double size = p[14] * (0.6 + 0.4 * life);
			double ax = p[10];
			double ay = p[11];
			double az = p[12];
			double al = Math.sqrt(ax * ax + ay * ay + az * az);
			ax /= al;
			ay /= al;
			az /= al;
			double[] v0 = rotate(size, 0, 0, ax, ay, az, angle);
			double[] v1 = rotate(-size * 0.5, size * 0.87, 0, ax, ay, az, angle);
			double[] v2 = rotate(-size * 0.5, -size * 0.8, size * 0.3, ax, ay, az, angle);
			fx.quad(p[0] + v0[0], p[1] + v0[1], p[2] + v0[2], c, p[0] + v1[0], p[1] + v1[1], p[2] + v1[2], c,
					p[0] + v2[0], p[1] + v2[1], p[2] + v2[2], c, p[0] + v2[0], p[1] + v2[1], p[2] + v2[2], c);
		}
	}
	private static double[] rotate(double x, double y, double z, double ax, double ay, double az, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double dot = ax * x + ay * y + az * z;
		double cx = ay * z - az * y;
		double cy = az * x - ax * z;
		double cz = ax * y - ay * x;
		return new double[] {x * cos + cx * sin + ax * dot * (1 - cos), y * cos + cy * sin + ay * dot * (1 - cos),
				z * cos + cz * sin + az * dot * (1 - cos)};
	}

	@Override
	protected void onDisable() {
		particles.clear();
		chips.stop(false);
	}
}
