package nz.temple.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.util.UseSlowdown;

@Mixin(LocalPlayer.class)
public abstract class NoSlowMixin {
	@Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
	private void temple$noSlow(CallbackInfoReturnable<Float> cir) {
		Float kept = UseSlowdown.multiplier((LocalPlayer) (Object) this);
		if (kept != null) {
			cir.setReturnValue(kept);
		}
	}
}
