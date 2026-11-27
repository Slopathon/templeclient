package nz.temple.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.movement.HighJump;

@Mixin(LivingEntity.class)
abstract class HighJumpMixin {
	@Inject(method = "getJumpPower()F", at = @At("RETURN"), cancellable = true)
	private void temple$highJump(CallbackInfoReturnable<Float> cir) {
		float power = HighJump.power((LivingEntity) (Object) this);
		if (!Float.isNaN(power)) {
			cir.setReturnValue(power);
		}
	}
}
