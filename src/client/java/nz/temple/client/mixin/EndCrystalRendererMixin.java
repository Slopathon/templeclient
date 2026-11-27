package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.render.CrystalChams;

/** Crystal Chams: size, tint, glow, bounce and base plate for end crystals. */
@Mixin(EndCrystalRenderer.class)
public class EndCrystalRendererMixin {
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
			+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void temple$size(PoseStack pose, float x, float y, float z) {
		CrystalChams chams = CrystalChams.active();
		float s = chams == null ? 1 : chams.size();
		pose.scale(x * s, y * s, z * s);
	}

	// 26.3 dropped the block-cracking overlay from this call.
	//? if >=26.3 {
	/*@SuppressWarnings({"rawtypes", "unchecked"})
	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel("
			+ "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/resources/Identifier;III)V"))
	private void temple$tint(SubmitNodeCollector collector, Model model, Object state, PoseStack pose, Identifier texture,
			int light, int overlay, int outline) {
		temple$tint(collector, model, state, pose, texture, light, overlay, outline,
				() -> collector.submitModel(model, state, pose, texture, light, overlay, outline));
	}
	*///?} else if >=26 {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel("
			+ "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void temple$tint(SubmitNodeCollector collector, Model model, Object state, PoseStack pose, Identifier texture,
			int light, int overlay, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		temple$tint(collector, model, state, pose, texture, light, overlay, outline,
				() -> collector.submitModel(model, state, pose, texture, light, overlay, outline, crumbling));
	}
	//?} else {
	/*// 1.21.11 passes the render type rather than the texture.
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel("
			+ "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void temple$tint(SubmitNodeCollector collector, Model model, Object state, PoseStack pose, RenderType type,
			int light, int overlay, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		temple$tint(collector, model, state, pose, TEXTURE, light, overlay, outline,
				() -> collector.submitModel(model, state, pose, type, light, overlay, outline, crumbling));
	}
	*///?}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void temple$tint(SubmitNodeCollector collector, Model model, Object state, PoseStack pose, Identifier texture,
			int light, int overlay, int outline, Runnable original) {
		CrystalChams chams = CrystalChams.active();
		if (chams != null && chams.replacesModel()) {
			// Drawn as a 3D model by Crystal Chams itself.
			return;
		}
		if (chams == null || !chams.recolors()) {
			original.run();
			return;
		}
		RenderType type;
		if (chams.glow()) {
			type = RenderTypes.entityTranslucentEmissive(texture);
			light = 0xF000F0;
		} else {
			type = chams.translucent() ? RenderTypes.entityTranslucent(texture) : model.renderType(texture);
		}
		int id = state instanceof EndCrystalRenderState s ? (int) (s.x * 7 + s.z * 13) : 0;
		nz.temple.client.compat.Submit.model(collector, model, state, pose, type, light, overlay, chams.tint(id), null, outline);
	}

	@Inject(method = "getY", at = @At("RETURN"), cancellable = true)
	private static void temple$bounce(float age, CallbackInfoReturnable<Float> cir) {
		CrystalChams chams = CrystalChams.active();
		if (chams != null) {
			cir.setReturnValue(cir.getReturnValueF() * chams.bounce());
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;"
			+ "Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V", at = @At("TAIL"))
	private void temple$base(EndCrystal crystal, EndCrystalRenderState state, float partialTicks, CallbackInfo ci) {
		CrystalChams chams = CrystalChams.active();
		Boolean show = chams == null ? null : chams.showBase();
		if (show != null) {
			state.showsBottom = show;
		}
	}
}
