package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ContainerInput;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AutoTotem extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private static final int OFFHAND = 45;
	private final NumberSetting delay;
	private int step = -1;
	private int slot = -1;
	private int cooldown;

	public AutoTotem() {
		super("Auto Totem", Category.PLAYER, "Moves a totem into your offhand when the current one pops.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Only works with your inventory open, at roughly six uneven clicks per second."));
		delay = add(new NumberSetting("Click delay", 2, 1, 10, 1, " t")
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
		if (step < 0) {
			if (!player.getOffhandItem().isEmpty() && Inv.pathContains(player.getOffhandItem(), "totem")) {
				return;
			}
			slot = Inv.findMain(s -> Inv.pathContains(s, "totem"));
			if (slot < 0) {
				slot = Inv.findHotbar(s -> Inv.pathContains(s, "totem"));
			}
			if (slot >= 0) {
				step = 0;
			}
			return;
		}
		if (step == 0) {
			Inv.click(slot, 0, ContainerInput.PICKUP);
			step = 1;
		} else if (step == 1) {
			Inv.click(OFFHAND, 0, ContainerInput.PICKUP);
			step = 2;
		} else {
			Inv.click(slot, 0, ContainerInput.PICKUP);
			step = -1;
			slot = -1;
		}
		nz.temple.client.util.LegitInv.clicked();
		cooldown = (int) Math.round(delay.get());
	}

	@Override
	protected void onDisable() {
		step = -1;
		slot = -1;
	}
}
