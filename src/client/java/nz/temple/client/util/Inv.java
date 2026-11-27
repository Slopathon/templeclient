package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public final class Inv {
	private Inv() {
	}

	public static boolean click(int slot, int button, ContainerInput type) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || player.containerMenu == null) {
			return false;
		}
		mc.gameMode.handleContainerInput(player.containerMenu.containerId, slot, button, type, player);
		return true;
	}

	public static boolean quickMove(int slot) {
		return click(slot, 0, ContainerInput.QUICK_MOVE);
	}

	public static boolean pickup(int slot) {
		return click(slot, 0, ContainerInput.PICKUP);
	}

	public static boolean swapHotbar(int slot, int hotbar) {
		return hotbar >= 0 && hotbar < 9 && click(slot, hotbar, ContainerInput.SWAP);
	}

	public static int findMain(Predicate<ItemStack> test) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.containerMenu == null) {
			return -1;
		}
		for (int i = 9; i <= 35; i++) {
			if (i < player.containerMenu.slots.size() && test.test(player.containerMenu.slots.get(i).getItem())) {
				return i;
			}
		}
		return -1;
	}

	public static String path(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	public static boolean pathContains(ItemStack stack, String part) {
		return path(stack).contains(part);
	}

	public static int findHotbar(Predicate<ItemStack> test) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.containerMenu == null) {
			return -1;
		}
		for (int i = 36; i <= 44; i++) {
			if (i < player.containerMenu.slots.size() && test.test(player.containerMenu.slots.get(i).getItem())) {
				return i;
			}
		}
		return -1;
	}
}
