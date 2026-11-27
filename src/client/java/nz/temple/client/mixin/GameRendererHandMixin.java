package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.Animations;
import nz.temple.client.util.DetachedCamera;

/** Hides the first-person hand (Freecam, Freelook, Animations), cancels the hurt camera shake, and shakes for Jump Circles' Earthquake. */
@Mixin(GameRenderer.class)
public abstract class GameRendererHandMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void temple$hideHand(CallbackInfo ci) {
		Animations animations = Animations.active();
		if (DetachedCamera.handHidden() || animations != null && animations.hideHand()) {
			ci.cancel();
		}
	}

	/** Jump Circles' Earthquake camera shake rides on the hurt bob, ahead of anything that cancels it. */
	@Inject(method = "bobHurt", at = @At("HEAD"))
	//? if >=26 {
	private void temple$quakeShake(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
	//?} else {
	/*private void temple$quakeShake(PoseStack pose, float partial, CallbackInfo ci) {
	*///?}
		nz.temple.client.render.GroundQuake.shake(pose);
	}

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	//? if >=26 {
	private void temple$noHurtShake(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
	//?} else {
	/*private void temple$noHurtShake(PoseStack pose, float partial, CallbackInfo ci) {
	*///?}
		Animations animations = Animations.active();
		if (animations != null && animations.noHurtShake()) {
			ci.cancel();
		}
	}
}
