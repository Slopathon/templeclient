package nz.temple.smoke.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.smoke.Trailer;

@Mixin(Hud.class)
abstract class TrailerHudMixin {
	@Inject(method = {"extractEffects", "extractChat"}, at = @At("HEAD"), cancellable = true)
	private void temple$cleanHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (Trailer.cleanHud()) {
			ci.cancel();
		}
	}

	@Inject(method = {"extractHotbarAndDecorations", "extractCrosshair"}, at = @At("HEAD"), cancellable = true)
	private void temple$cleanHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (Trailer.cleanHud() && !Trailer.vanillaHud) {
			ci.cancel();
		}
	}
}
