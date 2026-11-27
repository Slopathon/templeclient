package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class JointedLimbsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public JointedLimbsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || !((EspStateAccess) state).temple$self() || !JointedLimbs.active) {
			return;
		}
		JointedLimbs.submit(getParentModel(), state, pose, collector, light, LivingEntityRenderer.getOverlayCoords(state, 0f));
	}
}
