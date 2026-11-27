package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class LegitInv {
	private static long nextClick;
	private LegitInv() {
	}

	public static boolean inventoryOpen() {
		var screen = Minecraft.getInstance().gui.screen();
		return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
	}
	public static boolean containerOpen() {
		return Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>;
	}
	public static boolean ready() {
		return System.currentTimeMillis() >= nextClick;
	}
	public static void clicked() {
		nextClick = System.currentTimeMillis() + Human.clickGapMs();
	}
	public static void clickedAfterLooking() {
		nextClick = System.currentTimeMillis() + Human.clickGapMs() + Human.reactionMs();
	}
}
