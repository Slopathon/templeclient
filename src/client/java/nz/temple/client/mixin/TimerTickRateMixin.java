package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.movement.Timer;

@Mixin(Minecraft.class)
public abstract class TimerTickRateMixin {
	@Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
	private void temple$timer(float fallback, CallbackInfoReturnable<Float> cir) {
		float speed = Timer.speed();
		if (speed != 1) {
			cir.setReturnValue(cir.getReturnValue() / speed);
		}
	}
}
