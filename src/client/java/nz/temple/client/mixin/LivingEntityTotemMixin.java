package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.TotemTracker;

@Mixin(ClientPacketListener.class)
public abstract class LivingEntityTotemMixin {
	@Shadow
	private ClientLevel level;
	@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER))
	private void temple$totemPopped(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (packet.getEventId() == EntityEvent.PROTECTED_FROM_DEATH && level != null
				&& packet.getEntity(level) instanceof LivingEntity living) {
			TotemTracker.popped(living);
		}
	}
}
