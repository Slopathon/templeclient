//? if >=26.3 {
/*// 26.3 forbids other GPU commands while a render pass is open; this and RenderPassCloseMixin tell Gpu.outsidePass when
// one is. Earlier versions leave both out (mixins_excluded).
package nz.temple.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import nz.temple.client.compat.Gpu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FrontendCommandEncoder.class)
public abstract class RenderPassOpenMixin {
	@Inject(method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/api/commands/RenderPass;",
			at = @At("RETURN"))
	private void temple$opened(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> cir) {
		Gpu.passOpened();
	}
}
*///?}
