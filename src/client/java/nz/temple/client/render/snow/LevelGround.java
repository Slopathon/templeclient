package nz.temple.client.render.snow;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;
import nz.temple.snow.SnowSim;

final class LevelGround implements SnowSim.Ground {
	private static final int MAX_SCAN = 48;
	private ClientLevel level;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private int lastX = Integer.MIN_VALUE;
	private int lastZ;
	private float lastSurface;
	private boolean lastFoliage;
	void level(ClientLevel level) {
		this.level = level;
		lastX = Integer.MIN_VALUE;
	}

	void invalidate() {
		lastX = Integer.MIN_VALUE;
	}

	private void scan(int x, int z) {
		if (x == lastX && z == lastZ) {
			return;
		}
		lastX = x;
		lastZ = z;
		lastSurface = Float.NaN;
		lastFoliage = false;
		if (level == null || !level.hasChunk(x >> 4, z >> 4)) {
			return;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		int min = level.getMinY();
		for (int y = top - 1, n = 0; y >= min && n < MAX_SCAN; y--, n++) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (state.is(BlockTags.LEAVES)) {
				lastFoliage = true;
				continue;
			}
			if (!state.getFluidState().isEmpty()) {
				return;
			}
			VoxelShape shape = state.getShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			if (shape.min(Direction.Axis.X) > 0.08 || shape.max(Direction.Axis.X) < 0.92
					|| shape.min(Direction.Axis.Z) > 0.08 || shape.max(Direction.Axis.Z) < 0.92) {
				return;
			}
			lastSurface = y + (float) shape.max(Direction.Axis.Y);
			return;
		}
	}
	@Override
	public boolean loaded(int cx, int cz) {
		return level != null && level.hasChunk(cx, cz);
	}

	@Override
	public float surface(int x, int z) {
		scan(x, z);
		return lastSurface;
	}
	@Override
	public boolean underFoliage(int x, int z) {
		scan(x, z);
		return lastFoliage;
	}
	@Override
	public int heat(int x, int z) {
		scan(x, z);
		if (Float.isNaN(lastSurface)) {
			return 0;
		}
		pos.set(x, (int) Math.floor(lastSurface + 1e-3), z);
		return level.getBrightness(LightLayer.BLOCK, pos);
	}
	int light(int x, int z, float y) {
		if (level == null) {
			return 0xF0;
		}
		pos.set(x, (int) Math.floor(y + 1e-3), z);
		return level.getBrightness(LightLayer.BLOCK, pos) | level.getBrightness(LightLayer.SKY, pos) << 4;
	}
}
