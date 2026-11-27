package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import nz.temple.client.util.BlockList;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;

import java.util.Set;

public final class Xray extends Module {
	private static volatile boolean active;
	private static volatile Set<Block> shown = Set.of();
	private static volatile boolean fluids;
	private final nz.temple.setting.EntitySelection blocks;
	private final BoolSetting showFluids;
	private final BlockList list = new BlockList();

	public Xray() {
		super("Xray", Category.RENDER, "Hides every block except the ones you choose, and lights everything, so ores and the like "
				+ "stand out through the ground.");
		blocks = add(nz.temple.client.util.BlockCatalog.selection("Blocks", "*_ore, ancient_debris, spawner, chest, trapped_chest, "
				+ "ender_chest, *shulker_box, obsidian, crying_obsidian, bedrock")
				.describe("The blocks that stay visible. Pick them from the list, with their icons."));
		showFluids = add(new BoolSetting("Show water and lava", true)
				.describe("Keeps water and lava visible, so you can see where lava is before you dig into it."));
		blocks.addListener(this::apply);
		showFluids.addListener(this::apply);
	}
	@Override
	protected void onEnable() {
		shown = list.of(blocks.get());
		fluids = showFluids.get();
		active = true;
		rebuild();
	}
	@Override
	protected void onDisable() {
		active = false;
		rebuild();
	}
	private void apply() {
		shown = list.of(blocks.get());
		fluids = showFluids.get();
		active = isEnabled();
		if (isEnabled()) {
			rebuild();
		}
	}
	public static boolean hides(BlockState state) {
		if (!active || state.isAir() || shown.contains(state.getBlock())) {
			return false;
		}
		return !(fluids && !state.getFluidState().isEmpty());
	}
	public static boolean active() {
		return active;
	}
	private static void rebuild() {
		Fullbright.requestRefresh();
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.level != null) {
				mc.levelExtractor.allChanged();
			}
		});
	}
}
