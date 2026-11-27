package nz.temple.client.mixin;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.RealisticWater;

/**
 * Marks water vertices for Realistic Water as chunks are built: the kind of water (still, flowing and which way,
 * falling) goes into the alpha of each vertex's colour, which vanilla always writes as 255 for fluids.
 */
//? if >=26 {
@Mixin(net.minecraft.client.renderer.block.FluidRenderer.class)
public abstract class WaterTagMixin {
	@Inject(method = "tesselate", at = @At("HEAD"))
	private void temple$waterKind(BlockAndTintGetter level, BlockPos pos, net.minecraft.client.renderer.block.FluidRenderer.Output output,
			BlockState blockState, FluidState fluidState, CallbackInfo ci) {
		// Set on every call (never cleared), so a fluid drawn through another renderer cannot leave a stale tag behind.
		RealisticWater.beginFluid(level, pos, fluidState);
	}

	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "vertex", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int temple$waterTag(int color) {
		return RealisticWater.tagVertex(color);
	}
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.block.LiquidBlockRenderer.class)
public abstract class WaterTagMixin {
	@Inject(method = "tesselate", at = @At("HEAD"))
	private void temple$waterKind(BlockAndTintGetter level, BlockPos pos, com.mojang.blaze3d.vertex.VertexConsumer output,
			BlockState blockState, FluidState fluidState, CallbackInfo ci) {
		RealisticWater.beginFluid(level, pos, fluidState);
	}

	// 1.21.11 passes the colour as floats and the alpha as a constant 1.
	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "vertex", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), index = 3)
	private float temple$waterTag(float alpha) {
		return (RealisticWater.tagVertex(0xFFFFFFFF) >>> 24) / 255f;
	}
}
*///?}
