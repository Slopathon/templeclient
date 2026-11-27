package nz.temple.smoke.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.smoke.Trailer;

@Mixin(Minecraft.class)
abstract class TrailerFrameMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void temple$frameStart(boolean advanceGameTime, CallbackInfo ci) {
		Trailer.frameStart((Minecraft) (Object) this);
	}
}
