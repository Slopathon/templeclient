package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.cosmetic.CustomModels;
import nz.temple.client.render.HandChamsCollector;

@Mixin(AvatarRenderer.class)
public abstract class AvatarHandChamsMixin {
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void temple$armBegin(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm,
			boolean sleeve, CallbackInfo ci) {
		HandChamsCollector.armBegin();
		PlayerModel model = (PlayerModel) ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
		if (CustomModels.drawFirstPersonArm(arm == model.rightArm, arm, pose, collector, light)) {
			HandChamsCollector.armEnd();
			ci.cancel();
		}
	}
	@Inject(method = "renderHand", at = @At("RETURN"))
	private void temple$armEnd(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm,
			boolean sleeve, CallbackInfo ci) {
		HandChamsCollector.armEnd();
	}
}
