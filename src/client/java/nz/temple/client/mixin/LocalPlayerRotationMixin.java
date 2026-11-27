package nz.temple.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.Rotations;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerRotationMixin {
	@Unique private float temple$realYaw;
	@Unique private float temple$realPitch;
	@Unique private boolean temple$swapped;

	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void temple$applyRotation(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		temple$swapped = Rotations.consume();
		if (!temple$swapped) {
			float turns = Rotations.wholeTurns(self.getYRot());
			if (turns != 0) {
				self.setYRot(self.getYRot() + turns);
				self.yRotO += turns;
			}
		}
		if (temple$swapped) {
			temple$realYaw = self.getYRot();
			temple$realPitch = self.getXRot();
			self.setYRot(Rotations.requestedYaw());
			self.setXRot(Rotations.requestedPitch());
		}
	}

	@Inject(method = "sendPosition", at = @At("RETURN"))
	private void temple$restoreRotation(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		Rotations.sent(self.getYRot(), self.getXRot());
		if (temple$swapped) {
			self.setYRot(temple$realYaw);
			self.setXRot(temple$realPitch);
			temple$swapped = false;
		}
	}
}
