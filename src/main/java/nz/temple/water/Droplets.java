package nz.temple.water;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Droplets {
	public static final int PER_SIDE = 22;
	public static final float SPACING = 1f / PER_SIDE;
	private static final float H = SPACING * 2;
	private static final float H2 = H * H;
	private static final float POLY6 = (float) (315 / (64 * Math.PI * Math.pow(H, 9)));
	private static final float SPIKY = (float) (-45 / (Math.PI * Math.pow(H, 6)));
	private static final float GRAVITY = 16;
	public static final int ITERATIONS = 4;
	private volatile int iterations = ITERATIONS;
	private static final float EPSILON = 0.3f;
	private static final float COHESION = 0.009f;
	private static final float CLUMP = 0.0012f;
	private static final float CLUMP_AT = 0.3f;
	private static final float CLUMP_W = (float) (POLY6 * Math.pow(H2 - CLUMP_AT * CLUMP_AT * H2, 3));
	private static final float VISCOSITY = 0.018f;
	private static final float FLOOR_DRAG = 0.7f;
	private static final float MARGIN = SPACING * 0.3f;
	private static final float MAX_MOVE = SPACING * 0.6f;
	private static final int MAX_NEIGHBOURS = 96;
	private static final float REST_REACH = SPACING * 1f;
	private static final float REST_SPEED = 0.15f;
	private static final float REST_TIME = 0.5f;
	private static final float DAMPING = 0.4f;
	private static final float STILL = 0.6f;
	private static final float STILL_DAMPING = 6f;
	private static final float MAX_KICK = 1.0f;
	private static final float WAKE_SPEED = 0.3f;
	private static final int TABLE = 1 << 15;
	private static final int WALL_STEPS = 64;
	private static final float[] WALL_DENSITY = new float[WALL_STEPS + 1];
	private static final float[] WALL_PUSH = new float[WALL_STEPS + 1];
	static {
		for (int s = 0; s <= WALL_STEPS; s++) {
			float d = H * s / WALL_STEPS;
			float density = 0;
			float push = 0;
			int reach = (int) Math.ceil(H / SPACING) + 1;
			for (int m = 0; m < reach; m++) {
				float n = d + (SPACING - SPACING * 0.3f) + m * SPACING;
				for (int a = -reach; a <= reach; a++) {
					for (int b = -reach; b <= reach; b++) {
						float r2 = n * n + (a * a + b * b) * SPACING * SPACING;
						if (r2 >= H2) {
							continue;
						}
						float q = H2 - r2;
						density += POLY6 * q * q * q;
						float r = (float) Math.sqrt(r2);
						push += -SPIKY * (H - r) * (H - r) * n / r;
					}
				}
			}
			WALL_DENSITY[s] = density;
			WALL_PUSH[s] = push;
		}
	}
	public interface World {
		float[] FULL = {0, 0, 0, 1, 1, 1};
		boolean solid(int x, int y, int z);
		default float[] boxes(int x, int y, int z) {
			return solid(x, y, z) ? FULL : null;
		}

		boolean water(int x, int y, int z);
	}
	private static final java.util.concurrent.ForkJoinPool POOL = new java.util.concurrent.ForkJoinPool(
			Math.max(1, Runtime.getRuntime().availableProcessors() - 1), pool -> {
				var t = java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				t.setName("Temple droplet solver " + t.getPoolIndex());
				return t;
			}, null, false);
	private final int capacity;
	private int count;
	private final float[] x, y, z, px, py, pz, vx, vy, vz, lambda, dx, dy, dz;
	private final float[] lx, ly, lz;
	private final int[] group;
	private final int[] id;
	private final int[] freeIds;
	private int freeCount;
	private final boolean[] grounded;
	private final boolean[] asleep;
	private final float[] stillFor;
	private final float[] anchorX, anchorY, anchorZ;
	private int sinceReorder;
	private boolean reordered;
	private long[] sortKeys = new long[0];
	private float[] scratchF = new float[0];
	private boolean[] scratchB = new boolean[0];
	private int[] scratchI = new int[0];
	private final byte[] wallFace;
	private final float[] wallDist;
	private final int[] wallCount;
	private int[] neighbours = new int[0];

	private final int[] slot;
	private final int[] neighbourCount;
	private float[] pairX = new float[0], pairY = new float[0], pairZ = new float[0], pairGrad = new float[0],
			pairClump = new float[0], pairPull = new float[0];
	private final int[] head = new int[TABLE];
	private final int[] next;
	private final float restDensity;
	private int nextGroup = 1;
	private final Map<Integer, float[]> groups = new HashMap<>();
	private final List<Integer> settled = new ArrayList<>();
	public Droplets(int capacity) {
		this.capacity = capacity;
		x = new float[capacity];
		y = new float[capacity];
		z = new float[capacity];
		px = new float[capacity];
		py = new float[capacity];
		pz = new float[capacity];
		vx = new float[capacity];
		vy = new float[capacity];
		vz = new float[capacity];
		lambda = new float[capacity];
		lx = new float[capacity];
		ly = new float[capacity];
		lz = new float[capacity];
		dx = new float[capacity];
		dy = new float[capacity];
		dz = new float[capacity];
		group = new int[capacity];
		id = new int[capacity];
		freeIds = new int[capacity];
		for (int i = 0; i < capacity; i++) {
			freeIds[i] = capacity - 1 - i;
		}
		freeCount = capacity;
		grounded = new boolean[capacity];
		asleep = new boolean[capacity];
		stillFor = new float[capacity];
		anchorX = new float[capacity];
		anchorY = new float[capacity];
		anchorZ = new float[capacity];
		wallFace = new byte[capacity * 6];
		wallDist = new float[capacity * 6];
		wallCount = new int[capacity];
		next = new int[capacity];
		slot = new int[capacity];
		neighbourCount = new int[capacity];
		restDensity = latticeDensity();
	}
	private static float latticeDensity() {
		float sum = 0;
		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++) {
				for (int k = -2; k <= 2; k++) {
					float r2 = (i * i + j * j + k * k) * SPACING * SPACING;
					if (r2 < H2) {
						float d = H2 - r2;
						sum += POLY6 * d * d * d;
					}
				}
			}
		}
		return sum;
	}
	public int count() {
		return count;
	}
	public double x(int i) {
		return x[i];
	}
	public double y(int i) {
		return y[i];
	}
	public double z(int i) {
		return z[i];
	}
	public double x(int i, float t) {
		return lx[i] + (x[i] - lx[i]) * t;
	}
	public double y(int i, float t) {
		return ly[i] + (y[i] - ly[i]) * t;
	}
	public double z(int i, float t) {
		return lz[i] + (z[i] - lz[i]) * t;
	}
	public float speed(int i) {
		return (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
	}
	public int group(int i) {
		return group[i];
	}
	public boolean live(int g) {
		return groups.containsKey(g);
	}
	public int spawnBlock(int bx, int by, int bz, float svx, float svy, float svz) {
		return spawnBlock(nextGroup++, bx, by, bz, svx, svy, svz);
	}
	public int spawnBlock(int g, int bx, int by, int bz, float svx, float svy, float svz) {
		return spawnAt(g, bx, by, bz, svx, svy, svz);
	}
	public int spawnAt(int g, float bx, float by, float bz, float svx, float svy, float svz) {
		int n = PER_SIDE * PER_SIDE * PER_SIDE;
		if (count + n > capacity) {
			return 0;
		}
		for (int i = 0; i < PER_SIDE; i++) {
			for (int j = 0; j < PER_SIDE; j++) {
				for (int k = 0; k < PER_SIDE; k++) {
					int p = count++;

					float jitter = ((i * 7 + j * 13 + k * 29) % 11 - 5) * 0.001f;
					x[p] = bx + (i + 0.5f) * SPACING + jitter;
					y[p] = by + (j + 0.5f) * SPACING - jitter;
					z[p] = bz + (k + 0.5f) * SPACING + jitter * 0.5f;
					lx[p] = x[p];
					ly[p] = y[p];
					lz[p] = z[p];
					vx[p] = svx;
					vy[p] = svy;
					vz[p] = svz;
					group[p] = g;
					id[p] = freeIds[--freeCount];
				}
			}
		}
		groups.put(g, new float[] {0, 0});
		return g;
	}
	public static final class Frame {
		public int count;
		public float[] x = new float[0], y = new float[0], z = new float[0], lx = new float[0], ly = new float[0], lz = new float[0];
		public int[] group = new int[0];

		public int[] id = new int[0];
		public boolean reordered;
	}

	public void copyTo(Frame f) {
		if (f.x.length < count) {
			int n = Math.max(count, f.x.length * 2);
			f.x = new float[n];
			f.y = new float[n];
			f.z = new float[n];
			f.lx = new float[n];
			f.ly = new float[n];
			f.lz = new float[n];
			f.group = new int[n];
			f.id = new int[n];
		}
		System.arraycopy(x, 0, f.x, 0, count);
		System.arraycopy(y, 0, f.y, 0, count);
		System.arraycopy(z, 0, f.z, 0, count);
		System.arraycopy(lx, 0, f.lx, 0, count);
		System.arraycopy(ly, 0, f.ly, 0, count);
		System.arraycopy(lz, 0, f.lz, 0, count);
		System.arraycopy(group, 0, f.group, 0, count);
		System.arraycopy(id, 0, f.id, 0, count);
		f.count = count;
		f.reordered = reordered;
		reordered = false;
	}
	private void reorder() {
		if (count < 2) {
			return;
		}
		if (sortKeys.length < count) {
			int n = Math.max(count, sortKeys.length * 3 / 2);
			sortKeys = new long[n];
			scratchF = new float[n];
			scratchB = new boolean[n];
			scratchI = new int[n];
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			minX = Math.min(minX, x[i]);
			minY = Math.min(minY, y[i]);
			minZ = Math.min(minZ, z[i]);
		}
		for (int i = 0; i < count; i++) {
			long cx = Math.min(1023, (long) ((x[i] - minX) / H));
			long cy = Math.min(1023, (long) ((y[i] - minY) / H));
			long cz = Math.min(1023, (long) ((z[i] - minZ) / H));
			sortKeys[i] = (morton(cx) | morton(cy) << 1 | morton(cz) << 2) << 17 | i;
		}
		java.util.Arrays.sort(sortKeys, 0, count);
		for (float[] a : new float[][] {x, y, z, lx, ly, lz, vx, vy, vz, stillFor, anchorX, anchorY, anchorZ}) {
			for (int k = 0; k < count; k++) {
				scratchF[k] = a[(int) (sortKeys[k] & 0x1FFFF)];
			}
			System.arraycopy(scratchF, 0, a, 0, count);
		}
		for (boolean[] a : new boolean[][] {grounded, asleep}) {
			for (int k = 0; k < count; k++) {
				scratchB[k] = a[(int) (sortKeys[k] & 0x1FFFF)];
			}
			System.arraycopy(scratchB, 0, a, 0, count);
		}
		for (int k = 0; k < count; k++) {
			scratchI[k] = group[(int) (sortKeys[k] & 0x1FFFF)];
		}
		System.arraycopy(scratchI, 0, group, 0, count);
		for (int k = 0; k < count; k++) {
			scratchI[k] = id[(int) (sortKeys[k] & 0x1FFFF)];
		}
		System.arraycopy(scratchI, 0, id, 0, count);
		reordered = true;
	}
	private static long morton(long v) {
		v &= 0x3FF;
		v = (v | v << 16) & 0x030000FFL;
		v = (v | v << 8) & 0x0300F00FL;
		v = (v | v << 4) & 0x030C30C3L;
		v = (v | v << 2) & 0x09249249L;
		return v;
	}
	public void iterations(int n) {
		iterations = Math.max(2, Math.min(ITERATIONS, n));
	}
	public int iterations() {
		return iterations;
	}
	public void beginBatch() {
		System.arraycopy(x, 0, lx, 0, count);
		System.arraycopy(y, 0, ly, 0, count);
		System.arraycopy(z, 0, lz, 0, count);
	}
	private void parallel(java.util.function.IntConsumer body) {
		if (count < 600) {
			for (int i = 0; i < count; i++) {
				body.accept(i);
			}
			return;
		}
		POOL.submit(() -> java.util.stream.IntStream.range(0, count).parallel().forEach(body)).join();
	}
	public void remove(int g) {
		int w = 0;
		for (int i = 0; i < count; i++) {
			if (group[i] != g) {
				copy(i, w++);
			} else {
				free(i);
			}
		}
		count = w;
		groups.remove(g);
	}
	public void clear() {
		for (int i = 0; i < count; i++) {
			free(i);
		}
		count = 0;
		groups.clear();
		settled.clear();
	}
	public int capacity() {
		return capacity;
	}
	private void free(int i) {
		freeIds[freeCount++] = id[i];
	}
	public boolean asleep(int g) {
		boolean any = false;
		for (int i = 0; i < count; i++) {
			if (group[i] == g) {
				if (!asleep[i]) {
					return false;
				}
				any = true;
			}
		}
		return any;
	}

	public int awake(int g) {
		int n = 0;
		for (int i = 0; i < count; i++) {
			if (group[i] == g && !asleep[i]) {
				n++;
			}
		}
		return n;
	}
	public List<Integer> takeSettled() {
		List<Integer> out = new ArrayList<>(settled);
		settled.clear();
		return out;
	}
	public void step(float dt, World world, List<double[]> boxes) {
		if (count == 0) {
			return;
		}
		markSleep(boxes);
		boolean awake = false;
		float fastest = 0;
		for (int i = 0; i < count; i++) {
			if (!asleep[i]) {
				awake = true;
				fastest = Math.max(fastest, speed(i) + GRAVITY * dt);
			}
		}
		if (!awake) {
			return;
		}

		int steps = Math.max(1, Math.min(3, (int) Math.ceil(fastest * dt / MAX_MOVE)));
		float sub = dt / steps;
		for (int s = 0; s < steps; s++) {
			substep(sub, world, boxes);
		}
		absorb(world);
		if (++sinceReorder >= 30) {
			sinceReorder = 0;
			reorder();
		}
		rest(dt);
	}
	private void markSleep(List<double[]> boxes) {
		if (boxes.isEmpty()) {
			return;
		}
		parallel(i -> {
			if (!asleep[i]) {
				return;
			}
			for (double[] b : boxes) {
				if (x[i] > b[0] - H && x[i] < b[3] + H && y[i] > b[1] - H && y[i] < b[4] + H && z[i] > b[2] - H && z[i] < b[5] + H
						&& Math.abs(b[6]) + Math.abs(b[7]) + Math.abs(b[8]) > 0.05) {
					wakeOne(i);
				}
			}
		});
	}
	private void wakeOne(int i) {
		asleep[i] = false;
		stillFor[i] = 0;
		vx[i] = vy[i] = vz[i] = 0;
	}
	private void substep(float dt, World world, List<double[]> boxes) {
		parallel(i -> {
			if (asleep[i]) {
				px[i] = x[i];
				py[i] = y[i];
				pz[i] = z[i];
				lambda[i] = 0;
				wallCount[i] = 0;
				return;
			}
			vy[i] -= GRAVITY * dt;
			px[i] = x[i] + vx[i] * dt;
			py[i] = y[i] + vy[i] * dt;
			pz[i] = z[i] + vz[i] * dt;
			push(i, boxes, dt);
			grounded[i] = false;
			collide(i, world, x[i], y[i], z[i]);
		});
		findNeighbours();
		parallel(i -> {
			if (!asleep[i]) {
				findWalls(i, world);
			}
		});
		for (int i = 0; i < count; i++) {
			if (asleep[i] || speed(i) < WAKE_SPEED) {
				continue;
			}
			int base = slot[i] * MAX_NEIGHBOURS;
			for (int k = 0; k < neighbourCount[i]; k++) {
				int j = neighbours[base + k];
				if (asleep[j]) {
					wakeOne(j);
				}
			}
		}
		for (int it = 0; it < iterations; it++) {
			parallel(i -> {
				if (asleep[i]) {
					return;
				}
				float density = POLY6 * H2 * H2 * H2;
				float gx = 0, gy = 0, gz = 0, sum2 = 0;
				int base = slot[i] * MAX_NEIGHBOURS;
				for (int k = 0; k < neighbourCount[i]; k++) {
					int j = neighbours[base + k];
					int pk = base + k;
					float rx = px[i] - px[j], ry = py[i] - py[j], rz = pz[i] - pz[j];
					float r2 = rx * rx + ry * ry + rz * rz;
					pairX[pk] = rx;
					pairY[pk] = ry;
					pairZ[pk] = rz;
					if (r2 >= H2 || r2 < 1e-12f) {
						pairGrad[pk] = 0;
						pairClump[pk] = 0;
						pairPull[pk] = 0;
						continue;
					}
					float d = H2 - r2;
					float w = POLY6 * d * d * d;
					density += w;
					float r = (float) Math.sqrt(r2);
					float f = SPIKY * (H - r) * (H - r) / r / restDensity;
					float clump = w / CLUMP_W;
					clump *= clump;
					pairGrad[pk] = f;
					pairClump[pk] = CLUMP * clump * clump;
					pairPull[pk] = r > SPACING ? (r - SPACING) / r : 0;
					float wx = f * rx, wy = f * ry, wz = f * rz;
					gx += wx;
					gy += wy;
					gz += wz;
					sum2 += wx * wx + wy * wy + wz * wz;
				}
				for (int w = 0; w < wallCount[i]; w++) {
					float d = wallDist[i * 6 + w];
					int s = Math.min(WALL_STEPS, Math.round(d / H * WALL_STEPS));
					density += WALL_DENSITY[s];
					float g = -WALL_PUSH[s] / restDensity;
					int face = wallFace[i * 6 + w];
					float sign = (face & 1) == 0 ? 1 : -1;
					switch (face >> 1) {
						case 0 -> gx += g * sign;
						case 1 -> gy += g * sign;
						default -> gz += g * sign;
					}
				}
				float c = Math.max(0, density / restDensity - 1);
				lambda[i] = -c / (gx * gx + gy * gy + gz * gz + sum2 + EPSILON);
			});
			parallel(i -> {
				if (asleep[i]) {
					return;
				}
				float sx = 0, sy = 0, sz = 0;
				float cx = 0, cy = 0, cz = 0;
				int pulled = 0;
				int base = slot[i] * MAX_NEIGHBOURS;
				for (int k = 0; k < neighbourCount[i]; k++) {
					int pk = base + k;
					float g = pairGrad[pk];
					if (g == 0) {
						continue;
					}
					int j = neighbours[pk];
					float rx = pairX[pk], ry = pairY[pk], rz = pairZ[pk];
					float pull = pairPull[pk];
					if (pull > 0) {
						cx -= rx * pull;
						cy -= ry * pull;
						cz -= rz * pull;
						pulled++;
					}
					float f = (lambda[i] + lambda[j] - pairClump[pk]) * g;
					sx += f * rx;
					sy += f * ry;
					sz += f * rz;
				}
				for (int w = 0; w < wallCount[i]; w++) {
					float d = wallDist[i * 6 + w];
					int s = Math.min(WALL_STEPS, Math.round(d / H * WALL_STEPS));
					float push = -lambda[i] * WALL_PUSH[s] / restDensity;
					int face = wallFace[i * 6 + w];
					float sign = (face & 1) == 0 ? 1 : -1;
					switch (face >> 1) {
						case 0 -> sx += push * sign;
						case 1 -> sy += push * sign;
						default -> sz += push * sign;
					}
				}
				if (pulled > 0) {
					sx += cx / pulled * COHESION;
					sy += cy / pulled * COHESION;
					sz += cz / pulled * COHESION;
				}
				dx[i] = sx;
				dy[i] = sy;
				dz[i] = sz;
			});
			parallel(i -> {
				if (asleep[i]) {
					return;
				}
				float ox = px[i], oy = py[i], oz = pz[i];
				px[i] += clampMove(dx[i]);
				py[i] += clampMove(dy[i]);
				pz[i] += clampMove(dz[i]);
				collide(i, world, ox, oy, oz);
			});
		}
		parallel(i -> {
			if (asleep[i]) {
				return;
			}
			float mx = (px[i] - x[i]) / dt, my = (py[i] - y[i]) / dt, mz = (pz[i] - z[i]) / dt;
			float moved = (float) Math.sqrt(mx * mx + my * my + mz * mz);
			float allowed = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]) + MAX_KICK;
			float scale = moved > allowed ? allowed / moved : 1;
			float speed = moved * scale;
			float damp = Math.max(0, 1 - (speed < STILL ? STILL_DAMPING * (1 - speed / STILL) + DAMPING : DAMPING) * dt);
			vx[i] = mx * scale * damp;
			vy[i] = my * scale * damp;
			vz[i] = mz * scale * damp;
		});
		viscosity();
		float drag = Math.max(0, 1 - FLOOR_DRAG * dt);
		parallel(i -> {
			if (grounded[i]) {
				vx[i] *= drag;
				vz[i] *= drag;
			}
			x[i] = px[i];
			y[i] = py[i];
			z[i] = pz[i];
		});
	}
	private static float clampMove(float d) {
		return Math.max(-MAX_MOVE, Math.min(MAX_MOVE, d));
	}
	private void collide(int i, World world, float fx, float fy, float fz) {
		float tx = px[i], ty = py[i], tz = pz[i];
		float cx = fx, cy = fy, cz = fz;
		cy = sweep(world, cx, cy, cz, ty, 1);
		cx = sweep(world, cx, cy, cz, tx, 0);
		cz = sweep(world, cx, cy, cz, tz, 2);
		if (cy > ty + 1e-5f) {
			grounded[i] = true;
		}
		px[i] = cx;
		py[i] = cy;
		pz[i] = cz;
	}
	private static float sweep(World world, float x, float y, float z, float to, int axis) {
		float from = axis == 0 ? x : axis == 1 ? y : z;
		if (to == from) {
			return to;
		}
		float dir = Math.signum(to - from);
		float edge = to + dir * MARGIN;
		float result = to;
		int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
		int ex = (int) Math.floor(axis == 0 ? edge : x);
		int ey = (int) Math.floor(axis == 1 ? edge : y);
		int ez = (int) Math.floor(axis == 2 ? edge : z);
		boolean moves = ex != cx || ey != cy || ez != cz;
		for (int pass = 0; pass < 2; pass++) {
			if (!moves && (pass == 1 || pass == 3)) {
				continue;
			}
			int bx = pass % 2 == 0 ? cx : ex;
			int by = (pass % 2 == 0 ? cy : ey) - (pass >= 2 ? 1 : 0);
			int bz = pass % 2 == 0 ? cz : ez;
			if (pass == 2 && moves && ex == cx && ey == cy - 1 && ez == cz) {
				continue;
			}
			float[] boxes = world.boxes(bx, by, bz);
			if (boxes == null) {
				continue;
			}
			for (int k = 0; k + 5 < boxes.length; k += 6) {
				float x0 = bx + boxes[k] - MARGIN, y0 = by + boxes[k + 1] - MARGIN, z0 = bz + boxes[k + 2] - MARGIN;
				float x1 = bx + boxes[k + 3] + MARGIN, y1 = by + boxes[k + 4] + MARGIN, z1 = bz + boxes[k + 5] + MARGIN;
				if (axis != 0 && (x <= x0 || x >= x1) || axis != 1 && (y <= y0 || y >= y1) || axis != 2 && (z <= z0 || z >= z1)) {
					continue;
				}
				float lo = axis == 0 ? x0 : axis == 1 ? y0 : z0;
				float hi = axis == 0 ? x1 : axis == 1 ? y1 : z1;
				if (from > lo && from < hi) {
					if (axis == 1) {
						result = Math.max(result, hi);
					} else {
						result = from;
					}
					continue;
				}
				if (dir > 0 && from <= lo && result > lo) {
					result = lo;
				} else if (dir < 0 && from >= hi && result < hi) {
					result = hi;
				}
			}
		}
		return result;
	}
	private void push(int i, List<double[]> boxes, float dt) {
		for (double[] b : boxes) {
			if (px[i] < b[0] - MARGIN || px[i] > b[3] + MARGIN || py[i] < b[1] || py[i] > b[4] || pz[i] < b[2] - MARGIN
					|| pz[i] > b[5] + MARGIN) {
				continue;
			}

			double left = px[i] - (b[0] - MARGIN), right = b[3] + MARGIN - px[i];
			double back = pz[i] - (b[2] - MARGIN), front = b[5] + MARGIN - pz[i];
			double least = Math.min(Math.min(left, right), Math.min(back, front));
			if (least == left) {
				px[i] = (float) (b[0] - MARGIN);
			} else if (least == right) {
				px[i] = (float) (b[3] + MARGIN);
			} else if (least == back) {
				pz[i] = (float) (b[2] - MARGIN);
			} else {
				pz[i] = (float) (b[5] + MARGIN);
			}
			px[i] += (float) (b[6] * dt);
			pz[i] += (float) (b[8] * dt);
		}
	}
	private void findWalls(int i, World world) {
		int bx = (int) Math.floor(px[i]), by = (int) Math.floor(py[i]), bz = (int) Math.floor(pz[i]);
		float fx = px[i] - bx, fy = py[i] - by, fz = pz[i] - bz;
		int n = 0;
		int base = i * 6;
		if (fx < H && world.solid(bx - 1, by, bz)) {
			wallFace[base + n] = 0;
			wallDist[base + n++] = fx;
		}
		if (1 - fx < H && world.solid(bx + 1, by, bz)) {
			wallFace[base + n] = 1;
			wallDist[base + n++] = 1 - fx;
		}
		if (fy < H && world.solid(bx, by - 1, bz)) {
			wallFace[base + n] = 2;
			wallDist[base + n++] = fy;
		}
		if (1 - fy < H && world.solid(bx, by + 1, bz)) {
			wallFace[base + n] = 3;
			wallDist[base + n++] = 1 - fy;
		}
		if (fz < H && world.solid(bx, by, bz - 1)) {
			wallFace[base + n] = 4;
			wallDist[base + n++] = fz;
		}
		if (1 - fz < H && world.solid(bx, by, bz + 1)) {
			wallFace[base + n] = 5;
			wallDist[base + n++] = 1 - fz;
		}
		wallCount[i] = n;
	}
	private void findNeighbours() {
		int awake = 0;
		for (int i = 0; i < count; i++) {
			slot[i] = asleep[i] ? -1 : awake++;
		}
		int pairs = awake * MAX_NEIGHBOURS;
		if (neighbours.length < pairs) {
			neighbours = new int[Math.max(pairs, neighbours.length * 3 / 2)];
		}
		if (pairX.length < pairs) {
			int n = Math.max(pairs, pairX.length * 3 / 2);
			pairX = new float[n];
			pairY = new float[n];
			pairZ = new float[n];
			pairGrad = new float[n];
			pairClump = new float[n];
			pairPull = new float[n];
		}
		java.util.Arrays.fill(head, -1);
		for (int i = 0; i < count; i++) {
			int bucket = bucket(cell(px[i]), cell(py[i]), cell(pz[i]));
			next[i] = head[bucket];
			head[bucket] = i;
		}
		parallel(i -> {
			if (slot[i] < 0) {
				neighbourCount[i] = 0;
				return;
			}
			int cx = cell(px[i]), cy = cell(py[i]), cz = cell(pz[i]);
			int base = slot[i] * MAX_NEIGHBOURS;
			int n = 0;
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					for (int c = -1; c <= 1; c++) {
						for (int j = head[bucket(cx + a, cy + b, cz + c)]; j >= 0 && n < MAX_NEIGHBOURS; j = next[j]) {
							if (j == i) {
								continue;
							}
							float rx = px[i] - px[j], ry = py[i] - py[j], rz = pz[i] - pz[j];
							if (rx * rx + ry * ry + rz * rz < H2) {
								neighbours[base + n++] = j;
							}
						}
					}
				}
			}
			neighbourCount[i] = n;
		});
	}
	private static int cell(float v) {
		return (int) Math.floor(v / H);
	}
	private static int bucket(int x, int y, int z) {
		int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
		return h & (TABLE - 1);
	}
	private void viscosity() {
		parallel(i -> {
			if (asleep[i]) {
				dx[i] = dy[i] = dz[i] = 0;
				return;
			}
			float sx = 0, sy = 0, sz = 0;
			int base = slot[i] * MAX_NEIGHBOURS;
			for (int k = 0; k < neighbourCount[i]; k++) {
				int j = neighbours[base + k];
				float rx = px[i] - px[j], ry = py[i] - py[j], rz = pz[i] - pz[j];
				float r2 = rx * rx + ry * ry + rz * rz;
				if (r2 >= H2) {
					continue;
				}
				float d = H2 - r2;
				float w = POLY6 * d * d * d / restDensity;
				sx += (vx[j] - vx[i]) * w;
				sy += (vy[j] - vy[i]) * w;
				sz += (vz[j] - vz[i]) * w;
			}
			dx[i] = sx;
			dy[i] = sy;
			dz[i] = sz;
		});
		parallel(i -> {
			vx[i] += VISCOSITY * dx[i] * 10;
			vy[i] += VISCOSITY * dy[i] * 10;
			vz[i] += VISCOSITY * dz[i] * 10;
		});
	}
	private void absorb(World world) {
		int w = 0;
		for (int i = 0; i < count; i++) {
			boolean gone = !asleep[i] && (y[i] < -128 || world.water((int) Math.floor(x[i]), (int) Math.floor(y[i]), (int) Math.floor(z[i])));
			if (!gone) {
				copy(i, w++);
			} else {
				free(i);
			}
		}
		count = w;
	}
	private void copy(int from, int to) {
		if (from == to) {
			return;
		}
		x[to] = x[from];
		y[to] = y[from];
		z[to] = z[from];
		lx[to] = lx[from];
		ly[to] = ly[from];
		lz[to] = lz[from];
		vx[to] = vx[from];
		vy[to] = vy[from];
		vz[to] = vz[from];
		group[to] = group[from];
		id[to] = id[from];
		grounded[to] = grounded[from];
		asleep[to] = asleep[from];
		stillFor[to] = stillFor[from];
		anchorX[to] = anchorX[from];
		anchorY[to] = anchorY[from];
		anchorZ[to] = anchorZ[from];
	}
	private void rest(float dt) {
		parallel(i -> {
			if (asleep[i]) {
				return;
			}
			float ax = x[i] - anchorX[i], ay = y[i] - anchorY[i], az = z[i] - anchorZ[i];
			if (ax * ax + ay * ay + az * az < REST_REACH * REST_REACH) {
				stillFor[i] += dt;
			} else {
				anchorX[i] = x[i];
				anchorY[i] = y[i];
				anchorZ[i] = z[i];
				stillFor[i] = 0;
			}
			if (stillFor[i] >= REST_TIME) {
				asleep[i] = true;
				vx[i] = vy[i] = vz[i] = 0;
			}
		});
		java.util.Set<Integer> present = new java.util.HashSet<>();
		int last = Integer.MIN_VALUE;
		for (int i = 0; i < count; i++) {
			if (group[i] != last) {
				last = group[i];
				present.add(last);
			}
		}
		for (var it = groups.keySet().iterator(); it.hasNext(); ) {
			int g = it.next();
			if (!present.contains(g)) {
				settled.add(g);
				it.remove();
			}
		}
	}
}
