package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import nz.temple.client.util.Inv;
import nz.temple.client.util.Place;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;

public final class AutoTool extends Module {
	private final nz.temple.setting.BoolSetting legit;
	public AutoTool() {
		super("Auto Tool", Category.PLAYER, "Switches to the right tool for the block you are mining.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Switches after a short reaction delay instead of instantly."));
		on(TickEvent.class, e -> tick());
	}

	private net.minecraft.core.BlockPos seenBlock;
	private long switchAt;

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !mc.options.keyAttack.isDown()) {
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}
		if (legit.get()) {
			if (!hit.getBlockPos().equals(seenBlock)) {
				seenBlock = hit.getBlockPos().immutable();
				switchAt = System.currentTimeMillis() + nz.temple.client.util.Human.reactionMs();
			}
			if (System.currentTimeMillis() < switchAt) {
				return;
			}
		}
		String block = Place.path(mc.level.getBlockState(hit.getBlockPos()));
		String tool = toolFor(block);
		if (tool == null || Inv.pathContains(player.getMainHandItem(), tool)) {
			return;
		}
		for (int i = 0; i < 9; i++) {
			if (Inv.pathContains(player.getInventory().getItem(i), tool)) {
				player.getInventory().setSelectedSlot(i);
				return;
			}
		}
	}

	private static String toolFor(String block) {
		if (block.contains("log") || block.contains("wood") || block.contains("planks") || block.contains("leaves")
				|| block.contains("fence") || block.contains("crafting_table") || block.contains("bookshelf")
				|| block.contains("chest") || block.contains("barrel") || block.contains("pumpkin") || block.contains("melon")) {
			return "axe";
		}
		if (block.contains("stone") || block.contains("ore") || block.contains("deepslate") || block.contains("netherrack")
				|| block.contains("obsidian") || block.contains("brick") || block.contains("terracotta") || block.contains("quartz")
				|| block.contains("prismarine") || block.contains("copper") || block.contains("iron_block") || block.contains("gold_block")
				|| block.contains("debris") || block.contains("furnace") || block.contains("anvil") || block.contains("sandstone")) {
			return "pickaxe";
		}
		if (block.contains("dirt") || block.contains("sand") || block.contains("gravel") || block.contains("grass")
				|| block.contains("snow") || block.contains("clay") || block.contains("soul") || block.contains("mud")
				|| block.contains("farmland") || block.contains("path")) {
			return "shovel";
		}
		if (block.contains("cobweb")) {
			return "sword";
		}
		if (block.contains("leaves") || block.contains("wool") || block.contains("vine")) {
			return "shears";
		}
		return null;
	}
}
