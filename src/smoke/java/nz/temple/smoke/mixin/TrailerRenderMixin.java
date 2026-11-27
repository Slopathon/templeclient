package nz.temple.smoke.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.smoke.Trailer;

@Mixin(GameRenderer.class)
abstract class TrailerRenderMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void temple$frameEnd(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		Trailer.frameEnd(Minecraft.getInstance());
	}
}
