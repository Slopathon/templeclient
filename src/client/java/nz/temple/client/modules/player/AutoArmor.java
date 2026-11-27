package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AutoArmor extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting delay;
	private int cooldown;

	public AutoArmor() {
		super("Auto Armor", Category.PLAYER, "Equips armour from your inventory into empty slots as soon as you pick it up.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Only works with your inventory open, at roughly six uneven clicks per second."));
		delay = add(new NumberSetting("Click delay", 3, 1, 20, 1, " t")
				.describe("Ticks between clicks."));
		hideWhenLegit(legit, delay);
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !(player.containerMenu instanceof InventoryMenu) || cooldown-- > 0) {
			return;
		}
		if (legit.get() && (!nz.temple.client.util.LegitInv.inventoryOpen() || !nz.temple.client.util.LegitInv.ready())) {
			return;
		}
		String[] kinds = {"helmet", "chestplate", "leggings", "boots"};
		for (int a = 0; a < 4; a++) {
			ItemStack worn = player.containerMenu.slots.get(5 + a).getItem();
			if (!worn.isEmpty()) {
				continue;
			}
			String kind = kinds[a];
			int found = Inv.findMain(s -> !s.isEmpty() && Inv.path(s).contains(kind));
			if (found < 0 && a == 0) {
				found = Inv.findMain(s -> Inv.pathContains(s, "turtle_helmet"));
			}
			if (found < 0 && a == 1) {
				found = Inv.findMain(s -> Inv.pathContains(s, "elytra"));
			}
			if (found >= 0) {
				Inv.quickMove(found);
				nz.temple.client.util.LegitInv.clicked();
				cooldown = (int) Math.round(delay.get());
				return;
			}
		}
	}
}
