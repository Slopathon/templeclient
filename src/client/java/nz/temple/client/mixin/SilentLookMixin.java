package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.modules.combat.AimAssist;
import nz.temple.client.modules.movement.MoveFix;
import nz.temple.client.modules.SchemaModules;
import nz.temple.client.modules.combat.TimeFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class SilentLookMixin {
	@Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
	private void temple$silentViewVector(float partialTick, CallbackInfoReturnable<Vec3> cir) {
		if ((Object) this == Minecraft.getInstance().player && AimAssist.isActivelyTracking()) {
			cir.setReturnValue(AimAssist.getRotationVector(AimAssist.getSilentServerPitch(), AimAssist.getSilentServerYaw()));
		}
	}

	@Inject(method = "getHeadLookAngle", at = @At("HEAD"), cancellable = true)
	private void temple$silentHeadLook(CallbackInfoReturnable<Vec3> cir) {
		if ((Object) this == Minecraft.getInstance().player && AimAssist.isActivelyTracking()) {
			cir.setReturnValue(AimAssist.getRotationVector(AimAssist.getSilentServerPitch(), AimAssist.getSilentServerYaw()));
		}
	}

	@ModifyExpressionValue(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
	private float temple$moveYaw(float original) {
		return (Object) this == Minecraft.getInstance().player && AimAssist.hasSilentRotation() && MoveFix.shouldApplyRotationFix()
				? AimAssist.getSilentServerYaw()
				: original;
	}

	@Inject(method = "getEyeHeight()F", at = @At("HEAD"), cancellable = true)
	private void temple$timeFreezeEyeHeight(CallbackInfoReturnable<Float> cir) {
		Entity self = (Entity) (Object) this;
		if (self == Minecraft.getInstance().player && SchemaModules.get("Time Freeze") instanceof TimeFreeze tf && tf.isEnabled()
				&& tf.isElytraStopHeld()) {
			cir.setReturnValue(self.getEyeHeight(net.minecraft.world.entity.Pose.STANDING));
		}
	}
}
