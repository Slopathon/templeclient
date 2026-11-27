package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import nz.temple.client.util.Motion;
import nz.temple.client.util.Mining;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class Flatten extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting width;
	private final NumberSetting reach;
	private final BoolSetting headroom;
	private final BoolSetting feet;
	public Flatten() {
		super("Flatten", Category.WORLD, "Clears uneven ground and blocks at head height in front of you as you walk.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Clears one block at a time, looking at it and breaking it with your crosshair and best tool."));
		width = add(new NumberSetting("Width", 1, 1, 3, 1, " b")
				.describe("Width of the path to clear."));
		reach = add(new NumberSetting("Ahead", 1, 1, 3, 1, " b")
				.describe("How many blocks ahead of you to clear."));
		feet = add(new BoolSetting("Clear footing", true)
				.describe("Breaks the step you would have to climb."));
		headroom = add(new BoolSetting("Clear headroom", true)
				.describe("Breaks the block at head height you would walk into."));
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null
				|| Motion.wishDirection(player).lengthSqr() < 1e-6) {
			return;
		}
		float yaw = (float) Math.toRadians(player.getYRot());
		int dx = Math.round(-(float) Math.sin(yaw));
		int dz = Math.round((float) Math.cos(yaw));
		if (dx == 0 && dz == 0) {
			return;
		}
		BlockPos feetPos = player.blockPosition();
		int half = ((int) Math.round(width.get()) - 1) / 2;
		int ahead = (int) Math.round(reach.get());
		for (int step = 1; step <= ahead; step++) {
			BlockPos front = feetPos.offset(dx * step, 0, dz * step);
			for (int side = -half; side <= half; side++) {
				BlockPos column = front.offset(dz * side, 0, -dx * side);
				if (feet.get() && breakBlock(mc, column)) {
					return;
				}
				if (headroom.get() && breakBlock(mc, column.above())) {
					return;
				}
			}
		}
	}
	private boolean breakBlock(Minecraft mc, BlockPos pos) {
		BlockState state = mc.level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0 || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (legit.get()) {
			return nz.temple.client.util.LegitAim.mine(mc.player, pos, 35) != nz.temple.client.util.LegitAim.Step.CANNOT;
		}
		Mining.dig(mc, pos, Direction.UP);
		nz.temple.client.compat.Players.swing(mc.player, InteractionHand.MAIN_HAND);
		return true;
	}
}
