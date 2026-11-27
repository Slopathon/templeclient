package nz.temple.client.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.RealisticWater;

/** Hides vanilla's underwater screen overlay while Realistic Water draws its own underwater view. */
@Mixin(ScreenEffectRenderer.class)
public abstract class WaterOverlayMixin {
	//? if >=26 {
	private static final String WATER = "submitWater";
	//?} else {
	/*private static final String WATER = "renderWater";
	*///?}

	@Inject(method = WATER, at = @At("HEAD"), cancellable = true)
	private static void temple$underwaterOverlay(CallbackInfo ci) {
		if (RealisticWater.underwaterView()) {
			ci.cancel();
		}
	}
}
