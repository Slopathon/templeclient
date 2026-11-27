package nz.temple.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.render.Zoom;

/** Zoom divides the camera's finished field of view, so it is not held to the FOV option's range. */
//? if >=26 {
@Mixin(net.minecraft.client.Camera.class)
public abstract class CameraFovMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void temple$zoom(float partial, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Zoom.fov(cir.getReturnValue()));
	}
}
//?} else {
/*// 1.21.11 works the field of view out in the game renderer.
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class CameraFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void temple$zoom(net.minecraft.client.Camera camera, float partial, boolean useOption, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Zoom.fov(cir.getReturnValue()));
	}
}
*///?}
