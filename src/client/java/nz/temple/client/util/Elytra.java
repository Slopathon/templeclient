package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

public final class Elytra {
	private static int lastTap;
	private static long lastRocket;

	private Elytra() {
	}

	public static boolean worn(LocalPlayer player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
	}

	public static void open(LocalPlayer player) {
		if (!worn(player) || player.onGround() || player.isFallFlying() || player.isInWater()
				|| player.getDeltaMovement().y > -0.05 || player.tickCount - lastTap < 4) {
			return;
		}
		lastTap = player.tickCount;
		Keys.press(Minecraft.getInstance().options.keyJump, 1);
	}

	public static void boost(LocalPlayer player, double below) {
		if (!player.isFallFlying() || player.getDeltaMovement().length() >= below
				|| System.currentTimeMillis() - lastRocket < 1500) {
			return;
		}
		int slot = Place.hotbarSlot(player, Place.item("firework_rocket"));
		if (slot >= 0 && LegitAim.useToward(player, slot, null, 0, 0) == LegitAim.Step.DONE) {
			lastRocket = System.currentTimeMillis();
		}
	}
}
