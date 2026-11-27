package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import nz.temple.client.modules.render.HandChams;
import nz.temple.client.render.EspStateAccess;
import nz.temple.client.render.HandChamsCollector;

@Mixin(LivingEntityRenderer.class)
public abstract class SelfHandChamsMixin {
	@WrapMethod(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V")
	private void temple$selfHandChams(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
			CameraRenderState camera, Operation<Void> original) {
		HandChams chams = HandChams.active();
		EspStateAccess access = (EspStateAccess) state;
		if (chams != null && chams.thirdPersonOn() && access.temple$self() && !access.temple$chams() && !state.isInvisible) {
			collector = HandChamsCollector.wrapBody(collector, chams);
		}
		original.call(state, pose, collector, camera);
	}
}
