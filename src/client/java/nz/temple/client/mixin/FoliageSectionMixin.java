package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nz.temple.client.modules.render.RealisticGrass;
import nz.temple.client.modules.render.WavingFoliage;

/**
 * Tells Waving Foliage which block a chunk-building thread is on. Wraps the tesselate call rather than hooking the
 * vanilla block renderer, because Fabric's renderer API redirects that call to its own renderer; wrapping works on
 * top of that redirect, and the vertices are tagged where they reach the buffer (see FoliageVertexMixin).
 */
@Mixin(SectionCompiler.class)
public abstract class FoliageSectionMixin {
	//? if >=26 {
	@WrapOperation(method = "compile", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private void temple$foliageBlock(net.minecraft.client.renderer.block.ModelBlockRenderer renderer,
			net.minecraft.client.renderer.block.BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos,
			BlockState state, net.minecraft.client.renderer.block.dispatch.BlockStateModel model, long seed, Operation<Void> original) {
		temple$block(level, pos, state, y, () -> original.call(renderer, output, x, y, z, level, pos, state, model, seed));
	}
	//?} else {
	/*// 1.21.11 builds each block through the block render dispatcher, the pose stack already moved to it.
	@WrapOperation(method = "compile", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLjava/util/List;)V"))
	private void temple$foliageBlock(net.minecraft.client.renderer.block.BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos,
			BlockAndTintGetter level, com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean cull,
			java.util.List<net.minecraft.client.renderer.block.model.BlockModelPart> parts, Operation<Void> original) {
		temple$block(level, pos, state, pos.getY() & 15, () -> original.call(dispatcher, state, pos, level, pose, consumer, cull, parts));
	}
	*///?}

	private static void temple$block(BlockAndTintGetter level, BlockPos pos, BlockState state, float y, Runnable original) {
		if (RealisticGrass.hides(level, pos, state)) {
			// Realistic Grass draws blades where this plant stands.
			return;
		}
		boolean tagged = WavingFoliage.beginBlock(state, y, level, pos);
		try {
			original.run();
		} finally {
			if (tagged) {
				WavingFoliage.endBlock();
			}
		}
	}
}
