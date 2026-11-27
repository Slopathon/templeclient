package nz.temple.client.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.movement.Noclip;

@Mixin(Player.class)
public abstract class PlayerNoclipMixin {
	@Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
			opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void temple$noclip(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (Noclip.applies(self) || nz.temple.client.modules.movement.BoatFly.applies(self)) {
			self.noPhysics = true;
		}
	}
}
