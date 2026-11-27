package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.BodySnapshot;

@Mixin(LevelRenderer.class)
public abstract class GhostSubmitMixin {
	@Inject(method = "submitEntities", at = @At("TAIL"))
	private void temple$ghosts(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector output, CallbackInfo ci) {
		BodySnapshot.submitGhosts(output, state.cameraRenderState.pos);
	}
}
