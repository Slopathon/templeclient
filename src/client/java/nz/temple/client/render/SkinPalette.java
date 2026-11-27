package nz.temple.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SkinPalette {
	private static final int SIZE = 48;
	private static final Map<Identifier, int[]> CACHE = new HashMap<>();
	private SkinPalette() {
	}

	public static int[] of(Identifier texture) {
		if (texture == null) {
			return null;
		}
		int[] cached = CACHE.computeIfAbsent(texture, SkinPalette::read);
		return cached.length == 0 ? null : cached;
	}
	private static int[] read(Identifier texture) {
		Minecraft mc = Minecraft.getInstance();
		try {
			AbstractTexture loaded = mc.getTextureManager().getTexture(texture);
			if (loaded instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
				return pick(dynamic.getPixels());
			}
			Optional<net.minecraft.server.packs.resources.Resource> resource = mc.getResourceManager().getResource(texture);
			if (resource.isEmpty()) {
				return new int[0];
			}
			try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
				return pick(image);
			}
		} catch (Exception e) {
			return new int[0];
		}
	}
	private static int[] pick(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int[] opaque = new int[4096];
		int n = 0;
		int step = Math.max(1, (int) Math.sqrt((double) w * h / 4096));
		for (int y = 0; y < h && n < opaque.length; y += step) {
			for (int x = 0; x < w && n < opaque.length; x += step) {
				int argb = image.getPixel(x, y);
				if ((argb >>> 24) > 200) {
					opaque[n++] = argb | 0xFF000000;
				}
			}
		}
		if (n == 0) {
			return new int[0];
		}
		int[] palette = new int[SIZE];
		for (int i = 0; i < SIZE; i++) {
			palette[i] = opaque[(int) ((long) i * n / SIZE)];
		}
		return palette;
	}
}
