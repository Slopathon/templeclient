package nz.temple.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.Ambience;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorAmbienceMixin {
	@Shadow
	@Final
	private LevelRenderState levelRenderState;

	//? if >=26 {
	@Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V", at = @At("TAIL"))
	private void temple$ambience(DeltaTracker delta, Camera camera, float partialTick, CallbackInfo ci) {
		Ambience ambience = Ambience.active();
		if (ambience == null) {
			return;
		}
		if (ambience.clouds().equals("Hidden")) {
			levelRenderState.cloudColor = 0;
		} else if (ambience.clouds().equals("Custom") && levelRenderState.cloudColor >>> 24 != 0) {
			levelRenderState.cloudColor = ambience.cloudColor(levelRenderState.cloudColor);
		}
		temple$skyAndWeather(ambience);
	}
	//?} else {
	/*// 1.21.11 extracts inside renderLevel: the sky and weather are in by the time the sky pass is added, and the cloud
	// colour goes straight to the clouds pass.
	private static final String CLOUDS = "Lnet/minecraft/client/renderer/LevelRenderer;addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;JFIF)V";

	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
	private void temple$ambience(CallbackInfo ci) {
		Ambience ambience = Ambience.active();
		if (ambience != null) {
			temple$skyAndWeather(ambience);
		}
	}

	@com.llamalad7.mixinextras.injector.v2.WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = CLOUDS))
	private boolean temple$clouds(net.minecraft.client.renderer.LevelRenderer self, com.mojang.blaze3d.framegraph.FrameGraphBuilder frame,
			net.minecraft.client.CloudStatus status, net.minecraft.world.phys.Vec3 camera, long time, float partial, int colour, float height) {
		Ambience ambience = Ambience.active();
		return ambience == null || !ambience.clouds().equals("Hidden");
	}

	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = CLOUDS), index = 5)
	private int temple$cloudColour(int colour) {
		Ambience ambience = Ambience.active();
		return ambience != null && ambience.clouds().equals("Custom") && colour >>> 24 != 0 ? ambience.cloudColor(colour) : colour;
	}
	*///?}

	private void temple$skyAndWeather(Ambience ambience) {
		if (levelRenderState.skyRenderState.skybox == DimensionType.Skybox.OVERWORLD) {
			if (ambience.customSky()) {
				//? if >=26.3 {
				/*int sky = ambience.skyColor();
				levelRenderState.skyRenderState.skyColor = new org.joml.Vector3f((sky >> 16 & 0xFF) / 255f, (sky >> 8 & 0xFF) / 255f,
						(sky & 0xFF) / 255f);
				*///?} else {
				levelRenderState.skyRenderState.skyColor = ambience.skyColor();
				//?}
			}
			levelRenderState.skyRenderState.starBrightness = Math.min(1,
					levelRenderState.skyRenderState.starBrightness * ambience.starMultiplier());
		}
		if (ambience.hideWeather()) {
			levelRenderState.weatherRenderState.rainColumns.clear();
			levelRenderState.weatherRenderState.snowColumns.clear();
			levelRenderState.weatherRenderState.intensity = 0;
		}
	}
}
