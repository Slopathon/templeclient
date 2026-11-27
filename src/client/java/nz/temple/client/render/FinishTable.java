package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import nz.temple.cosmetic.Finish;
import nz.temple.cosmetic.Finishes;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class FinishTable {
	private static GpuTexture texture;
	private static GpuTextureView view;
	private FinishTable() {
	}
	public static GpuTextureView view() {
		if (view == null) {
			build();
		}
		return view;
	}
	private static void build() {
		GpuDevice device = RenderSystem.getDevice();
		int w = Finishes.CAPACITY;
		texture = Gpu.createTexture(() -> "Temple.nz finish table", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
				GpuFormat.RGBA8_UNORM, w, 2, 1, 1);
		view = device.createTextureView(texture);
		ByteBuffer data = MemoryUtil.memAlloc(w * 2 * 4);
		try {
			for (int i = 0; i < w; i++) {
				Finish f = i < Finishes.all().size() ? Finishes.all().get(i) : Finishes.LEGACY_MATTE;
				put(data, i * 4, f.roughness(), f.metalness(), f.transmission(), f.subsurface());
				put(data, (w + i) * 4, f.sheen(), f.clearcoat(), f.iridescence(), f.isLegacy() ? f.legacy() / 255f : 1f);
			}
			Gpu.writeRgba(texture, data, w, 2);
		} finally {
			MemoryUtil.memFree(data);
		}
	}
	private static void put(ByteBuffer data, int at, float r, float g, float b, float a) {
		data.put(at, unit(r)).put(at + 1, unit(g)).put(at + 2, unit(b)).put(at + 3, unit(a));
	}

	private static byte unit(float v) {
		return (byte) Math.round(Math.max(0, Math.min(1, v)) * 255);
	}
}
