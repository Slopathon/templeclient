package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class ChunkScan<T> {
	@FunctionalInterface
	public interface Scanner<T> {
		void scan(ClientLevel level, LevelChunk chunk, List<T> out);
	}
	private final Map<Long, List<T>> chunks = new HashMap<>();
	private ClientLevel level;
	private int radius = -1;

	private int[][] order = new int[0][];
	private int cursor;
	private int version;
	private double[] distances = new double[0];
	private int[] buckets = new int[0];
	public void tick(int radiusChunks, int perTick, Scanner<T> scanner) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			clear();
			return;
		}
		if (level != mc.level) {
			clear();
			level = mc.level;
		}
		if (radiusChunks != radius) {
			radius = radiusChunks;
			order = offsets(radiusChunks);
			cursor = 0;
		}
		ChunkPos center = mc.player.chunkPosition();
		if (chunks.keySet().removeIf(key -> Math.abs(ChunkPos.getX(key) - center.x()) > radius
				|| Math.abs(ChunkPos.getZ(key) - center.z()) > radius
				|| !mc.level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)))) {
			version++;
		}
		int budget = Math.min(perTick, order.length);
		for (int i = 0; i < budget; i++) {
			int[] o = order[Math.floorMod(cursor++, order.length)];
			int x = center.x() + o[0];
			int z = center.z() + o[1];
			if (!mc.level.hasChunk(x, z)) {
				continue;
			}
			List<T> out = new ArrayList<>();
			scanner.scan(mc.level, mc.level.getChunk(x, z), out);
			List<T> old = chunks.put(ChunkPos.pack(x, z), out);
			if (old == null ? !out.isEmpty() : !out.equals(old)) {
				version++;
			}
		}
	}
	public int version() {
		return version;
	}
	public void nearest(ToDoubleFunction<T> distSq, double maxSq, int cap, List<T> out) {
		out.clear();
		int n = 0;
		for (List<T> list : chunks.values()) {
			for (int i = 0, size = list.size(); i < size; i++) {
				T t = list.get(i);
				double d = distSq.applyAsDouble(t);
				if (d <= maxSq) {
					if (n == distances.length) {
						distances = java.util.Arrays.copyOf(distances, Math.max(256, n * 2));
					}
					distances[n++] = d;
					out.add(t);
				}
			}
		}
		if (n <= cap) {
			return;
		}
		int slots = (int) Math.sqrt(maxSq) + 2;
		if (buckets.length < slots) {
			buckets = new int[slots];
		} else {
			java.util.Arrays.fill(buckets, 0, slots, 0);
		}
		for (int i = 0; i < n; i++) {
			buckets[(int) Math.sqrt(distances[i])]++;
		}
		int edge = 0;
		int inside = 0;
		while (inside + buckets[edge] <= cap) {
			inside += buckets[edge++];
		}
		int room = cap - inside;
		int kept = 0;
		for (int i = 0; i < n; i++) {
			int bucket = (int) Math.sqrt(distances[i]);
			if (bucket < edge || bucket == edge && room-- > 0) {
				out.set(kept++, out.get(i));
			}
		}
		out.subList(kept, n).clear();
	}
	public Collection<List<T>> results() {
		return chunks.values();
	}
	public Map<Long, List<T>> byChunk() {
		return chunks;
	}
	public void clear() {
		chunks.clear();
		level = null;
		cursor = 0;
		version++;
	}

	private static int[][] offsets(int r) {
		List<int[]> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				list.add(new int[] {dx, dz});
			}
		}
		list.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
		return list.toArray(new int[0][]);
	}
}
