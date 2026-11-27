package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.SilentRotationState;
import nz.temple.client.modules.combat.AimAssist;
import nz.temple.client.modules.movement.MoveFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** MoveFix: sprint-jumps and elytra flight follow the silent (server) rotation instead of the camera. */
@Mixin(LivingEntity.class)
public class MoveFixLivingMixin {
	@ModifyArg(method = "jumpFromGround",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
	private Vec3 temple$sprintJump(Vec3 original) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || (Object) this != mc.player || !SilentRotationState.silentActive || !MoveFix.shouldApplyRotationFix()) {
			return original;
		}
		double magnitude = Math.sqrt(original.x * original.x + original.z * original.z);
		if (magnitude < 1.0E-6) {
			return original;
		}
		float yaw = SilentRotationState.tickServerYaw * (float) (Math.PI / 180.0);
		return new Vec3(-Mth.sin(yaw) * magnitude, original.y, Mth.cos(yaw) * magnitude);
	}

	@ModifyExpressionValue(method = "updateFallFlyingMovement",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
	private float temple$elytraPitch(float original) {
		return temple$silent() ? SilentRotationState.tickServerPitch : original;
	}

	@ModifyExpressionValue(method = "updateFallFlyingMovement",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 temple$elytraLook(Vec3 original) {
		return temple$silent() ? AimAssist.getRotationVector(SilentRotationState.tickServerPitch, SilentRotationState.tickServerYaw)
				: original;
	}

	private boolean temple$silent() {
		return (Object) this == Minecraft.getInstance().player && SilentRotationState.silentActive && MoveFix.shouldApplyRotationFix();
	}
}
