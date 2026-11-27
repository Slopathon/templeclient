package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.world.SpeedMine;

@Mixin(MultiPlayerGameMode.class)
public abstract class MiningDelayMixin {
	@Shadow
	private int destroyDelay;
	@Shadow
	private float destroyProgress;
	@Shadow
	private boolean isDestroying;
	@Shadow
	private BlockPos destroyBlockPos;
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private Minecraft minecraft;
	@Inject(method = "continueDestroyBlock", at = @At("HEAD"))
	private void temple$breakEarly(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		float threshold = SpeedMine.breakThreshold();
		if (threshold >= 1f || !isDestroying || destroyBlockPos == null || !destroyBlockPos.equals(pos)
				|| minecraft.player == null || minecraft.level == null) {
			return;
		}
		BlockState state = minecraft.level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		float step = state.getDestroyProgress(minecraft.player, minecraft.level, pos);
		if (step > 0 && destroyProgress + step >= threshold) {
			destroyProgress = Math.max(destroyProgress, 1f - step + 1e-4f);
		}
	}
	@Inject(method = "tick", at = @At("HEAD"))
	private void temple$noDelay(CallbackInfo ci) {
		if (SpeedMine.removesDelay()) {
			destroyDelay = 0;
		}
	}
}
