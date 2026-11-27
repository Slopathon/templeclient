package nz.temple.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import nz.temple.client.hud.AzuraHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides vanilla's effect icons in the top-right corner while the HUD's own effects list shows them. */
@Mixin(Hud.class)
public abstract class GlassHotbarMixin {
	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void temple$hideEffects(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
		if (AzuraHud.effectsActive()) {
			ci.cancel();
		}
	}
}
