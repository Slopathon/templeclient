package nz.temple.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WorldEvents {
	@FunctionalInterface
	public interface BlockListener {
		void changed(BlockPos pos, BlockState oldState, BlockState newState);
	}

	private static final List<BlockListener> BLOCK_LISTENERS = new CopyOnWriteArrayList<>();

	private WorldEvents() {
	}

	public static void onBlockChanged(BlockListener listener) {
		BLOCK_LISTENERS.add(listener);
	}

	public static void blockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
		for (BlockListener listener : BLOCK_LISTENERS) {
			listener.changed(pos, oldState, newState);
		}
	}
}
