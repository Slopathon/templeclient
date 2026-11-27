package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import nz.temple.client.modules.cosmetic.CustomModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class AvatarHandItemMixin {
	@Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			at = @At("HEAD"), cancellable = true)
	private void temple$avatarHand(AvatarRenderState state, HumanoidArm arm, PoseStack pose, CallbackInfo ci) {
		if (CustomModels.translateToHand((PlayerModel) (Object) this, state, arm, pose)) {
			ci.cancel();
		}
	}
}
