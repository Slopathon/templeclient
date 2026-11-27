package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockHider {
	private static final ConcurrentHashMap<Long, Integer> HIDDEN = new ConcurrentHashMap<>();
	private BlockHider() {
	}
	public static boolean hidden(BlockPos pos) {
		return !HIDDEN.isEmpty() && HIDDEN.containsKey(pos.asLong());
	}

	public static void hide(BlockPos pos) {
		if (add(pos)) {
			rebuild(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
		}
	}
	public static void show(BlockPos pos) {
		if (remove(pos)) {
			rebuild(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
		}
	}
	public static void hideAll(Collection<BlockPos> positions) {
		changeAll(positions, true);
	}
	public static void showAll(Collection<BlockPos> positions) {
		changeAll(positions, false);
	}
	private static void changeAll(Collection<BlockPos> positions, boolean hide) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		boolean changed = false;
		for (BlockPos p : positions) {
			if (hide ? add(p) : remove(p)) {
				changed = true;
				x0 = Math.min(x0, p.getX());
				y0 = Math.min(y0, p.getY());
				z0 = Math.min(z0, p.getZ());
				x1 = Math.max(x1, p.getX());
				y1 = Math.max(y1, p.getY());
				z1 = Math.max(z1, p.getZ());
			}
		}
		if (changed) {
			rebuild(x0, y0, z0, x1, y1, z1);
		}
	}
	private static boolean add(BlockPos pos) {
		return HIDDEN.merge(pos.asLong(), 1, Integer::sum) == 1;
	}
	private static boolean remove(BlockPos pos) {
		long key = pos.asLong();
		boolean[] gone = {false};
		HIDDEN.computeIfPresent(key, (k, n) -> {
			if (n <= 1) {
				gone[0] = true;
				return null;
			}
			return n - 1;
		});
		return gone[0];
	}
	private static void rebuild(int x0, int y0, int z0, int x1, int y1, int z1) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			level.setSectionRangeDirty(SectionPos.blockToSectionCoord(x0 - 1), SectionPos.blockToSectionCoord(y0 - 1),
					SectionPos.blockToSectionCoord(z0 - 1), SectionPos.blockToSectionCoord(x1 + 1),
					SectionPos.blockToSectionCoord(y1 + 1), SectionPos.blockToSectionCoord(z1 + 1));
		}
	}
}
