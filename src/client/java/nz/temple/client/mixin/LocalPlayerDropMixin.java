package nz.temple.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.player.InventoryTweaks;

/** Inventory Tweaks drop protection for the drop key outside screens. */
// 26.3 moved dropping from the player to the game mode.
//? if >=26.3 {
/*@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public abstract class LocalPlayerDropMixin {
	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	private void temple$protectDrop(LocalPlayer player, boolean wholeStack,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (protects(player)) {
			ci.cancel();
		}
	}
*///?} else {
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerDropMixin {
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void temple$protectDrop(boolean wholeStack, CallbackInfoReturnable<Boolean> cir) {
		if (protects((LocalPlayer) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
//?}

	private static boolean protects(LocalPlayer player) {
		InventoryTweaks tweaks = InventoryTweaks.active();
		return tweaks != null && tweaks.protect() && InventoryTweaks.valuable(player.getMainHandItem());
	}
}
