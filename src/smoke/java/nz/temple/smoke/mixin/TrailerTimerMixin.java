package nz.temple.smoke.mixin;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.smoke.Trailer;

@Mixin(DeltaTracker.Timer.class)
abstract class TrailerTimerMixin {
	@Shadow private float deltaTicks;
	@Shadow private float deltaTickResidual;
	@Shadow private float realtimeDeltaTicks;
	@Shadow private long lastMs;
	@Shadow private long lastUiMs;
	@Shadow @Final private float msPerTick;
	@Shadow @Final private FloatUnaryOperator targetMsptProvider;
	@Unique private double temple$lastGame = Double.NaN;
	@Unique private double temple$lastUi = Double.NaN;
	@Inject(method = "advanceGameTime", at = @At("HEAD"), cancellable = true)
	private void temple$exactGame(long currentMs, CallbackInfoReturnable<Integer> cir) {
		if (!Trailer.exactTime()) {
			temple$lastGame = Double.NaN;
			return;
		}
		double now = Util.getNanos() / 1e6;
		double last = Double.isNaN(temple$lastGame) ? lastMs : temple$lastGame;
		deltaTicks = (float) ((now - last) / targetMsptProvider.apply(msPerTick));
		temple$lastGame = now;
		lastMs = currentMs;
		deltaTickResidual += deltaTicks;
		int ticks = (int) deltaTickResidual;
		deltaTickResidual -= ticks;
		cir.setReturnValue(ticks);
	}
	@Inject(method = "advanceRealTime", at = @At("HEAD"), cancellable = true)
	private void temple$exactReal(long currentMs, CallbackInfo ci) {
		if (!Trailer.exactTime()) {
			temple$lastUi = Double.NaN;
			return;
		}
		double now = Util.getNanos() / 1e6;
		double last = Double.isNaN(temple$lastUi) ? lastUiMs : temple$lastUi;
		realtimeDeltaTicks = (float) ((now - last) / msPerTick);
		temple$lastUi = now;
		lastUiMs = currentMs;
		ci.cancel();
	}
}
