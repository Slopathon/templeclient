package nz.temple.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.UseSlowdown;

@Mixin(Entity.class)
public abstract class StuckInBlockMixin {
	@Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
	private void temple$unstuck(BlockState state, Vec3 multiplier, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && UseSlowdown.unstuck(player)) {
			ci.cancel();
		}
	}
}
