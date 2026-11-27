package nz.temple.client.util;

import net.minecraft.world.entity.player.Inventory;

public final class InventoryHelper {
	private InventoryHelper() {
	}

	public static int getSelected(Inventory inv) {
		return inv.getSelectedSlot();
	}

	public static void setSelected(Inventory inv, int slot) {
		inv.setSelectedSlot(slot);
	}
}
