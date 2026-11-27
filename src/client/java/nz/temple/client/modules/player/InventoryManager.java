package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

import java.util.Set;

public final class InventoryManager extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private static final Set<String> JUNK = Set.of("rotten_flesh", "poisonous_potato", "spider_eye", "bone",
			"arrow", "stick", "string", "feather", "wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds",
			"torchflower_seeds", "pitcher_pod", "sugar_cane", "cactus", "cobweb", "scaffolding", "dirt", "gravel");
	private final NumberSetting delay;
	private final nz.temple.setting.EntitySelection drop;
	private final nz.temple.setting.BoolSetting keepHotbar;
	private int cooldown;

	public InventoryManager() {
		super("Inventory Manager", Category.PLAYER, "Drops selected items from your inventory automatically.");
		drop = add(new nz.temple.setting.EntitySelection("Items to drop", JUNK.stream().map(id -> "minecraft:" + id).toList()).items()
				.describe("Items in this list are dropped. It starts with common junk; add or remove items as needed."));
		keepHotbar = add(new nz.temple.setting.BoolSetting("Keep hotbar", true)
				.describe("Never drops items from your hotbar."));
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Only works with your inventory open, at roughly six uneven clicks per second."));
		delay = add(new NumberSetting("Drop delay", 2, 1, 20, 1, " t")
				.describe("Ticks between drops."));
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
		java.util.function.Predicate<net.minecraft.world.item.ItemStack> junk = s -> !s.isEmpty()
				&& drop.has(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
		int found = Inv.findMain(junk);
		if (found < 0 && !keepHotbar.get()) {
			found = Inv.findHotbar(junk);
		}
		if (found >= 0) {
			Inv.click(found, 1, ContainerInput.THROW);
			nz.temple.client.util.LegitInv.clicked();
			cooldown = (int) Math.round(delay.get());
		}
	}
}
