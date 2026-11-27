package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import nz.temple.client.compat.legacy.LegacyCamera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class LegacyGameRendererProjectionMixin {
	@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;getProjectionMatrix(F)Lorg/joml/Matrix4f;"))
	private Matrix4f temple$plainProjection(Matrix4f projection) {
		LegacyCamera.capturePlain(projection);
		return projection;
	}
}
