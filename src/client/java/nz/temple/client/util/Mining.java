package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class Mining {
	private static BlockPos current;
	private Mining() {
	}
	public static boolean dig(Minecraft mc, BlockPos pos, Direction face) {
		if (mc.gameMode == null || mc.player == null) {
			return false;
		}
		if (!pos.equals(current)) {
			mc.gameMode.startDestroyBlock(pos, face);
			current = pos.immutable();
		}
		return mc.gameMode.continueDestroyBlock(pos, face);
	}
	public static void stop() {
		current = null;
	}
}
