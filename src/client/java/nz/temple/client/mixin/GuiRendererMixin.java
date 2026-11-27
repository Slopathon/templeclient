package nz.temple.client.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.TempleNzClient;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void templenz$renderGlassLayer(CallbackInfo ci) {
		if (!nz.temple.client.render.streamproof.StreamproofRenderer.cleanPass()) {
			nz.temple.client.render.ScreenFilter.beforeGui();
			nz.temple.client.render.title.TitleScene.get().render();
			nz.temple.client.render.title.BlackHoleScene.get().render();
			nz.temple.client.render.title.SongBackdrop.render();
			TempleNzClient.glass().render();
		}
	}
}
