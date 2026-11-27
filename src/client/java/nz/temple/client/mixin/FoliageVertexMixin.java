package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nz.temple.client.modules.render.WavingFoliage;

@Mixin(BufferBuilder.class)
public abstract class FoliageVertexMixin {
	@ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int temple$foliageWeight(int color, float x, float y) {
		return WavingFoliage.tagging() ? WavingFoliage.tagVertex(color, y) : color;
	}
}
