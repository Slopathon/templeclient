package nz.temple.client.mixin;

import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import nz.temple.TempleNz;
import nz.temple.client.render.Fonts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(FontTexture.class)
public abstract class FontTextureSmoothMixin extends AbstractTexture {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void temple$smooth(Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
		String name = label.get();
		if (name != null && name.startsWith(TempleNz.MOD_ID + ":")) {
			Fonts.smooth(textureView);
		}
	}
}
