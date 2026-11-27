package nz.temple.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class VanillaDeathPoofMixin {
	@Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
	private void temple$noDeathPoof(CallbackInfo ci) {
		if (((LivingEntity) (Object) this).level().isClientSide() && nz.temple.client.util.VanillaFx.replacesPoof((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
