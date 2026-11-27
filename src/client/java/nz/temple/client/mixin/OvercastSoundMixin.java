package nz.temple.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.snow.Overcast;

/** Overcast, not raining: no rain sounds or splash particles while the overcast sky is on. */
//? if >=26 {
@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
public abstract class OvercastSoundMixin {
	@Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true)
	private void temple$quiet(CallbackInfo ci) {
		if (Overcast.active()) {
			ci.cancel();
		}
	}
}
//?} else {
/*// 1.21.11 plays the rain's sounds and splashes from the weather renderer.
@Mixin(net.minecraft.client.renderer.WeatherEffectRenderer.class)
public abstract class OvercastSoundMixin {
	@Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
	private void temple$quiet(CallbackInfo ci) {
		if (Overcast.active()) {
			ci.cancel();
		}
	}
}
*///?}
