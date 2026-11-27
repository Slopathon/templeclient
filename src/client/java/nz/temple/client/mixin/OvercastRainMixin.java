package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.render.snow.Overcast;

@Mixin(Level.class)
public abstract class OvercastRainMixin {
	@Inject(method = "getRainLevel(F)F", at = @At("RETURN"), cancellable = true)
	private void temple$overcast(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof ClientLevel) || !Overcast.active()) {
			return;
		}
		float overcast = Overcast.amount();
		if (overcast > cir.getReturnValueF()) {
			cir.setReturnValue(overcast);
		}
	}
}
