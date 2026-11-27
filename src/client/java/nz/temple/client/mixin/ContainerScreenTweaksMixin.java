package nz.temple.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.player.InventoryTweaks;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenTweaksMixin {
	@Shadow
	protected Slot hoveredSlot;
	@Shadow
	@Final
	protected AbstractContainerMenu menu;
	@Unique
	private Slot temple$lastDragged;

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void temple$middleSort(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		temple$lastDragged = hoveredSlot;
		InventoryTweaks tweaks = InventoryTweaks.active();
		Minecraft mc = Minecraft.getInstance();
		if (tweaks == null || !tweaks.middleSort() || event.button() != InputConstants.MOUSE_BUTTON_MIDDLE
				|| hoveredSlot == null || mc.player == null || mc.player.isCreative()) {
			return;
		}
		if (tweaks.sort(menu, hoveredSlot)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"))
	private void temple$shiftDrag(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
		InventoryTweaks tweaks = InventoryTweaks.active();
		Minecraft mc = Minecraft.getInstance();
		if (tweaks == null || !tweaks.shiftDrag() || event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || !mc.hasShiftDown() || mc.player == null || mc.gameMode == null) {
			return;
		}
		Slot slot = hoveredSlot;
		if (slot == null || slot == temple$lastDragged || !slot.hasItem() || !menu.getCarried().isEmpty()) {
			return;
		}
		temple$lastDragged = slot;
		mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
	}
}
