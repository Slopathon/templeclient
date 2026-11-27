package nz.temple.client.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.cosmetic.Emotes;
import nz.temple.client.modules.cosmetic.WalkStyle;
import nz.temple.client.render.EspStateAccess;
import nz.temple.client.render.JointedLimbs;

@Mixin(PlayerModel.class)
public abstract class EmotePoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("HEAD"))
	private void temple$restoreLimbs(AvatarRenderState state, CallbackInfo ci) {
		JointedLimbs.hideVanilla((PlayerModel) (Object) this, false);
	}
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void temple$emote(AvatarRenderState state, CallbackInfo ci) {
		if (!((EspStateAccess) state).temple$self()) {
			if (((EspStateAccess) state).temple$mine()) {
				WalkStyle.pose((PlayerModel) (Object) this, state);
			}
			return;
		}
		PlayerModel model = (PlayerModel) (Object) this;
		JointedLimbs.reset();
		if (!Emotes.pose(model)) {
			WalkStyle.pose(model, state);
		}
		if (JointedLimbs.active) {
			JointedLimbs.hideVanilla(model, true);
		}
	}
}
