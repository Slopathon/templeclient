package nz.temple.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import nz.temple.client.render.Fonts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderState.class)
public abstract class GlyphSmoothMixin {
	@Shadow
	@Final
	private TextRenderable renderable;
	@Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true)
	private void temple$smooth(CallbackInfoReturnable<TextureSetup> cir) {
		if (Fonts.isSmooth(renderable.textureView())) {
			cir.setReturnValue(TextureSetup.singleTextureWithLightmap(renderable.textureView(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
		}
	}
}
