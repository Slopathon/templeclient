package nz.temple.client.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.render.NoBlockRotation;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSeedMixin {
	@Inject(method = "getSeed(Lnet/minecraft/core/BlockPos;)J", at = @At("HEAD"), cancellable = true)
	private void temple$uniformModelSeed(BlockPos pos, CallbackInfoReturnable<Long> cir) {
		if (NoBlockRotation.active()) {
			cir.setReturnValue(42L);
		}
	}
}
