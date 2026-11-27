package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class ChestStealer extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting delay;

	public ChestStealer() {
		super("Chest Stealer", Category.PLAYER, "Takes everything out of chests when you open them.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Waits a reaction delay after the chest opens, then takes items with uneven gaps, only while the chest is open."));
		delay = add(new NumberSetting("Click delay", 1, 0, 10, 1, " t")
				.describe("Ticks between clicks. 0 is instant; raise it if the server skips items."));
		hideWhenLegit(legit, delay);
		on(TickEvent.class, e -> tick());
	}

	private int cooldown;
	private int seenMenu = -1;

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.containerMenu == null || player.containerMenu instanceof InventoryMenu) {
			return;
		}
		if (cooldown-- > 0) {
			return;
		}
		if (legit.get()) {
			if (!nz.temple.client.util.LegitInv.containerOpen()) {
				return;
			}
			if (player.containerMenu.containerId != seenMenu) {
				seenMenu = player.containerMenu.containerId;
				nz.temple.client.util.LegitInv.clickedAfterLooking();
				return;
			}
			if (!nz.temple.client.util.LegitInv.ready()) {
				return;
			}
		}
		int containerSlots = player.containerMenu.slots.size() - 36;
		for (int i = 0; i < containerSlots; i++) {
			if (!player.containerMenu.slots.get(i).getItem().isEmpty()) {
				Inv.quickMove(i);
				nz.temple.client.util.LegitInv.clicked();
				cooldown = (int) Math.round(delay.get());
				return;
			}
		}
	}
}
