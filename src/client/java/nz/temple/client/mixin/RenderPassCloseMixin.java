//? if >=26.3 {
/*// See RenderPassOpenMixin.
package nz.temple.client.mixin;

import com.mojang.renderpearl.frontend.FrontendRenderPass;
import nz.temple.client.compat.Gpu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendRenderPass.class)
public abstract class RenderPassCloseMixin {
	@Inject(method = "close", at = @At("TAIL"))
	private void temple$closed(CallbackInfo ci) {
		Gpu.passClosed();
	}
}
*///?}
