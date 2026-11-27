package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.render.Chams;

public final class ChamsLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private final LivingEntityRenderer<?, S, ?> renderer;
	public ChamsLayer(RenderLayerParent<S, M> parent, LivingEntityRenderer<?, S, ?> renderer) {
		super(parent);
		this.renderer = renderer;
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float u, float v) {
		if (state.isInvisible) {
			return;
		}
		int espFill = ((EspStateAccess) state).temple$espFill();
		Chams chams = TempleNzClient.modules() == null ? null : TempleNzClient.modules().get(Chams.class);
		boolean chamsOn = chams != null && chams.isEnabled() && ((EspStateAccess) state).temple$chams();
		if (espFill == 0 && !chamsOn) {
			return;
		}
		M model = getParentModel();
		Identifier texture;
		try {
			texture = renderer.getTextureLocation(state);
		} catch (RuntimeException e) {
			return;
		}
		if (texture == null) {
			return;
		}
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0f);
		if (espFill != 0) {
			nz.temple.client.compat.Submit.model(collector.order(12), model, state, pose, ChamsRenderTypes.flat(texture),
					light, overlay, espFill, null, state.outlineColor);
		}
		if (!chamsOn) {
			return;
		}
		if (chams.outlined()) {
			float grow = chams.outlineThousandths() / 1000f;
			int shellOverlay = overlay & 0xFFFF0000;
			int shellColor = chams.outlineArgb();
			collector.order(9).submitCustomGeometry(pose, ChamsRenderTypes.shell(texture, !chams.wallsOn()), (p, consumer) -> {
				model.setupAnim(state);
				ChamsShell.draw(p, model.root(), consumer, grow, light, shellOverlay, shellColor);
			});
		}
		if (chams.glowOn()) {
			nz.temple.client.compat.Submit.model(collector.order(13), model, state, pose, ChamsRenderTypes.glow(texture, !chams.wallsOn()),
					light, overlay & 0xFFFF0000, chams.glowArgb(), null, state.outlineColor);
		}
		int friend = ((EspStateAccess) state).temple$friendTint();
		if (friend != 0) {
			int argb = chams.withAlpha(friend);
			if (chams.wallsOn()) {
				nz.temple.client.compat.Submit.model(collector.order(10), model, state, pose, ChamsRenderTypes.flat(texture, false),
						light, overlay, argb, null, state.outlineColor);
			}
			nz.temple.client.compat.Submit.model(collector.order(11), model, state, pose, ChamsRenderTypes.flat(texture, true),
					light, overlay, argb, null, state.outlineColor);
			return;
		}
		int look = chams.energyLook();
		if (look != 0) {
			int energyOverlay = (overlay & 0xFFFF0000) | look;
			nz.temple.client.compat.Submit.model(collector.order(11), model, state, pose, ChamsRenderTypes.energy(texture, !chams.wallsOn()),
					light, energyOverlay, chams.visibleArgb(), null, state.outlineColor);
			return;
		}
		if (chams.imageOn()) {
			int imageOverlay = (overlay & 0xFFFF0000) | chams.imageStrength();
			RenderType picture = ChamsRenderTypes.image(texture, chams.imageId(), !chams.wallsOn());
			nz.temple.client.compat.Submit.model(collector.order(11), model, state, pose, picture,
					light, imageOverlay, chams.imageArgb(), null, state.outlineColor);
			return;
		}
		if (chams.wallsOn()) {
			nz.temple.client.compat.Submit.model(collector.order(10), model, state, pose, ChamsRenderTypes.flat(texture, false),
					light, overlay, chams.wallArgb(), null, state.outlineColor);
		}
		if (chams.visibleOn()) {
			nz.temple.client.compat.Submit.model(collector.order(11), model, state, pose, ChamsRenderTypes.flat(texture, true),
					light, overlay, chams.visibleArgb(), null, state.outlineColor);
		}
	}
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register(LivingEntityRenderer<?, ?, ?> renderer,
			LivingEntityRenderLayerRegistrationCallback.RegistrationHelper helper) {
		LivingEntityRenderer raw = renderer;
		helper.register(new ChamsLayer(raw, raw));
	}
}
