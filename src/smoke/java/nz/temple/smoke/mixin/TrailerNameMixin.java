package nz.temple.smoke.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.smoke.Trailer;

@Mixin(LivingEntityRenderer.class)
abstract class TrailerNameMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
	private void temple$hideDummyName(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
		if (Trailer.cleanHud() && !(entity instanceof net.minecraft.world.entity.player.Player)) {
			cir.setReturnValue(false);
		}
	}
}
