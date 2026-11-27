package nz.temple.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import nz.temple.client.event.FrameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once per rendered world frame: aim smoothing in Aim Assist, Kill Aura and the other combat modules. */
@Mixin(value = GameRenderer.class, priority = 1001)
public class FrameEventsMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void temple$perFrame(CallbackInfo ci) {
		FrameEvents.fire();
	}
}
