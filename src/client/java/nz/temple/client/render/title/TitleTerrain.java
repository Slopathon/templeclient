package nz.temple.client.render.title;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class TitleTerrain {
	interface Sink {
		void quad(float[] xyz, float[] uv, int[] rgb, float nx, float ny, float nz, int kind);
	}
	interface Sprites {
		float[] get(String name);
	}
	static final int KIND_OPAQUE = 255;
	static final int KIND_CUTOUT = 254;
	static final int KIND_WATER = 253;
	static final int KIND_SWAY = 252;
	static final int MIN_X = -84;
	static final int MAX_X = 84;
	static final int MIN_Z = -120;
	static final int MAX_Z = 60;
	static final int STAGE = 4;
	static final int WATER = 3;
	private static final int GRASS = 0x8DB360;
	private static final int OAK_LEAVES = 0x5DA33A;
	private static final int BIRCH_LEAVES = 0x80A755;
	private static final int SPRUCE_LEAVES = 0x4E8A55;
	private final int[][] height;
	private final float stageHalfWidth;
	private final Sprites sprites;
	private final Sink out;
	private final Map<Long, String> extra = new HashMap<>();
	private final Set<Long> leaves = new HashSet<>();
	private TitleTerrain(float stageHalfWidth, Sprites sprites, Sink out) {
		this.stageHalfWidth = stageHalfWidth;
		this.sprites = sprites;
		this.out = out;
		height = new int[MAX_X - MIN_X + 1][MAX_Z - MIN_Z + 1];
	}
	static void build(float stageHalfWidth, Sprites sprites, Sink out) {
		TitleTerrain t = new TitleTerrain(stageHalfWidth, sprites, out);
		t.heights();
		t.trees();
		t.ground();
		t.blocks();
		t.plants();
	}
	private void heights() {
		for (int x = MIN_X; x <= MAX_X; x++) {
			for (int z = MIN_Z; z <= MAX_Z; z++) {
				height[x - MIN_X][z - MIN_Z] = shape(x, z);
			}
		}
	}
	private int shape(int x, int z) {
		double dx = Math.max(0, Math.abs(x) - stageHalfWidth);

		double dz = Math.max(0, Math.max((z - 6) * 0.2, -7 - z));
		double d = Math.sqrt(dx * dx + dz * dz);
		double blend = smooth(Math.min(1, d / 12.0));
		double hills = (fbm(x * 0.045, z * 0.045, 4) - 0.42) * (3 + 8 * smooth(Math.min(1, d / 45.0)));
		double range = 0;
		if (z < -50) {
			double k = Math.min(1, (-50 - z) / 45.0);
			range = k * k * (16 + 14 * fbm(x * 0.028 + 7, z * 0.02, 5)) * (0.55 + 0.45 * ridge(x * 0.035));
		}
		double lx = x + 30;
		double lz = z + 26;
		double lake = Math.max(0, 1 - Math.sqrt(lx * lx * 0.8 + lz * lz) / 13.0);
		double h = STAGE + blend * (hills + range) - lake * lake * 9 * blend;
		return (int) Math.max(1, Math.round(h));
	}
	int heightAt(int x, int z) {
		if (x < MIN_X || x > MAX_X || z < MIN_Z || z > MAX_Z) {
			return 0;
		}
		return height[x - MIN_X][z - MIN_Z];
	}
	private String topOf(int x, int z) {
		int h = heightAt(x, z);
		if (h <= WATER) {
			return h <= WATER - 2 ? "block/gravel" : "block/sand";
		}
		if (h == WATER + 1 && nearWater(x, z)) {
			return "block/sand";
		}
		if (h > 22) {
			return "block/snow";
		}
		if (h > 16 || steep(x, z)) {
			return "block/stone";
		}
		return "block/grass_block_top";
	}
	private boolean nearWater(int x, int z) {
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				if (heightAt(x + ox, z + oz) <= WATER) {
					return true;
				}
			}
		}
		return false;
	}
	private boolean steep(int x, int z) {
		int h = heightAt(x, z);
		return h - heightAt(x + 1, z) >= 3 || h - heightAt(x - 1, z) >= 3 || h - heightAt(x, z + 1) >= 3 || h - heightAt(x, z - 1) >= 3;
	}
	private void ground() {
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int x = MIN_X; x <= MAX_X; x++) {
			for (int z = MIN_Z; z <= MAX_Z; z++) {
				int h = heightAt(x, z);
				String top = topOf(x, z);
				boolean grass = top.equals("block/grass_block_top");
				topFace(x, z, h, top, grass ? GRASS : 0xFFFFFF);
				for (int[] d : dirs) {
					int nx = x + d[0];
					int nz = z + d[1];
					if (nx < MIN_X || nx > MAX_X || nz < MIN_Z || nz > MAX_Z) {
						continue;
					}
					int hn = heightAt(nx, nz);
					for (int y = Math.max(hn, 0); y < h; y++) {
						String tex;
						if (y == h - 1) {
							tex = grass ? "block/grass_block_side" : top.equals("block/snow") ? "block/stone" : top;
						} else if (y >= h - 4 && !top.equals("block/stone") && !top.equals("block/snow")) {
							tex = top.equals("block/sand") || top.equals("block/gravel") ? top : "block/dirt";
						} else {
							tex = "block/stone";
						}
						sideFace(x, y, z, d[0], d[1], tex, y - hn);
					}
				}
				if (h <= WATER) {
					water(x, z);
				}
			}
		}
	}
	private void topFace(int x, int z, int h, String tex, int tint) {
		float[] s = sprites.get(tex);
		float y = h;
		int[] ao = {ao(x, z, h, -1, -1), ao(x, z, h, 1, -1), ao(x, z, h, 1, 1), ao(x, z, h, -1, 1)};
		int turn = tex.equals("block/grass_block_top") ? (int) (hash(x, z, 3) * 4) : 0;
		float[][] uvs = {{s[0], s[1]}, {s[2], s[1]}, {s[2], s[3]}, {s[0], s[3]}};
		float[] uv = new float[8];
		for (int i = 0; i < 4; i++) {
			float[] u = uvs[(i + turn) % 4];
			uv[i * 2] = u[0];
			uv[i * 2 + 1] = u[1];
		}
		out.quad(new float[] {x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1}, uv,
				new int[] {shade(tint, ao[0]), shade(tint, ao[1]), shade(tint, ao[2]), shade(tint, ao[3])}, 0, 1, 0, KIND_OPAQUE);
	}
	private int ao(int x, int z, int h, int sx, int sz) {
		int cx = sx < 0 ? x - 1 : x + 1;
		int cz = sz < 0 ? z - 1 : z + 1;
		boolean a = heightAt(cx, z) > h || solidExtra(cx, h, z);
		boolean b = heightAt(x, cz) > h || solidExtra(x, h, cz);
		boolean c = heightAt(cx, cz) > h || solidExtra(cx, h, cz);
		int level = a && b ? 0 : 3 - ((a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0));
		return level;
	}
	private boolean solidExtra(int x, int y, int z) {
		return extra.containsKey(key(x, y, z)) && !leaves.contains(key(x, y, z));
	}

	private void sideFace(int x, int y, int z, int dx, int dz, String tex, int above) {
		float[] s = sprites.get(tex);
		float x0, z0, x1, z1;
		if (dx == 1) {
			x0 = x + 1;
			x1 = x + 1;
			z0 = z + 1;
			z1 = z;
		} else if (dx == -1) {
			x0 = x;
			x1 = x;
			z0 = z;
			z1 = z + 1;
		} else if (dz == 1) {
			x0 = x;
			x1 = x + 1;
			z0 = z + 1;
			z1 = z + 1;
		} else {
			x0 = x + 1;
			x1 = x;
			z0 = z;
			z1 = z;
		}
		int lower = above == 0 ? 0xB8B8B8 : 0xE6E6E6;
		int upper = 0xFFFFFF;
		int tint = tex.equals("block/grass_block_side") ? 0xFFFFFF : 0xFFFFFF;
		out.quad(new float[] {x0, y, z0, x1, y, z1, x1, y + 1, z1, x0, y + 1, z0},
				new float[] {s[0], s[3], s[2], s[3], s[2], s[1], s[0], s[1]},
				new int[] {mul(tint, lower), mul(tint, lower), mul(tint, upper), mul(tint, upper)}, dx, 0, dz, KIND_OPAQUE);
		if (tex.equals("block/grass_block_side")) {
			float[] o = sprites.get("block/grass_block_side_overlay");
			float ex = dx * 0.002f;
			float ez = dz * 0.002f;
			out.quad(new float[] {x0 + ex, y, z0 + ez, x1 + ex, y, z1 + ez, x1 + ex, y + 1, z1 + ez, x0 + ex, y + 1, z0 + ez},
					new float[] {o[0], o[3], o[2], o[3], o[2], o[1], o[0], o[1]},
					new int[] {mul(GRASS, lower), mul(GRASS, lower), GRASS, GRASS}, dx, 0, dz, KIND_CUTOUT);
		}
	}
	private void water(int x, int z) {
		float[] s = sprites.get("block/water_still");
		float y = WATER + 0.88f;
		out.quad(new float[] {x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1},
				new float[] {s[0], s[1], s[2], s[1], s[2], s[3], s[0], s[3]},
				new int[] {0x3F76E4, 0x3F76E4, 0x3F76E4, 0x3F76E4}, 0, 1, 0, KIND_WATER);
	}
	private void trees() {

		int cell = 6;
		for (int cx = MIN_X; cx < MAX_X; cx += cell) {
			for (int cz = MIN_Z; cz < MAX_Z; cz += cell) {
				int x = cx + (int) (hash(cx, cz, 11) * cell);
				int z = cz + (int) (hash(cx, cz, 12) * cell);
				if (x <= MIN_X + 2 || x >= MAX_X - 2 || z <= MIN_Z + 2 || z >= MAX_Z - 2) {
					continue;
				}
				if (Math.abs(x) < stageHalfWidth + 4 && z > -12) {
					continue;
				}
				int h = heightAt(x, z);
				String top = topOf(x, z);
				boolean ground = top.equals("block/grass_block_top") || top.equals("block/stone") && h > 14 && h < 22;
				if (!ground || h <= WATER + 1) {
					continue;
				}
				double chance = h > 14 ? 0.55 : z < -40 ? 0.6 : 0.42;
				if (hash(x, z, 13) > chance) {
					continue;
				}
				double pick = hash(x, z, 14);
				if (h > 12) {
					spruce(x, h, z);
				} else if (pick < 0.3) {
					birch(x, h, z);
				} else {
					oak(x, h, z);
				}
			}
		}
	}
	private void oak(int x, int base, int z) {
		int trunk = 4 + (int) (hash(x, z, 21) * 2);
		for (int y = base; y < base + trunk; y++) {
			extra.put(key(x, y, z), "oak_log");
		}
		int top = base + trunk;
		blob(x, top - 2, z, 2, "oak_leaves", 22);
		blob(x, top - 1, z, 2, "oak_leaves", 23);
		blob(x, top, z, 1, "oak_leaves", 24);
		blob(x, top + 1, z, 1, "oak_leaves", 25);
	}
	private void birch(int x, int base, int z) {
		int trunk = 5 + (int) (hash(x, z, 31) * 2);
		for (int y = base; y < base + trunk; y++) {
			extra.put(key(x, y, z), "birch_log");
		}
		int top = base + trunk;
		blob(x, top - 2, z, 2, "birch_leaves", 32);
		blob(x, top - 1, z, 2, "birch_leaves", 33);
		blob(x, top, z, 1, "birch_leaves", 34);
		blob(x, top + 1, z, 0, "birch_leaves", 35);
	}
	private void spruce(int x, int base, int z) {
		int trunk = 7 + (int) (hash(x, z, 41) * 3);
		for (int y = base; y < base + trunk; y++) {
			extra.put(key(x, y, z), "spruce_log");
		}
		int top = base + trunk;
		int[] radii = {0, 1, 2, 1, 2, 1};
		for (int i = 0; i < radii.length; i++) {
			blob(x, top - i, z, radii[i], "spruce_leaves", 42 + i);
		}
		blob(x, top + 1, z, 0, "spruce_leaves", 49);
	}
	private void blob(int cx, int y, int cz, int r, String tex, int salt) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (r > 0 && Math.abs(dx) == r && Math.abs(dz) == r && hash(cx + dx, cz + dz, salt) < 0.6) {
					continue;
				}
				long k = key(cx + dx, y, cz + dz);
				if (!extra.containsKey(k)) {
					extra.put(k, tex);
					leaves.add(k);
				}
			}
		}
	}
	private void blocks() {
		int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (Map.Entry<Long, String> e : extra.entrySet()) {
			long k = e.getKey();
			int x = (int) (k >> 40) - 1_000;
			int y = (int) ((k >> 20) & 0xFFFFF) - 1_000;
			int z = (int) (k & 0xFFFFF) - 1_000;
			String name = e.getValue();
			boolean leaf = leaves.contains(k);
			for (int[] f : faces) {
				long nk = key(x + f[0], y + f[1], z + f[2]);
				String neighbour = extra.get(nk);
				if (neighbour != null && !(leaf && leaves.contains(nk))) {
					continue;
				}
				if (f[1] == -1 && heightAt(x, z) >= y) {
					continue;
				}
				if (leaf && neighbour != null && hash(x + f[0], y + f[1] * 7, z + f[2] * 13) < 0.5) {
					continue;
				}
				String tex = leaf ? "block/" + name : f[1] != 0 ? "block/" + name + "_top" : "block/" + name;
				int tint = leaf ? (name.startsWith("birch") ? BIRCH_LEAVES : name.startsWith("spruce") ? SPRUCE_LEAVES : OAK_LEAVES) : 0xFFFFFF;
				cube(x, y, z, f, tex, tint, leaf ? KIND_CUTOUT : KIND_OPAQUE);
			}
		}
	}
	private void cube(int x, int y, int z, int[] f, String tex, int tint, int kind) {
		float[] s = sprites.get(tex);
		float[][] c;
		if (f[0] == 1) {
			c = new float[][] {{x + 1, y, z + 1}, {x + 1, y, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1}};
		} else if (f[0] == -1) {
			c = new float[][] {{x, y, z}, {x, y, z + 1}, {x, y + 1, z + 1}, {x, y + 1, z}};
		} else if (f[2] == 1) {
			c = new float[][] {{x, y, z + 1}, {x + 1, y, z + 1}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}};
		} else if (f[2] == -1) {
			c = new float[][] {{x + 1, y, z}, {x, y, z}, {x, y + 1, z}, {x + 1, y + 1, z}};
		} else if (f[1] == 1) {
			c = new float[][] {{x, y + 1, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}};
		} else {
			c = new float[][] {{x, y, z + 1}, {x + 1, y, z + 1}, {x + 1, y, z}, {x, y, z}};
		}
		int shade = f[1] == -1 ? mul(tint, 0x9A9A9A) : tint;
		out.quad(new float[] {c[0][0], c[0][1], c[0][2], c[1][0], c[1][1], c[1][2], c[2][0], c[2][1], c[2][2], c[3][0], c[3][1], c[3][2]},
				new float[] {s[0], s[3], s[2], s[3], s[2], s[1], s[0], s[1]},
				new int[] {shade, shade, shade, shade}, f[0], f[1], f[2], kind);
	}
	private void plants() {
		String[] flowers = {"block/poppy", "block/dandelion", "block/cornflower", "block/azure_bluet", "block/oxeye_daisy", "block/allium"};
		for (int x = MIN_X + 1; x < MAX_X; x++) {
			for (int z = -70; z < MAX_Z; z++) {
				if (!topOf(x, z).equals("block/grass_block_top")) {
					continue;
				}
				int h = heightAt(x, z);
				if (extra.containsKey(key(x, h, z))) {
					continue;
				}
				boolean stage = Math.abs(x) < stageHalfWidth + 1 && z > -4 && z < 45;
				if (stage && z > -2 && z < 16) {
					continue;
				}
				double r = hash(x, z, 51);
				if (r < (stage ? 0.1 : 0.3)) {
					cross(x, h, z, "block/short_grass", GRASS, 0.9f);
				} else if (r < (stage ? 0.16 : 0.38)) {
					cross(x, h, z, flowers[(int) (hash(x, z, 52) * flowers.length)], 0xFFFFFF, 0.8f);
				}
			}
		}
	}
	private void cross(int x, int y, int z, String tex, int tint, float size) {
		float[] s = sprites.get(tex);
		float jx = (float) (hash(x, z, 61) - 0.5) * 0.4f;
		float jz = (float) (hash(x, z, 62) - 0.5) * 0.4f;
		float cx = x + 0.5f + jx;
		float cz = z + 0.5f + jz;
		float r = 0.45f * size;
		float h = size;
		int lower = mul(tint, 0xB0B0B0);
		for (int i = 0; i < 2; i++) {
			float ax = i == 0 ? -r : -r;
			float az = i == 0 ? -r : r;
			float bx = -ax;
			float bz = -az;
			float[] xyz = {cx + ax, y, cz + az, cx + bx, y, cz + bz, cx + bx, y + h, cz + bz, cx + ax, y + h, cz + az};
			float[] uv = {s[0], s[3], s[2], s[3], s[2], s[1], s[0], s[1]};
			out.quad(xyz, uv, new int[] {lower, lower, tint, tint}, 0, 1, 0, KIND_SWAY);
		}
	}
	private static long key(int x, int y, int z) {
		return ((long) (x + 1_000) << 40) | ((long) (y + 1_000) << 20) | (z + 1_000);
	}

	private static int shade(int rgb, int ao) {
		int f = switch (ao) {
			case 0 -> 0x8C;
			case 1 -> 0xA8;
			case 2 -> 0xD0;
			default -> 0xFF;
		};
		return mul(rgb, (f << 16) | (f << 8) | f);
	}
	private static int mul(int a, int b) {
		int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
		int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
		int bl = (a & 0xFF) * (b & 0xFF) / 255;
		return (r << 16) | (g << 8) | bl;
	}
	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	static double hash(int x, int z, int salt) {
		long h = x * 374761393L + z * 668265263L + salt * 2147483647L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}
	private static double noise(double x, double z) {
		int xi = (int) Math.floor(x);
		int zi = (int) Math.floor(z);
		double fx = x - xi;
		double fz = z - zi;
		double a = hash(xi, zi, 1);
		double b = hash(xi + 1, zi, 1);
		double c = hash(xi, zi + 1, 1);
		double d = hash(xi + 1, zi + 1, 1);
		double u = smooth(fx);
		double v = smooth(fz);
		return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
	}
	private static double fbm(double x, double z, int octaves) {
		double sum = 0;
		double amp = 0.5;
		double norm = 0;
		for (int i = 0; i < octaves; i++) {
			sum += noise(x, z) * amp;
			norm += amp;
			x = x * 2.03 + 17.1;
			z = z * 2.03 - 9.3;
			amp *= 0.5;
		}
		return sum / norm;
	}
	private static double ridge(double x) {
		return 1 - Math.abs(noise(x, 3.7) * 2 - 1);
	}
}
