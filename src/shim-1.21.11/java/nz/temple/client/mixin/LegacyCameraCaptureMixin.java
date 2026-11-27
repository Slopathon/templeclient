package nz.temple.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import nz.temple.client.compat.legacy.LegacyCamera;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LegacyCameraCaptureMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void temple$camera(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera, Matrix4f viewRotation,
			Matrix4f projection, Matrix4f cullProjection, GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
		nz.temple.client.compat.Gpu.reserveSequentialIndices();
		LegacyCamera.capture(camera, viewRotation, projection);
		nz.temple.client.render.LevelProjection.capture(projection, LegacyCamera.plainProjection(projection), viewRotation);
	}

	@Inject(method = "prepareCullFrustum", at = @At("RETURN"))
	private void temple$frustum(CallbackInfoReturnable<Frustum> cir) {
		LegacyCamera.captureFrustum(cir.getReturnValue());
	}
}
