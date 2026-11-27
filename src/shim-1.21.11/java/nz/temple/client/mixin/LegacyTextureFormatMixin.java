package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.textures.TextureFormat;
import nz.temple.client.compat.legacy.LegacyTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GlDevice.class)
public abstract class LegacyTextureFormatMixin {
	private static final String CREATE = "createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;";
	private static GpuFormat temple$real(TextureFormat format) {
		GpuFormat real = LegacyTextures.pending();
		return real != null && format.hasColorAspect() ? real : null;
	}
	@WrapOperation(method = CREATE, at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/opengl/GlConst;toGlInternalId(Lcom/mojang/blaze3d/textures/TextureFormat;)I"))
	private int temple$internal(TextureFormat format, Operation<Integer> original) {
		GpuFormat real = temple$real(format);
		return real != null ? real.glInternalFormat() : original.call(format);
	}
	@WrapOperation(method = CREATE, at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/opengl/GlConst;toGlExternalId(Lcom/mojang/blaze3d/textures/TextureFormat;)I"))
	private int temple$external(TextureFormat format, Operation<Integer> original) {
		GpuFormat real = temple$real(format);
		return real != null ? real.glFormat() : original.call(format);
	}
	@WrapOperation(method = CREATE, at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/opengl/GlConst;toGlType(Lcom/mojang/blaze3d/textures/TextureFormat;)I"))
	private int temple$type(TextureFormat format, Operation<Integer> original) {
		GpuFormat real = temple$real(format);
		return real != null ? real.glType() : original.call(format);
	}
}
