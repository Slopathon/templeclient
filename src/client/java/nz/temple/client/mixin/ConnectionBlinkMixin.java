package nz.temple.client.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.movement.Blink;
import nz.temple.client.modules.movement.NoFall;

@Mixin(Connection.class)
public class ConnectionBlinkMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void temple$blink(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		NoFall.outgoing(packet);
		if (Blink.intercept(packet)) {
			ci.cancel();
		}
	}
}
