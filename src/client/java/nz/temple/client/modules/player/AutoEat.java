package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AutoEat extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting hungerAt;
	private final NumberSetting eatUntil;
	private int previousSlot = -1;
	private boolean eating;
	private int startAt;

	public AutoEat() {
		super("Auto Eat", Category.PLAYER, "Eats food from your hotbar when you are hungry.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Switches to the food normally, waits briefly, eats by holding your use key, then switches back."));
		hungerAt = add(new NumberSetting("Eat at", 16, 1, 19, 1, " hunger")
				.describe("Starts eating at this hunger level."));
		eatUntil = add(new NumberSetting("Eat until", 20, 2, 20, 1, " hunger")
				.describe("Stops eating at this hunger level."));
		instance = this;
		on(TickEvent.class, e -> tick());
	}

	private static AutoEat instance;

	public static boolean holdsBite() {
		AutoEat self = instance;
		return self != null && self.isEnabled() && self.eating && !self.legit.get();
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) {
			return;
		}
		int food = player.getFoodData().getFoodLevel();
		if (eating) {
			if (food >= eatUntil.get() || foodSlot(player) < 0) {
				stop(player);
			} else {
				keepEating(mc, player);
			}
			return;
		}
		if (food <= hungerAt.get()) {
			int slot = foodSlot(player);
			if (slot >= 0) {
				previousSlot = player.getInventory().getSelectedSlot();
				player.getInventory().setSelectedSlot(slot);
				eating = true;
				startAt = player.tickCount + (legit.get() ? nz.temple.client.util.Human.reactionTicks() : 0);
				keepEating(mc, player);
			}
		}
	}

	private void keepEating(Minecraft mc, LocalPlayer player) {
		if (legit.get()) {
			if (player.tickCount >= startAt) {
				nz.temple.client.util.Keys.press(mc.options.keyUse, 2);
			}
			return;
		}
		if (!player.isUsingItem()) {
			mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		}
	}

	private void stop(LocalPlayer player) {
		nz.temple.client.util.Keys.release(Minecraft.getInstance().options.keyUse);
		if (player.isUsingItem()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.gameMode != null) {
				mc.gameMode.releaseUsingItem(player);
			} else {
				player.stopUsingItem();
			}
		}
		if (previousSlot >= 0) {
			player.getInventory().setSelectedSlot(previousSlot);
			previousSlot = -1;
		}
		eating = false;
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			stop(player);
		}
	}

	private static int foodSlot(LocalPlayer player) {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
				return i;
			}
		}
		return -1;
	}
}
