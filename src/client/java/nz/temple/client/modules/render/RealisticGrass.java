package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class RealisticGrass extends Module {
	public record Look(float height, float variation, float shine, float flow, float displacement, float density, float distance,
			int version) {
	}
	private static volatile RealisticGrass instance;
	private static volatile boolean hiding;
	private final NumberSetting height;
	private final NumberSetting variation;
	private final NumberSetting shine;
	private final NumberSetting flow;
	private final NumberSetting displacement;
	private final NumberSetting density;
	private final NumberSetting distance;
	private int version;
	public RealisticGrass() {
		super("Realistic Grass", Category.COSMETICS, "Grass blocks grow separate blades that sway in the wind and bend away from "
				+ "you. Replaces short and tall grass.");
		height = add(new NumberSetting("Height", 100, 40, 250, 5, "%").describe("How tall the blades grow."));
		variation = add(new NumberSetting("Height variation", 50, 0, 100, 5, "%").describe("How much blade height differs from "
				+ "patch to patch and blade to blade."));
		shine = add(new NumberSetting("Shine", 40, 0, 100, 5, "%").describe("How strongly the blades catch the sun."));
		flow = add(new NumberSetting("Ambient flow", 100, 0, 250, 5, "%").describe("How far the wind moves the blades."));
		displacement = add(new NumberSetting("Displacement", 100, 0, 200, 5, "%").describe("How far blades bend away from "
				+ "players and mobs walking through them."));
		density = add(new NumberSetting("Density", 70, 20, 100, 5, "%").describe("How many blades grow on each block. Lower is "
				+ "faster."));
		distance = add(new NumberSetting("Distance", 48, 16, 96, 8, " blocks").describe("How far from you blades are drawn. "
				+ "Lower is faster."));
		height.addListener(this::remesh);
		variation.addListener(this::remesh);
		density.addListener(this::remesh);
		instance = this;
	}
	private void remesh() {
		version++;
	}
	@Override
	protected void onEnable() {
		hiding = true;
		rebuild();
	}

	@Override
	protected void onDisable() {
		hiding = false;
		rebuild();
	}

	private static void rebuild() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.level != null) {
				mc.levelExtractor.allChanged();
			}
		});
	}
	public static Look look() {
		RealisticGrass m = instance;
		if (m == null || !m.isEnabled()) {
			return null;
		}
		return new Look(m.height.getFloat() / 100f, m.variation.getFloat() / 100f, m.shine.getFloat() / 100f, m.flow.getFloat() / 100f,
				m.displacement.getFloat() / 100f, m.density.getFloat() / 100f, m.distance.getFloat(), m.version);
	}
	public static int plant(BlockState state) {
		if (state.is(Blocks.SHORT_GRASS)) {
			return 1;
		}
		if (state.is(Blocks.TALL_GRASS)) {
			return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? 3 : 2;
		}
		return 0;
	}
	public static boolean hides(BlockGetter level, BlockPos pos, BlockState state) {
		if (!hiding) {
			return false;
		}
		int plant = plant(state);
		if (plant == 0) {
			return false;
		}
		return level.getBlockState(plant == 3 ? pos.below(2) : pos.below()).is(Blocks.GRASS_BLOCK);
	}
}
