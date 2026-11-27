package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import nz.temple.client.util.DetachedCamera;
import nz.temple.client.util.LegitAim;
import nz.temple.client.util.Place;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;

public final class KeyPearl extends Module {
	private final BoolSetting legit;
	private int returnTo = -1;
	private int returnAt;
	private int startedAt;

	public KeyPearl() {
		super("Key Pearl", Category.PLAYER, "Throws an ender pearl from your hotbar in your look direction and switches back. Bind it to a "
				+ "key.");
		legit = add(new BoolSetting("Legit", false)
				.describe("Switches to the pearl normally, throws it the next tick and switches back shortly after, instead of doing all "
						+ "three in one tick."));
		on(TickEvent.class, e -> tick());
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null || DetachedCamera.freecam()) {
			setEnabled(false);
			return;
		}
		if (legit.get()) {
			returnTo = mc.player.getInventory().getSelectedSlot();
			returnAt = 0;
			startedAt = mc.player.tickCount;
			return;
		}
		var inventory = mc.player.getInventory();
		for (int i = 0; i < 9; i++) {
			if (!inventory.getItem(i).is(Items.ENDER_PEARL)) continue;
			int before = inventory.getSelectedSlot();
			try {
				inventory.setSelectedSlot(i);
				mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
			} finally {
				inventory.setSelectedSlot(before);
				mc.player.connection.send(new ServerboundSetCarriedItemPacket(before));
			}
			break;
		}
		setEnabled(false);
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (!legit.get() || mc.player == null || returnTo < 0) {
			return;
		}
		if (returnAt == 0) {
			int slot = Place.hotbarSlot(mc.player, Place.item("ender_pearl"));
			LegitAim.Step step = LegitAim.useToward(mc.player, slot, null, 0, 0);
			if (step != LegitAim.Step.WORKING || mc.player.tickCount - startedAt > 20) {
				returnAt = mc.player.tickCount + nz.temple.client.util.Human.ticks(3, 7);
			}
			return;
		}
		if (mc.player.tickCount >= returnAt) {
			mc.player.getInventory().setSelectedSlot(returnTo);
			returnTo = -1;
			setEnabled(false);
		}
	}
}
