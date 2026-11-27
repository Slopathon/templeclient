package nz.temple.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SusZones {
	public static final int POINTS_PER_STEP = 5;
	public static final int GEODE_LINK = 5;
	public enum Signal {
		AMETHYST(6, 16),
		KELP(2, 6),
		BAMBOO(2, 6),
		BERRIES(2, 5),
		VINES(2, 6),
		DRIPSTONE(2, 5);
		public final int weight;
		public final int cap;

		Signal(int weight, int cap) {
			this.weight = weight;
			this.cap = cap;
		}
		public int points(int count) {
			return weight * Math.min(count, cap);
		}
	}
	private SusZones() {
	}

	public static long chunkKey(int cx, int cz) {
		return (cx & 0xFFFFFFFFL) | (cz & 0xFFFFFFFFL) << 32;
	}
	public static int chunkX(long key) {
		return (int) key;
	}
	public static int chunkZ(long key) {
		return (int) (key >>> 32);
	}
	public static int threshold(int setting) {
		return setting * POINTS_PER_STEP;
	}
	public static int plantScore(int[] counts) {
		int total = 0;
		for (Signal s : Signal.values()) {
			if (s != Signal.AMETHYST && s.ordinal() < counts.length) {
				total += s.points(counts[s.ordinal()]);
			}
		}
		return total;
	}
	public record Cell(int x, int y, int z) {
		public long chunk() {
			return chunkKey(x >> 4, z >> 4);
		}
	}
	public record Geode(List<Cell> cells, Set<Long> chunks, int score) {
	}

	public static List<Geode> clusterGeodes(List<Cell> cells) {
		int n = cells.size();
		if (n == 0) {
			return List.of();
		}
		int size = GEODE_LINK + 1;
		Map<Cell, List<Integer>> buckets = new HashMap<>();
		for (int i = 0; i < n; i++) {
			Cell c = cells.get(i);
			buckets.computeIfAbsent(new Cell(Math.floorDiv(c.x(), size), Math.floorDiv(c.y(), size), Math.floorDiv(c.z(), size)),
					k -> new ArrayList<>()).add(i);
		}
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) {
			parent[i] = i;
		}
		for (int i = 0; i < n; i++) {
			Cell a = cells.get(i);
			int bx = Math.floorDiv(a.x(), size), by = Math.floorDiv(a.y(), size), bz = Math.floorDiv(a.z(), size);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						List<Integer> bucket = buckets.get(new Cell(bx + dx, by + dy, bz + dz));
						if (bucket == null) {
							continue;
						}
						for (int j : bucket) {
							if (j <= i) {
								continue;
							}
							Cell b = cells.get(j);
							int d = Math.max(Math.abs(a.x() - b.x()), Math.max(Math.abs(a.y() - b.y()), Math.abs(a.z() - b.z())));
							if (d <= GEODE_LINK) {
								parent[find(parent, i)] = find(parent, j);
							}
						}
					}
				}
			}
		}
		Map<Integer, List<Cell>> groups = new HashMap<>();
		for (int i = 0; i < n; i++) {
			groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(cells.get(i));
		}
		List<Geode> geodes = new ArrayList<>();
		for (List<Cell> group : groups.values()) {
			Set<Long> chunks = new HashSet<>();
			for (Cell c : group) {
				chunks.add(c.chunk());
			}
			geodes.add(new Geode(List.copyOf(group), Set.copyOf(chunks), Signal.AMETHYST.points(group.size())));
		}
		return geodes;
	}
	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}
	public static final class Flag {
		public final long key;
		public int score;
		public double hitWeight;
		public double hitX;
		public double hitZ;
		public int hitMinY = Integer.MAX_VALUE;
		public Flag(long key) {
			this.key = key;
		}
		public void hit(double weight, double x, int y, double z) {
			hitWeight += weight;
			hitX += x * weight;
			hitZ += z * weight;
			hitMinY = Math.min(hitMinY, y);
		}
	}
	public record Zone(Set<Long> members, long key, double x, int y, double z, int totalScore, int maxScore) {
	}
	public static List<Zone> buildZones(Collection<Flag> flags, int radius) {
		radius = Math.max(0, radius);
		Map<Long, Flag> byKey = new HashMap<>();
		for (Flag f : flags) {
			byKey.put(f.key, f);
		}
		List<Zone> zones = new ArrayList<>();
		Set<Long> visited = new HashSet<>();
		for (Flag seed : flags) {
			if (!visited.add(seed.key)) {
				continue;
			}
			List<Flag> group = new ArrayList<>();
			Deque<Flag> frontier = new ArrayDeque<>();
			frontier.add(seed);
			while (!frontier.isEmpty()) {
				Flag current = frontier.poll();
				group.add(current);
				int cx = chunkX(current.key), cz = chunkZ(current.key);
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						Flag next = byKey.get(chunkKey(cx + dx, cz + dz));
						if (next != null && visited.add(next.key)) {
							frontier.add(next);
						}
					}
				}
			}
			zones.add(zone(group));
		}
		zones.sort(Comparator.comparingInt(Zone::totalScore).reversed());
		return zones;
	}
	private static Zone zone(List<Flag> group) {
		Set<Long> members = new HashSet<>();
		long key = Long.MAX_VALUE;
		int total = 0, max = 0, minY = Integer.MAX_VALUE;
		double weight = 0, hx = 0, hz = 0, chunkX = 0, chunkZ = 0;
		for (Flag f : group) {
			members.add(f.key);
			key = Math.min(key, f.key);
			total += f.score;
			max = Math.max(max, f.score);
			if (f.hitWeight > 0) {
				weight += f.hitWeight;
				hx += f.hitX;
				hz += f.hitZ;
				minY = Math.min(minY, f.hitMinY);
			}
			chunkX += (chunkX(f.key) * 16 + 8) * (double) f.score;
			chunkZ += (chunkZ(f.key) * 16 + 8) * (double) f.score;
		}
		double x = weight > 0 ? hx / weight : total > 0 ? chunkX / total : chunkX(key) * 16 + 8;
		double z = weight > 0 ? hz / weight : total > 0 ? chunkZ / total : chunkZ(key) * 16 + 8;
		return new Zone(Set.copyOf(members), key, x, minY == Integer.MAX_VALUE ? 64 : minY, z, total, max);
	}
}
