package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import nz.temple.client.modules.render.GlintColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextureTransform.class)
public abstract class LegacyGlintSpeedMixin {
	@ModifyExpressionValue(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Ljava/lang/Double;doubleValue()D"))
	private static double temple$speed(double speed) {
		GlintColor glint = GlintColor.active();
		return glint == null ? speed : speed * glint.speedFactor();
	}
}
