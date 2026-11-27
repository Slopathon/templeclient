package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.cosmetic.Emotes;
import nz.temple.client.render.EspStateAccess;

@Mixin(AvatarRenderer.class)
public abstract class EmoteBodyMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void temple$emoteBody(AvatarRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
		if (((EspStateAccess) state).temple$self()) {
			Emotes.body(pose);
		}
	}
}
