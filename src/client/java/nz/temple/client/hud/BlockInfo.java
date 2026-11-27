package nz.temple.client.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public record BlockInfo(BlockPos pos, ItemStack icon, String name, String coords, String mining, boolean miningGood,
		String details) {
}
