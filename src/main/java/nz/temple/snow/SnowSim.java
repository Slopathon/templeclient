package nz.temple.snow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SnowSim {
	public static final int CELLS = 4;
	public static final int CHUNK = 16;
	public static final int SIDE = CHUNK * CELLS;
	public static final float CELL = 1f / CELLS;
	static final float OPEN_CAP = 0.8f;
	static final float MAX_CAP = 2.0f;
	public static final float MAX_DEPTH = MAX_CAP;
	static final float BASE_RATE = 0.35f / 720f;
	public static final double RADIUS = 28;
	static final int MAX_CHUNKS = 1400;
	static final int MAX_PRINTS = 1600;
	private static final double NEAR = 24;
	private static final double NEAR_INTERVAL = 0.2;
	private static final float REPOSE = 0.6f * CELL;
	static final int MELT_LIGHT = 11;
	private static final float[] SHOULDER = {0.45f, 0.78f, 0.94f};
	public interface Ground {
		float surface(int x, int z);
		boolean underFoliage(int x, int z);
		int heat(int x, int z);
		default boolean loaded(int cx, int cz) {
			return true;
		}
	}

	public record Print(double x, double z, double yaw, int side, float size, double time, float depth) {
	}
	public static final class Chunk {
		final int cx, cz;
		final float[] base = new float[CHUNK * CHUNK];
		final byte[] heat = new byte[CHUNK * CHUNK];
		final boolean[] foliage = new boolean[CHUNK * CHUNK];

		final float[] depth = new float[SIDE * SIDE];
		final short[] cap = new short[SIDE * SIDE];
		final short[] gain = new short[SIDE * SIDE];
		boolean derived;
		boolean validated;
		int version;
		boolean urgent;
		double lastActive;
		double steppedTo = Double.NaN;
		boolean queued;
		Chunk(int cx, int cz) {
			this.cx = cx;
			this.cz = cz;
			java.util.Arrays.fill(base, Float.NaN);
		}
		public int cx() {
			return cx;
		}
		public int cz() {
			return cz;
		}
		public int version() {
			return version;
		}
		public boolean urgent() {
			return urgent;
		}
		public void clearUrgent() {
			urgent = false;
		}

		public double steppedTo() {
			return steppedTo;
		}

		float capAt(int i) {
			return Float.float16ToFloat(cap[i]);
		}

		float gainAt(int i) {
			return Float.float16ToFloat(gain[i]);
		}

		public float base(int lx, int lz) {
			return base[lz * CHUNK + lx];
		}
		public float depth(int x, int z) {
			return depth[z * SIDE + x];
		}
	}
	private final Ground ground;
	private final Map<Long, Chunk> chunks = new HashMap<>();
	private final Deque<Print> prints = new ArrayDeque<>();
	private final Deque<Chunk> queue = new ArrayDeque<>();
	private double clock;
	private float rate = 1;
	private double radius = RADIUS;
	private double px, pz;
	private float windX, windZ, windStrength;
	public SnowSim(Ground ground) {
		this.ground = ground;
	}

	public void clear() {
		chunks.clear();
		prints.clear();
		queue.clear();
	}

	public Collection<Chunk> chunks() {
		return chunks.values();
	}
	public Chunk chunk(int cx, int cz) {
		return chunks.get(key(cx, cz));
	}
	public double clock() {
		return clock;
	}
	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}
	public void step(double dt, double px, double pz, float rate, float windX, float windZ) {
		step(dt, px, pz, RADIUS, rate, windX, windZ, Long.MAX_VALUE);
	}
	public void step(double dt, double px, double pz, double radius, float rate, float windX, float windZ, long budgetNanos) {
		long start = System.nanoTime();
		if (dt > 0) {
			clock += dt;
		}
		this.rate = rate;
		this.radius = radius;
		this.px = px;
		this.pz = pz;
		float strength = (float) Math.sqrt(windX * windX + windZ * windZ);
		float nx = strength < 1e-3f ? 0 : windX / strength, nz = strength < 1e-3f ? 0 : windZ / strength;
		float s = Math.min(1, strength);
		if (Math.abs(nx - this.windX) + Math.abs(nz - this.windZ) > 0.05f || Math.abs(s - windStrength) > 0.05f) {
			this.windX = nx;
			this.windZ = nz;
			windStrength = s;
			for (Chunk c : chunks.values()) {
				c.derived = false;
			}
		}
		int minCx = Math.floorDiv((int) Math.floor(px - radius), CHUNK), maxCx = Math.floorDiv((int) Math.floor(px + radius), CHUNK);
		int minCz = Math.floorDiv((int) Math.floor(pz - radius), CHUNK), maxCz = Math.floorDiv((int) Math.floor(pz + radius), CHUNK);
		List<Chunk> near = new ArrayList<>();
		List<Double> nearDistance = new ArrayList<>();
		for (int cz = minCz; cz <= maxCz; cz++) {
			for (int cx = minCx; cx <= maxCx; cx++) {
				double dx = Math.max(0, Math.max(cx * CHUNK - px, px - (cx + 1) * CHUNK));
				double dz = Math.max(0, Math.max(cz * CHUNK - pz, pz - (cz + 1) * CHUNK));
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius) {
					continue;
				}
				Chunk c = chunks.get(key(cx, cz));
				if (c == null) {
					c = new Chunk(cx, cz);
					chunks.put(key(cx, cz), c);
					c.queued = true;
					queue.addFirst(c);
				}
				c.lastActive = clock;
				if (d <= NEAR) {
					near.add(c);
					nearDistance.add(d);
				}
			}
		}
		Integer[] order = new Integer[near.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Double.compare(nearDistance.get(a), nearDistance.get(b)));
		for (int i : order) {
			Chunk c = near.get(i);
			if (System.nanoTime() - start >= budgetNanos) {
				break;
			}
			if (Double.isNaN(c.steppedTo) || clock - c.steppedTo >= NEAR_INTERVAL) {
				stepChunk(c);
			}
		}
		int tries = queue.size();
		while (tries-- > 0 && System.nanoTime() - start < budgetNanos) {
			Chunk c = queue.pollFirst();
			if (c == null) {
				break;
			}
			if (chunks.get(key(c.cx, c.cz)) != c || c.lastActive < clock) {
				c.queued = false;
				continue;
			}
			stepChunk(c);
			queue.addLast(c);
		}
		for (Chunk c : near) {
			if (!c.queued) {
				c.queued = true;
				queue.addLast(c);
			}
		}
		trimPrints();
		if (chunks.size() > MAX_CHUNKS) {
			evict();
		}
	}
	private void stepChunk(Chunk c) {
		if (!ground.loaded(c.cx, c.cz)) {
			return;
		}
		double dt = Double.isNaN(c.steppedTo) ? 0 : Math.min(30, clock - c.steppedTo);
		c.steppedTo = clock;
		validate(c);
		if (!c.derived) {
			derive(c);
		}
		if (dt > 0) {
			accumulate(c, dt);
			settle(c, dt);
		}
	}
	private void validate(Chunk c) {
		boolean changed = false;
		boolean first = !c.validated;
		c.validated = true;
		for (int lz = 0; lz < CHUNK; lz++) {
			for (int lx = 0; lx < CHUNK; lx++) {
				int x = c.cx * CHUNK + lx, z = c.cz * CHUNK + lz;
				int i = lz * CHUNK + lx;
				float surface = ground.surface(x, z);
				boolean under = !Float.isNaN(surface) && ground.underFoliage(x, z);
				c.heat[i] = (byte) (Float.isNaN(surface) ? 0 : Math.max(0, Math.min(15, ground.heat(x, z))));
				float old = c.base[i];
				boolean same = Float.isNaN(old) ? Float.isNaN(surface) : !Float.isNaN(surface) && Math.abs(old - surface) < 1e-3f;
				if (!same || c.foliage[i] != under) {
					c.base[i] = surface;
					c.foliage[i] = under;
					for (int k = 0; k < CELLS * CELLS; k++) {
						c.depth[(lz * CELLS + k / CELLS) * SIDE + lx * CELLS + k % CELLS] = 0;
					}
					if (!first && !Float.isNaN(old) && (Float.isNaN(surface) || surface < old - 1e-3f)) {
						slump(x, z, old, surface);
					}
					changed = true;
				}
			}
		}
		if (changed) {
			c.urgent = true;
			c.version++;
			c.derived = false;
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					Chunk n = chunks.get(key(c.cx + dx, c.cz + dz));
					if (n != null) {
						n.derived = false;
						n.urgent = true;
						n.version++;
					}
				}
			}
		}
	}
	private void slump(int x, int z, float was, float now) {
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] s : sides) {
			int nx = x + s[0], nz = z + s[1];
			Chunk n = chunkOfBlock(nx, nz);
			if (n == null) {
				continue;
			}
			float nb = n.base[Math.floorMod(nz, CHUNK) * CHUNK + Math.floorMod(nx, CHUNK)];
			if (Float.isNaN(nb) || Math.abs(nb - was) > 1e-3f) {
				continue;
			}
			for (int along = 0; along < CELLS; along++) {
				for (int in = 0; in < SHOULDER.length; in++) {
					int gx, gz;
					if (s[0] != 0) {
						gx = s[0] > 0 ? nx * CELLS + in : nx * CELLS + CELLS - 1 - in;
						gz = z * CELLS + along;
					} else {
						gz = s[1] > 0 ? nz * CELLS + in : nz * CELLS + CELLS - 1 - in;
						gx = x * CELLS + along;
					}
					Chunk cc = chunkOfCell(gx, gz);
					if (cc == null) {
						continue;
					}
					int ci = Math.floorMod(gz, SIDE) * SIDE + Math.floorMod(gx, SIDE);
					float d = cc.depth[ci];
					float keep = d * SHOULDER[in];
					cc.depth[ci] = keep;
					float lost = d - keep;
					if (lost <= 0 || Float.isNaN(now) || was - now > 2.5f) {
						continue;
					}
					int hx, hz;
					if (s[0] != 0) {
						hx = s[0] > 0 ? x * CELLS + CELLS - 1 : x * CELLS;
						hz = gz;
					} else {
						hz = s[1] > 0 ? z * CELLS + CELLS - 1 : z * CELLS;
						hx = gx;
					}
					Chunk hc = chunkOfCell(hx, hz);
					if (hc != null) {
						int hi = Math.floorMod(hz, SIDE) * SIDE + Math.floorMod(hx, SIDE);
						hc.depth[hi] = Math.min(MAX_CAP, hc.depth[hi] + lost * 0.6f);
						int hx2 = hx - s[0], hz2 = hz - s[1];
						Chunk hc2 = chunkOfCell(hx2, hz2);
						if (hc2 != null && Math.floorDiv(hx2, CELLS) == x && Math.floorDiv(hz2, CELLS) == z) {
							int hi2 = Math.floorMod(hz2, SIDE) * SIDE + Math.floorMod(hx2, SIDE);
							hc2.depth[hi2] = Math.min(MAX_CAP, hc2.depth[hi2] + lost * 0.25f);
						}
					}
				}
			}
		}
	}
	private Chunk chunkOfBlock(int x, int z) {
		return chunks.get(key(Math.floorDiv(x, CHUNK), Math.floorDiv(z, CHUNK)));
	}
	private Chunk chunkOfCell(int gx, int gz) {
		return chunks.get(key(Math.floorDiv(gx, SIDE), Math.floorDiv(gz, SIDE)));
	}
	private static final int MARGIN = 4;
	private static final int GRID = CHUNK + 2 * MARGIN;
	private final float[] grid = new float[GRID * GRID];
	private final boolean[] gridFoliage = new boolean[GRID * GRID];
	private int gridX = Integer.MIN_VALUE, gridZ;
	private void fillGrid(Chunk c, Map<Long, float[]> window) {
		gridX = Integer.MIN_VALUE;
		int x0 = c.cx * CHUNK - MARGIN, z0 = c.cz * CHUNK - MARGIN;
		for (int z = 0; z < GRID; z++) {
			for (int x = 0; x < GRID; x++) {
				grid[z * GRID + x] = surfaceAt(x0 + x, z0 + z, window);
				gridFoliage[z * GRID + x] = underFoliageSlow(x0 + x, z0 + z);
			}
		}
		gridX = x0;
		gridZ = z0;
	}
	private float surfaceAt(int x, int z, Map<Long, float[]> window) {
		int gx = x - gridX, gz = z - gridZ;
		if (gridX != Integer.MIN_VALUE && gx >= 0 && gz >= 0 && gx < GRID && gz < GRID) {
			return grid[gz * GRID + gx];
		}
		Chunk c = chunkOfBlock(x, z);
		if (c != null && c.validated) {
			return c.base[Math.floorMod(z, CHUNK) * CHUNK + Math.floorMod(x, CHUNK)];
		}
		long k = key(x, z);
		float[] v = window.get(k);
		if (v == null) {
			v = new float[] {ground.surface(x, z)};
			window.put(k, v);
		}
		return v[0];
	}
	private void derive(Chunk c) {
		Map<Long, float[]> window = new HashMap<>();
		fillGrid(c, window);
		for (int z = 0; z < SIDE; z++) {
			for (int x = 0; x < SIDE; x++) {
				int i = z * SIDE + x;
				int lx = x / CELLS, lz = z / CELLS;
				float base = c.base[lz * CHUNK + lx];
				if (Float.isNaN(base)) {
					c.cap[i] = 0;
					c.gain[i] = 0;
					continue;
				}
				double wx = c.cx * CHUNK + (x + 0.5) * CELL, wz = c.cz * CHUNK + (z + 0.5) * CELL;
				float exposure = 1;
				if (c.foliage[lz * CHUNK + lx]) {
					exposure = canopyEdge(wx, wz, window);
				}
				float cap = OPEN_CAP, gain = 1;
				if (windStrength > 0.01f) {
					float[] lee = obstacle(wx, wz, -windX, -windZ, base, 3.0, window);
					if (lee != null) {
						float h = Math.min(lee[0], MAX_CAP);
						float reach = 0.75f + 2.5f * h;
						float k = smooth(1 - lee[1] / reach) * windStrength;
						cap = Math.max(cap, h * k + OPEN_CAP * (1 - k));
						gain += 2.2f * k;
					}
					float[] bank = obstacle(wx, wz, windX, windZ, base, 1.5, window);
					if (bank != null) {
						float h = Math.min(bank[0], 0.7f);
						float k = smooth(1 - bank[1] / 1.5f) * windStrength;
						cap = Math.max(cap, h * 0.8f * k + OPEN_CAP * (1 - k));
						gain += 1.2f * k;
					}
					float down = surfaceAt((int) Math.floor(wx + windX * 0.45), (int) Math.floor(wz + windZ * 0.45), window);
					if (Float.isNaN(down) || down < base - 0.45f) {
						cap *= 1 - 0.4f * windStrength;
						gain *= 1 - 0.4f * windStrength;
					}
				}
				cap *= shoulder(wx, wz, base, window);
				c.cap[i] = Float.floatToFloat16(cap * exposure);
				c.gain[i] = Float.floatToFloat16(gain * exposure);
			}
		}
		gridX = Integer.MIN_VALUE;
		c.derived = true;
	}
	private float shoulder(double wx, double wz, float base, Map<Long, float[]> window) {
		float keep = 1;
		int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) {
			for (int in = 0; in < SHOULDER.length; in++) {
				double ex = wx + d[0] * (in + 0.5) * CELL, ez = wz + d[1] * (in + 0.5) * CELL;
				int x = (int) Math.floor(ex + d[0] * 1e-4), z = (int) Math.floor(ez + d[1] * 1e-4);
				if (x == bx && z == bz) {
					continue;
				}
				float s = surfaceAt(x, z, window);
				if (Float.isNaN(s) || s < base - 0.1f) {
					keep = Math.min(keep, SHOULDER[in]);
				}
				break;
			}
		}
		return keep;
	}
	private float canopyEdge(double wx, double wz, Map<Long, float[]> window) {
		double nearest = 9;
		int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				int x = bx + dx, z = bz + dz;
				if (Float.isNaN(surfaceAt(x, z, window)) || underFoliage(x, z)) {
					continue;
				}
				double ex = Math.max(Math.max(x - wx, wx - (x + 1)), 0);
				double ez = Math.max(Math.max(z - wz, wz - (z + 1)), 0);
				nearest = Math.min(nearest, Math.sqrt(ex * ex + ez * ez));
			}
		}
		return 0.85f * smooth((float) (1 - nearest / 1.5));
	}
	private boolean underFoliage(int x, int z) {
		int gx = x - gridX, gz = z - gridZ;
		if (gridX != Integer.MIN_VALUE && gx >= 0 && gz >= 0 && gx < GRID && gz < GRID) {
			return gridFoliage[gz * GRID + gx];
		}
		return underFoliageSlow(x, z);
	}
	private boolean underFoliageSlow(int x, int z) {
		Chunk c = chunkOfBlock(x, z);
		if (c != null && c.validated) {
			return c.foliage[Math.floorMod(z, CHUNK) * CHUNK + Math.floorMod(x, CHUNK)];
		}
		return ground.underFoliage(x, z);
	}
	private float[] obstacle(double wx, double wz, float dx, float dz, float base, double range, Map<Long, float[]> window) {
		int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
		for (double d = CELL; d <= range; d += CELL) {
			int x = (int) Math.floor(wx + dx * d), z = (int) Math.floor(wz + dz * d);
			if (x == bx && z == bz) {
				continue;
			}
			float s = surfaceAt(x, z, window);
			if (!Float.isNaN(s) && s - base > 0.2f) {
				return new float[] {s - base, (float) d};
			}
		}
		return null;
	}
	public float growth(Chunk c, int i) {
		int column = (i / SIDE / CELLS) * CHUNK + (i % SIDE) / CELLS;
		if (Float.isNaN(c.base[column])) {
			return 0;
		}
		int heat = c.heat[column];
		float d = c.depth[i];
		if (heat >= MELT_LIGHT) {
			return d > 0 ? -0.004f * (heat - MELT_LIGHT + 1) : 0;
		}
		float cap = c.capAt(i);
		if (d >= cap) {
			return 0;
		}
		double wx = c.cx * CHUNK + (i % SIDE + 0.5) * CELL - px, wz = c.cz * CHUNK + (i / SIDE + 0.5) * CELL - pz;
		float reach = smooth((float) ((radius - Math.sqrt(wx * wx + wz * wz)) / 8));
		float warm = heat >= MELT_LIGHT - 2 ? (MELT_LIGHT - heat) / 3f : 1;
		float room = 1 - d / cap;
		return BASE_RATE * rate * c.gainAt(i) * reach * warm * (0.35f + 0.65f * room);
	}
	public void gather(int cx, int cz, int border, int size, float[] base, float[] depth, float[] growth, float[] room) {
		int gx0 = cx * SIDE - border, gz0 = cz * SIDE - border;
		Chunk[] around = new Chunk[9];
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				around[(dz + 1) * 3 + dx + 1] = chunks.get(key(cx + dx, cz + dz));
			}
		}
		for (int z = 0; z < size; z++) {
			int gz = gz0 + z;
			int oz = Math.floorDiv(gz, SIDE) - cz;
			int lz = Math.floorMod(gz, SIDE);
			for (int x = 0; x < size; x++) {
				int gx = gx0 + x;
				int ox = Math.floorDiv(gx, SIDE) - cx;
				int o = z * size + x;
				Chunk c = ox < -1 || ox > 1 || oz < -1 || oz > 1 ? null : around[(oz + 1) * 3 + ox + 1];
				if (c == null) {
					base[o] = Float.NaN;
					depth[o] = growth[o] = room[o] = 0;
					continue;
				}
				int lx = Math.floorMod(gx, SIDE);
				int i = lz * SIDE + lx;
				base[o] = c.base[(lz / CELLS) * CHUNK + lx / CELLS];
				if (Float.isNaN(base[o])) {
					depth[o] = growth[o] = room[o] = 0;
					continue;
				}
				float d = c.depth[i];
				float g = growth(c, i);
				float r = room(c, i);
				float extra = Double.isNaN(c.steppedTo) ? 0 : Math.min(r, (float) (g * (clock - c.steppedTo)));
				depth[o] = Math.max(0, d + extra);
				growth[o] = g;
				room[o] = Math.max(0, r - extra);
			}
		}
	}
	public float growthAtCell(int gx, int gz) {
		Chunk c = chunkOfCell(gx, gz);
		return c == null ? 0 : growth(c, Math.floorMod(gz, SIDE) * SIDE + Math.floorMod(gx, SIDE));
	}
	public float roomAtCell(int gx, int gz) {
		Chunk c = chunkOfCell(gx, gz);
		return c == null ? 0 : room(c, Math.floorMod(gz, SIDE) * SIDE + Math.floorMod(gx, SIDE));
	}
	public float depthNow(int gx, int gz) {
		Chunk c = chunkOfCell(gx, gz);
		if (c == null) {
			return 0;
		}
		int i = Math.floorMod(gz, SIDE) * SIDE + Math.floorMod(gx, SIDE);
		float d = c.depth[i];
		if (Double.isNaN(c.steppedTo)) {
			return d;
		}
		float g = growth(c, i);
		float extra = (float) (g * (clock - c.steppedTo));
		return Math.max(0, d + Math.min(extra, room(c, i)));
	}
	public float room(Chunk c, int i) {
		return Math.max(0, c.capAt(i) - c.depth[i]);
	}
	private void accumulate(Chunk c, double dt) {
		for (int i = 0; i < SIDE * SIDE; i++) {
			float g = growth(c, i);
			if (g == 0) {
				continue;
			}
			float d = c.depth[i];
			c.depth[i] = g > 0 ? Math.min(Math.max(d, c.capAt(i)), d + (float) (g * dt)) : Math.max(0, d + (float) (g * dt));
		}
	}
	private void settle(Chunk c, double dt) {
		Chunk east = chunks.get(key(c.cx + 1, c.cz)), south = chunks.get(key(c.cx, c.cz + 1));
		float blend = (float) Math.min(0.5, dt * 0.6);
		for (int z = 0; z < SIDE; z++) {
			for (int x = 0; x < SIDE; x++) {
				int i = z * SIDE + x;
				float b = c.base[(z / CELLS) * CHUNK + x / CELLS];
				if (Float.isNaN(b)) {
					continue;
				}
				if (x + 1 < SIDE) {
					pair(c, i, b, c, i + 1, c.base[(z / CELLS) * CHUNK + (x + 1) / CELLS], blend);
				} else if (east != null) {
					pair(c, i, b, east, z * SIDE, east.base[(z / CELLS) * CHUNK], blend);
				}
				if (z + 1 < SIDE) {
					pair(c, i, b, c, i + SIDE, c.base[((z + 1) / CELLS) * CHUNK + x / CELLS], blend);
				} else if (south != null) {
					pair(c, i, b, south, x, south.base[x / CELLS], blend);
				}
			}
		}
	}
	private static void pair(Chunk a, int ia, float baseA, Chunk b, int ib, float baseB, float blend) {
		if (Float.isNaN(baseB) || Math.abs(baseA - baseB) > 1e-3f) {
			return;
		}
		float da = a.depth[ia], db = b.depth[ib];
		float diff = da - db;
		float move = 0;
		if (Math.abs(diff) > REPOSE) {
			move = (Math.abs(diff) - REPOSE) * 0.5f * Math.signum(diff);
		}
		move += diff * 0.08f * blend;
		if (move > 0) {
			move = Math.min(move, da);
			move = Math.min(move, Math.max(0, b.capAt(ib) * 1.15f - db));
		} else {
			move = Math.max(move, -db);
			move = Math.max(move, -Math.max(0, a.capAt(ia) * 1.15f - da));
		}
		a.depth[ia] = da - move;
		b.depth[ib] = db + move;
	}

	public static final double FOOT = 0.125;
	public boolean stamp(double x, double z, double yaw, int side, float size) {
		int gx = (int) Math.floor(x * CELLS), gz = (int) Math.floor(z * CELLS);
		float before = depthAtCell(gx, gz);
		if (before < 0.015f) {
			return false;
		}
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double half = FOOT * size + CELL * 0.5;
		int r = (int) Math.ceil(half * CELLS) + 1;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				double cx = (gx + dx + 0.5) * CELL - x, cz = (gz + dz + 0.5) * CELL - z;
				double along = cx * fx + cz * fz, across = cx * fz - cz * fx;
				double m = Math.max(Math.abs(along), Math.abs(across)) / half;
				double e = m * m;
				if (e > 1.6) {
					continue;
				}
				float press = e < 1 ? 0.55f : 0.55f + 0.45f * (float) ((e - 1) / 0.6);
				pressCell(gx + dx, gz + dz, press);
			}
		}
		prints.addLast(new Print(x, z, yaw, side, size, clock, before));
		return true;
	}
	private static final float PACKED = 0.08f;
	public boolean plow(double x, double z, double dirX, double dirZ, double radius, double moved) {
		float base = baseAt(x, z);
		if (Float.isNaN(base) || moved <= 0) {
			return false;
		}
		int gx = (int) Math.floor(x * CELLS), gz = (int) Math.floor(z * CELLS);
		double bank = radius + CELL * 2;
		int r = (int) Math.ceil(bank * CELLS) + 1;
		float share = (float) Math.min(0.85, moved / Math.max(CELL, radius * 2) * 0.85);
		float taken = 0;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				double cx = (gx + dx + 0.5) * CELL - x, cz = (gz + dz + 0.5) * CELL - z;
				double dist = Math.sqrt(cx * cx + cz * cz);
				if (dist > radius || !sameGround(gx + dx, gz + dz, base)) {
					continue;
				}
				Chunk c = chunkOfCell(gx + dx, gz + dz);
				int i = Math.floorMod(gz + dz, SIDE) * SIDE + Math.floorMod(gx + dx, SIDE);
				float d = c.depth[i];
				if (d <= PACKED) {
					continue;
				}
				float take = (d - PACKED) * share * (float) (1 - 0.4 * dist / radius);
				c.depth[i] = d - take;
				taken += take;
				c.urgent = true;
				c.version++;
			}
		}
		if (taken <= 1e-4f) {
			return false;
		}
		double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
		double ux = len > 1e-6 ? dirX / len : 0, uz = len > 1e-6 ? dirZ / len : 0;
		float[] weight = new float[(2 * r + 1) * (2 * r + 1)];
		float total = 0;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				double cx = (gx + dx + 0.5) * CELL - x, cz = (gz + dz + 0.5) * CELL - z;
				double dist = Math.sqrt(cx * cx + cz * cz);
				if (dist <= radius || dist > bank || !sameGround(gx + dx, gz + dz, base)) {
					continue;
				}
				double along = (cx * ux + cz * uz) / dist;
				if (along < -0.35) {
					continue;
				}
				float w = (float) ((1 - Math.abs(along) * 0.45) * (1 - (dist - radius) / (bank - radius) * 0.6));
				weight[(dz + r) * (2 * r + 1) + dx + r] = w;
				total += w;
			}
		}
		if (total <= 0) {
			return true;
		}
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				float w = weight[(dz + r) * (2 * r + 1) + dx + r];
				if (w <= 0) {
					continue;
				}
				Chunk c = chunkOfCell(gx + dx, gz + dz);
				int i = Math.floorMod(gz + dz, SIDE) * SIDE + Math.floorMod(gx + dx, SIDE);
				c.depth[i] = Math.min(c.depth[i] + taken * w / total, c.capAt(i) * 1.4f + 0.1f);
				c.urgent = true;
				c.version++;
			}
		}
		return true;
	}
	private boolean sameGround(int gx, int gz, float base) {
		float b = cellBase(gx, gz);
		return !Float.isNaN(b) && Math.abs(b - base) < 1e-3f;
	}
	private void pressCell(int gx, int gz, float press) {
		Chunk c = chunkOfCell(gx, gz);
		if (c == null) {
			return;
		}
		int i = Math.floorMod(gz, SIDE) * SIDE + Math.floorMod(gx, SIDE);
		float floor = Math.min(c.depth[i], 0.02f);
		c.depth[i] = Math.max(floor, c.depth[i] * press);
		c.urgent = true;
		c.version++;
	}
	private float depthAtCell(int gx, int gz) {
		Chunk c = chunkOfCell(gx, gz);
		return c == null ? 0 : c.depth[Math.floorMod(gz, SIDE) * SIDE + Math.floorMod(gx, SIDE)];
	}
	public float cellDepth(int gx, int gz) {
		return depthAtCell(gx, gz);
	}
	public float cellBase(int gx, int gz) {
		Chunk c = chunkOfCell(gx, gz);
		if (c == null) {
			return Float.NaN;
		}
		return c.base[(Math.floorMod(gz, SIDE) / CELLS) * CHUNK + Math.floorMod(gx, SIDE) / CELLS];
	}
	public float depthAt(double x, double z) {
		double gx = x * CELLS - 0.5, gz = z * CELLS - 0.5;
		int x0 = (int) Math.floor(gx), z0 = (int) Math.floor(gz);
		float fx = (float) (gx - x0), fz = (float) (gz - z0);
		float a = depthAtCell(x0, z0), b = depthAtCell(x0 + 1, z0), c = depthAtCell(x0, z0 + 1), d = depthAtCell(x0 + 1, z0 + 1);
		return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
	}
	public float baseAt(double x, double z) {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		Chunk c = chunkOfBlock(bx, bz);
		return c == null ? Float.NaN : c.base[Math.floorMod(bz, CHUNK) * CHUNK + Math.floorMod(bx, CHUNK)];
	}
	public Collection<Print> prints() {
		return prints;
	}
	public float printFade(Print p) {
		double fallen = (clock - p.time()) * BASE_RATE * rate;
		double fill = Math.max(0.02, p.depth() * 0.5);
		return (float) Math.max(0, Math.min(1, 1 - fallen / fill));
	}
	private void trimPrints() {
		while (prints.size() > MAX_PRINTS || (!prints.isEmpty() && printFade(prints.peekFirst()) <= 0)) {
			prints.removeFirst();
		}
	}
	private void evict() {
		List<Chunk> all = new ArrayList<>(chunks.values());
		all.sort((a, b) -> Double.compare(a.lastActive, b.lastActive));
		for (int i = 0; i < all.size() - MAX_CHUNKS; i++) {
			chunks.remove(key(all.get(i).cx, all.get(i).cz));
		}
	}
	private static float smooth(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}
}
