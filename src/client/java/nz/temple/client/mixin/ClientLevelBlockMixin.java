package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.WorldEvents;

@Mixin(ClientLevel.class)
public abstract class ClientLevelBlockMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void temple$blockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
		if (oldState != newState) {
			WorldEvents.blockChanged(pos, oldState, newState);
			nz.temple.client.render.grass.GrassRenderer.blockChanged(pos);
			nz.temple.client.render.light.LightingPass.blockChanged(pos);
		}
	}
}
