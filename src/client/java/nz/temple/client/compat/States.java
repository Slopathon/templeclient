package nz.temple.client.compat;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Block-state queries that were removed or renamed between versions. */
public final class States {
	private States() {
	}

	/** Whether the block stops movement through it (solid, except cobwebs and bamboo shoots), as vanilla had it up to 26.2. */
	public static boolean blocksMotion(BlockState state) {
		//? if >=26.3 {
		/*return !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING) && state.isSolid();
		*///?} else {
		return state.blocksMotion();
		//?}
	}

	/** The colour {@code state}'s first tint layer takes at {@code pos} (grass, leaves, water), opaque; white when untinted. */
	public static int tint(BlockState state, net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
		//? if >=26 {
		var source = net.minecraft.client.Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
		return source == null ? 0xFFFFFFFF : 0xFF000000 | source.colorInWorld(state, level, pos);
		//?} else {
		/*int colour = net.minecraft.client.Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
		return colour == -1 ? 0xFFFFFFFF : 0xFF000000 | colour;
		*///?}
	}
}
