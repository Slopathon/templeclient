package nz.temple.client.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.render.PlaceAnimation;

@Mixin(RenderSectionRegion.class)
public abstract class SectionRegionHideMixin {
	@Shadow
	public abstract LevelLightEngine getLightEngine();
	@Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
	private void temple$hideAnimating(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		if (PlaceAnimation.hidden(pos) || nz.temple.client.util.BlockHider.hidden(pos)) {
			cir.setReturnValue(Blocks.AIR.defaultBlockState());
		}
	}
	public int getBrightness(LightLayer layer, BlockPos pos) {
		LayerLightEventListener light = getLightEngine().getLayerListener(layer);
		if (!PlaceAnimation.hidden(pos) && !nz.temple.client.util.BlockHider.hidden(pos)) {
			return light.getLightValue(pos);
		}
		int best = 0;
		for (Direction d : Direction.values()) {
			int n = light.getLightValue(pos.relative(d));
			best = Math.max(best, layer == LightLayer.SKY && d == Direction.UP ? n : n - 1);
		}
		return Math.max(0, best);
	}
	@Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
	private void temple$xray(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		if (nz.temple.client.modules.render.Xray.hides(cir.getReturnValue())) {
			cir.setReturnValue(Blocks.AIR.defaultBlockState());
		}
	}
}
