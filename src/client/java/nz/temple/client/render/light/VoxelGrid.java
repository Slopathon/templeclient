package nz.temple.client.render.light;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.IdentityHashMap;
import java.util.Map;

final class VoxelGrid {
	static final int SIDE = 128;
	static final int HEIGHT = 64;
	static final int TILES = 8;
	private static final int TEXTURE = SIDE * TILES;
	static final int EMPTY = 0, PART = 110, LEAVES = 150, SOLID = 255;
	private static final int NEAR = 1024;
	private final int[] order = spiral();
	private final int[] mirror = new int[TEXTURE * TEXTURE];
	private final int[] slotX = new int[SIDE * SIDE];
	private final int[] slotZ = new int[SIDE * SIDE];
	private final int[] slotY = new int[SIDE * SIDE];
	private final boolean[] unloaded = new boolean[SIDE * SIDE];
	private final java.util.Queue<int[]> changed = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private int frame;
	private final Map<BlockState, Integer> kinds = new IdentityHashMap<>();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private DynamicTexture texture;
	private ClientLevel level;
	private int originX;
	private int originY;
	private int originZ;
	private boolean ready;
	private boolean dirty;
	private int nearCursor;
	private int farCursor;
	private boolean nearTurn;
	private LevelChunk lastChunk;
	private int lastChunkX = Integer.MIN_VALUE;
	private int lastChunkZ;
	private static int[] spiral() {
		Integer[] all = new Integer[SIDE * SIDE];
		for (int z = 0; z < SIDE; z++) {
			for (int x = 0; x < SIDE; x++) {
				all[z * SIDE + x] = x | z << 8;
			}
		}
		java.util.Arrays.sort(all, (a, b) -> {
			int ax = (a & 0xFF) - SIDE / 2, az = (a >> 8) - SIDE / 2, bx = (b & 0xFF) - SIDE / 2, bz = (b >> 8) - SIDE / 2;
			return Integer.compare(ax * ax + az * az, bx * bx + bz * bz);
		});
		int[] out = new int[all.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = all[i];
		}
		return out;
	}
	GpuTextureView view() {
		if (texture == null) {
			texture = new DynamicTexture(() -> "Temple.nz voxels", TEXTURE, TEXTURE, false);
			clear();
			texture.upload();
		}
		return texture.getTextureView();
	}
	int originX() {
		return originX;
	}
	int originY() {
		return originY;
	}
	int originZ() {
		return originZ;
	}
	void changed(int wx, int wz) {
		if (changed.size() < 4096) {
			changed.add(new int[] {wx, wz});
		}
	}
	private void clear() {
		int open = texel(EMPTY, 15, 0);
		java.util.Arrays.fill(mirror, open);
		texture.getPixels().fillRect(0, 0, TEXTURE, TEXTURE, open);
		java.util.Arrays.fill(slotY, Integer.MIN_VALUE);
		dirty = true;
	}
	private static int texel(int kind, int sky, int block) {
		return 0xFF000000 | kind << 16 | sky * 17 << 8 | block * 17;
	}
	void update(ClientLevel level, double camX, double camY, double camZ, long budgetNanos) {
		view();
		if (level != this.level || !ready) {
			this.level = level;
			kinds.clear();
			clear();
			ready = true;
		}
		lastChunk = null;
		lastChunkX = Integer.MIN_VALUE;
		originX = (int) Math.floor(camX) - SIDE / 2;
		originZ = (int) Math.floor(camZ) - SIDE / 2;
		originY = Math.floorDiv((int) Math.floor(camY) - HEIGHT / 2 - 6, 8) * 8;
		long start = System.nanoTime();
		NativeImage pixels = texture.getPixels();
		for (int[] c = changed.poll(); c != null; c = changed.poll()) {
			if (c[0] >= originX && c[0] < originX + SIDE && c[1] >= originZ && c[1] < originZ + SIDE) {
				column(pixels, c[0], c[1], (c[1] & SIDE - 1) * SIDE + (c[0] & SIDE - 1));
			}
		}
		boolean retry = ++frame % 30 == 0;
		int checked = 0;
		for (int i = 0; i < order.length; i++) {
			int wx = originX + (order[i] & 0xFF), wz = originZ + (order[i] >> 8);
			int slot = (wz & SIDE - 1) * SIDE + (wx & SIDE - 1);
			if (slotX[slot] == wx && slotZ[slot] == wz && slotY[slot] == originY && !(retry && unloaded[slot])) {
				continue;
			}
			column(pixels, wx, wz, slot);
			if ((++checked & 15) == 0 && System.nanoTime() - start > budgetNanos) {
				break;
			}
		}
		while (System.nanoTime() - start < budgetNanos / 4) {
			for (int n = 0; n < 16; n++) {
				int i;
				if (nearTurn) {
					i = nearCursor;
					nearCursor = (nearCursor + 1) % NEAR;
				} else {
					i = farCursor;
					farCursor = (farCursor + 1) % order.length;
				}
				nearTurn = !nearTurn;
				int wx = originX + (order[i] & 0xFF), wz = originZ + (order[i] >> 8);
				column(pixels, wx, wz, (wz & SIDE - 1) * SIDE + (wx & SIDE - 1));
			}
		}
		if (dirty) {
			texture.upload();
			dirty = false;
		}
	}
	private LevelChunk chunk(int cx, int cz) {
		if (cx != lastChunkX || cz != lastChunkZ) {
			lastChunkX = cx;
			lastChunkZ = cz;
			lastChunk = level.hasChunk(cx, cz) ? level.getChunk(cx, cz) : null;
		}
		return lastChunk;
	}
	private void column(NativeImage pixels, int wx, int wz, int slot) {
		LevelChunk chunk = chunk(wx >> 4, wz >> 4);
		slotX[slot] = wx;
		slotZ[slot] = wz;
		slotY[slot] = originY;
		unloaded[slot] = chunk == null;
		int sx = wx & SIDE - 1, sz = wz & SIDE - 1;
		int lx = wx & 15, lz = wz & 15;
		int surface = chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
		LevelChunkSection[] sections = chunk == null ? null : chunk.getSections();
		int lastSection = Integer.MIN_VALUE;
		LevelChunkSection section = null;
		DataLayer sky = null;
		DataLayer block = null;
		for (int wy = originY; wy < originY + HEIGHT; wy++) {
			int kind = EMPTY;
			int skyLight = 15;
			int blockLight = 0;
			if (chunk != null) {
				int sy = wy >> 4;
				if (sy != lastSection) {
					lastSection = sy;
					int index = level.getSectionIndexFromSectionY(sy);
					section = index >= 0 && index < sections.length ? sections[index] : null;
					SectionPos at = SectionPos.of(wx >> 4, sy, wz >> 4);
					sky = level.getLightEngine().getLayerListener(LightLayer.SKY).getDataLayerData(at);
					block = level.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(at);
				}
				if (section != null && !section.hasOnlyAir()) {
					kind = kind(section.getBlockState(lx, wy & 15, lz));
				}
				skyLight = sky != null ? sky.get(lx, wy & 15, lz) : wy > surface ? 15 : 0;
				blockLight = block != null ? block.get(lx, wy & 15, lz) : 0;
			}
			int value = texel(kind, skyLight, blockLight);
			int layer = wy & HEIGHT - 1;
			int px = (layer % TILES) * SIDE + sx, py = (layer / TILES) * SIDE + sz;
			if (mirror[py * TEXTURE + px] != value) {
				mirror[py * TEXTURE + px] = value;
				pixels.setPixel(px, py, value);
				dirty = true;
			}
		}
	}
	private int kind(BlockState state) {
		if (state.isAir()) {
			return EMPTY;
		}
		Integer known = kinds.get(state);
		if (known != null) {
			return known;
		}
		int kind = EMPTY;
		if (state.isSolidRender()) {
			kind = SOLID;
		} else if (state.getBlock() instanceof LeavesBlock) {
			kind = LEAVES;
		} else if (state.canOcclude() && nz.temple.client.compat.States.blocksMotion(state)) {
			VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos.set(0, 0, 0));
			if (!shape.isEmpty()) {
				AABB box = shape.bounds();
				if (box.getXsize() * box.getYsize() * box.getZsize() >= 0.45) {
					kind = PART;
				}
			}
		}
		kinds.put(state, kind);
		return kind;
	}
}
