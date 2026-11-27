package nz.temple.client.render.water;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.modules.render.RealisticWater;
import nz.temple.client.util.WorldEvents;
import nz.temple.water.Droplets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DropletWater {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/droplets");
	private static final int CAPACITY = 200000;
	private static final double REACH = 7;
	private static final long ACTION_WINDOW = 1_500_000_000L;
	private static final int PER_POUR = Droplets.PER_SIDE * Droplets.PER_SIDE * Droplets.PER_SIDE;
	private static final float STEP = 1 / 30f;
	private static final int CACHE_MARGIN = 3;
	private static final byte SOLID = 1, WATER = 2;
	private static final Droplets droplets = new Droplets(CAPACITY);
	private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Temple droplets");
		t.setDaemon(true);
		return t;
	});
	private static Future<?> running;
	private static final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
	private static final ConcurrentLinkedQueue<Integer> settledQueue = new ConcurrentLinkedQueue<>();

	private static volatile Droplets.Frame shown = new Droplets.Frame();
	private static Droplets.Frame spare = new Droplets.Frame();
	private static volatile long shownAt;
	private static volatile float shownSpan = STEP * 1e9f;
	private static float batchNanos;
	private record Cell(byte flags, float[] boxes) {
	}
	private static final Cell EMPTY = new Cell((byte) 0, null);
	private static final Map<Long, Cell> cells = new ConcurrentHashMap<>();
	private static float minX, minY, minZ, maxX, maxY, maxZ;
	private static boolean enabled;
	private static volatile boolean failed;
	private static ClientLevel level;
	private static long lastNanos;
	private static float carried;
	private static long attackedAt;
	private static int nextGroup = 1;
	private static int pendingDroplets;
	private static boolean useWasDown;
	private static final Map<Long, Long> broken = new HashMap<>();
	private static final Map<Long, Long> poured = new HashMap<>();
	private static final java.util.Set<BlockPos> hidden = new java.util.HashSet<>();
	private static final boolean PROFILE = Boolean.getBoolean("temple.showcase");
	private static volatile long workNanos;
	private static volatile int workSteps;
	private static long profileSince;
	private DropletWater() {
	}
	public static void register() {
		WorldEvents.onBlockChanged(DropletWater::changed);
	}
	public static void pretendBreak() {
		attackedAt = System.nanoTime();
	}

	public static void pretendPour(int x, int y, int z) {
		pour(new BlockPos(x, y, z));
	}
	public static Droplets.Frame frame() {
		return shown;
	}
	public static float between() {
		return Math.min(1, (System.nanoTime() - shownAt) / shownSpan);
	}
	public static boolean active() {
		return enabled && !failed && shown.count > 0;
	}
	private static void changed(BlockPos pos, BlockState oldState, BlockState newState) {
		cells.remove(pos.asLong());
		Minecraft mc = Minecraft.getInstance();
		if (!enabled || failed || mc.player == null || mc.level == null) {
			return;
		}
		long now = System.nanoTime();
		boolean wasWater = oldState.getFluidState().is(FluidTags.WATER);
		boolean isWater = newState.getFluidState().is(FluidTags.WATER);
		double dist = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
		if (!oldState.isAir() && !wasWater && (newState.isAir() || isWater) && dist <= REACH && now - attackedAt < ACTION_WINDOW) {
			broken.put(pos.asLong(), now);
		}
		BlockPos at = pos.immutable();
		if (wasWater && !isWater) {
			if (hidden.remove(at)) {
				hide(at, false);
			}
			return;
		}
		if (!isWater || wasWater) {
			return;
		}
		Long pouredAt = poured.remove(pos.asLong());
		if (pouredAt != null && now - pouredAt < ACTION_WINDOW) {
			hideForDroplets(at);
			return;
		}
		for (Direction d : Direction.values()) {
			if (hidden.contains(at.relative(d))) {
				broken.remove(pos.asLong());
				hideForDroplets(at);
				return;
			}
		}
		Long brokeAt = broken.remove(pos.asLong());
		if (brokeAt != null && now - brokeAt < ACTION_WINDOW) {
			float vx = 0, vy = 0, vz = 0;
			boolean beside = false;
			for (Direction d : Direction.values()) {
				if (d != Direction.DOWN && mc.level.getFluidState(at.relative(d)).is(FluidTags.WATER) && !RealisticWater.hidden(at.relative(d))) {
					vx -= d.getStepX() * 2.5f;
					vy -= d.getStepY() * 2.5f;
					vz -= d.getStepZ() * 2.5f;
					beside = true;
				}
			}
			if (beside && spawn(at.getX(), at.getY(), at.getZ(), vx, vy, vz)) {
				hideForDroplets(at);
			}
		}
	}
	private static void pour(BlockPos target) {
		float base = target.getY();
		Droplets.Frame f = shown;
		for (int i = 0; i < f.count; i++) {
			if (f.x[i] >= target.getX() && f.x[i] < target.getX() + 1 && f.z[i] >= target.getZ() && f.z[i] < target.getZ() + 1
					&& f.y[i] >= target.getY() - 0.5f && f.y[i] < base + 1.5f) {
				base = Math.max(base, f.y[i] + Droplets.SPACING * 0.6f);
			}
		}
		if (spawn(target.getX(), base, target.getZ(), 0, 0, 0)) {
			poured.put(target.asLong(), System.nanoTime());
			if (Minecraft.getInstance().level.getFluidState(target).is(FluidTags.WATER)) {
				hideForDroplets(target);
			}
		}
	}
	private static void hideForDroplets(BlockPos at) {
		if (hidden.add(at)) {
			hide(at, true);
		}
	}
	private static boolean holdsWaterBucket(Minecraft mc) {
		return mc.player.getMainHandItem().is(Items.WATER_BUCKET) || mc.player.getOffhandItem().is(Items.WATER_BUCKET);
	}
	private static boolean spawn(float x, float y, float z, float vx, float vy, float vz) {
		if (shown.count + pendingDroplets + PER_POUR > CAPACITY) {
			return false;
		}
		int id = nextGroup++;
		pendingDroplets += PER_POUR;
		cache((int) Math.floor(x) - CACHE_MARGIN, (int) Math.floor(y) - CACHE_MARGIN, (int) Math.floor(z) - CACHE_MARGIN,
				(int) Math.floor(x) + CACHE_MARGIN, (int) Math.floor(y) + CACHE_MARGIN + 1, (int) Math.floor(z) + CACHE_MARGIN);
		commands.add(() -> droplets.spawnAt(id, x, y, z, vx, vy, vz));
		return true;
	}
	private static void hide(BlockPos at, boolean hidden) {
		RealisticWater.hide(at, hidden);
		cells.remove(at.asLong());
	}
	public static void frame(boolean on) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? STEP : Math.min(0.05f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (mc.level != level || !on || mc.player == null) {
			if (!hidden.isEmpty() || shown.count > 0 || pendingDroplets > 0) {
				reset();
			}
			level = mc.level;
			enabled = on && mc.player != null;
			return;
		}
		enabled = true;
		boolean useDown = mc.options.keyUse.isDown();
		if (useDown && !useWasDown && mc.gui.screen() == null && holdsWaterBucket(mc)) {
			var hit = mc.player.pick(mc.player.blockInteractionRange(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), false);
			if (hit instanceof net.minecraft.world.phys.BlockHitResult block && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				pour(block.getBlockPos().relative(block.getDirection()));
			}
		}
		useWasDown = useDown;
		poured.values().removeIf(t -> now - t > ACTION_WINDOW);
		if (mc.options.keyAttack.isDown()) {
			attackedAt = now;
		}
		broken.values().removeIf(t -> now - t > ACTION_WINDOW);
		if (failed) {
			return;
		}
		try {
			settledQueue.clear();
			carried = Math.min(carried + dt, STEP * 3);
			boolean idle = running == null || running.isDone();
			if (idle && running != null) {
				running.get();
				running = null;
			}
			if (idle && carried >= STEP && (shown.count > 0 || !commands.isEmpty())) {
				int steps = (int) (carried / STEP);
				carried -= steps * STEP;
				Droplets.Frame f = shown;
				if (f.count > 0) {
					cache((int) Math.floor(minX) - CACHE_MARGIN, (int) Math.floor(minY) - CACHE_MARGIN, (int) Math.floor(minZ) - CACHE_MARGIN,
							(int) Math.floor(maxX) + CACHE_MARGIN, (int) Math.floor(maxY) + CACHE_MARGIN, (int) Math.floor(maxZ) + CACHE_MARGIN);
				}
				List<double[]> boxes = bodies(mc);
				running = worker.submit(() -> work(steps, boxes));
			}
			profile(mc, now);
		} catch (Exception e) {
			failed = true;
			LOGGER.error("Volumetric water disabled", e);
			reset();
		}
	}
	private static void work(int steps, List<double[]> boxes) {
		long start = System.nanoTime();
		for (Runnable r; (r = commands.poll()) != null; ) {
			r.run();
		}
		pendingDroplets = 0;
		droplets.beginBatch();
		for (int i = 0; i < steps; i++) {
			droplets.step(STEP, WORLD, boxes);
		}

		settledQueue.addAll(droplets.takeSettled());
		Droplets.Frame f = spare;
		droplets.copyTo(f);
		float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
		for (int i = 0; i < f.count; i++) {
			x0 = Math.min(x0, f.x[i]);
			y0 = Math.min(y0, f.y[i]);
			z0 = Math.min(z0, f.z[i]);
			x1 = Math.max(x1, f.x[i]);
			y1 = Math.max(y1, f.y[i]);
			z1 = Math.max(z1, f.z[i]);
		}
		minX = x0;
		minY = y0;
		minZ = z0;
		maxX = x1;
		maxY = y1;
		maxZ = z1;
		spare = shown;
		shown = f;
		long end = System.nanoTime();
		batchNanos = batchNanos == 0 ? end - start : batchNanos * 0.7f + (end - start) * 0.3f;
		shownSpan = Math.max(steps * STEP * 1e9f, batchNanos);
		shownAt = end;
		workNanos += end - start;
		workSteps += steps;
	}
	private static void profile(Minecraft mc, long now) {
		if (!PROFILE || now - profileSince < 2_000_000_000L) {
			return;
		}
		if (workSteps > 0) {
			System.out.printf("TEMPLE_DROPLET_PROFILE %.3f ms per step on the worker over %d steps, %d droplets, fps %d%n",
					workNanos / 1e6 / workSteps, workSteps, shown.count, mc.getFps());
		}
		workNanos = 0;
		workSteps = 0;
		profileSince = now;
	}
	private static void reset() {
		if (running != null) {
			running.cancel(false);
			running = null;
		}
		commands.clear();
		commands.add(droplets::clear);
		settledQueue.clear();
		for (BlockPos p : hidden) {
			hide(p, false);
		}
		hidden.clear();
		poured.clear();
		broken.clear();
		pendingDroplets = 0;
		cells.clear();
		shown = new Droplets.Frame();
		worker.submit(() -> {
			for (Runnable r; (r = commands.poll()) != null; ) {
				r.run();
			}
		});
	}
	private static void cache(int x0, int y0, int z0, int x1, int y1, int z1) {
		if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 40_000) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					long key = BlockPos.asLong(x, y, z);
					if (cells.containsKey(key)) {
						continue;
					}
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					byte flags = 0;
					float[] boxes = null;
					if (!state.isAir()) {
						var shape = state.getCollisionShape(level, pos);
						if (!shape.isEmpty()) {
							if (shape == net.minecraft.world.phys.shapes.Shapes.block()) {
								flags |= SOLID;
								boxes = Droplets.World.FULL;
							} else {
								List<AABB> list = shape.toAabbs();
								boxes = new float[list.size() * 6];
								for (int b = 0; b < list.size(); b++) {
									AABB a = list.get(b);
									boxes[b * 6] = (float) a.minX;
									boxes[b * 6 + 1] = (float) a.minY;
									boxes[b * 6 + 2] = (float) a.minZ;
									boxes[b * 6 + 3] = (float) a.maxX;
									boxes[b * 6 + 4] = (float) a.maxY;
									boxes[b * 6 + 5] = (float) a.maxZ;
								}
							}
						}
					}
					if (state.getFluidState().is(FluidTags.WATER) && !RealisticWater.hidden(pos)) {
						flags |= WATER;
					}
					cells.put(key, flags == 0 && boxes == null ? EMPTY : new Cell(flags, boxes));
				}
			}
		}
	}
	private static List<double[]> bodies(Minecraft mc) {
		List<double[]> boxes = new ArrayList<>();
		Vec3 me = mc.player.position();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (Entity e : level.entitiesForRendering()) {
			if (e.distanceToSqr(me) > 32 * 32 || e.isSpectator()) {
				continue;
			}
			Vec3 at = e.getPosition(partial);
			AABB box = e.getBoundingBox().move(at.subtract(e.position()));
			boxes.add(new double[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
					(e.getX() - e.xo) * 20, (e.getY() - e.yo) * 20, (e.getZ() - e.zo) * 20});
		}
		return boxes;
	}
	private static final Droplets.World WORLD = new Droplets.World() {
		@Override
		public boolean solid(int x, int y, int z) {
			Cell c = cells.get(BlockPos.asLong(x, y, z));
			return c != null && (c.flags() & SOLID) != 0;
		}
		@Override
		public float[] boxes(int x, int y, int z) {
			Cell c = cells.get(BlockPos.asLong(x, y, z));
			return c == null ? null : c.boxes();
		}
		@Override
		public boolean water(int x, int y, int z) {
			Cell c = cells.get(BlockPos.asLong(x, y, z));
			return c != null && (c.flags() & WATER) != 0;
		}
	};
}
