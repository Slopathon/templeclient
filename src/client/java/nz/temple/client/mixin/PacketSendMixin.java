package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import nz.temple.client.event.PacketSendEvents;
import nz.temple.client.event.PacketSendEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class PacketSendMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void temple$send(Packet<?> packet, CallbackInfo ci) {
		if (PacketSendEvents.fire(new PacketSendEvent(packet)).isCancelled()) {
			ci.cancel();
		}
	}
}
