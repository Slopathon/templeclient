package nz.temple.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import nz.temple.client.modules.SchemaModules;
import nz.temple.client.modules.combat.TimeFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Time Freeze: adds ghost copies of frozen entities, drawn where they really are on the server. */
@Mixin(LevelExtractor.class)
public class GhostEntitiesMixin {
	@Inject(method = "extractVisibleEntities", at = @At("TAIL"))
	private void temple$timeFreezeGhosts(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState state, CallbackInfo ci) {
		if (SchemaModules.get("Time Freeze") instanceof TimeFreeze timeFreeze && timeFreeze.isEnabled()) {
			try {
				timeFreeze.addGhostRenderStates(state, delta.getGameTimeDeltaPartialTick(false));
			} catch (RuntimeException ignored) {
			}
		}
	}
}
