package nz.temple.smoke.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class PerfThrottleMixin {
	@Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
	private void temple$unthrottled(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
		if (Boolean.getBoolean("temple.perf")) {
			cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
		}
	}
}
