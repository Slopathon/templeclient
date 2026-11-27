package nz.temple.client.mixin;

import io.netty.channel.ChannelFutureListener;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.Privacy;

import java.util.List;

/** Filters advertised Fabric channels before they reach the server connection. */
@Mixin(Connection.class)
public class ConnectionPayloadFilterMixin {
	@Unique
	private static final ThreadLocal<Boolean> temple$replaying = ThreadLocal.withInitial(() -> false);

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void temple$filterPayload(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (temple$replaying.get() || !Privacy.filtersPayloads()
				|| !(packet instanceof ServerboundCustomPayloadPacket customPacket)) {
			return;
		}
		CustomPacketPayload payload = customPacket.payload();
		if (payload instanceof BrandPayload) {
			return;
		}
		if (payload instanceof RegistrationPayload registration) {
			if (Privacy.spoofsVanillaBrand()) {
				ci.cancel();
				for (net.minecraft.resources.Identifier channel : registration.channels()) {
					Privacy.blockedProbe(channel);
				}
				return;
			}
			List<net.minecraft.resources.Identifier> allowed = registration.channels().stream()
					.filter(Privacy::allowsRegisteredChannel)
					.toList();
			if (allowed.equals(registration.channels())) {
				return;
			}
			ci.cancel();
			registration.channels().stream()
					.filter(channel -> !allowed.contains(channel))
					.forEach(Privacy::blockedProbe);
			if (!allowed.isEmpty()) {
				temple$replaying.set(true);
				try {
					//? if >=26 {
					((Connection) (Object) this).send(new ServerboundCustomPayloadPacket(
							new RegistrationPayload(registration.type(), allowed)));
					//?} else {
					/*((Connection) (Object) this).send(new ServerboundCustomPayloadPacket(
							new RegistrationPayload(registration.id(), allowed)));
					*///?}
				} finally {
					temple$replaying.set(false);
				}
			}
			return;
		}
		if (!Privacy.allowsPayload(payload.type().id())) {
			ci.cancel();
			Privacy.blockedProbe(payload.type().id());
		}
	}
}
