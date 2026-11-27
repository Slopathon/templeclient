package nz.temple.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.client.util.ServerKey;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapStore {
	public static final int TILE = 256;
	private static final int MAX_LOADED = 200;
	private static final int DISK_LOADS_PER_DRAW = 12;
	private static final int TRAIL_STEP = 6;
	private static final int TRAIL_BREAK = 96;
	public static final int BREAK = Integer.MIN_VALUE;
	public static final class Tile {
		final int tx;
		final int tz;
		NativeImage image;
		DynamicTexture texture;
		Identifier id;
		boolean dirty;
		boolean upload;
		boolean drawn;
		long used;
		Tile(int tx, int tz) {
			this.tx = tx;
			this.tz = tz;
		}
	}
	private static final MapStore INSTANCE = new MapStore();

	public static MapStore get() {
		return INSTANCE;
	}
	private String server;
	private String dimension;
	private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(64, 0.75f, true);
	private final Set<Long> onDisk = new java.util.HashSet<>();
	private final LinkedHashSet<Long> chunkQueue = new LinkedHashSet<>();

	private final Map<String, List<int[]>> trails = new HashMap<>();
	private final Set<String> trailDirty = new java.util.HashSet<>();
	private int textureSerial;
	private long lastSave;
	private MapStore() {
	}
	private static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("map");
	}
	private Path dir(String srv, String dim) {
		return root().resolve(srv).resolve(dim);
	}

	private boolean sync() {
		String srv = ServerKey.current();
		String dim = ServerKey.dimension();
		if (srv == null || dim == null) {
			if (server != null) {
				flush();
				unloadAll();
				server = null;
				dimension = null;
			}
			return false;
		}
		if (!srv.equals(server)) {
			flush();
			unloadAll();
			trails.clear();
			server = srv;
			dimension = null;
		}
		if (!dim.equals(dimension)) {
			saveTiles();
			unloadAll();
			dimension = dim;
			scanDisk();
		}
		return true;
	}
	private void scanDisk() {
		onDisk.clear();
		Path dir = dir(server, dimension);
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.list(dir)) {
			stream.forEach(p -> {
				String n = p.getFileName().toString();
				if (n.endsWith(".png")) {
					String[] parts = n.substring(0, n.length() - 4).split("_");
					if (parts.length == 2) {
						try {
							onDisk.add(ChunkPos.pack(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
						} catch (NumberFormatException ignored) {
						}
					}
				}
			});
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not list map tiles in {}", dir, e);
		}
	}
	public String server() {
		return server;
	}
	public String dimension() {
		return dimension;
	}

	public void queue(ChunkPos pos) {
		chunkQueue.add(pos.pack());
	}

	public void tick(boolean terrain, boolean trail) {
		Minecraft mc = Minecraft.getInstance();
		if (!sync() || mc.level == null || mc.player == null) {
			return;
		}
		if (terrain) {
			int budget = 6;
			var it = chunkQueue.iterator();
			while (budget > 0 && it.hasNext()) {
				long key = it.next();
				it.remove();
				int cx = ChunkPos.getX(key);
				int cz = ChunkPos.getZ(key);
				if (mc.level.hasChunk(cx, cz)) {
					record(mc.level, mc.level.getChunk(cx, cz));
					budget--;
				}
			}
			if (mc.player.tickCount % 40 == 0) {
				ChunkPos center = mc.player.chunkPosition();
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						chunkQueue.add(ChunkPos.pack(center.x() + dx, center.z() + dz));
					}
				}
			}
		}
		if (trail) {
			addTrail(mc.player.getBlockX(), mc.player.getBlockZ());
		}
		if (System.currentTimeMillis() - lastSave > 60_000) {
			flush();
		}
	}
	private void addTrail(int x, int z) {
		List<int[]> list = trail(dimension);
		int[] last = null;
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i)[0] != BREAK) {
				last = list.get(i);
				break;
			}
		}
		if (last != null) {
			double d = Math.hypot(x - last[0], z - last[1]);
			if (d < TRAIL_STEP) {
				return;
			}
			if (d > TRAIL_BREAK && list.get(list.size() - 1)[0] != BREAK) {
				list.add(new int[] {BREAK, BREAK});
			}
		}
		list.add(new int[] {x, z});
		trailDirty.add(dimension);
	}
	private void record(ClientLevel level, LevelChunk chunk) {
		int bx = chunk.getPos().getMinBlockX();
		int bz = chunk.getPos().getMinBlockZ();
		Tile tile = tile(Math.floorDiv(bx, TILE), Math.floorDiv(bz, TILE), true);
		if (tile == null) {
			return;
		}
		boolean ceiling = level.dimensionType().hasCeiling();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int[] heights = new int[17];
		for (int x = 0; x < 16; x++) {
			heights[x] = surfaceY(level, ceiling, bx + x, bz - 1, pos);
		}
		int ox = Math.floorMod(bx, TILE);
		int oz = Math.floorMod(bz, TILE);
		boolean changed = false;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int y = surfaceY(level, ceiling, bx + x, bz + z, pos);
				int argb = 0;
				if (y != Integer.MIN_VALUE) {
					pos.set(bx + x, y, bz + z);
					BlockState state = level.getBlockState(pos);
					MapColor color = state.getMapColor(level, pos);
					if (color != MapColor.NONE) {
						MapColor.Brightness shade;
						if (!state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER)) {
							int depth = 0;
							while (depth < 12 && level.getFluidState(pos.move(0, -1, 0)).is(FluidTags.WATER)) {
								depth++;
							}
							shade = depth < 2 ? MapColor.Brightness.HIGH : depth < 5 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
						} else {
							int north = heights[x];
							shade = north == Integer.MIN_VALUE || y == north ? MapColor.Brightness.NORMAL
									: y > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
						}
						argb = 0xFF000000 | color.calculateARGBColor(shade);
					}
				}
				heights[x] = y;
				int px = ox + x;
				int pz = oz + z;
				if (tile.image.getPixel(px, pz) != argb && argb != 0) {
					tile.image.setPixel(px, pz, argb);
					changed = true;
				}
			}
		}
		if (changed) {
			tile.dirty = true;
			tile.upload = true;
		}
	}
	private static int surfaceY(ClientLevel level, boolean ceiling, int x, int z, BlockPos.MutableBlockPos pos) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return Integer.MIN_VALUE;
		}
		LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
		if (!ceiling) {
			int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
			return y <= level.getMinY() ? Integer.MIN_VALUE : y;
		}
		int top = Math.min(level.getMaxY(), level.getMinY() + level.dimensionType().logicalHeight()) - 2;
		int y = top;
		pos.set(x, y, z);
		while (y > level.getMinY() && !chunk.getBlockState(pos).isAir()) {
			pos.setY(--y);
		}
		while (y > level.getMinY() && chunk.getBlockState(pos).isAir()) {
			pos.setY(--y);
		}
		return y <= level.getMinY() ? Integer.MIN_VALUE : y;
	}
	public Tile tile(int tx, int tz, boolean create) {
		if (server == null) {
			return null;
		}
		long key = ChunkPos.pack(tx, tz);
		Tile t = tiles.get(key);
		if (t != null) {
			t.used = System.nanoTime();
			return t;
		}
		if (!create && !onDisk.contains(key)) {
			return null;
		}
		t = new Tile(tx, tz);
		Path file = dir(server, dimension).resolve(tx + "_" + tz + ".png");
		if (onDisk.contains(key)) {
			try (InputStream in = Files.newInputStream(file)) {
				NativeImage read = NativeImage.read(in);
				if (read.getWidth() == TILE && read.getHeight() == TILE) {
					t.image = read;
				} else {
					read.close();
				}
			} catch (IOException | RuntimeException e) {
				TempleNzClient.LOGGER.warn("Map tile {} could not be read, starting it again", file, e);
			}
		}
		if (t.image == null) {
			t.image = new NativeImage(TILE, TILE, true);
			t.image.fillRect(0, 0, TILE, TILE, 0);
		}
		t.upload = true;
		t.used = System.nanoTime();
		tiles.put(key, t);
		return t;
	}
	public Identifier texture(Tile t) {
		if (t.image == null) {
			return null;
		}
		if (t.texture == null) {
			t.id = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "map/tile_" + (textureSerial++));
			NativeImage copy = new NativeImage(TILE, TILE, true);
			copy.copyFrom(t.image);
			t.texture = new DynamicTexture(() -> "Temple map tile", copy);
			Minecraft.getInstance().getTextureManager().register(t.id, t.texture);
			t.upload = false;
		} else if (t.upload) {
			t.texture.getPixels().copyFrom(t.image);
			t.texture.upload();
			t.upload = false;
		}
		return t.id;
	}
	public List<Tile> tilesIn(int tx0, int tz0, int tx1, int tz1, int limit) {
		List<Tile> out = new ArrayList<>();
		if (server == null) {
			return out;
		}
		List<Long> keys = new ArrayList<>();
		long area = (long) (tx1 - tx0 + 1) * (tz1 - tz0 + 1);
		if (area > onDisk.size() + tiles.size() + 64) {
			Set<Long> known = new java.util.HashSet<>(onDisk);
			known.addAll(tiles.keySet());
			for (long key : known) {
				int tx = ChunkPos.getX(key);
				int tz = ChunkPos.getZ(key);
				if (tx >= tx0 && tx <= tx1 && tz >= tz0 && tz <= tz1) {
					keys.add(key);
				}
			}
		} else {
			for (int tz = tz0; tz <= tz1; tz++) {
				for (int tx = tx0; tx <= tx1; tx++) {
					long key = ChunkPos.pack(tx, tz);
					if (tiles.containsKey(key) || onDisk.contains(key)) {
						keys.add(key);
					}
				}
			}
		}
		double mx = (tx0 + tx1) / 2.0;
		double mz = (tz0 + tz1) / 2.0;
		keys.sort(java.util.Comparator.comparingDouble(k -> {
			double dx = ChunkPos.getX(k) - mx;
			double dz = ChunkPos.getZ(k) - mz;
			return dx * dx + dz * dz;
		}));
		int diskLoads = DISK_LOADS_PER_DRAW;
		for (long key : keys) {
			if (out.size() >= limit) {
				break;
			}
			if (!tiles.containsKey(key)) {
				if (diskLoads <= 0) {
					continue;
				}
				diskLoads--;
			}
			Tile t = tile(ChunkPos.getX(key), ChunkPos.getZ(key), false);
			if (t != null) {
				t.drawn = true;
				out.add(t);
			}
		}
		return out;
	}
	public void trim() {
		var it = tiles.values().iterator();
		while (tiles.size() > MAX_LOADED && it.hasNext()) {
			Tile t = it.next();
			if (!t.drawn) {
				it.remove();
				unload(t);
			}
		}
		for (Tile t : tiles.values()) {
			t.drawn = false;
		}
	}
	private void unload(Tile t) {
		save(t);
		if (t.texture != null) {
			Minecraft.getInstance().getTextureManager().release(t.id);
			t.texture = null;
		}
		if (t.image != null) {
			t.image.close();
			t.image = null;
		}
	}
	private void unloadAll() {
		for (Tile t : tiles.values()) {
			unload(t);
		}
		tiles.clear();
	}
	private void save(Tile t) {
		if (!t.dirty || t.image == null || server == null) {
			return;
		}
		Path file = dir(server, dimension).resolve(t.tx + "_" + t.tz + ".png");
		try {
			Files.createDirectories(file.getParent());
			t.image.writeToFile(file);
			t.dirty = false;
			onDisk.add(ChunkPos.pack(t.tx, t.tz));
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not save map tile {}", file, e);
		}
	}
	private void saveTiles() {
		for (Tile t : tiles.values()) {
			save(t);
		}
	}
	public void flush() {
		lastSave = System.currentTimeMillis();
		if (server == null) {
			return;
		}
		saveTiles();
		for (String dim : trailDirty) {
			saveTrail(dim);
		}
		trailDirty.clear();
	}
	public List<int[]> trail(String dim) {
		return trails.computeIfAbsent(dim, this::loadTrail);
	}
	private List<int[]> loadTrail(String dim) {
		List<int[]> list = new ArrayList<>();
		if (server == null) {
			return list;
		}
		Path file = dir(server, dim).resolve("trail.bin");
		if (Files.exists(file)) {
			try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(file)))) {
				int n = in.readInt();
				for (int i = 0; i < n; i++) {
					list.add(new int[] {in.readInt(), in.readInt()});
				}
			} catch (IOException e) {
				TempleNzClient.LOGGER.warn("Could not read {}", file, e);
			}
		}
		if (!list.isEmpty()) {
			list.add(new int[] {BREAK, BREAK});
		}
		return list;
	}
	private void saveTrail(String dim) {
		List<int[]> list = trails.get(dim);
		if (list == null || server == null) {
			return;
		}
		Path file = dir(server, dim).resolve("trail.bin");
		try {
			Files.createDirectories(file.getParent());
			try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(file)))) {
				out.writeInt(list.size());
				for (int[] p : list) {
					out.writeInt(p[0]);
					out.writeInt(p[1]);
				}
			}
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not save {}", file, e);
		}
	}
	public void clearTrail() {
		if (dimension != null) {
			trail(dimension).clear();
			trailDirty.add(dimension);
		}
	}
	public void leave() {
		flush();
		unloadAll();
		trails.clear();
		chunkQueue.clear();
		server = null;
		dimension = null;
		onDisk.clear();
	}
	public int queued() {
		return chunkQueue.size();
	}
}
