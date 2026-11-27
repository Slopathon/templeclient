package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AutoGapple extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting health;
	private int previousSlot = -1;
	private int startAt;

	public AutoGapple() {
		super("Auto Gapple", Category.PLAYER, "Eats a golden apple when your health is low.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Switches to the golden apple normally, waits a short reaction delay, then eats it by holding your use key."));
		health = add(new NumberSetting("Health", 12, 2, 19, 1, " hp")
				.describe("Eats a golden apple at or below this health."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) {
			return;
		}
		if (player.isUsingItem()) {
			if (legit.get() && previousSlot >= 0) {
				nz.temple.client.util.Keys.press(mc.options.keyUse, 2);
			}
			if (player.getHealth() > health.get() + 4 && previousSlot >= 0) {
				player.stopUsingItem();
				player.getInventory().setSelectedSlot(previousSlot);
				previousSlot = -1;
			}
			return;
		}
		if (previousSlot >= 0 && player.getHealth() > health.get()) {
			if (legit.get()) {
				nz.temple.client.util.Keys.release(mc.options.keyUse);
			}
			player.getInventory().setSelectedSlot(previousSlot);
			previousSlot = -1;
		}
		if (player.getHealth() > health.get()) {
			return;
		}
		for (int i = 0; i < 9; i++) {
			if (Inv.pathContains(player.getInventory().getItem(i), "golden_apple")) {
				if (legit.get()) {
					if (player.getInventory().getSelectedSlot() != i) {
						if (previousSlot < 0) {
							previousSlot = player.getInventory().getSelectedSlot();
						}
						player.getInventory().setSelectedSlot(i);
						startAt = player.tickCount + nz.temple.client.util.Human.reactionTicks();
						return;
					}
					if (player.tickCount < startAt) {
						return;
					}
					nz.temple.client.util.Keys.press(mc.options.keyUse, 2);
					mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
					return;
				}
				if (previousSlot < 0) {
					previousSlot = player.getInventory().getSelectedSlot();
				}
				player.getInventory().setSelectedSlot(i);
				mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
				return;
			}
		}
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && previousSlot >= 0) {
			if (player.isUsingItem()) {
				player.stopUsingItem();
			}
			player.getInventory().setSelectedSlot(previousSlot);
		}
		previousSlot = -1;
	}
}
