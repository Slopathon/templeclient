package nz.temple.client.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import nz.temple.client.event.PacketReceiveEvents;
import nz.temple.client.event.PacketReceiveEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Incoming packets for Backtrack, Fake Lag, Time Freeze and Auto Jump Reset. */
@Mixin(Connection.class)
public class PacketReceiveMixin {
	@Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
	private static <T extends PacketListener> void temple$receive(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
		if (PacketReceiveEvents.fire(new PacketReceiveEvent(packet)).isCancelled()) {
			ci.cancel();
		}
	}
}
