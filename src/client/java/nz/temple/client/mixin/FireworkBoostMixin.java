package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.SilentRotationState;
import nz.temple.client.modules.combat.AimAssist;
import nz.temple.client.modules.movement.MoveFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Elytra rocket boosts push along the silent (server) rotation while MoveFix applies. */
@Mixin(FireworkRocketEntity.class)
public class FireworkBoostMixin {
	@WrapOperation(method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 temple$boostDirection(LivingEntity shooter, Operation<Vec3> original) {
		return shooter == Minecraft.getInstance().player && MoveFix.shouldApplyRotationFix()
				? AimAssist.getRotationVector(SilentRotationState.tickServerPitch, SilentRotationState.tickServerYaw)
				: original.call(shooter);
	}
}
