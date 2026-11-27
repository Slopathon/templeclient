package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.client.util.WorldEvents;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BlockEffects extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private static volatile BlockEffects instance;
	public static boolean replacesBreakParticles(net.minecraft.core.BlockPos pos) {
		BlockEffects m = instance;
		Minecraft mc = Minecraft.getInstance();
		return m != null && m.isEnabled() && m.onBreak.get() && mc.player != null
				&& mc.player.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= m.range.get() * m.range.get();
	}
	private static final int MAX_SHARDS = 400;
	private static final int MAX_FLASHES = 40;

	private final BoolSetting onBreak;
	private final BoolSetting onPlace;
	private final ModeSetting style;
	private final NumberSetting shardCount;
	private final NumberSetting size;
	private final NumberSetting lifetime;
	private final NumberSetting spread;
	private final NumberSetting range;
	private final BoolSetting blockColors;
	private final nz.temple.client.modules.cosmetic.Look look;
	private final List<Shard> shards = new ArrayList<>();
	private final List<Flash> flashes = new ArrayList<>();
	private ClientLevel level;
	private long lastNanos = System.nanoTime();
	private static final class Shard {
		double x, y, z, vx, vy, vz;
		final long born = System.nanoTime();
		final double life;
		final double phase;
		final boolean placing;
		final int blockColor;
		final double size;
		Shard(double x, double y, double z, double vx, double vy, double vz, double life, double phase, boolean placing,
				int blockColor, double size) {
			this.blockColor = blockColor;
			this.size = size;
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.life = life;
			this.phase = phase;
			this.placing = placing;
		}
	}
	private record Flash(double x, double y, double z, boolean placing, long born) {
	}

	static {
		nz.temple.client.util.VanillaFx.blockEffectsParticles = BlockEffects::replacesBreakParticles;
	}
	public BlockEffects() {
		super("Block Effects", Category.COSMETICS, "Effects when blocks are broken or placed, including by other players.");
		onBreak = add(new BoolSetting("On break", true).describe("Show an effect when a block is broken."));
		onPlace = add(new BoolSetting("On place", true).describe("Show an effect when a block is placed."));
		style = add(new ModeSetting("Style", List.of("Crumble", "Shatter", "Frame"), "Crumble")
				.describe("Crumble breaks the block into falling cubes of its own colour. Shatter breaks it into glass shards. Frame pushes "
						+ "its edges outward as rods. Placed blocks get a glass shell that settles onto them."));
		blockColors = add(new BoolSetting("Block colours", true)
				.describe("Pieces use the block's own colour. Off uses the colour settings.")
				.visibleWhen(() -> style.is("Crumble")));
		group();
		shardCount = add(new NumberSetting("Pieces", 10, 2, 30, 1, "").describe("Pieces per block.")
				.visibleWhen(() -> !style.is("Frame")));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Effect size."));
		lifetime = add(new NumberSetting("Lifetime", 0.9, 0.2, 3, 0.05, " s").describe("How long the pieces last."));
		spread = add(new NumberSetting("Spread", 1, 0.2, 3, 0.1, "x").describe("How far pieces fly out."));
		range = add(new NumberSetting("Range", 32, 4, 96, 4, " m").describe("Only shows effects for blocks within this distance."));
		instance = this;
		group();
		look = new nz.temple.client.modules.cosmetic.Look(s -> add(s), "Glass");
		WorldEvents.onBlockChanged(this::changed);
	}
	@Override
	public void preview(net.minecraft.world.phys.Vec3 at) {
		level = Minecraft.getInstance().level;
		BlockPos pos = BlockPos.containing(at.x, at.y + 0.5, at.z);
		spawn(pos, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), false);
		spawn(pos.above(2), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), true);
	}
	private void changed(BlockPos pos, BlockState oldState, BlockState newState) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			return;
		}
		boolean broke = !oldState.isAir() && newState.isAir();
		boolean placed = oldState.isAir() && !newState.isAir();
		if (broke && !onBreak.get() || placed && !onPlace.get() || !broke && !placed) {
			return;
		}
		double r = range.get();
		if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > r * r) {
			return;
		}
		spawn(pos, placed ? newState : oldState, placed);
	}
	private void spawn(BlockPos pos, BlockState state, boolean placed) {
		Minecraft mc = Minecraft.getInstance();
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		int tint;
		try {
			tint = 0xFF000000 | state.getMapColor(mc.level, pos).col;
		} catch (RuntimeException e) {
			tint = 0xFF888888;
		}
		if (level != mc.level) {
			level = mc.level;
			shards.clear();
			flashes.clear();
		}
		if (flashes.size() < MAX_FLASHES) {
			flashes.add(new Flash(x, y, z, placed, System.nanoTime()));
		}
		if (style.is("Frame") || placed) {
			return;
		}
		ThreadLocalRandom rand = ThreadLocalRandom.current();
		int n = (int) Math.round(shardCount.get());
		for (int i = 0; i < n && shards.size() < MAX_SHARDS; i++) {
			double yaw = rand.nextDouble(Math.PI * 2);
			double pitch = rand.nextDouble(-0.5, 1.1);
			double speed = rand.nextDouble(1.5, 4.5) * spread.get();
			shards.add(new Shard(
					x + rand.nextDouble(-0.35, 0.35), y + rand.nextDouble(-0.35, 0.35), z + rand.nextDouble(-0.35, 0.35),
					Math.cos(yaw) * Math.cos(pitch) * speed,
					Math.sin(pitch) * speed + (placed ? 0.5 : 1.5),
					Math.sin(yaw) * Math.cos(pitch) * speed,
					lifetime.get() * rand.nextDouble(0.8, 1.4), rand.nextDouble(Math.PI * 2), placed, tint,
					rand.nextDouble(0.1, 0.22)));
		}
	}
	public void emit(Fx fx, float partial) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			shards.clear();
			flashes.clear();
			return;
		}
		nz.temple.client.render.Meshes mesh = fx.mesh();
		double s = size.get();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = shards.size() - 1; i >= 0; i--) {
			Shard sh = shards.get(i);
			double age = (now - sh.born) / 1e9;
			if (age > sh.life) {
				shards.remove(i);
				continue;
			}
			sh.vy -= 14 * dt;
			double drag = Math.pow(0.35, dt);
			sh.vx *= drag;
			sh.vz *= drag;
			double ny = sh.y + sh.vy * dt;
			cursor.set(sh.x, ny - sh.size * 0.5, sh.z);
			if (sh.vy < 0 && !mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) {
				sh.vy = -sh.vy * 0.3;
				sh.vx *= 0.6;
				sh.vz *= 0.6;
				ny = sh.y;
			}
			sh.x += sh.vx * dt;
			sh.y = ny;
			sh.z += sh.vz * dt;
			double t = age / sh.life;
			double fade = Math.min(1, (1 - t) * 3);
			double shrink = t > 0.7 ? 1 - (t - 0.7) / 0.3 : 1;
			org.joml.Quaternionf spin = new org.joml.Quaternionf()
					.rotationXYZ((float) (sh.phase + age * sh.vx * 2), (float) (sh.phase * 2 + age * 3), (float) (age * sh.vz * 2));
			double piece = sh.size * s * shrink;
			if (style.is("Crumble")) {
				if (blockColors.get()) {
					mesh.material(nz.temple.client.render.Meshes.Material.MATTE);
					mesh.emission(0);
					mesh.box(sh.x, sh.y, sh.z, spin, piece * 0.5, piece * 0.5, piece * 0.5,
							Fx.withAlpha(sh.blockColor, (float) (255 * fade)));
				} else {
					look.apply(mesh, 0.1f);
					mesh.box(sh.x, sh.y, sh.z, spin, piece * 0.5, piece * 0.5, piece * 0.5, look.color(sh.phase, fade));
				}
			} else {
				look.apply(mesh, 0.2f);
				mesh.crystal(sh.x, sh.y, sh.z, spin, piece * 1.6, piece * 0.4, look.color(sh.phase, fade), 4);
			}
		}
		flashes.removeIf(f -> now - f.born() > 700_000_000L);
		for (Flash f : flashes) {
			double t = (now - f.born()) / 0.7e9;
			double fade = 1 - t;
			if (f.placing()) {
				double half = 0.5 + (1 - ease(t)) * 0.35 * s + 0.01;
				mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
				mesh.emission(0.15f);
				mesh.box(f.x(), f.y(), f.z(), null, half, half, half, look.color(0.3, 0.6 * fade));
				look.apply(mesh, 0.4f);
				frame(mesh, f.x(), f.y(), f.z(), half, 0.025 * s, look.color(0.3, fade));
			} else if (style.is("Frame")) {
				double half = 0.5 + ease(t) * 0.45 * s;
				look.apply(mesh, 0.4f);
				frame(mesh, f.x(), f.y(), f.z(), half, 0.035 * s * (1 - t * 0.6), look.color(0, fade));
			} else if (look.bloom() > 0.2f && t < 0.4) {
				fx.additive(true);
				fx.glow(f.x(), f.y(), f.z(), 0.8 * s, look.color(0, (1 - t / 0.4) * 0.35 * look.bloom()));
				fx.additive(false);
			}
		}
	}
	private static double ease(double t) {
		double u = 1 - Math.max(0, Math.min(1, t));
		return 1 - u * u * u;
	}
	private static void frame(nz.temple.client.render.Meshes mesh, double x, double y, double z, double h, double r, int argb) {
		double[][] corners = {
				{-h, -h, -h}, {h, -h, -h}, {h, -h, h}, {-h, -h, h},
				{-h, h, -h}, {h, h, -h}, {h, h, h}, {-h, h, h}
		};
		int[][] pairs = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		double[] radius = {r, r};
		int[] colours = {argb, argb};
		for (int[] p : pairs) {
			double[] a = corners[p[0]];
			double[] b = corners[p[1]];
			mesh.tube(new double[] {x + a[0], y + a[1], z + a[2], x + b[0], y + b[1], z + b[2]}, 2, radius, colours, 6);
		}
		for (double[] c : corners) {
			mesh.sphere(x + c[0], y + c[1], z + c[2], r * 1.3, argb, 4);
		}
	}
	@Override
	protected void onDisable() {
		shards.clear();
		flashes.clear();
	}
}
