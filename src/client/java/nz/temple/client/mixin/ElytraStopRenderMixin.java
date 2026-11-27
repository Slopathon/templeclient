package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import nz.temple.client.modules.SchemaModules;
import nz.temple.client.modules.combat.TimeFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Time Freeze: while elytra-stop is held your own player is drawn standing instead of gliding. */
@Mixin(AvatarRenderer.class)
public class ElytraStopRenderMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void temple$elytraStop(Avatar player, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || !(SchemaModules.get("Time Freeze") instanceof TimeFreeze tf) || !tf.isEnabled()
				|| !tf.isElytraStopHeld() || !state.isFallFlying && state.pose != Pose.FALL_FLYING) {
			return;
		}
		state.isFallFlying = false;
		state.pose = Pose.STANDING;
		state.fallFlyingTimeInTicks = 0.0F;
		state.shouldApplyFlyingYRot = false;
		state.flyingYRot = 0.0F;
	}
}
