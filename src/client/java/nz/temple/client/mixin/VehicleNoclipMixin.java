package nz.temple.client.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class VehicleNoclipMixin {
	@Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
	private void temple$vehicleNoclip(LevelReader level, Entity entity, AABB before, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (entity.noPhysics && !(entity instanceof net.minecraft.world.entity.player.Player)) {
			cir.setReturnValue(false);
		}
	}
}
