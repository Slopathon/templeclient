package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class Refill extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting delay;
	private final BoolSetting pearls;
	private final BoolSetting potions;
	private final BoolSetting food;
	private int cooldown;

	public Refill() {
		super("Refill", Category.PLAYER, "Refills pearls, potions and food from your inventory into empty hotbar slots.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Only works with your inventory open, at roughly six uneven clicks per second."));
		delay = add(new NumberSetting("Delay", 5, 1, 40, 1, " t")
				.describe("Ticks between clicks."));
		group();
		pearls = add(new BoolSetting("Pearls", true).describe("Restock ender pearls."));
		potions = add(new BoolSetting("Potions", true).describe("Restock splash potions."));
		food = add(new BoolSetting("Food", true).describe("Restock golden apples and food."));
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
		if (tryRefill(player, "ender_pearl", pearls.get())) {
			return;
		}
		if (tryRefill(player, "splash_potion", potions.get())) {
			return;
		}
		if (food.get()) {
			tryRefill(player, "golden_apple", true);
		}
	}

	private boolean tryRefill(LocalPlayer player, String kind, boolean on) {
		if (!on) {
			return false;
		}
		if (Inv.findHotbar(s -> Inv.pathContains(s, kind)) >= 0) {
			return false;
		}
		int found = Inv.findMain(s -> Inv.pathContains(s, kind) && !s.isEmpty());
		if (found < 0) {
			return false;
		}
		ItemStack moving = player.containerMenu.slots.get(found).getItem();
		Inv.quickMove(found);
		nz.temple.client.util.LegitInv.clicked();
		cooldown = (int) Math.round(delay.get()) + (moving.getCount() > 1 ? 0 : 0);
		return true;
	}
}
