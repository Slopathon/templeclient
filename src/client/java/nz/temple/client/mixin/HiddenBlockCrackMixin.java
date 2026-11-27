package nz.temple.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.PlaceAnimation;
import nz.temple.client.util.BlockHider;

/**
 * Vanilla's crack overlay is the whole block model drawn again with a darkening decal. While a block is left out of
 * the mesh (Break Animation's Chip style, Place Animation) that decal would sit on nothing: it z-fights with the
 * pieces drawn in its place and hangs in the holes they leave, which reads as a flickering shadow. Drop it there.
 */
@Mixin(LevelExtractor.class)
public abstract class HiddenBlockCrackMixin {
	@Inject(method = "extractBlockDestroyAnimation", at = @At("TAIL"))
	private void temple$noCrackOnHidden(Camera camera, LevelRenderState state, CallbackInfo ci) {
		if (!state.blockBreakingRenderStates.isEmpty()) {
			if (nz.temple.client.util.VanillaFx.hidesVanillaCracks()) {
				// Break Animation draws the mining progress itself.
				state.blockBreakingRenderStates.clear();
				return;
			}
			//? if >=26 {
			state.blockBreakingRenderStates.removeIf(s -> BlockHider.hidden(s.blockPos()) || PlaceAnimation.hidden(s.blockPos()));
			//?} else {
			/*state.blockBreakingRenderStates.removeIf(s -> BlockHider.hidden(s.blockPos) || PlaceAnimation.hidden(s.blockPos));
			*///?}
		}
	}
}
