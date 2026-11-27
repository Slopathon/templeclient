package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ExplosionFxMixin {
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void temple$explosionFx(ClientboundExplodePacket packet, CallbackInfo ci) {
		nz.temple.client.util.VanillaFx.onExplosion(packet.center());
		nz.temple.client.modules.player.FakePlayer fake = nz.temple.client.modules.player.FakePlayer.active();
		if (fake != null) {
			fake.explosion(packet.center(), packet.radius());
		}
	}
}
