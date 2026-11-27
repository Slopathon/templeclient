package nz.temple.client.mixin;

import baritone.api.utils.Rotation;
import nz.temple.client.util.BaritoneBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "baritone.behavior.LookBehavior", remap = false)
public abstract class BaritoneLookMixin {
	@ModifyVariable(method = "updateTarget", at = @At("HEAD"), argsOnly = true, remap = false)
	private Rotation temple$humanize(Rotation rotation) {
		float[] r = BaritoneBridge.humanize(rotation.getYaw(), rotation.getPitch());
		return r == null ? rotation : new Rotation(r[0], r[1]);
	}
}
