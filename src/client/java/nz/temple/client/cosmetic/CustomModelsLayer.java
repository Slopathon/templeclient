package nz.temple.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import nz.temple.client.modules.cosmetic.CustomModels;

public final class CustomModelsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public CustomModelsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		CustomModels.submit(getParentModel(), state, pose, collector, light);
	}
}
