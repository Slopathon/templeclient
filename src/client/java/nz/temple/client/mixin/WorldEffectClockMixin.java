package nz.temple.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.render.WorldEffects;

/**
 * A world effect's time of day: the client's clocks report the effect's hour, so the sun, moon, sky, fog and light all
 * follow it. Only the client's clocks; a singleplayer server keeps the real time.
 */
// 26.3 hands out a clock instance per clock, which reports its own time.
//? if >=26.3 {
/*@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public abstract class WorldEffectClockMixin {
	@Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
	private void temple$effectTime(CallbackInfoReturnable<Long> cir) {
*///?} else if >=26 {
@Mixin(net.minecraft.client.ClientClockManager.class)
public abstract class WorldEffectClockMixin {
	@Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
	private void temple$effectTime(CallbackInfoReturnable<Long> cir) {
//?} else {
/*// 1.21.11 has no clocks: the time of day is the client level's day time.
@Mixin(net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.class)
public abstract class WorldEffectClockMixin {
	@Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
	private void temple$effectTime(CallbackInfoReturnable<Long> cir) {
*///?}
		long real = cir.getReturnValueJ();
		long shown = WorldEffects.clock(real);
		if (shown != real) {
			cir.setReturnValue(shown);
		}
	}
}
