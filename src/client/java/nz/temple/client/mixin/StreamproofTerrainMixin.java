//? if <26.3 {
// 26.3 restructured terrain drawing (no output targets, the pass comes from the caller); this Sodium workaround is
// left out there (see mixins_excluded).
package nz.temple.client.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import nz.temple.client.render.streamproof.StreamproofRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Streamproof with Sodium: Sodium draws the terrain itself, setting state with raw graphics calls that the pass
 * recorder never sees, so its terrain cannot be played back onto the clean frame and the recording had none (plain
 * sky by day, black underground or at night). The terrain is drawn a second time instead, straight onto the clean
 * frame. The low priority puts this hook ahead of Sodium's own on this method, which cancels it: at the default
 * priority Sodium's ran first and this one never did.
 */
@Mixin(value = ChunkSectionsToRender.class, priority = 500)
public abstract class StreamproofTerrainMixin {
	@Shadow
	public abstract void renderGroup(ChunkSectionLayerGroup group, GpuSampler sampler);

	@Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true)
	private void temple$terrainTwice(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
		if (StreamproofRenderer.redrawsTerrain(group.outputTarget())) {
			StreamproofRenderer.drawTerrainTwice(() -> renderGroup(group, sampler));
			ci.cancel();
		}
	}
}
//?}
