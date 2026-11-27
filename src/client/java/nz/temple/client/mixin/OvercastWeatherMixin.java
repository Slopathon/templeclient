package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.snow.Overcast;

/** Overcast, not raining: no vanilla rain or snow is drawn while the overcast sky is on. */
@Mixin(WeatherEffectRenderer.class)
public abstract class OvercastWeatherMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	//? if >=26 {
	private void temple$noPrecipitation(ClientLevel level, float partialTick, Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
	//?} else {
	/*private void temple$noPrecipitation(net.minecraft.world.level.Level level, int ticks, float partialTick, Vec3 camera,
			WeatherRenderState state, CallbackInfo ci) {
	*///?}
		if (Overcast.active()) {
			state.rainColumns.clear();
			state.snowColumns.clear();
			state.intensity = 0;
		}
	}
}
