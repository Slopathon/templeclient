package nz.temple.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

public final class ServerLight {
	public static final int MIN_Y = -12;
	public static final int MAX_Y = 80;
	private static final int MIN_SECTION = SectionPos.blockToSectionCoord(MIN_Y);
	private static final int MAX_SECTION = SectionPos.blockToSectionCoord(MAX_Y);
	private static final Map<Long, short[]> LIGHT5 = new ConcurrentHashMap<>();
	private static final Set<Long> CHUNKS = ConcurrentHashMap.newKeySet();
	private static final Set<Long> DIRTY = ConcurrentHashMap.newKeySet();
	private static volatile boolean enabled;
	private ServerLight() {
	}
	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean on) {
		enabled = on;
		if (!on) {
			clear();
		}
	}
	public static void clear() {
		LIGHT5.clear();
		CHUNKS.clear();
		DIRTY.clear();
	}
	public static void drop(int cx, int cz) {
		if (CHUNKS.remove(ChunkPos.pack(cx, cz))) {
			for (int sy = MIN_SECTION; sy <= MAX_SECTION; sy++) {
				LIGHT5.remove(SectionPos.asLong(cx, sy, cz));
			}
		}
		DIRTY.remove(ChunkPos.pack(cx, cz));
	}
	public static void ingest(int cx, int cz, BitSet blockMask, BitSet emptyMask, List<byte[]> updates, int minLightSection) {
		if (!enabled || blockMask == null || emptyMask == null || updates == null) {
			return;
		}
		boolean changed = false;
		int index = 0;
		for (int i = blockMask.nextSetBit(0); i >= 0; i = blockMask.nextSetBit(i + 1)) {
			byte[] bytes = index < updates.size() ? updates.get(index) : null;
			index++;
			int sy = minLightSection + i;
			if (bytes != null && bytes.length == 2048 && sy >= MIN_SECTION && sy <= MAX_SECTION) {
				store(SectionPos.asLong(cx, sy, cz), bytes);
				changed = true;
			}
		}
		for (int i = emptyMask.nextSetBit(0); i >= 0; i = emptyMask.nextSetBit(i + 1)) {
			int sy = minLightSection + i;
			if (sy >= MIN_SECTION && sy <= MAX_SECTION) {
				changed |= LIGHT5.remove(SectionPos.asLong(cx, sy, cz)) != null;
			}
		}
		long key = ChunkPos.pack(cx, cz);
		changed |= CHUNKS.add(key);
		if (changed) {
			DIRTY.add(key);
		}
	}
	private static void store(long sectionKey, byte[] nibbles) {
		short[] cells = null;
		int count = 0;
		for (int index = 0; index < 4096; index++) {
			int b = nibbles[index >> 1] & 0xFF;
			if (((index & 1) == 0 ? b & 15 : b >> 4) == 5) {
				if (cells == null) {
					cells = new short[16];
				} else if (count == cells.length) {
					cells = Arrays.copyOf(cells, count * 2);
				}
				cells[count++] = (short) index;
			}
		}
		if (count == 0) {
			LIGHT5.remove(sectionKey);
		} else {
			LIGHT5.put(sectionKey, Arrays.copyOf(cells, count));
		}
	}
	public static boolean hasChunk(int cx, int cz) {
		return CHUNKS.contains(ChunkPos.pack(cx, cz));
	}
	public static void drainDirty(LongConsumer out) {
		for (var it = DIRTY.iterator(); it.hasNext(); ) {
			out.accept(it.next());
			it.remove();
		}
	}
	public static List<BlockPos> light5(int cx, int cz) {
		List<BlockPos> out = new ArrayList<>();
		for (int sy = MIN_SECTION; sy <= MAX_SECTION; sy++) {
			short[] cells = LIGHT5.get(SectionPos.asLong(cx, sy, cz));
			if (cells == null) {
				continue;
			}
			for (short cell : cells) {
				int index = cell & 0xFFFF;
				int y = (sy << 4) + (index >> 8);
				if (y >= MIN_Y && y <= MAX_Y) {
					out.add(new BlockPos((cx << 4) + (index & 15), y, (cz << 4) + (index >> 4 & 15)));
				}
			}
		}
		return out;
	}
}
