package nz.temple.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import nz.temple.client.util.SilentRotationState;
import nz.temple.client.event.PlayerTickEvents;
import nz.temple.client.modules.combat.AimAssist;
import nz.temple.client.util.RotationGcd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silent rotations: for the length of the player tick the player faces the server-side aim (GCD-snapped like real
 * mouse movement), so movement and the rotation packet use it; the camera rotation is restored afterwards.
 */
@Mixin(LocalPlayer.class)
public abstract class SilentRotationMixin {
	@Shadow
	public float yBob;
	@Shadow
	public float yBobO;
	@Shadow
	public float xBob;
	@Shadow
	public float xBobO;
	@Unique
	private boolean temple$applied;
	@Unique
	private float temple$serverYaw;
	@Unique
	private float temple$serverPitch;
	@Unique
	private float temple$yaw;
	@Unique
	private float temple$pitch;
	@Unique
	private float temple$renderYaw;
	@Unique
	private float temple$renderPitch;
	@Unique
	private float temple$headYaw;
	@Unique
	private float temple$bodyYaw;
	@Unique
	private boolean temple$haveLast;
	@Unique
	private float temple$lastYaw;
	@Unique
	private float temple$lastPitch;

	@Inject(method = "tick", at = @At("HEAD"))
	private void temple$silentRotationHead(CallbackInfo ci) {
		temple$applied = false;
		SilentRotationState.silentActive = false;
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!AimAssist.hasSilentRotation()) {
			temple$haveLast = true;
			temple$lastYaw = self.getYRot();
			temple$lastPitch = self.getXRot();
			return;
		}
		if (!temple$haveLast) {
			temple$lastYaw = self.getYRot();
			temple$lastPitch = self.getXRot();
			temple$haveLast = true;
		}
		float wantYaw = AimAssist.getSilentServerYaw();
		float wantPitch = Mth.clamp(AimAssist.getSilentServerPitch(), -90.0F, 90.0F);
		if (!Float.isFinite(wantYaw) || !Float.isFinite(wantPitch)) {
			temple$lastYaw = self.getYRot();
			temple$lastPitch = self.getXRot();
			return;
		}
		temple$serverYaw = RotationGcd.snapYaw(temple$lastYaw, wantYaw);
		temple$serverPitch = RotationGcd.snapPitch(temple$lastPitch, wantPitch);
		temple$lastYaw = temple$serverYaw;
		temple$lastPitch = temple$serverPitch;
		AimAssist.commitSnappedRotation(temple$serverYaw, temple$serverPitch);
		temple$yaw = self.getYRot();
		temple$pitch = self.getXRot();
		temple$renderYaw = yBob;
		temple$renderPitch = xBob;
		temple$headYaw = self.yHeadRot;
		temple$bodyYaw = self.yBodyRot;
		SilentRotationState.tickServerYaw = temple$serverYaw;
		SilentRotationState.tickServerPitch = temple$serverPitch;
		SilentRotationState.realYaw = temple$yaw;
		SilentRotationState.silentActive = true;
		self.setYRot(temple$serverYaw);
		SilentRotationState.lastModelPitch = SilentRotationState.modelPitch(1.0F);
		self.setXRot(temple$serverPitch);
		SilentRotationState.modelPitch = temple$serverPitch;
		temple$applied = true;
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void temple$silentRotationReturn(CallbackInfo ci) {
		PlayerTickEvents.fire();
		SilentRotationState.silentActive = false;
		if (!temple$applied) {
			return;
		}
		temple$applied = false;
		LocalPlayer self = (LocalPlayer) (Object) this;
		float realYaw = RotationGcd.unwrap(temple$serverYaw, temple$yaw);
		float shift = realYaw - temple$yaw;
		self.setYRot(realYaw);
		self.setXRot(temple$pitch);
		self.yRotO = realYaw;
		self.xRotO = temple$pitch;
		float modelYaw = temple$serverYaw;
		self.yHeadRotO = temple$headYaw;
		self.yHeadRot = modelYaw;
		float body = temple$bodyYaw + Mth.wrapDegrees(modelYaw - temple$bodyYaw) * 0.22F;
		float d = Mth.wrapDegrees(modelYaw - body);
		if (d > 50.0F) {
			body = modelYaw - 50.0F;
		} else if (d < -50.0F) {
			body = modelYaw + 50.0F;
		}
		self.yBodyRotO = temple$bodyYaw;
		self.yBodyRot = body;
		yBobO = temple$renderYaw + shift;
		xBobO = temple$renderPitch;
		yBob = temple$renderYaw + (temple$yaw - temple$renderYaw) * 0.5F + shift;
		xBob = temple$renderPitch + (temple$pitch - temple$renderPitch) * 0.5F;
	}
}
