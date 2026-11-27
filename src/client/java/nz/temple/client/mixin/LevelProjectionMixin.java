package nz.temple.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import nz.temple.client.render.LevelProjection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The projection the level is drawn with, view bobbing, hurt shake and nausea included, as vanilla hands it to the
 * GPU (see LevelProjection). 26.x only: 1.21.11 passes it to the level renderer, where LegacyCameraCaptureMixin takes it.
 */
@Mixin(GameRenderer.class)
public abstract class LevelProjectionMixin {
	@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
	private Matrix4f temple$levelProjection(Matrix4f projection) {
		// Before the level is drawn: see Gpu.reserveSequentialIndices.
		nz.temple.client.compat.Gpu.reserveSequentialIndices();
		// The camera state renderLevel reads, taken from the field rather than with MixinExtras @Local: the
		// parameter-annotation attribute @Local leaves crashes ZKM's saver (see FoliageVertexMixin).
		//? if >=26 {
		CameraRenderState camera = ((GameRenderer) (Object) this).gameRenderState().levelRenderState.cameraRenderState;
		//?} else {
		/*CameraRenderState camera = null;
		if (camera == null) return projection;
		*///?}
		if (camera.viewRotationMatrix != null && camera.projectionMatrix != null) {
			LevelProjection.capture(projection, camera.projectionMatrix, camera.viewRotationMatrix);
		}
		return projection;
	}
}
