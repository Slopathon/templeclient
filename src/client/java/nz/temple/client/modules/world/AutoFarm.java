package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Mining;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AutoFarm extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting range;

	public AutoFarm() {
		super("Auto Farm", Category.WORLD, "Harvests mature crops around you while you walk through a farm.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Only harvests crops you can see and reach, looking at each one and breaking it with your crosshair."));
		range = add(new NumberSetting("Range", 4, 1, 6, 1, " b")
				.describe("Harvest radius."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		int r = (int) Math.ceil(range.get());
		double maxSq = range.get() * range.get();
		BlockPos nearest = null;
		double nearestSq = Double.MAX_VALUE;
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			BlockState state = mc.level.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
				continue;
			}
			double distanceSq = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
			if (distanceSq <= maxSq && distanceSq < nearestSq && (!legit.get() || nz.temple.client.util.LegitAim.visiblePoint(player, pos, player.blockInteractionRange()) != null)) {
				nearest = pos.immutable();
				nearestSq = distanceSq;
			}
		}
		if (nearest != null) {
			if (legit.get()) {
				nz.temple.client.util.LegitAim.mine(player, nearest, 35);
			} else {
				Mining.dig(mc, nearest, Direction.UP);
				nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
			}
		}
	}
}
