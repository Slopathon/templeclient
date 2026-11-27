package nz.temple.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.world.Scaffold;

@Mixin(LocalPlayer.class)
public abstract class ScaffoldSprintMixin {
	@Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
	private void temple$noBridgeSprint(CallbackInfoReturnable<Boolean> cir) {
		if (Scaffold.blocksSprint((LocalPlayer) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
