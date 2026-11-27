package nz.temple.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.level.levelgen.Heightmap;

final class GroundHeights {
	static final int MAP = 256;
	static final int STEP = 2;
	private static final int REFRESH_ROWS = 2;
	private DynamicTexture texture;
	private ClientLevel level;
	private boolean ready;
	private int originX;
	private int originZ;
	private int refreshRow;
	private int maxHeight;
	private boolean dirty;
	GpuTextureView view() {
		if (texture == null) {
			texture = new DynamicTexture(() -> "Temple.nz ground heights", MAP, MAP, false);
			texture.getPixels().fillRect(0, 0, MAP, MAP, 0);
			texture.upload();
		}
		return texture.getTextureView();
	}

	int originX() {
		return originX;
	}
	int originZ() {
		return originZ;
	}

	int maxHeight() {
		return maxHeight;
	}

	void update(ClientLevel level, double x, double z) {
		view();
		NativeImage pixels = texture.getPixels();
		int wantX = (int) Math.floor(x / STEP) - MAP / 2;
		int wantZ = (int) Math.floor(z / STEP) - MAP / 2;
		if (!ready || level != this.level || Math.abs(wantX - originX) >= MAP / 2 || Math.abs(wantZ - originZ) >= MAP / 2) {
			this.level = level;
			originX = wantX;
			originZ = wantZ;
			maxHeight = level.getMinY();
			for (int tz = originZ; tz < originZ + MAP; tz++) {
				for (int tx = originX; tx < originX + MAP; tx++) {
					write(pixels, tx, tz);
				}
			}
			ready = true;
			dirty = true;
		} else {
			if (wantX != originX) {
				int from = wantX > originX ? originX + MAP : wantX;
				int to = wantX > originX ? wantX + MAP : originX;
				originX = wantX;
				for (int tx = from; tx < to; tx++) {
					for (int tz = originZ; tz < originZ + MAP; tz++) {
						write(pixels, tx, tz);
					}
				}
				dirty = true;
			}
			if (wantZ != originZ) {
				int from = wantZ > originZ ? originZ + MAP : wantZ;
				int to = wantZ > originZ ? wantZ + MAP : originZ;
				originZ = wantZ;
				for (int tz = from; tz < to; tz++) {
					for (int tx = originX; tx < originX + MAP; tx++) {
						write(pixels, tx, tz);
					}
				}
				dirty = true;
			}
			for (int i = 0; i < REFRESH_ROWS; i++) {
				int tz = originZ + refreshRow;
				for (int tx = originX; tx < originX + MAP; tx++) {
					write(pixels, tx, tz);
				}
				refreshRow = (refreshRow + 1) % MAP;
			}
			dirty = true;
		}
		if (dirty) {
			texture.upload();
			dirty = false;
		}
	}
	private void write(NativeImage pixels, int tx, int tz) {
		int wx = tx * STEP;
		int wz = tz * STEP;
		int h = 0;
		if (level.hasChunk(wx >> 4, wz >> 4)) {
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
			maxHeight = Math.max(maxHeight, y);
			h = Math.max(1, Math.min(0xFFFF, y - level.getMinY()));
		}
		pixels.setPixel(Math.floorMod(tx, MAP), Math.floorMod(tz, MAP), 0xFF000000 | ((h >> 8) << 16) | ((h & 0xFF) << 8));
	}
}
