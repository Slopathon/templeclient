package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out Minecraft's block particles where the client draws its own: the burst when a block breaks (Block Effects,
 * or Break Animation's shatter on a block you mined) and the chips while mining (Break Animation).
 */
@Mixin(ClientLevel.class)
public abstract class VanillaBreakParticlesMixin {
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void temple$noBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (nz.temple.client.util.VanillaFx.replacesBreakParticles(pos)) {
			ci.cancel();
		}
	}

	// 26.3 split the mining effect into particles and a sound; only the particles are replaced.
	//? if >=26.3 {
	/*@Inject(method = "addBreakingParticles", at = @At("HEAD"), cancellable = true)
	*///?} else {
	@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
	//?}
	private void temple$noMiningParticles(CallbackInfo ci) {
		if (nz.temple.client.util.VanillaFx.hidesVanillaCracks()) {
			ci.cancel();
		}
	}
}
