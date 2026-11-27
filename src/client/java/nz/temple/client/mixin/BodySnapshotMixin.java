package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.BodySnapshot;
import nz.temple.client.render.EspStateAccess;

/** Records each living entity's body as it is submitted, for {@link BodySnapshot}'s ghosts. */
@Mixin(LivingEntityRenderer.class)
public abstract class BodySnapshotMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
	// 26.3 takes any UV mapping where a sprite went, and dropped the block-cracking overlay.
	//? if >=26.3 {
	/*private static final String SUBMIT_MODEL = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;"
			+ "Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/"
			+ "client/renderer/texture/UvMapping;I)V";
	*///?} else {
	private static final String SUBMIT_MODEL = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;"
			+ "Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/"
			+ "client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";
	//?}

	@Shadow
	protected M model;

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = SUBMIT_MODEL))
	@SuppressWarnings("unchecked")
	private void temple$snapshot(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		BodySnapshot.capture(((EspStateAccess) state).temple$entityId(), model, state, poseStack.last(),
				((LivingEntityRenderer<?, S, M>) (Object) this).getTextureLocation(state), state.lightCoords, camera.pos);
	}
}
