package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.render.Animations;

/** Applies the Animations module to your own swing: how far the arm travels and how long a swing lasts. */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
	// 26.3 renamed these: getSwingAnimation, and getModifiedSwingDuration (which takes the swing it is timing).
	//? if >=26.3 {
	/*@Inject(method = "getSwingAnimation", at = @At("RETURN"), cancellable = true)
	*///?} else {
	@Inject(method = "getAttackAnim", at = @At("RETURN"), cancellable = true)
	//?}
	private void temple$swingHeight(float partialTick, CallbackInfoReturnable<Float> cir) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		Animations animations = Animations.instance();
		if (animations != null && animations.isEnabled()) {
			float frozen = animations.frozenSwing();
			float swing = frozen >= 0 ? frozen : cir.getReturnValue();
			cir.setReturnValue(swing * animations.swingScale());
		}
	}

	//? if >=26.3 {
	/*@Inject(method = "getModifiedSwingDuration", at = @At("HEAD"), cancellable = true)
	*///?} else {
	@Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
	//?}
	private void temple$swingSpeed(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		Animations animations = Animations.instance();
		if (animations != null && animations.isEnabled() && animations.overrideSpeed()) {
			cir.setReturnValue(animations.swingDuration());
		}
	}
}
