package nz.temple.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.client.MenuModule;

@Mixin(FramerateLimitTracker.class)
public abstract class MenuFramerateMixin {
	@Inject(method = "getThrottleReason", at = @At("RETURN"), cancellable = true)
	private void temple$uncapMenus(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
		if (cir.getReturnValue() == FramerateLimitTracker.FramerateThrottleReason.OUT_OF_LEVEL_MENU && MenuModule.uncappedMenus()) {
			cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
		}
	}
}
