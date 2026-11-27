package nz.temple.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import nz.temple.client.render.AmbienceFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ambience's fog colour and distances (see AmbienceFog). */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	//? if >=26 {
	@Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
			at = @At("RETURN"))
	private void temple$ambienceFog(Camera camera, int renderDistance, DeltaTracker delta, float darken,
			ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		AmbienceFog.apply(camera, cir.getReturnValue(), cir.getReturnValue().color);
	}
	//?} else {
	/*// 1.21.11 keeps the colour apart from FogData and writes both straight into the fog buffer.
	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
	private void temple$ambienceFog(FogRenderer self, java.nio.ByteBuffer buffer, int offset, org.joml.Vector4f color, float environmentalStart,
			float environmentalEnd, float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudEnd,
			com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
			Camera camera, int renderDistance, net.minecraft.client.DeltaTracker delta, float darkness,
			net.minecraft.client.multiplayer.ClientLevel level) {
		FogData fog = new FogData();
		fog.environmentalStart = environmentalStart;
		fog.environmentalEnd = environmentalEnd;
		fog.renderDistanceStart = renderDistanceStart;
		fog.renderDistanceEnd = renderDistanceEnd;
		fog.skyEnd = skyEnd;
		fog.cloudEnd = cloudEnd;
		AmbienceFog.apply(camera, fog, color);
		original.call(self, buffer, offset, color, fog.environmentalStart, fog.environmentalEnd, fog.renderDistanceStart,
				fog.renderDistanceEnd, fog.skyEnd, fog.cloudEnd);
	}
	*///?}
}
