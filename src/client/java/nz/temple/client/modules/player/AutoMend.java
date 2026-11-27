package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AutoMend extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting delay;
	private int cooldown;
	private int homeSlot = -1;

	public AutoMend() {
		super("Auto Mend", Category.PLAYER, "Throws experience bottles while you sneak to repair Mending gear.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Switches to the bottles normally, looks at your feet and throws them at an uneven pace while you sneak, then "
						+ "switches back."));
		delay = add(new NumberSetting("Throw delay", 4, 1, 20, 1, " t")
				.describe("Ticks between throws."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null && homeSlot >= 0 && !player.isShiftKeyDown()) {
			player.getInventory().setSelectedSlot(homeSlot);
			homeSlot = -1;
		}
		if (player == null || mc.gameMode == null || !player.isShiftKeyDown() || cooldown-- > 0) {
			return;
		}
		if (legit.get()) {
			int slot = nz.temple.client.util.Place.hotbarSlot(player, nz.temple.client.util.Place.item("experience_bottle"));
			if (slot < 0) {
				return;
			}
			if (homeSlot < 0 && player.getInventory().getSelectedSlot() != slot) {
				homeSlot = player.getInventory().getSelectedSlot();
			}
			if (nz.temple.client.util.LegitAim.useToward(player, slot, player.position(), 40, 10)
					== nz.temple.client.util.LegitAim.Step.DONE) {
				cooldown = Math.max((int) Math.round(delay.get()), nz.temple.client.util.Human.ticks(4, 7));
			}
			return;
		}
		for (int i = 0; i < 9; i++) {
			if (Inv.pathContains(player.getInventory().getItem(i), "experience_bottle")) {
				int previous = player.getInventory().getSelectedSlot();
				player.getInventory().setSelectedSlot(i);
				mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
				player.getInventory().setSelectedSlot(previous);
				cooldown = (int) Math.round(delay.get());
				return;
			}
		}
	}
}
