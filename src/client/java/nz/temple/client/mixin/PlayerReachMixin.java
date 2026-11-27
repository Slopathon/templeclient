package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.SilentRotationState;
import nz.temple.client.modules.combat.AimAssist;
import nz.temple.client.modules.combat.Reach;
import nz.temple.client.modules.movement.MoveFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerReachMixin {
	@ModifyExpressionValue(method = "travel",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 temple$swimLook(Vec3 original) {
		return (Object) this == Minecraft.getInstance().player && SilentRotationState.silentActive && MoveFix.shouldApplyRotationFix()
				? AimAssist.getRotationVector(SilentRotationState.tickServerPitch, SilentRotationState.tickServerYaw)
				: original;
	}

	@Inject(method = "blockInteractionRange", at = @At("HEAD"), cancellable = true)
	private void temple$blockReach(CallbackInfoReturnable<Double> cir) {
		if ((Object) this == Minecraft.getInstance().player) {
			float r = Reach.getBlockReach();
			if (r > 0.0F) {
				cir.setReturnValue((double) r);
			}
		}
	}

	@Inject(method = "entityInteractionRange", at = @At("HEAD"), cancellable = true)
	private void temple$entityReach(CallbackInfoReturnable<Double> cir) {
		if ((Object) this == Minecraft.getInstance().player) {
			float r = Reach.getEntityReach();
			if (r > 0.0F) {
				cir.setReturnValue((double) r);
			}
		}
	}
}
