package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Mining;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Nuker extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting range;
	private final ModeSetting mode;

	public Nuker() {
		super("Nuker", Category.WORLD, "Breaks blocks around you. Instant in creative; mines one at a time in survival.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Mines one block at a time: only blocks you can see and reach, looking at each and breaking it with your "
						+ "crosshair and best tool. No instant breaking."));
		range = add(new NumberSetting("Range", 4, 1, 6, 1, " b")
				.describe("Break radius."));
		mode = add(new ModeSetting("Mode", List.of("Auto", "Creative", "Survival"), "Auto")
				.describe("Auto uses instant breaking in creative and mining in survival."));
		hideWhenLegit(legit, mode);
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		boolean creative = !legit.get() && (mode.is("Creative") || (mode.is("Auto") && player.isCreative()));
		int r = (int) Math.ceil(range.get());
		double maxSq = range.get() * range.get();
		BlockPos origin = player.blockPosition();
		BlockPos nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					Vec3 center = Vec3.atCenterOf(pos);
					if (!breakable(mc.level, pos) || player.getEyePosition().distanceToSqr(center) > maxSq) {
						continue;
					}
					if (creative) {
						mc.gameMode.destroyBlock(pos);
					} else {
						double d = player.getEyePosition().distanceToSqr(center);
						if (d < nearestSq && (!legit.get() || nz.temple.client.util.LegitAim.visiblePoint(player, pos, player.blockInteractionRange()) != null)) {
							nearestSq = d;
							nearest = pos;
						}
					}
				}
			}
		}
		if (!creative && nearest != null && legit.get()) {
			nz.temple.client.util.LegitAim.mine(player, nearest, 35);
		} else if (!creative && nearest != null) {
			Mining.dig(mc, nearest, Direction.UP);
			nz.temple.client.compat.Players.swing(player, net.minecraft.world.InteractionHand.MAIN_HAND);
		}
	}

	private static boolean breakable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir()
				&& state.getBlock() != Blocks.BEDROCK
				&& state.getFluidState().isEmpty()
				&& state.getDestroySpeed(level, pos) >= 0;
	}
}
