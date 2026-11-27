package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.render.WorldEffects;

@Mixin(Level.class)
public abstract class WorldEffectWeatherMixin {
	@Inject(method = "getRainLevel(F)F", at = @At("RETURN"), cancellable = true, order = 1100)
	private void temple$effectRain(float partialTick, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel) {
			WorldEffects.Effect e = WorldEffects.current();
			if (e != null) {
				cir.setReturnValue(e.rain() * Math.min(1, WorldEffects.intensity()));
			}
		}
	}
	@Inject(method = "getThunderLevel(F)F", at = @At("RETURN"), cancellable = true)
	private void temple$effectThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel) {
			WorldEffects.Effect e = WorldEffects.current();
			if (e != null) {
				cir.setReturnValue(e.thunder() * Math.min(1, WorldEffects.intensity()));
			}
		}
	}
}
