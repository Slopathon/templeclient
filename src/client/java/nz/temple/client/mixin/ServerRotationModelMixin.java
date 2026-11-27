package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import nz.temple.client.modules.combat.AimAssist;
import nz.temple.client.util.SilentRotationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server rotations: while a module aims silently, your own player model looks where the server thinks you look (head
 * pitch here; body and head yaw are set by SilentRotationMixin). Visible in third person and to other players.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ServerRotationModelMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;"
			+ "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void temple$serverPitch(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		if (entity instanceof LocalPlayer && entity == Minecraft.getInstance().player && AimAssist.hasSilentRotation()) {
			state.xRot = SilentRotationState.modelPitch(partialTick);
		}
	}
}
