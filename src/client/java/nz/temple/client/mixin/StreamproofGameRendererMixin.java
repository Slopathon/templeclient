package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.streamproof.StreamproofRenderer;

/** Streamproof's hooks into the frame: the second GUI extraction, the world recording and the clean GUI. */
@Mixin(GameRenderer.class)
public abstract class StreamproofGameRendererMixin {
	// 26.3 dropped the frame timing from renderLevel (and from render).
	//? if >=26.3 {
	/*private static final String RENDER_LEVEL = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V";
	*///?} else {
	private static final String RENDER_LEVEL = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V";
	//?}

	//? if >=26 {
	@WrapOperation(method = "extract", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V"))
	private void temple$extractTwice(Gui gui, DeltaTracker delta, boolean level, boolean loaded, Operation<Void> original) {
		StreamproofRenderer.extract(gui, () -> original.call(gui, delta, level, loaded));
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
	private void temple$beforeGui(CallbackInfo ci) {
		StreamproofRenderer.beforeGui((GameRenderer) (Object) this);
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V",
			shift = At.Shift.AFTER))
	private void temple$afterGui(CallbackInfo ci) {
		StreamproofRenderer.afterGui((GameRenderer) (Object) this);
	}
	//?} else {
	/*// 1.21.11 builds the GUI in render itself, call by call, into the state it has just reset: the clean GUI is built
	// first, into its own state, by StreamproofRenderer.extractClean.
	@Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;reset()V"))
	private void temple$extractClean(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
		StreamproofRenderer.extractClean(delta);
	}

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
	private void temple$beforeGui(CallbackInfo ci) {
		StreamproofRenderer.beforeGui((GameRenderer) (Object) this);
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;incrementFrameNumber()V",
			shift = At.Shift.AFTER))
	private void temple$afterGui(CallbackInfo ci) {
		StreamproofRenderer.afterGui((GameRenderer) (Object) this);
	}
	*///?}

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = RENDER_LEVEL))
	private void temple$beforeLevel(CallbackInfo ci) {
		StreamproofRenderer.beforeLevel((GameRenderer) (Object) this);
	}

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = RENDER_LEVEL, shift = At.Shift.AFTER))
	private void temple$afterLevel(CallbackInfo ci) {
		StreamproofRenderer.afterLevel();
	}
}
