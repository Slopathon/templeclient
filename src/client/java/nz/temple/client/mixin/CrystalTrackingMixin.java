package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import nz.temple.client.modules.combat.AutoCrystal;
import nz.temple.client.modules.combat.CrystalAura;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class CrystalTrackingMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
	private void temple$entityDeath(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (packet.getEventId() == 3 && mc.isSameThread() && mc.level != null) {
			try {
				AutoCrystal.onEntityDeath(packet.getEntity(mc.level));
			} catch (RuntimeException ignored) {
			}
		}
	}

	@Inject(method = "handleAddEntity", at = @At("TAIL"))
	private void temple$entitySpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		CrystalAura.onEntitySpawn(packet.getId());
	}
}
