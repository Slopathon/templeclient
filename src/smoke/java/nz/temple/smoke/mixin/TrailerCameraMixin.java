package nz.temple.smoke.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.smoke.Trailer;

@Mixin(Camera.class)
abstract class TrailerCameraMixin {
	@Shadow private boolean detached;
	@Shadow protected abstract void setPosition(Vec3 pos);
	@Shadow protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void temple$trailerCamera(CallbackInfo ci) {
		Trailer.Cam cam = Trailer.camera();
		if (cam != null) {
			detached = cam.detached();
			setRotation(cam.yaw(), cam.pitch());
			setPosition(cam.position());
		}
	}

	@Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
	private void temple$trailerFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		Trailer.Cam cam = Trailer.camera();
		if (cam != null) {
			cir.setReturnValue(cam.fov());
		}
	}
}
