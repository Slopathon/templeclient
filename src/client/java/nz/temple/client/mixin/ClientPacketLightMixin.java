package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.ServerLight;

/** Copies the block light the server sends into {@link ServerLight}, for Sus Chunk Finder's amethyst detection. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketLightMixin {
	// Chunk and light update packets both apply their light here.
	@Inject(method = "applyLightData", at = @At("HEAD"))
	private void temple$serverLight(int x, int z, ClientboundLightUpdatePacketData data, boolean update, CallbackInfo ci) {
		var level = Minecraft.getInstance().level;
		if (!ServerLight.enabled() || data == null || level == null) {
			return;
		}
		// Light sections start one below the world's lowest block section. The data became a record in 26.3.
		//? if >=26.3 {
		/*ServerLight.ingest(x, z, data.blockYMask(), data.emptyBlockYMask(), data.blockUpdates(), level.getMinSectionY() - 1);
		*///?} else {
		ServerLight.ingest(x, z, data.getBlockYMask(), data.getEmptyBlockYMask(), data.getBlockUpdates(), level.getMinSectionY() - 1);
		//?}
	}
}
