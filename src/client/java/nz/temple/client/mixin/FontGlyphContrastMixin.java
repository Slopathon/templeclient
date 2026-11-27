package nz.temple.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Text contrast for the client font. Minecraft blends a glyph's anti-aliased coverage linearly, which makes light text on
 * a dark background look thin and grainy at small sizes. Operating systems and browsers correct for this with a contrast
 * (gamma) curve on the coverage; this applies the same curve to Inter's glyphs as they are rasterised, so the edges are
 * solid and the letters read cleanly. Other fonts are left as they are.
 */
@Mixin(NativeImage.class)
public abstract class FontGlyphContrastMixin {
	private static final byte[] TEMPLE$CURVE = new byte[256];

	static {
		for (int i = 0; i < 256; i++) {
			TEMPLE$CURVE[i] = (byte) Math.round(255 * Math.pow(i / 255.0, 0.7));
		}
	}

	@Inject(method = "copyFromFont", at = @At("RETURN"))
	private void temple$contrast(FT_Face face, int index, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		String family;
		try {
			family = face.family_nameString();
		} catch (RuntimeException e) {
			return;
		}
		if (family == null || !family.startsWith("Inter")) {
			return;
		}
		NativeImage image = (NativeImage) (Object) this;
		if (image.format().components() != 1) {
			return;
		}
		long pixels = image.getPointer();
		int count = image.getWidth() * image.getHeight();
		for (int i = 0; i < count; i++) {
			int v = MemoryUtil.memGetByte(pixels + i) & 0xFF;
			MemoryUtil.memPutByte(pixels + i, TEMPLE$CURVE[v]);
		}
	}
}
