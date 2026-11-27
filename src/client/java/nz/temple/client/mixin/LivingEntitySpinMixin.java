package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.cosmetic.Spinbot;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntitySpinMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;"
			+ "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void temple$spin(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		Spinbot spinbot = Spinbot.instance();
		if (spinbot == null || !spinbot.isEnabled() || entity != Minecraft.getInstance().player) {
			return;
		}
		state.bodyRot = spinbot.renderYaw(partialTick);
		state.yRot = 0;
		state.xRot = spinbot.renderPitch();
	}
}
