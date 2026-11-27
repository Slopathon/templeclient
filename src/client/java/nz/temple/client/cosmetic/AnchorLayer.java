package nz.temple.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class AnchorLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public AnchorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}
	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		PlayerModel model = getParentModel();
		pose.pushPose();
		model.root().translateAndRotate(pose);
		net.minecraft.client.model.geom.ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		org.joml.Matrix4f[] out = new org.joml.Matrix4f[parts.length];
		for (int i = 0; i < parts.length; i++) {
			pose.pushPose();
			parts[i].translateAndRotate(pose);
			out[i] = new org.joml.Matrix4f(pose.last().pose());
			pose.popPose();
		}
		pose.popPose();
		Anchor.capture(state.id, out, Minecraft.getInstance().gameRenderer.mainCamera().position());
	}
}
