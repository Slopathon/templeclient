package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.DetachedCamera;

@Mixin(MultiPlayerGameMode.class)
public class CombatInteractionMixin {
	@Unique private int temple$restoreSlot = -1;
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void temple$attack(Player player, Entity target, CallbackInfo ci) {
		temple$restoreSlot = -1;
		if (DetachedCamera.freecam()) { ci.cancel(); return; }
		if (player != Minecraft.getInstance().player || !(player instanceof LocalPlayer local)) return;
		nz.temple.client.modules.combat.AttributeSwap swap = nz.temple.client.modules.combat.AttributeSwap.instance();
		if (temple$restoreSlot < 0 && swap != null) temple$restoreSlot = swap.select(local, target);
	}
	@Inject(method = "attack", at = @At("TAIL"))
	private void temple$afterAttack(Player player, Entity target, CallbackInfo ci) {
		if (temple$restoreSlot >= 0 && player instanceof LocalPlayer local) {
			local.getInventory().setSelectedSlot(temple$restoreSlot);
			local.connection.send(new ServerboundSetCarriedItemPacket(temple$restoreSlot));
			temple$restoreSlot = -1;
		}
		if (player == Minecraft.getInstance().player && player instanceof LocalPlayer local) {
			nz.temple.client.util.CombatTracker.attacked(target);
		}
		if (player == Minecraft.getInstance().player) {
			nz.temple.client.util.VanillaFx.attacked(target);
		}
	}
}
