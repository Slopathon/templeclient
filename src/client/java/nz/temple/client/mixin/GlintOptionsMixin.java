package nz.temple.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.GlintColor;

/**
 * Glint Color's speed and strength, applied on top of the player's own glint options each frame. (On 1.21.11 this is
 * the strength; the speed is read where the glint moves, see LegacyGlintSpeedMixin.)
 */
@Mixin(GameRenderer.class)
public abstract class GlintOptionsMixin {
	//? if >=26 {
	@Inject(method = "extractOptions", at = @At("TAIL"))
	private void temple$glint(CallbackInfo ci) {
		GlintColor glint = GlintColor.active();
		if (glint == null) {
			return;
		}
		var options = ((GameRenderer) (Object) this).gameRenderState().optionsRenderState;
		options.glintSpeed *= glint.speedFactor();
		options.glintStrength = Math.min(1.0, options.glintStrength * glint.strengthFactor());
	}
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyArg(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/client/Camera;Z)V"),
			index = 2)
	private double temple$glint(double strength) {
		GlintColor glint = GlintColor.active();
		return glint == null ? strength : Math.min(1.0, strength * glint.strengthFactor());
	}
	*///?}
}
