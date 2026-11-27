package nz.temple.client.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.AimSmoother;
import nz.temple.client.util.DetachedCamera;
import nz.temple.client.modules.render.Zoom;

@Mixin(MouseHandler.class)
public class CameraMouseMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void temple$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (nz.temple.client.modules.cosmetic.Emotes.wheelScroll(vertical != 0 ? vertical : -horizontal)
				|| Zoom.scroll(vertical != 0 ? vertical : -horizontal)) {
			ci.cancel();
		}
	}

	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void temple$turn(LocalPlayer player, double dx, double dy) {
		double zoom = Zoom.mouseScale();
		dx *= zoom;
		dy *= zoom;
		if (nz.temple.client.modules.cosmetic.Emotes.wheelTurn(dx, dy)) return;
		if (DetachedCamera.followsPlayer()) {
			player.turn(dx, dy);
			DetachedCamera.followPlayer();
		} else if (DetachedCamera.ownerActive()) DetachedCamera.turn(dx, dy);
		else player.turn(dx, dy);
	}

	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void temple$smoothAim(CallbackInfo ci) {
		AimSmoother.frame();
	}
}
