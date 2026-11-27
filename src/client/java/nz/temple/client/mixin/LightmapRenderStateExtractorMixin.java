package nz.temple.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.render.Fullbright;

/** Fullbright (full night vision), and a world effect's moonlight, sky light and torch colour, in the lightmap. */
//? if >=26 {
@Mixin(net.minecraft.client.renderer.LightmapRenderStateExtractor.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.LightTexture.class)
*///?}
public abstract class LightmapRenderStateExtractorMixin {
	private static boolean temple$fullbright() {
		Fullbright fullbright = TempleNzClient.modules() == null ? null : TempleNzClient.modules().get(Fullbright.class);
		return fullbright != null && fullbright.isEnabled() || nz.temple.client.modules.render.Xray.active();
	}

	//? if >=26 {
	@org.spongepowered.asm.mixin.injection.Inject(method = "extract", at = @At("TAIL"))
	private void templenz$fullbright(net.minecraft.client.renderer.state.LightmapRenderState state, float partialTick,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		boolean refresh = Fullbright.consumeRefresh();
		if (temple$fullbright()) {
			state.nightVisionEffectIntensity = 1f;
			state.nightVisionColor = net.minecraft.client.renderer.LightmapRenderStateExtractor.WHITE;
			state.needsUpdate = true;
		} else if (refresh) {
			state.needsUpdate = true;
		}
		nz.temple.client.render.WorldEffects.light(state);
	}
	//?} else {
	/*// 1.21.11 works the lightmap out inside updateLightTexture and writes it straight into its uniforms, in this order:
	// ambient light, sky, block, night vision, darkness, darken world, brightness (floats), then sky light colour and
	// ambient colour. It updates every frame, so there is nothing to refresh, and it has no torch colour to tint.
	private static final String PUT_FLOAT = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;";
	private static final String PUT_VEC3 = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;";

	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 1))
	private float temple$sky(float sky) {
		return nz.temple.client.render.WorldEffects.skyFactor(sky);
	}

	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 3))
	private float temple$nightVision(float nightVision) {
		Fullbright.consumeRefresh();
		return temple$fullbright() ? 1f : nightVision;
	}

	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_VEC3, ordinal = 0))
	private org.joml.Vector3fc temple$skyLight(org.joml.Vector3fc colour) {
		return nz.temple.client.render.WorldEffects.skyLight(colour);
	}

	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_VEC3, ordinal = 1))
	private org.joml.Vector3fc temple$ambient(org.joml.Vector3fc colour) {
		return nz.temple.client.render.WorldEffects.ambient(colour);
	}
	*///?}
}
