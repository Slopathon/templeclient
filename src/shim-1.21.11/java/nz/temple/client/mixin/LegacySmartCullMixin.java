package nz.temple.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Turns off vanilla smart culling while freecam is on (1.21.11). Sodium replaces cullTerrain with its own culling, so
 * TempleMixinPlugin skips this mixin when Sodium is installed; split from LegacyCameraCaptureMixin so the rest of that
 * mixin still applies.
 */
@Mixin(LevelRenderer.class)
public abstract class LegacySmartCullMixin {
	@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "cullTerrain", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/Minecraft;smartCull:Z"))
	private boolean temple$smartCull(boolean smartCull) {
		return smartCull && !nz.temple.client.util.DetachedCamera.freecam();
	}
}
