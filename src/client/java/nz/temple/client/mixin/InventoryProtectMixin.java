package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.player.InventoryTweaks;

@Mixin(MultiPlayerGameMode.class)
public abstract class InventoryProtectMixin {
	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void temple$protect(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
		InventoryTweaks tweaks = InventoryTweaks.active();
		if (tweaks == null || !tweaks.protect()) {
			return;
		}
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack stack = ItemStack.EMPTY;
		if (input == ContainerInput.THROW && slot >= 0 && slot < menu.slots.size()) {
			stack = menu.slots.get(slot).getItem();
		} else if (input == ContainerInput.PICKUP && slot == -999) {
			stack = menu.getCarried();
		}
		if (InventoryTweaks.valuable(stack)) {
			ci.cancel();
		}
	}
}
